package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class WarehouseSecurityDutyPersistenceTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val directory = CollectionActorDirectory(scope)
    private val binder = mock(BusinessAttachmentBinder::class.java).also { b -> `when`(b.resolveSingle(anyString())).thenAnswer { it.arguments[0] as String } }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val service = WarehouseSecurityDutyService(scope, CollectionResponsibleUserService(directory, scope), archives)
    private val start = LocalDateTime.of(2026, 8, 1, 0, 0)
    private var sequence = 0
    private fun key() = "request-${++sequence}"
    @AfterEach fun cleanup() = SecurityContextHolder.clearContext()
    private fun fixture(block: () -> Unit) {
        val context = StaticApplicationContext()
        context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
        context.refresh(); SpringContextHolder().setApplicationContext(context)
        val db = Database.connect("jdbc:sqlite:file:security-duty-${System.nanoTime()}?mode=memory&cache=shared", driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionOrgs, Warehouses, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files,
                Groups, Users, Roles, RoleUsers, Permissions, RolePermissions, UserGroups)
            CollectionOrgDao.new("museum") { name="本馆"; code="M"; ownership=EnumOwnership.STATE; systemGroupId="museum" }
            CollectionOrgDao.new("other") { name="外馆"; code="O"; ownership=EnumOwnership.STATE; systemGroupId="other" }
            for (org in listOf("museum", "other")) GroupDao.new(org) { name=org; type='1'; parentId="0"; parentIds="0," }
            WarehouseDao.new("store") { orgId="museum"; name="库区" }
            WarehouseDao.new("foreign") { orgId="other"; name="外馆库区" }
            WarehouseDao.new("hall") { orgId="museum"; name="展厅"; sceneType=EnumLocationScene.EXHIBITION }
            for (id in listOf("manager", "one", "two", "outsider")) UserDao.new(id) {
                loginName=id; name=id; password="unused"; enabled=true; groupId=if(id=="outsider") "other" else "museum"
            }
            role("manager", "MANAGER", CollectionConst.WAREHOUSE_MANAGE)
            for (id in listOf("one", "two", "outsider")) role(id, CollectionConst.ROLE_SECURITY)
            FileDao.new("evidence") { name="值班签署原件.pdf"; path="/files/duty.pdf"; hash="fixture"; size=100; ext="pdf" }
            login("manager")
            block()
        }
    }
    private fun role(user: String, code: String, permission: String? = null) {
        val role = RoleDao.all().firstOrNull { it.code == code } ?: RoleDao.new { name=code; this.code=code; enabled=true }
        RoleUsers.insert { it[roleId]=role.id.value; it[userId]=user }
        if(permission != null) {
            val p = PermissionDao.new { name=permission; this.code=permission }
            RolePermissions.insert { it[roleId]=role.id; it[permissionId]=p.id }
        }
    }
    private fun login(id: String) {
        val actor = directory.requireActive(id)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
    }
    private fun schedule(officer: String="one", from: Int=0, to: Int=8): String {
        login("manager")
        return service.schedule("store", request(officer, from, to))
    }
    private fun request(officer: String="one", from: Int=0, to: Int=8) = WarehouseSecurityShiftRequest(key(), officer,
        SecurityDutyInterval(start.plusHours(from.toLong()), start.plusHours(to.toLong())), "一号库区安防值守", "evidence")
    private fun actual(from: Int=0, to: Int?=8) = WarehouseSecurityAttendanceRequest(key(), start.plusHours(from.toLong()),
        to?.let { start.plusHours(it.toLong()) }, "未发现异常", "钥匙与待办事项核对无误", "evidence")
    @Suppress("UNCHECKED_CAST")
    private fun shifts() = service.get("store", start.toLocalDate())["shifts"] as List<WarehouseSecurityShiftView>

    @Test fun `schedule is permanent and exact retry does not duplicate`() = fixture {
        val req = request()
        val id = service.schedule("store", req)
        assertEquals(id, service.schedule("store", req))
        assertEquals(1L, CollectionPermanentArchiveDao.count())
        assertThrows(BadRequestException::class.java) { service.schedule("store", req.copy(responsibility="changed")) }
        assertEquals("manager", shifts().single().shift.actorId)
    }
    @Test fun `only actual local security officer can record attendance`() = fixture {
        val id = schedule()
        assertThrows(BadRequestException::class.java) { service.attend("store", id, actual()) }
        login("two")
        assertThrows(BadRequestException::class.java) { service.attend("store", id, actual()) }
        login("one")
        val req = actual()
        val record = service.attend("store", id, req)
        assertEquals(record, service.attend("store", id, req))
        assertEquals(2L, CollectionPermanentArchiveDao.count())
        assertEquals("one", shifts().single().attendance.single().actorId)
    }
    @Test fun `revoked role is checked against current database despite prior login`() = fixture {
        val id = schedule(); login("one")
        RoleDao.all().single { it.code == CollectionConst.ROLE_SECURITY }.enabled=false
        assertThrows(BadRequestException::class.java) { service.attend("store", id, actual()) }
        assertThrows(BadRequestException::class.java) { service.get("store", start.toLocalDate()) }
        assertEquals(1L, CollectionPermanentArchiveDao.count())
    }
    @Test fun `schedule refuses wrong location cross museum and non security officer`() = fixture {
        assertThrows(BadRequestException::class.java) { service.schedule("hall", request()) }
        assertThrows(BadRequestException::class.java) { service.schedule("foreign", request()) }
        assertThrows(BadRequestException::class.java) { service.schedule("store", request("outsider")) }
        assertThrows(BadRequestException::class.java) { service.schedule("store", request("manager")) }
        assertEquals(0L, CollectionPermanentArchiveDao.count())
    }
    @Test fun `cancellation preserves original removes coverage and forbids later attendance`() = fixture {
        val id = schedule()
        val req = WarehouseSecurityCancellationRequest(key(), "改排其他班次", "evidence")
        val cancellation = service.cancel("store", id, req)
        assertEquals(cancellation, service.cancel("store", id, req))
        val coverage = service.get("store", start.toLocalDate())["coverage"] as WarehouseSecurityDutyCoverage
        assertEquals(0L, coverage.plannedCoveredSeconds)
        login("one")
        assertThrows(BadRequestException::class.java) { service.attend("store", id, actual()) }
        assertEquals(2L, CollectionPermanentArchiveDao.count())
    }
    @Test fun `attendance correction preserves original and stale edit cannot overwrite`() = fixture {
        val id = schedule(); login("one")
        val original = actual(to=null)
        val first = service.attend("store", id, original)
        val next = actual().copy(previousId=first, correctionReason="实际交班结束，补充结束时间")
        val second = service.attend("store", id, next)
        assertEquals(second, service.attend("store", id, next))
        assertThrows(BadRequestException::class.java) { service.attend("store", id, next.copy(requestId=key())) }
        assertThrows(BadRequestException::class.java) { service.attend("store", id, next.copy(requestId=key(), previousId=second, endedAt=null)) }
        assertEquals(listOf(original, next), shifts().single().attendance.map { it.value })
        login("manager")
        assertThrows(BadRequestException::class.java) { service.cancel("store", id, WarehouseSecurityCancellationRequest(key(), "不应隐藏实际历史", "evidence")) }
    }
    @Test fun `incoming officer verifies both current actual originals and cannot reuse stale handover`() = fixture {
        val outgoing = schedule("one", 0, 8)
        val incoming = schedule("two", 8, 24)
        login("one"); val first = service.attend("store", outgoing, actual())
        login("two"); val second = service.attend("store", incoming, actual(8,24))
        val req = WarehouseSecurityHandoverRequest(key(), outgoing, first, second, start.plusHours(8), "本人接班已核对交班事项", "evidence")
        login("manager")
        assertThrows(BadRequestException::class.java) { service.handover("store", incoming, req) }
        login("two")
        val receipt = service.handover("store", incoming, req)
        assertEquals(receipt, service.handover("store", incoming, req))
        assertEquals(listOf(receipt), service.get("store", start.toLocalDate())["currentHandoverIds"])
        val coverage = service.get("store", start.toLocalDate())["coverage"] as WarehouseSecurityDutyCoverage
        assertTrue(coverage.actualDayComplete)
        service.attend("store", incoming, actual(9,24).copy(previousId=second, correctionReason="核实原到岗时间录入有误"))
        assertThrows(BadRequestException::class.java) { service.handover("store", incoming, req.copy(requestId=key())) }
        assertFalse((service.get("store", start.toLocalDate())["coverage"] as WarehouseSecurityDutyCoverage).actualDayComplete)
        assertEquals(1, shifts().single { it.shift.id==incoming }.handovers.size)
        assertEquals(emptyList<String>(), service.get("store", start.toLocalDate())["currentHandoverIds"])
    }
    @Test fun `modified permanent snapshot fails reads instead of concealing gaps`() = fixture {
        schedule()
        CollectionPermanentArchiveDao.all().single().snapshot="{}"
        assertThrows(BadRequestException::class.java) { service.get("store", start.toLocalDate()) }
    }
    @Test fun `missing evidence references cannot yield trusted coverage`() = fixture {
        schedule()
        CollectionPermanentArchiveFiles.deleteAll()
        assertThrows(BadRequestException::class.java) { service.get("store", start.toLocalDate()) }
    }
    @Test fun `original file policy matches current museum and role checks`() = fixture {
        val id = schedule()
        val policy = CollectionSecurityDutyArchiveReadPolicy(scope)
        val reference = CollectionArchiveFileReference(id, "museum", WarehouseSecurityDutyService.BIZ, "store", "S_request-1", "evidence", "{}")
        login("one")
        assertDoesNotThrow { policy.requireReadable(reference) }
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference.copy(orgId="other")) }
        login("outsider")
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
        login("one"); UserDao["one"].enabled=false
        assertThrows(BadRequestException::class.java) { policy.requireReadable(reference) }
    }
    @Test fun `business download requires archived file of this warehouse and current access`() = fixture {
        schedule(); login("one")
        assertDoesNotThrow { service.requireOriginal("store", "evidence") }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("store", "unrelated") }
        assertThrows(BadRequestException::class.java) { service.requireOriginal("hall", "evidence") }
        login("outsider")
        assertThrows(BadRequestException::class.java) { service.requireOriginal("store", "evidence") }
    }
}
