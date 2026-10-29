package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionSearchAccessRuntimeTest {
    @TempDir lateinit var dir: Path

    private fun fixture(mode: String = "multi", body: (CollectionUnifiedSearchService, AcquisitionService) -> Unit) {
        val db = Database.connect("jdbc:sqlite:${dir.resolve("search.db")}", "org.sqlite.JDBC")
        val stale = SecurityUser(id="reader", name="旧登录", username="reader", password="", groupId="museum",
            groupIds=setOf("museum"), postIds=emptySet(), dataType=0, roles=setOf(EnumRole.ADMIN.value),
            authorities=listOf("ADMIN", CollectionConst.SELECT, CollectionConst.ACQUIRE, CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(stale, null, stale.authorities)
        try {
            transaction(db) {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs, Collections, AcquisitionPlans, Acquisitions, EntryVouchers, Appraisals, AppraisalMembers,
                    CollectionRetainedLedgers, CollectionPermanentArchives, CollectionPermanentArchiveFiles, io.hizhengfu.cloud.sys.file.dao.Files)
                for (org in listOf("museum", "other")) {
                    CollectionOrgDao.new(org) { name=org; code=org; systemGroupId=org }
                    CollectionDao.new("collection-$org") { orgId=org; name="关联藏品-$org"; totalRegNo="REG-$org" }
                    AcquisitionDao.new("acq-$org") { orgId=org; name="公开名称-$org"; source=EnumSource.PURCHASE;
                        reason="秘密估价-$org"; images="秘密附件-$org"; collectionId="collection-$org" }
                }
                originalReader("reader", CollectionConst.SELECT)
                val scope = CollectionScopeResolver(mode, "museum")
                val annualEvidence=mock(AnnualReportEvidenceService::class.java)
                org.mockito.Mockito.`when`(annualEvidence.verifiedRecordIds(2025)).thenReturn(
                    setOf("annual-museum-1","annual-museum-2","annual-other-1"))
                body(CollectionUnifiedSearchService(scope,annualEvidence), AcquisitionService(mock(FlowInstanceService::class.java), scope,
                    mock(BusinessAttachmentBinder::class.java), mock(AcquisitionComplianceService::class.java),
                    CollectionRetainedLedgerService(scope, CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java), scope))))
            }
        } finally { SecurityContextHolder.clearContext() }
    }

    @Test fun `mixed sources use a stable chronological prefix across pages`() = fixture { service, _ ->
        val at = LocalDateTime.of(2026, 9, 1, 12, 0)
        repeat(5) { index ->
            AcquisitionDao.new("ordered-acq-$index") { orgId="museum"; name="顺序-$index"; source=EnumSource.DONATE; createTime=at.plusMinutes(index.toLong()) }
            EntryVoucherDao.new("ordered-voucher-$index") { orgId="museum"; voucherNo="顺序-$index"; acquisitionIds=""; createTime=at.plusMinutes(index.toLong()) }
        }
        fun page(number: Int, size: Int) = service.search(CollectionUnifiedSearchQueryVo(keyword="顺序", group="ACQUISITION", pageNum=number, pageSize=size)).groups.single()
        val whole = page(1, 50).items.map { "${it.type}:${it.id}" }
        val paged = (1..4).flatMap { page(it, 3).items }.map { "${it.type}:${it.id}" }
        assertEquals(10, whole.size)
        assertEquals(whole, paged)
        assertEquals(10, paged.toSet().size)
        val onlyVouchers = service.search(CollectionUnifiedSearchQueryVo(keyword="顺序", group="ACQUISITION", type="ENTRY_VOUCHER", pageSize=50)).groups.single()
        assertEquals(5L, onlyVouchers.total)
        assertTrue(onlyVouchers.items.all { it.type == "ENTRY_VOUCHER" })
    }

    private fun search(service: CollectionUnifiedSearchService, keyword: String) =
        service.search(CollectionUnifiedSearchQueryVo(keyword=keyword, group="ACQUISITION"))

    @Test fun `ordinary searches cannot disclose or match confidential acquisition fields`() = fixture { unified, acquisition ->
        assertEquals(0L, search(unified, "秘密估价").total)
        val visible = search(unified, "公开名称").groups.single().items.single()
        assertFalse(visible.summary.orEmpty().contains("秘密"))
        assertFalse(visible.matchedFields.contains("征集理由"))
        for (keyword in listOf("秘密估价", "秘密附件")) assertEquals(0L, acquisition.findPage(MockHttpServletRequest(), keyword).total)
        assertEquals(1L, acquisition.findPage(MockHttpServletRequest(), "公开名称").total)
    }

    @Test fun `confidential permission is checked again after a successful search`() = fixture { unified, acquisition ->
        actorRole("reader", "ACQUIRER", setOf(CollectionConst.ACQUIRE))
        assertEquals(1L, search(unified, "秘密估价").total)
        assertTrue(search(unified, "公开名称").groups.single().items.single().summary.orEmpty().contains("秘密估价"))
        assertEquals(1L, acquisition.findPage(MockHttpServletRequest(), "秘密附件").total)
        RolePermissions.deleteWhere { RolePermissions.permissionId eq CollectionConst.ACQUIRE }
        assertEquals(0L, search(unified, "秘密估价").total)
        assertEquals(0L, acquisition.findPage(MockHttpServletRequest(), "秘密附件").total)
    }

    @Test fun `current museum membership replaces stale administrator scope`() = fixture { unified, acquisition ->
        assertEquals(listOf("acq-museum"), search(unified, "公开名称").groups.single().items.map { it.id })
        UserDao["reader"].groupId="other"
        assertEquals(listOf("acq-other"), search(unified, "公开名称").groups.single().items.map { it.id })
        @Suppress("UNCHECKED_CAST")
        val rows = acquisition.findPage(MockHttpServletRequest(), "公开名称").list as List<AcquisitionVo>
        assertEquals(listOf("acq-other"), rows.map { it.id })
    }

    @Test fun `single museum search remains bounded even with current cross museum permission`() = fixture("single") { unified, acquisition ->
        actorRole("reader", "CROSS", setOf(CollectionConst.CROSS_ORG))
        assertEquals(listOf("acq-museum"), search(unified, "公开名称").groups.single().items.map { it.id })
        assertEquals(1L, acquisition.findPage(MockHttpServletRequest(), "公开名称").total)
    }

    @Test fun `current disabled account and revoked read permission reject both search entry points`() = fixture { unified, acquisition ->
        UserDao["reader"].enabled=false
        assertThrows(BadRequestException::class.java) { search(unified, "公开名称") }
        assertThrows(BadRequestException::class.java) { acquisition.findPage(MockHttpServletRequest(), "公开名称") }
        UserDao["reader"].enabled=true
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { search(unified, "公开名称") }
        assertThrows(BadRequestException::class.java) { acquisition.findPage(MockHttpServletRequest(), "公开名称") }
    }

    @Test fun `image presence filtering requires confidential permission for either boolean value`() = fixture { _, acquisition ->
        for (value in listOf("true", "false")) {
            val request=MockHttpServletRequest().apply { addParameter("hasImage", value) }
            assertThrows(BadRequestException::class.java) { acquisition.findPage(request) }
        }
        actorRole("reader", "ACQUIRER", setOf(CollectionConst.ACQUIRE))
        assertEquals(1L, acquisition.findPage(MockHttpServletRequest().apply { addParameter("hasImage", "true") }).total)
        assertEquals(0L, acquisition.findPage(MockHttpServletRequest().apply { addParameter("hasImage", "false") }).total)
    }

    @Test fun `detail and linked titles use the same current museum boundary`() = fixture { unified, acquisition ->
        AcquisitionPlanDao.new("other-plan") { orgId="other"; title="外馆计划"; year=2026 }
        AcquisitionDao["acq-museum"].apply { collectionId="collection-other"; planId="other-plan" }
        val detail=acquisition.get("acq-museum")!!
        assertTrue(detail.collectionName == null); assertTrue(detail.collectionTotalRegNo == null); assertTrue(detail.planTitle == null)
        assertTrue(search(unified,"公开名称-museum").groups.single().items.single().relatedCollections.isEmpty())
        assertEquals(0L, search(unified,"REG-other").total)
        assertEquals(0L, acquisition.findPage(MockHttpServletRequest(),"REG-other").total)
        assertThrows(BadRequestException::class.java) { acquisition.get("acq-other") }
        UserDao["reader"].groupId="other"
        assertThrows(BadRequestException::class.java) { acquisition.get("acq-museum") }
        assertTrue(acquisition.get("acq-other") != null)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { acquisition.get("acq-other") }
    }

    @Test fun `moved acquisition shows only voucher bound original registration in detail and page`() = fixture { unified, acquisition ->
        actorRole("reader", "CROSS", setOf(CollectionConst.CROSS_ORG))
        val collection=CollectionDao["collection-museum"]
        collection.entryVoucherNo="RJ-museum"
        val original=collection.toModel()
        EntryVoucherDao.new("voucher-museum") {
            orgId="museum";voucherNo="RJ-museum";acquisitionIds="acq-museum";voucherStatus=EnumVoucherStatus.REGISTERED
        }
        val snapshot=CollectionRetainedLedgerSnapshot(original,
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,"transfer-museum","other"))
        val archiveService=CollectionPermanentArchiveService(mock(BusinessAttachmentBinder::class.java),CollectionScopeResolver("multi",""))
        val retained=CollectionRetainedLedgerDao.new("retained-museum") {
            orgId="museum";collectionId=collection.id.value;transferId="transfer-museum"
            totalRegNo=collection.totalRegNo;name=collection.name;targetOrgId="other";archiveId="";this.snapshot=snapshot
        }
        retained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED",snapshot,null)
        val laterSnapshot=snapshot.copy(
            collection=original.copy(entryVoucherNo="RJ-return",name="再次调出前的名称"),
            identity=CollectionRetainedLedgerIdentity("museum",collection.id.value,"transfer-again","other"))
        val laterRetained=CollectionRetainedLedgerDao.new("retained-museum-again") {
            orgId="museum";collectionId=collection.id.value;transferId="transfer-again"
            totalRegNo=collection.totalRegNo;name="再次调出前的名称";targetOrgId="other";archiveId="";this.snapshot=laterSnapshot
        }
        laterRetained.archiveId=archiveService.seal("museum","COLLECTION_RETAINED_LEDGER",laterRetained.id.value,"TRANSFERRED",laterSnapshot,null)
        collection.orgId="other";collection.name="接收馆当前名称";collection.totalRegNo="TARGET-001"
        val detail=acquisition.get("acq-museum")!!
        assertEquals(original.name,detail.collectionName)
        assertEquals(original.totalRegNo,detail.collectionTotalRegNo)
        @Suppress("UNCHECKED_CAST")
        val page=acquisition.findPage(MockHttpServletRequest(),"公开名称-museum").list as List<AcquisitionVo>
        assertEquals(original.name,page.single().collectionName)
        assertEquals(original.totalRegNo,page.single().collectionTotalRegNo)
        assertEquals(1L,acquisition.findPage(MockHttpServletRequest(),"REG-museum").total)
        assertEquals(0L,acquisition.findPage(MockHttpServletRequest(),"TARGET-001").total)
        assertEquals(1L,acquisition.findPage(MockHttpServletRequest(),"关联藏品-museum").total)
        assertEquals(0L,acquisition.findPage(MockHttpServletRequest(),"再次调出前的名称").total)
        assertEquals(0L,acquisition.findPage(MockHttpServletRequest(),"接收馆当前名称").total)
        assertEquals(0L,search(unified,"TARGET-001").total)
        assertTrue(search(unified,"公开名称-museum").groups.single().items.single().relatedCollections.isEmpty())
        EntryVoucherDao["voucher-museum"].acquisitionIds="acq-other"
        assertEquals(null,acquisition.get("acq-museum")!!.collectionName)
        EntryVoucherDao["voucher-museum"].acquisitionIds="acq-museum"
        retained.snapshot=snapshot.copy(collection=original.copy(name="被篡改的原馆名称"))
        assertThrows(BadRequestException::class.java) { acquisition.get("acq-museum") }
    }

    @Test fun `cross museum permission expands reads but grants no confidential duty`() = fixture { unified, acquisition ->
        actorRole("reader", "CROSS", setOf(CollectionConst.CROSS_ORG))
        assertEquals(2L, search(unified,"公开名称").total)
        assertEquals(0L, search(unified,"秘密估价").total)
        assertEquals(2L, acquisition.findPage(MockHttpServletRequest(),"公开名称").total)
        assertTrue(acquisition.get("acq-other")!!.reason == null)
        actorRole("reader", CollectionConst.ROLE_DIRECTOR)
        assertEquals(2L, search(unified,"秘密估价").total)
        assertEquals("秘密估价-other",acquisition.get("acq-other")!!.reason)
        RolePermissions.deleteWhere { RolePermissions.permissionId eq CollectionConst.CROSS_ORG }
        assertEquals(1L, search(unified,"秘密估价").total)
        assertThrows(BadRequestException::class.java) { acquisition.get("acq-other") }
    }

    @Test fun `disabled museum and department cannot retain ordinary search access`() = fixture { unified, acquisition ->
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertEquals(0L, search(unified,"公开名称").total)
        assertEquals(0L, acquisition.findPage(MockHttpServletRequest(),"公开名称").total)
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.ENABLED
        GroupDao["museum"].enabled=false
        assertEquals(0L, search(unified,"公开名称").total)
        assertEquals(0L, acquisition.findPage(MockHttpServletRequest(),"公开名称").total)
    }

    @Test fun `single museum configuration does not grant a moved person access to the default museum`() = fixture("single") { unified, acquisition ->
        UserDao["reader"].groupId="other"
        assertEquals(0L, search(unified,"公开名称").total)
        assertEquals(0L, acquisition.findPage(MockHttpServletRequest(),"公开名称").total)
        assertThrows(BadRequestException::class.java) { acquisition.get("acq-museum") }
    }

    @Test fun `all groups summary and paginated results apply confidentiality before counting`() = fixture { unified, acquisition ->
        SchemaUtils.create(AnnualReportArchives, AnnualReportEvidenceRecords, CollectionAttachments, CollectionBusinessItems, CollectionCatalogs,
            CollectionCategories, CollectionLedgerLogs, CollectionMediaFolders, CollectionMedias, CollectionPrintTemplates,
            CollectionReturns, CollectionSets, CollectionTransfers, Deregistrations, Exhibitions, Inventories,
            InventoryBatches, Loans, Reproductions, Requisitions, StorageMovements, WarehouseEntryApplications, WarehouseLogs, Warehouses)
        AcquisitionDao.new("second") { orgId="museum"; name="公开名称-second"; source=EnumSource.PURCHASE; reason="秘密估价-second" }
        val hidden=unified.search(CollectionUnifiedSearchQueryVo(keyword="秘密估价"))
        assertEquals(5, hidden.groups.size)
        assertEquals(0L, hidden.total)
        val summary=unified.search(CollectionUnifiedSearchQueryVo(keyword="公开名称"))
        assertEquals(2L, summary.total)
        assertTrue(summary.groups.flatMap { it.items }.all { !it.summary.orEmpty().contains("秘密") })
        val first=unified.search(CollectionUnifiedSearchQueryVo(keyword="公开名称",group="ACQUISITION",pageNum=1,pageSize=1)).groups.single()
        val second=unified.search(CollectionUnifiedSearchQueryVo(keyword="公开名称",group="ACQUISITION",pageNum=2,pageSize=1)).groups.single()
        assertEquals(2L,first.total);assertEquals(2L,second.total)
        assertEquals(2,(first.items+second.items).map { it.id }.toSet().size)
        val all=acquisition.findPage(MockHttpServletRequest().apply { addParameter("pageSize","0") },"公开名称")
        assertEquals(2L,all.total)
        @Suppress("UNCHECKED_CAST") val rows=all.list as List<AcquisitionVo>
        assertEquals(2,rows.size);assertTrue(rows.all { it.reason==null && it.images==null })
    }

    @Test fun `annual report versions are searchable only for the museum whose annual page can open them`() = fixture { unified, _ ->
        SchemaUtils.create(AnnualReportArchives, AnnualReportEvidenceRecords, CollectionAttachments, CollectionBusinessItems,
            CollectionCatalogs, CollectionCategories, CollectionLedgerLogs, CollectionMediaFolders, CollectionMedias,
            CollectionPrintTemplates, CollectionReturns, CollectionSets, CollectionTransfers, Deregistrations, Exhibitions,
            Inventories, InventoryBatches, Loans, Reproductions, Requisitions, StorageMovements, WarehouseEntryApplications,
            WarehouseLogs, Warehouses)
        val signedAt = LocalDateTime.of(2026, 1, 10, 10, 0)
        fun add(org: String, version: Int) {
            AnnualReportEvidenceDao.new("annual-$org-$version") {
                orgId=org;year=2025;this.version=version
                signed=AnnualReportSignedVo(2025,"stat","keeper","leader",signedAt,"signed",AnnualDeclaredFiguresVo(0,0,0,0,0,0,changesDescription="无变动"))
                calculated=AnnualLedgerPreviewVo(org,2025,signedAt,true)
                if(version==2) {
                    submission=AnnualReportSubmissionVo("主管部门","报-2025",signedAt.plusDays(1),"submitted")
                    filing=AnnualReportFilingVo("主管部门","备-2025",signedAt.plusDays(2),"filed")
                }
            }
        }
        add("museum",1);add("museum",2);add("other",1)
        val first=unified.search(CollectionUnifiedSearchQueryVo(keyword="2025 年报",group="STORAGE",pageNum=1,pageSize=1)).groups.single()
        val second=unified.search(CollectionUnifiedSearchQueryVo(keyword="2025",group="STORAGE",pageNum=2,pageSize=1)).groups.single()
        assertEquals(2L,first.total)
        assertEquals(2L,second.total)
        assertEquals(setOf("annual-museum-1","annual-museum-2"),(first.items+second.items).map { it.id }.toSet())
        assertTrue((first.items+second.items).all { it.targetYear==2025 && it.route=="/collection/annual-report" })
        assertTrue((first.items+second.items).any { it.subtitle=="已备案" && it.summary=="报-2025 · 备-2025" })
        fun annual(keyword: String) = unified.search(CollectionUnifiedSearchQueryVo(keyword=keyword,group="STORAGE")).groups.single()
        assertEquals(listOf("annual-museum-2"),annual("报-2025").items.map { it.id })
        assertEquals(listOf("报送编号"),annual("报-2025").items.single().matchedFields)
        assertEquals(listOf("annual-museum-2"),annual("备-2025").items.map { it.id })
        assertEquals(listOf("备案编号"),annual("备-2025").items.single().matchedFields)
        assertEquals(listOf("annual-museum-2"),annual("备案").items.map { it.id })
        assertEquals(listOf("annual-museum-2"),annual("报送").items.map { it.id })
        assertEquals(0L,annual("REG-2025").total)
        UserDao["reader"].groupId="other"
        val moved=unified.search(CollectionUnifiedSearchQueryVo(keyword="2025",group="STORAGE")).groups.single()
        assertEquals(listOf("annual-other-1"),moved.items.map { it.id })
        assertEquals(0L,annual("报-2025").total)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { unified.search(CollectionUnifiedSearchQueryVo(keyword="2025",group="STORAGE")) }
    }
}
