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
import io.hizhengfu.cloud.sys.system.dao.*
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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

/** Real engine, template, current identities, business callbacks, locks and transactions.
 * Legal document checks are stubbed here; full preparation has separate persistence and development HTTP coverage. */
class LoanEngineAccessTest {
    @TempDir lateinit var directory:Path
    @ParameterizedTest
    @CsvSource("submit,active", "submit,revoked", "submit,disabled",
        "submit,moved", "submit,orgDisabled", "submit,during-revoked",
        "submit,during-disabled", "submit,during-moved", "submit,during-orgDisabled",
        "cancel,active", "cancel,revoked", "cancel,disabled",
        "cancel,moved", "cancel,orgDisabled", "cancel,during-revoked",
        "cancel,during-disabled", "cancel,during-moved", "cancel,during-orgDisabled",
        "void,active", "void,revoked", "void,disabled",
        "void,moved", "void,orgDisabled", "void,during-revoked",
        "void,during-disabled", "void,during-moved", "void,during-orgDisabled",
        "approve,active", "approve,wrong-actor", "approve,blank-opinion", "approve,role-revoked", "approve,actor-moved", "approve,actor-disabled")
    fun `loan engine operations recheck current duty and roll back written workflow state`(operation:String,change:String) {
        val pg=System.getProperty("collection.test.pg.config")?.let {file ->
            val settings=java.util.Properties().apply {java.nio.file.Files.newInputStream(Path.of(file)).use(::load)}
            require(settings.getProperty("url").startsWith("jdbc:postgresql://127.0.0.1:") && !settings.getProperty("url").endsWith("/collection_db"))
            org.postgresql.ds.PGSimpleDataSource().apply {setURL(settings.getProperty("url"));user=settings.getProperty("user");password=settings.getProperty("password")}
        }
        val schema="loan_engine_"+java.util.UUID.randomUUID().toString().replace("-","")
        pg?.connection?.use {it.createStatement().use {sql->sql.execute("CREATE SCHEMA $schema")}}
        pg?.currentSchema=schema
        val manager=SpringTransactionManager(pg ?: SQLiteDataSource().apply {url="jdbc:sqlite:${directory.resolve("loan-engine.db")}"})
        val tx=TransactionTemplate(manager)
        val scope=CollectionScopeResolver("multi","source")
        try {StaticApplicationContext().use {context ->
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.refresh();SpringContextHolder().setApplicationContext(context)
            val binder=mock(BusinessAttachmentBinder::class.java)
            val archives=CollectionPermanentArchiveService(binder,scope)
            val statutory=spy(LoanStatutoryService(scope,CollectionResponsibleUserService(CollectionActorDirectory(scope),scope),
                binder,archives,CollectionCalendarService(scope,binder,archives),mock(CollectionArchivePacketService::class.java)))
            val loader=FlowGraphLoader(32,5)
            val definitions=FlowDefinitionService().also {it.graphLoader=loader}
            lateinit var service:LoanService
            val engine=FlowEngine(FlowAssigneeResolver(emptyList()),ApplicationEventPublisher {event ->
                if(event is FlowCanceledEvent)service.onCanceled(event)
                if(event is FlowTaskCompletedEvent)service.onTaskCompleted(event)
                if(event is FlowCompletedEvent)service.onCompleted(event)
            },context.beanFactory.getBeanProvider(SubprocessStarter::class.java),loader)
            val locks=CollectionOperationLockService()
            val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,
                mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),
                mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
            service=LoanService(FlowInstanceService(definitions,engine),scope,collections,binder,
                mock(CollectionReturnService::class.java),CollectionBusinessItemService(),locks,statutory,archives)
            tx.executeWithoutResult {
                actorSchema()
                SchemaUtils.create(CollectionLedgerLogs,Collections,CollectionOrgs,Loans,LoanComplianceRecords,CollectionBusinessItems,
                    CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                    FlowDefinitions,FlowNodeDefs,FlowEdgeDefs,FlowInstances,FlowTasks,FlowCCs,FlowInstanceLogs)
                for(id in listOf("source","outside")) {
                    GroupDao.new(id){name=id;type='1';parentId="0";parentIds="0,"}
                    CollectionOrgDao.new(id){name=id;code=id}
                }
                originalReader("operator",CollectionConst.STORAGE,"source")
                originalReader("keeper",CollectionConst.SELECT,"source");actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
                originalReader("director",CollectionConst.SELECT,"source");actorRole("director",CollectionConst.ROLE_DIRECTOR)
                val item=CollectionDao.new("object"){orgId="source";totalRegNo="001";name="合成藏品";lifeStatus=EnumCollectionStatus.IN_STORE}
                val at=LocalDateTime.of(2026,9,1,10,0)
                val record=LoanDao.new("request") {
                    orgId="source";title="合成借用";collectionIds="object";borrower="合成借用馆";createBy="operator"
                    dossier=LoanDossierVo(borrowerKind=EnumLoanBorrowerKind.STATE_MUSEUM,borrowerRegistrationNo="synthetic",
                        borrowerIdentityFileIds="stub",purpose=EnumLoanPurpose.EXHIBITION,purposeDescription="合成",
                        keeperUserId="keeper",leaderUserId="director",supervisionUserId="operator",agreedOutAt=at,agreedReturnAt=at.plusMonths(1),
                        collectiveDecisionAt=at.minusDays(2),collectiveDecisionFileIds="stub",agreementSignedAt=at.minusDays(1),
                        signedAgreementFileIds="stub",agreedCollectionListFileIds="stub",safetyResponsibilities="stub",transportRequirements="stub",
                        storageRequirements="stub",useSite="stub",carrierName="stub",carrierCapabilityFileIds="stub",transportSupervisorName="stub",
                        transportSupervisorOrganization="stub",transportAssignmentFileIds="stub",firstInspectionOn=at.toLocalDate(),
                        inspectionIntervalDays=1,inspectionPlanFileIds="stub",exhibitionOverseas=false)
                }
                doReturn(listOf(item)).`when`(statutory).requirePrepared(any(LoanDao::class.java) ?: record,anySet())
                doNothing().`when`(statutory).requireAdministrativeBeforeOut(any(LoanDao::class.java) ?: record,any(LocalDateTime::class.java) ?: at)
                doReturn("").`when`(statutory).allFiles(any(LoanDao::class.java) ?: record)
                val definition=definitions.create(FlowDefinitionVo(name="隔离借用权限验证",key=CollectionConst.FLOW_LOAN),"author")
                val json=javaClass.getResourceAsStream("/flow/collection_loan_2026.json")!!.bufferedReader().use {it.readText()}
                definitions.saveModel(definition.id.value,JsonUtils.mapper.readTree(json).path("model").toString(),"author")
                definitions.publish(definition.id.value,"author")
            }
            val login=tx.execute {CollectionActorDirectory(scope).requireActive("operator")}!!
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
            if(operation!="submit")tx.executeWithoutResult {service.submit("request")}
            val during=change.startsWith("during-")
            val fault=change.removePrefix("during-")
            fun mutate() {
                when(fault) {
                    "revoked"->RoleUsers.deleteWhere {userId eq "operator"}
                    "disabled"->UserDao["operator"].enabled=false
                    "moved"->UserDao["operator"].groupId="outside"
                    "orgDisabled"->CollectionOrgDao["source"].orgStatus=EnumOrgStatus.DISABLED
                }
            }
            if(during)tx.executeWithoutResult {
                val sql=when(fault) {
                    "revoked"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='operator'"
                    "disabled"->"UPDATE ${Users.tableName} SET enabled=${if(pg!=null)"false" else "0"} WHERE id='operator'"
                    "moved"->"UPDATE ${Users.tableName} SET group_id='outside' WHERE id='operator'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='source'"
                }
                val table=if(operation=="submit")FlowTasks.tableName else FlowInstances.tableName
                val event=if(operation=="submit")"AFTER INSERT" else "AFTER UPDATE"
                val condition=if(operation=="submit")"" else " WHEN (NEW.status='CANCELED')"
                val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                if(pg!=null) {
                    current.exec("CREATE FUNCTION loan_engine_fault_fn() RETURNS TRIGGER LANGUAGE plpgsql AS ${"$"}fault${"$"} BEGIN $sql; RETURN NEW; END ${"$"}fault${"$"}")
                    current.exec("CREATE TRIGGER loan_engine_fault $event ON $table FOR EACH ROW$condition EXECUTE FUNCTION loan_engine_fault_fn()")
                } else current.exec("CREATE TRIGGER loan_engine_fault $event ON $table$condition BEGIN $sql; END")
            } else tx.executeWithoutResult {mutate()}
            fun snapshot()=tx.execute {
                listOf(Loans,Collections,CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                    FlowInstances,FlowTasks,FlowCCs,FlowInstanceLogs,Users,RoleUsers,CollectionOrgs).map {table ->
                    table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()
                }
            }
            if(operation=="approve") {
                for((node,actor,role) in listOf(Triple("keeper","keeper",CollectionConst.ROLE_STORAGE_HEAD),
                    Triple("leader","director",CollectionConst.ROLE_DIRECTOR))) {
                    val taskId=tx.execute {
                        val task=FlowTaskDao.all().single {it.status=="PENDING"}
                        assertEquals(node,task.nodeKey);assertEquals(actor,task.assignee);task.id.value
                    }!!
                    if(change!="active") {
                        tx.executeWithoutResult {
                            when(change) {
                                "role-revoked"->RoleUsers.deleteWhere {userId eq actor}
                                "actor-moved"->UserDao[actor].groupId="outside"
                                "actor-disabled"->UserDao[actor].enabled=false
                            }
                        }
                        val prior=snapshot()
                        assertThrows(RuntimeException::class.java){tx.executeWithoutResult {
                            engine.complete(FlowTaskDao[taskId],if(change=="wrong-actor")"operator" else actor,actor,
                                "APPROVE",if(change=="blank-opinion")" " else "本人核对合成材料")
                        }}
                        assertEquals(prior,snapshot())
                        tx.executeWithoutResult {
                            UserDao[actor].groupId="source";UserDao[actor].enabled=true
                            if(change=="role-revoked")actorRole(actor,role)
                        }
                    }
                    tx.executeWithoutResult {engine.complete(FlowTaskDao[taskId],actor,actor,"APPROVE","本人核对合成材料")}
                }
                tx.executeWithoutResult {
                    val row=LoanDao["request"]
                    assertEquals(EnumFlowStatus.PASS,row.flowStatus)
                    assertEquals("COMPLETED",FlowInstanceDao[row.flowInstanceId!!].status)
                    assertTrue(FlowTaskDao.all().none {it.status=="PENDING"})
                    assertFalse(row.outExecuted)
                    assertEquals(EnumCollectionStatus.PRE_OUT,CollectionDao["object"].lifeStatus)
                    assertEquals(1L,CollectionOperationLockDao.count())
                    assertEquals(2L,CollectionPermanentArchiveDao.count())
                    val approvals=FlowInstanceLogDao.all().filter {it.action=="APPROVE"}.sortedBy {it.createTime}
                    assertEquals(listOf("keeper","leader"),approvals.map {it.nodeKey})
                    assertEquals(listOf("keeper","director"),approvals.map {it.operator})
                    assertNotNull(statutory.requireCurrentApproval(row))
                }
                return@use
            }
            val before=snapshot()
            fun perform()=tx.executeWithoutResult {when(operation) {
                "submit"->service.submit("request")
                "cancel"->service.cancel("request")
                else->service.void("request")
            }}
            if(change!="active") {
                assertThrows(BadRequestException::class.java){perform()}
                assertEquals(before,snapshot(),"拒绝后业务、流程、待办、日志、锁及档案保持不变")
                tx.executeWithoutResult {
                    if(during) {
                        val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                        val table=if(operation=="submit")FlowTasks.tableName else FlowInstances.tableName
                        current.exec("DROP TRIGGER loan_engine_fault"+if(pg!=null)" ON $table" else "")
                        if(pg!=null)current.exec("DROP FUNCTION loan_engine_fault_fn()")
                    } else {
                        UserDao["operator"].enabled=true;UserDao["operator"].groupId="source"
                        CollectionOrgDao["source"].orgStatus=EnumOrgStatus.ENABLED
                        if(fault=="revoked")actorRole("operator","FILE_operator",setOf(CollectionConst.STORAGE))
                    }
                }
            }
            perform()
            tx.executeWithoutResult {
                val row=LoanDao["request"]
                assertEquals(when(operation){"submit"->EnumFlowStatus.AUDIT;"cancel"->EnumFlowStatus.CANCEL;else->EnumFlowStatus.VOID},row.flowStatus)
                val instance=FlowInstanceDao[row.flowInstanceId!!]
                if(operation=="submit") {
                    assertEquals("RUNNING",instance.status)
                    val task=FlowTaskDao.all().single {it.status=="PENDING"}
                    assertEquals("keeper",task.nodeKey);assertEquals("keeper",task.assignee)
                    assertEquals(1L,CollectionOperationLockDao.count())
                } else {
                    assertEquals("CANCELED",instance.status);assertTrue(FlowTaskDao.all().none {it.status=="PENDING"})
                    assertEquals(0L,CollectionOperationLockDao.count())
                }
                assertFalse(row.outExecuted);assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["object"].lifeStatus)
                assertEquals(1L,CollectionPermanentArchiveDao.count());assertTrue(FlowInstanceLogDao.count()>0)
            }
        }} finally {
            SecurityContextHolder.clearContext()
            pg?.connection?.use {it.createStatement().use {sql->sql.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
}
