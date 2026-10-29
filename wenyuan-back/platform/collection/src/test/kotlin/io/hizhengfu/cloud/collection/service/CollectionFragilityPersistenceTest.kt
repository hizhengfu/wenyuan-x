package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
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
import java.time.temporal.ChronoUnit

class CollectionFragilityPersistenceTest {
    private val safetyOriginalRoot=createSafetyOriginalRoot()
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {c->c.getArgument<String>(0)}}
    private val archives=CollectionPermanentArchiveService(binder,scope,io.hizhengfu.cloud.common.file.LocalFileObjectStorage(safetyOriginalRoot.toString()),unprotectedArchiveReadAccessProvider())
    private val monitorIncidents=mock(CollectionIncidentService::class.java)
    private val monitorGuard=CollectionFragileMonitoringSafetyGuard(archives,monitorIncidents)
    private val guard=CollectionFragilityGuard(users)
    private val locks=CollectionOperationLockService(listOf(guard,monitorGuard,CollectionIncidentOperationGuard(),CollectionCareSafetyGuard(archives)))
    private val service=CollectionFragilityService(scope,users,archives,locks)
    private val originals=CollectionFragilityOriginalService(scope,archives)
    private val monitoring=CollectionFragileMonitoringService(scope,users,archives,emptyList(),monitorIncidents)
    private val adjustments=CollectionFragileAdjustmentService(scope,users,archives,locks,listOf(guard,monitorGuard,CollectionIncidentOperationGuard(),CollectionCareSafetyGuard(archives)))
    private val collectionService=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
    private val names=mock(CollectionUserResolver::class.java)
    private val items=CollectionBusinessItemService()
    private val placement=mock(CollectionPlacementService::class.java)
    private val returns=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collectionService,names,binder,items,locks,placement,ReturnStatutoryService(scope,users,binder,archives),archives)
    private val statutory=RequisitionStatutoryService(scope,users,binder,archives)
    private val requisitions=RequisitionService(mock(FlowInstanceService::class.java),scope,collectionService,names,binder,returns,items,locks,placement,statutory,archives)
    private val loanStatutory=LoanStatutoryService(scope,users,binder,archives,CollectionCalendarService(scope,binder,archives),mock(CollectionArchivePacketService::class.java))
    private val loans=LoanService(mock(FlowInstanceService::class.java),scope,collectionService,binder,returns,items,locks,loanStatutory,archives)
    private val care=CollectionCareService(scope,users,archives,CollectionOriginalAccessService(),locks,CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService()))
    private val context=StaticApplicationContext()
    private lateinit var db:Database
    private var safetyDatabase:Database?=null
    private lateinit var req:String
    private lateinit var at:LocalDateTime
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    @BeforeEach fun setup(){context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);actor("operator")}
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();context.close();removeSafetyOriginalRoot(safetyOriginalRoot)}
    private fun actor(id:String,permission:String="ADMIN") {
        val u=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(initialNature:EnumCollectionNature?=null,databaseUrl:String?=null,block:()->Unit) {
        db=safetyDatabase ?: Database.connect(databaseUrl ?: "jdbc:sqlite:file:fragility-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC",databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig {useNestedTransactions=true})
        transaction(db) {
            SchemaUtils.create(CollectionLedgerLogs,CollectionFragileMonitorRuntimes,CollectionFragileAutomaticChecks,CollectionFragileMonitorings,CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,CollectionFragileUseAdjustments,CollectionCareChecks,CollectionCareWorks,CollectionIncidentItems,CollectionIncidents,CollectionIncidentRecords,Collections,CollectionOrgs,Requisitions,Loans,LoanComplianceRecords,CollectionCalendarVersions,CollectionCatalogVersions,CollectionReturns,CollectionBusinessItems,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstanceLogs,Warehouses)
            at=now().minusMinutes(10)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            WarehouseDao.new("store"){orgId="museum";name="本馆库房";sceneType=EnumLocationScene.STORAGE}
            CollectionDao.new("piece"){nature=initialNature;orgId="museum";name="脆弱材质器物";totalRegNo="M1";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";catalogStatus=EnumCatalogStatus.APPROVED;source=EnumSource.OLD}
            FileDao.new("proof"){name="实际评估签署.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            req=requisitions.save(RequisitionVo(collectionIds="piece",purpose=EnumMoveReason.EXHIBIT,basis="本馆展览",department="展陈部",operator="operator",toWarehouseId="store",dossier=RequisitionDossierVo("dept","department","keeper","director","operator","固定支撑展出","本馆专题展览","本馆展柜","实际保护方案","proof",at.minusHours(1),at.plusDays(1),transportRequired=false,transportDecisionReason="本馆展柜原位提用")))
            incidentReaderFixture("operator","keeper","leader","conservation","department-head","department","director")
            listOf("operator","keeper","conservation","department","director").forEach {actorRole(it,"CARE_OPERATOR",setOf(CollectionConst.CARE))}
            actorRole("operator","REQUISITION_OPERATOR",setOf(CollectionConst.STORAGE))
            originalReader("custodian",CollectionConst.STORAGE)
            actorRole("custodian",CollectionConst.ROLE_KEEPER)
            block()
        }
    }
    private fun reject(block:()->Unit):BadRequestException {
        LoanComplianceRecordDao.all().forEach {it.flush()};LoanDao.all().forEach {it.flush()};RequisitionDao.findById(req)?.flush();CollectionDao.findById("piece")?.flush()
        CollectionCareCheckDao.all().forEach {it.flush()};CollectionCareWorkDao.all().forEach {it.flush()}
        CollectionFragileMonitoringDao.all().forEach {it.flush()};
        CollectionFragileAutomaticCheckDao.all().forEach {it.flush()};CollectionFragileMonitorRuntimeDao.all().forEach {it.flush()}
        CollectionFragilityAssessmentDao.all().forEach {it.flush()};CollectionFragileUsePlanDao.all().forEach {it.flush()};CollectionFragileUseRecordDao.all().forEach {it.flush()};CollectionFragileUseAdjustmentDao.all().forEach {it.flush()}
        return assertThrows(BadRequestException::class.java){transaction(db){block()}}
    }
    private val signature=CollectionCareSignatureVo("本人核对材质、历史、限额和保护措施","proof")
    private fun assessment(fragile:Boolean=true,limits:FragileUseLimitsVo=FragileUseLimitsVo(30,3,4,24,0))=FragilityAssessmentVo(sourceRevision=service.detail("piece")["sourceRevision"] as String,assessedAt=now(),fragileMaterial=fragile,materialAndCondition="实际材质与当前保存状况",environmentRequirements="严格依照本件监测和保护要求",storageProtection="科学支撑和专用囊匣",useRiskAssessment="已核对必要性、替代物及减少使用方案",conservationUserId="conservation",keeperUserId="keeper",historyVerifiedFrom=now().minusDays(40),historyReviewFileIds="proof",assessmentFileIds="proof",limits=if(fragile)limits else null,environmentCriteria=if(fragile)EnumFragileUseKind.entries.map {criterion(it)} else emptyList())
    private fun criterion(kind:EnumFragileUseKind=EnumFragileUseKind.DISPLAY)=FragileEnvironmentCriterionVo(kind,"TEMPERATURE","温度","℃",18.0,24.0,300,"本件实际专业评估及仪器测量能力")
    private fun source(kind:EnumFragileUseKind=EnumFragileUseKind.DISPLAY,index:Int=0,context:String="本馆实际展柜")=FragileEnvironmentSourceVo(kind,index,"TEMPERATURE",EnumFragileEnvironmentSource.SIGNED_ON_SITE,context,"经核验温度仪与测量方法","实际测量本件展柜或运输包装环境","proof")
    private fun measured(context:String="本馆实际展柜")=FragileEnvironmentReadingVo("TEMPERATURE",20.0,"℃",now().minusSeconds(1),"经核验温度仪与测量方法",context,"现场实际测量人","proof")
    private fun sources(p:FragileUsePlanVo)=p.copy(environmentSources=buildList {if(p.displayFrom!=null)add(source());p.transports.forEachIndexed {i,t->add(source(EnumFragileUseKind.TRANSPORT,i,t.route))}})
    private fun sign(id:String){actor("conservation");service.sign("piece",id,signature);actor("keeper");service.sign("piece",id,signature);actor("operator")}
    private fun assessed(fragile:Boolean=true,limits:FragileUseLimitsVo=FragileUseLimitsVo(30,3,4,24,0)):String {val a=service.assess("piece",assessment(fragile,limits));sign(a);return a}
    private fun usePlan(a:String)=sources(FragileUsePlanVo(a,CollectionConst.BIZ_REQUISITION,req,service.source("piece",CollectionConst.BIZ_REQUISITION,req)["sourceRevision"] as String,at,at.plusHours(1),environmentAndProtection="逐项核对温湿度光照与防护设施",necessityAndAlternatives="已采用替代展示并缩短原件使用",signedPlanFileIds="proof"))
    private fun planned(a:String=assessed()):String {val p=service.savePlan("piece",usePlan(a));sign(p);return p}
    private fun approve() {
        val r=RequisitionDao[req];r.flowInstanceId="flow";r.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire("museum",CollectionConst.BIZ_REQUISITION,req,listOf("piece"))
        listOf("department","keeper","leader").forEach {node->FlowInstanceLogDao.new {instanceId="flow";taskId="task-$node";nodeKey=node;action="APPROVE";operator=statutory.actors(r)[node];comment="本人确认完整使用及保护方案";createTime=now()} }
        requisitions.onFlowCompleted(r,FlowCompletedEvent("flow",CollectionConst.FLOW_REQUISITION,"提用",req,CollectionConst.BIZ_REQUISITION,emptyMap(),"operator","operator"))
    }
    private fun out() {
        val reviewed=now()
        statutory.recordHandover(req,RequisitionHandoverVo(now(),reviewed,"keeper","proof",setOf("custodian"),setOf("operator"),listOf(RequisitionConditionVo("piece","按实际原件逐件核对",false)),true,true,"proof","proof"))
        val previous=SecurityContextHolder.getContext().authentication
        actor("custodian",CollectionConst.STORAGE)
        try { requisitions.executeOut(req,CollectionOutboundExecutionVo("实际点交","proof","proof")) }
        finally { SecurityContextHolder.getContext().authentication=previous }
    }
    private fun begin(p:String)=service.start("piece",p,FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator","本馆实际展柜","实际环境及保护措施核对","proof",listOf(measured())))
    private fun assertFragileSelection(stage:String,change:String)=fixture {
        val p=planned();approve();out();val id=begin(p);val use=CollectionFragileUseRecordDao[id]
        if(stage=="ENDED")service.end("piece",id,FragileUseEndVo(now(),"实际位置","如实结束","proof"))
        val oldStart=use.started;val oldEnd=use.ended
        when(change) {
            "END" -> use.ended=if(stage=="RUNNING")FragileUseEndVo(now(),"实际位置","未经封存的结束","proof") else null
            "START" -> use.started=null
            else -> error(change)
        }
        val tables=arrayOf(CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,CollectionFragileMonitorings,CollectionFragileAutomaticChecks,Collections,Requisitions,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val before=settledSafetySelectionState(*tables)
        assertSafetySelectionIntegrity({monitorGuard.requireSafeResolution("piece")},{monitorGuard.requireExclusiveWork("piece","other","other")},{monitorGuard.requireAllowed("piece","other","other")})
        assertEquals(before,settledSafetySelectionState(*tables))
        use.started=oldStart;use.ended=oldEnd;settledSafetySelectionState(*tables)
        if(stage=="RUNNING")assertThrows(BadRequestException::class.java){monitorGuard.requireSafeResolution("piece")}
        else assertDoesNotThrow {monitorGuard.requireSafeResolution("piece")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("RUNNING,END","RUNNING,START","ENDED,END","ENDED,START")
    fun `operation safety selection fragile verifies actual stages before filtering`(stage:String,change:String)=assertFragileSelection(stage,change)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("RUNNING,END","RUNNING,START","ENDED,END","ENDED,START")
    fun `postgres operation safety selection fragile verifies actual stages before filtering`(stage:String,change:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertFragileSelection(stage,change)} finally {safetyDatabase=null}
    }
    private fun assertFragileRealAudit(kind:String)=fixture {
        val p=planned();approve();out();val use=CollectionFragileUseRecordDao[begin(p)]
        settledSafetySelectionState(CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords)
        actor("keeper")
        when(kind) {
            "ASSESSMENT" -> {val a=CollectionFragilityAssessmentDao[CollectionFragileUsePlanDao[p].content.assessmentId];assertSafetySelectionRealAudit(CollectionFragilityAssessments,a){a.collectionId="actual-second-piece"}}
            "PLAN" -> {val plan=CollectionFragileUsePlanDao[p];assertSafetySelectionRealAudit(CollectionFragileUsePlans,plan){plan.sourceSnapshot=plan.sourceSnapshot+" "}}
            "USE" -> assertSafetySelectionRealAudit(CollectionFragileUseRecords,use){use.started=use.started!!.copy(actualLocationOrRoute="实际第二次现场字段变更")}
            else -> error(kind)
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["ASSESSMENT","PLAN","USE"])
    fun `operation safety selection fragile preserves real audit changes`(kind:String)=assertFragileRealAudit(kind)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["ASSESSMENT","PLAN","USE"])
    fun `postgres operation safety selection fragile preserves real audit changes`(kind:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertFragileRealAudit(kind)} finally {safetyDatabase=null}
    }
    @Test fun `fragility original download resolves only the actual museum collection record phase and file`()=fixture {
        val a=assessed()
        val p=planned(a)
        val assessmentArchive=originals.requireArchive("piece","museum",a,"ASSESSMENT","proof")
        assertNotNull(assessmentArchive)
        assertNotNull(originals.requireArchive("piece","museum",a,"CONSERVATION","proof"))
        assertNotNull(originals.requireArchive("piece","museum",p,"USE_PLAN","proof"))
        assertNotNull(originals.requireArchive("piece","museum",p,"KEEPER","proof"))
        reject {originals.requireArchive("another","museum",a,"ASSESSMENT","proof")}
        reject {originals.requireArchive("piece","other",a,"ASSESSMENT","proof")}
        reject {originals.requireArchive("piece","museum",a,"USE_PLAN","proof")}
        reject {originals.requireArchive("piece","museum",p,"HISTORY","proof")}
        reject {originals.requireArchive("piece","museum",p,"USE_PLAN","missing")}

        approve();out();val use=begin(p);Thread.sleep(5)
        assertNotNull(originals.requireArchive("piece","museum",use,"STARTED","proof"))
        assertNotNull(originals.requireArchive("piece","museum",use,"ENVIRONMENT_VERIFIED","proof"))
        reject {originals.requireArchive("piece","museum",use,"HISTORY","proof")}
        val monitor=monitoring.inspect("piece",use,monitoringRequest())
        assertNotNull(originals.requireArchive("piece","museum",monitor,"MONITORED","proof"))
        reject {originals.requireArchive("piece","museum",monitor,"MONITOR_CLEARED_1","proof")}
        val auto=CollectionFragileAutomaticMonitoringService(archives,emptyList()).scan(use)!!
        reject {originals.requireArchive("piece","museum",auto,"AUTOMATIC_MONITORED","proof")}
        val autoSamples=monitoring.samples("piece",auto,1,20,true)["list"] as List<*>
        assertTrue(autoSamples.isNotEmpty())
        assertEquals(use,(autoSamples.first() as Map<*, *>)["originalRecordId"])
        assertEquals("ENVIRONMENT_VERIFIED",(autoSamples.first() as Map<*, *>)["originalPhase"])
        val surplus=FileDao.new("surplus") {name="不属于评估的附件.pdf";path="surplus";hash="surplus";size=100;ext="pdf"}
        CollectionPermanentArchiveFileDao.new {
            orgId="museum";archiveId=assessmentArchive;bizId=a;fileId=surplus.id.value
            metadata=CollectionArchiveFileIdentity.metadata(surplus,"museum",CollectionConst.BIZ_FRAGILITY,a,setOf("0".repeat(64)))
        }
        assertTrue(reject {originals.requireArchive("piece","museum",a,"ASSESSMENT","proof")}.message!!.contains("引用缺失"))
    }
    @Test fun `fragility adjustment original remains tied to the verified proposal and actual signatures`()=fixture {
        val p=planned();approve();out();val change=adjustments.propose("piece",p,adjustment(p,checked()))
        assertNotNull(originals.requireArchive("piece","museum",change,"ADJUSTMENT","proof"))
        reject {originals.requireArchive("piece","museum",change,"ADJUSTMENT_DIRECTOR","proof")}
        confirmAdjustment(change)
        assertNotNull(originals.requireArchive("piece","museum",change,"ADJUSTMENT_DIRECTOR","proof"))
        reject {originals.requireArchive("piece","museum",change,"ADJUSTMENT_WITHDRAWN","proof")}
        reject {originals.requireArchive("piece","museum",change,"MONITORED","proof")}
    }
    @Test fun `unknown assessment is never treated as non fragile in real requisition approval`()=fixture {
        reject {approve()}
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
        assertEquals(0,StorageMovementDao.all().toList().size)
        assessed(false);approve();out()
        assertEquals(1,StorageMovementDao.all().toList().size)
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `actual outbound does not fabricate display or transportation and actual end needs own inspection`()=fixture {
        val p=planned();approve();out()
        assertTrue(RequisitionDao[req].outExecuted);assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
        val r=begin(p);val end=FragileUseEndVo(now(),"实际结束后保管位置","实际结束和保护结果","proof")
        service.end("piece",r,end)
        assertNull(CollectionFragileUseRecordDao[r].review)
        val ck=care.inspect("piece",CollectionCareCheckVo(checkedAt=now(),operatorUserId="operator",actualLocation="实际保管位置",physicallyPresent=true,custodianUserId="custodian",condition="实际稳定",diseases="无新增病害",environmentObservation="实际数据",damaged=false,safeForRoutineCare=true,actualMeasures="支撑保护",followUp="继续复核",conditionFileIds="proof",signedFileIds="proof"), CollectionCareEvidence.digest(CollectionCareEvidence.json(CollectionCareEvidence.catalog(CollectionDao["piece"]))))
        actor("keeper");service.review("piece",r,FragileUseReviewVo(ck,"proof"))
        assertEquals(ck,CollectionFragileUseRecordDao[r].review!!.checkId)
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertFalse(RequisitionDao[req].returned)
    }
    @Test fun `planned display exceeding assessed cap cannot be signed or used`()=fixture {
        val a=assessed(limits=FragileUseLimitsVo(30,3,4,0,0))
        reject {service.savePlan("piece",usePlan(a))}
        assertEquals(0,CollectionFragileUsePlanDao.all().toList().size)
        reject {approve()}
    }
    @Test fun `historical extraction counts are checked again at actual outbound`()=fixture {
        val a=assessed(limits=FragileUseLimitsVo(30,1,4,24,0));planned(a);approve()
        StorageMovementDao.new {orgId="museum";collectionId="piece";type=EnumMoveType.OUT;reason=EnumMoveReason.RESEARCH;operatedAt=at.minusDays(2);refBizId="real-old-usage"}
        reject {out()}
        assertFalse(RequisitionDao[req].outExecuted)
        assertEquals(EnumCollectionStatus.PRE_OUT,CollectionDao["piece"].lifeStatus)
    }
    @Test fun `signed transport arrangements reserve frequency until real occurrence is recorded`()=fixture {
        RequisitionDao[req].purpose=EnumMoveReason.RESEARCH
        val a=assessed(limits=FragileUseLimitsVo(30,3,1,24,0))
        val v=usePlan(a).copy(displayFrom=null,displayTo=null,transports=listOf(FragileTransportPlanVo(at,at.plusMinutes(1),"出发至场所","保护"),FragileTransportPlanVo(at.plusHours(2),at.plusHours(3),"场所返回","保护")))
        assertTrue(reject {service.savePlan("piece",sources(v))}.message!!.contains("运输"))
        assertEquals(0,CollectionFragileUsePlanDao.all().toList().size)
    }
    @Test fun `historical display is dated and cannot be erased by replacing assessment`()=fixture {
        val history=FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,at.minusDays(2),at.minusDays(1),"实际展览原件","实际展出时长","proof")
        val id=service.history("piece",history);assertEquals(id,service.history("piece",history))
        val a=assessed(limits=FragileUseLimitsVo(30,3,4,24,0))
        reject {service.savePlan("piece",usePlan(a))}
        val next=service.assess("piece",assessment(limits=FragileUseLimitsVo(30,3,4,24,0)).copy(previousAssessmentId=a,useRiskAssessment="重新核对，仍采用原限额"));sign(next)
        reject {service.savePlan("piece",usePlan(next))}
        assertEquals(1,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `old history outside rolling window does not permanently prohibit current use`()=fixture {
        service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,at.minusDays(100),at.minusDays(90),"以前展览","如实保留既有事实","proof"))
        planned();assertDoesNotThrow{approve()}
    }
    @Test fun `actual signatures source changes and permanent attachments are enforced`()=fixture {
        val a=service.assess("piece",assessment())
        reject {service.sign("piece",a,signature)}
        actor("keeper");reject {service.sign("piece",a,signature)};actor("operator");sign(a)
        val p=planned(a)
        val reqRow=RequisitionDao[req];reqRow.dossier=reqRow.dossier!!.copy(usageSite="后来改换场所")
        reject {approve()}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
        CollectionFragileUsePlanDao[p].content=usePlan(a).copy(environmentAndProtection="篡改原件")
        reject {service.detail("piece")}
    }
    @Test fun `foreign museum missing file future history and unsigned plan cannot authorize use`()=fixture {
        reject {service.assess("piece",assessment().copy(assessmentFileIds="missing"))}
        assertEquals(0,CollectionFragilityAssessmentDao.all().toList().size)
        val a=assessed();service.savePlan("piece",usePlan(a));reject {approve()}
        reject {service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.TRANSPORT,now(),now().plusDays(1),"未发生","未发生","proof"))}
        CollectionDao["piece"].orgId="other";reject {service.detail("piece")}
    }
    @Test fun `actual start needs real out original and cannot replay a segment as another event`()=fixture {
        val p=planned();reject {begin(p)};approve();out()
        val value=FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator","本馆实际展柜","实际核实环境","proof",listOf(measured()))
        val r=service.start("piece",p,value);assertEquals(r,service.start("piece",p,value))
        reject {service.start("piece",p,value.copy(startedAt=now()))}
        val ended=FragileUseEndVo(now(),"保管位置","实际结束","proof");service.end("piece",r,ended)
        reject {service.end("piece",r,ended.copy(actualResult="替换历史"))}
    }
    @Test fun `submitted protection plan and signatures cannot be replaced`()=fixture {
        val p=planned();approve()
        assertFalse(service.source("piece",CollectionConst.BIZ_REQUISITION,req)["editable"] as Boolean)
        reject {service.savePlan("piece",CollectionFragileUsePlanDao[p].content.copy(necessityAndAlternatives="事后改变保护要求"))}
        assertEquals(1,CollectionFragileUsePlanDao.all().toList().size)
    }
    @Test fun `new non fragile assessment cannot authorize a previously fragile signed use`()=fixture {
        val a=assessed();val p=planned(a);approve();out()
        val revised=service.assess("piece",assessment(false).copy(previousAssessmentId=a));sign(revised)
        reject {begin(p)}
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `new incident prevents actual start but does not block truthful end`()=fixture {
        val p=planned();approve();out()
        val r=begin(p)
        CollectionIncidentItemDao.new {orgId="museum";incidentId="new-incident";collectionId="piece";restricted=true}.flush()
        service.end("piece",r,FragileUseEndVo(now(),"采取防护后的实际位置","如实停止并处理事故","proof"))
        assertNotNull(CollectionFragileUseRecordDao[r].ended)
        assertTrue(CollectionIncidentItemDao.all().single().restricted)
    }
    @Test fun `unsafe actual care inspection cannot be bypassed by a display start endpoint`()=fixture {
        val p=planned();approve();out()
        care.inspect("piece",CollectionCareCheckVo(checkedAt=now(),operatorUserId="operator",actualLocation="展前位置",physicallyPresent=true,custodianUserId="custodian",condition="发现损伤",diseases="新增异常",environmentObservation="现场数据",damaged=true,safeForRoutineCare=false,actualMeasures="暂停使用",followUp="调查",conditionFileIds="proof",signedFileIds="proof"), CollectionCareEvidence.digest(CollectionCareEvidence.json(CollectionCareEvidence.catalog(CollectionDao["piece"]))))
        assertTrue(reject {
            assertTrue(CollectionCareChecks.selectAll().single()[CollectionCareChecks.content].damaged)
            begin(p)
        }.message!!.contains("异常"))
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `actual received object cannot start another event under old outbound flag`()=fixture {
        val p=planned();approve();out()
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_STORE
        assertTrue(reject {begin(p)}.message!!.contains("实物已不在"))
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `real loan outbound and transport keep return leg reserved and require actual post trip review`()=fixture {
        actorRole("operator","LOAN_OPERATOR",setOf(CollectionConst.STORAGE))
        val c=CollectionDao["piece"];c.nature=EnumCollectionNature.RELIC;c.level=EnumRelicLevel.GENERAL;c.preserveState=EnumPreserve.A;c.registeredAt=at.minusYears(1)
        CollectionOrgDao["museum"].ownership=EnumOwnership.STATE
        CollectionCatalogVersionDao.new {orgId="museum";collectionId="piece";catalogId="catalog";version=1;catalogStatus=EnumCatalogStatus.APPROVED;content=CollectionCatalogMapper.fromCollection(c.toModel())}
        val d=LoanDossierVo(borrowerKind=EnumLoanBorrowerKind.STATE_MUSEUM,borrowerRegistrationNo="BORROWER",borrowerIdentityFileIds="proof",purpose=EnumLoanPurpose.RESEARCH,purposeDescription="实际研究",keeperUserId="keeper",leaderUserId="director",supervisionUserId="custodian",agreedOutAt=at.minusHours(1),agreedReturnAt=at.plusDays(2),collectiveDecisionAt=at.minusDays(5),collectiveDecisionFileIds="proof",collectiveParticipantNames=setOf("甲","乙"),agreementSignedAt=at.minusDays(4),signedAgreementFileIds="proof",agreedCollectionListFileIds="proof",safetyResponsibilities="实际双方责任",transportRequirements="专用防护",storageRequirements="严格环境与保卫",useSite="研究场所",carrierName="实际运输机构",carrierCapabilityFileIds="proof",transportSupervisorName="馆方监督",transportSupervisorOrganization="本馆",transportAssignmentFileIds="proof",firstInspectionOn=at.toLocalDate().plusDays(1),inspectionIntervalDays=1,inspectionPlanFileIds="proof",assessments=listOf(LoanAssessmentVo("piece",EnumLoanObjectKind.RELIC,"稳定",false,false,"必要的原件研究","实际风险核查","proof")),exhibitionOverseas=false,transportStandardReference="GB/T 23862-2024",transportStandardApplicability="本件按材质核对",transportPlanFileIds="proof")
        val loan=loans.save(LoanVo(title="研究借用",borrower="借入馆",protocolNo="L-1",collectionIds="piece",operator="operator",reviewer="reviewer",loanDate=d.agreedOutAt.toLocalDate(),dossier=d))
        fun approveLoan() {
            val r=LoanDao[loan];r.flowInstanceId="loan-flow";r.flowStatus=EnumFlowStatus.AUDIT
            locks.acquire("museum",CollectionConst.BIZ_LOAN,loan,listOf("piece"))
            loanStatutory.actors(r).forEach {(node,user)->FlowInstanceLogDao.new {instanceId="loan-flow";taskId="loan-task-$node";nodeKey=node;action="APPROVE";operator=user;comment="本人核实借用和保护方案";createTime=now()}}
            loans.onFlowCompleted(r,FlowCompletedEvent("loan-flow",CollectionConst.FLOW_LOAN,"研究借用",loan,CollectionConst.BIZ_LOAN,emptyMap(),"operator","operator"))
        }
        reject {approveLoan()}
        val a=assessed(limits=FragileUseLimitsVo(30,1,2,0,0))
        val v=sources(FragileUsePlanVo(a,CollectionConst.BIZ_LOAN,loan,service.source("piece",CollectionConst.BIZ_LOAN,loan)["sourceRevision"] as String,transports=listOf(FragileTransportPlanVo(at,at.plusHours(1),"本馆至借入馆","出借运输防护"),FragileTransportPlanVo(at.plusDays(1),at.plusDays(1).plusHours(1),"借入馆至本馆","返回运输防护")),environmentAndProtection="严格核实实际环境与保护",necessityAndAlternatives="必要研究及替代评估",signedPlanFileIds="proof"))
        reject {service.savePlan("piece",v.copy(transports=v.transports.take(1)))}
        val p=service.savePlan("piece",v);sign(p);approveLoan()
        loanStatutory.recordHandover(loan,LoanHandoverVo(now(),"keeper","proof",now(),setOf("custodian","custodian2"),setOf("接收甲","接收乙"),"proof",true,listOf(RequisitionConditionVo("piece","实际完整状况",false)),true,true,"proof","proof",appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="proof"))
        loans.executeOut(loan,CollectionOutboundExecutionVo("实际外借点交","proof","proof"))
        assertEquals(EnumCollectionStatus.ON_LOAN,c.lifeStatus)
        assertEquals(1,StorageMovementDao.all().toList().size)
        val start=FragileUseStartVo(EnumFragileUseKind.TRANSPORT,0,now(),"operator","本馆至借入馆","实际包装环境监测","proof",listOf(measured("本馆至借入馆")))
        val actual=service.start("piece",p,start)
        service.end("piece",actual,FragileUseEndVo(now(),"借入馆实际接收地点","运输结束，准备逐件检查","proof"))
        assertNull(CollectionFragileUseRecordDao[actual].review)
        reject {service.start("piece",p,start.copy(segmentIndex=1))}
        assertFalse(LoanDao[loan].returned)
        assertEquals(1,CollectionFragileUseRecordDao.all().toList().size)
        val check=checked()
        actor("keeper");service.review("piece",actual,FragileUseReviewVo(check,"proof"));actor("operator")
        val revised=adjustment(p,check,listOf(FragileSegmentAdjustmentVo(EnumFragileUseKind.TRANSPORT,1,EnumFragileAdjustmentAction.RESCHEDULE,"核实返回运输时长调整",at.plusDays(1),at.plusDays(1).plusHours(2),"借入馆至本馆","新的实际保护措施")))
        assertTrue(reject {adjustments.propose("piece",p,revised)}.message!!.contains("双方实际签署"))
        val adjustmentId=adjustments.propose("piece",p,revised.copy(borrowerAgreementFileIds="proof"));confirmAdjustment(adjustmentId,true)
        assertEquals(at.plusDays(1).plusHours(2),CollectionFragileAdjustmentEvidence.segments(CollectionFragileUsePlanDao[p]).last().to)
        assertNotNull(CollectionFragileUseRecordDao[actual].review)
        val issue=loanStatutory.recordMonitoring(loan,LoanMonitoringVo(EnumLoanMonitoringKind.SAFETY_CHECK,now(),setOf("piece"),"custodian","借用馆代表","新发现的使用安全问题","proof",safetyConfirmed=false))
        assertTrue(reject {CollectionFragilityEvidence.requireUse(c,CollectionConst.BIZ_LOAN,loan)}.message!!.contains("借用期间"))
        val resolved=loanStatutory.recordMonitoring(loan,LoanMonitoringVo(EnumLoanMonitoringKind.ISSUE_RESOLVED,now(),setOf("piece"),"custodian","借用馆代表","实际处理后共同核实安全","proof",safetyConfirmed=true,relatedRecordId=issue))
        assertDoesNotThrow {CollectionFragilityEvidence.requireUse(c,CollectionConst.BIZ_LOAN,loan)}
        service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.TRANSPORT,at.minusDays(3),at.minusDays(3).plusHours(1),"新发现旧运输原件","如实保留实际运输","proof"))
        assertTrue(reject {CollectionFragilityEvidence.requireUse(c,CollectionConst.BIZ_LOAN,loan)}.message!!.contains("运输"))
        val frozenChange=adjustments.propose("piece",p,adjustment(p,check,listOf(FragileSegmentAdjustmentVo(EnumFragileUseKind.TRANSPORT,1,EnumFragileAdjustmentAction.CANCEL,"核实返回运输未实施"))))
        assertNotNull(originals.requireLoanMonitoringSource("piece","museum",frozenChange,issue,"proof"))
        assertNotNull(originals.requireLoanMonitoringSource("piece","museum",frozenChange,resolved,"proof"))
        reject {originals.requireLoanMonitoringSource("another","museum",frozenChange,issue,"proof")}
        reject {originals.requireLoanMonitoringSource("piece","other",frozenChange,issue,"proof")}
        reject {originals.requireLoanMonitoringSource("piece","museum",adjustmentId,issue,"proof")}
        reject {originals.requireLoanMonitoringSource("piece","museum",frozenChange,"missing","proof")}
        reject {originals.requireLoanMonitoringSource("piece","museum",frozenChange,issue,"missing")}
    }
    @Test fun `new inventory hold blocks actual display without deleting old approval`()=fixture {
        val p=planned();approve();out()
        CollectionDao["piece"].inventoryHold=true
        assertTrue(reject {begin(p)}.message!!.contains("清点异常"))
        assertTrue(RequisitionDao[req].outExecuted)
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `current exhibit must respect the assessed interval after a real prior display`()=fixture {
        service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,at.minusHours(2),at.minusHours(1),"之前实际展示","结束原件","proof"))
        val a=assessed(limits=FragileUseLimitsVo(30,3,4,24,2))
        val next=usePlan(a).copy(displayFrom=now().plusMinutes(1),displayTo=now().plusHours(1))
        assertTrue(reject {service.savePlan("piece",next)}.message!!.contains("间隔"))
        assertEquals(0,CollectionFragileUsePlanDao.all().toList().size)
        assertDoesNotThrow {service.savePlan("piece",next.copy(displayFrom=at.plusHours(2),displayTo=at.plusHours(3)))}
    }
    @Test fun `changing actual applicant or destination invalidates the signed use protection snapshot`()=fixture {
        val p=planned()
        RequisitionDao[req].toWarehouseId="new-store"
        reject {CollectionFragilityEvidence.requireUse(CollectionDao["piece"],CollectionConst.BIZ_REQUISITION,req)}
        assertFalse((service.detail("piece")["plans"] as List<Map<String,Any?>>).single()["current"] as Boolean)
        assertNotNull(CollectionFragileUsePlanDao[p].keeper)
    }
    private fun checked(safe:Boolean=true):String {
        CollectionFragileUseRecordDao.all().filter {it.started!=null && it.ended!=null}.forEach {use->
            val latest=CollectionFragileMonitoringEvidence.all("piece").filter {it.useRecordId==use.id.value}.lastOrNull()
            if(latest==null || latest.request.checkedAt<use.ended!!.endedAt)monitoring.inspect("piece",use.id.value,monitoringRequest())
        }
        return care.inspect("piece",CollectionCareCheckVo(checkedAt=now(),operatorUserId="operator",actualLocation="实际保管位置",physicallyPresent=true,custodianUserId="custodian",condition=if(safe)"实际稳定" else "发现新增损伤",diseases=if(safe)"无新增病害" else "新增异常",environmentObservation="实际监测数据",damaged=!safe,safeForRoutineCare=safe,actualMeasures=if(safe)"已核对保护" else "暂停使用并防护",followUp="持续检查",conditionFileIds="proof",signedFileIds="proof"), CollectionCareEvidence.digest(CollectionCareEvidence.json(CollectionCareEvidence.catalog(CollectionDao["piece"]))))
    }
    private fun adjustment(p:String,check:String,items:List<FragileSegmentAdjustmentVo> = listOf(FragileSegmentAdjustmentVo(EnumFragileUseKind.DISPLAY,0,EnumFragileAdjustmentAction.CANCEL,"实际核实未开展原展出")))=FragileUseAdjustmentVo(
        previousAdjustmentId=CollectionFragileAdjustmentEvidence.all(p).lastOrNull()?.id?.value,
        factsRevision=CollectionFragilityEvidence.digest(CollectionFragileAdjustmentEvidence.facts(CollectionDao["piece"],CollectionFragileUsePlanDao[p])),
        reviewedAt=now(),checkId=check,reason="依据实际现状变更剩余使用",actualUnstartedEvidence="查阅现场记录并核对人员及实物，所选段从未实际开始",adjustments=items,signedFileIds="proof")
    private fun confirmAdjustment(id:String,loan:Boolean=false) {
        listOfNotNull("CONSERVATION" to "conservation",if(loan)null else "DEPARTMENT" to "department","KEEPER" to "keeper","DIRECTOR" to "director").forEach {(role,user)->actor(user);adjustments.sign("piece",id,role,signature)}
        actor("operator")
    }
    private fun reschedule(from:LocalDateTime,to:LocalDateTime)=listOf(FragileSegmentAdjustmentVo(EnumFragileUseKind.DISPLAY,0,EnumFragileAdjustmentAction.RESCHEDULE,"缩短原展览的原件使用",from,to,protection="按原用途场所加强现场防护"))
    @Test fun `verified unstarted cancellation releases reservation only after all actual confirmations`()=fixture {
        val p=planned();approve();out();val check=checked()
        val original=CollectionFragileUsePlanDao[p].content
        val change=adjustments.propose("piece",p,adjustment(p,check))
        assertTrue(reject {begin(p)}.message!!.contains("共同确认"))
        assertFalse(CollectionFragileAdjustmentEvidence.segments(CollectionFragileUsePlanDao[p]).single().cancelled)
        actor("director");reject {adjustments.sign("piece",change,"DIRECTOR",signature)};actor("operator")
        confirmAdjustment(change)
        assertTrue(CollectionFragileAdjustmentEvidence.segments(CollectionFragileUsePlanDao[p]).single().cancelled)
        reject {begin(p)}
        assertEquals(original,CollectionFragileUsePlanDao[p].content)
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
        assertEquals(1,StorageMovementDao.all().toList().size)
        assertFalse(RequisitionDao[req].returned)
        reject {adjustments.withdraw("piece",change,FragileAdjustmentWithdrawalVo(now(),"撤去已确认事实","proof"))}
    }
    @Test fun `actual started segment can never be cancelled as unstarted even after a truthful end`()=fixture {
        val p=planned();approve();out();val actual=begin(p)
        val check=checked()
        reject {adjustments.propose("piece",p,adjustment(p,check))}
        service.end("piece",actual,FragileUseEndVo(now(),"实际位置","实际结束而非未实施","proof"))
        val after=checked()
        reject {adjustments.propose("piece",p,adjustment(p,after))}
        assertEquals(0,CollectionFragileUseAdjustmentDao.all().toList().size)
        assertNotNull(CollectionFragileUseRecordDao[actual].ended)
    }
    @Test fun `rescheduled segment uses new prospective approval and keeps original timetable and extraction`()=fixture {
        val p=planned();approve();out();val check=checked()
        val start=now().plusMinutes(1);val finish=start.plusMinutes(5)
        val v=adjustment(p,check,reschedule(start,finish))
        val id=adjustments.propose("piece",p,v);confirmAdjustment(id)
        reject {begin(p)}
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock->
            clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenReturn(start.plusSeconds(1))
            val record=begin(p)
            assertEquals(start.plusSeconds(1),CollectionFragileUseRecordDao[record].started!!.startedAt)
            assertEquals(id,CollectionFragileUseRecordDao[record].authorizationAdjustmentId)
            assertDoesNotThrow {CollectionFragilityEvidence.record(CollectionFragileUseRecordDao[record])}
            assertEquals(finish,CollectionFragileAdjustmentEvidence.segments(CollectionFragileUsePlanDao[p]).single().to)
        }
        assertEquals(at,CollectionFragileUsePlanDao[p].content.displayFrom)
        assertEquals(1,StorageMovementDao.all().toList().size)
    }
    @Test fun `new actual history invalidates pending signatures and requires a new immutable review`()=fixture {
        val p=planned();approve();out();val check=checked();val v=adjustment(p,check)
        val old=adjustments.propose("piece",p,v);actor("conservation");adjustments.sign("piece",old,"CONSERVATION",signature);actor("operator")
        service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.TRANSPORT,at.minusDays(2),at.minusDays(2).plusHours(1),"新查明实际运输","实际路线记录","proof"))
        actor("department");assertTrue(reject {adjustments.sign("piece",old,"DEPARTMENT",signature)}.message!!.contains("事实"));actor("operator")
        val revised=adjustments.propose("piece",p,adjustment(p,check));confirmAdjustment(revised)
        assertNotNull(CollectionFragileUseAdjustmentDao[old].conservation)
        assertNull(CollectionFragileUseAdjustmentDao[old].director)
        assertEquals(2,CollectionFragileUseAdjustmentDao.all().toList().size)
        assertEquals(1,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `withdrawing unconfirmed change restores original authorization without losing signatures`()=fixture {
        val p=planned();approve();out();val change=adjustments.propose("piece",p,adjustment(p,checked()))
        actor("conservation");adjustments.sign("piece",change,"CONSERVATION",signature);actor("operator")
        val withdrawal=FragileAdjustmentWithdrawalVo(now(),"核查方案需重新研究，撤回尚未生效变更","proof")
        adjustments.withdraw("piece",change,withdrawal)
        assertEquals(change,adjustments.withdraw("piece",change,withdrawal))
        assertNotNull(CollectionFragileUseAdjustmentDao[change].conservation)
        assertFalse(CollectionFragileAdjustmentEvidence.segments(CollectionFragileUsePlanDao[p]).single().cancelled)
        assertDoesNotThrow {begin(p)}
    }
    @Test fun `truthful cancellation can be confirmed during an incident but cannot clear risk or authorize rescheduling`()=fixture {
        val p=planned();approve();out();val check=checked(false)
        CollectionIncidentItemDao.new {orgId="museum";incidentId="actual-risk";collectionId="piece";restricted=true}.flush()
        reject {adjustments.propose("piece",p,adjustment(p,check,reschedule(now().plusMinutes(1),now().plusMinutes(5))))}
        val id=adjustments.propose("piece",p,adjustment(p,check));confirmAdjustment(id)
        assertTrue(CollectionIncidentItemDao.all().single().restricted)
        assertTrue(CollectionCareEvidence.unsafe(CollectionCareCheckDao[check]))
        reject {begin(p)}
        assertFalse(RequisitionDao[req].returned)
    }
    @Test fun `adjustment rejects missing originals foreign museum fabricated dates and source expansion`()=fixture {
        val p=planned();val checkBefore=checked()
        reject {adjustments.propose("piece",p,adjustment(p,checkBefore))}
        approve();out();val check=checked();val v=adjustment(p,check)
        reject {adjustments.propose("piece",p,v.copy(signedFileIds="missing"))}
        reject {adjustments.propose("piece",p,v.copy(reviewedAt=now().plusHours(1)))}
        reject {adjustments.propose("piece",p,adjustment(p,check,reschedule(now().minusMinutes(1),now().plusMinutes(5))))}
        reject {adjustments.propose("piece",p,adjustment(p,check,reschedule(now().plusDays(2),now().plusDays(3))))}
        assertEquals(0,CollectionFragileUseAdjustmentDao.all().toList().size)
        CollectionDao["piece"].orgId="other";reject {adjustments.propose("piece",p,v)}
    }
    @Test fun `cancellation never removes real history from assessed limits`()=fixture {
        val p=planned(assessed(limits=FragileUseLimitsVo(30,1,1,24,0)));approve();out();val check=checked()
        service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,at.minusDays(3),at.minusDays(1),"新查明的历史超时展示","如实保留两天实际展示","proof"))
        val id=adjustments.propose("piece",p,adjustment(p,check));confirmAdjustment(id)
        assertTrue(reject {CollectionFragilityEvidence.requireUse(CollectionDao["piece"],CollectionConst.BIZ_REQUISITION,req)}.message!!.contains("累计展出"))
        assertEquals(1,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `signed adjustment originals and files cannot be overwritten or removed`()=fixture {
        val p=planned();approve();out();val v=adjustment(p,checked());val id=adjustments.propose("piece",p,v);confirmAdjustment(id)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
        CollectionFragileUseAdjustmentDao[id].content=v.copy(reason="篡改已签署核查")
        assertTrue(reject {service.detail("piece")}.message!!.contains("永久原件"))
    }
    @Test fun `returned source cannot be reactivated by a later in use status while unstarted cancellation remains factual`()=fixture {
        val p=planned();approve();out();val check=checked()
        RequisitionDao[req].returned=true
        assertTrue(reject {begin(p)}.message!!.contains("已归还"))
        val v=adjustment(p,check)
        reject {adjustments.propose("piece",p,v.copy(adjustments=reschedule(now().plusMinutes(1),now().plusMinutes(5))))}
        val id=adjustments.propose("piece",p,v);confirmAdjustment(id)
        assertTrue(RequisitionDao[req].returned)
        assertEquals(0,CollectionFragileUseRecordDao.all().toList().size)
    }
    @Test fun `fragile assessments require complete professional numeric criteria without legal defaults`()=fixture {
        val v=assessment()
        assertTrue(reject {service.assess("piece",v.copy(environmentCriteria=emptyList()))}.message!!.contains("数值环境"))
        val c=criterion()
        listOf(c.copy(unit=""),c.copy(maximumSampleAgeSeconds=0),c.copy(minimum=Double.NaN),c.copy(minimum=25.0,maximum=20.0),c.copy(minimum=null,maximum=null),c.copy(metricCode=" temperature")).forEach {bad->
            reject {service.assess("piece",v.copy(environmentCriteria=listOf(bad,criterion(EnumFragileUseKind.TRANSPORT))))}
        }
        reject {service.assess("piece",v.copy(environmentCriteria=listOf(c,c,criterion(EnumFragileUseKind.TRANSPORT))))}
        reject {service.assess("piece",assessment(false).copy(environmentCriteria=listOf(c)))}
        assertEquals(0,CollectionFragilityAssessmentDao.all().count())
    }
    @Test fun `every planned segment binds all assessed metrics and protects instrument originals`()=fixture {
        val a=assessed();val v=usePlan(a)
        listOf(v.copy(environmentSources=emptyList()),v.copy(environmentSources=v.environmentSources+v.environmentSources),
            v.copy(environmentSources=listOf(source().copy(qualificationFileIds="missing"))),
            v.copy(environmentSources=listOf(source().copy(metricCode="HUMIDITY"))),
            v.copy(environmentSources=listOf(source().copy(deviceId="arbitrary")))).forEach {bad->reject {service.savePlan("piece",bad)}}
        val p=planned(a)
        assertTrue(CollectionPermanentArchiveFileDao.all().any {it.bizId==p && it.fileId=="proof"})
    }
    @Test fun `actual start rejects stale unsafe future mismatched and missing signed measurements before writing facts`()=fixture {
        val p=planned();approve();out()
        fun start(r:List<FragileEnvironmentReadingVo>,context:String="本馆实际展柜")=FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator",context,"实际逐项核对","proof",r)
        val v=measured()
        listOf(v.copy(value=25.0),v.copy(value=Double.POSITIVE_INFINITY),v.copy(unit="°F"),v.copy(measuredAt=now().plusSeconds(1)),
            v.copy(measuredAt=now().minusSeconds(301)),v.copy(context="另一展柜"),v.copy(instrumentAndMethod="临时替换仪器"),
            v.copy(measuredBy=""),v.copy(signedFileIds="missing")).forEach {bad->reject {service.start("piece",p,start(listOf(bad)))}}
        reject {service.start("piece",p,start(emptyList()))};reject {service.start("piece",p,start(listOf(v,v)))}
        reject {service.start("piece",p,start(listOf(v),"另一场所"))}
        assertEquals(0,CollectionFragileUseRecordDao.all().count())
        assertFalse(CollectionPermanentArchiveDao.all().any {it.phase=="ENVIRONMENT_VERIFIED"})
        assertTrue(RequisitionDao[req].outExecuted)
    }
    @Test fun `accepted actual numeric evidence is immutable and does not claim continuous protection`()=fixture {
        val p=planned();approve();out();val id=begin(p)
        val r=CollectionFragileUseRecordDao[id]
        val archive=CollectionPermanentArchiveDao[r.environmentArchiveId!!]
        val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(archive.snapshot)
        assertEquals(20.0,root.get("metrics").get(0).get("value").doubleValue())
        assertTrue(root.get("scope").stringValue().contains("不证明"))
        val original=archive.snapshot
        service.end("piece",id,FragileUseEndVo(now(),"实际结束位置","实际结束及检查安排","proof"))
        assertEquals(original,archive.snapshot)
        archive.snapshot=original.replace("20.0","21.0")
        archive.flush() // Keep each corruption outside the refusal's nested rollback.
        assertTrue(reject {service.detail("piece")}.message!!.contains("环境核验"))
        archive.digest=CollectionFragilityEvidence.digest(archive.snapshot)
        archive.flush()
        val changedDigest=reject {service.detail("piece")}
        assertTrue(changedDigest.message!!.contains("永久原件"),changedDigest.message)
        archive.snapshot=original;archive.digest=CollectionFragilityEvidence.digest(original);archive.flush()
        assertDoesNotThrow {service.detail("piece")}
    }
    @Test fun `old signed text artifacts remain readable but cannot authorize a new ordinary start`()=fixture {
        val p=planned();approve();out()
        val plan=CollectionFragileUsePlanDao[p];val a=CollectionFragilityAssessmentDao[plan.content.assessmentId]
        // 模拟升级前已经封存的原件，而不是通过新接口补签旧资料。
        a.content=a.content.copy(environmentCriteria=emptyList());plan.content=plan.content.copy(environmentSources=emptyList())
        listOf(a.id.value to ("ASSESSMENT" to CollectionFragilityEvidence.assessmentValue(a)),p to ("USE_PLAN" to CollectionFragilityEvidence.planValue(plan))).forEach {(id,value)->
            val row=CollectionPermanentArchiveDao.all().single {it.bizId==id && it.phase==value.first}
            row.snapshot=CollectionFragilityEvidence.json(value.second);row.digest=CollectionFragilityEvidence.digest(row.snapshot)
        }
        assertDoesNotThrow {service.detail("piece")}
        assertTrue(reject {begin(p)}.message!!.contains("旧文字评估"))
        assertEquals(0,CollectionFragileUseRecordDao.all().count())
        val old=CollectionFragileUseRecordDao.new {orgId="museum";collectionId="piece";planId=p;started=FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator","本馆实际展柜","升级前实际文字记录","proof")}
        archives.seal("museum",CollectionConst.BIZ_FRAGILITY,old.id.value,"STARTED",CollectionFragilityEvidence.startValue(old),"proof")
        assertNull(CollectionFragilityEvidence.environment(old))
        assertDoesNotThrow {service.end("piece",old.id.value,FragileUseEndVo(now(),"真实结束位置","如实登记结束，旧记录不推定环境达标","proof"))}
    }
    @Test fun `registered sources never fall back to hand typed readings when provider is absent`()=fixture {
        val provider=mock(CollectionFragileEnvironmentProvider::class.java)
        `when`(provider.sources("museum")).thenReturn(listOf(FragileEnvironmentDeviceOption("device","展柜仪器","location","展厅","TEMPERATURE","温度","℃")))
        val enabled=CollectionFragilityService(scope,users,archives,locks,listOf(provider))
        val a=assessed();val v=usePlan(a).copy(environmentSources=listOf(source().copy(type=EnumFragileEnvironmentSource.REGISTERED_DEVICE,deviceId="device",locationId="location")))
        val p=enabled.savePlan("piece",v);sign(p);approve();out()
        val started=FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator","本馆实际展柜","实际现场核对","proof")
        assertTrue(reject {service.start("piece",p,started)}.message!!.contains("服务不可用"))
        assertTrue(reject {enabled.start("piece",p,started.copy(environmentReadings=listOf(measured())))}.message!!.contains("联网指标"))
        assertEquals(0,CollectionFragileUseRecordDao.all().count())
    }
    private fun monitoringRequest(readings:List<FragileEnvironmentReadingVo> = emptyList())=FragileEnvironmentMonitoringVo(now(),"operator",true,"custodian","实际保管位置","真实现状",false,"实际防护及核查措施","继续跟踪环境与现状","proof",readings)
    @Test fun `monitoring seals actual intervals and repeated identical inspection is idempotent`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val v=monitoringRequest();val m=monitoring.inspect("piece",use,v)
        assertEquals(m,monitoring.inspect("piece",use,v))
        val row=CollectionFragileMonitoringDao[m]
        assertFalse(CollectionFragileMonitoringEvidence.unsafe(row))
        val original=row.evidenceSnapshot
        assertDoesNotThrow {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
        row.evidenceSnapshot=original.replace("false","true")
        reject {service.detail("piece")}
    }
    @Test fun `intermediate excursion remains restricted after later normal reading and truthful end`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val bad=measured().copy(measuredAt=now(),recordedAt=now(),value=30.0);Thread.sleep(5)
        val first=monitoring.inspect("piece",use,monitoringRequest(listOf(bad)))
        assertTrue(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[first]))
        assertTrue(reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}.message!!.contains("后来正常"))
        val boundary=CollectionFragileMonitoringDao[first].request.checkedAt
        val good=measured().copy(measuredAt=boundary,recordedAt=boundary,value=20.0);Thread.sleep(5)
        val later=monitoring.inspect("piece",use,monitoringRequest(listOf(good)))
        assertFalse(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[later]))
        reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
        assertDoesNotThrow {service.end("piece",use,FragileUseEndVo(now(),"真实结束地点","发现风险后如实停止","proof"))}
        assertDoesNotThrow {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_RETURN,req)}
        reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
    }
    @Test fun `interval source snapshot accumulates actual issues and normal custody does not erase them`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val bad=measured().copy(measuredAt=now(),recordedAt=now(),value=30.0);Thread.sleep(5)
        monitoring.inspect("piece",use,monitoringRequest(listOf(bad)))
        val provider=CollectionFragileMonitoringIncidentSourceProvider(archives)
        val source=IncidentSourceVo(CollectionConst.BIZ_FRAGILITY,"piece",use)
        val items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"真实监测异常"))
        val before=CollectionFragilityEvidence.json(provider.snapshot("museum",source,items))
        reject {provider.stoppedCustody("museum","piece",source)}
        reject {monitorGuard.requireSafeResolution("piece")}
        service.end("piece",use,FragileUseEndVo(now(),"真实停止地点","如实停止","proof"));Thread.sleep(5)
        monitoring.inspect("piece",use,monitoringRequest())
        assertDoesNotThrow {provider.stoppedCustody("museum","piece",source)}
        assertTrue(before.contains("30.0"))
        assertTrue(provider.reportingCatalogs(CollectionFragilityEvidence.json(provider.snapshot("museum",source,items))).containsKey("piece"))
        reject {provider.snapshot("other",source,items)}
        reject {provider.snapshot("museum",source,listOf(items.single().copy(damaged=true,collectionId="other")))}
    }
    @Test fun `monitoring records invalid original timing missing originals and foreign scope without inventing coverage`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        reject {monitoring.inspect("piece",use,monitoringRequest().copy(signedFileIds="missing"))}
        reject {monitoring.inspect("piece",use,monitoringRequest().copy(operatorUserId="other"))}
        reject {monitoring.inspect("piece",use,monitoringRequest().copy(checkedAt=now().plusDays(1)))}
        val m=monitoring.inspect("piece",use,monitoringRequest(listOf(measured().copy(recordedAt=now().plusDays(1)))))
        assertTrue(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[m]))
        assertFalse(CollectionFragileUseRecordDao[use].ended!=null)
        CollectionDao["piece"].orgId="other";reject {monitoring.inspect("piece",use,monitoringRequest())}
    }
    @Suppress("UNCHECKED_CAST")
    private fun closeMonitoringIncident(incidents:CollectionIncidentService,id: String) {
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
    @Test fun `real incident source reports personal confirmations and clearance release all related monitoring holds`()=fixture(initialNature=EnumCollectionNature.OTHER) {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val bad=measured().copy(measuredAt=now(),recordedAt=now(),value=30.0);Thread.sleep(5)
        val first=monitoring.inspect("piece",use,monitoringRequest(listOf(bad)))
        Thread.sleep(5)
        val second=monitoring.inspect("piece",use,monitoringRequest())
        val boundary=CollectionFragileMonitoringDao[second].request.checkedAt
        service.end("piece",use,FragileUseEndVo(now(),"停止后的实际位置","真实停止并保护","proof"));Thread.sleep(5)
        monitoring.inspect("piece",use,monitoringRequest(listOf(measured().copy(measuredAt=boundary,recordedAt=boundary))))
        val automaticId=CollectionFragileAutomaticMonitoringService(archives,emptyList()).scan(use)!!
        assertTrue(CollectionFragileAutomaticCheckDao[automaticId].needsHandling)
        val source=IncidentSourceVo(CollectionConst.BIZ_FRAGILITY,"piece",use)
        var laterWorkActive=false
        val laterWorkGuard=object:IncidentResolutionGuard {
            override fun requireSafeResolution(collectionId:String) {
                if(laterWorkActive)throw BadRequestException("后来合法作业尚在实施，新解除仍须核实")
            }
        }
        val actualIncidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService(listOf(CollectionFragileMonitoringIncidentSourceProvider(archives))),listOf(CollectionFragileMonitoringSafetyGuard(archives),laterWorkGuard))
        val actualMonitoring=CollectionFragileMonitoringService(scope,users,archives,emptyList(),actualIncidents)
        val facts=IncidentFactsVo("展出期间实际监测异常",now(),location="真实展柜",reporterUserId="operator",description="真实超限及证据核查",immediateMeasures="停止展出并保护",items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"真实环境异常")),source=source)
        val incident=actualIncidents.create(facts)
        actor("keeper");reject {actualMonitoring.clear("piece",first,IncidentClearanceVo(incident,now(),"conservation","keeper","恢复现状","实际措施","proof"))}
        actor("operator");closeMonitoringIncident(actualIncidents,incident);actor("keeper")
        actualMonitoring.clear("piece",first,IncidentClearanceVo(incident,now(),"conservation","keeper","恢复现状","实际措施","proof"))
        val guard=CollectionFragileMonitoringSafetyGuard(archives,actualIncidents)
        assertDoesNotThrow {guard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
        assertTrue(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[first]),"解限不改写原超限事实")
        assertNotNull(CollectionFragileMonitoringDao[first].clearance)
        assertNotNull(CollectionFragileMonitoringDao[second].clearance)
        assertNotNull(CollectionFragileAutomaticCheckDao[automaticId].clearance)
        assertTrue(CollectionFragileAutomaticCheckDao[automaticId].needsHandling)
        laterWorkActive=true
        assertDoesNotThrow {guard.requireAllowed("piece","COLLECTION_RESTORATION","later-current-project")}
        assertFalse(CollectionFragileMonitoringTaskSource(actualIncidents).tasks("museum",now()).any {it.kind=="FRAGILE_MONITOR_FOLLOWUP"})
        val saved=CollectionFragileMonitoringDao[first].clearance!!
        assertThrows(BadRequestException::class.java) {
            actualIncidents.requireClearance(incident,"museum","piece",source,now(),keeperUserId="keeper")
        }
        val failingIncidents=spy(actualIncidents)
        doThrow(IllegalStateException("stored clearance archive storage unavailable")).`when`(failingIncidents)
            .requireStoredClearance(incident,"museum","piece",source,saved.record.confirmedAt,saved.incidentProof,keeperUserId="keeper")
        assertThrows(IllegalStateException::class.java) {CollectionFragileMonitoringTaskSource(failingIncidents).tasks("museum",now())}
        laterWorkActive=false
        val factsId=actualIncidents.detail(incident)["factsId"] as String
        actualIncidents.amend(incident,facts.copy(description="新增须核实事实",replacementReason="实际复核",replacementFileIds="proof"),factsId)
        reject {guard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
    }
    @Test fun `completed real use requires inspection through actual end before another ordinary operation`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        monitoring.inspect("piece",use,monitoringRequest());Thread.sleep(5)
        service.end("piece",use,FragileUseEndVo(now(),"真实位置","如实结束","proof"))
        assertTrue(reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}.message!!.contains("真实结束"))
        Thread.sleep(5);monitoring.inspect("piece",use,monitoringRequest())
        assertDoesNotThrow {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
    }
    @Test fun `frozen monitoring samples are scoped paginated and excluded from ordinary detail payload`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val m=monitoring.inspect("piece",use,monitoringRequest())
        val detail=service.detail("piece")["monitoringReports"] as List<*>
        val report=detail.single() as Map<*,*>
        val summary=report["evidence"] as Map<*,*>
        val metric=(summary["metrics"] as List<*>).single() as Map<*,*>
        assertFalse(metric.containsKey("samples"));assertEquals(1,metric["sampleCount"])
        assertEquals(1L,monitoring.samples("piece",m,1,20)["total"])
        val samples=monitoring.samples("piece",m,1,0)["list"] as List<*>
        assertEquals(1,samples.size)
        assertEquals(use,(samples.single() as Map<*, *>)["originalRecordId"])
        assertEquals("ENVIRONMENT_VERIFIED",samples.single().let { (it as Map<*, *>)["originalPhase"] })
        assertTrue((monitoring.samples("piece",m,2,20)["list"] as List<*>).isEmpty())
        reject {monitoring.samples("piece",m,0,20)}
        CollectionDao["piece"].orgId="other";reject {monitoring.samples("piece",m,1,20)}
    }
    @Test fun `truthful earlier end can be retained alongside contradictory prior monitoring instead of being overwritten`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val actualEnd=now();Thread.sleep(5)
        monitoring.inspect("piece",use,monitoringRequest())
        service.end("piece",use,FragileUseEndVo(actualEnd,"真实停止位置","后来核实真实停止较早","proof"));Thread.sleep(5)
        val contradiction=monitoring.inspect("piece",use,monitoringRequest())
        val evidence=CollectionFragileMonitoringEvidence.evidence(CollectionFragileMonitoringDao[contradiction])
        assertTrue(evidence.get("needsHandling").booleanValue());assertTrue(evidence.get("issues").toString().contains("记录矛盾"))
        assertEquals(actualEnd,CollectionFragileUseRecordDao[use].ended!!.endedAt)
        assertEquals(2,CollectionFragileMonitoringEvidence.all("piece").size)
    }
    @Test fun `approved emergency protection still needs truthful stop and does not clear ordinary restrictions`()=fixture {
        val p=planned();approve();out();val use=begin(p);Thread.sleep(5)
        val bad=measured().copy(measuredAt=now(),recordedAt=now(),value=30.0);Thread.sleep(5)
        monitoring.inspect("piece",use,monitoringRequest(listOf(bad)))
        val restricted=mock(CollectionRestrictedOperationService::class.java)
        `when`(restricted.permits("piece","EMERGENCY","job")).thenReturn(true)
        val guard=CollectionFragileMonitoringSafetyGuard(archives,monitorIncidents,restricted)
        reject {guard.requireAllowed("piece","EMERGENCY","job")}
        service.end("piece",use,FragileUseEndVo(now(),"停止地点","真实停止","proof"))
        assertDoesNotThrow {guard.requireAllowed("piece","EMERGENCY","job")}
        reject {guard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
        assertTrue(CollectionFragileMonitoringEvidence.all("piece").any {CollectionFragileMonitoringEvidence.unsafe(it) && it.clearance==null})
    }
    @Test fun `production operation lock discovers the mandatory fragility guard`() {
        val ctx=org.springframework.context.annotation.AnnotationConfigApplicationContext()
        try {ctx.beanFactory.registerSingleton("users",users);ctx.beanFactory.registerSingleton("archives",archives);ctx.register(CollectionFragilityGuard::class.java,CollectionFragileMonitoringSafetyGuard::class.java,CollectionOperationLockService::class.java);ctx.refresh();assertEquals(2,ctx.getBeansOfType(CollectionOperationSafetyGuard::class.java).size)}finally{ctx.close()}
    }
    private fun automatic(at:LocalDateTime)=CollectionFragileAutomaticMonitoringService(archives,emptyList(),
        java.time.Clock.fixed(at.atZone(java.time.ZoneId.systemDefault()).toInstant(),java.time.ZoneId.systemDefault()))
    @Test fun `automatic scan discovers real uses without a login and preserves expiry gaps in bounded immutable intervals`()=fixture {
        val p=planned();approve();out();val use=begin(p)
        val started=CollectionFragileUseRecordDao[use].started!!.startedAt
        CollectionFragileMonitorRuntimeDao[use].delete() // 升级前仍在实际使用，尚无新增调度状态。
        SecurityContextHolder.clearContext()
        val firstService=automatic(started.plusSeconds(120))
        assertEquals(listOf(use),firstService.due())
        val first=firstService.scan(use)!!
        assertFalse(CollectionFragileAutomaticCheckDao[first].needsHandling)
        assertNull(firstService.scan(use))
        val later=automatic(started.plusMinutes(10))
        val second=later.scan(use)!!
        assertEquals(started.plusMinutes(7),CollectionFragileAutomaticCheckDao[second].toAt)
        assertTrue(CollectionFragileAutomaticCheckDao[second].needsHandling)
        val third=later.scan(use)!!
        assertEquals(started.plusMinutes(10),CollectionFragileAutomaticCheckDao[third].toAt)
        assertNull(later.scan(use))
        assertEquals(0L,CollectionFragileMonitoringDao.all().count(),"自动巡检不制造人工检查")
        assertNull(CollectionFragileUseRecordDao[use].ended,"自动巡检不停止实物使用")
        assertEquals(3,CollectionPermanentArchiveDao.all().count {it.phase=="AUTOMATIC_MONITORED"})
        actor("operator")
        assertTrue(reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}.message!!.contains("自动监测"))
        val source=CollectionFragileMonitoringIncidentSourceProvider(archives)
        val evidence=source.snapshot("museum",IncidentSourceVo(CollectionConst.BIZ_FRAGILITY,"piece",use),listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"真实核实监测证据缺口")))
        assertEquals(2,(evidence as Map<*,*>)["automaticChecks"].let {it as List<*>}.size)
        assertNull(CollectionFragileAutomaticCheckDao[second].clearance)
    }
    @Test fun `automatic retry retains cursor and marks failure until the original evidence is restored`()=fixture {
        val p=planned();approve();out();val use=begin(p);val start=CollectionFragileUseRecordDao[use].started!!.startedAt
        val first=automatic(start.plusSeconds(60)).scan(use)!!
        val row=CollectionFragileAutomaticCheckDao[first];val original=row.evidenceSnapshot
        row.evidenceSnapshot=original.replace("20.0","21.0")
        val retry=automatic(start.plusSeconds(120))
        reject {retry.scan(use)}
        retry.failed(use)
        assertNotNull(CollectionFragileMonitorRuntimeDao[use].lastError)
        assertEquals(1L,CollectionFragileAutomaticCheckDao.all().count())
        assertTrue(reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}.message!!.contains("核验失败"))
        row.evidenceSnapshot=original
        assertNull(retry.scan(use),"失败重试有间隔且不跳过原区间")
        val restored=automatic(start.plusSeconds(180)).scan(use)!!
        assertEquals(start.plusSeconds(60),CollectionFragileAutomaticCheckDao[restored].fromAt)
        assertNull(CollectionFragileMonitorRuntimeDao[use].lastError)
    }
    @Test fun `automatic originals are paged by actual use and require own museum scope`()=fixture {
        val p=planned();approve();out();val use=begin(p);val start=CollectionFragileUseRecordDao[use].started!!.startedAt
        val first=automatic(start.plusSeconds(60)).scan(use)!!
        automatic(start.plusSeconds(120)).scan(use)
        val page=monitoring.automaticPage("piece",use,1,1)
        assertEquals(2L,page["total"]);assertEquals(1,(page["list"] as List<*>).size)
        assertEquals(2,(monitoring.automaticPage("piece",use,1,0)["list"] as List<*>).size)
        assertEquals(1L,monitoring.samples("piece",first,1,20,true)["total"])
        reject {monitoring.automaticPage("piece",use,0,20)}
        val summary=service.detail("piece")["records"] as List<Map<String,Any?>>
        assertEquals(2,(summary.single()["automaticMonitoring"] as Map<*,*>)["total"])
        actor("reader","COLLECTION_VIEW");reject {monitoring.automaticPage("piece",use,1,20)}
        actor("operator");CollectionDao["piece"].orgId="other";reject {monitoring.samples("piece",first,1,20,true)}
    }
    @Test fun `unchanged automatic gap stays one responsible task while new scan chunks do not spam reminders`()=fixture {
        val p=planned();approve();out();val use=begin(p);val start=CollectionFragileUseRecordDao[use].started!!.startedAt
        val firstService=automatic(start.plusMinutes(6));firstService.scan(use);firstService.scan(use)
        val tasks=CollectionFragileMonitoringTaskSource(monitorIncidents)
        val first=tasks.tasks("museum",start.plusMinutes(6)).single {it.kind=="FRAGILE_MONITOR_FOLLOWUP"}
        assertEquals(setOf("conservation","keeper","operator"),first.assignees.map {it.userId}.toSet())
        automatic(start.plusMinutes(7)).scan(use)
        val next=tasks.tasks("museum",start.plusMinutes(7)).single {it.kind=="FRAGILE_MONITOR_FOLLOWUP"}
        assertEquals(first.key,next.key);assertEquals(first.basis,next.basis)
        assertTrue(tasks.tasks("other",start.plusMinutes(7)).isEmpty())
        assertTrue(tasks.tasks("museum",start.plusMinutes(10)).any {it.kind=="FRAGILE_MONITOR_DELAY"})
        val original=CollectionFragileAutomaticCheckDao.all().first();original.evidenceSnapshot="{}"
        assertEquals("FRAGILE_ORIGINAL_ERROR",tasks.tasks("museum",start.plusMinutes(10)).single().kind)
    }
    @Test fun `earlier truthful end remains recorded and produces one automatic contradiction instead of overwriting history`()=fixture {
        val p=planned();approve();out();val use=begin(p);val start=CollectionFragileUseRecordDao[use].started!!.startedAt
        val first=automatic(start.plusSeconds(60)).scan(use)!!
        service.end("piece",use,FragileUseEndVo(now(),"实际停止位置","据实登记停止","proof"))
        val later=automatic(start.plusSeconds(120));val contradiction=later.scan(use)!!
        val row=CollectionFragileAutomaticCheckDao[contradiction]
        assertTrue(row.needsHandling);assertEquals(row.fromAt,row.toAt)
        assertEquals(start.plusSeconds(60),CollectionFragileAutomaticCheckDao[first].toAt)
        assertTrue(CollectionFragileMonitorRuntimeDao[use].completed)
        assertEquals(0L,monitoring.samples("piece",contradiction,1,20,true)["total"])
        assertDoesNotThrow {monitoring.automaticPage("piece",use,1,20)}
        assertNull(later.scan(use))
        assertTrue(CollectionFragileMonitoringTaskSource(monitorIncidents).tasks("museum",start.plusSeconds(120)).any {it.kind=="FRAGILE_FINAL_INSPECTION"})
    }

    @Test fun `automatic source failure remains restricted after restored readings and safe manual inspection until real incident clearance`()=fixture(initialNature=EnumCollectionNature.OTHER) {
        var unavailable=true
        val provider=object:CollectionFragileEnvironmentProvider {
            override fun sources(orgId:String)=listOf(FragileEnvironmentDeviceOption("device","展柜仪器","location","展厅","TEMPERATURE","温度","℃"))
            override fun verify(orgId:String,source:FragileEnvironmentSourceVo,criterion:FragileEnvironmentCriterionVo,startedAt:LocalDateTime,evaluatedAt:LocalDateTime)=FragileEnvironmentDeviceEvidence(20.0,"℃",startedAt,mapOf("fact" to "test-start"))
            override fun inspectPeriod(orgId:String,source:FragileEnvironmentSourceVo,criterion:FragileEnvironmentCriterionVo,from:LocalDateTime,to:LocalDateTime):FragilePeriodSourceEvidence {
                assertEquals("museum",orgId);assertEquals("device",source.deviceId);assertEquals("location",source.locationId)
                if(unavailable)throw BadRequestException("实际采样来源暂时无法核实")
                return FragilePeriodSourceEvidence(listOf(FragilePeriodSample(from,from,20.0,"℃",true,evidence=mapOf("fact" to "test-period"))))
            }
        }
        val enabled=CollectionFragilityService(scope,users,archives,locks,listOf(provider))
        val a=assessed();val p=enabled.savePlan("piece",usePlan(a).copy(environmentSources=listOf(source().copy(type=EnumFragileEnvironmentSource.REGISTERED_DEVICE,deviceId="device",locationId="location"))))
        sign(p);approve();out()
        val use=enabled.start("piece",p,FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator","本馆实际展柜","实际现场核对","proof"))
        Thread.sleep(5)
        val auto=CollectionFragileAutomaticMonitoringService(archives,listOf(provider))
        val problem=auto.scan(use)!!
        assertTrue(CollectionFragileAutomaticCheckDao[problem].needsHandling)
        assertEquals(0L,CollectionFragileMonitoringDao.all().count())
        unavailable=false
        enabled.end("piece",use,FragileUseEndVo(now(),"实际保管位置","真实停止展出并防护","proof"));Thread.sleep(5)
        auto.scan(use)
        assertTrue(CollectionFragileMonitorRuntimeDao[use].completed)
        val incidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService(listOf(CollectionFragileMonitoringIncidentSourceProvider(archives))),listOf(CollectionFragileMonitoringSafetyGuard(archives)))
        val inspection=CollectionFragileMonitoringService(scope,users,archives,listOf(provider),incidents)
        val manual=inspection.inspect("piece",use,monitoringRequest())
        assertFalse(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[manual]))
        reject {monitorGuard.requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
        val incident=incidents.create(IncidentFactsVo("使用期间自动核验来源异常",now(),location="真实展柜",reporterUserId="operator",description="自动核验未能获得证据，随后现场核实来源并处置",immediateMeasures="实际停止并防护",items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"监测证据缺口")),source=IncidentSourceVo(CollectionConst.BIZ_FRAGILITY,"piece",use)))
        closeMonitoringIncident(incidents,incident);actor("keeper")
        inspection.clear("piece",manual,IncidentClearanceVo(incident,now(),"conservation","keeper","恢复现状","已实际核查防护","proof"))
        assertNotNull(CollectionFragileAutomaticCheckDao[problem].clearance)
        assertTrue(CollectionFragileAutomaticCheckDao[problem].needsHandling)
        assertDoesNotThrow {CollectionFragileMonitoringSafetyGuard(archives,incidents).requireAllowed("piece",CollectionConst.BIZ_REQUISITION,req)}
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    class AutomaticTransactions
    @Test fun `Spring proxy rolls back partial automatic evidence and commits failure retry independently without a user`() {
        val ds=org.sqlite.SQLiteDataSource().apply {url="jdbc:sqlite:file:auto-transaction-${System.nanoTime()}?mode=memory&cache=shared"}
        ds.connection.use {
            lateinit var use:String;lateinit var started:LocalDateTime
            fixture(databaseUrl=ds.url) {val p=planned();approve();out();use=begin(p);started=CollectionFragileUseRecordDao[use].started!!.startedAt}
            val tx=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(ds,org.jetbrains.exposed.v1.core.DatabaseConfig {})
            val template=org.springframework.transaction.support.TransactionTemplate(tx)
            var tick=started.plusSeconds(60).atZone(java.time.ZoneId.systemDefault()).toInstant()
            val clock=object:java.time.Clock() {
                override fun instant()=tick
                override fun getZone()=java.time.ZoneId.systemDefault()
                override fun withZone(zone:java.time.ZoneId)=java.time.Clock.fixed(tick,zone)
            }
            var fail=true
            val failing=mock(CollectionPermanentArchiveService::class.java,org.mockito.stubbing.Answer {call->
                if(call.method.name=="seal") {
                    if(fail)throw IllegalStateException("archive storage unavailable")
                    call.method.invoke(archives,*call.arguments)
                } else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
            })
            org.springframework.context.annotation.AnnotationConfigApplicationContext().use {runtime->
                runtime.beanFactory.registerSingleton("transactionManager",tx)
                runtime.beanFactory.registerSingleton("archives",failing)
                runtime.beanFactory.registerSingleton("clock",clock)
                runtime.register(AutomaticTransactions::class.java,CollectionFragileAutomaticMonitoringService::class.java)
                runtime.refresh()
                val actual=runtime.getBean(CollectionFragileAutomaticMonitoringService::class.java)
                assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(actual))
                SecurityContextHolder.clearContext()
                assertThrows(IllegalStateException::class.java){actual.scan(use)}
                template.executeWithoutResult {
                    assertEquals(0L,CollectionFragileAutomaticCheckDao.all().count())
                    assertNull(CollectionFragileMonitorRuntimeDao[use].lastAttemptAt)
                }
                actual.failed(use)
                template.executeWithoutResult {assertNotNull(CollectionFragileMonitorRuntimeDao[use].lastError)}
                fail=false;tick=tick.plusSeconds(60)
                val saved=actual.scan(use)!!
                template.executeWithoutResult {
                    assertEquals(started,CollectionFragileAutomaticCheckDao[saved].fromAt)
                    assertNull(CollectionFragileMonitorRuntimeDao[use].lastError)
                    CollectionFragileAutomaticEvidence.verify(CollectionFragileAutomaticCheckDao[saved])
                }
            }
        }
    }

    @Test fun `short new use remains queued through its end while completed pre upgrade uses are not replayed as new incidents`()=fixture {
        val p=planned();approve();out();val use=begin(p)
        assertNotNull(CollectionFragileMonitorRuntimeDao.findById(use))
        service.end("piece",use,FragileUseEndVo(now(),"实际停止位置","短时间实际展出已结束","proof"))
        val auto=CollectionFragileAutomaticMonitoringService(archives,emptyList())
        assertTrue(use in auto.due())
        CollectionFragileMonitorRuntimeDao[use].delete()
        assertFalse(use in auto.due())
        val legacy=CollectionFragileAutomaticEvidence.runtime(CollectionFragileUseRecordDao[use],now().plusMinutes(10))
        assertEquals(true,legacy["legacy"]);assertEquals(false,legacy["overdue"])
        CollectionFragileMonitorRuntimeDao.new(use) {orgId="museum";collectionId="piece";nextAttemptAt=now()}
        auto.scan(use)
        assertTrue(CollectionFragileMonitorRuntimeDao[use].completed)
        assertFalse(use in auto.due())
        // 模拟升级前已结束且未设新调度状态：其原人工监测/处置档案不因新功能而重放。
        CollectionFragileMonitorRuntimeDao[use].delete()
        assertFalse(use in auto.due())
    }

    private fun assertFragileOriginals(phase:String,damage:String)=fixture {
        fun file(label:String)=safetyOriginalFile(safetyOriginalRoot,label)
        var useId:String
        if(phase=="HISTORY")useId=service.history("piece",FragileUseHistoryVo(EnumFragileUseKind.DISPLAY,now().minusDays(2),now().minusDays(1),"既有真实记录","据实补录",file("HISTORY")))
        else {
            val a=service.assess("piece",assessment().copy(assessmentFileIds=file("ASSESSMENT"),historyReviewFileIds="ASSESSMENT"))
            actor("conservation");service.sign("piece",a,CollectionCareSignatureVo("本人核对",file("ASSESSMENT_CONSERVATION")))
            actor("keeper");service.sign("piece",a,CollectionCareSignatureVo("本人核对",file("ASSESSMENT_KEEPER")));actor("operator")
            val p=service.savePlan("piece",usePlan(a).copy(signedPlanFileIds=file("USE_PLAN")))
            actor("conservation");service.sign("piece",p,CollectionCareSignatureVo("本人核对",file("USE_CONSERVATION")))
            actor("keeper");service.sign("piece",p,CollectionCareSignatureVo("本人核对",file("USE_KEEPER")));actor("operator")
            approve();out()
            val startAt=now()
            useId=service.start("piece",p,FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,startAt,"operator","本馆实际展柜","实际保护",file("STARTED"),listOf(measured().copy(measuredAt=startAt,recordedAt=startAt,signedFileIds=file("ENVIRONMENT_VERIFIED")))))
            service.end("piece",useId,FragileUseEndVo(now(),"实际位置","据实结束",file("ENDED")))
            monitoring.inspect("piece",useId,monitoringRequest(listOf(measured().copy(measuredAt=startAt,recordedAt=startAt,signedFileIds="ENVIRONMENT_VERIFIED"),measured())).copy(signedFileIds=file("MONITORED")))
        }
        val archive=CollectionPermanentArchiveFileDao.all().single {it.fileId==phase && CollectionPermanentArchiveDao[it.archiveId].phase==when(phase){"ASSESSMENT_CONSERVATION","USE_CONSERVATION"->"CONSERVATION";"ASSESSMENT_KEEPER","USE_KEEPER"->"KEEPER";else->phase}}.archiveId.let(CollectionPermanentArchiveDao::get)
        val tables=arrayOf(CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,CollectionFragileMonitorings,CollectionFragileAutomaticChecks,CollectionFragileMonitorRuntimes,CollectionFragileUseAdjustments,Collections,Requisitions,CollectionCareChecks,CollectionCareWorks,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val actions=arrayOf<()->Unit>({monitorGuard.requireSafeResolution("piece")},{monitorGuard.requireExclusiveWork("piece","other","other")},{monitorGuard.requireAllowed("piece","other","other")})
        assertSafetyOriginalDamage(safetyOriginalRoot,archive,phase,damage,tables,actions){actions.forEach {assertDoesNotThrow(it)}}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "ASSESSMENT,MISSING",
        "ASSESSMENT,IDENTITY",
        "ASSESSMENT,EXTRA",
        "ASSESSMENT,BYTES",
        "ASSESSMENT,BASELINE",
        "ASSESSMENT_CONSERVATION,MISSING",
        "ASSESSMENT_CONSERVATION,IDENTITY",
        "ASSESSMENT_CONSERVATION,EXTRA",
        "ASSESSMENT_CONSERVATION,BYTES",
        "ASSESSMENT_CONSERVATION,BASELINE",
        "ASSESSMENT_KEEPER,MISSING",
        "ASSESSMENT_KEEPER,IDENTITY",
        "ASSESSMENT_KEEPER,EXTRA",
        "ASSESSMENT_KEEPER,BYTES",
        "ASSESSMENT_KEEPER,BASELINE",
        "USE_PLAN,MISSING",
        "USE_PLAN,IDENTITY",
        "USE_PLAN,EXTRA",
        "USE_PLAN,BYTES",
        "USE_PLAN,BASELINE",
        "USE_CONSERVATION,MISSING",
        "USE_CONSERVATION,IDENTITY",
        "USE_CONSERVATION,EXTRA",
        "USE_CONSERVATION,BYTES",
        "USE_CONSERVATION,BASELINE",
        "USE_KEEPER,MISSING",
        "USE_KEEPER,IDENTITY",
        "USE_KEEPER,EXTRA",
        "USE_KEEPER,BYTES",
        "USE_KEEPER,BASELINE",
        "ENVIRONMENT_VERIFIED,MISSING",
        "ENVIRONMENT_VERIFIED,IDENTITY",
        "ENVIRONMENT_VERIFIED,EXTRA",
        "ENVIRONMENT_VERIFIED,BYTES",
        "ENVIRONMENT_VERIFIED,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE",
        "MONITORED,MISSING",
        "MONITORED,IDENTITY",
        "MONITORED,EXTRA",
        "MONITORED,BYTES",
        "MONITORED,BASELINE",
        "HISTORY,MISSING",
        "HISTORY,IDENTITY",
        "HISTORY,EXTRA",
        "HISTORY,BYTES",
        "HISTORY,BASELINE")
    fun `operation safety originals fragile rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertFragileOriginals(phase,damage)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "ASSESSMENT,MISSING",
        "ASSESSMENT,IDENTITY",
        "ASSESSMENT,EXTRA",
        "ASSESSMENT,BYTES",
        "ASSESSMENT,BASELINE",
        "ASSESSMENT_CONSERVATION,MISSING",
        "ASSESSMENT_CONSERVATION,IDENTITY",
        "ASSESSMENT_CONSERVATION,EXTRA",
        "ASSESSMENT_CONSERVATION,BYTES",
        "ASSESSMENT_CONSERVATION,BASELINE",
        "ASSESSMENT_KEEPER,MISSING",
        "ASSESSMENT_KEEPER,IDENTITY",
        "ASSESSMENT_KEEPER,EXTRA",
        "ASSESSMENT_KEEPER,BYTES",
        "ASSESSMENT_KEEPER,BASELINE",
        "USE_PLAN,MISSING",
        "USE_PLAN,IDENTITY",
        "USE_PLAN,EXTRA",
        "USE_PLAN,BYTES",
        "USE_PLAN,BASELINE",
        "USE_CONSERVATION,MISSING",
        "USE_CONSERVATION,IDENTITY",
        "USE_CONSERVATION,EXTRA",
        "USE_CONSERVATION,BYTES",
        "USE_CONSERVATION,BASELINE",
        "USE_KEEPER,MISSING",
        "USE_KEEPER,IDENTITY",
        "USE_KEEPER,EXTRA",
        "USE_KEEPER,BYTES",
        "USE_KEEPER,BASELINE",
        "ENVIRONMENT_VERIFIED,MISSING",
        "ENVIRONMENT_VERIFIED,IDENTITY",
        "ENVIRONMENT_VERIFIED,EXTRA",
        "ENVIRONMENT_VERIFIED,BYTES",
        "ENVIRONMENT_VERIFIED,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE",
        "MONITORED,MISSING",
        "MONITORED,IDENTITY",
        "MONITORED,EXTRA",
        "MONITORED,BYTES",
        "MONITORED,BASELINE",
        "HISTORY,MISSING",
        "HISTORY,IDENTITY",
        "HISTORY,EXTRA",
        "HISTORY,BYTES",
        "HISTORY,BASELINE")
    fun `postgres operation safety originals fragile rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertFragileOriginals(phase,damage)} finally {safetyDatabase=null}
    }

    private fun assertMonitoringRealAudit()=fixture {
        val p=planned();approve();out();val use=begin(p)
        val id=monitoring.inspect("piece",use,monitoringRequest())
        val row=CollectionFragileMonitoringDao[id]
        settledSafetySelectionState(CollectionFragileMonitorings)
        actor("keeper")
        assertSafetySelectionRealAudit(CollectionFragileMonitorings,row){row.request=row.request.copy(actualMeasures="实际第二次监测措施变化")}
    }
    @Test fun `operation safety originals monitoring preserves real audit changes`()=assertMonitoringRealAudit()
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres operation safety originals monitoring preserves real audit changes`()=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertMonitoringRealAudit()} finally {safetyDatabase=null}
    }

    private fun assertMonitoringRuntimeRealAudit()=fixture {
        val p=planned();approve();out();val use=begin(p)
        val row=CollectionFragileMonitorRuntimeDao[use]
        settledSafetySelectionState(CollectionFragileMonitorRuntimes)
        actor("keeper")
        assertSafetySelectionRealAudit(CollectionFragileMonitorRuntimes,row){row.lastError="实际第二次核验失败"}
    }
    @Test fun `operation safety originals monitor runtime preserves real audit changes`()=assertMonitoringRuntimeRealAudit()
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres operation safety originals monitor runtime preserves real audit changes`()=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertMonitoringRuntimeRealAudit()} finally {safetyDatabase=null}
    }

    private fun fragileFollowupTables()=arrayOf(
        CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,
        CollectionFragileMonitorings,CollectionFragileAutomaticChecks,CollectionFragileMonitorRuntimes,
        CollectionFragileUseAdjustments,Collections,Requisitions,CollectionCareChecks,CollectionCareWorks,
        CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOperationLocks,
        StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,
    )
    private fun fragileFollowupActions(actualGuard:CollectionFragileMonitoringSafetyGuard)=arrayOf<()->Unit>(
        {actualGuard.requireSafeResolution("piece")},
        {actualGuard.requireExclusiveWork("piece","other","other")},
        {actualGuard.requireAllowed("piece","other","other")},
    )
    private fun completedFragileFollowup(p:String,startedAt:LocalDateTime=now(),block:(String,LocalDateTime)->Unit) {
        var tick=startedAt
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock ->
            clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenAnswer {tick}
            val use=begin(p)
            tick=startedAt.plusSeconds(1)
            service.end("piece",use,FragileUseEndVo(now(),"实际保管位置","实际结束并防护","proof"))
            tick=startedAt.plusSeconds(2)
            monitoring.inspect("piece",use,monitoringRequest())
            block(use,startedAt)
        }
    }
    private fun clearedFragileFollowup(automatic:Boolean,block:(String,CollectionFragileMonitoringSafetyGuard)->Unit) {
        val base=now()
        var tick=base
        var unavailable=true
        val provider=object:CollectionFragileEnvironmentProvider {
            override fun sources(orgId:String)=listOf(FragileEnvironmentDeviceOption("device","展柜仪器","location","展厅","TEMPERATURE","温度","℃"))
            override fun verify(orgId:String,source:FragileEnvironmentSourceVo,criterion:FragileEnvironmentCriterionVo,startedAt:LocalDateTime,evaluatedAt:LocalDateTime)=FragileEnvironmentDeviceEvidence(20.0,"℃",startedAt,mapOf("fact" to "followup-start"))
            override fun inspectPeriod(orgId:String,source:FragileEnvironmentSourceVo,criterion:FragileEnvironmentCriterionVo,from:LocalDateTime,to:LocalDateTime):FragilePeriodSourceEvidence {
                assertEquals("museum",orgId);assertEquals("device",source.deviceId);assertEquals("location",source.locationId)
                if(unavailable)throw BadRequestException("实际采样来源暂时无法核实")
                return FragilePeriodSourceEvidence(listOf(FragilePeriodSample(from,from,20.0,"℃",true,evidence=mapOf("fact" to "followup-period"))))
            }
        }
        val providers=if(automatic)listOf(provider) else emptyList()
        val actualIncidents=CollectionIncidentService(scope,users,archives,
            CollectionIncidentSourceService(listOf(CollectionFragileMonitoringIncidentSourceProvider(archives))),
            listOf(CollectionFragileMonitoringSafetyGuard(archives)))
        val actualMonitoring=CollectionFragileMonitoringService(scope,users,archives,providers,actualIncidents)
        val actualGuard=CollectionFragileMonitoringSafetyGuard(archives,actualIncidents)
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock ->
            clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenAnswer {tick}
            val enabled=CollectionFragilityService(scope,users,archives,locks,providers)
            val a=assessed()
            val plan=usePlan(a).let {if(automatic)it.copy(environmentSources=listOf(source().copy(
                type=EnumFragileEnvironmentSource.REGISTERED_DEVICE,deviceId="device",locationId="location"))) else it}
            val p=enabled.savePlan("piece",plan);sign(p);approve();out()
            val use=if(automatic)enabled.start("piece",p,FragileUseStartVo(EnumFragileUseKind.DISPLAY,0,now(),"operator","本馆实际展柜","实际现场核对","proof")) else begin(p)
            var problem:String
            var manual:String
            if(automatic) {
                val autoClock=object:java.time.Clock() {
                    override fun instant()=tick.atZone(java.time.ZoneId.systemDefault()).toInstant()
                    override fun getZone()=java.time.ZoneId.systemDefault()
                    override fun withZone(zone:java.time.ZoneId)=java.time.Clock.fixed(instant(),zone)
                }
                val auto=CollectionFragileAutomaticMonitoringService(archives,providers,autoClock)
                tick=base.plusSeconds(1);problem=auto.scan(use)!!
                assertTrue(CollectionFragileAutomaticCheckDao[problem].needsHandling)
                unavailable=false;tick=base.plusSeconds(2)
                enabled.end("piece",use,FragileUseEndVo(now(),"实际保管位置","实际停止并防护","proof"))
                tick=base.plusSeconds(3);auto.scan(use)
                assertTrue(CollectionFragileMonitorRuntimeDao[use].completed)
                tick=base.plusSeconds(4);manual=actualMonitoring.inspect("piece",use,monitoringRequest())
                assertFalse(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[manual]))
            } else {
                tick=base.plusSeconds(1)
                val bad=measured().copy(measuredAt=now(),recordedAt=now(),value=30.0)
                tick=base.plusSeconds(2);problem=actualMonitoring.inspect("piece",use,monitoringRequest(listOf(bad)))
                assertTrue(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[problem]))
                tick=base.plusSeconds(3)
                enabled.end("piece",use,FragileUseEndVo(now(),"实际保管位置","真实停止并防护","proof"))
                tick=base.plusSeconds(4);actualMonitoring.inspect("piece",use,monitoringRequest(listOf(measured())))
                manual=problem
                assertEquals(0L,CollectionFragileAutomaticCheckDao.all().count())
            }
            val source=IncidentSourceVo(CollectionConst.BIZ_FRAGILITY,"piece",use)
            val incident=actualIncidents.create(IncidentFactsVo("实际监测异常处置历史",now(),location="真实展柜",reporterUserId="operator",
                description="核实实际异常及保存安全",immediateMeasures="停止并保护",
                items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"实际监测异常")),source=source))
            closeMonitoringIncident(actualIncidents,incident);actor("keeper")
            val prefix=if(automatic)"AUTO_CLEARED_" else "MONITOR_CLEARED_"
            tick=base.plusSeconds(5)
            val first=IncidentClearanceVo(incident,now(),"conservation","keeper","首次核实安全","实际防护措施",
                safetyOriginalFile(safetyOriginalRoot,prefix+"1"))
            actualMonitoring.clear("piece",manual,first)
            tick=base.plusSeconds(6)
            actualMonitoring.clear("piece",manual,first.copy(confirmedAt=now(),condition="追加实际安全核对",
                signedFileIds=safetyOriginalFile(safetyOriginalRoot,prefix+"2")))
            val clearance=if(automatic)CollectionFragileAutomaticCheckDao[problem].clearance else CollectionFragileMonitoringDao[problem].clearance
            assertEquals(2,clearance!!.version)
            if(automatic) {
                assertTrue(CollectionFragileAutomaticCheckDao[problem].needsHandling)
                assertNull(CollectionFragileMonitoringDao[manual].clearance)
            } else assertTrue(CollectionFragileMonitoringEvidence.unsafe(CollectionFragileMonitoringDao[problem]))
            block(problem,actualGuard)
        }
    }
    private fun assertFragileFollowupOriginals(phase:String,damage:String)=fixture(initialNature=EnumCollectionNature.OTHER) {
        fun verify(record:String,actualGuard:CollectionFragileMonitoringSafetyGuard=monitorGuard) {
            val archive=CollectionPermanentArchiveDao.all().single {
                it.bizType==CollectionConst.BIZ_FRAGILITY && it.bizId==record && it.phase==phase
            }
            val actions=fragileFollowupActions(actualGuard)
            assertSafetyOriginalDamage(safetyOriginalRoot,archive,phase,damage,fragileFollowupTables(),actions) {
                actions.forEach {assertDoesNotThrow(it)}
            }
        }
        when {
            phase.startsWith("MONITOR_CLEARED_") -> clearedFragileFollowup(false) {record,actualGuard->verify(record,actualGuard)}
            phase.startsWith("AUTO_CLEARED_") -> clearedFragileFollowup(true) {record,actualGuard->verify(record,actualGuard)}
            else -> {
                val p=planned();approve();out()
                if(phase=="REVIEWED")completedFragileFollowup(p) {use,_->
                    val check=checked();actor("keeper")
                    service.review("piece",use,FragileUseReviewVo(check,safetyOriginalFile(safetyOriginalRoot,phase)))
                    verify(use)
                } else {
                    val check=checked()
                    val start=now().plusMinutes(1)
                    val request=adjustment(p,check,if(phase=="ADJUSTMENT_WITHDRAWN")adjustment(p,check).adjustments else reschedule(start,start.plusMinutes(5)))
                        .copy(signedFileIds=safetyOriginalFile(safetyOriginalRoot,"ADJUSTMENT"))
                    val change=adjustments.propose("piece",p,request)
                    val roles=listOf("CONSERVATION" to "conservation","DEPARTMENT" to "department","KEEPER" to "keeper","DIRECTOR" to "director")
                    (if(phase=="ADJUSTMENT_WITHDRAWN")roles.take(1) else roles).forEach {(role,user)->
                        actor(user);adjustments.sign("piece",change,role,CollectionCareSignatureVo("本人核对实际变更",
                            safetyOriginalFile(safetyOriginalRoot,"ADJUSTMENT_$role")))
                    }
                    actor("operator")
                    if(phase=="ADJUSTMENT_WITHDRAWN")adjustments.withdraw("piece",change,FragileAdjustmentWithdrawalVo(now(),
                        "核查方案尚未生效，实际撤回重新研究",safetyOriginalFile(safetyOriginalRoot,phase)))
                    completedFragileFollowup(p,if(phase=="ADJUSTMENT_WITHDRAWN")now() else start.plusSeconds(1)) {use,_->
                        assertEquals(if(phase=="ADJUSTMENT_WITHDRAWN")null else change,CollectionFragileUseRecordDao[use].authorizationAdjustmentId)
                        verify(change)
                    }
                }
            }
        }
    }
    private fun clearanceHistory(automatic:Boolean,recordId:String):List<CollectionCareClearanceSnapshot> {
        val row=if(automatic) {
            val use=CollectionFragileAutomaticCheckDao[recordId].useRecordId
            (monitoring.automaticPage("piece",use,1,0)["list"] as List<*>).filterIsInstance<Map<*,*>>().single {it["id"]==recordId}
        } else (service.detail("piece")["monitoringReports"] as List<*>).filterIsInstance<Map<*,*>>().single {it["id"]==recordId}
        return (row["clearances"] as? List<*>)?.filterIsInstance<CollectionCareClearanceSnapshot>().orEmpty()
    }
    private fun assertClearanceHistoryReadable(automatic:Boolean)=fixture(initialNature=EnumCollectionNature.OTHER) {
        clearedFragileFollowup(automatic) {record,_ ->
            actor("keeper",CollectionConst.CARE)
            val prefix=if(automatic)"AUTO_CLEARED_" else "MONITOR_CLEARED_"
            val before=settledSafetySelectionState(*fragileFollowupTables())
            for(version in 1..2) {
                val phase="$prefix$version"
                val archive=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase==phase}
                assertEquals(archive.id.value,originals.requireArchive("piece","museum",record,phase,phase))
                assertThrows(BadRequestException::class.java){originals.requireArchive("piece","museum",record,phase,"${prefix}${3-version}")}
            }
            @Suppress("UNCHECKED_CAST")
            val frozen=service.detail("piece")["archiveFiles"] as Map<String,Map<String,List<Map<String,Any?>>>>
            for(version in 1..2) {
                val phase="$prefix$version"
                val archive=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase==phase}
                assertEquals(CollectionPermanentArchiveFileDao.all().single {it.archiveId==archive.id.value}.metadata,
                    frozen.getValue(record).getValue(phase).single()["metadata"])
            }
            val history=clearanceHistory(automatic,record)
            assertEquals(listOf(1,2),history.map {it.version})
            assertEquals(listOf("${prefix}1","${prefix}2"),history.map {it.record.signedFileIds})
            for(phase in listOf("${prefix}0","${prefix}3","${prefix}01","CLEARED_1"))
                assertThrows(BadRequestException::class.java){originals.requireArchive("piece","museum",record,phase,"${prefix}1")}
            assertThrows(BadRequestException::class.java){originals.requireArchive("other","museum",record,"${prefix}1","${prefix}1")}
            assertThrows(BadRequestException::class.java){originals.requireArchive("piece","other-museum",record,"${prefix}1","${prefix}1")}
            actor("keeper",CollectionConst.SELECT)
            val oldArchive=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase=="${prefix}1"}
            assertEquals(oldArchive.id.value,originals.requireArchive("piece","museum",record,"${prefix}1","${prefix}1"))
            actor("department-head",CollectionConst.CARE)
            assertThrows(BadRequestException::class.java){originals.requireArchive("piece","museum",record,"${prefix}1","${prefix}1")}
            assertEquals(before,settledSafetySelectionState(*fragileFollowupTables()))
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans=[false,true])
    fun `fragile clearance history both versions remain readable with current museum permission`(automatic:Boolean)=assertClearanceHistoryReadable(automatic)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans=[false,true])
    fun `postgres fragile clearance history both versions remain readable with current museum permission`(automatic:Boolean)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertClearanceHistoryReadable(automatic)} finally {safetyDatabase=null}
    }
    private fun assertClearanceHistoryOriginal(phase:String,damage:String)=fixture(initialNature=EnumCollectionNature.OTHER) {
        clearedFragileFollowup(phase.startsWith("AUTO_")) {record,_ ->
            actor("keeper",CollectionConst.CARE)
            val archive=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase==phase}
            val read={originals.requireArchive("piece","museum",record,phase,phase);Unit}
            assertSafetyOriginalDamage(safetyOriginalRoot,archive,phase,damage,fragileFollowupTables(),arrayOf(read)) {
                assertDoesNotThrow(read)
            }
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("clearanceHistoryOriginalCases")
    fun `fragile clearance history original rejects manifest bytes and missing baseline`(phase:String,damage:String)=assertClearanceHistoryOriginal(phase,damage)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("clearanceHistoryOriginalCases")
    fun `postgres fragile clearance history original rejects manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertClearanceHistoryOriginal(phase,damage)} finally {safetyDatabase=null}
    }
    private fun assertClearanceHistoryChain(automatic:Boolean,damage:String)=fixture(initialNature=EnumCollectionNature.OTHER) {
        clearedFragileFollowup(automatic) {record,_ ->
            actor("keeper",CollectionConst.CARE)
            val prefix=if(automatic)"AUTO_CLEARED_" else "MONITOR_CLEARED_"
            val old=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase=="${prefix}1"}
            val snapshot=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(old.snapshot,CollectionCareClearanceSnapshot::class.java)!!
            when(damage) {
                "MISSING" -> old.phase="REMOVED_HISTORY"
                "GAP" -> old.phase="${prefix}3"
                "ORG" -> old.orgId="other-museum"
                "DIGEST" -> old.digest="changed"
                "MALFORMED" -> {old.snapshot="{}";old.digest=CollectionCareEvidence.digest(old.snapshot)}
                "ROLLBACK","NULL" -> if(automatic)CollectionFragileAutomaticCheckDao[record].clearance=if(damage=="NULL")null else snapshot
                    else CollectionFragileMonitoringDao[record].clearance=if(damage=="NULL")null else snapshot
                "TIME" -> {old.snapshot=CollectionCareEvidence.json(snapshot.copy(record=snapshot.record.copy(confirmedAt=now().plusDays(1))));old.digest=CollectionCareEvidence.digest(old.snapshot)}
                else -> error(damage)
            }
            val damaged=settledSafetySelectionState(*fragileFollowupTables())
            assertThrows(BadRequestException::class.java){clearanceHistory(automatic,record)}
            assertThrows(BadRequestException::class.java){originals.requireArchive("piece","museum",record,"${prefix}2","${prefix}2")}
            assertEquals(damaged,settledSafetySelectionState(*fragileFollowupTables()))
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("clearanceHistoryChainCases")
    fun `fragile clearance history refuses incomplete rolled back or inconsistent chain`(automatic:Boolean,damage:String)=assertClearanceHistoryChain(automatic,damage)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("clearanceHistoryChainCases")
    fun `postgres fragile clearance history refuses incomplete rolled back or inconsistent chain`(automatic:Boolean,damage:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertClearanceHistoryChain(automatic,damage)} finally {safetyDatabase=null}
    }
    companion object {
        @JvmStatic fun clearanceHistoryOriginalCases()=listOf("MONITOR_CLEARED_1","MONITOR_CLEARED_2","AUTO_CLEARED_1","AUTO_CLEARED_2").flatMap {phase ->
            listOf("MISSING","IDENTITY","EXTRA","BYTES","BASELINE").map {damage ->org.junit.jupiter.params.provider.Arguments.of(phase,damage)}
        }.stream()
        @JvmStatic fun clearanceHistoryChainCases()=listOf(false,true).flatMap {automatic ->
            listOf("MISSING","GAP","ORG","DIGEST","MALFORMED","ROLLBACK","NULL","TIME").map {damage ->org.junit.jupiter.params.provider.Arguments.of(automatic,damage)}
        }.stream()
        @JvmStatic fun fragileFollowupCases()=listOf(
            "REVIEWED","ADJUSTMENT","ADJUSTMENT_CONSERVATION","ADJUSTMENT_DEPARTMENT","ADJUSTMENT_KEEPER",
            "ADJUSTMENT_DIRECTOR","ADJUSTMENT_WITHDRAWN","MONITOR_CLEARED_1","MONITOR_CLEARED_2","AUTO_CLEARED_1","AUTO_CLEARED_2",
        ).flatMap {phase -> listOf("MISSING","IDENTITY","EXTRA","BYTES","BASELINE").map {damage ->
            org.junit.jupiter.params.provider.Arguments.of(phase,damage)
        }}.stream()
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("fragileFollowupCases")
    fun `operation safety originals fragile followup rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertFragileFollowupOriginals(phase,damage)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("fragileFollowupCases")
    fun `postgres operation safety originals fragile followup rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertFragileFollowupOriginals(phase,damage)} finally {safetyDatabase=null}
    }
    private fun assertFragileEmptyAutomaticOriginal()=fixture {
        val p=planned();approve();out()
        completedFragileFollowup(p) {use,startedAt ->
            val automatic=automatic(startedAt.plusSeconds(2)).scan(use)!!
            assertFalse(CollectionFragileAutomaticCheckDao[automatic].needsHandling)
            val archive=CollectionPermanentArchiveDao.all().single {it.bizId==automatic && it.phase=="AUTOMATIC_MONITORED"}
            assertTrue(CollectionPermanentArchiveFileDao.all().none {it.archiveId==archive.id.value})
            val actions=fragileFollowupActions(monitorGuard)
            actions.forEach {assertDoesNotThrow(it)}
            val file=FileDao[safetyOriginalFile(safetyOriginalRoot,"unexpected-auto-original")]
            val storage=io.hizhengfu.cloud.common.file.LocalFileObjectStorage(safetyOriginalRoot.toString())
            val reference=CollectionPermanentArchiveFileDao.new {
                orgId=archive.orgId;archiveId=archive.id.value;bizId=archive.bizId;fileId=file.id.value
                metadata=CollectionArchiveFileIdentity.metadata(file,archive.orgId,archive.bizType,archive.bizId,
                    io.hizhengfu.cloud.common.file.FileReadPath.keys(file.path,"LOCAL",storage),
                    storage.openStream(file.path).use {CollectionOriginalContent.read(it)})
            }
            val tables=fragileFollowupTables()
            try {
                val before=settledSafetySelectionState(*tables)
                assertSafetySelectionIntegrity(*actions)
                assertEquals(before,settledSafetySelectionState(*tables))
            } finally {reference.delete()}
            settledSafetySelectionState(*tables)
            assertTrue(CollectionPermanentArchiveFileDao.all().none {it.archiveId==archive.id.value})
            actions.forEach {assertDoesNotThrow(it)}
        }
    }
    @Test fun `operation safety originals fragile followup accepts fileless automatic evidence and rejects extra actual original`()=assertFragileEmptyAutomaticOriginal()
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres operation safety originals fragile followup accepts fileless automatic evidence and rejects extra actual original`()=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertFragileEmptyAutomaticOriginal()} finally {safetyDatabase=null}
    }

    private fun assertFragileIncidentSourceOriginals(kind:String,damage:String)=fixture(initialNature=EnumCollectionNature.OTHER) {
        val automatic=kind=="AUTO_CLEARED_1"
        clearedFragileFollowup(automatic) {problem,_ ->
            val use=if(automatic)CollectionFragileAutomaticCheckDao[problem].useRecordId else CollectionFragileMonitoringDao[problem].useRecordId
            val record=if(kind=="STARTED")use else problem
            val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_FRAGILITY && it.bizId==record && it.phase==kind}
            val file=if(automatic)CollectionPermanentArchiveFileDao.all().single {it.archiveId==archive.id.value}.fileId else "proof"
            val provider=CollectionFragileMonitoringIncidentSourceProvider(archives)
            val source=IncidentSourceVo(CollectionConst.BIZ_FRAGILITY,"piece",use)
            val items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"据实保留监测异常"))
            val actions=arrayOf<()->Unit>(
                {provider.snapshot("museum",source,items)},
                {provider.stoppedCustody("museum","piece",source)},
            )
            assertSafetyOriginalDamage(safetyOriginalRoot,archive,file,damage,fragileFollowupTables(),actions) {
                actions.forEach {assertDoesNotThrow(it)}
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "STARTED,MISSING","STARTED,IDENTITY","STARTED,EXTRA","STARTED,BYTES","STARTED,BASELINE",
        "MONITORED,MISSING","MONITORED,IDENTITY","MONITORED,EXTRA","MONITORED,BYTES","MONITORED,BASELINE",
        "AUTO_CLEARED_1,MISSING","AUTO_CLEARED_1,IDENTITY","AUTO_CLEARED_1,EXTRA","AUTO_CLEARED_1,BYTES","AUTO_CLEARED_1,BASELINE")
    fun `operation safety incident source originals fragile rejects changed manifest bytes and missing baseline`(kind:String,damage:String)=assertFragileIncidentSourceOriginals(kind,damage)

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "STARTED,MISSING","STARTED,IDENTITY","STARTED,EXTRA","STARTED,BYTES","STARTED,BASELINE",
        "MONITORED,MISSING","MONITORED,IDENTITY","MONITORED,EXTRA","MONITORED,BYTES","MONITORED,BASELINE",
        "AUTO_CLEARED_1,MISSING","AUTO_CLEARED_1,IDENTITY","AUTO_CLEARED_1,EXTRA","AUTO_CLEARED_1,BYTES","AUTO_CLEARED_1,BASELINE")
    fun `postgres operation safety incident source originals fragile rejects changed manifest bytes and missing baseline`(kind:String,damage:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertFragileIncidentSourceOriginals(kind,damage)} finally {safetyDatabase=null}
    }

    @Test fun `fragility detail exposes frozen files for only the selected collection record phases`()=fixture {
        val assessment=assessed();val plan=planned(assessment);approve();out();val use=begin(plan)
        val monitor=monitoring.inspect("piece",use,monitoringRequest())
        FileDao["proof"].name="当前通用文件名称，不应覆盖冻结名称.pdf";actor("keeper",CollectionConst.CARE)
        val before=settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        @Suppress("UNCHECKED_CAST")
        val frozen=service.detail("piece")["archiveFiles"] as Map<String,Map<String,List<Map<String,Any?>>>>
        listOf(assessment to "ASSESSMENT",assessment to "CONSERVATION",assessment to "KEEPER",plan to "USE_PLAN",plan to "KEEPER",
            use to "STARTED",use to "ENVIRONMENT_VERIFIED",monitor to "MONITORED").forEach {(record,phase)->
            val file=frozen.getValue(record).getValue(phase).single();assertEquals("proof",file["fileId"])
            val saved=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase==phase}
            assertEquals(CollectionPermanentArchiveFileDao.all().single {it.archiveId==saved.id.value}.metadata,file["metadata"])
            assertTrue(file["metadata"].toString().contains("实际评估签署.pdf"));assertFalse(file["metadata"].toString().contains("当前通用文件名称"))
        }
        assertFalse(frozen.getValue(assessment).containsKey("USE_PLAN"));assertFalse(frozen.getValue(use).containsKey("HISTORY"))
        assertEquals(before,settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files))
        reject {CollectionPermanentArchiveDao.all().single {it.bizId==assessment && it.phase=="ASSESSMENT"}.orgId="other";service.detail("piece")}
        reject {io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();service.detail("piece")}
    }

}
