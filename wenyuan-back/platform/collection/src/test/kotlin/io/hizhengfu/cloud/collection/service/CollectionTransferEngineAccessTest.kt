package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

/** Actual engine, business callbacks, identity directory, locks and archives; statutory document preparation is stubbed. */
class CollectionTransferEngineAccessTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @CsvSource("submit,orgDisabled", "cancel,orgDisabled", "void,orgDisabled",
        "submit,during-revoked", "submit,during-disabled", "submit,during-moved", "submit,during-orgDisabled",
        "cancel,during-revoked", "cancel,during-disabled", "cancel,during-moved", "cancel,during-orgDisabled",
        "void,during-revoked", "void,during-disabled", "void,during-moved", "void,during-orgDisabled",
        "submit,active", "cancel,active", "void,active", "submit,disabled", "cancel,disabled", "void,disabled", "submit,revoked", "cancel,revoked", "void,revoked", "submit,moved", "cancel,moved", "void,moved", "approve,active", "approve,wrong-actor", "approve,blank-opinion", "approve,role-revoked", "approve,actor-moved", "approve,actor-disabled")
    fun `engine operations use current operator access without partial state`(operation: String, change: String) {
        val fourParty=operation=="approve"
        val pg=System.getProperty("collection.test.pg.config")?.let { file ->
            val settings=java.util.Properties().apply { java.nio.file.Files.newInputStream(Path.of(file)).use(::load) }
            require(settings.getProperty("url").startsWith("jdbc:postgresql://127.0.0.1:") && !settings.getProperty("url").endsWith("/collection_db"))
            org.postgresql.ds.PGSimpleDataSource().apply { setURL(settings.getProperty("url"));user=settings.getProperty("user");password=settings.getProperty("password") }
        }
        val schema="transfer_engine_"+java.util.UUID.randomUUID().toString().replace("-","")
        pg?.connection?.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
        pg?.currentSchema=schema
        val manager=SpringTransactionManager(pg ?: SQLiteDataSource().apply { url="jdbc:sqlite:${directory.resolve("engine.db")}" },DatabaseConfig {})
        val tx=TransactionTemplate(manager)
        val scope=CollectionScopeResolver("multi", "source")
        val user=SecurityUser(id="operator",name="合成经办",username="operator",password="",groupId="source",groupIds=setOf("source"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try { StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.refresh();SpringContextHolder().setApplicationContext(context)
            val binder=mock(BusinessAttachmentBinder::class.java)
            val archives=CollectionPermanentArchiveService(binder,scope)
            val statutory=spy(CollectionTransferStatutoryService(scope,CollectionResponsibleUserService(CollectionActorDirectory(scope),scope),binder,archives,mock(CollectionArchivePacketService::class.java),mock(CollectionDisclosureService::class.java)))
            // Legal documents and four-party signoff have separate persistence coverage. Keep current access checks real.
            val loader=FlowGraphLoader(32,5)
            val definitions=FlowDefinitionService().also { it.graphLoader=loader }
            lateinit var service: CollectionTransferService
            val engine=FlowEngine(FlowAssigneeResolver(emptyList()),ApplicationEventPublisher { event ->
                if(event is FlowCanceledEvent)service.onCanceled(event)
                if(event is FlowTaskCompletedEvent)service.onTaskCompleted(event)
                if(event is FlowCompletedEvent)service.onCompleted(event)
            },context.beanFactory.getBeanProvider(SubprocessStarter::class.java),loader)
            val instances=FlowInstanceService(definitions,engine)
            service=CollectionTransferService(instances,scope,mock(CollectionService::class.java),binder,CollectionOperationLockService(),statutory,archives,mock(CollectionCatalogService::class.java))
            tx.executeWithoutResult {
                actorSchema()
                SchemaUtils.create(Collections,CollectionOrgs,CollectionTransfers,CollectionOperationLocks,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                    FlowDefinitions,FlowNodeDefs,FlowEdgeDefs,FlowInstances,FlowTasks,FlowCCs,FlowInstanceLogs)
                for(id in listOf("source","target","outside")) {
                    GroupDao.new(id) { name=id;type='1';parentId="0";parentIds="0," }
                    CollectionOrgDao.new(id) { code=id;name=id }
                }
                originalReader("operator",CollectionConst.STORAGE,"source")
                CollectionDao.new("object") { orgId="source";totalRegNo="000001";name="合成藏品" }
                val record=CollectionTransferDao.new("request") { orgId="source";fromOrgId="source";toOrgId="target";collectionId="object";reason="合成权限验证";createBy="operator" }
                doNothing().`when`(statutory).requirePrepared(any(CollectionTransferDao::class.java) ?: record)
                if(!fourParty)doReturn(emptyMap<String,CollectionTransferStatutoryService.Actor>()).`when`(statutory).expectedActors(any(CollectionTransferDao::class.java) ?: record)
                if(fourParty) {
                    doReturn("").`when`(statutory).allFiles(any(CollectionTransferDao::class.java) ?: record)
                    for(org in listOf("source","target")) for(role in listOf("keeper","leader")) {
                        val id="$org-$role"
                        originalReader(id,CollectionConst.STORAGE,org)
                        actorRole(id,if(role=="keeper")CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR)
                    }
                    record.dossier=io.hizhengfu.cloud.collection.domain.CollectionTransferDossierVo(
                        io.hizhengfu.cloud.collection.domain.EnumCollectionTransferKind.STATE_ALLOCATION,
                        "source-keeper","source-leader","target-keeper","target-leader","target-warehouse","synthetic-document-stub","原馆账页","接收馆账页")
                }
                val model=FlowModel(nodes=listOf("s" to "START","review" to "APPROVE","e" to "END").map { (key,type) ->
                    FlowModelNode(key,type,properties=FlowNodeProperties(nodeKey=key,name=key,nodeType=type,assigneeType=if(key=="review")"FIXED_USER" else null,assignees=if(key=="review")listOf("synthetic-reviewer") else null))
                },edges=listOf(FlowModelEdge("a","s","review"),FlowModelEdge("b","review","e")))
                val definition=definitions.create(FlowDefinitionVo(name="隔离引擎权限验证",key=CollectionConst.FLOW_TRANSFER),"operator")
                val modelJson=if(fourParty) {
                    val text=javaClass.getResourceAsStream("/flow/collection_transfer_2026.json")!!.bufferedReader().use { it.readText() }
                    JsonUtils.mapper.readTree(text).path("model").toString()
                } else JsonUtils.mapper.writeValueAsString(model)
                definitions.saveModel(definition.id.value,modelJson,"operator")
                definitions.publish(definition.id.value,"operator")
            }
            if(operation!="submit")tx.executeWithoutResult { service.submit("request") }
            val during=change.startsWith("during-")
            val fault=change.removePrefix("during-")
            fun mutate() {
                when(fault) {
                    "revoked"->RoleUsers.deleteWhere {userId eq "operator"}
                    "disabled"->UserDao["operator"].enabled=false
                    "moved"->UserDao["operator"].groupId="outside"
                    "orgDisabled"->CollectionOrgDao["source"].orgStatus=io.hizhengfu.cloud.collection.domain.EnumOrgStatus.DISABLED
                }
            }
            if(during)tx.executeWithoutResult {
                val sql=when(fault) {
                    "revoked"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='operator'"
                    "disabled"->"UPDATE ${Users.tableName} SET enabled=${if(pg!=null)"false" else "0"} WHERE id='operator'"
                    "moved"->"UPDATE ${Users.tableName} SET group_id='outside' WHERE id='operator'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${io.hizhengfu.cloud.collection.domain.EnumOrgStatus.DISABLED.ordinal} WHERE id='source'"
                }
                val table=if(operation=="submit")FlowTasks.tableName else FlowInstances.tableName
                val event=if(operation=="submit")"AFTER INSERT" else "AFTER UPDATE"
                val condition=if(operation=="submit")"" else " WHEN (NEW.status='CANCELED')"
                val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                if(pg!=null) {
                    current.exec("CREATE FUNCTION transfer_engine_fault_fn() RETURNS TRIGGER LANGUAGE plpgsql AS ${"$"}fault${"$"} BEGIN $sql; RETURN NEW; END ${"$"}fault${"$"}")
                    current.exec("CREATE TRIGGER transfer_engine_fault $event ON $table FOR EACH ROW$condition EXECUTE FUNCTION transfer_engine_fault_fn()")
                } else current.exec("CREATE TRIGGER transfer_engine_fault $event ON $table$condition BEGIN $sql; END")
            } else tx.executeWithoutResult {mutate()}
            fun snapshot()=tx.execute {
                listOf(CollectionTransfers,Collections,CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                    FlowInstances,FlowTasks,FlowCCs,FlowInstanceLogs,Users,RoleUsers,CollectionOrgs).map {table ->
                    table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()
                }
            }
            val before=snapshot()
            val perform={ tx.executeWithoutResult { when(operation) { "submit" -> service.submit("request"); "cancel" -> service.cancel("request"); else -> service.void("request") } } }
            if(fourParty) {
                val actors=listOf("source_keeper" to "source-keeper","source_leader" to "source-leader","target_keeper" to "target-keeper","target_leader" to "target-leader")
                for((node,actor) in actors) {
                    val taskId=tx.execute {
                        val task=FlowTaskDao.all().single { it.status=="PENDING" }
                        assertEquals(node,task.nodeKey);assertEquals(actor,task.assignee);task.id.value
                    }!!
                    val org=actor.substringBefore('-')
                    val role=if(actor.endsWith("keeper"))CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR
                    if(change!="active") {
                        tx.executeWithoutResult {
                            when(change) {
                                "role-revoked" -> RoleUsers.deleteWhere { userId eq actor }
                                "actor-moved" -> UserDao[actor].groupId="outside"
                                "actor-disabled" -> UserDao[actor].enabled=false
                            }
                        }
                        val prior=snapshot()
                        assertThrows(Exception::class.java) { tx.executeWithoutResult {
                            engine.complete(FlowTaskDao[taskId],if(change=="wrong-actor")"operator" else actor,actor,"APPROVE",if(change=="blank-opinion")" " else "合成本人确认")
                        } }
                        assertEquals(prior,snapshot())
                        tx.executeWithoutResult {
                            UserDao[actor].groupId=org;UserDao[actor].enabled=true
                            if(change=="role-revoked")actorRole(actor,role)
                        }
                    }
                    tx.executeWithoutResult { engine.complete(FlowTaskDao[taskId],actor,actor,"APPROVE","合成本人确认") }
                }
                tx.executeWithoutResult {
                    val record=CollectionTransferDao["request"]
                    assertEquals(EnumFlowStatus.PASS,record.flowStatus)
                    assertEquals("COMPLETED",FlowInstanceDao[record.flowInstanceId!!].status)
                    assertTrue(FlowTaskDao.all().none { it.status=="PENDING" })
                    assertEquals("source",CollectionDao["object"].orgId)
                    assertNull(record.executedAt);assertNull(record.execution)
                    assertEquals(1L,CollectionOperationLockDao.count())
                    assertEquals(2L,CollectionPermanentArchiveDao.count())
                    assertNotNull(statutory.requireCurrentApprovalEvidence(record))
                    val approvals=FlowInstanceLogDao.all().filter { it.action=="APPROVE" }.sortedBy { it.createTime }
                    assertEquals(actors.map { it.first },approvals.map { it.nodeKey })
                    assertEquals(actors.map { it.second },approvals.map { it.operator })
                }
            } else if(change!="active") {
                assertThrows(BadRequestException::class.java) { perform() }
                assertEquals(before,snapshot())
                if(during) {
                    tx.executeWithoutResult {
                        val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                        val table=if(operation=="submit")FlowTasks.tableName else FlowInstances.tableName
                        current.exec("DROP TRIGGER transfer_engine_fault"+if(pg!=null)" ON $table" else "")
                        if(pg!=null)current.exec("DROP FUNCTION transfer_engine_fault_fn()")
                    }
                    perform()
                    tx.executeWithoutResult {
                        val record=CollectionTransferDao["request"]
                        assertEquals(when(operation) { "submit" -> EnumFlowStatus.AUDIT; "cancel" -> EnumFlowStatus.CANCEL; else -> EnumFlowStatus.VOID },record.flowStatus)
                        assertEquals(if(operation=="submit")1L else 0L,CollectionOperationLockDao.count())
                        assertNull(record.executedAt)
                    }
                }
            } else {
                perform()
                tx.executeWithoutResult {
                    val record=CollectionTransferDao["request"]
                    assertEquals(when(operation) { "submit" -> EnumFlowStatus.AUDIT; "cancel" -> EnumFlowStatus.CANCEL; else -> EnumFlowStatus.VOID },record.flowStatus)
                    assertNotNull(record.flowInstanceId)
                    val instance=FlowInstanceDao[record.flowInstanceId!!]
                    if(operation=="submit") {
                        assertEquals("RUNNING",instance.status);assertEquals("PENDING",FlowTaskDao.all().single().status);assertEquals(1L,CollectionOperationLockDao.count())
                    } else {
                        assertEquals("CANCELED",instance.status);assertTrue(FlowTaskDao.all().none { it.status=="PENDING" });assertEquals(0L,CollectionOperationLockDao.count())
                    }
                    assertEquals(1L,CollectionPermanentArchiveDao.count())
                    assertTrue(FlowInstanceLogDao.count()>0)
                }
            }
        } } finally {
            SecurityContextHolder.clearContext()
            pg?.connection?.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}
