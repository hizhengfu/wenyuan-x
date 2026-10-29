package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.dao.entityCache
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
import java.time.LocalDateTime

class CollectionCarePersistenceTest {
    private val safetyOriginalRoot=createSafetyOriginalRoot()
    @org.junit.jupiter.api.io.TempDir lateinit var originalRoot: java.nio.file.Path
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c->c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,io.hizhengfu.cloud.common.file.LocalFileObjectStorage(safetyOriginalRoot.toString()),unprotectedArchiveReadAccessProvider())
    private val sources=CollectionIncidentSourceService(listOf(CollectionCareIncidentSourceProvider(archives)))
    private var laterWorkActive=false
    private val laterWorkGuard=object:IncidentResolutionGuard {
        override fun requireSafeResolution(collectionId:String) {
            if(laterWorkActive)throw BadRequestException("当前实际作业尚未停止，不能办理新的解除")
        }
    }
    private val incidents=CollectionIncidentService(scope,users,archives,sources,listOf(CollectionCareSafetyGuard(archives),laterWorkGuard))
    private val guard=CollectionCareSafetyGuard(archives,incidents)
    private val locks=CollectionOperationLockService(listOf(CollectionIncidentOperationGuard(),guard))
    private val service=CollectionCareService(scope,users,archives,CollectionOriginalAccessService(),locks,incidents)
    private val context=StaticApplicationContext()
    private lateinit var db: Database
    private var safetyDatabase: Database? = null
    @BeforeEach fun setup() {
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        actor("operator")
    }
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();context.close();removeSafetyOriginalRoot(safetyOriginalRoot)}
    private fun actor(id: String,permission: String="ADMIN") {
        val user=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun now()=LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
    private fun fixture(block:()->Unit) {
        db=safetyDatabase ?: Database.connect("jdbc:sqlite:file:care-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC",databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig { useNestedTransactions=true })
        transaction(db) {
            SchemaUtils.create(CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,Collections,CollectionOrgs,CollectionOperationLocks,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,Warehouses,WarehouseLogs,WarehouseEntryApplications,Requisitions,StorageMovements)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            WarehouseDao.new("store"){orgId="museum";name="实际库房"}
            CollectionDao.new("piece"){orgId="museum";name="馆藏器物";totalRegNo="M1";registeredAt=now().minusMonths(1);catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.OTHER;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store"}
            FileDao.new("proof"){name="实际签署文件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            listOf("operator","keeper","conservation").forEach {actorRole(it,"CARE_OPERATOR",setOf(CollectionConst.CARE))}
            block()
        }
    }
    private fun plan()=CollectionCarePlanVo(sourceRevision=service.detail("piece")["sourceRevision"] as String,preparedAt=now().minusMinutes(1),conservationUserId="conservation",keeperUserId="keeper",inspectionIntervalDays=7,careIntervalDays=30,methods=setOf(EnumCareMethod.DUST_REMOVAL,EnumCareMethod.SUPPORT),instructions="低压除尘和独立支撑，具体范围材料见原件",environmentRequirements="既有监测策略及本件风险评估",storageProtection="独立支撑囊匣",safetyInstructions="发现损伤停止并防护报告",planFileIds="proof")
    private val signature=CollectionCareSignatureVo("本人核对方法、周期和风险","proof")
    private fun prepared():String {
        val id=service.savePlan("piece",plan());actor("conservation");service.sign("piece",id,signature);actor("keeper");service.sign("piece",id,signature);actor("operator");return id
    }
    private fun inspection(plan: String?=null)=CollectionCareCheckVo(plan,now(),"operator","实际库房",true,"custodian","当前保存现状","未发现新增病害","对应实际监测记录",false,true,"维持原保护措施","按实际周期复核","proof","proof")
    private fun attendance(seal: Boolean=true,org:String="museum",store:String="store",suffix:String="") {
        val at=now().minusMinutes(1)
        val diary=WarehouseDiaryVo(at,setOf("operator"),true,"实际在库养护","无异常",listOf(WarehouseEnvironmentReadingVo(at,22.0,50.0,"实际监测记录","proof")),false,signedDiaryFileIds="proof")
        val entry=WarehouseAttendanceVo(at,setOf("operator","keeper"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="proof")
        WarehouseEntryApplicationDao.new("entry$suffix") {
            orgId=org;applicationNo="实际入库";applicantId="operator";entrantIds="operator,keeper";warehouseIds=store;purpose="在库养护"
            plannedStart=at;plannedEnd=at.plusHours(8);actualEntryAt=at;entryRecord=entry
        }
        val entries=WarehouseDiaryEntriesVo(listOf(WarehouseDiaryVisitVo("entry$suffix","实际入库","在库养护",entry,null,at,at.plusHours(8),exitedAt=null)))
        val log=WarehouseLogDao.new("log$suffix"){orgId=org;warehouseId=store;logDate=at.toLocalDate();content="实际在库养护";this.diary=diary;this.entries=entries}
        if(seal) {
            archives.seal(org,WarehouseDiaryService.BIZ,"log$suffix","RECORDED_1",mapOf("warehouseId" to store,"logDate" to log.logDate,"workContent" to log.content,"diary" to diary,"entries" to entries),"proof")
            archives.seal(org,CollectionConst.BIZ_WAREHOUSE_ENTRY,"entry$suffix","CHECKED_IN",entry,"proof")
        }
    }
    private fun start(plan: String,check: String)=CollectionCareStartVo(plan,check,now(),"operator",setOf(EnumCareMethod.DUST_REMOVAL),onSiteWarehouseLogId="log",actualLocation="实际库房",safetyChecks="本人核对实际原件和环境安全",signedFileIds="proof")
    private fun end()=CollectionCareEndVo(now(),true,setOf(EnumCareMethod.DUST_REMOVAL),"实际除尘范围和材料剂量","现状稳定","无新增病害",false,true,true,"实际库房","custodian","独立支撑和包装","按计划复核","proof","proof")
    private fun running():String { val p=prepared();val check=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String);attendance();return service.start("piece",start(p,check)) }
    private fun assertCareSelection(stage: String, change: String) = fixture {
        val id=running();val work=CollectionCareWorkDao[id]
        if(stage=="ENDED")service.end("piece",id,end())
        val oldEnd=work.ended
        val plan=CollectionCarePlanDao[work.planId];val oldPlan=plan.content
        when(change) {
            "END" -> work.ended=if(stage=="RUNNING")end() else null
            "PLAN" -> plan.content=oldPlan.copy(instructions="未经永久原件确认的改写")
            else -> error(change)
        }
        val tables=arrayOf(CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,Collections,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val before=settledSafetySelectionState(*tables)
        assertSafetySelectionIntegrity({guard.requireSafeResolution("piece")},{guard.requireExclusiveWork("piece","other","other")},{guard.requireAllowed("piece","other","other")})
        assertEquals(before,settledSafetySelectionState(*tables))
        work.ended=oldEnd;plan.content=oldPlan;settledSafetySelectionState(*tables)
        if(stage=="RUNNING")assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        else assertDoesNotThrow {guard.requireSafeResolution("piece")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("RUNNING,END","ENDED,END","ENDED,PLAN")
    fun `operation safety selection care verifies ended state before filtering`(stage:String,change:String)=assertCareSelection(stage,change)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("RUNNING,END","ENDED,END","ENDED,PLAN")
    fun `postgres operation safety selection care verifies ended state before filtering`(stage:String,change:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertCareSelection(stage,change)} finally {safetyDatabase=null}
    }
    private fun assertCareRealAudit(kind:String)=fixture {
        val work=CollectionCareWorkDao[running()]
        settledSafetySelectionState(CollectionCarePlans,CollectionCareChecks,CollectionCareWorks)
        actor("keeper")
        when(kind) {
            "PLAN" -> {val p=CollectionCarePlanDao[work.planId];assertSafetySelectionRealAudit(CollectionCarePlans,p){p.content=p.content.copy(instructions="实际第二次方案字段变更")}}
            "CHECK" -> {val c=CollectionCareCheckDao[work.started.checkId];assertSafetySelectionRealAudit(CollectionCareChecks,c){c.content=c.content.copy(condition="实际第二次现状字段变更")}}
            "WORK" -> assertSafetySelectionRealAudit(CollectionCareWorks,work){work.started=work.started.copy(safetyChecks="实际第二次作业字段变更")}
            else -> error(kind)
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["PLAN","CHECK","WORK"])
    fun `operation safety selection care preserves real audit changes`(kind:String)=assertCareRealAudit(kind)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["PLAN","CHECK","WORK"])
    fun `postgres operation safety selection care preserves real audit changes`(kind:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertCareRealAudit(kind)} finally {safetyDatabase=null}
    }
    @Test fun `care history retains records while changed originals block further handling`()=fixture {
        val path=originalRoot.resolve("proof")
        java.nio.file.Files.writeString(path,"ORIGINAL")
        val storage=LocalFileObjectStorage(originalRoot.toString())
        val contentArchives=CollectionPermanentArchiveService(binder,scope,storage,unprotectedArchiveReadAccessProvider())
        val contentCare=CollectionCareService(scope,users,contentArchives,CollectionOriginalAccessService(),locks,incidents)
        val proposal=plan()
        val planId=contentCare.savePlan("piece",proposal)
        val ref=CollectionPermanentArchiveFileDao.all().single()
        assertNotNull(CollectionArchiveFileIdentity.content(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(ref.metadata)))
        assertNotNull(contentCare.detail("piece"))
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertUnavailableOriginals(contentCare.detail("piece"))
        assertThrows(BadRequestException::class.java){contentCare.savePlan("piece",proposal)}
        actor("conservation")
        assertThrows(BadRequestException::class.java){contentCare.sign("piece",planId,signature)}
    }
    @Test fun `care start rejects rewritten inspection evidence before acquiring custody`()=fixture {
        java.nio.file.Files.writeString(originalRoot.resolve("proof"),"ORIGINAL")
        val inspectionFile=originalRoot.resolve("checkProof")
        java.nio.file.Files.writeString(inspectionFile,"ORIGINAL")
        FileDao.new("checkProof"){name="检查原件.pdf";path="checkProof";hash="checkProof";size=8;ext="pdf"}
        val contentArchives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val contentCare=CollectionCareService(scope,users,contentArchives,CollectionOriginalAccessService(),locks,incidents)
        val planId=contentCare.savePlan("piece",plan())
        actor("conservation");contentCare.sign("piece",planId,signature)
        actor("keeper");contentCare.sign("piece",planId,signature)
        actor("operator")
        val checkId=contentCare.inspect("piece",inspection(planId).copy(conditionFileIds="checkProof",signedFileIds="checkProof"), contentCare.detail("piece")["sourceRevision"] as String)
        attendance()
        assertNotNull(contentCare.detail("piece"))
        val timestamp=java.nio.file.Files.getLastModifiedTime(inspectionFile)
        java.nio.file.Files.writeString(inspectionFile,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(inspectionFile,timestamp)
        assertThrows(BadRequestException::class.java){contentCare.start("piece",start(planId,checkId))}
        assertEquals(0L,CollectionCareWorkDao.count())
        assertEquals(0L,CollectionOperationLockDao.count())
    }
    @Test fun `real signed attendance allows care and actual completion releases only care occupancy`()=fixture {
        val work=running();val w=CollectionCareWorkDao[work]
        assertEquals(1L,CollectionOperationLockDao.all().toList().size.toLong());assertNull(w.ended)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        val value=end();service.end("piece",work,value);service.end("piece",work,value)
        assertEquals(value,CollectionCareWorkDao[work].ended);assertEquals(0L,CollectionOperationLockDao.all().toList().size.toLong())
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
        assertEquals(0L,StorageMovementDao.count());assertDoesNotThrow{locks.requireUnlocked("piece")}
        assertEquals(value.endedAt.plusDays(30),service.detail("piece")["nextCareAt"])
        assertThrows(BadRequestException::class.java){service.end("piece",work,value.copy(actualWork="替换历史"))}
    }
    @Test fun `plan signatures require designated separate actors and latest current source`()=fixture {
        val original=plan();val p=service.savePlan("piece",original)
        assertThrows(BadRequestException::class.java){service.sign("piece",p,signature)}
        actor("keeper");assertThrows(BadRequestException::class.java){service.sign("piece",p,signature)}
        actor("conservation");service.sign("piece",p,signature);assertEquals(p,service.sign("piece",p,signature))
        actor("operator");val p2=service.savePlan("piece",original.copy(previousPlanId=p,instructions="重新评估方法"))
        actor("keeper");assertThrows(BadRequestException::class.java){service.sign("piece",p,signature)}
        assertNull(CollectionCarePlanDao[p2].conservation)
        CollectionDao["piece"].name="已订正名称";actor("conservation")
        assertThrows(BadRequestException::class.java){service.sign("piece",p2,signature)}
    }
    @Test fun `unsealed attendance and missing actual actor cannot authorize care`()=fixture {
        val p=prepared();val ck=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String);attendance(false)
        assertThrows(BadRequestException::class.java){service.start("piece",start(p,ck))}
        assertEquals(0L,CollectionCareWorkDao.count())
        actor("admin");assertThrows(BadRequestException::class.java){service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String)}
    }
    @Test fun `inspection preserves unknown facts and later normal record cannot erase restriction`()=fixture {
        val bad=inspection().copy(physicallyPresent=false,custodianUserId=null,safeForRoutineCare=false,condition="未找到实物")
        val id=service.inspect("piece",bad, service.detail("piece")["sourceRevision"] as String);assertEquals(id,service.inspect("piece",bad, service.detail("piece")["sourceRevision"] as String))
        service.inspect("piece",inspection(), service.detail("piece")["sourceRevision"] as String)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        assertDoesNotThrow{guard.requireAllowed("piece",CollectionConst.BIZ_RETURN,"return")}
        assertFalse(CollectionCareCheckDao[id].content.physicallyPresent)
        assertThrows(BadRequestException::class.java){service.inspect("piece",bad.copy(safeForRoutineCare=true), service.detail("piece")["sourceRevision"] as String)}
        assertThrows(BadRequestException::class.java){sources.stoppedCustody("museum","piece",IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",id))}
    }
    @Test fun `latest inspection and evaluated methods are mandatory for actual start`()=fixture {
        val p=prepared();val old=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String);val latest=service.inspect("piece",inspection(p).copy(environmentObservation="本次新的监测复核"), service.detail("piece")["sourceRevision"] as String);attendance()
        assertThrows(BadRequestException::class.java){service.start("piece",start(p,old))}
        assertThrows(BadRequestException::class.java){service.start("piece",start(p,latest).copy(methods=setOf(EnumCareMethod.DISINFECTION)))}
        assertThrows(BadRequestException::class.java){service.start("piece",start(p,latest).copy(startedAt=now().plusDays(1)))}
        val work=service.start("piece",start(p,latest));assertNotNull(work)
        assertThrows(BadRequestException::class.java){service.savePlan("piece",plan().copy(previousPlanId=p))}
    }
    @Test fun `damaged stop remains an unsafe end and source must retain damage`()=fixture {
        val w=running();val stop=end().copy(completed=false,safeToRelease=false,unexpectedDamage=true,actualWork="发现损伤立即停止")
        assertThrows(BadRequestException::class.java){service.end("piece",w,stop.copy(completed=true))}
        service.end("piece",w,stop)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        assertEquals(1L,CollectionOperationLockDao.all().toList().size.toLong())
        val source=IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",w)
        assertThrows(BadRequestException::class.java){sources.snapshot("museum",source,listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"遗漏损伤")))}
        assertNotNull(sources.snapshot("museum",source,listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"真实损伤"))))
        assertEquals(stop.endedAt,sources.stoppedCustody("museum","piece",source).stoppedAt)
        assertNull(service.detail("piece")["works"].let { (it as List<*>).single() as Map<*,*> }["clearance"])
    }
    @Test fun `new incident during work does not prevent truthful stop or allow safe completion`()=fixture {
        val w=running()
        val facts=IncidentFactsVo("现场新增风险",now(),location="实际库房",reporterUserId="operator",description="实际发现风险",immediateMeasures="停止养护",items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"安全条件变化")))
        incidents.create(facts)
        assertThrows(BadRequestException::class.java){service.end("piece",w,end())}
        service.end("piece",w,end().copy(completed=false,safeToRelease=false))
        assertTrue(CollectionIncidentItemDao.all().single().restricted)
    }
    @Test fun `permanent files and source tampering fail closed`()=fixture {
        val p=prepared();val ck=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
        CollectionCareCheckDao[ck].content=inspection(p).copy(condition="篡改检查")
        assertThrows(BadRequestException::class.java){service.detail("piece")}
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
    }
    @Test fun `missing attachment rolls back inspection and its safety effect`()=fixture {
        val n=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java){transaction(db){service.inspect("piece",inspection().copy(safeForRoutineCare=false,signedFileIds="missing"), service.detail("piece")["sourceRevision"] as String)}}
        assertEquals(0L,CollectionCareCheckDao.count());assertEquals(n,CollectionPermanentArchiveDao.count())
        assertDoesNotThrow{locks.requireUnlocked("piece")}
    }
    @Test fun `foreign museum and missing service permission cannot read or write originals`()=fixture {
        actor("reader","COLLECTION_VIEW");assertThrows(BadRequestException::class.java){service.detail("piece")}
        actor("operator");CollectionDao["piece"].orgId="other"
        assertThrows(BadRequestException::class.java){service.inspect("piece",inspection(), service.detail("piece")["sourceRevision"] as String)}
        assertThrows(BadRequestException::class.java){service.detail("piece")}
    }
    @Suppress("UNCHECKED_CAST")
    private fun closeIncident(id: String) {
        val d=incidents.detail(id);val factsId=d["factsId"] as String
        val requirements=d["requirements"] as List<Map<String,Any>>
        val snapshot=d["facts"] as IncidentFactsSnapshot
        val reports=requirements.filter {it["kind"]==EnumIncidentReportKind.INCIDENT}.groupBy {it["target"] as EnumIncidentTarget}.map { (target,rows)->
            val v=IncidentReportVo(factsId,rows.map {it["collectionId"] as String}.toSet(),target,EnumIncidentReportKind.INCIDENT,"实际接收机关",if(target==EnumIncidentTarget.KEEPER)"keeper" else if(target==EnumIncidentTarget.DIRECTOR)"leader" else null,"实际回执",now(),"实际报告情况","proof")
            incidents.report(id,v) to v
        }.toMap()
        val handledAt=now()
        val handling=incidents.recordHandling(id,IncidentHandlingVo(factsId,handledAt,"operator","调查原因","实际处置","proof",snapshot.items.map {IncidentItemResolutionVo(it.item.collectionId,EnumIncidentDisposition.PRESENT_SAFE,true,"处理后安全","实际库房","custodian","实际防护","跟进检查","proof")},reports.filter {it.value.target !in setOf(EnumIncidentTarget.KEEPER,EnumIncidentTarget.DIRECTOR)}.map {(rid,r)->IncidentAuthorityResultVo(r.target,r.authorityName,r.collectionIds,handledAt,setOf(rid),"实际核查结果","proof")},"keeper","leader","M1/日常养护事故档案","proof"))
        val filing=requirements.filter {it["kind"]==EnumIncidentReportKind.RESULT_FILING}.map {it["collectionId"] as String}.toSet()
        if(filing.isNotEmpty())incidents.report(id,IncidentReportVo(factsId,filing,EnumIncidentTarget.NATIONAL,EnumIncidentReportKind.RESULT_FILING,"国务院文物行政部门",null,"备案回执",now(),"处理结果备案","proof",handling))
        listOf("keeper","leader").forEach {actor(it);incidents.confirm(id,IncidentConfirmationVo(incidents.detail(id)["basisRevision"] as String,now(),"本人核对处理结果","proof"))}
        actor("operator")
    }
    @Test fun `actual incident reports handling and personal confirmation allow separate care clearance`()=fixture {
        val w=running();val stop=end().copy(completed=false,safeToRelease=false,unexpectedDamage=true)
        service.end("piece",w,stop)
        val source=IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",w)
        val facts=IncidentFactsVo("养护实际损伤",now(),location="实际库房",reporterUserId="operator",description="操作时发现真实损伤",immediateMeasures="停止并保护",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"实际损伤")),source=source)
        val id=incidents.create(facts)
        actor("keeper")
        val clearance=IncidentClearanceVo(id,now(),"conservation","keeper","恢复安全","实际防护措施","proof")
        assertThrows(BadRequestException::class.java){service.clear("piece",w,clearance)}
        actor("operator");closeIncident(id);actor("keeper")
        val actual=clearance.copy(confirmedAt=now());service.clear("piece",w,actual)
        assertFalse(CollectionCareWorkDao[w].ended!!.safeToRelease)
        assertTrue(CollectionCareWorkDao[w].ended!!.unexpectedDamage)
        assertEquals(0L,CollectionOperationLockDao.all().toList().size.toLong());assertDoesNotThrow{locks.requireUnlocked("piece")}
        val oldFacts=incidents.detail(id)["factsId"] as String
        incidents.amend(id,facts.copy(description="发现需要补核的新事实",replacementReason="实际追加核实",replacementFileIds="proof"),oldFacts)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        actor("operator");closeIncident(id);actor("keeper")
        service.clear("piece",w,actual.copy(confirmedAt=now(),condition="本轮新增事实处理后重新核实安全"))
        assertEquals(2,CollectionCareWorkDao[w].clearance!!.version)
        assertEquals(2,archives.list(CollectionConst.BIZ_CARE,w).count {(it["phase"] as String).startsWith("CLEARED_")})
        assertDoesNotThrow{locks.requireUnlocked("piece")}
    }
    @Test fun `unfinished care prevents premature case safety confirmation and can stop after archive change`()=fixture {
        val w=running()
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        CollectionDao["piece"].name="已依法订正名称"
        service.end("piece",w,end().copy(completed=false,safeToRelease=false))
        assertDoesNotThrow{guard.requireSafeResolution("piece")}
        assertThrows(BadRequestException::class.java){guard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,"req")}
    }
    private fun resolvedUnsafeCare(clear:Boolean=true):Pair<String,String> {
        val work=running();service.end("piece",work,end().copy(completed=false,safeToRelease=false,unexpectedDamage=true))
        val case=incidents.create(IncidentFactsVo("养护实际损伤",now(),location="实际库房",reporterUserId="operator",description="当次真实损伤",immediateMeasures="停止并保护",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"当次损伤")),source=IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",work)))
        closeIncident(case);actor("keeper")
        if(clear)service.clear("piece",work,IncidentClearanceVo(case,now(),"conservation","keeper","当次已经恢复安全","当次防护措施","proof"))
        return work to case
    }
    @Test fun `stored care clearance allows a later legitimate work without repeating current resolution`()=fixture {
        val (work,case)=resolvedUnsafeCare();val saved=CollectionCareWorkDao[work].clearance!!
        laterWorkActive=true;actor("operator","COLLECTION_CARE_MANAGE")
        assertDoesNotThrow {guard.requireAllowed("piece","COLLECTION_RESTORATION","new-current-project")}
        assertEquals(saved,CollectionCareWorkDao[work].clearance)
        assertThrows(BadRequestException::class.java) {
            incidents.requireClearance(case,"museum","piece",IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",work),now(),keeperUserId="keeper")
        }
    }
    @Test fun `stored care clearance still rejects a changed signed incident original`()=fixture {
        val (_,case)=resolvedUnsafeCare();laterWorkActive=true
        val signed=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizId eq case) and (CollectionPermanentArchives.phase like "CONFIRMATION_%")
        }.first()
        signed.digest="changed-after-signature"
        assertThrows(BadRequestException::class.java) {guard.requireAllowed("piece","COLLECTION_RESTORATION","new-current-project")}
    }
    @Test fun `new care clearance cannot ignore later current work occupancy`()=fixture {
        val (work,case)=resolvedUnsafeCare();val saved=CollectionCareWorkDao[work].clearance!!
        laterWorkActive=true
        assertThrows(BadRequestException::class.java) {
            service.clear("piece",work,saved.record.copy(confirmedAt=now(),condition="新的解除申请"))
        }
        assertEquals(saved,CollectionCareWorkDao[work].clearance)
    }
    @Test fun `stored clearance rejects a changed saved proof even when signed incident is valid`()=fixture {
        val (work,case)=resolvedUnsafeCare();val saved=CollectionCareWorkDao[work].clearance!!
        assertThrows(BadRequestException::class.java) {
            incidents.requireStoredClearance(case,"museum","piece",IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",work),saved.record.confirmedAt,saved.incidentProof+mapOf("basisRevision" to "changed-proof"),keeperUserId="keeper")
        }
    }
    @Test fun `closed incident alone cannot substitute for a missing source clearance`()=fixture {
        val (work,_)=resolvedUnsafeCare(clear=false)
        assertNull(CollectionCareWorkDao[work].clearance)
        assertThrows(BadRequestException::class.java) {guard.requireAllowed("piece","COLLECTION_RESTORATION","new-current-project")}
    }
    @Test fun `Spring registers real care guard and source without circular construction`() {
        val ctx=org.springframework.context.annotation.AnnotationConfigApplicationContext()
        try {
            ctx.beanFactory.registerSingleton("scope",scope)
            ctx.beanFactory.registerSingleton("users",users)
            ctx.beanFactory.registerSingleton("archives",archives)
            ctx.register(CollectionIncidentService::class.java,CollectionIncidentSourceService::class.java,CollectionCareIncidentSourceProvider::class.java,CollectionCareSafetyGuard::class.java,CollectionOperationLockService::class.java,CollectionOriginalAccessService::class.java,CollectionCareService::class.java)
            ctx.refresh()
            assertEquals(1,ctx.getBeansOfType(CollectionOperationSafetyGuard::class.java).size)
            assertTrue(ctx.getBean(CollectionIncidentSourceService::class.java).supportsStoppedCustody(CollectionConst.BIZ_CARE))
            assertNotNull(ctx.getBean(CollectionCareService::class.java))
        } finally {ctx.close()}
    }
    @Test fun `care workbench follows actionable signatures actual completion and unsafe work`()=fixture {
        val source=CollectionCareTaskSource(incidents)
        assertEquals(listOf("MISSING_PLAN"),source.tasks("museum",now()).map {it.kind})
        val p=service.savePlan("piece",plan())
        assertEquals(listOf("conservation"),source.tasks("museum",now()).single().assignees.map {it.userId})
        actor("conservation");service.sign("piece",p,signature)
        assertEquals("PLAN_KEEPER",source.tasks("museum",now()).single().kind)
        actor("keeper");service.sign("piece",p,signature);actor("operator")
        val check=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String);attendance();val work=service.start("piece",start(p,check))
        val running=source.tasks("museum",now());assertFalse(running.any {it.kind=="CARE_DUE"});assertEquals("IN_PROGRESS",running.single {it.kind=="WORK_IN_PROGRESS"}.status)
        val result=end().copy(completed=false,safeToRelease=false,actualWork="实际停止并保护",actualMethods=emptySet())
        service.end("piece",work,result)
        assertTrue(source.tasks("museum",now()).any {it.kind=="WORK_FOLLOWUP"})
        assertTrue(source.tasks("museum",now()).any {it.kind=="CARE_DUE"})
    }
    @Test fun `longer replacement plan cannot move a missed care deadline and actual check resets it`()=fixture {
        val past=now().minusDays(20);var first=""
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock->clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenReturn(past);first=prepared()}
        val id=service.savePlan("piece",plan().copy(previousPlanId=first,inspectionIntervalDays=30))
        actor("conservation");service.sign("piece",id,signature);actor("keeper");service.sign("piece",id,signature);actor("operator")
        val source=CollectionCareTaskSource(incidents)
        assertEquals(past.plusDays(7),service.detail("piece")["nextInspectionAt"])
        assertEquals(past.plusDays(7),source.tasks("museum",now()).single {it.kind=="INSPECTION_DUE"}.dueAt)
        val value=inspection(id);service.inspect("piece",value, service.detail("piece")["sourceRevision"] as String)
        assertEquals(value.checkedAt.plusDays(30),service.detail("piece")["nextInspectionAt"])
        assertEquals(value.checkedAt.plusDays(30),source.tasks("museum",now()).single {it.kind=="INSPECTION_DUE"}.dueAt)
    }
    @Test fun `removed signed care stage appears as original error without trusting corrupted assignees`()=fixture {
        val p=prepared();CollectionCarePlanDao[p].keeper=null
        val row=CollectionCareTaskSource(incidents).tasks("museum",now()).single()
        assertEquals("ORIGINAL_ERROR",row.status);assertTrue(row.assignees.isEmpty())
        assertThrows(BadRequestException::class.java){service.detail("piece")}
    }
    @Test fun `removed care ending cannot masquerade as ordinary work in progress`()=fixture {
        val w=running();service.end("piece",w,end());CollectionCareWorkDao[w].ended=null
        val row=CollectionCareTaskSource(incidents).tasks("museum",now()).single()
        assertEquals("ORIGINAL_ERROR",row.status);assertTrue(row.assignees.isEmpty())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["after-exit","at-exit","missing-visit","changed-entry"])
    fun `signed old diary cannot authorize operations outside actual attendance`(kind:String)=fixture {
        val p=prepared();val check=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String);attendance()
        val v=start(p,check)
        val visit=WarehouseEntryApplicationDao["entry"]
        when(kind) {
            "missing-visit"->visit.delete()
            "changed-entry"->visit.entryRecord=visit.entryRecord!!.copy(entrantUserIds=setOf("different","keeper"))
            else->{
                val exit=visit.entryRecord!!.copy(occurredAt=if(kind=="at-exit")v.startedAt else v.startedAt.minusSeconds(1))
                visit.actualExitAt=exit.occurredAt;visit.exitRecord=exit
                archives.seal("museum",CollectionConst.BIZ_WAREHOUSE_ENTRY,"entry","CHECKED_OUT",exit,"proof")
            }
        }
        val frozen=WarehouseLogDao["log"].entries
        assertThrows(BadRequestException::class.java){service.start("piece",v)}
        assertEquals(0L,CollectionCareWorkDao.count());assertEquals(0L,CollectionOperationLockDao.count())
        assertEquals(frozen,WarehouseLogDao["log"].entries)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["verified","missing-exit-archive","changed-exit-archive","unaccompanied","time-only","record-only"])
    fun `historical operation before later checkout requires consistent exit evidence`(kind:String)=fixture {
        val p=prepared();val check=service.inspect("piece",inspection(p), service.detail("piece")["sourceRevision"] as String);attendance()
        val v=start(p,check)
        val visit=WarehouseEntryApplicationDao["entry"]
        val exit=visit.entryRecord!!.copy(occurredAt=v.startedAt.plusSeconds(1),continuousAccompaniment=kind!="unaccompanied")
        if(kind!="record-only")visit.actualExitAt=exit.occurredAt
        if(kind!="time-only")visit.exitRecord=exit
        if(kind!="missing-exit-archive")archives.seal("museum",CollectionConst.BIZ_WAREHOUSE_ENTRY,"entry","CHECKED_OUT",exit,"proof")
        if(kind=="changed-exit-archive")CollectionPermanentArchiveDao.all().single {it.phase=="CHECKED_OUT"}.snapshot="{}"
        val frozen=WarehouseLogDao["log"].entries
        if(kind=="verified")assertNotNull(service.start("piece",v))
        else {
            assertThrows(BadRequestException::class.java){service.start("piece",v)}
            assertEquals(0L,CollectionCareWorkDao.count());assertEquals(0L,CollectionOperationLockDao.count())
        }
        assertEquals(frozen,WarehouseLogDao["log"].entries)
    }

    @Test fun `revoking current care duty rejects direct repeated inspection and cross read never grants writing`()=fixture {
        val v=inspection();val id=service.inspect("piece",v, service.detail("piece")["sourceRevision"] as String)
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.inspect("piece",v, service.detail("piece")["sourceRevision"] as String)}
        actorRole("operator","CARE_OPERATOR",setOf(CollectionConst.CARE,CollectionConst.CROSS_ORG))
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        assertDoesNotThrow {service.detail("piece")}
        assertThrows(BadRequestException::class.java){service.inspect("piece",v, service.detail("piece")["sourceRevision"] as String)}
        assertEquals(id,CollectionCareCheckDao.all().single().id.value)
        assertEquals(v,CollectionCareCheckDao[id].content)
    }
    private fun clearedHistory(kind:String,firstFile:String="proof",secondFile:String=firstFile):Pair<String,List<CollectionCareClearanceSnapshot>> {
        val record=if(kind=="work")running().also {service.end("piece",it,end().copy(completed=false,safeToRelease=false,unexpectedDamage=true))}
            else service.inspect("piece",inspection().copy(damaged=true,safeForRoutineCare=false), service.detail("piece")["sourceRevision"] as String)
        val incident=incidents.create(IncidentFactsVo("实际养护异常",now(),location="实际库房",reporterUserId="operator",description="核实真实损伤",immediateMeasures="停止并保护",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"实际损伤")),source=IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",record)))
        closeIncident(incident);actor("keeper")
        service.clear("piece",record,IncidentClearanceVo(incident,now(),"conservation","keeper","首次核实安全","实际防护措施",firstFile))
        fun current()=if(kind=="work")CollectionCareWorkDao[record].clearance!! else CollectionCareCheckDao[record].clearance!!
        val first=current()
        service.clear("piece",record,first.record.copy(confirmedAt=now(),condition="追加实际核对安全",signedFileIds=secondFile))
        val second=current()
        assertDoesNotThrow {service.detail("piece")};assertDoesNotThrow {locks.requireUnlocked("piece")}
        return record to listOf(first,second)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("work,cleared","check,cleared","work,rollback","check,rollback","work,missing-first","check,missing-first","work,changed-first","check,changed-first","work,wrong-version","check,wrong-version","work,foreign-first","check,foreign-first")
    fun `clearance history rejects erased rolled back or inconsistent archived versions`(kind:String,change:String)=fixture {
        val (id,values)=clearedHistory(kind)
        val first=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_CARE && it.bizId==id && it.phase=="CLEARED_1"}
        when(change) {
            "cleared","rollback" -> {val value=if(change=="cleared")null else values.first();if(kind=="work")CollectionCareWorkDao[id].clearance=value else CollectionCareCheckDao[id].clearance=value}
            "missing-first" -> first.delete()
            "changed-first" -> first.snapshot=CollectionCareEvidence.json(values.first().copy(record=values.first().record.copy(condition="改写原核对")))
            "wrong-version" -> {first.snapshot=CollectionCareEvidence.json(values.first().copy(version=7));first.digest=CollectionCareEvidence.digest(first.snapshot)}
            "foreign-first" -> first.orgId="other"
        }
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.clear()
        val before=CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.snapshot,it.digest)}.sortedBy {it.first()}
        assertAll(
            {assertThrows(BadRequestException::class.java){service.detail("piece")}},
            {assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}},
            {assertEquals(before,CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.snapshot,it.digest)}.sortedBy {it.first()})}
        )
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["PLAN","CHECKED","STARTED"])
    fun `care detail rejects foreign file references at every original record`(phase:String)=fixture {
        running();assertDoesNotThrow {service.detail("piece")}
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_CARE && it.phase==phase}
        CollectionPermanentArchiveFileDao.all().first {it.archiveId==archive.id.value}.orgId="other"
        assertThrows(BadRequestException::class.java){service.detail("piece")}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["plan","check","work"])
    fun `care history checks foreign ownership before parsing its body`(kind:String)=fixture {
        val work=running();val plan=CollectionCareWorkDao[work].planId;val check=CollectionCareWorkDao[work].started.checkId
        val table=when(kind){"plan"->"col_care_plan";"check"->"col_care_check";else->"col_care_work"}
        val id=when(kind){"plan"->plan;"check"->check;else->work}
        val field=if(kind=="work")"started" else "content"
        val tx=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
        tx.entityCache.flush()
        tx.exec("UPDATE $table SET org_id='other', $field='invalid-json' WHERE id='$id'")
        tx.entityCache.clear()
        if(kind=="work")assertEquals(emptyList<Any>(),service.detail("piece")["works"])
        else assertThrows(BadRequestException::class.java){service.detail("piece")}
    }

    @Test fun `care capability requires actual writable museum and retained login permission`()=fixture {
        assertEquals(true,service.detail("piece")["canOperate"])
        actor("operator",CollectionConst.SELECT)
        assertEquals(false,service.detail("piece")["canOperate"])
        actor("operator")
        actorRole("operator","CARE_OPERATOR",setOf(CollectionConst.CARE,CollectionConst.CROSS_ORG))
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        assertEquals(false,service.detail("piece")["canOperate"])
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["PLAN","CONSERVATION","KEEPER","CHECKED","STARTED","ENDED","CLEARED_1","CLEARED_2"])
    fun `care detail rejects deleted file references at every archived phase`(phase:String)=fixture {
        clearedHistory("work")
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_CARE && it.phase==phase}
        val refs=CollectionPermanentArchiveFileDao.all().filter {it.archiveId==archive.id.value}
        assertTrue(refs.isNotEmpty())
        refs.forEach {it.delete()}
        val tx=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current()
        tx.entityCache.flush();tx.entityCache.clear()
        assertThrows(BadRequestException::class.java){service.detail("piece")}
    }

    private fun receivingService():CollectionCareService {
        if(CollectionOrgDao.findById("other")==null)CollectionOrgDao.new("other"){name="接收馆";code="O";systemGroupId="other"}
        originalReaderSchema()
        CollectionDao["piece"].apply {orgId="other";name="接收馆当前内部名称";totalRegNo="O-2"}
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        actor("operator")
        return CollectionCareService(CollectionScopeResolver("single","other"),users,archives,CollectionOriginalAccessService(),locks,incidents)
    }
    @Test fun `care original is limited to its museum record phase and archived file even after transfer`()=fixture {
        val work=running();service.end("piece",work,end())
        val view=service.detail("piece")
        val records=listOf("plans","checks","works").flatMap {key->(view[key] as List<*>).map {it as Map<*,*>}}
        val plan=records.single {it["id"]==CollectionCareWorkDao[work].planId}
        val check=records.single {it["id"]==CollectionCareWorkDao[work].started.checkId}
        val workRecord=records.single {it["id"]==work}
        listOf(plan to listOf("PLAN","CONSERVATION","KEEPER"),check to listOf("CHECKED"),workRecord to listOf("STARTED","ENDED")).forEach {(record,phases)->
            val archives=record["archives"] as List<*>
            phases.forEach {phase->
                val archive=archives.map {it as Map<*,*>}.single {it["phase"]==phase}
                assertEquals(archive["id"],service.archivedOriginal("piece","museum",record["id"] as String,phase,"proof"))
            }
        }
        assertThrows(BadRequestException::class.java){service.archivedOriginal("piece","museum",work,"PLAN","proof")}
        assertThrows(BadRequestException::class.java){service.archivedOriginal("piece","museum",work,"STARTED","missing")}
        assertThrows(BadRequestException::class.java){service.archivedOriginal("other-piece","museum",work,"STARTED","proof")}
        assertThrows(BadRequestException::class.java){service.archivedOriginal("piece","other",work,"STARTED","proof")}
        val originalArchive=service.archivedOriginal("piece","museum",work,"ENDED","proof")
        val receiver=receivingService()
        assertThrows(BadRequestException::class.java){receiver.archivedOriginal("piece","museum",work,"ENDED","proof")}
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="museum";actor("operator")
        assertEquals(originalArchive,service.archivedOriginal("piece","museum",work,"ENDED","proof"))
    }
    @Test fun `receiving museum current care does not load the previous museum history`()=fixture {
        val w=running();service.end("piece",w,end())
        val current=receivingService().detail("piece")
        assertEquals("接收馆当前内部名称",current["name"])
        assertEquals(emptyList<Any>(),current["plans"])
        assertEquals(emptyList<Any>(),current["checks"])
        assertEquals(emptyList<Any>(),current["works"])
    }
    @Test fun `receiving museum creates its own first plan without depending on foreign predecessor`()=fixture {
        val original=plan();val old=prepared()
        val receiver=receivingService()
        val next=original.copy(previousPlanId=null,sourceRevision=CollectionCareEvidence.digest(CollectionCareEvidence.json(CollectionCareEvidence.catalog(CollectionDao["piece"]))),instructions="接收馆实际保护方案")
        val id=receiver.savePlan("piece",next)
        assertEquals("other",CollectionCarePlanDao[id].orgId)
        assertEquals(2,CollectionCarePlanDao[id].version)
        assertNull(CollectionCarePlanDao[id].content.previousPlanId)
        assertEquals("museum",CollectionCarePlanDao[old].orgId)
    }

    @Test fun `original care history stays frozen while receiver completes independent care`()=fixture {
        val oldWork=running();service.end("piece",oldWork,end())
        val original=service.detail("piece")
        val oldArchives=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        val receiver=receivingService()
        WarehouseDao.new("receiver-store"){orgId="other";name="接收馆实际库房"}
        CollectionDao["piece"].warehouseId="receiver-store"
        val next=CollectionCarePlanDao[CollectionCareWorkDao[oldWork].planId].content.copy(previousPlanId=null,sourceRevision=receiver.detail("piece")["sourceRevision"] as String,instructions="接收馆新方案")
        val newPlan=receiver.savePlan("piece",next)
        listOf("conservation","keeper").forEach {id->
            io.hizhengfu.cloud.sys.system.dao.UserDao[id].groupId="other";actor(id);receiver.sign("piece",newPlan,signature)
        }
        actor("operator")
        val newCheck=receiver.inspect("piece",inspection(newPlan), receiver.detail("piece")["sourceRevision"] as String)
        attendance(org="other",store="receiver-store",suffix="-receiver")
        val newWork=receiver.start("piece",start(newPlan,newCheck).copy(onSiteWarehouseLogId="log-receiver"))
        receiver.end("piece",newWork,end())
        assertEquals(listOf(newWork),(receiver.detail("piece")["works"] as List<*>).map {(it as Map<*,*>)["id"]})
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="museum"
        val history=service.history("piece","museum")
        assertEquals(true,history["historical"]);assertEquals(false,history["canOperate"])
        assertNull(history["sourceRevision"]);assertNull(history["nextInspectionAt"]);assertNull(history["nextCareAt"])
        assertEquals(original["name"],history["name"])
        listOf("plans","checks","works").forEach {assertEquals(original[it],history[it])}
        assertThrows(BadRequestException::class.java){service.detail("piece")}
        assertThrows(BadRequestException::class.java){service.end("piece",oldWork,end())}
        oldArchives.forEach {(id,value)->assertEquals(value,CollectionPermanentArchiveDao[id].let {it.snapshot to it.digest})}
    }

    @Test fun `history needs no current collection and ignores unreadable receiving museum body`()=fixture {
        val p=prepared();val expected=service.detail("piece")["plans"]
        val receiver=receivingService()
        val next=CollectionCarePlanDao[p].content.copy(previousPlanId=null,sourceRevision=receiver.detail("piece")["sourceRevision"] as String)
        val foreign=receiver.savePlan("piece",next)
        val tx=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current();tx.entityCache.flush()
        tx.exec("UPDATE col_care_plan SET content='invalid-json' WHERE id='$foreign'");tx.entityCache.clear()
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="museum"
        assertEquals(expected,service.history("piece","museum")["plans"])
        CollectionDao["piece"].delete()
        assertEquals(expected,service.history("piece","museum")["plans"])
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["plan","file","clearance"])
    fun `original museum history still rejects inconsistent original evidence after transfer`(kind:String)=fixture {
        val (record,_)=clearedHistory("work")
        receivingService();io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="museum";actor("operator")
        assertDoesNotThrow {service.history("piece","museum")}
        when(kind) {
            "plan" -> CollectionCarePlanDao.all().single().apply {content=content.copy(instructions="变更旧方案")}
            "file" -> CollectionPermanentArchiveFileDao.all().first().orgId="other"
            "clearance" -> CollectionCareWorkDao[record].clearance=null
        }
        assertThrows(BadRequestException::class.java){service.history("piece","museum")}
    }

    private fun assertCareOriginals(phase:String,damage:String)=fixture {
        fun file(label:String)=safetyOriginalFile(safetyOriginalRoot,label)
        val p=service.savePlan("piece",plan().copy(planFileIds=file("PLAN")))
        actor("conservation");service.sign("piece",p,CollectionCareSignatureVo("本人核对",file("CONSERVATION")))
        actor("keeper");service.sign("piece",p,CollectionCareSignatureVo("本人核对",file("KEEPER")));actor("operator")
        val check=service.inspect("piece",inspection(p).copy(conditionFileIds=file("CHECKED"),signedFileIds="CHECKED"), service.detail("piece")["sourceRevision"] as String)
        attendance()
        val id=service.start("piece",start(p,check).copy(signedFileIds=file("STARTED")))
        service.end("piece",id,end().copy(conditionFileIds=file("ENDED"),signedFileIds="ENDED"))
        val expectedId=when(phase){"PLAN","CONSERVATION","KEEPER"->p;"CHECKED"->check;else->id}
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_CARE && it.bizId==expectedId && it.phase==phase}
        val tables=arrayOf(CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,Collections,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val actions=arrayOf<()->Unit>({guard.requireSafeResolution("piece")},{guard.requireExclusiveWork("piece","other","other")},{guard.requireAllowed("piece","other","other")})
        assertSafetyOriginalDamage(safetyOriginalRoot,archive,phase,damage,tables,actions){actions.forEach {assertDoesNotThrow(it)}}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,MISSING",
        "PLAN,IDENTITY",
        "PLAN,EXTRA",
        "PLAN,BYTES",
        "PLAN,BASELINE",
        "CONSERVATION,MISSING",
        "CONSERVATION,IDENTITY",
        "CONSERVATION,EXTRA",
        "CONSERVATION,BYTES",
        "CONSERVATION,BASELINE",
        "KEEPER,MISSING",
        "KEEPER,IDENTITY",
        "KEEPER,EXTRA",
        "KEEPER,BYTES",
        "KEEPER,BASELINE",
        "CHECKED,MISSING",
        "CHECKED,IDENTITY",
        "CHECKED,EXTRA",
        "CHECKED,BYTES",
        "CHECKED,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE")
    fun `operation safety originals care rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertCareOriginals(phase,damage)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,MISSING",
        "PLAN,IDENTITY",
        "PLAN,EXTRA",
        "PLAN,BYTES",
        "PLAN,BASELINE",
        "CONSERVATION,MISSING",
        "CONSERVATION,IDENTITY",
        "CONSERVATION,EXTRA",
        "CONSERVATION,BYTES",
        "CONSERVATION,BASELINE",
        "KEEPER,MISSING",
        "KEEPER,IDENTITY",
        "KEEPER,EXTRA",
        "KEEPER,BYTES",
        "KEEPER,BASELINE",
        "CHECKED,MISSING",
        "CHECKED,IDENTITY",
        "CHECKED,EXTRA",
        "CHECKED,BYTES",
        "CHECKED,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE")
    fun `postgres operation safety originals care rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertCareOriginals(phase,damage)} finally {safetyDatabase=null}
    }

    private fun assertCareClearanceOriginals(kind:String,version:Int,damage:String)=fixture {
        val first=safetyOriginalFile(safetyOriginalRoot,"clearance-first-$kind")
        val second=safetyOriginalFile(safetyOriginalRoot,"clearance-second-$kind")
        val (record,values)=clearedHistory(kind,first,second)
        assertEquals(listOf(first,second),values.map {it.record.signedFileIds})
        val archive=CollectionPermanentArchiveDao.all().single {
            it.bizType==CollectionConst.BIZ_CARE && it.bizId==record && it.phase=="CLEARED_$version"
        }
        val tables=arrayOf(CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,Collections,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val actions=arrayOf<()->Unit>(
            {guard.requireSafeResolution("piece")},
            {guard.requireExclusiveWork("piece","other","other")},
            {guard.requireAllowed("piece","other","other")},
        )
        val file=if(version==1)first else second
        assertSafetyOriginalDamage(safetyOriginalRoot,archive,file,damage,tables,actions) {
            actions.forEach {assertDoesNotThrow(it)}
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "work,1,MISSING","work,1,IDENTITY","work,1,EXTRA","work,1,BYTES","work,1,BASELINE",
        "work,2,MISSING","work,2,IDENTITY","work,2,EXTRA","work,2,BYTES","work,2,BASELINE",
        "check,1,MISSING","check,1,IDENTITY","check,1,EXTRA","check,1,BYTES","check,1,BASELINE",
        "check,2,MISSING","check,2,IDENTITY","check,2,EXTRA","check,2,BYTES","check,2,BASELINE")
    fun `operation safety originals care clearance history rejects changed manifest bytes and missing baseline`(kind:String,version:Int,damage:String)=assertCareClearanceOriginals(kind,version,damage)

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "work,1,MISSING","work,1,IDENTITY","work,1,EXTRA","work,1,BYTES","work,1,BASELINE",
        "work,2,MISSING","work,2,IDENTITY","work,2,EXTRA","work,2,BYTES","work,2,BASELINE",
        "check,1,MISSING","check,1,IDENTITY","check,1,EXTRA","check,1,BYTES","check,1,BASELINE",
        "check,2,MISSING","check,2,IDENTITY","check,2,EXTRA","check,2,BYTES","check,2,BASELINE")
    fun `postgres operation safety originals care clearance history rejects changed manifest bytes and missing baseline`(kind:String,version:Int,damage:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertCareClearanceOriginals(kind,version,damage)} finally {safetyDatabase=null}
    }

    private fun assertCareIncidentSourceOriginals(kind:String,damage:String)=fixture {
        val file=safetyOriginalFile(safetyOriginalRoot,"incident-source-$kind")
        val id=if(kind=="work")running().also {
            service.end("piece",it,end().copy(completed=false,safeToRelease=false,unexpectedDamage=true,conditionFileIds=file))
        } else service.inspect("piece",inspection().copy(damaged=true,safeForRoutineCare=false,conditionFileIds=file), service.detail("piece")["sourceRevision"] as String)
        val phase=if(kind=="work")"ENDED" else "CHECKED"
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_CARE && it.bizId==id && it.phase==phase}
        val source=IncidentSourceVo(CollectionConst.BIZ_CARE,"piece",id)
        val items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"据实保留损伤"))
        val actions=arrayOf<()->Unit>(
            {sources.snapshot("museum",source,items)},
            {sources.stoppedCustody("museum","piece",source)},
        )
        val tables=arrayOf(CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,Collections,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOperationLocks,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        assertSafetyOriginalDamage(safetyOriginalRoot,archive,file,damage,tables,actions) {
            actions.forEach {assertDoesNotThrow(it)}
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "work,MISSING","work,IDENTITY","work,EXTRA","work,BYTES","work,BASELINE",
        "check,MISSING","check,IDENTITY","check,EXTRA","check,BYTES","check,BASELINE")
    fun `operation safety incident source originals care rejects changed manifest bytes and missing baseline`(kind:String,damage:String)=assertCareIncidentSourceOriginals(kind,damage)

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "work,MISSING","work,IDENTITY","work,EXTRA","work,BYTES","work,BASELINE",
        "check,MISSING","check,IDENTITY","check,EXTRA","check,BYTES","check,BASELINE")
    fun `postgres operation safety incident source originals care rejects changed manifest bytes and missing baseline`(kind:String,damage:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertCareIncidentSourceOriginals(kind,damage)} finally {safetyDatabase=null}
    }

}
