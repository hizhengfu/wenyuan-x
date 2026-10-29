package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.support.AopUtils
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import java.nio.file.Files as IoFiles
import java.security.MessageDigest
import javax.sql.DataSource
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

/** Only original02-C05's ten missing disabled/configured × formal-operation results.
 * Reuses the accepted real Spring workflow fixture setup in owned PostgreSQL schemas, not its tests.
 * LOCAL originals are actual controlled input bytes; the binding adapter writes FileContents but is not FileService/upload/GUI proof.
 */
@Tag("postgres-concurrency")
class CollectionChangeCurrentEntrancesPostgresTest {
    companion object {
        @JvmStatic fun entranceCases(): List<Arguments> {
            val selected=System.getProperty("collection.test.currentChangeKind")
            require(selected==null || selected=="configured") { "Only the same original ten cases or their five configured cases may be selected" }
            return listOf("disabled","configured").filter {selected==null || it==selected}.flatMap { damage ->
                listOf("submit","cancel","void","firstexecute","correction").map { Arguments.of(damage,it) }
            }
        }
    }
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, val source: PGSimpleDataSource, val local: Path) {
        val service = c.getBean(CollectionChangeService::class.java)
        lateinit var paperBookId: String
        fun execution() = CollectionChangeExecutionVo("signed", null, LocalDate.now(), paperBookId, 1)
        val tasks = c.getBean(FlowTaskService::class.java)
        val instances = c.getBean(FlowInstanceService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun login(id: String) {
            val user=db { c.getBean(CollectionActorDirectory::class.java).requireActive(id) }
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,"",user.authorities)
        }
        fun act(id: String, person: String, action: String="APPROVE") {
            val task=db { FlowTaskDao.find {(FlowTasks.instanceId eq CollectionChangeDao[id].flowInstanceId!!) and (FlowTasks.status eq "PENDING")}.single().id.value }
            tasks.complete(CompleteTaskRequest(task,action,comment="本人核对材料"),person,person)
        }
        fun draft(): String {
            val p=service.proposal("object")
            return service.save(p.copy(reason="按原件核对定名",content=p.content!!.copy(formalName="原拟定名"),keeperUserId="keeper",leaderUserId="leader",evidenceFileIds="evidence"))
        }
        fun original(): String {
            val id=draft()
            service.submit(id);act(id,"keeper");act(id,"leader","REJECT")
            return id
        }
        fun request(id:String,key:String="request")=CollectionChangeCorrectionRequest(key,db { CollectionChangeDao[id].flowInstanceId!! },"补充新发现的定名依据","correction-file")
    }
    private fun fixture(body:(Runtime)->Unit) {
        require(System.getProperty("collection.test.pg.config") != null) { "Explicit private development PostgreSQL config required" }
        departureTestDatabase(dir.resolve("unused.db")) { dataSource ->
        val source = dataSource as PGSimpleDataSource
        val local = IoFiles.createDirectory(dir.resolve("owned-local"))
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("single",CollectionDefaultOrgSettings()))
            c.beanFactory.registerSingleton("binder",mock(BusinessAttachmentBinder::class.java).also { b->
                `when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0).trim().takeIf(String::isNotBlank)}
                doAnswer { call ->
                    BusinessAttachmentBindingPlanner.planSingle(call.getArgument<String>(0), call.getArgument<String>(1), call.getArgument<String>(2)).forEach { item ->
                        FileContents.insertIgnore { row -> row[fileId]=item.fileId; row[contentId]=item.contentId; row[aliasName]=item.aliasName }
                    }; null
                }.`when`(b).bindSingle(anyString(),anyString(),anyString())
                doAnswer { call ->
                    BusinessAttachmentBindingPlanner.plan(call.getArgument<String>(0), call.getArgument<String>(1), call.getArgument<String>(2)).forEach { item ->
                        FileContents.insertIgnore { row -> row[fileId]=item.fileId; row[contentId]=item.contentId; row[aliasName]=item.aliasName }
                    }; null
                }.`when`(b).bind(anyString(),anyString(),anyString())
            })
            c.beanFactory.registerSingleton("localStorage",LocalFileObjectStorage(local.toString()))
            c.beanFactory.registerSingleton("grading",mock(CollectionGradingService::class.java))
            c.beanFactory.registerSingleton("receipt",mock(AccessionReceiptService::class.java))
            c.beanFactory.registerSingleton("loader",FlowGraphLoader(16,5))
            c.beanFactory.registerSingleton("registry",FlowBusinessTypeRegistry())
            c.beanFactory.registerSingleton("resolver",FlowAssigneeResolver())
            c.register(Transactions::class.java,CollectionChangeService::class.java,CollectionCatalogService::class.java,CollectionOperationLockService::class.java,
                CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java,CollectionRetainedLedgerService::class.java,CollectionActorDirectory::class.java,CollectionResponsibleUserService::class.java,PaperLedgerService::class.java,
                CoreCollectionFlowReadSources::class.java,CollectionFlowStartPolicy::class.java,CollectionNamedFlowActorPolicy::class.java,CollectionFlowFormPolicy::class.java,
                CollectionFlowDefinitionConstraint::class.java,FlowDefinitionService::class.java,FlowEngine::class.java,FlowInstanceService::class.java,FlowTaskService::class.java,FlowEventDispatcher::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager),source,local)
            assertTrue(AopUtils.isAopProxy(r.service))
            r.db {
                actorSchema()
                SchemaUtils.create(Configs,CollectionSets,CollectionOrgs,Collections,CollectionCatalogs,CollectionCatalogVersions,CollectionChanges,CollectionChangeApprovals,CollectionOperationLocks,CollectionRetainedLedgers,PaperLedgerBooks,PaperLedgerCustodies,
                    CollectionLedgerLogs,CollectionLedgerStatEvents,CollectionMedias,CollectionAttachments,CollectionCategories,Warehouses,
                    CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FileContents,FileUsers,FlowDefinitions,FlowNodeDefs,FlowEdgeDefs,FlowInstances,FlowTasks,FlowInstanceLogs,FlowCCs)
                listOf("a","b").forEach {id->CollectionOrgDao.new(id){code=id;name=id;systemGroupId="g-$id"};GroupDao.new("g-$id"){name=id;type='1';parentId="0";parentIds="0,"}}
                ConfigDao.new("DEV-SYNTHETIC-default-museum") { name=CollectionDefaultOrgSettings.KEY;content="a";enable=true;isPrivate=true;fieldType="STRING" }
                listOf("registrar","keeper","leader","ledgerKeeper","other").forEach {id->UserDao.new(id){name=id;loginName=id;password="unused";enabled=true;groupId=if(id=="other")"g-b" else "g-a"}}
                actorRole("registrar","REGISTRAR",setOf(CollectionConst.LEDGER_CORRECT,"FLOW_MANAGE"))
                actorRole("other","OTHER",setOf(CollectionConst.LEDGER_CORRECT))
                actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("leader",CollectionConst.ROLE_DIRECTOR)
                actorRole("ledgerKeeper",CollectionConst.ROLE_LEDGER_CUSTODIAN)
                listOf("evidence","correction-file","signed").forEach { id ->
                    val input="DEV-SYNTHETIC 02-C05 $id; isolated workflow software test only, no physical paper or human signature.\n".toByteArray()
                    IoFiles.write(local.resolve("$id.txt"),input)
                    FileDao.new(id){name="DEV-SYNTHETIC-$id.txt";path="/$id.txt";ext="txt";hash=MessageDigest.getInstance("MD5").digest(input).joinToString(""){"%02x".format(it)};size=input.size.toLong()}
                }
                val collection=CollectionDao.new("object") {orgId="a";totalRegNo="001";name="原定名";era="清";texture="瓷";categoryId="ceramic";
                    level=EnumRelicLevel.GENERAL;this.source=EnumSource.OLD;acquireDate=LocalDate.of(2020,1,1);completeness=EnumCompleteness.COMPLETE;
                    lifeStatus=EnumCollectionStatus.IN_STORE;catalogStatus=EnumCatalogStatus.APPROVED;registeredAt=LocalDateTime.of(2020,1,2,10,0)}
                c.getBean(CollectionCatalogService::class.java).ensureCatalogFor(collection)
            }
            r.login("registrar")
            r.paperBookId = c.getBean(PaperLedgerService::class.java).register("a", PaperLedgerBookVo(
                "总账第一册", "总登记账", LocalDate.of(2020, 1, 1), 20, "测试藏品范围", "evidence",
                PaperLedgerCustodyVo(LocalDateTime.now().minusDays(1), "ledgerKeeper", "纸账专用室",
                    EnumPaperLedgerCondition.GOOD, "保存完好", "signed")))
            try {
                val json=javaClass.classLoader.getResourceAsStream("flow/collection_change.json")!!.use {JsonUtils.mapper.readTree(it).path("model").toString()}
                val definitions=c.getBean(FlowDefinitionService::class.java)
                val d=definitions.create(FlowDefinitionVo(key=CollectionConst.FLOW_CHANGE,name="原件补正核验",modelData=json),"author")
                definitions.publish(d.id.value,"publisher")
                body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
        }
    }

    private data class TableState(val rows: Long, val digest: String)
    private fun projection(source: DataSource, schema: String): Map<String, TableState> = source.connection.use { c ->
        val names=c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename").use { s ->
            s.setString(1,schema);s.executeQuery().use { rows ->buildList {while(rows.next())add(rows.getString(1))} }
        }
        names.associateWith { table ->c.createStatement().use { s ->
            s.executeQuery("SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM \"$schema\".\"$table\" t").use { rows ->
                assertTrue(rows.next());TableState(rows.getLong(1),rows.getString(2))
            }
        } }
    }
    private fun localState(root: Path)=IoFiles.list(root).use { files ->files.sorted().toList().associate { p ->
        p.fileName.toString() to MessageDigest.getInstance("SHA-256").digest(IoFiles.readAllBytes(p)).joinToString(""){"%02x".format(it)}
    } }
    @ParameterizedTest(name="02-C05 {0} rejects legal {1} using the original authenticated identity")
    @MethodSource("entranceCases")
    fun `formal current change entrances reject disabled account or changed single museum`(damage:String,entry:String)=fixture { r ->
        val id=if(entry=="correction")r.original() else r.draft()
        if(entry in setOf("cancel","firstexecute")) {
            r.service.submit(id)
            if(entry=="firstexecute") {r.act(id,"keeper");r.act(id,"leader")}
        }
        val request=if(entry=="correction")r.request(id,"DEV-SYNTHETIC-current-only") else null
        val expectedStage=when(entry){"cancel"->EnumFlowStatus.AUDIT;"firstexecute"->EnumFlowStatus.PASS;"correction"->EnumFlowStatus.REJECT;else->EnumFlowStatus.DRAFT}
        r.db {
            val row=CollectionChangeDao[id]
            assertEquals(expectedStage,row.flowStatus);assertNull(row.appliedAt)
            if(entry in setOf("cancel","firstexecute","correction")) assertNotNull(row.flowInstanceId)
            else assertNull(row.flowInstanceId)
            assertEquals("a",row.orgId);assertFalse(row.evidenceFileIds.isNullOrBlank())
            if(entry=="firstexecute")assertEquals(2L,CollectionChangeApprovalDao.find {CollectionChangeApprovals.changeId eq id}.count())
            if(entry=="correction")assertEquals(1L,CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "SUBMITTED")}.count())
        }
        val initialUser=r.db {r.c.getBean(CollectionActorDirectory::class.java).requireActive("registrar")}
        assertFalse("ADMIN" in initialUser.roles);assertFalse(CollectionConst.CROSS_ORG in initialUser.permissions)
        val auth=SecurityContextHolder.getContext().authentication
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        assertEquals("a",r.db {scope.currentOrgId()});assertFalse(scope.isMulti())
        val schema=requireNotNull(r.source.currentSchema)
        val qualifiedBefore=projection(r.source,schema)
        val localBefore=localState(r.local);assertEquals(3,localBefore.size)
        if(damage=="disabled")r.source.connection.use { c ->c.createStatement().use {assertEquals(1,it.executeUpdate("UPDATE sys_user SET enabled=false WHERE id='registrar'"))} }
        else updateDefaultMuseum(r.source,"b")
        val deniedBefore=projection(r.source,schema)
        assertSame(auth,SecurityContextHolder.getContext().authentication,"Never refresh/relogin the old authenticated principal")
        val rejection=assertThrows(BadRequestException::class.java) {
            when(entry) {
                "submit"->r.service.submit(id)
                "cancel"->r.service.cancel(id)
                "void"->r.service.void(id)
                "firstexecute"->r.service.execute(id,r.execution())
                else->r.service.createCorrection(id,requireNotNull(request))
            }
        }
        val message=requireNotNull(rejection.message)
        if(damage=="disabled")assertTrue(message.contains("账号不存在、已停用"),"Expected current-account refusal, got: $message")
        else assertTrue(message.contains("不属于当前配置的收藏单位"),"Expected configuration refusal, got: $message")
        val deniedAfter=projection(r.source,schema)
        assertEquals(deniedBefore,deniedAfter,"Every declared table must retain the exact failed-operation baseline, including original flow, ledger and references")
        if(damage=="disabled")r.source.connection.use { c ->c.createStatement().use {assertEquals(1,it.executeUpdate("UPDATE sys_user SET enabled=true WHERE id='registrar'"))} }
        else updateDefaultMuseum(r.source,"a")
        val restored=projection(r.source,schema)
        assertEquals(qualifiedBefore,restored,"Restore only this deliberate synthetic qualification change, then compare all rows/fields again")
        assertEquals(localBefore,localState(r.local));assertSame(auth,SecurityContextHolder.getContext().authentication)
        assertEquals(expectedStage,r.service.get(id).flowStatus)
        println("02_C05_CURRENT_FORMAL_ENTRANCE_RESULT "+JsonUtils.toJson(mapOf("schema" to schema,"change" to damage,"entry" to entry,
            "initialStage" to expectedStage.name,"sameAuthenticatedIdentity" to true,"qualificationRefusal" to message,"declaredTables" to qualifiedBefore.size,
            "qualifiedBefore" to qualifiedBefore.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)},
            "deniedBefore" to deniedBefore.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)},
            "deniedAfter" to deniedAfter.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)},
            "restored" to restored.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)},"localInputHashes" to localBefore,
            "backendDefaultSetting" to CollectionDefaultOrgSettings.KEY,"configuredViaCommittedOwnedDatabaseSetting" to (damage=="configured"),
            "restoredLegalRead" to true,"businessOperationReplayed" to false,"physicalOrGuiSigning" to false)))
    }
    private fun updateDefaultMuseum(source: DataSource,orgId:String) = source.connection.use { c ->
        assertTrue(c.autoCommit,"Configuration changes must be independently committed before the next service transaction")
        c.prepareStatement("UPDATE sys_config SET content=? WHERE name=?").use { s ->
            s.setString(1,orgId);s.setString(2,CollectionDefaultOrgSettings.KEY);assertEquals(1,s.executeUpdate())
        }
    }
    @ParameterizedTest(name="02-C05 legal DRAFT void rejects current {0}")
    @ValueSource(strings=["revoked","moved","keeper"])
    fun `legal draft void checks current registrar qualification before any stage mutation`(damage:String)=fixture { r ->
        val id=r.draft()
        val keeperRole="DEV-SYNTHETIC-added-keeper-role"
        r.db {
            assertEquals(EnumFlowStatus.DRAFT,CollectionChangeDao[id].flowStatus)
            assertNull(CollectionChangeDao[id].flowInstanceId);assertNull(CollectionChangeDao[id].appliedAt)
            Roles.insert {it[Roles.id]=keeperRole;it[Roles.code]=CollectionConst.ROLE_KEEPER;it[Roles.name]="DEV-SYNTHETIC new keeper duty"}
        }
        val auth=SecurityContextHolder.getContext().authentication
        val schema=requireNotNull(r.source.currentSchema)
        val before=projection(r.source,schema);val localBefore=localState(r.local);assertEquals(3,localBefore.size)
        val role="role:REGISTRAR"
        fun mutate(sql:String,vararg values:String)=r.source.connection.use {c->
            assertTrue(c.autoCommit)
            c.prepareStatement(sql).use {s->values.forEachIndexed {i,value->s.setString(i+1,value)};assertEquals(1,s.executeUpdate())}
        }
        when(damage) {
            "revoked"->mutate("DELETE FROM sys_role_user WHERE user_id=? AND role_id=?","registrar",role)
            "moved"->mutate("UPDATE sys_user SET group_id=? WHERE id=?","g-b","registrar")
            else->mutate("INSERT INTO sys_role_user(user_id,role_id) VALUES (?,?)","registrar",keeperRole)
        }
        val deniedBefore=projection(r.source,schema)
        assertSame(auth,SecurityContextHolder.getContext().authentication)
        val rejection=assertThrows(BadRequestException::class.java) {r.service.void(id)}
        val expected=when(damage) {
            "revoked"->"当前账号没有信息变更申请权限"
            "moved"->"无权查看信息变更申请"
            else->"总账登记与库房保管岗位不相容"
        }
        assertTrue(requireNotNull(rejection.message).contains(expected),"Must be a current qualification refusal on a legal DRAFT: ${rejection.message}")
        val deniedAfter=projection(r.source,schema);assertEquals(deniedBefore,deniedAfter)
        when(damage) {
            "revoked"->mutate("INSERT INTO sys_role_user(user_id,role_id) VALUES (?,?)","registrar",role)
            "moved"->mutate("UPDATE sys_user SET group_id=? WHERE id=?","g-a","registrar")
            else->mutate("DELETE FROM sys_role_user WHERE user_id=? AND role_id=?","registrar",keeperRole)
        }
        val restored=projection(r.source,schema)
        assertEquals(before,restored);assertEquals(localBefore,localState(r.local));assertSame(auth,SecurityContextHolder.getContext().authentication)
        assertEquals(EnumFlowStatus.DRAFT,r.service.get(id).flowStatus)
        fun rows(values:Map<String,TableState>)=values.mapValues {(_,v)->mapOf("rows" to v.rows,"digest" to v.digest)}
        println("02_C05_LEGAL_VOID_CURRENT_RESULT "+JsonUtils.toJson(mapOf("schema" to schema,"damage" to damage,"legalInitialStage" to "DRAFT",
            "sameAuthenticatedPrincipal" to true,"independentQualificationCommit" to true,"qualificationRefusal" to rejection.message,
            "before" to rows(before),"deniedBefore" to rows(deniedBefore),"deniedAfter" to rows(deniedAfter),"restored" to rows(restored),
            "localInputHashes" to localBefore,"restoredLegalRead" to true,"businessOperationReplayed" to false,"actualGuiOrPhysicalSignature" to false)))
    }
}
