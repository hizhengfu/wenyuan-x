package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.rfid.dao.*
import io.hizhengfu.cloud.collection.rfid.domain.*
import io.hizhengfu.cloud.collection.service.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceDao
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class RfidMobileInventorySessionTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val inventory = mock(InventoryService::class.java)
    private val compliance = mock(InventoryComplianceService::class.java)
    private val commands = mock(RfidDeviceCommandPort::class.java)
    private val service = RfidScanSessionService(scope, commands, mock(LoanService::class.java),
        mock(RequisitionService::class.java), inventory, mock(CollectionReturnService::class.java),
        RfidRealtimeEventService(scope), 300, 180, 14_400, 86_400, mobileInventory = compliance)

    @AfterEach fun clear() = SecurityContextHolder.clearContext()

    private fun auth(id: String = "operator") {
        val user = SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.RFID_SCAN)),enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }

    private fun fixture(block: () -> Unit) {
        auth()
        val context = StaticApplicationContext().apply {
            beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); refresh()
        }
        SpringContextHolder().setApplicationContext(context)
        val db = Database.connect("jdbc:sqlite:file:mobile-${System.nanoTime()}?mode=memory&cache=shared", driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionOrgs, Collections, InventoryBatches, MuseumDevices, RfidTags,
                RfidScanSessions, RfidScanExpectedItems, RfidScanReads, RfidHandheldReadBatches, RfidRealtimeEvents)
            originalReaderSchema()
            originalReader("operator", CollectionConst.RFID_SCAN)
            originalReader("second", CollectionConst.RFID_SCAN)
            CollectionOrgDao.new("museum") { name="本馆"; code="M"; ownership=EnumOwnership.STATE }
            CollectionOrgDao.new("other") { name="外馆"; code="O"; ownership=EnumOwnership.STATE }
            listOf("c1", "c2").forEach { id -> CollectionDao.new(id) {
                orgId="museum"; name=id; totalRegNo=id; lifeStatus=EnumCollectionStatus.IN_STORE
                rfidTag=if(id=="c1") "3001" else "3002"
            } }
            InventoryBatchDao.new("task") {
                orgId="museum"; batchName="名称可超过六十四个字符但稳定批次ID不受影响"
                plan=InventoryPlanVo(2026,EnumInventoryMethod.SPECIAL,EnumInventoryPurpose.ROUTINE,"库房盘库","keeper","leader","scope")
                baseline=InventoryBaselineVo(LocalDateTime.now(),EnumOwnership.STATE,listOf("c1","c2").map {
                    InventoryBaselineItemVo(it,CollectionCatalogContentVo(formalName=it),null,"WAREHOUSE:one","一号库",false,false,false)
                })
            }
            `when`(compliance.get("task")).thenAnswer { InventoryBatchDao["task"].toModel() }
            listOf("d1","d2").forEach { id -> MuseumDeviceDao.new(id) {
                orgId="museum"; code=id; name=id; modelCode="HANDHELD"; gatewayId="gateway"; externalDeviceId=id
            }; `when`(commands.supportsAnyRole(id,"museum",setOf("HANDHELD"))).thenReturn(true) }
            block()
        }
    }
    private fun create(device: String = "d1", request: String = "request-1", ids: List<String> = emptyList()) = service.create(
        RfidScanSessionCreateVo(deviceId=device,type=EnumRfidScanSessionType.INVENTORY,businessId="task",
            clientRequestId=request,directInput=true,collectionIds=ids))

    @Test fun `two handhelds scan one task without any workstation table or binding`() = fixture {
        val a=create(); val b=create("d2","request-2")
        assertNull(a.workstationId); assertNull(b.workstationId)
        assertEquals("task",a.businessId); assertEquals(2,a.expectedCount)
        assertNotEquals(a.id,b.id)
        assertThrows(BadRequestException::class.java) { create("d1","request-3") }
        assertEquals(2,RfidScanSessions.selectAll().count())
    }
    @Test fun `mobile request retries are operator bound and do not allocate another session`() = fixture {
        val a=create(); assertEquals(a.id,create().id)
        auth("second")
        assertThrows(BadRequestException::class.java) { create() }
        assertEquals(1,RfidScanSessions.selectAll().count())
    }
    @Test fun `task partitions cannot add outside collections`() = fixture {
        assertThrows(BadRequestException::class.java) { create(ids=listOf("foreign")) }
        assertEquals(1,create(ids=listOf("c1")).expectedCount)
    }
    @Test fun `superseded or unverified inventory cannot be downloaded or claimed`() = fixture {
        `when`(compliance.get("task")).thenReturn(InventoryBatchVo(id="task", supersededByBatchId="replacement"))
        assertThrows(BadRequestException::class.java) { service.mobileInventoryManifest("task") }
        assertThrows(BadRequestException::class.java) { create() }
        `when`(compliance.get("task")).thenReturn(InventoryBatchVo(id="task", evidenceErrors=listOf("原件摘要不匹配")))
        assertThrows(BadRequestException::class.java) { service.mobileInventoryManifest("task") }
        assertThrows(BadRequestException::class.java) { create() }
        assertEquals(0, RfidScanSessions.selectAll().count())
    }
    @Test fun `task list uses stable batch id and does not require workstation parameter`() = fixture {
        val request=MockHttpServletRequest().apply {setParameter("type","INVENTORY")}
        val rows = service.handheldTasks(request).list as List<RfidHandheldTaskVo>
        assertEquals("task",rows.single().businessId)
    }
    @Test fun `device role and current authorization are revalidated`() = fixture {
        `when`(commands.supportsAnyRole("d1","museum",setOf("HANDHELD"))).thenReturn(false)
        assertThrows(BadRequestException::class.java) { create() }
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { create("d2") }
    }
    @Test fun `sealing a scan round preserves missing items without executing inventory business`() = fixture {
        val session=create(); service.start(session.id)
        assertEquals(false,service.sealMobileObservations(session.id,RfidHandheldSealVo("local",0))["sealed"])
        RfidScanSessions.update({RfidScanSessions.id eq session.id}) {
            it[settledAt]=LocalDateTime.now().minusSeconds(1)
        }
        assertEquals(true,service.sealMobileObservations(session.id,RfidHandheldSealVo("local",0))["sealed"])
        assertEquals(EnumRfidScanSessionState.FINALIZED,service.get(session.id).state)
        assertEquals(true,service.sealMobileObservations(session.id,RfidHandheldSealVo("local",0))["sealed"])
        verifyNoInteractions(inventory)
        assertEquals(2,service.preview(session.id).missing)
        assertDoesNotThrow { create("d1","next-round") }
    }
    @Test fun `sealing rejects missing upload batches`() = fixture {
        val session=create(); service.start(session.id)
        assertThrows(BadRequestException::class.java) {
            service.sealMobileObservations(session.id,RfidHandheldSealVo("local",1))
        }
        assertEquals(EnumRfidScanSessionState.READING,service.get(session.id).state)
    }
    @Test fun `mobile scans survive a workday but fixed readers keep their shorter watchdog`() = fixture {
        val session=create();service.start(session.id)
        RfidScanSessions.update({RfidScanSessions.id eq session.id}) {
            it[startedAt]=LocalDateTime.now().minusHours(8)
        }
        service.recoverStaleSessions()
        assertEquals(EnumRfidScanSessionState.READING,service.get(session.id).state)
    }

    @Test fun `manifest merges rounds and exposes checks actions location and current stock separately`() = fixture {
        val batch=InventoryBatchDao["task"]
        CollectionDao["c2"].lifeStatus=EnumCollectionStatus.ON_LOAN
        val check=InventoryItemCheckVo("c2",LocalDateTime.now().withNano(0),setOf("keeper"),"借展现场待核实",
            true,false,true,true,"实物未在清单位置找到",false,"ledger","card","photo","signed","待查","核对借还单")
        batch.checks=InventoryChecksVo(listOf(check))
        batch.flush() // 模拟上一次核验 HTTP 事务已提交，再领取新轮次。
        val a=create(ids=listOf("c1"));service.start(a.id)
        service.recordRead(a.id,RfidHandheldReadVo(deviceId="d1",epc="3001",tid="E200"))
        val b=create("d2","request-second",listOf("c2"));service.start(b.id)
        val manifest=service.mobileInventoryManifest("task")
        val items=manifest["items"] as List<Map<String,Any?>>
        assertEquals("一号库",items.first()["location"])
        assertEquals(true,items.first()["observed"])
        assertEquals(check,items.last()["check"])
        assertEquals(EnumCollectionStatus.ON_LOAN,items.last()["lifeStatus"])
        assertNull(items.last()["issueAction"])
        assertFalse(manifest["finished"] as Boolean)
        assertEquals(2,(manifest["scanRounds"] as List<*>).size)
        val reads=manifest["reads"] as List<Map<String,Any?>>
        assertEquals(listOf("一号库"),reads.single()["scopeLocations"])
        assertEquals("c1",reads.single()["collectionName"])
        verifyNoInteractions(inventory)
    }

    @Test fun `full task observation includes another partition but conflicts cannot hide behind an earlier match`() = fixture {
        listOf("c1" to "3001","c2" to "3002").forEach { (collection,epc) -> RfidTags.insert {
            it[id]="tag-$collection";it[orgId]="museum";it[RfidTags.collectionId]=collection
            it[RfidTags.epc]=epc;it[tid]=if(collection=="c1")"E200" else "E202";it[tagStatus]=EnumRfidTagStatus.ACTIVE
        } }
        val a=create(ids=listOf("c1"));service.start(a.id)
        service.recordRead(a.id,RfidHandheldReadVo(deviceId="d1",epc="3001",tid="E200"))
        service.recordRead(a.id,RfidHandheldReadVo(deviceId="d1",epc="3002",tid="E202"))
        var items=service.mobileInventoryManifest("task")["items"] as List<Map<String,Any?>>
        assertTrue(items.all{it["observed"]==true})
        val b=create("d2","second-round",listOf("c1"));service.start(b.id)
        service.recordRead(b.id,RfidHandheldReadVo(deviceId="d2",epc="3001",tid="E201"))
        items=service.mobileInventoryManifest("task")["items"] as List<Map<String,Any?>>
        assertEquals(true,items.first()["conflict"])
        assertEquals(false,items.first()["observed"])
        assertNull(InventoryBatchDao["task"].finishedAt)
    }

    @Test fun `manifest unknown tags retain identity and cancelled rounds do not supply observations`() = fixture {
        val a=create();service.start(a.id)
        service.recordRead(a.id,RfidHandheldReadVo(deviceId="d1",epc="ABCD",tid="E200"))
        val read=(service.mobileInventoryManifest("task")["reads"] as List<Map<String,Any?>>).single()
        assertEquals("ABCD",read["epc"])
        assertEquals(EnumRfidReadClassification.UNKNOWN,read["classification"])
        assertNull(read["collectionName"])
        RfidScanSessions.update({RfidScanSessions.id eq a.id}) {it[sessionState]=EnumRfidScanSessionState.CANCELLED}
        assertTrue((service.mobileInventoryManifest("task")["reads"] as List<*>).isEmpty())
    }

    @Test fun `completed inventory can be reviewed but cannot claim new scanning rounds`() = fixture {
        InventoryBatchDao["task"].finishedAt=LocalDateTime.now()
        val request=MockHttpServletRequest().apply {setParameter("type","INVENTORY")}
        assertTrue((service.handheldTasks(request).list as List<*>).isEmpty())
        request.setParameter("inventoryStatus","COMPLETED")
        val row=(service.handheldTasks(request).list as List<RfidHandheldTaskVo>).single()
        assertEquals("task",row.businessId)
        assertEquals(2,row.collectionCount)
        assertEquals(true,service.mobileInventoryManifest("task")["finished"])
        assertThrows(BadRequestException::class.java){create()}
        request.setParameter("inventoryStatus","invented")
        assertThrows(BadRequestException::class.java){service.handheldTasks(request)}
    }
}
