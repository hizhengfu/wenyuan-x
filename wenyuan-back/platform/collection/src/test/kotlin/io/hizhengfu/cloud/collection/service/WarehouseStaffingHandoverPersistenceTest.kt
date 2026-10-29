package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteAll
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

class WarehouseStaffingHandoverPersistenceTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val users = mock(CollectionResponsibleUserService::class.java)
    private val binder = mock(BusinessAttachmentBinder::class.java).also { b -> `when`(b.resolveSingle(anyString())).thenAnswer { it.arguments[0] as String } }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val incidents = CollectionIncidentService(scope, users, archives, CollectionIncidentSourceService())
    private val service = InventoryComplianceService(scope, users, archives, incidents)
    private val batches = InventoryBatchService(scope, binder, archives)
    private val at = LocalDateTime.of(2026, 8, 1, 9, 0)
    @BeforeEach fun prepare() {
        val context = StaticApplicationContext(); context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh(); SpringContextHolder().setApplicationContext(context)
        val user = SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    @AfterEach fun clean() = SecurityContextHolder.clearContext()
    private fun fixture(block: (InventoryBatchDao) -> Unit) {
        val db = Database.connect("jdbc:sqlite:file:inventory-${System.nanoTime()}?mode=memory&cache=shared", driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOrgs, Collections, CollectionLedgerLogs, Warehouses, Loans, Requisitions, CollectionReturns, InventoryBatches, Inventories, InventoryAnnualReports, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files)
            CollectionOrgDao.new("museum") { name="本馆"; code="M"; ownership=EnumOwnership.STATE }
            CollectionOrgDao.new("other") { name="外馆"; code="O"; ownership=EnumOwnership.STATE }
            WarehouseDao.new("store") { orgId="museum"; name="一号库"; sceneType=EnumLocationScene.STORAGE }
            WarehouseDao.new("hall") { orgId="museum"; name="一号展厅"; sceneType=EnumLocationScene.EXHIBITION }
            listOf("scope","ledger","card","photo","check","report","handover","appointment","supervision","action","receipt").forEach { key -> FileDao.new(key) { name="$key.pdf"; path="/files/$key.pdf"; hash=key; size=100; ext="pdf" } }
            listOf("one", "two").forEachIndexed { i, key -> CollectionDao.new(key) { orgId="museum"; totalRegNo=key; name="藏品$key"; nature=EnumCollectionNature.RELIC; categoryId="category-$i"; level=if(i==0) EnumRelicLevel.FIRST else EnumRelicLevel.GENERAL; lifeStatus=EnumCollectionStatus.IN_STORE; warehouseId=if(i==0) "store" else "hall"; registeredAt=at.minusMonths(2) } }
            CollectionDao.new("foreign") { orgId="other"; totalRegNo="foreign"; name="外馆藏品"; lifeStatus=EnumCollectionStatus.IN_STORE }
            val e = InventoryBatchDao.new("batch") { orgId="museum"; batchName="年度抽检" }
            SchemaUtils.create(io.hizhengfu.cloud.sys.system.dao.Groups, io.hizhengfu.cloud.sys.system.dao.Users, io.hizhengfu.cloud.sys.system.dao.Roles, io.hizhengfu.cloud.sys.system.dao.RoleUsers, io.hizhengfu.cloud.sys.system.dao.Permissions, io.hizhengfu.cloud.sys.system.dao.RolePermissions, io.hizhengfu.cloud.sys.system.dao.UserGroups)
            io.hizhengfu.cloud.sys.system.dao.GroupDao.new("museum") { name="本馆机构"; type='1'; parentId="0"; parentIds="0," }
            listOf("operator", "keeper", "leader", "new", "old").forEach { id ->
                io.hizhengfu.cloud.sys.system.dao.UserDao.new(id) { loginName=id; name=id; password="unused"; enabled=true; groupId="museum" }
            }
            role("operator", "MANAGER", CollectionConst.WAREHOUSE_MANAGE)
            role("operator", "INVENTORY_READER", CollectionConst.SELECT)
            role("operator", "INVENTORY_WRITER", CollectionConst.STORAGE)
            role("keeper", CollectionConst.ROLE_STORAGE_HEAD)
            role("leader", CollectionConst.ROLE_DIRECTOR)
            role("old", CollectionConst.ROLE_KEEPER); role("new", CollectionConst.ROLE_KEEPER)
            login("operator")
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            block(e)
        }
    }
    private fun plan(purpose: EnumInventoryPurpose = EnumInventoryPurpose.ROUTINE) = InventoryPlanVo(2026, EnumInventoryMethod.FULL, purpose, "全馆账物卡照清点", "keeper", "leader", "scope")
    private fun check(id: String) = InventoryItemCheckVo(id, at, setOf("custodian"), "实际现场位置", true, true, true, true, "保存稳定", false, "ledger", "card", "photo", "check")
    private fun completion() = InventoryCompletionVo(at.plusDays(2), "keeper", "leader", "report")
    private val directory=CollectionActorDirectory(scope)
    private val staffing=WarehouseStaffingService(scope,users,archives)
    private val handovers=WarehouseStaffingHandoverService(scope,directory,CollectionResponsibleUserService(directory,scope),archives)
    private fun role(user:String, code:String, permission:String?=null) {
        val r=io.hizhengfu.cloud.sys.system.dao.RoleDao.all().firstOrNull { it.code == code } ?: io.hizhengfu.cloud.sys.system.dao.RoleDao.new { name=code; this.code=code; enabled=true }
        io.hizhengfu.cloud.sys.system.dao.RoleUsers.insert { it[roleId]=r.id.value; it[userId]=user }
        if(permission!=null) {
            val p=io.hizhengfu.cloud.sys.system.dao.PermissionDao.new { name=permission; this.code=permission }
            io.hizhengfu.cloud.sys.system.dao.RolePermissions.insert { it[roleId]=r.id; it[permissionId]=p.id }
        }
    }
    private fun login(id:String) {
        val user=directory.requireActive(id)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun removal(): WarehouseStaffingHandoverItem {
        val first=WarehouseStaffingVo(setOf("old"),"keeper","leader",at.minusDays(3),"appointment")
        staffing.save("store",first)
        staffing.save("store",first.copy(keeperUserIds=setOf("new"),assignedAt=at.plusDays(5),replacementReason="实际离任和接任"))
        return items().single()
    }
    private fun items()=(handovers.get("store")["items"] as List<*>).filterIsInstance<WarehouseStaffingHandoverItem>()
    private fun request(c:WarehouseStaffingHandoverItem, kind:EnumStaffingHandoverKind=EnumStaffingHandoverKind.DUTY_DEPARTURE)=WarehouseStaffingHandoverRequest(c.assignmentArchiveId,c.leavingUserId,kind,if(kind==EnumStaffingHandoverKind.DUTY_DEPARTURE)setOf("batch") else emptySet(),"核对全部保管责任、全部移交范围及接任情况","scope",true,"keeper","leader")
    private fun departed(e:InventoryBatchDao,late:Boolean=false) {
        val p=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(if(late)1 else 5),responsibilityFileIds="scope")
        service.begin(e.id.value,p);service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        service.complete(e.id.value,completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover",departureDelayReason=if(late)"迟办原因" else null,departureDelayFileIds=if(late)"action" else null))
    }
    private fun signature()=CollectionCareSignatureVo("本人已核对范围、任职及清点移交原件","report")
    private fun confirm(id:String) { login("keeper");handovers.confirm("store",id,"keeper",signature());login("leader");handovers.confirm("store",id,"leader",signature()) }

    @Test fun `historical keeper departure rejects altered assignment original identity`()=fixture {
        val change=removal()
        val departure=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(
            leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(5),
            keeperStaffingChangeIds=setOf(change.assignmentArchiveId))
        val evidence=WarehouseStaffingArchiveEvidence(archives)
        assertEquals(1,evidence.forPlan("museum",departure).size)
        val file=CollectionPermanentArchiveFileDao.find {
            CollectionPermanentArchiveFiles.archiveId eq change.assignmentArchiveId
        }.single()
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path","/files/changed-assignment.pdf")
        file.metadata=metadata.toString()
        assertThrows(BadRequestException::class.java) { evidence.forPlan("museum",departure) }
    }

    @Test fun `keeper cannot confirm a handover whose registered original identity changed`()=fixture {
        val change=removal()
        val id=handovers.register("store",request(change,EnumStaffingHandoverKind.RECORD_CORRECTION))
        val record=CollectionPermanentArchiveDao.all().single { it.bizType==WarehouseStaffingHandoverService.BIZ && it.phase=="R_$id" }
        val file=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq record.id.value }.single()
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path","/files/changed-handover.pdf")
        file.metadata=metadata.toString()
        val count=CollectionPermanentArchiveDao.count()
        login("keeper")
        assertThrows(BadRequestException::class.java) { handovers.confirm("store",id,"keeper",signature()) }
        assertEquals(count,CollectionPermanentArchiveDao.count())
    }

    @Test fun `director cannot confirm a handover whose keeper signature original identity changed`()=fixture {
        val change=removal()
        val id=handovers.register("store",request(change,EnumStaffingHandoverKind.RECORD_CORRECTION))
        login("keeper")
        handovers.confirm("store",id,"keeper",signature())
        val signed=CollectionPermanentArchiveDao.all().single { it.bizType==WarehouseStaffingHandoverService.BIZ && it.phase=="K_$id" }
        val file=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq signed.id.value }.single()
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path","/files/changed-signature.pdf")
        file.metadata=metadata.toString()
        val count=CollectionPermanentArchiveDao.count()
        login("leader")
        assertThrows(BadRequestException::class.java) { handovers.confirm("store",id,"leader",signature()) }
        assertEquals(count,CollectionPermanentArchiveDao.count())
    }

    @Test fun `current revocation blocks staffing originals despite old session`()=fixture {
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){staffing.get("store")}
    }
    @Test fun `handover originals are limited to the selected warehouse archive and its referenced files`()=fixture {
        val c=removal();val id=handovers.register("store",request(c,EnumStaffingHandoverKind.RECORD_CORRECTION));confirm(id)
        login("operator")
        val rows=CollectionPermanentArchiveDao.all().filter { it.bizType==WarehouseStaffingHandoverService.BIZ && it.bizId=="store" }
        val record=rows.single { it.phase=="R_$id" }
        val keeper=rows.single { it.phase=="K_$id" }
        val leader=rows.single { it.phase=="L_$id" }
        handovers.requireArchivedOriginal("store",record.id.value,"scope")
        handovers.requireArchivedOriginal("store",keeper.id.value,"report")
        handovers.requireArchivedOriginal("store",leader.id.value,"report")
        assertThrows(BadRequestException::class.java){handovers.requireArchivedOriginal("store",record.id.value,"report")}
        assertThrows(BadRequestException::class.java){handovers.requireArchivedOriginal("hall",record.id.value,"scope")}
        assertThrows(BadRequestException::class.java){handovers.requireArchivedOriginal("store",c.assignmentArchiveId,"appointment")}
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){handovers.requireArchivedOriginal("store",record.id.value,"scope")}
    }
    @Test fun `assignment source originals stay limited to this departure before and after roster`()=fixture {
        val change=removal()
        val source=handovers.get("store")["assignmentArchives"] as List<*>
        assertEquals(setOf(change.previousAssignmentArchiveId,change.assignmentArchiveId),source.filterIsInstance<Map<*,*>>().map {it["id"]}.toSet())
        handovers.requireAssignmentSourceOriginal("store",change.previousAssignmentArchiveId,"appointment")
        handovers.requireAssignmentSourceOriginal("store",change.assignmentArchiveId,"appointment")
        val policy=CollectionBusinessArchiveReadPolicy(CollectionFileReadAuthorization(scope,directory),mock(CollectionDisclosureService::class.java))
        val reference=CollectionArchiveFileReference(change.assignmentArchiveId,"museum",WarehouseStaffingService.BIZ,"store","ASSIGNED_TEST","appointment","{}")
        login("keeper");handovers.requireAssignmentSourceOriginal("store",change.assignmentArchiveId,"appointment");policy.requireReadable(reference)
        val keeperRole=io.hizhengfu.cloud.sys.system.dao.RoleDao.all().single {it.code==CollectionConst.ROLE_STORAGE_HEAD}
        keeperRole.enabled=false
        assertThrows(BadRequestException::class.java){policy.requireReadable(reference)}
        keeperRole.enabled=true
        login("leader");handovers.requireAssignmentSourceOriginal("store",change.previousAssignmentArchiveId,"appointment");policy.requireReadable(reference)
        login("old");assertThrows(BadRequestException::class.java){policy.requireReadable(reference)}
        login("operator")
        assertThrows(BadRequestException::class.java){handovers.requireAssignmentSourceOriginal("store",change.assignmentArchiveId,"scope")}
        assertThrows(BadRequestException::class.java){handovers.requireAssignmentSourceOriginal("hall",change.assignmentArchiveId,"appointment")}
        val latest=WarehouseDao["store"].staffing!!
        staffing.save("store",latest.copy(assignedAt=at.plusDays(6),replacementReason="补正签署时间"))
        val unrelated=CollectionPermanentArchiveDao.all().filter {it.bizType==WarehouseStaffingService.BIZ && it.bizId=="store"}.maxWith(compareBy({it.createTime},{it.id.value}))
        assertThrows(BadRequestException::class.java){handovers.requireAssignmentSourceOriginal("store",unrelated.id.value,"appointment")}
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){handovers.requireAssignmentSourceOriginal("store",change.assignmentArchiveId,"appointment")}
    }
    @Test fun `single museum configuration blocks another museum handover originals`()=fixture {
        val single=CollectionScopeResolver("single","other")
        val service=WarehouseStaffingHandoverService(single,CollectionActorDirectory(single),users,archives)
        assertThrows(BadRequestException::class.java){service.get("store")}
    }

    @Test fun `actual roster removal remains pending until independent confirmations without retaining old authorization`()=fixture { e ->
        val c=removal();assertEquals("PENDING",c.status);assertEquals(setOf("new"),WarehouseDao["store"].staffing!!.keeperUserIds)
        departed(e);val req=request(c);val id=handovers.register("store",req)
        val count=CollectionPermanentArchiveDao.count();assertEquals(id,handovers.register("store",req));assertEquals(count,CollectionPermanentArchiveDao.count())
        assertEquals("PENDING_KEEPER",items().single().status)
        assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"keeper",signature())}
        login("leader");assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"leader",signature())}
        login("keeper");assertTrue(items().single().canConfirmKeeper);handovers.confirm("store",id,"keeper",signature());handovers.confirm("store",id,"keeper",signature())
        assertEquals("PENDING_LEADER",items().single().status)
        login("leader");assertTrue(items().single().canConfirmLeader);handovers.confirm("store",id,"leader",signature())
        assertEquals("DEPARTURE_CONFIRMED",items().single().status)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("scope")}
    }
    @Test fun `late physical handover remains late after both current officers confirm`()=fixture { e ->
        val c=removal();departed(e,true);val id=handovers.register("store",request(c));confirm(id)
        assertEquals("DEPARTURE_CONFIRMED_LATE",items().single().status);assertEquals(setOf("batch"),items().single().lateBatchIds)
    }
    @Test fun `evidenced roster correction is separate and later version does not inherit signatures`()=fixture {
        val c=removal();val req=request(c,EnumStaffingHandoverKind.RECORD_CORRECTION)
        val id=handovers.register("store",req);confirm(id)
        assertEquals("CORRECTION_CONFIRMED",items().single().status)
        login("operator");assertThrows(BadRequestException::class.java){handovers.register("store",req.copy(scopeStatement="补充依据"))}
        assertThrows(BadRequestException::class.java){handovers.register("store",req.copy(previousId=id,scopeStatement="补充依据"))}
        val next=handovers.register("store",req.copy(previousId=id,scopeStatement="补充依据",replacementReason="原录入错误补证"))
        assertNotEquals(id,next);assertEquals("PENDING_KEEPER",items().single().status);assertNull(items().single().keeper)
        login("keeper");assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"keeper",signature())}
        assertEquals(2,(handovers.get("store")["records"] as List<*>).size)
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["routine","wrong-person","wrong-successor","incomplete","other-museum","changed-check","missing-reference","scope-not-confirmed","missing-scope-file","unknown-assignment","correction-with-batch"])
    fun `invalid departure or unsupported scope cannot create an immutable handover record`(invalid:String)=fixture { e ->
        val c=removal();departed(e)
        var req=request(c)
        when(invalid) {
            "routine"->e.plan=e.plan!!.copy(purpose=EnumInventoryPurpose.ROUTINE)
            "wrong-person"->e.plan=e.plan!!.copy(leavingUserId="someone")
            "wrong-successor"->e.plan=e.plan!!.copy(successorUserId="someone")
            "incomplete"->e.completion=null
            "other-museum"->e.orgId="other"
            "changed-check"->e.checks=InventoryChecksVo()
            "missing-reference"->CollectionPermanentArchiveFileDao.all().first{it.bizId==e.id.value}.delete()
            "scope-not-confirmed"->req=req.copy(fullResponsibilityScopeConfirmed=false)
            "missing-scope-file"->req=req.copy(scopeFileIds="")
            "unknown-assignment"->req=req.copy(assignmentArchiveId="unknown")
            "correction-with-batch"->req=req.copy(kind=EnumStaffingHandoverKind.RECORD_CORRECTION)
        }
        val count=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java){handovers.register("store",req)}
        assertEquals(count,CollectionPermanentArchiveDao.count())
    }
    @Test fun `confirmed record fails closed when original evidence changes and recovers after genuine original restored`()=fixture { e ->
        val c=removal();departed(e);confirm(handovers.register("store",request(c)))
        val original=e.checks;e.checks=InventoryChecksVo()
        assertTrue((handovers.get("store")["evidenceErrors"] as List<*>).isNotEmpty());assertTrue(items().isEmpty())
        e.checks=original;assertEquals("DEPARTURE_CONFIRMED",items().single().status)
    }
    @Test fun `stale login cannot sign after role removal or account disable and foreign museum cannot read`()=fixture {
        val c=removal();val id=handovers.register("store",request(c,EnumStaffingHandoverKind.RECORD_CORRECTION))
        login("keeper")
        val role=io.hizhengfu.cloud.sys.system.dao.RoleDao.all().single{it.code==CollectionConst.ROLE_STORAGE_HEAD}
        role.enabled=false
        assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"keeper",signature())}
        role.enabled=true
        io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].enabled=false
        assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"keeper",signature())}
        login("operator");io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        assertThrows(BadRequestException::class.java){handovers.get("store")}
    }
    @Test fun `later actual officer appointment requires a new version instead of substitute confirmation`()=fixture {
        val c=removal();val id=handovers.register("store",request(c,EnumStaffingHandoverKind.RECORD_CORRECTION))
        val current=WarehouseDao["store"].staffing!!
        staffing.save("store",current.copy(keeperUserId="new-head",replacementReason="实际负责人变更"))
        login("keeper");assertFalse(items().single().canConfirmKeeper)
        assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"keeper",signature())}
        assertEquals("PENDING_KEEPER",items().single().status)
    }
    @Test fun `current roster inconsistency and malformed archive are shown as evidence gaps`()=fixture {
        removal();val original=WarehouseDao["store"].staffing
        WarehouseDao["store"].staffing=null
        assertTrue((handovers.get("store")["evidenceErrors"] as List<*>).isNotEmpty())
        WarehouseDao["store"].staffing=original
        val row=CollectionPermanentArchiveDao.all().first();row.snapshot="invalid";row.digest=CollectionCareEvidence.digest(row.snapshot)
        assertTrue((handovers.get("store")["evidenceErrors"] as List<*>).isNotEmpty())
    }
    @Test fun `the same completed batch cannot discharge another departure after reappointment`()=fixture { e ->
        val c=removal();departed(e);handovers.register("store",request(c))
        val current=WarehouseDao["store"].staffing!!
        staffing.save("store",current.copy(keeperUserIds=setOf("old","new"),replacementReason="原人员重新任职"))
        staffing.save("store",current.copy(replacementReason="原人员再次离任"))
        val next=items().last{it.leavingUserId=="old"}
        assertNotEquals(c.assignmentArchiveId,next.assignmentArchiveId)
        assertThrows(BadRequestException::class.java){handovers.register("store",request(next))}
        assertEquals("PENDING",items().last{it.leavingUserId=="old"}.status)
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["routine", "wrong-person", "wrong-successor"])
    fun `genuine but unrelated completed inventories cannot satisfy this departure`(case:String)=fixture { e ->
        val c=removal()
        val p=if(case=="routine") plan() else plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(leavingUserId=if(case=="wrong-person")"another-person" else "old",successorUserId=if(case=="wrong-successor")"another-successor" else "new",leavingAt=at.plusDays(5),responsibilityFileIds="scope")
        service.begin(e.id.value,p);service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        service.complete(e.id.value,completion().copy(actualHandoverAt=if(case=="routine")null else at.plusDays(1),handoverFileIds=if(case=="routine")null else "handover"))
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertThrows(BadRequestException::class.java){handovers.register("store",request(c))}
    }

    @Test fun `confirmed correction survives persisted reload and disabled museum cannot accept another version`()=fixture {
        val c=removal();val req=request(c,EnumStaffingHandoverKind.RECORD_CORRECTION)
        val id=handovers.register("store",req);confirm(id)
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertEquals("CORRECTION_CONFIRMED",items().single().status)
        login("operator");CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){handovers.register("store",req.copy(previousId=id,replacementReason="补充",scopeStatement="补充原件说明"))}
    }

    @Test fun `legacy scope deficiency preserves signed handover and permits correction with a complete replacement batch`()=fixture { e ->
        val c=removal();departed(e);val req=request(c);val id=handovers.register("store",req);confirm(id)
        // 构造旧版允许抽检离任时已经归档和本人确认的数据，不通过新入口创建此类方案。
        e.plan=e.plan!!.copy(method=EnumInventoryMethod.SAMPLE);e.baseline=e.baseline!!.copy(scopeSelection=null)
        CollectionPermanentArchiveDao.all().filter{it.bizType==InventoryComplianceService.BIZ && it.bizId==e.id.value && it.phase in setOf("STARTED","COMPLETED")}.forEach { row ->
            val node=JsonUtils.mapper.readTree(row.snapshot) as tools.jackson.databind.node.ObjectNode
            node.set("plan",JsonUtils.mapper.readTree(JsonUtils.toJson(e.plan)!!));node.set("baseline",JsonUtils.mapper.readTree(JsonUtils.toJson(e.baseline)!!))
            row.snapshot=node.toString();row.digest=CollectionCareEvidence.digest(row.snapshot)
        }
        val completed=CollectionPermanentArchiveDao.all().single{it.bizType==InventoryComplianceService.BIZ && it.bizId==e.id.value && it.phase=="COMPLETED"}
        val original=CollectionPermanentArchiveDao.all().single{it.bizType==WarehouseStaffingHandoverService.BIZ && it.phase=="R_$id"}
        val originalRecord=JsonUtils.fromJson(original.snapshot,WarehouseStaffingHandoverRecord::class.java)!!
        original.snapshot=JsonUtils.toJson(originalRecord.copy(inventoryArchiveDigests=mapOf(completed.id.value to completed.digest)))!!;original.digest=CollectionCareEvidence.digest(original.snapshot)
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        login("keeper")
        val old=items().single();assertEquals("SCOPE_REVIEW_REQUIRED",old.status)
        assertNotNull(old.keeper);assertNotNull(old.leader);assertTrue(old.departureScopeErrors.isNotEmpty());assertFalse(old.canConfirmKeeper);assertFalse(old.canConfirmLeader)
        assertTrue((handovers.get("store")["evidenceErrors"] as List<*>).isEmpty())
        assertThrows(BadRequestException::class.java){handovers.confirm("store",id,"keeper",signature())}
        login("operator");assertThrows(BadRequestException::class.java){handovers.register("store",req)}
        assertTrue((handovers.get("store")["inventoryBatches"] as List<*>).isEmpty())
        val replacement=InventoryBatchDao.new("replacement"){orgId="museum";batchName="完整责任复核清点"}
        departed(replacement)
        val next=handovers.register("store",req.copy(inventoryBatchIds=setOf("replacement"),previousId=id,replacementReason="原抽检不能抵完整责任移交，补齐完整清点"))
        assertNull(items().single().keeper);confirm(next)
        assertEquals("DEPARTURE_CONFIRMED",items().single().status)
        assertEquals(2,(handovers.get("store")["records"] as List<*>).size)
        assertNotNull(CollectionPermanentArchiveDao.findById(original.id.value))
        assertThrows(BadRequestException::class.java){service.get("batch")}
        login("operator")
        assertEquals(EnumInventoryDepartureStatus.SCOPE_REVIEW_REQUIRED,service.get("batch").departureStatus)
    }

}
