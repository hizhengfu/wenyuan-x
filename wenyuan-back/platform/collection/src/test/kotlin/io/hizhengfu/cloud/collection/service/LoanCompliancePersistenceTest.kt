package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.domain.FlowCanceledEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime

class LoanCompliancePersistenceTest {
    @org.junit.jupiter.api.io.TempDir lateinit var originalsDir:java.nio.file.Path
    private val scope=CollectionScopeResolver("single","museum")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val calendar=CollectionCalendarService(scope,binder,archives)
    private val archivePackets=mock(CollectionArchivePacketService::class.java)
    private val statutory=LoanStatutoryService(scope,mock(CollectionResponsibleUserService::class.java),binder,archives,calendar,archivePackets)
    private val locks=CollectionOperationLockService(listOf(CollectionIncidentOperationGuard()))
    private val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
    private val items=CollectionBusinessItemService()
    private val returns=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collections,mock(CollectionUserResolver::class.java),binder,items,locks,mock(CollectionPlacementService::class.java),ReturnStatutoryService(scope,mock(CollectionResponsibleUserService::class.java),binder,archives),archives)
    private val service=LoanService(mock(FlowInstanceService::class.java),scope,collections,binder,returns,items,locks,statutory,archives)
    private val out=LocalDateTime.of(2026,6,1,9,0)
    private val approved=LocalDateTime.of(2026,5,25,9,0)
    @BeforeEach fun prepare() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val user=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun cleanup()=SecurityContextHolder.clearContext()
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["missing","changed","replaced"])
    fun `unavailable loan original preserves business detail but suppresses verified conclusions and original download`(damage:String)=fixture {e->
        FileDao["identity"].path="identity.txt"
        java.nio.file.Files.writeString(originalsDir.resolve("identity.txt"),"original-identity")
        val validFileId="2107319685954252801"
        FileDao.new(validFileId){name="有效原件";path="image.txt";hash="valid-archive-image";size=14;ext="txt"}
        java.nio.file.Files.writeString(originalsDir.resolve("image.txt"),"original-image")
        val realArchives=CollectionPermanentArchiveService(binder,scope,io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalsDir.toString()),readAccess=unprotectedArchiveReadAccessProvider())
        val realService=LoanService(mock(FlowInstanceService::class.java),scope,collections,binder,returns,items,locks,statutory,realArchives)
        val archiveId=realArchives.seal("museum",CollectionConst.BIZ_LOAN,e.id.value,"SUBMITTED",statutory.proposal(e),"identity")
        val validId=realArchives.seal("museum",CollectionConst.BIZ_LOAN,e.id.value,"ADDITIONAL",mapOf("note" to "有效档案"),validFileId)
        val frozen=CollectionPermanentArchiveFileDao.all().associate {it.id.value to it.metadata}
        when(damage) {
            "missing"->java.nio.file.Files.delete(originalsDir.resolve("identity.txt"))
            "changed"->java.nio.file.Files.writeString(originalsDir.resolve("identity.txt"),"changed-identity")
            else->{
                java.nio.file.Files.copy(originalsDir.resolve("identity.txt"),originalsDir.resolve("copy.txt"))
                java.nio.file.Files.move(originalsDir.resolve("copy.txt"),originalsDir.resolve("identity.txt"),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val before=e.toModel()
        val detail=realService.get(e.id.value)!!
        assertEquals(before.title,detail.title);assertEquals(before.dossier,detail.dossier)
        assertEquals(setOf("one","two"),detail.collectionSummaries.map {it.id}.toSet())
        assertEquals(listOf("ADDITIONAL"),detail.archives.map {it["phase"]})
        assertEquals(1,detail.evidenceErrors.size);assertEquals(false,detail.proposalEditable)
        assertEquals(mapOf("evidenceUnavailable" to true),detail.obligations)
        assertThrows(BadRequestException::class.java){realService.requireArchivedOriginal(e.id.value,archiveId,"identity")}
        assertDoesNotThrow {realService.requireArchivedOriginal(e.id.value,validId,validFileId)}
        assertThrows(BadRequestException::class.java){realService.requireArchivedOriginal(e.id.value,validId,"identity")}
        assertEquals(before,e.toModel());assertEquals(frozen,CollectionPermanentArchiveFileDao.all().associate {it.id.value to it.metadata})
    }
    private fun dossier(kind:EnumLoanBorrowerKind=EnumLoanBorrowerKind.STATE_MUSEUM)=LoanDossierVo(
        exhibitionOverseas=false,borrowerKind=kind,borrowerRegistrationNo="borrower-reg",borrowerIdentityFileIds="identity",purpose=EnumLoanPurpose.EXHIBITION,purposeDescription="主题公益展览",
        keeperUserId="keeper",leaderUserId="leader",supervisionUserId="custodian1",agreedOutAt=out,agreedReturnAt=out.plusMonths(2),
        collectiveDecisionAt=out.minusMonths(2),collectiveDecisionFileIds="minutes",collectiveParticipantNames=setOf("甲","乙"),agreementSignedAt=out.minusMonths(2).plusDays(1),signedAgreementFileIds="agreement",agreedCollectionListFileIds="list",
        safetyResponsibilities="借入方安全主责，出借方监督",transportRequirements="专用囊匣全程监测",storageRequirements="恒温恒湿定岗安全保卫",useSite="借入馆第一展厅",carrierName="博物馆运输组",carrierCapabilityFileIds="carrier",transportSupervisorName="运输专员",transportSupervisorOrganization="出借馆",transportAssignmentFileIds="assignment",firstInspectionOn=out.toLocalDate().plusDays(9),inspectionIntervalDays=15,inspectionPlanFileIds="plan",
        transportStandardReference="GB/T 23862-2024",transportStandardApplicability="按本批文物材质和路线核对包装运输方案",transportPlanFileIds="transport-plan",
        assessments=listOf("one","two").map { LoanAssessmentVo(it,EnumLoanObjectKind.RELIC,"状态稳定",false,false,"展览必要", "包装运输风险可控","assessment") },
    )
    private fun fixture(afterCommit:((org.springframework.transaction.PlatformTransactionManager,String,Boolean)->Unit)?=null,block:(LoanDao)->Unit) {
        val pg=System.getProperty("collection.test.pg.config")?.let { config ->
            val properties=java.util.Properties().apply { java.nio.file.Files.newInputStream(java.nio.file.Path.of(config)).use(::load) }
            org.postgresql.ds.PGSimpleDataSource().apply {setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password")}
        }
        val schema="loan_compliance_"+java.util.UUID.randomUUID().toString().replace("-","")
        pg?.connection?.use {it.createStatement().use {s -> s.execute("CREATE SCHEMA $schema")}}
        pg?.currentSchema=schema
        val file=if(afterCommit!=null && pg==null)java.nio.file.Files.createTempFile("loan-transaction-",".db") else null
        val db=if(pg==null) Database.connect(file?.let {"jdbc:sqlite:$it"} ?: "jdbc:sqlite:file:loan-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC") else Database.connect(pg)
        var createdId=""
        try { transaction(db) {
            SchemaUtils.create(CollectionLedgerLogs,CollectionIncidentItems,Collections,CollectionOrgs,Loans,LoanComplianceRecords,CollectionReturns,CollectionBusinessItems,CollectionOperationLocks,StorageMovements,CollectionTransfers,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionCalendarVersions,CollectionCatalogVersions,Files,FlowInstanceLogs,Warehouses)
            CollectionOrgDao.new("museum") { name="出借博物馆";code="M";ownership=EnumOwnership.STATE }
            CollectionOrgDao.new("other") { name="其他馆";code="O";ownership=EnumOwnership.STATE }
            WarehouseDao.new("warehouse") { orgId="museum";name="一库";sceneType=EnumLocationScene.STORAGE;type=EnumWarehouseType.WAREHOUSE }
            listOf("identity","minutes","draft-agreement","agreement","list","carrier","assignment","plan","transport-plan","assessment","submission","approval","review","authorization","safety","packing-check","sign","image","monitor").forEach { key -> FileDao.new(key) { name="$key.pdf";path="/files/$key.pdf";hash=key;size=100;ext="pdf" } }
            listOf("one","two").forEach { key ->
                val c=CollectionDao.new(key) { orgId="museum";totalRegNo=key;name="藏品$key";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="warehouse";preserveState=EnumPreserve.A;catalogStatus=EnumCatalogStatus.APPROVED;registeredAt=out.minusYears(1);source=EnumSource.OLD }
                CollectionCatalogVersionDao.new { orgId="museum";collectionId=key;catalogId="catalog-$key";version=1;catalogStatus=EnumCatalogStatus.APPROVED;content=CollectionCatalogMapper.fromCollection(c.toModel()) }
            }
            originalReaderSchema();originalReader("operator",CollectionConst.STORAGE)
            val id=service.save(LoanVo(title="主题展",borrower="借入博物馆",protocolNo="XY-001",collectionIds="one,two",operator="operator",reviewer="reviewer",loanDate=out.toLocalDate(),dossier=dossier()))
            createdId=id
            block(LoanDao[id])
        };afterCommit?.invoke(org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(
            pg ?: org.sqlite.SQLiteDataSource().apply {url=db.url}),createdId,pg!=null) } finally {
            pg?.connection?.use {it.createStatement().use {s -> s.execute("DROP SCHEMA $schema CASCADE")}}
            file?.let(java.nio.file.Files::deleteIfExists)
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("new,revoked", "edit,revoked", "new,disabled", "edit,disabled",
        "new,moved", "edit,moved", "new,orgDisabled", "edit,orgDisabled")
    fun `loan draft rejects obsolete login authority without changing records`(operation:String,change:String)=fixture { e ->
        originalReaderSchema();if(io.hizhengfu.cloud.sys.system.dao.UserDao.findById("operator")==null)originalReader("operator",CollectionConst.STORAGE)
        val body=e.toModel().copy(id=if(operation=="new")null else e.id.value,borrower="变更借用单位")
        val before=e.toModel()
        val count=LoanDao.all().count()
        val bound=CollectionBusinessItemDao.all().count()
        when(change) {
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "operator" }
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
            "orgDisabled"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        }
        assertThrows(BadRequestException::class.java) {service.save(body)}
        assertEquals(count,LoanDao.all().count());assertEquals(bound,CollectionBusinessItemDao.all().count())
        assertEquals(before,e.toModel())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("first,revoked", "retry,revoked", "first,disabled", "retry,disabled",
        "first,moved", "retry,moved", "first,orgDisabled", "retry,orgDisabled")
    fun `loan execution rejects obsolete login authority including idempotent retries`(operation:String,change:String)=fixture { e ->
        originalReaderSchema();if(io.hizhengfu.cloud.sys.system.dao.UserDao.findById("operator")==null)originalReader("operator",CollectionConst.STORAGE)
        approve(e);statutory.recordHandover(e.id.value,handover())
        if(operation=="retry")service.executeOut(e.id.value,CollectionOutboundExecutionVo())
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        e.refresh(flush=false)
        val before=e.toModel()
        val movements=StorageMovementDao.all().count()
        val snapshots=CollectionPermanentArchiveDao.all().count()
        when(change) {
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "operator" }
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
            "orgDisabled"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        }
        assertThrows(BadRequestException::class.java) {service.executeOut(e.id.value,CollectionOutboundExecutionVo())}
        assertEquals(before,e.toModel())
        assertEquals(movements,StorageMovementDao.all().count());assertEquals(snapshots,CollectionPermanentArchiveDao.all().count())
    }

    @Test fun `loan detail edit hint follows current storage duty rather than old login`()=fixture { e ->
        SchemaUtils.create(CollectionCategories,CollectionMedias,CollectionAttachments)
        actorRole("operator","READ",setOf(CollectionConst.SELECT))
        assertEquals(true,service.get(e.id.value)!!.proposalEditable)
        io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere {
            (userId eq "operator") and (roleId eq "role:FILE_operator")
        }
        assertEquals(false,service.get(e.id.value)!!.proposalEditable)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("administrative,first,revoked", "administrative,first,disabled", "administrative,first,moved", "administrative,first,orgDisabled",
        "administrative,retry,revoked", "administrative,retry,disabled", "administrative,retry,moved", "administrative,retry,orgDisabled",
        "handover,first,revoked", "handover,first,disabled", "handover,first,moved", "handover,first,orgDisabled",
        "handover,retry,revoked", "handover,retry,disabled", "handover,retry,moved", "handover,retry,orgDisabled",
        "monitoring,first,revoked", "monitoring,first,disabled", "monitoring,first,moved", "monitoring,first,orgDisabled",
        "monitoring,retry,revoked", "monitoring,retry,disabled", "monitoring,retry,moved", "monitoring,retry,orgDisabled")
    fun `loan statutory writes reject obsolete current duties before first or repeated records`(operation:String,stage:String,change:String)=fixture { e ->
        if(operation=="handover")approve(e)
        if(operation=="monitoring")execute(e)
        val monitoring=LoanMonitoringVo(EnumLoanMonitoringKind.SAFETY_CHECK,out.plusDays(1),setOf("one","two"),
            "custodian1","借用方代表","合成双方安全检查","monitor",safetyConfirmed=true)
        fun write()=when(operation) {
            "administrative"->statutory.recordAdministrative(e.id.value,admin())
            "handover"->statutory.recordHandover(e.id.value,handover())
            else->statutory.recordMonitoring(e.id.value,monitoring)
        }
        if(stage=="retry")write()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        e.refresh(flush=false)
        val before=e.toModel()
        val records=LoanComplianceRecordDao.all().count()
        val snapshots=CollectionPermanentArchiveDao.all().count()
        val files=CollectionPermanentArchiveFileDao.all().count()
        when(change) {
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere {userId eq "operator"}
            "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
            "moved"->io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
            "orgDisabled"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        }
        assertThrows(BadRequestException::class.java) {write()}
        assertEquals(before,e.toModel())
        assertEquals(records,LoanComplianceRecordDao.all().count())
        assertEquals(snapshots,CollectionPermanentArchiveDao.all().count())
        assertEquals(files,CollectionPermanentArchiveFileDao.all().count())
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
    class LoanTransactions

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("administrative,revoked", "administrative,disabled", "administrative,moved", "administrative,orgDisabled",
        "handover,revoked", "handover,disabled", "handover,moved", "handover,orgDisabled",
        "monitoring,revoked", "monitoring,disabled", "monitoring,moved", "monitoring,orgDisabled")
    fun `statutory archive time authority changes roll back records and permit clean retry`(operation:String,change:String) {
        fixture(afterCommit={manager,id,postgres ->
            val tx=org.springframework.transaction.support.TransactionTemplate(manager)
            org.springframework.context.annotation.AnnotationConfigApplicationContext().use {context ->
                context.beanFactory.registerSingleton("transactionManager",manager)
                context.register(LoanTransactions::class.java)
                context.registerBean(LoanStatutoryService::class.java,java.util.function.Supplier {
                    LoanStatutoryService(scope,mock(CollectionResponsibleUserService::class.java),binder,archives,calendar,archivePackets)
                })
                context.refresh()
                val actual=context.getBean(LoanStatutoryService::class.java)
                fun state()=tx.execute {
                    listOf(Loans,LoanComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                        CollectionOrgs,io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers).map {table ->
                        table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()
                    }
                }
                val before=state()
                val phase=when(operation) {"administrative"->"ADMINISTRATIVE_%";"handover"->"HANDOVER_%";else->"MONITORING_%"}
                val mutation=when(change) {
                    "revoked"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='operator'"
                    "disabled"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=${if(postgres)"false" else "0"} WHERE id='operator'"
                    "moved"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET group_id='other' WHERE id='operator'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                }
                tx.execute {
                    val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                    if(postgres) {
                        current.exec("CREATE FUNCTION loan_current_fault_fn() RETURNS TRIGGER LANGUAGE plpgsql AS ${"$"}fault${"$"} BEGIN $mutation; RETURN NEW; END ${"$"}fault${"$"}")
                        current.exec("CREATE TRIGGER loan_current_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} FOR EACH ROW WHEN (NEW.biz_id='$id' AND NEW.phase LIKE '$phase') EXECUTE FUNCTION loan_current_fault_fn()")
                    } else current.exec("CREATE TRIGGER loan_current_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_id='$id' AND NEW.phase LIKE '$phase' BEGIN $mutation; END")
                }
                fun write()=when(operation) {
                    "administrative"->actual.recordAdministrative(id,admin())
                    "handover"->actual.recordHandover(id,handover())
                    else->actual.recordMonitoring(id,LoanMonitoringVo(EnumLoanMonitoringKind.SAFETY_CHECK,out.plusDays(1),
                        setOf("one","two"),"custodian1","借用方代表","合成双方安全检查","monitor",safetyConfirmed=true))
                }
                assertThrows(BadRequestException::class.java){write()}
                assertEquals(before,state(),"业务、档案和触发器修改必须一起回滚")
                tx.execute {
                    val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                    current.exec("DROP TRIGGER loan_current_fault"+if(postgres)" ON ${CollectionPermanentArchives.tableName}" else "")
                    if(postgres)current.exec("DROP FUNCTION loan_current_fault_fn()")
                }
                val result=write();assertEquals(result,write())
            }
        }) { e ->
            if(operation=="handover")approve(e)
            if(operation=="monitoring")execute(e)
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("out,revoked", "out,disabled", "out,moved", "out,orgDisabled")
    fun `outbound archive time authority changes roll back movement and permit clean retry`(operation:String,change:String) {
        fixture(afterCommit={manager,id,postgres ->
            val tx=org.springframework.transaction.support.TransactionTemplate(manager)
            org.springframework.context.annotation.AnnotationConfigApplicationContext().use {context ->
                context.beanFactory.registerSingleton("transactionManager",manager)
                context.register(LoanTransactions::class.java)
                context.registerBean(LoanService::class.java,java.util.function.Supplier {
                    LoanService(mock(FlowInstanceService::class.java),scope,collections,binder,returns,items,locks,statutory,archives)
                })
                context.refresh()
                val actual=context.getBean(LoanService::class.java)
                fun state()=tx.execute {
                    listOf(Collections,StorageMovements,CollectionReturns,CollectionBusinessItems,CollectionOperationLocks,Loans,LoanComplianceRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                        CollectionOrgs,io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers).map {table ->
                        table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()
                    }
                }
                val before=state()
                val phase="OUT_EXECUTED"
                val mutation=when(change) {
                    "revoked"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='operator'"
                    "disabled"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=${if(postgres)"false" else "0"} WHERE id='operator'"
                    "moved"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET group_id='other' WHERE id='operator'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                }
                tx.execute {
                    val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                    if(postgres) {
                        current.exec("CREATE FUNCTION loan_current_fault_fn() RETURNS TRIGGER LANGUAGE plpgsql AS ${"$"}fault${"$"} BEGIN $mutation; RETURN NEW; END ${"$"}fault${"$"}")
                        current.exec("CREATE TRIGGER loan_current_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} FOR EACH ROW WHEN (NEW.biz_id='$id' AND NEW.phase LIKE '$phase') EXECUTE FUNCTION loan_current_fault_fn()")
                    } else current.exec("CREATE TRIGGER loan_current_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_id='$id' AND NEW.phase LIKE '$phase' BEGIN $mutation; END")
                }
                fun write()=actual.executeOut(id,CollectionOutboundExecutionVo())
                assertThrows(BadRequestException::class.java){write()}
                assertEquals(before,state(),"业务、档案和触发器修改必须一起回滚")
                tx.execute {
                    val current=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
                    current.exec("DROP TRIGGER loan_current_fault"+if(postgres)" ON ${CollectionPermanentArchives.tableName}" else "")
                    if(postgres)current.exec("DROP FUNCTION loan_current_fault_fn()")
                }
                val result=write();assertEquals(result,write())
            }
        }) { e ->
            approve(e);statutory.recordHandover(e.id.value,handover())
        }
    }

    private fun approve(e:LoanDao) {
        e.flowInstanceId="flow-${e.id.value}";e.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire("museum",CollectionConst.BIZ_LOAN,e.id.value,splitCollectionIds(e.collectionIds))
        statutory.actors(e).entries.forEachIndexed { i,(node,user) -> FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="task-$node";nodeKey=node;action="APPROVE";operator=user;comment="本人核实集体研究、协议和原件";createTime=approved.plusMinutes(i.toLong()) } }
        service.onFlowCompleted(e,FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,"借用",e.id.value,CollectionConst.BIZ_LOAN,emptyMap(),"operator","经办"))
    }
    private fun admin(level:EnumAuthorityLevel=EnumAuthorityLevel.COMPETENT,action:EnumAdministrativeAction=EnumAdministrativeAction.APPROVAL,submitted:LocalDate=LocalDate.of(2026,4,20),issued:LocalDate=submitted.plusDays(1))=LoanAdministrativeVo(level,action,"文物行政机关","文物文号",submitted,issued,"submission","approval",setOf("one","two"))
    private fun handover()=LoanHandoverVo(approved.plusDays(1),"keeper","review",out,setOf("custodian1","custodian2"),setOf("接收甲","接收乙"),"authorization",true,listOf(RequisitionConditionVo("one","釉面完整",false),RequisitionConditionVo("two","原底部细痕见图",true,"image")),true,true,"safety","sign",appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="packing-check")
    private fun execute(e:LoanDao) { approve(e);statutory.recordHandover(e.id.value,handover());service.executeOut(e.id.value,CollectionOutboundExecutionVo()) }

    @Test fun `loan safety tasks follow actual outbound monitoring and filing evidence`()=fixture {e ->
        val source=CollectionLoanSafetyTaskSource(statutory)
        val at=out.plusDays(10)
        assertTrue(source.tasks("museum",at).isEmpty(),"草案不得产生在借催办")
        execute(e)
        assertTrue(source.tasks("other",at).isEmpty(),"外馆查询不得看到本馆借用")
        val initial=source.tasks("museum",at)
        assertEquals(2,initial.count {it.kind=="RETURN_DUE"})
        assertEquals(2,initial.count {it.kind=="INSPECTION_DUE" && it.status=="OVERDUE"})
        assertEquals(1,initial.count {it.kind=="ADMIN_FILING"})
        val first=initial.single {it.kind=="INSPECTION_DUE" && it.collectionId=="one"}
        statutory.recordMonitoring(e.id.value,LoanMonitoringVo(EnumLoanMonitoringKind.SAFETY_CHECK,out.plusDays(9),setOf("one"),
            "custodian1","借用方代表","双方现场检查安全","monitor",safetyConfirmed=true))
        val updated=source.tasks("museum",at)
        assertEquals("SCHEDULED",updated.single {it.key==first.key}.status)
        assertEquals("OVERDUE",updated.single {it.kind=="INSPECTION_DUE" && it.collectionId=="two"}.status)
        statutory.recordAdministrative(e.id.value,admin(action=EnumAdministrativeAction.FILING,submitted=out.toLocalDate().plusDays(1)))
        assertTrue(source.tasks("museum",at).none {it.kind=="ADMIN_FILING"})
    }
    @Test fun `agreement return hour and date-only recall use their distinct precise deadlines`()=fixture { e ->
        execute(e)
        val source=CollectionLoanSafetyTaskSource(statutory)
        val agreement=e.dossier!!.agreedReturnAt
        fun deadline(id:String,at:LocalDateTime)=source.tasks("museum",at).single {it.kind=="RETURN_DUE" && it.collectionId==id}
        val before=agreement.minusMinutes(1)
        assertEquals(false,statutory.obligations(e,before)["returnOverdue"])
        assertEquals(agreement,deadline("two",before).dueAt)
        assertEquals("SCHEDULED",deadline("two",before).status)
        assertEquals(true,statutory.obligations(e,agreement)["returnOverdue"])
        assertEquals("OVERDUE",deadline("two",agreement).status)
        statutory.recordMonitoring(e.id.value,LoanMonitoringVo(EnumLoanMonitoringKind.RECALL,out.plusDays(5),setOf("one"),
            "custodian1","借用方代表","提前催还","monitor",recallReturnBy=out.plusDays(7).toLocalDate()))
        val recallCutoff=out.plusDays(8).toLocalDate().atStartOfDay()
        assertEquals(recallCutoff,deadline("one",recallCutoff.minusMinutes(1)).dueAt)
        assertEquals("SCHEDULED",deadline("one",recallCutoff.minusMinutes(1)).status)
        assertEquals("OVERDUE",deadline("one",recallCutoff).status)
        assertEquals("SCHEDULED",deadline("two",recallCutoff).status)
    }
    private fun extension(e:LoanDao,outbound:Boolean=true):LoanOverseasExtensionVo {
        originalReaderSchema();if(io.hizhengfu.cloud.sys.system.dao.UserDao.findById("operator")==null)originalReader("operator",CollectionConst.STORAGE)
        e.dossier=e.dossier!!.copy(borrowerKind=EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION,exhibitionOverseas=true,
            collectiveDecisionAt=out.minusMonths(7),agreementSignedAt=out.minusMonths(4),
            overseasProcedureFileIds="approval",overseasRequiredApprovalLevels=setOf(EnumAuthorityLevel.NATIONAL),
            overseasExhibition=LoanOverseasExhibitionVo(out.toLocalDate(),out.plusMonths(2).toLocalDate(),2,0,"list","assessment",
                listOf("one","two").map {LoanOverseasObjectVo(it,false,false,"assessment")},out.minusMonths(7).toLocalDate(),"draft-agreement"))
        val aid=statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.NATIONAL,submitted=out.minusMonths(6).toLocalDate(),issued=out.minusMonths(5).toLocalDate()))
        if(outbound)execute(e)
        return LoanOverseasExtensionVo(statutory.extensionRevision(e),null,aid,"特殊展览交流需要","文物行政机关","延期批准01",out.plusDays(15).toLocalDate(),out.plusDays(20).toLocalDate(),out.plusMonths(3).toLocalDate(),out.plusMonths(4),out.plusDays(21).toLocalDate(),"submission","approval","agreement")
    }
    @Test fun `overseas extension preserves originals and recall remains earlier than revised return`()=fixture { e ->
        val v=extension(e);val original=e.dossier;val originalDate=e.returnDate
        val previousProtectionSource=CollectionFragilityEvidence.source(CollectionConst.BIZ_LOAN,e.id.value)!!
        val id=statutory.recordExtension(e.id.value,v,"museum")
        assertEquals(id,statutory.recordExtension(e.id.value,v,"museum"))
        val current=LoanDao[e.id];assertEquals(original,current.dossier);assertEquals(originalDate,current.returnDate)
        assertEquals(v.agreedReturnAt,statutory.obligations(current)["effectiveReturnDueAt"])
        assertEquals(v.agreedReturnAt,LoanOverseasExtensionEvidence.effectiveReturnAt(current))
        val protectionSource=CollectionFragilityEvidence.source(CollectionConst.BIZ_LOAN,current.id.value)!!
        assertEquals(v.agreedReturnAt,protectionSource.returnAt)
        assertNotEquals(previousProtectionSource.snapshot,protectionSource.snapshot)
        assertEquals(original!!.agreedReturnAt,statutory.obligations(current)["returnDueAt"])
        statutory.recordMonitoring(current.id.value,LoanMonitoringVo(EnumLoanMonitoringKind.RECALL,out.plusMonths(1),setOf("one"),"custodian1","借用甲","提前收回","monitor",recallReturnBy=out.plusMonths(1).plusDays(1).toLocalDate()))
        val deadlines=statutory.obligations(current)["returnDeadlines"] as List<Map<String,Any?>>
        assertEquals(out.plusMonths(1).plusDays(1).toLocalDate(),deadlines.single {it["collectionId"]=="one"}["dueOn"])
        assertEquals(v.agreedReturnAt.toLocalDate(),deadlines.single {it["collectionId"]=="two"}["dueOn"])
        assertEquals(out.plusMonths(1).plusDays(2).toLocalDate().atStartOfDay(),LoanReturnDeadlineEvidence.forCollection(current,"one"))
        assertEquals(v.agreedReturnAt,LoanReturnDeadlineEvidence.forCollection(current,"two"))
        assertEquals(1,statutory.extensionViews(current).size)
        val printed=LoanCompliancePrintFields.build(current.toModel().copy(overseasExtensions=statutory.extensionViews(current))).associate {it.label to it.value}
        assertEquals("2026-08-01 09:00:00",printed["协议约定归还时间"])
        assertEquals("2026-10-01 09:00:00",printed["出境延期1：补充协议归还时间"])
    }
    @Test fun `overseas administrative application uses draft before approval and formal agreement after approval`()=fixture { e ->
        extension(e)
        val current=LoanDao[e.id]
        val approval=statutory.administrativeViews(current.id.value).single()["content"] as LoanAdministrativeVo
        assertTrue(approval.submittedOn.isBefore(current.dossier!!.agreementSignedAt.toLocalDate()))
        assertTrue(approval.issuedOn.isBefore(current.dossier!!.agreementSignedAt.toLocalDate()))
        assertTrue("draft-agreement" in statutory.allFiles(current))
        val fields=LoanCompliancePrintFields.build(current.toModel()).associate {it.label to it.value}
        assertEquals("draft-agreement",fields["出境草签协议草案原件"])
        current.dossier=current.dossier!!.copy(agreementSignedAt=out.minusMonths(7))
        assertThrows(BadRequestException::class.java) {statutory.requireAdministrativeBeforeOut(current)}
    }
    @Test fun `overseas approval rejects missing draft premature submission and premature formal signature`()=fixture { e ->
        extension(e)
        val current=LoanDao[e.id];current.outExecuted=false
        val d=current.dossier!!;val count=LoanComplianceRecordDao.count()
        val a=admin(EnumAuthorityLevel.NATIONAL,submitted=out.minusMonths(6).toLocalDate(),issued=out.minusMonths(5).toLocalDate())
        current.dossier=d.copy(overseasExhibition=d.overseasExhibition!!.copy(draftAgreementSignedOn=null))
        assertThrows(BadRequestException::class.java) {statutory.recordAdministrative(current.id.value,a)}
        current.dossier=d.copy(overseasExhibition=d.overseasExhibition.copy(draftAgreementFileIds=""))
        assertThrows(BadRequestException::class.java) {statutory.recordAdministrative(current.id.value,a)}
        current.dossier=d
        assertThrows(BadRequestException::class.java) {statutory.recordAdministrative(current.id.value,a.copy(submittedOn=out.minusMonths(8).toLocalDate()))}
        current.dossier=d.copy(agreementSignedAt=out.minusMonths(7))
        assertThrows(BadRequestException::class.java) {statutory.recordAdministrative(current.id.value,a)}
        assertEquals(count,LoanComplianceRecordDao.count())
    }
    private fun filing(e:LoanDao)=LoanOverseasFilingVo(statutory.extensionRevision(e),EnumOverseasFilingKind.AGREEMENT,
        authorityName="国家文物局（合成）",submissionNo="DEV-FILING",submittedOn=out.minusMonths(4).toLocalDate().plusDays(1),submissionReceiptFileIds="submission")
    @Test fun `overseas agreement filing is allowed before outbound and late correction remains in history`()=fixture {e ->
        extension(e,false);var current=LoanDao[e.id];val original=current.dossier
        val value=filing(current).copy(submittedOn=out.minusMonths(2).toLocalDate())
        val id=statutory.recordOverseasFiling(current.id.value,value,"museum")
        assertEquals(id,statutory.recordOverseasFiling(current.id.value,value,"museum"))
        current=LoanDao[e.id];assertFalse(current.outExecuted);assertEquals(original,current.dossier)
        val actual=statutory.overseasFilingViews(current).single()["content"] as LoanOverseasFilingRecordVo
        assertTrue(actual.late);assertEquals(out.minusMonths(3).toLocalDate(),actual.dueOn)
        val corrected=filing(current).copy(previousId=id,correctionReason="核实原接收日期，历次材料保留")
        statutory.recordOverseasFiling(current.id.value,corrected,"museum");current=LoanDao[e.id]
        val obligations=statutory.obligations(current)["overseasFilings"] as List<Map<String,Any?>>
        val agreement=obligations.single {it["kind"]==EnumOverseasFilingKind.AGREEMENT}
        assertEquals(false,agreement["late"]);assertEquals(listOf(id),agreement["lateHistoryIds"])
        assertEquals(2,statutory.overseasFilingViews(current).size)
    }
    @Test fun `conclusion needs actual end and complete material and does not replace reentry or permit another extension`()=fixture {e ->
        val ext=extension(e);var current=LoanDao[e.id]
        val end=statutory.recordExhibitionEnd(current.id.value,LoanExhibitionEndVo(statutory.extensionRevision(current),out.plusDays(20).toLocalDate(),"assessment"),"museum")
        current=LoanDao[e.id]
        val v=filing(current).copy(kind=EnumOverseasFilingKind.CONCLUSION,endingRecordId=end,actualExhibitionEndedOn=out.plusDays(20).toLocalDate(),
            submittedOn=out.plusMonths(3).toLocalDate(),endingEvidenceFileIds="assessment",conclusionFormFileIds="list",conclusionReportFileIds="plan",audiovisualFileIds="image")
        assertThrows(BadRequestException::class.java) {statutory.recordOverseasFiling(current.id.value,v.copy(audiovisualFileIds=null),"museum")}
        assertThrows(BadRequestException::class.java) {statutory.recordOverseasFiling(current.id.value,v.copy(actualExhibitionEndedOn=null),"museum")}
        statutory.recordOverseasFiling(current.id.value,v,"museum");current=LoanDao[e.id]
        val row=statutory.overseasFilingViews(current).single()["content"] as LoanOverseasFilingRecordVo
        assertEquals(out.plusDays(20).toLocalDate().plusMonths(2),row.dueOn);assertTrue(row.late)
        assertEquals(listOf("one","two"),statutory.obligations(current)["pendingReentryCollectionIds"])
        assertFalse(statutory.extensionAvailable(current))
        assertThrows(BadRequestException::class.java) {statutory.recordExtension(current.id.value,ext.copy(sourceRevision=statutory.extensionRevision(current)),"museum")}
        val printed=LoanCompliancePrintFields.build(current.toModel().copy(overseasFilings=statutory.overseasFilingViews(current))).associate {it.label to it.value}
        assertEquals("image",printed["出境报送1：展览音像资料"])
    }
    @Test fun `overseas filings reject stale foreign future early dates and destroyed originals`()=fixture {e ->
        extension(e,false);val current=LoanDao[e.id];val v=filing(current);val count=LoanComplianceRecordDao.count()
        listOf(v.copy(sourceRevision="stale"),v.copy(authorityLevel=EnumAuthorityLevel.PROVINCE),v.copy(submittedOn=out.minusMonths(5).toLocalDate()),
            v.copy(submittedOn=java.time.LocalDate.now().plusDays(1)),v.copy(submissionReceiptFileIds=""),v.copy(previousId="other")).forEach {bad->
            assertThrows(BadRequestException::class.java) {statutory.recordOverseasFiling(current.id.value,bad,"museum")}}
        assertThrows(BadRequestException::class.java) {statutory.recordOverseasFiling(current.id.value,v,"other")}
        assertEquals(count,LoanComplianceRecordDao.count())
        val id=statutory.recordOverseasFiling(current.id.value,v,"museum")
        CollectionPermanentArchiveDao.all().single {it.phase=="OVERSEAS_FILING_$id"}.snapshot="{}"
        assertThrows(BadRequestException::class.java) {statutory.overseasFilingViews(LoanDao[e.id])}
        assertThrows(BadRequestException::class.java) {statutory.obligations(LoanDao[e.id])}
    }
    @Test fun `actual exhibition end starts pending filing deadline without claiming submission`()=fixture {e ->
        extension(e);var current=LoanDao[e.id];val dossier=current.dossier
        val value=LoanExhibitionEndVo(statutory.extensionRevision(current),out.plusDays(10).toLocalDate(),"assessment")
        val id=statutory.recordExhibitionEnd(current.id.value,value,"museum")
        assertEquals(id,statutory.recordExhibitionEnd(current.id.value,value,"museum"));current=LoanDao[e.id]
        fun duty(day:java.time.LocalDate)=(statutory.obligations(current,day)["overseasFilings"] as List<Map<String,Any?>>).single {it["kind"]==EnumOverseasFilingKind.CONCLUSION}
        val due=value.actualEndedOn.plusMonths(2)
        assertEquals(due,duty(due)["dueOn"]);assertEquals(false,duty(due)["overdue"])
        assertEquals(true,duty(due.plusDays(1))["overdue"]);assertNull(duty(due)["submittedOn"])
        assertEquals(false,duty(due)["endDateNeedsVerification"])
        assertEquals(dossier,current.dossier);assertFalse(statutory.extensionAvailable(current))
        assertDoesNotThrow {LoanExhibitionEndEvidence.requireDisplayStart(current,value.actualEndedOn.atTime(12,0))}
        assertThrows(BadRequestException::class.java) {LoanExhibitionEndEvidence.requireDisplayStart(current,value.actualEndedOn.plusDays(1).atStartOfDay())}
        assertTrue(statutory.overseasFilingViews(current).isEmpty())
    }
    @Test fun `corrected end retains submitted originals but requires filing against latest factual basis`()=fixture {e ->
        extension(e);var current=LoanDao[e.id]
        val endRequest=LoanExhibitionEndVo(statutory.extensionRevision(current),out.plusDays(10).toLocalDate(),"assessment")
        val end=statutory.recordExhibitionEnd(current.id.value,endRequest,"museum");current=LoanDao[e.id]
        val v=filing(current).copy(kind=EnumOverseasFilingKind.CONCLUSION,endingRecordId=end,actualExhibitionEndedOn=endRequest.actualEndedOn,
            submittedOn=out.plusMonths(3).toLocalDate(),endingEvidenceFileIds="assessment",conclusionFormFileIds="list",conclusionReportFileIds="plan",audiovisualFileIds="image")
        val report=statutory.recordOverseasFiling(current.id.value,v,"museum");current=LoanDao[e.id]
        val next=endRequest.copy(sourceRevision=statutory.extensionRevision(current),previousId=end,correctionReason="核实实际结束日期",actualEndedOn=out.plusDays(11).toLocalDate())
        val nextId=statutory.recordExhibitionEnd(current.id.value,next,"museum");current=LoanDao[e.id]
        val duty=(statutory.obligations(current,out.plusMonths(4).toLocalDate())["overseasFilings"] as List<Map<String,Any?>>).single {it["kind"]==EnumOverseasFilingKind.CONCLUSION}
        assertEquals(next.actualEndedOn.plusMonths(2),duty["dueOn"]);assertEquals(true,duty["filingNeedsReverification"]);assertNull(duty["submittedOn"])
        assertEquals(listOf(report),duty["lateHistoryIds"]);assertEquals(2,statutory.exhibitionEndViews(current).size)
        assertThrows(BadRequestException::class.java) {statutory.recordOverseasFiling(current.id.value,v.copy(sourceRevision=statutory.extensionRevision(current),previousId=report,correctionReason="重核"),"museum")}
        statutory.recordOverseasFiling(current.id.value,v.copy(sourceRevision=statutory.extensionRevision(current),previousId=report,correctionReason="按更新后的结束依据报送",endingRecordId=nextId,actualExhibitionEndedOn=next.actualEndedOn),"museum")
        assertEquals(2,statutory.overseasFilingViews(LoanDao[e.id]).size)
    }
    @Test fun `actual end rejects future early stale wrong scope and altered original`()=fixture {e ->
        extension(e);val current=LoanDao[e.id];val v=LoanExhibitionEndVo(statutory.extensionRevision(current),out.plusDays(10).toLocalDate(),"assessment")
        val count=LoanComplianceRecordDao.count()
        listOf(v.copy(sourceRevision="stale"),v.copy(previousId="outside"),v.copy(actualEndedOn=java.time.LocalDate.now().plusDays(1)),v.copy(actualEndedOn=out.minusDays(1).toLocalDate()),v.copy(evidenceFileIds="")).forEach {bad->
            assertThrows(BadRequestException::class.java) {statutory.recordExhibitionEnd(current.id.value,bad,"museum")}}
        assertThrows(BadRequestException::class.java) {statutory.recordExhibitionEnd(current.id.value,v,"other")}
        assertEquals(count,LoanComplianceRecordDao.count())
        val id=statutory.recordExhibitionEnd(current.id.value,v,"museum")
        CollectionPermanentArchiveDao.all().single {it.phase=="EXHIBITION_END_$id"}.snapshot="{}"
        assertThrows(BadRequestException::class.java) {statutory.exhibitionEndViews(LoanDao[e.id])}
        assertThrows(BadRequestException::class.java) {statutory.obligations(LoanDao[e.id])}
    }
    private fun port(e:LoanDao,approvalId:String)=LoanPortClearanceVo(statutory.extensionRevision(e),EnumLoanPortStage.EXIT,approvalId,
        collectionIds=setOf("one","two"),reviewAgency="合成进出境审核机构",reviewedAt=out.plusDays(1),certificateNo="EXIT-CERT",certificateFileIds="approval",
        identifiers=mapOf("one" to "MARK-1","two" to "MARK-2"),portName="合成指定口岸",designatedPortFileIds="assessment",customsAt=out.plusDays(2),customsDocumentNo="EXIT-CUSTOMS",customsFileIds="submission")
    @Test fun `port originals support partial reentry and retain unfinished customs duties after returned flag`()=fixture {e ->
        val extension=extension(e);var current=LoanDao[e.id];val proposal=statutory.proposal(current)
        val exit=port(current,extension.originalApprovalId)
        val exitId=statutory.recordPort(current.id.value,exit,"museum")
        assertEquals(exitId,statutory.recordPort(current.id.value,exit,"museum"))
        current=LoanDao[e.id]
        assertEquals(emptyList<String>(),statutory.obligations(current)["missingExitClearanceCollectionIds"])
        val entry=exit.copy(sourceRevision=statutory.extensionRevision(current),stage=EnumLoanPortStage.REENTRY,exitRecordId=exitId,
            collectionIds=setOf("one"),identifiers=mapOf("one" to "MARK-1"),reviewedAt=out.plusDays(10),customsAt=out.plusDays(11),certificateNo="ENTRY-CERT",customsDocumentNo="ENTRY-CUSTOMS",designatedPortFileIds=null)
        statutory.recordPort(current.id.value,entry,"museum");current=LoanDao[e.id]
        assertEquals(proposal,statutory.proposal(current))
        current.returned=true
        assertEquals(listOf("two"),statutory.obligations(current)["pendingReentryCollectionIds"])
        val print=LoanCompliancePrintFields.build(current.toModel().copy(overseasPorts=statutory.portViews(current))).associate {it.label to it.value}
        assertEquals("EXIT-CUSTOMS",print["口岸手续1：海关单证号"])
        assertEquals("ENTRY-CUSTOMS",print["口岸手续2：海关单证号"])
        statutory.recordPort(current.id.value,entry.copy(sourceRevision=statutory.extensionRevision(current),collectionIds=setOf("two"),identifiers=mapOf("two" to "MARK-2")),"museum")
        assertEquals(emptyList<String>(),statutory.obligations(LoanDao[e.id])["pendingReentryCollectionIds"])
    }
    @Test fun `port rejects stale scope wrong items missing documents future and duplicate exits`()=fixture {e ->
        val extension=extension(e);val current=LoanDao[e.id];val v=port(current,extension.originalApprovalId);val count=LoanComplianceRecordDao.count()
        listOf(v.copy(sourceRevision="stale"),v.copy(collectionIds=setOf("outside")),v.copy(identifiers=emptyMap()),v.copy(designatedPortFileIds=""),
            v.copy(certificateFileIds=""),v.copy(customsAt=LocalDateTime.now().plusDays(1)),v.copy(customsAt=v.reviewedAt.minusDays(1)),v.copy(originalApprovalId="outside")
        ).forEach {bad->assertThrows(BadRequestException::class.java) {statutory.recordPort(current.id.value,bad,"museum")}}
        assertThrows(BadRequestException::class.java) {statutory.recordPort(current.id.value,v,"outside")}
        assertEquals(count,LoanComplianceRecordDao.count())
        statutory.recordPort(current.id.value,v,"museum")
        assertThrows(BadRequestException::class.java) {statutory.recordPort(current.id.value,v.copy(sourceRevision=statutory.extensionRevision(LoanDao[e.id]),certificateNo="duplicate"),"museum")}
    }
    @Test fun `reentry requires original review agency matching marks earlier exit and no duplicate coverage`()=fixture {e ->
        val extension=extension(e);var current=LoanDao[e.id];val v=port(current,extension.originalApprovalId)
        val exit=statutory.recordPort(current.id.value,v,"museum");current=LoanDao[e.id]
        val entry=v.copy(sourceRevision=statutory.extensionRevision(current),stage=EnumLoanPortStage.REENTRY,exitRecordId=exit,reviewedAt=out.plusDays(10),customsAt=out.plusDays(11))
        val count=LoanComplianceRecordDao.count()
        listOf(entry.copy(exitRecordId="outside"),entry.copy(reviewAgency="另一机构"),entry.copy(identifiers=mapOf("one" to "wrong","two" to "MARK-2")),entry.copy(reviewedAt=out)).forEach {bad->
            assertThrows(BadRequestException::class.java) {statutory.recordPort(current.id.value,bad,"museum")}}
        assertEquals(count,LoanComplianceRecordDao.count())
        statutory.recordPort(current.id.value,entry,"museum")
        assertThrows(BadRequestException::class.java) {statutory.recordPort(current.id.value,entry.copy(sourceRevision=statutory.extensionRevision(LoanDao[e.id]),certificateNo="duplicate"),"museum")}
    }
    @Test fun `port rejects disabled old session and altered permanent originals`()=fixture {e ->
        val extension=extension(e);val current=LoanDao[e.id];val v=port(current,extension.originalApprovalId)
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
        assertThrows(BadRequestException::class.java) {statutory.recordPort(current.id.value,v,"museum")}
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=true
        val id=statutory.recordPort(current.id.value,v,"museum")
        CollectionPermanentArchiveDao.all().single {it.phase=="OVERSEAS_PORT_$id"}.snapshot="{}"
        assertThrows(BadRequestException::class.java) {statutory.portViews(LoanDao[e.id])}
        assertThrows(BadRequestException::class.java) {statutory.obligations(LoanDao[e.id])}
    }
    @Test fun `overseas extension rejects other authority stale source excessive term and future approval`()=fixture { e ->
        val v=extension(e);val count=LoanComplianceRecordDao.count()
        listOf(v.copy(authorityName="另一机关"),v.copy(originalApprovalId="foreign"),v.copy(sourceRevision="stale"),v.copy(previousId="old"),
            v.copy(exhibitionEndsOn=out.plusMonths(15).toLocalDate(),agreedReturnAt=out.plusMonths(16)),
            v.copy(agreedReturnAt=out.plusYears(3).plusDays(1)),v.copy(approvedOn=LocalDate.now().plusDays(1)),v.copy(approvalFileIds="")).forEach {
            assertThrows(BadRequestException::class.java) {statutory.recordExtension(e.id.value,it,"museum")}
            assertEquals(count,LoanComplianceRecordDao.count())
        }
        assertThrows(BadRequestException::class.java) {statutory.recordExtension(e.id.value,v,"other")}
    }
    @Test fun `late overseas approval is recorded without erasing previous deadline`()=fixture { e ->
        val v=extension(e).copy(submittedOn=out.plusMonths(2).toLocalDate(),approvedOn=out.plusMonths(2).plusDays(2).toLocalDate(),agreementSignedOn=out.plusMonths(2).plusDays(3).toLocalDate())
        val id=statutory.recordExtension(e.id.value,v,"museum");val current=LoanDao[e.id]
        assertEquals(listOf(id),statutory.obligations(current)["lateExtensionIds"])
        val value=statutory.extensionViews(current).single()["content"] as LoanOverseasExtensionRecordVo
        assertEquals(out.plusMonths(2).toLocalDate(),value.previousExhibitionEndsOn)
        assertEquals(out.plusMonths(2),value.previousReturnAt)
    }
    @Test fun `second extension cannot reset cumulative year and cannot continue after actual return`()=fixture { e ->
        val v=extension(e);val id=statutory.recordExtension(e.id.value,v,"museum");val current=LoanDao[e.id]
        val next=v.copy(sourceRevision=statutory.extensionRevision(current),previousId=id,documentNo="延期02",exhibitionEndsOn=out.plusMonths(15).toLocalDate(),agreedReturnAt=out.plusMonths(16))
        assertThrows(BadRequestException::class.java) {statutory.recordExtension(e.id.value,next,"museum")}
        current.returned=true
        assertThrows(BadRequestException::class.java) {statutory.recordExtension(e.id.value,next.copy(sourceRevision=statutory.extensionRevision(current),exhibitionEndsOn=out.plusMonths(5).toLocalDate(),agreedReturnAt=out.plusMonths(6)),"museum")}
    }
    @Test fun `extension write rechecks active actor instead of old login snapshot`()=fixture { e ->
        val v=extension(e);val count=LoanComplianceRecordDao.count()
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
        assertThrows(BadRequestException::class.java) {statutory.recordExtension(e.id.value,v,"museum")}
        assertEquals(count,LoanComplianceRecordDao.count())
    }
    @Test fun `extension cannot be read against a rewritten permanent original`()=fixture { e ->
        val v=extension(e);val id=statutory.recordExtension(e.id.value,v,"museum")
        val archive=CollectionPermanentArchiveDao.all().single {it.phase=="OVERSEAS_EXTENSION_$id"}
        archive.snapshot="{}"
        assertThrows(BadRequestException::class.java) {statutory.extensionViews(LoanDao[e.id])}
        assertThrows(BadRequestException::class.java) {statutory.obligations(LoanDao[e.id])}
    }
    @Test fun `scientific fossil new loans require matching formal nature and cannot downgrade to other or relic`()=fixture { e ->
        val c=CollectionDao["one"];c.nature=EnumCollectionNature.OTHER
        e.dossier=e.dossier!!.copy(assessments=e.dossier!!.assessments.map { if(it.collectionId=="one")it.copy(objectKind=EnumLoanObjectKind.SCIENTIFIC_FOSSIL) else it })
        e.baseline=statutory.baseline(listOf(c,CollectionDao["two"]))
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(e)}
        val review=CollectionNatureReviewService(scope,CollectionActorDirectory(scope))
        assertEquals(listOf("one"),review.inspect().items.map {it.collectionId})
        c.nature=EnumCollectionNature.SCIENTIFIC_FOSSIL
        e.baseline=statutory.baseline(listOf(c,CollectionDao["two"]))
        assertDoesNotThrow {statutory.requirePrepared(e)}
        assertEquals(0,review.inspect().total)
        val proper=e.dossier!!
        for(kind in listOf(EnumLoanObjectKind.OTHER,EnumLoanObjectKind.RELIC)) {
            e.dossier=proper.copy(assessments=proper.assessments.map { if(it.collectionId=="one")it.copy(objectKind=kind) else it })
            assertThrows(BadRequestException::class.java){statutory.requirePrepared(e)}
        }
        e.dossier=proper
        execute(e)
        assertTrue(e.outExecuted);assertEquals(EnumCollectionStatus.ON_LOAN,c.lifeStatus)
        assertEquals(EnumLoanObjectKind.SCIENTIFIC_FOSSIL,e.dossier!!.assessments.first().objectKind)
    }
    @Test fun `cancel approved but unexecuted loan restores in store while preserving independently discovered incident`()=fixture { e ->
        approve(e)
        CollectionIncidentItemDao.new {orgId="museum";incidentId="actual-case";collectionId="one";restricted=true}
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("one",CollectionConst.BIZ_LOAN,e.id.value)}
        service.onFlowCanceled(e,FlowCanceledEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,"借用",e.id.value,CollectionConst.BIZ_LOAN,"operator","经办","operator","经办"))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertEquals(0,CollectionOperationLockDao.all().toList().size)
        assertTrue(CollectionIncidentItemDao.all().single().restricted)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("one")}
    }
    @Test fun `loan print uses actual item handover administrative originals and separate agreement dates`()=fixture { e ->
        execute(e)
        statutory.recordAdministrative(e.id.value,admin(action=EnumAdministrativeAction.FILING,submitted=out.toLocalDate().plusDays(1)))
        originalReaderSchema();if(io.hizhengfu.cloud.sys.system.dao.UserDao.findById("operator")==null)originalReader("operator",CollectionConst.STORAGE)
        SchemaUtils.create(CollectionCategories,CollectionMedias,CollectionAttachments)
        val collectionReader=mock(CollectionService::class.java)
        listOf("one","two").forEach { id -> `when`(collectionReader.get(id)).thenReturn(CollectionDao[id].toModel()) }
        val templates=mock(CollectionPrintTemplateService::class.java) { invocation ->
            if(invocation.method.name=="resolve") CollectionPrintTemplateRegistry.resolve(invocation.getArgument(0))
            else RETURNS_DEFAULTS.answer(invocation)
        }
        val printer=CollectionPrintService(collectionReader,CollectionScopeResolver("multi",""),templates,statutory)
        val printed=printer.loanPrint(e.id.value)
        val fields=printed.fields.associate { it.label to it.value }
        assertEquals("2026-08-01 09:00:00",fields["协议约定归还时间"])
        assertEquals("2026-06-01 09:00:00",fields["实际点交时间"])
        assertEquals("custodian1、custodian2",fields["实际点交保管人员"])
        assertEquals("接收甲、接收乙",fields["借用方实际点交人员"])
        assertEquals("否",fields["藏品one（one）：文字需影像补充"])
        assertEquals("image",fields["藏品two（two）：点交影像"])
        assertEquals("2026-06-02",fields["行政原件1：实际报送日期"])
        assertEquals("submission",fields["行政原件1：报送接收凭据"])
        assertEquals("approval",fields["行政原件1：实际文书文件"])
        assertFalse(fields.containsKey("预计/实际返还日期"))
        assertEquals(2,printed.collections.size)
        assertTrue(printed.template!!.signatures.contains("出借方点交人员二及其他人员"))
        val evidence=LoanComplianceRecordDao.all().first()
        evidence.orgId="other"
        assertThrows(BadRequestException::class.java) { printer.loanPrint(e.id.value) }
        evidence.orgId="museum"
        e.orgId="other"
        assertThrows(BadRequestException::class.java) { printer.loanPrint(e.id.value) }
    }
    @Test fun `loan draft rejects cross museum collections and freezes after first administrative evidence`()=fixture { e ->
        CollectionDao["two"].orgId="other"
        assertThrows(BadRequestException::class.java) { service.save(e.toModel()) }
        CollectionDao["two"].orgId="museum"
        e.dossier=e.dossier!!.copy(borrowerKind=EnumLoanBorrowerKind.NON_STATE_MUSEUM)
        statutory.recordAdministrative(e.id.value,admin())
        assertThrows(BadRequestException::class.java) { service.save(e.toModel().copy(borrower="替换单位")) }
        assertEquals(1,statutory.administrativeViews(e.id.value).size)
    }
    @Test fun `actual loan preparation cannot bypass missing archive packet using approved catalog`()=fixture { e ->
        doThrow(BadRequestException("实际建档目录和原件缺失")).`when`(archivePackets).requireEstablished(CollectionDao["one"])
        assertTrue(assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }.message!!.contains("实际建档目录"))
    }
    @Test fun `loan requires formal registration archival evidence stable condition and at most three years`()=fixture { e ->
        e.dossier=e.dossier!!.copy(agreedReturnAt=out.plusYears(3).plusSeconds(1));e.returnDate=e.dossier!!.agreedReturnAt.toLocalDate()
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
        e.dossier=dossier();e.returnDate=e.dossier!!.agreedReturnAt.toLocalDate()
        CollectionDao["one"].registeredAt=null
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
        CollectionDao["one"].registeredAt=out.minusYears(1)
        e.dossier=e.dossier!!.copy(assessments=e.dossier!!.assessments.map { it.copy(transportDamageRisk=true) })
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
        e.dossier=dossier();CollectionDao["one"].preserveState=EnumPreserve.B;e.baseline=statutory.baseline(listOf(CollectionDao["one"],CollectionDao["two"]))
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
    }
    @Test fun `loan transport standard plan and actual packing check are required before outbound`()=fixture { e ->
        e.dossier=e.dossier!!.copy(transportStandardReference=null)
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
        e.dossier=dossier().copy(transportPlanFileIds=null)
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
        e.dossier=dossier()
        approve(e)
        val archivesBefore=CollectionPermanentArchiveDao.count()
        listOf(
            handover().copy(appliedTransportStandardReference="GB/T 23862-2009"),
            handover().copy(packingStandardConfirmed=false),
            handover().copy(packingCheckFileIds=null),
        ).forEach { assertThrows(BadRequestException::class.java) { statutory.recordHandover(e.id.value,it) } }
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        statutory.recordHandover(e.id.value,handover())
        assertTrue(CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.fileId eq "packing-check" }.count()>0)
    }
    @Test fun `national filing for first grade state museum loans is after outbound and cannot be replaced by province approval`()=fixture { e ->
        CollectionDao["one"].level=EnumRelicLevel.FIRST;e.baseline=statutory.baseline(listOf(CollectionDao["one"],CollectionDao["two"]))
        assertDoesNotThrow { statutory.requireAdministrativeBeforeOut(e) }
        assertThrows(BadRequestException::class.java) { statutory.recordAdministrative(e.id.value,admin(action=EnumAdministrativeAction.FILING,submitted=out.toLocalDate())) }
        execute(e)
        val before=statutory.obligations(e,LocalDate.of(2026,8,1))
        assertEquals(2,(before["filings"] as List<*>).size)
        statutory.recordAdministrative(e.id.value,admin(action=EnumAdministrativeAction.FILING,submitted=out.toLocalDate().plusDays(1)))
        assertEquals(1,(statutory.obligations(e,LocalDate.of(2026,8,1))["filings"] as List<Map<String,Any?>>).count { it["overdue"]==true })
        statutory.recordAdministrative(e.id.value,admin(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.FILING,submitted=out.toLocalDate().plusDays(2)))
        assertEquals(false,statutory.obligations(e)["simultaneousFirstLevelFiling"])
    }
    @Test fun `nonstate first grade borrowing needs national approval and twenty actual working days before outbound`()=fixture { e ->
        e.dossier=dossier(EnumLoanBorrowerKind.NON_STATE_MUSEUM)
        CollectionDao["one"].level=EnumRelicLevel.FIRST;e.baseline=statutory.baseline(listOf(CollectionDao["one"],CollectionDao["two"]))
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.PROVINCE))
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.NATIONAL,submitted=out.toLocalDate().minusDays(20)))
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.NATIONAL))
        assertDoesNotThrow { statutory.requireAdministrativeBeforeOut(e) }
        assertEquals(3,statutory.administrativeViews(e.id.value).size)
    }
    @Test fun `unknown working calendar prevents uncomputable post outbound filing obligation`()=fixture { e ->
        val d=dossier();e.dossier=d.copy(agreedOutAt=out.withMonth(12).withDayOfMonth(20),agreedReturnAt=out.withMonth(12).withDayOfMonth(30));e.loanDate=e.dossier!!.agreedOutAt.toLocalDate();e.returnDate=e.dossier!!.agreedReturnAt.toLocalDate()
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
    }
    @Test fun `approval still reserves collections and cannot execute using only legacy summary evidence`()=fixture { e ->
        approve(e)
        assertEquals(EnumCollectionStatus.PRE_OUT,CollectionDao["one"].lifeStatus)
        assertThrows(BadRequestException::class.java) { locks.requireUnlocked("one") }
        assertThrows(BadRequestException::class.java) { service.executeOut(e.id.value,CollectionOutboundExecutionVo("整单说明","image","sign")) }
        assertEquals(0,StorageMovementDao.all().count())
        service.onFlowCanceled(e,FlowCanceledEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,"借用",e.id.value,CollectionConst.BIZ_LOAN,"operator","经办","operator","经办"))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertDoesNotThrow { locks.requireUnlocked("one") }
    }
    @Test fun `outbound handover needs two on each side safe site exact item coverage and signed files`()=fixture { e ->
        approve(e)
        for(v in listOf(handover().copy(custodianUserIds=setOf("custodian1")),handover().copy(receivingPersonNames=setOf("接收甲")),handover().copy(items=handover().items.take(1)),handover().copy(siteSafetyConfirmed=false),handover().copy(distinctPartiesConfirmed=false)))assertThrows(BadRequestException::class.java) { statutory.recordHandover(e.id.value,v) }
        statutory.recordHandover(e.id.value,handover())
        val json=JsonUtils.fromJson(JsonUtils.toJson(e.handover)!!,LoanHandoverRecordVo::class.java)!!
        assertFalse(json.content.items.first().textNeedsImages)
        service.executeOut(e.id.value,CollectionOutboundExecutionVo());service.executeOut(e.id.value,CollectionOutboundExecutionVo())
        assertEquals(2,StorageMovementDao.all().count());assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["one"].lifeStatus)
        assertEquals(1,CollectionReturnDao.all().count());assertEquals(out,e.outExecutedTime)
        assertBusinessReentryDenied(service,e,"keeper")
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("sign") }
    }
    @Test fun `repeated loan outbound requires its sealed original and unchanged verification`()=fixture { e ->
        approve(e);statutory.recordHandover(e.id.value,handover())
        service.executeOut(e.id.value,CollectionOutboundExecutionVo())
        e.returned=true;e.returnDate=out.plusDays(30).toLocalDate()
        service.executeOut(e.id.value,CollectionOutboundExecutionVo())
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==e.id.value && it.phase=="OUT_EXECUTED" }
        assertThrows(BadRequestException::class.java) {
            service.executeOut(e.id.value,CollectionOutboundExecutionVo("different verification"))
        }
        val snapshot=original.snapshot
        original.snapshot="{}"
        assertThrows(BadRequestException::class.java) { service.executeOut(e.id.value,CollectionOutboundExecutionVo()) }
        original.snapshot=snapshot
        original.delete()
        assertThrows(BadRequestException::class.java) { service.executeOut(e.id.value,CollectionOutboundExecutionVo()) }
        assertEquals(2,StorageMovementDao.all().count())
    }
    @Test fun `loan reapproval requires new signed handover and retains earlier originals`()=fixture { e ->
        approve(e);statutory.recordHandover(e.id.value,handover())
        service.onFlowCanceled(e,FlowCanceledEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,"借用",e.id.value,CollectionConst.BIZ_LOAN,"operator","经办","operator","经办"))
        service.onFlowReactivated(e,FlowReactivatedEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,"借用",e.id.value,CollectionConst.BIZ_LOAN,"CANCELED","keeper","保管确认","重新核实","operator","经办","operator","经办",emptyMap()))
        FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;action="REACTIVATE";operator="operator";createTime=approved.plusDays(2) }
        statutory.actors(e).entries.forEachIndexed { i,(node,user) -> FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="new-$node";nodeKey=node;action="APPROVE";operator=user;comment="本轮重新核实";createTime=approved.plusDays(2).plusMinutes(i+1L) } }
        service.onFlowCompleted(e,FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,"借用",e.id.value,CollectionConst.BIZ_LOAN,emptyMap(),"operator","经办"))
        assertThrows(BadRequestException::class.java) { service.executeOut(e.id.value,CollectionOutboundExecutionVo()) }
        assertEquals(0,StorageMovementDao.all().count())
        val revised=handover().copy(reviewedAt=approved.plusDays(3),replacementReason="新一轮批准后重新审查并由双方签署点交")
        statutory.recordHandover(e.id.value,revised)
        statutory.recordHandover(e.id.value,revised)
        assertEquals(2,archives.list(CollectionConst.BIZ_LOAN,e.id.value).count { it["phase"].toString().startsWith("HANDOVER_") })
        service.executeOut(e.id.value,CollectionOutboundExecutionVo())
        assertEquals(2,StorageMovementDao.all().count())
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = ["JUMP", "REJECT", "RETURN"])
    fun `legacy pass and signed loan handover cannot reuse opinions before a later round`(action: String) = fixture { e ->
        approve(e); statutory.recordHandover(e.id.value, handover())
        val original = Loans.selectAll().where { Loans.id eq e.id.value }.single()[Loans.handover]
        val originals = archives.list(CollectionConst.BIZ_LOAN, e.id.value)
        FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!; this.action=action; nodeKey="keeper"; operator="manager"; createTime=approved.plusDays(2) }
        assertThrows(BadRequestException::class.java) { service.executeOut(e.id.value, CollectionOutboundExecutionVo()) }
        assertFalse(e.outExecuted); assertEquals(0, StorageMovementDao.all().count())
        assertEquals(original, e.handover); assertEquals(originals, archives.list(CollectionConst.BIZ_LOAN, e.id.value))
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["early", "due-today", "later-recall", "multiple", "resolved", "partial", "held", "all-returned"])
    fun `recall deadlines remain per item and cannot extend the agreement or disappear through issue resolution`(scenario: String)=fixture { e ->
        execute(e)
        val agreement=e.dossier!!.agreedReturnAt
        val due=if(scenario=="later-recall") agreement.toLocalDate().plusDays(5) else out.toLocalDate().plusDays(7)
        val recall=LoanMonitoringVo(EnumLoanMonitoringKind.RECALL,out.plusDays(5),setOf("one"),"custodian1","借入方人员","因保管风险要求提前归还","monitor",recallReturnBy=due)
        val first=statutory.recordMonitoring(e.id.value,recall)
        if(scenario=="multiple") statutory.recordMonitoring(e.id.value,recall.copy(occurredAt=out.plusDays(6),recallReturnBy=due.plusDays(5)))
        if(scenario=="resolved") statutory.recordMonitoring(e.id.value,recall.copy(kind=EnumLoanMonitoringKind.ISSUE_RESOLVED,occurredAt=out.plusDays(6),relatedRecordId=first,description="安全问题已处理，尚未实际归还"))
        if(scenario in setOf("partial","held","all-returned")) CollectionReturnDao.new {
            orgId="museum";sourceBizType=CollectionConst.BIZ_LOAN;sourceBizId=e.id.value
            collectionIds=if(scenario=="all-returned") "one,two" else "one"
            acceptedCollectionIds=if(scenario=="held") "" else collectionIds
            executionState=EnumReturnExecutionState.EXECUTED
        }
        val today=when(scenario) { "due-today" -> due; "later-recall" -> agreement.toLocalDate().plusDays(1); else -> out.toLocalDate().plusDays(8) }
        val result=statutory.obligations(e,today)
        assertEquals(agreement,result["returnDueAt"],"催还不能覆盖原协议")
        assertEquals(scenario !in setOf("due-today","partial","held","all-returned"),result["returnOverdue"])
        @Suppress("UNCHECKED_CAST")
        val deadlines=result["returnDeadlines"] as List<Map<String,Any?>>
        if(scenario=="all-returned") assertTrue(deadlines.isEmpty())
        else {
            val second=deadlines.single { it["collectionId"]=="two" }
            assertEquals(agreement.toLocalDate(),second["dueOn"])
            assertNull(second["recallDueOn"])
            if(scenario !in setOf("partial","held")) {
                val firstItem=deadlines.single { it["collectionId"]=="one" }
                assertEquals(minOf(due,agreement.toLocalDate()),firstItem["dueOn"])
                assertEquals(due,firstItem["recallDueOn"])
                assertTrue((firstItem["recallRecordIds"] as List<*>).contains(first))
            } else assertEquals(listOf("two"),deadlines.map { it["collectionId"] })
        }
        if(scenario=="held") assertEquals(setOf("one"),result["heldForReviewCollectionIds"])
        assertEquals(agreement,e.dossier!!.agreedReturnAt)
    }

    @Test fun `late filing remains late and overdue return inspection and major reports remain actionable`()=fixture { e ->
        execute(e)
        statutory.recordAdministrative(e.id.value,admin(action=EnumAdministrativeAction.FILING,submitted=LocalDate.of(2026,8,10)))
        assertEquals(false,(statutory.obligations(e)["filings"] as List<Map<String,Any?>>).single()["onTime"])
        assertEquals(true,statutory.obligations(e)["returnOverdue"])
        val issue=LoanMonitoringVo(EnumLoanMonitoringKind.SAFETY_CHECK,out.plusDays(20),setOf("one"),"custodian1","借入方人员","双方检查发现温湿度失控，暂停使用","monitor",safetyConfirmed=false,majorSituation=true)
        val issueId=statutory.recordMonitoring(e.id.value,issue)
        assertEquals(listOf(issueId),statutory.obligations(e)["pendingMajorReportIds"])
        val safety=CollectionLoanSafetyTaskSource(statutory)
        assertEquals(setOf("ISSUE_RESOLUTION","MAJOR_REPORT"),safety.tasks("museum",out.plusDays(21)).filter {it.recordId==issueId}.map {it.kind}.toSet())
        val resolved=issue.copy(kind=EnumLoanMonitoringKind.ISSUE_RESOLVED,occurredAt=out.plusDays(21),relatedRecordId=issueId,majorSituation=false,safetyConfirmed=null)
        assertThrows(BadRequestException::class.java) { statutory.recordMonitoring(e.id.value,resolved) }
        statutory.recordMonitoring(e.id.value,resolved.copy(kind=EnumLoanMonitoringKind.MAJOR_REPORT,authorityName="主管文物行政部门",reportNo="报送回执-1"))
        statutory.recordMonitoring(e.id.value,resolved.copy(occurredAt=out.plusDays(22)))
        assertEquals(emptyList<String>(),statutory.obligations(e)["pendingMajorReportIds"])
        assertEquals(emptyList<String>(),statutory.obligations(e)["unresolvedIssueIds"])
        assertTrue(safety.tasks("museum",out.plusDays(23)).none {it.recordId==issueId})
        assertEquals(3,statutory.monitoringViews(e.id.value).size)
    }
    @Test fun `overseas exhibition applies whole exhibition first level threshold six months and prohibited item rules`()=fixture { e ->
        CollectionDao["one"].level=EnumRelicLevel.FIRST;e.baseline=statutory.baseline(listOf(CollectionDao["one"],CollectionDao["two"]))
        val plan=LoanOverseasExhibitionVo(out.toLocalDate().plusDays(2),out.toLocalDate().plusMonths(1),100,21,"list","assessment",listOf("one","two").map { LoanOverseasObjectVo(it,false,false,"assessment") },out.minusYears(1).toLocalDate(),"draft-agreement")
        e.dossier=dossier(EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION).copy(collectiveDecisionAt=out.minusYears(1),agreementSignedAt=out.minusMonths(2),exhibitionOverseas=true,overseasExhibition=plan,overseasProcedureFileIds="assessment",overseasRequiredApprovalLevels=setOf(EnumAuthorityLevel.NATIONAL,EnumAuthorityLevel.STATE_COUNCIL))
        assertDoesNotThrow { statutory.requirePrepared(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.NATIONAL,submitted=LocalDate.of(2025,11,1)))
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.STATE_COUNCIL,submitted=LocalDate.of(2026,1,1)))
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.STATE_COUNCIL,submitted=LocalDate.of(2025,11,1)))
        assertDoesNotThrow { statutory.requireAdministrativeBeforeOut(e) }
        e.dossier=e.dossier!!.copy(overseasExhibition=plan.copy(objects=plan.objects.map { it.copy(prohibitedFromExit=true) }))
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(e) }
    }

    @Test fun `overseas institution cannot lower state first level loan approval to competent authority`()=fixture { e ->
        CollectionDao["one"].level=EnumRelicLevel.FIRST
        e.baseline=statutory.baseline(listOf(CollectionDao["one"],CollectionDao["two"]))
        e.dossier=dossier(EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION).copy(
            overseasProcedureFileIds="assessment",overseasRequiredApprovalLevels=setOf(EnumAuthorityLevel.COMPETENT))
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.COMPETENT))
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.NATIONAL))
        assertDoesNotThrow { statutory.requireAdministrativeBeforeOut(e) }
    }

    @Test fun `overseas institution cannot lower state ordinary relic loan approval to provincial authority`()=fixture { e ->
        e.dossier=dossier(EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION).copy(
            overseasProcedureFileIds="assessment",overseasRequiredApprovalLevels=setOf(EnumAuthorityLevel.PROVINCE))
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.PROVINCE))
        assertThrows(BadRequestException::class.java) { statutory.requireAdministrativeBeforeOut(e) }
        statutory.recordAdministrative(e.id.value,admin(EnumAuthorityLevel.COMPETENT))
        assertDoesNotThrow { statutory.requireAdministrativeBeforeOut(e) }
    }

}
