package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path

class EntryVoucherReadRuntimeTest {
    @TempDir lateinit var dir:Path
    private fun fixture(mode:String="multi",body:(EntryVoucherService,CollectionPrintService,CollectionService)->Unit) {
        val db=Database.connect("jdbc:sqlite:${dir.resolve("entry.db")}","org.sqlite.JDBC")
        val stale=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf("ADMIN",CollectionConst.SELECT,CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(stale,null,stale.authorities)
        try {transaction(db) {
            originalReaderSchema();SchemaUtils.create(CollectionRetainedLedgers,CollectionOrgs,EntryVouchers,Acquisitions,Warehouses,StorageMovements,Collections,AccessionReceipts,CollectionCategories,CollectionMedias,CollectionAttachments)
            for(org in listOf("museum","other")) {
                CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                WarehouseDao.new("store-$org"){orgId=org;name="库房-$org";sceneType=EnumLocationScene.STORAGE}
                CollectionDao.new("collection-$org"){orgId=org;name="藏品-$org";totalRegNo="REG-$org";entryVoucherNo="RJ-$org"}
                for(prefix in listOf("held","available"))AcquisitionDao.new("$prefix-$org"){orgId=org;name="征集-$prefix-$org";source=EnumSource.PURCHASE;reason="秘密估价-$org";images="秘密附件-$org";flowStatus=EnumFlowStatus.PASS}
                EntryVoucherDao.new("voucher-$org"){orgId=org;voucherNo="RJ-$org";acquisitionIds="held-$org";warehouseId="store-$org"}
                StorageMovementDao.new("move-$org"){orgId=org;type=EnumMoveType.IN;collectionId="collection-$org";refBizId="voucher-$org"}
            }
            originalReader("reader",CollectionConst.SELECT)
            val scope=CollectionScopeResolver(mode,"museum")
            val collections=mock(CollectionService::class.java)
            val printer=CollectionPrintService(collections,scope,mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java))
            val service=EntryVoucherService(scope,collections,mock(BusinessAttachmentBinder::class.java),printer,mock(AcquisitionComplianceService::class.java),mock(AccessionReceiptService::class.java))
            body(service,printer,collections)
        }}finally{SecurityContextHolder.clearContext()}
    }
    @Suppress("UNCHECKED_CAST") private fun rows(s:EntryVoucherService)=s.findPage(MockHttpServletRequest()).list as List<EntryVoucherVo>

    @Test fun `current membership controls lists detail and printed voucher despite stale administrator login`()=fixture {s,p,_ ->
        assertEquals(listOf("voucher-museum"),rows(s).map {it.id})
        assertThrows(BadRequestException::class.java){s.get("voucher-other")}
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-other")}
        assertEquals("voucher-museum",s.print("voucher-museum").businessId)
        UserDao["reader"].groupId="other"
        assertEquals(listOf("voucher-other"),rows(s).map {it.id})
        assertThrows(BadRequestException::class.java){s.get("voucher-museum")}
        assertThrows(BadRequestException::class.java){s.print("voucher-museum")}
    }

    @Test fun `registrable choices use current primary museum and preserve occupancy and redaction`()=fixture {s,_,_ ->
        assertEquals(listOf("available-museum"),s.registrableAcquisitions().map {it.id})
        assertTrue(s.registrableAcquisitions().single().reason==null)
        UserDao["reader"].groupId="other"
        assertEquals(listOf("available-other"),s.registrableAcquisitions().map {it.id})
        actorRole("reader","ACQUIRER",setOf(CollectionConst.ACQUIRE))
        assertEquals("秘密估价-other",s.registrableAcquisitions().single().reason)
        AcquisitionDao["available-other"].collectionId="collection-other"
        assertTrue(s.registrableAcquisitions().isEmpty())
    }

    @Test fun `revocation and account disabling reject every voucher read entrance`()=fixture {s,p,_ ->
        assertEquals("voucher-museum",s.get("voucher-museum")!!.id)
        RolePermissions.deleteAll()
        fun deny() {
            assertThrows(BadRequestException::class.java){s.findPage(MockHttpServletRequest())}
            assertThrows(BadRequestException::class.java){s.get("voucher-museum")}
            assertThrows(BadRequestException::class.java){s.registrableAcquisitions()}
            assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        }
        deny();actorRole("reader","READ",setOf(CollectionConst.SELECT));UserDao["reader"].enabled=false;deny()
    }

    @Test fun `single configured museum cannot be bypassed by cross museum reads or stale department`()=fixture("single") {s,p,_ ->
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals(listOf("voucher-museum"),rows(s).map {it.id})
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-other")}
        RolePermissions.deleteWhere {RolePermissions.permissionId eq CollectionConst.CROSS_ORG}
        UserDao["reader"].groupId="other"
        assertEquals(0L,s.findPage(MockHttpServletRequest()).total)
        assertThrows(BadRequestException::class.java){s.registrableAcquisitions()}
    }

    @Test fun `foreign acquisitions warehouses and movements do not enrich a local voucher`()=fixture {s,_,_ ->
        EntryVoucherDao["voucher-museum"].apply {acquisitionIds="held-museum,held-other";warehouseId="store-other"}
        StorageMovementDao["move-other"].refBizId="voucher-museum"
        val v=s.get("voucher-museum")!!
        assertEquals(listOf("held-museum"),v.acquisitionSummaries.map {it.id})
        assertTrue(v.warehouseName==null)
        assertEquals(listOf("move-museum"),v.movementIds)
        assertEquals("held-museum,held-other",EntryVoucherDao["voucher-museum"].acquisitionIds)
    }

    @Test fun `printing rejects inconsistent acquisition and registered collection museums before rendering`()=fixture {_,p,collections ->
        actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
        EntryVoucherDao["voucher-museum"].acquisitionIds="held-other"
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        EntryVoucherDao["voucher-museum"].acquisitionIds="held-museum"
        AcquisitionDao["held-museum"].collectionId="collection-other"
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        verifyNoInteractions(collections)
    }

    @Test fun `printing rejects a foreign warehouse and never substitutes missing registered content`()=fixture {_,p,collections ->
        EntryVoucherDao["voucher-museum"].warehouseId="store-other"
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        EntryVoucherDao["voucher-museum"].warehouseId="store-museum"
        AcquisitionDao["held-museum"].collectionId="missing"
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        verifyNoInteractions(collections)
        AcquisitionDao["held-museum"].collectionId="collection-museum"
        assertEquals("REG-museum",p.entryVoucherPrint("voucher-museum").collections.single().totalRegNo)
    }

    private fun receiptReader()=AccessionReceiptService(CollectionScopeResolver("multi",""),mock(AcquisitionComplianceService::class.java),
        mock(CollectionResponsibleUserService::class.java),mock(CollectionPermanentArchiveService::class.java),mock(CollectionMainFileReadPolicy::class.java))

    @Test fun `actual receipt detail uses current duties and never exposes foreign acquisition or register data`()=fixture {_,_,_ ->
        val receipts=receiptReader()
        assertEquals(1,(receipts.detail("voucher-museum")["items"] as List<*>).size)
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-other")}
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-museum")}
        actorRole("reader","REGISTRAR",setOf(CollectionConst.LEDGER_CREATE))
        assertEquals(1,(receipts.detail("voucher-museum")["items"] as List<*>).size)
        EntryVoucherDao["voucher-museum"].acquisitionIds="held-other"
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-museum")}
        EntryVoucherDao["voucher-museum"].acquisitionIds="held-museum"
        AcquisitionDao["held-museum"].collectionId="collection-other"
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-museum")}
        AcquisitionDao["held-museum"].collectionId=null
        UserDao["reader"].groupId="other"
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-museum")}
        assertEquals(1,(receipts.detail("voucher-other")["items"] as List<*>).size)
    }

    @Test fun `receipt detail and print reject foreign or orphan receipt rows without rewriting history`()=fixture {_,p,_ ->
        val row=AccessionReceiptDao.new("wrong-receipt") {
            orgId="other";voucherId="voucher-museum";acquisitionId="held-museum";version=1;sourceSnapshot="{}"
            val at=java.time.LocalDateTime.of(2026,8,1,10,0)
            content=AccessionReceiptVo("held-museum",at,"store-other",setOf("keeper"),setOf("移交人"),
                listOf(AccessionPieceVo("piece-1",at,"实物标识","完整",true,true,"photo")),false,"original")
        }
        val receipts=receiptReader()
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-museum")}
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        row.orgId="museum";row.acquisitionId="unlisted"
        assertThrows(BadRequestException::class.java){receipts.detail("voucher-museum")}
        assertThrows(BadRequestException::class.java){p.entryVoucherPrint("voucher-museum")}
        assertEquals(1L,AccessionReceiptDao.count());assertEquals("unlisted",row.acquisitionId)
    }

    @Test fun `supplied voucher print payloads reload current business and cannot bypass revoked access`()=fixture {_,p,_ ->
        val old=p.entryVoucherPrint("voucher-museum").copy(title="旧前端标题",fields=listOf(CollectionPrintFieldVo("旧前端字段","旧内容")))
        EntryVoucherDao["voucher-museum"].basis="当前入藏依据"
        val fresh=CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(data=old),p::print).single()
        assertTrue(fresh.fields.any {it.value=="当前入藏依据"})
        assertFalse(fresh.fields.any {it.value=="旧内容"})
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(items=listOf(old)),p::print)}
        assertThrows(BadRequestException::class.java){CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(data=old.copy(businessId=null)),p::print)}
    }

    @Test fun `legacy blank registration links remain explicitly unregistered`()=fixture {s,p,collections ->
        for(value in listOf(""," ")) {
            AcquisitionDao["held-museum"].collectionId=value
            AcquisitionDao["available-museum"].collectionId=value
            assertEquals("征集-held-museum",p.entryVoucherPrint("voucher-museum").collections.single().name)
            val item=(receiptReader().detail("voucher-museum")["items"] as List<*>).single() as Map<*,*>
            assertTrue(item["totalRegNo"]==null)
            assertEquals(listOf("available-museum"),s.registrableAcquisitions().map {it.id})
        }
        verifyNoInteractions(collections)
    }
}
