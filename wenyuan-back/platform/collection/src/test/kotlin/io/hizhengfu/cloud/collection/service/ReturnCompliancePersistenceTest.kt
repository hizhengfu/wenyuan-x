package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.domain.FlowCanceledEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import io.hizhengfu.cloud.flow.domain.FlowJumpedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.RoleUsers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource
import org.sqlite.SQLiteDataSource

class ReturnCompliancePersistenceTest {
    @TempDir lateinit var originalRoot: Path
    private lateinit var fixtureDataSource: DataSource
    private val scope=CollectionScopeResolver("single","museum")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val operationOriginalRoot=createSafetyOriginalRoot().also {root->
        java.nio.file.Files.createDirectories(root.resolve("files"))
        java.nio.file.Files.write(root.resolve("files/report.pdf"),ByteArray(100){it.toByte()})
    }
    private val operationArchives=CollectionPermanentArchiveService(binder,scope,
        LocalFileObjectStorage(operationOriginalRoot.toString()),unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val incidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService(),listOf(IncidentWorkGuard(operationArchives),IncidentRelocationGuard(operationArchives)))
    private val statutory=ReturnStatutoryService(scope,users,binder,archives,incidents)
    private val locks=CollectionOperationLockService(listOf(CollectionIncidentOperationGuard(),IncidentWorkGuard(operationArchives),IncidentRelocationGuard(operationArchives)))
    private val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
    private val items=CollectionBusinessItemService()
    private val service=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collections,mock(CollectionUserResolver::class.java),binder,items,locks,mock(CollectionPlacementService::class.java),statutory,archives)
    private val out=LocalDateTime.of(2026,6,1,9,0)
    private val approved=LocalDateTime.of(2026,5,25,9,0)
    private val at=LocalDateTime.of(2026,8,1,9,0)
    @BeforeEach fun prepare() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val user=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();removeSafetyOriginalRoot(operationOriginalRoot)}
    private fun dossier(kind:EnumLoanBorrowerKind=EnumLoanBorrowerKind.STATE_MUSEUM)=LoanDossierVo(
        exhibitionOverseas=false,borrowerKind=kind,borrowerRegistrationNo="borrower-reg",borrowerIdentityFileIds="identity",purpose=EnumLoanPurpose.EXHIBITION,purposeDescription="主题公益展览",
        keeperUserId="keeper",leaderUserId="leader",supervisionUserId="custodian1",agreedOutAt=out,agreedReturnAt=out.plusMonths(2),
        collectiveDecisionAt=out.minusMonths(2),collectiveDecisionFileIds="minutes",collectiveParticipantNames=setOf("甲","乙"),agreementSignedAt=out.minusMonths(2).plusDays(1),signedAgreementFileIds="agreement",agreedCollectionListFileIds="list",
        safetyResponsibilities="借入方安全主责，出借方监督",transportRequirements="专用囊匣全程监测",storageRequirements="恒温恒湿定岗安全保卫",useSite="借入馆第一展厅",carrierName="博物馆运输组",carrierCapabilityFileIds="carrier",transportSupervisorName="运输专员",transportSupervisorOrganization="出借馆",transportAssignmentFileIds="assignment",firstInspectionOn=out.toLocalDate().plusDays(9),inspectionIntervalDays=15,inspectionPlanFileIds="plan",
        assessments=listOf("one","two").map { LoanAssessmentVo(it,EnumLoanObjectKind.RELIC,"状态稳定",false,false,"展览必要", "包装运输风险可控","assessment") },
    )
    private fun fixture(afterCommit: ((Database, String, String) -> Unit)? = null, block:(LoanDao,CollectionReturnDao)->Unit) {
        val pgConfig=System.getProperty("collection.test.pg.config")
        val schema="return_responsibility_"+UUID.randomUUID().toString().replace("-","")
        val postgres=pgConfig?.let {config->
            val properties=Properties().apply {Path.of(config).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password")}.also {source->
                source.connection.use {connection->connection.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
                source.currentSchema=schema
            }
        }
        val databaseFile=if(postgres==null)java.nio.file.Files.createTempFile("return-compliance-", ".db") else null
        fixtureDataSource=postgres ?: SQLiteDataSource().apply {url="jdbc:sqlite:$databaseFile"}
        try {
        val db=Database.connect(fixtureDataSource)
        var returnId=""
        transaction(db) {
            SchemaUtils.create(CollectionLedgerLogs,CollectionPlacements,IncidentRelocations,IncidentRelocationRecords,IncidentWorks,IncidentWorkRecords,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,Collections,CollectionOrgs,Loans,LoanComplianceRecords,Requisitions,CollectionReturns,ReturnComplianceRecords,CollectionBusinessItems,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstanceLogs,Warehouses)
            CollectionOrgDao.new("museum") { name="本馆";code="M";ownership=EnumOwnership.STATE }
            CollectionOrgDao.new("other") { name="外馆";code="O" }
            WarehouseDao.new("warehouse") { orgId="museum";name="原库";sceneType=EnumLocationScene.STORAGE }
            WarehouseDao.new("receiving") { orgId="museum";name="接收保管库";sceneType=EnumLocationScene.STORAGE }
            WarehouseDao.new("foreign-place") { orgId="other";name="外馆库";sceneType=EnumLocationScene.STORAGE }
            listOf("agreement","signed-out","sign","image","authorization","report","explain-keeper","explain-returning","investigation","result","approval","determination","carrier-proof","supervision","packing").forEach { key -> FileDao.new(key) { name="$key.pdf";path="/files/$key.pdf";hash=key;size=100;ext="pdf" } }
            val cols=listOf("one","two").map { key -> CollectionDao.new(key) { orgId="museum";totalRegNo=key;name="藏品$key";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.ON_LOAN;warehouseId="warehouse";preserveState=EnumPreserve.A } }
            val loan=LoanDao.new("loan") {
                orgId="museum";title="借用";borrower="借入馆";protocolNo="XY-001";collectionIds="one,two";loanDate=out.toLocalDate();returnDate=at.toLocalDate();operator="operator";reviewer="reviewer";flowStatus=EnumFlowStatus.PASS;outExecuted=true;outExecutedTime=out
                dossier=dossier();baseline=LoanBaselineVo(EnumOwnership.STATE,cols.map { RequisitionCollectionBaselineVo(it.id.value,CollectionCatalogMapper.fromCollection(it.toModel())) })
                handover=LoanHandoverRecordVo(LoanHandoverVo(approved,"keeper","approval",out,setOf("k1","k2"),setOf("甲","乙"),"authorization",true,cols.map { RequisitionConditionVo(it.id.value,"出库时完整",false) },true,true,"approval","signed-out"),"loan-task","operator",out)
            }
            val id=service.ensureDraftFromLoan(loan.id.value)
            val e=CollectionReturnDao[id]
            service.save(e.toModel().copy(toWarehouseId="receiving"))
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            actorRole("operator","RETURN_OPERATOR",setOf(CollectionConst.STORAGE))
            block(loan,e)
            returnId=e.id.value
        }
        afterCommit?.invoke(db,"loan",returnId)
        } finally {
            if(databaseFile!=null)java.nio.file.Files.deleteIfExists(databaseFile)
            postgres?.connection?.use {connection->connection.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    private fun inspection(ids:List<String> = listOf("one","two"))=ReturnInspectionVo(at,setOf("k1","k2"),emptySet(),setOf("甲","乙"),"authorization",true,true,ids.map { ReturnItemCheckVo(it,EnumLoanObjectKind.RELIC,true,"核对原影像、题铭及细部标志",true,"与出库记录一致",false,null,false,EnumReturnSecurityIncident.NONE) },"sign",transportStandardReference="GB/T 23862-2024",transportStandardApplicability="本批文物归还包装适用范围已核对",transportCarrierName="借入馆运输组",transportCarrierCapabilityFileIds="carrier-proof",transportSupervisorName="借用归还监督专员",transportSupervisionFileIds="supervision",packingStandardConfirmed=true,packingCheckFileIds="packing")
    private fun inspectionArchiveId(e:CollectionReturnDao)=CollectionPermanentArchiveDao.all()
        .filter {it.bizType==CollectionConst.BIZ_RETURN && it.bizId==e.id.value && it.phase.startsWith("INSPECTION_")}
        .maxWithOrNull(compareBy<CollectionPermanentArchiveDao>({it.createTime},{it.id.value}))!!.id.value
    @Test fun `damaged sealed inspection bytes cannot enter or reenter return approval`()=fixture { _,e ->
        FileDao.all().forEach { file ->
            val local=originalRoot.resolve("files/${file.id.value}.pdf")
            java.nio.file.Files.createDirectories(local.parent)
            java.nio.file.Files.writeString(local,"ORIGINAL")
        }
        val guardedArchives=CollectionPermanentArchiveService(binder,scope,
            LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val guarded=ReturnStatutoryService(scope,users,binder,guardedArchives,incidents)
        val guardedService=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collections,
            mock(CollectionUserResolver::class.java),binder,items,locks,mock(CollectionPlacementService::class.java),guarded,guardedArchives)
        guarded.recordInspection(e.id.value,inspection())
        val file=originalRoot.resolve("files/sign.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(file)
        java.nio.file.Files.writeString(file,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        val failure=assertThrows(BadRequestException::class.java) {guardedService.submit(e.id.value)}
        assertTrue(failure.message!!.contains("原件"),failure.message)
        assertEquals(EnumFlowStatus.DRAFT,e.flowStatus)
        assertEquals(0,CollectionPermanentArchiveDao.all().count {it.phase=="SUBMITTED"})
        val before=listOf(e.flowStatus,e.flowInstanceId,e.updateTime,e.updateBy)
        val reactivated=FlowReactivatedEvent("flow",CollectionConst.FLOW_RETURN,"归还",e.id.value,
            CollectionConst.BIZ_RETURN,"CANCELED","keeper","保管确认","核对原件","operator","经办","operator","经办",emptyMap())
        val jumped=FlowJumpedEvent("flow",CollectionConst.FLOW_RETURN,"归还",e.id.value,
            CollectionConst.BIZ_RETURN,listOf("start"),"keeper","保管确认","核对原件","operator","经办","operator","经办",emptyMap())
        for (reentry in listOf<() -> Unit>({guardedService.onFlowReactivated(e,reactivated)},
                {guardedService.onFlowJumped(e,jumped)})) {
            val rejected=assertThrows(BadRequestException::class.java) {reentry()}
            assertTrue(rejected.message!!.contains("原件"),rejected.message)
            assertEquals(before,listOf(e.flowStatus,e.flowInstanceId,e.updateTime,e.updateBy))
        }
    }
    @Test fun `damaged sealed inspection bytes cannot authorize a new return report`()=fixture { _,e ->
        FileDao.all().forEach { file ->
            val local=originalRoot.resolve("files/${file.id.value}.pdf")
            java.nio.file.Files.createDirectories(local.parent)
            java.nio.file.Files.writeString(local,"ORIGINAL")
        }
        val guardedArchives=CollectionPermanentArchiveService(binder,scope,
            LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val guarded=ReturnStatutoryService(scope,users,binder,guardedArchives,incidents)
        guarded.recordInspection(e.id.value,inspection().copy(items=inspection().items.map {
            if(it.collectionId=="one")it.copy(damaged=true,anomalyDescription="新增损伤",protectiveMeasures="隔离保管") else it
        }))
        val archiveId=inspectionArchiveId(e)
        val file=originalRoot.resolve("files/sign.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(file)
        java.nio.file.Files.writeString(file,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        val archiveCount=CollectionPermanentArchiveDao.count()
        val reportCount=ReturnComplianceRecordDao.count()
        assertThrows(BadRequestException::class.java) {
            guarded.recordReport(e.id.value,report("one",EnumReturnReportTarget.PROVINCE),archiveId)
        }
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
        assertEquals(reportCount,ReturnComplianceRecordDao.count())
        java.nio.file.Files.writeString(file,"ORIGINAL")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        guarded.recordReport(e.id.value,report("one",EnumReturnReportTarget.PROVINCE),archiveId)
        assertEquals(reportCount+1,ReturnComplianceRecordDao.count())
    }
    @Test fun `old report form cannot append after inspection original changes`()=fixture { _,e ->
        val first=inspection().copy(items=inspection().items.map {if(it.collectionId=="one")it.copy(damaged=true,anomalyDescription="旧点交发现损伤",protectiveMeasures="隔离暂存")else it})
        statutory.recordInspection(e.id.value,first)
        val opened=inspectionArchiveId(e)
        statutory.recordInspection(e.id.value,first.copy(expectedInspectionArchiveId=opened,replacementReason="补充实查结果",
            items=first.items.map {if(it.collectionId=="one")it.copy(condition="已复核新增裂隙",anomalyDescription="复核后新增裂隙")else it}))
        val current=inspectionArchiveId(e)
        assertNotEquals(opened,current)
        val archivesBefore=CollectionPermanentArchiveDao.count()
        val reportsBefore=ReturnComplianceRecordDao.count()
        val report=report("one",EnumReturnReportTarget.PROVINCE)
        assertThrows(BadRequestException::class.java) {statutory.recordReport(e.id.value,report,opened)}
        assertThrows(BadRequestException::class.java) {statutory.recordReport(e.id.value,report,null)}
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertEquals(reportsBefore,ReturnComplianceRecordDao.count())
        statutory.recordReport(e.id.value,report,current)
        assertEquals(reportsBefore+1,ReturnComplianceRecordDao.count())
        assertEquals(archivesBefore+1,CollectionPermanentArchiveDao.count())
    }
    @Test fun `report refuses a matching inspection version when sealed custody has drifted`()=fixture { _,e ->
        val abnormal=inspection().copy(items=inspection().items.map { item ->
            if(item.collectionId=="one") item.copy(damaged=true,anomalyDescription="点交发现损伤",protectiveMeasures="隔离暂存") else item
        })
        statutory.recordInspection(e.id.value,abnormal)
        val opened=inspectionArchiveId(e)
        val archiveCount=CollectionPermanentArchiveDao.count()
        val reportCount=ReturnComplianceRecordDao.count()
        val report=report("one",EnumReturnReportTarget.PROVINCE)
        e.toWarehouseId="warehouse"
        assertThrows(BadRequestException::class.java) { statutory.recordReport(e.id.value,report,opened) }
        e.toWarehouseId="receiving"
        e.sourceBizId="missing"
        assertThrows(BadRequestException::class.java) { statutory.recordReport(e.id.value,report,opened) }
        e.sourceBizId="loan"
        e.collectionIds="one"
        assertThrows(BadRequestException::class.java) { statutory.recordReport(e.id.value,report,opened) }
        e.collectionIds="one,two"
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
        assertEquals(reportCount,ReturnComplianceRecordDao.count())
        statutory.recordReport(e.id.value,report,opened)
        assertEquals(reportCount+1,ReturnComplianceRecordDao.count())
    }
    @Test fun `report refuses an inspection original with a missing file reference`()=fixture { _,e ->
        val abnormal=inspection().copy(items=inspection().items.map { item ->
            if(item.collectionId=="one") item.copy(damaged=true,anomalyDescription="点交发现损伤",protectiveMeasures="隔离暂存") else item
        })
        statutory.recordInspection(e.id.value,abnormal)
        val opened=inspectionArchiveId(e)
        val archiveCount=CollectionPermanentArchiveDao.count()
        val reportCount=ReturnComplianceRecordDao.count()
        CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq opened}.first().delete()
        assertThrows(BadRequestException::class.java) {
            statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.PROVINCE),opened)
        }
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
        assertEquals(reportCount,ReturnComplianceRecordDao.count())
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["inspection","report"])
    fun `return evidence writes reject current duty revoked after login`(operation:String)=fixture { _,e ->
        if(operation=="report") {
            val abnormal=inspection().copy(items=inspection().items.map {item ->
                if(item.collectionId=="one") item.copy(damaged=true,anomalyDescription="发现新损伤",protectiveMeasures="隔离暂存") else item
            })
            statutory.recordInspection(e.id.value,abnormal)
        }
        val inspectionBefore=e.inspection
        val archiveCount=CollectionPermanentArchiveDao.count()
        val recordCount=ReturnComplianceRecordDao.count()
        assertEquals(1,RoleUsers.deleteWhere {(RoleUsers.userId eq "operator") and (RoleUsers.roleId eq "role:RETURN_OPERATOR")})
        val failure=assertThrows(BadRequestException::class.java) {
            if(operation=="inspection") statutory.recordInspection(e.id.value,inspection())
            else statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.COMPETENT),inspectionArchiveId(e))
        }
        assertTrue(failure.message!!.contains("当前账号"))
        assertEquals(inspectionBefore,e.inspection)
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
        assertEquals(recordCount,ReturnComplianceRecordDao.count())
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["inspection","report"])
    fun `return evidence write rolls back when duty revoked after initial check`(operation:String) {
        fixture(afterCommit={db,_,returnId ->
            val before=transaction(db) { listOf(CollectionReturns,ReturnComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles).map {table ->
                table.selectAll().map {row -> table.columns.map {row[it]}.toString()}.sorted()
            } }
            val entered=CountDownLatch(1)
            val release=CountDownLatch(1)
            val pauseFile=if(operation=="inspection")"sign" else "report"
            `when`(binder.resolveSingle(pauseFile)).thenAnswer { call ->
                entered.countDown()
                check(release.await(15,TimeUnit.SECONDS)) { "归还原件写入等待超时" }
                call.getArgument<String>(0)
            }
            val authentication=SecurityContextHolder.getContext().authentication
            val executor=Executors.newSingleThreadExecutor()
            val writing=executor.submit<Throwable?> {
                SecurityContextHolder.getContext().authentication=authentication
                try {
                    transaction(db) {
                        if(operation=="inspection")statutory.recordInspection(returnId,inspection())
                        else statutory.recordReport(returnId,report("one",EnumReturnReportTarget.COMPETENT),inspectionArchiveId(CollectionReturnDao[returnId]))
                    }
                    null
                } catch (failure:Throwable) { failure }
                finally { SecurityContextHolder.clearContext() }
            }
            try {
                assertTrue(entered.await(15,TimeUnit.SECONDS),"写入未到达原件准备阶段")
                transaction(db) {
                    assertEquals(1,RoleUsers.deleteWhere {(RoleUsers.userId eq "operator") and (RoleUsers.roleId eq "role:RETURN_OPERATOR")})
                }
                release.countDown()
                val failure=writing.get(15,TimeUnit.SECONDS)
                assertTrue(failure is BadRequestException,"预期当前职责拒绝，实际为 $failure")
                assertTrue(failure!!.message!!.contains("当前账号"))
                transaction(db) {
                    val after=listOf(CollectionReturns,ReturnComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles).map {table ->
                        table.selectAll().map {row -> table.columns.map {row[it]}.toString()}.sorted()
                    }
                    assertEquals(before,after,"归还事实、报告及永久档案必须一起回滚")
                    assertEquals(0,RoleUsers.selectAll().where {(RoleUsers.userId eq "operator") and (RoleUsers.roleId eq "role:RETURN_OPERATOR")}.count())
                }
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))
            }
        }) { _,e ->
            if(operation=="report") {
                val abnormal=inspection().copy(items=inspection().items.map {item ->
                    if(item.collectionId=="one") item.copy(damaged=true,anomalyDescription="发现新损伤",protectiveMeasures="隔离暂存") else item
                })
                statutory.recordInspection(e.id.value,abnormal)
            }
        }
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["operator","keeper","leader"])
    fun `return resolution rejects duty revoked during original preparation`(revoked:String) {
        var incidentId=""
        fixture(afterCommit={db,loanId,returnId ->
            val currentUsers=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
            val currentStatutory=ReturnStatutoryService(scope,currentUsers,binder,archives,incidents)
            val currentService=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collections,
                mock(CollectionUserResolver::class.java),binder,items,locks,mock(CollectionPlacementService::class.java),currentStatutory,archives)
            val tables=listOf(CollectionReturns,ReturnComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Collections,Loans,CollectionOperationLocks,StorageMovements,CollectionLedgerLogs)
            val before=transaction(db) { tables.map {table -> table.selectAll().map {row -> table.columns.map {row[it]}.toString()}.sorted() } }
            val entered=CountDownLatch(1)
            val release=CountDownLatch(1)
            `when`(binder.resolveSingle("explain-keeper")).thenAnswer { call ->
                entered.countDown()
                check(release.await(15,TimeUnit.SECONDS)) { "归还异常处理原件准备等待超时" }
                call.getArgument<String>(0)
            }
            val authentication=SecurityContextHolder.getContext().authentication
            val executor=Executors.newSingleThreadExecutor()
            val writing=executor.submit<Throwable?> {
                SecurityContextHolder.getContext().authentication=authentication
                try {
                    transaction(db) { currentService.resolveIssue(returnId,resolution("one").copy(incidentId=incidentId)) }
                    null
                } catch (failure:Throwable) { failure }
                finally { SecurityContextHolder.clearContext() }
            }
            try {
                assertTrue(entered.await(15,TimeUnit.SECONDS),"异常处理未到达原件准备阶段")
                val role=when(revoked) {
                    "operator" -> "role:RETURN_OPERATOR"
                    "keeper" -> "role:${CollectionConst.ROLE_STORAGE_HEAD}"
                    else -> "role:${CollectionConst.ROLE_DIRECTOR}"
                }
                transaction(db) {
                    assertEquals(1,RoleUsers.deleteWhere {(RoleUsers.userId eq revoked) and (RoleUsers.roleId eq role)})
                }
                release.countDown()
                val failure=writing.get(15,TimeUnit.SECONDS)
                assertTrue(failure is BadRequestException,"$revoked 职责撤销后须拒绝，实际为 $failure")
                transaction(db) {
                    val after=tables.map {table -> table.selectAll().map {row -> table.columns.map {row[it]}.toString()}.sorted() }
                    assertEquals(before,after,"异常调查、状态、来源及永久档案须一起回滚")
                    assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
                    assertFalse(LoanDao[loanId].returned)
                }
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))
            }
        }) { _,e ->
            receiveDamaged(e)
            statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.PROVINCE),inspectionArchiveId(e))
            statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.NATIONAL,EnumReturnReportKind.RESULT_FILING),inspectionArchiveId(e))
            incidentId=closedIncident(e)
            actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
            actorRole("leader",CollectionConst.ROLE_DIRECTOR)
        }
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans=[true,false])
    fun `inspection correction rechecks archive version after row lock wait`(commitFirst:Boolean) {
        fixture(afterCommit={db,_,returnId ->
            val currentArchive=transaction(db) { CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq returnId)
            }.filter {it.phase.startsWith("INSPECTION_")}.single().id.value }
            val firstValue=inspection().copy(items=inspection().items.map {it.copy(condition="先提交的补正")},
                replacementReason="第一位经办补正",expectedInspectionArchiveId=currentArchive)
            val secondValue=inspection().copy(items=inspection().items.map {it.copy(condition="等待中的补正")},
                replacementReason="第二位经办补正",expectedInspectionArchiveId=currentArchive)
            val marker="return-version-"+UUID.randomUUID().toString().take(12)
            val entered=CountDownLatch(1)
            val release=CountDownLatch(1)
            val authentication=SecurityContextHolder.getContext().authentication
            val executor=Executors.newFixedThreadPool(2)
            fun write(name:String,value:ReturnInspectionVo,hold:Boolean)=executor.submit<Throwable?> {
                SecurityContextHolder.getContext().authentication=authentication
                try {
                    transaction(db) {
                        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SET application_name='$marker-$name'")
                        statutory.recordInspection(returnId,value)
                        if(hold) {
                            entered.countDown()
                            check(release.await(15,TimeUnit.SECONDS)) { "归还版本锁等待超时" }
                            if(!commitFirst)error("first correction rollback")
                        }
                    }
                    null
                } catch(failure:Throwable) {failure}
                finally {SecurityContextHolder.clearContext()}
            }
            val first=write("first",firstValue,true)
            try {
                assertTrue(entered.await(15,TimeUnit.SECONDS),"第一事务未形成待提交点交补正")
                val second=write("second",secondValue,false)
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
                var waiting=false
                while(!waiting && System.nanoTime()<deadline) {
                    if(second.isDone)fail<Unit>("第二事务未等待原归还单行锁：${second.get()}")
                    waiting=fixtureDataSource.connection.use {connection ->
                        connection.prepareStatement("""
                            SELECT EXISTS (
                                SELECT 1 FROM pg_stat_activity waiting
                                JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(waiting.pid))
                                WHERE waiting.application_name = ? AND blocking.application_name = ?
                                  AND waiting.wait_event_type = 'Lock'
                            )
                        """.trimIndent()).use {statement ->
                            statement.setString(1,"$marker-second")
                            statement.setString(2,"$marker-first")
                            statement.executeQuery().use {result -> result.next();result.getBoolean(1)}
                        }
                    }
                    if(!waiting)Thread.sleep(25)
                }
                assertTrue(waiting,"第二事务未进入 PostgreSQL 实际行锁等待")
                release.countDown()
                val firstFailure=first.get(15,TimeUnit.SECONDS)
                val secondFailure=second.get(15,TimeUnit.SECONDS)
                if(commitFirst) {
                    assertNull(firstFailure)
                    assertTrue(secondFailure is BadRequestException,"旧版本应拒绝：$secondFailure")
                    assertTrue(secondFailure!!.message!!.contains("版本"))
                } else {
                    assertEquals("first correction rollback",firstFailure?.message)
                    assertNull(secondFailure,"先行事务回滚后原版本仍可办理")
                }
                transaction(db) {
                    assertEquals(if(commitFirst)firstValue else secondValue,CollectionReturnDao[returnId].inspection)
                    assertEquals(2,CollectionPermanentArchiveDao.find {
                        (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq returnId)
                    }.count {it.phase.startsWith("INSPECTION_")})
                }
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))
            }
        }) { _,e ->statutory.recordInspection(e.id.value,inspection()) }
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans=[true,false])
    fun `report rechecks inspection original after row lock wait`(commitCorrection:Boolean) {
        fixture(afterCommit={db,_,returnId ->
            val opened=transaction(db) { inspectionArchiveId(CollectionReturnDao[returnId]) }
            val corrected=transaction(db) { CollectionReturnDao[returnId].inspection!!.copy(
                replacementReason="另一经办已核对归还差异",expectedInspectionArchiveId=opened,
                items=CollectionReturnDao[returnId].inspection!!.items.map {if(it.collectionId=="one")it.copy(condition="新版实际裂隙记录")else it}) }
            val marker="return-report-version-"+UUID.randomUUID().toString().take(12)
            val entered=CountDownLatch(1)
            val release=CountDownLatch(1)
            val authentication=SecurityContextHolder.getContext().authentication
            val executor=Executors.newFixedThreadPool(2)
            val first=executor.submit<Throwable?> {
                SecurityContextHolder.getContext().authentication=authentication
                try {
                    transaction(db) {
                        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SET application_name='$marker-first'")
                        statutory.recordInspection(returnId,corrected)
                        entered.countDown()
                        check(release.await(15,TimeUnit.SECONDS)) {"归还点交补正等待超时"}
                        if(!commitCorrection)error("first correction rollback")
                    }
                    null
                } catch(failure:Throwable) {failure}
                finally {SecurityContextHolder.clearContext()}
            }
            try {
                assertTrue(entered.await(15,TimeUnit.SECONDS),"第一事务未形成待提交点交补正")
                val second=executor.submit<Throwable?> {
                    SecurityContextHolder.getContext().authentication=authentication
                    try {
                        transaction(db) {
                            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SET application_name='$marker-second'")
                            statutory.recordReport(returnId,report("one",EnumReturnReportTarget.PROVINCE),opened)
                        }
                        null
                    } catch(failure:Throwable) {failure}
                    finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
                var waiting=false
                while(!waiting && System.nanoTime()<deadline) {
                    if(second.isDone)fail<Unit>("旧报告未等待原归还单行锁：${second.get()}")
                    waiting=fixtureDataSource.connection.use {connection ->
                        connection.prepareStatement("""
                            SELECT EXISTS (
                                SELECT 1 FROM pg_stat_activity waiting
                                JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(waiting.pid))
                                WHERE waiting.application_name = ? AND blocking.application_name = ?
                                  AND waiting.wait_event_type = 'Lock'
                            )
                        """.trimIndent()).use {statement ->
                            statement.setString(1,"$marker-second")
                            statement.setString(2,"$marker-first")
                            statement.executeQuery().use {result -> result.next();result.getBoolean(1)}
                        }
                    }
                    if(!waiting)Thread.sleep(25)
                }
                assertTrue(waiting,"旧报告须实际等待 PostgreSQL 归还单行锁")
                release.countDown()
                val firstFailure=first.get(15,TimeUnit.SECONDS)
                val secondFailure=second.get(15,TimeUnit.SECONDS)
                if(commitCorrection) {
                    assertNull(firstFailure)
                    assertTrue(secondFailure is BadRequestException,"点交补正后旧报告应拒绝：$secondFailure")
                    assertTrue(secondFailure!!.message!!.contains("版本"))
                } else {
                    assertEquals("first correction rollback",firstFailure?.message)
                    assertNull(secondFailure,"点交补正回滚后旧报告应成功")
                }
                transaction(db) {
                    val e=CollectionReturnDao[returnId]
                    if(commitCorrection) {
                        assertEquals(corrected,e.inspection)
                        assertNotEquals(opened,inspectionArchiveId(e))
                    } else assertEquals(opened,inspectionArchiveId(e))
                    assertEquals(if(commitCorrection)0L else 1L,ReturnComplianceRecordDao.count())
                    assertEquals(if(commitCorrection)2 else 1,CollectionPermanentArchiveDao.all().count {it.phase.startsWith("INSPECTION_")})
                    assertEquals(if(commitCorrection)0 else 1,CollectionPermanentArchiveDao.all().count {it.phase.startsWith("REPORT_")})
                }
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))
            }
        }) { _,e ->
            val abnormal=inspection().copy(items=inspection().items.map {if(it.collectionId=="one")it.copy(damaged=true,anomalyDescription="实际损伤",protectiveMeasures="隔离暂存")else it})
            statutory.recordInspection(e.id.value,abnormal)
        }
    }
    @Test fun `loan return transport requires standard carrier supervision and signed packing proof`()=fixture { _,e ->
        val complete=inspection()
        for (invalid in listOf(
            complete.copy(transportStandardReference=null),
            complete.copy(transportCarrierCapabilityFileIds=null),
            complete.copy(transportSupervisorName=null),
            complete.copy(transportSupervisionFileIds=null),
            complete.copy(packingStandardConfirmed=false),
            complete.copy(packingCheckFileIds=null),
        )) {
            assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,invalid) }
            assertNull(e.inspection)
        }
        statutory.recordInspection(e.id.value,complete)
        assertTrue(archives.list(CollectionConst.BIZ_RETURN,e.id.value).any { it["phase"].toString().startsWith("INSPECTION_") && it["files"].toString().contains("packing") })
    }
    private fun fossilInspection()=inspection().copy(items=inspection().items.map {if(it.collectionId=="one")it.copy(objectKind=EnumLoanObjectKind.SCIENTIFIC_FOSSIL)else it})
    @Test fun `already lent historical fossil returns under original kind without rewriting master or source`()=fixture { loan,e ->
        CollectionDao["one"].nature=EnumCollectionNature.OTHER
        loan.dossier=loan.dossier!!.copy(assessments=loan.dossier!!.assessments.map {if(it.collectionId=="one")it.copy(objectKind=EnumLoanObjectKind.SCIENTIFIC_FOSSIL)else it})
        loan.baseline=loan.baseline!!.copy(items=loan.baseline!!.items.map {if(it.collectionId=="one")it.copy(content=it.content.copy(nature=EnumCollectionNature.OTHER))else it})
        val original=JsonUtils.toJson(loan.toModel())
        assertThrows(BadRequestException::class.java){statutory.recordInspection(e.id.value,inspection())}
        statutory.recordInspection(e.id.value,fossilInspection());approve(e);service.executeApprovedReturn(e.id.value)
        assertEquals(EnumCollectionNature.OTHER,CollectionDao["one"].nature)
        assertEquals(EnumLoanObjectKind.SCIENTIFIC_FOSSIL,e.inspection!!.items.first().objectKind)
        assertTrue(loan.returned);assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertEquals(original,JsonUtils.toJson(loan.toModel().copy(returned=false)))
    }
    @Test fun `formally registered scientific fossil cannot return as ordinary other or relic`()=fixture { loan,e ->
        CollectionDao["one"].nature=EnumCollectionNature.SCIENTIFIC_FOSSIL
        loan.dossier=loan.dossier!!.copy(assessments=loan.dossier!!.assessments.map {if(it.collectionId=="one")it.copy(objectKind=EnumLoanObjectKind.SCIENTIFIC_FOSSIL)else it})
        for(kind in listOf(EnumLoanObjectKind.RELIC,EnumLoanObjectKind.OTHER)) {
            val wrong=fossilInspection().copy(items=fossilInspection().items.map {if(it.collectionId=="one")it.copy(objectKind=kind)else it})
            assertThrows(BadRequestException::class.java){statutory.recordInspection(e.id.value,wrong)}
            assertNull(e.inspection)
        }
        statutory.recordInspection(e.id.value,fossilInspection());approve(e);service.executeApprovedReturn(e.id.value)
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    private fun fossilRequisitionReturn(loan:LoanDao, unused:CollectionReturnDao):CollectionReturnDao {
        unused.flowStatus=EnumFlowStatus.CANCEL
        listOf("one","two").forEach {CollectionDao[it].lifeStatus=EnumCollectionStatus.IN_USE}
        CollectionDao["one"].nature=EnumCollectionNature.OTHER
        val req=RequisitionDao.new("fossil-req") {
            orgId="museum";collectionIds="one,two";documentNo="TY-fossil";department="dept";operator="operator";toWarehouseId="receiving";flowStatus=EnumFlowStatus.PASS;outExecuted=true;outExecutedTime=out
            dossier=RequisitionDossierVo("dept","department-head","keeper","leader","use-person","研究","研究必要性","研究室","定岗保卫","agreement",out,at)
            baseline=RequisitionBaselineVo(loan.baseline!!.items.map {if(it.collectionId=="one")it.copy(content=it.content.copy(nature=EnumCollectionNature.OTHER))else it})
            handover=RequisitionHandoverRecordVo(RequisitionHandoverVo(out,approved,"keeper","approval",setOf("k1"),setOf("use-person"),loan.handover!!.content.items,true,true,"approval","signed-out"),"req-task","operator",out)
        }
        return CollectionReturnDao[service.ensureDraftFromRequisition(req.id.value)]
    }
    private fun fossilRequisitionInspection()=fossilInspection().copy(custodianUserIds=setOf("k1"),returningUserIds=setOf("use-person"),returningPersonNames=emptySet())
    @Test fun `unclassified new requisition return cannot manufacture fossil status from a local selection`()=fixture { loan,unused ->
        val e=fossilRequisitionReturn(loan,unused)
        assertThrows(BadRequestException::class.java){statutory.recordInspection(e.id.value,fossilRequisitionInspection())}
        assertNull(e.inspection);assertEquals(0L,CollectionPermanentArchiveDao.count())
    }
    @Test fun `previously sealed scientific requisition inspection remains returnable with original source and kind`()=fixture { loan,unused ->
        val e=fossilRequisitionReturn(loan,unused)
        e.sourceSnapshot=statutory.source(e);e.inspection=fossilRequisitionInspection()
        // 装入旧版本允许的永久原件，模拟升级前状态；新版本接口不能创建这个性质组合。
        archives.seal(e.orgId,CollectionConst.BIZ_RETURN,e.id.value,"INSPECTION_original",statutory.proposal(e),statutory.allFiles(e))
        val original=CollectionPermanentArchiveDao.all().single().snapshot
        val downgraded=fossilRequisitionInspection().copy(replacementReason="改选其他不能替代正式核实",items=fossilRequisitionInspection().items.map {if(it.collectionId=="one")it.copy(objectKind=EnumLoanObjectKind.OTHER)else it})
        assertThrows(BadRequestException::class.java){statutory.recordInspection(e.id.value,downgraded)}
        assertEquals(EnumLoanObjectKind.SCIENTIFIC_FOSSIL,e.inspection!!.items.first().objectKind)
        statutory.requirePrepared(e);approve(e);service.executeApprovedReturn(e.id.value)
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertEquals(original,CollectionPermanentArchiveDao.all().single {it.phase=="INSPECTION_original"}.snapshot)
        assertEquals(EnumCollectionNature.OTHER,CollectionDao["one"].nature)
    }
    @Test fun `legacy scientific inspection cannot justify return when its sealed bytes change`()=fixture { loan,unused ->
        val e=fossilRequisitionReturn(loan,unused)
        e.sourceSnapshot=statutory.source(e);e.inspection=fossilRequisitionInspection()
        FileDao.all().forEach { file ->
            val local=originalRoot.resolve(file.path.removePrefix("/"))
            java.nio.file.Files.createDirectories(local.parent)
            java.nio.file.Files.writeString(local,"ORIGINAL")
        }
        val guardedArchives=CollectionPermanentArchiveService(binder,scope,
            LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val guarded=ReturnStatutoryService(scope,users,binder,guardedArchives,incidents)
        guardedArchives.seal(e.orgId,CollectionConst.BIZ_RETURN,e.id.value,"INSPECTION_original",
            guarded.proposal(e),guarded.allFiles(e))
        assertDoesNotThrow {guarded.requirePrepared(e)}
        val file=originalRoot.resolve("files/sign.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(file)
        java.nio.file.Files.writeString(file,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        assertThrows(BadRequestException::class.java) {guarded.requirePrepared(e)}
        java.nio.file.Files.writeString(file,"ORIGINAL")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        assertDoesNotThrow {guarded.requirePrepared(e)}
    }
    @Test fun `changed original scientific requisition source or digest cannot authorize the compatibility path`()=fixture { loan,unused ->
        val e=fossilRequisitionReturn(loan,unused)
        e.sourceSnapshot=statutory.source(e);e.inspection=fossilRequisitionInspection()
        archives.seal(e.orgId,CollectionConst.BIZ_RETURN,e.id.value,"INSPECTION_original",statutory.proposal(e),statutory.allFiles(e))
        val a=CollectionPermanentArchiveDao.all().single();val original=a.snapshot
        a.snapshot=original.replace("fossil-req","another-req");a.digest=CollectionCareEvidence.digest(a.snapshot)
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(e)}
        a.snapshot=original
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(e)}
        assertEquals(EnumReturnExecutionState.PENDING,e.executionState)
    }
    private fun approve(e:CollectionReturnDao) {
        e.flowInstanceId="return-flow";e.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire(e.orgId,CollectionConst.BIZ_RETURN,e.id.value,splitCollectionIds(e.collectionIds))
        FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="return-task";nodeKey="keeper";action="APPROVE";operator="keeper";comment="本人核对逐件验收及保管";createTime=at.plusHours(1) }
        service.onFlowCompleted(e,FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_RETURN,"归还",e.id.value,CollectionConst.BIZ_RETURN,emptyMap(),"operator","经办"))
    }
    private fun report(item:String,target:EnumReturnReportTarget,kind:EnumReturnReportKind=EnumReturnReportKind.INCIDENT)=ReturnReportVo(item,target,kind,"实际机关","回执-1",at.plusDays(if(kind==EnumReturnReportKind.RESULT_FILING)2 else 1),"实际报送及结果", "report")
    private fun resolution(id:String)=ReturnResolutionVo(id,at.plusDays(2),"双方调查确认差异原因并完成处理","explain-keeper","explain-returning","investigation",true,true,"经核实已满足安全保管要求","result","keeper","leader",at.plusDays(3),"approval","按本件数量价值及实际情况核实报告程序","determination")
    private fun incident(e: CollectionReturnDao, source: IncidentSourceVo? = IncidentSourceVo(CollectionConst.BIZ_RETURN,e.id.value)): String =
        IncidentSourceTestSupport(incidents,at,"report").create(source,e.inspection!!.items.filter { it.abnormal() }.map { IncidentItemVo(it.collectionId,it.damaged,EnumIncidentSecurity.valueOf(it.securityIncident.name),false,it.anomalyDescription!!) })
    private fun closedIncident(e: CollectionReturnDao): String = incident(e).also { IncidentSourceTestSupport(incidents,at,"report").close(it) }
    private fun receiveDamaged(e: CollectionReturnDao) {
        statutory.recordInspection(e.id.value,inspection().copy(items=inspection().items.map { if(it.collectionId=="one")it.copy(damaged=true,anomalyDescription="新增损伤",protectiveMeasures="隔离保管")else it }))
        approve(e);service.executeApprovedReturn(e.id.value)
        assertBusinessReentryDenied(service,e,"keeper")
    }
    @Test fun `emergency relocation after actual damaged return preserves source lock and later acceptance retains new location`()=fixture { _,e ->
        receiveDamaged(e);val caseId=incident(e)
        val relocation=IncidentRelocationService(scope,users,incidents,IncidentWorkService(scope,users,incidents,operationArchives),operationArchives,listOf(IncidentWorkGuard(operationArchives),IncidentRelocationGuard(operationArchives)))
        val helper=IncidentRelocationTestSupport(relocation,incidents,at,"report")
        val id=helper.actualEmergency("one",caseId,"receiving","warehouse")
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        assertEquals("warehouse",CollectionDao["one"].warehouseId)
        assertTrue(CollectionOperationLockDao.all().toList().any {it.collectionId=="one"&&it.bizId==e.id.value})
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,resolution("one").copy(incidentId=caseId))}
        helper.confirmAndLink(caseId,id);IncidentSourceTestSupport(incidents,at.plusHours(3),"report").close(caseId)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=caseId))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertEquals("warehouse",CollectionDao["one"].warehouseId)
        assertEquals("receiving",e.toWarehouseId)
        assertTrue(e.inspection!!.items.single {it.collectionId=="one"}.damaged)
        assertDoesNotThrow{locks.requireUnlocked("one")}
    }
    @Test fun `actual damaged return protection retains received custody and source lock until case and return resolution`()=fixture { _,e ->
        receiveDamaged(e)
        val caseId=incident(e)
        val work=IncidentWorkService(scope,users,incidents,operationArchives)
        val support=IncidentWorkTestSupport(work,incidents,at.plusHours(1),"report")
        val workId=support.perform("one",caseId)
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        assertEquals("receiving",CollectionDao["one"].warehouseId)
        assertTrue(CollectionOperationLockDao.all().toList().any {it.collectionId=="one" && it.bizId==e.id.value && it.bizType==CollectionConst.BIZ_RETURN})
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,resolution("one").copy(incidentId=caseId))}
        support.linkResult(caseId,workId)
        IncidentSourceTestSupport(incidents,at.plusHours(4),"report").close(caseId)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("one")}
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=caseId))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertTrue(e.inspection!!.items.single {it.collectionId=="one"}.damaged)
        assertDoesNotThrow{locks.requireUnlocked("one")}
    }
    @Test fun `normal return requires both parties actual signatures and approval then atomically restores location`()=fixture { loan,e ->
        e.flowStatus=EnumFlowStatus.PASS
        assertThrows(BadRequestException::class.java) { service.executeApprovedReturn(e.id.value) }
        e.flowStatus=EnumFlowStatus.DRAFT
        for(v in listOf(inspection().copy(custodianUserIds=setOf("k1")),inspection().copy(returningPersonNames=setOf("甲")),inspection().copy(originalDocumentsChecked=false),inspection().copy(items=inspection().items.take(1))))assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,v) }
        statutory.recordInspection(e.id.value,inspection());approve(e)
        assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["one"].lifeStatus)
        service.executeApprovedReturn(e.id.value);service.executeApprovedReturn(e.id.value)
        assertEquals(2,StorageMovementDao.all().count());assertEquals("receiving",CollectionDao["one"].warehouseId)
        val warehouseLogs=CollectionLedgerLogDao.all().filter { it.field=="warehouseId" }
        assertEquals(2,warehouseLogs.size)
        assertTrue(warehouseLogs.all { it.before=="warehouse" && it.after=="receiving" && it.refBizId==e.id.value })
        assertEquals(at,e.executedAt);assertTrue(loan.returned)
        assertEquals(setOf("one","two"),service.returnedIdsBySource(CollectionConst.BIZ_LOAN,listOf("loan"))["loan"])
        assertDoesNotThrow { locks.requireUnlocked("one") }
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("sign") }
    }
    @Test fun `return approval rejects changed sealed custody before confirming the handover`()=fixture { _,e ->
        statutory.recordInspection(e.id.value,inspection())
        val archivesBefore=CollectionPermanentArchiveDao.count()
        e.toWarehouseId="warehouse"
        assertThrows(BadRequestException::class.java) { approve(e) }
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertTrue(CollectionPermanentArchiveDao.all().none {it.phase.startsWith("APPROVED_")})
        assertEquals(0L,StorageMovementDao.count())
    }
    @Test fun `actual receipt requires the approved archive snapshot and file identity`()=fixture { _,e ->
        statutory.recordInspection(e.id.value,inspection());approve(e)
        val approved=CollectionPermanentArchiveDao.all().single {it.phase.startsWith("APPROVED_")}
        val snapshot=approved.snapshot
        val digest=approved.digest
        approved.snapshot="{}"
        approved.digest=CollectionCareEvidence.digest(approved.snapshot)
        assertThrows(BadRequestException::class.java) {service.executeApprovedReturn(e.id.value)}
        assertEquals(0L,StorageMovementDao.count())
        assertEquals(EnumReturnExecutionState.PENDING,e.executionState)
        approved.snapshot=snapshot
        approved.digest=digest
        val ref=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq approved.id.value}.first()
        val metadata=ref.metadata
        ref.metadata="{}"
        assertThrows(BadRequestException::class.java) {service.executeApprovedReturn(e.id.value)}
        assertEquals(0L,StorageMovementDao.count())
        ref.metadata=metadata
        service.executeApprovedReturn(e.id.value)
        assertEquals(EnumReturnExecutionState.EXECUTED,e.executionState)
    }
    @Test fun `actual receipt rejects changed sealed custody and missing inspection file references`()=fixture { _,e ->
        statutory.recordInspection(e.id.value,inspection());approve(e)
        val opened=inspectionArchiveId(e)
        val archivesBefore=CollectionPermanentArchiveDao.count()
        e.toWarehouseId="warehouse"
        assertThrows(BadRequestException::class.java) { service.executeApprovedReturn(e.id.value) }
        e.toWarehouseId="receiving"
        CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq opened}.first().delete()
        assertThrows(BadRequestException::class.java) { service.executeApprovedReturn(e.id.value) }
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertEquals(0L,StorageMovementDao.count())
        assertEquals(EnumReturnExecutionState.PENDING,e.executionState)
        assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["one"].lifeStatus)
    }
    @Test fun `repeated receipt requires its sealed original`()=fixture { _,e ->
        statutory.recordInspection(e.id.value,inspection());approve(e)
        service.executeApprovedReturn(e.id.value)
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==e.id.value && it.phase=="RECEIVED" }
        val snapshot=original.snapshot
        original.snapshot="{}"
        assertThrows(BadRequestException::class.java) { service.executeApprovedReturn(e.id.value) }
        original.snapshot=snapshot
        original.delete()
        assertThrows(BadRequestException::class.java) { service.executeApprovedReturn(e.id.value) }
        assertEquals(2,StorageMovementDao.all().count())
    }
    @Test fun `paired print keeps every partial return and frozen attributes after main changes`()=fixture { loan,e ->
        archives.seal("museum",CollectionConst.BIZ_LOAN,"loan","OUT_EXECUTED",mapOf("request" to loan.toModel()),"signed-out")
        val outbound=StorageMovementDao.new { orgId="museum";collectionId="one";documentNo="XY-001";type=EnumMoveType.OUT;reason=EnumMoveReason.LOAN;refBizId="loan";operatedAt=out;totalRegNoAtMovement="one";collectionNameAtMovement="藏品one";fromWarehouseId="warehouse" }
        CollectionMovementFacts.capture(outbound,archives)
        service.save(e.toModel().copy(collectionIds="one"));statutory.recordInspection(e.id.value,inspection(listOf("one")));approve(e);service.executeApprovedReturn(e.id.value)
        val first=CollectionCirculationPrintFields.build(outbound.toModel(),archives)
        assertTrue(first.any {it.value?.contains("有逐件接收原件1条")==true})
        assertTrue(first.any {it.label.contains("前后对照") && it.value?.contains("出库时完整")==true})
        val next=CollectionReturnDao[service.ensureDraftFromLoan("loan")]
        service.save(next.toModel().copy(toWarehouseId="receiving"));statutory.recordInspection(next.id.value,inspection(listOf("two")));approve(next);service.executeApprovedReturn(next.id.value)
        CollectionDao["one"].name="后续订正名称";CollectionDao["one"].quantity=9
        val second=CollectionCirculationPrintFields.build(outbound.toModel(),archives)
        assertTrue(second.any {it.value?.contains("有逐件接收原件2条")==true})
        assertEquals(2,second.count {it.label.endsWith("接收时间")})
        val frozen=CollectionMovementFacts.read(outbound.toModel(),archives)!!
        assertEquals("藏品one",frozen.collection.name);assertEquals(1,frozen.collection.quantity)
        outbound.collectionNameAtMovement="被修改的流水"
        assertThrows(BadRequestException::class.java) { CollectionMovementFacts.read(outbound.toModel(),archives) }
    }
    @Test fun `partial return only closes accepted subset and next draft contains remaining objects`()=fixture { loan,e ->
        service.save(e.toModel().copy(collectionIds="one"));statutory.recordInspection(e.id.value,inspection(listOf("one")));approve(e);service.executeApprovedReturn(e.id.value)
        assertFalse(loan.returned);assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["two"].lifeStatus)
        val next=CollectionReturnDao[service.ensureDraftFromLoan("loan")];assertEquals("two",next.collectionIds)
    }
    @Test fun `foreign placement source replacement and unsigned summary are rejected`()=fixture { loan,e ->
        val before=e.toModel()
        assertThrows(BadRequestException::class.java) { service.save(e.toModel().copy(toWarehouseId="foreign-place")) }
        assertEquals(before,e.toModel())
        // 模拟已存在的跨馆旧记录，实际点交仍需独立拒绝。
        e.toWarehouseId="foreign-place"
        assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,inspection()) }
        e.toWarehouseId="receiving"
        statutory.recordInspection(e.id.value,inspection())
        assertThrows(BadRequestException::class.java) { service.save(e.toModel().copy(collectionIds="one")) }
        loan.outExecuted=false
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
    }
    @Test fun `damaged returns are physically received under hold until both explanations and mandatory reports exist`()=fixture { loan,e ->
        val damaged=inspection().copy(items=inspection().items.map { if(it.collectionId=="one")it.copy(conditionMatches=false,damaged=true,condition="新出现裂隙",textNeedsImages=true,imageFileIds="image",anomalyDescription="与原图不同",protectiveMeasures="独立支撑封存，禁止再用")else it })
        statutory.recordInspection(e.id.value,damaged);approve(e);service.executeApprovedReturn(e.id.value)
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        assertThrows(BadRequestException::class.java) { CollectionRecordPolicy.requireCatalogAuditable(EnumCatalogStatus.SUBMITTED,EnumCollectionStatus.RETURN_HOLD) }
        assertThrows(BadRequestException::class.java) { CollectionRecordPolicy.requireEditable(EnumCatalogStatus.DRAFT,EnumCollectionStatus.RETURN_HOLD) }
        assertThrows(BadRequestException::class.java) { collections.changeLifeStatus("one",EnumCollectionStatus.ON_LOAN) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["two"].lifeStatus);assertFalse(loan.returned)
        assertThrows(BadRequestException::class.java) { locks.requireUnlocked("one") };assertDoesNotThrow { locks.requireUnlocked("two") }
        assertEquals(setOf("two"),service.returnedIdsBySource(CollectionConst.BIZ_LOAN,listOf("loan"))["loan"])
        assertEquals(setOf("one"),service.heldIdsBySource(CollectionConst.BIZ_LOAN,listOf("loan"))["loan"])
        assertEquals(e.id.value,service.ensureDraftFromLoan("loan"))
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,resolution("one")) }
        statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.PROVINCE),inspectionArchiveId(e))
        assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,inspection().copy(replacementReason="试图覆盖已报告异常")) }
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,resolution("one")) }
        statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.NATIONAL,EnumReturnReportKind.RESULT_FILING),inspectionArchiveId(e))
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,resolution("one").copy(returningExplanationFileIds="")) }
        val closed=closedIncident(e)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=closed));service.resolveIssue(e.id.value,resolution("one").copy(incidentId=closed))
        service.executeApprovedReturn(e.id.value)
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus);assertTrue(loan.returned)
        assertEquals(2,StorageMovementDao.all().count());assertDoesNotThrow { locks.requireUnlocked("one") }
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("explain-returning") }
    }
    @Test fun `first level damage needs national report while security incident always needs police and competent reports`()=fixture { loan,e ->
        val baseline=loan.baseline!!;loan.baseline=baseline.copy(items=baseline.items.map { if(it.collectionId=="one")it.copy(content=it.content.copy(level=EnumRelicLevel.FIRST))else it });CollectionDao["one"].level=EnumRelicLevel.FIRST
        val check=inspection().copy(items=inspection().items.map { if(it.collectionId=="one")it.copy(authenticityMatches=false,damaged=true,securityIncident=EnumReturnSecurityIncident.THEFT,anomalyDescription="实物身份存疑并损伤",protectiveMeasures="隔离封存")else it })
        statutory.recordInspection(e.id.value,check);approve(e);service.executeApprovedReturn(e.id.value)
        statutory.recordReport(e.id.value,report("one",EnumReturnReportTarget.PROVINCE),inspectionArchiveId(e))
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,resolution("one")) }
        for(target in listOf(EnumReturnReportTarget.NATIONAL,EnumReturnReportTarget.POLICE,EnumReturnReportTarget.COMPETENT))statutory.recordReport(e.id.value,report("one",target),inspectionArchiveId(e))
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,resolution("one").copy(confirmedAuthentic=false)) }
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=closedIncident(e)));assertTrue(loan.returned)
    }
    @Test fun `evidence replacement preserves originals and executed return cannot reactivate or cancel`()=fixture { _,e ->
        statutory.recordInspection(e.id.value,inspection());statutory.recordInspection(e.id.value,inspection())
        val revised=inspection().copy(items=inspection().items.map { it.copy(condition="补充细部一致") })
        assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,revised) }
        val currentArchive=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq e.id.value)
        }.filter {it.phase.startsWith("INSPECTION_")}.single().id.value
        statutory.recordInspection(e.id.value,revised.copy(replacementReason="补充签署附页",expectedInspectionArchiveId=currentArchive))
        assertEquals(2,archives.list(CollectionConst.BIZ_RETURN,e.id.value).count { it["phase"].toString().startsWith("INSPECTION_") })
        approve(e);service.executeApprovedReturn(e.id.value)
        assertThrows(BadRequestException::class.java) { service.onFlowCanceled(e,FlowCanceledEvent(e.flowInstanceId!!,CollectionConst.FLOW_RETURN,"归还",e.id.value,CollectionConst.BIZ_RETURN,"operator","经办","operator","经办")) }
        assertThrows(BadRequestException::class.java) { service.onFlowCompleted(e,FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_RETURN,"归还",e.id.value,CollectionConst.BIZ_RETURN,emptyMap(),"operator","经办")) }
    }
    @Test fun `stale inspection correction cannot replace newer signed evidence`()=fixture { _,e ->
        statutory.recordInspection(e.id.value,inspection())
        val first=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq e.id.value)
        }.filter {it.phase.startsWith("INSPECTION_")}.single().id.value
        val second=inspection().copy(signedHandoverFileIds="approval",replacementReason="补充签署附页",expectedInspectionArchiveId=first)
        statutory.recordInspection(e.id.value,second)
        val archiveCount=CollectionPermanentArchiveDao.count()
        val stale=inspection().copy(signedHandoverFileIds="image",replacementReason="旧页面提交补正",expectedInspectionArchiveId=first)
        val missing=stale.copy(expectedInspectionArchiveId=null)
        for(value in listOf(stale,missing)) {
            val failure=assertThrows(BadRequestException::class.java) {statutory.recordInspection(e.id.value,value)}
            assertTrue(failure.message!!.contains("版本") || failure.message!!.contains("档案"))
            assertEquals(second,e.inspection)
            assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
        }
    }
    @Test fun `explicit false and exact inspection time survive JSON and images are conditionally mandatory`()=fixture { _,e ->
        val missing=inspection().copy(inspectedAt=at.plusNanos(123000000),items=inspection().items.map { it.copy(textNeedsImages=true) })
        assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,missing) }
        val value=missing.copy(items=missing.items.map { it.copy(imageFileIds="image") })
        statutory.recordInspection(e.id.value,value)
        val restored=JsonUtils.fromJson(JsonUtils.toJson(e.inspection)!!,ReturnInspectionVo::class.java)!!
        assertFalse(restored.items.first().damaged);assertEquals(value.inspectedAt,restored.inspectedAt)
    }
    @Test fun `internal requisition return verifies different actual department members and accepts one person on each side`()=fixture { loan,unused ->
        unused.flowStatus=EnumFlowStatus.CANCEL
        listOf("one","two").forEach { CollectionDao[it].lifeStatus=EnumCollectionStatus.IN_USE }
        val req=RequisitionDao.new("req") {
            orgId="museum";collectionIds="one,two";documentNo="TY-1";department="dept";operator="operator";toWarehouseId="receiving";flowStatus=EnumFlowStatus.PASS;outExecuted=true;outExecutedTime=out
            dossier=RequisitionDossierVo("dept","department-head","keeper","leader","use-person","展厅展陈","主题展","展厅","定岗保卫","agreement",out,at)
            baseline=RequisitionBaselineVo(loan.baseline!!.items)
            handover=RequisitionHandoverRecordVo(RequisitionHandoverVo(out,approved,"keeper","approval",setOf("k1"),setOf("use-person"),loan.handover!!.content.items,true,true,"approval","signed-out"),"req-task","operator",out)
        }
        val e=CollectionReturnDao[service.ensureDraftFromRequisition(req.id.value)]
        val check=inspection().copy(custodianUserIds=setOf("k1"),returningUserIds=setOf("k1"),returningPersonNames=emptySet())
        assertThrows(BadRequestException::class.java) { statutory.recordInspection(e.id.value,check) }
        statutory.recordInspection(e.id.value,check.copy(returningUserIds=setOf("use-person")))
        verify(users).requireDepartmentMember("use-person","museum","dept")
        approve(e);service.executeApprovedReturn(e.id.value)
        assertTrue(req.returned);assertEquals(2,StorageMovementDao.all().count())
    }
    @Test fun `return resolution requires same source closed case actual heads and prior confirmation`()=fixture { loan,e ->
        receiveDamaged(e)
        val case=incident(e)
        val value=resolution("one").copy(incidentId=case,completedAt=at.plusMinutes(30))
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,value)}
        IncidentSourceTestSupport(incidents,at,"report").close(case)
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,value.copy(approvedAt=at.plusMinutes(40)))}
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,value.copy(leaderUserId="another-leader"))}
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,value.copy(additionalReportTargets=setOf(EnumReturnReportTarget.COMPETENT)))}
        assertTrue(statutory.records(e.id.value).none { it["kind"]=="RESOLUTION" })
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        service.resolveIssue(e.id.value,value)
        assertTrue(loan.returned)
        val proof=archives.list(CollectionConst.BIZ_RETURN,e.id.value).single { it["phase"].toString().startsWith("INCIDENT_CLEARANCE_") }
        assertTrue(proof["snapshot"].toString().contains(case))
        assertTrue(proof["snapshot"].toString().contains("basisRevision"))
    }
    @Test fun `repeated return clearance rejects a missing incident clearance original`()=fixture { _,e ->
        receiveDamaged(e)
        val value=resolution("one").copy(incidentId=closedIncident(e))
        service.resolveIssue(e.id.value,value)
        val recordsBefore=ReturnComplianceRecordDao.count()
        val archivesBefore=CollectionPermanentArchiveDao.count()
        CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }.delete()
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,value) }
        assertEquals(recordsBefore,ReturnComplianceRecordDao.count())
        assertEquals(archivesBefore-1,CollectionPermanentArchiveDao.count())
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    @Test fun `repeated return clearance rejects changed incident proof despite matching digest`()=fixture { _,e ->
        receiveDamaged(e)
        val value=resolution("one").copy(incidentId=closedIncident(e))
        service.resolveIssue(e.id.value,value)
        val archive=CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }
        archive.snapshot=archive.snapshot.replace("basisRevision", "obsoleteRevision")
        archive.digest=CollectionCareEvidence.digest(archive.snapshot)
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,value) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    @Test fun `repeated return clearance rejects damaged sealed approval bytes`()=fixture { _,e ->
        receiveDamaged(e)
        val value=resolution("one").copy(incidentId=closedIncident(e))
        FileDao.all().forEach { file ->
            val local=originalRoot.resolve(file.path.removePrefix("/"))
            java.nio.file.Files.createDirectories(local.parent)
            java.nio.file.Files.writeString(local,"ORIGINAL")
        }
        val guardedArchives=CollectionPermanentArchiveService(binder,scope,
            LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val guardedStatutory=ReturnStatutoryService(scope,users,binder,guardedArchives,incidents)
        val guardedService=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collections,
            mock(CollectionUserResolver::class.java),binder,items,locks,mock(CollectionPlacementService::class.java),
            guardedStatutory,guardedArchives)
        guardedService.resolveIssue(e.id.value,value)
        val file=originalRoot.resolve("files/approval.pdf")
        val timestamp=java.nio.file.Files.getLastModifiedTime(file)
        java.nio.file.Files.writeString(file,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        val archiveCount=CollectionPermanentArchiveDao.count()
        val recordCount=ReturnComplianceRecordDao.count()
        assertThrows(BadRequestException::class.java) {guardedService.resolveIssue(e.id.value,value)}
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
        assertEquals(recordCount,ReturnComplianceRecordDao.count())
        java.nio.file.Files.writeString(file,"ORIGINAL")
        java.nio.file.Files.setLastModifiedTime(file,timestamp)
        guardedService.resolveIssue(e.id.value,value)
        assertEquals(archiveCount,CollectionPermanentArchiveDao.count())
    }
    @Test fun `repeated return clearance rejects a missing incident original file reference`()=fixture { _,e ->
        receiveDamaged(e)
        val value=resolution("one").copy(incidentId=closedIncident(e))
        service.resolveIssue(e.id.value,value)
        val archive=CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.first().delete()
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,value) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    @Test fun `return history rejects a missing incident clearance original`()=fixture { _,e ->
        receiveDamaged(e)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=closedIncident(e)))
        assertEquals(1,statutory.records(e.id.value).count { it["kind"]=="RESOLUTION" })
        CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }.delete()
        assertThrows(BadRequestException::class.java) { statutory.records(e.id.value) }
        assertThrows(BadRequestException::class.java) { statutory.pendingRequirements(e) }
    }
    @Test fun `return history rejects a changed incident basis with matching digest`()=fixture { _,e ->
        receiveDamaged(e)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=closedIncident(e)))
        val archive=CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }
        archive.snapshot=archive.snapshot.replace("basisRevision", "obsoleteRevision")
        archive.digest=CollectionCareEvidence.digest(archive.snapshot)
        assertThrows(BadRequestException::class.java) { statutory.records(e.id.value) }
    }
    @Test fun `return history rejects a missing incident original file reference`()=fixture { _,e ->
        receiveDamaged(e)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=closedIncident(e)))
        val archive=CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.first().delete()
        assertThrows(BadRequestException::class.java) { statutory.records(e.id.value) }
    }
    @ParameterizedTest @ValueSource(strings=["FACTS","REPORT","HANDLING","CONFIRMATION"])
    fun `return history rejects a missing underlying incident file reference`(kindName:String)=fixture { _,e ->
        receiveDamaged(e)
        val case=if(kindName=="FACTS") incident(e).also { id ->
            val facts=CollectionIncidentRecordDao.all().single { it.incidentId==id && it.kind==EnumIncidentRecordKind.FACTS }
            val initial=JsonUtils.fromJson(facts.content,IncidentFactsSnapshot::class.java)!!.facts
            incidents.amend(id,initial.copy(evidenceFileIds="report",replacementReason="补充事实原件",replacementFileIds="report"),facts.id.value)
            IncidentSourceTestSupport(incidents,at,"report").close(id)
        } else closedIncident(e)
        val value=resolution("one").copy(incidentId=case)
        service.resolveIssue(e.id.value,value)
        assertEquals(1,statutory.records(e.id.value).count { it["kind"]=="RESOLUTION" })
        val record=CollectionIncidentRecordDao.all().filter { it.incidentId==case && it.kind==EnumIncidentRecordKind.valueOf(kindName) }.maxBy { it.sequence }
        val archive=CollectionPermanentArchiveDao.all().single { it.bizType==CollectionConst.BIZ_INCIDENT && it.bizId==case && it.phase==record.kind.toString()+"_"+record.id.value }
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.first().delete()
        assertThrows(BadRequestException::class.java) { statutory.records(e.id.value) }
        assertThrows(BadRequestException::class.java) { service.resolveIssue(e.id.value,value) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    @Test fun `return history rejects changed incident file identity metadata`()=fixture { _,e ->
        receiveDamaged(e)
        val case=closedIncident(e)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=case))
        assertEquals(1,statutory.records(e.id.value).count { it["kind"]=="RESOLUTION" })
        val report=CollectionIncidentRecordDao.all().first { it.incidentId==case && it.kind==EnumIncidentRecordKind.REPORT }
        val archive=CollectionPermanentArchiveDao.all().single { it.bizType==CollectionConst.BIZ_INCIDENT && it.bizId==case && it.phase==report.kind.toString()+"_"+report.id.value }
        val file=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.first()
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path","changed-incident-original")
        file.metadata=metadata.toString()
        assertThrows(BadRequestException::class.java) { statutory.records(e.id.value) }
    }
    @Test fun `return history retains a sealed closeout after later incident facts are amended`()=fixture { _,e ->
        receiveDamaged(e)
        val case=closedIncident(e)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=case))
        val original=statutory.records(e.id.value).single { it["kind"]=="RESOLUTION" }
        val facts=CollectionIncidentRecordDao.all().single { it.incidentId==case && it.kind==EnumIncidentRecordKind.FACTS }
        val value=JsonUtils.fromJson(facts.content,IncidentFactsSnapshot::class.java)!!.facts
        incidents.amend(case,value.copy(description="发现新的事故情况，旧次归还处理材料仍须保留",
            replacementReason="追加新事实",replacementFileIds="report"),facts.id.value)
        assertEquals(original,statutory.records(e.id.value).single { it["kind"]=="RESOLUTION" })
    }
    @Test fun `unrelated case and another open incident cannot release return safety hold`()=fixture { _,e ->
        receiveDamaged(e)
        val unrelated=incident(e,null)
        val helper=IncidentSourceTestSupport(incidents,at,"report");helper.close(unrelated)
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,resolution("one").copy(incidentId=unrelated))}
        val linked=incident(e);helper.close(linked)
        val another=incident(e,null)
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))}
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        helper.close(another)
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))
        assertDoesNotThrow { locks.requireUnlocked("one") }
    }
    @Test fun `changed source evidence rejects clearance without writing resolution or accepting object`()=fixture { loan,e ->
        receiveDamaged(e)
        val linked=closedIncident(e)
        val old=e.inspection!!
        e.inspection=old.copy(items=old.items.map { if(it.collectionId=="one")it.copy(condition="新增实际待核实事实")else it })
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))}
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        assertFalse(loan.returned)
        assertTrue(statutory.records(e.id.value).none { it["kind"]=="RESOLUTION" })
    }
    @Test fun `return clearance rejects drifted sealed custody before releasing held object`()=fixture { _,e ->
        receiveDamaged(e)
        val linked=closedIncident(e)
        val archivesBefore=CollectionPermanentArchiveDao.count()
        val recordsBefore=ReturnComplianceRecordDao.count()
        e.toWarehouseId="warehouse"
        assertThrows(BadRequestException::class.java) {
            service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))
        }
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertEquals(recordsBefore,ReturnComplianceRecordDao.count())
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        e.toWarehouseId="receiving"
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    @Test fun `return clearance rejects missing inspection original reference`()=fixture { _,e ->
        receiveDamaged(e)
        val linked=closedIncident(e)
        val opened=inspectionArchiveId(e)
        val archivesBefore=CollectionPermanentArchiveDao.count()
        val recordsBefore=ReturnComplianceRecordDao.count()
        CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq opened}.first().delete()
        assertThrows(BadRequestException::class.java) {
            service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))
        }
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertEquals(recordsBefore,ReturnComplianceRecordDao.count())
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
    }
    @Test fun `source first level obligation survives catalog downgrade before incident first report`()=fixture { loan,e ->
        loan.baseline=loan.baseline!!.copy(items=loan.baseline!!.items.map { if(it.collectionId=="one")it.copy(content=it.content.copy(level=EnumRelicLevel.FIRST))else it })
        CollectionDao["one"].level=EnumRelicLevel.SECOND
        receiveDamaged(e)
        val linked=closedIncident(e)
        @Suppress("UNCHECKED_CAST") val requirements=incidents.detail(linked)["requirements"] as List<Map<String,Any>>
        assertTrue(requirements.any { it["target"]==EnumIncidentTarget.NATIONAL && it["kind"]==EnumIncidentReportKind.INCIDENT })
        assertTrue(requirements.any { it["target"]==EnumIncidentTarget.PROVINCE })
        service.resolveIssue(e.id.value,resolution("one").copy(incidentId=linked))
        assertTrue(loan.returned)
    }

    @Test fun `legacy resolution replay cannot newly accept a held object without verified incident`()=fixture { loan,e ->
        receiveDamaged(e)
        val old=resolution("one")
        ReturnComplianceRecordDao.new { orgId=e.orgId;returnId=e.id.value;kind="RESOLUTION";content=JsonUtils.toJson(old)!! }
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,old)}
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        assertFalse(loan.returned)
    }
    @Test fun `late operation conflict rolls back resolution archive acceptance and lifecycle together`() {
        var caseId=""
        fixture(afterCommit={db,loanId,returnId ->
            assertThrows(BadRequestException::class.java) {
                transaction(db) { service.resolveIssue(returnId,resolution("one").copy(incidentId=caseId)) }
            }
            transaction(db) {
                assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
                assertFalse(LoanDao[loanId].returned)
                assertEquals("two",CollectionReturnDao[returnId].acceptedCollectionIds)
                assertTrue(statutory.records(returnId).none { it["kind"]=="RESOLUTION" })
                assertTrue(archives.list(CollectionConst.BIZ_RETURN,returnId).none { it["phase"].toString().startsWith("INCIDENT_CLEARANCE_") })
                assertEquals(true,incidents.detail(caseId)["closed"])
            }
        }) { _,e ->
            receiveDamaged(e);caseId=closedIncident(e)
            CollectionOperationLockDao.all().single().apply { bizType=CollectionConst.BIZ_LOAN;bizId="conflicting-operation" }
        }
    }

    private fun responsibilityService() = ReturnResponsibilityService(scope,CollectionResponsibleUserService(CollectionActorDirectory(scope),scope),statutory,archives)
    private fun responsibilityPeople() {
        incidentReaderFixture("new-keeper","new-director","next-keeper")
        actorRole("new-keeper",CollectionConst.ROLE_STORAGE_HEAD,setOf(CollectionConst.STORAGE))
        actorRole("next-keeper",CollectionConst.ROLE_STORAGE_HEAD,setOf(CollectionConst.STORAGE))
        actorRole("new-director",CollectionConst.ROLE_DIRECTOR,setOf(CollectionConst.STORAGE))
    }
    private fun responsibility(e:CollectionReturnDao) = ReturnResponsibilityVo(
        receivedDigest=ReturnResponsibilityEvidence.received(e).digest,collectionIds=setOf("one"),handedAt=LocalDateTime.now().minusMinutes(1),
        keeperUserId="new-keeper",directorUserId="new-director",reason="原保管负责人离任",actualInventory="逐件核对实物、接收点交及当前隔离保管位置",outstandingMatters="损伤原因调查与处理案卷仍须接续，继续隔离保护",appointmentFileIds="report",handoverFileIds="sign")
    private fun responsibilityActor(id:String)=IncidentSourceTestSupport(incidents,at,"report").actor(id)
    private val responsibilitySignature=CollectionCareSignatureVo("本人核对实际清点移交及全部未结事项","sign")
    private fun confirmResponsibility(e:CollectionReturnDao,v:ReturnResponsibilityVo=responsibility(e)):String {
        val h=responsibilityService();responsibilityActor(v.keeperUserId);val id=h.register(e.id.value,v,"museum")
        h.sign(e.id.value,id,"KEEPER",responsibilitySignature,"museum")
        responsibilityActor(v.directorUserId);h.sign(e.id.value,id,"DIRECTOR",responsibilitySignature,"museum")
        return id
    }
    @Test fun `responsibility proposal and each signature expose only their own archived originals`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople()
        val fileIds=(1..4).map { "210000000000000000$it" }
        fileIds.forEach { fileId -> FileDao.new(fileId) {name="$fileId.pdf";path="/files/$fileId.pdf";hash=fileId;size=100;ext="pdf"} }
        val v=responsibility(e).copy(appointmentFileIds=fileIds[0],handoverFileIds=fileIds[1])
        responsibilityActor("new-keeper")
        val recordId=responsibilityService().register(e.id.value,v,"museum")
        responsibilityService().sign(e.id.value,recordId,"KEEPER",responsibilitySignature.copy(fileIds=fileIds[2]),"museum")
        responsibilityActor("new-director")
        responsibilityService().sign(e.id.value,recordId,"DIRECTOR",responsibilitySignature.copy(fileIds=fileIds[3]),"museum")
        responsibilityActor("operator")
        val byPhase=service.get(e.id.value)!!.archives.associateBy {it["phase"] as String}
        val expected=mapOf(
            "RESPONSIBILITY_P_$recordId" to setOf(fileIds[0],fileIds[1]),
            "RESPONSIBILITY_KEEPER_$recordId" to setOf(fileIds[2]),
            "RESPONSIBILITY_DIRECTOR_$recordId" to setOf(fileIds[3]),
        )
        expected.forEach { (phase,files) ->
            val archive=byPhase[phase] ?: fail("缺少责任承接归档阶段：$phase")
            val archiveId=archive["id"] as String
            assertEquals(files,(archive["files"] as List<*>).map {(it as Map<*,*>)["fileId"]}.toSet())
            files.forEach {fileId -> assertDoesNotThrow {service.requireArchivedOriginal(e.id.value,archiveId,fileId)} }
            (fileIds.toSet()-files).forEach {fileId ->
                assertThrows(BadRequestException::class.java) {service.requireArchivedOriginal(e.id.value,archiveId,fileId)}
            }
        }
        val proposalId=byPhase["RESPONSIBILITY_P_$recordId"]!!["id"] as String
        assertThrows(BadRequestException::class.java) {service.requireArchivedOriginal(e.id.value,"2100000000000000005",fileIds[0])}
        assertThrows(BadRequestException::class.java) {service.requireArchivedOriginal("2100000000000000006",proposalId,fileIds[0])}
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
    }
    @Test fun `received abnormal return succeeds with confirmed successor and matching completed incident while original signatures remain`()=fixture { loan,e ->
        receiveDamaged(e);responsibilityPeople()
        val original=statutory.proposal(e);val receipt=ReturnResponsibilityEvidence.received(e).snapshot
        io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false
        val caseId=incident(e)
        IncidentSourceTestSupport(incidents,LocalDateTime.now().minusHours(1),"report").close(caseId,"new-keeper","new-director")
        val value=resolution("one").copy(incidentId=caseId,keeperUserId="new-keeper",leaderUserId="new-director",approvedAt=LocalDateTime.now())
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,value,"museum")}
        val id=confirmResponsibility(e)
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus);assertFalse(loan.returned)
        responsibilityActor("operator")
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,value,"museum")}
        val final=value.copy(expectedResponsibilityId=id,approvedAt=LocalDateTime.now())
        service.resolveIssue(e.id.value,final,"museum");service.resolveIssue(e.id.value,final,"museum")
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus);assertTrue(loan.returned)
        assertEquals(original,statutory.proposal(e));assertEquals(receipt,ReturnResponsibilityEvidence.received(e).snapshot)
        assertEquals("keeper",e.keeperUserId);assertTrue(e.inspection!!.items.first().damaged)
        assertEquals(1,statutory.records(e.id.value).count {it["kind"]=="RESOLUTION"})
        assertEquals(emptySet<String>(),responsibilityService().detail(e.id.value,"museum")["pendingCollectionIds"])
    }
    @Test fun `responsibility needs ordered actual people and keeps previous effective responsibility until all confirmations`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val h=responsibilityService();val v=responsibility(e)
        responsibilityActor("operator");assertThrows(BadRequestException::class.java){h.register(e.id.value,v,"museum")}
        responsibilityActor("new-keeper");val id=h.register(e.id.value,v,"museum")
        assertEquals(id,h.register(e.id.value,v,"museum"))
        responsibilityActor("new-director");assertThrows(BadRequestException::class.java){h.sign(e.id.value,id,"DIRECTOR",responsibilitySignature,"museum")}
        responsibilityActor("operator");assertThrows(BadRequestException::class.java){h.sign(e.id.value,id,"KEEPER",responsibilitySignature,"museum")}
        responsibilityActor("new-keeper");h.sign(e.id.value,id,"KEEPER",responsibilitySignature,"museum")
        assertNull(ReturnResponsibilityEvidence.effective(e,"one"))
        assertThrows(BadRequestException::class.java){h.sign(e.id.value,id,"KEEPER",responsibilitySignature.copy(opinion="覆盖旧意见"),"museum")}
        responsibilityActor("new-director");h.sign(e.id.value,id,"DIRECTOR",responsibilitySignature,"museum")
        responsibilityActor("next-keeper")
        val next=v.copy(previousId=id,expectedEffectiveId=id,keeperUserId="next-keeper",handedAt=LocalDateTime.now())
        val nextId=h.register(e.id.value,next,"museum");h.sign(e.id.value,nextId,"KEEPER",responsibilitySignature,"museum")
        assertEquals(id,ReturnResponsibilityEvidence.effective(e,"one")!!.record.id)
        responsibilityActor("new-director");h.sign(e.id.value,nextId,"DIRECTOR",responsibilitySignature,"museum")
        assertEquals(nextId,ReturnResponsibilityEvidence.effective(e,"one")!!.record.id)
        assertEquals(2,ReturnResponsibilityEvidence.entries(e).count {it.complete})
    }
    @Test fun `responsibility rejects stale source museum incomplete scope future time and missing originals`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val h=responsibilityService();val v=responsibility(e);responsibilityActor("new-keeper")
        assertThrows(BadRequestException::class.java){h.register(e.id.value,v,"other")}
        for(wrong in listOf(v.copy(receivedDigest="stale"),v.copy(collectionIds=setOf("two")),v.copy(collectionIds=emptySet()),v.copy(previousId="old"),v.copy(expectedEffectiveId="old"),v.copy(handedAt=at.minusDays(1)),v.copy(handedAt=LocalDateTime.now().plusDays(1)),v.copy(appointmentFileIds=""),v.copy(handoverFileIds="missing"),v.copy(actualInventory=""),v.copy(directorUserId="new-keeper"),v.copy(keeperUserId="operator"))) {
            assertThrows(BadRequestException::class.java){h.register(e.id.value,wrong,"museum")}
        }
        assertTrue(ReturnResponsibilityEvidence.entries(e).isEmpty())
    }
    @Test fun `pending responsibility can be superseded and old confirmation cannot sign newer proposal`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val h=responsibilityService();val v=responsibility(e);responsibilityActor("new-keeper")
        val id=h.register(e.id.value,v,"museum");h.sign(e.id.value,id,"KEEPER",responsibilitySignature,"museum")
        val next=h.register(e.id.value,v.copy(previousId=id,outstandingMatters="补充未结实物保管事项"),"museum")
        responsibilityActor("new-director")
        assertThrows(BadRequestException::class.java){h.sign(e.id.value,id,"DIRECTOR",responsibilitySignature,"museum")}
        assertThrows(BadRequestException::class.java){h.sign(e.id.value,next,"DIRECTOR",responsibilitySignature,"museum")}
        assertEquals(setOf("KEEPER"),ReturnResponsibilityEvidence.entries(e).first().signatures.keys)
        assertNull(ReturnResponsibilityEvidence.effective(e,"one"))
    }
    @Test fun `successor does not bypass missing incident different incident signers or pre succession approval`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val oldCase=closedIncident(e);val id=confirmResponsibility(e);responsibilityActor("operator")
        val v=resolution("one").copy(keeperUserId="new-keeper",leaderUserId="new-director",expectedResponsibilityId=id,approvedAt=LocalDateTime.now())
        for(wrong in listOf(v,v.copy(incidentId=oldCase),v.copy(approvedAt=at.plusDays(3)),v.copy(keeperUserId="keeper")))assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,wrong,"museum")}
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
        assertTrue(statutory.records(e.id.value).none {it["kind"]=="RESOLUTION"})
    }
    @Test fun `receipt or responsibility signature evidence damage fails closed without changing original return`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val id=confirmResponsibility(e)
        val a=CollectionPermanentArchiveDao.all().single {it.phase=="RESPONSIBILITY_KEEPER_$id"}
        val ref=CollectionPermanentArchiveFileDao.all().single {it.archiveId==a.id.value};ref.delete()
        assertThrows(BadRequestException::class.java){ReturnResponsibilityEvidence.entries(e)}
        assertThrows(BadRequestException::class.java){responsibilityService().detail(e.id.value,"museum")}
        assertEquals("keeper",e.keeperUserId);assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
    }
    @Test fun `current permission and museum revocation cannot be bypassed by login snapshot`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val h=responsibilityService();responsibilityActor("new-keeper")
        io.hizhengfu.cloud.sys.system.dao.UserDao["new-keeper"].enabled=false
        assertThrows(BadRequestException::class.java){h.register(e.id.value,responsibility(e),"museum")}
        responsibilityActor("operator");io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
        assertThrows(BadRequestException::class.java){service.resolveIssue(e.id.value,resolution("one"),"museum")}
        assertTrue(ReturnResponsibilityEvidence.entries(e).isEmpty())
    }

    @Test fun `partially resolved scope invalidates pending successor confirmation but retains effective responsibility for remaining objects`()=fixture { loan,e ->
        val damaged=inspection().copy(items=inspection().items.map {it.copy(damaged=true,anomalyDescription="实际损傷",protectiveMeasures="隔离保护")})
        statutory.recordInspection(e.id.value,damaged);approve(e);service.executeApprovedReturn(e.id.value);responsibilityPeople()
        val h=responsibilityService();val id=confirmResponsibility(e,responsibility(e).copy(collectionIds=setOf("one","two")))
        val caseId=incident(e);IncidentSourceTestSupport(incidents,LocalDateTime.now().minusHours(1),"report").close(caseId,"new-keeper","new-director")
        responsibilityActor("next-keeper")
        val pending=h.register(e.id.value,responsibility(e).copy(collectionIds=setOf("one","two"),previousId=id,expectedEffectiveId=id,keeperUserId="next-keeper",handedAt=LocalDateTime.now()),"museum")
        h.sign(e.id.value,pending,"KEEPER",responsibilitySignature,"museum")
        responsibilityActor("operator")
        fun result(cid:String)=resolution(cid).copy(incidentId=caseId,keeperUserId="new-keeper",leaderUserId="new-director",expectedResponsibilityId=id,approvedAt=LocalDateTime.now())
        service.resolveIssue(e.id.value,result("one"),"museum");assertFalse(loan.returned)
        responsibilityActor("new-director")
        assertThrows(BadRequestException::class.java){h.sign(e.id.value,pending,"DIRECTOR",responsibilitySignature,"museum")}
        assertEquals(id,ReturnResponsibilityEvidence.effective(e,"two")!!.record.id)
        assertEquals(setOf("two"),h.detail(e.id.value,"museum")["pendingCollectionIds"])
        responsibilityActor("operator");service.resolveIssue(e.id.value,result("two"),"museum");assertTrue(loan.returned)
    }
    @Test fun `changed original receipt cannot be used as successor basis`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();val v=responsibility(e);responsibilityActor("new-keeper")
        val originalKeeper=e.keeperUserId;e.keeperUserId="another"
        assertThrows(BadRequestException::class.java){responsibilityService().register(e.id.value,v,"museum")}
        e.keeperUserId=originalKeeper
        val a=ReturnResponsibilityEvidence.received(e);a.digest="modified"
        assertThrows(BadRequestException::class.java){responsibilityService().register(e.id.value,v,"museum")}
    }
    @Test fun `unarchived result cannot hide pending objects from successor scope`()=fixture { _,e ->
        receiveDamaged(e);responsibilityPeople();responsibilityActor("new-keeper")
        val v=responsibility(e)
        ReturnComplianceRecordDao.new {orgId=e.orgId;returnId=e.id.value;kind="RESOLUTION";content=JsonUtils.toJson(resolution("one"))!!}
        e.acceptedCollectionIds="one,two"
        assertThrows(BadRequestException::class.java){responsibilityService().register(e.id.value,v,"museum")}
        assertThrows(BadRequestException::class.java){statutory.records(e.id.value)}
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
    }

    @Test fun `revocation during director signature rolls back archive and leaves pending responsibility intact`() {
        var recordId=""
        fixture(afterCommit={db,_,returnId ->
            responsibilityActor("new-director")
            `when`(binder.resolveSingle("approval")).thenAnswer {
                io.hizhengfu.cloud.sys.system.dao.UserDao["new-director"].enabled=false
                "approval"
            }
            assertThrows(BadRequestException::class.java) {
                transaction(db) {responsibilityService().sign(returnId,recordId,"DIRECTOR",responsibilitySignature.copy(fileIds="approval"),"museum")}
            }
            transaction(db) {
                val e=CollectionReturnDao[returnId]
                assertTrue(io.hizhengfu.cloud.sys.system.dao.UserDao["new-director"].enabled==true)
                assertNull(ReturnResponsibilityEvidence.effective(e,"one"))
                assertEquals(setOf("KEEPER"),ReturnResponsibilityEvidence.entries(e).single().signatures.keys)
                assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
                assertTrue(CollectionPermanentArchiveDao.all().none {it.phase=="RESPONSIBILITY_DIRECTOR_$recordId"})
            }
        }) {_,e ->
            receiveDamaged(e);responsibilityPeople();responsibilityActor("new-keeper")
            recordId=responsibilityService().register(e.id.value,responsibility(e),"museum")
            responsibilityService().sign(e.id.value,recordId,"KEEPER",responsibilitySignature,"museum")
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
    class ResponsibilityTransactions
    private fun responsibilityRuntime(db:Database,body:(ReturnResponsibilityService,org.springframework.transaction.support.TransactionTemplate)->Unit) {
        val manager=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(fixtureDataSource,org.jetbrains.exposed.v1.core.DatabaseConfig {})
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.register(ResponsibilityTransactions::class.java)
            c.registerBean(ReturnResponsibilityService::class.java,java.util.function.Supplier {responsibilityService()})
            c.refresh();SpringContextHolder().setApplicationContext(c)
            body(c.getBean(ReturnResponsibilityService::class.java),org.springframework.transaction.support.TransactionTemplate(manager))
        }
    }
    companion object {
        @JvmStatic fun responsibilityFinalFaults()=listOf("REGISTER","KEEPER","DIRECTOR").flatMap {stage ->
            listOf("museum-disabled","return-org","return-inspection","return-accepted","return-deleted","receipt-content","new-content","new-deleted","old-file-metadata","new-file-foreign","actor-disabled","actor-role").map {org.junit.jupiter.params.provider.Arguments.of(stage,it)}
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("responsibilityFinalFaults")
    fun `responsibility actual archive verifies return source originals and current museum in every stage`(stage:String,fault:String) {
        lateinit var value:ReturnResponsibilityVo
        fixture(afterCommit={db,_,returnId ->
            responsibilityRuntime(db) {h,tx ->
                fun <T> read(body:()->T):T=tx.execute {body()}
                fun state()=read {listOf(CollectionReturns,Loans,Collections,CollectionOperationLocks,StorageMovements,ReturnComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles).map {t ->
                    t.selectAll().map {row->t.columns.map {row[it]}.toString()}.sorted()
                }}
                responsibilityActor("new-keeper")
                val recordId=if(stage=="REGISTER")null else h.register(returnId,value,"museum")
                if(stage=="DIRECTOR") {h.sign(returnId,recordId!!,"KEEPER",responsibilitySignature,"museum");responsibilityActor("new-director")}
                val before=state()
                val phase=if(stage=="REGISTER")"RESPONSIBILITY_P_%" else "RESPONSIBILITY_${stage}_%"
                read {
                    val sql=when(fault) {
                        "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                        "return-org"->"UPDATE ${CollectionReturns.tableName} SET org_id='other' WHERE id='$returnId'"
                        "return-inspection"->"UPDATE ${CollectionReturns.tableName} SET inspection=NULL WHERE id='$returnId'"
                        "return-accepted"->"UPDATE ${CollectionReturns.tableName} SET accepted_collection_ids='one,two' WHERE id='$returnId'"
                        "return-deleted"->"DELETE FROM ${CollectionReturns.tableName} WHERE id='$returnId'"
                        "receipt-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE biz_id='$returnId' AND phase='RECEIVED'"
                        "new-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                        "old-file-metadata"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET metadata='{}' WHERE archive_id IN (SELECT id FROM ${CollectionPermanentArchives.tableName} WHERE biz_id='$returnId' AND phase='RECEIVED')"
                        "new-file-foreign"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET org_id='other' WHERE id=NEW.id"
                        "actor-disabled"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=${if(System.getProperty("collection.test.pg.config")==null)"0" else "FALSE"} WHERE id='${if(stage=="DIRECTOR")"new-director" else "new-keeper"}'"
                        "actor-role"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='${if(stage=="DIRECTOR")"new-director" else "new-keeper"}' AND role_id='role:${if(stage=="DIRECTOR")CollectionConst.ROLE_DIRECTOR else CollectionConst.ROLE_STORAGE_HEAD}'"
                        else->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                    }
                    val event=if(fault=="new-file-foreign")"AFTER INSERT ON ${CollectionPermanentArchiveFiles.tableName} WHEN EXISTS (SELECT 1 FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.archive_id AND biz_id='$returnId' AND phase LIKE '$phase')"
                        else "AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_id='$returnId' AND NEW.phase LIKE '$phase'"
                    val transaction=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                    if(System.getProperty("collection.test.pg.config")==null)
                        transaction.exec("CREATE TRIGGER return_responsibility_final_fault $event BEGIN $sql; END")
                    else {
                        val target=if(fault=="new-file-foreign")CollectionPermanentArchiveFiles.tableName else CollectionPermanentArchives.tableName
                        val condition=if(fault=="new-file-foreign")
                            "EXISTS (SELECT 1 FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.archive_id AND biz_id='$returnId' AND phase LIKE '$phase')"
                        else "NEW.biz_id='$returnId' AND NEW.phase LIKE '$phase'"
                        transaction.exec("CREATE FUNCTION return_responsibility_final_fault_fn() RETURNS trigger AS \$\$ BEGIN IF pg_trigger_depth() = 1 AND ($condition) THEN $sql; END IF; RETURN NEW; END; \$\$ LANGUAGE plpgsql")
                        transaction.exec("CREATE TRIGGER return_responsibility_final_fault AFTER INSERT ON $target FOR EACH ROW EXECUTE FUNCTION return_responsibility_final_fault_fn()")
                    }
                }
                fun save()=if(stage=="REGISTER")h.register(returnId,value,"museum") else h.sign(returnId,recordId!!,stage,responsibilitySignature,"museum")
                assertThrows(BadRequestException::class.java){save()};assertEquals(before,state())
                read {
                    val transaction=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                    if(System.getProperty("collection.test.pg.config")==null)transaction.exec("DROP TRIGGER return_responsibility_final_fault")
                    else {
                        transaction.exec("DROP TRIGGER return_responsibility_final_fault ON ${if(fault=="new-file-foreign")CollectionPermanentArchiveFiles.tableName else CollectionPermanentArchives.tableName}")
                        transaction.exec("DROP FUNCTION return_responsibility_final_fault_fn()")
                    }
                }
                val id=save();assertEquals(id,save())
                read {assertEquals("keeper",CollectionReturnDao[returnId].keeperUserId);assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)}
            }
        }) {_,e ->
            receiveDamaged(e);responsibilityPeople();value=responsibility(e)
            io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false
        }
    }

    @Test fun `receipt comparison preserves legitimately absent optional document number`()=fixture { _,e ->
        e.sourceDocumentNo=null
        receiveDamaged(e);responsibilityPeople()
        val id=confirmResponsibility(e)
        assertEquals(id,ReturnResponsibilityEvidence.effective(e,"one")!!.record.id)
        assertNull(e.sourceDocumentNo)
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["one"].lifeStatus)
    }

}
