package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.context.support.StaticApplicationContext
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class IncidentWorkPersistenceTest {
    private val safetyOriginalRoot=createSafetyOriginalRoot()
    private val scope = CollectionScopeResolver("single", "museum")
    private val users = mock(CollectionResponsibleUserService::class.java)
    private val binder = mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives = CollectionPermanentArchiveService(binder, scope, io.hizhengfu.cloud.common.file.LocalFileObjectStorage(safetyOriginalRoot.toString()),unprotectedArchiveReadAccessProvider())
    private val guard = IncidentWorkGuard(archives)
    private val incidents = CollectionIncidentService(scope, users, archives, CollectionIncidentSourceService(), listOf(guard))
    private val service = IncidentWorkService(scope, users, incidents, archives)
    private val at = LocalDateTime.of(2026, 9, 1, 9, 0)
    private val support = IncidentSourceTestSupport(incidents, at, "proof")
    private val context = StaticApplicationContext()
    private val temp = mutableListOf<java.nio.file.Path>()
    private var safetyDatabase: Database? = null
    @BeforeEach fun prepare() { context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);support.actor("operator") }
    @AfterEach fun clean() { context.close();SecurityContextHolder.clearContext();removeSafetyOriginalRoot(safetyOriginalRoot);temp.forEach { java.nio.file.Files.deleteIfExists(it) } }
    private fun database(): Database {
        val db = safetyDatabase ?: java.nio.file.Files.createTempFile("incident-work-", ".db").also { temp.add(it) }.let { Database.connect("jdbc:sqlite:$it", driver="org.sqlite.JDBC") }
        transaction(db) {
            SchemaUtils.create(StorageMovements,CollectionOrgs, Collections, CollectionIncidents, CollectionIncidentRecords, CollectionIncidentItems, IncidentWorks, IncidentWorkRecords, CollectionOperationLocks, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files)
            CollectionOrgDao.new("museum") { code="M";name="本馆" }
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            CollectionOrgDao.new("other") { code="O";name="外馆" }
            CollectionDao.new("piece") { orgId="museum";name="瓶";totalRegNo="M1";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store" }
            CollectionDao.new("foreign") { orgId="other";name="外馆藏品";totalRegNo="O1";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.FIRST;lifeStatus=EnumCollectionStatus.IN_STORE }
            FileDao.new("proof") { name="实际原件.pdf";path="proof";hash="proof";size=100;ext="pdf" }
        }
        return db
    }
    private fun case(damaged: Boolean = false) = support.create(null, listOf(IncidentItemVo("piece",damaged,EnumIncidentSecurity.NONE,false,"实际不安全情况")))
    private fun fixture(block: (String) -> Unit) = transaction(database()) { block(case()) }
    private fun plan(vararg ids: String) = IncidentWorkPlanVo("piece",ids.toSet(),at,at.plusHours(1),at.plusHours(3),"原库现场","支撑不稳定",setOf(EnumIncidentWorkMethod.SUPPORT),"避免继续倾斜损伤","不搬移场所、不改变原状、不取样或修复","原地增设无损支撑","人员在场并保护现场","发现裂隙或支撑失稳立即停止","department","conservation","department-head","keeper","leader","security","proof","proof")
    private fun confirm(id: String, who: String, minute: Long, stage: EnumIncidentWorkStage = EnumIncidentWorkStage.PLAN) {
        support.actor(who)
        service.confirm(id,IncidentWorkConfirmationVo(stage,service.detail(id)[if(stage==EnumIncidentWorkStage.PLAN)"planBasis" else "endBasis"] as String,at.plusMinutes(minute),"本人核对真实原件","proof"))
    }
    private fun approve(id: String) { listOf("conservation","department-head","keeper","leader").forEachIndexed { i,who -> confirm(id,who,10L+i) };support.actor("operator") }
    private fun start() = IncidentWorkStartVo(at.plusHours(1),"原库现场","conservation","custodian","security",true,true,true,true,"原实物在原库","按实际方案布置支撑","proof")
    private fun end() = IncidentWorkEndVo(at.plusHours(2),true,"已完成原地支撑","现场稳定",true,false,"本次防护结束，事故仍需处理","继续定期核查","proof")
    private fun finish(id: String, value: IncidentWorkEndVo = end()) { service.end(id,value);confirm(id,"conservation",130,EnumIncidentWorkStage.END);confirm(id,"keeper",131,EnumIncidentWorkStage.END);support.actor("operator") }
    private fun assertWorkSelection(stage:String,change:String)=fixture { case ->
        val id=service.create(plan(case));val root=IncidentWorkDao[id]
        if(stage!="PLAN")approve(id)
        if(stage in setOf("STARTED","ENDED","COMPLETED"))service.start(id,start())
        if(stage=="ENDED")service.end(id,end())
        if(stage=="COMPLETED") {
            finish(id)
            val detail=incidents.detail(case);val facts=(detail["facts"] as IncidentFactsSnapshot).facts
            incidents.amend(case,facts.copy(relatedWorkIds=setOf(id),replacementReason="归档实际结束",replacementFileIds="proof"),detail["factsId"] as String)
        }
        if(stage=="CANCELED")service.cancel(id,IncidentWorkCancelVo(at.plusHours(1),"未实施取消","proof"))
        val oldState=root.state
        val row=IncidentWorkRecordDao.find { IncidentWorkRecords.workId eq id }.maxBy {it.sequence}
        val oldOwner=row.workId;val oldContent=row.content
        when(change) {
            "PLAN" -> root.state=EnumIncidentWorkState.PLAN
            "CANCELED" -> root.state=EnumIncidentWorkState.CANCELED
            "SIGNATURE" -> row.workId="detached"
            "CONTENT" -> row.content="{}"
            else -> error(change)
        }
        val tables=arrayOf(IncidentWorks,IncidentWorkRecords,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,Collections,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val before=settledSafetySelectionState(*tables)
        assertSafetySelectionIntegrity({guard.requireSafeResolution("piece")},{guard.requireExclusiveWork("piece","other","other")},{guard.requireAllowed("piece","other","other")})
        assertEquals(before,settledSafetySelectionState(*tables))
        root.state=oldState;row.workId=oldOwner;row.content=oldContent;settledSafetySelectionState(*tables)
        if(stage in setOf("APPROVED","STARTED","ENDED"))assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        else assertDoesNotThrow {guard.requireSafeResolution("piece")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("APPROVED,PLAN","APPROVED,CANCELED","STARTED,PLAN","STARTED,CANCELED","ENDED,PLAN","ENDED,CANCELED","COMPLETED,PLAN","COMPLETED,CANCELED","CANCELED,PLAN","COMPLETED,SIGNATURE","PLAN,CONTENT")
    fun `operation safety selection work verifies state and sealed records before filtering`(stage:String,change:String)=assertWorkSelection(stage,change)
    @Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("APPROVED,PLAN","APPROVED,CANCELED","STARTED,PLAN","STARTED,CANCELED","ENDED,PLAN","ENDED,CANCELED","COMPLETED,PLAN","COMPLETED,CANCELED","CANCELED,PLAN","COMPLETED,SIGNATURE","PLAN,CONTENT")
    fun `postgres operation safety selection work verifies state and sealed records before filtering`(stage:String,change:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertWorkSelection(stage,change)} finally {safetyDatabase=null}
    }
    private fun assertWorkRealAudit(kind:String)=fixture {case ->
        val id=service.create(plan(case));val root=IncidentWorkDao[id];val row=IncidentWorkRecordDao.all().single()
        settledSafetySelectionState(IncidentWorks,IncidentWorkRecords)
        support.actor("keeper")
        if(kind=="ROOT")assertSafetySelectionRealAudit(IncidentWorks,root){root.collectionId="actual-second-piece"}
        else assertSafetySelectionRealAudit(IncidentWorkRecords,row){row.workId="actual-second-work"}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["ROOT","RECORD"])
    fun `operation safety selection work preserves real audit changes`(kind:String)=assertWorkRealAudit(kind)
    @Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["ROOT","RECORD"])
    fun `postgres operation safety selection work preserves real audit changes`(kind:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertWorkRealAudit(kind)} finally {safetyDatabase=null}
    }
    @Test fun `onsite protection requires four actual actors and never clears incident lifecycle or original location`() = fixture { case ->
        assertThrows(BadRequestException::class.java){service.create(plan(case).copy(preparedAt=at.minusMinutes(1)))}
        val id=service.create(plan(case))
        assertThrows(BadRequestException::class.java){confirm(id,"operator",10)}
        assertThrows(BadRequestException::class.java){confirm(id,"leader",10)}
        assertThrows(BadRequestException::class.java){service.start(id,start())}
        approve(id)
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        service.start(id,start());service.start(id,start());finish(id)
        assertEquals(EnumIncidentWorkState.COMPLETED,IncidentWorkDao[id].state)
        assertTrue(incidents.restrictedCollectionIds("museum").contains("piece"))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
        assertEquals("store",CollectionDao["piece"].warehouseId)
        assertEquals(1,service.list(case).size)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
    }
    @Test fun `start rejects relocation restoration unsafe absent or changed people and preserves approved state`() = fixture { case ->
        val id=service.create(plan(case));approve(id)
        for(v in listOf(start().copy(noRelocation=false),start().copy(noRestorationOrSampling=false),start().copy(physicallyPresent=false),start().copy(siteSafe=false),start().copy(location="新库"),start().copy(conservationUserId="other"),start().copy(custodianUserId="conservation"),start().copy(startedAt=at))) assertThrows(BadRequestException::class.java){service.start(id,v)}
        assertEquals(EnumIncidentWorkState.APPROVED,IncidentWorkDao[id].state)
        assertTrue(service.detail(id)["records"].toString().contains("CONFIRMATION"))
        service.start(id,start())
    }
    @Test fun `all incident restrictions must be covered and new facts invalidate an old authorization`() = fixture { first ->
        val second=case()
        assertThrows(BadRequestException::class.java){service.create(plan(first))}
        val id=service.create(plan(first,second));approve(id)
        val old=incidents.detail(first)["facts"] as IncidentFactsSnapshot
        incidents.amend(first,old.facts.copy(description="新增实际发现",replacementReason="新核实",replacementFileIds="proof"),incidents.detail(first)["factsId"] as String)
        assertEquals(true,service.detail(id)["factsChanged"])
        assertThrows(BadRequestException::class.java){service.start(id,start())}
        service.cancel(id,IncidentWorkCancelVo(at.plusHours(1),"依据变化，未实施方案取消","proof"))
        assertTrue(incidents.restrictedCollectionIds("museum").contains("piece"))
    }
    @Test fun `other approvals and missing or external objects cannot use protection to bypass original custody`() = fixture { case ->
        assertThrows(BadRequestException::class.java){service.create(plan(case).copy(collectionId="foreign"))}
        CollectionOperationLockDao.new {orgId="museum";collectionId="piece";bizType=CollectionConst.BIZ_LOAN;bizId="loan"}
        assertThrows(BadRequestException::class.java){service.create(plan(case))}
        assertEquals(1,CollectionOperationLockDao.all().count())
        CollectionOperationLockDao.all().single().delete()
        for(state in listOf(EnumCollectionStatus.MISSING,EnumCollectionStatus.ON_LOAN,EnumCollectionStatus.IN_USE,EnumCollectionStatus.RESTORING)) {
            CollectionDao["piece"].lifeStatus=state
            assertThrows(BadRequestException::class.java){service.create(plan(case))}
        }
    }
    @Test fun `unimplemented cancel cannot be backdated before approvals and started work must record actual end`() = fixture { case ->
        val id=service.create(plan(case));approve(id)
        assertThrows(BadRequestException::class.java){service.cancel(id,IncidentWorkCancelVo(at,"取消","proof"))}
        service.start(id,start())
        assertThrows(BadRequestException::class.java){service.cancel(id,IncidentWorkCancelVo(at.plusHours(2),"作废实际作业","proof"))}
        finish(id)
        assertThrows(BadRequestException::class.java){service.end(id,end().copy(conditionAfter="覆盖旧结果"))}
    }
    @Test fun `two simultaneous approved protection operations on one object are prevented`() = fixture { case ->
        val first=service.create(plan(case));val second=service.create(plan(case));approve(first)
        assertThrows(BadRequestException::class.java){confirm(second,"conservation",20)}
        support.actor("operator");service.cancel(first,IncidentWorkCancelVo(at.plusMinutes(30),"未实施取消","proof"));approve(second)
        assertEquals(EnumIncidentWorkState.APPROVED,IncidentWorkDao[second].state)
    }
    @Test fun `safety observations and truthful stopping remain possible after incident facts change`() = fixture { case ->
        val id=service.create(plan(case));approve(id);service.start(id,start())
        val old=incidents.detail(case)["facts"] as IncidentFactsSnapshot
        incidents.amend(case,old.facts.copy(description="发现更多风险",replacementReason="现场核实",replacementFileIds="proof"),incidents.detail(case)["factsId"] as String)
        service.observation(id,IncidentWorkObservationVo(at.plusMinutes(90),"custodian","现场不安全",true,false,"停止作业并继续保护现场","proof"))
        finish(id,end().copy(workPerformed=false,actualMeasures="发现风险后未继续实施，仅保护现场"))
        assertEquals(EnumIncidentWorkState.COMPLETED,IncidentWorkDao[id].state)
        assertTrue(incidents.restrictedCollectionIds("museum").contains("piece"))
    }
    @Test fun `extra damage requires honest end and linked new incident facts before reporting and clearance`() = fixture { case ->
        val id=service.create(plan(case));approve(id);service.start(id,start())
        service.observation(id,IncidentWorkObservationVo(at.plusMinutes(90),"custodian","新增裂隙",true,true,"停止并保留现场","proof"))
        assertThrows(BadRequestException::class.java){service.end(id,end())}
        finish(id,end().copy(unexpectedDamage=true,withinApprovedScope=false))
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        val old=incidents.detail(case)["facts"] as IncidentFactsSnapshot
        val updated=old.facts.copy(relatedWorkIds=setOf(id),replacementReason="追加现场防护实际损伤",replacementFileIds="proof")
        assertThrows(BadRequestException::class.java){incidents.amend(case,updated,incidents.detail(case)["factsId"] as String)}
        incidents.amend(case,updated.copy(items=updated.items.map {it.copy(damaged=true)}),incidents.detail(case)["factsId"] as String)
        IncidentSourceTestSupport(incidents,at.plusHours(3),"proof").close(case)
        assertFalse(incidents.restrictedCollectionIds("museum").contains("piece"))
    }
    @Test fun `ongoing work and unfiled normal results prevent safe clearance until linked actual end is jointly confirmed`() = fixture { case ->
        val id=service.create(plan(case));approve(id);service.start(id,start())
        assertThrows(BadRequestException::class.java){IncidentSourceTestSupport(incidents,at.plusHours(3),"proof").close(case)}
        assertEquals(false,incidents.detail(case)["closed"])
        service.end(id,end());confirm(id,"conservation",130,EnumIncidentWorkStage.END)
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        assertThrows(BadRequestException::class.java){confirm(id,"leader",131,EnumIncidentWorkStage.END)}
        confirm(id,"keeper",131,EnumIncidentWorkStage.END)
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        val old = incidents.detail(case)["facts"] as IncidentFactsSnapshot
        incidents.amend(case,old.facts.copy(relatedWorkIds=setOf(id),replacementReason="归入实际防护结果",replacementFileIds="proof"),incidents.detail(case)["factsId"] as String)
        assertThrows(BadRequestException::class.java){support.close(case)}
        IncidentSourceTestSupport(incidents,at.plusHours(3),"proof").close(case)
        assertEquals(true,incidents.detail(case)["closed"])
    }
    @Test fun `out of window end must preserve overrun rather than claim approved scope`() = fixture { case ->
        val id=service.create(plan(case));approve(id);service.start(id,start())
        assertThrows(BadRequestException::class.java){service.end(id,end().copy(endedAt=at.plusHours(4)))}
        service.end(id,end().copy(endedAt=at.plusHours(4),withinApprovedScope=false))
        assertEquals(EnumIncidentWorkState.ENDED,IncidentWorkDao[id].state)
    }
    @Test fun `spring injects onsite work guard into both ordinary operations and incident safe resolution`() = fixture { case ->
        val id=service.create(plan(case));approve(id)
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { runtime ->
            runtime.beanFactory.registerSingleton("scope",scope)
            runtime.beanFactory.registerSingleton("users",users)
            runtime.beanFactory.registerSingleton("archives",archives)
            runtime.register(IncidentWorkGuard::class.java,CollectionOperationLockService::class.java,CollectionIncidentService::class.java,CollectionIncidentSourceService::class.java)
            runtime.refresh()
            assertThrows(BadRequestException::class.java){runtime.getBean(CollectionOperationLockService::class.java).requireUnlocked("piece")}
            val actual=runtime.getBean(CollectionIncidentService::class.java)
            assertThrows(BadRequestException::class.java){IncidentSourceTestSupport(actual,at.plusHours(3),"proof").close(case)}
            assertFalse(CollectionIncidentDao[case].closed)
            assertTrue(incidents.restrictedCollectionIds("museum").contains("piece"))
        }
    }
    @Test fun `tampered permanent evidence cannot authorize start`() = fixture { case ->
        val id=service.create(plan(case));approve(id)
        val row=CollectionPermanentArchiveDao.all().first {it.bizType==IncidentWorkService.BIZ}
        row.snapshot="{}"
        assertThrows(BadRequestException::class.java){service.start(id,start())}
        assertEquals(EnumIncidentWorkState.APPROVED,IncidentWorkDao[id].state)
    }
    @Test fun `missing original file rolls back plan records and parent work together`() {
        val db=database();val case=transaction(db){case()}
        assertThrows(BadRequestException::class.java){transaction(db){service.create(plan(case).copy(signedPlanFileIds="missing"))}}
        transaction(db){assertEquals(0,IncidentWorkDao.all().count());assertEquals(0,IncidentWorkRecordDao.all().count());assertTrue(incidents.restrictedCollectionIds("museum").contains("piece"))}
    }
    @Test fun `explicit false and millisecond evidence survive serialization`() {
        val value=end().copy(endedAt=at.plusHours(2).withNano(123000000),workPerformed=false,withinApprovedScope=false)
        assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,IncidentWorkEndVo::class.java))
    }
    private fun assertWorkOriginals(phase:String,damage:String,serviceEntrypoints:Boolean=false)=fixture { case ->
        fun file(label:String)=safetyOriginalFile(safetyOriginalRoot,label)
        val id=service.create(plan(case).copy(siteAndAssignmentFileIds=file("PLAN"),signedPlanFileIds="PLAN"))
        fun sign(who:String,minute:Long,stage:EnumIncidentWorkStage,label:String) {
            support.actor(who);service.confirm(id,IncidentWorkConfirmationVo(stage,service.detail(id)[if(stage==EnumIncidentWorkStage.PLAN)"planBasis" else "endBasis"] as String,at.plusMinutes(minute),"本人核对",file(label)))
        }
        if(phase=="CANCELED")service.cancel(id,IncidentWorkCancelVo(at.plusHours(1),"未实施取消",file("CANCELED")))
        else {
            listOf("conservation","department-head","keeper","leader").forEachIndexed {i,who->sign(who,10L+i,EnumIncidentWorkStage.PLAN,"PLAN_$who")}
            support.actor("operator");service.start(id,start().copy(signedStartFileIds=file("STARTED")))
            service.observation(id,IncidentWorkObservationVo(at.plusMinutes(70),"conservation","实际稳定",false,false,"继续核查",file("OBSERVATION")))
            service.end(id,end().copy(signedEndFileIds=file("ENDED")))
            sign("conservation",130,EnumIncidentWorkStage.END,"END_conservation");sign("keeper",131,EnumIncidentWorkStage.END,"END_keeper");support.actor("operator")
            val detail=incidents.detail(case);val facts=(detail["facts"] as IncidentFactsSnapshot).facts
            incidents.amend(case,facts.copy(relatedWorkIds=setOf(id),replacementReason="归档实际结果",replacementFileIds="proof"),detail["factsId"] as String)
        }
        val target=when(phase){"PLAN_CONFIRMATION"->"PLAN_conservation";"END_CONFIRMATION"->"END_keeper";else->phase}
        val archive=CollectionPermanentArchiveFileDao.all().single {it.bizId==id && it.fileId==target}.archiveId.let(CollectionPermanentArchiveDao::get)
        val tables=arrayOf(IncidentWorks,IncidentWorkRecords,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,Collections,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val repeat:()->Unit=if(phase=="CANCELED") {
            val cancel=JsonUtils.fromJson(IncidentWorkRecordDao.find {IncidentWorkRecords.workId eq id}.single {it.kind==EnumIncidentWorkRecordKind.CANCELED}.content,IncidentWorkCancelVo::class.java)!!
            val action:()->Unit={service.cancel(id,cancel);Unit}
            action
        } else {
            val end=JsonUtils.fromJson(IncidentWorkRecordDao.find {IncidentWorkRecords.workId eq id}.single {it.kind==EnumIncidentWorkRecordKind.ENDED}.content,IncidentWorkEndVo::class.java)!!
            val action:()->Unit={service.end(id,end);Unit}
            action
        }
        val readActions=if(serviceEntrypoints)arrayOf<()->Unit>({service.detail(id);Unit},{service.list(case);Unit}) else emptyArray()
        val actions=if(serviceEntrypoints)arrayOf<()->Unit>(
            {service.requireArchivedOriginal(id,archive.id.value,target)},repeat,
        ) else arrayOf<()->Unit>({guard.requireSafeResolution("piece")},{guard.requireExclusiveWork("piece","other","other")},{guard.requireAllowed("piece","other","other")})
        assertSafetyOriginalDamage(safetyOriginalRoot,archive,target,damage,tables,actions,readActions){(actions+readActions).forEach {assertDoesNotThrow(it)}}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,MISSING",
        "PLAN,IDENTITY",
        "PLAN,EXTRA",
        "PLAN,BYTES",
        "PLAN,BASELINE",
        "PLAN_CONFIRMATION,MISSING",
        "PLAN_CONFIRMATION,IDENTITY",
        "PLAN_CONFIRMATION,EXTRA",
        "PLAN_CONFIRMATION,BYTES",
        "PLAN_CONFIRMATION,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE")
    fun `operation safety originals work rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertWorkOriginals(phase,damage)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,MISSING",
        "PLAN,IDENTITY",
        "PLAN,EXTRA",
        "PLAN,BYTES",
        "PLAN,BASELINE",
        "PLAN_CONFIRMATION,MISSING",
        "PLAN_CONFIRMATION,IDENTITY",
        "PLAN_CONFIRMATION,EXTRA",
        "PLAN_CONFIRMATION,BYTES",
        "PLAN_CONFIRMATION,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE")
    fun `postgres operation safety originals work rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertWorkOriginals(phase,damage)} finally {safetyDatabase=null}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,MISSING",
        "PLAN,IDENTITY",
        "PLAN,EXTRA",
        "PLAN,BYTES",
        "PLAN,BASELINE",
        "PLAN_CONFIRMATION,MISSING",
        "PLAN_CONFIRMATION,IDENTITY",
        "PLAN_CONFIRMATION,EXTRA",
        "PLAN_CONFIRMATION,BYTES",
        "PLAN_CONFIRMATION,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE")
    fun `operation safety direct entries originals work rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertWorkOriginals(phase,damage,true)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
        "PLAN,MISSING",
        "PLAN,IDENTITY",
        "PLAN,EXTRA",
        "PLAN,BYTES",
        "PLAN,BASELINE",
        "PLAN_CONFIRMATION,MISSING",
        "PLAN_CONFIRMATION,IDENTITY",
        "PLAN_CONFIRMATION,EXTRA",
        "PLAN_CONFIRMATION,BYTES",
        "PLAN_CONFIRMATION,BASELINE",
        "STARTED,MISSING",
        "STARTED,IDENTITY",
        "STARTED,EXTRA",
        "STARTED,BYTES",
        "STARTED,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "ENDED,MISSING",
        "ENDED,IDENTITY",
        "ENDED,EXTRA",
        "ENDED,BYTES",
        "ENDED,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE")
    fun `postgres operation safety direct entries originals work rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertWorkOriginals(phase,damage,true)} finally {safetyDatabase=null}
    }

}
