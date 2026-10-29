package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class LeadershipDeparturePersistenceTest {
    @TempDir lateinit var originalRoot: java.nio.file.Path
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
            listOf("operator", "keeper", "leader", "new", "old", "custodian").forEach { id ->
                io.hizhengfu.cloud.sys.system.dao.UserDao.new(id) { loginName=id; name=id; password="unused"; enabled=true; groupId="museum" }
            }
            role("operator", "MANAGER", CollectionConst.ORG_MANAGE)
            role("operator", "INVENTORY_READER", CollectionConst.SELECT)
            role("operator", "INVENTORY_WRITER", CollectionConst.STORAGE)
            role("keeper", CollectionConst.ROLE_STORAGE_HEAD)
            role("leader", CollectionConst.ROLE_DIRECTOR)
            role("old", CollectionConst.ROLE_DIRECTOR); role("custodian", CollectionConst.ROLE_KEEPER)
            login("operator")
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            block(e)
        }
    }
    private fun plan(purpose: EnumInventoryPurpose = EnumInventoryPurpose.ROUTINE) = InventoryPlanVo(2026, EnumInventoryMethod.FULL, purpose, "全馆账物卡照清点", "keeper", "leader", "scope")
    private fun check(id: String) = InventoryItemCheckVo(id, at, setOf("custodian"), "实际现场位置", true, true, true, true, "保存稳定", false, "ledger", "card", "photo", "check")
    private fun completion() = InventoryCompletionVo(at.plusDays(2), "keeper", "leader", "report")
    private val directory=CollectionActorDirectory(scope)
    private val realUsers=CollectionResponsibleUserService(directory,scope)
    private val departures=LeadershipDepartureService(scope,directory,realUsers,archives)
    private val inventory=InventoryComplianceService(scope,realUsers,archives,incidents)
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

    private fun request()=LeadershipDepartureRequest(EnumLeadershipPost.BOTH,"old",at.minusYears(2),at.plusDays(5),"appointment","report","leader","appointment","keeper","核对全馆责任、在馆及外部保管藏品、任职与离任时间","scope")
    private fun signature()=CollectionCareSignatureVo("本人核对任职或清点移交原件及完整责任范围","report")
    private fun prepared(v:LeadershipDepartureRequest=request()):String {
        val id=departures.register("museum",v)
        login("keeper");departures.confirm("museum",id,"PK",signature())
        login("leader");departures.confirm("museum",id,"PS",signature());login("operator")
        return id
    }
    private fun planFor(id:String,v:LeadershipDepartureRequest=request())=plan(EnumInventoryPurpose.DIRECTOR_DEPARTURE).copy(leavingUserId=v.leavingUserId,successorUserId=v.successorUserId,leavingAt=v.leavingAt,responsibilityFileIds="scope",leadershipDepartureId=id)
    private fun finish(e:InventoryBatchDao,late:Boolean=false) {
        inventory.check(e.id.value,check("one"));inventory.check(e.id.value,check("two"))
        inventory.complete(e.id.value,completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover",supervisorAuthority="实际主管文物行政部门",supervisorNames=setOf("实际监交人员"),supervisionAt=at,appointmentFileIds="appointment",supervisionFileIds="supervision",departureDelayReason=if(late)"实际迟办原因" else null,departureDelayFileIds=if(late)"action" else null))
    }
    private fun items()=(departures.get("museum")["items"] as List<*>).filterIsInstance<Map<String,Any?>>()
    private fun item(id:String)=items().single{(it["record"] as LeadershipDepartureRecord).id==id}
    private fun confirmResults(id:String) {
        login("keeper");departures.confirm("museum",id,"HK",signature())
        login("leader");departures.confirm("museum",id,"HS",signature())
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["CASE_", "PK_"])
    fun `departure source cannot accept a rewritten frozen file identity`(phase:String)=fixture {
        val id=prepared()
        val evidence=LeadershipDepartureEvidence(archives)
        assertTrue(evidence.read("museum").prepared(id))
        val archive=CollectionPermanentArchiveDao.all().single {it.bizType==LeadershipDepartureEvidence.BIZ && it.phase=="$phase$id"}
        val file=CollectionPermanentArchiveFileDao.all().first {it.archiveId==archive.id.value}
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path", "/files/changed-departure-original.pdf")
        file.metadata=metadata.toString()
        assertThrows(BadRequestException::class.java) {evidence.read("museum")}
    }
    @Test fun `changed departure original bytes invalidate historical handover and retry`()=fixture {
        for (fileId in listOf("appointment","report","scope")) {
            java.nio.file.Files.writeString(originalRoot.resolve(fileId),"ORIGINAL")
            FileDao[fileId].apply {path=fileId;size=8}
        }
        val contentArchives=CollectionPermanentArchiveService(
            binder,scope,LocalFileObjectStorage(originalRoot.toString()),unprotectedArchiveReadAccessProvider())
        val contentDepartures=LeadershipDepartureService(scope,directory,realUsers,contentArchives)
        val id=contentDepartures.register("museum",request())
        login("keeper");contentDepartures.confirm("museum",id,"PK",signature())
        login("leader");contentDepartures.confirm("museum",id,"PS",signature())
        login("operator")
        assertTrue((contentDepartures.get("museum")["evidenceErrors"] as List<*>).isEmpty())
        val archivesBefore=CollectionPermanentArchiveDao.count()
        val filesBefore=CollectionPermanentArchiveFileDao.count()
        for (fileId in listOf("appointment","report")) {
            val file=originalRoot.resolve(fileId)
            val timestamp=java.nio.file.Files.getLastModifiedTime(file)
            java.nio.file.Files.writeString(file,"CHANGED!")
            java.nio.file.Files.setLastModifiedTime(file,timestamp)
            assertTrue((contentDepartures.get("museum")["evidenceErrors"] as List<*>).isNotEmpty(),fileId)
            assertThrows(BadRequestException::class.java) {contentDepartures.register("museum",request())}
            assertEquals(archivesBefore,CollectionPermanentArchiveDao.count(),fileId)
            assertEquals(filesBefore,CollectionPermanentArchiveFileDao.count(),fileId)
            java.nio.file.Files.writeString(file,"ORIGINAL")
            java.nio.file.Files.setLastModifiedTime(file,timestamp)
            assertTrue((contentDepartures.get("museum")["evidenceErrors"] as List<*>).isEmpty(),fileId)
        }
    }
    @Test fun `verified historical director departure can be recorded after account disable without restoring privileges`()=fixture { e ->
        io.hizhengfu.cloud.sys.system.dao.UserDao["old"].enabled=false
        val id=prepared()
        assertThrows(BadRequestException::class.java){inventory.begin(e.id.value,planFor(id).copy(leadershipDepartureId=null))}
        inventory.begin(e.id.value,planFor(id));assertEquals("INVENTORY_IN_PROGRESS",item(id)["status"])
        finish(e);assertEquals("PENDING_HANDOVER_REVIEW",item(id)["status"])
        login("leader");assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"HS",signature())}
        confirmResults(id);assertEquals("COMPLETED",item(id)["status"])
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertEquals("COMPLETED",item(id)["status"]);assertNull(directory.findActive("old"))
        assertEquals(setOf("batch"),LeadershipDepartureEvidence(archives).boundBatchIds("museum",id))
    }
    @Test fun `late director handover retains lateness after later online result confirmations`()=fixture { e ->
        val v=request().copy(leavingAt=at.plusDays(1));val id=prepared(v)
        inventory.begin(e.id.value,planFor(id,v));finish(e,true);confirmResults(id)
        assertEquals("COMPLETED_LATE",item(id)["status"])
    }
    @Test fun `current officials must personally verify appointment evidence in order before history can qualify`()=fixture { e ->
        val v=request();val id=departures.register("museum",v)
        val count=CollectionPermanentArchiveDao.count();assertEquals(id,departures.register("museum",v));assertEquals(count,CollectionPermanentArchiveDao.count())
        assertThrows(BadRequestException::class.java){inventory.begin(e.id.value,planFor(id))}
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"PK",signature())}
        login("leader");assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"PS",signature())}
        login("keeper");departures.confirm("museum",id,"PK",signature());departures.confirm("museum",id,"PK",signature())
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"PK",signature().copy(opinion="替换原确认"))}
        login("leader");departures.confirm("museum",id,"PS",signature());login("operator")
        assertEquals("PENDING_INVENTORY",item(id)["status"])
        inventory.begin(e.id.value,planFor(id))
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["purpose","method","leaving","successor","date","keeper","leader","scope-subset","warehouse","ownership"])
    fun `prepared case cannot authorize mismatched or partial inventory`(change:String)=fixture { e ->
        val id=prepared();var p=planFor(id)
        when(change) {
            "purpose"->p=p.copy(purpose=EnumInventoryPurpose.ROUTINE)
            "method"->p=p.copy(method=EnumInventoryMethod.SAMPLE)
            "leaving"->p=p.copy(leavingUserId="leader")
            "successor"->p=p.copy(successorUserId="old")
            "date"->p=p.copy(leavingAt=p.leavingAt!!.plusDays(1))
            "keeper"->p=p.copy(keeperUserId="old")
            "leader"->p=p.copy(leaderUserId="old")
            "scope-subset"->e.collectionIds="one"
            "warehouse"->e.warehouseId="store"
            "ownership"->CollectionOrgDao["museum"].ownership=EnumOwnership.NON_STATE
        }
        val count=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java){inventory.begin(e.id.value,p)}
        assertNull(e.plan);assertEquals(count,CollectionPermanentArchiveDao.count())
    }
    @Test fun `state museum still needs genuine competent supervision before result confirmation`()=fixture { e ->
        val id=prepared();inventory.begin(e.id.value,planFor(id))
        inventory.check(e.id.value,check("one"));inventory.check(e.id.value,check("two"))
        assertThrows(BadRequestException::class.java){inventory.complete(e.id.value,completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover"))}
        login("keeper");assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"HK",signature())}
        assertNull(e.completion);assertEquals("INVENTORY_IN_PROGRESS",item(id)["status"])
    }
    @Test fun `case correction preserves original signatures but cannot change a bound inventory source`()=fixture { e ->
        val id=prepared();val v=request().copy(previousId=id,replacementReason="更正任职原件说明",responsibilityStatement="依据原件补充责任范围")
        val next=departures.register("museum",v)
        assertEquals("SUPERSEDED",item(id)["status"]);assertEquals("PENDING_KEEPER",item(next)["status"])
        assertThrows(BadRequestException::class.java){inventory.begin(e.id.value,planFor(id))}
        login("keeper");departures.confirm("museum",next,"PK",signature());login("leader");departures.confirm("museum",next,"PS",signature());login("operator")
        inventory.begin(e.id.value,planFor(next))
        assertThrows(BadRequestException::class.java){departures.register("museum",v.copy(previousId=next,replacementReason="清点后不能替换"))}
        e.plan=null
        assertThrows(BadRequestException::class.java){departures.register("museum",v.copy(previousId=next,replacementReason="清空方案仍有开始原件"))}
        assertEquals("EVIDENCE_REQUIRED",item(next)["status"])
    }
    @Test fun `same case cannot start a second full inventory or appear as finished from a bare pass flag`()=fixture { e ->
        val id=prepared();inventory.begin(e.id.value,planFor(id));val other=InventoryBatchDao.new("another"){orgId="museum";batchName="重复清点"}
        assertThrows(BadRequestException::class.java){inventory.begin(other.id.value,planFor(id))}
        assertNull(other.plan);assertFalse(item(id)["canCorrect"] as Boolean)
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"HK",signature())}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["case-digest","appointment-reference","prepared-signature","inventory-check","inventory-complete","completed-reference"])
    fun `linked original damage cannot be displayed as completed or reused`(change:String)=fixture { e ->
        val id=prepared();inventory.begin(e.id.value,planFor(id));finish(e);confirmResults(id)
        when(change) {
            "case-digest"->CollectionPermanentArchiveDao.all().first{it.phase=="CASE_$id"}.digest="bad"
            "appointment-reference"->CollectionPermanentArchiveFileDao.all().first{it.bizId=="museum"}.delete()
            "prepared-signature"->CollectionPermanentArchiveDao.all().first{it.phase=="PK_$id"}.delete()
            "inventory-check"->e.checks=InventoryChecksVo()
            "inventory-complete"->e.completion=null
            "completed-reference"->CollectionPermanentArchiveFileDao.all().first{it.archiveId==CollectionPermanentArchiveDao.all().first{a->a.phase=="COMPLETED"}.id.value}.delete()
        }
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        val data=departures.get("museum");val errors=data["evidenceErrors"] as List<*>?
        assertTrue(!errors.isNullOrEmpty() || item(id)["status"]=="EVIDENCE_REQUIRED")
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"HS",signature())}
        assertThrows(BadRequestException::class.java){inventory.complete(e.id.value,completion())}
    }
    @Test fun `revoked current roles and museum changes invalidate stale login approvals`()=fixture {
        val id=departures.register("museum",request());login("keeper")
        val role=io.hizhengfu.cloud.sys.system.dao.RoleDao.all().single{it.code==CollectionConst.ROLE_STORAGE_HEAD};role.enabled=false
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"PK",signature())};role.enabled=true
        io.hizhengfu.cloud.sys.system.dao.UserDao["keeper"].groupId="other"
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"PK",signature())}
        login("operator");assertThrows(BadRequestException::class.java){departures.get("other")}
    }
    @Test fun `original login name resolves an inactive historical person without exposing or reviving the account`()=fixture {
        io.hizhengfu.cloud.sys.system.dao.UserDao["old"].apply { enabled=false; loginName="former.director" }
        val id=departures.register("museum",request().copy(leavingUserId="former.director"))
        assertEquals("old",(item(id)["record"] as LeadershipDepartureRecord).request.leavingUserId)
        assertEquals(id,departures.register("museum",request().copy(leavingUserId="former.director")))
        assertNull(directory.findActive("old"))
    }

    @Test fun `changed full collection scope can explicitly restart inventory while preserving original incomplete batch`()=fixture { e ->
        val id=prepared();inventory.begin(e.id.value,planFor(id))
        inventory.check(e.id.value,check("one"));inventory.check(e.id.value,check("two"))
        CollectionDao.new("three"){orgId="museum";totalRegNo="three";name="新入账藏品";nature=EnumCollectionNature.RELIC;categoryId="category-1";level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";registeredAt=at.plusHours(1)}
        assertThrows(BadRequestException::class.java){inventory.complete(e.id.value,completion())}
        val next=InventoryBatchDao.new("replanned"){orgId="museum";batchName="范围变化后重新组织全馆清点"}
        val p=planFor(id).copy(replacesInventoryBatchId=e.id.value,replanReason="清点期间正式藏品范围发生变化",replanFileIds="action")
        assertThrows(BadRequestException::class.java){inventory.begin(next.id.value,p.copy(replanReason=null))}
        assertThrows(BadRequestException::class.java){inventory.begin(next.id.value,p.copy(replanFileIds=null))}
        inventory.begin(next.id.value,p)
        assertEquals(3,next.baseline!!.items.size);assertEquals(2,e.checks!!.items.size);assertNull(e.completion)
        assertEquals(next.id.value,inventory.get(e.id.value).supersededByBatchId)
        assertTrue(inventory.get(e.id.value).evidenceErrors.isEmpty())
        assertThrows(BadRequestException::class.java){inventory.check(e.id.value,check("one").copy(replacementReason="不能改旧清点"))}
        assertThrows(BadRequestException::class.java){inventory.complete(e.id.value,completion())}
        inventory.check(next.id.value,check("three").copy(checkedAt=at.plusDays(1)))
        finish(next);confirmResults(id)
        assertEquals("COMPLETED",item(id)["status"]);assertEquals("replanned",item(id)["inventoryBatchId"])
        assertEquals(2,(item(id)["inventoryHistory"] as List<*>).size)
        val third=InventoryBatchDao.new("third"){orgId="museum";batchName="不能覆盖已完成清点"}
        login("operator");assertThrows(BadRequestException::class.java){inventory.begin(third.id.value,p.copy(replacesInventoryBatchId=next.id.value))}
    }
    @Test fun `replan cannot branch from obsolete predecessor or hide a damaged original batch`()=fixture { e ->
        val id=prepared();inventory.begin(e.id.value,planFor(id))
        val next=InventoryBatchDao.new("replanned"){orgId="museum";batchName="重办全馆清点"}
        val p=planFor(id).copy(replacesInventoryBatchId=e.id.value,replanReason="真实重办原因",replanFileIds="action")
        inventory.begin(next.id.value,p)
        val fork=InventoryBatchDao.new("fork"){orgId="museum";batchName="不能分叉"}
        assertThrows(BadRequestException::class.java){inventory.begin(fork.id.value,p)}
        finish(next)
        e.plan=null
        login("keeper");assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"HK",signature())}
        assertEquals("EVIDENCE_REQUIRED",item(id)["status"])
    }

    @Test fun `separators alone cannot stand in for any required appointment or signature original`()=fixture {
        val count=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java){departures.register("museum",request().copy(appointmentFileIds=", ,"))}
        assertEquals(count,CollectionPermanentArchiveDao.count())
        val id=departures.register("museum",request());login("keeper")
        assertThrows(BadRequestException::class.java){departures.confirm("museum",id,"PK",signature().copy(fileIds=", ,"))}
    }

    @Test fun `departure originals are limited to this museum case phase and referenced file`()=fixture { e ->
        val id=prepared();inventory.begin(e.id.value,planFor(id));finish(e);confirmResults(id)
        login("operator")
        val rows=CollectionPermanentArchiveDao.all().filter {it.bizType==LeadershipDepartureEvidence.BIZ && it.bizId=="museum"}
        for ((phase,file) in listOf("CASE_$id" to "appointment","CASE_$id" to "scope",
                "PK_$id" to "report","PS_$id" to "report","HK_$id" to "report","HS_$id" to "report")) {
            departures.requireArchivedOriginal("museum",rows.single {it.phase==phase}.id.value,file)
        }
        val case=rows.single {it.phase=="CASE_$id"}
        val keeper=rows.single {it.phase=="PK_$id"}
        assertThrows(BadRequestException::class.java){departures.requireArchivedOriginal("museum",case.id.value,"supervision")}
        assertThrows(BadRequestException::class.java){departures.requireArchivedOriginal("museum",keeper.id.value,"appointment")}
        assertThrows(BadRequestException::class.java){departures.requireArchivedOriginal("other",case.id.value,"appointment")}
        val inventoryOriginal=CollectionPermanentArchiveDao.all().single {it.bizType==InventoryComplianceService.BIZ && it.phase=="COMPLETED"}
        assertThrows(BadRequestException::class.java){departures.requireArchivedOriginal("museum",inventoryOriginal.id.value,"report")}
        val digest=inventoryOriginal.digest
        inventoryOriginal.digest="damaged"
        assertThrows(BadRequestException::class.java){departures.requireArchivedOriginal("museum",rows.single {it.phase=="HK_$id"}.id.value,"report")}
        inventoryOriginal.digest=digest
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){departures.requireArchivedOriginal("museum",case.id.value,"appointment")}
    }

    @Test fun `superseded departure case retains its own readable original without borrowing the new version`()=fixture {
        val old=departures.register("museum",request())
        val next=departures.register("museum",request().copy(previousId=old,replacementReason="更正任职依据",responsibilityStatement="补充全部责任范围"))
        val rows=CollectionPermanentArchiveDao.all().filter {it.bizType==LeadershipDepartureEvidence.BIZ}
        val oldArchive=rows.single {it.phase=="CASE_$old"}
        val nextArchive=rows.single {it.phase=="CASE_$next"}
        departures.requireArchivedOriginal("museum",oldArchive.id.value,"appointment")
        departures.requireArchivedOriginal("museum",nextArchive.id.value,"appointment")
        assertEquals("SUPERSEDED",item(old)["status"])
    }

}
