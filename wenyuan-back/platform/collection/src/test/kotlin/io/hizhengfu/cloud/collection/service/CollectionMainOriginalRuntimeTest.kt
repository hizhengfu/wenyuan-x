package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.common.web.FileViewController
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.api.FileFrontController
import io.hizhengfu.cloud.sys.file.api.FilePreviewController
import io.hizhengfu.cloud.sys.file.domain.ParamsFile
import io.hizhengfu.cloud.sys.file.service.*
import io.hizhengfu.cloud.sys.file.service.storage.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionMainOriginalRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableCaching class Transactions
    private val at = LocalDateTime.of(2026, 1, 2, 12, 0)
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, root: Path) {
        val archives = c.getBean(CollectionPermanentArchiveService::class.java)
        val disclosures = c.getBean(CollectionDisclosureService::class.java)
        val access = c.getBean(FileReadAccess::class.java)
        val controller = FileViewController(c, false, root.toString(), false, "unused", c.getBean(FileObjectStorageService::class.java), access)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun seal(biz: String, org: String = "museum", id: String = "case", file: String = "proof", phase: String = "RECORDED"): String {
            val previous=SecurityContextHolder.getContext().authentication
            val user=SecurityUser(id="archivist",name="归档人员",username="archivist",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
            try { return archives.seal(org,biz,id,phase,mapOf("fact" to id),file) }
            finally { SecurityContextHolder.getContext().authentication=previous }
        }
        fun permission(vararg values: String) = db {
            RolePermissions.deleteAll()
            actorRole("reader", "FILE_reader", values.toSet())
        }
        fun allow(path: String = "proof.txt") {
            val response = MockHttpServletResponse()
            val request = MockHttpServletRequest("GET", "/userfiles/$path")
            request.addHeader("If-Modified-Since", "Tue, 01 Sep 2026 10:00:00 GMT")
            controller.downloadFile(request, response)
            assertEquals("CONTENT:$path", response.contentAsString)
            assertEquals("private, no-store", response.getHeader("Cache-Control"))
        }
        fun deny(path: String = "proof.txt") {
            for (original in listOf(false, true)) {
                val response = MockHttpServletResponse()
                val request = MockHttpServletRequest("GET", "/userfiles/${if (original) "original/" else ""}$path")
                request.addHeader("Range", "bytes=0-3"); request.addHeader("If-Modified-Since", "Tue, 01 Sep 2026 10:00:00 GMT")
                assertThrows(BadRequestException::class.java) { if (original) controller.downloadOriginalFile(request, response) else controller.downloadFile(request, response) }
                assertEquals(0, response.contentAsByteArray.size); assertNotEquals(304, response.status)
            }
        }
    }
    private fun login(id: String = "reader", cachedAdmin: Boolean = true, permissions: List<String>? = null) {
        val user = SecurityUser(id=id, name=id, username=id, password="", groupId="museum", groupIds=setOf("museum"), postIds=emptySet(), dataType=0,
            roles=if(cachedAdmin) setOf(EnumRole.ADMIN.value) else emptySet(), roleCodes=if(cachedAdmin) setOf(CollectionConst.ROLE_DIRECTOR) else emptySet(), authorities=(permissions ?: if(cachedAdmin) listOf("ADMIN",CollectionConst.CROSS_ORG) else listOf(CollectionConst.SELECT)).map(::SimpleGrantedAuthority), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    private fun fixture(realAccession: Boolean = false, body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("main-original.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("localStorage", LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator", org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver("single", "museum"))
            c.beanFactory.registerSingleton("users", mock(CollectionResponsibleUserService::class.java))
            c.beanFactory.registerSingleton("paperLedger", mock(PaperLedgerService::class.java).also {
                org.mockito.Mockito.`when`(it.requirePageReference("museum","paper-book",1)).thenReturn(PaperLedgerService.PageReference("paper-book","总账第一册",1,20))
            })
            c.beanFactory.registerSingleton("grading", mock(CollectionGradingService::class.java))
            if (realAccession) c.register(AccessionReceiptService::class.java)
            else c.beanFactory.registerSingleton("accession", mock(AccessionReceiptService::class.java))
            c.beanFactory.registerSingleton("archivePackets", mock(CollectionArchivePacketService::class.java))
            c.beanFactory.registerSingleton("calendar", mock(CollectionCalendarService::class.java))
            c.beanFactory.registerSingleton("printService", mock(CollectionPrintService::class.java))
            c.beanFactory.registerSingleton("auxiliary", mock(CollectionAuxiliaryService::class.java))
            c.register(RequisitionService::class.java, RequisitionStatutoryService::class.java, CollectionReturnService::class.java,
                StorageMovementService::class.java,
                WarehouseLogService::class.java, WarehouseDiaryService::class.java, EntryVoucherService::class.java,
                AnnualReportEvidenceService::class.java, CollectionLedgerStatisticsService::class.java,
                AcquisitionComplianceService::class.java, AcquisitionService::class.java, AppraisalService::class.java,
                ReturnStatutoryService::class.java, CollectionUserResolver::class.java, DeregistrationService::class.java,
                DeregistrationStatutoryService::class.java, WarehouseEntryApplicationService::class.java,
                WarehouseEntryStatutoryService::class.java, WarehouseStaffingService::class.java, WarehouseStaffingHandoverService::class.java)
            c.register(ExhibitionService::class.java, CollectionPlacementService::class.java, ReproductionService::class.java,
                ReproductionComplianceService::class.java, DigitalAcquisitionService::class.java, CollectionOriginalAccessService::class.java)
            c.register(InventoryService::class.java, InventoryBatchService::class.java, InventoryComplianceService::class.java,
                CollectionChangeService::class.java, LoanService::class.java, LoanStatutoryService::class.java,
                CollectionTransferService::class.java, CollectionTransferStatutoryService::class.java, CollectionBusinessItemService::class.java)
            c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier { FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java), mock(FileStorageConfigReader::class.java), mock(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java)) })
            c.registerBean(FileService::class.java, java.util.function.Supplier { FileService(dir.toString(), FileFolderService(), c.getBean(FileObjectStorageService::class.java), listOf(CollectionArchiveFileRetentionGuard(), c.getBean(CollectionBusinessOriginalFileRetentionGuard::class.java)), c.getBean(FileReadAccess::class.java)) })
            c.beanFactory.registerSingleton("flowInstances", mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java))
            c.register(CollectionMainOriginalFileSource::class.java, CollectionMainFileReadPolicy::class.java, WarehouseService::class.java, CollectionLocationReferenceGuard::class.java, AcquisitionPlanService::class.java, CollectionBusinessOriginalFileSource::class.java, CollectionBusinessOriginalFiles::class.java, CollectionOriginalIdentityReadPolicy::class.java, CollectionBusinessOriginalFileReadGuard::class.java, CollectionBusinessOriginalFileRetentionGuard::class.java)
            c.register(CollectionFileService::class.java, CollectionService::class.java, CollectionCatalogService::class.java,
                CollectionOperationLockService::class.java, CollectionRegistrationNumberService::class.java,
                CollectionRetainedLedgerService::class.java, CollectionOriginalHistoryService::class.java)
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java, FileReadAccess::class.java,
                BusinessAttachmentBinder::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                CollectionBusinessArchiveReadPolicy::class.java, CollectionPersonnelArchiveReadPolicy::class.java, CollectionTransferArchiveReadPolicy::class.java,
                CollectionDisclosureArchiveReadPolicy::class.java, CollectionDisclosureService::class.java, StaffEducationAccess::class.java, CollectionStaffEducationService::class.java,
                AcquisitionOriginalFiles::class.java, AcquisitionFileReadGuard::class.java, DigitalAcquisitionFileReadGuard::class.java,
                CollectionEmergencyFileReadGuard::class.java, CollectionStaffEducationFileReadGuard::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager), dir)
            r.db {
                originalReaderSchema()
                if (realAccession) SchemaUtils.create(AccessionReceipts)
                SchemaUtils.create(*CollectionBusinessOriginalFileSource().tables.map { it.table }.distinct().toTypedArray())
                SchemaUtils.create(IncidentRelocations, CollectionMedias, CollectionAttachments, CollectionRetainedLedgers, AnnualReportEvidenceRecords)
                SchemaUtils.create(CollectionMediaFolders, CollectionOperationLocks, CollectionRegistrationNumbers,
                    CollectionLedgerLogs, CollectionLedgerStatEvents, CollectionCatalogs)
                SchemaUtils.create(CollectionAdministrativeRecords, LoanComplianceRecords, CollectionBusinessItems, CollectionChangeApprovals,
                    CollectionIncidents, CollectionIncidentRecords, CollectionIncidentItems, InventoryAnnualReports,
                    CollectionPlacements, ReproductionPlans, DigitalAcquisitionPlans,
                    io.hizhengfu.cloud.flow.dao.FlowInstanceLogs, StorageMovements, ReturnComplianceRecords)
                SchemaUtils.create(CollectionOrgs, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, FileUsers,
                    Acquisitions, AcquisitionComplianceCases, AcquisitionExpertOpinions, DigitalAcquisitionFileAccesses, CollectionEmergencyFileAccesses,
                    CollectionStaffEducations, CollectionStaffEducationVersions, CollectionStaffEducationFileAccesses, CollectionTransfers, CollectionTransferTerminations, CollectionDisclosureReviews, CollectionDisclosureEvents,
                    Collections, CollectionCatalogVersions, Deregistrations, DeregistrationExpertOpinions, DeregistrationNotices, CollectionTransferNotices)
                listOf("museum", "other").forEach { id -> CollectionOrgDao.new(id) { name=id; code=id; systemGroupId=id;ownership=EnumOwnership.STATE } }
                originalReader("reader", CollectionConst.SELECT)
                originalReader("archivist", "ADMIN")
                Roles.insert { it[Roles.id]=EnumRole.ADMIN.value; it[code]="ADMIN"; it[name]="管理员" }
                RoleUsers.insert { it[userId]="archivist"; it[roleId]=EnumRole.ADMIN.value }
                for (id in listOf("proof", "public", "review", "replacement")) {
                    FileDao.new(id) { name="$id.txt"; path="$id.txt"; hash=id; ext="txt"; size=32; secure=false }
                    FileUsers.insert { it[fileId]=id; it[userId]="reader"; it[uploadDate]=at }
                    java.nio.file.Files.writeString(dir.resolve("$id.txt"), "CONTENT:$id.txt")
                }
                val collection = CollectionDao.new("one") { orgId="museum"; name="铜器"; totalRegNo="A1"; registeredAt=at.minusDays(10); catalogStatus=EnumCatalogStatus.APPROVED; lifeStatus=EnumCollectionStatus.IN_STORE }
                CollectionCatalogVersionDao.new("v1") { orgId="museum"; catalogId="catalog"; collectionId="one"; version=1; catalogStatus=EnumCatalogStatus.APPROVED; content=CollectionCatalogMapper.fromCollection(collection.toModel()); auditTime=at.minusDays(1) }
            }
            login()
            try { body(r) } finally { SecurityContextHolder.clearContext() }
        }
    }

    private fun signedInventory(r: Runtime): String {
        r.permission(CollectionConst.SELECT, CollectionConst.STORAGE)
        val service = r.c.getBean(InventoryService::class.java)
        val id = service.save(InventoryVo(batchName="原清点", collectionId="one", result=EnumInventoryResult.MATCH, note="原观察记录"))
        val batch = r.db { InventoryDao[id].batchId!! }
        val compliance = r.c.getBean(InventoryComplianceService::class.java)
        compliance.begin(batch, InventoryPlanVo(2026, EnumInventoryMethod.FULL, EnumInventoryPurpose.ROUTINE,
            "全馆账物卡照清点", "keeper", "leader", "proof"))
        compliance.check(batch, InventoryItemCheckVo("one", at, setOf("reader"), "实际现场位置",
            true, true, true, true, "保存稳定", false, "proof", "proof", "proof", "proof"))
        compliance.complete(batch, InventoryCompletionVo(at.plusDays(2), "keeper", "leader", "review"))
        return id
    }

    @ParameterizedTest @ValueSource(strings=["new-target", "open-target", "legacy-name", "cleared-completion"])
    fun `signed inventory observation cannot escape its original batch`(kind: String) = fixture { r ->
        val id = signedInventory(r)
        r.db {
            if (kind == "open-target") InventoryBatchDao.new("open") { orgId="museum"; batchName="补充清点" }
            if (kind == "legacy-name") InventoryDao[id].batchId=null
            if (kind == "cleared-completion") InventoryBatchDao[InventoryDao[id].batchId!!].completion=null
        }
        val before = r.db { Triple(InventoryBatchDao.count(), CollectionPermanentArchiveDao.count(), FileContents.selectAll().count()) }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(InventoryService::class.java).save(InventoryVo(id=id, batchName="补充清点", collectionId="one",
                result=EnumInventoryResult.MATCH, note="改写原观察", attachmentIds="replacement"))
        }
        r.db {
            assertEquals("原清点", InventoryDao[id].batchName)
            assertEquals("原观察记录", InventoryDao[id].note)
            org.junit.jupiter.api.Assertions.assertNull(InventoryDao[id].attachmentIds)
            assertEquals(before, Triple(InventoryBatchDao.count(), CollectionPermanentArchiveDao.count(), FileContents.selectAll().count()))
        }
    }

    @ParameterizedTest @ValueSource(strings=["missing-source", "foreign-source", "changed-org", "missing-legacy", "explicit-missing-id", "signed-target"])
    fun `inventory update rejects invalid origin and protected target without changing rows`(kind: String) = fixture { r ->
        r.permission(CollectionConst.SELECT, CollectionConst.STORAGE)
        val service = r.c.getBean(InventoryService::class.java)
        val signedId = if (kind == "signed-target") signedInventory(r) else null
        val id = service.save(InventoryVo(batchName="待核对", collectionId="one", result=EnumInventoryResult.MATCH, note="原观察"))
        r.db {
            val row = InventoryDao[id]
            when (kind) {
                "missing-source" -> InventoryBatchDao[row.batchId!!].delete()
                "foreign-source" -> InventoryBatchDao[row.batchId!!].orgId="other"
                "changed-org" -> CollectionDao.new("foreign") { orgId="other"; name="外馆藏品"; totalRegNo="FOREIGN" }
                "missing-legacy" -> { row.batchId=null; row.batchName="找不到的旧批次" }
                "explicit-missing-id" -> row.batchId="missing"
            }
        }
        val before = r.db { InventoryDao[id].toModel() to InventoryBatchDao.count() }
        assertThrows(BadRequestException::class.java) {
            service.save(InventoryVo(id=id, batchName=if(signedId != null) "原清点" else "补充清点",
                collectionId=if(kind == "changed-org") "foreign" else "one", result=EnumInventoryResult.MATCH, note="新观察"))
        }
        r.db { assertEquals(before, InventoryDao[id].toModel() to InventoryBatchDao.count()) }
    }

    @ParameterizedTest @ValueSource(strings=["new-target", "open-target", "legacy", "renamed-source"])
    fun `open inventory observations keep legitimate same museum draft corrections`(kind: String) = fixture { r ->
        r.permission(CollectionConst.SELECT, CollectionConst.STORAGE)
        val service = r.c.getBean(InventoryService::class.java)
        val id = service.save(InventoryVo(batchName="原草稿", collectionId="one", result=EnumInventoryResult.MATCH, note="原观察"))
        val oldBatch = r.db { InventoryDao[id].batchId!! }
        r.db {
            when (kind) {
                "open-target" -> InventoryBatchDao.new("target") { orgId="museum"; batchName="新草稿" }
                "legacy" -> InventoryDao[id].batchId=null
                "renamed-source" -> InventoryBatchDao[oldBatch].batchName="原批次现名"
            }
        }
        assertEquals(id, service.save(InventoryVo(id=id, batchName="新草稿", collectionId="one", result=EnumInventoryResult.MATCH, note="实际更正")))
        r.db {
            val row=InventoryDao[id]
            assertEquals("museum",row.orgId);assertEquals("新草稿",row.batchName);assertEquals("实际更正",row.note)
            assertNotEquals(oldBatch,row.batchId)
            assertEquals("新草稿",InventoryBatchDao[row.batchId!!].batchName)
            assertEquals(2L,InventoryBatchDao.count())
            if(kind == "open-target") assertEquals("target",row.batchId)
        }
    }

    private fun routineType(kind: String): String = when (kind) {
        "plan" -> CollectionConst.BIZ_ACQUISITION_PLAN
        "inventory" -> CollectionConst.BIZ_INVENTORY
        "batch" -> InventoryComplianceService.BIZ
        "exhibition" -> CollectionConst.BIZ_EXHIBITION
        else -> CollectionConst.BIZ_REPRODUCTION
    }
    private fun saveRoutine(r: Runtime, kind: String, id: String? = null, file: String = "proof"): String = when (kind) {
        "plan" -> r.c.getBean(AcquisitionPlanService::class.java).save(AcquisitionPlanVo(id=id,title="年度征集计划",year=2026,attachmentIds=file))
        "inventory" -> r.c.getBean(InventoryService::class.java).save(InventoryVo(id=id,batchName="清点草稿",collectionId="one",result=EnumInventoryResult.MATCH,attachmentIds=file))
        "batch" -> r.c.getBean(InventoryBatchService::class.java).save(InventoryBatchVo(id=id,batchName="清点方案",reportAttachmentIds=file))
        "exhibition" -> r.c.getBean(ExhibitionService::class.java).save(ExhibitionVo(id=id,title="展览方案",collectionIds="one",coverImage=file,attachmentIds=file))
        else -> r.c.getBean(ReproductionService::class.java).save(ReproductionVo(id=id,collectionId="one",repType=EnumRepType.COPY,images=file,purpose="实际复制方案"))
    }

    @ParameterizedTest @CsvSource("plan,proof", "plan,proof.txt", "plan,replacement", "inventory,proof", "inventory,proof.txt", "inventory,replacement",
        "batch,proof", "batch,proof.txt", "batch,replacement", "exhibition,proof", "exhibition,proof.txt", "exhibition,replacement",
        "reproduction,proof", "reproduction,proof.txt", "reproduction,replacement")
    fun `routine businesses retain legacy originals across uploader changes`(kind: String, file: String) = fixture { r ->
        businessEditor(r,routineType(kind))
        val id=saveRoutine(r,kind)
        r.db {
            CollectionPermanentArchiveFileDao.all().toList().forEach { it.delete() }
            CollectionPermanentArchiveDao.all().toList().forEach { it.delete() }
            FileContents.deleteAll()
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
        }
        r.allow()
        assertEquals(id,saveRoutine(r,kind,id,file))
        val archive = if(kind == "batch") {
            r.c.getBean(InventoryComplianceService::class.java).begin(id,InventoryPlanVo(2026,EnumInventoryMethod.FULL,
                EnumInventoryPurpose.ROUTINE,"全馆清点","keeper","leader","proof"))
            r.db { CollectionPermanentArchiveDao.all().single { it.bizType == InventoryComplianceService.BIZ && it.phase == "STARTED" }.id.value }
        } else r.archives.seal("museum",routineType(kind),id,"EVIDENCE_CHECK",mapOf("purpose" to "文件继承测试，不代表实际签署"),"proof")
        r.db {
            assertEquals(1L,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq archive) }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
            val originalIds=CollectionRetainedOriginalFiles.ids(routineType(kind),id,setOf("museum"))
            assertTrue("proof" in originalIds)
            if(file == "replacement") assertTrue("replacement" in originalIds)
            else assertEquals(1L,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq id) }.count())
        }
    }

    @ParameterizedTest @CsvSource("plan,new-file", "inventory,new-file", "batch,new-file", "exhibition,new-file", "reproduction,new-file",
        "plan,revoked", "inventory,revoked", "batch,revoked", "exhibition,revoked", "reproduction,revoked",
        "plan,other-case", "inventory,other-case", "batch,other-case", "exhibition,other-case", "reproduction,other-case")
    fun `routine original inheritance rejects new unowned files revocation and another case`(kind: String, change: String) = fixture { r ->
        businessEditor(r,routineType(kind))
        val id=saveRoutine(r,kind)
        r.db { FileUsers.deleteWhere { (fileId eq if(change == "new-file") "public" else "proof") and (userId eq "reader") } }
        val before=r.db { CollectionBusinessOriginalFileSource().references(routineType(kind),id) to CollectionPermanentArchiveDao.count() }
        if(change == "revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny() }
        assertThrows(BadRequestException::class.java) {
            if(change == "other-case") r.archives.seal("museum",routineType(kind),"unrelated","EVIDENCE_CHECK",mapOf("test" to true),"proof")
            else saveRoutine(r,kind,id,if(change == "new-file") "public" else "replacement")
        }
        r.db { assertEquals(before,CollectionBusinessOriginalFileSource().references(routineType(kind),id) to CollectionPermanentArchiveDao.count()) }
    }

    @ParameterizedTest @ValueSource(strings=["plan-cleared", "all-fields-cleared"])
    fun `inventory header cannot replace signed evidence after current fields are cleared`(kind: String) = fixture { r ->
        val observation=signedInventory(r)
        val id=r.db { InventoryDao[observation].batchId!! }
        r.db { InventoryBatchDao[id].apply {
            plan=null
            if(kind == "all-fields-cleared") { baseline=null;checks=null;issueActions=null;completion=null;finishedAt=null;reportAttachmentIds=null }
        } }
        val before=r.db { InventoryBatchDao[id].toModel() to CollectionPermanentArchiveDao.count() }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(InventoryBatchService::class.java).save(InventoryBatchVo(id=id,batchName="改写标题",reportAttachmentIds="replacement"))
        }
        r.db { assertEquals(before,InventoryBatchDao[id].toModel() to CollectionPermanentArchiveDao.count()) }
    }

    @Test fun `reproduction draft cannot switch its collection to another museum`() = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_REPRODUCTION)
        val id=saveRoutine(r,"reproduction")
        r.db { CollectionDao.new("foreign") { orgId="other";totalRegNo="FOREIGN";name="外馆藏品";lifeStatus=EnumCollectionStatus.IN_STORE } }
        val before=r.db { ReproductionDao[id].toModel() to CollectionPermanentArchiveDao.count() }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(ReproductionService::class.java).save(ReproductionVo(id=id,collectionId="foreign",repType=EnumRepType.COPY,images="proof"))
        }
        r.db { assertEquals(before,ReproductionDao[id].toModel() to CollectionPermanentArchiveDao.count()) }
    }

    private fun custodyType(kind: String): String = when(kind) {
        "requisition" -> CollectionConst.BIZ_REQUISITION
        "return" -> CollectionConst.BIZ_RETURN
        "exit" -> CollectionConst.BIZ_DEREGISTER
        else -> CollectionConst.BIZ_WAREHOUSE_ENTRY
    }
    private fun custodySetup(r: Runtime, kind: String) {
        r.db {
            CollectionDao["one"].nature=EnumCollectionNature.RELIC
            WarehouseDao.new("custody-store") { orgId="museum";name="保管库房";type=EnumWarehouseType.WAREHOUSE;sceneType=EnumLocationScene.STORAGE }
        }
        if(kind == "entry") {
            // 任职属于准备数据；只在准备期间授予维护权限，原件编辑用例仍使用自身权限。
            val setupRole=r.db { actorRole("reader","STAFFING_SETUP",setOf(CollectionConst.WAREHOUSE_MANAGE)) }
            try { r.c.getBean(WarehouseStaffingService::class.java).save("custody-store",
                WarehouseStaffingVo(setOf("reader","archivist"),"keeper","leader",at.minusDays(1),"review")) }
            finally { r.db { RoleUsers.deleteWhere { (userId eq "reader") and (roleId eq setupRole) } } }
        }
        if(kind == "return") {
            val source=saveCustody(r,"requisition",file="review")
            // 仅构造归还保存所需的既有来源；不表示此测试执行了真实审批与出库点交。
            r.db {
                RequisitionDao[source].apply {
                    flowStatus=EnumFlowStatus.PASS;outExecuted=true;outExecutedTime=at
                    handover=RequisitionHandoverRecordVo(RequisitionHandoverVo(at,at.minusHours(1),"keeper","review",
                        setOf("custodian"),setOf("reader"),listOf(RequisitionConditionVo("one","出库原始状态",false)),true,true,"review","review"),"source-task","reader",at)
                }
                CollectionDao["one"].lifeStatus=EnumCollectionStatus.IN_USE
            }
        }
    }
    private fun requisitionDossier(file: String) = RequisitionDossierVo("museum","department-head","keeper","leader","reader",
        "无损观察","课题比较","馆内工作室","专人值守",file,at,at.plusDays(2),transportRequired=false,transportDecisionReason="本馆同室提用，不需要包装运输")
    private fun saveCustody(r: Runtime, kind: String, id: String? = null, file: String = "proof"): String = when(kind) {
        "requisition" -> r.c.getBean(RequisitionService::class.java).save(RequisitionVo(id=id,collectionIds="one",purpose=EnumMoveReason.RESEARCH,
            basis="课题研究",department="研究部",operator="reader",toWarehouseId="custody-store",attachmentIds=file,dossier=requisitionDossier(file)))
        "return" -> r.c.getBean(CollectionReturnService::class.java).save(CollectionReturnVo(id=id,sourceBizType=CollectionConst.BIZ_REQUISITION,
            sourceBizId=r.db { RequisitionDao.all().single().id.value },collectionIds="one",operator="reader",toWarehouseId="custody-store",attachmentIds=file))
        "exit" -> r.c.getBean(DeregistrationService::class.java).save(DeregistrationVo(id=id,collectionId="one",reason=EnumDeregReason.DAMAGE_NO_VALUE,
            dossier=DeregistrationDossierVo(dispositionPlanFileIds=file),attachmentIds=file))
        else -> r.c.getBean(WarehouseEntryApplicationService::class.java).save(WarehouseEntryApplicationVo(id=id,warehouseIds="custody-store",entrantIds="reader,archivist",
            purpose="日常保管工作",plannedStart=at,plannedEnd=at.plusHours(1),attachmentIds=file,
            dossier=WarehouseEntryDossierVo(EnumWarehouseEntryKind.KEEPERS,keeperUserId="keeper",identityFileIds=file)))
    }
    private fun removeCustodyIdentity(r: Runtime, type: String, id: String) = r.db {
        val rows=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionBusinessOriginalFiles.BIZ) and (CollectionPermanentArchives.bizId eq id) }
            .filter { JsonUtils.mapper.readTree(it.snapshot).path("businessType").asString() == type }
        val archives=rows.map { it.id.value }
        CollectionPermanentArchiveFiles.deleteWhere { archiveId inList archives }
        FileContents.deleteWhere { contentId inList (archives+id) }
        rows.forEach { it.delete() }
        FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
    }

    @ParameterizedTest @CsvSource("requisition,proof", "requisition,proof.txt", "requisition,replacement", "return,proof", "return,proof.txt", "return,replacement",
        "exit,proof", "exit,proof.txt", "exit,replacement", "entry,proof", "entry,proof.txt", "entry,replacement")
    fun `custody drafts preserve legacy originals when the editor is not their uploader`(kind: String, file: String) = fixture { r ->
        businessEditor(r,custodyType(kind));custodySetup(r,kind)
        val id=saveCustody(r,kind)
        removeCustodyIdentity(r,custodyType(kind),id)
        r.allow()
        assertEquals(id,saveCustody(r,kind,id,file))
        val stage=r.archives.seal("museum",custodyType(kind),id,"EVIDENCE_CHECK",mapOf("purpose" to "文件关联测试，不代表实际交接或签署"),"proof")
        r.db {
            assertEquals(1L,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq stage) }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
            assertTrue("proof" in CollectionRetainedOriginalFiles.ids(custodyType(kind),id,setOf("museum")))
        }
    }

    @ParameterizedTest @ValueSource(strings=["requisition", "entry"])
    fun `legacy custody submission archives existing materials without an intervening save`(kind: String) = fixture { r ->
        businessEditor(r,custodyType(kind));custodySetup(r,kind)
        val id=saveCustody(r,kind)
        removeCustodyIdentity(r,custodyType(kind),id)
        r.db {
            // 仅执行实际提交材料钩子；外层使用实际事务，不模拟流程引擎已启动。
            val type = if(kind == "requisition") RequisitionService::class.java else WarehouseEntryApplicationService::class.java
            val entity = if(kind == "requisition") RequisitionDao[id] else WarehouseEntryApplicationDao[id]
            val method = type.getDeclaredMethod("beforeSubmit", if(kind == "requisition") RequisitionDao::class.java else WarehouseEntryApplicationDao::class.java)
            method.isAccessible=true
            try { method.invoke(r.c.getBean(type),entity) } catch(ex: java.lang.reflect.InvocationTargetException) { throw ex.targetException }
            val archive=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq custodyType(kind)) and
                (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "SUBMITTED") }.single()
            assertEquals(1L,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq archive.id.value) }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
    }
    @Test fun `exit draft cannot move a different museum object into the original case`() = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_DEREGISTER)
        custodySetup(r,"exit");val id=saveCustody(r,"exit")
        r.db { CollectionDao.new("foreign") { orgId="other";totalRegNo="FOREIGN";name="外馆文物";nature=EnumCollectionNature.RELIC;lifeStatus=EnumCollectionStatus.IN_STORE } }
        val before=r.db { DeregistrationDao[id].toModel() to CollectionPermanentArchiveDao.count() }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(DeregistrationService::class.java).save(DeregistrationVo(id=id,collectionId="foreign",reason=EnumDeregReason.DAMAGE_NO_VALUE,
                dossier=DeregistrationDossierVo(dispositionPlanFileIds="proof"),attachmentIds="proof"))
        }
        r.db { assertEquals(before,DeregistrationDao[id].toModel() to CollectionPermanentArchiveDao.count()) }
    }

    @ParameterizedTest @CsvSource("requisition,new-file", "return,new-file", "exit,new-file", "entry,new-file",
        "requisition,revoked", "return,revoked", "exit,revoked", "entry,revoked",
        "requisition,other-case", "return,other-case", "exit,other-case", "entry,other-case")
    fun `custody original inheritance rejects unowned replacement revoked access and other cases`(kind: String, change: String) = fixture { r ->
        businessEditor(r,custodyType(kind));custodySetup(r,kind)
        val id=saveCustody(r,kind)
        r.db { FileUsers.deleteWhere { (fileId eq if(change == "new-file") "public" else "proof") and (userId eq "reader") } }
        val before=r.db { CollectionBusinessOriginalFileSource().references(custodyType(kind),id) to CollectionPermanentArchiveDao.count() }
        if(change == "revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny() }
        assertThrows(BadRequestException::class.java) {
            if(change == "other-case") r.archives.seal("museum",custodyType(kind),"unrelated","EVIDENCE_CHECK",mapOf("test" to true),"proof")
            else saveCustody(r,kind,id,if(change == "new-file") "public" else "replacement")
        }
        r.db { assertEquals(before,CollectionBusinessOriginalFileSource().references(custodyType(kind),id) to CollectionPermanentArchiveDao.count()) }
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `automatic return draft can carry its actual source originals across uploaders`(legacy: Boolean) = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_RETURN);custodySetup(r,"return")
        val source=r.db { RequisitionDao.all().single().id.value }
        if(legacy) removeCustodyIdentity(r,CollectionConst.BIZ_REQUISITION,source)
        r.db { FileUsers.deleteWhere { (fileId eq "review") and (userId eq "reader") } }
        r.allow("review.txt")
        val id=r.c.getBean(CollectionReturnService::class.java).ensureDraftFromRequisition(source)
        r.db {
            assertEquals(source,CollectionReturnDao[id].sourceBizId)
            assertEquals("review",CollectionReturnDao[id].attachmentIds)
            assertTrue("review" in CollectionRetainedOriginalFiles.ids(CollectionConst.BIZ_RETURN,id,setOf("museum")))
            assertEquals(1L,FileContents.selectAll().where { (FileContents.fileId eq "review") and (FileContents.contentId eq id) }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "review") and (FileUsers.userId eq "reader") }.count())
        }
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `loan return carries only its existing source originals across uploaders`(legacy: Boolean) = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_RETURN)
        val source=r.db {
            val collection=CollectionDao["one"]
            val baseline=LoanBaselineVo(EnumOwnership.STATE,listOf(RequisitionCollectionBaselineVo("one",CollectionCatalogMapper.fromCollection(collection.toModel()))))
            val dossier=LoanDossierVo(EnumLoanBorrowerKind.STATE_MUSEUM,"登记号","review",EnumLoanPurpose.EXHIBITION,"公益展览",
                "keeper","leader","supervisor",at,at.plusDays(2),at.minusDays(2),"review",setOf("甲","乙"),at.minusDays(1),"review","review",
                "实际安全责任","包装运输要求","实际储存要求","展厅","承运单位","review","运输监督人","本馆","review",at.toLocalDate(),1,"review")
            val loan=LoanDao.new("loan-source") {
                orgId="museum";collectionIds="one";borrower="借用馆";attachmentIds="review";operator="reader"
                flowStatus=EnumFlowStatus.PASS;outExecuted=true;outExecutedTime=at;this.dossier=dossier;this.baseline=baseline
                handover=LoanHandoverRecordVo(LoanHandoverVo(at.minusHours(1),"keeper","review",at,setOf("k1","k2"),setOf("甲","乙"),"review",true,
                    listOf(RequisitionConditionVo("one","原件状态",false)),true,true,"review","review"),"source-task","reader",at)
            }
            collection.lifeStatus=EnumCollectionStatus.ON_LOAN
            loan.id.value
        }
        if(!legacy) r.c.getBean(CollectionBusinessOriginalFiles::class.java).rememberExistingBusiness(CollectionConst.BIZ_LOAN,source)
        r.db { FileUsers.deleteWhere { (fileId eq "review") and (userId eq "reader") } }
        val id=r.c.getBean(CollectionReturnService::class.java).ensureDraftFromLoan(source)
        r.db {
            assertEquals(source,CollectionReturnDao[id].sourceBizId)
            assertTrue("review" in CollectionRetainedOriginalFiles.ids(CollectionConst.BIZ_RETURN,id,setOf("museum")))
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "review") and (FileUsers.userId eq "reader") }.count())
        }
    }

    @ParameterizedTest @ValueSource(strings=["source-org", "not-out", "missing-baseline", "missing-item", "snapshot", "source-digest", "new-file", "source-type"])
    fun `return source inheritance requires the actual matching outbound record and valid originals`(change: String) = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_RETURN);custodySetup(r,"return")
        val source=r.db { RequisitionDao.all().single().id.value }
        val id=r.c.getBean(CollectionReturnService::class.java).ensureDraftFromRequisition(source)
        removeCustodyIdentity(r,CollectionConst.BIZ_RETURN,id)
        r.db {
            FileUsers.deleteWhere { userId eq "reader" }
            val parent=RequisitionDao[source];val child=CollectionReturnDao[id]
            when(change) {
                "source-org" -> parent.orgId="other"
                "not-out" -> parent.outExecuted=false
                "missing-baseline" -> parent.baseline=null
                "missing-item" -> parent.handover=parent.handover!!.copy(content=parent.handover!!.content.copy(items=emptyList()))
                "snapshot" -> child.sourceSnapshot=child.sourceSnapshot!!.copy(outAt=at.plusHours(1))
                "source-digest" -> CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionBusinessOriginalFiles.BIZ) and (CollectionPermanentArchives.bizId eq source) }.first().digest="invalid"
                "source-type" -> child.sourceBizType="UNKNOWN"
            }
        }
        val before=r.db { CollectionPermanentArchiveDao.count() to FileContents.selectAll().count() }
        assertThrows(BadRequestException::class.java) {
            r.archives.seal("museum",CollectionConst.BIZ_RETURN,id,"SOURCE_EVIDENCE_CHECK",mapOf("test" to true),if(change == "new-file") "public" else "review")
        }
        r.db { assertEquals(before,CollectionPermanentArchiveDao.count() to FileContents.selectAll().count()) }
    }

    /** 注入审批日志以验证实际服务回调和执行；不代表真实人员审批与签署。 */
    private fun recordBiz(kind: String) = when(kind) { "diary" -> WarehouseDiaryService.BIZ; "voucher" -> AccessionReceiptService.BIZ; else -> CollectionConst.BIZ_APPRAISAL }
    private fun setupRecord(r: Runtime,kind: String) {
        businessEditor(r,recordBiz(kind))
        if(kind == "diary") custodySetup(r,"entry")
        if(kind == "voucher") r.db { AcquisitionDao.new("accepted-source") { orgId="museum";name="核准征集对象";flowStatus=EnumFlowStatus.PASS } }
    }
    private fun saveRecord(r: Runtime,kind: String,id: String? = null,file: String = "proof",source: String? = null,signature: String = "review"): String = when(kind) {
        "diary" -> r.c.getBean(WarehouseLogService::class.java).save(WarehouseLogVo(id=id,warehouseId="custody-store",logDate=at.toLocalDate(),content="逐件及环境巡查",
            attachmentIds=file,entries=WarehouseDiaryEntriesVo(),diary=WarehouseDiaryVo(at,setOf("reader"),true,"动态已核对","安防正常",
                listOf(WarehouseEnvironmentReadingVo(at,22.0,50.0,"现场仪表",signature)),false,signedDiaryFileIds=signature,replacementReason=if(id==null)null else "核实补充")))
        "voucher" -> r.c.getBean(EntryVoucherService::class.java).save(EntryVoucherVo(id=id,acquisitionIds=source ?: "accepted-source",attachmentIds=file,basis="核准征集清单"))
        else -> r.c.getBean(AppraisalService::class.java).save(AppraisalVo(id=id,collectionId=source ?: "one",attachmentIds=file,resultName="鉴定对象",
            members=listOf(AppraisalMemberVo(memberName="鉴定成员",organization="外部机构",opinion="核对材料",signatureFileId=signature))))
    }
    private fun recordState(r: Runtime,kind: String,id: String): List<Any?> = r.db {
        val value=when(kind) { "diary" -> WarehouseLogDao[id].toModel(); "voucher" -> EntryVoucherDao[id].toModel(); else -> AppraisalDao[id].toModel() }
        listOf(value,AppraisalMemberDao.all().map { it.toModel() },CollectionPermanentArchiveDao.all().map { it.id.value to it.digest }.toSet(),
            FileContents.selectAll().map { it[FileContents.contentId] to it[FileContents.fileId] }.toSet())
    }
    private fun removeRecordOrigins(r: Runtime,kind: String,id: String,keepSignedDiary: Boolean = false) {
        removeCustodyIdentity(r,recordBiz(kind),id)
        r.db {
            CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq recordBiz(kind)) and (CollectionPermanentArchives.bizId eq id) }.toList().filterNot { keepSignedDiary && kind == "diary" && it.phase.startsWith("RECORDED_") }.forEach { archive ->
                CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value };FileContents.deleteWhere { contentId eq archive.id.value };archive.delete()
            }
            FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") }
        }
    }

    @ParameterizedTest @CsvSource("diary,proof","diary,proof.txt","diary,replacement","voucher,proof","voucher,proof.txt","voucher,replacement","appraisal,proof","appraisal,proof.txt","appraisal,replacement")
    fun `daily voucher and appraisal records retain existing originals across uploaders`(kind: String,file: String) = fixture { r ->
        setupRecord(r,kind);val id=saveRecord(r,kind);removeRecordOrigins(r,kind,id,keepSignedDiary=true)
        assertEquals(id,saveRecord(r,kind,id,file))
        r.db {
            assertTrue("proof" in CollectionRetainedOriginalFiles.ids(recordBiz(kind),id,setOf("museum")))
            if(kind != "voucher") assertTrue("review" in CollectionRetainedOriginalFiles.ids(recordBiz(kind),id,setOf("museum")))
            val expected=if(file == "replacement")"replacement" else "proof"
            assertTrue(FileContents.selectAll().where { (FileContents.fileId eq expected) and (FileContents.contentId eq id) }.any())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId inList listOf("proof","review")) and (FileUsers.userId eq "reader") }.count())
        }
    }

    @Test fun `signed diary missing its original rejects amendment and rolls back reconstructed file references`() = fixture { r ->
        setupRecord(r,"diary");val id=saveRecord(r,"diary");removeRecordOrigins(r,"diary",id)
        val before=recordState(r,"diary",id)
        val failure=assertThrows(BadRequestException::class.java) { saveRecord(r,"diary",id,"replacement") }
        assertTrue(failure.message.orEmpty().contains("缺少已签署日记原件"))
        assertEquals(before,recordState(r,"diary",id))
    }

    @ParameterizedTest @ValueSource(strings=["voucher","appraisal"])
    fun `existing intake and appraisal records cannot adopt another museum source`(kind: String) = fixture { r ->
        setupRecord(r,kind);val id=saveRecord(r,kind)
        val permissions=listOf(CollectionConst.SELECT,if(kind=="appraisal")CollectionConst.APPRAISE else CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
        r.db {
            AcquisitionDao.new("foreign-source") { orgId="other";name="外馆征集";flowStatus=EnumFlowStatus.PASS }
            CollectionDao.new("foreign") { orgId="other";name="外馆藏品";totalRegNo="OTHER-1";lifeStatus=EnumCollectionStatus.IN_STORE }
        }
        val before=recordState(r,kind,id)
        assertThrows(BadRequestException::class.java) { saveRecord(r,kind,id,source=if(kind == "voucher")"foreign-source" else "foreign") }
        assertEquals(before,recordState(r,kind,id))
    }

    @ParameterizedTest @CsvSource("diary,new-file","voucher,new-file","appraisal,new-file",
        "diary,revoked","voucher,revoked","appraisal,revoked",
        "diary,other-case","voucher,other-case","appraisal,other-case")
    fun `record originals reject unowned replacements revoked access and unrelated records`(kind: String,change: String) = fixture { r ->
        setupRecord(r,kind);val id=saveRecord(r,kind)
        r.db { FileUsers.deleteWhere { (fileId eq if(change == "new-file") "public" else "proof") and (userId eq "reader") } }
        val before=recordState(r,kind,id)
        if(change == "revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny() }
        assertThrows(BadRequestException::class.java) {
            if(change == "other-case") r.archives.seal("museum",recordBiz(kind),"unrelated","EVIDENCE_CHECK",mapOf("test" to true),"proof")
            else saveRecord(r,kind,id,if(change == "new-file") "public" else "replacement")
        }
        assertEquals(before,recordState(r,kind,id))
    }

    @ParameterizedTest @ValueSource(strings=["new-signature","foreign-member"])
    fun `appraisal signature inheritance requires its own qualified member source`(change: String) = fixture { r ->
        setupRecord(r,"appraisal");val id=saveRecord(r,"appraisal")
        removeRecordOrigins(r,"appraisal",id)
        r.db {
            FileUsers.deleteWhere { (fileId eq "public") and (userId eq "reader") }
            if(change == "foreign-member") AppraisalMemberDao.all().single().orgId="other"
        }
        val before=recordState(r,"appraisal",id)
        assertThrows(BadRequestException::class.java) { saveRecord(r,"appraisal",id,signature=if(change == "new-signature") "public" else "review") }
        assertEquals(before,recordState(r,"appraisal",id))
    }

    @ParameterizedTest @ValueSource(strings=["allowed", "registrar", "ordinary-create"])
    fun `accession uses actual receipt photo without granting confidential source or upload ownership`(scenario: String) = fixture(realAccession = true) { r ->
        acquisitionEditor(r)
        val id = saveAcquisition(r)
        val compliance = r.c.getBean(AcquisitionComplianceService::class.java)
        compliance.saveDossier(id, acquisitionDossier())
        (1..3).forEach { compliance.recordExpert(id, acquisitionExpert(it)) }
        compliance.complete(id, acquisitionCompletion())
        r.db {
            AcquisitionDao[id].flowStatus = EnumFlowStatus.PASS
            WarehouseDao.new("accession-store") { orgId="museum"; name="核收库房"; sceneType=EnumLocationScene.STORAGE }
        }
        val vouchers = r.c.getBean(EntryVoucherService::class.java)
        val voucher = vouchers.save(EntryVoucherVo(acquisitionIds=id, verifiedAcquisitionIds=id,
            basis="审批通过的征集原件", entryDate=at.toLocalDate().plusDays(6),
            handoverUser="移交人", receiverUser="保管员", custodian="保管员",
            warehouseId="accession-store", attachmentIds="review"))
        r.c.getBean(AccessionReceiptService::class.java).record(voucher, AccessionReceiptVo(
            id, at.plusDays(6), "accession-store", setOf("reader"), setOf("移交人"),
            listOf(AccessionPieceVo("piece-1", at.plusDays(5), "实物编号", "完好", true, true, "public",actualQuantity=1,actualQuantityUnit="件")), true, "review"))
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","public")) and (userId eq "reader") } }
        val permissions = listOf(CollectionConst.SELECT, CollectionConst.LEDGER_CREATE) +
            if (scenario == "registrar") emptyList() else listOf(CollectionConst.ACQUIRE)
        r.permission(*permissions.toTypedArray()); login(cachedAdmin=false, permissions=permissions)
        if (scenario == "ordinary-create") {
            val before = r.db { listOf(CollectionDao.count(), CollectionRegistrationNumberDao.count(), CollectionLedgerLogDao.count(), CollectionPermanentArchiveDao.count(), FileContents.selectAll().count()) }
            assertThrows(BadRequestException::class.java) {
                r.c.getBean(CollectionService::class.java).create(
                    CollectionVo(name="不能冒用的封面", coverImage="proof", warehouseId="accession-store"))
            }
            r.db {
                assertEquals(before, listOf(CollectionDao.count(), CollectionRegistrationNumberDao.count(), CollectionLedgerLogDao.count(), CollectionPermanentArchiveDao.count(), FileContents.selectAll().count()))
                org.junit.jupiter.api.Assertions.assertNull(AcquisitionDao[id].collectionId)
                assertEquals(EnumVoucherStatus.DRAFT, EntryVoucherDao[voucher].voucherStatus)
            }
            return@fixture
        }
        vouchers.execute(voucher)
        r.db {
            val collectionId = AcquisitionDao[id].collectionId!!
            assertEquals("public", CollectionDao[collectionId].coverImage)
            assertEquals(1L, FileContents.selectAll().where { (FileContents.contentId eq collectionId) and (FileContents.fileId eq "public") }.count())
            assertEquals(0L, FileUsers.selectAll().where { (FileUsers.userId eq "reader") and (FileUsers.fileId eq "proof") }.count())
            org.junit.jupiter.api.Assertions.assertNull(CollectionDao[collectionId].registeredAt)
        }
        if (scenario == "registrar") {
            r.db { assertThrows(BadRequestException::class.java) { r.access.requireReadable("proof.txt","LOCAL") } }
            assertThrows(BadRequestException::class.java) { compliance.detail(id) }
            val printed=r.db { CollectionPrintService(r.c.getBean(CollectionService::class.java),r.c.getBean(CollectionScopeResolver::class.java),
                mock(CollectionPrintTemplateService::class.java),mock(LoanStatutoryService::class.java),
                receiptEvidence=AccessionReceiptEvidence(r.archives,r.c.getBean(CollectionMainFileReadPolicy::class.java))).entryVoucherPrint(voucher) }
            val resolver=CollectionPrintAttachmentResolver(r.c.getBean(FileObjectStorageService::class.java),r.access,r.c.getBean(CollectionActorDirectory::class.java))
            val docx=r.db { CollectionPrintDocxRenderer.renderToBytes(printed,attachmentResolver=resolver::resolve) }
            assertTrue(docx.isNotEmpty())
            val pdf=java.io.ByteArrayOutputStream()
            com.aspose.words.Document(java.io.ByteArrayInputStream(docx)).save(pdf,com.aspose.words.SaveFormat.PDF)
            assertTrue(pdf.toByteArray().toString(Charsets.ISO_8859_1).startsWith("%PDF"))
        }
    }

    private fun acquisitionEditor(r: Runtime) {
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.ACQUIRE)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
    }
    private fun saveAcquisition(r: Runtime,id: String?=null,file: String="proof",plan: String?=null) =
        r.c.getBean(AcquisitionService::class.java).save(AcquisitionVo(id=id,name="陶器征集",source=EnumSource.PURCHASE,images=file,planId=plan))
    private fun acquisitionState(r: Runtime,id: String): List<Any?> = r.db {
        listOf(AcquisitionDao[id].toModel(),AcquisitionComplianceCaseDao.all().map { it.dossier to it.completion },
            AcquisitionExpertOpinionDao.all().map { it.content },CollectionPermanentArchiveDao.all().map { it.id.value to it.digest }.toSet(),
            FileContents.selectAll().map { it[FileContents.contentId] to it[FileContents.fileId] }.toSet())
    }
    private fun removeAcquisitionOrigins(r: Runtime,id: String,legacy: Boolean=true) = r.db {
        if(legacy) CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_ACQUISITION) and (CollectionPermanentArchives.bizId eq id) }.toList().forEach { archive ->
            CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value };FileContents.deleteWhere { contentId eq archive.id.value };archive.delete()
        }
        FileContents.deleteWhere { contentId eq id }
        FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
    }
    private fun acquisitionDossier()=AcquisitionDossierVo(EnumCollectionNature.RELIC,at.toLocalDate(),"proof","proof","proof",true,"proof",setOf("seller"),"proof")
    private fun acquisitionExpert(index: Int,file: String="proof")=AcquisitionExpertOpinionVo(expertKey="expert-$index",expertName="专家$index",organization="文博机构$index",professionalTitle="研究馆员",specialty="陶器",qualificationFileIds=file,independenceFileIds=file,authenticityOpinion="真品",valueOpinion="具有研究价值",provenanceOpinion="来源流传清楚",proposedPriceCents=10000L*index,agree=true,signedOpinionFileIds=file,assessedOn=at.toLocalDate().plusDays(1))
    private fun acquisitionCompletion()=AcquisitionCompletionVo(decidedOn=at.toLocalDate().plusDays(2),collectiveDecisionFileIds="proof",agreementFileIds="proof",agreedOn=at.toLocalDate().plusDays(4),actualAcquiredOn=at.toLocalDate().plusDays(5),handoverReceiptFileIds="proof",publicDisclosurePlan="公开征集管理使用情况，估价材料保密",comparablePriceFileIds="proof",suggestedPriceCents=20000,valuationReason="专家均价及同类成交价",highValueAssessmentRequired=false,highValueDeterminationFileIds="proof",negotiatedPriceCents=19000,negotiatedOn=at.toLocalDate().plusDays(3),negotiationFileIds="proof",paymentFileIds="proof")

    @ParameterizedTest @ValueSource(strings=["proof","proof.txt","replacement"])
    fun `acquisition draft retains actual originals across uploaders`(file: String) = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);removeAcquisitionOrigins(r,id)
        assertEquals(id,saveAcquisition(r,id,file))
        r.allow("proof.txt")
        r.db { assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count()) }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `acquisition dossier expert agreement and later archives inherit their own originals`(legacy: Boolean) = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);val service=r.c.getBean(AcquisitionComplianceService::class.java)
        service.saveDossier(id,acquisitionDossier());removeAcquisitionOrigins(r,id,legacy)
        service.saveDossier(id,acquisitionDossier())
        (1..3).forEach { service.recordExpert(id,acquisitionExpert(it)) }
        service.complete(id,acquisitionCompletion())
        r.db { service.sealPhase(AcquisitionDao[id],"SUBMITTED") }
        r.allow()
        r.db {
            assertEquals(3L,AcquisitionExpertOpinionDao.count());assertTrue(service.case(id)?.completion != null)
            assertTrue(CollectionPermanentArchiveDao.all().any { it.bizId==id && it.phase=="SUBMITTED" })
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `acquisition cannot use a plan from another museum even with cross organization access`(existing: Boolean) = fixture { r ->
        acquisitionEditor(r);val id=if(existing)saveAcquisition(r) else null
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
        r.db { AcquisitionPlanDao.new("foreign-plan") { orgId="other";title="外馆征集计划";year=2026 } }
        val before=r.db { AcquisitionDao.count() to CollectionPermanentArchiveDao.count() }
        assertThrows(BadRequestException::class.java) { saveAcquisition(r,id,plan="foreign-plan") }
        r.db { assertEquals(before,AcquisitionDao.count() to CollectionPermanentArchiveDao.count()) }
    }

    @ParameterizedTest @CsvSource("draft,new-file","dossier,new-file","expert,new-file",
        "draft,revoked","dossier,revoked","expert,revoked",
        "draft,other-case","dossier,other-case","expert,other-case",
        "complete,new-file","complete,revoked","complete,other-case")
    fun `acquisition inheritance rejects new unowned files revocation and unrelated business`(entry: String,change: String) = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);val service=r.c.getBean(AcquisitionComplianceService::class.java)
        if(entry!="draft")service.saveDossier(id,acquisitionDossier())
        if(entry=="complete")(1..3).forEach { service.recordExpert(id,acquisitionExpert(it)) }
        r.db { FileUsers.deleteWhere { (fileId eq if(change=="new-file")"public" else "proof") and (userId eq "reader") } }
        if(change=="revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny() }
        val before=acquisitionState(r,id)
        assertThrows(BadRequestException::class.java) {
            if(change=="other-case") r.archives.seal("museum",CollectionConst.BIZ_ACQUISITION,"unrelated","PROPOSAL",mapOf("test" to true),"proof")
            else when(entry) {
                "draft" -> saveAcquisition(r,id,"public")
                "dossier" -> service.saveDossier(id,acquisitionDossier().copy(standardsFileIds="public"))
                "complete" -> service.complete(id,acquisitionCompletion().copy(agreementFileIds="public"))
                else -> service.recordExpert(id,acquisitionExpert(1,"public"))
            }
        }
        assertEquals(before,acquisitionState(r,id))
    }
    @ParameterizedTest @ValueSource(strings=["case","expert"])
    fun `acquisition existing source rejects child records from another museum`(child: String) = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);val service=r.c.getBean(AcquisitionComplianceService::class.java)
        service.saveDossier(id,acquisitionDossier())
        if(child=="expert")service.recordExpert(id,acquisitionExpert(1))
        removeAcquisitionOrigins(r,id)
        r.db { if(child=="case") AcquisitionComplianceCaseDao.all().single().orgId="other" else AcquisitionExpertOpinionDao.all().single().orgId="other" }
        val before=acquisitionState(r,id)
        assertThrows(BadRequestException::class.java) { service.rememberExistingOriginals(id) }
        assertEquals(before,acquisitionState(r,id))
    }
    @ParameterizedTest @ValueSource(strings=["bytes","digest"])
    fun `acquisition original integrity failure rolls back subsequent edits`(change: String) = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);removeAcquisitionOrigins(r,id,false)
        if(change=="bytes")java.nio.file.Files.writeString(dir.resolve("proof.txt"),"changed original")
        else r.db { CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single().digest="broken" }
        val before=acquisitionState(r,id)
        assertThrows(BadRequestException::class.java) { saveAcquisition(r,id,"replacement") }
        assertEquals(before,acquisitionState(r,id))
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `acquisition retains same museum plan assignment for new and existing drafts`(existing: Boolean) = fixture { r ->
        acquisitionEditor(r);val id=if(existing)saveAcquisition(r) else null
        r.db { AcquisitionPlanDao.new("own-plan") { orgId="museum";title="本馆征集计划";year=2026 } }
        val saved=saveAcquisition(r,id,plan="own-plan")
        r.db { assertEquals("own-plan",AcquisitionDao[saved].planId);assertEquals("museum",AcquisitionDao[saved].orgId) }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `acquisition source stays frozen after expert opinions or agreement`(completed: Boolean) = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);val service=r.c.getBean(AcquisitionComplianceService::class.java)
        service.saveDossier(id,acquisitionDossier());(1..3).forEach { service.recordExpert(id,acquisitionExpert(it)) }
        if(completed)service.complete(id,acquisitionCompletion())
        removeAcquisitionOrigins(r,id,false)
        val before=acquisitionState(r,id)
        assertThrows(BadRequestException::class.java) { saveAcquisition(r,id,"replacement") }
        assertThrows(BadRequestException::class.java) { service.saveDossier(id,acquisitionDossier()) }
        assertEquals(before,acquisitionState(r,id))
    }

    @Test fun `acquisition agreement accepts independently uploaded new material with retained originals`() = fixture { r ->
        acquisitionEditor(r);val id=saveAcquisition(r);val service=r.c.getBean(AcquisitionComplianceService::class.java)
        service.saveDossier(id,acquisitionDossier());(1..3).forEach { service.recordExpert(id,acquisitionExpert(it)) }
        removeAcquisitionOrigins(r,id,false)
        service.complete(id,acquisitionCompletion().copy(agreementFileIds="replacement"))
        r.db { assertEquals("replacement",service.case(id)?.completion?.agreementFileIds) }
        r.allow();r.allow("replacement.txt")
    }

    private fun staffingEditor(r: Runtime) {
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.WAREHOUSE_MANAGE)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
    }
    @ParameterizedTest @CsvSource("legacy,proof", "legacy,proof.txt", "legacy,replacement", "legacy,clear",
        "retained,proof", "retained,proof.txt", "retained,replacement", "retained,clear")
    fun `warehouse editor retains its own thumbnail without uploader identity`(origin: String, file: String) = fixture { r ->
        staffingEditor(r)
        val service=r.c.getBean(WarehouseService::class.java)
        val id=service.create(WarehouseVo(name="原库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        if(origin=="legacy")removeCustodyIdentity(r,CollectionBusinessOriginalFileSource.WAREHOUSE,id)
        else r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        service.update(id,WarehouseVo(name="修正库房名称",thumbnail=file.takeUnless { it=="clear" }))
        r.db {
            assertEquals("修正库房名称",WarehouseDao[id].name)
            assertEquals(file.takeUnless { it=="clear" },WarehouseDao[id].thumbnail)
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
            assertTrue(CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.any { JsonUtils.mapper.readTree(it.snapshot).path("fileId").asString()=="proof" })
        }
        r.allow()
        if(file=="replacement")r.allow("replacement.txt")
        r.permission();r.deny()
    }

    @Test fun `warehouse removal preserves an unarchived original from another uploader`() = fixture { r ->
        r.db { SchemaUtils.create(*io.hizhengfu.cloud.collection.install.CollectionInstall("single").initTables().toTypedArray()) }
        staffingEditor(r)
        val service=r.c.getBean(WarehouseService::class.java)
        val id=service.create(WarehouseVo(name="未使用位置",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        removeCustodyIdentity(r,CollectionBusinessOriginalFileSource.WAREHOUSE,id)
        service.delete(id)
        r.db { org.junit.jupiter.api.Assertions.assertNull(WarehouseDao.findById(id));assertTrue(CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.any()) }
        r.allow();r.permission();r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["revoked","foreign-org","digest","snapshot","bytes","metadata","metadata-id","metadata-path","metadata-storage","metadata-array","missing-file","new-file","other-warehouse","other-business"])
    fun `warehouse thumbnail edit rejects invalid provenance and rolls back all changes`(failure: String) = fixture { r ->
        staffingEditor(r)
        val service=r.c.getBean(WarehouseService::class.java)
        val id=service.create(WarehouseVo(name="原库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        if(failure=="other-warehouse")service.create(WarehouseVo(name="另一库房",thumbnail="public",type=EnumWarehouseType.WAREHOUSE))
        if(failure=="other-business")r.seal(CollectionConst.BIZ_CHANGE,id="different",file="public")
        r.db {
            FileUsers.deleteWhere { (fileId inList listOf("proof","public")) and (userId eq "reader") }
            val archive=CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single()
            when(failure) {
                "foreign-org" -> WarehouseDao[id].orgId="other"
                "digest" -> archive.digest="broken"
                "snapshot" -> archive.snapshot="{}"
                "metadata" -> CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.single().metadata="{}"
                "metadata-id", "metadata-path", "metadata-storage", "metadata-array" -> {
                    val file=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.single()
                    val legacy=JsonUtils.mapper.readTree(JsonUtils.toJson(FileDao["proof"].toModel())) as tools.jackson.databind.node.ObjectNode
                    when(failure) {
                        "metadata-id" -> legacy.put("id","another-file")
                        "metadata-path" -> legacy.remove("path")
                        "metadata-storage" -> legacy.put("storageType","UNKNOWN")
                    }
                    file.metadata=if(failure=="metadata-array")"[]" else legacy.toString()
                }
                "missing-file" -> FileDao["proof"].delete()
            }
        }
        if(failure=="revoked")r.permission()
        if(failure=="bytes")java.nio.file.Files.writeString(dir.resolve("proof.txt"),"MODIFIED ORIGINAL")
        val before=r.db { WarehouseDao[id].toModel() } to archiveState(r)
        val candidate=if(failure in setOf("new-file","other-warehouse","other-business"))"public" else "replacement"
        assertThrows(BadRequestException::class.java) { service.update(id,WarehouseVo(name="不应保存",thumbnail=candidate)) }
        assertEquals(before,r.db { WarehouseDao[id].toModel() } to archiveState(r))
        r.db { assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count()) }
    }

    @ParameterizedTest @ValueSource(strings=["new","borrowed"])
    fun `warehouse creation cannot adopt an unuploaded file through inheritance`(origin: String) = fixture { r ->
        staffingEditor(r)
        val service=r.c.getBean(WarehouseService::class.java)
        if(origin=="borrowed")service.create(WarehouseVo(name="原库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val before=staffingAnnualState(r) to archiveState(r)
        assertThrows(BadRequestException::class.java) { service.create(WarehouseVo(name="新库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE)) }
        assertEquals(before,staffingAnnualState(r) to archiveState(r))
    }

    @ParameterizedTest @ValueSource(strings=["revoked","missing-bytes","foreign-org"])
    fun `warehouse removal checks original access before retaining and deleting`(failure: String) = fixture { r ->
        r.db { SchemaUtils.create(*io.hizhengfu.cloud.collection.install.CollectionInstall("single").initTables().toTypedArray()) }
        staffingEditor(r)
        val service=r.c.getBean(WarehouseService::class.java)
        val id=service.create(WarehouseVo(name="未使用库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        removeCustodyIdentity(r,CollectionBusinessOriginalFileSource.WAREHOUSE,id)
        if(failure=="revoked")r.permission()
        // 无旧身份时只能核验现存文件；缺失文件不能伪造为已留存原件。
        if(failure=="missing-bytes")java.nio.file.Files.delete(dir.resolve("proof.txt"))
        if(failure=="foreign-org")r.db { WarehouseDao[id].orgId="other" }
        val before=staffingAnnualState(r) to archiveState(r)
        assertThrows(BadRequestException::class.java) { service.delete(id) }
        assertEquals(before,staffingAnnualState(r) to archiveState(r))
    }

    @ParameterizedTest @ValueSource(strings=["delete","type","scene"])
    fun `warehouse referenced by a collection rejects destructive edits before changing originals`(operation: String) = fixture { r ->
        staffingEditor(r)
        val service=r.c.getBean(WarehouseService::class.java)
        val id=service.create(WarehouseVo(name="原库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        r.db { CollectionDao["one"].warehouseId=id }
        val before=staffingAnnualState(r) to archiveState(r)
        assertThrows(BadRequestException::class.java) {
            when(operation) {
                "delete" -> service.delete(id)
                "type" -> service.update(id,WarehouseVo(name="不应保存",thumbnail="replacement",type=EnumWarehouseType.AREA))
                else -> service.update(id,WarehouseVo(name="不应保存",thumbnail="replacement",type=EnumWarehouseType.EXHIBITION_HALL,sceneType=EnumLocationScene.EXHIBITION))
            }
        }
        assertEquals(before,staffingAnnualState(r) to archiveState(r))
        r.db { assertEquals(id,CollectionDao["one"].warehouseId) }
    }

    private fun annualEditor(r: Runtime) {
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.STAT)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
    }
    private fun annualSigned()=AnnualReportSignedVo(2025,"reader","keeper","leader",at,"proof",
        AnnualDeclaredFiguresVo(0,1,0,0,0,1,listOf(AnnualDeclaredUnitVo("件",0,1,0,0,0,0,1)),"新增登记1件"))
    private fun annualSubmission(file: String="proof")=AnnualReportSubmissionVo("主管文物局","报2025-1",at.plusDays(1),file)
    private fun annualFiling(file: String="proof")=AnnualReportFilingVo("主管文物局","备2025-1",at.plusDays(2),file)
    private fun setupAnnual(r: Runtime): String {
        annualEditor(r)
        r.db { CollectionLedgerStatRecorder.record(CollectionDao["one"],EnumLedgerStatEvent.REGISTER,"initial",at.minusDays(10)) }
        return r.c.getBean(AnnualReportEvidenceService::class.java).signed(annualSigned(), "museum")
    }

    @ParameterizedTest @ValueSource(strings=["signed","submission","filing"])
    fun `annual unified search rejects a stage whose business fields diverge from frozen originals`(phase: String) = fixture { r ->
        val id=setupAnnual(r)
        val reports=r.c.getBean(AnnualReportEvidenceService::class.java)
        reports.submission(id,annualSubmission());reports.filing(id,annualFiling())
        val search=CollectionUnifiedSearchService(r.c.getBean(CollectionScopeResolver::class.java),reports)
        val query=CollectionUnifiedSearchQueryVo(keyword="2025",group="STORAGE")
        r.db { assertEquals(listOf(id),search.search(query).groups.single().items.filter { it.type=="ANNUAL_REPORT" }.map { it.id }) }
        r.db {
            val row=AnnualReportEvidenceDao[id]
            when(phase) {
                "signed" -> row.signed=row.signed.copy(signedReportFileIds="replacement")
                "submission" -> row.submission=row.submission!!.copy(reportNo="伪造报送号")
                else -> row.filing=row.filing!!.copy(filingNo="伪造备案号")
            }
        }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) { r.db { search.search(query) } }
        assertEquals(before,staffingAnnualState(r))
    }
    private fun staffingAnnualState(r: Runtime): List<Any?> = r.db {
        listOf(WarehouseDao.all().map { it.toModel() },AnnualReportEvidenceDao.all().map { listOf(it.id.value,it.signed,it.submission,it.filing) },
            CollectionPermanentArchiveDao.all().map { it.id.value to it.digest }.toSet(),FileContents.selectAll().map { it[FileContents.contentId] to it[FileContents.fileId] }.toSet())
    }

    @ParameterizedTest @ValueSource(strings=["review","review.txt","replacement"])
    fun `staffing correction can retain its own archived assignment original`(file: String) = fixture { r ->
        staffingEditor(r);custodySetup(r,"entry")
        val previous=r.db { WarehouseDao["custody-store"].staffing!! }
        r.db { FileUsers.deleteWhere { (fileId eq "review") and (userId eq "reader") } }
        val service=r.c.getBean(WarehouseStaffingService::class.java)
        service.save("custody-store",previous.copy(signedAssignmentFileIds=file,replacementReason="任职原件录入订正"))
        r.db { assertEquals(2,CollectionPermanentArchiveDao.all().count { it.bizType==WarehouseStaffingService.BIZ });assertEquals(previous.keeperUserIds,WarehouseDao["custody-store"].staffing!!.keeperUserIds) }
        r.allow("review.txt")
    }
    @ParameterizedTest @ValueSource(strings=["submission","filing"])
    fun `annual reporting phases inherit their own archived originals`(entry: String) = fixture { r ->
        val id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        if(entry=="filing")service.submission(id,annualSubmission())
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        if(entry=="submission")service.submission(id,annualSubmission()) else service.filing(id,annualFiling())
        r.allow()
        r.db { assertTrue(AnnualReportEvidenceDao[id].submission!=null);if(entry=="filing")assertTrue(AnnualReportEvidenceDao[id].filing!=null) }
    }
    @ParameterizedTest @ValueSource(strings=["staffing","signed","submission","filing"])
    fun `repeated staffing and annual reporting requests still verify original bytes`(entry: String) = fixture { r ->
        var id="custody-store"
        if(entry=="staffing") { staffingEditor(r);custodySetup(r,"entry") }
        else {
            id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
            if(entry in setOf("submission","filing"))service.submission(id,annualSubmission())
            if(entry=="filing")service.filing(id,annualFiling())
        }
        val old=r.db { WarehouseDao.findById(id)?.staffing }
        java.nio.file.Files.writeString(dir.resolve(if(entry=="staffing")"review.txt" else "proof.txt"),"damaged original")
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) {
            when(entry) {
                "staffing" -> r.c.getBean(WarehouseStaffingService::class.java).save(id,old!!)
                "signed" -> r.c.getBean(AnnualReportEvidenceService::class.java).signed(annualSigned(), "museum")
                "submission" -> r.c.getBean(AnnualReportEvidenceService::class.java).submission(id,annualSubmission())
                else -> r.c.getBean(AnnualReportEvidenceService::class.java).filing(id,annualFiling())
            }
        }
        assertEquals(before,staffingAnnualState(r))
    }
    @ParameterizedTest @ValueSource(strings=["missing","mismatch","missing-reference"])
    fun `staffing update cannot erase an unresolved previous assignment`(change: String) = fixture { r ->
        staffingEditor(r);custodySetup(r,"entry")
        val old=r.db { WarehouseDao["custody-store"].staffing!! }
        r.db {
            val archive=CollectionPermanentArchiveDao.all().single { it.bizType==WarehouseStaffingService.BIZ }
            when(change) {
                "missing" -> { CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value };FileContents.deleteWhere { contentId eq archive.id.value };archive.delete() }
                "missing-reference" -> CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value }
                else -> WarehouseDao["custody-store"].staffing=old.copy(keeperUserIds=setOf("unrecorded"))
            }
        }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) { r.c.getBean(WarehouseStaffingService::class.java).save("custody-store",old.copy(keeperUserIds=setOf("reader"),signedAssignmentFileIds="replacement",replacementReason="人员实际变动")) }
        assertEquals(before,staffingAnnualState(r))
    }

    @ParameterizedTest @CsvSource("staffing,new-file","submission,new-file","filing,new-file",
        "staffing,revoked","submission,revoked","filing,revoked",
        "staffing,other-case","submission,other-case","filing,other-case")
    fun `staffing and annual original inheritance preserves upload and current access boundaries`(entry: String,change: String) = fixture { r ->
        var id="custody-store"
        if(entry=="staffing") { staffingEditor(r);custodySetup(r,"entry") }
        else { id=setupAnnual(r);if(entry=="filing")r.c.getBean(AnnualReportEvidenceService::class.java).submission(id,annualSubmission()) }
        val old=r.db { WarehouseDao.findById(id)?.staffing }
        val original=if(entry=="staffing")"review" else "proof"
        r.db { FileUsers.deleteWhere { (fileId eq if(change=="new-file")"public" else original) and (userId eq "reader") } }
        if(change=="revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny(if(entry=="staffing")"review.txt" else "proof.txt") }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) {
            if(change=="other-case")r.archives.seal("museum",if(entry=="staffing")WarehouseStaffingService.BIZ else AnnualReportEvidenceService.BIZ,"unrelated","EVIDENCE_CHECK",mapOf("test" to true),original)
            else when(entry) {
                "staffing" -> r.c.getBean(WarehouseStaffingService::class.java).save(id,old!!.copy(signedAssignmentFileIds="public",replacementReason="核对任职原件"))
                "submission" -> r.c.getBean(AnnualReportEvidenceService::class.java).submission(id,annualSubmission("public"))
                else -> r.c.getBean(AnnualReportEvidenceService::class.java).filing(id,annualFiling("public"))
            }
        }
        assertEquals(before,staffingAnnualState(r))
    }
    @ParameterizedTest @ValueSource(strings=["signed","submission","filing"])
    fun `annual repeat cannot report success after its phase references disappear`(entry: String) = fixture { r ->
        val id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        if(entry in setOf("submission","filing"))service.submission(id,annualSubmission())
        if(entry=="filing")service.filing(id,annualFiling())
        r.db {
            val phase=when(entry){"signed"->"SIGNED";"submission"->"SUBMITTED";else->"FILED"}
            val archive=CollectionPermanentArchiveDao.all().single { it.bizType==AnnualReportEvidenceService.BIZ && it.bizId==id && it.phase==phase }
            CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value }
        }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) {
            when(entry){"signed"->service.signed(annualSigned(), "museum");"submission"->service.submission(id,annualSubmission());else->service.filing(id,annualFiling())}
        }
        assertEquals(before,staffingAnnualState(r))
    }
    @ParameterizedTest @ValueSource(strings=["SIGNED","SUBMITTED","FILED"])
    fun `annual detail rejects missing original references in every phase`(phase: String) = fixture { r ->
        val id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        service.submission(id,annualSubmission());service.filing(id,annualFiling())
        val valid=service.detail(2025)["records"] as List<*>
        assertEquals(1,valid.size)
        r.db {
            val archive=CollectionPermanentArchiveDao.all().single { it.bizType==AnnualReportEvidenceService.BIZ && it.bizId==id && it.phase==phase }
            CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value }
        }
        assertThrows(BadRequestException::class.java) { service.detail(2025) }
    }
    @ParameterizedTest @ValueSource(strings=["SIGNED","SUBMITTED","FILED"])
    fun `annual detail rejects business fields that differ from frozen stages`(phase: String) = fixture { r ->
        val id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        service.submission(id,annualSubmission());service.filing(id,annualFiling())
        r.db {
            val row=AnnualReportEvidenceDao[id]
            when(phase) {
                "SIGNED" -> row.signed=row.signed.copy(signedReportFileIds="replacement")
                "SUBMITTED" -> row.submission=row.submission!!.copy(reportNo="伪造报送号")
                else -> row.filing=row.filing!!.copy(filingNo="伪造备案号")
            }
        }
        assertThrows(BadRequestException::class.java) { service.detail(2025) }
    }
    @Test fun `annual correction cannot supersede a report with missing signed evidence`() = fixture { r ->
        val id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        r.db {
            val archive=CollectionPermanentArchiveDao.all().single { it.bizType==AnnualReportEvidenceService.BIZ && it.bizId==id && it.phase=="SIGNED" }
            CollectionPermanentArchiveFiles.deleteWhere { archiveId eq archive.id.value }
        }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) {
            service.signed(annualSigned().copy(signedAt=at.plusDays(3),replacementReason="实际订正",replacementFileIds="replacement"), "museum")
        }
        assertEquals(before,staffingAnnualState(r))
    }
    @Test fun `new annual report version does not borrow the previous versions upload qualification`() = fixture { r ->
        setupAnnual(r)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) { r.c.getBean(AnnualReportEvidenceService::class.java).signed(annualSigned().copy(signedAt=at.plusDays(3),replacementReason="实际订正",replacementFileIds="replacement"), "museum") }
        assertEquals(before,staffingAnnualState(r))
    }
    @Test fun `valid staffing removal keeps pending handover and removes former keeper authorization`() = fixture { r ->
        staffingEditor(r);custodySetup(r,"entry")
        val service=r.c.getBean(WarehouseStaffingService::class.java)
        val old=r.db { WarehouseDao["custody-store"].staffing!! }
        r.db { FileUsers.deleteWhere { (fileId eq "review") and (userId eq "reader") } }
        service.save("custody-store",old.copy(keeperUserIds=setOf("reader"),assignedAt=at,signedAssignmentFileIds="replacement",replacementReason="实际离任和接任"))
        val detail=r.c.getBean(WarehouseStaffingHandoverService::class.java).get("custody-store")
        val items=(detail["items"] as List<*>).filterIsInstance<WarehouseStaffingHandoverItem>()
        assertEquals(1,items.size);assertEquals("archivist",items.single().leavingUserId);assertEquals("PENDING",items.single().status)
        assertThrows(BadRequestException::class.java) { service.requireKeeper("archivist","museum",setOf("custody-store")) }
        r.db { assertEquals(setOf("reader"),WarehouseDao["custody-store"].staffing!!.keeperUserIds) }
    }

    @ParameterizedTest @ValueSource(strings=["signed","submission","filing"])
    fun `annual repeated requests reject a modified archive snapshot with its old digest`(entry: String) = fixture { r ->
        val id=setupAnnual(r);val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        if(entry in setOf("submission","filing"))service.submission(id,annualSubmission())
        if(entry=="filing")service.filing(id,annualFiling())
        val phase=when(entry){"signed"->"SIGNED";"submission"->"SUBMITTED";else->"FILED"}
        r.db { CollectionPermanentArchiveDao.all().single { it.bizType==AnnualReportEvidenceService.BIZ && it.bizId==id && it.phase==phase }.snapshot="{}" }
        val before=staffingAnnualState(r)
        assertThrows(BadRequestException::class.java) {
            when(entry){"signed"->service.signed(annualSigned(), "museum");"submission"->service.submission(id,annualSubmission());else->service.filing(id,annualFiling())}
        }
        assertEquals(before,staffingAnnualState(r))
        r.db { assertEquals("{}",CollectionPermanentArchiveDao.all().single { it.bizType==AnnualReportEvidenceService.BIZ && it.bizId==id && it.phase==phase }.snapshot) }
    }

    private fun registeredBusiness(kind: String): Pair<String,String> = when(kind) {
        "care" -> CollectionConst.BIZ_CARE to CollectionConst.CARE
        "sampling" -> CollectionConst.BIZ_SAMPLING to CollectionConst.SAMPLING
        "incident" -> CollectionConst.BIZ_INCIDENT to CollectionConst.INCIDENT
        "incident-work" -> CollectionConst.BIZ_INCIDENT_WORK to CollectionConst.INCIDENT
        "digital" -> DigitalAcquisitionService.BIZ to CollectionConst.STORAGE
        "reproduction-plan" -> ReproductionComplianceService.BIZ to CollectionConst.STORAGE
        "rules" -> CollectionManagementRulesService.BIZ to CollectionConst.ORG_MANAGE
        else -> CollectionArchivePacketService.BIZ to CollectionConst.ARCHIVE_MANAGE
    }
    private fun registeredEditor(r: Runtime,permission: String) {
        val values=listOf(CollectionConst.SELECT,permission)
        r.permission(*values.toTypedArray());login(cachedAdmin=false,permissions=values)
    }
    private fun archiveState(r: Runtime): List<Any?> = r.db {
        listOf(CollectionPermanentArchiveDao.all().map { listOf(it.id.value,it.orgId,it.bizType,it.bizId,it.phase,it.snapshot,it.digest) },
            CollectionPermanentArchiveFileDao.all().map { listOf(it.id.value,it.archiveId,it.orgId,it.bizId,it.fileId,it.metadata) },
            FileContents.selectAll().map { it[FileContents.contentId] to it[FileContents.fileId] }.toSet())
    }
    @ParameterizedTest @ValueSource(strings=["care","sampling","incident","incident-work","digital","reproduction-plan","rules","packet"])
    fun `registered business archive phases inherit their verified originals without a business allowlist`(kind: String) = fixture { r ->
        val (biz,permission)=registeredBusiness(kind);registeredEditor(r,permission)
        r.archives.seal("museum",biz,"case","ORIGINAL",mapOf("verificationFixture" to kind),"proof")
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val id=r.archives.seal("museum",biz,"case","LATER",mapOf("verificationFixture" to "later"),"proof.txt")
        r.allow()
        r.db {
            assertEquals("LATER",CollectionPermanentArchiveDao[id].phase)
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
    }
    private fun educationPlan()=StaffEducationPlan(EnumStaffEducationKind.POST_TRAINING,"保管岗位技能","本馆保管","装具取放与状态记录",at,"proof")
    @Test fun `staff education revision keeps the same persons retained evidence across uploaders`() = fixture { r ->
        registeredEditor(r,CollectionConst.STAFF_EDUCATION_MANAGE)
        val service=r.c.getBean(CollectionStaffEducationService::class.java)
        val id=service.create("museum",StaffEducationCreate("request","reader",employmentFileIds="proof",plan=educationPlan()))
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val content=StaffEducationContent(educationPlan())
        service.revise(id,StaffEducationRevision(1,content,"依据原始材料补充说明","replacement"))
        r.allow()
        r.db { assertEquals(2L,CollectionStaffEducationVersionDao.count());assertEquals("reader",CollectionStaffEducationDao[id].userId) }
    }
    private fun setupStaffingReview(r: Runtime): Pair<String,WarehouseStaffingHandoverRequest> {
        staffingEditor(r);custodySetup(r,"entry")
        val old=r.db { WarehouseDao["custody-store"].staffing!! }
        r.c.getBean(WarehouseStaffingService::class.java).save("custody-store",old.copy(keeperUserIds=setOf("reader"),assignedAt=at,signedAssignmentFileIds="replacement",replacementReason="实际任职更正"))
        val service=r.c.getBean(WarehouseStaffingHandoverService::class.java)
        val change=(service.get("custody-store")["items"] as List<*>).filterIsInstance<WarehouseStaffingHandoverItem>().single()
        val request=WarehouseStaffingHandoverRequest(change.assignmentArchiveId,change.leavingUserId,EnumStaffingHandoverKind.RECORD_CORRECTION,emptySet(),"逐项核对录入错误及任职依据","proof",true,"keeper","leader")
        return service.register("custody-store",request) to request
    }
    @Test fun `staffing handover correction retains only actual prior evidence and stays pending confirmation`() = fixture { r ->
        val (id,request)=setupStaffingReview(r)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val service=r.c.getBean(WarehouseStaffingHandoverService::class.java)
        val next=service.register("custody-store",request.copy(previousId=id,replacementReason="补充核对说明",scopeStatement="补充核对原件全部范围"))
        assertNotEquals(id,next)
        val item=(service.get("custody-store")["items"] as List<*>).filterIsInstance<WarehouseStaffingHandoverItem>().single()
        assertEquals("PENDING_KEEPER",item.status)
        r.allow()
    }

    @ParameterizedTest @ValueSource(strings=["new-file","other-business","other-id","other-org","revoked","digest","metadata","bytes"])
    fun `registered archive inheritance keeps source scope current authority and integrity boundaries`(change: String) = fixture { r ->
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.CARE,CollectionConst.SAMPLING,CollectionConst.CROSS_ORG)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
        val original=r.archives.seal("museum",CollectionConst.BIZ_CARE,"case","ORIGINAL",mapOf("verificationFixture" to true),"proof")
        r.db {
            FileUsers.deleteWhere { (fileId eq if(change=="new-file")"public" else "proof") and (userId eq "reader") }
            if(change=="digest")CollectionPermanentArchiveDao[original].digest="broken"
            if(change=="metadata")CollectionPermanentArchiveFileDao.all().single().metadata="not-json"
        }
        if(change=="bytes")java.nio.file.Files.writeString(dir.resolve("proof.txt"),"changed content")
        if(change=="revoked"){r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny()}
        val before=archiveState(r)
        assertThrows(BadRequestException::class.java) {
            r.archives.seal(if(change=="other-org")"other" else "museum",if(change=="other-business")CollectionConst.BIZ_SAMPLING else CollectionConst.BIZ_CARE,
                if(change=="other-id")"different" else "case","LATER",mapOf("verificationFixture" to "later"),if(change=="new-file")"public" else "proof")
        }
        assertEquals(before,archiveState(r))
    }
    private fun dynamicArchivePolicy(r: Runtime,name: String) {
        r.c.beanFactory.registerSingleton(name,object: CollectionArchiveReadPolicy {
            override val businessTypes=setOf("REGISTERED_ARCHIVE_TEST")
            override fun requireReadable(reference: CollectionArchiveFileReference) {
                r.c.getBean(CollectionFileReadAuthorization::class.java).requireReadable(setOf(reference.orgId),setOf(CollectionConst.CARE),CollectionConst.CROSS_ORG,"扩展业务测试原件")
            }
        })
    }
    @ParameterizedTest @ValueSource(strings=["unique","missing","duplicate"])
    fun `registered archive inheritance follows the current unique policy registry`(configuration: String) = fixture { r ->
        registeredEditor(r,CollectionConst.CARE);dynamicArchivePolicy(r,"extensionPolicy")
        val biz="REGISTERED_ARCHIVE_TEST"
        r.archives.seal("museum",biz,"case","ORIGINAL",mapOf("verificationFixture" to true),"proof")
        if(configuration=="missing")(r.c.beanFactory as org.springframework.beans.factory.support.DefaultListableBeanFactory).destroySingleton("extensionPolicy")
        if(configuration=="duplicate")dynamicArchivePolicy(r,"duplicateExtensionPolicy")
        // 配置异常用例仍拥有文件，验证不能退化为普通上传权限；唯一策略用例不拥有文件。
        if(configuration=="unique")r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val before=archiveState(r)
        val save={r.archives.seal("museum",biz,"case","LATER",mapOf("verificationFixture" to "later"),"proof")}
        if(configuration=="unique") { save();r.allow() }
        else { assertThrows(BadRequestException::class.java) { save() };assertEquals(before,archiveState(r)) }
    }
    @Test fun `registered archive inheritance rejects a read service without business guards`() = fixture { r ->
        registeredEditor(r,CollectionConst.CARE)
        r.archives.seal("museum",CollectionConst.BIZ_CARE,"case","ORIGINAL",mapOf("verificationFixture" to true),"proof")
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        AnnotationConfigApplicationContext().use { empty ->
            empty.refresh()
            empty.beanFactory.registerSingleton("readAccess",FileReadAccess(empty.getBeanProvider(FileReadGuard::class.java),r.c.getBean(LocalFileObjectStorage::class.java)))
            val unprotected=CollectionPermanentArchiveService(r.c.getBean(BusinessAttachmentBinder::class.java),r.c.getBean(CollectionScopeResolver::class.java),
                r.c.getBean(LocalFileObjectStorage::class.java),empty.getBeanProvider(FileReadAccess::class.java),r.c.getBeanProvider(FileObjectStorage::class.java),r.c.getBeanProvider(CollectionArchiveReadPolicy::class.java))
            val before=archiveState(r)
            assertThrows(BadRequestException::class.java) { r.db { unprotected.seal("museum",CollectionConst.BIZ_CARE,"case","LATER",mapOf("verificationFixture" to "later"),"proof") } }
            assertEquals(before,archiveState(r))
        }
    }
    @ParameterizedTest @CsvSource("education,new-file","education,revoked","handover,new-file","handover,revoked")
    fun `personnel service archive inheritance retains upload and permission checks`(kind: String,change: String) = fixture { r ->
        var id:String;var request:WarehouseStaffingHandoverRequest?=null
        if(kind=="education") {
            registeredEditor(r,CollectionConst.STAFF_EDUCATION_MANAGE)
            id=r.c.getBean(CollectionStaffEducationService::class.java).create("museum",StaffEducationCreate("request","archivist",employmentFileIds="proof",plan=educationPlan()))
        } else { val value=setupStaffingReview(r);id=value.first;request=value.second }
        r.db { FileUsers.deleteWhere { (fileId eq if(change=="new-file")"public" else "proof") and (userId eq "reader") } }
        if(change=="revoked"){r.permission();login(cachedAdmin=false,permissions=emptyList());r.deny()}
        val before=archiveState(r) to r.db { CollectionStaffEducationVersionDao.count() to CollectionStaffEducationFileAccessDao.count() }
        assertThrows(BadRequestException::class.java) {
            if(kind=="education")r.c.getBean(CollectionStaffEducationService::class.java).revise(id,StaffEducationRevision(1,StaffEducationContent(educationPlan()),"补充真实依据","public"))
            else r.c.getBean(WarehouseStaffingHandoverService::class.java).register("custody-store",request!!.copy(previousId=id,replacementReason="补充依据",scopeFileIds="public"))
        }
        assertEquals(before,archiveState(r) to r.db { CollectionStaffEducationVersionDao.count() to CollectionStaffEducationFileAccessDao.count() })
    }

    private fun manualMovementState(r: Runtime) = r.db {
        listOf(CollectionDao["one"].toModel(),StorageMovementDao.count(),CollectionPermanentArchiveDao.count(),CollectionReturnDao.count(),
            FileContents.selectAll().count(),CollectionOperationLockDao.all().map { it.id.value }.toSet())
    }

    @ParameterizedTest @CsvSource("create,ON_LOAN","import,ON_LOAN","create,RETURN_HOLD","import,RETURN_HOLD")
    fun `generic movement cannot receive borrowed or held objects without acceptance`(entry: String,status: String) = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_REQUISITION)
        r.db { CollectionDao["one"].lifeStatus=EnumCollectionStatus.valueOf(status) }
        val service=r.c.getBean(StorageMovementService::class.java)
        val before=manualMovementState(r)
        val failure=runCatching {
            if(entry == "create") service.create(MovementVo(collectionId="one",type=EnumMoveType.IN,toWarehouseId="unverified-target"))
            else service.commitImport(listOf(MovementImportRowVo(rowNo=2,documentNo="DIRECT-1",collectionId="one",type="IN",reason="RESEARCH",toWarehouseId="unverified-target")))
        }.exceptionOrNull()
        assertEquals(before,manualMovementState(r),"未经验收不得改变实物状态、库位或形成流水")
        assertTrue(failure is BadRequestException)
    }

    @ParameterizedTest @ValueSource(strings=["create","import"])
    fun `generic movement cannot replay a completed requisition as a new loan`(entry: String) = fixture { r ->
        val source=approvedMovement(r,"out");executeMovement(r,"out",source)
        val returned=r.db { CollectionReturnDao.all().single().id.value }
        approveMovementReturn(r,returned,"replacement");executeMovement(r,"in",returned)
        val service=r.c.getBean(StorageMovementService::class.java)
        val before=manualMovementState(r)
        val failure=runCatching {
            if(entry == "create") service.create(MovementVo(collectionId="one",type=EnumMoveType.OUT,reason=EnumMoveReason.LOAN,refBizId=source))
            else service.commitImport(listOf(MovementImportRowVo(rowNo=2,documentNo="REPLAY-1",collectionId="one",type="OUT",reason="LOAN",refBizId=source)))
        }.exceptionOrNull()
        assertEquals(before,manualMovementState(r),"已归还的提用批准不能作为新借用再次出库")
        assertTrue(failure is BadRequestException)
    }

    @Test fun `retired generic batch endpoint does not report empty writes as successful`() = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_REQUISITION)
        val before=manualMovementState(r)
        assertThrows(BadRequestException::class.java) { r.c.getBean(StorageMovementService::class.java).commitImport(emptyList()) }
        assertEquals(before,manualMovementState(r))
    }

    @ParameterizedTest @ValueSource(strings=["ON_LOAN","RETURN_HOLD"])
    fun `historical movement writer preserves current custody and unresolved acceptance`(status: String) = fixture { r ->
        businessEditor(r,CollectionConst.BIZ_REQUISITION)
        r.db { CollectionDao["one"].apply { lifeStatus=EnumCollectionStatus.valueOf(status);warehouseId="current-store" } }
        val before=r.db { CollectionDao["one"].toModel() }
        r.db { WarehouseDao.new("historic-store") { orgId="museum";name="历史库房";sceneType=EnumLocationScene.STORAGE;type=EnumWarehouseType.WAREHOUSE;parentId="0";parentIds="0," } }
        val row=r.c.getBean(StorageMovementService::class.java).createHistoricalImport("one","historical-import",1,
            CollectionWordMovementPreviewVo(2,at.minusYears(1),EnumMoveType.IN,toWarehouseId="historic-store",operator="historical-keeper"))
        r.db {
            assertEquals(before,CollectionDao["one"].toModel())
            val movement=StorageMovementDao[row];assertEquals("historical-import",movement.refBizId)
            assertEquals(at.minusYears(1),movement.operatedAt);assertEquals("historic-store",movement.toWarehouseId)
            assertEquals(1L,StorageMovementDao.count());assertEquals(0L,CollectionReturnDao.count())
        }
    }

    private fun approvedTransferExecution(r: Runtime): String {
        // 本辅助函数准备已批准调拨；申请准备与后续总账执行分别使用当前职责。
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.STORAGE)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
        custodySetup(r,"requisition")
        r.db {
            SchemaUtils.create(io.hizhengfu.cloud.collection.rfid.dao.RfidTags,io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs)
            WarehouseDao.new("receiving-store") { orgId="other";name="接收库房";type=EnumWarehouseType.WAREHOUSE;sceneType=EnumLocationScene.STORAGE }
            val collection=CollectionDao["one"];collection.source=EnumSource.ALLOCATE;collection.level=EnumRelicLevel.GENERAL;collection.warehouseId="custody-store"
            r.c.getBean(CollectionRegistrationNumberService::class.java).markUsed("museum","A1","one","原登记")
            r.c.getBean(CollectionCatalogService::class.java).ensureCatalogFor(collection).catalogStatus=EnumCatalogStatus.APPROVED
        }
        val service=r.c.getBean(CollectionTransferService::class.java)
        val id=service.save(CollectionTransferVo(collectionId="one",toOrgId="other",reason="依法调拨",
            dossier=CollectionTransferDossierVo(EnumCollectionTransferKind.STATE_ALLOCATION,"keeper","leader","other-keeper","other-leader",
                "receiving-store","proof","原馆总账1页","接收馆总账2页",provincialJurisdictionFileIds="review",
                transportStandardReference="GB/T 23862-2024",transportStandardApplicability="逐件核对文物包装运输条件",transportCarrierName="本馆",
                transportCarrierCapabilityFileIds="proof",transportPlanFileIds="review")))
        val statutory=r.c.getBean(CollectionTransferStatutoryService::class.java)
        statutory.recordAdministrative(id,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,
            authorityName="省文物部门",documentNo="调拨批准01",issuedAt=at.minusDays(1).toLocalDate(),fileIds="review"))
        r.db {
            val e=CollectionTransferDao[id];e.flowInstanceId="transfer-original-flow";e.flowStatus=EnumFlowStatus.AUDIT
            r.c.getBean(CollectionOperationLockService::class.java).acquire(e.orgId,CollectionConst.BIZ_TRANSFER,id,listOf("one"))
            statutory.expectedActors(e).entries.forEachIndexed { i,(node,actor) ->
                io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="transfer-original-$node";nodeKey=node;action="APPROVE";operator=actor.userId;comment="双方核对原件";createTime=at.plusMinutes(i.toLong()) }
            }
            service.onFlowCompleted(e,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_TRANSFER,
                "调拨",id,CollectionConst.BIZ_TRANSFER,emptyMap(),"reader","经办"))
        }
        val executionPermissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CORRECT)
        r.permission(*executionPermissions.toTypedArray());login(cachedAdmin=false,permissions=executionPermissions)
        return id
    }
    private fun transferExecution(file: String = "proof") = CollectionTransferExecutionVo(at.plusDays(1).toLocalDate(),"custodian","other-custodian",
        file,"双方逐件核对一致","review",true,file,"review",
        appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="replacement")

    @ParameterizedTest @ValueSource(strings=["ledger","storage"])
    fun `noneditable transfer detail commits read transaction for current duty`(kind:String) = fixture { r ->
        val id=approvedTransferExecution(r)
        if(kind=="storage") {
            val permissions=listOf(CollectionConst.SELECT,CollectionConst.STORAGE)
            r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
        }
        val result=r.c.getBean(CollectionTransferService::class.java).get(id)
        assertEquals(true,result.sourceWritable)
        assertEquals(false,result.proposalEditable)
        assertEquals(EnumFlowStatus.PASS,result.flowStatus)
    }

    @Test fun `actual transfer binds retained signing originals to both movements and ledgers`() = fixture { r ->
        val id=approvedTransferExecution(r)
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") } }
        val service=r.c.getBean(CollectionTransferService::class.java)
        assertEquals(id,service.execute(id,transferExecution()))
        r.db {
            val transfer=CollectionTransferDao[id]
            val retained=CollectionRetainedLedgerDao.all().single { it.transferId==id }
            val received=CollectionPermanentArchiveDao.all().single { it.bizId==id && it.phase=="RECEIVED" }
            val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(received.snapshot)
            val mapper=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper
            fun json(value:Any?)=mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(value))
            assertEquals(json(id),root.get("transferId"),"received transfer id")
            assertEquals(json(transfer.execution),root.get("execution"),"received execution")
            assertEquals(json(retained.archiveId),root.get("sourceOriginalArchiveId"),"received retained archive")
            assertEquals(retained.snapshot.originalFileIds.isEmpty(),root.get("originalFileIds")==null,"empty original list omission")
        }
        assertEquals(id,service.execute(id,transferExecution()))
        r.db {
            assertEquals("other",CollectionDao["one"].orgId);assertEquals("receiving-store",CollectionDao["one"].warehouseId)
            val movements=StorageMovementDao.all().toList();assertEquals(2,movements.size);assertEquals(setOf("museum","other"),movements.map { it.orgId }.toSet())
            (movements.map { it.id.value }+listOf(id,"one")).forEach { target ->
                assertEquals(setOf("proof","review","replacement"),FileContents.selectAll().where { FileContents.contentId eq target }.map { it[FileContents.fileId] }.toSet())
            }
            assertEquals(1L,CollectionRetainedLedgerDao.count());assertEquals(0L,CollectionOperationLockDao.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId inList listOf("proof","review")) and (FileUsers.userId eq "reader") }.count())
        }
    }

    private fun approvedExit(r: Runtime): String {
        businessEditor(r,CollectionConst.BIZ_DEREGISTER)
        r.db { CollectionDao["one"].apply { nature=EnumCollectionNature.RELIC;lifeStatus=EnumCollectionStatus.MISSING } }
        val service=r.c.getBean(DeregistrationService::class.java)
        val id=service.save(DeregistrationVo(collectionId="one",reason=EnumDeregReason.FORCE_MAJEURE,detail="不可抗力灭失已核实",
            dossier=DeregistrationDossierVo(proposedOn=at.minusDays(3).toLocalDate(),keeperUserId="keeper",leaderUserId="leader",destinationType=EnumExitDestination.LOST,
                dispositionPlanFileIds="proof"),attachmentIds="proof"))
        r.c.getBean(DeregistrationStatutoryService::class.java).recordAdministrative(id,CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.COMPETENT,
            action=EnumAdministrativeAction.FILING,authorityName="主管文物部门",documentNo="备案01",issuedAt=at.minusDays(1).toLocalDate(),fileIds="review"))
        r.db {
            val e=DeregistrationDao[id];e.flowInstanceId="exit-original-flow";e.flowStatus=EnumFlowStatus.AUDIT
            r.c.getBean(CollectionOperationLockService::class.java).acquire(e.orgId,CollectionConst.BIZ_DEREGISTER,id,listOf("one"))
            listOf("keeper","leader").forEachIndexed { i,node ->
                io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="exit-original-$node";nodeKey=node;action="APPROVE";operator=node;comment="核对退出原件";createTime=at.plusMinutes(i.toLong()) }
            }
            service.onFlowCompleted(e,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_DEREGISTER,
                "退出",id,CollectionConst.BIZ_DEREGISTER,emptyMap(),"reader","经办"))
        }
        val permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CORRECT,CollectionConst.DEREGISTER)
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
        return id
    }
    private fun exitExecution(file: String = "proof") = DeregistrationExecutionVo(file,paperUpdatedAt=at.plusDays(1).toLocalDate(),dispositionReceiptFileIds="review",paperLedgerBookId="paper-book",paperLedgerPageNo=1)

    @Test fun `executed exit binds retained originals to original ledger across uploaders`() = fixture { r ->
        val id=approvedExit(r)
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") } }
        val service=r.c.getBean(DeregistrationService::class.java)
        assertEquals(id,service.execute(id,exitExecution()));assertEquals(id,service.execute(id,exitExecution()))
        r.db {
            assertTrue(DeregistrationDao[id].executedAt!=null);assertEquals(EnumCollectionStatus.DEREGISTERED,CollectionDao["one"].lifeStatus)
            assertEquals("A1",CollectionDao["one"].totalRegNo);assertEquals(0L,CollectionOperationLockDao.count())
            assertEquals(setOf("proof","review"),FileContents.selectAll().where { FileContents.contentId eq "one" }.map { it[FileContents.fileId] }.toSet())
            assertEquals(1,CollectionPermanentArchiveDao.all().count { it.bizId == id && it.phase == "EXECUTED" })
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId inList listOf("proof","review")) and (FileUsers.userId eq "reader") }.count())
        }
    }

    private fun prepareOwnershipExecution(r: Runtime,kind: String) = if(kind == "exit") approvedExit(r) else approvedTransferExecution(r)
    private fun executeOwnership(r: Runtime,kind: String,id: String,file: String = "proof") =
        if(kind == "exit") r.c.getBean(DeregistrationService::class.java).execute(id,exitExecution(file))
        else r.c.getBean(CollectionTransferService::class.java).execute(id,transferExecution(file))
    private fun ownershipExecutionState(r: Runtime,kind: String,id: String): List<Any?> = r.db {
        listOf(CollectionDao["one"].toModel(),if(kind == "exit") DeregistrationDao[id].toModel() else CollectionTransferDao[id].toModel(),
            StorageMovementDao.count(),CollectionPermanentArchiveDao.all().map { it.id.value to it.digest }.toSet(),
            FileContents.selectAll().map { it[FileContents.contentId] to it[FileContents.fileId] }.toSet(),
            CollectionOperationLockDao.all().map { it.id.value }.toSet(),CollectionRetainedLedgerDao.count(),CollectionLedgerLogDao.count(),
            CollectionRegistrationNumberDao.count(),CollectionLedgerStatEvents.selectAll().count())
    }

    @ParameterizedTest @CsvSource("exit,revoked","exit,bytes","exit,digest","transfer,revoked","transfer,bytes","transfer,digest")
    fun `ownership execution original failures roll back ledger custody archives and locks`(kind: String,failure: String) = fixture { r ->
        val id=prepareOwnershipExecution(r,kind)
        r.db {
            FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") }
            if(failure == "digest") CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.first { it.phase.startsWith("APPROVED_") }.digest="invalid"
        }
        if(failure == "bytes") java.nio.file.Files.writeString(dir.resolve("review.txt"),"CHANGED")
        if(failure == "revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList()) }
        val before=ownershipExecutionState(r,kind,id)
        assertThrows(BadRequestException::class.java) { executeOwnership(r,kind,id) }
        assertEquals(before,ownershipExecutionState(r,kind,id))
    }

    @ParameterizedTest @CsvSource("exit,false","exit,true","transfer,false","transfer,true")
    fun `ownership execution cannot self authorize new signing material`(kind: String,owned: Boolean) = fixture { r ->
        val id=prepareOwnershipExecution(r,kind)
        r.db { FileUsers.deleteWhere { (fileId inList if(owned) listOf("proof","review") else listOf("proof","review","public")) and (userId eq "reader") } }
        val before=ownershipExecutionState(r,kind,id)
        if(!owned) {
            assertThrows(BadRequestException::class.java) { executeOwnership(r,kind,id,"public") }
            assertEquals(before,ownershipExecutionState(r,kind,id))
        } else {
            assertEquals(id,executeOwnership(r,kind,id,"public"))
            r.db { assertEquals(if(kind=="transfer")setOf("public","review","replacement") else setOf("public","review"),
                FileContents.selectAll().where { FileContents.contentId eq "one" }.map { it[FileContents.fileId] }.toSet()) }
        }
    }

    @ParameterizedTest @ValueSource(strings=["target","org","unexecuted","approval","lifecycle","archive","digest","time","file"])
    fun `exit archive binding rejects mismatched original ledger or execution evidence`(change: String) = fixture { r ->
        val id=approvedExit(r);executeOwnership(r,"exit",id)
        r.db {
            FileContents.deleteWhere { contentId eq "one" };FileUsers.deleteWhere { userId eq "reader" }
            val e=DeregistrationDao[id]
            when(change) {
                "org" -> e.orgId="other"
                "unexecuted" -> e.executedAt=null
                "approval" -> e.flowStatus=EnumFlowStatus.DRAFT
                "lifecycle" -> CollectionDao["one"].lifeStatus=EnumCollectionStatus.IN_STORE
                "archive" -> CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single { it.phase == "EXECUTED" }.delete()
                "digest" -> CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single { it.phase == "EXECUTED" }.digest="invalid"
                "time" -> e.executedAt=at.plusYears(1)
            }
        }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).bindExecutedExitReference(id,if(change == "target")"unrelated" else "one",if(change == "file")"public" else "proof","测试关联")
        }
        r.db { assertEquals(0L,FileContents.selectAll().where { FileContents.contentId inList listOf("one","unrelated") }.count()) }
    }

    @ParameterizedTest @ValueSource(strings=["target","source-org","target-org","current-org","warehouse","unexecuted","approval","execution",
        "source-archive","target-archive","digest","file","movement-source","movement-org","movement-collection","movement-time","movement-from","movement-to","movement-files"])
    fun `transfer archive binding rejects unrelated targets and incomplete custody evidence`(change: String) = fixture { r ->
        val id=approvedTransferExecution(r);executeOwnership(r,"transfer",id)
        val target=r.db {
            val movement=StorageMovementDao.all().single { it.type == EnumMoveType.OUT }
            val target=if(change.startsWith("movement-"))movement.id.value else if(change == "target")"unrelated" else "one"
            FileContents.deleteWhere { contentId eq target };FileUsers.deleteWhere { userId eq "reader" }
            val e=CollectionTransferDao[id]
            when(change) {
                "source-org" -> e.orgId="outside"
                "target-org" -> e.toOrgId="outside"
                "current-org" -> CollectionDao["one"].orgId="museum"
                "warehouse" -> CollectionDao["one"].warehouseId="custody-store"
                "unexecuted" -> e.executedAt=null
                "approval" -> e.flowStatus=EnumFlowStatus.DRAFT
                "execution" -> e.execution=e.execution!!.copy(conditionDescription="被改写")
                "source-archive","target-archive" -> CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single { it.phase == if(change == "source-archive")"EXECUTED" else "RECEIVED" }.delete()
                "digest" -> CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single { it.phase == "RECEIVED" }.digest="invalid"
                "movement-source" -> movement.refBizId="unrelated"
                "movement-org" -> movement.orgId="other"
                "movement-collection" -> movement.collectionId="unrelated"
                "movement-time" -> movement.operatedAt=at
                "movement-from" -> movement.fromWarehouseId="unrelated"
                "movement-to" -> movement.toWarehouseId="unrelated"
                "movement-files" -> movement.attachmentIds="review"
            }
            target
        }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).bindExecutedTransferReference(id,target,if(change == "file")"public" else "proof","测试关联")
        }
        r.db { assertEquals(0L,FileContents.selectAll().where { FileContents.contentId eq target }.count()) }
    }

    private fun approvedLoanMovement(r: Runtime): String {
        businessEditor(r,CollectionConst.BIZ_LOAN)
        custodySetup(r,"requisition")
        r.db {
            CollectionDao["one"].apply { warehouseId="custody-store";level=EnumRelicLevel.GENERAL;preserveState=EnumPreserve.A }
        }
        // 文件事务测试明确隔离法定工作日历；日期计算由其专门用例验证。
        org.mockito.Mockito.`when`(r.c.getBean(CollectionCalendarService::class.java).deadline("museum",at.toLocalDate(),30))
            .thenReturn(at.toLocalDate().plusDays(42))
        val dossier=LoanDossierVo(EnumLoanBorrowerKind.STATE_MUSEUM,"借入馆登记号","proof",EnumLoanPurpose.EXHIBITION,"公益展览",
            "keeper","leader","custodian",at,at.plusDays(2),at.minusDays(2),"proof",setOf("甲","乙"),at.minusDays(1),"proof","proof",
            "借入方安全主责、出借方监督","专用包装运输","定岗保管","展厅","承运单位","proof","运输监督人","出借馆","proof",at.toLocalDate(),1,"proof",
            assessments=listOf(LoanAssessmentVo("one",EnumLoanObjectKind.RELIC,"状态稳定",false,false,"公益展示必要","运输风险可控","proof")),exhibitionOverseas=false,
            transportStandardReference="GB/T 23862-2024",transportStandardApplicability="本件包装运输路线按批准方案核对",transportPlanFileIds="proof")
        val service=r.c.getBean(LoanService::class.java)
        val id=service.save(LoanVo(title="公益展览借用",collectionIds="one",borrower="借入馆",protocolNo="LOAN-001",operator="reader",
            loanDate=at.toLocalDate(),returnDate=at.plusDays(2).toLocalDate(),dossier=dossier,attachmentIds="proof"))
        r.db {
            val e=LoanDao[id];e.flowInstanceId="loan-movement-flow";e.flowStatus=EnumFlowStatus.AUDIT
            r.c.getBean(CollectionOperationLockService::class.java).acquire(e.orgId,CollectionConst.BIZ_LOAN,id,listOf("one"))
            listOf("keeper" to "keeper","leader" to "leader").forEachIndexed { i,(node,actor) ->
                io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="loan-movement-$node";nodeKey=node;action="APPROVE";operator=actor;comment="核对后确认";createTime=at.plusMinutes(i.toLong()) }
            }
            service.onFlowCompleted(e,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_LOAN,
                "借用",id,CollectionConst.BIZ_LOAN,emptyMap(),"reader","经办"))
        }
        r.c.getBean(LoanStatutoryService::class.java).recordHandover(id,LoanHandoverVo(at.plusMinutes(10),"keeper","review",at.plusHours(1),
            setOf("custodian","custodian-two"),setOf("接收甲","接收乙"),"review",true,
            listOf(RequisitionConditionVo("one","完整原状",true,"review")),true,true,"review","review",
            appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds="review"))
        return id
    }

    @Test fun `loan outbound retains signed originals across uploaders`() = fixture { r ->
        val id=approvedLoanMovement(r)
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") } }
        val service=r.c.getBean(LoanService::class.java)
        assertEquals(id,service.executeOut(id,CollectionOutboundExecutionVo()))
        assertEquals(id,service.executeOut(id,CollectionOutboundExecutionVo()))
        r.db {
            val movement=StorageMovementDao.all().single()
            assertEquals(id,movement.refBizId);assertEquals(EnumMoveType.OUT,movement.type)
            assertEquals(setOf("proof","review"),FileContents.selectAll().where { FileContents.contentId eq movement.id.value }.map { it[FileContents.fileId] }.toSet())
            assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["one"].lifeStatus)
            assertTrue(LoanDao[id].outExecuted);assertEquals(1L,CollectionReturnDao.count());assertEquals(0L,CollectionOperationLockDao.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId inList listOf("proof","review")) and (FileUsers.userId eq "reader") }.count())
        }
    }

    private fun loanMovementState(r: Runtime,id: String) = r.db {
        listOf(CollectionDao["one"].toModel(),LoanDao[id].toModel(),StorageMovementDao.count(),CollectionPermanentArchiveDao.count(),
            FileContents.selectAll().count(),CollectionOperationLockDao.all().map { it.id.value }.toSet(),CollectionReturnDao.count())
    }

    @ParameterizedTest @ValueSource(strings=["revoked","bytes","digest"])
    fun `loan outbound original failures roll back execution records and automatic returns`(failure: String) = fixture { r ->
        val id=approvedLoanMovement(r)
        r.db {
            FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") }
            if(failure == "digest") CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.first { it.phase.startsWith("HANDOVER_") }.digest="invalid"
        }
        if(failure == "bytes") java.nio.file.Files.writeString(dir.resolve("review.txt"),"CHANGED")
        if(failure == "revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList()) }
        val before=loanMovementState(r,id)
        assertThrows(BadRequestException::class.java) { r.c.getBean(LoanService::class.java).executeOut(id,CollectionOutboundExecutionVo()) }
        assertEquals(before,loanMovementState(r,id))
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `loan outbound supplemental files require independent upload qualification`(owned: Boolean) = fixture { r ->
        val id=approvedLoanMovement(r)
        r.db { FileUsers.deleteWhere { (fileId inList if(owned) listOf("proof","review") else listOf("proof","review","public")) and (userId eq "reader") } }
        val before=loanMovementState(r,id)
        val execute={r.c.getBean(LoanService::class.java).executeOut(id,CollectionOutboundExecutionVo(conditionPhotoIds="public"))}
        if(!owned) {
            assertThrows(BadRequestException::class.java) { execute() };assertEquals(before,loanMovementState(r,id))
        } else {
            assertEquals(id,execute())
            r.db {
                val movement=StorageMovementDao.all().single()
                assertEquals(setOf("proof","review","public"),FileContents.selectAll().where { FileContents.contentId eq movement.id.value }.map { it[FileContents.fileId] }.toSet())
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["source","org","direction","collection","time","file-list","approval","current-org"])
    fun `loan movement inheritance rejects unrelated targets and inconsistent signed facts`(change: String) = fixture { r ->
        val id=approvedLoanMovement(r)
        val movementId=r.db {
            val time=LoanDao[id].handover!!.content.handedAt
            val movement=StorageMovementDao.new { orgId="museum";collectionId="one";type=EnumMoveType.OUT;refBizId=id;operatedAt=time;attachmentIds="proof,review" }
            when(change) {
                "source" -> movement.refBizId="unrelated"
                "org" -> movement.orgId="other"
                "direction" -> movement.type=EnumMoveType.IN
                "collection" -> movement.collectionId="unrelated"
                "time" -> movement.operatedAt=time.plusHours(1)
                "file-list" -> movement.attachmentIds="review"
                "approval" -> LoanDao[id].flowStatus=EnumFlowStatus.DRAFT
                "current-org" -> CollectionDao["one"].orgId="other"
            }
            FileUsers.deleteWhere { userId eq "reader" };movement.id.value
        }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).bindMovementReference(CollectionConst.BIZ_LOAN,id,movementId,"proof","测试关联")
        }
        r.db { assertEquals(0L,FileContents.selectAll().where { FileContents.contentId eq movementId }.count()) }
    }

    @Test fun `loan outbound automatic return and receipt retain both parties originals`() = fixture { r ->
        val id=approvedLoanMovement(r)
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") } }
        r.c.getBean(LoanService::class.java).executeOut(id,CollectionOutboundExecutionVo())
        val received=r.db { CollectionReturnDao.all().single().id.value }
        val receivingDraft=r.db { CollectionReturnDao[received].toModel() }
        r.c.getBean(CollectionReturnService::class.java).save(receivingDraft.copy(toWarehouseId="custody-store",keeperUserId="keeper"))
        approveMovementReturn(r,received,"replacement",loan=true)
        r.db { FileUsers.deleteWhere { (fileId eq "replacement") and (userId eq "reader") } }
        r.c.getBean(CollectionReturnService::class.java).executeApprovedReturn(received)
        r.db {
            assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus);assertEquals("custody-store",CollectionDao["one"].warehouseId)
            assertEquals(EnumReturnExecutionState.EXECUTED,CollectionReturnDao[received].executionState)
            assertEquals(2L,StorageMovementDao.count());assertEquals(0L,CollectionOperationLockDao.count())
            val incoming=StorageMovementDao.all().single { it.type == EnumMoveType.IN }
            assertEquals(setOf("proof","review","replacement"),FileContents.selectAll().where { FileContents.contentId eq incoming.id.value }.map { it[FileContents.fileId] }.toSet())
            assertTrue(CollectionPermanentArchiveDao.all().any { it.bizId == id && it.phase == "OUT_EXECUTED" })
            assertTrue(CollectionPermanentArchiveDao.all().any { it.bizId == received && it.phase == "RECEIVED" })
        }
    }

    private fun approvedMovement(r: Runtime, direction: String): String {
        businessEditor(r,CollectionConst.BIZ_REQUISITION)
        custodySetup(r,if(direction == "out") "requisition" else "return")
        if(direction == "out") {
            r.db {
                CollectionDao["one"].warehouseId="custody-store"
                actorRole("reader",CollectionConst.ROLE_KEEPER)
                originalReader("movement-receiver",CollectionConst.SELECT)
            }
            val id=saveCustody(r,"requisition")
            r.db {
                val e=RequisitionDao[id];e.flowInstanceId="movement-flow";e.flowStatus=EnumFlowStatus.AUDIT
                r.c.getBean(CollectionOperationLockService::class.java).acquire(e.orgId,CollectionConst.BIZ_REQUISITION,id,listOf("one"))
                listOf("department" to "department-head","keeper" to "keeper","leader" to "leader").forEachIndexed { i,(node,actor) ->
                    io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="movement-$node";nodeKey=node;action="APPROVE";operator=actor;comment="核对后确认";createTime=at.plusMinutes(i.toLong()) }
                }
                r.c.getBean(RequisitionService::class.java).onFlowCompleted(e,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_REQUISITION,
                    "提用",id,CollectionConst.BIZ_REQUISITION,emptyMap(),"reader","经办"))
            }
            r.c.getBean(RequisitionStatutoryService::class.java).recordHandover(id,RequisitionHandoverVo(at.plusHours(1),at.plusMinutes(10),"keeper","review",
                setOf("reader"),setOf("movement-receiver"),listOf(RequisitionConditionVo("one","完整原状",true,"review")),true,true,"review","review"))
            return id
        }
        val id=saveCustody(r,"return")
        approveMovementReturn(r,id)
        return id
    }
    private fun approveMovementReturn(r: Runtime, id: String, file: String = "proof", loan: Boolean = false) {
        r.c.getBean(ReturnStatutoryService::class.java).recordInspection(id,ReturnInspectionVo(at.plusDays(1),
            custodianUserIds=if(loan)setOf("custodian","custodian-two")else setOf("custodian"),returningUserIds=if(loan)emptySet()else setOf("reader"),
            returningPersonNames=if(loan)setOf("归还甲","归还乙")else emptySet(),
            returningAuthorizationFileIds=file,distinctPartiesConfirmed=true,originalDocumentsChecked=true,
            items=listOf(ReturnItemCheckVo("one",EnumLoanObjectKind.RELIC,true,"核对器形和原图",true,"与出库一致",false,null,false,EnumReturnSecurityIncident.NONE)),signedHandoverFileIds=file,
            transportStandardReference=if(loan)"GB/T 23862-2024" else null,transportStandardApplicability=if(loan)"本件文物包装适用范围已核对" else null,
            transportCarrierName=if(loan)"借入馆运输组" else null,transportCarrierCapabilityFileIds=if(loan)file else null,
            transportSupervisorName=if(loan)"专人监督" else null,transportSupervisionFileIds=if(loan)file else null,
            packingStandardConfirmed=if(loan)true else null,packingCheckFileIds=if(loan)file else null))
        r.db {
            val e=CollectionReturnDao[id];e.flowInstanceId="movement-return-flow";e.flowStatus=EnumFlowStatus.AUDIT
            r.c.getBean(CollectionOperationLockService::class.java).acquire(e.orgId,CollectionConst.BIZ_RETURN,id,listOf("one"))
            io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="movement-return-keeper";nodeKey="keeper";action="APPROVE";operator="keeper";comment="核对接收";createTime=at.plusDays(1).plusHours(1) }
            r.c.getBean(CollectionReturnService::class.java).onFlowCompleted(e,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_RETURN,
                "归还",id,CollectionConst.BIZ_RETURN,emptyMap(),"reader","经办"))
        }
    }
    private fun executeMovement(r: Runtime, direction: String, id: String): String =
        if(direction == "out") r.c.getBean(RequisitionService::class.java).executeOut(id,CollectionOutboundExecutionVo())
        else r.c.getBean(CollectionReturnService::class.java).executeApprovedReturn(id)

    @ParameterizedTest @ValueSource(strings=["out", "in"])
    fun `actual movement execution retains signed material when uploader differs from operator`(direction: String) = fixture { r ->
        val id=approvedMovement(r,direction)
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") } }
        assertEquals(id,executeMovement(r,direction,id))
        assertEquals(id,executeMovement(r,direction,id))
        r.db {
            val movement=StorageMovementDao.all().single()
            assertEquals(id,movement.refBizId)
            assertEquals(if(direction == "out") EnumMoveType.OUT else EnumMoveType.IN,movement.type)
            assertEquals(setOf("proof","review"),FileContents.selectAll().where { FileContents.contentId eq movement.id.value }.map { it[FileContents.fileId] }.toSet())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId inList listOf("proof","review")) and (FileUsers.userId eq "reader") }.count())
            assertEquals(if(direction == "out") EnumCollectionStatus.IN_USE else EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
            if(direction == "out") { assertTrue(RequisitionDao[id].outExecuted);assertEquals(1L,CollectionReturnDao.count()) }
            else { assertEquals(EnumReturnExecutionState.EXECUTED,CollectionReturnDao[id].executionState);assertTrue(RequisitionDao[CollectionReturnDao[id].sourceBizId].returned) }
            assertEquals(0L,CollectionOperationLockDao.count())
        }
    }

    @Test
    fun `completed requisition refuses stale session after execution permission revoked`() = fixture { r ->
        val id=approvedMovement(r,"out")
        assertEquals(id,executeMovement(r,"out",id))
        r.permission(CollectionConst.SELECT)
        val before=movementState(r,"out",id)
        assertThrows(BadRequestException::class.java) { executeMovement(r,"out",id) }
        assertEquals(before,movementState(r,"out",id))
    }

    private fun movementState(r: Runtime, direction: String, id: String): List<Any?> = r.db {
        listOf(CollectionDao["one"].toModel(),if(direction == "out") RequisitionDao[id].toModel() else CollectionReturnDao[id].toModel(),
            StorageMovementDao.count(),CollectionPermanentArchiveDao.count(),FileContents.selectAll().count(),CollectionOperationLockDao.count(),CollectionReturnDao.count())
    }
    @ParameterizedTest @CsvSource("out,revoked", "in,revoked", "out,bytes", "in,bytes", "out,digest", "in,digest")
    fun `movement failures roll back lifecycle location records archives and locks`(direction: String, failure: String) = fixture { r ->
        val id=approvedMovement(r,direction)
        r.db {
            FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") }
            if(failure == "digest") CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }
                .first { it.phase.startsWith(if(direction == "out") "HANDOVER_" else "INSPECTION_") }.digest="invalid"
        }
        if(failure == "bytes") java.nio.file.Files.writeString(dir.resolve("review.txt"),"CHANGED")
        if(failure == "revoked") { r.permission();login(cachedAdmin=false,permissions=emptyList()) }
        val before=movementState(r,direction,id)
        assertThrows(BadRequestException::class.java) { executeMovement(r,direction,id) }
        assertEquals(before,movementState(r,direction,id))
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `outbound supplementary file still requires an independent upload relationship`(owned: Boolean) = fixture { r ->
        val id=approvedMovement(r,"out")
        r.db { FileUsers.deleteWhere { (fileId inList if(owned) listOf("proof","review") else listOf("proof","review","public")) and (userId eq "reader") } }
        val before=movementState(r,"out",id)
        val execute={r.c.getBean(RequisitionService::class.java).executeOut(id,CollectionOutboundExecutionVo(conditionPhotoIds="public"))}
        if(!owned) {
            assertThrows(BadRequestException::class.java) { execute() };assertEquals(before,movementState(r,"out",id))
        } else {
            assertEquals(id,execute())
            r.db { val movement=StorageMovementDao.all().single()
                assertEquals(setOf("proof","review","public"),FileContents.selectAll().where { FileContents.contentId eq movement.id.value }.map { it[FileContents.fileId] }.toSet())
            }
        }
    }

    @ParameterizedTest @CsvSource("out,source", "in,source", "out,org", "in,org", "out,direction", "in,direction", "out,collection", "in,collection",
        "out,time", "in,time", "out,file-list", "in,file-list", "out,approval", "in,approval", "out,current-org", "in,current-org")
    fun `movement inheritance refuses unrelated targets and inconsistent signed facts`(direction: String, change: String) = fixture { r ->
        val id=approvedMovement(r,direction)
        val type=if(direction == "out") CollectionConst.BIZ_REQUISITION else CollectionConst.BIZ_RETURN
        val movementId=r.db {
            val time=if(direction == "out") RequisitionDao[id].handover!!.content.handedAt else CollectionReturnDao[id].inspection!!.inspectedAt
            val movement=StorageMovementDao.new {
                orgId="museum";collectionId="one";this.type=if(direction == "out") EnumMoveType.OUT else EnumMoveType.IN
                refBizId=id;operatedAt=time;attachmentIds="proof,review"
            }
            when(change) {
                "source" -> movement.refBizId="unrelated"
                "org" -> movement.orgId="other"
                "direction" -> movement.type=if(direction == "out") EnumMoveType.IN else EnumMoveType.OUT
                "collection" -> movement.collectionId="unrelated"
                "time" -> movement.operatedAt=time.plusHours(1)
                "file-list" -> movement.attachmentIds="review"
                "approval" -> if(direction == "out") RequisitionDao[id].flowStatus=EnumFlowStatus.DRAFT else CollectionReturnDao[id].flowStatus=EnumFlowStatus.DRAFT
                "current-org" -> CollectionDao["one"].orgId="other"
            }
            FileUsers.deleteWhere { userId eq "reader" };movement.id.value
        }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).bindMovementReference(type,id,movementId,"proof","测试关联")
        }
        r.db { assertEquals(0L,FileContents.selectAll().where { FileContents.contentId eq movementId }.count()) }
    }

    @Test fun `outbound automatic return and receipt preserve all source and receiving originals`() = fixture { r ->
        val source=approvedMovement(r,"out")
        r.db { FileUsers.deleteWhere { (fileId inList listOf("proof","review")) and (userId eq "reader") } }
        executeMovement(r,"out",source)
        val received=r.db { CollectionReturnDao.all().single().id.value }
        approveMovementReturn(r,received,"replacement")
        r.db { FileUsers.deleteWhere { (fileId eq "replacement") and (userId eq "reader") } }
        executeMovement(r,"in",received)
        r.db {
            assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
            assertEquals("custody-store",CollectionDao["one"].warehouseId)
            assertTrue(RequisitionDao[source].returned)
            assertEquals(EnumReturnExecutionState.EXECUTED,CollectionReturnDao[received].executionState)
            assertEquals(2L,StorageMovementDao.count())
            val incoming=StorageMovementDao.all().single { it.type == EnumMoveType.IN }
            assertEquals(setOf("proof","review","replacement"),FileContents.selectAll().where { FileContents.contentId eq incoming.id.value }.map { it[FileContents.fileId] }.toSet())
            assertTrue(CollectionPermanentArchiveDao.all().any { it.bizId == source && it.phase == "OUT_EXECUTED" })
            assertTrue(CollectionPermanentArchiveDao.all().any { it.bizId == received && it.phase == "RECEIVED" })
            assertEquals(0L,CollectionOperationLockDao.count())
        }
    }

    private fun mainFile(r: Runtime, kind: String = "cover", file: String = "proof") = r.db {
        when (kind) {
            "cover" -> CollectionDao["one"].coverImage = file
            "media" -> CollectionMediaDao.new("media") { orgId="museum"; collectionId="one"; fileId=file }
            "attachment" -> CollectionAttachmentDao.new("attachment") { orgId="museum"; collectionId="one"; fileId=file }
            "binding" -> FileContents.insert { it[fileId]=file; it[contentId]="one" }
        }
    }

    /** 构造留存和执行记录，用于读取边界测试；不替代实际调拨流程及签署验收。 */
    private fun transfer(r: Runtime, source: String = "museum", targetOrg: String = "other", transferId: String = "transfer", actor: String = "archivist", signingFile: String = "review") {
        login(actor,cachedAdmin=actor!="reader")
        r.db {
            val collection = CollectionDao["one"]
            val mainSource=CollectionMainOriginalFileSource()
            val files=mainSource.references(CollectionMainOriginalFileSource.BIZ,"one").map { it.file }+mainSource.historicalFileIds("one")+signingFile
            val prepared=r.archives.prepareFiles(files.joinToString(","))
            val retainedId = CollectionRetainedLedgerService(r.c.getBean(CollectionScopeResolver::class.java), r.archives).retain(collection,targetOrg,transferId)
            val retained = CollectionRetainedLedgerDao[retainedId]
            val signing = CollectionTransferExecutionVo(at.toLocalDate(), "source-custodian", "target-custodian", signingFile, "现状一致", signingFile, true, signingFile, signingFile)
            val movement = CollectionTransferDao.new(transferId) {
                orgId=source;fromOrgId=source;toOrgId=targetOrg;collectionId="one";flowStatus=EnumFlowStatus.PASS
                execution=signing;executedAt=at;executedBy="officer"
            }
            collection.orgId=targetOrg
            CollectionCatalogDao.all().forEach { it.orgId=targetOrg }
            CollectionMediaDao.all().forEach { it.orgId=targetOrg }
            CollectionAttachmentDao.all().forEach { it.orgId=targetOrg }
            r.archives.sealPrepared(listOf(
                CollectionPermanentArchiveService.SealRequest(source,CollectionConst.BIZ_TRANSFER,transferId,"EXECUTED",movement.toModel(),signingFile),
                CollectionPermanentArchiveService.SealRequest(targetOrg,CollectionConst.BIZ_TRANSFER,transferId,"RECEIVED",mapOf("transferId" to transferId, "collection" to collection.toModel(), "execution" to signing, "sourceOriginalArchiveId" to retained.archiveId, "originalFileIds" to retained.snapshot.originalFileIds), (listOf(signingFile) + retained.snapshot.originalFileIds).joinToString(","))
            ),prepared)
        }
        login()
    }

    private fun retainedRow() = CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq "transfer" }.single()

    private fun catalogFile(r: Runtime, kind: String) = r.db {
        val content=CollectionCatalogMapper.fromCollection(CollectionDao["one"].toModel())
        CollectionCatalogDao.new("catalog") { orgId="museum";collectionId="one";version=1;catalogStatus=EnumCatalogStatus.APPROVED;this.content=content.copy(coverImage=if(kind=="catalog") "proof" else null) }
        if(kind=="version") CollectionCatalogVersionDao["v1"].content=content.copy(coverImage="proof")
    }
    private fun businessEditor(r: Runtime, biz: String = CollectionConst.BIZ_CHANGE) {
        val permissions=listOf(CollectionConst.SELECT,when(biz) { CollectionConst.BIZ_CHANGE -> CollectionConst.LEDGER_CORRECT; CollectionConst.BIZ_APPRAISAL -> CollectionConst.APPRAISE; CollectionConst.BIZ_ACQUISITION_PLAN, AccessionReceiptService.BIZ -> CollectionConst.ACQUIRE; CollectionConst.BIZ_DEREGISTER -> CollectionConst.DEREGISTER; CollectionConst.BIZ_WAREHOUSE_ENTRY -> CollectionConst.WAREHOUSE_ENTRY_APPLY; else -> CollectionConst.STORAGE })
        r.permission(*permissions.toTypedArray());login(cachedAdmin=false,permissions=permissions)
    }
    private fun saveBusiness(r: Runtime, biz: String, id: String? = null, file: String = "proof"): String = when(biz) {
        CollectionConst.BIZ_CHANGE -> r.c.getBean(CollectionChangeService::class.java).let { service ->
            val proposal=service.proposal("one")
            service.save(proposal.copy(id=id,reason="核对原始依据",content=proposal.content!!.copy(formalName="核实后的定名"),evidenceFileIds=file))
        }
        CollectionConst.BIZ_LOAN -> r.c.getBean(LoanService::class.java).save(LoanVo(id=id,collectionIds="one",title="借用方案",borrower="借用单位",operator="reader",reviewer="archivist",attachmentIds=file))
        else -> r.c.getBean(CollectionTransferService::class.java).save(CollectionTransferVo(id=id,collectionId="one",toOrgId="other",reason="核实调拨方案",attachmentIds=file))
    }
    @ParameterizedTest @CsvSource("COLLECTION_CHANGE,proof", "COLLECTION_CHANGE,replacement", "COLLECTION_LOAN,proof", "COLLECTION_LOAN,replacement", "COLLECTION_TRANSFER,proof", "COLLECTION_TRANSFER,replacement")
    fun `business editor retains another uploader legacy materials and can archive the same case later`(biz: String, file: String) = fixture { r ->
        businessEditor(r,biz)
        val id=saveBusiness(r,biz)
        r.db {
            CollectionPermanentArchiveFileDao.all().toList().forEach { it.delete() }
            CollectionPermanentArchiveDao.all().toList().forEach { it.delete() }
            FileContents.deleteAll()
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
        }
        r.allow()
        assertEquals(id,saveBusiness(r,biz,id,file))
        val items=r.c.getBean(CollectionOriginalHistoryService::class.java).page(biz,id,null,pageSize=0).list as List<CollectionOriginalHistoryItem>
        assertEquals(setOf("proof.txt","$file.txt"),items.map { it.name }.toSet())
        val archive=r.archives.seal("museum",biz,id,"EVIDENCE_CHECK",mapOf("purpose" to "测试原件关联，不代表审批或签署"),"proof")
        r.db {
            assertEquals(1,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq archive) }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
        assertThrows(BadRequestException::class.java) { r.c.getBean(FileService::class.java).insertFileContent("proof","arbitrary-content") }
        r.allow()
    }
    private fun removeIdentityArchives(r: Runtime) = r.db {
        CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizType eq CollectionBusinessOriginalFiles.BIZ }.toList().forEach { archive ->
            FileContents.deleteWhere { contentId eq archive.id.value }
            CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.toList().forEach { it.delete() }
            archive.delete()
        }
    }
    @Test fun `new information change inherits only its server derived original cover`() = fixture { r ->
        catalogFile(r,"version")
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        businessEditor(r)
        val id=saveBusiness(r,CollectionConst.BIZ_CHANGE,file="review")
        r.db { assertEquals("proof",CollectionChangeDao[id].before.coverImage);assertEquals("proof",CollectionChangeDao[id].content.coverImage) }
        val items=r.c.getBean(CollectionOriginalHistoryService::class.java).page(CollectionConst.BIZ_CHANGE,id,null,pageSize=0).list as List<CollectionOriginalHistoryItem>
        assertEquals(setOf("proof.txt","review.txt"),items.map { it.name }.toSet());r.allow()
        assertThrows(BadRequestException::class.java) { r.c.getBean(FileService::class.java).insertFileContent("proof","arbitrary-content") }
    }
    @Test fun `client supplied before content cannot authorize a foreign proposed cover`() = fixture { r ->
        catalogFile(r,"version")
        r.db {
            CollectionDao.new("another") { orgId="museum";name="另一藏品";totalRegNo="A2";coverImage="replacement" }
            FileUsers.deleteWhere { (fileId eq "proof") or (fileId eq "replacement") }
        }
        businessEditor(r);r.allow("replacement.txt")
        val service=r.c.getBean(CollectionChangeService::class.java)
        val p=service.proposal("one")
        assertThrows(BadRequestException::class.java) {
            service.save(p.copy(reason="不能冒用其他藏品影像",before=p.before!!.copy(coverImage="replacement"),content=p.content!!.copy(formalName="另一拟定名",coverImage="replacement"),evidenceFileIds="review"))
        }
        r.db { assertEquals(0,CollectionChangeDao.count());assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,FileContents.selectAll().count()) }
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_CHANGE","COLLECTION_LOAN","COLLECTION_TRANSFER"])
    fun `existing stage originals can enter another stage without an identity record or uploader ownership`(biz: String) = fixture { r ->
        businessEditor(r,biz);val id=saveBusiness(r,biz)
        r.archives.seal("museum",biz,id,"SOURCE_EVIDENCE",mapOf("purpose" to "测试已存阶段材料"),"proof")
        removeIdentityArchives(r)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val next=r.archives.seal("museum",biz,id,"FOLLOWING_EVIDENCE",mapOf("purpose" to "测试同申请材料关联，不代表审批"),"proof")
        r.db {
            assertEquals(1,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq next) }.count())
            assertEquals(0,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizType eq CollectionBusinessOriginalFiles.BIZ }.count())
        }
        r.allow()
    }
    @ParameterizedTest @CsvSource("COLLECTION_CHANGE,revoke", "COLLECTION_CHANGE,other-case", "COLLECTION_CHANGE,digest", "COLLECTION_CHANGE,confidential", "COLLECTION_LOAN,revoke", "COLLECTION_LOAN,other-case", "COLLECTION_LOAN,digest", "COLLECTION_LOAN,confidential", "COLLECTION_TRANSFER,revoke", "COLLECTION_TRANSFER,other-case", "COLLECTION_TRANSFER,digest", "COLLECTION_TRANSFER,confidential")
    fun `stage inheritance rejects revoked permission another case and altered evidence`(biz: String, fault: String) = fixture { r ->
        businessEditor(r,biz);val id=saveBusiness(r,biz)
        val first=r.archives.seal("museum",biz,id,"SOURCE_EVIDENCE",mapOf("purpose" to "测试既有材料"),"proof")
        val targetId=if(fault=="other-case") saveBusiness(r,biz,file="replacement") else id
        removeIdentityArchives(r)
        r.db {
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
            if(fault=="digest") CollectionPermanentArchiveDao[first].digest="changed"
            if(fault=="confidential") AcquisitionDao.new("private-source") { orgId="museum";name="保密征集依据";contractAttachmentIds="proof" }
        }
        if(fault=="revoke") r.permission()
        assertThrows(BadRequestException::class.java) {
            r.archives.seal("museum",biz,targetId,"DENIED_EVIDENCE",mapOf("purpose" to "不能继承"),"proof")
        }
        r.db { assertEquals(0,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.phase eq "DENIED_EVIDENCE" }.count()) }
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_CHANGE","COLLECTION_LOAN","COLLECTION_TRANSFER"])
    fun `new unowned business material cannot gain authority from the just edited field`(biz: String) = fixture { r ->
        businessEditor(r,biz);val id=saveBusiness(r,biz)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") or (fileId eq "replacement") } }
        val count=r.db { CollectionPermanentArchiveDao.count() }
        assertThrows(BadRequestException::class.java) { saveBusiness(r,biz,id,"replacement") }
        r.db {
            assertEquals(count,CollectionPermanentArchiveDao.count())
            val files=when(biz) { CollectionConst.BIZ_CHANGE -> CollectionChangeDao[id].evidenceFileIds;CollectionConst.BIZ_LOAN -> LoanDao[id].attachmentIds;else -> CollectionTransferDao[id].attachmentIds }
            assertEquals("proof",files)
            assertEquals(0,FileContents.selectAll().where { FileContents.fileId eq "replacement" }.count())
        }
    }
    @Test fun `stage inheritance does not cross business types sharing an identifier`() = fixture { r ->
        val biz=CollectionConst.BIZ_LOAN
        businessEditor(r,biz);val id=saveBusiness(r,biz)
        r.archives.seal("museum",biz,id,"SOURCE_EVIDENCE",mapOf("purpose" to "借用材料"),"proof")
        removeIdentityArchives(r)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        assertThrows(BadRequestException::class.java) {
            r.archives.seal("museum",CollectionConst.BIZ_CHANGE,id,"DENIED_EVIDENCE",mapOf("purpose" to "不能串用"),"proof")
        }
        r.db { assertEquals(0,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.phase eq "DENIED_EVIDENCE" }.count()) }
    }
    @ParameterizedTest @ValueSource(booleans=[true,false])
    fun `archive without inheritance authorization still applies real uploader binding rules`(owned: Boolean) = fixture { r ->
        businessEditor(r)
        val id=saveBusiness(r,CollectionConst.BIZ_CHANGE)
        if(!owned) r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        val ordinary=CollectionPermanentArchiveService(r.c.getBean(BusinessAttachmentBinder::class.java),r.c.getBean(CollectionScopeResolver::class.java),r.c.getBean(LocalFileObjectStorage::class.java),
            readAccess=unprotectedArchiveReadAccessProvider())
        val save={ r.db { ordinary.seal("museum",CollectionConst.BIZ_CHANGE,id,"ORDINARY_BINDING",mapOf("purpose" to "原有引用规则"),"proof") } }
        if(owned) {
            val archive=save()
            r.db { assertEquals(1,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq archive) }.count()) }
        } else {
            assertThrows(BadRequestException::class.java) { save() }
            r.db { assertEquals(0,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.phase eq "ORDINARY_BINDING" }.count()) }
        }
    }
    @ParameterizedTest @ValueSource(strings=["catalog","version"])
    fun `catalog working copies and version-only originals remain protected`(kind: String) = fixture { r ->
        catalogFile(r,kind);r.allow();target(r);r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["catalog","version"])
    fun `catalog-only originals enter both transfer manifests without rewriting old version ownership`(kind: String) = fixture { r ->
        catalogFile(r,kind);transfer(r,actor="reader")
        r.db { assertTrue("proof" in retainedRow().snapshot.originalFileIds);assertEquals("museum",CollectionCatalogVersionDao["v1"].orgId) }
        r.allow();target(r);r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["missing","foreign","org"])
    fun `catalog version with inconsistent catalog parent cannot expose its files`(fault: String) = fixture { r ->
        catalogFile(r,"version")
        r.db { when(fault) { "missing" -> CollectionCatalogVersionDao["v1"].catalogId="missing"; "foreign" -> CollectionCatalogDao["catalog"].collectionId="another"; else -> CollectionCatalogDao["catalog"].orgId="other" } }
        r.deny()
    }
    @Test fun `altered former museum version cannot introduce a new file into received history`() = fixture { r ->
        catalogFile(r,"version");transfer(r)
        r.db { CollectionCatalogVersionDao["v1"].content=CollectionCatalogVersionDao["v1"].content.copy(coverImage="replacement") }
        target(r);r.deny("replacement.txt");r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_CATALOG_EDIT","COLLECTION_CATALOG_AUDIT"])
    fun `current catalog duties can read main originals but cannot bypass another confidential source`(permission: String) = fixture { r ->
        catalogFile(r,"catalog");r.permission(permission);r.allow()
        r.db { AcquisitionDao.new("acquisition") { orgId="museum";name="保密征集";contractAttachmentIds="proof" } }
        r.deny()
    }
    @Test fun `catalog draft replacement retains previous working-copy original for the catalog editor`() = fixture { r ->
        r.db { CollectionDao.new("draft") { orgId="museum";name="初编藏品";totalRegNo="D1";catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING } }
        r.permission(CollectionConst.CATALOG_EDIT);login(cachedAdmin=false,permissions=listOf(CollectionConst.CATALOG_EDIT))
        val catalog=r.c.getBean(CollectionCatalogService::class.java)
        catalog.save("draft",CollectionCatalogContentVo(formalName="初编藏品",coverImage="proof"))
        catalog.save("draft",CollectionCatalogContentVo(formalName="初编藏品",coverImage="replacement"))
        assertEquals(setOf("proof.txt","replacement.txt"),history(r,"draft").map { it.name }.toSet())
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `authorized keeper preserves another uploader legacy original before replacement`(kind: String) = fixture { r ->
        mainFile(r,kind)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE))
        r.allow()
        val files=r.c.getBean(CollectionFileService::class.java)
        if(kind=="media") files.saveMedia("one",CollectionMediaVo(id="media",fileId="replacement"))
        else files.saveAttachment("one",CollectionAttachmentVo(id="attachment",fileId="replacement"))
        assertEquals(setOf("proof.txt","replacement.txt"),history(r).map { it.name }.toSet())
        r.allow()
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(FileService::class.java).insertFileContent("proof","arbitrary-content","冒用")
        }
        r.db { assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count()) }
    }
    @ParameterizedTest @ValueSource(strings=["proof","proof.txt"])
    fun `catalog rejects another uploader new cover before creating business or archive references`(reference: String) = fixture { r ->
        r.db {
            CollectionDao.new("draft") { orgId="museum";name="初编藏品";totalRegNo="D1";catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING }
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
        }
        r.permission(CollectionConst.CATALOG_EDIT);login(cachedAdmin=false,permissions=listOf(CollectionConst.CATALOG_EDIT))
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionCatalogService::class.java).save("draft",CollectionCatalogContentVo(formalName="初编藏品",coverImage=reference))
        }
        r.db {
            assertEquals(0,CollectionCatalogDao.count())
            assertEquals(0,CollectionPermanentArchiveDao.count())
            assertEquals(0,FileContents.selectAll().count())
        }
    }
    @Test fun `catalog editor preserves unchanged legacy cover from another uploader`() = fixture { r ->
        r.db {
            val collection=CollectionDao.new("draft") { orgId="museum";name="初编藏品";totalRegNo="D1";catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING }
            CollectionCatalogDao.new("draft-catalog") { orgId="museum";collectionId="draft";catalogStatus=EnumCatalogStatus.DRAFT;content=CollectionCatalogMapper.fromCollection(collection.toModel()).copy(coverImage="proof.txt") }
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
        }
        r.permission(CollectionConst.CATALOG_EDIT);login(cachedAdmin=false,permissions=listOf(CollectionConst.CATALOG_EDIT))
        r.c.getBean(CollectionCatalogService::class.java).save("draft",CollectionCatalogContentVo(formalName="核实定名",coverImage="proof"))
        assertEquals(listOf("proof.txt"),history(r,"draft").map { it.name })
        r.db {
            assertEquals("proof",CollectionCatalogDao["draft-catalog"].content.coverImage)
            assertEquals(1,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq "draft-catalog") }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
        r.allow()
    }
    @ParameterizedTest @CsvSource("media,proof", "media,proof.txt", "attachment,proof", "attachment,proof.txt")
    fun `keeping another uploader legacy child file repairs its own binding and updates its alias`(kind: String, reference: String) = fixture { r ->
        mainFile(r,kind)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") };assertEquals(0,FileContents.selectAll().count()) }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE))
        val files=r.c.getBean(CollectionFileService::class.java)
        for(title in listOf("原件说明","核实后的说明")) {
            if(kind=="media") files.saveMedia("one",CollectionMediaVo(id="media",fileId=reference,title=title))
            else files.saveAttachment("one",CollectionAttachmentVo(id="attachment",fileId=reference,name=title))
            r.db {
                val binding=FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq "one") }.single()
                assertEquals(title,binding[FileContents.aliasName])
                assertEquals("proof",if(kind=="media") CollectionMediaDao["media"].fileId else CollectionAttachmentDao["attachment"].fileId)
                assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
            }
        }
        assertEquals(listOf("proof.txt"),history(r).map { it.name });r.allow()
        assertThrows(BadRequestException::class.java) { r.c.getBean(FileService::class.java).insertFileContent("proof","arbitrary-content") }
    }
    @ParameterizedTest @ValueSource(strings=["update","import"])
    fun `draft metadata update can keep another uploader legacy cover without an ordinary binding`(path: String) = fixture { r ->
        r.db {
            CollectionDao.new("draft") { orgId="museum";name="初编藏品";totalRegNo="D1";catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING;coverImage="proof.txt";acquireDate=at.toLocalDate() }
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
        }
        r.permission(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT))
        val service=r.c.getBean(CollectionService::class.java)
        val changed=r.db { CollectionDao["draft"].toModel().copy(coverImage="proof",originalName="旧定名") }
        if(path=="update") service.update("draft",changed) else assertTrue(service.updateFromImport("draft",changed,"核对旧定名"))
        r.db {
            assertEquals("旧定名",CollectionDao["draft"].originalName)
            assertEquals("proof",CollectionDao["draft"].coverImage)
            assertEquals(1,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq "draft") }.count())
        }
        assertEquals(listOf("proof.txt"),history(r,"draft").map { it.name });r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["foreign","version","missing"])
    fun `existing reference binding refuses foreign historical or missing source records`(fault: String) = fixture { r ->
        mainFile(r,"attachment");catalogFile(r,"version")
        r.db { CollectionDao.new("another") { orgId="museum";name="另一藏品";totalRegNo="A2" } }
        login(cachedAdmin=false)
        val originals=r.c.getBean(CollectionBusinessOriginalFiles::class.java)
        val collection=if(fault=="foreign") "another" else "one"
        val record=when(fault) { "foreign" -> "attachment";"version" -> "v1";else -> "missing" }
        assertFalse(originals.bindExistingMainReference(collection,record,"proof",collection,"不能建立"))
        r.db { assertEquals(0,FileContents.selectAll().count()) }
    }
    @Test fun `existing reference cannot target arbitrary content even when its original is readable`() = fixture { r ->
        mainFile(r,"attachment");login(cachedAdmin=false);r.allow()
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).bindExistingMainReference("one","attachment","proof","arbitrary-content","不能建立")
        }
        r.db { assertEquals(0,FileContents.selectAll().count()) }
    }
    @Test fun `existing reference binding rechecks revoked permission after original retention`() = fixture { r ->
        mainFile(r,"attachment");login(cachedAdmin=false)
        val originals=r.c.getBean(CollectionBusinessOriginalFiles::class.java)
        originals.rememberExistingMain("one")
        r.permission()
        assertThrows(BadRequestException::class.java) { originals.bindExistingMainReference("one","attachment","proof","one","不能建立") }
        r.db { assertEquals(0,FileContents.selectAll().where { FileContents.contentId eq "one" }.count()) }
    }
    @Test fun `historical retention does not substitute for a removed current record reference`() = fixture { r ->
        mainFile(r,"attachment");login(cachedAdmin=false)
        val originals=r.c.getBean(CollectionBusinessOriginalFiles::class.java)
        originals.rememberExistingMain("one")
        r.db { CollectionAttachmentDao["attachment"].fileId="replacement" }
        assertFalse(originals.bindExistingMainReference("one","attachment","proof","one","不能重新引入"))
        r.db { assertEquals(0,FileContents.selectAll().where { FileContents.contentId eq "one" }.count()) }
    }
    @Test fun `repaired existing reference invalidates the cached ordinary attachment query`() = fixture { r ->
        mainFile(r,"attachment");login(cachedAdmin=false)
        val files=r.c.getBean(FileService::class.java)
        val params=ParamsFile(contentId="one")
        val request=MockHttpServletRequest()
        assertEquals(0,files.findPage(params,request)!!.total)
        assertTrue(r.c.getBean(CollectionBusinessOriginalFiles::class.java).bindExistingMainReference("one","attachment","proof","one","原件说明"))
        assertEquals(1,files.findPage(params,request)!!.total)
    }
    @ParameterizedTest @ValueSource(strings=["update","import"])
    fun `new draft cover cannot borrow another collection readable original`(path: String) = fixture { r ->
        r.db {
            CollectionDao.new("draft") { orgId="museum";name="初编藏品";totalRegNo="D1";catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING;coverImage="proof";acquireDate=at.toLocalDate() }
            CollectionDao.new("another") { orgId="museum";name="另一藏品";totalRegNo="A2";coverImage="replacement" }
            FileUsers.deleteWhere { (fileId eq "proof") or (fileId eq "replacement") }
        }
        r.permission(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT))
        r.allow("replacement.txt")
        val service=r.c.getBean(CollectionService::class.java)
        val changed=r.db { CollectionDao["draft"].toModel().copy(coverImage="replacement",originalName="不能写入") }
        assertThrows(BadRequestException::class.java) {
            if(path=="update") service.update("draft",changed) else service.updateFromImport("draft",changed,"不能冒用")
        }
        r.db {
            assertEquals("proof",CollectionDao["draft"].coverImage);assertTrue(CollectionDao["draft"].originalName == null)
            assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,FileContents.selectAll().count())
        }
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `child replacement cannot inherit another collection readable original`(kind: String) = fixture { r ->
        mainFile(r,kind)
        r.db {
            CollectionDao.new("another") { orgId="museum";name="另一藏品";totalRegNo="A2";coverImage="replacement" }
            FileUsers.deleteWhere { (fileId eq "proof") or (fileId eq "replacement") }
        }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE));r.allow("replacement.txt")
        val files=r.c.getBean(CollectionFileService::class.java)
        assertThrows(BadRequestException::class.java) {
            if(kind=="media") files.saveMedia("one",CollectionMediaVo(id="media",fileId="replacement"))
            else files.saveAttachment("one",CollectionAttachmentVo(id="attachment",fileId="replacement"))
        }
        r.db {
            assertEquals("proof",if(kind=="media") CollectionMediaDao["media"].fileId else CollectionAttachmentDao["attachment"].fileId)
            assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,FileContents.selectAll().count())
        }
    }
    @Test fun `catalog cannot adopt another collection readable cover through existing reference binding`() = fixture { r ->
        r.db {
            CollectionDao.new("draft") { orgId="museum";name="初编藏品";totalRegNo="D1";catalogStatus=EnumCatalogStatus.DRAFT;lifeStatus=EnumCollectionStatus.PENDING }
            CollectionCatalogDao.new("draft-catalog") { orgId="museum";collectionId="draft";catalogStatus=EnumCatalogStatus.DRAFT;content=CollectionCatalogContentVo(coverImage="proof") }
            CollectionDao.new("another") { orgId="museum";name="另一藏品";totalRegNo="A2";coverImage="replacement" }
            FileUsers.deleteWhere { (fileId eq "proof") or (fileId eq "replacement") }
        }
        r.permission(CollectionConst.CATALOG_EDIT);login(cachedAdmin=false,permissions=listOf(CollectionConst.CATALOG_EDIT));r.allow("replacement.txt")
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionCatalogService::class.java).save("draft",CollectionCatalogContentVo(coverImage="replacement"))
        }
        r.db { assertEquals("proof",CollectionCatalogDao["draft-catalog"].content.coverImage);assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,FileContents.selectAll().count()) }
    }
    @Test fun `another uploader deleted original follows its own collection into both transfer archives`() = fixture { r ->
        mainFile(r,"attachment")
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE))
        r.c.getBean(CollectionFileService::class.java).deleteAttachment("one","attachment")
        transfer(r,actor="reader")
        r.db {
            assertEquals(listOf("proof"),retainedRow().snapshot.originalFileIds)
            val retainedArchive=retainedRow().archiveId
            assertEquals(1,FileContents.selectAll().where { (FileContents.fileId eq "proof") and (FileContents.contentId eq retainedArchive) }.count())
            assertEquals(0L,FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
        r.allow();target(r);r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["permission","confidential","org"])
    fun `existing main archival authorization cannot bypass current role scope or shared confidentiality`(fault: String) = fixture { r ->
        mainFile(r,"attachment")
        r.db {
            FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") }
            if(fault=="confidential") AcquisitionDao.new("private-source") { orgId="museum";name="保密依据";contractAttachmentIds="proof" }
            if(fault=="org") CollectionAttachmentDao["attachment"].orgId="other"
        }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE))
        if(fault=="permission") r.permission()
        assertThrows(BadRequestException::class.java) { r.c.getBean(CollectionFileService::class.java).deleteAttachment("one","attachment") }
        r.db { assertTrue(CollectionAttachmentDao.findById("attachment") != null);assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,FileContents.selectAll().count()) }
    }
    @Test fun `unowned replacement rolls back old original capture without gaining references`() = fixture { r ->
        mainFile(r,"attachment")
        r.db { FileUsers.deleteWhere { (fileId eq "proof") or (fileId eq "replacement") } }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE))
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionFileService::class.java).saveAttachment("one",CollectionAttachmentVo(id="attachment",fileId="replacement"))
        }
        r.db { assertEquals("proof",CollectionAttachmentDao["attachment"].fileId);assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,FileContents.selectAll().count()) }
    }
    @Test fun `retained main original from another collection cannot authorize a new identity binding`() = fixture { r ->
        mainFile(r)
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE))
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).rememberExistingMain("one")
        r.db { CollectionDao.new("another") { orgId="museum";name="另一藏品";totalRegNo="A2";coverImage="proof" } }
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionMainOriginalFileSource.BIZ,"another")
        }
        r.db { assertEquals(0,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq "another" }.count()) }
    }
    @Test fun `statutory catalog materialization retains previous main and new approved originals`() = fixture { r ->
        catalogFile(r,"version");mainFile(r)
        r.db {
            val node=CollectionDao["one"]
            r.c.getBean(CollectionCatalogService::class.java).applyStatutoryChange(node,CollectionCatalogMapper.fromCollection(node.toModel()).copy(coverImage="replacement"),"change","批准更换影像","applicant","approver",at)
        }
        assertEquals(setOf("proof.txt","replacement.txt"),history(r).map { it.name }.toSet())
        r.db { assertEquals("replacement",CollectionDao["one"].coverImage) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun history(r: Runtime, id: String = "one") = r.c.getBean(CollectionOriginalHistoryService::class.java)
        .page(CollectionMainOriginalFileSource.BIZ,id,null,pageSize=0).list as List<CollectionOriginalHistoryItem>

    private fun transferredHistory(r: Runtime) {
        mainFile(r)
        login("archivist")
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionMainOriginalFileSource.BIZ,"one")
        login()
        transfer(r)
        assertTrue(history(r).isNotEmpty())
    }

    @ParameterizedTest @ValueSource(strings=["retained","execution","receipt","reference","file-path","role","account","museum"])
    fun `history output rejects source or identity changes after batch authorization and clears its read context`(kind:String) = fixture {r ->
        transferredHistory(r)
        var changed=false
        r.c.beanFactory.registerSingleton("historyLateChange",object:FileReadMetadataBatchAuthorizationGuard {
            override fun isProtected(path:String,storageType:String?)=false
            override fun requireReadable(path:String,storageType:String?)=Unit
            override fun checkReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadable(path:String,storageType:String?)=false
            override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
                if(!changed)r.db {
                    when(kind) {
                        "retained"->retainedRow().snapshot=retainedRow().snapshot.copy(collection=retainedRow().snapshot.collection.copy(name="查询期间改变"))
                        "execution"->CollectionTransferDao["transfer"].executedAt=null
                        "receipt"->CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionPermanentArchives.phase eq "RECEIVED")}.single().snapshot="{}"
                        "reference"->CollectionPermanentArchiveFiles.deleteWhere {archiveId eq retainedRow().archiveId}
                        "file-path"->FileDao["proof"].path="replacement.txt"
                        "role"->RolePermissions.deleteAll()
                        "account"->UserDao["reader"].enabled=false
                        else->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
                    }
                    changed=true
                }
                return locations.associateWith {Result.success(false)}
            }
        })
        assertThrows(BadRequestException::class.java) {history(r)}
        assertTrue(changed)
        // 页面事务回滚晚发生的测试变更；下一次查询必须重新建立身份与来源核验。
        assertTrue(history(r).isNotEmpty())
        r.allow()
    }
    @Test fun `successful history read cannot retain transfer proof for later query or download`() = fixture {r ->
        transferredHistory(r)
        r.db {CollectionTransferDao["transfer"].executedAt=null}
        assertTrue(history(r).isEmpty())
        r.deny()
        r.db {CollectionTransferDao["transfer"].executedAt=at}
        assertTrue(history(r).isNotEmpty())
        r.allow()
    }

    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `main material replacement retains old and new originals through the actual write service`(kind: String) = fixture { r ->
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        mainFile(r,kind)
        val files=r.c.getBean(CollectionFileService::class.java)
        if(kind=="media") files.saveMedia("one",CollectionMediaVo(id="media",fileId="replacement"))
        else files.saveAttachment("one",CollectionAttachmentVo(id="attachment",fileId="replacement"))
        assertEquals(setOf("proof.txt","replacement.txt"),history(r).map { it.name }.toSet())
        assertTrue(history(r).all { it.contentIntegrity=="SHA256_RECORDED" })
        r.allow();r.allow("replacement.txt")
        val response=MockHttpServletResponse()
        r.c.getBean(CollectionOriginalHistoryService::class.java).download(history(r).single { it.name=="proof.txt" }.id,response)
        assertEquals("CONTENT:proof.txt",response.contentAsString)
        r.permission();assertTrue(history(r).isEmpty());r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `deleting a legacy child preserves its unbound original and child-only ordinary attachments`(kind: String) = fixture { r ->
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        mainFile(r,kind)
        r.db { FileContents.insert { it[fileId]="review";it[contentId]=kind } }
        val files=r.c.getBean(CollectionFileService::class.java)
        if(kind=="media") files.deleteMedia("one","media") else files.deleteAttachment("one","attachment")
        r.db { assertEquals(0,CollectionMediaDao.count()+CollectionAttachmentDao.count()) }
        assertEquals(setOf("proof.txt","review.txt"),history(r).map { it.name }.toSet())
        r.allow();r.allow("review.txt")
        assertThrows(BadRequestException::class.java) { r.c.getBean(FileService::class.java).delete("proof") }
        target(r);assertTrue(history(r).isEmpty());r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["update","import"])
    fun `new main registration and both cover update paths retain the original`(path: String) = fixture { r ->
        r.permission(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT)
        login(cachedAdmin=false,permissions=listOf(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT))
        val service=r.c.getBean(CollectionService::class.java)
        val id=service.create(CollectionVo(name="新登记",totalRegNo="A2",coverImage="proof",acquireDate=at.toLocalDate()))
        assertEquals(listOf("proof.txt"),history(r,id).map { it.name })
        val update=r.db { CollectionDao[id].toModel().copy(coverImage="replacement") }
        if(path=="update") service.update(id,update) else assertTrue(service.updateFromImport(id,update,"核实后更换封面"))
        assertEquals(setOf("proof.txt","replacement.txt"),history(r,id).map { it.name }.toSet())
        r.db { assertEquals("replacement",CollectionDao[id].coverImage) }
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `failed new original retention rolls back child edits bindings and old retention`(kind: String) = fixture { r ->
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        mainFile(r,kind)
        r.db {
            FileDao.new("missing") { name="missing.txt";path="missing.txt";hash="missing";ext="txt";size=1 }
            FileUsers.insert {it[fileId]="missing";it[userId]="reader";it[uploadDate]=at}
        }
        val files=r.c.getBean(CollectionFileService::class.java)
        assertThrows(BadRequestException::class.java) {
            if(kind=="media") files.saveMedia("one",CollectionMediaVo(id="media",fileId="missing"))
            else files.saveAttachment("one",CollectionAttachmentVo(id="attachment",fileId="missing"))
        }
        r.db {
            assertEquals("proof",if(kind=="media") CollectionMediaDao["media"].fileId else CollectionAttachmentDao["attachment"].fileId)
            assertEquals(0,CollectionPermanentArchiveDao.count())
            assertEquals(0,FileContents.selectAll().where { FileContents.fileId eq "missing" }.count())
        }
    }
    @Test fun `deleted main originals remain in the next actual retained and receiving manifests`() = fixture { r ->
        r.permission(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE)
        mainFile(r,"attachment")
        r.c.getBean(CollectionFileService::class.java).deleteAttachment("one","attachment")
        transfer(r,actor="reader")
        r.db { assertEquals(listOf("proof"),retainedRow().snapshot.originalFileIds) }
        r.allow();target(r);r.allow()
        assertEquals(listOf("proof.txt"),history(r).map { it.name })
    }

    @Test fun `ordinary source reader can archive transferred main originals also used as handover evidence`() = fixture { r ->
        mainFile(r);transfer(r,actor="reader",signingFile="proof")
        r.allow();target(r);r.allow()
    }
    @Test fun `ordinary receiving reader can prepare the next transfer while prior custody evidence remains readable`() = fixture { r ->
        mainFile(r);transfer(r,actor="reader");target(r)
        r.db {
            GroupDao.new("third") { name="third";type='1';parentId="0";parentIds="0," }
            CollectionOrgDao.new("third") { name="third";code="third";systemGroupId="third" }
        }
        transfer(r,"other","third","second-transfer",actor="reader")
        r.allow()
        r.db { UserDao["reader"].groupId="third" };login();r.allow()
    }
    @Test fun `prepared archive rejects files not checked before transfer`() = fixture { r ->
        val prepared=r.db { r.archives.prepareFiles("proof") }
        assertThrows(BadRequestException::class.java) { r.db {
            r.archives.sealPrepared(listOf(CollectionPermanentArchiveService.SealRequest("museum",CollectionMainOriginalFileSource.BIZ,"one","TEST_CAPTURE",mapOf("fact" to "original"),"replacement")),prepared)
        } }
        r.db { assertEquals(0,CollectionPermanentArchiveDao.count()) }
    }
    @Test fun `prepared archive rechecks actual bytes and rolls back references after content changes`() = fixture { r ->
        val prepared=r.db { r.archives.prepareFiles("proof") }
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"changed after preparation")
        assertThrows(BadRequestException::class.java) { r.db {
            r.archives.sealPrepared(listOf(CollectionPermanentArchiveService.SealRequest("museum",CollectionMainOriginalFileSource.BIZ,"one","TEST_CAPTURE",mapOf("fact" to "original"),"proof")),prepared)
        } }
        r.db { assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,CollectionPermanentArchiveFileDao.count()) }
    }
    @Test fun `prepared archive does not retain permission after current role is revoked`() = fixture { r ->
        mainFile(r)
        val prepared=r.db { r.archives.prepareFiles("proof") }
        r.permission()
        assertThrows(BadRequestException::class.java) { r.db {
            r.archives.sealPrepared(listOf(CollectionPermanentArchiveService.SealRequest("museum",CollectionMainOriginalFileSource.BIZ,"one","TEST_CAPTURE",mapOf("fact" to "original"),"proof")),prepared)
        } }
        r.db { assertEquals(0,CollectionPermanentArchiveDao.count());assertEquals(0,CollectionPermanentArchiveFileDao.count()) }
    }

    private fun target(r: Runtime) { r.db { UserDao["reader"].groupId="other" }; login() }

    @ParameterizedTest @ValueSource(strings=["cover","media","attachment","binding"])
    fun `unarchived main files require current museum read permission`(kind: String) = fixture { r ->
        mainFile(r,kind);r.allow();r.permission();r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["cover","media","attachment","binding"])
    fun `other museum cannot read ordinary main files`(kind: String) = fixture { r ->
        mainFile(r,kind);target(r);r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `wrong child ownership or missing parent is not a public file`(kind: String) = fixture { r ->
        mainFile(r,kind)
        r.db { if(kind=="media") CollectionMediaDao["media"].orgId="other" else CollectionAttachmentDao["attachment"].orgId="other" }
        r.deny()
        r.db { if(kind=="media") { CollectionMediaDao["media"].orgId="museum"; CollectionMediaDao["media"].collectionId="missing" } else { CollectionAttachmentDao["attachment"].orgId="museum"; CollectionAttachmentDao["attachment"].collectionId="missing" } }
        r.deny()
    }
    @Test fun `main file stays protected while another confidential business references it`() = fixture { r ->
        mainFile(r)
        r.db { AcquisitionDao.new("acquisition") { orgId="museum";name="征集材料";contractAttachmentIds="proof" } }
        r.deny()
    }
    @Test fun `public file delete and unlink cannot detach current main materials`() = fixture { r ->
        mainFile(r,"binding")
        val service = r.c.getBean(FileService::class.java)
        assertThrows(BadRequestException::class.java) { service.delete("proof") }
        assertThrows(BadRequestException::class.java) { service.deleteByFileIdAndContentId("proof","one") }
        r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["cover","media","attachment","binding"])
    fun `actual retained original and signed receipt permit both museums`(kind: String) = fixture { r ->
        mainFile(r,kind);transfer(r);r.allow();target(r);r.allow();r.permission();r.deny()
    }
    @Test fun `changing owner alone does not grant access to earlier archived original`() = fixture { r ->
        mainFile(r)
        login("archivist")
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionMainOriginalFileSource.BIZ,"one")
        r.db { CollectionDao["one"].orgId="other" }
        target(r);r.deny()
    }
    @Test fun `source museum does not gain access to new materials added after handover`() = fixture { r ->
        mainFile(r);transfer(r)
        r.db { CollectionMediaDao.new("new-target-media") { orgId="other";collectionId="one";fileId="replacement" } }
        r.deny("replacement.txt");target(r);r.allow("replacement.txt")
    }
    @Test fun `shared signed handover materials bound to received collection remain readable by both parties`() = fixture { r ->
        mainFile(r);transfer(r)
        r.db { FileContents.insert { it[fileId]="review";it[contentId]="one" } }
        r.allow("review.txt");target(r);r.allow("review.txt")
    }
    @ParameterizedTest @ValueSource(strings=["missing-receipt","missing-execution","missing-signature","wrong-target","unexecuted","changed-snapshot","changed-execution","changed-receipt","changed-retained-file"])
    fun `incomplete or inconsistent transfer evidence cannot grant cross museum read`(fault: String) = fixture { r ->
        mainFile(r);transfer(r)
        r.db {
            fun phase(name: String) = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionPermanentArchives.phase eq name) }.single()
            when(fault) {
                "missing-receipt" -> phase("RECEIVED").delete()
                "missing-execution" -> phase("EXECUTED").delete()
                "missing-signature" -> CollectionPermanentArchiveFiles.deleteWhere { archiveId eq phase("RECEIVED").id.value }
                "wrong-target" -> retainedRow().targetOrgId="missing"
                "unexecuted" -> CollectionTransferDao["transfer"].executedAt=null
                "changed-snapshot" -> retainedRow().snapshot=retainedRow().snapshot.copy(collection=retainedRow().snapshot.collection.copy(name="改写"))
                "changed-execution" -> CollectionTransferDao["transfer"].execution=CollectionTransferDao["transfer"].execution!!.copy(conditionDescription="改写")
                "changed-receipt" -> phase("RECEIVED").snapshot="{}"
                "changed-retained-file" -> {
                    val row=retainedRow()
                    row.snapshot=row.snapshot.copy(collection=row.snapshot.collection.copy(coverImage="replacement"))
                }
            }
        }
        target(r);r.deny()
    }
    @Test fun `multiple verified transfers retain per-file access across the custody chain`() = fixture { r ->
        mainFile(r);transfer(r)
        r.db {
            GroupDao.new("third") { name="third";type='1';parentId="0";parentIds="0," }
            CollectionOrgDao.new("third") { name="third";code="third";systemGroupId="third" }
        }
        transfer(r,"other","third","second-transfer")
        r.allow()
        target(r);r.allow()
        r.db { UserDao["reader"].groupId="third" };login();r.allow()
    }
    @Test fun `return transfer to original museum terminates graph traversal and preserves both parties`() = fixture { r ->
        mainFile(r);transfer(r);transfer(r,"other","museum","return-transfer")
        r.allow();target(r);r.allow()
    }
    @Test fun `inactive former museum membership does not mask a verified active receiving membership`() = fixture { r ->
        mainFile(r);transfer(r)
        r.db {
            CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            UserGroups.insert { it[userId]="reader";it[groupId]="other" }
        }
        r.allow()
        r.db { UserGroups.deleteWhere { userId eq "reader" } }
        r.deny()
    }

    @Test fun `legacy retained snapshots without generic binding manifest remain readable for explicit files`() = fixture { r ->
        mainFile(r);transfer(r)
        r.db {
            val row=retainedRow()
            row.snapshot=row.snapshot.copy(originalFileIds=emptyList())
            val archive=CollectionPermanentArchiveDao[row.archiveId]
            val node=JsonUtils.mapper.readTree(JsonUtils.toJson(row.snapshot)) as tools.jackson.databind.node.ObjectNode
            node.remove("originalFileIds")
            archive.snapshot=node.toString();archive.digest=CollectionCareEvidence.digest(archive.snapshot)
        }
        r.allow();target(r);r.allow()
    }
    @Test fun `generic child bindings are retained by actual ledger service before transfer`() = fixture { r ->
        mainFile(r,"media")
        r.db { FileContents.insert { it[fileId]="replacement";it[contentId]="media" } }
        transfer(r)
        r.db { assertTrue("replacement" in retainedRow().snapshot.originalFileIds) }
        r.allow("replacement.txt");target(r);r.allow("replacement.txt")
    }
    @Test fun `missing permanently retained file reference invalidates cross museum sharing`() = fixture { r ->
        mainFile(r,"binding");transfer(r)
        r.db { CollectionPermanentArchiveFiles.deleteWhere { archiveId eq retainedRow().archiveId } }
        r.deny();target(r);r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["list","source","file-reference"])
    fun `receiving original manifest must agree with source retained originals`(fault: String) = fixture { r ->
        mainFile(r,"binding");transfer(r)
        r.db {
            val receipt=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionPermanentArchives.phase eq "RECEIVED") }.single()
            if(fault=="file-reference") CollectionPermanentArchiveFiles.deleteWhere { (archiveId eq receipt.id.value) and (fileId eq "proof") }
            else {
                val node=JsonUtils.mapper.readTree(receipt.snapshot) as tools.jackson.databind.node.ObjectNode
                if(fault=="source") node.put("sourceOriginalArchiveId","another-archive") else node.remove("originalFileIds")
                receipt.snapshot=node.toString();receipt.digest=CollectionCareEvidence.digest(receipt.snapshot)
            }
        }
        r.deny();target(r);r.deny()
    }

    private fun retainedReadFixture(r:Runtime):String {
        r.db {CollectionDao["one"].coverImage="proof"}
        val transfer=approvedTransferExecution(r)
        r.c.getBean(CollectionTransferService::class.java).execute(transfer,transferExecution())
        login();r.permission(CollectionConst.SELECT)
        return r.db {CollectionRetainedLedgerDao.all().single().id.value}
    }
    @ParameterizedTest @ValueSource(strings=["revoked","disabled","moved"])
    fun `retained ledger reads cannot reuse old administrator login`(kind:String)=fixture {r ->
        val id=retainedReadFixture(r);val ledger=r.c.getBean(CollectionRetainedLedgerService::class.java)
        Assertions.assertNotNull(ledger.get(id));assertEquals(1L,ledger.findPage(MockHttpServletRequest()).total.toLong())
        when(kind){"revoked"->r.permission();"disabled"->r.db {UserDao["reader"].enabled=false};else->r.db {UserDao["reader"].groupId="other"}}
        assertAll(
            {assertThrows(BadRequestException::class.java){ledger.get(id)}},
            {if(kind=="moved")assertEquals(0L,ledger.findPage(MockHttpServletRequest()).total.toLong()) else assertThrows(BadRequestException::class.java){ledger.findPage(MockHttpServletRequest())}}
        )
    }
    @ParameterizedTest @ValueSource(strings=["name","number","archive-id","snapshot","archive-digest","reference-org","missing-reference","transfer-id","target-org"])
    fun `retained ledger detail rejects changed original or frozen association`(kind:String)=fixture {r ->
        val id=retainedReadFixture(r);val ledger=r.c.getBean(CollectionRetainedLedgerService::class.java)
        Assertions.assertNotNull(ledger.get(id))
        r.db {
            val row=CollectionRetainedLedgerDao[id];val archive=CollectionPermanentArchiveDao[row.archiveId]
            when(kind) {
                "name"->row.name="被更换的原账名称"
                "number"->row.totalRegNo="CHANGED"
                "archive-id"->row.archiveId="missing"
                "snapshot"->row.snapshot=row.snapshot.copy(collection=row.snapshot.collection.copy(name="被修改的冻结内容"))
                "archive-digest"->archive.digest="bad"
                "reference-org"->CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}.first().orgId="other"
                "missing-reference"->CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}.forEach {it.delete()}
                "transfer-id"->row.transferId="wrong-transfer"
                "target-org"->row.targetOrgId="museum"
            }
        }
        assertAll({assertThrows(BadRequestException::class.java){ledger.get(id)}},{assertThrows(BadRequestException::class.java){ledger.findPage(MockHttpServletRequest())}})
    }

    @ParameterizedTest @ValueSource(strings=["identity-only","old-files"])
    fun `retained ledger legacy associations stay explicit without rewriting old evidence`(kind:String)=fixture {r ->
        val id=retainedReadFixture(r);val ledger=r.c.getBean(CollectionRetainedLedgerService::class.java)
        r.db {
            val row=CollectionRetainedLedgerDao[id]
            row.snapshot=row.snapshot.copy(identity=null,originalFileIds=if(kind=="old-files")emptyList() else row.snapshot.originalFileIds)
            val old=JsonUtils.mapper.readTree(JsonUtils.toJson(row.snapshot)) as tools.jackson.databind.node.ObjectNode
            old.remove("identity");if(kind=="old-files")old.remove("originalFileIds")
            CollectionPermanentArchiveDao[row.archiveId].apply {snapshot=old.toString();digest=CollectionCareEvidence.digest(snapshot)}
        }
        val before=r.db {CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}}
        assertEquals(false,ledger.get(id)["bindingVerified"])
        assertEquals(1L,ledger.findPage(MockHttpServletRequest()).total.toLong())
        r.allow()
        r.db {assertEquals(before,CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)})}
    }
    @Test fun `retained ledger new identity cannot be cleared to claim legacy status`()=fixture {r ->
        val id=retainedReadFixture(r);val ledger=r.c.getBean(CollectionRetainedLedgerService::class.java)
        assertEquals(true,ledger.get(id)["bindingVerified"])
        r.db {CollectionRetainedLedgerDao[id].apply {snapshot=snapshot.copy(identity=null)}}
        assertThrows(BadRequestException::class.java){ledger.get(id)}
    }
    @Test fun `retained ledger scope rejects foreign body before decoding despite old admin and current cross grant`()=fixture {r ->
        val id=retainedReadFixture(r);val ledger=r.c.getBean(CollectionRetainedLedgerService::class.java)
        r.db {
            val original=CollectionRetainedLedgerDao[id]
            CollectionRetainedLedgerDao.new("foreign-retained"){orgId="other";collectionId="one";transferId="foreign-transfer";totalRegNo="FOREIGN";name="外馆隐私名称";targetOrgId="museum";archiveId="absent";snapshot=original.snapshot}
        }
        r.db {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("UPDATE col_retained_ledger SET snapshot='invalid-json' WHERE id='foreign-retained'")}
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertEquals(1L,ledger.findPage(MockHttpServletRequest()).total.toLong())
        assertThrows(BadRequestException::class.java){ledger.get("foreign-retained")}
        assertThrows(BadRequestException::class.java){ledger.findPage(MockHttpServletRequest().also {it.setParameter("orgId","other")})}
        val multi=CollectionRetainedLedgerService(CollectionScopeResolver("multi","museum"),r.archives)
        r.db {assertEquals(true,multi.get(id)["bindingVerified"])}
    }
    @Test fun `retained ledger disabled museum history needs current explicit historical grant`()=fixture {r ->
        val id=retainedReadFixture(r);val ledger=r.c.getBean(CollectionRetainedLedgerService::class.java)
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        assertThrows(BadRequestException::class.java){ledger.get(id)}
        assertEquals(0L,ledger.findPage(MockHttpServletRequest()).total.toLong())
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertEquals(true,ledger.get(id)["bindingVerified"])
        assertEquals(1L,ledger.findPage(MockHttpServletRequest()).total.toLong())
    }

    /** 真 Spring/LOCAL 夹具：历史原馆 CHANGE 与同藏品接收原件共享文件；当前人只有接收馆总账权限。 */
    private fun receivedChangeContext(r: Runtime): CollectionChangeService {
        r.db {
            CollectionDao["one"].apply {
                era = "清"; texture = "铜"; categoryId = "DEV-SYNTHETIC-bronze"; level = EnumRelicLevel.GENERAL
                source = EnumSource.OLD; acquireDate = at.toLocalDate(); completeness = EnumCompleteness.COMPLETE
                quantity = 1; unit = "件"
            }
        }
        mainFile(r)
        catalogFile(r, "catalog")
        r.seal(CollectionConst.BIZ_CHANGE, id = "source-change", file = "proof", phase = "APPLIED")
        transfer(r)
        target(r)
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java), "defaultOrgId", "other")
        businessEditor(r)
        org.mockito.Mockito.`when`(r.c.getBean(PaperLedgerService::class.java).requirePageReference("other", "paper-book", 1))
            .thenReturn(PaperLedgerService.PageReference("paper-book", "接收馆总账第一册", 1, 20))
        r.db { FileUsers.deleteWhere { (fileId eq "proof") and (userId eq "reader") } }
        return r.c.getBean(CollectionChangeService::class.java)
    }

    private fun receivedApprovedChange(r: Runtime, service: CollectionChangeService): String {
        val proposal = service.proposal("one")
        assertTrue(CollectionCatalogPolicy.completeness(proposal.content!!).complete)
        val id = service.save(proposal.copy(reason = "DEV-SYNTHETIC 接收馆核对旧原件后订正", content = proposal.content!!.copy(formalName = "接收馆核定名称"),
            keeperUserId = "keeper", leaderUserId = "leader", evidenceFileIds = "public"))
        r.db {
            val row = CollectionChangeDao[id]
            row.flowInstanceId = "received-change-$id"; row.flowStatus = EnumFlowStatus.AUDIT
            listOf("keeper", "leader").forEachIndexed { index, node ->
                io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new {
                    instanceId = row.flowInstanceId!!; taskId = "received-$node-$id"; nodeKey = node
                    action = "APPROVE"; operator = node; comment = "DEV-SYNTHETIC 本轮指定核对"
                    createTime = at.plusMinutes(index.toLong())
                }
            }
            service.onFlowCompleted(row, io.hizhengfu.cloud.flow.domain.FlowCompletedEvent(row.flowInstanceId!!,
                CollectionConst.FLOW_CHANGE, "DEV-SYNTHETIC", id, CollectionConst.BIZ_CHANGE, emptyMap(), "reader", "reader"))
            assertEquals(EnumFlowStatus.PASS, row.flowStatus)
        }
        return id
    }

    private fun receivedArchive() = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionPermanentArchives.phase eq "RECEIVED")
    }.single()

    private fun receivedExecution() = CollectionChangeExecutionVo(signedChangeFileIds = "replacement",
        paperUpdatedAt = java.time.LocalDate.now(), paperLedgerBookId = "paper-book", paperLedgerPageNo = 1)

    private fun receivedProjection(r: Runtime) = r.db {
        listOf(Collections, CollectionCatalogs, CollectionCatalogVersions, CollectionChanges, CollectionChangeApprovals,
            CollectionPermanentArchives, CollectionPermanentArchiveFiles, FileContents, FileUsers, CollectionRetainedLedgers,
            CollectionTransfers, CollectionLedgerLogs, CollectionLedgerStatEvents, CollectionOperationLocks).associate { table ->
            table.tableName to table.selectAll().map { row -> table.columns.map { row[it]?.toString() } }.sortedBy { it.toString() }
        }
    }

    @Test fun `received main originals shared with source change survive current statutory execution and exact repeat`() = fixture { r ->
        val service = receivedChangeContext(r)
        r.deny()
        val sourceBefore = r.db { CollectionPermanentArchiveDao.all().map { it.id.value to (it.snapshot to it.digest) }.toMap() }
        val id = receivedApprovedChange(r, service)
        val approvedBefore = r.db { CollectionChangeDao[id].toModel() }
        org.junit.jupiter.api.Assertions.assertNull(approvedBefore.appliedAt)
        assertEquals(id, service.execute(id, receivedExecution()))
        r.db {
            assertEquals("接收馆核定名称", CollectionDao["one"].name)
            assertEquals(2, CollectionCatalogDao["catalog"].version)
            org.junit.jupiter.api.Assertions.assertNotNull(CollectionChangeDao[id].appliedAt)
            assertEquals(1, CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "APPLIED")
            }.count())
            sourceBefore.forEach { (archiveId, original) ->
                val archive = CollectionPermanentArchiveDao[archiveId]
                assertEquals(original, archive.snapshot to archive.digest)
            }
            // APPLIED 同类归档也须消费写前 RECEIVED 能力，不能只绕过 MAIN 首次检查。
            r.archives.seal("other", CollectionConst.BIZ_CHANGE, id, "VERIFIED_MAIN_SOURCE", mapOf("changeId" to id), "proof")
            assertEquals(0L, FileUsers.selectAll().where { (FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader") }.count())
        }
        val beforeRepeat = receivedProjection(r)
        assertEquals(id, service.execute(id, receivedExecution()))
        assertEquals(beforeRepeat, receivedProjection(r))
        r.deny()
    }

    @ParameterizedTest @ValueSource(strings = ["missing-received", "coherent-received-change", "bytes", "revoked", "foreign-collection"])
    fun `received main inheritance rejects broken prior source current authority and unrelated collection`(fault: String) = fixture { r ->
        receivedChangeContext(r)
        r.db {
            when (fault) {
                "missing-received" -> receivedArchive().delete()
                "coherent-received-change" -> {
                    val archive = receivedArchive()
                    val changedSnapshot = archive.snapshot.replace("现状一致", "改写接收事实")
                    assertNotEquals(archive.snapshot, changedSnapshot)
                    archive.snapshot = changedSnapshot
                    archive.digest = CollectionCareEvidence.digest(archive.snapshot)
                }
                "foreign-collection" -> CollectionDao.new("unrelated-receiver") {
                    orgId = "other"; name = "另一藏品"; totalRegNo = "B1"; coverImage = "proof"
                }
            }
        }
        if (fault == "bytes") java.nio.file.Files.writeString(dir.resolve("proof.txt"), "changed actual original")
        if (fault == "revoked") r.permission()
        val before = receivedProjection(r)
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).rememberExistingMain(
                if (fault == "foreign-collection") "unrelated-receiver" else "one")
        }
        assertEquals(before, receivedProjection(r))
    }

    @Test fun `received main inheritance rechecks current authority after preparing prior proof and rolls back new references`() = fixture { r ->
        receivedChangeContext(r)
        val baseline = receivedProjection(r)
        var changed = false
        r.c.beanFactory.registerSingleton("lateReceivedReadChange", object : FileReadGuard {
            override fun isProtected(path: String, storageType: String?) = false
            override fun requireReadable(path: String, storageType: String?) = Unit
            override fun contentBaselines(path: String, storageType: String?): List<FileContentBaseline> {
                if (!changed && CollectionPermanentArchiveDao.count() > baseline.getValue(CollectionPermanentArchives.tableName).size) {
                    changed = true
                    RolePermissions.deleteAll()
                }
                return emptyList()
            }
        })
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(CollectionBusinessOriginalFiles::class.java).rememberExistingMain("one")
        }
        assertTrue(changed, "must reach archive write after prior proof was prepared")
        assertEquals(baseline, receivedProjection(r))
        r.db { assertTrue(RolePermissions.selectAll().count() > 0L, "same transaction qualification mutation must roll back") }
    }

    @Test fun `main original source batches current parents versions catalogs and original museum ledgers`() = fixture { r ->
        val count = 240
        r.db {
            for (i in 0 until count) {
                val collection = CollectionDao.new("batch-main-$i") {
                    orgId="other"; name="批量来源$i"; totalRegNo="BATCH-$i"
                    catalogStatus=EnumCatalogStatus.APPROVED; lifeStatus=EnumCollectionStatus.IN_STORE
                    coverImage="replacement"
                }
                CollectionCatalogDao.new("batch-catalog-$i") {
                    orgId="other"; collectionId=collection.id.value; version=1
                    catalogStatus=EnumCatalogStatus.APPROVED; content=CollectionCatalogContentVo()
                }
                val version = CollectionCatalogVersionDao.new("batch-version-$i") {
                    orgId="museum"; catalogId="batch-catalog-$i"; collectionId=collection.id.value
                    this.version=1; catalogStatus=EnumCatalogStatus.APPROVED
                    content=CollectionCatalogContentVo(coverImage="proof")
                }
                val frozen = CollectionRetainedLedgerSnapshot(collection.toModel().copy(orgId="museum"), listOf(version.toModel()))
                CollectionRetainedLedgerDao.new("batch-retained-$i") {
                    orgId="museum"; collectionId=collection.id.value; transferId="batch-transfer-$i"
                    totalRegNo="BATCH-$i"; name="批量来源$i"; targetOrgId="other"
                    archiveId="batch-archive-$i"; snapshot=frozen
                }
                val raw = JsonUtils.toJson(frozen)!!
                CollectionPermanentArchiveDao.new("batch-archive-$i") {
                    orgId="museum"; bizType=CollectionMainFileReadPolicy.RETAINED; bizId="batch-retained-$i"
                    phase="TRANSFERRED"; snapshot=raw; digest=CollectionCareEvidence.digest(raw)
                }
            }
        }
        r.db {
            val selects = mutableMapOf<String, Int>()
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().registerInterceptor(object : org.jetbrains.exposed.v1.core.statements.StatementInterceptor {
                override fun beforeExecution(transaction: Transaction, context: org.jetbrains.exposed.v1.core.statements.StatementContext) {
                    val query = context.statement as? Query ?: return
                    query.targets.map { it.tableName }.distinct().forEach { table ->
                        selects[table] = (selects[table] ?: 0) + 1
                    }
                }
            })
            val references = CollectionMainOriginalFileSource().references(CollectionMainOriginalFileSource.BIZ, null)
            assertEquals(count * 2, references.size)
            assertTrue(references.all { it.businessType == CollectionMainOriginalFileSource.BIZ })
            assertEquals(count, references.count { it.orgId == "museum" && it.recordId.startsWith("batch-version-") })
            assertEquals(count, references.count { it.orgId == "other" && it.file == "replacement" })
            // 三张主表各含来源分页及两批父记录读取；留存和档案各只读取两批。
            listOf(Collections, CollectionCatalogVersions, CollectionCatalogs).forEach { table ->
                assertTrue((selects[table.tableName] ?: 0) <= 5, "$table required ${selects[table.tableName]} SELECTs")
            }
            listOf(CollectionRetainedLedgers, CollectionPermanentArchives).forEach { table ->
                assertTrue((selects[table.tableName] ?: 0) <= 2, "$table required ${selects[table.tableName]} SELECTs")
            }
            val relevant = listOf(Collections, CollectionCatalogVersions, CollectionCatalogs, CollectionRetainedLedgers, CollectionPermanentArchives)
                .sumOf { selects[it.tableName] ?: 0 }
            assertTrue(relevant <= 19, "240 current and retained sources required $relevant parent/version/catalog/ledger/archive SELECTs")
        }
    }

    @Test fun `main source batch retains exact original museum version and rechecks dirty archive and version changes`() = fixture { r ->
        catalogFile(r, "version")
        transfer(r)
        val source = CollectionMainOriginalFileSource()
        r.db {
            val reference = source.references(CollectionMainOriginalFileSource.BIZ, "one").single { it.recordId == "v1" }
            assertEquals("museum", reference.orgId)
            assertEquals(CollectionMainOriginalFileSource.BIZ, reference.businessType)
            assertTrue(source.isRetainedVersion(reference))
            val archive = CollectionPermanentArchiveDao[retainedRow().archiveId]
            val digest = archive.digest
            archive.digest = "changed-before-query"
            assertEquals("COLLECTION_UNRESOLVED_MAIN_FILES",
                source.references(CollectionMainOriginalFileSource.BIZ, "one").single { it.recordId == "v1" }.businessType)
            assertFalse(source.isRetainedVersion(reference))
            archive.digest = digest
            assertEquals(CollectionMainOriginalFileSource.BIZ,
                source.references(CollectionMainOriginalFileSource.BIZ, "one").single { it.recordId == "v1" }.businessType)

            val version = CollectionCatalogVersionDao["v1"]
            val content = version.content
            version.content = content.copy(formalName="未归档的脏版本变化")
            assertEquals("COLLECTION_UNRESOLVED_MAIN_FILES",
                source.references(CollectionMainOriginalFileSource.BIZ, "one").single { it.recordId == "v1" }.businessType)
            assertFalse(source.isRetainedVersion(reference))
            version.content = content
            assertEquals(CollectionMainOriginalFileSource.BIZ,
                source.references(CollectionMainOriginalFileSource.BIZ, "one").single { it.recordId == "v1" }.businessType)

            CollectionCatalogDao["catalog"].orgId = "museum"
            assertEquals("COLLECTION_UNRESOLVED_MAIN_FILES",
                source.references(CollectionMainOriginalFileSource.BIZ, "one").single { it.recordId == "v1" }.businessType)
        }
        r.deny()
    }

    @Test fun `content read phases share current actor queries but the next phase reads again`() = fixture { r ->
        catalogFile(r, "catalog")
        r.db {
            var actorQueries = 0
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().registerInterceptor(object : org.jetbrains.exposed.v1.core.statements.StatementInterceptor {
                override fun beforeExecution(transaction: Transaction, context: org.jetbrains.exposed.v1.core.statements.StatementContext) {
                    val query = context.statement as? Query ?: return
                    if (Users in query.targets) actorQueries++
                }
            })
            assertTrue(r.access.readEvidence("proof.txt", "LOCAL").location.protected)
            assertEquals(2, actorQueries, "one current actor read and one final fresh read for all source policies")
            assertTrue(r.access.readEvidence("proof.txt", "LOCAL").location.protected)
            assertEquals(4, actorQueries, "the next read phase must not reuse the prior identity")
        }
    }

    @ParameterizedTest @ValueSource(strings=["role", "account", "expiry", "group", "museum", "mapping", "parent", "actor"])
    fun `content read phase rejects current identity or museum changes before returning`(change: String) = fixture { r ->
        val phase = r.c.getBean(FileReadAuthorizationPhase::class.java)
        val authorization = r.c.getBean(CollectionFileReadAuthorization::class.java)
        assertThrows(BadRequestException::class.java) {
            phase.withReadPhase {
                authorization.requireReadable(setOf("museum"), setOf(CollectionConst.SELECT), CollectionConst.CROSS_ORG, "测试原件")
                r.db {
                    when (change) {
                        "role" -> RolePermissions.deleteAll()
                        "account" -> Users.update({ Users.id eq "reader" }) { it[enabled] = false }
                        "expiry" -> Users.update({ Users.id eq "reader" }) { it[expireTime] = LocalDateTime.now().minusSeconds(1) }
                        "group" -> Groups.update({ Groups.id eq "museum" }) { it[enabled] = false }
                        "museum" -> CollectionOrgs.update({ CollectionOrgs.id eq "museum" }) { it[orgStatus] = EnumOrgStatus.DISABLED }
                        "mapping" -> CollectionOrgs.update({ CollectionOrgs.id eq "museum" }) { it[systemGroupId] = "other" }
                        "parent" -> CollectionOrgs.update({ CollectionOrgs.id eq "museum" }) { it[parentId] = "other" }
                        "actor" -> login("archivist")
                    }
                }
            }
        }
        login()
        r.db {
            Users.update({ Users.id eq "reader" }) { it[enabled] = true; it[expireTime] = null }
            Groups.update({ Groups.id eq "museum" }) { it[enabled] = true }
            CollectionOrgs.update({ CollectionOrgs.id eq "museum" }) {
                it[orgStatus] = EnumOrgStatus.ENABLED; it[systemGroupId] = "museum"; it[parentId] = "0"
            }
        }
        r.permission(CollectionConst.SELECT)
        catalogFile(r, "catalog")
        r.allow()
    }

    @Test fun `content read phase isolates nested reads exceptions and metadata while anonymous public reads remain available`() = fixture { r ->
        val phase = r.c.getBean(FileReadAuthorizationPhase::class.java)
        val authorization = r.c.getBean(CollectionFileReadAuthorization::class.java)
        assertThrows(BadRequestException::class.java) {
            phase.withReadPhase {
                authorization.requireReadable(setOf("museum"), setOf(CollectionConst.SELECT), CollectionConst.CROSS_ORG, "测试原件")
                r.db { RolePermissions.deleteAll() }
                phase.withReadPhase {
                    authorization.requireReadable(setOf("museum"), setOf(CollectionConst.SELECT), CollectionConst.CROSS_ORG, "嵌套测试原件")
                }
            }
        }
        r.permission(CollectionConst.SELECT)
        r.db {
            authorization.withMetadataReadIdentity {
                var entered = false
                assertThrows(BadRequestException::class.java) { phase.withReadPhase { entered = true } }
                assertFalse(entered, "a metadata identity cannot authorize a content phase")
            }
        }
        SecurityContextHolder.clearContext()
        assertFalse(r.access.readEvidence("public.txt", "LOCAL").location.protected)
        catalogFile(r, "catalog")
        r.deny()
        login()
        r.allow()
    }

}
