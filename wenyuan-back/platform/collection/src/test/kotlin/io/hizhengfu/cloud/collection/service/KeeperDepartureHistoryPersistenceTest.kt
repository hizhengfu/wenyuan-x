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
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.sys.system.dao.UserDao
import io.hizhengfu.cloud.sys.system.dao.RoleUsers
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

class KeeperDepartureHistoryPersistenceTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val directory = CollectionActorDirectory(scope)
    private val users = CollectionResponsibleUserService(directory, scope)
    private val binder = mock(BusinessAttachmentBinder::class.java).also { b -> `when`(b.resolveSingle(anyString())).thenAnswer { it.arguments[0] as String } }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val incidents = CollectionIncidentService(scope, users, archives, CollectionIncidentSourceService())
    private val service = InventoryComplianceService(scope, users, archives, incidents)
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
    private fun check(id: String) = InventoryItemCheckVo(id, at, setOf("new"), "实际现场位置", true, true, true, true, "保存稳定", false, "ledger", "card", "photo", "check")
    private fun completion() = InventoryCompletionVo(at.plusDays(2), "keeper", "leader", "report")
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
    private fun signature()=CollectionCareSignatureVo("本人已核对范围、任职及清点移交原件","report")
    private fun confirm(id:String) { login("keeper");handovers.confirm("store",id,"keeper",signature());login("leader");handovers.confirm("store",id,"leader",signature()) }

    private fun historical(c: WarehouseStaffingHandoverItem) = plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(
        leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(5),responsibilityFileIds="scope",keeperStaffingChangeIds=setOf(c.assignmentArchiveId))
    private fun finish(e: InventoryBatchDao) {
        service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        service.complete(e.id.value,completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover"))
    }
    private fun reload() { TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear() }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["disabled","role-removed","moved-museum"])
    fun `verified historical keeper can complete handover without restoring current authority`(reason:String)=fixture { e ->
        val c=removal()
        when(reason) {
            "disabled" -> UserDao["old"].enabled=false
            "role-removed" -> RoleUsers.deleteWhere { userId eq "old" }
            else -> UserDao["old"].groupId="other"
        }
        assertThrows(BadRequestException::class.java){users.requireRole("old","museum",CollectionConst.ROLE_KEEPER)}
        assertThrows(BadRequestException::class.java){service.begin(e.id.value,historical(c).copy(keeperStaffingChangeIds=emptySet()))}
        service.begin(e.id.value,historical(c))
        assertEquals(c.assignmentArchiveId,e.baseline!!.keeperStaffingEvidence.single().assignmentArchiveId)
        finish(e);val id=handovers.register("store",request(c));confirm(id);reload()
        assertEquals("DEPARTURE_CONFIRMED",items().single().status)
        assertThrows(BadRequestException::class.java){service.get(e.id.value)}
        login("operator")
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertThrows(BadRequestException::class.java){users.requireRole("old","museum",CollectionConst.ROLE_KEEPER)}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["routine","director","sample","unknown","first","foreign","no-removal","wrong-person","wrong-successor","before-appointment","no-date","no-responsibility","warehouse-scope","no-special-scope","duplicate-warehouse"])
    fun `unsupported historic role or incomplete responsibility cannot start inventory`(case:String)=fixture { e ->
        val c=removal();UserDao["old"].enabled=false
        var p=historical(c)
        when(case) {
            "routine"->p=p.copy(purpose=EnumInventoryPurpose.ROUTINE)
            "director"->p=p.copy(purpose=EnumInventoryPurpose.DIRECTOR_DEPARTURE)
            "sample"->p=p.copy(method=EnumInventoryMethod.SAMPLE)
            "unknown"->p=p.copy(keeperStaffingChangeIds=setOf("missing"))
            "first"->p=p.copy(keeperStaffingChangeIds=setOf(CollectionPermanentArchiveDao.all().first().id.value))
            "foreign"->CollectionPermanentArchiveDao[c.assignmentArchiveId].orgId="other"
            "no-removal"->{
                val current=WarehouseDao["store"].staffing!!
                staffing.save("store",current.copy(replacementReason="补充任职原件"))
                p=p.copy(keeperStaffingChangeIds=setOf(CollectionPermanentArchiveDao.all().last().id.value))
            }
            "wrong-person"->p=p.copy(leavingUserId="keeper")
            "wrong-successor"->p=p.copy(successorUserId="keeper")
            "before-appointment"->p=p.copy(leavingAt=at.minusDays(4))
            "no-date"->p=p.copy(leavingAt=null)
            "no-responsibility"->p=p.copy(responsibilityFileIds=", ,")
            "warehouse-scope"->{e.warehouseId="store";p=p.copy(method=EnumInventoryMethod.SPECIAL)}
            "no-special-scope"->p=p.copy(method=EnumInventoryMethod.SPECIAL)
            "duplicate-warehouse"->{
                UserDao["old"].enabled=true
                val current=WarehouseDao["store"].staffing!!
                staffing.save("store",current.copy(keeperUserIds=setOf("old","new"),replacementReason="重新任职"))
                staffing.save("store",current.copy(replacementReason="再次离任"))
                p=p.copy(keeperStaffingChangeIds=setOf(c.assignmentArchiveId,items().last().assignmentArchiveId))
            }
        }
        val count=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java){service.begin(e.id.value,p)}
        assertNull(e.plan);assertNull(e.baseline);assertEquals(count,CollectionPermanentArchiveDao.count())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["successor","head","director","checker"])
    fun `historical outgoing proof never bypasses current responsible roles`(case:String)=fixture { e ->
        val c=removal();UserDao["old"].enabled=false
        if(case=="checker") {
            service.begin(e.id.value,historical(c));UserDao["new"].enabled=false
            assertThrows(BadRequestException::class.java){service.check(e.id.value,check("one"))}
            assertTrue(e.checks!!.items.isEmpty())
        } else {
            UserDao[when(case){"successor"->"new";"head"->"keeper";else->"leader"}].enabled=false
            assertThrows(BadRequestException::class.java){service.begin(e.id.value,historical(c))};assertNull(e.plan)
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["digest","rehash","missing-previous","missing-file","empty-file-list","baseline-cleared","duplicate-plan-reference"])
    fun `changed original history blocks later checks completion and annual coverage`(case:String)=fixture { e ->
        val c=removal();service.begin(e.id.value,historical(c));finish(e)
        val proof=e.baseline!!.keeperStaffingEvidence.single()
        val row=CollectionPermanentArchiveDao[proof.previousAssignmentArchiveId]
        when(case) {
            "digest"->row.digest="changed"
            "rehash"->{
                val v=JsonUtils.fromJson(row.snapshot,WarehouseStaffingVo::class.java)!!
                row.snapshot=JsonUtils.toJson(v.copy(replacementReason="重新计算过摘要的历史改写"))!!
                row.digest=CollectionCareEvidence.digest(row.snapshot)
            }
            "missing-previous"->row.delete()
            "missing-file"->CollectionPermanentArchiveFileDao.all().first{it.archiveId==row.id.value}.delete()
            "empty-file-list"->{
                val v=JsonUtils.fromJson(row.snapshot,WarehouseStaffingVo::class.java)!!
                row.snapshot=JsonUtils.toJson(v.copy(signedAssignmentFileIds=", ,"))!!;row.digest=CollectionCareEvidence.digest(row.snapshot)
            }
            "baseline-cleared"->e.baseline=e.baseline!!.copy(keeperStaffingEvidence=emptyList())
            "duplicate-plan-reference"->{
                val start=CollectionPermanentArchiveDao.all().single{it.bizId=="batch" && it.phase=="STARTED"}
                val content=JsonUtils.mapper.readTree(start.snapshot)
                (content.get("plan") as tools.jackson.databind.node.ObjectNode).set("keeperStaffingChangeIds",JsonUtils.mapper.readTree(JsonUtils.toJson(listOf(c.assignmentArchiveId,c.assignmentArchiveId))!!))
                start.snapshot=content.toString();start.digest=CollectionCareEvidence.digest(start.snapshot)
            }
        }
        reload()
        assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty())
        assertTrue("batch" in service.coverage(2026).unverifiedBatchIds)
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,completion())}
    }

    @Test fun `later valid roster changes do not invalidate the original pair and explicit scope includes offsite objects`()=fixture { e ->
        val c=removal();e.collectionIds="one,two"
        service.begin(e.id.value,historical(c).copy(method=EnumInventoryMethod.SPECIAL))
        assertEquals(setOf("one","two"),e.baseline!!.items.map{it.collectionId}.toSet())
        UserDao["old"].enabled=false
        val current=WarehouseDao["store"].staffing!!
        staffing.save("store",current.copy(replacementReason="后续任职签署原件补充"))
        finish(e);reload();assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        confirm(handovers.register("store",request(c)));assertEquals("DEPARTURE_CONFIRMED",items().single().status)
    }

    @Test fun `multiple warehouses retain their own evidence and cannot reuse inventory for an unrelated removal`()=fixture { e ->
        val c=removal()
        WarehouseDao.new("store2"){orgId="museum";name="二号库";sceneType=EnumLocationScene.STORAGE}
        val first=WarehouseStaffingVo(setOf("old"),"keeper","leader",at.minusDays(3),"appointment")
        staffing.save("store2",first);staffing.save("store2",first.copy(keeperUserIds=setOf("new"),assignedAt=at.plusDays(5),replacementReason="实际离任接任"))
        val other=(handovers.get("store2")["items"] as List<*>).filterIsInstance<WarehouseStaffingHandoverItem>().single()
        service.begin(e.id.value,historical(c));finish(e)
        assertThrows(BadRequestException::class.java){handovers.register("store2",request(other))}
        val next=InventoryBatchDao.new("both"){orgId="museum";batchName="完整责任范围清点";collectionIds="one,two"}
        UserDao["old"].enabled=false
        service.begin(next.id.value,historical(c).copy(method=EnumInventoryMethod.SPECIAL,keeperStaffingChangeIds=setOf(other.assignmentArchiveId,c.assignmentArchiveId)))
        // 刻意保留与当前集合相反的原件数组顺序，模拟不同序列化器/数据库重载。
        val start=CollectionPermanentArchiveDao.all().single{it.bizId=="both" && it.phase=="STARTED"}
        val original=JsonUtils.mapper.readTree(start.snapshot)
        (original.get("plan") as tools.jackson.databind.node.ObjectNode).set("keeperStaffingChangeIds",JsonUtils.mapper.readTree(JsonUtils.toJson(next.plan!!.keeperStaffingChangeIds.toList().reversed())!!))
        start.snapshot=original.toString();start.digest=CollectionCareEvidence.digest(start.snapshot)
        finish(next);reload();assertTrue(service.get("both").evidenceErrors.isEmpty())
        assertEquals(2,next.baseline!!.keeperStaffingEvidence.size)
        handovers.register("store",request(c).copy(inventoryBatchIds=setOf("both")))
        handovers.register("store2",request(other).copy(inventoryBatchIds=setOf("both")))
    }
    @Test fun `legacy inventory archives without historical staffing fields remain verifiable`()=fixture { e ->
        service.begin(e.id.value,plan())
        service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        service.complete(e.id.value,completion())
        val starts=CollectionPermanentArchiveDao.all().filter{it.bizType==InventoryComplianceService.BIZ}
        assertTrue(starts.none { "keeperStaffingChangeIds" in it.snapshot || "keeperStaffingEvidence" in it.snapshot })
        reload();assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertTrue(service.coverage(2026).unverifiedBatchIds.isEmpty())
    }

}
