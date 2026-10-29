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
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.postgresql.ds.PGSimpleDataSource
import io.hizhengfu.cloud.sys.system.dao.RoleUsers
import io.hizhengfu.cloud.sys.system.dao.UserGroups

class CollectionJointSafetyPersistenceTest {
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {c->c.getArgument<String>(0)}}
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val guard=CollectionJointSafetyGuard(users)
    private val locks=CollectionOperationLockService(listOf(guard,CollectionFragilityGuard(users),CollectionIncidentOperationGuard(),CollectionCareSafetyGuard(archives)))
    private val service=CollectionFragilityService(scope,users,archives,locks)
    private val joint=CollectionJointSafetyService(scope,users,archives)
    private val jointOriginals=CollectionJointSafetyOriginalService(joint,archives)
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
    private var fixtureDataSource:javax.sql.DataSource?=null
    private lateinit var req:String
    private lateinit var at:LocalDateTime
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    @BeforeEach fun setup(){context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);actor("operator")}
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();context.close()}
    private fun actor(id:String,permission:String="ADMIN") {
        val u=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(afterCommit:((Database)->Unit)?=null,block:()->Unit) {
        val pgConfig=System.getProperty("collection.test.pg.config")
        val postgres=pgConfig?.let {config->
            val properties=Properties().apply {Path.of(config).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {
                setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password")
                currentSchema="joint_safety_"+UUID.randomUUID().toString().replace("-","")
            }.also {source->source.connection.use {connection->connection.createStatement().use {it.execute("CREATE SCHEMA ${source.currentSchema}")}}}
        }
        val databaseFile=if(postgres==null && afterCommit!=null)java.nio.file.Files.createTempFile("joint-report-", ".db") else null
        try {
        fixtureDataSource=postgres
        db=if(postgres!=null)Database.connect(postgres,databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig {useNestedTransactions=true})
            else Database.connect(databaseFile?.let {"jdbc:sqlite:$it"} ?: "jdbc:sqlite:file:joint-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC",databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig {useNestedTransactions=true})
        transaction(db) {
            SchemaUtils.create(CollectionLedgerLogs,CollectionJointSafetyPlans,CollectionJointSafetyRounds,CollectionFragileMonitorRuntimes,CollectionFragileAutomaticChecks,CollectionFragileMonitorings,CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,CollectionFragileUseAdjustments,CollectionCareChecks,CollectionCareWorks,CollectionIncidentItems,Collections,CollectionOrgs,Requisitions,Loans,LoanComplianceRecords,CollectionCalendarVersions,CollectionCatalogVersions,CollectionReturns,ReturnComplianceRecords,CollectionBusinessItems,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstanceLogs,Warehouses)
            at=now().minusMinutes(10)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            WarehouseDao.new("store"){orgId="museum";name="本馆库房";sceneType=EnumLocationScene.STORAGE}
            CollectionDao.new("piece"){orgId="museum";name="脆弱材质器物";totalRegNo="M1";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.OTHER;source=EnumSource.OLD}
            CollectionDao.new("second"){orgId="museum";name="第二件";totalRegNo="M2";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.OTHER;source=EnumSource.OLD}
            FileDao.new("proof"){name="实际评估签署.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            req=requisitions.save(RequisitionVo(collectionIds="piece,second",purpose=EnumMoveReason.EXHIBIT,basis="本馆展览",department="展陈部",operator="operator",toWarehouseId="store",dossier=RequisitionDossierVo("dept","department","keeper","director","operator","固定支撑展出","本馆专题展览","本馆展柜","实际保护方案","proof",at.minusHours(1),at.plusDays(30),transportRequired=false,transportDecisionReason="本馆展柜原位提用")))
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            originalReader("security",CollectionConst.CARE);originalReader("director",CollectionConst.CARE)
            listOf("operator","conservation","keeper").forEach {actorRole(it,"CARE_OPERATOR",setOf(CollectionConst.CARE))}
            actorRole("operator","REQUISITION_OPERATOR",setOf(CollectionConst.STORAGE))
            originalReader("custodian",CollectionConst.STORAGE)
            actorRole("custodian",CollectionConst.ROLE_KEEPER)
            block()
        }
        afterCommit?.invoke(db)
        } finally {
            fixtureDataSource=null
            databaseFile?.let {java.nio.file.Files.deleteIfExists(it)}
            postgres?.connection?.use {connection->connection.createStatement().use {it.execute("DROP SCHEMA ${postgres.currentSchema} CASCADE")}}
        }
    }
    private val signature=CollectionCareSignatureVo("本人核对共同检查原件与职责","proof")
    private fun assess() {
        listOf("piece","second").forEach {cid->
            val v=FragilityAssessmentVo(sourceRevision=service.detail(cid)["sourceRevision"] as String,assessedAt=now(),fragileMaterial=false,materialAndCondition="已实际核对",environmentRequirements="保存环境规范",storageProtection="独立支撑",useRiskAssessment="真实风险评估",conservationUserId="conservation",keeperUserId="keeper",historyVerifiedFrom=now().minusDays(40),historyReviewFileIds="proof",assessmentFileIds="proof")
            val id=service.assess(cid,v)
            actor("conservation");service.sign(cid,id,signature);actor("keeper");service.sign(cid,id,signature);actor("operator")
        }
    }
    private fun plan()=CollectionJointSafetyPlanVo(sourceRevision=joint.detail(req)["sourceRevision"] as String,preparedAt=now(),inspectionIntervalDays=1,firstDueAt=now().plusHours(12),keeperUserId="keeper",useUserId="operator",securityUserId="security",directorUserId="director",scopeAndMethod="逐件实物、病害、环境及场所防护",environmentalAndSecurityChecks="现场环境监测与值守设施检查",measuresAndReporting="实际处理并及时报告主要负责人",planFileIds="proof")
    private fun confirmPlan(id:String) {listOf("KEEPER" to "keeper","USE" to "operator","SECURITY" to "security").forEach {(role,user)->actor(user);joint.signPlan(req,id,role,signature)};actor("operator")}
    private fun prepared():String=joint.savePlan(req,plan()).also(::confirmPlan)
    private fun approve() {
        val r=RequisitionDao[req];r.flowInstanceId="flow";r.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire("museum",CollectionConst.BIZ_REQUISITION,req,listOf("piece","second"))
        listOf("department","keeper","leader").forEach {node->FlowInstanceLogDao.new {instanceId="flow";taskId="task-$node";nodeKey=node;action="APPROVE";operator=statutory.actors(r)[node];comment="本人确认完整使用及保护方案";createTime=now()} }
        requisitions.onFlowCompleted(r,FlowCompletedEvent("flow",CollectionConst.FLOW_REQUISITION,"提用",req,CollectionConst.BIZ_REQUISITION,emptyMap(),"operator","operator"))
    }
    private fun out() {
        val reviewed=now()
        statutory.recordHandover(req,RequisitionHandoverVo(now(),reviewed,"keeper","proof",setOf("custodian"),setOf("operator"),listOf("piece","second").map {RequisitionConditionVo(it,"按实际原件逐件核对",false)},true,true,"proof","proof"))
        val previous=SecurityContextHolder.getContext().authentication
        actor("custodian",CollectionConst.STORAGE)
        try { requisitions.executeOut(req,CollectionOutboundExecutionVo("实际点交","proof","proof")) }
        finally { SecurityContextHolder.getContext().authentication=previous }
    }
    private fun running():String {assess();val p=prepared();approve();out();return p}
    private fun inspection(cid:String="piece",bad:Boolean=false):String=care.inspect(cid,CollectionCareCheckVo(checkedAt=now(),operatorUserId="operator",actualLocation="本馆展柜",physicallyPresent=true,custodianUserId="custodian",condition="实际保存现状",diseases=if(bad)"发现病害" else "未见新增病害",environmentObservation="实际环境监测",damaged=bad,safeForRoutineCare=!bad,actualMeasures=if(bad)"停止并防护报告" else "维持措施",followUp="按实际情况复查",conditionFileIds="proof",signedFileIds="proof"), CollectionCareEvidence.digest(CollectionCareEvidence.json(CollectionCareEvidence.catalog(CollectionDao[cid]))))
    private fun round(p:String,cc:Set<String>,from:LocalDateTime)=CollectionJointSafetyRoundVo(p,from,now(),cc,"实际展厅","共同核对现场逐件现状","实际措施及后续检查","proof")
    private fun confirmRound(id:String) {listOf("KEEPER" to "keeper","USE" to "operator","SECURITY" to "security").forEach {(role,user)->actor(user);joint.signRound(req,id,role,signature)};actor("operator")}
    @Test fun `joint safety originals require the actual requisition record phase and file`()=fixture {
        val plan=running()
        val from=now();val check=inspection(bad=true)
        val round=joint.recordRound(req,round(plan,setOf(check),from))
        confirmRound(round)
        joint.report(req,round,CollectionJointSafetyReportVo(now(),"operator","director","发现实物保存问题，已实际报告","停止操作并实施保护","proof"))
        actor("keeper")
        val recall=recalls.register(req,recall(),"museum")
        actor("security",CollectionConst.CARE)
        fun archive(recordId:String,phase:String)=jointOriginals.requireArchive(req,recordId,phase,"proof")
        assertEquals("PLAN",CollectionPermanentArchiveDao[archive(plan,"PLAN")].phase)
        assertEquals("SECURITY",CollectionPermanentArchiveDao[archive(plan,"SECURITY")].phase)
        assertEquals("ROUND",CollectionPermanentArchiveDao[archive(round,"ROUND")].phase)
        assertEquals("CHECKED",CollectionPermanentArchiveDao[archive(check,"CHECKED")].phase)
        assertEquals("SECURITY",CollectionPermanentArchiveDao[archive(round,"SECURITY")].phase)
        assertEquals("DIRECTOR_REPORT",CollectionPermanentArchiveDao[archive(round,"DIRECTOR_REPORT")].phase)
        assertEquals("${RequisitionRecallEvidence.PREFIX}$recall",CollectionPermanentArchiveDao[archive(req,"${RequisitionRecallEvidence.PREFIX}$recall")].phase)
        assertTrue(reject {jointOriginals.requireArchive(req,round,"PLAN","proof")}.message!!.contains("阶段"))
        assertTrue(reject {jointOriginals.requireArchive(req,plan,"ROUND","proof")}.message!!.contains("阶段"))
        assertTrue(reject {jointOriginals.requireArchive(req,round,"DIRECTOR","proof")}.message!!.contains("阶段"))
        assertTrue(reject {jointOriginals.requireArchive(req,round,"ROUND","missing")}.message!!.contains("文件"))
        assertTrue(reject {jointOriginals.requireArchive(req,"other","PLAN","proof")}.message!!.contains("不属于"))
        assertTrue(reject {jointOriginals.requireArchive("other",round,"ROUND","proof")}.message!!.contains("来源"))
    }
    @Test fun `custody originals remain scoped to each signed succession stage`()=fixture {
        running()
        val request=custody()
        actor("new-keeper")
        val recordId=joint.registerCustody(req,request,"museum")
        val proposed=jointOriginals.requireArchive(req,req,"CUSTODY_P_$recordId","proof")
        assertEquals("CUSTODY_P_$recordId",CollectionPermanentArchiveDao[proposed].phase)
        assertTrue(reject {jointOriginals.requireArchive(req,req,"CUSTODY_KEEPER_$recordId","proof")}.message!!.contains("阶段"))
        confirmCustody(recordId)
        actor("security",CollectionConst.CARE)
        for(role in listOf("KEEPER","USE","DIRECTOR")) {
            val phase="CUSTODY_${role}_$recordId"
            assertEquals(phase,CollectionPermanentArchiveDao[jointOriginals.requireArchive(req,req,phase,"proof")].phase)
        }
    }
    @Test fun `report handover originals remain bound to the actual report round and signatures`()=fixture {
        val round=pendingReport()
        actor("new-keeper")
        val recordId=joint.handOverReport(req,round,reportHandover(round),"museum")
        assertEquals("REPORT_HANDOVER_P_$recordId",CollectionPermanentArchiveDao[jointOriginals.requireArchive(req,round,"REPORT_HANDOVER_P_$recordId","proof")].phase)
        confirmReportHandover(round,recordId)
        actor("security",CollectionConst.CARE)
        for(role in listOf("KEEPER","DIRECTOR")) {
            val phase="REPORT_HANDOVER_${role}_$recordId"
            assertEquals(phase,CollectionPermanentArchiveDao[jointOriginals.requireArchive(req,round,phase,"proof")].phase)
        }
        assertTrue(reject {jointOriginals.requireArchive(req,req,"REPORT_HANDOVER_P_$recordId","proof")}.message!!.contains("阶段"))
    }
    @Suppress("UNCHECKED_CAST") private fun rows()=joint.detail(req)["items"] as List<Map<String,Any?>>
    private fun travel(t:LocalDateTime,block:()->Unit) {mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock->clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenReturn(t);block()}}
    private fun flush() {
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
    }
    private fun reject(block:()->Unit):BadRequestException {flush();return assertThrows(BadRequestException::class.java){transaction(db){block()}}}
    @Test fun `real approval and actual out require a jointly signed current plan`()=fixture {
        assess();assertTrue(reject {approve()}.message!!.contains("共同制定"))
        val p=joint.savePlan(req,plan());actor("keeper");joint.signPlan(req,p,"KEEPER",signature);actor("operator")
        reject {approve()};confirmPlan(p);approve();out()
        assertEquals(2,StorageMovementDao.all().toList().size)
        assertEquals(0,CollectionJointSafetyRoundDao.all().toList().size)
        assertEquals(setOf("CURRENT"),rows().map {it["status"]}.toSet())
    }
    @Test fun `each actual piece advances from its own check time only after all three confirm`()=fixture {
        val p=running();val start=now();val ck=inspection();val checked=CollectionCareCheckDao[ck].content.checkedAt
        val later=now().plusHours(13)
        travel(later) {
            val r=joint.recordRound(req,round(p,setOf(ck),start))
            assertEquals(setOf("OVERDUE"),rows().map {it["status"]}.toSet())
            actor("security");joint.signRound(req,r,"SECURITY",signature);actor("operator")
            assertEquals(setOf("OVERDUE"),rows().map {it["status"]}.toSet())
            confirmRound(r)
            val byId=rows().associateBy {it["collectionId"]}
            assertEquals(checked.plusDays(1),byId["piece"]!!["nextDueAt"])
            assertEquals("CURRENT",byId["piece"]!!["status"]);assertEquals("OVERDUE",byId["second"]!!["status"])
            assertDoesNotThrow {guard.requireAllowed("piece",null,null)}
            reject {guard.requireAllowed("second",null,null)}
            assertEquals(r,joint.recordRound(req,round(p,setOf(ck),start)))
            reject {joint.recordRound(req,round(p,setOf(ck),start).copy(actualMeasures="不能重复计算"))}
        }
    }
    @Test fun `late truthful plan needs a new actual first check instead of backdating compliance`()=fixture {
        running();val t=now().plusHours(1)
        travel(t) {
            val old=CollectionJointSafetyPlanDao.all().single().id.value
            val p=joint.savePlan(req,plan().copy(previousPlanId=old));confirmPlan(p)
            assertEquals(setOf("OVERDUE"),rows().map {it["status"]}.toSet())
            reject {guard.requireAllowed("piece",null,null)}
            val start=now();val ck=inspection();val rr=joint.recordRound(req,round(p,setOf(ck),start));confirmRound(rr)
            assertDoesNotThrow {guard.requireAllowed("piece",null,null)}
            assertFalse(RequisitionDao[req].returned)
        }
    }
    @Test fun `report may precede participation signatures but never clears unsafe care originals`()=fixture {
        val p=running();val start=now();val ck=inspection(bad=true);val rr=joint.recordRound(req,round(p,setOf(ck),start))
        val report=CollectionJointSafetyReportVo(now(),"operator","director","实际发现损伤及处理报告","停止并保护现场","proof")
        assertEquals(rr,joint.report(req,rr,report));assertEquals(rr,joint.report(req,rr,report))
        reject {joint.acknowledge(req,rr,signature)}
        actor("director");joint.acknowledge(req,rr,signature);actor("operator")
        confirmRound(rr)
        assertDoesNotThrow {guard.requireAllowed("piece",null,null)}
        reject {locks.requireUnlocked("piece",CollectionConst.BIZ_REQUISITION,req)}
        inspection();reject {locks.requireUnlocked("piece",CollectionConst.BIZ_REQUISITION,req)}
        assertNull(CollectionCareCheckDao[ck].clearance)
        assertFalse(RequisitionDao[req].returned)
        assertTrue(CollectionJointSafetyRoundDao[rr].director!=null)
    }
    @Test fun `later safe round does not remove unresolved director report from older unsafe inspection`()=fixture {
        val p=running();val start=now();val bad=inspection(bad=true);val rr=joint.recordRound(req,round(p,setOf(bad),start));confirmRound(rr)
        val start2=now();val good=inspection();val rr2=joint.recordRound(req,round(p,setOf(good),start2));confirmRound(rr2)
        assertEquals("AWAITING_DIRECTOR",rows().first {it["collectionId"]=="piece"}["status"])
        reject {guard.requireAllowed("piece",null,null)}
        assertDoesNotThrow {guard.requireAllowed("piece",CollectionConst.BIZ_RETURN,"return")}
        reject {joint.report(req,rr2,CollectionJointSafetyReportVo(now(),"operator","director","未出现的新问题","实际措施","proof"))}
    }
    @Test fun `plan roles are designated self only and source changes need immutable successor`()=fixture {
        val v=plan();val p=joint.savePlan(req,v)
        reject {joint.signPlan(req,p,"KEEPER",signature)}
        actor("keeper");joint.signPlan(req,p,"KEEPER",signature)
        assertEquals(p,joint.signPlan(req,p,"KEEPER",signature))
        reject {joint.signPlan(req,p,"KEEPER",signature.copy(opinion="覆盖旧意见"))}
        actor("operator");val p2=joint.savePlan(req,v.copy(previousPlanId=p,scopeAndMethod="新方案"))
        actor("security");reject {joint.signPlan(req,p,"SECURITY",signature)}
        RequisitionDao[req].basis="变化后的提用依据"
        reject {joint.signPlan(req,p2,"SECURITY",signature)}
        assertNull(CollectionJointSafetyPlanDao[p2].security)
    }
    @Test fun `revoked security responsibility prevents actual out even after original signatures`()=fixture {
        assess();prepared();approve()
        doThrow(BadRequestException("已不具备安全保卫职责")).`when`(users).requireRole("security","museum",CollectionConst.ROLE_SECURITY)
        reject {out()};assertFalse(RequisitionDao[req].outExecuted)
    }
    @Test fun `old returned flag cannot substitute for receipt and original corruption is visible on board`()=fixture {
        running();RequisitionDao[req].returned=true
        travel(now().plusDays(2)) {reject {guard.requireAllowed("piece",null,null)};assertEquals("OVERDUE",rows().first()["status"])}
        RequisitionDao[req].handover=RequisitionDao[req].handover!!.let {it.copy(content=it.content.copy(handedAt=it.content.handedAt.minusDays(1)))}
        reject {joint.detail(req)}
        val request=org.springframework.mock.web.MockHttpServletRequest().also {it.setParameter("pageSize","0")}
        @Suppress("UNCHECKED_CAST") val board=joint.page(request).list as List<Map<String,Any?>>
        assertEquals(setOf("ORIGINAL_ERROR"),board.map {it["status"]}.toSet())
    }
    @Test fun `checks outside this usage or true round time cannot advance compliance`()=fixture {
        val p=running();val start=now();val ck=inspection()
        reject {joint.recordRound(req,round(p,setOf(ck),start.plusMinutes(1)).copy(endedAt=start.plusMinutes(2)))}
        val rr=joint.recordRound(req,round(p,setOf(ck),start));confirmRound(rr)
        CollectionCareCheckDao[ck].content=CollectionCareCheckDao[ck].content.copy(condition="篡改现状")
        reject {joint.detail(req)};reject {guard.requireAllowed("piece",null,null)}
    }
    @Test fun `missing files roll back new plan and actual round without fabricating inspection`()=fixture {
        reject {joint.savePlan(req,plan().copy(planFileIds="missing"))}
        assertEquals(0,CollectionJointSafetyPlanDao.all().toList().size)
        val p=running();val start=now();val ck=inspection()
        reject {joint.recordRound(req,round(p,setOf(ck),start).copy(signedFileIds="missing"))}
        assertEquals(0,CollectionJointSafetyRoundDao.all().toList().size)
        assertEquals(1,CollectionCareCheckDao.all().toList().size)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
    }
    @Test fun `workbench preserves scope query filters and page size zero`()=fixture {
        running();val request=org.springframework.mock.web.MockHttpServletRequest().also {it.setParameter("pageSize","0")}
        assertEquals(2,joint.page(request).total)
        request.setParameter("keyword","M2");assertEquals(1,joint.page(request).total)
        request.setParameter("status","OVERDUE");assertEquals(0,joint.page(request).total)
        actor("reader","COLLECTION_VIEW");reject {joint.page(request)};reject {joint.detail(req)}
        actor("operator");RequisitionDao[req].orgId="foreign";reject {joint.detail(req)}
        request.setParameter("keyword","");request.setParameter("status","");assertEquals(0,joint.page(request).total)
    }
    @Test fun `source changed after signed plan is displayed as requiring fresh verification`()=fixture {
        running();RequisitionDao[req].basis="新实际用途依据"
        assertEquals(setOf("STALE_PLAN"),rows().map {it["status"]}.toSet())
        reject {guard.requireAllowed("piece",null,null)}
    }
    private fun receive(bad:Boolean=false):String {
        val r=CollectionReturnDao.all().single()
        returns.save(r.toModel().copy(collectionIds="piece",operator="operator",keeperUserId="keeper",toWarehouseId="store"))
        val rs=ReturnStatutoryService(scope,users,binder,archives)
        rs.recordInspection(r.id.value,ReturnInspectionVo(now(),setOf("custodian"),setOf("operator"),emptySet(),"proof",true,true,listOf(ReturnItemCheckVo("piece",EnumLoanObjectKind.OTHER,true,"核对出库原件",!bad,"实际接收现状",false,null,bad,EnumReturnSecurityIncident.NONE,if(bad)"损伤待核查" else null,if(bad)"隔离保护" else null)),"proof"))
        r.flowInstanceId="return-flow";r.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire("museum",CollectionConst.BIZ_RETURN,r.id.value,listOf("piece"))
        FlowInstanceLogDao.new {instanceId="return-flow";taskId="return-task";nodeKey="keeper";action="APPROVE";operator="keeper";comment="实际核对接收及保管";createTime=now()}
        returns.onFlowCompleted(r,FlowCompletedEvent("return-flow",CollectionConst.FLOW_RETURN,"归还",r.id.value,CollectionConst.BIZ_RETURN,emptyMap(),"operator","operator"))
        returns.executeApprovedReturn(r.id.value)
        return r.id.value
    }
    @Test fun `actual partial receipt releases only received piece due and rejects later in store check as in use`()=fixture {
        val p=running();val before=now();val ck=inspection();val ret=receive()
        travel(now().plusDays(2)) {
            assertEquals(setOf("piece"),CollectionJointSafetyEvidence.receivedIds(RequisitionDao[req]))
            val byId=rows().associateBy {it["collectionId"]}
            assertEquals("RECEIVED",byId["piece"]!!["status"]);assertEquals("OVERDUE",byId["second"]!!["status"])
            assertDoesNotThrow {guard.requireAllowed("piece",null,null)};reject {guard.requireAllowed("second",null,null)}
            // 事后整理实际归还前发生的检查仍保留真实时间，不补造归还后的在用检查。
            val rr=joint.recordRound(req,round(p,setOf(ck),before));confirmRound(rr)
            val later=inspection();reject {joint.recordRound(req,round(p,setOf(later),now()))}
            CollectionReturnDao[ret].inspection=CollectionReturnDao[ret].inspection!!.copy(inspectedAt=now())
            reject {guard.requireAllowed("piece",null,null)}
        }
    }
    @Test fun `received with hold never removes pending actual director reporting`()=fixture {
        val p=running();val start=now();val ck=inspection(bad=true);val rr=joint.recordRound(req,round(p,setOf(ck),start));confirmRound(rr)
        receive(true)
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["piece"].lifeStatus)
        assertEquals("AWAITING_DIRECTOR",rows().first {it["collectionId"]=="piece"}["status"])
        reject {guard.requireAllowed("piece",null,null)}
        assertTrue(rows().first {it["collectionId"]=="piece"}["actualReceived"] as Boolean)
    }
    @Test fun `expired first check plan and removed signature cannot authorize actual extraction`()=fixture {
        assess();val p=prepared();approve()
        travel(now().plusHours(13)) {reject {out()};assertFalse(RequisitionDao[req].outExecuted)}
        CollectionJointSafetyPlanDao[p].security=null
        reject {joint.detail(req)};reject {out()}
        assertFalse(RequisitionDao[req].outExecuted)
    }
    @Test fun `multiple actual checks survive database collection order and jointly advance both pieces`()=fixture {
        val p=running();val start=now();val a=inspection();val b=inspection("second")
        val v=round(p,linkedSetOf(b,a),start);val r=joint.recordRound(req,v)
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        confirmRound(r)
        assertEquals(r,joint.recordRound(req,v.copy(checkIds=linkedSetOf(a,b))))
        assertEquals(setOf("CURRENT"),rows().map {it["status"]}.toSet())
        assertEquals(0,rows().sumOf {it["pendingRoundCount"] as Int})
    }
    private fun custody():RequisitionCustodyRequest {
        val ids=setOf("piece","second")-CollectionJointSafetyEvidence.receivedIds(RequisitionDao[req])
        val cc=ids.map {inspection(it)}.toSet()
        listOf("new-keeper","new-use","new-director").forEach {if(io.hizhengfu.cloud.sys.system.dao.UserDao.findById(it)==null)originalReader(it,CollectionConst.CARE)}
        return RequisitionCustodyRequest(sourceRevision=joint.detail(req)["sourceRevision"] as String,collectionIds=ids,checkIds=cc,handedAt=now(),reason="原责任人员离任",actualHandover="依据原点交单逐件核对实物、保管位置与责任范围",outstandingMatters="继续保留所有尚未办理的检查及异常事项",keeperUserId="new-keeper",useUserId="new-use",directorUserId="new-director",appointmentFileIds="proof",handoverFileIds="proof")
    }
    private fun confirmCustody(id:String) {
        listOf("KEEPER" to "new-keeper","USE" to "new-use","DIRECTOR" to "new-director").forEach {(role,user)->actor(user);joint.signCustody(req,id,role,signature,"museum")}
    }
    @Test fun `actual custody succession lets new staff resume plans without rewriting outgoing truth`()=fixture {
        val oldPlan=running();val r=RequisitionDao[req];val original=CollectionCareEvidence.json(requisitionProposal(r));val movements=StorageMovementDao.all().map {it.id.value}
        val proposal=custody()
        actor("new-keeper")
        assertTrue(reject {joint.savePlan(req,plan().copy(previousPlanId=oldPlan,keeperUserId="new-keeper",useUserId="new-use",directorUserId="new-director"))}.message!!.contains("职责"))
        val id=joint.registerCustody(req,proposal,"museum")
        assertEquals("operator",(joint.detail(req)["actors"] as Map<*,*>)["useUserId"])
        assertTrue(reject {joint.signCustody(req,id,"DIRECTOR",signature,"museum")}.message!!.contains("本人"))
        actor("new-director");assertTrue(reject {joint.signCustody(req,id,"DIRECTOR",signature,"museum")}.message!!.contains("先由"))
        confirmCustody(id)
        io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
        assertEquals("new-use",(joint.detail(req)["actors"] as Map<*,*>)["useUserId"])
        assertEquals(setOf("STALE_PLAN"),rows().map {it["status"]}.toSet())
        val p=joint.savePlan(req,plan().copy(previousPlanId=oldPlan,keeperUserId="new-keeper",useUserId="new-use",directorUserId="new-director"))
        listOf("KEEPER" to "new-keeper","USE" to "new-use","SECURITY" to "security").forEach {(role,user)->actor(user);joint.signPlan(req,p,role,signature)}
        assertEquals(setOf("OVERDUE"),rows().map {it["status"]}.toSet())
        assertEquals(original,CollectionCareEvidence.json(requisitionProposal(r)));assertEquals(movements,StorageMovementDao.all().map {it.id.value})
        assertTrue(CollectionJointSafetyPlanDao[oldPlan].keeper!=null)
        assertEquals(id,joint.detail(req)["effectiveCustodyId"])
    }
    @Test fun `custody refuses future handover missing pieces and mismatched original museum`()=fixture {
        running();val v=custody();actor("new-keeper")
        listOf(v.copy(handedAt=now().plusDays(1)),v.copy(collectionIds=setOf("piece")),v.copy(checkIds=emptySet()),v.copy(sourceRevision="stale"),v.copy(handoverFileIds="")).forEach {bad->reject {joint.registerCustody(req,bad,"museum")}}
        reject {joint.registerCustody(req,v,"other")};reject {joint.registerCustody(req,v,"")}
        assertTrue(RequisitionCustodyEvidence.entries(RequisitionDao[req]).isEmpty())
    }
    @Test fun `partial receipt invalidates an unsigned full custody scope and permits corrected remaining scope`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        actor("operator");receive(true)
        actor("new-keeper");assertTrue(reject {joint.signCustody(req,id,"KEEPER",signature,"museum")}.message!!.contains("清单"))
        actor("operator");val next=custody().copy(previousId=id)
        assertEquals(setOf("second"),next.collectionIds)
        actor("new-keeper");val second=joint.registerCustody(req,next,"museum");confirmCustody(second)
        assertEquals(second,joint.detail(req)["effectiveCustodyId"])
        assertEquals(2,RequisitionCustodyEvidence.entries(RequisitionDao[req]).size)
    }
    @Test fun `new responsibility cannot validate old plan for checks begun after succession`()=fixture {
        val old=running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum");confirmCustody(id)
        actor("operator");val from=now();val cc=inspection()
        assertTrue(reject {joint.recordRound(req,round(old,setOf(cc),from))}.message!!.contains("责任已承接"))
        assertTrue(reject {joint.savePlan(req,plan().copy(previousPlanId=old,preparedAt=at,keeperUserId="new-keeper",useUserId="new-use",directorUserId="new-director"))}.message!!.contains("倒填"))
    }
    @Test fun `custody retains independent signatures and fails closed on missing original file references`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        assertEquals(id,joint.registerCustody(req,v,"museum"))
        joint.signCustody(req,id,"KEEPER",signature,"museum")
        assertEquals(id,joint.signCustody(req,id,"KEEPER",signature,"museum"))
        reject {joint.signCustody(req,id,"KEEPER",signature.copy(opinion="覆盖原确认"),"museum")}
        val a=CollectionPermanentArchiveDao.all().single {it.phase=="CUSTODY_P_$id"}
        CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}
        assertTrue(reject {joint.detail(req)}.message!!.contains("原件引用"))
    }
    @Test fun `revoked successor permission cannot confirm with stale administrator login`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        io.hizhengfu.cloud.sys.system.dao.UserDao["new-keeper"].enabled=false
        reject {joint.signCustody(req,id,"KEEPER",signature,"museum")}
        assertTrue(RequisitionCustodyEvidence.entries(RequisitionDao[req]).single().signatures.isEmpty())
    }
    @Test fun `custody pending tasks reach new actors and vanish only after their actual confirmations`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        assertEquals(setOf("CUSTODY_KEEPER","CUSTODY_USE","CUSTODY_DIRECTOR"),CollectionJointSafetyTaskSource().tasks("museum",now()).filter {it.kind.startsWith("CUSTODY_")}.map {it.kind}.toSet())
        confirmCustody(id)
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).none {it.kind.startsWith("CUSTODY_")})
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).any {it.kind=="STALE_PLAN"})
    }
    @Test fun `obsolete unsigned plan creates correction duty instead of impossible signature duties`()=fixture {
        val original=running()
        val draft=joint.savePlan(req,plan().copy(previousPlanId=original))
        val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum");confirmCustody(id)
        val tasks=CollectionJointSafetyTaskSource().tasks("museum",now())
        assertEquals(draft,tasks.single {it.kind=="STALE_PLAN"}.recordId)
        assertTrue(tasks.none {it.kind.startsWith("PLAN_")})
        actor("operator");reject {joint.signPlan(req,draft,"USE",signature)}
    }
    @Test fun `stale plan duty follows effective custody while pending succession keeps current actors`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        confirmCustody(id)
        fun duty()=CollectionJointSafetyTaskSource().tasks("museum",now()).single {it.kind=="STALE_PLAN"}
        assertEquals(setOf("new-keeper","new-use","security"),duty().assignees.map {it.userId}.toSet())
        val before=duty()
        actor("operator");val next=custody().copy(previousId=id,expectedEffectiveId=id,useUserId="operator")
        actor("new-keeper");joint.registerCustody(req,next,"museum")
        assertEquals(before.assignees,duty().assignees)
        assertEquals(before.basis,duty().basis)
    }
    @Test fun `role loss during final custody signature rolls back activation and the appended original`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        joint.signCustody(req,id,"KEEPER",signature,"museum");actor("new-use");joint.signCustody(req,id,"USE",signature,"museum")
        val count=CollectionPermanentArchiveDao.all().count()
        actor("new-director")
        `when`(binder.resolveSingle(anyString())).thenAnswer {c->io.hizhengfu.cloud.sys.system.dao.UserDao["new-director"].enabled=false;c.getArgument<String>(0)}
        reject {joint.signCustody(req,id,"DIRECTOR",signature,"museum")}
        assertEquals(count,CollectionPermanentArchiveDao.all().count())
        assertFalse(RequisitionCustodyEvidence.entries(RequisitionDao[req]).single().complete)
    }
    @Test fun `custody chain preserves prior effective responsibility until the next version is fully signed`()=fixture {
        running();val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum");confirmCustody(id)
        actor("operator");val next=custody().copy(previousId=id,expectedEffectiveId=id,useUserId="operator")
        actor("new-keeper");val pending=joint.registerCustody(req,next,"museum")
        assertEquals(id,joint.detail(req)["effectiveCustodyId"])
        assertEquals("new-use",(joint.detail(req)["actors"] as Map<*,*>)["useUserId"])
        reject {joint.signCustody(req,id,"KEEPER",signature,"museum")}
        joint.signCustody(req,pending,"KEEPER",signature,"museum");actor("operator");joint.signCustody(req,pending,"USE",signature,"museum");actor("new-director");joint.signCustody(req,pending,"DIRECTOR",signature,"museum")
        assertEquals(pending,joint.detail(req)["effectiveCustodyId"])
        assertEquals("operator",(joint.detail(req)["actors"] as Map<*,*>)["useUserId"])
        assertEquals(2,RequisitionCustodyEvidence.entries(RequisitionDao[req]).count {it.complete})
    }
    @Test fun `Spring discovers joint safety guard in real operation lock without duplicate clearance source`() {
        val ctx=org.springframework.context.annotation.AnnotationConfigApplicationContext()
        try {ctx.beanFactory.registerSingleton("users",users);ctx.register(CollectionJointSafetyGuard::class.java,CollectionOperationLockService::class.java);ctx.refresh();assertEquals(1,ctx.getBeansOfType(CollectionOperationSafetyGuard::class.java).size);assertNotNull(ctx.getBean(CollectionOperationLockService::class.java))}finally{ctx.close()}
    }
    private fun pendingReport():String {
        val p=running();val from=now();val check=inspection(bad=true)
        val rr=joint.recordRound(req,round(p,setOf(check),from))
        joint.report(req,rr,CollectionJointSafetyReportVo(now(),"operator","director","发现实物保存问题，已实际报告","停止操作并实施保护","proof"))
        listOf("new-keeper","new-director","later-director").forEach {originalReader(it,CollectionConst.CARE)}
        return rr
    }
    private fun reportHandover(rr:String)=CollectionJointReportHandoverVo(expectedReportDigest=CollectionJointReportHandoverEvidence.report(CollectionJointSafetyRoundDao[rr]).digest,handedAt=now(),keeperUserId="new-keeper",directorUserId="new-director",reason="原主要负责人离任，报告需移交现任人员处理",outstandingMatters="原报告内容及全部未结检查、调查和处置事项据实移交",appointmentFileIds="proof",handoverFileIds="proof")
    private fun confirmReportHandover(rr:String,id:String) {
        actor("new-keeper");joint.signReportHandover(req,rr,id,"KEEPER",signature,"museum")
        actor("new-director");joint.signReportHandover(req,rr,id,"DIRECTOR",signature,"museum")
    }
    @Test fun `new director receives transferred old report without rewriting original recipient or clearing damage`()=fixture {
        val rr=pendingReport();val row=CollectionJointSafetyRoundDao[rr];val original=row.report
        io.hizhengfu.cloud.sys.system.dao.UserDao["director"].enabled=false
        actor("new-director");assertTrue(reject {joint.acknowledge(req,rr,signature,"museum")}.message!!.contains("本人"))
        actor("new-keeper");val id=joint.handOverReport(req,rr,reportHandover(rr),"museum")
        assertFalse(CollectionJointSafetyEvidence.reportHandled(row));assertEquals(1,rows().first {it["collectionId"]=="piece"}["pendingDirectorCount"])
        confirmReportHandover(rr,id)
        assertEquals(original,row.report);assertNull(row.director)
        assertTrue(CollectionJointSafetyEvidence.reportHandled(row));assertEquals(0,rows().first {it["collectionId"]=="piece"}["pendingDirectorCount"])
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).none {it.recordId==rr && (it.kind.startsWith("REPORT_HANDOVER_") || it.kind=="DIRECTOR_CONFIRM")})
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).any {it.recordId==rr && it.kind.startsWith("ROUND_")})
        assertTrue(CollectionJointSafetyEvidence.checked(row).any(CollectionCareEvidence::unsafe))
        reject {CollectionCareSafetyGuard(archives).requireAllowed("piece",null,null)}
    }
    @Test fun `report handover confirms ordered actual people and never permits a second original closure`()=fixture {
        val rr=pendingReport();actor("new-keeper");val v=reportHandover(rr);val id=joint.handOverReport(req,rr,v,"museum")
        assertEquals(id,joint.handOverReport(req,rr,v,"museum"))
        reject {joint.signReportHandover(req,rr,id,"DIRECTOR",signature,"museum")}
        actor("new-director");assertTrue(reject {joint.signReportHandover(req,rr,id,"DIRECTOR",signature,"museum")}.message!!.contains("先由"))
        confirmReportHandover(rr,id)
        assertEquals(id,joint.signReportHandover(req,rr,id,"DIRECTOR",signature,"museum"))
        actor("director");assertTrue(reject {joint.acknowledge(req,rr,signature,"museum")}.message!!.contains("后续"))
        actor("new-keeper");reject {joint.handOverReport(req,rr,v.copy(reason="不能重复办结"),"museum")}
    }
    @Test fun `unfinished report handover can be replaced while earlier signatures remain original`()=fixture {
        val rr=pendingReport();actor("new-keeper");val v=reportHandover(rr);val first=joint.handOverReport(req,rr,v,"museum")
        joint.signReportHandover(req,rr,first,"KEEPER",signature,"museum")
        reject {joint.handOverReport(req,rr,v.copy(directorUserId="later-director"),"museum")}
        val second=joint.handOverReport(req,rr,v.copy(previousId=first,directorUserId="later-director"),"museum")
        actor("new-director");reject {joint.signReportHandover(req,rr,first,"DIRECTOR",signature,"museum")}
        actor("new-keeper");joint.signReportHandover(req,rr,second,"KEEPER",signature,"museum")
        actor("later-director");joint.signReportHandover(req,rr,second,"DIRECTOR",signature,"museum")
        val entries=CollectionJointReportHandoverEvidence.entries(CollectionJointSafetyRoundDao[rr])
        assertEquals(2,entries.size);assertEquals(setOf("KEEPER"),entries.first().signatures.keys);assertTrue(entries.last().complete)
    }
    @Test fun `report handover rejects stale source wrong museum future time and absent signed files`()=fixture {
        val rr=pendingReport();actor("new-keeper");val v=reportHandover(rr)
        listOf(v.copy(expectedReportDigest="stale"),v.copy(handedAt=now().plusDays(1)),v.copy(handedAt=at),v.copy(directorUserId="director"),v.copy(handoverFileIds=""),v.copy(keeperUserId=v.directorUserId)).forEach {bad->reject {joint.handOverReport(req,rr,bad,"museum")}}
        reject {joint.handOverReport(req,rr,v,"other")};reject {joint.handOverReport(req,rr,v,"")}
        assertTrue(CollectionJointReportHandoverEvidence.entries(CollectionJointSafetyRoundDao[rr]).isEmpty())
    }
    @Test fun `received affected piece still permits actual pending report transfer without in use custody`()=fixture {
        val rr=pendingReport();receive(true)
        assertEquals(setOf("piece"),CollectionJointSafetyEvidence.receivedIds(RequisitionDao[req]))
        actor("new-keeper");val id=joint.handOverReport(req,rr,reportHandover(rr),"museum");confirmReportHandover(rr,id)
        assertTrue(RequisitionCustodyEvidence.entries(RequisitionDao[req]).isEmpty())
        assertTrue(CollectionJointSafetyEvidence.reportHandled(CollectionJointSafetyRoundDao[rr]))
        assertEquals(EnumCollectionStatus.RETURN_HOLD,CollectionDao["piece"].lifeStatus)
    }
    @Test fun `missing original report or missing prior handover signature cannot clear pending safety`()=fixture {
        val rr=pendingReport();actor("new-keeper");val id=joint.handOverReport(req,rr,reportHandover(rr),"museum");confirmReportHandover(rr,id)
        CollectionPermanentArchiveDao.all().single {it.bizId==rr && it.phase=="REPORT_HANDOVER_KEEPER_$id"}.delete()
        assertTrue(reject {joint.detail(req)}.message!!.contains("顺序"))
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).any {it.kind=="ORIGINAL_ERROR"})
    }
    @Test fun `withdrawn current account during report receipt rolls back new acknowledgment`()=fixture {
        val rr=pendingReport();actor("new-keeper");val id=joint.handOverReport(req,rr,reportHandover(rr),"museum")
        joint.signReportHandover(req,rr,id,"KEEPER",signature,"museum")
        val count=CollectionPermanentArchiveDao.all().count();actor("new-director")
        `when`(binder.resolveSingle(anyString())).thenAnswer {c->io.hizhengfu.cloud.sys.system.dao.UserDao["new-director"].enabled=false;c.getArgument<String>(0)}
        reject {joint.signReportHandover(req,rr,id,"DIRECTOR",signature,"museum")}
        assertEquals(count,CollectionPermanentArchiveDao.all().count());assertFalse(CollectionJointSafetyEvidence.reportHandled(CollectionJointSafetyRoundDao[rr]))
    }
    @Test fun `original current recipient can finish before successor and only one receipt becomes effective`()=fixture {
        val rr=pendingReport();actor("new-keeper");val id=joint.handOverReport(req,rr,reportHandover(rr),"museum")
        joint.signReportHandover(req,rr,id,"KEEPER",signature,"museum")
        actor("director");joint.acknowledge(req,rr,signature,"museum")
        actor("new-director");assertTrue(reject {joint.signReportHandover(req,rr,id,"DIRECTOR",signature,"museum")}.message!!.contains("原接收人"))
        val row=CollectionJointSafetyRoundDao[rr]
        assertEquals("director",row.director!!.actorUserId);assertTrue(CollectionJointSafetyEvidence.reportHandled(row))
        assertFalse(CollectionJointReportHandoverEvidence.entries(row).single().complete)
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).none {it.recordId==rr && it.kind.startsWith("REPORT_HANDOVER_")})
    }
    private fun unreportedRound():String {
        val p=running();val from=now();val cc=inspection(bad=true)
        val rr=joint.recordRound(req,round(p,setOf(cc),from))
        originalReader("new-keeper",CollectionConst.CARE);originalReader("new-director",CollectionConst.CARE)
        return rr
    }
    private fun currentReport(rr:String)=CollectionJointCurrentReportVo(CollectionJointReportHandoverEvidence.roundDigest(CollectionJointSafetyRoundDao[rr]),"proof","原方案负责人已离任，由现任负责人接收首次实际报告",CollectionJointSafetyReportVo(now(),"new-keeper","new-director","本轮实物状况异常首次实际报告","实际保护及后续核查安排","proof"))
    @Test fun `unreported old round can be truthfully reported to current director with separate appointment original`()=fixture {
        val rr=unreportedRound();val v=currentReport(rr).let {it.copy(report=it.report.copy(reporterUserId="operator"))};actor("operator")
        io.hizhengfu.cloud.sys.system.dao.UserDao["director"].enabled=false
        assertTrue(reject {joint.report(req,rr,v.report,"museum")}.message!!.contains("本方案"))
        joint.reportToCurrentDirector(req,rr,v,"museum")
        assertEquals(v.report,CollectionJointSafetyRoundDao[rr].report)
        assertEquals("director",CollectionJointSafetyPlanDao[CollectionJointSafetyRoundDao[rr].planId].content.directorUserId)
        assertFalse(CollectionJointSafetyEvidence.reportHandled(CollectionJointSafetyRoundDao[rr]))
        actor("new-director");joint.acknowledge(req,rr,signature,"museum")
        assertTrue(CollectionJointSafetyEvidence.reportHandled(CollectionJointSafetyRoundDao[rr]))
        assertEquals(v,CollectionJointReportHandoverEvidence.currentBasis(CollectionJointSafetyRoundDao[rr])!!.request)
        assertTrue(CollectionJointSafetyEvidence.checked(CollectionJointSafetyRoundDao[rr]).any(CollectionCareEvidence::unsafe))
    }
    @Test fun `first report to current director cannot replace old report or stale check original`()=fixture {
        val rr=unreportedRound();val v=currentReport(rr);actor("new-keeper")
        listOf(v.copy(expectedRoundDigest="stale"),v.copy(appointmentFileIds=""),v.copy(report=v.report.copy(directorUserId="director")),v.copy(report=v.report.copy(reportedAt=now().plusDays(1)))).forEach {bad->reject {joint.reportToCurrentDirector(req,rr,bad,"museum")}}
        assertNull(CollectionJointSafetyRoundDao[rr].report)
        joint.reportToCurrentDirector(req,rr,v,"museum");assertEquals(rr,joint.reportToCurrentDirector(req,rr,v,"museum"))
        reject {joint.reportToCurrentDirector(req,rr,v.copy(changeReason="不能覆盖"),"museum")}
        reject {joint.reportToCurrentDirector(req,rr,v,"other")}
    }
    @Test fun `first report original and appointment basis roll back together on current access revocation`()=fixture {
        val rr=unreportedRound();val v=currentReport(rr);actor("new-keeper")
        val count=CollectionPermanentArchiveDao.all().count()
        `when`(binder.resolveSingle(anyString())).thenAnswer {c->io.hizhengfu.cloud.sys.system.dao.UserDao["new-keeper"].enabled=false;c.getArgument<String>(0)}
        reject {joint.reportToCurrentDirector(req,rr,v,"museum")}
        CollectionJointSafetyRoundDao[rr].refresh()
        assertEquals(count,CollectionPermanentArchiveDao.all().count());assertNull(CollectionJointSafetyRoundDao[rr].report)
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["files","basis"])
    fun `current report appointment original cannot disappear after receipt`(kind:String)=fixture {
        val rr=unreportedRound();val v=currentReport(rr);actor("new-keeper");joint.reportToCurrentDirector(req,rr,v,"museum")
        actor("new-director");joint.acknowledge(req,rr,signature,"museum")
        val a=CollectionPermanentArchiveDao.all().single {it.bizId==rr && it.phase==CollectionJointReportHandoverEvidence.CURRENT_BASIS}
        if(kind=="basis")a.delete() else CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}
        assertTrue(reject {joint.detail(req)}.message!!.contains("原件"))
    }
    private val recalls=RequisitionRecallService(scope,users,archives)
    private fun recall()=RequisitionRecallVo(
        sourceRevision=joint.detail(req)["sourceRevision"] as String,
        previousId=RequisitionRecallEvidence.entries(RequisitionDao[req]).lastOrNull()?.id,
        collectionIds=setOf("piece"),issuedAt=now(),returnBy=now().plusHours(2),reason="UNSAFE_SITE",
        safetyFindings="实际展柜防护失效",protectiveMeasures="停止使用并安排安全运输",notification="已向提用负责人送达逐件归库通知",fileIds="proof")
    @Test fun `early return uses earliest per piece deadline without changing approved plan`()=fixture {
        running();val r=RequisitionDao[req];val original=r.dossier!!.expectedReturnAt
        val v=recall();actor("keeper");val id=recalls.register(req,v,"museum")
        assertEquals(id,recalls.register(req,v,"museum"))
        val later=recall().copy(returnBy=v.returnBy.plusHours(3));recalls.register(req,later,"museum")
        val tasks=CollectionJointSafetyTaskSource().tasks("museum",v.returnBy).filter {it.kind=="RETURN_DUE"}.associateBy {it.collectionId}
        assertEquals(v.returnBy,tasks.getValue("piece").dueAt)
        assertEquals("OVERDUE",tasks.getValue("piece").status)
        assertEquals(original,tasks.getValue("second").dueAt)
        assertEquals(original,r.dossier!!.expectedReturnAt)
        assertFalse(r.returned)
        assertEquals(2,RequisitionRecallEvidence.entries(r).size)
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["actor","org","future","late","blank","other-piece","revision","reason","before-out"])
    fun `early return rejects invalid identity source dates and evidence`(scenario:String)=fixture {
        running();var v=recall();actor("keeper")
        when(scenario) {
            "actor"->actor("operator")
            "future"->v=v.copy(issuedAt=now().plusHours(1))
            "late"->v=v.copy(returnBy=RequisitionDao[req].dossier!!.expectedReturnAt)
            "blank"->v=v.copy(fileIds="")
            "other-piece"->v=v.copy(collectionIds=setOf("unknown"))
            "revision"->v=v.copy(sourceRevision="stale")
            "reason"->v=v.copy(reason="OTHER")
            "before-out"->v=v.copy(issuedAt=at.minusDays(1))
        }
        val count=CollectionPermanentArchiveDao.all().count()
        reject {recalls.register(req,v,if(scenario=="org")"another" else "museum")}
        assertEquals(count,CollectionPermanentArchiveDao.all().count())
    }
    @Test fun `actual receipt removes recalled item only and prevents a new recall for it`()=fixture {
        running();val v=recall();actor("keeper");recalls.register(req,v,"museum");actor("operator");receive(true)
        val tasks=CollectionJointSafetyTaskSource().tasks("museum",v.returnBy).filter {it.kind=="RETURN_DUE"}
        assertEquals(listOf("second"),tasks.map {it.collectionId})
        actor("keeper");reject {recalls.register(req,recall(),"museum")}
        assertEquals(1,RequisitionRecallEvidence.entries(RequisitionDao[req]).size)
    }
    @Test fun `recall evidence survives completed custody succession and transfers pending duty`()=fixture {
        running();val v=recall();actor("keeper");recalls.register(req,v,"museum");actor("operator")
        val change=custody();actor("new-keeper");val id=joint.registerCustody(req,change,"museum");confirmCustody(id)
        val task=CollectionJointSafetyTaskSource().tasks("museum",v.returnBy).single {it.kind=="RETURN_DUE" && it.collectionId=="piece"}
        assertEquals(v.returnBy,task.dueAt)
        assertEquals(setOf("new-keeper","new-use"),task.assignees.map {it.userId}.toSet())
        actor("keeper");reject {recalls.register(req,recall(),"museum")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["disabled","revoked","museum","replay"])
    fun `recall rejects current access loss even with old login or retry`(scenario:String)=fixture {
        running();val v=recall();actor("keeper")
        if(scenario=="replay")recalls.register(req,v,"museum")
        when(scenario) {
            "disabled","replay"->io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false
            "revoked"->io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "museum"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        }
        val count=CollectionPermanentArchiveDao.all().count()
        reject {recalls.register(req,v,"museum")}
        assertEquals(count,CollectionPermanentArchiveDao.all().count())
    }
    @Test fun `recall rolls back archive if responsible account disabled while binding evidence`()=fixture {
        running();val v=recall();actor("keeper");val count=CollectionPermanentArchiveDao.all().count()
        `when`(binder.resolveSingle(anyString())).thenAnswer {c->io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false;c.getArgument<String>(0)}
        reject {recalls.register(req,v,"museum")}
        assertEquals(count,CollectionPermanentArchiveDao.all().count())
    }

    @Test fun `recall requires actual storage head role and rejects subsequent revocation`()=fixture {
        running();val v=recall();actor("keeper")
        val actual=RequisitionRecallService(scope,CollectionResponsibleUserService(CollectionActorDirectory(scope),scope),archives)
        reject {actual.register(req,v,"museum")}
        val role=actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
        val id=actual.register(req,v,"museum")
        assertEquals(id,RequisitionRecallEvidence.entries(RequisitionDao[req]).single().id)
        io.hizhengfu.cloud.sys.system.dao.RoleDao[role].enabled=false
        reject {actual.register(req,v,"museum")}
    }
    @Test fun `recall cannot be recorded before actual outbound`()=fixture {
        prepared();val v=recall();actor("keeper")
        reject {recalls.register(req,v,"museum")}
        assertTrue(RequisitionRecallEvidence.entries(RequisitionDao[req]).isEmpty())
    }

    @Test fun `missing recall file reference fails closed in detail and safety tasks`()=fixture {
        running();val v=recall();actor("keeper");recalls.register(req,v,"museum")
        val a=CollectionPermanentArchiveDao.all().single {it.phase.startsWith(RequisitionRecallEvidence.PREFIX)}
        CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}
        reject {joint.detail(req)}
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",now()).any {it.kind=="ORIGINAL_ERROR"})
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["before", "due", "overdue", "received", "held"])
    fun `return deadline tasks follow actual outbound and receipt per piece`(scenario:String)=fixture {
        running()
        val r=RequisitionDao[req];val due=r.dossier!!.expectedReturnAt
        if(scenario in setOf("received","held")) receive(bad=scenario=="held")
        val clock=when(scenario) {"before"->due.minusNanos(1_000_000);"due"->due;else->due.plusNanos(1_000_000)}
        val tasks=CollectionJointSafetyTaskSource().tasks("museum",clock).filter {it.kind=="RETURN_DUE"}
        assertEquals(if(scenario in setOf("received","held")) setOf("second") else setOf("piece","second"),tasks.map {it.collectionId}.toSet())
        tasks.forEach {
            assertEquals(due,it.dueAt)
            assertEquals(if(scenario=="before")"SCHEDULED" else "OVERDUE",it.status)
            assertEquals(setOf("keeper","operator"),it.assignees.map {a->a.userId}.toSet())
        }
        assertEquals(due,r.dossier!!.expectedReturnAt)
    }
    @Test fun `return deadline task responsibility changes only after completed actual custody handover`()=fixture {
        running();val r=RequisitionDao[req];val due=r.dossier!!.expectedReturnAt
        fun pending()=CollectionJointSafetyTaskSource().tasks("museum",due.plusDays(1)).filter {it.kind=="RETURN_DUE"}
        val before=pending();assertEquals(2,before.size)
        val v=custody();actor("new-keeper");val id=joint.registerCustody(req,v,"museum")
        assertEquals(before.map {it.basis},pending().map {it.basis})
        assertEquals(setOf("keeper","operator"),pending().flatMap {it.assignees}.map {it.userId}.toSet())
        confirmCustody(id)
        val after=pending();assertEquals(before.map {it.key},after.map {it.key})
        assertNotEquals(before.map {it.basis},after.map {it.basis})
        assertEquals(setOf("new-keeper","new-use"),after.flatMap {it.assignees}.map {it.userId}.toSet())
        assertEquals(due,r.dossier!!.expectedReturnAt)
    }
    @Test fun `unexecuted requisition never creates a return task merely because planned date passed`()=fixture {
        prepared();val due=RequisitionDao[req].dossier!!.expectedReturnAt
        assertTrue(CollectionJointSafetyTaskSource().tasks("museum",due.plusDays(1)).none {it.kind=="RETURN_DUE"})
    }

    @Test fun `joint task source skips actually received pieces but retains unsafe reporting`()=fixture {
        val p=running();val start=now();val ck=inspection(bad=true);val rr=joint.recordRound(req,round(p,setOf(ck),start));confirmRound(rr);receive(true)
        val tasks=CollectionJointSafetyTaskSource().tasks("museum",now().plusDays(2))
        assertEquals(listOf("second"),tasks.filter {it.kind=="JOINT_CHECK_DUE"}.map {it.collectionId})
        assertTrue(tasks.any {it.kind=="DIRECTOR_REPORT" && it.recordId==rr})
        assertFalse(tasks.any {it.kind.startsWith("ROUND_")})
    }
    @Test fun `joint source shows pending real confirmations and preserves source failures`()=fixture {
        assess();val p=joint.savePlan(req,plan())
        assertEquals(setOf("PLAN_KEEPER","PLAN_USE","PLAN_SECURITY"),CollectionJointSafetyTaskSource().tasks("museum",now()).map {it.kind}.toSet())
        actor("keeper");joint.signPlan(req,p,"KEEPER",signature)
        assertFalse(CollectionJointSafetyTaskSource().tasks("museum",now()).any {it.kind=="PLAN_KEEPER"})
        CollectionJointSafetyPlanDao[p].keeper=null
        val broken=CollectionJointSafetyTaskSource().tasks("museum",now()).single()
        assertEquals("ORIGINAL_ERROR",broken.status);assertTrue(broken.assignees.isEmpty())
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
    class ReportTransactions
    private fun reportRuntime(db:Database,body:(CollectionJointSafetyService,org.springframework.transaction.support.TransactionTemplate)->Unit) {
        val manager=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(fixtureDataSource ?: org.sqlite.SQLiteDataSource().apply {url=db.url})
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.register(ReportTransactions::class.java)
            val actualUsers=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
            c.registerBean(CollectionJointSafetyService::class.java,java.util.function.Supplier {CollectionJointSafetyService(scope,actualUsers,archives)})
            c.refresh();SpringContextHolder().setApplicationContext(c)
            body(c.getBean(CollectionJointSafetyService::class.java),org.springframework.transaction.support.TransactionTemplate(manager))
        }
    }
    companion object {
        @JvmStatic fun historicalParticipantCases()=listOf("KEEPER","USE","SECURITY").flatMap {role->
            listOf("keeper-disabled","director-role","other-disabled").map {fault->org.junit.jupiter.params.provider.Arguments.of(role,fault)}
        }
        @JvmStatic fun historicalSignerLossCases()=listOf("KEEPER","USE","SECURITY").flatMap {role->
            listOf("disabled","departed","duty","care","proxy","wrong-org").map {fault->org.junit.jupiter.params.provider.Arguments.of(role,fault)}
        }
        @JvmStatic fun planRoundFinalFaults()=listOf("PLAN","PLAN_KEEPER","PLAN_USE","PLAN_SECURITY","ROUND","ROUND_KEEPER","ROUND_USE","ROUND_SECURITY").flatMap {stage ->
            val faults=listOf("new-deleted","new-content","old-file","new-file","museum-disabled","actor-disabled")+
                (if('_' in stage)listOf("record-deleted","record-org") else emptyList())+
                (if(stage!="ROUND")listOf("keeper-role") else emptyList())
            faults.filterNot {it=="old-file" && stage.startsWith("PLAN")}.map {org.junit.jupiter.params.provider.Arguments.of(stage,it)}
        }

        @JvmStatic fun custodyFinalFaults()=listOf("REGISTER","KEEPER","USE","DIRECTOR").flatMap {stage ->
            listOf("request-org","new-deleted","new-content","old-file","new-file","check-org","piece-org","museum-disabled","actor-disabled","use-department","director-role","return-deleted","return-scope").map {org.junit.jupiter.params.provider.Arguments.of(stage,it)}
        }

        @JvmStatic fun currentReportFinalFaults()=listOf("BASIS","REPORT","ACKNOWLEDGE").flatMap {stage ->
            listOf("round-checks","round-deleted","new-deleted","new-content","old-file","new-file","request-org","museum-disabled","director-role","reporter-disabled").map {org.junit.jupiter.params.provider.Arguments.of(stage,it)}
        }
        @JvmStatic fun reportFinalFaults()=listOf("REGISTER","KEEPER","DIRECTOR").flatMap {stage ->
            listOf("round-report","round-deleted","new-deleted","new-content","old-file","new-file","request-org","museum-disabled","actor-disabled","actor-role","director-disabled","keeper-role").map {org.junit.jupiter.params.provider.Arguments.of(stage,it)}
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("reportFinalFaults")
    fun `report handover checks actual written source and signature in every stage`(stage:String,fault:String) {
        lateinit var rr:String;lateinit var value:CollectionJointReportHandoverVo
        fixture(afterCommit={db -> reportRuntime(db) {h,tx ->
            fun <T> read(block:()->T):T=tx.execute {block()}
            fun state()=read {listOf(Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,CollectionCareChecks,Collections,CollectionOrgs,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers).map {t ->
                t.selectAll().map {row->t.columns.map {row[it]}.toString()}.sorted()
            }}
            actor("new-keeper")
            val recordId=if(stage=="REGISTER")null else h.handOverReport(req,rr,value,"museum")
            if(stage=="DIRECTOR") {h.signReportHandover(req,rr,recordId!!,"KEEPER",signature,"museum");actor("new-director")}
            val before=state();val phase=if(stage=="REGISTER")"REPORT_HANDOVER_P_%" else "REPORT_HANDOVER_${stage}_%"
            read {
                val sql=when(fault) {
                    "round-report"->"UPDATE ${CollectionJointSafetyRounds.tableName} SET report=NULL WHERE id='$rr'"
                    "round-deleted"->"DELETE FROM ${CollectionJointSafetyRounds.tableName} WHERE id='$rr'"
                    "new-deleted"->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                    "new-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                    "old-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET metadata='{}' WHERE archive_id IN (SELECT id FROM ${CollectionPermanentArchives.tableName} WHERE biz_id='$rr' AND phase='DIRECTOR_REPORT')"
                    "new-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET org_id='other' WHERE id=NEW.id"
                    "request-org"->"UPDATE ${Requisitions.tableName} SET org_id='other' WHERE id='$req'"
                    "actor-disabled"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='${if(stage=="DIRECTOR")"new-director" else "new-keeper"}'"
                    "actor-role"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='${if(stage=="DIRECTOR")"new-director" else "new-keeper"}' AND role_id='role:${if(stage=="DIRECTOR")CollectionConst.ROLE_DIRECTOR else CollectionConst.ROLE_STORAGE_HEAD}'"
                    "director-disabled"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='new-director'"
                    "keeper-role"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='new-keeper' AND role_id='role:${CollectionConst.ROLE_STORAGE_HEAD}'"
                    else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                }
                val event=if(fault=="new-file")"AFTER INSERT ON ${CollectionPermanentArchiveFiles.tableName} WHEN EXISTS (SELECT 1 FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.archive_id AND biz_id='$rr' AND phase LIKE '$phase')"
                    else "AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_id='$rr' AND NEW.phase LIKE '$phase'"
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER joint_report_final_fault $event BEGIN $sql; END")
            }
            fun save()=if(stage=="REGISTER")h.handOverReport(req,rr,value,"museum") else h.signReportHandover(req,rr,recordId!!,stage,signature,"museum")
            assertThrows(BadRequestException::class.java){save()};assertEquals(before,state())
            read {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP TRIGGER joint_report_final_fault")}
            val id=save();assertEquals(id,save())
            read {assertEquals("director",CollectionJointSafetyRoundDao[rr].report!!.directorUserId);assertNull(CollectionJointSafetyRoundDao[rr].director);assertTrue(CollectionJointSafetyEvidence.unsafe(CollectionJointSafetyRoundDao[rr]));assertEquals(stage=="DIRECTOR",CollectionJointSafetyEvidence.reportHandled(CollectionJointSafetyRoundDao[rr]))}
        }}) {
            rr=pendingReport();value=reportHandover(rr)
            actorRole("new-keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("new-director",CollectionConst.ROLE_DIRECTOR)
            io.hizhengfu.cloud.sys.system.dao.UserDao["director"].enabled=false
        }
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("currentReportFinalFaults")
    fun `current report and actual acknowledgment verify both originals and persisted source`(stage:String,fault:String) {
        lateinit var rr:String;lateinit var value:CollectionJointCurrentReportVo
        fixture(afterCommit={db -> reportRuntime(db) {h,tx ->
            fun <T> read(block:()->T):T=tx.execute {block()}
            fun state()=read {listOf(Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,CollectionCareChecks,Collections,CollectionOrgs,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers).map {t ->
                t.selectAll().map {row->t.columns.map {row[it]}.toString()}.sorted()
            }}
            actor("new-keeper")
            if(stage=="ACKNOWLEDGE") {h.reportToCurrentDirector(req,rr,value,"museum");actor("new-director")}
            val before=state()
            val phase=when(stage){"BASIS"->CollectionJointReportHandoverEvidence.CURRENT_BASIS;"REPORT","ORIGINAL"->"DIRECTOR_REPORT";else->"DIRECTOR"}
            read {
                val sql=when(fault) {
                    "round-checks"->"UPDATE ${CollectionJointSafetyRounds.tableName} SET checks_snapshot='changed' WHERE id='$rr'"
                    "round-deleted"->"DELETE FROM ${CollectionJointSafetyRounds.tableName} WHERE id='$rr'"
                    "new-deleted"->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                    "new-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                    "old-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET metadata='{}' WHERE archive_id IN (SELECT id FROM ${CollectionPermanentArchives.tableName} WHERE biz_id='$rr' AND phase='ROUND')"
                    "new-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET org_id='other' WHERE id=NEW.id"
                    "request-org"->"UPDATE ${Requisitions.tableName} SET org_id='other' WHERE id='$req'"
                    "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                    "director-role"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='${if(stage=="ORIGINAL")"director" else "new-director"}' AND role_id='role:${CollectionConst.ROLE_DIRECTOR}'"
                    else->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='${if(stage=="ACKNOWLEDGE")"new-director" else "new-keeper"}'"
                }
                val event=if(fault=="new-file")"AFTER INSERT ON ${CollectionPermanentArchiveFiles.tableName} WHEN NEW.biz_id='$rr' AND NEW.file_id='${when(stage){"BASIS"->"basis-proof";"REPORT","ORIGINAL"->"report-proof";else->"proof"}}'"
                    else "AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_id='$rr' AND NEW.phase='$phase'"
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER current_report_final_fault $event BEGIN $sql; END")
            }
            fun save()=when(stage){"ACKNOWLEDGE"->h.acknowledge(req,rr,signature,"museum");"ORIGINAL"->h.report(req,rr,value.report,"museum");else->h.reportToCurrentDirector(req,rr,value,"museum")}
            assertThrows(BadRequestException::class.java){save()};assertEquals(before,state())
            read {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP TRIGGER current_report_final_fault")}
            assertEquals(rr,save());assertEquals(rr,save())
            read {
                val row=CollectionJointSafetyRoundDao[rr]
                if(stage=="ORIGINAL")assertNull(CollectionJointReportHandoverEvidence.currentBasis(row)) else assertEquals(value,CollectionJointReportHandoverEvidence.currentBasis(row)!!.request)
                assertEquals(value.report,row.report);assertEquals(stage=="ACKNOWLEDGE",CollectionJointSafetyEvidence.reportHandled(row))
                assertEquals("director",CollectionJointSafetyPlanDao[row.planId].content.directorUserId)
                assertTrue(CollectionJointSafetyEvidence.unsafe(row))
                assertEquals(stage=="ORIGINAL",io.hizhengfu.cloud.sys.system.dao.UserDao["director"].enabled)
            }
        }}) {
            rr=unreportedRound()
            listOf("basis-proof","report-proof").forEach {id->FileDao.new(id){name="$id.pdf";path=id;hash=id;size=100;ext="pdf"}}
            value=currentReport(rr).let {it.copy(appointmentFileIds="basis-proof",report=it.report.copy(signedFileIds="report-proof",directorUserId=if(stage=="ORIGINAL")"director" else "new-director"))}
            actorRole(if(stage=="ORIGINAL")"director" else "new-director",CollectionConst.ROLE_DIRECTOR)
            io.hizhengfu.cloud.sys.system.dao.UserDao["director"].enabled=stage=="ORIGINAL"
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["round-checks","round-deleted","new-deleted","new-content","old-file","new-file","request-org","museum-disabled","director-role","reporter-disabled"])
    fun `original report writes verify preserved source and current participants`(fault:String) {
        `current report and actual acknowledgment verify both originals and persisted source`("ORIGINAL",fault)
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("custodyFinalFaults")
    fun `in use custody writes verify actual archives source pieces and participants`(stage:String,fault:String) {
        lateinit var value:RequisitionCustodyRequest
        fixture(afterCommit={db -> reportRuntime(db) {h,tx ->
            fun <T> read(block:()->T):T=tx.execute {block()}
            fun state()=read {listOf(Requisitions,CollectionReturns,ReturnComplianceRecords,CollectionCareChecks,Collections,CollectionOrgs,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers,io.hizhengfu.cloud.sys.system.dao.UserGroups).map {t ->
                t.selectAll().map {row->t.columns.map {row[it]}.toString()}.sorted()
            }}
            actor("new-keeper")
            val recordId=if(stage=="REGISTER")null else h.registerCustody(req,value,"museum")
            if(stage=="DIRECTOR") {
                h.signCustody(req,recordId!!,"KEEPER",signature,"museum");actor("new-use");h.signCustody(req,recordId,"USE",signature,"museum")
            }
            val actorId=when(stage){"USE"->"new-use";"DIRECTOR"->"new-director";else->"new-keeper"};actor(actorId)
            val before=state();val phase=if(stage=="REGISTER")"CUSTODY_P_%" else "CUSTODY_${stage}_%"
            read {
                if(fault.startsWith("return-"))assertTrue(CollectionReturnDao.all().any {it.sourceBizId==req && it.sourceBizType==CollectionConst.BIZ_REQUISITION})
                val sql=when(fault) {
                    "request-org"->"UPDATE ${Requisitions.tableName} SET org_id='other' WHERE id='$req'"
                    "new-deleted"->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                    "new-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                    "old-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET metadata='{}' WHERE archive_id IN (SELECT id FROM ${CollectionPermanentArchives.tableName} WHERE biz_id='$req' AND phase='OUT_EXECUTED')"
                    "new-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET org_id='other' WHERE id=NEW.id"
                    "check-org"->"UPDATE ${CollectionCareChecks.tableName} SET org_id='other' WHERE id='${value.checkIds.first()}'"
                    "piece-org"->"UPDATE ${Collections.tableName} SET org_id='other' WHERE id='piece'"
                    "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                    "actor-disabled"->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='$actorId'"
                    "return-deleted"->"DELETE FROM ${CollectionReturns.tableName} WHERE source_biz_type='${CollectionConst.BIZ_REQUISITION}' AND source_biz_id='$req'"
                    "return-scope"->"UPDATE ${CollectionReturns.tableName} SET collection_ids='piece' WHERE source_biz_type='${CollectionConst.BIZ_REQUISITION}' AND source_biz_id='$req'"
                    "use-department"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.UserGroups.tableName} WHERE user_id='new-use' AND group_id='dept'"
                    else->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='new-director' AND role_id='role:${CollectionConst.ROLE_DIRECTOR}'"
                }
                val event=if(fault=="new-file")"AFTER INSERT ON ${CollectionPermanentArchiveFiles.tableName} WHEN NEW.biz_id='$req' AND NEW.file_id='custody-proof'"
                    else "AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_id='$req' AND NEW.phase LIKE '$phase'"
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER custody_final_fault $event BEGIN $sql; END")
            }
            fun save()=if(stage=="REGISTER")h.registerCustody(req,value,"museum") else h.signCustody(req,recordId!!,stage,signature.copy(fileIds="custody-proof"),"museum")
            assertThrows(BadRequestException::class.java){save()};assertEquals(before,state())
            read {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP TRIGGER custody_final_fault")}
            val id=save();assertEquals(id,save())
            read {
                val r=RequisitionDao[req];val e=RequisitionCustodyEvidence.entries(r).single()
                assertEquals(stage=="DIRECTOR",e.complete);assertTrue(r.outExecuted);assertFalse(r.returned)
                assertEquals("keeper",r.dossier!!.keeperUserId);assertEquals("operator",r.dossier!!.useResponsibleUserId)
                assertFalse(io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled!!)
            }
        }}) {
            running();value=custody().copy(appointmentFileIds="custody-proof",handoverFileIds="custody-proof")
            FileDao.new("custody-proof"){name="承接原件.pdf";path="custody-proof";hash="custody-proof";size=100;ext="pdf"}
            actorRole("new-keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("new-director",CollectionConst.ROLE_DIRECTOR)
            io.hizhengfu.cloud.sys.system.dao.GroupDao.new("dept"){name="提用部门";type='1';parentId="museum";parentIds="0,museum,"}
            io.hizhengfu.cloud.sys.system.dao.UserGroups.insert {it[userId]="new-use";it[groupId]="dept"}
            io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false
            io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
        }
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("planRoundFinalFaults")
    fun `joint plans rounds and all participant signatures verify persisted records`(stage:String,fault:String) {
        lateinit var pv:CollectionJointSafetyPlanVo;var rv:CollectionJointSafetyRoundVo?=null;var planId:String?=null
        fixture(afterCommit={db -> reportRuntime(db) {h,tx ->
            fun <T> read(block:()->T):T=tx.execute {block()}
            fun state()=read {listOf(Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,CollectionCareChecks,Collections,CollectionOrgs,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,io.hizhengfu.cloud.sys.system.dao.Users,io.hizhengfu.cloud.sys.system.dao.RoleUsers).map {t ->
                t.selectAll().map {row->t.columns.map {row[it]}.toString()}.sorted()
            }}
            val isRound=stage.startsWith("ROUND");val role=stage.substringAfter('_',"")
            actor("operator")
            if(!isRound && stage!="PLAN")planId=h.savePlan(req,pv,"museum")
            val roundId=if(isRound && stage!="ROUND")h.recordRound(req,rv!!,"museum") else null
            val actorId=when(role){"KEEPER"->"keeper";"SECURITY"->"security";else->"operator"};actor(actorId)
            val before=state();val phase=role.ifEmpty {stage}
            read {
                if(fault=="old-file") {
                    val originals=CollectionPermanentArchiveDao.all().filter {it.bizId==req}.map {it.id.value}.toSet()
                    assertTrue(CollectionPermanentArchiveFileDao.all().any {it.archiveId in originals})
                }
                val table=if(isRound)CollectionJointSafetyRounds.tableName else CollectionJointSafetyPlans.tableName
                val sql=when(fault) {
                    "record-deleted"->"DELETE FROM $table WHERE id=NEW.biz_id"
                    "record-org"->"UPDATE $table SET org_id='other' WHERE id=NEW.biz_id"
                    "new-deleted"->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                    "new-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                    "old-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET metadata='{}' WHERE archive_id IN (SELECT id FROM ${CollectionPermanentArchives.tableName} WHERE biz_id='$req')"
                    "new-file"->"UPDATE ${CollectionPermanentArchiveFiles.tableName} SET org_id='other' WHERE id=NEW.id"
                    "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                    "keeper-role"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='keeper' AND role_id='role:${CollectionConst.ROLE_STORAGE_HEAD}'"
                    else->"UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='$actorId'"
                }
                val event=if(fault=="new-file")"AFTER INSERT ON ${CollectionPermanentArchiveFiles.tableName} WHEN NEW.file_id='joint-proof'"
                    else "AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${CollectionJointSafetyEvidence.BIZ}' AND NEW.phase='$phase'"
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER joint_plan_round_final_fault $event BEGIN $sql; END")
            }
            fun save()=when(stage) {
                "PLAN"->h.savePlan(req,pv,"museum")
                "ROUND"->h.recordRound(req,rv!!,"museum")
                else->if(isRound)h.signRound(req,roundId!!,role,signature.copy(fileIds="joint-proof"),"museum") else h.signPlan(req,planId!!,role,signature.copy(fileIds="joint-proof"),"museum")
            }
            assertThrows(BadRequestException::class.java){save()};assertEquals(before,state())
            read {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP TRIGGER joint_plan_round_final_fault")}
            val id=save();assertEquals(id,save())
            read {
                val r=RequisitionDao[req];assertEquals("keeper",r.dossier!!.keeperUserId);assertEquals("operator",r.dossier!!.useResponsibleUserId)
                if(isRound)CollectionJointSafetyEvidence.round(CollectionJointSafetyRoundDao[id]) else CollectionJointSafetyEvidence.plan(CollectionJointSafetyPlanDao[id])
            }
        }}) {
            if(stage.startsWith("ROUND")) {
                planId=running();val start=now();val ck=inspection();rv=round(planId!!,setOf(ck),start).copy(signedFileIds="joint-proof")
            } else {assess();pv=plan().copy(planFileIds="joint-proof")}
            FileDao.new("joint-proof"){name="本人签署.pdf";path="joint-proof";hash="joint-proof";size=100;ext="pdf"}
            actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("security",CollectionConst.ROLE_SECURITY);actorRole("director",CollectionConst.ROLE_DIRECTOR)
            io.hizhengfu.cloud.sys.system.dao.GroupDao.new("dept"){name="提用部门";type='1';parentId="museum";parentIds="0,museum,"}
            io.hizhengfu.cloud.sys.system.dao.UserGroups.insert {it[userId]="operator";it[groupId]="dept"}
        }
    }

    @Test fun `historical round registration preserves departed planner without granting a new signature`() {
        lateinit var value:CollectionJointSafetyRoundVo
        fixture(afterCommit={db -> reportRuntime(db) {h,tx ->
            actor("operator");val id=h.recordRound(req,value,"museum")
            tx.execute {
                val row=CollectionJointSafetyRoundDao[id]
                assertEquals("security",CollectionJointSafetyPlanDao[row.planId].content.securityUserId)
                assertNull(row.security)
                assertFalse(io.hizhengfu.cloud.sys.system.dao.UserDao["security"].enabled!!)
            }
            actor("security");assertThrows(BadRequestException::class.java){h.signRound(req,id,"SECURITY",signature,"museum")}
        }}) {
            val p=running();val started=now();val check=inspection();value=round(p,setOf(check),started)
            io.hizhengfu.cloud.sys.system.dao.UserDao["security"].enabled=false
        }
    }

    private fun pendingHistoricalParticipantRound():String {
        val p=running();val started=now();val check=inspection()
        val roundId=joint.recordRound(req,round(p,setOf(check),started))
        val v=custody();actor("new-keeper");val change=joint.registerCustody(req,v,"museum");confirmCustody(change)
        val current=joint.savePlan(req,plan().copy(previousPlanId=p,keeperUserId="new-keeper",useUserId="new-use",directorUserId="new-director"))
        listOf("KEEPER" to "new-keeper","USE" to "new-use","SECURITY" to "security").forEach {(role,user)->actor(user);joint.signPlan(req,current,role,signature,"museum")}
        actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("security",CollectionConst.ROLE_SECURITY);actorRole("director",CollectionConst.ROLE_DIRECTOR)
        actorRole("new-keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("new-director",CollectionConst.ROLE_DIRECTOR)
        io.hizhengfu.cloud.sys.system.dao.GroupDao.new("dept"){name="提用部门";type='1';parentId="museum";parentIds="0,museum,"}
        listOf("operator","new-use").forEach {user->UserGroups.insert {it[userId]=user;it[groupId]="dept"}}
        return roundId
    }

    private fun historicalParticipantState()=listOf(Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,
        CollectionCareChecks,Collections,CollectionPermanentArchives,CollectionPermanentArchiveFiles,StorageMovements).map {table->
        table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("historicalParticipantCases")
    fun `historical pending round permits current participant after another former officer leaves`(role:String,fault:String) {
        lateinit var roundId:String
        fixture(afterCommit={database->reportRuntime(database) {h,tx->
            val participant=when(role){"KEEPER"->"keeper";"USE"->"operator";else->"security"}
            val departed=when(fault){"keeper-disabled"->if(role=="KEEPER")"operator" else "keeper";"director-role"->"director";else->if(role=="SECURITY")"operator" else "security"}
            val before=tx.execute {historicalParticipantState()}
            val old=tx.execute {CollectionJointSafetyRoundDao[roundId].let {listOf(it.content,it.checksSnapshot,it.planId)}}
            actor(participant,CollectionConst.CARE)
            assertEquals(roundId,h.signRound(req,roundId,role,signature,"museum"))
            val signed=tx.execute {
                val row=CollectionJointSafetyRoundDao[roundId]
                assertEquals(old,listOf(row.content,row.checksSnapshot,row.planId))
                val actual=when(role){"KEEPER"->row.keeper;"USE"->row.use;else->row.security}
                assertEquals(participant,actual!!.actorUserId)
                assertFalse(CollectionJointSafetyEvidence.confirmed(CollectionJointSafetyPlanDao[row.planId],row))
                assertEquals(1,CollectionPermanentArchiveDao.all().count {it.bizId==roundId && it.phase==role})
                assertEquals(if(fault=="director-role")true else false,io.hizhengfu.cloud.sys.system.dao.UserDao[departed].enabled)
                historicalParticipantState()
            }
            assertNotEquals(before,signed)
            assertEquals(roundId,h.signRound(req,roundId,role,signature,"museum"))
            assertEquals(signed,tx.execute {historicalParticipantState()})
        }}) {
            roundId=pendingHistoricalParticipantRound()
            when(fault) {
                "keeper-disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao[if(role=="KEEPER")"operator" else "keeper"].enabled=false
                "director-role"->RoleUsers.deleteWhere {(RoleUsers.userId eq "director") and (RoleUsers.roleId eq "role:${CollectionConst.ROLE_DIRECTOR}")}
                else->io.hizhengfu.cloud.sys.system.dao.UserDao[if(role=="SECURITY")"operator" else "security"].enabled=false
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("historicalSignerLossCases")
    fun `historical pending round still rejects invalid actual signer and proxy`(role:String,fault:String) {
        lateinit var roundId:String
        fixture(afterCommit={database->reportRuntime(database) {h,tx->
            val participant=when(role){"KEEPER"->"keeper";"USE"->"operator";else->"security"}
            actor(if(fault=="proxy")"new-use" else participant,CollectionConst.CARE)
            val before=tx.execute {historicalParticipantState()}
            assertThrows(BadRequestException::class.java) {h.signRound(req,roundId,role,signature,if(fault=="wrong-org")"other" else "museum")}
            assertEquals(before,tx.execute {historicalParticipantState()})
        }}) {
            roundId=pendingHistoricalParticipantRound();val participant=when(role){"KEEPER"->"keeper";"USE"->"operator";else->"security"}
            when(fault) {
                "disabled"->io.hizhengfu.cloud.sys.system.dao.UserDao[participant].enabled=false
                "departed"->{io.hizhengfu.cloud.sys.system.dao.UserDao[participant].groupId="other";UserGroups.deleteWhere {UserGroups.userId eq participant}}
                "duty"->if(role=="USE")UserGroups.deleteWhere {(UserGroups.userId eq participant) and (UserGroups.groupId eq "dept")} else RoleUsers.deleteWhere {(RoleUsers.userId eq participant) and (RoleUsers.roleId eq "role:${if(role=="KEEPER")CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_SECURITY}")}
                "care"->RoleUsers.deleteWhere {RoleUsers.userId eq participant}
            }
        }
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["KEEPER","USE","SECURITY"])
    fun `historical pending round rejects signer duty revoked during original preparation`(role:String) {
        lateinit var roundId:String
        fixture(afterCommit={database->
            val participant=when(role){"KEEPER"->"keeper";"USE"->"operator";else->"security"}
            val duty=when(role){"KEEPER"->CollectionConst.ROLE_STORAGE_HEAD;"USE"->"USE_DEPARTMENT_MEMBER";else->CollectionConst.ROLE_SECURITY}
            actor(participant,CollectionConst.CARE)
            verifyConcurrentDutyRevocation(database,"旧轮本人补签-$role",participant,duty) {h->h.signRound(req,roundId,role,signature,"museum")}
        }) {roundId=pendingHistoricalParticipantRound()}
    }

    @Test fun `report write comparison uses persisted millisecond time precision`()=fixture {
        val rr=unreportedRound();val value=currentReport(rr).let {it.copy(report=it.report.copy(reportedAt=it.report.reportedAt.plusNanos(123456)))}
        actor("new-keeper")
        travel(value.report.reportedAt.plusSeconds(1)) {
            assertEquals(rr,joint.reportToCurrentDirector(req,rr,value,"museum"))
            CollectionJointSafetyRoundDao[rr].refresh()
            assertEquals(value.report.reportedAt.truncatedTo(ChronoUnit.MILLIS),CollectionJointSafetyRoundDao[rr].report!!.reportedAt)
            CollectionJointSafetyEvidence.round(CollectionJointSafetyRoundDao[rr])
        }
    }

    private fun verifyConcurrentDutyRevocation(db:Database,stage:String,revoked:String,role:String,write:(CollectionJointSafetyService)->String) {
        val current=CollectionJointSafetyService(scope,CollectionResponsibleUserService(CollectionActorDirectory(scope),scope),archives)
        val tables=listOf(Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,CollectionCareChecks,Collections,
            CollectionPermanentArchives,CollectionPermanentArchiveFiles)
        fun state()=transaction(db) {tables.map {table->table.selectAll().map {row->table.columns.map {row[it]}.toString()}.sorted()}}
        val before=state()
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        `when`(binder.resolveSingle("proof")).thenAnswer {call->
            entered.countDown()
            check(release.await(15,TimeUnit.SECONDS)) { "$stage 原件准备等待超时" }
            call.getArgument<String>(0)
        }
        val authentication=SecurityContextHolder.getContext().authentication
        val executor=Executors.newSingleThreadExecutor()
        val writing=executor.submit<Throwable?> {
            SecurityContextHolder.getContext().authentication=authentication
            try {transaction(db) {write(current)};null}
            catch(failure:Throwable) {failure}
            finally {SecurityContextHolder.clearContext()}
        }
        try {
            assertTrue(entered.await(15,TimeUnit.SECONDS),"$stage 未到达原件准备阶段")
            transaction(db) {
                val deleted=if(role=="USE_DEPARTMENT_MEMBER")UserGroups.deleteWhere {
                    (UserGroups.userId eq revoked) and (UserGroups.groupId eq "dept")
                } else RoleUsers.deleteWhere {
                    (RoleUsers.userId eq revoked) and (RoleUsers.roleId eq "role:$role")
                }
                assertEquals(1,deleted)
            }
            release.countDown()
            val failure=writing.get(15,TimeUnit.SECONDS)
            assertTrue(failure is BadRequestException,"$stage 的 $revoked 岗位撤销后须拒绝，实际为 $failure")
            assertEquals(before,state(),"$stage 的业务状态和永久原件须整体回滚")
            transaction(db) {
                val remaining=if(role=="USE_DEPARTMENT_MEMBER")UserGroups.selectAll().where {
                    (UserGroups.userId eq revoked) and (UserGroups.groupId eq "dept")
                }.count() else RoleUsers.selectAll().where {
                    (RoleUsers.userId eq revoked) and (RoleUsers.roleId eq "role:$role")
                }.count()
                assertEquals(0,remaining)
            }
            transaction(db) {
                if(role=="USE_DEPARTMENT_MEMBER")UserGroups.insert {it[userId]=revoked;it[groupId]="dept"}
                else actorRole(revoked,role)
            }
            transaction(db) {
                val id=write(current)
                assertTrue(id.isNotBlank(),"$stage 恢复职责后应可实际办理")
                assertEquals(id,write(current),"$stage 相同原件重复办理应幂等")
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS))
        }
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["new-keeper","new-use","new-director"])
    fun `in use custody rejects participant duty revoked during original preparation`(revoked:String) {
        lateinit var value:RequisitionCustodyRequest
        fixture(afterCommit={database->
            val role=when(revoked) {
                "new-keeper"->CollectionConst.ROLE_STORAGE_HEAD
                "new-use"->"USE_DEPARTMENT_MEMBER"
                else->CollectionConst.ROLE_DIRECTOR
            }
            verifyConcurrentDutyRevocation(database,"在用承接",revoked,role) {current->current.registerCustody(req,value,"museum")}
        }) {
            running();value=custody()
            actorRole("new-keeper",CollectionConst.ROLE_STORAGE_HEAD)
            actorRole("new-director",CollectionConst.ROLE_DIRECTOR)
            io.hizhengfu.cloud.sys.system.dao.GroupDao.new("dept"){name="提用部门";type='1';parentId="museum";parentIds="0,museum,"}
            io.hizhengfu.cloud.sys.system.dao.UserGroups.insert {it[userId]="new-use";it[groupId]="dept"}
            actor("new-keeper")
        }
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["new-keeper","new-director"])
    fun `report handover rejects signer duty revoked during original preparation`(revoked:String) {
        lateinit var roundId:String;lateinit var value:CollectionJointReportHandoverVo
        fixture(afterCommit={database->
            val role=if(revoked=="new-keeper")CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR
            verifyConcurrentDutyRevocation(database,"报告移交",revoked,role) {current->current.handOverReport(req,roundId,value,"museum")}
        }) {
            roundId=pendingReport();value=reportHandover(roundId)
            actorRole("new-keeper",CollectionConst.ROLE_STORAGE_HEAD)
            actorRole("new-director",CollectionConst.ROLE_DIRECTOR)
            actor("new-keeper")
        }
    }

    @Test fun `joint detail exposes frozen files only for its visible record phases`()=fixture {
        val plan=running();val from=now();val check=inspection(bad=true)
        val round=joint.recordRound(req,round(plan,setOf(check),from));confirmRound(round)
        joint.report(req,round,CollectionJointSafetyReportVo(now(),"operator","director","据实报告","保持防护","proof"))
        actor("keeper");val recall=recalls.register(req,recall(),"museum")
        actor("security",CollectionConst.CARE)
        FileDao["proof"].name="当前通用文件名称，不应覆盖冻结名称.pdf"
        val before=settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        @Suppress("UNCHECKED_CAST")
        val frozen=joint.detail(req)["archiveFiles"] as Map<String,Map<String,List<Map<String,Any?>>>>
        listOf(plan to "PLAN",plan to "KEEPER",plan to "USE",plan to "SECURITY",
            round to "ROUND",round to "SECURITY",round to "DIRECTOR_REPORT",check to "CHECKED",
            req to "${RequisitionRecallEvidence.PREFIX}$recall").forEach {(record,phase)->
            val file=frozen.getValue(record).getValue(phase).single()
            assertEquals("proof",file["fileId"])
            val saved=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase==phase}
            assertEquals(CollectionPermanentArchiveFileDao.all().single {it.archiveId==saved.id.value}.metadata,file["metadata"])
            assertTrue(file["metadata"].toString().contains("实际评估签署.pdf"))
            assertFalse(file["metadata"].toString().contains("当前通用文件名称"))
        }
        assertFalse(frozen.getValue(plan).containsKey("ROUND"));assertFalse(frozen.getValue(req).containsKey("SUBMITTED"))
        assertEquals(before,settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files))
        reject {CollectionPermanentArchiveDao.all().single {it.bizId==plan && it.phase=="PLAN"}.orgId="other";joint.detail(req)}
        reject {io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();joint.detail(req)}
    }

}
