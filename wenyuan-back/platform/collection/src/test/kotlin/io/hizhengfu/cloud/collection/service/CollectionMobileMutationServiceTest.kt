package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.security.MessageDigest
import java.time.LocalDateTime

class CollectionMobileMutationServiceTest {
    private val scope=CollectionScopeResolver("single","museum")
    private val inventory=mock(InventoryComplianceService::class.java)
    private val service=CollectionMobileMutationService(scope,inventory,mock(CollectionCareService::class.java))
    private lateinit var request: MobileInventoryCheckRequest
    private fun auth(id: String="operator") {
        val user=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(block: ()->Unit) {
        auth()
        val db=Database.connect("jdbc:sqlite:file:mobile-mutation-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionOrgs,InventoryBatches,MobileOperationReceipts,RfidScanSessions)
            originalReaderSchema();originalReader("operator",CollectionConst.STORAGE);originalReader("second",CollectionConst.STORAGE)
            CollectionOrgDao.new("museum"){name="本馆";code="M";ownership=EnumOwnership.STATE}
            val baseline=InventoryBaselineVo(LocalDateTime.of(2026,10,1,9,0),EnumOwnership.STATE,
                listOf(InventoryBaselineItemVo("c1",CollectionCatalogContentVo(formalName="藏品"),null,
                    "WAREHOUSE:one","一号库",false,false,false)))
            InventoryBatchDao.new("task"){orgId="museum";batchName="盘库";this.baseline=baseline}.flush()
            val revision=MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJson(baseline)!!.toByteArray())
                .joinToString(""){"%02x".format(it)}
            request=MobileInventoryCheckRequest("op-1",revision,InventoryItemCheckVo("c1",LocalDateTime.of(2026,10,20,9,0),
                setOf("operator"),"一号库",true,true,true,true,"完好",false,"ledger","card","photo","signed"))
            `when`(inventory.check("task",request.value)).thenReturn("task")
            block()
        }
    }
    @Test fun `lost response retry invokes business only once`()=fixture {
        assertEquals("task",service.inventoryCheck("task",request))
        assertEquals("task",service.inventoryCheck("task",request))
        verify(inventory,times(1)).check("task",request.value)
        assertEquals(1,MobileOperationReceipts.selectAll().count())
    }
    @Test fun `reused operation id cannot overwrite a later or different observation`()=fixture {
        service.inventoryCheck("task",request)
        assertThrows(BadRequestException::class.java){service.inventoryCheck("task",request.copy(value=request.value.copy(condition="不同现状")))}
        verify(inventory,times(1)).check("task",request.value)
    }
    @Test fun `stale baseline keeps offline operation uncommitted`()=fixture {
        assertThrows(BadRequestException::class.java){service.inventoryCheck("task",request.copy(baselineRevision="stale"))}
        verifyNoInteractions(inventory)
        assertEquals(0,MobileOperationReceipts.selectAll().count())
    }
    @Test fun `another operators newer check cannot be overwritten by an offline draft`()=fixture {
        InventoryBatchDao["task"].apply {
            checks=InventoryChecksVo(listOf(request.value.copy(condition="另一位操作员的新核验")))
            flush()
        }
        assertThrows(BadRequestException::class.java){service.inventoryCheck("task",request)}
        verifyNoInteractions(inventory)
        assertEquals(0,MobileOperationReceipts.selectAll().count())
    }
    @Test fun `revoked permission is checked before replaying a receipt`()=fixture {
        service.inventoryCheck("task",request)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.inventoryCheck("task",request)}
        verify(inventory,times(1)).check("task",request.value)
    }
    @Test fun `failed business write is not acknowledged`()=fixture {
        `when`(inventory.check("task",request.value)).thenThrow(BadRequestException("核验材料不完整"))
        assertThrows(BadRequestException::class.java){service.inventoryCheck("task",request)}
        assertEquals(0,MobileOperationReceipts.selectAll().count())
    }
    @Test fun `mobile completion rejects active rounds then delegates actual signed completion after sealing`()=fixture {
        RfidScanSessions.insert {
            it[id]="scan";it[orgId]="museum";it[sessionNo]="test-scan";it[deviceId]="d1"
            it[sessionType]=EnumRfidScanSessionType.INVENTORY;it[businessId]="task";it[operatorId]="second"
            it[directInput]=true;it[sessionState]=EnumRfidScanSessionState.READING
        }
        val value=InventoryCompletionVo(LocalDateTime.now(),"keeper","leader","signed")
        val completion=MobileInventoryCompletionRequest(request.baselineRevision,value,"NONE","NONE")
        assertThrows(BadRequestException::class.java){service.inventoryComplete("task",completion)}
        verifyNoInteractions(inventory)
        RfidScanSessions.update({RfidScanSessions.id eq "scan"}) {it[sessionState]=EnumRfidScanSessionState.FINALIZED}
        `when`(inventory.complete("task",value)).thenReturn("task")
        assertEquals("task",service.inventoryComplete("task",completion))
        verify(inventory,times(1)).complete("task",value)
    }
    @Test fun `mobile completion rechecks baseline and revoked membership before accepting signed report`()=fixture {
        val value=InventoryCompletionVo(LocalDateTime.now(),"keeper","leader","signed")
        assertThrows(BadRequestException::class.java){service.inventoryComplete("task",MobileInventoryCompletionRequest("stale",value,"NONE","NONE"))}
        assertThrows(BadRequestException::class.java){service.inventoryComplete("task",MobileInventoryCompletionRequest(request.baselineRevision,value,"stale","NONE"))}
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.inventoryComplete("task",MobileInventoryCompletionRequest(request.baselineRevision,value,"NONE","NONE"))}
        verifyNoInteractions(inventory)
    }
}
