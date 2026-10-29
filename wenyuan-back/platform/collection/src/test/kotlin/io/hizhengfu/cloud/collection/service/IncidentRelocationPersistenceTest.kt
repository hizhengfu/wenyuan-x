package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.context.support.StaticApplicationContext
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class IncidentRelocationPersistenceTest {
    private val safetyOriginalRoot=createSafetyOriginalRoot()
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {v->v.getArgument<String>(0)}}
    private val archives=CollectionPermanentArchiveService(binder,scope,io.hizhengfu.cloud.common.file.LocalFileObjectStorage(safetyOriginalRoot.toString()),unprotectedArchiveReadAccessProvider())
    private val guard=IncidentRelocationGuard(archives)
    private val guards:List<IncidentResolutionGuard> = listOf(IncidentWorkGuard(archives),guard)
    private val incidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService(),guards)
    private val work=IncidentWorkService(scope,users,incidents,archives,guards)
    private val service=IncidentRelocationService(scope,users,incidents,work,archives,guards)
    private val at=LocalDateTime.of(2026,9,1,9,0)
    private val support=IncidentSourceTestSupport(incidents,at,"proof")
    private val context=StaticApplicationContext()
    private val files=mutableListOf<java.nio.file.Path>()
    private var safetyDatabase:Database?=null
    @BeforeEach fun prepare(){context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);support.actor("operator")}
    @AfterEach fun cleanup(){context.close();SecurityContextHolder.clearContext();removeSafetyOriginalRoot(safetyOriginalRoot);files.forEach {java.nio.file.Files.deleteIfExists(it)}}
    private fun database():Database {
        val db=safetyDatabase ?: java.nio.file.Files.createTempFile("incident-relocation-",".db").also {files.add(it)}.let {Database.connect("jdbc:sqlite:$it",driver="org.sqlite.JDBC")}
        transaction(db) {
            SchemaUtils.create(CollectionPlacements,Collections,CollectionLedgerLogs,CollectionOrgs,Warehouses,CollectionIncidents,CollectionIncidentItems,CollectionIncidentRecords,IncidentWorks,IncidentWorkRecords,IncidentRelocations,IncidentRelocationRecords,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            actorRole("operator","STAFFING_SETUP",setOf(CollectionConst.WAREHOUSE_MANAGE))
            CollectionOrgDao.new("other"){name="外馆";code="O"}
            FileDao.new("proof"){name="现场真实原件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            FileDao.new("packing"){name="逐件包装核验.pdf";path="packing";hash="packing";size=100;ext="pdf"}
            listOf("from","to","alternate").forEach {id ->
                WarehouseDao.new(id){orgId="museum";name=id;sceneType=EnumLocationScene.STORAGE;type=EnumWarehouseType.WAREHOUSE}
                WarehouseStaffingService(scope,users,archives).save(id,WarehouseStaffingVo(setOf("custodian","receiver"),"keeper","leader",at.minusDays(1),"proof"))
            }
            WarehouseDao.new("foreign"){orgId="other";name="外馆地点";sceneType=EnumLocationScene.STORAGE}
            CollectionDao.new("piece"){orgId="museum";name="器物";totalRegNo="M1";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="from"}
        }
        return db
    }
    private fun case()=support.create(null,listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"现场需要移离风险")))
    private fun fixture(block:(String)->Unit)=transaction(database()){block(case())}
    private fun plan(id:String)=IncidentRelocationPlanVo("piece",setOf(id),at,at.plusHours(1),at.plusHours(3),"from","to","原地措施不足，须移离风险","原实物现状","依据质地现状双人取放","专用支撑包护","本馆实际安全通道","目的地及全程安全防护","发现失稳立即停止",at.minusMinutes(1),true,"department","conservation","department-head","keeper","leader","custodian","receiver","security","本馆运输组","supervisor","proof","proof","proof","proof",
        transportStandardReference="GB/T 23862-2024",transportStandardApplicability="逐件核对文物包装运输条件")
    private fun departure()=IncidentRelocationDepartureVo(at.plusHours(1),"from",setOf("custodian","conservation"),"supervisor",true,true,"实际搬出前现状","实际支撑包护","proof","proof",
        appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="proof")
    private fun receipt()=IncidentRelocationReceiptVo(at.plusHours(2),"to",setOf("receiver","conservation"),"receiver",true,true,true,false,true,"实际接收现状","独立隔离支撑保管","持续核查并处理事故","proof","proof")
    private fun confirm(id:String,who:String,minute:Long,stage:EnumIncidentWorkStage=EnumIncidentWorkStage.PLAN) {
        support.actor(who);service.confirm(id,IncidentWorkConfirmationVo(stage,service.detail(id)[if(stage==EnumIncidentWorkStage.PLAN)"planBasis"else"endBasis"] as String,at.plusMinutes(minute),"本人核对本版实际原件","proof"))
    }
    private fun approve(id:String){listOf("conservation","department-head","keeper","leader").forEachIndexed {i,who->confirm(id,who,10L+i)};support.actor("operator")}
    private fun finish(id:String){confirm(id,"conservation",130,EnumIncidentWorkStage.END);confirm(id,"keeper",131,EnumIncidentWorkStage.END);support.actor("operator")}
    private fun emergency(id:String)=IncidentEmergencyRelocationVo("piece",setOf(id),"突发险情，已先行移离危险","按实际指挥及现场经过补记，未取得本流程事前批准","proof","conservation","keeper",departure(),receipt())
    private fun link(caseId:String,id:String,damage:Boolean=false) {
        val detail=incidents.detail(caseId);val facts=(detail["facts"] as IncidentFactsSnapshot).facts
        incidents.amend(caseId,facts.copy(relatedRelocationIds=facts.relatedRelocationIds+id,items=facts.items.map {it.copy(damaged=it.damaged||damage)},replacementReason="补入实际搬移结果",replacementFileIds="proof"),detail["factsId"] as String)
    }
    private fun assertRelocationSelection(stage:String,change:String)=fixture {case ->
        val id=if(stage=="EMERGENCY")service.recordEmergency(emergency(case)) else service.create(plan(case))
        val root=IncidentRelocationDao[id]
        if(stage !in setOf("PLAN","EMERGENCY"))approve(id)
        if(stage in setOf("DEPARTED","RECEIVED","COMPLETED"))service.depart(id,departure())
        if(stage in setOf("RECEIVED","COMPLETED"))service.receive(id,receipt())
        if(stage=="COMPLETED") {finish(id);link(case,id)}
        if(stage=="CANCELED")service.cancel(id,IncidentWorkCancelVo(at.plusHours(1),"未实施取消","proof"))
        val oldState=root.state
        val row=IncidentRelocationRecordDao.find { IncidentRelocationRecords.relocationId eq id }.maxBy {it.sequence}
        val oldOwner=row.relocationId;val oldContent=row.content
        when(change) {
            "PLAN" -> root.state=EnumIncidentRelocationState.PLAN
            "CANCELED" -> root.state=EnumIncidentRelocationState.CANCELED
            "SIGNATURE" -> row.relocationId="detached"
            "CONTENT" -> row.content="{}"
            else -> error(change)
        }
        val tables=arrayOf(IncidentRelocations,IncidentRelocationRecords,IncidentWorks,IncidentWorkRecords,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,Collections,CollectionOperationLocks,StorageMovements,CollectionLedgerLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val before=settledSafetySelectionState(*tables)
        assertSafetySelectionIntegrity({guard.requireSafeResolution("piece")},{guard.requireExclusiveWork("piece","other","other")},{guard.requireAllowed("piece","other","other")})
        assertEquals(before,settledSafetySelectionState(*tables))
        root.state=oldState;row.relocationId=oldOwner;row.content=oldContent;settledSafetySelectionState(*tables)
        if(stage in setOf("APPROVED","DEPARTED","RECEIVED","EMERGENCY"))assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        else assertDoesNotThrow {guard.requireSafeResolution("piece")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("APPROVED,PLAN","APPROVED,CANCELED","DEPARTED,PLAN","DEPARTED,CANCELED","RECEIVED,PLAN","RECEIVED,CANCELED","COMPLETED,PLAN","CANCELED,PLAN","COMPLETED,SIGNATURE","PLAN,CONTENT","EMERGENCY,CANCELED")
    fun `operation safety selection relocation verifies state and sealed records before filtering`(stage:String,change:String)=assertRelocationSelection(stage,change)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("APPROVED,PLAN","APPROVED,CANCELED","DEPARTED,PLAN","DEPARTED,CANCELED","RECEIVED,PLAN","RECEIVED,CANCELED","COMPLETED,PLAN","CANCELED,PLAN","COMPLETED,SIGNATURE","PLAN,CONTENT","EMERGENCY,CANCELED")
    fun `postgres operation safety selection relocation verifies state and sealed records before filtering`(stage:String,change:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertRelocationSelection(stage,change)} finally {safetyDatabase=null}
    }
    private fun assertRelocationRealAudit(kind:String)=fixture {case ->
        val id=service.create(plan(case));val root=IncidentRelocationDao[id];val row=IncidentRelocationRecordDao.all().single()
        if(kind in setOf("MOVEMENT","LEDGER")) {
            approve(id);service.depart(id,departure())
            val table=if(kind=="MOVEMENT")StorageMovements else CollectionLedgerLogs
            val before=settledSafetySelectionState(table).getValue(table.tableName).single()
            support.actor("keeper");service.receive(id,receipt())
            val after=settledSafetySelectionState(table).getValue(table.tableName)
            assertEquals(2,after.size);assertTrue(before in after,"Previously recorded movement or ledger row must remain frozen")
            val fresh=table.selectAll().single {it[table.createBy]=="keeper"}
            assertEquals("keeper",fresh[table.updateBy]);assertNotNull(fresh[table.createTime]);assertNotNull(fresh[table.updateTime])
        } else {
            settledSafetySelectionState(IncidentRelocations,IncidentRelocationRecords);support.actor("keeper")
            if(kind=="ROOT")assertSafetySelectionRealAudit(IncidentRelocations,root){root.collectionId="actual-second-piece"}
            else assertSafetySelectionRealAudit(IncidentRelocationRecords,row){row.relocationId="actual-second-relocation"}
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["ROOT","RECORD","MOVEMENT","LEDGER"])
    fun `operation safety selection relocation preserves real audit and appended history`(kind:String)=assertRelocationRealAudit(kind)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["ROOT","RECORD","MOVEMENT","LEDGER"])
    fun `postgres operation safety selection relocation preserves real audit and appended history`(kind:String)=withSafetySelectionPostgres {database ->
        safetyDatabase=database
        try {assertRelocationRealAudit(kind)} finally {safetyDatabase=null}
    }
    private fun retiredActualFacts(kind:String)=fixture {case ->
        val before=settledSafetySelectionState(StorageMovements,CollectionLedgerLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
        val id=if(kind=="departure") {
            WarehouseDao["from"].status=EnumContentStatus.DELETE
            service.recordEmergency(emergency(case).copy(receipt=null))
        } else {
            val departed=service.recordEmergency(emergency(case).copy(receipt=null))
            WarehouseDao["to"].status=EnumContentStatus.DELETE
            service.receive(departed,receipt().copy(safeForCustody=false))
        }
        val root=IncidentRelocationDao[id]
        assertEquals(EnumIncidentRelocationMode.EMERGENCY_RECORD,root.mode)
        assertEquals(if(kind=="departure")EnumIncidentRelocationState.DEPARTED else EnumIncidentRelocationState.RECEIVED,root.state)
        assertFalse(IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq id}.any {it.kind==EnumIncidentRelocationKind.PLAN})
        assertEquals(if(kind=="departure")null else "to",CollectionDao["piece"].warehouseId)
        val moves=StorageMovementDao.all().toList().sortedBy {it.operatedAt}
        assertEquals(if(kind=="departure")1 else 2,moves.size)
        assertEquals("from",moves.first().fromWarehouseId)
        if(kind=="receipt") {
            val recorded=IncidentRelocationEvidence.receipt(root,archives)!!
            assertEquals("to",recorded.toLocationId)
            assertFalse(recorded.safeForCustody)
            assertEquals("to",moves.last().toWarehouseId)
        }
        val after=settledSafetySelectionState(StorageMovements,CollectionLedgerLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
        before.forEach {(table,rows)->assertTrue(rows.all {it in after.getValue(table)},"Earlier $table rows must remain frozen")}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["departure","receipt"])
    fun `actual emergency facts keep retired source and destination identity`(kind:String)=retiredActualFacts(kind)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["departure","receipt"])
    fun `postgres actual emergency facts keep retired source and destination identity`(kind:String)=withSafetySelectionPostgres {db->
        safetyDatabase=db
        try {retiredActualFacts(kind)} finally {safetyDatabase=null}
    }
    private fun rejectRetiredPlan(location:String)=fixture {case ->
        WarehouseDao[location].status=EnumContentStatus.DELETE
        val tables=arrayOf<org.jetbrains.exposed.v1.core.Table>(IncidentRelocations,IncidentRelocationRecords,StorageMovements,Collections,CollectionLedgerLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
        val before=settledSafetySelectionState(*tables)
        val error=assertThrows(BadRequestException::class.java) {service.create(plan(case))}
        assertTrue(error.message.orEmpty().contains("退役"),error.message)
        assertEquals(before,settledSafetySelectionState(*tables))
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["from","to"])
    fun `new relocation plan excludes retired locations without replacing recorded facts`(location:String)=rejectRetiredPlan(location)
    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["from","to"])
    fun `postgres new relocation plan excludes retired locations without replacing recorded facts`(location:String)=withSafetySelectionPostgres {db->
        safetyDatabase=db
        try {rejectRetiredPlan(location)} finally {safetyDatabase=null}
    }
    @Test fun `planned move requires actual ordered actors and only real departure and receipt change location`()=fixture {case ->
        val id=service.create(plan(case));assertEquals("from",CollectionDao["piece"].warehouseId)
        assertThrows(BadRequestException::class.java){service.depart(id,departure())}
        assertThrows(BadRequestException::class.java){confirm(id,"operator",10)}
        assertThrows(BadRequestException::class.java){confirm(id,"leader",10)}
        approve(id);assertEquals("from",CollectionDao["piece"].warehouseId)
        service.depart(id,departure());service.depart(id,departure());assertNull(CollectionDao["piece"].warehouseId)
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        service.receive(id,receipt());service.receive(id,receipt());finish(id)
        assertEquals("to",CollectionDao["piece"].warehouseId);assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
        val warehouseLogs=CollectionLedgerLogDao.all().filter { it.field=="warehouseId" }
        assertEquals(2,warehouseLogs.size)
        assertEquals(setOf("from" to null,null to "to"),warehouseLogs.map { it.before to it.after }.toSet())
        assertTrue(warehouseLogs.all { it.refBizId==id })
        val moves=StorageMovementDao.all().toList().sortedBy {it.operatedAt}
        assertEquals(2,moves.size);assertEquals(listOf(EnumMoveType.OUT,EnumMoveType.IN),moves.map {it.type})
        assertEquals(listOf(at.plusHours(1),at.plusHours(2)),moves.map {it.operatedAt})
        assertTrue(moves.all {it.reason==EnumMoveReason.EMERGENCY&&it.refBizId==id})
        assertTrue(moves.all {it.totalRegNoAtMovement=="M1"&&it.collectionNameAtMovement=="器物"})
        CollectionDao["piece"].name="事后订正名称"
        assertTrue(moves.all {it.toModel().collectionName=="器物"})
        assertTrue(incidents.restrictedCollectionIds("museum").contains("piece"))
    }
    @Test fun `planned relocation enforces applicable transport standard before departure and retains packing proof`()=fixture {case ->
        assertThrows(BadRequestException::class.java){service.create(plan(case).copy(transportStandardReference=null))}
        assertThrows(BadRequestException::class.java){service.create(plan(case).copy(transportStandardApplicability=null))}
        assertEquals(0,IncidentRelocationDao.all().count())
        val id=service.create(plan(case));approve(id)
        listOf(
            departure().copy(appliedTransportStandardReference=null),
            departure().copy(appliedTransportStandardReference="其他版本"),
            departure().copy(packingStandardConfirmed=false),
            departure().copy(packingCheckFileIds=null),
        ).forEach { value ->
            assertThrows(BadRequestException::class.java){service.depart(id,value)}
            assertEquals("from",CollectionDao["piece"].warehouseId)
            assertEquals(0,StorageMovementDao.all().count())
        }
        service.depart(id,departure().copy(packingCheckFileIds="packing"))
        val archived=CollectionPermanentArchiveDao.all().single { it.bizId==id && it.phase.startsWith("DEPARTURE_") }
        assertTrue(CollectionPermanentArchiveFileDao.all().filter { it.archiveId==archived.id.value }
            .any { it.fileId=="packing" })
        assertTrue(StorageMovementDao.all().single().attachmentIds.orEmpty().contains("packing"))
    }
    @Test fun `missing planned packing proof rolls back departure and permits corrected retry`() {
        val db=database()
        val id=transaction(db){val id=service.create(plan(case()));approve(id);id}
        assertThrows(BadRequestException::class.java){transaction(db){service.depart(id,departure().copy(packingCheckFileIds="missing-file"))}}
        transaction(db){
            assertEquals("from",CollectionDao["piece"].warehouseId)
            assertEquals(EnumIncidentRelocationState.APPROVED,IncidentRelocationDao[id].state)
            assertEquals(0,StorageMovementDao.all().count())
            service.depart(id,departure().copy(packingCheckFileIds="packing"))
            assertEquals(EnumIncidentRelocationState.DEPARTED,IncidentRelocationDao[id].state)
        }
    }
    @Test fun `unsafe foreign unchanged and unstaffed locations cannot authorize planned transport`()=fixture {case ->
        for(v in listOf(plan(case).copy(destinationSafe=false),plan(case).copy(toLocationId="foreign"),plan(case).copy(toLocationId="from"),plan(case).copy(fromLocationId="alternate"),plan(case).copy(fromCustodianUserId="stranger"),plan(case).copy(plannedArrival=at)))assertThrows(BadRequestException::class.java){service.create(v)}
        WarehouseDao["to"].staffing=null;assertThrows(BadRequestException::class.java){service.create(plan(case))}
        assertEquals(0,IncidentRelocationDao.all().toList().size)
    }
    @Test fun `new cases facts or location personnel invalidate authorization before new move`()=fixture {case ->
        val id=service.create(plan(case));approve(id)
        WarehouseDao["to"].name="目的地信息已变化"
        assertThrows(BadRequestException::class.java){service.depart(id,departure())}
        service.cancel(id,IncidentWorkCancelVo(at.plusMinutes(20),"条件变化，未执行取消","proof"))
        val another=case()
        assertThrows(BadRequestException::class.java){service.create(plan(case))}
        val next=service.create(plan(case).copy(incidentIds=setOf(case,another)));approve(next)
        val d=incidents.detail(case);val f=(d["facts"] as IncidentFactsSnapshot).facts
        incidents.amend(case,f.copy(description="追加风险",replacementReason="实地核实",replacementFileIds="proof"),d["factsId"] as String)
        assertThrows(BadRequestException::class.java){service.depart(next,departure())}
    }
    @Test fun `planned start rejects missing review unsafe route and times but actual received anomalies remain recordable`()=fixture {case ->
        val id=service.create(plan(case));approve(id)
        for(v in listOf(departure().copy(releasingUserIds=setOf("custodian")),departure().copy(routeSafe=false),departure().copy(departedAt=at),departure().copy(fromLocationId="alternate")))assertThrows(BadRequestException::class.java){service.depart(id,v)}
        service.depart(id,departure())
        assertThrows(BadRequestException::class.java){service.cancel(id,IncidentWorkCancelVo(at.plusHours(2),"作废","proof"))}
        service.observation(id,IncidentWorkObservationVo(at.plusMinutes(90),"supervisor","新增损伤",true,true,"停留并保护","proof"))
        assertThrows(BadRequestException::class.java){service.receive(id,receipt())}
        service.receive(id,receipt().copy(toLocationId="alternate",receivingUserIds=emptySet(),custodianUserId="",unexpectedDamage=true,safeForCustody=false,conditionMatchesDeparture=false,authenticityConfirmed=false))
        assertEquals("alternate",CollectionDao["piece"].warehouseId)
        val actual=IncidentRelocationEvidence.receipt(IncidentRelocationDao[id],archives);assertFalse(actual.safeForCustody);assertTrue(actual.unexpectedDamage)
        finish(id);assertThrows(BadRequestException::class.java){link(case,id)};link(case,id,true)
    }
    @Test fun `normal completed move must enter new case facts before safe clearance and handling cannot predate receipt`()=fixture {case ->
        val id=service.create(plan(case));approve(id);service.depart(id,departure());service.receive(id,receipt());finish(id)
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
        link(case,id)
        assertThrows(BadRequestException::class.java){support.close(case)}
        IncidentSourceTestSupport(incidents,at.plusHours(3),"proof").close(case)
        assertFalse(incidents.restrictedCollectionIds("museum").contains("piece"))
        assertDoesNotThrow{guard.requireSafeResolution("piece")}
        val d=incidents.detail(case);val f=(d["facts"] as IncidentFactsSnapshot).facts
        assertThrows(BadRequestException::class.java){incidents.amend(case,f.copy(relatedRelocationIds=emptySet(),replacementReason="删历史",replacementFileIds="proof"),d["factsId"] as String)}
    }
    @Test fun `onsite protection and planned relocation cannot both be approved for the same original`()=fixture {case ->
        val onsitePlan=IncidentWorkTestSupport(work,incidents,at,"proof").plan("piece",case)
        val onsite=work.create(onsitePlan);val move=service.create(plan(case));approve(move)
        support.actor("conservation")
        assertThrows(BadRequestException::class.java){work.confirm(onsite,IncidentWorkConfirmationVo(EnumIncidentWorkStage.PLAN,work.detail(onsite)["planBasis"] as String,at.plusMinutes(20),"本人核实","proof"))}
        support.actor("operator");service.cancel(move,IncidentWorkCancelVo(at.plusMinutes(25),"尚未实施，调整防护","proof"))
        listOf("conservation","department-head","keeper","leader").forEachIndexed {i,who->
            support.actor(who);work.confirm(onsite,IncidentWorkConfirmationVo(EnumIncidentWorkStage.PLAN,work.detail(onsite)["planBasis"] as String,at.plusMinutes(30L+i),"本人核实","proof"))
        }
        support.actor("operator");val later=service.create(plan(case))
        assertThrows(BadRequestException::class.java){confirm(later,"conservation",40)}
    }
    @Test fun `actual emergency record preserves missing prior approval unsafe facts and unrelated source occupancy`()=fixture {case ->
        CollectionOperationLockDao.new {orgId="museum";collectionId="piece";bizType=CollectionConst.BIZ_LOAN;bizId="source"}
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.PRE_OUT
        val value=emergency(case).copy(departure=departure().copy(routeSafe=false,releasingUserIds=emptySet(),transportSupervisorUserId=""),receipt=receipt().copy(safeForCustody=false,receivingUserIds=emptySet(),custodianUserId=""))
        val id=service.recordEmergency(value);assertEquals(id,service.recordEmergency(value))
        assertEquals(EnumIncidentRelocationMode.EMERGENCY_RECORD,IncidentRelocationDao[id].mode)
        assertEquals("to",CollectionDao["piece"].warehouseId);assertEquals(EnumCollectionStatus.PRE_OUT,CollectionDao["piece"].lifeStatus)
        assertEquals("source",CollectionOperationLockDao.all().single().bizId)
        assertFalse(IncidentRelocationRecordDao.all().toList().any {it.kind==EnumIncidentRelocationKind.PLAN})
        assertThrows(BadRequestException::class.java){confirm(id,"conservation",130)}
        assertThrows(BadRequestException::class.java){service.depart(id,departure().copy(routeSafe=true))}
        finish(id);link(case,id);IncidentSourceTestSupport(incidents,at.plusHours(3),"proof").close(case)
        assertEquals("source",CollectionOperationLockDao.all().single().bizId)
    }
    @Test fun `historical actual emergency never overwrites a later known physical location`()=fixture {case ->
        CollectionDao["piece"].warehouseId="alternate"
        StorageMovementDao.new {orgId="museum";collectionId="piece";type=EnumMoveType.IN;toWarehouseId="alternate";operatedAt=at.plusHours(4)}
        val id=service.recordEmergency(emergency(case))
        assertEquals("alternate",CollectionDao["piece"].warehouseId)
        val row=IncidentRelocationRecordDao.all().single {it.kind==EnumIncidentRelocationKind.RECEIPT}
        assertFalse(JsonUtils.fromJson(row.content,IncidentRelocationReceiptSnapshot::class.java)!!.updatesCurrentLocation)
        assertEquals(3,StorageMovementDao.all().toList().size)
        service.recordEmergency(emergency(case));assertEquals(3,StorageMovementDao.all().toList().size)
        assertThrows(BadRequestException::class.java){guard.requireSafeResolution("piece")}
    }
    @Test fun `emergency currently in transport can be recorded before receipt and late actual receipt is still available`()=fixture {case ->
        val id=service.recordEmergency(emergency(case).copy(receipt=null))
        assertEquals(EnumIncidentRelocationState.DEPARTED,IncidentRelocationDao[id].state);assertNull(CollectionDao["piece"].warehouseId)
        assertThrows(BadRequestException::class.java){link(case,id)}
        val d=incidents.detail(case);val f=(d["facts"] as IncidentFactsSnapshot).facts
        incidents.amend(case,f.copy(description="新增风险",replacementReason="实地核对",replacementFileIds="proof"),d["factsId"] as String)
        service.receive(id,receipt());finish(id);assertEquals("to",CollectionDao["piece"].warehouseId)
    }
    @Test fun `missing receipt file rolls back actual new movement location sequence and state`() {
        val db=database();val id=transaction(db){val id=service.create(plan(case()));approve(id);service.depart(id,departure());id}
        assertThrows(BadRequestException::class.java){transaction(db){service.receive(id,receipt().copy(signedFileIds="missing"))}}
        transaction(db){assertNull(CollectionDao["piece"].warehouseId);assertEquals(EnumIncidentRelocationState.DEPARTED,IncidentRelocationDao[id].state);assertEquals(1,StorageMovementDao.all().toList().size)}
    }
    @Test fun `permanent originals cannot be deleted or changed to authorize a move`()=fixture {case ->
        val id=service.create(plan(case));approve(id)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
        CollectionPermanentArchiveDao.all().first {it.bizType==IncidentRelocationService.BIZ}.snapshot="{}"
        assertThrows(BadRequestException::class.java){service.depart(id,departure())}
        assertEquals("from",CollectionDao["piece"].warehouseId)
    }
    @Test fun `actual emergency location overrides old showcase while keeping original return warehouse and placement history`()=fixture {case ->
        WarehouseDao["from"].sceneType=EnumLocationScene.EXHIBITION
        WarehouseDao["from"].type=EnumWarehouseType.SHOWCASE
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_USE
        CollectionDao["piece"].warehouseId="alternate"
        val placement=CollectionPlacementDao.new {orgId="museum";collectionId="piece";sourceBizType=CollectionConst.BIZ_EXHIBITION;sourceBizId="show";locationId="from";approved=true;validFrom=at.minusDays(1);validTo=at.plusDays(2)}
        val resolver=CollectionLocationResolver(listOf(IncidentRelocationLocationResolver()))
        assertTrue(resolver.collectionIdsAt("from",at).contains("piece"))
        val id=service.recordEmergency(emergency(case))
        assertEquals("alternate",CollectionDao["piece"].warehouseId)
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertEquals(at.plusHours(1),placement.validTo)
        assertTrue(resolver.collectionIdsAt("from",at.plusMinutes(30)).contains("piece"))
        assertFalse(resolver.collectionIdsAt("from",at.plusMinutes(90)).contains("piece"))
        assertFalse(resolver.collectionIdsAt("to",at.plusMinutes(90)).contains("piece"))
        assertTrue(resolver.collectionIdsAt("to",at.plusHours(2)).contains("piece"))
        assertFalse(resolver.collectionIdsAt("from",at.plusHours(2)).contains("piece"))
        assertEquals("to",service.context("piece")["currentLocationId"])
        assertTrue(CollectionPermanentArchiveDao.all().toList().any {it.bizId==id&&it.phase=="LOCATIONS_LEFT"})
        StorageMovementDao.new {orgId="museum";collectionId="piece";type=EnumMoveType.IN;reason=EnumMoveReason.INVENTORY;toWarehouseId="alternate";operatedAt=at.plusHours(4)}
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_STORE
        assertFalse(resolver.collectionIdsAt("to",at.plusHours(5)).contains("piece"))
        assertTrue(resolver.collectionIdsAt("alternate",at.plusHours(5)).contains("piece"))
    }
    @Test fun `storage location before departure in transit and after receipt follow actual movement times`()=fixture {case ->
        service.recordEmergency(emergency(case))
        val resolver=CollectionLocationResolver(listOf(IncidentRelocationLocationResolver()))
        assertTrue(resolver.collectionIdsAt("from",at.plusMinutes(30)).contains("piece"))
        assertFalse(resolver.collectionIdsAt("to",at.plusMinutes(30)).contains("piece"))
        assertFalse(resolver.collectionIdsAt("from",at.plusMinutes(90)).contains("piece"))
        assertFalse(resolver.collectionIdsAt("to",at.plusMinutes(90)).contains("piece"))
        assertTrue(resolver.collectionIdsAt("to",at.plusHours(3)).contains("piece"))
    }
    @Test fun `intervening physical emergency invalidates an old plan even after returning to the same place`()=fixture {case ->
        val old=service.create(plan(case).copy(plannedDeparture=at.plusHours(4),plannedArrival=at.plusHours(6)));approve(old)
        val actual=service.recordEmergency(emergency(case).copy(receipt=receipt().copy(toLocationId="from")))
        finish(actual)
        assertEquals("from",CollectionDao["piece"].warehouseId)
        assertThrows(BadRequestException::class.java){service.depart(old,departure().copy(departedAt=at.plusHours(4)))}
        assertEquals(EnumIncidentRelocationState.APPROVED,IncidentRelocationDao[old].state)
    }
    @Test fun `spring wiring includes both exclusive guards and ordinary movement cannot impersonate emergency`()=fixture {case ->
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use {runtime ->
            runtime.beanFactory.registerSingleton("scope",scope);runtime.beanFactory.registerSingleton("users",users);runtime.beanFactory.registerSingleton("archives",archives)
            runtime.register(CollectionIncidentService::class.java,CollectionIncidentSourceService::class.java,IncidentWorkGuard::class.java,IncidentRelocationGuard::class.java,IncidentWorkService::class.java,IncidentRelocationService::class.java,CollectionOperationLockService::class.java,CollectionIncidentOperationGuard::class.java,CollectionLocationResolver::class.java,IncidentRelocationLocationResolver::class.java);runtime.refresh()
            val actual=runtime.getBean(IncidentRelocationService::class.java);assertNotNull(actual.context("piece"))
            actual.recordEmergency(emergency(case))
            assertTrue(runtime.getBean(CollectionLocationResolver::class.java).collectionIdsAt("to").contains("piece"))
            assertThrows(BadRequestException::class.java){runtime.getBean(CollectionOperationLockService::class.java).requireUnlocked("piece")}
            assertThrows(BadRequestException::class.java){StorageMovementService(scope,binder,runtime.getBean(CollectionOperationLockService::class.java)).create(MovementVo(collectionId="piece",reason=EnumMoveReason.EMERGENCY,type=EnumMoveType.IN,toWarehouseId="to"))}
        }
    }
    private fun assertRelocationOriginals(phase:String,damage:String,serviceEntrypoints:Boolean=false)=fixture { case ->
        fun file(label:String)=safetyOriginalFile(safetyOriginalRoot,label)
        if(phase=="LOCATIONS_LEFT") {
            WarehouseDao["from"].sceneType=EnumLocationScene.EXHIBITION;WarehouseDao["from"].type=EnumWarehouseType.SHOWCASE
            CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_USE;CollectionDao["piece"].warehouseId="alternate"
            CollectionPlacementDao.new {orgId="museum";collectionId="piece";sourceBizType=CollectionConst.BIZ_EXHIBITION;sourceBizId="show";locationId="from";approved=true;validFrom=at.minusDays(1);validTo=at.plusDays(2)}
        }
        val id=if(phase in setOf("EMERGENCY","LOCATIONS_LEFT"))service.recordEmergency(emergency(case).copy(instructionFileIds=file("EMERGENCY"),departure=departure().copy(accessRecordFileIds=file("DEPARTURE"),signedFileIds="DEPARTURE",packingCheckFileIds="DEPARTURE"),receipt=receipt().copy(accessRecordFileIds=file("RECEIPT"),signedFileIds="RECEIPT")))
            else service.create(plan(case).copy(sourceLocationFileIds=file("PLAN"),destinationSafetyFileIds="PLAN",carrierCapabilityFileIds="PLAN",signedPlanFileIds="PLAN"))
        fun sign(who:String,minute:Long,stage:EnumIncidentWorkStage,label:String) {
            support.actor(who);service.confirm(id,IncidentWorkConfirmationVo(stage,service.detail(id)[if(stage==EnumIncidentWorkStage.PLAN)"planBasis" else "endBasis"] as String,at.plusMinutes(minute),"本人核对",file(label)))
        }
        if(phase=="CANCELED")service.cancel(id,IncidentWorkCancelVo(at.plusHours(1),"未实施取消",file("CANCELED")))
        else {
            if(phase !in setOf("EMERGENCY","LOCATIONS_LEFT")) {
                listOf("conservation","department-head","keeper","leader").forEachIndexed {i,who->sign(who,10L+i,EnumIncidentWorkStage.PLAN,"PLAN_$who")}
                support.actor("operator");service.depart(id,departure().copy(accessRecordFileIds=file("DEPARTURE"),signedFileIds="DEPARTURE",packingCheckFileIds="DEPARTURE"))
                service.observation(id,IncidentWorkObservationVo(at.plusMinutes(70),"conservation","实际稳定",false,false,"继续核查",file("OBSERVATION")))
                service.receive(id,receipt().copy(accessRecordFileIds=file("RECEIPT"),signedFileIds="RECEIPT"))
            }
            sign("conservation",130,EnumIncidentWorkStage.END,"END_conservation");sign("keeper",131,EnumIncidentWorkStage.END,"END_keeper");support.actor("operator");link(case,id)
        }
        val target=when(phase){"LOCATIONS_LEFT"->"DEPARTURE";"PLAN_CONFIRMATION"->"PLAN_conservation";"END_CONFIRMATION"->"END_keeper";else->phase}
        val archive=if(phase=="LOCATIONS_LEFT")CollectionPermanentArchiveDao.all().single {it.bizId==id && it.phase==phase} else CollectionPermanentArchiveFileDao.all().single {it.bizId==id && it.fileId==target}.archiveId.let(CollectionPermanentArchiveDao::get)
        val tables=arrayOf(IncidentRelocations,IncidentRelocationRecords,IncidentWorks,IncidentWorkRecords,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,Collections,CollectionOperationLocks,StorageMovements,CollectionLedgerLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        val repeat:()->Unit=if(phase=="CANCELED") {
            val cancel=JsonUtils.fromJson(IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq id}.single {it.kind==EnumIncidentRelocationKind.CANCELED}.content,IncidentWorkCancelVo::class.java)!!
            val action:()->Unit={service.cancel(id,cancel);Unit}
            action
        } else {
            val receipt=JsonUtils.fromJson(IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq id}.single {it.kind==EnumIncidentRelocationKind.RECEIPT}.content,IncidentRelocationReceiptSnapshot::class.java)!!.receipt
            val action:()->Unit={service.receive(id,receipt);Unit}
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
        "DEPARTURE,MISSING",
        "DEPARTURE,IDENTITY",
        "DEPARTURE,EXTRA",
        "DEPARTURE,BYTES",
        "DEPARTURE,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "RECEIPT,MISSING",
        "RECEIPT,IDENTITY",
        "RECEIPT,EXTRA",
        "RECEIPT,BYTES",
        "RECEIPT,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE",
        "EMERGENCY,MISSING",
        "EMERGENCY,IDENTITY",
        "EMERGENCY,EXTRA",
        "EMERGENCY,BYTES",
        "EMERGENCY,BASELINE",
        "LOCATIONS_LEFT,MISSING",
        "LOCATIONS_LEFT,IDENTITY",
        "LOCATIONS_LEFT,EXTRA",
        "LOCATIONS_LEFT,BYTES",
        "LOCATIONS_LEFT,BASELINE")
    fun `operation safety originals relocation rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertRelocationOriginals(phase,damage)
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
        "DEPARTURE,MISSING",
        "DEPARTURE,IDENTITY",
        "DEPARTURE,EXTRA",
        "DEPARTURE,BYTES",
        "DEPARTURE,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "RECEIPT,MISSING",
        "RECEIPT,IDENTITY",
        "RECEIPT,EXTRA",
        "RECEIPT,BYTES",
        "RECEIPT,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE",
        "EMERGENCY,MISSING",
        "EMERGENCY,IDENTITY",
        "EMERGENCY,EXTRA",
        "EMERGENCY,BYTES",
        "EMERGENCY,BASELINE",
        "LOCATIONS_LEFT,MISSING",
        "LOCATIONS_LEFT,IDENTITY",
        "LOCATIONS_LEFT,EXTRA",
        "LOCATIONS_LEFT,BYTES",
        "LOCATIONS_LEFT,BASELINE")
    fun `postgres operation safety originals relocation rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertRelocationOriginals(phase,damage)} finally {safetyDatabase=null}
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
        "DEPARTURE,MISSING",
        "DEPARTURE,IDENTITY",
        "DEPARTURE,EXTRA",
        "DEPARTURE,BYTES",
        "DEPARTURE,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "RECEIPT,MISSING",
        "RECEIPT,IDENTITY",
        "RECEIPT,EXTRA",
        "RECEIPT,BYTES",
        "RECEIPT,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE",
        "EMERGENCY,MISSING",
        "EMERGENCY,IDENTITY",
        "EMERGENCY,EXTRA",
        "EMERGENCY,BYTES",
        "EMERGENCY,BASELINE",
        "LOCATIONS_LEFT,MISSING",
        "LOCATIONS_LEFT,IDENTITY",
        "LOCATIONS_LEFT,EXTRA",
        "LOCATIONS_LEFT,BYTES",
        "LOCATIONS_LEFT,BASELINE")
    fun `operation safety direct entries originals relocation rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=assertRelocationOriginals(phase,damage,true)
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
        "DEPARTURE,MISSING",
        "DEPARTURE,IDENTITY",
        "DEPARTURE,EXTRA",
        "DEPARTURE,BYTES",
        "DEPARTURE,BASELINE",
        "OBSERVATION,MISSING",
        "OBSERVATION,IDENTITY",
        "OBSERVATION,EXTRA",
        "OBSERVATION,BYTES",
        "OBSERVATION,BASELINE",
        "RECEIPT,MISSING",
        "RECEIPT,IDENTITY",
        "RECEIPT,EXTRA",
        "RECEIPT,BYTES",
        "RECEIPT,BASELINE",
        "END_CONFIRMATION,MISSING",
        "END_CONFIRMATION,IDENTITY",
        "END_CONFIRMATION,EXTRA",
        "END_CONFIRMATION,BYTES",
        "END_CONFIRMATION,BASELINE",
        "CANCELED,MISSING",
        "CANCELED,IDENTITY",
        "CANCELED,EXTRA",
        "CANCELED,BYTES",
        "CANCELED,BASELINE",
        "EMERGENCY,MISSING",
        "EMERGENCY,IDENTITY",
        "EMERGENCY,EXTRA",
        "EMERGENCY,BYTES",
        "EMERGENCY,BASELINE",
        "LOCATIONS_LEFT,MISSING",
        "LOCATIONS_LEFT,IDENTITY",
        "LOCATIONS_LEFT,EXTRA",
        "LOCATIONS_LEFT,BYTES",
        "LOCATIONS_LEFT,BASELINE")
    fun `postgres operation safety direct entries originals relocation rejects changed manifest bytes and missing baseline`(phase:String,damage:String)=withSafetySelectionPostgres { database ->
        safetyDatabase=database
        try {assertRelocationOriginals(phase,damage,true)} finally {safetyDatabase=null}
    }

}
