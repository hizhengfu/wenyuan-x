package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.EnumSource as NatureSource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.math.BigDecimal
import java.time.LocalDateTime

class CollectionSamplingPersistenceTest {
    private val at=LocalDateTime.of(2026,9,1,10,0)
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c->c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val operationOriginalRoot=createSafetyOriginalRoot()
    private val operationArchives=CollectionPermanentArchiveService(binder,scope,
        io.hizhengfu.cloud.common.file.LocalFileObjectStorage(operationOriginalRoot.toString()),unprotectedArchiveReadAccessProvider())
    private val originals=mock(CollectionOriginalAccessService::class.java)
    private val incidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService(),listOf(IncidentWorkGuard(operationArchives),IncidentRelocationGuard(operationArchives)))
    private val locks=CollectionOperationLockService(listOf(CollectionIncidentOperationGuard(),IncidentWorkGuard(operationArchives),IncidentRelocationGuard(operationArchives)))
    private val flow=mock(FlowInstanceService::class.java)
    private val context=StaticApplicationContext()
    private val directory=mock(CollectionActorDirectory::class.java)
    private val service=CollectionSamplingService(flow,scope,users,archives,originals,locks,incidents,directory)
    private val requests=mutableListOf<StartFlowRequest>()
    @BeforeEach fun setup() {
        `when`(directory.requireActive(anyString())).thenAnswer {io.hizhengfu.cloud.common.utils.SecurityUtils.getUserDetail()}
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val user=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        `when`(flow.startBusiness(any() ?: StartFlowRequest("unused","unused"),anyString(),anyString(), any<(FlowInstanceDao) -> Unit>() ?: {})).thenAnswer { c->
            val req=c.getArgument<StartFlowRequest>(0);requests+=req
            FlowInstanceDao.new {definitionId="definition";definitionKey=req.definitionKey;definitionName="共同方案";title=req.title;initiator="operator";initiatorName="经办"}
                .also { c.getArgument<(FlowInstanceDao) -> Unit>(3)(it) }
        }
    }
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();context.close();removeSafetyOriginalRoot(operationOriginalRoot)}
    private fun fixture(block:()->Unit) {
        val db=Database.connect("jdbc:sqlite:file:sampling-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) { seed();incidentReaderFixture("operator","keeper","leader","conservation","department-head");block() }
    }
    @Test fun `original download is limited to a verified archive of the selected sampling case`()=fixture {
        val fileId="2100000000000000001"
        FileDao.new(fileId){name="取样原件.pdf";path="/files/sampling-original.pdf";hash="original";size=16;ext="pdf"}
        val planId=service.savePlan("sampling",plan())
        val archiveId=archives.seal("museum",CollectionConst.BIZ_SAMPLING,planId,"TEST_ORIGINAL",mapOf("planId" to planId),fileId)
        assertDoesNotThrow { service.requireOriginal("sampling",archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("sampling",archiveId,"2100000000000000002") }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("sampling","missing",fileId) }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("missing",archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("sampling",archiveId,"not-a-file") }
    }
    @Test fun `approval form exposes only the current plan and its phase identified permanent originals`()=fixture {
        val planId=service.savePlan("sampling",plan())
        val administrativeId=permit(planId)
        val form=service.buildFormData(CollectionSamplingDao["sampling"])
        assertEquals("sampling",form["id"])
        assertEquals(planId,form["planId"])
        assertEquals(plan(),form["plan"])
        val administrative=form["administrative"] as List<*>
        assertEquals(administrativeId,(administrative.single() as CollectionAdministrativeEvidenceVo).id)
        val records=form["archives"] as List<*>
        val phases=records.map { (it as Map<*,*>)["phase"] }.toSet()
        assertTrue("PLAN" in phases)
        assertTrue("ADMIN_$administrativeId" in phases)
    }
    @Test fun `sample detail identifies the sealed journal and later custody archive phases`()=fixture {
        val sampleId=sampleReady()
        val eventPhase=service.analyzeBatch("sampling",sampleId,batch(sampleId))
        @Suppress("UNCHECKED_CAST") val plan=(service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()
        @Suppress("UNCHECKED_CAST") val sample=(plan["samples"] as List<Map<String,Any?>>).single()
        @Suppress("UNCHECKED_CAST") val prefixes=sample["archivePhasePrefixes"] as Map<String,String>
        assertEquals(eventPhase,"${prefixes["journal"]}1")
        assertEquals(SamplingSampleJournal.custodyPrefix(sampleId),prefixes["custody"])
        assertEquals(SamplingAnalysisAmendment.prefix(sampleId),prefixes["analysisAmendment"])
        assertTrue(CollectionPermanentArchiveDao.all().any { it.phase==eventPhase && it.bizId==plan["id"] })
    }
    private fun seed() {
            SchemaUtils.create(CollectionPlacements,Warehouses,StorageMovements,IncidentRelocations,IncidentRelocationRecords,IncidentWorks,IncidentWorkRecords,CollectionOrgs,Collections,CollectionLedgerLogs,CollectionSamplings,CollectionSamplingPlans,CollectionSamples,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstances,FlowInstanceLogs,CollectionOperationLocks)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            CollectionDao.new("piece"){orgId="museum";name="一级器物";totalRegNo="M1";registeredAt=at.minusMonths(1);catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.FIRST;lifeStatus=EnumCollectionStatus.IN_STORE}
            CollectionSamplingDao.new("sampling"){orgId="museum";collectionId="piece";title="特殊研究取样";createBy="operator"}
            FileDao.new("proof"){name="真实共同签署原件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
    }
    private fun actor(id:String) {
        val user=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun incidentFacts(planId:String)=IncidentFactsVo(title="取样实际额外损伤",discoveredAt=at.plusMinutes(25),location="实际取样场所",reporterUserId="operator",description="实际额外损伤与现场措施",immediateMeasures="停止原件操作并保护现场",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"实际损伤")),source=IncidentSourceVo(CollectionConst.BIZ_SAMPLING,"sampling",planId))
    private fun closeIncident(id:String) {
        val d=incidents.detail(id);val factsId=d["factsId"] as String
        @Suppress("UNCHECKED_CAST") val req=d["requirements"] as List<Map<String,Any>>
        val reports=req.filter { it["kind"]==EnumIncidentReportKind.INCIDENT }.groupBy { it["target"] as EnumIncidentTarget }.map { (target,rows)->
            val v=IncidentReportVo(factsId,rows.map { it["collectionId"] as String }.toSet(),target,EnumIncidentReportKind.INCIDENT,"实际接收机关或部门",if(target==EnumIncidentTarget.KEEPER)"keeper" else if(target==EnumIncidentTarget.DIRECTOR)"leader" else null,"实际报告编号",at.plusMinutes(45),"事故实际报告","proof")
            incidents.report(id,v) to v
        }.toMap()
        incidents.recordHandling(id,IncidentHandlingVo(factsId,at.plusMinutes(50),"operator","真实原因调查","真实保护处理","proof",listOf(IncidentItemResolutionVo("piece",EnumIncidentDisposition.PRESENT_SAFE,true,"处理后真实现状","实际保管位置","custodian","实际保护措施","后续检查安排","proof")),reports.filter { it.value.target !in setOf(EnumIncidentTarget.KEEPER,EnumIncidentTarget.DIRECTOR) }.map { (rid,r)->IncidentAuthorityResultVo(r.target,r.authorityName,r.collectionIds,at.plusMinutes(48),setOf(rid),"实际核查处理结果","proof") },"keeper","leader","本件事故档案索引","proof"))
        actor("keeper");incidents.confirm(id,IncidentConfirmationVo(incidents.detail(id)["basisRevision"] as String,at.plusMinutes(55),"本人核对","proof"))
        actor("leader");incidents.confirm(id,IncidentConfirmationVo(incidents.detail(id)["basisRevision"] as String,at.plusMinutes(60),"本人核对","proof"));actor("operator")
    }
    private fun clearance(id:String)=IncidentClearanceVo(id,at.plusMinutes(65),"conservation","keeper","实际处置后现状","共同核实实际保护措施","proof")
    private fun plan()=SamplingPlanVo(
        service.detail("sampling")["sourceRevision"] as String,at,"conservation-dept","conservation","keeper",EnumSamplingPurpose.RESEARCH,
        "特殊研究问题及必须进行本体分析的理由","已评估非有损方法，不能满足本次实际问题","proof","实际调查同时代同类型同质地替代物范围","proof",
        listOf(SamplingAlternativeVo("实际候选器物及现状",true,true,true,false,"候选物无法代替本件特殊研究问题，附评估依据","proof")),true,"本件必须取样的实际理由",
        at.plusMinutes(5),at.plusHours(1),"本馆实验室",listOf(SamplingPointVo("point","底部现存剥落处","proof","微量材料","显微取样","限定在最小必要范围",BigDecimal("2.50"),EnumSamplingUnit.MG,2,"支撑与清洁防护")),
        "分析实验室","分析技术方法",true,"剩余样品编号和封存保管","现场安全及取样后养护","proof","proof")
    private fun permit(p:String,level:EnumAuthorityLevel=EnumAuthorityLevel.NATIONAL)=service.administrative("sampling",p,CollectionAdministrativeEvidenceVo(level=level,action=EnumAdministrativeAction.APPROVAL,authorityName="实际批准机关",documentNo="实际文号",issuedAt=at.toLocalDate(),fileIds="proof"))
    private fun approve(keeper:Boolean=true,target:CollectionSamplingService=service) {
        target.submit("sampling");val r=CollectionSamplingDao["sampling"]
        listOf("conservation","keeper").filter { keeper || it!="keeper" }.forEachIndexed { i,node->FlowInstanceLogDao.new {instanceId=r.flowInstanceId!!;nodeKey=node;taskId="task-$node";action="APPROVE";operator=node;comment="本人核对最小干预、替代评估及共同方案";createTime=at.plusMinutes(1+i.toLong())} }
        target.onFlowCompleted(r,FlowCompletedEvent(r.flowInstanceId!!,CollectionConst.FLOW_SAMPLING,"取样","sampling",CollectionConst.BIZ_SAMPLING,emptyMap(),"operator","经办"))
    }
    private fun start(p:String)=SamplingStartVo(p,at.plusMinutes(10),"operator","req",null,true,true,"实际核对原件和场所安全","proof","proof")
    private fun ready():String {val p=service.savePlan("sampling",plan());approve();permit(p);service.start("sampling",start(p));return p}
    private fun take()=SamplingTakeVo("M-S1","point",at.plusMinutes(20),"operator","底部现存剥落处",BigDecimal("1.25"),EnumSamplingUnit.MG,true,false,"实际取样后现状，无额外损伤","实际防护养护","样品柜","custodian","proof","proof","proof")
    private fun end(ids:Set<String>)=SamplingExtractionEndVo(at.plusMinutes(40),"conservation","keeper",ids,"取样后的真实现状","实际表面处理和养护",true,"proof","proof")
    private fun analysis()=SamplingAnalysisVo(at.plusHours(1),"分析实验室","operator","实际分析方法、结果及消耗",BigDecimal("0.25"),BigDecimal("1.00"),"剩余样品柜","custodian","proof","proof")
    private fun completion(ids:Set<String>)=SamplingCompletionVo(at.plusHours(2),"conservation","keeper",ids,"共同核对原件、样品分析消耗、剩余保管和全部原件","M1/取样档案","proof")

    private fun journal(sampleId:String):SamplingSampleJournalVo {
        val sample=CollectionSampleDao[sampleId]
        return SamplingSampleJournal.read(CollectionSamplingPlanDao[sample.planId],sample) { key,value->
            val a=CollectionPermanentArchiveDao.all().single { it.bizId==sample.planId && it.phase==key }
            assertEquals(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(a.snapshot),io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(value)))
        }
    }
    private fun batch(sampleId:String,number:String="B1",used:String="0.25",left:String="1.00",minutes:Long=60)=SamplingBatchAnalysisVo(
        journal(sampleId).revision,number,at.plusMinutes(minutes),"分析实验室","实际分析专家","operator","实际分析方法和报告结果",
        BigDecimal(used),BigDecimal(left),"实际样品柜","proof","proof")
    private fun handover(sampleId:String,external:Boolean=true,minutes:Long=50)=SamplingSampleHandoverVo(
        journal(sampleId).revision,at.plusMinutes(minutes),"operator",journal(sampleId).remainingQuantity,
        if(external)SamplingSampleCustodyVo(EnumSampleCustody.EXTERNAL,"实验室样品柜",externalUnit="分析实验室",externalPerson="实验室接收人员",expectedReturnAt=at.plusDays(1))
        else SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"本馆样品柜",userId="custodian"),
        "点交现状及标识相符","本方案分析及剩余样品返还安排","proof","proof","proof")
    private fun sampleReady():String {
        ready();val id=service.take("sampling",take());service.endExtraction("sampling",end(setOf(id)));return id
    }
    private fun review(sampleId:String)=SamplingCompletionReviewVo(completion(setOf(sampleId)),mapOf(sampleId to journal(sampleId).revision))

    @Test fun `revoked current permission blocks journal access even when login still holds admin`()=fixture {
        val id=sampleReady();val v=batch(id)
        val revoked=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true)
        `when`(directory.requireActive("operator")).thenReturn(revoked)
        assertThrows(BadRequestException::class.java){service.detail("sampling")}
        assertThrows(BadRequestException::class.java){service.page(MockHttpServletRequest())}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,v)}
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","rollback","legacy","completed","discrepancy","confirmation-race","confirmation-duplicate","new-observation","confirmation-rollback"])
    fun `PostgreSQL serializes sample journal operations and refreshes cached state`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();sampleReady()}
            if(scenario=="completed")transaction(db){service.analysis("sampling",id,analysis())}
            val withReview=scenario.startsWith("confirmation-") || scenario=="new-observation"
            if(withReview)transaction(db){service.recordSampleDiscrepancy("sampling",id,discrepancy(id));service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id))}
            val confirm=if(withReview)transaction(db){confirmation(id)} else null
            val v=transaction(db){batch(id,used=if(scenario=="completed")"0.25" else "0.25",left=if(scenario=="completed")"0.75" else "1.00",minutes=80)}
            val first=pool.submit<Result<String>> {
                actor(if(scenario.startsWith("confirmation-"))"conservation" else "operator")
                try {runCatching {transaction(db) {
                    val result=when(scenario) {
                        "legacy" -> service.analysis("sampling",id,analysis())
                        "completed" -> service.complete("sampling",completion(setOf(id)))
                        "discrepancy" -> service.recordSampleDiscrepancy("sampling",id,discrepancy(id))
                        "new-observation" -> service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0.75",105))
                        "confirmation-race","confirmation-duplicate","confirmation-rollback" -> service.confirmSampleDiscrepancy("sampling",id,confirm!!)
                        else -> service.analyzeBatch("sampling",id,v)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario=="rollback" || scenario=="confirmation-rollback")throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(withReview) {if(scenario=="confirmation-duplicate")"conservation" else "keeper"} else "operator")
                try {runCatching {transaction(db) {
                    service.detail("sampling") // deliberately retain old plan and legacy sample DAO values
                    if(withReview)service.confirmSampleDiscrepancy("sampling",id,confirm!!)
                    else service.analyzeBatch("sampling",id,if(scenario=="different")v.copy(batchNumber="OTHER") else v)
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
            var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait")
            release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario=="rollback" || scenario=="confirmation-rollback")assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("different","legacy","completed","discrepancy","confirmation-race","new-observation"))assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            else {two.getOrThrow();if(scenario in setOf("same","confirmation-duplicate"))assertEquals(one.getOrThrow(),two.getOrThrow())}
            transaction(db) {
                assertEquals(if(scenario in setOf("legacy","completed"))0 else if(withReview)3 else 1,journal(id).events.size)
                if(withReview) {
                    assertTrue(journal(id).openDiscrepancySequences.isNotEmpty())
                    val expected=when(scenario){"new-observation"->emptySet();"confirmation-rollback"->setOf("keeper");else->setOf("conservation")}
                    assertEquals(expected,journal(id).confirmedResolutionUserIds)
                }
                if(scenario=="legacy")assertEquals(analysis(),CollectionSampleDao[id].analysis)
                if(scenario=="completed")assertNotNull(CollectionSamplingPlanDao.all().single().completed)
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["end","take","race","duplicate","rollback","replacement"])
    fun `PostgreSQL serializes no sample closure against execution and other signers`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val execution=scenario in setOf("end","take")
            transaction(db){seed();if(execution)ready() else {noSampleReady();service.reviewNoSampleClosure("sampling",noSampleReview())}}
            val review=transaction(db){noSampleReview()}
            val confirmation=if(execution)null else transaction(db){noSampleConfirm(100)}
            val first=pool.submit<Result<String>> {
                actor(if(scenario in setOf("race","duplicate","rollback"))"conservation" else "operator")
                try {runCatching {transaction(db) {
                    val result=when(scenario) {
                        "end" -> service.endExtraction("sampling",end(emptySet()))
                        "take" -> service.take("sampling",take())
                        "replacement" -> service.reviewNoSampleClosure("sampling",review.copy(reason="新增实际核查说明",reviewedAt=at.plusMinutes(95)))
                        else -> service.confirmNoSampleClosure("sampling",confirmation!!)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario=="rollback")throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(execution)"operator" else if(scenario=="duplicate")"conservation" else "keeper")
                try {runCatching {transaction(db) {
                    service.detail("sampling") // cache plan and old closure before attempting the root write lock
                    if(execution)service.reviewNoSampleClosure("sampling",review) else service.confirmNoSampleClosure("sampling",confirmation!!)
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait");release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario=="rollback")assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("duplicate","rollback")) {two.getOrThrow();if(scenario=="duplicate")assertEquals(one.getOrThrow(),two.getOrThrow())}
            else assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            transaction(db) {
                val state=noSampleState();assertNull(state.closedAt)
                assertEquals(if(execution)0 else 2,state.events.size)
                assertEquals(when(scenario){"race","duplicate"->setOf("conservation");"rollback"->setOf("keeper");else->emptySet()},state.confirmedUserIds)
                assertNull(CollectionSamplingPlanDao.all().single().completed)
                assertEquals(if(scenario=="take")1 else 0,CollectionSampleDao.all().count())
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun noSampleState():SamplingNoSampleClosureVo = ((service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()["noSampleClosure"] as SamplingNoSampleClosureVo)
    private fun noSampleReady():String {val p=ready();service.endExtraction("sampling",end(emptySet()));return p}
    private fun noSampleReview()=SamplingNoSampleReviewVo(noSampleState().revision,noSampleState().basisRevision,CollectionSamplingPlanDao.all().maxBy {it.version}.id.value,
        at.plusMinutes(80),"operator","conservation","keeper","实际检查后决定不再取样，未取得任何样品","实际准备与检查，没有分离本体材料","原藏品实际现状","实际完成的保护养护","保留停止记录，后续另按实际需要办理","M1/未取得样品档案","proof","proof","proof")
    private fun noSampleConfirm(minutes:Long=90)=SamplingNoSampleConfirmationVo(noSampleState().revision,noSampleState().reviewSequence!!,at.plusMinutes(minutes),"本人核对实际未取得样品及原件安全","proof")

    @Test fun `zero sample closure requires separate people and never manufactures ordinary completion`()=fixture {
        val p=noSampleReady();val original=CollectionSamplingPlanDao[p].ended
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(emptySet()))}
        val review=noSampleReview();service.reviewNoSampleClosure("sampling",review)
        assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",noSampleConfirm())}
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm())
        assertNull(noSampleState().closedAt)
        assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",noSampleConfirm(92))}
        actor("keeper");service.confirmNoSampleClosure("sampling",noSampleConfirm(95))
        assertEquals(at.plusMinutes(95),noSampleState().closedAt)
        assertEquals(setOf("conservation","keeper"),noSampleState().confirmedUserIds)
        assertNull(CollectionSamplingPlanDao[p].completed);assertEquals(0,CollectionSampleDao.all().count())
        assertEquals(original,CollectionSamplingPlanDao[p].ended)
        assertThrows(BadRequestException::class.java){service.take("sampling",take())}
        actor("operator");assertThrows(BadRequestException::class.java){service.reviewNoSampleClosure("sampling",noSampleReview().copy(reviewedAt=at.plusMinutes(100)))}
        assertDoesNotThrow{locks.requireUnlocked("piece")}
    }
    @Test fun `no sample review and confirmations retry without duplicate archives`()=fixture {
        noSampleReady();val review=noSampleReview();val r=service.reviewNoSampleClosure("sampling",review)
        assertEquals(r,service.reviewNoSampleClosure("sampling",review))
        actor("conservation");val first=noSampleConfirm();val a=service.confirmNoSampleClosure("sampling",first)
        assertEquals(a,service.confirmNoSampleClosure("sampling",first))
        actor("keeper");val second=noSampleConfirm(95);val b=service.confirmNoSampleClosure("sampling",second)
        assertEquals(b,service.confirmNoSampleClosure("sampling",second));actor("conservation")
        assertEquals(a,service.confirmNoSampleClosure("sampling",first));assertEquals(3,noSampleState().events.size)
    }
    @Test fun `replacement zero sample review invalidates earlier individual confirmation and preserves history`()=fixture {
        noSampleReady();service.reviewNoSampleClosure("sampling",noSampleReview())
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm())
        val stale=noSampleConfirm(100);actor("operator")
        service.reviewNoSampleClosure("sampling",noSampleReview().copy(reviewedAt=at.plusMinutes(95),reason="后续材料补充真实停止原因"))
        assertTrue(noSampleState().confirmedUserIds.isEmpty());assertEquals(3,noSampleState().reviewSequence)
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",stale)}
        service.confirmNoSampleClosure("sampling",noSampleConfirm(100));assertNull(noSampleState().closedAt)
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm(105))
        assertNotNull(noSampleState().closedAt);assertEquals(5,noSampleState().events.size)
    }
    @Test fun `zero sample closure cannot replace records before actual start end or after actual extraction`() {
        fixture {service.savePlan("sampling",plan());assertFalse(noSampleState().eligible);assertThrows(BadRequestException::class.java){service.reviewNoSampleClosure("sampling",noSampleReview())}}
        fixture {actor("operator");ready();assertFalse(noSampleState().eligible);assertThrows(BadRequestException::class.java){service.reviewNoSampleClosure("sampling",noSampleReview())}}
        fixture {actor("operator");sampleReady();assertFalse(noSampleState().eligible);assertThrows(BadRequestException::class.java){service.reviewNoSampleClosure("sampling",noSampleReview())}}
    }
    @Test fun `unsafe zero sample ending retains lock and requires actual incident clearance`()=fixture {
        ready();service.endExtraction("sampling",end(emptySet()).copy(safeToReleaseOriginal=false))
        assertThrows(BadRequestException::class.java){service.reviewNoSampleClosure("sampling",noSampleReview())}
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        assertTrue(noSampleState().events.isEmpty())
    }
    @Test fun `zero sample closure after real incident clearance retains unsafe ending and incident evidence`()=fixture {
        val p=ready();service.endExtraction("sampling",end(emptySet()).copy(safeToReleaseOriginal=false))
        val incident=incidents.create(incidentFacts(p));closeIncident(incident);service.clearIncident("sampling",clearance(incident))
        service.reviewNoSampleClosure("sampling",noSampleReview())
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm())
        actor("keeper");service.confirmNoSampleClosure("sampling",noSampleConfirm(95))
        assertNotNull(noSampleState().closedAt);assertFalse(CollectionSamplingPlanDao[p].ended!!.safeToReleaseOriginal)
        assertEquals(incident,CollectionSamplingPlanDao[p].incidentClearance!!.record.incidentId)
        assertEquals(0,CollectionSampleDao.all().count());assertNull(CollectionSamplingPlanDao[p].completed)
    }
    @Test fun `reopened incident blocks remaining no sample confirmation without erasing first signature`()=fixture {
        val p=ready();service.endExtraction("sampling",end(emptySet()).copy(safeToReleaseOriginal=false))
        val facts=incidentFacts(p);val incident=incidents.create(facts);closeIncident(incident);service.clearIncident("sampling",clearance(incident))
        service.reviewNoSampleClosure("sampling",noSampleReview())
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm())
        val first=noSampleState().events.last();actor("operator")
        incidents.amend(incident,facts.copy(description="后来查明新的原件风险",replacementReason="实际新增材料",replacementFileIds="proof"),incidents.detail(incident)["factsId"] as String)
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",noSampleConfirm(95))}
        assertNull(noSampleState().closedAt);assertEquals(first,noSampleState().events.last())
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
    }
    @Test fun `zero sample review verifies actual evidence people chronology and exact basis`()=fixture {
        noSampleReady();val v=noSampleReview()
        for(bad in listOf(v.copy(noSampleEvidenceFileIds=""),v.copy(conditionFileIds=""),v.copy(reason=""),v.copy(actualOperations=""),v.copy(signedReviewFileIds=""),v.copy(followUp=""),v.copy(collectionArchiveIndex=""),v.copy(recorderUserId="keeper"),v.copy(keeperUserId="conservation"),v.copy(planId="other"),v.copy(basisRevision="old"),v.copy(previousRevision="old"),v.copy(reviewedAt=at.plusMinutes(30)),v.copy(reviewedAt=LocalDateTime.now().plusDays(1)))) {
            assertThrows(BadRequestException::class.java){service.reviewNoSampleClosure("sampling",bad)}
        }
        assertTrue(noSampleState().events.isEmpty());service.reviewNoSampleClosure("sampling",v)
        actor("conservation");val c=noSampleConfirm()
        for(bad in listOf(c.copy(confirmedAt=at.plusMinutes(70)),c.copy(confirmedAt=LocalDateTime.now().plusDays(1)),c.copy(opinion=""),c.copy(signedFileIds=""),c.copy(reviewSequence=999),c.copy(previousRevision="old"))) {
            assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",bad)}
        }
        assertEquals(1,noSampleState().events.size)
    }
    @Test fun `revoked responsible role cannot supply second no sample confirmation`()=fixture {
        noSampleReady();service.reviewNoSampleClosure("sampling",noSampleReview())
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm())
        doThrow(BadRequestException("职责已撤销")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",noSampleConfirm(95))}
        assertNull(noSampleState().closedAt);assertEquals(setOf("conservation"),noSampleState().confirmedUserIds)
    }
    @Test fun `damaged no sample archive remains visible as issue and cannot authorize closure`()=fixture {
        noSampleReady();service.reviewNoSampleClosure("sampling",noSampleReview());val c=noSampleConfirm()
        CollectionPermanentArchiveDao.all().single {it.phase=="NOSAMPLEV1"}.digest="bad"
        @Suppress("UNCHECKED_CAST") val p=(service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()
        assertNull(p["noSampleClosure"]);assertNotNull(p["noSampleClosureIssue"])
        assertTrue((p["archiveIssues"] as List<*>).isNotEmpty())
        @Suppress("UNCHECKED_CAST") val originals=p["archives"] as List<Map<String,Any?>>
        assertFalse(originals.any {it["phase"]=="NOSAMPLEV1"})
        actor("conservation");assertThrows(BadRequestException::class.java){service.confirmNoSampleClosure("sampling",c)}
    }

    private fun discrepancy(id:String,quantity:String="0.50",minutes:Long=90)=SamplingSampleDiscrepancyVo(
        journal(id).revision,at.plusMinutes(minutes),"operator",BigDecimal(quantity),true,true,
        SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"核查暂存柜",userId="custodian"),
        "交接核对实际短缺及标识现状","停止分析，保护封存查见样品","已经报告并继续查找，记录后续责任及核查安排","proof","proof")
    private fun discrepancyReview(id:String,quantity:BigDecimal=journal(id).remainingQuantity)=SamplingSampleResolutionVo(
        journal(id).revision,journal(id).openDiscrepancySequences,at.plusMinutes(100),"operator","conservation","keeper",
        quantity,true,true,SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"核实后的样品柜",userId="custodian"),
        "实际调查原因及逐项查找核对结果","据实追回并核实原标识与现状","实际报告及核查处理依据","后续复查保管安排","proof","proof","proof")
    private fun confirmation(id:String,minutes:Long=110)=SamplingSampleConfirmationVo(
        journal(id).revision,journal(id).pendingResolutionSequence!!,at.plusMinutes(minutes),"本人逐项核实调查材料及仍存问题","proof")
    private fun bothConfirm(id:String) {
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,110))
        actor("keeper");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,115));actor("operator")
    }
    @Test fun `actual shortage remains distinct from expected quantity and blocks analysis transfer and completion`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id));val original=CollectionSampleDao[id].content
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id))
        assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.50"),journal(id).observedQuantity)
        assertFalse(journal(id).readyForCompletion)
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"B2","0.1","0.9",120))}
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id,false,120))}
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        assertEquals(original,CollectionSampleDao[id].content);assertEquals(0,CollectionIncidentDao.all().count())
    }
    @Test fun `recovered sample needs two distinct current actors and keeps discovery investigation and signatures`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id));service.recordSampleDiscrepancy("sampling",id,discrepancy(id))
        val proposal=discrepancyReview(id);service.reviewSampleDiscrepancy("sampling",id,proposal)
        val v=confirmation(id)
        assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,v)}
        actor("conservation");val key=service.confirmSampleDiscrepancy("sampling",id,v)
        assertEquals(key,service.confirmSampleDiscrepancy("sampling",id,v));assertFalse(journal(id).readyForCompletion)
        assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,confirmation(id,112))}
        actor("keeper");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,115));actor("operator")
        assertTrue(journal(id).readyForCompletion);assertTrue(journal(id).openDiscrepancySequences.isEmpty());assertNull(journal(id).observedQuantity)
        assertEquals(setOf("conservation","keeper"),journal(id).events.mapNotNull {it.confirmation?.operatorUserId}.toSet())
        assertEquals(proposal,journal(id).events.single {it.resolution!=null}.resolution)
        service.completeCurrent("sampling",review(id))
    }
    @Test fun `confirmed findings do not clear shortage surplus unknown identity or unsafe condition`() {
        for(kind in listOf("shortage","surplus","identity","unsafe"))fixture {
            actor("operator");val id=sampleReady();service.analyzeBatch("sampling",id,batch(id))
            service.recordSampleDiscrepancy("sampling",id,discrepancy(id))
            val v=discrepancyReview(id).let {when(kind){
                "shortage" -> it.copy(actualQuantity=BigDecimal("0.50"))
                "surplus" -> it.copy(actualQuantity=BigDecimal("2.00"))
                "identity" -> it.copy(identityConfirmed=false)
                else -> it.copy(safeToContinue=false)
            }}
            service.reviewSampleDiscrepancy("sampling",id,v);bothConfirm(id)
            assertFalse(journal(id).readyForCompletion,kind);assertEquals(2,journal(id).confirmedResolutionUserIds.size)
            assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity)
            assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        }
    }
    @Test fun `new discovery invalidates earlier review and first signature without rewriting them`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id))
        val proposal=journal(id).pendingResolutionSequence!!
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id))
        actor("operator");service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0.75",112))
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,SamplingSampleConfirmationVo(journal(id).revision,proposal,at.plusMinutes(115),"沿用旧调查","proof"))}
        assertEquals(2,journal(id).openDiscrepancySequences.size);assertNull(journal(id).pendingResolutionSequence)
        assertEquals(1,journal(id).events.count {it.confirmation!=null})
    }
    @Test fun `unknown missing sample custody stays unknown and reported time is not rewritten`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id))
        val v=discrepancy(id,"0",30).copy(observedCustody=null,identityConfirmed=false,conditionSafe=false)
        service.recordSampleDiscrepancy("sampling",id,v)
        assertNull(journal(id).events.last().discrepancy!!.observedCustody)
        assertEquals(at.plusMinutes(30),journal(id).events.last().discrepancy!!.observedAt)
        assertEquals(at.plusMinutes(60),journal(id).lastOccurredAt)
        service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id,BigDecimal.ZERO).copy(actualCustody=null,identityConfirmed=false,safeToContinue=false))
        bothConfirm(id);assertFalse(journal(id).readyForCompletion)
    }
    @Test fun `discovery during extraction is recorded and stops further normal extraction`()=fixture {
        ready();val id=service.take("sampling",take())
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0",25).copy(observedCustody=null))
        assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(number="M-S2",takenAt=at.plusMinutes(30)))}
        service.endExtraction("sampling",end(setOf(id)))
        assertFalse(journal(id).readyForCompletion);assertTrue(CollectionSamplingPlanDao.all().single().ended!!.safeToReleaseOriginal)
    }
    @Test fun `investigation must include every open discovery and retain mandatory report evidence`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0.75",95))
        val v=discrepancyReview(id)
        for(invalid in listOf(v.copy(discrepancySequences=setOf(v.discrepancySequences.first())),v.copy(reportingAndHandlingFileIds=""),v.copy(keeperUserId="other"),v.copy(actualQuantity=BigDecimal("-1")),v.copy(reviewedAt=at.plusMinutes(80)))) {
            assertThrows(BadRequestException::class.java){service.reviewSampleDiscrepancy("sampling",id,invalid)}
        }
        assertEquals(2,journal(id).events.size)
    }
    @Test fun `recovery from external custody requires actual handover signatures in addition to internal review`()=fixture {
        val id=sampleReady();service.handoverSample("sampling",id,handover(id));service.recordSampleDiscrepancy("sampling",id,discrepancy(id))
        service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id))
        actor("conservation")
        val error=assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,confirmation(id))}
        assertTrue(error.message!!.contains("双方签署"));assertTrue(journal(id).confirmedResolutionUserIds.isEmpty())
        actor("operator");service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id).copy(reviewedAt=at.plusMinutes(105),custodyHandoverFileIds="proof"))
        bothConfirm(id)
        assertTrue(journal(id).openDiscrepancySequences.isEmpty());assertEquals(EnumSampleCustody.INTERNAL,journal(id).custody.kind)
        assertEquals(2,journal(id).events.count {it.resolution!=null})
    }
    @Test fun `same report and same review retries preserve one immutable record`()=fixture {
        val id=sampleReady();val d=discrepancy(id);val key=service.recordSampleDiscrepancy("sampling",id,d)
        assertEquals(key,service.recordSampleDiscrepancy("sampling",id,d))
        val r=discrepancyReview(id);val rk=service.reviewSampleDiscrepancy("sampling",id,r)
        assertEquals(rk,service.reviewSampleDiscrepancy("sampling",id,r));assertEquals(key,service.recordSampleDiscrepancy("sampling",id,d))
        assertEquals(2,journal(id).events.size)
    }
    @Test fun `current role revocation prevents second signature and leaves first signature intact`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id))
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id))
        doThrow(BadRequestException("保管职责已撤销")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,confirmation(id,115))}
        assertEquals(setOf("conservation"),journal(id).confirmedResolutionUserIds)
        assertTrue(journal(id).openDiscrepancySequences.isNotEmpty())
    }
    @Test fun `old event bytes and revision remain compatible after optional discrepancy fields are added`()=fixture {
        val id=sampleReady();val a=batch(id);val from=journal(id).custody
        val old=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(linkedMapOf("sampleId" to id,"sequence" to 1,"from" to from,"analysis" to a,"handover" to null))!!
        val event=SamplingSampleEventVo(id,1,from,analysis=a)
        assertEquals(old,io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(event))
        val key=service.analyzeBatch("sampling",id,a)
        assertEquals(old,CollectionPermanentArchiveDao.all().single {it.phase==key}.snapshot)
        assertTrue(journal(id).readyForCompletion)
    }
    @Test fun `ineligible actual custodian can be reported but cannot silently authorize normal continuation`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id).copy(observedCustody=SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"原保管点",userId="departed")))
        service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id).copy(actualCustody=SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"核查点",userId="departed")))
        doThrow(BadRequestException("人员已离馆")).`when`(users).requireMuseumMember("departed","museum")
        actor("conservation");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,confirmation(id))}
        assertTrue(journal(id).openDiscrepancySequences.isNotEmpty())
    }
    @Test fun `tampered investigation blocks review confirmation and remains visible as an archive issue`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));val key=service.reviewSampleDiscrepancy("sampling",id,discrepancyReview(id));val v=confirmation(id)
        CollectionPermanentArchiveDao.all().single {it.phase==key}.digest="corrupt"
        actor("conservation");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,v)}
        @Suppress("UNCHECKED_CAST") val plans=service.detail("sampling")["plans"] as List<Map<String,Any?>>
        @Suppress("UNCHECKED_CAST") val rows=plans.single()["samples"] as List<Map<String,Any?>>
        assertNotNull(rows.single()["journalIssue"])
    }
    @Test fun `remaining custodian departure requires an actual handover before joint completion`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id))
        doThrow(BadRequestException("原保管人已离馆")).`when`(users).requireMuseumMember("custodian","museum")
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        val moved=handover(id,false,80).let {it.copy(destination=it.destination.copy(userId="successor"))}
        service.handoverSample("sampling",id,moved)
        assertEquals("custodian",journal(id).events.last().from.userId)
        service.completeCurrent("sampling",review(id))
    }
    @Test fun `sample journal API encodes micro quantities as text and preserves millisecond timestamps`()=fixture {
        val id=sampleReady();val value=batch(id).copy(consumedQuantity=BigDecimal("0.00000000000000000001"),remainingQuantity=BigDecimal("1.24999999999999999999"))
        service.analyzeBatch("sampling",id,value)
        val tree=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(journal(id)))
        assertTrue(tree.get("remainingQuantity").isString)
        assertEquals("1.24999999999999999999",tree.get("remainingQuantity").asText())
        assertTrue(tree.get("events").get(0).get("analysis").get("consumedQuantity").isString)
        assertTrue(tree.get("readyForCompletion").asBoolean())
    }
    @Test fun `sequential analysis consumes previous remainder and preserves every report`()=fixture {
        val id=sampleReady();val first=batch(id);val key=service.analyzeBatch("sampling",id,first)
        assertEquals(key,service.analyzeBatch("sampling",id,first))
        val second=batch(id,"B2","0.30","0.70",70);service.analyzeBatch("sampling",id,second)
        assertEquals(key,service.analyzeBatch("sampling",id,first))
        val state=journal(id);assertEquals(2,state.analysisCount);assertEquals(BigDecimal("0.70"),state.remainingQuantity)
        assertEquals(listOf(first,second),state.events.map { it.analysis });assertNull(CollectionSampleDao[id].analysis)
        service.completeCurrent("sampling",review(id));assertNotNull(CollectionSamplingPlanDao.all().single().completed)
    }
    @Test fun `legacy analysis is retained as baseline for subsequent batches without overwrite`()=fixture {
        val id=sampleReady();service.analysis("sampling",id,analysis())
        service.analyzeBatch("sampling",id,batch(id,"B2","0.30","0.70",70))
        assertEquals(analysis(),CollectionSampleDao[id].analysis);assertEquals(2,journal(id).analysisCount)
        assertThrows(BadRequestException::class.java){service.analysis("sampling",id,analysis())}
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(setOf(id)))}
        service.completeCurrent("sampling",review(id))
    }
    @Test fun `external custody blocks completion until actual return and keeps both signed transfers`()=fixture {
        val id=sampleReady();val out=handover(id);val outKey=service.handoverSample("sampling",id,out)
        assertEquals(outKey,service.handoverSample("sampling",id,out))
        service.analyzeBatch("sampling",id,batch(id));val stale=review(id)
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",stale)}
        val back=handover(id,false,80);service.handoverSample("sampling",id,back)
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",stale)}
        assertEquals(EnumSampleCustody.EXTERNAL,journal(id).events.last().from.kind)
        assertEquals(EnumSampleCustody.INTERNAL,journal(id).custody.kind)
        service.completeCurrent("sampling",review(id))
        assertEquals(3,journal(id).events.size)
    }
    @Test fun `fully consumed external sample keeps consumption evidence without fabricated return`()=fixture {
        val id=sampleReady();service.handoverSample("sampling",id,handover(id))
        service.analyzeBatch("sampling",id,batch(id,used="1.25",left="0.00"))
        assertTrue(journal(id).readyForCompletion)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id,false,80))}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"B2","0","0",80))}
        service.completeCurrent("sampling",review(id))
    }
    @Test fun `stale quantities duplicated batch labels negative and reversed times cannot append`()=fixture {
        val id=sampleReady();val stale=batch(id,"B2");service.analyzeBatch("sampling",id,batch(id))
        val invalid=listOf(stale,batch(id,"B1","0.25","0.75",70),batch(id,"B2","-0.2","1.2",70),
            batch(id,"B2","0.2","0.9",70),batch(id,"B2","0.2","0.8",55))
        for(v in invalid)assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,v)}
        assertEquals(1,journal(id).events.size)
    }
    @Test fun `handover cannot lose quantity invent recipient or omit signatures`()=fixture {
        val id=sampleReady();val v=handover(id)
        val invalid=listOf(v.copy(quantity=BigDecimal("1")),v.copy(signedBothPartiesFileIds=""),v.copy(recorderUserId="other"),
            v.copy(destination=v.destination.copy(externalUnit="未批准单位")),v.copy(destination=v.destination.copy(externalPerson="")),
            v.copy(destination=v.destination.copy(expectedReturnAt=null)),v.copy(destination=v.destination.copy(userId="borrowed")),
            v.copy(destination=SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"馆内")))
        for(value in invalid)assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,value)}
        assertTrue(journal(id).events.isEmpty())
    }
    @Test fun `event digest tampering is visible and blocks further analysis and completion`()=fixture {
        val id=sampleReady();val key=service.analyzeBatch("sampling",id,batch(id))
        CollectionPermanentArchiveDao.all().single { it.phase==key }.digest="altered"
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"B2","0.1","0.9",70))}
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(setOf(id)))}
        @Suppress("UNCHECKED_CAST") val plans=service.detail("sampling")["plans"] as List<Map<String,Any?>>
        @Suppress("UNCHECKED_CAST") val samples=plans.single()["samples"] as List<Map<String,Any?>>
        assertNotNull(samples.single()["journalIssue"]);assertNull(samples.single()["journal"])
        assertTrue((plans.single()["archiveIssues"] as List<*>).isNotEmpty())
        @Suppress("UNCHECKED_CAST") val originals=plans.single()["archives"] as List<Map<String,Any?>>
        assertFalse(originals.any {it["phase"]==key})
    }
    @Test fun `event linked to a different sample or missing preceding batch is rejected`()=fixture {
        val id=sampleReady();val firstKey=service.analyzeBatch("sampling",id,batch(id))
        service.analyzeBatch("sampling",id,batch(id,"B2","0.1","0.9",70))
        CollectionPermanentArchiveDao.all().single { it.phase==firstKey }.delete()
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(setOf(id)))}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,missing","PLAN,replacement","PLAN,extra",
        "ADMIN,missing","ADMIN,replacement","ADMIN,extra",
        "STARTED,missing","STARTED,replacement","STARTED,extra",
        "SAMPLE,missing","SAMPLE,replacement","SAMPLE,extra",
        "EVENT,missing","EVENT,replacement","EVENT,extra",
        "COMPLETED,missing","COMPLETED,replacement","COMPLETED,extra")
    fun `stage declared originals cannot be removed coherently replaced or supplemented`(stage:String,change:String)=fixture {
        lateinit var action:()->Any?
        val key=when(stage) {
            "PLAN"->{service.savePlan("sampling",plan());action={service.submit("sampling")};"PLAN"}
            "ADMIN"->{val p=service.savePlan("sampling",plan());approve();val a=permit(p);action={service.start("sampling",start(p))};"ADMIN_$a"}
            "STARTED"->{ready();action={service.take("sampling",take())};"STARTED"}
            "SAMPLE"->{ready();val s=service.take("sampling",take());action={service.endExtraction("sampling",end(setOf(s)))};"SAMPLE_$s"}
            "EVENT"->{val s=sampleReady();val k=service.analyzeBatch("sampling",s,batch(s));action={service.analyzeBatch("sampling",s,batch(s,"B2","0.1","0.9",70))};k}
            else->{val s=custodyReady(legacy=true);action={service.complete("sampling",completion(setOf(s)))};"COMPLETED"}
        }
        val archive=CollectionPermanentArchiveDao.all().single {it.phase==key}
        val original=CollectionPermanentArchiveFileDao.all().single {it.archiveId==archive.id.value}
        val savedFile=original.fileId;val savedMetadata=original.metadata
        FileDao.new("replacement"){name="无关合成文件.pdf";path="replacement";hash="replacement";size=100;ext="pdf"}
        val other=FileDao["replacement"]
        val metadata=CollectionArchiveFileIdentity.metadata(other,archive.orgId,archive.bizType,archive.bizId,archives.fileReadKeys(other))
        val extra=when(change) {
            "missing"->{original.delete();null}
            "replacement"->{original.fileId="replacement";original.metadata=metadata;null}
            else->CollectionPermanentArchiveFileDao.new {orgId=archive.orgId;archiveId=archive.id.value;bizId=archive.bizId;fileId="replacement";this.metadata=metadata}
        }
        fun state()=listOf(
            CollectionSamplingDao.all().map {listOf(it.id.value,it.flowStatus,it.flowInstanceId)},
            CollectionSamplingPlanDao.all().map {listOf(it.id.value,it.started,it.ended,it.completed)},
            CollectionSampleDao.all().map {it.id.value to it.analysis},
            CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.snapshot,it.digest)},
            CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.fileId,it.metadata)},
            CollectionOperationLockDao.all().map {listOf(it.id.value,it.collectionId,it.bizType,it.bizId)},requests.size)
        val before=state()
        assertThrows(BadRequestException::class.java){action()};assertEquals(before,state())
        assertThrows(BadRequestException::class.java){archives.phaseForAuthorizedOrg(CollectionConst.BIZ_SAMPLING,archive.bizId,"museum",key)}
        val inspected=archives.inspectForAuthorizedOrg(CollectionConst.BIZ_SAMPLING,archive.bizId,"museum")
        assertTrue(inspected.issues.any {it.startsWith("$key：")});assertFalse(inspected.archives.any {it["id"]==archive.id.value})
        assertEquals(before,state())
        when(change) {
            "missing"->CollectionPermanentArchiveFileDao.new {orgId=archive.orgId;archiveId=archive.id.value;bizId=archive.bizId;fileId=savedFile;this.metadata=savedMetadata}
            "replacement"->{original.fileId=savedFile;original.metadata=savedMetadata}
            else->extra!!.delete()
        }
        assertDoesNotThrow {action()}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["PLAN","STARTED","SAMPLE"])
    fun `damaged phase original blocks subsequent sampling work without changing records`(phase:String)=fixture {
        val root=java.nio.file.Files.createTempDirectory("sampling-original-")
        val ids=listOf("proof","start-proof","take-proof")
        try {
            ids.forEach {id->
                java.nio.file.Files.write(root.resolve(id),ByteArray(100){'A'.code.toByte()})
                if(id!="proof")FileDao.new(id){name="$id.pdf";path=id;hash="synthetic-$id";size=100;ext="pdf"}
            }
            val localArchives=CollectionPermanentArchiveService(binder,scope,
                io.hizhengfu.cloud.common.file.LocalFileObjectStorage(root.toString()),unprotectedArchiveReadAccessProvider())
            val actual=CollectionSamplingService(flow,scope,users,localArchives,originals,locks,incidents,directory)
            val p=actual.savePlan("sampling",plan());approve(target=actual)
            actual.administrative("sampling",p,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,
                action=EnumAdministrativeAction.APPROVAL,authorityName="合成批准机关",documentNo="TEST",issuedAt=at.toLocalDate(),fileIds="proof"))
            val started=start(p).copy(signedStartFileIds="start-proof")
            val taking=take().copy(signedTakeFileIds="take-proof")
            if(phase!="PLAN")actual.start("sampling",started)
            val sampleId=if(phase=="SAMPLE")actual.take("sampling",taking) else null
            if(sampleId!=null)actual.endExtraction("sampling",end(setOf(sampleId)))
            val path=root.resolve(when(phase){"PLAN"->"proof";"STARTED"->"start-proof";else->"take-proof"})
            val bytes=java.nio.file.Files.readAllBytes(path);val modified=java.nio.file.Files.getLastModifiedTime(path)
            fun state()=listOf(CollectionSamplingPlanDao[p].started,CollectionSamplingPlanDao[p].completed,
                CollectionSampleDao.all().map {it.id.value to it.analysis},
                CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.snapshot,it.digest)},
                CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.metadata)},
                CollectionOperationLockDao.all().map {listOf(it.id.value,it.collectionId,it.bizType,it.bizId)})
            val before=state()
            java.nio.file.Files.write(path,ByteArray(bytes.size){'X'.code.toByte()});java.nio.file.Files.setLastModifiedTime(path,modified)
            fun proceed(){when(phase){"PLAN"->actual.start("sampling",started);"STARTED"->actual.take("sampling",taking);else->actual.analyzeBatch("sampling",sampleId!!,batch(sampleId))}}
            val failure=assertThrows(BadRequestException::class.java){proceed()}
            assertTrue(failure.message!!.contains("内容"));assertEquals(before,state())
            @Suppress("UNCHECKED_CAST") val detail=(actual.detail("sampling")["plans"] as List<Map<String,Any?>>).single()
            assertTrue((detail["archiveIssues"] as List<*>).isNotEmpty())
            java.nio.file.Files.write(path,bytes);java.nio.file.Files.setLastModifiedTime(path,modified)
            assertDoesNotThrow {proceed()}
        } finally {
            ids.forEach {java.nio.file.Files.deleteIfExists(root.resolve(it))};java.nio.file.Files.deleteIfExists(root)
        }
    }
    @Test fun `sample journal enforces museum scope on reads and mutations`()=fixture {
        val id=sampleReady();val v=batch(id)
        CollectionSamplingDao["sampling"].orgId="another-museum"
        assertThrows(BadRequestException::class.java){service.detail("sampling")}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,v)}
        assertTrue(journal(id).events.isEmpty())
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","rollback","discrepancy","confirmation"])
    fun `PostgreSQL serializes post completion custody without changing frozen completion`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();custodyReady()}
            val frozen=transaction(db){journal(id)}
            val completed=transaction(db){CollectionSamplingPlanDao.all().single().completed}
            if(scenario=="confirmation")transaction(db){service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id),true);service.reviewSampleDiscrepancy("sampling",id,custodyReview(id),true)}
            val handover=transaction(db){custodyHandover(id)}
            val confirm=if(scenario=="confirmation")transaction(db){custodyConfirm(id)} else null
            val first=pool.submit<Result<String>> {
                actor(if(scenario=="confirmation")"new-conservation" else "operator")
                try {runCatching {transaction(db) {
                    val result=when(scenario) {
                        "discrepancy" -> service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id),true)
                        "confirmation" -> service.confirmSampleDiscrepancy("sampling",id,confirm!!,true)
                        else -> service.handoverSample("sampling",id,handover,true)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario=="rollback")throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(scenario=="confirmation")"new-keeper" else "operator")
                try {runCatching {transaction(db) {
                    service.detail("sampling") // retain old completed plan and both custody periods before waiting
                    if(scenario=="confirmation")service.confirmSampleDiscrepancy("sampling",id,confirm!!,true)
                    else service.handoverSample("sampling",id,if(scenario=="different")handover.copy(destination=handover.destination.copy(externalPerson="另一位实际接收人")) else handover,true)
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait");release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario=="rollback")assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("different","discrepancy","confirmation"))assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            else {two.getOrThrow();if(scenario=="same")assertEquals(one.getOrThrow(),two.getOrThrow())}
            transaction(db) {
                assertEquals(frozen,journal(id));assertEquals(completed,CollectionSamplingPlanDao.all().single().completed)
                assertEquals(if(scenario=="confirmation")3 else 1,custodyJournal(id).events.size)
                assertEquals(BigDecimal("1.00"),custodyJournal(id).remainingQuantity)
                if(scenario=="confirmation")assertEquals(setOf("new-conservation"),custodyJournal(id).confirmedResolutionUserIds)
                if(scenario in setOf("discrepancy","confirmation"))assertTrue(custodyJournal(id).openDiscrepancySequences.isNotEmpty())
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun custodyJournal(id:String):SamplingSampleJournalVo {
        val p=(service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()
        return (p["samples"] as List<Map<String,Any?>>).single {it["id"]==id}["custodyJournal"] as SamplingSampleJournalVo
    }
    private fun custodyReady(legacy:Boolean=false):String {
        val id=sampleReady();if(legacy)service.analysis("sampling",id,analysis()) else service.analyzeBatch("sampling",id,batch(id))
        if(legacy)service.complete("sampling",completion(setOf(id))) else service.completeCurrent("sampling",review(id))
        return id
    }
    private fun custodyHandover(id:String,external:Boolean=true,minutes:Long=130)=handover(id,external,minutes).copy(previousRevision=custodyJournal(id).revision,quantity=custodyJournal(id).remainingQuantity)
    private fun custodyDiscrepancy(id:String,quantity:String="0.5",minutes:Long=140)=discrepancy(id,quantity,minutes).copy(previousRevision=custodyJournal(id).revision)
    private fun custodyReview(id:String)=discrepancyReview(id).copy(previousRevision=custodyJournal(id).revision,discrepancySequences=custodyJournal(id).openDiscrepancySequences,reviewedAt=at.plusMinutes(150),conservationUserId="new-conservation",keeperUserId="new-keeper")
    private fun custodyConfirm(id:String,minutes:Long=160)=SamplingSampleConfirmationVo(custodyJournal(id).revision,custodyJournal(id).pendingResolutionSequence!!,at.plusMinutes(minutes),"本人核实后续样品保管事实","proof")

    @Test fun `completed samples continue custody without changing completion or original sample revisions`()=fixture {
        val id=custodyReady();val signed=review(id);val original=journal(id)
        val archivesBefore=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        val transfer=custodyHandover(id);val key=service.handoverSample("sampling",id,transfer,true)
        assertEquals(key,service.handoverSample("sampling",id,transfer,true))
        assertEquals(EnumSampleCustody.EXTERNAL,custodyJournal(id).custody.kind)
        assertEquals(original,journal(id));service.completeCurrent("sampling",signed)
        service.handoverSample("sampling",id,custodyHandover(id,false,140),true)
        assertEquals(EnumSampleCustody.INTERNAL,custodyJournal(id).custody.kind)
        assertEquals(BigDecimal("1.00"),custodyJournal(id).remainingQuantity);assertEquals(2,custodyJournal(id).events.size)
        archivesBefore.forEach {(id,v)->assertEquals(v,CollectionPermanentArchiveDao[id].let {it.snapshot to it.digest})}
        assertEquals(1,CollectionSampleDao.all().count());assertEquals(signed.completion,CollectionSamplingPlanDao.all().single().completed)
    }
    @Test fun `legacy completed sample keeps old completion and analysis during later custody`()=fixture {
        val id=custodyReady(true);val a=CollectionSampleDao[id].analysis
        service.handoverSample("sampling",id,custodyHandover(id,false),true)
        service.complete("sampling",completion(setOf(id)))
        assertEquals(a,CollectionSampleDao[id].analysis);assertTrue(journal(id).events.isEmpty());assertEquals(1,custodyJournal(id).events.size)
        assertFalse(CollectionPermanentArchiveDao.all().any {it.phase=="COMPLETION_SAMPLES"})
    }
    @Test fun `post completion custody rejects implementation entry old revision prior time and missing handover proof`()=fixture {
        val id=custodyReady();val v=custodyHandover(id)
        for(bad in listOf(v.copy(previousRevision=journal(id).revision),v.copy(handedAt=at.plusMinutes(110)),v.copy(signedBothPartiesFileIds=""),v.copy(quantity=BigDecimal("0.5")),v.copy(destination=v.destination.copy(externalUnit="unapproved-lab")))) {
            assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,bad,true)}
        }
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,v)}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"NEW","0.1","0.9",140))}
        assertTrue(custodyJournal(id).events.isEmpty())
    }
    @Test fun `post completion custody requires an actual completed project and remaining sample`() {
        fixture {val id=sampleReady();assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id),true)}}
        fixture {actor("operator");val id=sampleReady();service.analyzeBatch("sampling",id,batch(id,used="1.25",left="0"));service.completeCurrent("sampling",review(id))
            assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,custodyHandover(id),true)}
            assertTrue(custodyJournal(id).events.isEmpty())}
    }
    @Test fun `post completion discrepancy preserves historical completion and current people may investigate`()=fixture {
        val id=custodyReady();val completed=CollectionSamplingPlanDao.all().single().completed
        service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id),true)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,custodyHandover(id,false,145),true)}
        doThrow(BadRequestException("原人员已离任")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        service.reviewSampleDiscrepancy("sampling",id,custodyReview(id),true)
        actor("new-conservation");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id),true)
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id,162),true)}
        actor("new-keeper");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id,165),true);actor("operator")
        assertTrue(custodyJournal(id).openDiscrepancySequences.isEmpty())
        service.handoverSample("sampling",id,custodyHandover(id,false,170),true)
        assertEquals(completed,CollectionSamplingPlanDao.all().single().completed)
        assertTrue(journal(id).openDiscrepancySequences.isEmpty());assertEquals(5,custodyJournal(id).events.size)
    }
    @Test fun `later discovered historical shortage keeps actual date and does not pretend old completion had new facts`()=fixture {
        val id=custodyReady();service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id,minutes=90).copy(observedCustody=null),true)
        val state=custodyJournal(id);assertEquals(at.plusMinutes(90),state.events.single().discrepancy!!.observedAt)
        assertEquals(at.plusMinutes(120),state.lastOccurredAt);assertEquals(BigDecimal("1.00"),state.remainingQuantity)
        service.reviewSampleDiscrepancy("sampling",id,custodyReview(id).copy(actualQuantity=BigDecimal("0.5"),identityConfirmed=false,safeToContinue=false,actualCustody=null),true)
        actor("new-conservation");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id),true)
        actor("new-keeper");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id,165),true)
        assertTrue(custodyJournal(id).openDiscrepancySequences.isNotEmpty());assertNotNull(CollectionSamplingPlanDao.all().single().completed)
    }
    @Test fun `post completion new findings invalidate prior review and signatures`()=fixture {
        val id=custodyReady();service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id),true)
        service.reviewSampleDiscrepancy("sampling",id,custodyReview(id),true)
        actor("new-conservation");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id),true);val old=custodyConfirm(id,175)
        actor("operator");service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id,"0.75",170),true)
        actor("new-keeper");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,old,true)}
        assertNull(custodyJournal(id).pendingResolutionSequence);assertTrue(custodyJournal(id).confirmedResolutionUserIds.isEmpty())
    }
    @Test fun `post completion review checks current responsibilities and actual external return proof`()=fixture {
        val id=custodyReady();service.handoverSample("sampling",id,custodyHandover(id),true)
        service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id),true)
        service.reviewSampleDiscrepancy("sampling",id,custodyReview(id),true)
        actor("new-conservation");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id),true)}
        actor("operator");service.reviewSampleDiscrepancy("sampling",id,custodyReview(id).copy(reviewedAt=at.plusMinutes(155),custodyHandoverFileIds="proof"),true)
        actor("new-conservation");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id),true)
        doThrow(BadRequestException("现职责已撤销")).`when`(users).requireRole("new-keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        actor("new-keeper");assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id,165),true)}
        assertEquals(setOf("new-conservation"),custodyJournal(id).confirmedResolutionUserIds)
    }
    @Test fun `broken completion baseline blocks custody and reports original error`()=fixture {
        val id=custodyReady();val v=custodyHandover(id)
        CollectionPermanentArchiveDao.all().single {it.phase=="COMPLETION_SAMPLES"}.digest="damaged"
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,v,true)}
        @Suppress("UNCHECKED_CAST") val p=(service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()
        @Suppress("UNCHECKED_CAST") val row=(p["samples"] as List<Map<String,Any?>>).single()
        assertNull(row["custodyJournal"]);assertNotNull(row["custodyJournalIssue"]);assertNotNull(row["journal"])
    }
    @Test fun `post completion custody requires original plan start and ending archives`() {
        for(key in listOf("PLAN","STARTED","EXTRACTION_ENDED"))fixture {
            actor("operator");val id=custodyReady();val v=custodyHandover(id)
            CollectionPermanentArchiveDao.all().single {it.phase==key}.delete()
            assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,v,true)}
            assertNotNull(CollectionSamplingPlanDao.all().single().completed)
        }
    }
    @Test fun `post completion archive missing sequence cannot silently restore normal custody`()=fixture {
        val id=custodyReady();val first=service.handoverSample("sampling",id,custodyHandover(id),true)
        service.handoverSample("sampling",id,custodyHandover(id,false,140),true);val v=custodyHandover(id,true,150)
        CollectionPermanentArchiveDao.all().single {it.phase==first}.delete()
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,v,true)}
        assertNotNull(CollectionSamplingPlanDao.all().single().completed)
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","rollback","analysis","handover","completed"])
    fun `PostgreSQL serializes splitting and independent portion writes with complete sample review`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();sampleReady()}
            val whole=scenario in setOf("same","different","rollback")
            if(!whole)transaction(db){service.splitSample("sampling",id,split(id));if(scenario=="completed")service.analyzeBatch("sampling",id,batch(id,"A1","0.1","0.4",60).copy(portionKey="A"))}
            val original=transaction(db){CollectionSampleDao[id].content}
            val splitRequest=transaction(db){if(whole)split(id) else split(id,"A",minutes=75,parts=listOf("A1" to "0.2","A2" to "0.3"))}
            val analysisA=transaction(db){batch(id,"A1","0.1","0.4",70).copy(portionKey="A")}
            val analysisB=transaction(db){batch(id,"B1","0.1","0.65",70).copy(portionKey="B")}
            val handover=if(whole)null else transaction(db){portionHandover(id,"B",minutes=75)}
            val completion=if(scenario=="completed")transaction(db){review(id)} else null
            val first=pool.submit<Result<String>> {
                actor("operator")
                try {runCatching {transaction(db) {
                    val result=when(scenario) {
                        "analysis" -> service.analyzeBatch("sampling",id,analysisA)
                        "handover" -> service.handoverSample("sampling",id,handover!!)
                        "completed" -> service.completeCurrent("sampling",completion!!)
                        else -> service.splitSample("sampling",id,splitRequest)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario=="rollback")throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor("operator")
                try {runCatching {transaction(db) {
                    service.detail("sampling") // cache old sample portions and completion state before waiting
                    when(scenario) {
                        "analysis" -> service.analyzeBatch("sampling",id,analysisB)
                        "completed" -> service.handoverSample("sampling",id,handover!!)
                        "different" -> service.splitSample("sampling",id,splitRequest.copy(portions=splitRequest.portions.map {it.copy(key="other-${it.key}")}))
                        else -> service.splitSample("sampling",id,splitRequest)
                    }
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait");release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario=="rollback")assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("different","analysis","handover","completed"))assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            else {two.getOrThrow();if(scenario=="same")assertEquals(one.getOrThrow(),two.getOrThrow())}
            transaction(db) {
                assertEquals(original,CollectionSampleDao[id].content);assertEquals(1,CollectionSampleDao.all().count())
                assertEquals(if(whole)1 else 2,journal(id).events.size);assertEquals(setOf("A","B"),journal(id).portions.keys)
                assertEquals(if(scenario in setOf("analysis","completed"))BigDecimal("1.15") else BigDecimal("1.25"),journal(id).remainingQuantity)
                if(scenario=="completed")assertNotNull(CollectionSamplingPlanDao.all().single().completed) else assertNull(CollectionSamplingPlanDao.all().single().completed)
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @Test fun `tampered disposition originals block confirmation and expose a detail issue`()=fixture {
        val id=sampleReady();val key=service.recordSampleDisposition("sampling",id,disposition(id));val c=dispositionConfirmation(id)
        CollectionPermanentArchiveDao.all().single {it.phase==key}.digest="corrupt"
        actor("conservation");assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,c)}
        val plans=service.detail("sampling")["plans"] as List<Map<String,Any?>>
        val sample=(plans.last()["samples"] as List<Map<String,Any?>>).single()
        assertNotNull(sample["journalIssue"]);assertNull(sample["journal"])
    }
    @Test fun `post completion disposition rejects old phase times and confirmation before actual disposal`()=fixture {
        val id=custodyReady();val v=disposition(id,after=true,used="1.00",left="0",minutes=130)
        assertThrows(BadRequestException::class.java){service.recordSampleDisposition("sampling",id,v.copy(disposedAt=at.plusMinutes(110)),true)}
        service.recordSampleDisposition("sampling",id,v,true);actor("new-conservation")
        assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,after=true,minutes=125),true)}
        assertEquals(BigDecimal("1.00"),custodyJournal(id).remainingQuantity)
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","rollback","confirm","confirm-rollback","analysis","discrepancy"])
    fun `PostgreSQL serializes disposition recording confirmation and competing sample facts`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();sampleReady()};val v=transaction(db){disposition(id)}
            val isConfirmation=scenario in setOf("confirm","confirm-rollback","discrepancy")
            if(isConfirmation)transaction(db){service.recordSampleDisposition("sampling",id,v)}
            val c=if(isConfirmation)transaction(db){dispositionConfirmation(id)} else null
            val a=transaction(db){batch(id,minutes=100)}
            val d=transaction(db){discrepancy(id,minutes=95)}
            val first=pool.submit<Result<String>> {
                actor(if(isConfirmation && scenario!="discrepancy")"conservation" else "operator")
                try {runCatching {transaction(db) {
                    val result=when(scenario) {
                        "confirm","confirm-rollback" -> service.confirmSampleDisposition("sampling",id,c!!)
                        "discrepancy" -> service.recordSampleDiscrepancy("sampling",id,d)
                        else -> service.recordSampleDisposition("sampling",id,v)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario in setOf("rollback","confirm-rollback"))throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(isConfirmation)"keeper" else "operator")
                try {runCatching {transaction(db) {
                    service.detail("sampling")
                    when(scenario) {
                        "confirm","confirm-rollback","discrepancy" -> service.confirmSampleDisposition("sampling",id,c!!)
                        "analysis" -> service.analyzeBatch("sampling",id,a)
                        "different" -> service.recordSampleDisposition("sampling",id,v.copy(methodAndReason="另一次登记"))
                        else -> service.recordSampleDisposition("sampling",id,v)
                    }
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {con->con.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait");release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario in setOf("rollback","confirm-rollback"))assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("different","confirm","analysis","discrepancy"))assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            else {two.getOrThrow();if(scenario=="same")assertEquals(one.getOrThrow(),two.getOrThrow())}
            transaction(db) {
                assertEquals(0,journal(id).analysisCount);assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
                assertEquals(BigDecimal.ZERO,journal(id).nonAnalysisDisposedQuantity)
                if(scenario in setOf("confirm","confirm-rollback")) {
                    assertEquals(1,journal(id).confirmedDispositionUserIds.size)
                    actor(if(scenario=="confirm")"keeper" else "conservation")
                    service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,minutes=100))
                    assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.25"),journal(id).nonAnalysisDisposedQuantity)
                } else if(scenario=="discrepancy") {
                    assertNull(journal(id).pendingDispositionSequence);assertTrue(journal(id).openDiscrepancySequences.isNotEmpty())
                } else assertEquals(1,journal(id).events.size)
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","review-rollback","confirm-race","final-rollback","analysis","new-observation"])
    fun `PostgreSQL serializes final variance decisions personal confirmations and new findings`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));id}
            val v=transaction(db){finalReview(id)}
            val isReview=scenario in setOf("same","different","review-rollback")
            if(!isReview)transaction(db){service.reviewSampleDiscrepancy("sampling",id,v)}
            if(scenario in setOf("final-rollback","analysis","new-observation"))transaction(db) {
                actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id));actor("operator")
            }
            val c=if(!isReview)transaction(db){confirmation(id,115)} else null
            val a=transaction(db){batch(id,"AFTER","0.10","1.15",130)}
            val d=transaction(db){discrepancy(id,"0.25",120)}
            val first=pool.submit<Result<String>> {
                actor(if(isReview || scenario=="new-observation")"operator" else if(scenario=="confirm-race")"conservation" else "keeper")
                try {runCatching {transaction(db) {
                    val result=when {
                        isReview -> service.reviewSampleDiscrepancy("sampling",id,v)
                        scenario=="new-observation" -> service.recordSampleDiscrepancy("sampling",id,d)
                        else -> service.confirmSampleDiscrepancy("sampling",id,c!!)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario in setOf("review-rollback","final-rollback"))throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(isReview || scenario=="analysis")"operator" else "keeper")
                try {runCatching {transaction(db) {
                    service.detail("sampling")
                    when {
                        scenario=="different" -> service.reviewSampleDiscrepancy("sampling",id,v.copy(finalAccounting=v.finalAccounting!!.copy(decisionNo="另一决定")))
                        isReview -> service.reviewSampleDiscrepancy("sampling",id,v)
                        scenario=="analysis" -> service.analyzeBatch("sampling",id,a)
                        else -> service.confirmSampleDiscrepancy("sampling",id,c!!)
                    }
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {con->con.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait");release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario in setOf("review-rollback","final-rollback"))assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("different","confirm-race","analysis","new-observation"))assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            else {two.getOrThrow();if(scenario=="same")assertEquals(one.getOrThrow(),two.getOrThrow())}
            transaction(db) {
                assertEquals(0,journal(id).analysisCount);assertEquals(BigDecimal("1.25"),CollectionSampleDao[id].content.quantity)
                if(scenario=="confirm-race") {
                    assertEquals(1,journal(id).confirmedResolutionUserIds.size);assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
                    actor("keeper");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,120))
                }
                if(scenario in setOf("confirm-race","final-rollback","analysis")) {
                    assertEquals(BigDecimal("0.50"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.75"),journal(id).accountedShortageQuantity);assertEquals(1,journal(id).finalAccountingCount)
                } else {
                    assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity);assertEquals(BigDecimal.ZERO,journal(id).accountedShortageQuantity);assertEquals(0,journal(id).finalAccountingCount)
                    if(scenario=="new-observation")assertNull(journal(id).pendingResolutionSequence) else assertEquals(2,journal(id).events.size)
                }
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","rollback","confirm","final-rollback","custody","analysis","closed-handover"])
    fun `PostgreSQL serializes stopped review personal closure and changing sample custody`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();sampleReady()};val v=transaction(db){stoppedReview()}
            val isReview=scenario in setOf("same","different","rollback","analysis")
            if(!isReview)transaction(db){service.reviewStoppedClosure("sampling",v)}
            if(scenario in setOf("final-rollback","custody","closed-handover"))transaction(db){actor("new-conservation");service.confirmStoppedClosure("sampling",stoppedConfirmation());actor("operator")}
            val c=if(!isReview)transaction(db){stoppedConfirmation(105)} else null
            val a=transaction(db){batch(id,minutes=110)};val h=transaction(db){handover(id,false,102)}
            val first=pool.submit<Result<String>> {
                actor(if(isReview || scenario=="custody")"operator" else if(scenario=="confirm")"new-conservation" else "new-keeper")
                try {runCatching {transaction(db) {
                    val result=when {
                        isReview -> service.reviewStoppedClosure("sampling",v)
                        scenario=="custody" -> service.handoverSample("sampling",id,h)
                        else -> service.confirmStoppedClosure("sampling",c!!)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario in setOf("rollback","final-rollback"))throw IllegalStateException("synthetic transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(isReview || scenario=="closed-handover")"operator" else "new-keeper")
                try {runCatching {transaction(db) {
                    service.detail("sampling")
                    when {
                        scenario=="different" -> service.reviewStoppedClosure("sampling",v.copy(reason="另一项实际停止原因"))
                        scenario=="analysis" -> service.analyzeBatch("sampling",id,a)
                        isReview -> service.reviewStoppedClosure("sampling",v)
                        scenario=="closed-handover" -> service.handoverSample("sampling",id,h)
                        else -> service.confirmStoppedClosure("sampling",c!!)
                    }
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                assertFalse(second.isDone,"Second mutation must wait for first root lock")
                waiting=source.connection.use {con->con.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(!waiting)Thread.sleep(20)
            }
            assertTrue(waiting,"Observed real PostgreSQL lock wait");release.countDown()
            val one=first.get(15,java.util.concurrent.TimeUnit.SECONDS);val two=second.get(15,java.util.concurrent.TimeUnit.SECONDS)
            if(scenario in setOf("rollback","final-rollback"))assertTrue(one.exceptionOrNull() is IllegalStateException) else one.getOrThrow()
            if(scenario in setOf("different","confirm","custody","analysis","closed-handover"))assertTrue(two.exceptionOrNull() is BadRequestException,two.toString())
            else {two.getOrThrow();if(scenario=="same")assertEquals(one.getOrThrow(),two.getOrThrow())}
            transaction(db) {
                assertNull(CollectionSampleDao[id].analysis);assertEquals(0,journal(id).analysisCount);assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
                assertNull(CollectionSamplingPlanDao.all().single().completed)
                if(scenario=="confirm") {
                    assertEquals(1,stoppedState().confirmedUserIds.size);assertNull(stoppedState().closedAt)
                    actor("new-keeper");service.confirmStoppedClosure("sampling",stoppedConfirmation(110))
                }
                if(scenario in setOf("confirm","final-rollback","closed-handover")) {
                    assertNotNull(stoppedState().closedAt);assertEquals(2,stoppedState().confirmedUserIds.size);assertEquals(BigDecimal("1.25"),custodyJournal(id).remainingQuantity)
                } else {
                    assertNull(stoppedState().closedAt)
                    if(scenario=="custody")assertFalse(stoppedState().reviewCurrent) else assertEquals(1,stoppedState().events.size)
                }
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun stoppedState():SamplingStoppedClosureVo=((service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()["stoppedClosure"] as SamplingStoppedClosureVo)
    private fun stoppedReview(stopped:Long=60,reviewed:Long=90)=SamplingStoppedReviewVo(stoppedState().revision,stoppedState().basisRevision,CollectionSamplingPlanDao.all().maxBy {it.version}.id.value,
        at.plusMinutes(stopped),at.plusMinutes(reviewed),"operator","new-conservation","new-keeper","实际停止原因","实际已取样，尚未分析","原方案研究尚未实施，后续须另核实",
        "原件实际现状和养护","全部样品当前保管及复查安排","实际停止依据及后续适用方案手续","M1/停作档案","proof","proof","proof")
    private fun stoppedConfirmation(minutes:Long=100)=SamplingStoppedConfirmationVo(stoppedState().revision,stoppedState().reviewSequence!!,at.plusMinutes(minutes),"本人核对停作事实、未完成研究及样品保管","proof")
    private fun bothStoppedConfirm(minutes:Long=100) {
        actor("new-conservation");service.confirmStoppedClosure("sampling",stoppedConfirmation(minutes))
        actor("new-keeper");service.confirmStoppedClosure("sampling",stoppedConfirmation(minutes+5));actor("operator")
    }
    @Test fun `stopped project with real unanalysed samples closes without false research completion`()=fixture {
        val id=sampleReady();val original=CollectionSampleDao[id].content;val initial=journal(id);val v=stoppedReview()
        val key=service.reviewStoppedClosure("sampling",v);assertTrue(stoppedState().reviewCurrent);assertNull(stoppedState().closedAt)
        actor("operator");assertThrows(BadRequestException::class.java){service.confirmStoppedClosure("sampling",stoppedConfirmation())}
        actor("new-conservation");val c=stoppedConfirmation();val ck=service.confirmStoppedClosure("sampling",c)
        assertEquals(ck,service.confirmStoppedClosure("sampling",c));assertNull(stoppedState().closedAt)
        assertThrows(BadRequestException::class.java){service.confirmStoppedClosure("sampling",stoppedConfirmation(101))}
        actor("new-keeper");service.confirmStoppedClosure("sampling",stoppedConfirmation(105));actor("operator")
        assertNotNull(stoppedState().closedAt);assertEquals(key,service.reviewStoppedClosure("sampling",v))
        assertNull(CollectionSamplingPlanDao.all().single().completed);assertNull(CollectionSampleDao[id].analysis)
        assertEquals(initial,journal(id));assertEquals(original,CollectionSampleDao[id].content)
        assertEquals(BigDecimal("1.25"),custodyJournal(id).remainingQuantity);assertEquals(0,custodyJournal(id).analysisCount)
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview(reviewed=110))}
    }
    @Test fun `recorded stopping freezes both analysis entry points before final confirmation`()=fixture {
        val id=sampleReady();service.reviewStoppedClosure("sampling",stoppedReview())
        assertThrows(BadRequestException::class.java){service.analysis("sampling",id,analysis())}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,minutes=110))}
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        assertEquals(0,journal(id).analysisCount)
    }
    @Test fun `custody changes invalidate stop review and preserve earlier signed confirmation`()=fixture {
        val id=sampleReady();service.reviewStoppedClosure("sampling",stoppedReview());actor("new-conservation");service.confirmStoppedClosure("sampling",stoppedConfirmation());actor("operator")
        val old=stoppedConfirmation(110);service.handoverSample("sampling",id,handover(id,false,102))
        assertFalse(stoppedState().reviewCurrent);actor("new-keeper")
        assertThrows(BadRequestException::class.java){service.confirmStoppedClosure("sampling",old)};actor("operator")
        assertEquals(2,stoppedState().events.size)
        service.reviewStoppedClosure("sampling",stoppedReview(reviewed=110));assertTrue(stoppedState().confirmedUserIds.isEmpty());bothStoppedConfirm(115)
        assertEquals(5,stoppedState().events.size);assertNotNull(stoppedState().closedAt)
    }
    @Test fun `stop closure rejects absent evidence invalid times actors and outdated basis`()=fixture {
        sampleReady();val v=stoppedReview()
        listOf(v.copy(stoppedAt=at.plusMinutes(39)),v.copy(stoppedAt=at.plusMinutes(91)),v.copy(reviewedAt=LocalDateTime.now().plusDays(1)),v.copy(recorderUserId="other"),
            v.copy(conservationUserId="new-keeper"),v.copy(planId="other"),v.copy(previousRevision="old"),v.copy(basisRevision="old"),v.copy(reason=""),v.copy(unfinishedWork=""),
            v.copy(stoppingBasisFileIds=""),v.copy(actualWorkAndCustodyFileIds=""),v.copy(signedReviewFileIds=""),v.copy(authorityAndFutureWork="")
        ).forEach {bad->assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",bad)}}
        assertTrue(stoppedState().events.isEmpty())
    }
    @Test fun `no sample cases keep their separate closure instead of being mislabeled stopped sample research`()=fixture {
        noSampleReady();assertFalse(stoppedState().eligible)
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview())}
        service.reviewNoSampleClosure("sampling",noSampleReview());assertTrue(noSampleState().reviewCurrent)
    }
    @Test fun `normally completed projects cannot acquire a conflicting stop closure`()=fixture {
        custodyReady();assertFalse(stoppedState().eligible)
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview(reviewed=130))}
        assertNotNull(CollectionSamplingPlanDao.all().single().completed)
    }
    @Test fun `external remaining samples must really return before stop closure`()=fixture {
        val id=sampleReady();service.handoverSample("sampling",id,handover(id))
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview())}
        service.handoverSample("sampling",id,handover(id,false,80));service.reviewStoppedClosure("sampling",stoppedReview());bothStoppedConfirm()
        assertEquals(EnumSampleCustody.INTERNAL,custodyJournal(id).custody.kind)
    }
    @Test fun `unresolved sample findings cannot be hidden by stopped closure`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id,minutes=70))
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview())}
        assertTrue(journal(id).openDiscrepancySequences.isNotEmpty())
    }
    @Test fun `pending non analysis disposition cannot be hidden by stopped closure`()=fixture {
        val id=sampleReady();service.recordSampleDisposition("sampling",id,disposition(id))
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview())}
        assertTrue(journal(id).dispositionPending)
    }
    @Test fun `stop closure does not release unresolved damage to the original object`()=fixture {
        ready();val id=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="实际损伤",exceptionFileIds="proof"))
        service.endExtraction("sampling",end(setOf(id)).copy(safeToReleaseOriginal=false))
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview())}
        assertNull(CollectionSamplingPlanDao.all().single().incidentClearance)
    }
    @Test fun `stop confirmation checks live duties and current remaining custodian`()=fixture {
        val id=sampleReady();service.reviewStoppedClosure("sampling",stoppedReview())
        actor("new-conservation");service.confirmStoppedClosure("sampling",stoppedConfirmation());actor("new-keeper")
        doThrow(BadRequestException("retired custodian")).`when`(users).requireMuseumMember("custodian","museum")
        assertThrows(BadRequestException::class.java){service.confirmStoppedClosure("sampling",stoppedConfirmation(105))}
        doNothing().`when`(users).requireMuseumMember("custodian","museum")
        doThrow(BadRequestException("revoked role")).`when`(users).requireRole("new-keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        assertThrows(BadRequestException::class.java){service.confirmStoppedClosure("sampling",stoppedConfirmation(105))}
        assertNull(stoppedState().closedAt);assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
    }
    @Test fun `stopped sample can split and change custody afterward without rewriting the frozen stop basis`()=fixture {
        val id=sampleReady();service.reviewStoppedClosure("sampling",stoppedReview());bothStoppedConfirm()
        val closure=stoppedState();val frozen=journal(id)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id,minutes=130))}
        service.splitSample("sampling",id,split(id,after=true,minutes=130),true)
        service.handoverSample("sampling",id,portionHandover(id,"B",after=true,minutes=140),true)
        assertEquals(closure,stoppedState());assertEquals(frozen,journal(id));assertEquals(setOf("A","B"),custodyJournal(id).portions.keys)
        assertEquals(EnumSampleCustody.EXTERNAL,custodyJournal(id).portions.getValue("B").custody.kind)
        assertEquals(0,custodyJournal(id).analysisCount)
    }
    @Test fun `stopping time cannot precede actual legacy analysis but can precede later custody`()=fixture {
        val id=sampleReady();service.analysis("sampling",id,analysis().copy(analyzedAt=at.plusMinutes(70)))
        service.handoverSample("sampling",id,handover(id,false,80))
        assertThrows(BadRequestException::class.java){service.reviewStoppedClosure("sampling",stoppedReview(stopped=60))}
        service.reviewStoppedClosure("sampling",stoppedReview(stopped=75));bothStoppedConfirm()
        assertEquals(1,custodyJournal(id).analysisCount);assertNotNull(CollectionSampleDao[id].analysis)
    }
    @Test fun `tampered stopped review blocks confirmation analysis and post closure custody`()=fixture {
        val id=sampleReady();val key=service.reviewStoppedClosure("sampling",stoppedReview());val c=stoppedConfirmation()
        CollectionPermanentArchiveDao.all().single {it.phase==key}.digest="corrupt"
        actor("new-conservation");assertThrows(BadRequestException::class.java){service.confirmStoppedClosure("sampling",c)};actor("operator")
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id))}
        val plans=service.detail("sampling")["plans"] as List<Map<String,Any?>>;assertNotNull(plans.last()["stoppedClosureIssue"])
    }

    private fun finalAccounting(kind:EnumSampleFinalAccounting=EnumSampleFinalAccounting.SHORTAGE,minutes:Long=95)=SamplingSampleFinalAccountingVo(
        kind,at.plusMinutes(minutes),"实际有权处理决定及适用制度","实际决定编号","proof","据实持续查找、责任及复查安排","proof")
    private fun finalReview(id:String,quantity:String="0.50",kind:EnumSampleFinalAccounting=EnumSampleFinalAccounting.SHORTAGE)=discrepancyReview(id,BigDecimal(quantity)).copy(
        actualMeasures="已经实施的查找保护及实际处理措施，未恢复数量不编造追回",finalAccounting=finalAccounting(kind))
    @Test fun `shortage accounting needs two real confirmations and keeps loss separate from analysis`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id));val original=CollectionSampleDao[id].content
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id));val v=finalReview(id);val key=service.reviewSampleDiscrepancy("sampling",id,v)
        assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity);assertEquals(BigDecimal.ZERO,journal(id).accountedShortageQuantity)
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id));assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity)
        actor("keeper");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,115));actor("operator")
        assertEquals(BigDecimal("0.50"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.50"),journal(id).accountedShortageQuantity)
        assertEquals(1,journal(id).finalAccountingCount);assertEquals(1,journal(id).analysisCount);assertEquals(original,CollectionSampleDao[id].content)
        assertTrue(journal(id).openDiscrepancySequences.isEmpty());assertEquals(key,service.reviewSampleDiscrepancy("sampling",id,v))
        service.completeCurrent("sampling",review(id))
    }
    @Test fun `entire unrecovered shortage can be recorded without false identity safety return or analysis`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0").copy(identityConfirmed=false,conditionSafe=false,observedCustody=null))
        service.reviewSampleDiscrepancy("sampling",id,finalReview(id,"0").copy(identityConfirmed=false,safeToContinue=false,actualCustody=null));bothConfirm(id)
        assertEquals(BigDecimal("1.25"),journal(id).accountedShortageQuantity);assertEquals(0,journal(id).analysisCount);assertTrue(journal(id).readyForCompletion)
        service.completeCurrent("sampling",review(id));assertNull(CollectionSampleDao[id].analysis)
        assertEquals(BigDecimal("1.25"),custodyJournal(id).accountedShortageQuantity)
    }
    @Test fun `excess accounting requires authentic safe remaining material and exact direction`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"1.50"));val v=finalReview(id,"1.50",EnumSampleFinalAccounting.EXCESS)
        listOf(v.copy(identityConfirmed=false),v.copy(safeToContinue=false),v.copy(actualCustody=null),v.copy(actualQuantity=BigDecimal("1.25")),
            v.copy(finalAccounting=finalAccounting(EnumSampleFinalAccounting.SHORTAGE))).forEach {bad->assertThrows(BadRequestException::class.java){service.reviewSampleDiscrepancy("sampling",id,bad)}}
        service.reviewSampleDiscrepancy("sampling",id,v);bothConfirm(id)
        assertEquals(BigDecimal("1.50"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.25"),journal(id).accountedExcessQuantity)
        assertEquals(BigDecimal("1.25"),CollectionSampleDao[id].content.quantity);assertFalse(journal(id).readyForCompletion)
    }
    @Test fun `final accounting rejects absent decision follow up wrong direction and decision before facts`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));val v=finalReview(id);val f=v.finalAccounting!!
        listOf(v.copy(finalAccounting=f.copy(decisionFileIds="")),v.copy(finalAccounting=f.copy(authorityAndBasis="")),v.copy(finalAccounting=f.copy(decisionNo="")),
            v.copy(finalAccounting=f.copy(followUpResponsibility="")),v.copy(finalAccounting=f.copy(followUpFileIds="")),v.copy(finalAccounting=f.copy(decidedAt=at.plusMinutes(89))),
            v.copy(finalAccounting=f.copy(decidedAt=at.plusMinutes(101))),v.copy(finalAccounting=f.copy(kind=EnumSampleFinalAccounting.EXCESS)),v.copy(actualQuantity=BigDecimal("1.25")),
            v.copy(actualQuantity=BigDecimal("-1")),v.copy(identityConfirmed=false),v.copy(safeToContinue=false),v.copy(actualCustody=null)
        ).forEach {bad->assertThrows(BadRequestException::class.java){service.reviewSampleDiscrepancy("sampling",id,bad)}}
        assertNull(journal(id).pendingResolutionSequence);assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
    }
    @Test fun `disposition discovered during discrepancy is accounted as non analysis rather than unexplained shortage`()=fixture {
        val id=sampleReady();service.recordSampleDisposition("sampling",id,disposition(id));service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"1.00"))
        assertNull(journal(id).pendingDispositionSequence)
        service.reviewSampleDiscrepancy("sampling",id,finalReview(id,"1.00",EnumSampleFinalAccounting.NON_ANALYSIS_DISPOSITION));bothConfirm(id)
        assertEquals(BigDecimal("0.25"),journal(id).nonAnalysisDisposedQuantity);assertEquals(BigDecimal.ZERO,journal(id).accountedShortageQuantity)
        assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity);assertEquals(0,journal(id).analysisCount)
    }
    @Test fun `later recovery appends excess accounting without erasing the earlier unrecovered shortage`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0"));service.reviewSampleDiscrepancy("sampling",id,finalReview(id,"0"));bothConfirm(id)
        val originalEvents=journal(id).events
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0.50",120))
        service.reviewSampleDiscrepancy("sampling",id,finalReview(id,"0.50",EnumSampleFinalAccounting.EXCESS).copy(reviewedAt=at.plusMinutes(140),finalAccounting=finalAccounting(EnumSampleFinalAccounting.EXCESS,130)))
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,145));actor("keeper");service.confirmSampleDiscrepancy("sampling",id,confirmation(id,150));actor("operator")
        assertEquals(originalEvents,journal(id).events.take(originalEvents.size));assertEquals(BigDecimal("1.25"),journal(id).accountedShortageQuantity)
        assertEquals(BigDecimal("0.50"),journal(id).accountedExcessQuantity);assertEquals(BigDecimal("0.50"),journal(id).remainingQuantity);assertEquals(2,journal(id).finalAccountingCount)
    }
    @Test fun `final accounting cannot substitute for real custody handover when remaining material changes keeper`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));val v=finalReview(id).copy(actualCustody=SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"新柜",userId="new-custodian"))
        service.reviewSampleDiscrepancy("sampling",id,v);actor("conservation")
        assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,confirmation(id))};actor("operator")
        service.reviewSampleDiscrepancy("sampling",id,v.copy(previousRevision=journal(id).revision,custodyHandoverFileIds="proof"));bothConfirm(id)
        assertEquals("new-custodian",journal(id).custody.userId);assertEquals(BigDecimal("0.50"),journal(id).remainingQuantity)
    }
    @Test fun `final accounting is local to the selected portion and survives later resplitting`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id));service.recordSampleDiscrepancy("sampling",id,portionDiscrepancy(id,"A"))
        val v=portionReview(id,"A").copy(actualQuantity=BigDecimal("0.20"),finalAccounting=finalAccounting())
        service.reviewSampleDiscrepancy("sampling",id,v);actor("conservation")
        assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,portionConfirmation(id,"A").copy(portionKey="B"))}
        service.confirmSampleDiscrepancy("sampling",id,portionConfirmation(id,"A"));actor("keeper");service.confirmSampleDiscrepancy("sampling",id,portionConfirmation(id,"A",minutes=115));actor("operator")
        assertEquals(BigDecimal("0.95"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.75"),journal(id).portions.getValue("B").remainingQuantity)
        service.splitSample("sampling",id,split(id,"A",minutes=120,parts=listOf("A1" to "0.10","A2" to "0.10")))
        assertEquals(BigDecimal("0.30"),journal(id).accountedShortageQuantity);assertEquals(1,journal(id).finalAccountingCount)
    }
    @Test fun `post completion final accounting preserves frozen completion and checks current designated people`()=fixture {
        val id=custodyReady();val frozen=journal(id);val completed=CollectionSamplingPlanDao.all().single().completed
        service.recordSampleDiscrepancy("sampling",id,custodyDiscrepancy(id,"0"),true)
        service.reviewSampleDiscrepancy("sampling",id,custodyReview(id).copy(actualQuantity=BigDecimal.ZERO,actualCustody=null,identityConfirmed=false,safeToContinue=false,finalAccounting=finalAccounting(minutes=145)),true)
        actor("new-conservation");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id),true);actor("new-keeper");service.confirmSampleDiscrepancy("sampling",id,custodyConfirm(id,165),true);actor("operator")
        assertEquals(frozen,journal(id));assertEquals(completed,CollectionSamplingPlanDao.all().single().completed)
        assertEquals(BigDecimal("1.00"),custodyJournal(id).accountedShortageQuantity);assertEquals(BigDecimal.ZERO,custodyJournal(id).remainingQuantity)
    }
    @Test fun `additional findings invalidate final accounting confirmations without reducing expected stock`()=fixture {
        val id=sampleReady();service.recordSampleDiscrepancy("sampling",id,discrepancy(id));service.reviewSampleDiscrepancy("sampling",id,finalReview(id))
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,confirmation(id));actor("operator");val old=confirmation(id,120)
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0.25",115));actor("keeper")
        assertThrows(BadRequestException::class.java){service.confirmSampleDiscrepancy("sampling",id,old.copy(previousRevision=journal(id).revision))}
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity);assertEquals(0,journal(id).finalAccountingCount)
    }
    @Test fun `final accounting waits for safe extraction end and never clears an original incident`()=fixture {
        ready();val id=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="意外损伤",exceptionFileIds="proof"))
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id))
        assertThrows(BadRequestException::class.java){service.reviewSampleDiscrepancy("sampling",id,finalReview(id))}
        service.endExtraction("sampling",end(setOf(id)).copy(safeToReleaseOriginal=false))
        assertThrows(BadRequestException::class.java){service.reviewSampleDiscrepancy("sampling",id,finalReview(id))}
        assertNull(CollectionSamplingPlanDao.all().single().incidentClearance)
    }
    @Test fun `micro shortage accounting preserves exact quantity and string values`()=fixture {
        ready();val id=service.take("sampling",take().copy(quantity=BigDecimal("1E-400")));service.endExtraction("sampling",end(setOf(id)))
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"5E-401"));service.reviewSampleDiscrepancy("sampling",id,finalReview(id,"5E-401"));bothConfirm(id)
        val tree=JsonUtils.mapper.readTree(JsonUtils.toJson(journal(id)));assertTrue(tree["accountedShortageQuantity"].isString)
        assertEquals(0,BigDecimal(tree["accountedShortageQuantity"].stringValue()).compareTo(BigDecimal("5E-401")))
    }

    private fun disposition(id:String,key:String?=null,after:Boolean=false,used:String="0.25",left:String="1.00",minutes:Long=70)=SamplingSampleDispositionVo(
        portionState(id,after).revision,at.plusMinutes(minutes),"operator",if(after)"new-conservation" else "conservation",if(after)"new-keeper" else "keeper",
        BigDecimal(used),BigDecimal(left),"处置后剩余样品柜","已批准的实际非分析处理及原因","实际适用制度、权限及批准依据",
        "实际处理结果和去向索引","实际现状及剩余物保管安排","proof","proof","proof",key)
    private fun dispositionConfirmation(id:String,key:String?=null,after:Boolean=false,minutes:Long=90):SamplingSampleDispositionConfirmationVo {
        val state=portionState(id,after);val target=SamplingSampleJournal.target(state,key)
        return SamplingSampleDispositionConfirmationVo(state.revision,target.pendingDispositionSequence!!,at.plusMinutes(minutes),"本人核对处置依据和实际数量去向","proof",key)
    }
    private fun confirmDispositionBoth(id:String,key:String?=null,after:Boolean=false,minutes:Long=90) {
        actor(if(after)"new-conservation" else "conservation");service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,key,after,minutes),after)
        actor(if(after)"new-keeper" else "keeper");service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,key,after,minutes+1),after);actor("operator")
    }
    @Test fun `non analysis disposition changes quantity only after two real confirmations and never creates analysis`()=fixture {
        val id=sampleReady();val original=CollectionSampleDao[id].content;val v=disposition(id)
        val key=service.recordSampleDisposition("sampling",id,v)
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity);assertTrue(journal(id).dispositionPending);assertFalse(journal(id).readyForCompletion)
        actor("operator");assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id))}
        actor("conservation");val c=dispositionConfirmation(id);val ck=service.confirmSampleDisposition("sampling",id,c)
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
        assertEquals(ck,service.confirmSampleDisposition("sampling",id,c))
        assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id))}
        actor("keeper");service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,minutes=91));actor("operator")
        assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.25"),journal(id).nonAnalysisDisposedQuantity)
        assertFalse(journal(id).dispositionPending);assertEquals(0,journal(id).analysisCount);assertNull(CollectionSampleDao[id].analysis)
        assertEquals(original,CollectionSampleDao[id].content);assertEquals(key,service.recordSampleDisposition("sampling",id,v))
        service.analyzeBatch("sampling",id,batch(id,"real-analysis","0.1","0.90",100))
        service.completeCurrent("sampling",review(id));assertEquals(1,journal(id).analysisCount)
    }
    @Test fun `fully disposed sample can complete truthfully without inventing any analysis`()=fixture {
        val id=sampleReady();service.recordSampleDisposition("sampling",id,disposition(id,used="1.25",left="0"));confirmDispositionBoth(id)
        assertTrue(journal(id).readyForCompletion);assertEquals(0,journal(id).analysisCount)
        service.completeCurrent("sampling",review(id));assertNotNull(CollectionSamplingPlanDao.all().single().completed)
        assertNull(CollectionSampleDao[id].analysis);assertEquals(BigDecimal("0"),custodyJournal(id).remainingQuantity)
    }
    @Test fun `invalid disposition quantities evidence actors and dates cannot reduce stock`()=fixture {
        val id=sampleReady();val v=disposition(id)
        listOf(v.copy(disposedQuantity=BigDecimal.ZERO),v.copy(disposedQuantity=BigDecimal("-1")),v.copy(remainingQuantity=BigDecimal("-1")),
            v.copy(remainingQuantity=BigDecimal("1.25")),v.copy(remainingLocation=""),v.copy(authorizationFileIds=""),v.copy(quantityAndResultFileIds=""),
            v.copy(signedDispositionFileIds=""),v.copy(authorityAndBasis=""),v.copy(actualResultAndDestination=""),v.copy(recorderUserId="other"),
            v.copy(conservationUserId="keeper"),v.copy(disposedAt=at),v.copy(disposedAt=LocalDateTime.now().plusDays(1)),v.copy(previousRevision="old")
        ).forEach {bad->assertThrows(BadRequestException::class.java){service.recordSampleDisposition("sampling",id,bad)}}
        assertTrue(journal(id).events.isEmpty());assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
    }
    @Test fun `pending disposition blocks analysis handover splitting completion and competing disposition`()=fixture {
        val id=sampleReady();service.recordSampleDisposition("sampling",id,disposition(id))
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,minutes=80))}
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id,minutes=80))}
        assertThrows(BadRequestException::class.java){service.splitSample("sampling",id,split(id,minutes=80))}
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        assertThrows(BadRequestException::class.java){service.recordSampleDisposition("sampling",id,disposition(id,minutes=80))}
    }
    @Test fun `new discrepancy invalidates pending disposition confirmations and never writes off shortage`()=fixture {
        val id=sampleReady();service.recordSampleDisposition("sampling",id,disposition(id));actor("conservation")
        service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id));actor("operator");val old=dispositionConfirmation(id)
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,"0.50",95))
        assertNull(journal(id).pendingDispositionSequence);assertTrue(journal(id).confirmedDispositionUserIds.isEmpty())
        actor("keeper");assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,old.copy(previousRevision=journal(id).revision))};actor("operator")
        assertThrows(BadRequestException::class.java){service.recordSampleDisposition("sampling",id,disposition(id,minutes=100))}
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity);assertEquals(BigDecimal.ZERO,journal(id).nonAnalysisDisposedQuantity)
    }
    @Test fun `different portions retain independent disposition quantities while sharing all normal action restrictions`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id));service.recordSampleDisposition("sampling",id,disposition(id,"A",used="0.20",left="0.30"))
        assertTrue(journal(id).dispositionPending)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,portionHandover(id,"B",minutes=80))}
        actor("conservation");val wrong=dispositionConfirmation(id,"A").copy(portionKey="B")
        assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,wrong)};actor("operator")
        confirmDispositionBoth(id,"A")
        assertEquals(BigDecimal("0.30"),journal(id).portions.getValue("A").remainingQuantity)
        assertEquals(BigDecimal("0.75"),journal(id).portions.getValue("B").remainingQuantity)
        assertEquals(BigDecimal("0.20"),journal(id).nonAnalysisDisposedQuantity);assertEquals(BigDecimal("1.05"),journal(id).remainingQuantity)
        service.splitSample("sampling",id,split(id,"A",minutes=100,parts=listOf("A1" to "0.10","A2" to "0.20")))
        assertEquals(BigDecimal("0.20"),journal(id).nonAnalysisDisposedQuantity)
        service.recordSampleDisposition("sampling",id,disposition(id,"A1",used="0.10",left="0",minutes=105));confirmDispositionBoth(id,"A1",minutes=110)
        assertEquals(BigDecimal("0.30"),journal(id).nonAnalysisDisposedQuantity);assertEquals(BigDecimal("0.95"),journal(id).remainingQuantity)
    }
    @Test fun `post completion disposition freezes pre completion originals and remains separate from analysis`()=fixture {
        val id=custodyReady();val frozen=journal(id);val completed=CollectionSamplingPlanDao.all().single().completed
        val v=disposition(id,after=true,used="1.00",left="0",minutes=130)
        assertThrows(BadRequestException::class.java){service.recordSampleDisposition("sampling",id,v)}
        service.recordSampleDisposition("sampling",id,v,true);confirmDispositionBoth(id,after=true,minutes=140)
        assertEquals(frozen,journal(id));assertEquals(completed,CollectionSamplingPlanDao.all().single().completed)
        assertEquals(BigDecimal.ZERO,custodyJournal(id).remainingQuantity);assertEquals(BigDecimal("1.00"),custodyJournal(id).nonAnalysisDisposedQuantity)
        assertEquals(1,custodyJournal(id).analysisCount)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,custodyHandover(id,minutes=150),true)}
    }
    @Test fun `disposition confirmation checks live duties and current source revision`()=fixture {
        val id=sampleReady();service.recordSampleDisposition("sampling",id,disposition(id));val c=dispositionConfirmation(id)
        actor("conservation");service.confirmSampleDisposition("sampling",id,c);actor("keeper")
        assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,c)}
        doThrow(BadRequestException("revoked")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        assertThrows(BadRequestException::class.java){service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,minutes=91))}
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
    }
    @Test fun `disposition after external custody stays accountable and does not fake return`()=fixture {
        val id=sampleReady();service.handoverSample("sampling",id,handover(id))
        service.recordSampleDisposition("sampling",id,disposition(id));confirmDispositionBoth(id)
        assertEquals(EnumSampleCustody.EXTERNAL,journal(id).custody.kind);assertFalse(journal(id).readyForCompletion)
        service.analyzeBatch("sampling",id,batch(id,used="0",left="1.00",minutes=100))
        assertFalse(journal(id).readyForCompletion);assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        service.handoverSample("sampling",id,handover(id,false,110));assertTrue(journal(id).readyForCompletion)
    }
    @Test fun `micro disposition preserves exact decimal amounts through string serialization`()=fixture {
        ready();val quantity=BigDecimal("1E-400");val id=service.take("sampling",take().copy(quantity=quantity));service.endExtraction("sampling",end(setOf(id)))
        service.recordSampleDisposition("sampling",id,disposition(id,used="5E-401",left="5E-401"));confirmDispositionBoth(id)
        assertEquals(0,journal(id).remainingQuantity.compareTo(BigDecimal("5E-401")))
        val tree=JsonUtils.mapper.readTree(JsonUtils.toJson(journal(id)))
        assertTrue(tree["nonAnalysisDisposedQuantity"].isString)
        assertEquals(0,BigDecimal(tree["nonAnalysisDisposedQuantity"].stringValue()).compareTo(BigDecimal("5E-401")))
    }

    private fun portionState(id:String,after:Boolean=false)=if(after)custodyJournal(id) else journal(id)
    private fun split(id:String,key:String?=null,after:Boolean=false,minutes:Long=50,parts:List<Pair<String,String>> = listOf("A" to "0.50","B" to "0.75"))=SamplingSampleSplitVo(
        portionState(id,after).revision,at.plusMinutes(minutes),"operator",parts.map {SamplingSplitPortionVo(it.first,BigDecimal(it.second),"实际分份柜-${it.first}")},
        "实际需要分份保存及分析，按原方案保护措施办理","逐份核对现状和数量","proof","proof","proof",key)
    private fun portionHandover(id:String,key:String,external:Boolean=true,after:Boolean=false,minutes:Long=70)=handover(id,external,minutes).copy(
        previousRevision=portionState(id,after).revision,quantity=portionState(id,after).portions.getValue(key).remainingQuantity,portionKey=key)
    private fun portionDiscrepancy(id:String,key:String,after:Boolean=false,minutes:Long=80)=discrepancy(id,"0.10",minutes).copy(previousRevision=portionState(id,after).revision,portionKey=key)
    private fun portionReview(id:String,key:String,after:Boolean=false,minutes:Long=100):SamplingSampleResolutionVo {
        val state=portionState(id,after);val target=state.portions.getValue(key)
        return discrepancyReview(id).copy(previousRevision=state.revision,discrepancySequences=target.openDiscrepancySequences,actualQuantity=target.remainingQuantity,
            reviewedAt=at.plusMinutes(minutes),portionKey=key)
    }
    private fun portionConfirmation(id:String,key:String,after:Boolean=false,minutes:Long=110)=SamplingSampleConfirmationVo(
        portionState(id,after).revision,portionState(id,after).portions.getValue(key).pendingResolutionSequence!!,at.plusMinutes(minutes),"本人逐项核实该分份","proof",key)

    @Test fun `split preserves sample identity quantity and custody while each portion can be analyzed and returned`()=fixture {
        val id=sampleReady();val original=CollectionSampleDao[id].content
        service.splitSample("sampling",id,split(id))
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity);assertEquals(setOf("A","B"),journal(id).portions.keys)
        assertEquals(setOf("custodian"),journal(id).portions.values.map {it.custody.userId}.toSet())
        assertEquals(0,journal(id).analysisCount);assertFalse(journal(id).readyForCompletion)
        service.analyzeBatch("sampling",id,batch(id,"A1","0.10","0.40",60).copy(portionKey="A"))
        // B was handed before A's analysis; independent portion chronology must retain the actual time.
        service.handoverSample("sampling",id,portionHandover(id,"B",minutes=55))
        assertEquals(BigDecimal("1.15"),journal(id).remainingQuantity);assertFalse(journal(id).readyForCompletion)
        assertEquals(at.plusMinutes(60),journal(id).lastOccurredAt)
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        service.handoverSample("sampling",id,portionHandover(id,"B",false,minutes=85))
        assertTrue(journal(id).readyForCompletion);service.completeCurrent("sampling",review(id))
        assertEquals(1,CollectionSampleDao.all().count());assertEquals(original,CollectionSampleDao[id].content)
        assertEquals(1,journal(id).analysisCount);assertEquals(BigDecimal("0.40"),journal(id).portions.getValue("A").remainingQuantity)
    }
    @Test fun `micro portions preserve exact decimal conservation without conversion to floating point`()=fixture {
        val id=sampleReady();val tiny=BigDecimal("1E-400");val other=BigDecimal("1.25")-tiny
        service.splitSample("sampling",id,split(id,parts=listOf("MICRO" to tiny.toString(),"REST" to other.toPlainString())))
        assertEquals(0,journal(id).remainingQuantity.compareTo(BigDecimal("1.25")))
        assertEquals(tiny,journal(id).portions.getValue("MICRO").remainingQuantity)
        val tree=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(journal(id)))
        assertTrue(tree["portions"]["MICRO"]["remainingQuantity"].isString)
        assertEquals("1E-400",tree["portions"]["MICRO"]["remainingQuantity"].stringValue())
        service.analyzeBatch("sampling",id,batch(id,"MICRO-ANALYSIS","0",tiny.toString(),60).copy(portionKey="MICRO"))
        assertTrue(journal(id).readyForCompletion);assertEquals(0,journal(id).remainingQuantity.compareTo(BigDecimal("1.25")))
    }
    @Test fun `split must preserve total positive quantities unique permanent portion identifiers and required originals`()=fixture {
        val id=sampleReady();val v=split(id)
        for(bad in listOf(v.copy(portions=listOf(v.portions.first())),v.copy(portions=listOf(v.portions.first(),v.portions.first())),
            v.copy(portions=listOf(v.portions[0].copy(quantity=BigDecimal.ZERO),v.portions[1].copy(quantity=BigDecimal("1.25")))),
            v.copy(portions=listOf(v.portions[0].copy(quantity=BigDecimal("-0.5")),v.portions[1].copy(quantity=BigDecimal("1.75")))),
            v.copy(portions=v.portions.map {it.copy(quantity=BigDecimal.ONE)}),v.copy(portions=listOf(v.portions[0].copy(key="invalid/key"),v.portions[1])),
            v.copy(portions=listOf(v.portions[0].copy(location=""),v.portions[1])),v.copy(recorderUserId="other"),v.copy(basisFileIds=""),v.copy(markingFileIds=""),v.copy(signedSplitAndCustodyFileIds=""),
            v.copy(splitAt=at.plusMinutes(30)),v.copy(splitAt=LocalDateTime.now().plusDays(1)),v.copy(previousRevision="stale"))) {
            assertThrows(BadRequestException::class.java){service.splitSample("sampling",id,bad)}
        }
        assertTrue(journal(id).events.isEmpty());assertTrue(journal(id).portions.isEmpty())
    }
    @Test fun `portion target is mandatory after split and whole sample old endpoints cannot bypass it`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id))
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id))}
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id))}
        assertThrows(BadRequestException::class.java){service.recordSampleDiscrepancy("sampling",id,discrepancy(id))}
        assertThrows(BadRequestException::class.java){service.analysis("sampling",id,analysis())}
        assertThrows(BadRequestException::class.java){service.splitSample("sampling",id,split(id))}
    }
    @Test fun `nested split retires its parent preserves all lineage and never reuses an earlier identifier`()=fixture {
        val id=sampleReady();val first=split(id);val phase=service.splitSample("sampling",id,first)
        assertEquals(phase,service.splitSample("sampling",id,first))
        service.splitSample("sampling",id,split(id,"A",minutes=60,parts=listOf("A1" to "0.20","A2" to "0.30")))
        assertEquals(setOf("A1","A2","B"),journal(id).portions.keys);assertEquals("A",journal(id).portions.getValue("A1").parentKey)
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
        assertEquals(phase,service.splitSample("sampling",id,first))
        assertThrows(BadRequestException::class.java){service.splitSample("sampling",id,split(id,"B",minutes=65,parts=listOf("A" to "0.25","C" to "0.50")))}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"OLD","0.1","0.4",70).copy(portionKey="A"))}
        assertEquals(setOf("A","B","A1","A2"),journal(id).usedPortionKeys)
    }
    @Test fun `one portion discrepancy blocks normal work on all portions without changing other expected quantities`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id));val b=journal(id).portions.getValue("B")
        service.recordSampleDiscrepancy("sampling",id,portionDiscrepancy(id,"A"))
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,portionHandover(id,"B",minutes=90))}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"B1","0.1","0.65",90).copy(portionKey="B"))}
        assertThrows(BadRequestException::class.java){service.splitSample("sampling",id,split(id,"B",minutes=90,parts=listOf("B1" to "0.25","B2" to "0.50")))}
        assertEquals(b,journal(id).portions.getValue("B"));assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
        assertEquals(BigDecimal("0.10"),journal(id).portions.getValue("A").observedQuantity)
    }
    @Test fun `portion investigations require their own findings and two confirmations while other findings stay open`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id))
        service.recordSampleDiscrepancy("sampling",id,portionDiscrepancy(id,"A"));service.recordSampleDiscrepancy("sampling",id,portionDiscrepancy(id,"B",minutes=85))
        assertThrows(BadRequestException::class.java){service.reviewSampleDiscrepancy("sampling",id,portionReview(id,"A").copy(discrepancySequences=journal(id).openDiscrepancySequences))}
        service.reviewSampleDiscrepancy("sampling",id,portionReview(id,"A"))
        actor("conservation");service.confirmSampleDiscrepancy("sampling",id,portionConfirmation(id,"A"))
        actor("keeper");service.confirmSampleDiscrepancy("sampling",id,portionConfirmation(id,"A",minutes=115));actor("operator")
        assertTrue(journal(id).portions.getValue("A").openDiscrepancySequences.isEmpty())
        assertTrue(journal(id).portions.getValue("B").openDiscrepancySequences.isNotEmpty())
        assertFalse(journal(id).readyForCompletion)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,portionHandover(id,"A",false,minutes=118))}
    }
    @Test fun `custody only discrepancy can be reported truthfully without inventing a quantity identity or condition failure`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id))
        val target=journal(id).portions.getValue("A")
        val same=portionDiscrepancy(id,"A").copy(observedQuantity=target.remainingQuantity,identityConfirmed=true,conditionSafe=true,observedCustody=target.custody)
        assertThrows(BadRequestException::class.java){service.recordSampleDiscrepancy("sampling",id,same)}
        service.recordSampleDiscrepancy("sampling",id,same.copy(observedCustody=target.custody.copy(location="实际另一位置")))
        assertTrue(journal(id).portions.getValue("A").openDiscrepancySequences.isNotEmpty())
        assertEquals(BigDecimal("1.25"),journal(id).remainingQuantity)
        assertTrue(journal(id).events.last().discrepancy!!.identityConfirmed)
        assertTrue(journal(id).events.last().discrepancy!!.conditionSafe)
    }
    @Test fun `portion observation cannot predate its creation but later recording keeps actual observation time`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id))
        assertThrows(BadRequestException::class.java){service.recordSampleDiscrepancy("sampling",id,portionDiscrepancy(id,"A",minutes=45))}
        service.analyzeBatch("sampling",id,batch(id,"A1","0.1","0.4",70).copy(portionKey="A"))
        service.recordSampleDiscrepancy("sampling",id,portionDiscrepancy(id,"A",minutes=60))
        assertEquals(at.plusMinutes(60),journal(id).events.last().discrepancy!!.observedAt)
        assertEquals(at.plusMinutes(70),journal(id).lastOccurredAt)
    }
    @Test fun `completion version covers all portions and verifies every remaining internal custodian`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id));service.analyzeBatch("sampling",id,batch(id,"A1","0.1","0.4",60).copy(portionKey="A"))
        val stale=review(id);service.handoverSample("sampling",id,portionHandover(id,"B",false).copy(destination=SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,"B柜",userId="other-custodian")))
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",stale)}
        doThrow(BadRequestException("B份保管人已离馆")).`when`(users).requireMuseumMember("other-custodian","museum")
        assertThrows(BadRequestException::class.java){service.completeCurrent("sampling",review(id))}
        assertNull(CollectionSamplingPlanDao.all().single().completed)
    }
    @Test fun `the same real laboratory batch can cover distinct portions but cannot consume the same portion twice`()=fixture {
        val id=sampleReady();service.splitSample("sampling",id,split(id))
        service.analyzeBatch("sampling",id,batch(id,"LAB-1","0.1","0.40",60).copy(portionKey="A"))
        service.analyzeBatch("sampling",id,batch(id,"LAB-1","0.1","0.65",60).copy(portionKey="B"))
        assertEquals(BigDecimal("1.05"),journal(id).remainingQuantity);assertEquals(2,journal(id).analysisCount)
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"LAB-1","0.05","0.35",70).copy(portionKey="A"))}
        assertEquals(3,journal(id).events.size)
    }
    @Test fun `split after analysis preserves earlier consumption and new portions do not duplicate the analysis count`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id))
        service.splitSample("sampling",id,split(id,minutes=70,parts=listOf("A" to "0.4","B" to "0.6")))
        assertEquals(BigDecimal("1.0"),journal(id).remainingQuantity);assertEquals(1,journal(id).analysisCount)
        assertTrue(journal(id).readyForCompletion);service.completeCurrent("sampling",review(id))
        assertEquals(BigDecimal("1.25"),CollectionSampleDao[id].content.quantity)
    }
    @Test fun `post completion portions inherit frozen split lineage and later changes do not alter completion`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id));service.splitSample("sampling",id,split(id,minutes=70,parts=listOf("A" to "0.4","B" to "0.6")))
        val signed=review(id);service.completeCurrent("sampling",signed);val frozen=journal(id)
        assertEquals(setOf("A","B"),custodyJournal(id).portions.keys)
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,portionHandover(id,"A",after=true,minutes=100),true)}
        service.handoverSample("sampling",id,portionHandover(id,"A",after=true,minutes=130),true)
        service.splitSample("sampling",id,split(id,"B",true,140,listOf("B1" to "0.2","B2" to "0.4")),true)
        assertEquals(setOf("A","B1","B2"),custodyJournal(id).portions.keys);assertEquals(frozen,journal(id))
        service.completeCurrent("sampling",signed)
        assertThrows(BadRequestException::class.java){service.splitSample("sampling",id,split(id,"B1",true,150,listOf("B" to "0.1","C" to "0.1")),true)}
    }
    @Test fun `post completion whole sample may split without becoming another extraction`()=fixture {
        val id=custodyReady();val frozen=journal(id)
        service.splitSample("sampling",id,split(id,after=true,minutes=130,parts=listOf("A" to "0.4","B" to "0.6")),true)
        assertEquals(BigDecimal("1.0"),custodyJournal(id).remainingQuantity);assertEquals(2,custodyJournal(id).portions.size)
        assertEquals(frozen,journal(id));assertEquals(1,CollectionSampleDao.all().count())
    }

    @Test fun `completed project rejects further analysis and transfer without changing existing originals`()=fixture {
        val id=sampleReady();service.analyzeBatch("sampling",id,batch(id));val signed=review(id)
        service.completeCurrent("sampling",signed);service.completeCurrent("sampling",signed)
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,batch(id,"B2","0.1","0.9",90))}
        assertThrows(BadRequestException::class.java){service.handoverSample("sampling",id,handover(id))}
        assertEquals(1,journal(id).events.size)
    }

    @Test fun `emergency relocation of unsafe sampling original retains sampling occupancy and historical unsafe end`()=fixture {
        listOf("laboratory","safe-room").forEach {id->WarehouseDao.new(id){orgId="museum";name=id;sceneType=EnumLocationScene.STORAGE}}
        val p=ready();CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_USE
        val sample=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="实际额外损伤",exceptionFileIds="proof"))
        service.endExtraction("sampling",end(setOf(sample)).copy(safeToReleaseOriginal=false))
        val caseId=incidents.create(incidentFacts(p))
        val relocation=IncidentRelocationService(scope,users,incidents,IncidentWorkService(scope,users,incidents,operationArchives),operationArchives,listOf(IncidentWorkGuard(operationArchives),IncidentRelocationGuard(operationArchives)))
        val helper=IncidentRelocationTestSupport(relocation,incidents,at,"proof")
        val id=helper.actualEmergency("piece",caseId,"laboratory","safe-room")
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertNull(CollectionDao["piece"].warehouseId)
        assertTrue(CollectionLocationResolver(listOf(IncidentRelocationLocationResolver())).collectionIdsAt("safe-room").contains("piece"))
        assertTrue(CollectionOperationLockDao.all().toList().any {it.bizType==CollectionConst.BIZ_SAMPLING})
        helper.confirmAndLink(caseId,id);IncidentSourceTestSupport(incidents,at.plusHours(3),"proof").close(caseId)
        actor("operator")
        service.clearIncident("sampling",clearance(caseId).copy(confirmedAt=at.plusHours(4)))
        assertFalse(CollectionSamplingPlanDao[p].ended!!.safeToReleaseOriginal)
        assertTrue(CollectionSampleDao[sample].content.unexpectedDamage)
        assertNull(CollectionDao["piece"].warehouseId)
        assertTrue(CollectionLocationResolver(listOf(IncidentRelocationLocationResolver())).collectionIdsAt("safe-room").contains("piece"))
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertDoesNotThrow{locks.requireUnlocked("piece")}
    }
    @Test fun `unsafe sampling must stop before onsite protection and keeps original exception and occupancy until separate clearance`()=fixture {
        val p=ready()
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_USE
        val sample=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="实际额外损伤",exceptionFileIds="proof"))
        val caseId=incidents.create(incidentFacts(p))
        val work=IncidentWorkService(scope,users,incidents,operationArchives)
        val support=IncidentWorkTestSupport(work,incidents,at.plusHours(1),"proof")
        assertThrows(BadRequestException::class.java){work.create(support.plan("piece",caseId))}
        service.endExtraction("sampling",end(setOf(sample)).copy(safeToReleaseOriginal=false))
        incidents.amend(caseId,incidentFacts(p).copy(replacementReason="补入实际停止原件",replacementFileIds="proof"),incidents.detail(caseId)["factsId"] as String)
        val workId=support.perform("piece",caseId)
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertTrue(CollectionOperationLockDao.all().toList().any {it.collectionId=="piece" && it.bizType==CollectionConst.BIZ_SAMPLING})
        actor("operator")
        assertThrows(BadRequestException::class.java){service.clearIncident("sampling",clearance(caseId).copy(confirmedAt=at.plusHours(5)))}
        support.linkResult(caseId,workId)
        IncidentSourceTestSupport(incidents,at.plusHours(4),"proof").close(caseId)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        actor("operator")
        service.clearIncident("sampling",clearance(caseId).copy(confirmedAt=at.plusHours(5)))
        assertFalse(CollectionSamplingPlanDao[p].ended!!.safeToReleaseOriginal)
        assertTrue(CollectionSampleDao[sample].content.unexpectedDamage)
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertDoesNotThrow{locks.requireUnlocked("piece")}
    }
    @Test fun `joint plan enforces distinct departments minimum quantities and original necessity`()=fixture {
        for(v in listOf(plan().copy(conservationUserId="keeper"),plan().copy(originalIndispensable=false),plan().copy(nonDestructiveAlternatives=""),plan().copy(points=listOf(plan().points.single().copy(maximumQuantity=BigDecimal.ZERO)))))assertThrows(BadRequestException::class.java){service.savePlan("sampling",v)}
        val p=service.savePlan("sampling",plan());assertEquals(p,service.savePlan("sampling",plan()))
        verify(users,atLeastOnce()).requireDepartmentMember("conservation","museum","conservation-dept")
        verify(users,atLeastOnce()).requireRole("conservation","museum",CollectionConst.ROLE_CONSERVATION)
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `first class relic cannot bypass concrete alternatives or choose original with an available substitute`(nature:EnumCollectionNature)=fixture {
        CollectionDao["piece"].nature=nature
        assertThrows(BadRequestException::class.java){service.savePlan("sampling",plan().copy(alternatives=emptyList()))}
        assertThrows(BadRequestException::class.java){service.savePlan("sampling",plan().copy(alternatives=listOf(plan().alternatives.single().copy(canReplace=true))))}
        val p=service.savePlan("sampling",plan())
        assertThrows(BadRequestException::class.java){permit(p,EnumAuthorityLevel.PROVINCE)}
        permit(p)
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `other grades require province approval and unknown grading cannot silently downgrade`(nature:EnumCollectionNature)=fixture {
        CollectionDao["piece"].nature=nature
        CollectionDao["piece"].level=EnumRelicLevel.UNRATED
        assertThrows(BadRequestException::class.java){service.savePlan("sampling",plan())}
        CollectionDao["piece"].level=EnumRelicLevel.SECOND
        val p=service.savePlan("sampling",plan().copy(alternatives=emptyList()))
        assertEquals(EnumAuthorityLevel.PROVINCE,CollectionSamplingPlanDao[p].authority)
        assertThrows(BadRequestException::class.java){permit(p)}
        permit(p,EnumAuthorityLevel.PROVINCE)
    }
    @Test fun `actual submit selects joint flow and freezes the version`()=fixture {
        service.savePlan("sampling",plan());service.submit("sampling")
        assertEquals(CollectionConst.FLOW_SAMPLING,requests.single().definitionKey)
        assertEquals(mapOf("conservation" to listOf("conservation"),"keeper" to listOf("keeper")),requests.single().selfSelectAssignees)
        assertThrows(BadRequestException::class.java){service.savePlan("sampling",plan().copy(specialNeed="覆盖"))}
        assertThrows(BadRequestException::class.java){service.saveDraft("sampling")}
    }
    @Test fun `old passed state missing keeper and internal confirmation without permit cannot start`()=fixture {
        val p=service.savePlan("sampling",plan());CollectionSamplingDao["sampling"].flowStatus=EnumFlowStatus.PASS
        assertThrows(BadRequestException::class.java){service.start("sampling",start(p))}
        CollectionSamplingDao["sampling"].flowStatus=EnumFlowStatus.DRAFT
        approve();assertThrows(BadRequestException::class.java){service.start("sampling",start(p))}
        permit(p);service.start("sampling",start(p))
        verify(originals).requireAccess("museum","piece",CollectionConst.BIZ_SAMPLING,"sampling",at.plusMinutes(10),"operator","req",null)
    }
    @Test fun `one department cannot stand in for both actual plan confirmations`()=fixture {
        service.savePlan("sampling",plan());assertThrows(BadRequestException::class.java){approve(false)}
        assertNotEquals(EnumFlowStatus.PASS,CollectionSamplingDao["sampling"].flowStatus)
    }
    @Test fun `joint confirmation requires designated actor actual opinion and current department responsibility`()=fixture {
        service.savePlan("sampling",plan());service.submit("sampling")
        val r=CollectionSamplingDao["sampling"]
        val event=FlowTaskCompletedEvent(
            instanceId=r.flowInstanceId!!,definitionKey=CollectionConst.FLOW_SAMPLING,definitionName="有损取样共同确认",
            businessKey="sampling",businessType=CollectionConst.BIZ_SAMPLING,taskId="conservation-task",
            nodeKey="conservation",nodeName="保护修复部门确认",action="APPROVE",comment="本人核对共同方案及最小干预依据",
            operator="conservation",operatorName="保护修复责任人员",initiator="operator",initiatorName="经办",
        )
        assertThrows(BadRequestException::class.java){service.onFlowTaskCompleted(r,event.copy(operator="operator"))}
        assertThrows(BadRequestException::class.java){service.onFlowTaskCompleted(r,event.copy(comment=" "))}
        assertThrows(BadRequestException::class.java){service.onFlowTaskCompleted(r,event.copy(nodeKey="national"))}
        assertDoesNotThrow{service.onFlowTaskCompleted(r,event)}
        assertDoesNotThrow{service.onFlowTaskCompleted(r,event.copy(nodeKey="keeper",operator="keeper"))}
        doThrow(BadRequestException("实际部门成员关系已变化")).`when`(users).requireDepartmentMember("conservation","museum","conservation-dept")
        assertThrows(BadRequestException::class.java){service.onFlowTaskCompleted(r,event)}
    }
    @Test fun `source changes invalidate plan before actual work and administrative evidence is permanent`()=fixture {
        val p=service.savePlan("sampling",plan());approve();permit(p);CollectionDao["piece"].name="发生变更"
        assertThrows(BadRequestException::class.java){service.start("sampling",start(p))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
    }
    @Test fun `decimal quantity cumulative limit count and unit cannot be labelled compliant when exceeded`()=fixture {
        ready();val id=service.take("sampling",take());assertEquals(id,service.take("sampling",take()))
        assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(number="M-S2",quantity=BigDecimal("1.26")))}
        assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(number="M-S2",unit=EnumSamplingUnit.G))}
        service.take("sampling",take().copy(number="M-S2"))
        assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(number="M-S3",quantity=BigDecimal("0.01")))}
    }
    @Test fun `out of scope facts are preserved as exceptions and cannot release original`()=fixture {
        ready();val v=take().copy(quantity=BigDecimal("3"),withinApprovedScope=false,exceptionExplanation="实际超出批文数量，已立即停止并报告",exceptionFileIds="proof")
        val id=service.take("sampling",v)
        assertEquals(BigDecimal("3"),CollectionSampleDao[id].content.quantity)
        assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(number="M-S2"))}
        assertThrows(BadRequestException::class.java){service.endExtraction("sampling",end(setOf(id)))}
        service.endExtraction("sampling",end(setOf(id)).copy(safeToReleaseOriginal=false))
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(setOf(id)))}
    }
    @Test fun `unexpected damage differs from approved material removal and stays blocked for handling`()=fixture {
        ready();assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(unexpectedDamage=true))}
        val id=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="实际额外损伤及现场处置",exceptionFileIds="proof"))
        assertTrue(CollectionSampleDao[id].content.withinApprovedScope)
        assertThrows(BadRequestException::class.java){service.endExtraction("sampling",end(setOf(id)))}
    }
    @Test fun `actual end requires every sample and releases only normal original access without auto return`()=fixture {
        ready();val id=service.take("sampling",take())
        assertThrows(BadRequestException::class.java){service.endExtraction("sampling",end(emptySet()))}
        service.endExtraction("sampling",end(setOf(id)));service.endExtraction("sampling",end(setOf(id)))
        assertDoesNotThrow{locks.requireUnlocked("piece")}
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
        assertThrows(BadRequestException::class.java){service.cancel("sampling")}
        assertThrows(BadRequestException::class.java){service.take("sampling",take().copy(number="M-S2"))}
    }
    @Test fun `analysis conserves sample quantities and requires permitted laboratory and real remainder custody`()=fixture {
        ready();val id=service.take("sampling",take());service.endExtraction("sampling",end(setOf(id)))
        for(v in listOf(analysis().copy(consumedQuantity=BigDecimal("1")),analysis().copy(remainingLocation=null),analysis().copy(laboratoryName="未经方案确定的单位")))assertThrows(BadRequestException::class.java){service.analysis("sampling",id,v)}
        service.analysis("sampling",id,analysis());service.analysis("sampling",id,analysis())
        assertThrows(BadRequestException::class.java){service.analysis("sampling",id,analysis().copy(methodAndResult="覆盖"))}
    }
    @Test fun `joint completion checks all analyses and retains frozen source after physical transfer`()=fixture {
        ready();val id=service.take("sampling",take());service.endExtraction("sampling",end(setOf(id)))
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(setOf(id)))}
        CollectionDao["piece"].orgId="receiving-museum"
        assertNotNull(service.detail("sampling")["sourceIssue"])
        service.analysis("sampling",id,analysis());service.complete("sampling",completion(setOf(id)))
        assertNotNull(CollectionSamplingPlanDao.all().single().completed)
        assertThrows(BadRequestException::class.java){service.complete("sampling",completion(setOf(id)).copy(review="覆盖"))}
    }
    @Test fun `museum scope applies to detail page and mutation independently of admin role`()=fixture {
        CollectionSamplingDao["sampling"].orgId="other"
        assertThrows(BadRequestException::class.java){service.detail("sampling")}
        assertEquals(0,service.page(MockHttpServletRequest()).total)
        assertThrows(BadRequestException::class.java){service.savePlan("sampling",plan())}
    }
    @Test fun `decimal strings from actual form preserve small sampling limits without integer rounding`()=fixture {
        val json=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(plan())!!.replace(Regex("\"maximumQuantity\":\\s*[0-9.]+"),"\"maximumQuantity\":\"0.000125\"")
        val value=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(json,SamplingPlanVo::class.java)!!
        assertEquals(BigDecimal("0.000125"),value.points.single().maximumQuantity)
        service.savePlan("sampling",value)
    }
    @Test fun `actual incident processing and joint clearance release sampling original without rewriting damage history`()=fixture {
        val p=ready();val sample=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="真实额外损伤",exceptionFileIds="proof"))
        service.endExtraction("sampling",end(setOf(sample)).copy(safeToReleaseOriginal=false))
        val incident=incidents.create(incidentFacts(p))
        assertThrows(BadRequestException::class.java){service.clearIncident("sampling",clearance(incident))}
        closeIncident(incident)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        assertThrows(BadRequestException::class.java){service.clearIncident("sampling",clearance(incident).copy(confirmedAt=at.plusMinutes(59)))}
        service.clearIncident("sampling",clearance(incident));service.clearIncident("sampling",clearance(incident))
        assertDoesNotThrow{locks.requireUnlocked("piece")}
        assertFalse(CollectionSamplingPlanDao[p].ended!!.safeToReleaseOriginal);assertTrue(CollectionSampleDao[sample].content.unexpectedDamage)
        assertThrows(BadRequestException::class.java){service.analysis("sampling",sample,analysis())}
        service.analysis("sampling",sample,analysis().copy(analyzedAt=at.plusMinutes(70)));service.complete("sampling",completion(setOf(sample)))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
    }
    @Test fun `unrelated incident and later reopened facts cannot authorize sampling exception continuation`()=fixture {
        val p=ready();val sample=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="真实损伤",exceptionFileIds="proof"))
        service.endExtraction("sampling",end(setOf(sample)).copy(safeToReleaseOriginal=false))
        val unrelated=incidents.create(incidentFacts(p).copy(source=null));closeIncident(unrelated)
        assertThrows(BadRequestException::class.java){service.clearIncident("sampling",clearance(unrelated))}
        val original=incidentFacts(p);val incident=incidents.create(original);closeIncident(incident)
        service.clearIncident("sampling",clearance(incident))
        incidents.amend(incident,original.copy(description="新发现须重新核查",replacementReason="实际新增证据",replacementFileIds="proof"),incidents.detail(incident)["factsId"] as String)
        assertThrows(BadRequestException::class.java){service.analysis("sampling",sample,analysis().copy(analyzedAt=at.plusMinutes(70)))}
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
    }

    @Test fun `emergency incident can link verified sampling source later while preserving first report`()=fixture {
        val p=ready();val sample=service.take("sampling",take().copy(unexpectedDamage=true,exceptionExplanation="实际损伤",exceptionFileIds="proof"))
        service.endExtraction("sampling",end(setOf(sample)).copy(safeToReleaseOriginal=false))
        val incident=incidents.create(incidentFacts(p).copy(source=null));val first=incidents.detail(incident)["factsId"] as String
        val linked=incidents.linkSource(incident,first,IncidentSourceVo(CollectionConst.BIZ_SAMPLING,"sampling",p),"事后核实来源编号","proof")
        assertNotEquals(first,linked)
        assertThrows(BadRequestException::class.java){incidents.amend(incident,incidentFacts(p),first)}
        closeIncident(incident);service.clearIncident("sampling",clearance(incident));assertDoesNotThrow{locks.requireUnlocked("piece")}
        assertTrue(CollectionIncidentRecordDao.all().any { it.id.value==first })
    }

    @Suppress("UNCHECKED_CAST")
    private fun continuationRequest()=SamplingContinuationRequestVo(CollectionSamplingPlanDao.all().single().id.value,
        ((service.detail("sampling")["plans"] as List<Map<String,Any?>>).last()["continuationBasisRevision"] as String),
        at.plusMinutes(160),"operator","变更后的后续原件取样","真实研究问题变化","另一个最小范围的实际取样",
        "逐项评估旧样品不能满足新增问题","累计既有取样量、位置和原件养护再评估","proof","proof")
    private fun stoppedSample():String {
        val id=sampleReady();service.reviewStoppedClosure("sampling",stoppedReview());bothStoppedConfirm();return id
    }
    private fun childPlan(child:String):SamplingPlanVo=plan().copy(sourceRevision=service.detail(child)["sourceRevision"] as String,
        preparedAt=at.plusMinutes(180),plannedStart=at.plusMinutes(185),plannedEnd=at.plusMinutes(240),
        conservationUserId="new-conservation",keeperUserId="new-keeper")
    private fun approveChild(child:String) {
        service.submit(child);val r=CollectionSamplingDao[child]
        listOf("conservation" to "new-conservation","keeper" to "new-keeper").forEachIndexed {i,(node,person)->
            FlowInstanceLogDao.new {instanceId=r.flowInstanceId!!;nodeKey=node;taskId="child-$node";action="APPROVE";operator=person;comment="本人核对前案与累计干预及本次变更方案";createTime=at.plusMinutes(181+i.toLong())}
        }
        service.onFlowCompleted(r,FlowCompletedEvent(r.flowInstanceId!!,CollectionConst.FLOW_SAMPLING,"变更取样",child,CollectionConst.BIZ_SAMPLING,emptyMap(),"operator","经办"))
    }
    @Test fun `successor detail identifies predecessor original under the predecessor case`()=fixture {
        val fileId="2100000000000000001"
        FileDao.new(fileId){name="后续取样依据.pdf";path="proof";hash="continuation-proof";size=100;ext="pdf"}
        stoppedSample()
        val child=service.continueExtraction("sampling",continuationRequest().copy(changeBasisFileIds=fileId,assessmentFileIds=fileId))
        val detail=service.detail(child)
        val link=detail["predecessor"] as SamplingContinuationVo
        assertEquals("sampling",link.predecessorId);assertEquals(child,link.successorId)
        @Suppress("UNCHECKED_CAST") val original=detail["predecessorOriginal"] as Map<String,Any?>
        assertEquals("CONTINUATION_$child",original["phase"])
        @Suppress("UNCHECKED_CAST") val files=original["files"] as List<Map<String,Any?>>
        assertEquals(listOf(fileId),files.map { it["fileId"] })
        val archiveId=original["id"] as String
        assertDoesNotThrow { service.requireOriginal(link.predecessorId,archiveId,fileId) }
        assertThrows(BadRequestException::class.java) { service.requireOriginal(child,archiveId,fileId) }
    }
    @Test fun `successor extraction requires new joint plan and permit and preserves old unanalysed sample`()=fixture {
        val sample=stoppedSample();val original=CollectionSampleDao[sample].content
        val oldPlan=CollectionSamplingPlanDao.all().single();val oldContent=oldPlan.content;val oldStart=oldPlan.started;val oldClosed=stoppedState().revision
        val v=continuationRequest();val child=service.continueExtraction("sampling",v)
        assertEquals(child,service.continueExtraction("sampling",v));assertEquals(2,CollectionSamplingDao.all().count())
        assertEquals(EnumFlowStatus.DRAFT,CollectionSamplingDao[child].flowStatus);assertNull(CollectionSamplingDao[child].flowInstanceId)
        assertEquals(1,CollectionSampleDao.all().count());assertNull(CollectionSampleDao[sample].analysis)
        assertNotNull(service.detail(child)["predecessor"])
        val newPlan=service.savePlan(child,childPlan(child));assertNotEquals(newPlan,oldPlan.id.value)
        val start=SamplingStartVo(newPlan,at.plusMinutes(190),"operator","new-req",null,true,true,"重新实际核实原件与现场","proof","proof")
        assertThrows(BadRequestException::class.java){service.start(child,start)}
        approveChild(child)
        assertThrows(BadRequestException::class.java){service.start(child,start)} // 前案批文不授予新方案许可
        assertThrows(BadRequestException::class.java){service.administrative(child,oldPlan.id.value,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.APPROVAL,authorityName="机关",documentNo="旧文号",issuedAt=at.toLocalDate(),fileIds="proof"))}
        service.administrative(child,newPlan,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.APPROVAL,authorityName="实际机关",documentNo="变更后新批文",issuedAt=at.toLocalDate(),fileIds="proof"))
        service.start(child,start)
        assertTrue(CollectionPermanentArchiveDao.all().any {it.bizId==newPlan && it.phase=="PREDECESSOR_AT_START"})
        val newSample=service.take(child,take().copy(number="M-S2",takenAt=at.plusMinutes(200)))
        service.endExtraction(child,end(setOf(newSample)).copy(endedAt=at.plusMinutes(220),conservationUserId="new-conservation",keeperUserId="new-keeper"))
        assertEquals(2,CollectionSampleDao.all().count());assertEquals(original,CollectionSampleDao[sample].content)
        assertEquals(oldContent,oldPlan.content);assertEquals(oldStart,oldPlan.started);assertNull(oldPlan.completed)
        assertEquals(oldClosed,stoppedState().revision);assertNull(CollectionSampleDao[sample].analysis)
        assertThrows(BadRequestException::class.java){service.analyzeBatch(child,sample,batch(sample))}
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",sample,batch(sample))}
        CollectionPermanentArchiveDao.all().single {it.bizId==newPlan && it.phase=="PREDECESSOR_AT_START"}.snapshot="{}"
        assertThrows(BadRequestException::class.java){service.start(child,start)}
        assertThrows(BadRequestException::class.java){service.take(child,take().copy(number="M-S3",takenAt=at.plusMinutes(210)))}
    }
    @Test fun `continuation rejects active and pending stopped projects without creating another request`()=fixture {
        sampleReady()
        val v=SamplingContinuationRequestVo(CollectionSamplingPlanDao.all().single().id.value,"not-closed",at.plusMinutes(160),"operator","后续","原因","范围","旧样品","累计干预","proof","proof")
        assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",v)}
        service.reviewStoppedClosure("sampling",stoppedReview())
        assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",v)}
        assertEquals(1,CollectionSamplingDao.all().count())
    }
    @Test fun `continuation validates current sample basis and truthful request identity time and assessments`()=fixture {
        val id=stoppedSample();val v=continuationRequest()
        listOf(v.copy(predecessorPlanId="another"),v.copy(basisRevision="stale"),v.copy(requestedAt=at.plusMinutes(104)),
            v.copy(requestedAt=LocalDateTime.now().plusDays(1)),v.copy(recorderUserId="someone"),v.copy(title=" "),v.copy(title="x".repeat(501)),
            v.copy(changeReason=""),v.copy(changedScope=""),v.copy(previousWorkAndSamplesAssessment=""),v.copy(cumulativeInterventionAssessment=""),v.copy(changeBasisFileIds=""),v.copy(assessmentFileIds="")).forEach {
            assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",it)}
        }
        service.handoverSample("sampling",id,handover(id,false,130).copy(previousRevision=custodyJournal(id).revision),true)
        assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",v)}
        assertEquals(1,CollectionSamplingDao.all().count())
        assertNotNull(service.continueExtraction("sampling",continuationRequest()))
    }
    @Test fun `continuation blocks fresh post closure discrepancies and external remainder`()=fixture {
        val id=stoppedSample()
        service.handoverSample("sampling",id,handover(id,true,130).copy(previousRevision=custodyJournal(id).revision),true)
        assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",continuationRequest())}
        service.handoverSample("sampling",id,handover(id,false,135).copy(previousRevision=custodyJournal(id).revision),true)
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,minutes=140).copy(previousRevision=custodyJournal(id).revision),true)
        assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",continuationRequest())}
        assertEquals(1,CollectionSamplingDao.all().count())
    }
    @Test fun `successor branch cannot be duplicated and existing child requires current collection source and new plan time`()=fixture {
        stoppedSample();val v=continuationRequest();val child=service.continueExtraction("sampling",v)
        assertThrows(BadRequestException::class.java){service.continueExtraction("sampling",v.copy(title="另开并行申请"))}
        val plan=childPlan(child)
        assertThrows(BadRequestException::class.java){service.savePlan(child,plan.copy(preparedAt=at))}
        assertThrows(BadRequestException::class.java){service.savePlan(child,plan.copy(sourceRevision=service.detail("sampling")["sourceRevision"] as String))}
        service.savePlan(child,plan)
        assertTrue(CollectionSamplingPlanDao.all().last().sourceSnapshot.contains("变更后的后续原件取样"))
    }
    @Test fun `later custody preserves frozen continuation assessment and old completion originals`()=fixture {
        val id=stoppedSample();val child=service.continueExtraction("sampling",continuationRequest());val before=service.detail(child)
        service.handoverSample("sampling",id,handover(id,false,170).copy(previousRevision=custodyJournal(id).revision),true)
        val after=service.detail(child)
        assertEquals(before["predecessor"],after["predecessor"]);assertEquals(before["sourceRevision"],after["sourceRevision"])
        val source=after["predecessor"] as SamplingContinuationVo
        assertTrue(source.priorWorkSnapshot.contains(id));assertEquals(1,CollectionSampleDao.all().count())
    }
    @Test fun `tampered continuation proof fails closed for child source and new plan`()=fixture {
        stoppedSample();val child=service.continueExtraction("sampling",continuationRequest());val plan=childPlan(child)
        CollectionPermanentArchiveDao.all().single {it.phase=="CONTINUATION_$child"}.snapshot="{}"
        assertNotNull(service.detail(child)["sourceIssue"])
        assertThrows(BadRequestException::class.java){service.savePlan(child,plan)}
    }
    @Test fun `normal completed predecessor can initiate new extraction without changing its completed state`()=fixture {
        val id=sampleReady();service.analysis("sampling",id,analysis());service.complete("sampling",completion(setOf(id)))
        val completed=CollectionSamplingPlanDao.all().single().completed
        val child=service.continueExtraction("sampling",continuationRequest())
        assertEquals(completed,CollectionSamplingPlanDao.all().single().completed);assertNull(CollectionSamplingDao[child].flowInstanceId)
    }
    @Test fun `zero sample predecessor links successor without creating fictional sample or completion`()=fixture {
        val p=noSampleReady();service.reviewNoSampleClosure("sampling",noSampleReview())
        actor("conservation");service.confirmNoSampleClosure("sampling",noSampleConfirm())
        actor("keeper");service.confirmNoSampleClosure("sampling",noSampleConfirm(95));actor("operator")
        val child=service.continueExtraction("sampling",continuationRequest())
        assertEquals(0,CollectionSampleDao.all().count());assertNull(CollectionSamplingPlanDao[p].completed)
        assertNotNull(service.detail(child)["predecessor"])
    }
    @Test fun `new discrepancy after continuation registration blocks successor start against live predecessor`()=fixture {
        val id=stoppedSample();val child=service.continueExtraction("sampling",continuationRequest());val p=service.savePlan(child,childPlan(child));approveChild(child)
        service.administrative(child,p,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.APPROVAL,authorityName="机关",documentNo="新批准",issuedAt=at.toLocalDate(),fileIds="proof"))
        service.recordSampleDiscrepancy("sampling",id,discrepancy(id,minutes=185).copy(previousRevision=custodyJournal(id).revision),true)
        val start=SamplingStartVo(p,at.plusMinutes(190),"operator","new-req",null,true,true,"重新核实","proof","proof")
        assertThrows(BadRequestException::class.java){service.start(child,start)}
        assertNull(CollectionSamplingPlanDao[p].started)
        assertFalse(CollectionPermanentArchiveDao.all().any {it.bizId==p && it.phase=="PREDECESSOR_AT_START"})
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["same","different","rollback","custody","start-custody"])
    fun `PostgreSQL serializes successor creation and checks current predecessor before start`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val sample=transaction(db){seed();stoppedSample()};val v=transaction(db){continuationRequest()}
            var child:String?=null;var start:SamplingStartVo?=null
            if(scenario=="start-custody")transaction(db) {
                child=service.continueExtraction("sampling",v);val p=service.savePlan(child!!,childPlan(child!!));approveChild(child!!)
                service.administrative(child!!,p,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.APPROVAL,authorityName="机关",documentNo="新批准",issuedAt=at.toLocalDate(),fileIds="proof"))
                start=SamplingStartVo(p,at.plusMinutes(190),"operator","new-req",null,true,true,"实际开始核实","proof","proof")
            }
            val first=pool.submit<Result<String>> {
                actor("operator")
                try {runCatching {transaction(db) {
                    val result=if(scenario in setOf("custody","start-custody"))service.handoverSample("sampling",sample,handover(sample,scenario=="start-custody",130).copy(previousRevision=custodyJournal(sample).revision),true)
                        else service.continueExtraction("sampling",v)
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario=="rollback")throw IllegalStateException("synthetic continuation rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor("operator")
                try {runCatching {transaction(db) {
                    service.detail(child ?: "sampling") // intentional pre-lock cached predecessor
                    if(scenario=="start-custody")service.start(child!!,start!!)
                    else service.continueExtraction("sampling",if(scenario=="different")v.copy(title="竞争申请") else v)
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                source.connection.use {c->c.prepareStatement("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {rows->rows.next();waiting=rows.getBoolean(1)}}}
                if(!waiting)Thread.sleep(25)
            }
            assertTrue(waiting,"second transaction must wait on the actual predecessor row lock")
            release.countDown();val a=first.get(20,java.util.concurrent.TimeUnit.SECONDS);val b=second.get(20,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(scenario!="rollback",a.isSuccess);assertEquals(scenario in setOf("same","rollback"),b.isSuccess)
            if(scenario=="same")assertEquals(a.getOrThrow(),b.getOrThrow())
            actor("operator");transaction(db) {
                assertEquals(if(scenario=="custody")1 else 2,CollectionSamplingDao.all().count())
                assertEquals(1,CollectionSampleDao.all().count());assertNull(CollectionSampleDao[sample].analysis)
                val links=CollectionPermanentArchiveDao.all().filter {it.phase.startsWith("CONTINUATION_")}
                assertEquals(if(scenario=="custody")0 else 1,links.size)
                if(scenario=="start-custody")assertNull(CollectionSamplingPlanDao[start!!.planId].started)
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    private fun amended(id:String)=SamplingAnalysisAmendment.read(CollectionSamplingPlanDao[CollectionSampleDao[id].planId],id) {_,_->}
    private fun analysisProposal(id:String,treatment:EnumExistingSampleApproval=EnumExistingSampleApproval.NOT_APPLICABLE,portion:String?=null)=SamplingAnalysisProposalVo(
        amended(id).revision,custodyJournal(id).revision,service.detail("sampling")["sourceRevision"] as String,at.plusMinutes(130),"operator","new-dept","new-conservation","new-keeper",
        "仅对已有样品调整分析","既有样品及结果可以用于后续研究，不再原件取样",at.plusMinutes(140),at.plusMinutes(500),
        listOf(SamplingAnalysisTargetVo(portion,"新分析实验室","新分析方法",BigDecimal("0.50"),"剩余样品保管")),treatment,
        if(treatment==EnumExistingSampleApproval.EXISTING_APPROVAL_COVERS)CollectionAdministrativeRecordDao.all().single().id.value else null,
        "按实际依据核实本次仅分析已有样品的批准适用范围","proof","proof","proof")
    private fun analysisConfirm(id:String,subject:Int=amended(id).proposalSequence!!,minutes:Long=135)=SamplingAnalysisConfirmationVo(amended(id).revision,subject,at.plusMinutes(minutes),"本人核对实际方案与审批适用原件","proof")
    private fun confirmAnalysisPlan(id:String) {
        actor("new-conservation");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id))
        actor("new-keeper");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,minutes=136));actor("operator")
    }
    private fun analysisActivate(id:String)=SamplingAnalysisActivationVo(amended(id).revision,amended(id).proposalSequence!!,custodyJournal(id).revision,at.plusMinutes(145),"实际核实当前样品和已办理手续","proof")
    private fun enableAnalysis(id:String,proposal:SamplingAnalysisProposalVo=analysisProposal(id)) {
        service.proposeExistingAnalysis("sampling",id,proposal);confirmAnalysisPlan(id)
        if(proposal.approvalTreatment==EnumExistingSampleApproval.NEW_APPROVAL)service.approveExistingAnalysis("sampling",id,SamplingAnalysisAdministrativeVo(amended(id).revision,amended(id).proposalSequence!!,
            CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.NATIONAL,action=EnumAdministrativeAction.APPROVAL,authorityName="实际机关",documentNo="旧样品新分析批准",issuedAt=at.toLocalDate(),fileIds="proof")))
        service.activateExistingAnalysis("sampling",id,analysisActivate(id))
    }
    private fun existingBatch(id:String,used:String="0.25",left:String="1.00",number:String="NEW-B1",minutes:Long=160,portion:String?=null)=SamplingBatchAnalysisVo(
        custodyJournal(id).revision,number,at.plusMinutes(minutes),"新分析实验室","实际分析人","operator","真实方法与结果",BigDecimal(used),BigDecimal(left),"样品柜","proof","proof",portion,amended(id).proposalSequence)
    private fun analysisClose(id:String,completed:Boolean=false)=SamplingAnalysisClosureVo(amended(id).revision,amended(id).proposalSequence!!,custodyJournal(id).revision,
        at.plusMinutes(200),at.plusMinutes(210),"operator","current-conservation","current-keeper",completed,"实际已完成的分析和结果","真实未完成工作与停止原因","剩余样品保管和后续安排","proof","proof")
    @Test fun `existing sample analysis changes laboratory without new extraction or altered stopped closure`()=fixture {
        val id=stoppedSample();val p=CollectionSamplingPlanDao.all().single();val old=p.content;val before=stoppedState().revision;val initial=custodyJournal(id)
        enableAnalysis(id)
        assertEquals(initial.remainingQuantity,custodyJournal(id).remainingQuantity);assertEquals(0,custodyJournal(id).analysisCount)
        assertEquals(1,CollectionSamplingDao.all().count());assertEquals(1,CollectionSampleDao.all().count());assertNull(p.completed)
        val v=existingBatch(id);val key=service.analyzeExistingSample("sampling",id,v)
        assertEquals(key,service.analyzeExistingSample("sampling",id,v));assertEquals(BigDecimal("1.00"),custodyJournal(id).remainingQuantity)
        assertEquals(1,custodyJournal(id).analysisCount);assertEquals(0,journal(id).analysisCount);assertNull(CollectionSampleDao[id].analysis)
        assertEquals(old,p.content);assertEquals(before,stoppedState().revision)
        assertThrows(BadRequestException::class.java){service.analyzeBatch("sampling",id,v)}
        assertThrows(BadRequestException::class.java){service.analysis("sampling",id,analysis())}
    }
    @Test fun `existing analysis needs separate personal confirmations and explicit applicable approval`()=fixture {
        val id=stoppedSample();val v=analysisProposal(id,EnumExistingSampleApproval.NEW_APPROVAL);service.proposeExistingAnalysis("sampling",id,v)
        assertThrows(BadRequestException::class.java){service.confirmExistingAnalysis("sampling",id,analysisConfirm(id))}
        assertThrows(BadRequestException::class.java){service.activateExistingAnalysis("sampling",id,analysisActivate(id))}
        confirmAnalysisPlan(id)
        assertThrows(BadRequestException::class.java){service.activateExistingAnalysis("sampling",id,analysisActivate(id))}
        val permit=SamplingAnalysisAdministrativeVo(amended(id).revision,amended(id).proposalSequence!!,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,authorityName="机关",documentNo="批文",issuedAt=at.toLocalDate(),fileIds="proof"))
        assertThrows(BadRequestException::class.java){service.approveExistingAnalysis("sampling",id,permit)}
        service.approveExistingAnalysis("sampling",id,permit.copy(evidence=permit.evidence.copy(level=EnumAuthorityLevel.NATIONAL)))
        service.activateExistingAnalysis("sampling",id,analysisActivate(id));assertNotNull(amended(id).activationSequence)
    }
    @Test fun `covering original approval must be the actual predecessor permit with retained scope basis`()=fixture {
        val id=stoppedSample();val v=analysisProposal(id,EnumExistingSampleApproval.EXISTING_APPROVAL_COVERS)
        assertThrows(BadRequestException::class.java){service.proposeExistingAnalysis("sampling",id,v.copy(existingApprovalId="unrelated"))}
        enableAnalysis(id,v);assertNotNull(amended(id).activationSequence)
    }
    @Test fun `existing analysis rejects stale sample and collection evidence and rechecks current duties`()=fixture {
        val id=stoppedSample();val v=analysisProposal(id);service.proposeExistingAnalysis("sampling",id,v)
        service.handoverSample("sampling",id,handover(id,false,132).copy(previousRevision=custodyJournal(id).revision),true)
        actor("new-conservation");assertThrows(BadRequestException::class.java){service.confirmExistingAnalysis("sampling",id,analysisConfirm(id))};actor("operator")
        service.proposeExistingAnalysis("sampling",id,v.copy(previousRevision=amended(id).revision,sampleRevision=custodyJournal(id).revision,preparedAt=at.plusMinutes(133)))
        confirmAnalysisPlan(id)
        doThrow(BadRequestException("当前任职已变化")).`when`(users).requireRole("new-keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        assertThrows(BadRequestException::class.java){service.activateExistingAnalysis("sampling",id,analysisActivate(id))}
        assertNull(amended(id).activationSequence)
    }
    @Test fun `existing analysis validates real plan fields scope and non consuming zero limit`()=fixture {
        val id=stoppedSample();val p=analysisProposal(id)
        listOf(p.copy(targets=emptyList()),p.copy(targets=p.targets+p.targets),p.copy(applicabilityExplanation=""),p.copy(applicabilityFileIds=""),p.copy(sourceRevision="stale"),
            p.copy(preparedAt=at.plusMinutes(100)),p.copy(recorderUserId="other"),p.copy(conservationUserId="new-keeper"),
            p.copy(targets=listOf(p.targets.single().copy(portionKey="unknown"))),p.copy(targets=listOf(p.targets.single().copy(maximumConsumption=BigDecimal("1.26"))))).forEach {
            assertThrows(BadRequestException::class.java){service.proposeExistingAnalysis("sampling",id,it)}
        }
        enableAnalysis(id,p.copy(targets=listOf(p.targets.single().copy(maximumConsumption=BigDecimal.ZERO))))
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id))}
        service.analyzeExistingSample("sampling",id,existingBatch(id,"0","1.25"));assertEquals(BigDecimal("1.25"),custodyJournal(id).remainingQuantity)
    }
    @Test fun `cumulative analysis limit scope window and actual external handover are enforced`()=fixture {
        val id=stoppedSample();enableAnalysis(id)
        val external=handover(id,true,150).copy(previousRevision=custodyJournal(id).revision,destination=handover(id,true,150).destination.copy(externalUnit="新分析实验室"))
        service.handoverSample("sampling",id,external,true)
        val v=existingBatch(id)
        for(b in listOf(v.copy(laboratoryName="其他实验室"),v.copy(analyzedAt=at.plusMinutes(144)),v.copy(analyzedAt=at.plusMinutes(501)),v.copy(analysisPlanSequence=999),v.copy(consumedQuantity=BigDecimal("0.6"),remainingQuantity=BigDecimal("0.65"))))
            assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,b)}
        service.analyzeExistingSample("sampling",id,v)
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id,"0.3","0.7","NEW-B2",170))}
        service.analyzeExistingSample("sampling",id,existingBatch(id,"0.25","0.75","NEW-B2",170))
        assertEquals(BigDecimal("0.75"),custodyJournal(id).remainingQuantity)
    }
    @Test fun `split targets maintain quantities and cannot authorize unreviewed child portions`()=fixture {
        val id=stoppedSample();service.splitSample("sampling",id,split(id,after=true,minutes=120).copy(previousRevision=custodyJournal(id).revision),true)
        val p=analysisProposal(id,portion="A");enableAnalysis(id,p)
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id,"0.1","0.65",portion="B"))}
        service.analyzeExistingSample("sampling",id,existingBatch(id,"0.25","0.25",portion="A"))
        assertEquals(BigDecimal("0.25"),custodyJournal(id).portions.getValue("A").remainingQuantity)
        assertEquals(BigDecimal("0.75"),custodyJournal(id).portions.getValue("B").remainingQuantity)
    }
    @Test fun `analysis closure truthfully stops unfinished work and two people confirm without rewriting original closure`()=fixture {
        val id=stoppedSample();enableAnalysis(id);val original=stoppedState().revision
        assertThrows(BadRequestException::class.java){service.closeExistingAnalysis("sampling",id,analysisClose(id,true))}
        service.closeExistingAnalysis("sampling",id,analysisClose(id));assertNull(amended(id).closedAt)
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id))}
        actor("current-conservation");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,amended(id).closureSequence!!,215))
        actor("current-keeper");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,amended(id).closureSequence!!,220));actor("operator")
        assertNotNull(amended(id).closedAt);assertEquals(original,stoppedState().revision);assertEquals(0,custodyJournal(id).analysisCount)
        val next=analysisProposal(id).copy(previousRevision=amended(id).revision,preparedAt=at.plusMinutes(230),plannedStart=at.plusMinutes(240))
        service.proposeExistingAnalysis("sampling",id,next);assertNull(amended(id).activationSequence);assertEquals(0,custodyJournal(id).analysisCount)
    }
    @Test fun `analysis closure confirmation rejects changed custody and retains earlier signature`()=fixture {
        val id=stoppedSample();enableAnalysis(id);service.closeExistingAnalysis("sampling",id,analysisClose(id))
        actor("current-conservation");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,amended(id).closureSequence!!,215));actor("operator")
        service.handoverSample("sampling",id,handover(id,false,217).copy(previousRevision=custodyJournal(id).revision),true)
        actor("current-keeper");assertThrows(BadRequestException::class.java){service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,amended(id).closureSequence!!,220))}
        assertEquals(setOf("current-conservation"),amended(id).closureConfirmedUserIds);assertNull(amended(id).closedAt)
    }
    @Test fun `tampered approval or activation cannot be read as authorized historical analysis`()=fixture {
        val id=stoppedSample();enableAnalysis(id);service.analyzeExistingSample("sampling",id,existingBatch(id))
        val next=existingBatch(id,"0.25","0.75","NEW-B2",170)
        CollectionPermanentArchiveDao.all().single {it.phase==SamplingAnalysisAmendment.phase(id,amended(id).activationSequence!!)}.snapshot="{}"
        @Suppress("UNCHECKED_CAST") val plans=service.detail("sampling")["plans"] as List<Map<String,Any?>>
        @Suppress("UNCHECKED_CAST") val row=(plans.last()["samples"] as List<Map<String,Any?>>).single()
        assertNotNull(row["custodyJournalIssue"]);assertNull(row["custodyJournal"])
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,next)}
    }
    @Test fun `new laboratory custody supports actual split disposition and discrepancy investigation`()=fixture {
        val id=stoppedSample();enableAnalysis(id)
        val external=handover(id,true,150).copy(previousRevision=custodyJournal(id).revision,destination=handover(id,true,150).destination.copy(externalUnit="新分析实验室"))
        service.handoverSample("sampling",id,external,true)
        service.splitSample("sampling",id,split(id,after=true,minutes=155),true)
        service.recordSampleDisposition("sampling",id,disposition(id,"A",true,"0.1","0.4",160),true)
        actor("new-conservation");service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,"A",true,165),true)
        actor("new-keeper");service.confirmSampleDisposition("sampling",id,dispositionConfirmation(id,"A",true,170),true);actor("operator")
        assertEquals(BigDecimal("0.4"),custodyJournal(id).portions.getValue("A").remainingQuantity)
        // 分份保管继承已知来源；原来针对整份的分析范围不自动授权新子份。
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id,"0.1","0.3",minutes=175,portion="A"))}
        val finding=discrepancy(id,"0.75",180).copy(previousRevision=custodyJournal(id).revision,portionKey="B",observedCustody=external.destination)
        service.recordSampleDiscrepancy("sampling",id,finding,true)
        val state=custodyJournal(id);val review=discrepancyReview(id,BigDecimal("0.75")).copy(previousRevision=state.revision,portionKey="B",discrepancySequences=state.portions.getValue("B").openDiscrepancySequences,reviewedAt=at.plusMinutes(185),actualCustody=external.destination)
        service.reviewSampleDiscrepancy("sampling",id,review,true)
        for((index,person) in listOf("conservation","keeper").withIndex()) {
            actor(person);val current=custodyJournal(id)
            service.confirmSampleDiscrepancy("sampling",id,SamplingSampleConfirmationVo(current.revision,current.portions.getValue("B").pendingResolutionSequence!!,at.plusMinutes(190+index.toLong()),"本人核实新单位实际保管","proof","B"),true)
        }
        actor("operator");assertTrue(custodyJournal(id).openDiscrepancySequences.isEmpty())
    }
    @Test fun `completed predecessor gets later analysis without modifying original completion`()=fixture {
        val id=custodyReady();val p=CollectionSamplingPlanDao.all().single();val original=p.completed
        val proposal=analysisProposal(id).copy(preparedAt=at.plusMinutes(130))
        enableAnalysis(id,proposal);service.analyzeExistingSample("sampling",id,existingBatch(id,"0.25","0.75"))
        assertEquals(original,p.completed);assertEquals(BigDecimal("1.00"),journal(id).remainingQuantity);assertEquals(BigDecimal("0.75"),custodyJournal(id).remainingQuantity)
    }
    @Test fun `active analysis must truthfully end before a replacement plan and old batch cannot reuse new scope`()=fixture {
        val id=stoppedSample();enableAnalysis(id);service.analyzeExistingSample("sampling",id,existingBatch(id))
        val oldProposal=amended(id).proposalSequence
        assertThrows(BadRequestException::class.java){service.proposeExistingAnalysis("sampling",id,analysisProposal(id).copy(preparedAt=at.plusMinutes(170),plannedStart=at.plusMinutes(175)))}
        service.closeExistingAnalysis("sampling",id,analysisClose(id,true))
        actor("current-conservation");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,amended(id).closureSequence!!,215))
        actor("current-keeper");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,amended(id).closureSequence!!,220));actor("operator")
        val next=analysisProposal(id).copy(preparedAt=at.plusMinutes(230),plannedStart=at.plusMinutes(240))
        service.proposeExistingAnalysis("sampling",id,next)
        actor("new-conservation");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,minutes=235))
        actor("new-keeper");service.confirmExistingAnalysis("sampling",id,analysisConfirm(id,minutes=236));actor("operator")
        service.activateExistingAnalysis("sampling",id,analysisActivate(id).copy(activatedAt=at.plusMinutes(245)))
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id,"0.25","0.75","NEW-B2",250).copy(analysisPlanSequence=oldProposal))}
        service.analyzeExistingSample("sampling",id,existingBatch(id,"0.25","0.75","NEW-B2",250))
        assertEquals(2,custodyJournal(id).analysisCount);assertEquals(BigDecimal("0.75"),custodyJournal(id).remainingQuantity)
    }
    @Test fun `current source changes block analysis activation and implementation but allow factual ending`()=fixture {
        val id=stoppedSample();enableAnalysis(id);CollectionDao["piece"].name="据实订正后的名称"
        assertThrows(BadRequestException::class.java){service.analyzeExistingSample("sampling",id,existingBatch(id))}
        service.closeExistingAnalysis("sampling",id,analysisClose(id,false));assertNotNull(amended(id).closureSequence)
    }
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["proposal-same","proposal-different","proposal-rollback","confirm-race","activation-custody","activation-rollback","activation-same","analysis-same","analysis-different","analysis-rollback","close-analysis"])
    fun `PostgreSQL serializes existing analysis authority consumption and truthful closing`(scenario:String) {
        val props=java.util.Properties().apply {java.nio.file.Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)}
        val schema="sampling_"+java.util.UUID.randomUUID().toString().replace("-","")
        val source=org.postgresql.ds.PGSimpleDataSource().apply {setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema;applicationName=schema}
        source.connection.use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect(source);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        try {
            val id=transaction(db){seed();stoppedSample()};val proposal=transaction(db){analysisProposal(id)}
            if(!scenario.startsWith("proposal-"))transaction(db) {
                if(scenario.startsWith("analysis-") || scenario=="close-analysis")enableAnalysis(id)
                else {service.proposeExistingAnalysis("sampling",id,proposal);if(scenario!="confirm-race")confirmAnalysisPlan(id)}
            }
            val confirm=if(scenario=="confirm-race")transaction(db){analysisConfirm(id)} else null
            val activate=if(scenario.startsWith("activation-"))transaction(db){analysisActivate(id)} else null
            val batch=if(scenario.startsWith("analysis-") || scenario=="close-analysis")transaction(db){existingBatch(id)} else null
            val close=if(scenario=="close-analysis")transaction(db){analysisClose(id)} else null
            val first=pool.submit<Result<String>> {
                actor(if(scenario=="confirm-race")"new-conservation" else "operator")
                try {runCatching {transaction(db) {
                    val result=when {
                        scenario.startsWith("proposal-")->service.proposeExistingAnalysis("sampling",id,proposal)
                        scenario=="confirm-race"->service.confirmExistingAnalysis("sampling",id,confirm!!)
                        scenario=="activation-custody"->service.handoverSample("sampling",id,handover(id,false,138).copy(previousRevision=custodyJournal(id).revision),true)
                        scenario.startsWith("activation-")->service.activateExistingAnalysis("sampling",id,activate!!)
                        scenario=="close-analysis"->service.closeExistingAnalysis("sampling",id,close!!)
                        else->service.analyzeExistingSample("sampling",id,batch!!)
                    }
                    ready.countDown();check(release.await(15,java.util.concurrent.TimeUnit.SECONDS))
                    if(scenario.endsWith("rollback"))throw IllegalStateException("synthetic analysis transaction rollback")
                    result
                }}} finally {SecurityContextHolder.clearContext()}
            }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Result<String>> {
                actor(if(scenario=="confirm-race")"new-keeper" else "operator")
                try {runCatching {transaction(db) {
                    service.detail("sampling") // deliberately cache pre-lock sample and proposal data
                    when {
                        scenario.startsWith("proposal-")->service.proposeExistingAnalysis("sampling",id,if(scenario=="proposal-different")proposal.copy(changeReason="另一份变更") else proposal)
                        scenario=="confirm-race"->service.confirmExistingAnalysis("sampling",id,confirm!!)
                        scenario.startsWith("activation-")->service.activateExistingAnalysis("sampling",id,activate!!)
                        else->service.analyzeExistingSample("sampling",id,if(scenario=="analysis-different")batch!!.copy(batchNumber="OTHER") else batch!!)
                    }
                }}} finally {SecurityContextHolder.clearContext()}
            }
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);var waiting=false
            while(System.nanoTime()<deadline && !waiting) {
                source.connection.use {c->c.prepareStatement("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock')").use {q->q.setString(1,schema);q.executeQuery().use {rows->rows.next();waiting=rows.getBoolean(1)}}}
                if(!waiting)Thread.sleep(25)
            }
            assertTrue(waiting,"actual root row lock wait must be observed")
            release.countDown();val a=first.get(20,java.util.concurrent.TimeUnit.SECONDS);val b=second.get(20,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(!scenario.endsWith("rollback"),a.isSuccess,a.toString())
            val success=scenario in setOf("proposal-same","proposal-rollback","activation-rollback","activation-same","analysis-same","analysis-rollback")
            assertEquals(success,b.isSuccess,b.toString())
            if(scenario.endsWith("same"))assertEquals(a.getOrThrow(),b.getOrThrow())
            actor("operator");transaction(db) {
                assertEquals(1,CollectionSampleDao.all().count());assertEquals(1,CollectionSamplingDao.all().count());assertNull(CollectionSamplingPlanDao.all().single().completed)
                val journal=custodyJournal(id);val state=amended(id)
                assertEquals(if(scenario.startsWith("analysis-"))BigDecimal("1.00") else BigDecimal("1.25"),journal.remainingQuantity)
                assertEquals(if(scenario.startsWith("analysis-"))1 else 0,journal.analysisCount)
                val activations=journal.events.count {it.analysisAuthorization!=null}
                assertEquals(if(scenario.startsWith("analysis-") || scenario=="close-analysis" || scenario in setOf("activation-rollback","activation-same"))1 else 0,activations)
                if(scenario=="confirm-race")assertEquals(setOf("new-conservation"),state.confirmedUserIds)
                if(scenario=="close-analysis"){assertNotNull(state.closureSequence);assertNull(state.closedAt)}
            }
        } finally {
            release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS))
            source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }

    @Test fun `missing sample authorization cannot be accepted as an idempotent activation or truthful closure`()=fixture {
        val id=stoppedSample();service.proposeExistingAnalysis("sampling",id,analysisProposal(id));confirmAnalysisPlan(id)
        val v=analysisActivate(id);service.activateExistingAnalysis("sampling",id,v)
        val close=analysisClose(id)
        CollectionPermanentArchiveDao.all().single {it.phase=="${SamplingSampleJournal.custodyPrefix(id)}1"}.delete()
        assertThrows(BadRequestException::class.java){service.activateExistingAnalysis("sampling",id,v)}
        assertThrows(BadRequestException::class.java){service.closeExistingAnalysis("sampling",id,close)}
    }

}
