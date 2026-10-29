package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import io.hizhengfu.cloud.flow.domain.FlowCanceledEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import io.hizhengfu.cloud.sys.system.dao.Groups
import io.hizhengfu.cloud.sys.system.dao.UserDao
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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

class RequisitionCompliancePersistenceTest {
    private val scope=CollectionScopeResolver("single","museum")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val statutory=RequisitionStatutoryService(scope,users,binder,archives)
    private val safetyGuards=mutableListOf<CollectionOperationSafetyGuard>(CollectionIncidentOperationGuard())
    private val locks=CollectionOperationLockService(safetyGuards)
    private val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
    private val userNames=mock(CollectionUserResolver::class.java)
    private val items=CollectionBusinessItemService()
    private val placement=mock(CollectionPlacementService::class.java)
    private val returns=CollectionReturnService(mock(FlowInstanceService::class.java),scope,collections,userNames,binder,items,locks,placement,ReturnStatutoryService(scope,mock(CollectionResponsibleUserService::class.java),binder,archives),archives)
    private val service=RequisitionService(mock(FlowInstanceService::class.java),scope,collections,userNames,binder,returns,items,locks,placement,statutory,archives)
    private val time=LocalDateTime.of(2026,8,10,9,0)
    @BeforeEach fun prepare() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login(identity("operator","dept"))
    }
    @AfterEach fun cleanup()=SecurityContextHolder.clearContext()
    private fun identity(id:String,group:String,roleCodes:Set<String> = emptySet())=SecurityUser(id=id,name=id,username=id,password="",groupId=group,groupIds=setOf(group),postIds=emptySet(),dataType=0,roles=emptySet(),roleCodes=roleCodes,authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
    private fun login(user:SecurityUser) { SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities) }
    private fun fixture(block:(RequisitionDao)->Unit) {
        val db=Database.connect("jdbc:sqlite:file:req-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionLedgerLogs,CollectionIncidentItems,Collections,CollectionOrgs,Requisitions,CollectionReturns,CollectionBusinessItems,CollectionOperationLocks,StorageMovements,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstanceLogs,Warehouses,Groups,Users)
            CollectionOrgDao.new("museum") { name="本馆";code="M";systemGroupId="group-root" }
            CollectionOrgDao.new("other") { name="其他馆";code="O";systemGroupId="other-root" }
            GroupDao.new("group-root") { name="本馆机构";type='1';parentId="0";parentIds="0," }
            GroupDao.new("dept") { name="研究部";type='2';parentId="group-root";parentIds="0,group-root," }
            GroupDao.new("other-root") { name="他馆机构";type='1';parentId="0";parentIds="0," }
            WarehouseDao.new("warehouse") { orgId="museum";name="一库";sceneType=EnumLocationScene.STORAGE;type=EnumWarehouseType.WAREHOUSE }
            listOf("plan","review","safety","sign","image","packing").forEach { key -> FileDao.new(key) { name="$key.pdf";path="/files/$key.pdf";hash=key;size=100;ext="pdf" } }
            listOf("one","two").forEach { key -> CollectionDao.new(key) { orgId="museum";totalRegNo=key;name="藏品$key";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="warehouse";catalogStatus=EnumCatalogStatus.APPROVED;source=EnumSource.OLD } }
            actorSchema();originalReader("operator",CollectionConst.STORAGE,"dept")
            originalReader("custodian",CollectionConst.STORAGE,"dept")
            actorRole("custodian",CollectionConst.ROLE_KEEPER)
            val vo=RequisitionVo(collectionIds="one,two",purpose=EnumMoveReason.RESEARCH,basis="课题研究",department="研究部",operator="operator",toWarehouseId="warehouse",dossier=RequisitionDossierVo("dept","department-head","keeper-head","director","researcher","在指定工作台无损观察","课题器形比较","馆内研究室","限位支撑、专人值守","plan",time.minusDays(1),time.plusDays(10),transportRequired=false,transportDecisionReason="本馆同室工作台原位提用，不发生需包装运输的移动"))
            val id=service.save(vo)
            block(RequisitionDao[id])
        }
    }
    private fun logs(req:RequisitionDao,round:String="1",nodes:List<String> = listOf("department","keeper","leader")) {
        req.flowInstanceId="flow-${req.id.value}";req.flowStatus=EnumFlowStatus.AUDIT
        locks.acquire("museum",CollectionConst.BIZ_REQUISITION,req.id.value,splitCollectionIds(req.collectionIds))
        nodes.forEachIndexed { i,node -> FlowInstanceLogDao.new { instanceId=req.flowInstanceId!!;taskId="$round-$node";nodeKey=node;action="APPROVE";operator=statutory.actors(req)[node];comment="本人核对方案后同意";createTime=time.plusMinutes(i.toLong()) } }
    }
    private fun complete(req:RequisitionDao)=service.onFlowCompleted(req,FlowCompletedEvent(req.flowInstanceId!!,CollectionConst.FLOW_REQUISITION,"提用",req.id.value,CollectionConst.BIZ_REQUISITION,emptyMap(),"operator","经办"))
    private fun approve(req:RequisitionDao) { logs(req);complete(req) }
    private fun handover()=RequisitionHandoverVo(time.plusHours(1),time.plusMinutes(10),"keeper-head","review",setOf("custodian"),setOf("researcher"),listOf(RequisitionConditionVo("one","釉面与原档案记录一致",false),RequisitionConditionVo("two","口沿细裂隙位置见局部图",true,"image")),true,true,"safety","sign")
    private fun canceled(req:RequisitionDao)=FlowCanceledEvent(req.flowInstanceId!!,CollectionConst.FLOW_REQUISITION,"提用",req.id.value,CollectionConst.BIZ_REQUISITION,"operator","经办","operator","经办")
    @Test fun `relocation and other internal use require concrete purpose and keep the full safety preparation`()=fixture { req ->
        val original=req.dossier!!
        for (reason in listOf(EnumMoveReason.RELOCATION,EnumMoveReason.OTHER)) {
            req.purpose=reason
            assertEquals(2,statutory.requirePrepared(req).size)
            req.dossier=original.copy(purposeDescription=" ")
            assertThrows(BadRequestException::class.java){statutory.requirePrepared(req)}
            req.dossier=original.copy(safetyRequirements="")
            assertThrows(BadRequestException::class.java){statutory.requirePrepared(req)}
            req.dossier=original
        }
        req.purpose=EnumMoveReason.LOAN
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(req)}
    }
    @Test fun `planned internal transport requires standard and signed packing evidence before outbound`()=fixture { req ->
        val original=req.dossier!!
        req.dossier=original.copy(transportRequired=null)
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(req) }
        req.dossier=original.copy(transportRequired=true)
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(req) }
        req.dossier=original.copy(transportRequired=true,transportDecisionReason="跨楼转运",transportStandardReference="GB/T 23862-2024",transportStandardApplicability="本件文物适用范围经专业人员核对",transportCarrierName="本馆搬运组",transportCarrierCapabilityFileIds="review",transportPlanFileIds="plan")
        approve(req)
        val signed=handover()
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,signed) }
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,signed.copy(appliedTransportStandardReference="错误版本",packingStandardConfirmed=true,packingCheckFileIds="packing")) }
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,signed.copy(appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=false,packingCheckFileIds="packing")) }
        val verified=signed.copy(appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="packing")
        statutory.recordHandover(req.id.value,verified)
        login(identity("custodian","dept",setOf(CollectionConst.ROLE_KEEPER)))
        service.executeOut(req.id.value,CollectionOutboundExecutionVo())
        assertEquals(2,StorageMovementDao.all().count())
        assertTrue(StorageMovementDao.all().all { "packing" in it.attachmentIds.orEmpty() })
        assertTrue(archives.list(CollectionConst.BIZ_REQUISITION,req.id.value).any { it["phase"]=="OUT_EXECUTED" && it["files"].toString().contains("packing") })
    }
    @Test fun `incomplete use safety preparation allows draft correction but still rejects approval acquisition`()=fixture { req ->
        safetyGuards += object : CollectionOperationSafetyGuard {
            override fun requireAllowed(collectionId:String,bizType:String?,bizId:String?) {
                throw BadRequestException("共同检查方案尚未确认")
            }
        }
        val revised=req.toModel().copy(dossier=req.dossier!!.copy(useResponsibleUserId="replacement-user"))
        assertEquals(req.id.value,service.save(revised))
        assertEquals("replacement-user",RequisitionDao[req.id.value].dossier!!.useResponsibleUserId)
        assertThrows(BadRequestException::class.java) { locks.acquire("museum",CollectionConst.BIZ_REQUISITION,req.id.value,listOf("one","two")) }
        assertEquals(0L,CollectionOperationLockDao.count())
    }
    @Test fun `draft correction still refuses another business reservation`()=fixture { req ->
        CollectionOperationLockDao.new {orgId="museum";collectionId="one";bizType="OTHER";bizId="other-case"}
        val original=req.toModel()
        assertThrows(BadRequestException::class.java) {service.save(original.copy(basis="must not change"))}
        assertEquals(original.basis,RequisitionDao[req.id.value].basis)
    }
    @Test fun `draft rejects missing objects and mixed museums before binding a collection list`()=fixture { req ->
        assertThrows(BadRequestException::class.java) { service.save(req.toModel().copy(id=null,collectionIds="one,missing")) }
        CollectionDao["two"].orgId="other"
        assertThrows(BadRequestException::class.java) { service.save(req.toModel().copy(id=null)) }
        assertEquals(1,RequisitionDao.all().count())
    }
    @Test fun `three distinct actual approvals and a complete use plan are required`()=fixture { req ->
        val original=req.dossier!!
        req.dossier=original.copy(usageMethod="")
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(req) }
        req.dossier=original.copy(expectedReturnAt=original.expectedOutAt)
        assertThrows(BadRequestException::class.java) { statutory.requirePrepared(req) }
        req.dossier=original;logs(req,nodes=listOf("keeper","leader"))
        assertThrows(BadRequestException::class.java) { complete(req) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
    }
    @Test fun `approval reserves objects and old signed summary alone cannot execute outbound`()=fixture { req ->
        approve(req)
        assertEquals(EnumCollectionStatus.PRE_OUT,CollectionDao["one"].lifeStatus)
        assertThrows(BadRequestException::class.java) { locks.requireUnlocked("one") }
        assertThrows(BadRequestException::class.java) { service.executeOut(req.id.value,CollectionOutboundExecutionVo("整单说明","image","sign")) }
        assertEquals(0,StorageMovementDao.all().count())
        assertThrows(BadRequestException::class.java) { service.save(req.toModel()) }
    }
    @Test fun `handover requires every object actual opposite parties and sufficient condition evidence`()=fixture { req ->
        approve(req)
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,handover().copy(items=handover().items.take(1))) }
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,handover().copy(receivingUserIds=setOf("custodian"))) }
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,handover().copy(siteSafetyConfirmed=false)) }
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,handover().copy(items=handover().items.map { it.copy(imageFileIds=null) })) }
        statutory.recordHandover(req.id.value,handover())
        assertNotNull(req.handover)
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("sign") }
    }
    @Test fun `returned requisition cannot accept another handover even with valid original approvals`()=fixture { req ->
        approve(req);req.returned=true
        val before=CollectionPermanentArchiveDao.all().count()
        assertThrows(BadRequestException::class.java) {statutory.recordHandover(req.id.value,handover())}
        assertNull(req.handover);assertEquals(before,CollectionPermanentArchiveDao.all().count())
    }
    @Test fun `returned legacy marker cannot execute a new outbound from otherwise valid handover`()=fixture { req ->
        approve(req);statutory.recordHandover(req.id.value,handover());req.returned=true
        val before=CollectionPermanentArchiveDao.all().count()
        assertThrows(BadRequestException::class.java) {service.executeOut(req.id.value,CollectionOutboundExecutionVo())}
        assertFalse(req.outExecuted);assertEquals(0,StorageMovementDao.all().count())
        assertEquals(before,CollectionPermanentArchiveDao.all().count())
        assertEquals(EnumCollectionStatus.PRE_OUT,CollectionDao["one"].lifeStatus)
    }
    @Test fun `signed per object handover executes once and creates a real pending return draft`()=fixture { req ->
        approve(req);statutory.recordHandover(req.id.value,handover())
        login(identity("custodian","dept",setOf(CollectionConst.ROLE_KEEPER)))
        service.executeOut(req.id.value,CollectionOutboundExecutionVo());service.executeOut(req.id.value,CollectionOutboundExecutionVo())
        req.returned=true;req.returnDate=time.toLocalDate()
        service.executeOut(req.id.value,CollectionOutboundExecutionVo())
        assertEquals(2,StorageMovementDao.all().count())
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["one"].lifeStatus)
        assertTrue(StorageMovementDao.all().map { it.reviewOpinion }.toSet().containsAll(handover().items.map { it.condition }))
        assertEquals(handover().handedAt,req.outExecutedTime)
        assertEquals(1,CollectionReturnDao.all().count())
        assertEquals(EnumReturnExecutionState.PENDING,CollectionReturnDao.all().single().executionState)
        assertEquals(req.id.value,CollectionReturnDao.all().single().sourceBizId)
        assertBusinessReentryDenied(service,req,"department")
        assertEquals(1,archives.list(CollectionConst.BIZ_REQUISITION,req.id.value).count { it["phase"] == "OUT_EXECUTED" })
        assertThrows(BadRequestException::class.java) { service.onFlowCanceled(req,canceled(req)) }
    }
    @Test fun `repeated outbound requires its sealed original and unchanged verification`()=fixture { req ->
        approve(req);statutory.recordHandover(req.id.value,handover())
        login(identity("custodian","dept",setOf(CollectionConst.ROLE_KEEPER)))
        service.executeOut(req.id.value,CollectionOutboundExecutionVo())
        val original=CollectionPermanentArchiveDao.all().single { it.bizId==req.id.value && it.phase=="OUT_EXECUTED" }
        assertThrows(BadRequestException::class.java) {
            service.executeOut(req.id.value,CollectionOutboundExecutionVo("different verification"))
        }
        val snapshot=original.snapshot
        original.snapshot="{}"
        assertThrows(BadRequestException::class.java) { service.executeOut(req.id.value,CollectionOutboundExecutionVo()) }
        original.snapshot=snapshot
        original.delete()
        assertThrows(BadRequestException::class.java) { service.executeOut(req.id.value,CollectionOutboundExecutionVo()) }
        assertEquals(2,StorageMovementDao.all().count())
    }
    @Test fun `cancellation restores unexecuted reservations and reapproval cannot reuse old handover`()=fixture { req ->
        approve(req);statutory.recordHandover(req.id.value,handover())
        service.onFlowCanceled(req,canceled(req))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertDoesNotThrow { locks.requireUnlocked("one") }
        val event=FlowReactivatedEvent(req.flowInstanceId!!,CollectionConst.FLOW_REQUISITION,"提用",req.id.value,CollectionConst.BIZ_REQUISITION,"CANCELED","department","部门确认","重新办理","operator","经办","operator","经办",emptyMap())
        service.onFlowReactivated(req,event)
        FlowInstanceLogDao.new { instanceId=req.flowInstanceId!!;action="REACTIVATE";nodeKey="department";operator="operator";createTime=time.plusHours(2) }
        logs(req,"2")
        assertThrows(BadRequestException::class.java) { complete(req) }
    }
    @Test fun `department identity uses nearest mapped museum and administrator is not a department head`()=fixture { _ ->
        actorSchema()
        val multi=CollectionScopeResolver("multi","")
        val checker=CollectionResponsibleUserService(CollectionActorDirectory(multi),multi)
        val person=UserDao.new("person") { loginName="person";name="负责人";password="unused";enabled=true;groupId="dept" }
        actorRole("person",CollectionConst.ROLE_DEPARTMENT_HEAD)
        assertEquals(setOf("museum"),multi.writableOrgIds(identity("person","dept")))
        login(identity("person","dept"))
        assertEquals("museum",multi.currentOrgId())
        assertDoesNotThrow { checker.requireDepartmentHead("person","museum","dept") }
        assertThrows(BadRequestException::class.java) { checker.requireDepartmentHead("person","other","other-root") }
        io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { userId eq "person" }
        assertThrows(BadRequestException::class.java) { checker.requireDepartmentHead("person","museum","dept") }
        CollectionOrgDao.new("child") { name="分馆";code="C";parentId="museum";systemGroupId="child-group" }
        GroupDao.new("child-group") { name="分馆机构";type='1';parentId="group-root";parentIds="0,group-root," }
        GroupDao.new("child-dept") { name="分馆部门";type='2';parentId="child-group";parentIds="0,group-root,child-group," }
        assertEquals(setOf("child"),multi.writableOrgIds(identity("person","child-dept")))
        login(identity("person","child-dept"))
        assertEquals("child",multi.currentOrgId())
        GroupDao["child-dept"].enabled=false
        assertEquals(setOf("__none__"),multi.writableOrgIds(identity("person","child-dept")))
        assertThrows(BadRequestException::class.java) { multi.currentOrgId() }
    }
    @Test fun `handover correction retains previous signed version and repeated identical submission is idempotent`()=fixture { req ->
        approve(req);val original=handover()
        statutory.recordHandover(req.id.value,original);statutory.recordHandover(req.id.value,original)
        assertEquals(1,archives.list(CollectionConst.BIZ_REQUISITION,req.id.value).count { it["phase"].toString().startsWith("HANDOVER_") })
        val corrected=original.copy(items=original.items.map { if(it.collectionId=="one")it.copy(condition="补充底部原有细痕")else it })
        assertThrows(BadRequestException::class.java) { statutory.recordHandover(req.id.value,corrected) }
        statutory.recordHandover(req.id.value,corrected.copy(replacementReason="原点交单附页补充底部描述"))
        val records=archives.list(CollectionConst.BIZ_REQUISITION,req.id.value).filter { it["phase"].toString().startsWith("HANDOVER_") }
        assertEquals(2,records.size)
        assertTrue(records.any { it["snapshot"].toString().contains("釉面与原档案记录一致") })
        assertTrue(records.any { it["snapshot"].toString().contains("补充底部原有细痕") })
    }
    @Test fun `JSON persisted signed evidence preserves explicit false and exact time and print includes per object evidence`()=fixture { req ->
        approve(req);statutory.recordHandover(req.id.value,handover())
        val restored=JsonUtils.fromJson(JsonUtils.toJson(req.handover)!!,RequisitionHandoverRecordVo::class.java)!!
        assertFalse(restored.content.items.first().textNeedsImages)
        assertEquals(handover().handedAt,restored.content.handedAt)
        val fields=CollectionPrintFieldBuilder.requisitionFields(req.toModel()).associateBy { it.label }
        assertEquals("department-head",fields["提用部门负责人"]?.value)
        assertEquals("2026-08-10 10:00:00",fields["实际点交时间"]?.value)
        assertEquals("sign",fields["双方签署逐件点交单"]?.value)
        assertTrue(fields.values.any { it.value==handover().items.first().condition })
    }
    @Test fun `a new valid approval round cannot reuse an earlier signed handover`()=fixture { req ->
        approve(req);statutory.recordHandover(req.id.value,handover());service.onFlowCanceled(req,canceled(req))
        service.onFlowReactivated(req,FlowReactivatedEvent(req.flowInstanceId!!,CollectionConst.FLOW_REQUISITION,"提用",req.id.value,CollectionConst.BIZ_REQUISITION,"CANCELED","department","部门确认","重新办理","operator","经办","operator","经办",emptyMap()))
        FlowInstanceLogDao.new { instanceId=req.flowInstanceId!!;action="REACTIVATE";operator="operator";createTime=time.plusHours(2) }
        statutory.actors(req).entries.forEachIndexed { i,(node,user) -> FlowInstanceLogDao.new { instanceId=req.flowInstanceId!!;taskId="new-$node";nodeKey=node;action="APPROVE";operator=user;comment="本轮重新核对";createTime=time.plusHours(2).plusMinutes(i+1L) } }
        complete(req)
        login(identity("custodian","dept",setOf(CollectionConst.ROLE_KEEPER)))
        val stale=assertThrows(BadRequestException::class.java) { service.executeOut(req.id.value,CollectionOutboundExecutionVo()) }
        assertEquals("点交记录不对应本轮批准，须重新核对并签署",stale.message)
        assertEquals(0,StorageMovementDao.all().count())
        statutory.recordHandover(req.id.value,handover().copy(reviewedAt=time.plusHours(3),handedAt=time.plusHours(4),replacementReason="撤销后重新审批并再次点交"))
        service.executeOut(req.id.value,CollectionOutboundExecutionVo())
        assertEquals(2,StorageMovementDao.all().count())
    }

    @Test fun `incident discovered after approval does not prevent safe cancellation of an unexecuted requisition`()=fixture { req ->
        approve(req)
        CollectionIncidentItemDao.new {orgId="museum";collectionId="one";incidentId="incident";restricted=true}
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("one",CollectionConst.BIZ_REQUISITION,req.id.value)}
        service.onFlowCanceled(req,canceled(req))
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertEquals(0,CollectionOperationLockDao.all().toList().size)
        assertTrue(CollectionIncidentItemDao.all().single().restricted)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("one")}
    }

}
