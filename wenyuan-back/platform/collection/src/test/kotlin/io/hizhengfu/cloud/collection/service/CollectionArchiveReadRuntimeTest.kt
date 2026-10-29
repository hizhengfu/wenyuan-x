package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.diagnostic.CollectionPermanentOriginalIdentityInspection
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
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
import org.jetbrains.exposed.v1.dao.entityCache
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

class CollectionArchiveReadRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableCaching class Transactions
    private val at = LocalDateTime.of(2026, 1, 2, 12, 0)
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, root: Path) {
        val archives = c.getBean(CollectionPermanentArchiveService::class.java)
        val disclosures = c.getBean(CollectionDisclosureService::class.java)
        val access = c.getBean(FileReadAccess::class.java)
        val controller = FileViewController(c, false, root.toString(), false, "unused", c.getBean(FileObjectStorageService::class.java), access)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun seal(biz: String, org: String = "museum", id: String = "case", file: String = "proof", phase: String = "RECORDED", content: Any = mapOf("fact" to id), sources: Map<String, String>? = null): String {
            val previous=SecurityContextHolder.getContext().authentication
            val user=SecurityUser(id="archivist",name="归档人员",username="archivist",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
            try { return if (sources == null) archives.seal(org,biz,id,phase,content,file)
                else archives.sealFromArchives(org,biz,id,phase,content,file,sources) }
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
    @Test fun `period signed report original downloads follow current stat permission and configured museum`()=fixture { r ->
        val archive=r.seal(CollectionPeriodReportService.BIZ,phase="SIGNED")
        r.permission(CollectionConst.STAT);r.allow()
        val files=r.c.getBean(FileService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        r.permission("FILE_SELECT");r.deny()
        assertThrows(BadRequestException::class.java){front.findByContent(archive,ParamsFile(),MockHttpServletRequest())}
        r.permission(CollectionConst.STAT,CollectionConst.CROSS_ORG);configureOtherMuseum(r);r.deny()
        assertThrows(BadRequestException::class.java){front.findByContent(archive,ParamsFile(),MockHttpServletRequest())}
    }
    @Test fun `protected watermarked preview preserves original and rejects revoked repeated reads`() = fixture { r ->
        val image=java.awt.image.BufferedImage(640,480,java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g=image.createGraphics();g.color=java.awt.Color.BLUE;g.fillRect(0,0,640,480);g.dispose()
        val target=dir.resolve("protected.png")
        javax.imageio.ImageIO.write(image,"png",target.toFile())
        val bytes=java.nio.file.Files.readAllBytes(target)
        val mark=java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB)
        val mg=mark.createGraphics();mg.color=java.awt.Color.WHITE;mg.fillRect(0,0,32,32);mg.dispose()
        javax.imageio.ImageIO.write(mark,"png",dir.resolve("watermark.png").toFile())
        r.db { FileDao.new("watermarked") {name="protected.png";path="protected.png";ext="png";size=bytes.size.toLong();hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(bytes))} }
        r.seal(LedgerReconciliationService.BIZ,file="watermarked")
        r.permission(CollectionConst.LEDGER_FINANCE,"FILE_SELECT")
        val controller=FileViewController(r.c,false,dir.toString(),false,"watermark.png",r.c.getBean(FileObjectStorageService::class.java),r.access)
        fun request(original:Boolean=false):MockHttpServletResponse {
            val response=MockHttpServletResponse()
            val req=MockHttpServletRequest("GET","/userfiles/${if(original)"original/" else ""}protected.png")
            if(!original)req.addHeader("If-None-Match","*")
            if(original)controller.downloadOriginalFile(req,response) else controller.downloadFile(req,response)
            return response
        }
        val first=request();assertEquals(200,first.status)
        val rendered=javax.imageio.ImageIO.read(first.contentAsByteArray.inputStream())
        assertEquals(640,rendered.width);assertEquals(480,rendered.height)
        assertFalse(image.getRGB(0,0,640,480,null,0,640).contentEquals(rendered.getRGB(0,0,640,480,null,0,640)),"Watermark must actually change pixels")
        assertEquals("private, no-store",first.getHeader("Cache-Control"));org.junit.jupiter.api.Assertions.assertNull(first.getHeader("ETag"))
        assertArrayEquals(first.contentAsByteArray,request().contentAsByteArray)
        assertArrayEquals(bytes,request(true).contentAsByteArray)
        r.permission()
        assertThrows(BadRequestException::class.java){request()}
        assertThrows(BadRequestException::class.java){request(true)}
        assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(target))
    }

    private fun login(id: String = "reader") {
        val user = SecurityUser(id=id, name=id, username=id, password="", groupId="museum", groupIds=setOf("museum"), postIds=emptySet(), dataType=0,
            roles=setOf(EnumRole.ADMIN.value), roleCodes=setOf(CollectionConst.ROLE_DIRECTOR), authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    @ParameterizedTest
    @ValueSource(strings=["intact","missing-keeper-file","foreign-keeper","wrong-keeper-digest","revoked"])
    fun `institution representative originals reuse handover without importing keeper signature`(kind:String)=fixture {r->
        r.permission(CollectionConst.STORAGE)
        val biz=InstitutionSuccessionCaseEvidence.BIZ
        fun record(id:String,role:EnumSuccessionCaseRecord,request:InstitutionScopeReviewVo,keeper:String?=null)=r.db {
            InstitutionSuccessionRecordDao.new(id) {
                orgId="museum";caseId="institution-case";this.kind=role;actorUserId="reader";recordedAt=at
                content=InstitutionSuccessionCaseEvidence.json(mapOf("request" to request,"keeperRecordId" to keeper))
            }
        }
        val c=r.db {
            SchemaUtils.create(InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords)
            InstitutionSuccessionCaseDao.new("institution-case") {
                orgId="museum";sourceKey="institution-source"
                content=InstitutionSuccessionCaseVo("museum",EnumInstitutionSuccession.MERGER,"museum","registration","decision",at.toLocalDate(),at.toLocalDate(),"proof","basis","proof","proof","reader","reader","proof")
            }
        }
        r.seal(biz,id=c.id.value,phase="CASE",content=r.db {mapOf("orgId" to c.orgId,"sourceKey" to c.sourceKey,"content" to c.content)})
        val keeper=record("keeper-review",EnumSuccessionCaseRecord.KEEPER_REVIEW,InstitutionScopeReviewVo("scope","digest",at,"reader",true,"receiver","proof","none","review"))
        val keeperArchive=r.seal(biz,id=keeper.id.value,phase=keeper.kind.name,file="proof,review",content=r.db {InstitutionSuccessionCaseEvidence.recordValue(keeper)})
        val representative=record("representative-review",EnumSuccessionCaseRecord.REPRESENTATIVE_REVIEW,InstitutionScopeReviewVo("scope","digest",at,"reader",true,"receiver","proof","none","replacement"),keeper.id.value)
        val representativeArchive=r.seal(biz,id=representative.id.value,phase=representative.kind.name,file="proof,replacement",content=r.db {InstitutionSuccessionCaseEvidence.recordValue(representative)})
        val service=InstitutionSuccessionCaseService(r.c.getBean(CollectionScopeResolver::class.java),r.c.getBean(CollectionResponsibleUserService::class.java),r.archives)
        if(kind=="intact") {
            r.db {
                assertEquals(representativeArchive to "proof",service.requireArchivedOriginal(c.id.value,representative.id.value,"proof"))
                assertEquals(representativeArchive to "replacement",service.requireArchivedOriginal(c.id.value,representative.id.value,"replacement.txt"))
                assertEquals(keeperArchive to "review",service.requireArchivedOriginal(c.id.value,keeper.id.value,"review"))
                assertThrows(BadRequestException::class.java) {service.requireArchivedOriginal(c.id.value,representative.id.value,"review")}
                assertEquals(setOf("proof","replacement"),CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq representativeArchive}.map {it.fileId}.toSet())
            }
        } else {
            r.db {when(kind) {
                "missing-keeper-file"->CollectionPermanentArchiveFileDao.find {(CollectionPermanentArchiveFiles.archiveId eq keeperArchive) and (CollectionPermanentArchiveFiles.fileId eq "review")}.single().delete()
                "foreign-keeper"->keeper.caseId="other-case"
                "wrong-keeper-digest"->CollectionPermanentArchiveDao[keeperArchive].digest="changed"
                "revoked"->RolePermissions.deleteAll()
            }}
            r.db {assertThrows(BadRequestException::class.java) {service.requireArchivedOriginal(c.id.value,representative.id.value,"proof")}}
        }
    }
    private fun fixture(body: (Runtime) -> Unit) = fixture(
        SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("archive.db")}" }, DatabaseConfig {}), body)

    private fun multiFixture(body:(Runtime)->Unit)=fixture(
        SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("archive.db")}"},DatabaseConfig {}),body,CollectionScopeResolver("multi","museum"))

    private fun postgresFixture(body: (Runtime) -> Unit) {
        val config=System.getProperty("collection.test.pg.config") ?: error("Explicit isolated PostgreSQL configuration required")
        val properties=java.util.Properties().apply { java.nio.file.Files.newInputStream(Path.of(config)).use(::load) }
        fun source()=org.postgresql.ds.PGSimpleDataSource().apply {
            setURL(properties.getProperty("url")); user=properties.getProperty("user"); password=properties.getProperty("password")
        }
        val admin=source(); val schema="archive_read_"+java.util.UUID.randomUUID().toString().replace("-", "")
        admin.connection.use {it.createStatement().use {sql ->sql.execute("CREATE SCHEMA $schema")}}
        try {
            fixture(SpringTransactionManager(source().apply {currentSchema=schema},DatabaseConfig {}),body)
        } finally {
            admin.connection.use {it.createStatement().use {sql ->sql.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }

    private fun fixture(manager: SpringTransactionManager, body: (Runtime) -> Unit, scope:CollectionScopeResolver=CollectionScopeResolver("single","museum")) {
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("localStorage", LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator", org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope", scope)
            c.beanFactory.registerSingleton("users", mock(CollectionResponsibleUserService::class.java))
            c.beanFactory.registerSingleton("userResolver", mock(CollectionUserResolver::class.java))
            c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier { FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java), mock(FileStorageConfigReader::class.java), mock(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java)) })
            c.registerBean(FileService::class.java, java.util.function.Supplier { FileService(dir.toString(), FileFolderService(), c.getBean(FileObjectStorageService::class.java), listOf(CollectionArchiveFileRetentionGuard(), c.getBean(CollectionBusinessOriginalFileRetentionGuard::class.java)), c.getBean(FileReadAccess::class.java)) })
            c.register(CollectionBusinessOriginalFileSource::class.java, CollectionBusinessOriginalFiles::class.java, CollectionOriginalIdentityReadPolicy::class.java, CollectionBusinessOriginalFileReadGuard::class.java, CollectionBusinessOriginalFileRetentionGuard::class.java)
            c.beanFactory.registerSingleton("warehouseFlow",mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java))
            c.beanFactory.registerSingleton("inventoryCollections",mock(CollectionService::class.java))
            c.beanFactory.registerSingleton("inventoryLocks",mock(CollectionOperationLockService::class.java))
            c.register(InventoryComplianceService::class.java,InventoryBatchService::class.java,InventoryService::class.java)
            c.register(WarehouseEntryStatutoryService::class.java,WarehouseEntryApplicationService::class.java,WarehouseDiaryService::class.java,WarehouseLogService::class.java)
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java, FileReadAccess::class.java,
                BusinessAttachmentBinder::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                CollectionPermanentOriginalTransitionService::class.java,
                CollectionPermanentOriginalRecoveryService::class.java,
                WarehouseEntryTimelineService::class.java, CollectionBusinessArchiveReadPolicy::class.java, CollectionPersonnelArchiveReadPolicy::class.java, CollectionTransferArchiveReadPolicy::class.java,
                CollectionDisclosureArchiveReadPolicy::class.java, CollectionDisclosureService::class.java, StaffEducationAccess::class.java,
                CollectionManagementRulesService::class.java, CollectionRetainedLedgerService::class.java, PaperLedgerService::class.java, LedgerReconciliationService::class.java,
                WarehouseStaffingService::class.java, CollectionArchivePacketService::class.java, AnnualReportEvidenceService::class.java, AnnualReportArchiveService::class.java,
                CollectionLedgerStatisticsService::class.java, CollectionDashboardService::class.java, CollectionGradingService::class.java,
                AcquisitionOriginalFiles::class.java, AcquisitionFileReadGuard::class.java, DigitalAcquisitionFileReadGuard::class.java,
                CollectionEmergencyFileReadGuard::class.java, CollectionStaffEducationFileReadGuard::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager), dir)
            r.db {
                originalReaderSchema()
                SchemaUtils.create(*CollectionBusinessOriginalFileSource().tables.map { it.table }.distinct().toTypedArray())
                SchemaUtils.create(InventoryAnnualReports,IncidentRelocations,CollectionLedgerStatEvents,CollectionRetainedLedgers,AnnualReportEvidenceRecords,AnnualReportArchives,CollectionGradings,StorageMovements)
                SchemaUtils.create(CollectionManagementRulesRecords, PaperLedgerBooks, PaperLedgerCustodies, LedgerReconciliations,
                    ArchiveJurisdictions)
                SchemaUtils.create(CollectionOrgs, CollectionPermanentArchives, CollectionPermanentArchiveFiles,
                    CollectionPermanentOriginalTransitions, CollectionPermanentOriginalRecoveries, Files, FileContents, FileUsers,
                    Acquisitions, AcquisitionComplianceCases, AcquisitionExpertOpinions, DigitalAcquisitionFileAccesses, CollectionEmergencyFileAccesses,
                    CollectionStaffEducations, CollectionStaffEducationVersions, CollectionStaffEducationFileAccesses, CollectionTransfers, CollectionDisclosureReviews, CollectionDisclosureEvents,
                    Collections, CollectionCatalogVersions, Deregistrations, DeregistrationExpertOpinions, DeregistrationNotices, CollectionTransferNotices)
                listOf("museum", "other").forEach { id -> CollectionOrgDao.new(id) { name=id; code=id; systemGroupId=id } }
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

    private fun listedOriginal(r: Runtime, entry: String): List<Map<String, Any?>> = when (entry) {
        "list" -> r.archives.list(CollectionConst.BIZ_REQUISITION, "case")
        "authorized" -> r.archives.listForAuthorizedOrg(CollectionConst.BIZ_REQUISITION, "case", "museum")
        "phase" -> listOfNotNull(r.archives.phaseForAuthorizedOrg(CollectionConst.BIZ_REQUISITION, "case", "museum", "RECORDED"))
        else -> error("Unknown archive entry")
    }

    private fun listingRejectsChangedContent(r: Runtime, entry: String) {
        val id = r.seal(CollectionConst.BIZ_REQUISITION)
        assertEquals(id, listedOriginal(r, entry).single()["id"])
        val recorded = r.db {
            CollectionPermanentArchiveDao[id].let { listOf(it.id.value, it.orgId, it.bizType, it.bizId, it.phase, it.snapshot, it.digest) } to
                CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.map { it.metadata }
        }
        val path = dir.resolve("proof.txt")
        val original = java.nio.file.Files.readAllBytes(path)
        val modified = java.nio.file.Files.getLastModifiedTime(path)
        try {
            java.nio.file.Files.write(path, ByteArray(original.size) { 'X'.code.toByte() })
            java.nio.file.Files.setLastModifiedTime(path, modified)
            assertThrows(BadRequestException::class.java) { listedOriginal(r, entry) }
            r.db {
                assertEquals(recorded.first, CollectionPermanentArchiveDao[id].let { listOf(it.id.value, it.orgId, it.bizType, it.bizId, it.phase, it.snapshot, it.digest) })
                assertEquals(recorded.second, CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.map { it.metadata })
                assertEquals(1L, CollectionPermanentArchiveDao.count())
            }
        } finally {
            java.nio.file.Files.write(path, original)
            java.nio.file.Files.setLastModifiedTime(path, modified)
        }
        assertEquals(id, listedOriginal(r, entry).single()["id"])
    }

    @ParameterizedTest @ValueSource(strings = ["list", "authorized", "phase"])
    fun `permanent archive listing rejects same length content rewrite with restored timestamp`(entry: String) = fixture { r ->
        listingRejectsChangedContent(r, entry)
    }

    @Tag("postgres-concurrency")
    @ParameterizedTest @ValueSource(strings = ["list", "authorized", "phase"])
    fun `postgres permanent archive listing rejects changed content and preserves originals`(entry: String) = postgresFixture { r ->
        listingRejectsChangedContent(r, entry)
    }

    @ParameterizedTest @ValueSource(strings = ["list", "authorized", "phase"])
    fun `legacy archive listing does not manufacture a missing content baseline`(entry: String) = fixture { r ->
        val id = r.seal(CollectionConst.BIZ_REQUISITION)
        val legacy = r.db {
            val file = FileDao["proof"]
            CollectionArchiveFileIdentity.metadata(file, "museum", CollectionConst.BIZ_REQUISITION, "case", r.archives.fileReadKeys(file)).also { metadata ->
                CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.single().metadata = metadata
            }
        }
        val listed = listedOriginal(r, entry).single()
        @Suppress("UNCHECKED_CAST")
        val files = listed["files"] as List<Map<String, Any?>>
        assertEquals(legacy, files.single()["metadata"])
        org.junit.jupiter.api.Assertions.assertNull(CollectionArchiveFileIdentity.content(JsonUtils.mapper.readTree(legacy)))
        r.db { assertEquals(legacy, CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.single().metadata) }
    }

    @ParameterizedTest @ValueSource(strings = ["snapshot", "reference"])
    fun `scoped archive listing rejects corrupted headers and frozen file identities`(kind: String) = fixture { r ->
        val id = r.seal(CollectionConst.BIZ_REQUISITION)
        r.db {
            if (kind == "snapshot") CollectionPermanentArchiveDao[id].snapshot = "{\"fact\":\"rewritten\"}"
            else {
                val file = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.single()
                val metadata = JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
                metadata.put("path", "replacement.txt")
                file.metadata = metadata.toString()
            }
        }
        assertThrows(BadRequestException::class.java) { listedOriginal(r, "list") }
    }

    private fun listingShowsOnlyValidOriginals(r: Runtime, kind: String) {
        val damaged = r.seal(CollectionConst.BIZ_REQUISITION)
        val valid = r.seal(CollectionConst.BIZ_REQUISITION, phase = "UNAFFECTED", file = "")
        r.db {
            when (kind) {
                "snapshot" -> CollectionPermanentArchiveDao[damaged].snapshot = "{}"
                "reference" -> {
                    val ref = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq damaged }.single()
                    val metadata = JsonUtils.mapper.readTree(ref.metadata) as tools.jackson.databind.node.ObjectNode
                    metadata.put("path", "replacement.txt"); ref.metadata = metadata.toString()
                }
                "missing-file" -> FileDao["proof"].delete()
                else -> java.nio.file.Files.write(dir.resolve("proof.txt"), "CHANGED:proof.txt".toByteArray())
            }
        }
        fun state() = r.db {
            CollectionPermanentArchiveDao.all().map { listOf(it.id.value, it.snapshot, it.digest) } to
                CollectionPermanentArchiveFileDao.all().map { listOf(it.id.value, it.fileId, it.metadata) }
        }
        val before = state()
        val inspected = r.archives.inspectForAuthorizedOrg(CollectionConst.BIZ_REQUISITION, "case", "museum")
        assertEquals(listOf(valid), inspected.archives.map { it["id"] })
        assertEquals(1, inspected.issues.size); assertTrue(inspected.issues.single().startsWith("RECORDED："))
        assertTrue((inspected.archives.single()["files"] as List<*>).isEmpty())
        assertThrows(BadRequestException::class.java) { listedOriginal(r, "authorized") }
        assertThrows(BadRequestException::class.java) { listedOriginal(r, "phase") }
        assertEquals(before, state())
    }

    @ParameterizedTest @ValueSource(strings = ["snapshot", "reference", "bytes", "missing-file"])
    fun `authorized issue inspection excludes damaged originals while strict reads still reject`(kind: String) = fixture { r ->
        listingShowsOnlyValidOriginals(r, kind)
    }

    @Tag("postgres-concurrency")
    @ParameterizedTest @ValueSource(strings = ["snapshot", "reference", "bytes", "missing-file"])
    fun `postgres issue inspection excludes damaged originals without changing records`(kind: String) = postgresFixture { r ->
        listingShowsOnlyValidOriginals(r, kind)
    }

    @Test fun `authorized issue inspection refuses foreign archive before exposing its phase`() = fixture { r ->
        r.seal(CollectionConst.BIZ_REQUISITION, org = "other", phase = "PRIVATE_PHASE", file = "")
        val failure = assertThrows(BadRequestException::class.java) {
            r.archives.inspectForAuthorizedOrg(CollectionConst.BIZ_REQUISITION, "case", "museum")
        }
        assertFalse(failure.message!!.contains("PRIVATE_PHASE"))
        r.db { assertEquals(1L, CollectionPermanentArchiveDao.count()) }
    }

    @ParameterizedTest @ValueSource(strings = ["missing", "changed"])
    fun `display retains verified snapshots and unaffected stages when original content is unavailable`(damage: String) = fixture { r ->
        val damaged = r.seal(CollectionConst.BIZ_REQUISITION)
        val healthy = r.seal(CollectionConst.BIZ_REQUISITION, phase = "UNAFFECTED", file = "")
        val snapshot = r.db { CollectionPermanentArchiveDao[damaged].snapshot }
        val frozen = r.db { CollectionPermanentArchiveFileDao.all().map { it.metadata } }
        if (damage == "missing") java.nio.file.Files.delete(dir.resolve("proof.txt"))
        else java.nio.file.Files.writeString(dir.resolve("proof.txt"), "changed original bytes")
        val displayed = r.archives.readForDisplay(CollectionConst.BIZ_REQUISITION, "case", "museum")
        assertEquals(2, displayed.archives.size)
        assertEquals(1, displayed.issues.size)
        val row = displayed.archives.single { it["id"] == damaged }
        assertEquals(snapshot, row["snapshot"])
        assertEquals(row,r.archives.phaseForDisplay(CollectionConst.BIZ_REQUISITION,"case","museum","RECORDED"))
        org.junit.jupiter.api.Assertions.assertNotNull(row["evidenceIssue"])
        assertTrue((row["files"] as List<*>).isEmpty())
        org.junit.jupiter.api.Assertions.assertNull(displayed.archives.single { it["id"] == healthy }["evidenceIssue"])
        assertThrows(BadRequestException::class.java) {
            r.archives.archiveForAuthorizedOrg(CollectionConst.BIZ_REQUISITION, "case", "museum", damaged)
        }
        assertEquals(healthy, r.archives.archiveForAuthorizedOrg(CollectionConst.BIZ_REQUISITION, "case", "museum", healthy)["id"])
        assertEquals(frozen, r.db { CollectionPermanentArchiveFileDao.all().map { it.metadata } })
        r.db { CollectionPermanentArchiveDao[damaged].snapshot = "{}" }
        assertThrows(BadRequestException::class.java) {
            r.archives.readForDisplay(CollectionConst.BIZ_REQUISITION, "case", "museum")
        }
    }

    @Test fun `display still refuses a foreign archive before exposing its metadata`() = fixture { r ->
        r.seal(CollectionConst.BIZ_REQUISITION, org = "other", phase = "PRIVATE_PHASE", file = "")
        assertThrows(BadRequestException::class.java) {
            r.archives.readForDisplay(CollectionConst.BIZ_REQUISITION, "case", "museum")
        }
    }

    @Test fun `cross business archive inheritance preserves another uploader original`() = fixture { r ->
        val source = r.seal(CollectionConst.BIZ_REQUISITION)
        val digest = r.db { CollectionPermanentArchiveDao[source].digest }
        r.db { FileUsers.deleteAll() }
        val actor = SecurityUser(id="reader", name="reader", username="reader", password="", groupId="museum", groupIds=setOf("museum"), postIds=emptySet(), dataType=0,
            roles=emptySet(), authorities=listOf(SimpleGrantedAuthority(CollectionConst.SELECT)), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
        assertThrows(BadRequestException::class.java) {
            r.archives.seal("museum", CollectionConst.BIZ_RETURN, "new-case", "CREATED", mapOf("test" to true), "proof")
        }
        val id = r.archives.sealFromArchives("museum", CollectionConst.BIZ_RETURN, "new-case", "CREATED",
            mapOf("test" to true), "proof", mapOf(source to digest))
        r.db {
            assertEquals(1L, FileContents.selectAll().where { (FileContents.contentId eq id) and (FileContents.fileId eq "proof") }.count())
            assertEquals(0L, FileUsers.selectAll().count())
        }
        r.allow()
    }

    @Test fun `archive service without read access cannot prepare seal or inherit originals`() = fixture { r ->
        val source = r.seal(CollectionConst.BIZ_REQUISITION)
        val digest = r.db { CollectionPermanentArchiveDao[source].digest }
        val baseline = r.db { CollectionPermanentArchiveDao.count() }
        val unguarded = CollectionPermanentArchiveService(
            r.c.getBean(BusinessAttachmentBinder::class.java),
            r.c.getBean(CollectionScopeResolver::class.java),
            r.c.getBean(LocalFileObjectStorage::class.java),
        )
        assertThrows(BadRequestException::class.java) { r.db { unguarded.prepareFiles("proof") } }
        assertThrows(BadRequestException::class.java) {
            r.db { unguarded.seal("museum", CollectionConst.BIZ_RETURN, "unguarded-new", "CREATED", mapOf("test" to true), "proof") }
        }
        assertThrows(BadRequestException::class.java) {
            r.db { unguarded.sealFromArchives("museum", CollectionConst.BIZ_RETURN, "unguarded-inherit", "CREATED",
                mapOf("test" to true), "proof", mapOf(source to digest)) }
        }
        r.db { assertEquals(baseline, CollectionPermanentArchiveDao.count()) }
    }
    @ParameterizedTest @ValueSource(strings=["digest", "museum", "permission"])
    fun `cross business inherited originals reject changed source or revoked access`(kind: String) = fixture { r ->
        val source = r.seal(CollectionConst.BIZ_REQUISITION)
        val digest = r.db { CollectionPermanentArchiveDao[source].digest }
        val before = r.db { CollectionPermanentArchiveDao.count() }
        if (kind == "permission") r.permission()
        assertThrows(BadRequestException::class.java) {
            r.archives.sealFromArchives(if (kind == "museum") "other" else "museum", CollectionConst.BIZ_RETURN,
                "new-case", "CREATED", mapOf("test" to true), "proof", mapOf(source to if (kind == "digest") "stale" else digest))
        }
        r.db { assertEquals(before, CollectionPermanentArchiveDao.count()) }
    }
    @ParameterizedTest @CsvSource(
        "COLLECTION_LEDGER_RECONCILIATION,COLLECTION_LEDGER_FINANCE", "COLLECTION_ARCHIVE_PACKET,COLLECTION_ARCHIVE_MANAGE",
        "COLLECTION_ARCHIVE_JURISDICTION,COLLECTION_ARCHIVE_MANAGE", "COLLECTION_CARE,COLLECTION_CARE_MANAGE",
        "COLLECTION_FRAGILITY,COLLECTION_CARE_MANAGE", "COLLECTION_SAMPLING,COLLECTION_SAMPLING_MANAGE",
        "COLLECTION_INCIDENT,COLLECTION_INCIDENT_MANAGE", "COLLECTION_INCIDENT_RELOCATION,COLLECTION_INCIDENT_MANAGE",
        "COLLECTION_INCIDENT_WORK,COLLECTION_INCIDENT_MANAGE", "COLLECTION_JOINT_SAFETY,COLLECTION_CARE_MANAGE",
        "COLLECTION_DIGITAL_ACQUISITION,COLLECTION_STORAGE", "COLLECTION_EMERGENCY,COLLECTION_EMERGENCY_MANAGE",
        "COLLECTION_DESIGNATED_RECEPTION,COLLECTION_ACQUIRE", "COLLECTION_ACQUISITION,COLLECTION_ACQUIRE"
    )
    fun `specialized originals require actual current business authority`(biz: String, permission: String) = fixture { r ->
        r.seal(biz); r.deny(); r.permission(permission); r.allow()
        r.permission(CollectionConst.SELECT); r.deny()
    }
    @ParameterizedTest @ValueSource(strings=[CollectionConst.ROLE_CONSERVATION, CollectionConst.ROLE_STORAGE_HEAD])
    fun `sampling originals follow current read only duty and museum`(role: String) = fixture { r ->
        r.seal(CollectionConst.BIZ_SAMPLING)
        r.permission()
        r.deny()
        r.db { actorRole("reader", role) }
        r.allow()
        r.db { UserDao["reader"].groupId="other" }
        r.deny()
        r.db { UserDao["reader"].groupId="museum"; UserDao["reader"].enabled=false }
        r.deny()
    }
    @Test fun `acquisition archive accepts dedicated confidential read only permission`() = fixture { r ->
        r.seal(CollectionConst.BIZ_ACQUISITION)
        r.deny()
        r.permission(CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT)
        r.allow()
        r.permission(CollectionConst.SELECT)
        r.deny()
    }
    @Test fun `designated reception original accepts confidential read only permission without acquisition write`() = fixture { r ->
        r.seal(DesignatedReceptionEvidence.BIZ)
        r.permission(CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT)
        r.allow()
        r.permission(CollectionConst.SELECT)
        r.deny()
    }
    @Test fun `catalog auditor can read receipt original without seeing confidential acquisition original`() = fixture { r ->
        r.seal(AccessionReceiptService.BIZ, file = "proof")
        r.seal(CollectionConst.BIZ_ACQUISITION, id = "acquisition", file = "review")
        r.permission(CollectionConst.CATALOG_AUDIT)
        r.allow("proof.txt")
        r.deny("review.txt")
        r.seal(DesignatedReceptionEvidence.BIZ, id = "unrelated-reception", file = "proof")
        r.deny("proof.txt")
        r.permission()
        r.deny("proof.txt")
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_REQUISITION", "COLLECTION_RETURN", "COLLECTION_LOAN", "COLLECTION_INVENTORY_BATCH", "COLLECTION_GRADING", "COLLECTION_ACCESSION_RECEIPT", "COLLECTION_CHANGE", "COLLECTION_WAREHOUSE_DIARY", "COLLECTION_CALENDAR"])
    fun `ordinary business originals retain current scoped reading`(biz: String) = fixture { r ->
        r.seal(biz); r.allow(); r.permission(); r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["P", "KEEPER", "USE", "DIRECTOR"])
    fun `current joint safety permission reads custody originals but does not disclose original outgoing files`(phase:String)=fixture {r->
        r.seal(CollectionConst.BIZ_REQUISITION,phase="CUSTODY_${phase}_123")
        r.permission(CollectionConst.CARE);r.allow()
        r.seal(CollectionConst.BIZ_REQUISITION,phase="OUT_EXECUTED")
        r.deny() // 同一文件还被原出库使用时，原出库读取条件继续生效。
        r.permission(CollectionConst.CARE,CollectionConst.STORAGE);r.allow()
        r.permission();r.deny()
    }
    @Test fun `custody original follows current museum membership despite old administrator session`()=fixture {r->
        r.seal(CollectionConst.BIZ_REQUISITION,phase="CUSTODY_P_123")
        r.permission(CollectionConst.CARE);r.allow()
        r.db {UserDao["reader"].groupId="other"};r.deny()
        r.permission(CollectionConst.CARE,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false};r.deny()
    }
    @Test fun `custody phase prefix alone cannot grant unrelated archive access`()=fixture {r->
        r.seal(CollectionConst.BIZ_REQUISITION,phase="CUSTODY_UNRECOGNIZED_123")
        r.permission(CollectionConst.CARE);r.deny()
        r.permission(CollectionConst.STORAGE);r.allow()
    }
    @Test fun `current joint safety permission reads actual early return notice only`()=fixture {r->
        r.seal(CollectionConst.BIZ_REQUISITION,phase="EARLY_RETURN_123")
        r.permission(CollectionConst.CARE);r.allow()
        r.seal(CollectionConst.BIZ_REQUISITION,phase="EARLY_RETURN_UNRECOGNIZED_123")
        r.deny()
        r.permission(CollectionConst.STORAGE);r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["disabled", "locked", "role_removed", "role_disabled", "moved", "anonymous"])
    fun `old administrator login cannot survive current account changes`(kind: String) = fixture { r ->
        r.seal(CollectionConst.BIZ_RETURN); r.allow()
        r.db { when(kind) {
            "disabled" -> UserDao["reader"].enabled=false
            "locked" -> Users.update({ Users.id eq "reader" }) { it[accountNonLocked]=false }
            "role_removed" -> RoleUsers.deleteAll()
            "role_disabled" -> Roles.update { it[enabled]=false }
            "moved" -> UserDao["reader"].groupId="other"
            "anonymous" -> SecurityContextHolder.clearContext()
        } }; r.deny()
    }
    @Test fun `cross museum permission must accompany business authority for shared originals`() = multiFixture { r ->
        r.seal(LedgerReconciliationService.BIZ); r.seal(LedgerReconciliationService.BIZ, "other", "other-case")
        r.permission(CollectionConst.LEDGER_FINANCE); r.deny()
        r.permission(CollectionConst.CROSS_ORG); r.deny()
        r.permission(CollectionConst.CROSS_ORG, CollectionConst.LEDGER_FINANCE); r.allow()
    }
    @Test fun `both transfer parties can read joint handover without membership in both museums`() = fixture { r ->
        r.db { CollectionTransferDao.new("transfer") { orgId="museum"; collectionId="one"; toOrgId="other" } }
        r.seal(CollectionConst.BIZ_TRANSFER, id="transfer", phase="EXECUTED")
        r.seal(CollectionConst.BIZ_TRANSFER, org="other", id="transfer", phase="RECEIVED")
        r.allow(); r.db { UserDao["reader"].groupId="other" }; r.allow()
        r.db { UserDao["reader"].groupId=null }; r.deny()
    }
    @Test fun `a shared file retains every private business restriction`() = fixture { r ->
        r.seal(CollectionConst.BIZ_RETURN); r.seal(LedgerReconciliationService.BIZ, id="finance")
        r.deny(); r.permission(CollectionConst.SELECT, CollectionConst.LEDGER_FINANCE); r.allow()
    }
    @Test fun `unknown and duplicate business policies fail closed`() = fixture { r ->
        // 模拟来自未安装业务模块的历史档案，不能通过新业务入口创建无策略记录。
        r.db {
            val a=CollectionPermanentArchiveDao.new { orgId="museum";bizType="UNREGISTERED_ARCHIVE";bizId="old";phase="LEGACY";snapshot="{}";digest=CollectionCareEvidence.digest(snapshot) }
            CollectionPermanentArchiveFileDao.new { orgId="museum";archiveId=a.id.value;bizId="old";fileId="proof";metadata=JsonUtils.toJson(FileDao["proof"].toModel())!! }
        }
        r.permission("ADMIN"); r.deny()
        r.c.beanFactory.registerSingleton("unknownOne", object:CollectionArchiveReadPolicy { override val businessTypes=setOf("UNREGISTERED_ARCHIVE"); override fun requireReadable(reference:CollectionArchiveFileReference) {} })
        r.allow()
        r.c.beanFactory.registerSingleton("unknownTwo", object:CollectionArchiveReadPolicy { override val businessTypes=setOf("UNREGISTERED_ARCHIVE"); override fun requireReadable(reference:CollectionArchiveFileReference) {} })
        r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["symlink", "hardlink", "rename", "file_row_changed"])
    fun `frozen native identity and paths retain protection`(kind: String) = fixture { r ->
        r.seal(LedgerReconciliationService.BIZ)
        val alias="alias.txt"
        when(kind) {
            "symlink" -> java.nio.file.Files.createSymbolicLink(dir.resolve(alias), Path.of("proof.txt"))
            "hardlink" -> java.nio.file.Files.createLink(dir.resolve(alias), dir.resolve("proof.txt"))
            "rename" -> java.nio.file.Files.move(dir.resolve("proof.txt"), dir.resolve(alias))
            else -> r.db { FileDao["proof"].path="replacement.txt" }
        }
        r.deny(if(kind=="file_row_changed")"proof.txt" else alias)
        if(kind=="file_row_changed")r.deny("replacement.txt")
    }
    @Test fun `same byte replacement cannot use generic permanent original read routes`() = fixture { r ->
        r.seal(LedgerReconciliationService.BIZ)
        r.permission(CollectionConst.LEDGER_FINANCE)
        r.allow()
        val frozen = r.db { CollectionPermanentArchiveFileDao.all().single().metadata }
        val original = dir.resolve("proof.txt")
        val replacement = dir.resolve("same-byte-copy.txt")
        java.nio.file.Files.copy(original, replacement)
        java.nio.file.Files.move(replacement, original, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertEquals("CONTENT:proof.txt", java.nio.file.Files.readString(original))
        r.deny()
        r.db { assertEquals(frozen, CollectionPermanentArchiveFileDao.all().single().metadata) }
    }
    @Test fun `audited restore binds copied bytes to one new file identity without changing original archive`() = fixture { r ->
        r.seal(LedgerReconciliationService.BIZ)
        r.permission(CollectionConst.LEDGER_FINANCE)
        r.allow()
        val frozen = r.db { CollectionPermanentArchiveFileDao.all().single().metadata }
        val original = dir.resolve("proof.txt")
        val copy = dir.resolve("recovered-copy.txt")
        java.nio.file.Files.copy(original, copy)
        java.nio.file.Files.move(copy, original, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        r.deny()
        val referenceId = r.db { CollectionPermanentArchiveFileDao.all().single().id.value }
        val report = JsonUtils.toJson(mapOf(
            "format" to "collection-restored-content-check-v3",
            "result" to "ALL_CONTENT_REFERENCES_AND_ARCHIVES_MATCH",
            "inventorySha256" to "a".repeat(64),
            "expectedReferences" to 1,
            "counts" to mapOf("CONTENT_MATCH" to 1),
            "results" to listOf(mapOf("referenceId" to referenceId, "status" to "CONTENT_MATCH")),
            "restoredReferences" to mapOf("result" to "MATCH", "independentReferenceCount" to 1,
                "counts" to mapOf("REFERENCE_MATCH" to 1)),
            "restoredArchives" to mapOf("result" to "MATCH", "expectedArchives" to 1,
                "independentRestoredArchiveCount" to 1, "counts" to mapOf("ARCHIVE_MATCH" to 1),
                "sourceReferenceLinks" to mapOf("result" to "MATCH"),
                "restoredReferenceLinks" to mapOf("result" to "MATCH")),
        ))!!
        fun sha(value: String) = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
        val request = CollectionPermanentOriginalRecoveryRequest("proof", "a".repeat(64), "b".repeat(64),
            "c".repeat(64), sha(report), report, "隔离恢复清单已逐条核对数据库归档引用与复制后文件的原始字节，登记当前文件身份")
        val service = r.c.getBean(CollectionPermanentOriginalRecoveryService::class.java)
        assertThrows(BadRequestException::class.java) { service.record(request) } // 登录令牌的管理员声明不能代替当前角色。
        login("archivist")
        assertThrows(BadRequestException::class.java) { service.record(request.copy(restoreReportSha256 = "invalid")) }
        val missing = report.replace(referenceId, "missing-ref")
        assertThrows(BadRequestException::class.java) { service.record(request.copy(verificationReport = missing, restoreReportSha256 = sha(missing))) }
        assertEquals("proof", service.record(request))
        assertEquals("proof", service.record(request))
        login()
        r.db {
            assertEquals(1L, CollectionPermanentOriginalRecoveryDao.count())
            assertEquals(frozen, CollectionPermanentArchiveFileDao.all().single().metadata)
        }
        r.allow()
        assertEquals(EnumDeploymentCheckState.PASS, r.db { CollectionPermanentOriginalIdentityInspection.inspect(r.c.getBean(LocalFileObjectStorage::class.java)).state })
        r.permission(); r.deny()
        r.permission(CollectionConst.LEDGER_FINANCE)
        val next = dir.resolve("next-copy.txt")
        java.nio.file.Files.copy(original, next)
        java.nio.file.Files.move(next, original, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        r.deny()
        assertEquals(EnumDeploymentCheckState.FAIL, r.db { CollectionPermanentOriginalIdentityInspection.inspect(r.c.getBean(LocalFileObjectStorage::class.java)).state })
        login("archivist")
        assertEquals("proof", service.record(request.copy(reason = request.reason + "，再次恢复并保留旧承接记录")))
        login()
        r.db { assertEquals(2L, CollectionPermanentOriginalRecoveryDao.count()) }
        r.allow()
        assertEquals(EnumDeploymentCheckState.PASS, r.db { CollectionPermanentOriginalIdentityInspection.inspect(r.c.getBean(LocalFileObjectStorage::class.java)).state })
        r.db { CollectionPermanentOriginalRecoveryDao.all().maxBy { it.id.value }.restoredStableKey = "u" + "0".repeat(63) }
        r.deny()
        assertEquals(EnumDeploymentCheckState.FAIL, r.db { CollectionPermanentOriginalIdentityInspection.inspect(r.c.getBean(LocalFileObjectStorage::class.java)).state })
    }
    @Test fun `recorded device transition releases only the same archived inode and bytes`() = fixture { r ->
        r.seal(LedgerReconciliationService.BIZ)
        val local = r.c.getBean(LocalFileObjectStorage::class.java)
        val native = local.readIdentity("proof.txt").nativeIdentity!!
        val device = Regex("dev=([0-9a-fA-F]+)").find(native)!!.groupValues[1].toLong(16)
        val former = (device - 1).toString(16)
        val priorNative = native.replace("dev=${device.toString(16)}", "dev=$former")
        val oldKey = "n" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(priorNative.toByteArray(Charsets.UTF_8))).take(63)
        r.db {
            val archive = CollectionPermanentArchiveDao.all().single()
            val reference = CollectionPermanentArchiveFileDao.all().single()
            val file = FileDao["proof"]
            val metadata = JsonUtils.mapper.readTree(reference.metadata)
            val frozen = CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(
                archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase,
                reference.fileId, reference.metadata), metadata)
            reference.metadata = CollectionArchiveFileIdentity.metadata(file, archive.orgId, archive.bizType,
                archive.bizId, frozen.filterNot { it.startsWith("n") || it.startsWith("u") }.toSet() + oldKey,
                CollectionArchiveFileIdentity.content(metadata))
        }
        r.permission(CollectionConst.LEDGER_FINANCE)
        r.deny()
        val reason = "开发环境卷设备号变更，逐文件核对冻结身份、创建时间和实际原件字节"
        val service = r.c.getBean(CollectionPermanentOriginalTransitionService::class.java)
        val admin = SecurityContextHolder.getContext().authentication
        val reader = SecurityUser(id="reader", name="reader", username="reader", password="", groupId="museum",
            groupIds=setOf("museum"), postIds=emptySet(), dataType=0, roles=emptySet(),
            authorities=listOf(SimpleGrantedAuthority(CollectionConst.SELECT)), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(reader, null, reader.authorities)
        assertThrows(BadRequestException::class.java) {
            service.record(CollectionPermanentOriginalTransitionRequest("proof", former, reason))
        }
        SecurityContextHolder.getContext().authentication = admin
        assertThrows(BadRequestException::class.java) {
            service.record(CollectionPermanentOriginalTransitionRequest("proof", (device - 2).toString(16), reason))
        }
        assertEquals("proof", service.record(CollectionPermanentOriginalTransitionRequest("proof", former, reason)))
        assertEquals("proof", service.record(CollectionPermanentOriginalTransitionRequest("proof", former, reason)))
        r.db { assertEquals(1L, CollectionPermanentOriginalTransitionDao.count()) }
        r.allow()
        assertEquals(EnumDeploymentCheckState.PASS, r.db { CollectionPermanentOriginalIdentityInspection.inspect(local).state })
        val saved = r.db { CollectionPermanentOriginalTransitionDao["proof"].stableKey }
        r.db { CollectionPermanentOriginalTransitionDao["proof"].stableKey = "u" + "0".repeat(63) }
        r.deny()
        assertEquals(EnumDeploymentCheckState.FAIL, r.db { CollectionPermanentOriginalIdentityInspection.inspect(local).state })
        r.db { CollectionPermanentOriginalTransitionDao["proof"].stableKey = saved }
        r.allow()
        val copy = dir.resolve("same-byte-transition-copy.txt")
        java.nio.file.Files.copy(dir.resolve("proof.txt"), copy)
        java.nio.file.Files.move(copy, dir.resolve("proof.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        r.deny()
        assertEquals(EnumDeploymentCheckState.FAIL, r.db { CollectionPermanentOriginalIdentityInspection.inspect(local).state })
    }
    @Test fun `stable archive identity permits a device number change without editing the original`() = fixture { r ->
        r.seal(LedgerReconciliationService.BIZ)
        r.db {
            val archive = CollectionPermanentArchiveDao.all().single()
            val reference = CollectionPermanentArchiveFileDao.all().single()
            val file = FileDao["proof"]
            val old = JsonUtils.mapper.readTree(reference.metadata)
            val keys = CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(
                archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase,
                file.id.value, reference.metadata), old)
            assertTrue(keys.any { it.startsWith("u") })
            reference.metadata = CollectionArchiveFileIdentity.metadata(file, archive.orgId, archive.bizType,
                archive.bizId, keys.filterNot { it.startsWith("n") }.toSet() + ("n" + "0".repeat(63)),
                CollectionArchiveFileIdentity.content(old))
        }
        r.permission(CollectionConst.LEDGER_FINANCE)
        r.allow()
    }
    @Test fun `legacy archive metadata is protected and replay remains immutable`() = fixture { r ->
        val id=r.seal(LedgerReconciliationService.BIZ)
        val old=r.db { CollectionPermanentArchiveFileDao.all().single().apply { metadata=JsonUtils.toJson(FileDao["proof"].toModel())!! }.metadata }
        r.deny(); assertEquals(id, r.seal(LedgerReconciliationService.BIZ))
        r.db { assertEquals(old, CollectionPermanentArchiveFileDao.all().single().metadata) }
        r.permission(CollectionConst.LEDGER_FINANCE); r.allow()
    }

    private fun sealingState(r: Runtime) = r.db {
        listOf(CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents).associate { table ->
            table.tableName to table.selectAll().map { row -> table.columns.map { column -> row[column]?.toString() } }
                .sortedBy { it.toString() }
        }
    }

    private fun assertSealingRetryManifest(r: Runtime, kind: String, inherited: Boolean) {
        val biz = LedgerReconciliationService.BIZ
        val source = r.seal(biz, phase = "SOURCE")
        val other = r.seal(biz, file = "public", phase = "OTHER")
        val sources = if (inherited) r.db { mapOf(source to CollectionPermanentArchiveDao[source].digest) } else null
        val archive = r.seal(biz, sources = sources)
        val saved = r.db {
            CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive }.single().let {
                listOf(it.id.value, it.orgId, it.archiveId, it.bizId, it.fileId, it.metadata)
            }
        }
        var added: String? = null
        val request = when (kind) {
            "REQUEST_CHANGED" -> "public"
            "REQUEST_OMITTED" -> ""
            "REQUEST_EXTRA" -> "proof,public"
            else -> "proof"
        }
        try {
            r.db {
                when (kind) {
                    "MISSING" -> CollectionPermanentArchiveFileDao[saved[0]].delete()
                    "REPLACED" -> CollectionPermanentArchiveFileDao[saved[0]].apply {
                        fileId = "public"
                        metadata = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq other }.single().metadata
                    }
                    "EXTRA" -> {
                        val extra = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq other }.single()
                        added = CollectionPermanentArchiveFileDao.new {
                            orgId = extra.orgId; archiveId = archive; bizId = extra.bizId; fileId = extra.fileId; metadata = extra.metadata
                        }.id.value
                    }
                }
            }
            val before = sealingState(r)
            assertThrows(BadRequestException::class.java) { r.seal(biz, file = request, sources = sources) }
            assertEquals(before, sealingState(r), "A rejected retry must not replace, bind or append originals")
        } finally {
            r.db {
                added?.let { CollectionPermanentArchiveFileDao[it].delete() }
                (CollectionPermanentArchiveFileDao.findById(saved[0]) ?: CollectionPermanentArchiveFileDao.new(saved[0]) {
                    orgId = saved[1]; archiveId = saved[2]; bizId = saved[3]; fileId = saved[4]; metadata = saved[5]
                }).apply { orgId = saved[1]; archiveId = saved[2]; bizId = saved[3]; fileId = saved[4]; metadata = saved[5] }
            }
        }
        val restored = sealingState(r)
        assertEquals(archive, r.seal(biz, sources = sources))
        assertEquals(restored, sealingState(r), "Restoring the exact originals permits an immutable retry")
        assertEquals("CONTENT:proof.txt", java.nio.file.Files.readString(dir.resolve("proof.txt")))
        assertEquals("CONTENT:public.txt", java.nio.file.Files.readString(dir.resolve("public.txt")))
    }

    @ParameterizedTest @CsvSource(
        "MISSING,false", "REPLACED,false", "EXTRA,false", "REQUEST_CHANGED,false", "REQUEST_OMITTED,false", "REQUEST_EXTRA,false",
        "MISSING,true", "REPLACED,true", "EXTRA,true", "REQUEST_CHANGED,true", "REQUEST_OMITTED,true", "REQUEST_EXTRA,true"
    )
    fun `archive sealing retry rejects changed declared originals`(kind: String, inherited: Boolean) = fixture { r ->
        assertSealingRetryManifest(r, kind, inherited)
    }

    @Tag("postgres-concurrency")
    @ParameterizedTest @CsvSource(
        "MISSING,false", "REPLACED,false", "EXTRA,false", "REQUEST_CHANGED,false", "REQUEST_OMITTED,false", "REQUEST_EXTRA,false",
        "MISSING,true", "REPLACED,true", "EXTRA,true", "REQUEST_CHANGED,true", "REQUEST_OMITTED,true", "REQUEST_EXTRA,true"
    )
    fun `postgres archive sealing retry rejects changed declared originals`(kind: String, inherited: Boolean) = postgresFixture { r ->
        assertSealingRetryManifest(r, kind, inherited)
    }

    private fun assertSealingRetryCompatibility(r: Runtime, kind: String) {
        val biz = LedgerReconciliationService.BIZ
        val file = when (kind) { "EMPTY" -> ""; "ORDER" -> "proof,public"; "RENAMED_PATH" -> "proof.txt"; else -> "proof" }
        val archive = r.seal(biz, file = file)
        if (kind == "RENAMED_PATH") {
            java.nio.file.Files.move(dir.resolve("proof.txt"), dir.resolve("renamed-proof.txt"))
            r.db { FileDao["proof"].apply { name = "renamed-proof.txt"; path = "renamed-proof.txt" } }
        }
        val before = sealingState(r)
        val request = if (kind == "ORDER") "public,proof" else file
        assertEquals(archive, r.seal(biz, file = request))
        assertEquals(before, sealingState(r))
        r.permission(CollectionConst.LEDGER_FINANCE)
        if (kind == "RENAMED_PATH") {
            val response = MockHttpServletResponse()
            r.controller.downloadFile(MockHttpServletRequest("GET", "/userfiles/renamed-proof.txt"), response)
            assertEquals("CONTENT:proof.txt", response.contentAsString)
            assertEquals("private, no-store", response.getHeader("Cache-Control"))
        } else if (kind != "EMPTY") r.allow()
    }

    @ParameterizedTest @ValueSource(strings = ["UNCHANGED", "EMPTY", "ORDER", "RENAMED_PATH"])
    fun `archive sealing retry preserves unchanged originals and old paths`(kind: String) = fixture { r ->
        assertSealingRetryCompatibility(r, kind)
    }

    @Tag("postgres-concurrency")
    @ParameterizedTest @ValueSource(strings = ["UNCHANGED", "EMPTY", "ORDER", "RENAMED_PATH"])
    fun `postgres archive sealing retry preserves unchanged originals and old paths`(kind: String) = postgresFixture { r ->
        assertSealingRetryCompatibility(r, kind)
    }
    @ParameterizedTest @ValueSource(strings=["identity", "org", "path"])
    fun `archive read identity corruption cannot release the file`(kind:String) = fixture { r ->
        r.seal(CollectionConst.BIZ_RETURN)
        r.db {
            val ref=CollectionPermanentArchiveFileDao.all().single()
            if(kind=="org")ref.orgId="other" else {
                val json=JsonUtils.mapper.readTree(ref.metadata) as tools.jackson.databind.node.ObjectNode
                json.put(if(kind=="path")"path" else "_archiveReadIdentityDigest", "tampered"); ref.metadata=json.toString()
            }
        }; r.deny()
    }
    @Test fun `missing actual local original rolls back archive and file references`() = fixture { r ->
        java.nio.file.Files.delete(dir.resolve("proof.txt"))
        assertThrows(BadRequestException::class.java) { r.seal(CollectionConst.BIZ_RETURN) }
        r.db { assertEquals(0L, CollectionPermanentArchiveDao.count()); assertEquals(0L, CollectionPermanentArchiveFileDao.count()); assertEquals(0L, FileContents.selectAll().count()) }
    }
    @Test fun `missing archive header does not turn retained file reference public`() = fixture { r ->
        val id=r.seal(CollectionConst.BIZ_RETURN)
        r.db { CollectionPermanentArchiveDao[id].delete() }
        SecurityContextHolder.clearContext(); r.deny()
        val response=MockHttpServletResponse()
        r.controller.downloadFile(MockHttpServletRequest("GET","/userfiles/public.txt"),response)
        assertEquals("CONTENT:public.txt",response.contentAsString)
    }
    @Test fun `cached attachment list preview temporary read and zip recheck archive duties`() = fixture { r ->
        val archive=r.seal(LedgerReconciliationService.BIZ);r.permission(CollectionConst.LEDGER_FINANCE)
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing();r.permission(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java){listing()}
        val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",response)}
        verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
        val zipped=MockHttpServletResponse();assertThrows(BadRequestException::class.java){files.zipContentFile(archive,zipped)};assertEquals(0,zipped.contentAsByteArray.size)
        assertThrows(BadRequestException::class.java){storage.withTempFile("proof.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
    }
    @Test fun `shared original checks every reference including final denial`() = fixture { r ->
        assertSharedReferenceAuthorization(r)
    }

    @Tag("postgres-concurrency")
    @Test fun `shared original cursor permits policy queries and rejects final denial`() = postgresFixture { r ->
        assertSharedReferenceAuthorization(r)
    }

    private fun assertSharedReferenceAuthorization(r: Runtime) {
        r.db {
            val saved = JsonUtils.toJson(FileDao["proof"].toModel())!!
            repeat(1001) { i ->
                val archive = CollectionPermanentArchiveDao.new {
                    orgId="museum"; bizType="STREAMING_CHECK"; bizId="case-$i"; phase="LEGACY"
                    snapshot="{}"; digest=CollectionCareEvidence.digest(snapshot)
                }.id.value
                CollectionPermanentArchiveFileDao.new("shared-${i.toString().padStart(5, '0')}") {
                    orgId="museum"; archiveId=archive; bizId="case-$i"; fileId="proof"; metadata=saved
                }
                if ((i + 1) % 250 == 0) {
                    val cache = org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache
                    cache.flush(); cache.clear()
                }
            }
        }
        var visited = 0
        var denyLast = true
        r.c.beanFactory.registerSingleton("sharedReferencePolicy", object : CollectionArchiveReadPolicy {
            override val businessTypes = setOf("STREAMING_CHECK")
            override fun requireReadable(reference: CollectionArchiveFileReference) {
                // Actual policy queries must coexist with the archive cursor across fetch batches.
                assertEquals(1L, Files.selectAll().where { Files.id eq "proof" }.count())
                visited++
                if (denyLast && visited == 1001) throw BadRequestException("final reference denied")
            }
        })
        val guard = r.c.getBean(CollectionPermanentArchiveFileReadGuard::class.java)
        val rejected = assertThrows(BadRequestException::class.java) { r.db { guard.readEvidence("proof.txt", "LOCAL") } }
        assertEquals("final reference denied", rejected.message)
        assertEquals(1001, visited)
        visited = 0; denyLast = false
        val evidence = r.db { guard.readEvidence("proof.txt", "LOCAL") }
        assertTrue(evidence.protected)
        assertTrue(evidence.baselines.isEmpty())
        assertTrue(evidence.revisions.isEmpty())
        assertEquals(1001, visited)
    }

    private fun manyArchiveFiles(r: Runtime, count: Int): String {
        val archive=r.seal(LedgerReconciliationService.BIZ)
        r.db {
            val target=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive}.single()
            val saved=target.metadata; target.delete()
            repeat(count) { i ->
                val id="noise-$i"
                FileDao.new(id) {name="$id.txt";path="missing/$id.txt";hash=id;ext="txt";size=1}
                CollectionPermanentArchiveFileDao.new("a-${i.toString().padStart(5,'0')}") {
                    orgId="museum";archiveId=archive;bizId="case";fileId=id
                    metadata=JsonUtils.toJson(mapOf("id" to id,"path" to "missing/$id.txt","storageType" to "LOCAL"))!!
                }
                // Keep fixture construction separate from the measured read; bound pending DAO entities.
                if ((i + 1) % 250 == 0) {
                    val cache = org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache
                    cache.flush()
                    cache.clear()
                }
            }
            CollectionPermanentArchiveFileDao.new("zz-target") {orgId="museum";archiveId=archive;bizId="case";fileId="proof";metadata=saved}
        }
        return archive
    }

    @Test fun `archive guard query count grows by batches with a target beyond one thousand files`() = fixture { r ->
        assertArchiveQueryBatches(r,"SQLite")
    }

    @Tag("postgres-concurrency") @Test
    fun `archive guard batches actual PostgreSQL file identities and retains scoped download`() = postgresFixture { r ->
        assertArchiveQueryBatches(r,"PostgreSQL")
    }

    @Tag("postgres-concurrency") @Test
    fun `archive guard scale baseline keeps authorization with ten thousand historical references`() = postgresFixture { r ->
        assertArchiveQueryBatches(r,"PostgreSQL",10000)
    }

    @Tag("postgres-concurrency") @Test
    fun `archive guard scale baseline keeps authorization with one hundred thousand historical references`() = postgresFixture { r ->
        assertArchiveQueryBatches(r,"PostgreSQL",100000)
    }

    private fun assertArchiveQueryBatches(r: Runtime, database: String, count: Int = 1000) {
        manyArchiveFiles(r,count)
        val guard=r.c.getBean(CollectionPermanentArchiveFileReadGuard::class.java)
        for((path,expected) in listOf("proof.txt" to true,"public.txt" to false)) {
            var selects=0
            val started=System.nanoTime()
            r.db {
                val logger=object:SqlLogger {
                    override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {
                        if(context.statement.type == org.jetbrains.exposed.v1.core.statements.StatementType.SELECT) selects++
                    }
                }
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(logger)
                assertEquals(expected,guard.isProtected(path,"LOCAL"))
            }
            val elapsed=(System.nanoTime()-started)/1_000_000
            println("ARCHIVE_GUARD_QUERY_OBSERVATION "+JsonUtils.toJson(mapOf("database" to database,"unrelatedFiles" to count,"protected" to expected,"selects" to selects,"elapsedMillis" to elapsed)))
            assertTrue(selects <= 2, "A single file check issued $selects SELECTs for ${count + 1} archive references")
        }
        r.deny();r.permission(CollectionConst.LEDGER_FINANCE);r.allow()
    }

    @Test fun `one read authorization scans permanent references once and rechecks revocation`() = fixture { r ->
        manyArchiveFiles(r,1000)
        r.permission(CollectionConst.LEDGER_FINANCE)
        val beans=org.springframework.beans.factory.support.DefaultListableBeanFactory()
        beans.registerSingleton("archiveGuard",r.c.getBean(CollectionPermanentArchiveFileReadGuard::class.java))
        val access=FileReadAccess(beans.getBeanProvider(FileReadGuard::class.java))
        var scans=0
        r.db {
            val logger=object:SqlLogger {
                override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {
                    if(context.statement.type == org.jetbrains.exposed.v1.core.statements.StatementType.SELECT &&
                        context.statement.prepareSQL(transaction).contains("col_permanent_archive_file")) scans++
                }
            }
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(logger)
            assertTrue(access.requireReadable("proof.txt","LOCAL"))
        }
        assertEquals(1,scans,"One authorization should scan the 1,001 references only once")
        r.permission(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { access.requireReadable("proof.txt","LOCAL") }
        assertFalse(access.requireReadable("public.txt","LOCAL"))
    }

    @Test fun `one evidence read scans permanent references once and rechecks revocation`() = fixture { r ->
        manyArchiveFiles(r,1000)
        r.permission(CollectionConst.LEDGER_FINANCE)
        val beans=org.springframework.beans.factory.support.DefaultListableBeanFactory()
        beans.registerSingleton("archiveGuard",r.c.getBean(CollectionPermanentArchiveFileReadGuard::class.java))
        val access=FileReadAccess(beans.getBeanProvider(FileReadGuard::class.java))
        var scans=0
        r.db {
            val logger=object:SqlLogger {
                override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {
                    if(context.statement.type == org.jetbrains.exposed.v1.core.statements.StatementType.SELECT &&
                        context.statement.prepareSQL(transaction).contains("col_permanent_archive_file")) scans++
                }
            }
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(logger)
            assertTrue(access.readEvidence("proof.txt","LOCAL").location.protected)
        }
        assertEquals(1,scans,"One authorization should scan the 1,001 references only once")
        r.permission(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { access.readEvidence("proof.txt","LOCAL").location.protected }
        assertFalse(access.readEvidence("public.txt","LOCAL").location.protected)
    }

    @ParameterizedTest @ValueSource(strings=["missing_file", "missing_archive", "current_path", "corrupted_metadata", "foreign_reference"])
    fun `archive guard preserves frozen identity and failed evidence beyond the first batch`(change:String) = fixture { r ->
        val archive=manyArchiveFiles(r,210)
        r.db {when(change) {
            "missing_file" -> FileDao["proof"].delete()
            "missing_archive" -> CollectionPermanentArchiveDao[archive].delete()
            "current_path" -> FileDao["proof"].path="replacement.txt"
            "corrupted_metadata" -> CollectionPermanentArchiveFileDao["zz-target"].metadata="{invalid"
            "foreign_reference" -> CollectionPermanentArchiveFileDao["zz-target"].orgId="other"
        } }
        val guard=r.c.getBean(CollectionPermanentArchiveFileReadGuard::class.java)
        val path=if(change=="current_path")"replacement.txt" else "proof.txt"
        if(change in setOf("missing_archive","corrupted_metadata","foreign_reference","current_path"))
            assertThrows(BadRequestException::class.java) {guard.isProtected(path,"LOCAL")}
        else assertTrue(guard.isProtected(path,"LOCAL"))
        r.deny(path)
        if(change=="missing_file") {
            r.permission(CollectionConst.LEDGER_FINANCE)
            assertDoesNotThrow {guard.requireReadable(path,"LOCAL")}
        }
    }

    private fun timelineOriginal(r: Runtime, phase: String = WarehouseEntryTimelineService.PHASE + "legacy-key", org: String = "museum", id: String = "case"): String =
        r.seal(CollectionConst.BIZ_WAREHOUSE_ENTRY, org=org, id=id, phase=phase,
            content=mapOf("kind" to "ONSITE_TIME_REVIEW", "entryId" to id, "content" to mapOf("statement" to "受限核查情况", "baseline" to "previous")))

    @ParameterizedTest @CsvSource(
        "legacy-key,COLLECTION_SELECT", "legacy-key,COLLECTION_WAREHOUSE_ENTRY_APPLY",
        "0123456789012345678901234567890123456789,COLLECTION_SELECT",
        "0123456789012345678901234567890123456789,COLLECTION_WAREHOUSE_ENTRY_APPLY")
    fun `timeline signed originals require verification duty for legacy and hashed phases`(suffix: String, permission: String) = fixture { r ->
        timelineOriginal(r, WarehouseEntryTimelineService.PHASE + suffix)
        r.permission(permission); r.deny()
        r.permission(CollectionConst.WAREHOUSE_ENTRY_VERIFY); r.allow()
        r.permission(permission); r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["COLLECTION_SELECT", "COLLECTION_WAREHOUSE_ENTRY_APPLY", "COLLECTION_WAREHOUSE_ENTRY_VERIFY"])
    fun `ordinary entry detail excludes restricted review while its dedicated endpoint rechecks duty`(permission: String) = fixture { r ->
        r.db { WarehouseEntryApplicationDao.new("case") {
            orgId="museum"; applicationNo="ENTRY-1"; applicantId="reader"; entrantIds="reader"; warehouseIds="";
            purpose="查看入库申请"; plannedStart=at; plannedEnd=at.plusHours(1)
        } }
        val ordinary=r.seal(CollectionConst.BIZ_WAREHOUSE_ENTRY, file="public", phase="SUBMITTED")
        val restricted=timelineOriginal(r)
        r.permission(permission)
        val result=r.db {
            WarehouseEntryApplicationService(mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java),
                r.c.getBean(CollectionScopeResolver::class.java), CollectionUserResolver(), r.c.getBean(BusinessAttachmentBinder::class.java),
                mock(WarehouseEntryStatutoryService::class.java), mock(WarehouseStaffingService::class.java), r.archives).get("case")
        }
        assertEquals(listOf(ordinary), result.archives.map { it["id"] })
        assertFalse(JsonUtils.toJson(result)!!.contains("受限核查情况"))
        r.allow("public.txt")
        val timeline=r.c.getBean(WarehouseEntryTimelineService::class.java)
        if(permission==CollectionConst.WAREHOUSE_ENTRY_VERIFY) {
            assertEquals(listOf(restricted),timeline.get("case").reviews.map {it["id"]}); r.allow()
        } else { assertThrows(BadRequestException::class.java) {timeline.get("case")}; r.deny() }
    }

    @ParameterizedTest @ValueSource(strings=["disabled", "role_removed", "moved", "anonymous"])
    fun `timeline original checks current account despite old administrator login`(change: String) = fixture { r ->
        timelineOriginal(r); r.permission(CollectionConst.WAREHOUSE_ENTRY_VERIFY); r.allow()
        r.db { when(change) {
            "disabled" -> UserDao["reader"].enabled=false
            "role_removed" -> RoleUsers.deleteWhere { RoleUsers.userId eq "reader" }
            "moved" -> UserDao["reader"].groupId="other"
            "anonymous" -> SecurityContextHolder.clearContext()
        } }; r.deny()
    }

    @Test fun `timeline original restrictions also cover cached lists previews temporary reads and zip`() = fixture { r ->
        val archive=timelineOriginal(r); r.permission(CollectionConst.WAREHOUSE_ENTRY_VERIFY)
        val files=r.c.getBean(FileService::class.java); val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing(); listing(); r.permission(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) {listing()}
        val converter=mock(FileConvertService::class.java); val preview=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java) {FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",preview)}
        verifyNoInteractions(converter); assertEquals(0,preview.contentAsByteArray.size)
        val zip=MockHttpServletResponse(); assertThrows(BadRequestException::class.java) {files.zipContentFile(archive,zip)}
        assertEquals(0,zip.contentAsByteArray.size)
        assertThrows(BadRequestException::class.java) {storage.withTempFile("proof.txt","LOCAL",".txt") {throw AssertionError("Must reject before opening")}}
    }

    @Test fun `actual timeline review binds readable signed bytes then revokes every ordinary reader`() = fixture { r ->
        val entry=WarehouseAttendanceVo(at,setOf("reader"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="public")
        r.db { WarehouseEntryApplicationDao.new("case") {
            orgId="museum"; applicationNo="ENTRY-1"; applicantId="reader"; entrantIds="reader"; warehouseIds="";
            purpose="核对历史时间"; plannedStart=at; plannedEnd=at.plusHours(1); actualEntryAt=at; actualExitAt=at.plusMinutes(1); entryRecord=entry
        } }
        r.seal(WarehouseDiaryService.BIZ,id="diary",file="public",phase="RECORDED_1",
            content=mapOf("entries" to WarehouseDiaryEntriesVo(listOf(WarehouseDiaryVisitVo("case","ENTRY-1","核对历史时间",entry,null,at,at.plusHours(1),exitedAt=null)))))
        r.seal(CollectionConst.BIZ_CARE,id="operation",file="replacement",phase="STARTED",
            content=mapOf("started" to mapOf("onSiteWarehouseLogId" to "diary","operatorUserId" to "reader","startedAt" to at.plusMinutes(2))))
        r.permission(CollectionConst.WAREHOUSE_ENTRY_VERIFY)
        val service=r.c.getBean(WarehouseEntryTimelineService::class.java)
        val before=service.get("case"); assertEquals(1,before.conflicts.size)
        val request=WarehouseEntryTimelineReviewVo("actual-key-001",before.baseline,at.plusMinutes(3),true,"逐项核对发现待核实时间差异","保留原件并联系经办人核实","proof")
        val archive=service.review("case",request)
        assertEquals(archive,service.review("case",request))
        assertEquals(listOf(archive),service.get("case").reviews.map {it["id"]}); r.allow()
        r.db {
            assertEquals(1L,CollectionPermanentArchiveFiles.selectAll().where {CollectionPermanentArchiveFiles.archiveId eq archive}.count())
            assertEquals(at.plusMinutes(1),WarehouseEntryApplicationDao["case"].actualExitAt)
        }
        r.permission(CollectionConst.SELECT); r.deny()
        assertThrows(BadRequestException::class.java) {service.get("case")}
        r.permission(CollectionConst.WAREHOUSE_ENTRY_VERIFY); r.allow()
        assertEquals(archive,service.get("case").reviews.single()["id"])
    }

    @Test fun `timeline shared originals retain verification duty and every museum scope`() = multiFixture { r ->
        r.seal(CollectionConst.BIZ_RETURN)
        timelineOriginal(r); timelineOriginal(r,org="other",id="foreign")
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG); r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.WAREHOUSE_ENTRY_VERIFY); r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.WAREHOUSE_ENTRY_VERIFY,CollectionConst.CROSS_ORG); r.allow()
    }

    @ParameterizedTest @ValueSource(strings=["rules", "paper", "finance", "jurisdiction", "feed", "annual_reports", "annual_legacy", "annual_stat", "annual_dashboard", "grading", "staffing"])
    fun `current original readers work through actual SQLite Spring transactions without resealing`(kind:String) = fixture { r ->
        r.permission(CollectionConst.ORG_MANAGE,CollectionConst.LEDGER_FINANCE,CollectionConst.ARCHIVE_MANAGE,CollectionConst.PUBLISH,CollectionConst.STAT,CollectionConst.APPRAISE,CollectionConst.WAREHOUSE_MANAGE)
        val before=r.db { CollectionPermanentArchiveDao.count() }
        when(kind) {
            "staffing" -> {
                r.db {WarehouseDao.new("staffing-store"){orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE}}
                assertEquals("staffing-store",r.c.getBean(WarehouseStaffingService::class.java).get("staffing-store").id)
            }
            "rules" -> assertEquals("museum",r.c.getBean(CollectionManagementRulesService::class.java).detail("museum")["orgId"])
            "paper" -> assertTrue(r.c.getBean(PaperLedgerService::class.java).list("museum").isEmpty())
            "finance" -> assertTrue(r.c.getBean(LedgerReconciliationService::class.java).list("museum").isEmpty())
            "jurisdiction" -> assertTrue(r.c.getBean(CollectionArchivePacketService::class.java).jurisdictions("museum").isEmpty())
            "feed" -> assertTrue(r.disclosures.feed().isEmpty())
            "annual_reports" -> assertEquals("museum",r.c.getBean(AnnualReportEvidenceService::class.java).detail(2025)["orgId"])
            "annual_legacy" -> Assertions.assertNull(r.c.getBean(AnnualReportArchiveService::class.java).get(2025))
            "annual_stat" -> assertEquals("museum",r.c.getBean(CollectionLedgerStatisticsService::class.java).preview(2025).orgId)
            "annual_dashboard" -> assertEquals("museum",r.c.getBean(CollectionDashboardService::class.java).annual(2025).orgId)
            "grading" -> assertEquals("one",r.c.getBean(CollectionGradingService::class.java).detail("one")["collectionId"])
        }
        assertEquals(before,r.db { CollectionPermanentArchiveDao.count() })
        r.permission()
        assertThrows(BadRequestException::class.java) {
            when(kind) {
                "staffing" -> r.c.getBean(WarehouseStaffingService::class.java).get("staffing-store")
                "rules" -> r.c.getBean(CollectionManagementRulesService::class.java).detail("museum")
                "paper" -> r.c.getBean(PaperLedgerService::class.java).list("museum")
                "finance" -> r.c.getBean(LedgerReconciliationService::class.java).list("museum")
                "jurisdiction" -> r.c.getBean(CollectionArchivePacketService::class.java).jurisdictions("museum")
                "annual_reports" -> r.c.getBean(AnnualReportEvidenceService::class.java).detail(2025)
                "annual_legacy" -> r.c.getBean(AnnualReportArchiveService::class.java).get(2025)
                "annual_stat" -> r.c.getBean(CollectionLedgerStatisticsService::class.java).preview(2025)
                "annual_dashboard" -> r.c.getBean(CollectionDashboardService::class.java).annual(2025)
                "grading" -> r.c.getBean(CollectionGradingService::class.java).detail("one")
                else -> r.disclosures.feed()
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["entry", "entry-page", "entry-flow", "diary", "log-page", "entries", "gaps", "timeline"])
    fun `warehouse readers use current identity in actual Spring transactions`(kind:String)=fixture {r ->
        r.db {
            WarehouseDao.new("store"){orgId="museum";name="一号库";sceneType=EnumLocationScene.STORAGE}
            WarehouseEntryApplicationDao.new("visit"){orgId="museum";applicationNo="ENTRY-1";applicantId="reader";entrantIds="reader";warehouseIds="store";purpose="巡库";plannedStart=at;plannedEnd=at.plusHours(1)}
            WarehouseLogDao.new("log"){orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="巡库"}
        }
        r.permission(CollectionConst.SELECT,CollectionConst.WAREHOUSE_ENTRY_VERIFY)
        val count=r.db {CollectionPermanentArchiveDao.count()}
        fun read():Any?=when(kind) {
            "entry" -> r.c.getBean(WarehouseEntryApplicationService::class.java).get("visit")
            "entry-page" -> r.c.getBean(WarehouseEntryApplicationService::class.java).findPage(MockHttpServletRequest())
            "entry-flow" -> r.c.getBean(WarehouseEntryApplicationService::class.java).getFlowInfo("visit")
            "diary" -> r.c.getBean(WarehouseDiaryService::class.java).get("log")
            "log-page" -> r.c.getBean(WarehouseLogService::class.java).findPage(MockHttpServletRequest())
            "entries" -> r.c.getBean(WarehouseDiaryService::class.java).entries("store",at.toLocalDate())
            "gaps" -> r.c.getBean(WarehouseDiaryService::class.java).gaps(at.toLocalDate(),at.toLocalDate())
            else -> r.c.getBean(WarehouseEntryTimelineService::class.java).get("visit")
        }
        assertDoesNotThrow {read()}
        assertEquals(count,r.db {CollectionPermanentArchiveDao.count()})
        r.permission()
        assertThrows(BadRequestException::class.java){read()}
    }
    @Test fun `warehouse diary original requires current museum and archive file membership` ()=fixture {r ->
        r.db {WarehouseLogDao.new("diary-original"){orgId="museum";logDate=at.toLocalDate();content="巡库"}}
        val archive=r.seal(WarehouseDiaryService.BIZ,id="diary-original")
        r.permission(CollectionConst.SELECT)
        val service=r.c.getBean(WarehouseDiaryService::class.java)
        service.requireArchivedOriginal("diary-original",archive,"proof")
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("diary-original","missing","proof")}
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("diary-original",archive,"missing")}
        configureOtherMuseum(r)
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal("diary-original",archive,"proof")}
    }

    @ParameterizedTest @ValueSource(strings=["batch", "configs", "coverage", "annual", "observations", "summaries", "flow", "import"])
    fun `inventory readers enforce current permission through actual Spring transactions`(kind:String)=fixture {r ->
        r.db {
            InventoryBatchDao.new("inventory-batch"){orgId="museum";batchName="年度清点"}
            InventoryDao.new("observation"){orgId="museum";batchId="inventory-batch";batchName="年度清点";collectionId="one"}
        }
        r.permission(CollectionConst.STORAGE)
        val before=r.db {CollectionPermanentArchiveDao.count()}
        fun read():Any?=when(kind) {
            "batch" -> r.c.getBean(InventoryComplianceService::class.java).get("inventory-batch")
            "configs" -> r.c.getBean(InventoryBatchService::class.java).list()
            "coverage" -> r.c.getBean(InventoryComplianceService::class.java).coverage(2026)
            "annual" -> r.c.getBean(InventoryComplianceService::class.java).annualReports(2026)
            "observations" -> r.c.getBean(InventoryService::class.java).findPage(MockHttpServletRequest())
            "summaries" -> r.c.getBean(InventoryService::class.java).batchSummaries(MockHttpServletRequest())
            "flow" -> r.c.getBean(InventoryService::class.java).getFlowInfo("observation")
            else -> r.c.getBean(InventoryService::class.java).validateImport(emptyList())
        }
        assertDoesNotThrow{read()};assertEquals(before,r.db {CollectionPermanentArchiveDao.count()})
        r.permission();assertThrows(BadRequestException::class.java){read()}
    }

    @Test fun `inventory evidence errors return normally through Spring without exposing invalid files`()=fixture {r ->
        r.db {InventoryBatchDao.new("damaged-batch"){orgId="museum";batchName="待核查"}}
        r.seal(InventoryComplianceService.BIZ,id="damaged-batch")
        r.db {CollectionPermanentArchiveDao.all().single().digest="bad-digest"}
        r.permission(CollectionConst.STORAGE)
        val result=r.c.getBean(InventoryComplianceService::class.java).get("damaged-batch")
        assertTrue(result.evidenceErrors.isNotEmpty());assertTrue(result.archives.isEmpty())
        assertEquals(1L,r.db {CollectionPermanentArchiveDao.count()})
    }

    private fun review(r: Runtime, publicFile:String="public"):String {
        r.permission(CollectionConst.PUBLISH)
        val source=r.disclosures.detail("museum",EnumDisclosureSubject.RESOURCE,"one")["sourceRevision"] as String
        return r.disclosures.review("museum",EnumDisclosureSubject.RESOURCE,"one", CollectionDisclosureReviewVo(source,EnumDisclosureDecision.PUBLIC,at,"reviewer","review",
            EnumDisclosureRestriction.entries.associateWith { DisclosureRestrictionVo(EnumDisclosureHandling.REDACTED,"已依据原件核对公开范围") },
            content=CollectionPublicContentVo("铜器","公开介绍","服务台接收意见",mapOf(EnumPublicFact.NAME to "铜器")),publicContentFileIds=publicFile))
    }
    private fun changeOriginal(r:Runtime,kind:String):String {
        if(kind=="archive")return r.seal(CollectionConst.BIZ_CHANGE)
        r.permission(CollectionConst.LEDGER_CORRECT)
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        val service=CollectionChangeService(mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java),scope,
            mock(CollectionOperationLockService::class.java),r.c.getBean(BusinessAttachmentBinder::class.java),mock(CollectionCatalogService::class.java),
            mock(CollectionResponsibleUserService::class.java),r.archives,CollectionRetainedLedgerService(scope,r.archives),r.c.getBean(CollectionActorDirectory::class.java),mock(PaperLedgerService::class.java),r.c.getBean(CollectionBusinessOriginalFiles::class.java))
        val id=r.db {
            SchemaUtils.create(CollectionCategories)
            if(CollectionCategoryDao.findById("standard-bronze")==null)
                CollectionCategoryDao.new("standard-bronze") { name="铜器";code="04";parentId="0" }
            val proposal=service.proposal("one")
            service.save(proposal.copy(reason="按原件订正",content=proposal.content!!.copy(formalName="新定名",categoryId="standard-bronze"),evidenceFileIds="proof"))
        }
        return if(kind=="draft")id else r.db {CollectionPermanentArchiveDao.all().first {it.bizType==CollectionBusinessOriginalFiles.BIZ && it.bizId==id}.id.value}
    }
    @ParameterizedTest @CsvSource("draft,direct","draft,list","draft,preview","draft,temp","draft,zip",
        "retained,direct","retained,list","retained,preview","retained,temp","retained,zip",
        "archive,direct","archive,list","archive,preview","archive,temp","archive,zip")
    fun `information change files honor configured museum across all file entries`(kind:String,entry:String)=fixture {r->
        val content=changeOriginal(r,kind);r.permission(CollectionConst.LEDGER_CORRECT,CollectionConst.CROSS_ORG);r.allow()
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.c.getBean(FileService::class.java))
        front.findByContent(content,ParamsFile(),MockHttpServletRequest());front.findByContent(content,ParamsFile(),MockHttpServletRequest())
        val before=r.db {CollectionPermanentArchives.selectAll().map {row->CollectionPermanentArchives.columns.map {row[it]}}}
        configureOtherMuseum(r);assertProtectionFileEntryDenied(r,content,entry)
        assertEquals(before,r.db {CollectionPermanentArchives.selectAll().map {row->CollectionPermanentArchives.columns.map {row[it]}}})
    }
    @ParameterizedTest @ValueSource(strings=["draft","retained","archive"])
    fun `information change files preserve current cross museum reads and reject revoked duties`(kind:String)=multiFixture {r->
        val content=changeOriginal(r,kind);r.permission(CollectionConst.SELECT);r.allow()
        r.db {UserDao["reader"].groupId="other"};r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.permission(CollectionConst.CROSS_ORG)
        for(entry in listOf("direct","list","preview","temp","zip"))assertProtectionFileEntryDenied(r,content,entry)
        r.permission(CollectionConst.LEDGER_CORRECT,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false};r.deny()
    }
    @Test fun `information change shared foreign originals remain private in single museum`()=multiFixture {r->
        val content=changeOriginal(r,"draft");r.seal(CollectionConst.BIZ_CHANGE,"other","foreign-change")
        r.permission(CollectionConst.LEDGER_CORRECT,CollectionConst.CROSS_ORG);r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        for(entry in listOf("direct","list","preview","temp","zip"))assertProtectionFileEntryDenied(r,content,entry)
    }

    private fun appraisalOriginal(r:Runtime,kind:String):Pair<AppraisalService,String> {
        r.permission(CollectionConst.APPRAISE)
        val service=AppraisalService(mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java),
            r.c.getBean(CollectionScopeResolver::class.java),mock(CollectionUserResolver::class.java),mock(CollectionChangeService::class.java),
            r.c.getBean(BusinessAttachmentBinder::class.java),mock(CollectionOperationLockService::class.java),mock(AccessionReceiptService::class.java),
            r.c.getBean(CollectionBusinessOriginalFiles::class.java))
        val id=r.db {service.save(AppraisalVo(collectionId="one",resultName="馆内鉴选",conclusion="原意见",attachmentIds="proof",
            members=listOf(AppraisalMemberVo(memberName="外部专家",signatureFileId="proof"))))}
        val content=if(kind=="draft")id else r.db {CollectionPermanentArchiveDao.all().first {it.bizType==CollectionBusinessOriginalFiles.BIZ && it.bizId==id}.id.value}
        r.db {service.get(id)}
        return service to content
    }
    @ParameterizedTest @CsvSource("draft,direct","draft,list","draft,preview","draft,temp","draft,zip",
        "retained,direct","retained,list","retained,preview","retained,temp","retained,zip")
    fun `appraisal draft and retained signature files enforce current configured museum`(kind:String,entry:String)=fixture {r ->
        val (_,content)=appraisalOriginal(r,kind);r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);r.allow()
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.c.getBean(FileService::class.java))
        front.findByContent(content,ParamsFile(),MockHttpServletRequest());front.findByContent(content,ParamsFile(),MockHttpServletRequest())
        configureOtherMuseum(r);assertProtectionFileEntryDenied(r,content,entry)
    }
    @ParameterizedTest @ValueSource(strings=["draft","retained"])
    fun `appraisal original signatures retain current multi museum grants and reject revocation`(kind:String)=multiFixture {r ->
        val (_,content)=appraisalOriginal(r,kind);r.permission(CollectionConst.SELECT);r.allow()
        r.db {UserDao["reader"].groupId="other"};r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.permission(CollectionConst.CROSS_ORG)
        for(entry in listOf("direct","list","preview","temp","zip"))assertProtectionFileEntryDenied(r,content,entry)
        r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false};r.deny()
    }
    @Test fun `appraisal signatures cannot expose a shared foreign original in single museum`()=multiFixture {r ->
        val (_,content)=appraisalOriginal(r,"draft");r.seal(CollectionConst.BIZ_APPRAISAL,"other","foreign-appraisal")
        r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        for(entry in listOf("direct","list","preview","temp","zip"))assertProtectionFileEntryDenied(r,content,entry)
    }

    private fun protectionPermission(kind:String)=when(kind) {
        "COLLECTION_FRAGILITY","COLLECTION_JOINT_SAFETY"->CollectionConst.CARE
        "COLLECTION_SAMPLING"->CollectionConst.SAMPLING
        "COLLECTION_EMERGENCY","emergency_index"->CollectionConst.EMERGENCY
        else->CollectionConst.STORAGE
    }
    /** 索引用例单独保留资源索引，以免归档策略的拒绝掩盖独立索引入口。 */
    private fun protectionOriginal(r:Runtime,kind:String,org:String="museum",id:String="protection"):String {
        val archive=r.seal(if(kind.endsWith("_index")) "COLLECTION_CALENDAR" else kind,org,id)
        if(kind.endsWith("_index"))r.db {
            r.archives.fileReadKeys(FileDao["proof"]).forEach {key ->
                if(kind=="digital_index")DigitalAcquisitionFileAccessDao.new {orgId=org;planId=id;fileId="proof";pathKey=key}
                else CollectionEmergencyFileAccessDao.new {orgId=org;archiveId=archive;fileId="proof";pathKey=key}
            }
        }
        return archive
    }
    private fun assertProtectionFileEntryDenied(r:Runtime,archive:String,entry:String) {
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        when(entry) {
            "direct"->r.deny()
            "list"->assertThrows(BadRequestException::class.java){FileFrontController(mock(FileSystemStorageService::class.java),files).findByContent(archive,ParamsFile(),MockHttpServletRequest())}
            "preview"->{
                val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
                assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",response)}
                verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
            }
            "temp"->assertThrows(BadRequestException::class.java){storage.withTempFile("proof.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
            "zip"->{val response=MockHttpServletResponse();assertThrows(BadRequestException::class.java){files.zipContentFile(archive,response)};assertEquals(0,response.contentAsByteArray.size)}
            else->error("Unknown entry: $entry")
        }
    }
    @ParameterizedTest @CsvSource(
        "COLLECTION_REPRODUCTION,direct",
        "COLLECTION_REPRODUCTION,list",
        "COLLECTION_REPRODUCTION,preview",
        "COLLECTION_REPRODUCTION,temp",
        "COLLECTION_REPRODUCTION,zip",
        "REPRODUCTION_COMPLIANCE_2026,direct",
        "REPRODUCTION_COMPLIANCE_2026,list",
        "REPRODUCTION_COMPLIANCE_2026,preview",
        "REPRODUCTION_COMPLIANCE_2026,temp",
        "REPRODUCTION_COMPLIANCE_2026,zip",
        "COLLECTION_DIGITAL_ACQUISITION,direct",
        "COLLECTION_DIGITAL_ACQUISITION,list",
        "COLLECTION_DIGITAL_ACQUISITION,preview",
        "COLLECTION_DIGITAL_ACQUISITION,temp",
        "COLLECTION_DIGITAL_ACQUISITION,zip",
        "COLLECTION_FRAGILITY,direct",
        "COLLECTION_FRAGILITY,list",
        "COLLECTION_FRAGILITY,preview",
        "COLLECTION_FRAGILITY,temp",
        "COLLECTION_FRAGILITY,zip",
        "COLLECTION_JOINT_SAFETY,direct",
        "COLLECTION_JOINT_SAFETY,list",
        "COLLECTION_JOINT_SAFETY,preview",
        "COLLECTION_JOINT_SAFETY,temp",
        "COLLECTION_JOINT_SAFETY,zip",
        "COLLECTION_SAMPLING,direct",
        "COLLECTION_SAMPLING,list",
        "COLLECTION_SAMPLING,preview",
        "COLLECTION_SAMPLING,temp",
        "COLLECTION_SAMPLING,zip",
        "COLLECTION_EMERGENCY,direct",
        "COLLECTION_EMERGENCY,list",
        "COLLECTION_EMERGENCY,preview",
        "COLLECTION_EMERGENCY,temp",
        "COLLECTION_EMERGENCY,zip",
        "digital_index,direct",
        "digital_index,list",
        "digital_index,preview",
        "digital_index,temp",
        "digital_index,zip",
        "emergency_index,direct",
        "emergency_index,list",
        "emergency_index,preview",
        "emergency_index,temp",
        "emergency_index,zip"
    )
    fun `protection originals and standalone indexes enforce configured museum across file entries`(kind:String,entry:String)=fixture {r ->
        val archive=protectionOriginal(r,kind)
        r.permission(protectionPermission(kind),CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        r.allow()
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.c.getBean(FileService::class.java))
        front.findByContent(archive,ParamsFile(),MockHttpServletRequest());front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        configureOtherMuseum(r)
        assertProtectionFileEntryDenied(r,archive,entry)
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_REPRODUCTION","REPRODUCTION_COMPLIANCE_2026","COLLECTION_DIGITAL_ACQUISITION","COLLECTION_FRAGILITY","COLLECTION_JOINT_SAFETY","COLLECTION_SAMPLING","COLLECTION_EMERGENCY","digital_index","emergency_index"])
    fun `protection originals retain multi museum history and current revocation`(kind:String)=multiFixture {r ->
        val archive=protectionOriginal(r,kind);r.permission(protectionPermission(kind),CollectionConst.SELECT);r.allow()
        r.db {UserDao["reader"].groupId="other"};r.deny()
        r.permission(protectionPermission(kind),CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED};r.allow()
        r.permission(CollectionConst.CROSS_ORG);r.deny()
        r.permission(protectionPermission(kind),CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false}
        for(entry in listOf("direct","list","preview","temp","zip"))assertProtectionFileEntryDenied(r,archive,entry)
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_REPRODUCTION","REPRODUCTION_COMPLIANCE_2026","COLLECTION_DIGITAL_ACQUISITION","COLLECTION_FRAGILITY","COLLECTION_JOINT_SAFETY","COLLECTION_SAMPLING","COLLECTION_EMERGENCY","digital_index","emergency_index"])
    fun `protection originals shared across museums cannot bypass single configuration`(kind:String)=multiFixture {r ->
        val archive=protectionOriginal(r,kind);protectionOriginal(r,kind,"other","foreign-protection")
        r.permission(protectionPermission(kind),CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        for(entry in listOf("direct","list","preview","temp","zip"))assertProtectionFileEntryDenied(r,archive,entry)
    }
    @ParameterizedTest @CsvSource("COLLECTION_DIGITAL_ACQUISITION,COLLECTION_INFORMATION","COLLECTION_DIGITAL_ACQUISITION,COLLECTION_STORAGE_HEAD",
        "digital_index,COLLECTION_INFORMATION","digital_index,COLLECTION_STORAGE_HEAD")
    fun `protection digital originals preserve actual information and custody roles`(kind:String,role:String)=multiFixture {r ->
        protectionOriginal(r,kind);r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.deny()
        r.db {actorRole("reader",role,emptySet());UserDao["reader"].groupId="other"};r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        configureOtherMuseum(r);r.deny()
    }

    private fun administrativeOriginal(r:Runtime,kind:String):String {
        r.permission(CollectionConst.ORG_MANAGE,CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_FINANCE)
        val (biz,id,phase)=if(kind=="rules") Triple(CollectionManagementRulesService.BIZ,
            r.c.getBean(CollectionManagementRulesService::class.java).register("museum",
                CollectionManagementRulesVo("藏品管理制度","馆规1号",at,at.toLocalDate(),"director","proof","review")),"ISSUED")
        else {
            val books=r.c.getBean(PaperLedgerService::class.java)
            val file=if(kind=="paper") "proof" else "review"
            val book=books.register("museum",PaperLedgerBookVo("BOOK1","总登记账",at.toLocalDate().minusDays(3),30,"本馆纸账",file,
                PaperLedgerCustodyVo(at,"keeper","档案室",EnumPaperLedgerCondition.GOOD,"逐页核实",file)))
            if(kind=="paper") Triple(PaperLedgerService.BIZ,book,"REGISTERED")
            else Triple(LedgerReconciliationService.BIZ,r.c.getBean(LedgerReconciliationService::class.java).record("museum",
                LedgerReconciliationVo("CHECK1",at.toLocalDate().minusDays(2),at.toLocalDate().minusDays(1),at,"keeper","finance",
                    "本馆纸质总账与财务账",setOf(book),"proof","proof","proof",at.toLocalDate().plusMonths(3),
                    rows=listOf(LedgerAccountComparisonVo(1,book,"第1页","第1条",paper=LedgerAccountEntryVo("A1","铜器",1,"件"),
                        financial=LedgerAccountEntryVo("A1","铜器",1,"件"))))),"CHECKED")
        }
        return r.db {CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq biz) and
            (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.single().id.value}
    }
    private fun administrativeDetail(r:Runtime,kind:String):Any = when(kind) {
        "rules"->r.c.getBean(CollectionManagementRulesService::class.java).detail("museum")
        "paper"->r.c.getBean(PaperLedgerService::class.java).list("museum")
        else->r.c.getBean(LedgerReconciliationService::class.java).list("museum")
    }
    @ParameterizedTest @CsvSource("rules,direct","rules,list","rules,preview","rules,temp","rules,zip",
        "paper,direct","paper,list","paper,preview","paper,temp","paper,zip",
        "finance,direct","finance,list","finance,preview","finance,temp","finance,zip")
    fun `administrative original file entries enforce the same configured museum as detail`(kind:String,entry:String)=fixture {r ->
        val archive=administrativeOriginal(r,kind)
        r.permission(if(kind=="finance") CollectionConst.LEDGER_FINANCE else CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        administrativeDetail(r,kind);r.allow()
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing();configureOtherMuseum(r)
        assertThrows(BadRequestException::class.java){administrativeDetail(r,kind)}
        when(entry) {
            "direct"->r.deny()
            "list"->assertThrows(BadRequestException::class.java){listing()}
            "preview"->{
                val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
                assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",response)}
                verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
            }
            "temp"->assertThrows(BadRequestException::class.java){storage.withTempFile("proof.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
            "zip"->{
                val response=MockHttpServletResponse();assertThrows(BadRequestException::class.java){files.zipContentFile(archive,response)}
                assertEquals(0,response.contentAsByteArray.size)
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["rules","paper","finance"])
    fun `administrative original file entries preserve multi museum grants and revoke current access`(kind:String)=multiFixture {r ->
        val archive=administrativeOriginal(r,kind)
        val permission=if(kind=="finance") CollectionConst.LEDGER_FINANCE else CollectionConst.SELECT
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.c.getBean(FileService::class.java))
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        r.permission(permission);administrativeDetail(r,kind);r.allow();listing();listing()
        r.db {UserDao["reader"].groupId="other"}
        assertThrows(BadRequestException::class.java){administrativeDetail(r,kind)}
        r.deny();assertThrows(BadRequestException::class.java){listing()}
        r.permission(permission,CollectionConst.CROSS_ORG)
        administrativeDetail(r,kind);r.allow();listing()
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        administrativeDetail(r,kind);r.allow();listing()
        r.permission(CollectionConst.CROSS_ORG)
        assertThrows(BadRequestException::class.java){administrativeDetail(r,kind)}
        r.deny();assertThrows(BadRequestException::class.java){listing()}
        r.permission(permission,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false}
        assertThrows(BadRequestException::class.java){administrativeDetail(r,kind)}
        r.deny();assertThrows(BadRequestException::class.java){listing()}
    }

    @Test fun `administrative original file entries keep financial originals restricted to finance duty`()=fixture {r ->
        administrativeOriginal(r,"finance")
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertThrows(BadRequestException::class.java){administrativeDetail(r,"finance")};r.deny()
        r.permission(CollectionConst.LEDGER_FINANCE)
        administrativeDetail(r,"finance");r.allow()
    }

    @Test fun `administrative original file entries cannot use local reference to expose shared foreign original`()=multiFixture {r ->
        r.seal(LedgerReconciliationService.BIZ);r.seal(LedgerReconciliationService.BIZ,"other","other-case")
        r.permission(CollectionConst.CROSS_ORG,CollectionConst.LEDGER_FINANCE);r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        r.deny()
    }

    private fun filingOriginal(r:Runtime,kind:String):String {
        r.db {SchemaUtils.create(CollectionArchivePackets,CollectionArchiveSubmissions)}
        r.permission(CollectionConst.ARCHIVE_MANAGE,CollectionConst.STAT)
        val targetFile="proof"
        val (biz,id,phase)=if(kind.startsWith("annual")) {
            val service=r.c.getBean(AnnualReportEvidenceService::class.java)
            val signedFile=if(kind=="annual") targetFile else "review"
            val id=service.signed(AnnualReportSignedVo(2025,"reader","keeper","director",at,signedFile,
                AnnualDeclaredFiguresVo(0,0,0,0,0,0,changesDescription="核对纸账，本期无正式登记"),
                reconciliationNote="逐项核对纸账及历史缺口",reconciliationFileIds=signedFile), "museum")
            if(kind!="annual") service.submission(id,AnnualReportSubmissionVo("主管文物局","报1",at.plusDays(1),
                if(kind=="annual_submission") targetFile else "review"))
            if(kind=="annual_receipt") service.filing(id,AnnualReportFilingVo("主管文物局","备1",at.plusDays(2),targetFile))
            Triple(AnnualReportEvidenceService.BIZ,id,when(kind){"annual"->"SIGNED";"annual_submission"->"SUBMITTED";else->"FILED"})
        } else {
            val service=r.c.getBean(CollectionArchivePacketService::class.java)
            r.db {
                val c=CollectionDao["one"];c.nature=EnumCollectionNature.RELIC;c.level=EnumRelicLevel.SECOND
                CollectionCatalogVersionDao["v1"].content=CollectionCatalogMapper.fromCollection(c.toModel())
            }
            val jurisdiction=service.jurisdiction("museum",ArchiveJurisdictionVo(EnumArchiveJurisdiction.LOCAL_COMPETENT,
                "主管文物局","国家文物局","主管文物局",at.toLocalDate().minusYears(1),at,"director",
                if(kind=="jurisdiction") targetFile else "review"))
            if(kind=="jurisdiction") Triple(CollectionArchivePacketService.JURISDICTION_BIZ,jurisdiction,"VERIFIED")
            else {
                val file=if(kind=="packet") targetFile else "review"
                val packet=service.record("museum","one",CollectionArchivePacketVo("v1","档案A1",EnumCollectionArchiveFormat.BOTH,
                    at,"reader","档案室01柜","档案系统卷宗A1",file,file,file,
                    EnumArchiveSection.entries.associateWith {ArchiveSectionVo(EnumArchiveSectionStatus.INCLUDED,"目录第${it.ordinal+1}项",file)}))
                if(kind=="packet") Triple(CollectionArchivePacketService.BIZ,packet,"COMPILED")
                else {
                    val sent=service.submission("museum",packet,ArchivePacketSubmissionVo(EnumArchiveFilingTarget.COMPETENT,
                        "主管文物局",at.plusDays(1),"报1",if(kind=="packet_submission") targetFile else "review"))
                    if(kind=="packet_receipt") service.receipt("museum",sent,ArchivePacketReceiptVo("主管文物局",at.plusDays(2),"备1",targetFile))
                    Triple(CollectionArchivePacketService.BIZ,sent,if(kind=="packet_submission") "SUBMITTED" else "FILED")
                }
            }
        }
        return r.db {CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq biz) and
            (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.single().id.value}
    }
    private fun filingDetail(r:Runtime,kind:String):Any = when {
        kind.startsWith("annual")->r.c.getBean(AnnualReportEvidenceService::class.java).detail(2025)
        kind=="jurisdiction"->r.c.getBean(CollectionArchivePacketService::class.java).jurisdictions("museum")
        else->r.c.getBean(CollectionArchivePacketService::class.java).detail("museum","one")
    }
    @ParameterizedTest @ValueSource(strings=["annual","annual_submission","annual_receipt"])
    fun `annual report original requires current museum year version archive and file`(kind:String)=fixture {r ->
        val archive=filingOriginal(r,kind)
        val id=r.db {CollectionPermanentArchiveDao[archive].bizId}
        val service=r.c.getBean(AnnualReportEvidenceService::class.java)
        r.permission(CollectionConst.SELECT)
        service.requireArchivedOriginal(2025,id,archive,"proof")
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal(2024,id,archive,"proof")}
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal(2025,id,"999999999999999","proof")}
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal(2025,id,archive,"missing")}
        configureOtherMuseum(r)
        assertThrows(BadRequestException::class.java){service.requireArchivedOriginal(2025,id,archive,"proof")}
    }
    @ParameterizedTest @CsvSource("packet,direct","packet,list","packet,preview","packet,temp","packet,zip",
        "jurisdiction,direct","jurisdiction,list","jurisdiction,preview","jurisdiction,temp","jurisdiction,zip",
        "packet_submission,direct","packet_submission,list","packet_submission,preview","packet_submission,temp","packet_submission,zip",
        "packet_receipt,direct","packet_receipt,list","packet_receipt,preview","packet_receipt,temp","packet_receipt,zip",
        "annual,direct","annual,list","annual,preview","annual,temp","annual,zip",
        "annual_submission,direct","annual_submission,list","annual_submission,preview","annual_submission,temp","annual_submission,zip",
        "annual_receipt,direct","annual_receipt,list","annual_receipt,preview","annual_receipt,temp","annual_receipt,zip")
    fun `filing original entries match configured museum for directories and reports`(kind:String,entry:String)=fixture {r ->
        val archive=filingOriginal(r,kind)
        r.permission(if(kind.startsWith("annual")) CollectionConst.SELECT else CollectionConst.ARCHIVE_MANAGE,CollectionConst.CROSS_ORG)
        filingDetail(r,kind);r.allow()
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing();configureOtherMuseum(r)
        if(kind.startsWith("annual")) {
            val detail=r.c.getBean(AnnualReportEvidenceService::class.java).detail(2025)
            assertEquals("other",detail["orgId"]);assertTrue((detail["records"] as List<*>).isEmpty())
        } else assertThrows(BadRequestException::class.java){filingDetail(r,kind)}
        when(entry) {
            "direct"->r.deny()
            "list"->assertThrows(BadRequestException::class.java){listing()}
            "preview"->{
                val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
                assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",response)}
                verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
            }
            "temp"->assertThrows(BadRequestException::class.java){storage.withTempFile("proof.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
            "zip"->{
                val response=MockHttpServletResponse();assertThrows(BadRequestException::class.java){files.zipContentFile(archive,response)}
                assertEquals(0,response.contentAsByteArray.size)
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["packet","jurisdiction","packet_submission","packet_receipt","annual","annual_submission","annual_receipt"])
    fun `filing original entries preserve explicit multi museum history and reject revoked users`(kind:String)=multiFixture {r ->
        val archive=filingOriginal(r,kind)
        val permission=if(kind.startsWith("annual")) CollectionConst.STAT else CollectionConst.ARCHIVE_MANAGE
        r.permission(permission);filingDetail(r,kind);r.allow()
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.c.getBean(FileService::class.java))
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing();r.db {UserDao["reader"].groupId="other"}
        r.deny();assertThrows(BadRequestException::class.java){listing()}
        r.permission(permission,CollectionConst.CROSS_ORG);r.allow();listing()
        if(!kind.startsWith("annual")) filingDetail(r,kind)
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        r.allow();listing()
        r.permission(CollectionConst.CROSS_ORG)
        r.deny();assertThrows(BadRequestException::class.java){listing()}
        r.permission(permission,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false}
        r.deny();assertThrows(BadRequestException::class.java){listing()}
    }

    @ParameterizedTest @ValueSource(strings=["packet","jurisdiction"])
    fun `filing original entries keep archive duty separate from ordinary collection viewing`(kind:String)=fixture {r ->
        filingOriginal(r,kind);r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertThrows(BadRequestException::class.java){filingDetail(r,kind)};r.deny()
        r.permission(CollectionConst.ARCHIVE_MANAGE);filingDetail(r,kind);r.allow()
    }

    @ParameterizedTest @ValueSource(strings=["packet","jurisdiction"])
    fun `archive select grants current museum original reads without management duty`(kind:String)=fixture {r ->
        filingOriginal(r,kind)
        r.permission(CollectionConst.ARCHIVE_SELECT)
        filingDetail(r,kind)
        r.allow()
        r.permission(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java){filingDetail(r,kind)}
        r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["packet","jurisdiction","annual"])
    fun `filing original entries reject shared foreign file under single museum configuration`(kind:String)=multiFixture {r ->
        filingOriginal(r,kind)
        val biz=when(kind){"packet"->CollectionArchivePacketService.BIZ;"jurisdiction"->CollectionArchivePacketService.JURISDICTION_BIZ;else->AnnualReportEvidenceService.BIZ}
        r.seal(biz,"other","foreign-case")
        r.permission(CollectionConst.ARCHIVE_MANAGE,CollectionConst.STAT,CollectionConst.CROSS_ORG);r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        r.deny()
    }

    private fun gradingOriginal(r:Runtime,stage:String):String {
        val service=r.c.getBean(CollectionGradingService::class.java)
        r.permission(CollectionConst.APPRAISE)
        val applicationFile=if(stage=="application") "proof" else "review"
        val id=service.apply("one",GradingApplicationVo(at,applicationFile,EnumRelicLevel.FIRST,"铜器本件识别特征",applicationFile,
            "省文物局","定申1",at.plusDays(1),applicationFile,applicationFile))
        if(stage!="application") {
            val file=if(stage in setOf("result","used")) "proof" else "review"
            service.result(id,GradingResultVo("省文物局","定1",at.plusDays(2),EnumRelicLevel.FIRST,file,"省备1",at.plusDays(3),file))
        }
        if(stage in setOf("national","used")) {
            val file=if(stage=="national") "proof" else "review"
            service.national(id,GradingNationalFilingVo("省文物局","国家文物局",at.plusDays(4),file,"国备1",at.plusDays(5),file))
        }
        if(stage=="used") r.db {
            val collection=CollectionDao["one"]
            val grading=service.requireContent(collection,CollectionCatalogMapper.fromCollection(collection.toModel()).copy(
                nature=EnumCollectionNature.RELIC,level=EnumRelicLevel.FIRST),true)
            service.recordUse(collection,grading,"catalog-approval",EnumRelicLevel.FIRST)
        }
        val phase=when(stage){"application"->"APPLICATION";"result"->"PROVINCIAL_RESULT";"national"->"NATIONAL_FILING";else->"USED_catalog-approval"}
        return r.db {CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionGradingService.BIZ) and
            (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.single().id.value}
    }
    @ParameterizedTest @CsvSource("application,direct","application,list","application,preview","application,temp","application,zip",
        "result,direct","result,list","result,preview","result,temp","result,zip",
        "national,direct","national,list","national,preview","national,temp","national,zip",
        "used,direct","used,list","used,preview","used,temp","used,zip")
    fun `grading original file entries match single museum detail boundary`(stage:String,entry:String)=fixture {r ->
        val archive=gradingOriginal(r,stage)
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        val service=r.c.getBean(CollectionGradingService::class.java)
        service.detail("one");r.allow()
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing();configureOtherMuseum(r)
        assertThrows(BadRequestException::class.java){service.detail("one")}
        when(entry) {
            "direct"->r.deny()
            "list"->assertThrows(BadRequestException::class.java){listing()}
            "preview"->{
                val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
                assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",response)}
                verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
            }
            "temp"->assertThrows(BadRequestException::class.java){storage.withTempFile("proof.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
            "zip"->{
                val response=MockHttpServletResponse();assertThrows(BadRequestException::class.java){files.zipContentFile(archive,response)}
                assertEquals(0,response.contentAsByteArray.size)
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["application","result","national","used"])
    fun `grading original file entries preserve multi museum history and revoke current access`(stage:String)=multiFixture {r ->
        val archive=gradingOriginal(r,stage)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.c.getBean(FileService::class.java))
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        val service=r.c.getBean(CollectionGradingService::class.java)
        service.detail("one");r.allow();listing();listing()
        r.db {UserDao["reader"].groupId="other"}
        assertThrows(BadRequestException::class.java){service.detail("one")};r.deny()
        assertThrows(BadRequestException::class.java){listing()}
        r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG)
        service.detail("one");r.allow();listing()
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        service.detail("one");r.allow();listing()
        r.permission(CollectionConst.CROSS_ORG)
        assertThrows(BadRequestException::class.java){service.detail("one")};r.deny()
        assertThrows(BadRequestException::class.java){listing()}
        r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false}
        assertThrows(BadRequestException::class.java){service.detail("one")};r.deny()
        assertThrows(BadRequestException::class.java){listing()}
    }

    @ParameterizedTest @ValueSource(strings=["COLLECTION_SELECT","COLLECTION_APPRAISE","COLLECTION_LEDGER_CREATE","COLLECTION_LEDGER_CORRECT"])
    fun `grading original file entries retain each existing read duty`(permission:String)=fixture {r ->
        gradingOriginal(r,"used");r.permission(permission)
        r.c.getBean(CollectionGradingService::class.java).detail("one");r.allow()
        r.permission();r.deny()
    }

    @Test fun `grading original file entries reject shared foreign reference in single museum`()=multiFixture {r ->
        gradingOriginal(r,"national");r.seal(CollectionGradingService.BIZ,"other","foreign-case")
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"mode","single")
        r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["changed_bytes","missing_bytes","changed_result","missing_reference","empty_used_snapshot","malformed_used_snapshot"])
    fun `grading recorded originals are verified through real Spring transactions`(damage:String)=fixture {r ->
        gradingOriginal(r,"used")
        val service=r.c.getBean(CollectionGradingService::class.java)
        service.detail("one")
        when(damage) {
            "changed_bytes"->java.nio.file.Files.writeString(dir.resolve("proof.txt"),"MODIFIED")
            "missing_bytes"->java.nio.file.Files.delete(dir.resolve("proof.txt"))
            "changed_result"->r.db {val row=CollectionGradingDao.all().single();row.result=row.result!!.copy(documentNo="改写文号")}
            "empty_used_snapshot","malformed_used_snapshot"->r.db {
                val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.phase.startsWith("USED_")}
                archive.snapshot=if(damage=="empty_used_snapshot") "" else "{broken"
                archive.digest=CollectionCareEvidence.digest(archive.snapshot)
            }
            else->r.db {
                val archive=CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingService.BIZ && it.phase=="PROVINCIAL_RESULT"}
                CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}.forEach {it.delete()}
            }
        }
        fun state()=r.db {Pair(CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.snapshot,it.digest)},
            CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.metadata)})}
        val before=state()
        if(damage in setOf("changed_bytes","missing_bytes")) assertUnavailableOriginals(service.detail("one"))
        else assertThrows(BadRequestException::class.java){service.detail("one")}
        assertEquals(before,state())
    }

    private fun gradingWriteAction(r:Runtime,action:String):()->String {
        r.permission(CollectionConst.APPRAISE)
        val service=r.c.getBean(CollectionGradingService::class.java)
        val application=GradingApplicationVo(at,"proof",EnumRelicLevel.FIRST,"本件铜器识别依据","proof","省文物局","定申1",at.plusDays(1),"proof","proof")
        val result=GradingResultVo("省文物局","定1",at.plusDays(2),EnumRelicLevel.FIRST,"proof","省备1",at.plusDays(3),"proof")
        val national=GradingNationalFilingVo("省文物局","国家文物局",at.plusDays(4),"proof","国备1",at.plusDays(5),"proof")
        val id=if(action=="apply") null else service.apply("one",application)
        if(action in setOf("national","repeat_result","repeat_national")) service.result(id!!,result)
        if(action=="repeat_national") service.national(id!!,national)
        return when(action) {
            "apply","repeat_apply"->{ {service.apply("one",application)} }
            "result","repeat_result"->{ {service.result(id!!,result)} }
            else->{ {service.national(id!!,national)} }
        }
    }
    @ParameterizedTest @CsvSource("duty_revoked,apply","duty_revoked,result","duty_revoked,national","duty_revoked,repeat_apply","duty_revoked,repeat_result","duty_revoked,repeat_national",
        "moved,apply","moved,result","moved,national","moved,repeat_apply","moved,repeat_result","moved,repeat_national",
        "configured,apply","configured,result","configured,national","configured,repeat_apply","configured,repeat_result","configured,repeat_national",
        "museum_disabled,apply","museum_disabled,result","museum_disabled,national","museum_disabled,repeat_apply","museum_disabled,repeat_result","museum_disabled,repeat_national",
        "account_disabled,apply","account_disabled,result","account_disabled,national","account_disabled,repeat_apply","account_disabled,repeat_result","account_disabled,repeat_national")
    fun `grading administrative writes recheck current authority before first or repeated operation`(change:String,action:String)=fixture {r ->
        val write=gradingWriteAction(r,action)
        when(change) {
            "duty_revoked"->r.permission(CollectionConst.SELECT)
            "moved"->{r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);r.db {UserDao["reader"].groupId="other"}}
            "configured"->{r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);configureOtherMuseum(r)}
            "museum_disabled"->{r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG);r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}}
            "account_disabled"->r.db {UserDao["reader"].enabled=false}
        }
        fun state()=r.db {Triple(CollectionGradingDao.all().map {listOf(it.id.value,it.orgId,it.version.toString(),JsonUtils.toJson(it.application),JsonUtils.toJson(it.result),JsonUtils.toJson(it.nationalFiling))},
            CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.orgId,it.phase,it.snapshot,it.digest)},
            CollectionPermanentArchiveFileDao.all().map {listOf(it.id.value,it.archiveId,it.fileId,it.metadata)})}
        val before=state()
        assertThrows(BadRequestException::class.java){write()}
        assertEquals(before,state(),"Rejected administrative operation must not change records or archives")
    }

    @ParameterizedTest @ValueSource(strings=["apply","result","national","repeat_apply","repeat_result","repeat_national"])
    fun `grading administrative writes follow actual new assignment despite nonadmin old login`(action:String)=multiFixture {r ->
        r.db {UserDao["reader"].groupId="other";CollectionDao["one"].orgId="other"}
        val old=SecurityUser(id="reader",name="reader",username="reader",password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.APPRAISE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(old,null,old.authorities)
        val write=gradingWriteAction(r,action)
        val id=write();assertEquals(id,write())
        assertEquals("other",r.db {CollectionGradingDao[id].orgId})
        assertEquals("one",r.c.getBean(CollectionGradingService::class.java).detail("one")["collectionId"])
    }

    @ParameterizedTest @ValueSource(strings=["COLLECTION_ADMIN","ADMIN","ADMIN_ROLE"])
    fun `grading administrative writes preserve current administrator grants`(grant:String)=fixture {r ->
        if(grant=="ADMIN_ROLE")r.db {RoleUsers.insert {it[userId]="reader";it[roleId]=EnumRole.ADMIN.value}}
        for(action in listOf("apply","result","national","repeat_apply","repeat_result","repeat_national")) {
            val write=gradingWriteAction(r,action)
            if(grant=="ADMIN_ROLE")r.permission() else r.permission(grant)
            val id=write();assertEquals(id,write())
        }
        assertEquals(1L,r.db {CollectionGradingDao.count()})
        assertEquals(3,r.db {CollectionPermanentArchiveDao.all().count {it.bizType==CollectionGradingService.BIZ}})
        assertEquals(3,r.db {CollectionPermanentArchiveDao.all().count {it.bizType==CollectionGradingHistoryEvidence.BIZ}})
    }

    @ParameterizedTest @ValueSource(strings=["disabled_museum","configured_museum"])
    fun `grading administrative writes keep deployment and active museum limits for current administrators`(change:String)=fixture {r ->
        val write=gradingWriteAction(r,"repeat_national")
        r.db {RoleUsers.insert {it[userId]="reader";it[roleId]=EnumRole.ADMIN.value}}
        if(change=="disabled_museum")r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED} else configureOtherMuseum(r)
        assertThrows(BadRequestException::class.java){write()}
        assertEquals(3,r.db {CollectionPermanentArchiveDao.all().count {it.bizType==CollectionGradingService.BIZ}})
        assertEquals(3,r.db {CollectionPermanentArchiveDao.all().count {it.bizType==CollectionGradingHistoryEvidence.BIZ}})
    }

    @ParameterizedTest @ValueSource(strings=["missing_record","missing_evidence"])
    fun `grading history evidence survives mutable record loss in real transactions`(damage:String)=fixture {r ->
        gradingOriginal(r,"used")
        r.db {
            if(damage=="missing_record")CollectionGradingDao.all().single().delete()
            else CollectionPermanentArchiveDao.all().single {it.bizType==CollectionGradingHistoryEvidence.BIZ && it.phase=="APPLICATION_1"}.delete()
        }
        fun state()=r.db {Pair(CollectionGradingDao.all().map {it.id.value},CollectionPermanentArchiveDao.all().map {listOf(it.id.value,it.snapshot,it.digest)})}
        val before=state()
        assertThrows(BadRequestException::class.java){r.c.getBean(CollectionGradingService::class.java).detail("one")}
        assertEquals(before,state())
    }

    @Test fun `grading history separates source and receiving museum evidence for the same collection`()=multiFixture {r ->
        gradingOriginal(r,"used")
        val source=r.db {CollectionPermanentArchiveDao.all().filter {it.bizType==CollectionGradingHistoryEvidence.BIZ}.associate {it.id.value to (it.snapshot to it.digest)}}
        r.db {CollectionDao["one"].orgId="other";UserDao["reader"].groupId="other"}
        r.permission(CollectionConst.APPRAISE,CollectionConst.CROSS_ORG)
        val service=r.c.getBean(CollectionGradingService::class.java)
        val id=service.apply("one",GradingApplicationVo(at,"replacement",EnumRelicLevel.SECOND,"接收馆核实依据","replacement",
            "接收馆省文物局","新馆申请",at.plusDays(1),"replacement","replacement"))
        assertEquals(1,r.db {CollectionGradingDao[id].version});service.detail("one")
        r.db {
            source.forEach {(id,value)->assertEquals(value,CollectionPermanentArchiveDao[id].let {it.snapshot to it.digest})}
            val histories=CollectionPermanentArchiveDao.all().filter {it.bizType==CollectionGradingHistoryEvidence.BIZ}
            assertEquals(setOf("museum","other"),histories.map {it.orgId}.toSet())
            assertEquals(2,histories.map {it.bizId}.toSet().size)
        }
    }

    private data class PublishedOriginals(val reviewId:String,val eventId:String,val reviewArchive:String,val eventArchive:String)
    private fun publishedOriginals(r:Runtime):PublishedOriginals {
        val id=review(r);r.disclosures.enable("museum",id)
        val event=r.disclosures.event("museum",id,CollectionDisclosureEventVo(EnumDisclosureEvent.PUBLISHED,
            EnumDisclosureChannel.OFFICIAL_WEBSITE,"https://museum.example.org/one",at.plusDays(1),"reader","proof","public"))
        fun archive(bizId:String,phase:String)=r.db {CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionDisclosureService.BIZ) and
                (CollectionPermanentArchives.bizId eq bizId) and (CollectionPermanentArchives.phase eq phase)
        }.single().id.value}
        return PublishedOriginals(id,event,archive(id,"REVIEWED"),archive(event,"PUBLISHED"))
    }
    private fun configureOtherMuseum(r:Runtime) {
        // 模拟同一历史数据库以另一单馆配置重新接入；已签署业务、归档内容及人员授权完全保留。
        org.springframework.test.util.ReflectionTestUtils.setField(r.c.getBean(CollectionScopeResolver::class.java),"defaultOrgId","other")
    }

    @ParameterizedTest @CsvSource("review,direct","review,list","review,preview","review,temp","review,zip",
        "event,direct","event,list","event,preview","event,temp","event,zip",
        "withdrawal,direct","withdrawal,list","withdrawal,preview","withdrawal,temp","withdrawal,zip",
        "replacement,direct","replacement,list","replacement,preview","replacement,temp","replacement,zip")
    fun `disclosure private originals enforce the same configured museum as detail`(kind:String,entry:String)=fixture {r ->
        val original=publishedOriginals(r)
        val file=when(kind){"review"->"review";"event"->"proof";else->"replacement"}
        val archive=when(kind) {
            "review"->original.reviewArchive
            "event"->original.eventArchive
            else->{
                val id=if(kind=="withdrawal") r.disclosures.event("museum",original.reviewId,
                    CollectionDisclosureEventVo(EnumDisclosureEvent.WITHDRAWN,EnumDisclosureChannel.OFFICIAL_WEBSITE,
                        "https://museum.example.org/one",at.plusDays(2),"reader","replacement",
                        revokesPublicationId=original.eventId,reason="依据真实记录撤下"))
                else {
                    val content=r.db {CollectionDisclosureReviewDao[original.reviewId].content.copy(
                        reviewedAt=at.plusDays(2),replacementReason="核对公开说明",replacementFileIds="replacement")}
                    r.disclosures.review("museum",EnumDisclosureSubject.RESOURCE,"one",content)
                }
                r.db {CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionDisclosureService.BIZ) and
                    (CollectionPermanentArchives.bizId eq id)}.single().id.value}
            }
        }
        r.permission(CollectionConst.PUBLISH,CollectionConst.CROSS_ORG);r.allow("$file.txt")
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing();configureOtherMuseum(r)
        assertThrows(BadRequestException::class.java){r.disclosures.detail("museum",EnumDisclosureSubject.RESOURCE,"one")}
        when(entry) {
            "direct" -> r.deny("$file.txt")
            "list" -> assertThrows(BadRequestException::class.java){listing()}
            "preview" -> {
                val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
                assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument(file,"pdf",response)}
                verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
            }
            "temp" -> assertThrows(BadRequestException::class.java){storage.withTempFile("$file.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
            "zip" -> {
                val response=MockHttpServletResponse()
                assertThrows(BadRequestException::class.java){files.zipContentFile(archive,response)}
                assertEquals(0,response.contentAsByteArray.size)
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["review","event"])
    fun `disclosure private originals preserve explicit multi museum history and revoke old login`(kind:String)=multiFixture {r ->
        publishedOriginals(r);val file=if(kind=="review") "review.txt" else "proof.txt"
        r.allow(file)
        r.db {UserDao["reader"].groupId="other"}
        r.permission(CollectionConst.PUBLISH);r.deny(file)
        r.permission(CollectionConst.PUBLISH,CollectionConst.CROSS_ORG);r.allow(file)
        assertEquals("museum",r.disclosures.detail("museum",EnumDisclosureSubject.RESOURCE,"one")["orgId"])
        r.db {CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED}
        r.allow(file);SecurityContextHolder.clearContext();r.deny("public.txt");login()
        r.permission(CollectionConst.CROSS_ORG);r.deny(file)
        r.permission(CollectionConst.PUBLISH,CollectionConst.CROSS_ORG);r.allow(file)
        r.db {UserDao["reader"].enabled=false};r.deny(file)
    }

    @ParameterizedTest @ValueSource(strings=["anonymous","outside-reader","disabled-reader"])
    fun `disclosure current public copy remains public across configured museum without exposing signatures`(actor:String)=fixture {r ->
        publishedOriginals(r);configureOtherMuseum(r)
        when(actor) {
            "anonymous"->SecurityContextHolder.clearContext()
            "outside-reader"->{r.db {UserDao["reader"].groupId="other"};r.permission(CollectionConst.PUBLISH,CollectionConst.CROSS_ORG)}
            else->{r.db {UserDao["reader"].enabled=false}}
        }
        r.allow("public.txt");r.deny("review.txt");r.deny()
    }

    @Test fun `disclosure draft public copy does not gain anonymous access from configured museum change`()=fixture {r ->
        review(r);configureOtherMuseum(r);r.permission(CollectionConst.PUBLISH,CollectionConst.CROSS_ORG)
        r.deny("public.txt");SecurityContextHolder.clearContext();r.deny("public.txt")
    }

    @Test fun `anonymous public copy needs explicit enablement and never exposes signatures or event evidence`() = fixture { r ->
        val id=review(r)
        SecurityContextHolder.clearContext(); r.deny("public.txt"); r.deny("review.txt")
        login(); r.disclosures.enable("museum",id)
        r.disclosures.event("museum",id,CollectionDisclosureEventVo(EnumDisclosureEvent.PUBLISHED,EnumDisclosureChannel.OFFICIAL_WEBSITE,"https://museum.example.org/one",at.plusDays(1),"reader","proof","public"))
        SecurityContextHolder.clearContext(); r.allow("public.txt"); r.deny("review.txt"); r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["stop", "source_changed", "signature_missing", "signature_changed", "feed_record_changed", "museum_disabled"])
    fun `public access is revoked when current review evidence changes`(kind:String) = fixture { r ->
        val id=review(r); r.disclosures.enable("museum",id)
        SecurityContextHolder.clearContext(); r.allow("public.txt"); login()
        when(kind) {
            "stop" -> r.disclosures.stop("museum",id,DisclosureFeedStopVo("需要更正公开版本"))
            "source_changed" -> r.db { CollectionCatalogVersionDao["v1"].content=CollectionCatalogVersionDao["v1"].content.copy(formalName="变更名称") }
            "signature_missing" -> r.db { CollectionPermanentArchiveFiles.deleteWhere { CollectionPermanentArchiveFiles.fileId eq "review" } }
            "signature_changed" -> r.db { CollectionDisclosureReviewDao[id].content=CollectionDisclosureReviewDao[id].content.copy(signedReviewFileIds="proof") }
            "feed_record_changed" -> r.db { CollectionPermanentArchiveDao.find { CollectionPermanentArchives.phase eq "FEED_ENABLED" }.single().snapshot="{}" }
            "museum_disabled" -> r.db { CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED }
        }
        SecurityContextHolder.clearContext(); r.deny("public.txt")
    }
    @Test fun `public file also used as a signed original remains private`() = fixture { r ->
        val id=review(r,"review"); r.disclosures.enable("museum",id)
        SecurityContextHolder.clearContext(); r.deny("review.txt")
    }
    @Test fun `public disclosure cannot release a financial original shared with it`() = fixture { r ->
        val id=review(r,"proof"); r.disclosures.enable("museum",id); r.seal(LedgerReconciliationService.BIZ)
        SecurityContextHolder.clearContext(); r.deny(); login(); r.permission(CollectionConst.LEDGER_FINANCE); r.allow()
    }
    private fun prepareExecutedExit(r:Runtime) = r.db {
        SchemaUtils.create(DeregistrationExpertOpinions,DeregistrationNotices,CollectionAdministrativeRecords,CollectionCalendarVersions,io.hizhengfu.cloud.flow.dao.FlowInstanceLogs,CollectionOperationLocks,CollectionAuxiliaries,CollectionLedgerLogs,CollectionLedgerStatEvents,CollectionCategories,Warehouses,CollectionMedias,CollectionAttachments)
        actorRole("reader","EXIT_EDITOR",setOf(CollectionConst.DEREGISTER,CollectionConst.LEDGER_CORRECT))
        CollectionOrgDao["museum"].ownership=EnumOwnership.STATE
        CollectionDao["one"].nature=EnumCollectionNature.OTHER
        val request=DeregistrationDao.new("case") {orgId="museum";collectionId="one";createBy="reader";reason=EnumDeregReason.OTHER_BELOW_STANDARD;detail="逐件核实不符合入藏标准";baseline=CollectionCatalogMapper.fromCollection(CollectionDao["one"].toModel());dossier=DeregistrationDossierVo(proposedOn=java.time.LocalDate.of(2026,5,1),keeperUserId="keeper",leaderUserId="director",destinationType=EnumExitDestination.AUXILIARY_ACCOUNT,dispositionPlanFileIds="proof",auxiliaryRecordNo="AUX1",auxiliaryLocation="辅助库")}
        val scope=r.c.getBean(CollectionScopeResolver::class.java);val users=r.c.getBean(CollectionResponsibleUserService::class.java);val binder=r.c.getBean(BusinessAttachmentBinder::class.java)
        val statutory=DeregistrationStatutoryService(scope,binder,r.archives,CollectionCalendarService(scope,binder,r.archives),users,r.disclosures)
        (1..5).forEach {n->statutory.recordExpert("case",DeregistrationExpertOpinionVo(expertKey="expert$n",expertName="外部专家$n",organization="外部文博机构$n",professionalTitle="研究馆员",specialty="自然标本",seniorQualificationFileIds="proof",independenceStatementFileIds="proof",signedOpinionFileIds="proof",opinion="本人逐件核对，同意退出",agree=true,assessedAt=java.time.LocalDate.of(2026,5,20)))}
        val source=r.disclosures.detail("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"case")["sourceRevision"] as String
        val opinions=DeregistrationExpertOpinionDao.all().mapIndexed {i,e->PublicExpertOpinionVo(e.id.value,"专家${i+1}","该专家核实标本不符合入藏标准的独立理由${i+1}",true)}
        val review=r.disclosures.review("museum",EnumDisclosureSubject.EXIT_CONSULTATION,"case",CollectionDisclosureReviewVo(source,EnumDisclosureDecision.PUBLIC,LocalDateTime.of(2026,5,21,12,0),"reader","review",EnumDisclosureRestriction.entries.associateWith {DisclosureRestrictionVo(EnumDisclosureHandling.REDACTED,"核对公开范围")},content=CollectionPublicContentVo("退出公示","已核实","服务台",mapOf(EnumPublicFact.NAME to "铜器",EnumPublicFact.DISPOSITION_PLAN to "纳入辅助账"),expertOpinions=opinions),publicContentFileIds="review"))
        EnumExitNoticeChannel.entries.forEach {statutory.recordNotice("case",DeregistrationNoticeVo(disclosureReviewId=review,kind=EnumExitNoticeKind.CONSULTATION,channel=it,startedOn=java.time.LocalDate.of(2026,5,25),endedOn=java.time.LocalDate.of(2026,7,10),urlOrLocation="https://museum.example.org/consultation",publicContentFileIds="review",publicationEvidenceFileIds="proof"))}
        statutory.recordAdministrative("case",CollectionAdministrativeEvidenceVo(level=EnumAuthorityLevel.PROVINCE,action=EnumAdministrativeAction.APPROVAL,authorityName="省文物部门",documentNo="正式批复001",issuedAt=java.time.LocalDate.of(2026,7,12),fileIds="proof"))
        val locks=CollectionOperationLockService()
        val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),locks,mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),r.disclosures)
        val paperLedgers=mock(PaperLedgerService::class.java).also { org.mockito.Mockito.`when`(it.requirePageReference("museum","paper-book",1)).thenReturn(PaperLedgerService.PageReference("paper-book","总账第一册",1,20)) }
        val workflow=DeregistrationService(mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java),scope,collections,mock(CollectionPrintService::class.java),binder,locks,statutory,r.archives,CollectionAuxiliaryService(scope,binder,r.archives),paperLedgers)
        request.flowStatus=io.hizhengfu.cloud.common.domain.EnumFlowStatus.AUDIT;request.flowInstanceId="exit-flow"
        locks.acquire("museum",CollectionConst.BIZ_DEREGISTER,"case",listOf("one"))
        listOf("keeper" to "keeper","leader" to "director").forEach {(node,user)->io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.new {instanceId="exit-flow";taskId="task-$node";nodeKey=node;action="APPROVE";operator=user;comment="本人核对原件，确认同意"}}
        workflow.onFlowCompleted(request,io.hizhengfu.cloud.flow.domain.FlowCompletedEvent("exit-flow",CollectionConst.FLOW_DEREGISTER,"退出","case",CollectionConst.BIZ_DEREGISTER,emptyMap(),"reader","经办人"))
        workflow.execute("case",DeregistrationExecutionVo("proof",paperUpdatedAt=java.time.LocalDate.now(),dispositionReceiptFileIds="proof",paperLedgerBookId="paper-book",paperLedgerPageNo=1))
    }
    @ParameterizedTest @ValueSource(strings=["approvalLog","proposalFiles","closedTime","malformedExecution"])
    fun `anonymous exit manuscript download stops when underlying exit evidence changes`(change:String)=fixture {r->
        r.permission(CollectionConst.PUBLISH,CollectionConst.SELECT)
        prepareExecutedExit(r)
        val source=r.disclosures.detail("museum",EnumDisclosureSubject.EXIT_RESULT,"case")["sourceRevision"] as String
        val review=r.disclosures.review("museum",EnumDisclosureSubject.EXIT_RESULT,"case",CollectionDisclosureReviewVo(source,EnumDisclosureDecision.PUBLIC,LocalDateTime.now(),"reader","review",EnumDisclosureRestriction.entries.associateWith {DisclosureRestrictionVo(EnumDisclosureHandling.REDACTED,"核对公开范围")},content=CollectionPublicContentVo("退出结果","已核实实际退出","服务台",mapOf(EnumPublicFact.NAME to "铜器",EnumPublicFact.ACCOUNT_DISPOSITION to "保留原号和退出凭据",EnumPublicFact.OBJECT_DISPOSITION to "实物纳入辅助账")),publicContentFileIds="public"))
        r.db {
            val scope=r.c.getBean(CollectionScopeResolver::class.java);val users=r.c.getBean(CollectionResponsibleUserService::class.java);val binder=r.c.getBean(BusinessAttachmentBinder::class.java)
            val statutory=DeregistrationStatutoryService(scope,binder,r.archives,CollectionCalendarService(scope,binder,r.archives),users,r.disclosures)
            listOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE).forEach {statutory.recordNotice("case",DeregistrationNoticeVo(disclosureReviewId=review,kind=EnumExitNoticeKind.RESULT,channel=it,startedOn=java.time.LocalDate.now(),urlOrLocation="https://museum.example.org/result",publicContentFileIds="public",publicationEvidenceFileIds="proof"))}
        }
        r.disclosures.enable("museum",review)
        SecurityContextHolder.clearContext();r.allow("public.txt")
        r.db {when(change) {
            "approvalLog"->io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao.all().first().comment="后改签署"
            "closedTime"->DeregistrationDao["case"].apply {announcedAt=announcedAt!!.plusMinutes(1)}
            "malformedExecution"->CollectionPermanentArchiveDao.all().single {it.bizId=="case" && it.phase=="EXECUTED"}.apply {snapshot="{";digest=CollectionCareEvidence.digest(snapshot)}
            else->{val a=CollectionPermanentArchiveDao.all().single {it.bizId=="case" && it.phase=="PROPOSAL"};CollectionPermanentArchiveFileDao.all().filter {it.archiveId==a.id.value}.forEach {it.delete()}}
        }}
        r.deny("public.txt")
        login();assertFalse(r.disclosures.feed(EnumDisclosureSubject.EXIT_RESULT).single().available)
    }

    @ParameterizedTest @CsvSource("exit,false", "transfer,false", "exit,true", "transfer,true")
    fun `statutory notice only publishes its reviewed manuscript and retains posting evidence`(kind:String,overlap:Boolean) = fixture { r ->
        val biz=if(kind=="exit")CollectionConst.BIZ_DEREGISTER else CollectionConst.BIZ_TRANSFER
        val subject=if(kind=="exit")EnumDisclosureSubject.EXIT_RESULT else EnumDisclosureSubject.TRANSFER_RESULT
        r.permission(CollectionConst.PUBLISH,CollectionConst.SELECT)
        if(kind=="exit")prepareExecutedExit(r)
        else r.db {
            CollectionTransferDao.new("case") {orgId="museum";toOrgId="other";collectionId="one";baseline=CollectionCatalogMapper.fromCollection(CollectionDao["one"].toModel());executedAt=at
                execution=CollectionTransferExecutionVo(handedOn=at.toLocalDate(),sourceCustodianUserId="keeper",targetCustodianUserId="receiver",signedHandoverFileIds="proof",conditionDescription="已核对",conditionImageFileIds="proof",authenticityVerified=true,signedLedgerChangeFileIds="proof",receivingLedgerReceiptFileIds="proof")}
        }
        if(kind!="exit")r.seal(biz,phase="EXECUTED")
        val reviewedAt=if(kind=="exit")LocalDateTime.now() else at.plusDays(1)
        val source=r.disclosures.detail("museum",subject,"case")["sourceRevision"] as String
        val reviewId=r.disclosures.review("museum",subject,"case",CollectionDisclosureReviewVo(source,EnumDisclosureDecision.PUBLIC,reviewedAt,"reader","review",
            EnumDisclosureRestriction.entries.associateWith { DisclosureRestrictionVo(EnumDisclosureHandling.REDACTED,"核对公开范围") },
            content=CollectionPublicContentVo("退出结果","已核实退出事实","服务台",mapOf(EnumPublicFact.NAME to "铜器",EnumPublicFact.ACCOUNT_DISPOSITION to "原号永久保留",EnumPublicFact.OBJECT_DISPOSITION to "完成实物交接")),publicContentFileIds="public"))
        val notice=DeregistrationNoticeVo(kind=EnumExitNoticeKind.RESULT,channel=EnumExitNoticeChannel.MUSEUM_WEBSITE,startedOn=if(kind=="exit")java.time.LocalDate.now() else at.plusDays(2).toLocalDate(),urlOrLocation="https://museum.example.org/result",publicContentFileIds="public",publicationEvidenceFileIds=if(overlap)"public" else "proof",disclosureReviewId=reviewId)
        val scope=r.c.getBean(CollectionScopeResolver::class.java);val users=r.c.getBean(CollectionResponsibleUserService::class.java);val binder=r.c.getBean(BusinessAttachmentBinder::class.java)
        // 使用实际公示登记服务写入同一版公开稿及公告档案；人员资格已由本测试的外部签署夹具提供。
        r.db {
            if(kind=="exit") {actorRole("reader","EXIT_EDITOR",setOf(CollectionConst.DEREGISTER));DeregistrationStatutoryService(scope,binder,r.archives,CollectionCalendarService(scope,binder,r.archives),users,r.disclosures).recordNotice("case",notice)}
            else {
                actorRole("reader","TRANSFER_NOTICE_EDITOR",setOf(CollectionConst.LEDGER_CORRECT))
                CollectionTransferStatutoryService(scope,users,binder,r.archives,mock(CollectionArchivePacketService::class.java),r.disclosures).recordNotice("case",notice)
            }
        }
        r.disclosures.enable("museum",reviewId)
        SecurityContextHolder.clearContext()
        if(overlap)r.deny("public.txt") else r.allow("public.txt")
        r.deny();r.deny("review.txt")
        if(!overlap) {
            r.db {CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase like "NOTICE_%"}.single {JsonUtils.mapper.readTree(it.snapshot).path("kind").asText()=="RESULT"}.snapshot="{}"}
            r.deny("public.txt")
        }
    }
    @ParameterizedTest @ValueSource(strings=["COLLECTION_WAREHOUSE_STAFFING", "COLLECTION_STAFFING_HANDOVER", "COLLECTION_LEADERSHIP_DEPARTURE", "COLLECTION_WAREHOUSE_DIARY", CollectionConst.BIZ_WAREHOUSE_ENTRY, CollectionConst.BIZ_INVENTORY, InventoryComplianceService.BIZ, InventoryComplianceService.ANNUAL, CollectionConst.BIZ_REQUISITION, CollectionConst.BIZ_LOAN, CollectionConst.BIZ_RETURN, CollectionConst.BIZ_INCIDENT, CollectionConst.BIZ_INCIDENT_WORK, CollectionConst.BIZ_INCIDENT_RELOCATION, CollectionConst.BIZ_CARE])
    fun `personnel file originals respect single museum configuration even with current cross grant`(biz:String)=fixture {r ->
        r.seal(biz)
        // 模拟数据库中既存的外馆原件；单馆正常归档入口本身已拒绝创建这类越界记录。
        r.db {
            CollectionPermanentArchiveDao.all().single().orgId="other"
            CollectionPermanentArchiveFileDao.all().forEach {
                val metadata=JsonUtils.mapper.readTree(it.metadata)
                it.orgId="other"
                it.metadata=CollectionArchiveFileIdentity.metadata(FileDao[it.fileId],"other",biz,it.bizId,
                    CollectionArchiveFileIdentity.candidates(metadata),CollectionArchiveFileIdentity.content(metadata),CollectionArchiveFileIdentity.revision(metadata))
            }
        }
        r.permission(CollectionConst.WAREHOUSE_MANAGE,CollectionConst.ORG_MANAGE,CollectionConst.CROSS_ORG,CollectionConst.WAREHOUSE_ENTRY_APPLY,CollectionConst.STORAGE,CollectionConst.INCIDENT,CollectionConst.CARE)
        val error=Assertions.assertThrows(BadRequestException::class.java){r.access.requireReadable("proof.txt","LOCAL")}
        assertTrue(error.message!!.contains("当前配置收藏单位"), error.message)
        r.deny()
    }

    @ParameterizedTest @ValueSource(strings=[CollectionConst.BIZ_ACQUISITION, CollectionConst.BIZ_ACQUISITION_PLAN, DesignatedReceptionEvidence.BIZ, AccessionReceiptService.BIZ])
    fun `acquisition chain originals obey single museum configuration despite cross grant`(biz:String)=fixture {r ->
        r.seal(biz)
        r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG);r.allow()
        r.db {
            CollectionPermanentArchiveDao.all().single().orgId="other"
            CollectionPermanentArchiveFileDao.all().forEach {
                val metadata=JsonUtils.mapper.readTree(it.metadata)
                it.orgId="other"
                it.metadata=CollectionArchiveFileIdentity.metadata(FileDao[it.fileId],"other",biz,it.bizId,
                    CollectionArchiveFileIdentity.candidates(metadata),CollectionArchiveFileIdentity.content(metadata),CollectionArchiveFileIdentity.revision(metadata))
            }
        }
        val error=assertThrows(BadRequestException::class.java){r.access.requireReadable("proof.txt","LOCAL")}
        assertTrue(error.message!!.contains("当前配置收藏单位"),error.message)
        r.deny()
    }

    @Test fun `acquisition draft originals obey single museum configuration despite cross grant`()=fixture {r ->
        r.db {AcquisitionDao.new("draft") {orgId="museum";name="未提交征集";contractAttachmentIds="proof"}}
        r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG);r.allow()
        r.db {AcquisitionDao["draft"].orgId="other"}
        val error=assertThrows(BadRequestException::class.java){r.access.requireReadable("proof.txt","LOCAL")}
        assertTrue(error.message!!.contains("当前配置收藏单位"),error.message)
        r.deny()
    }

    @ParameterizedTest @ValueSource(strings=[CollectionConst.BIZ_ACQUISITION, CollectionConst.BIZ_ACQUISITION_PLAN, DesignatedReceptionEvidence.BIZ, AccessionReceiptService.BIZ])
    fun `acquisition chain retains explicitly authorized multi museum originals`(biz:String)=multiFixture {r ->
        r.seal(biz,org="other")
        r.permission(CollectionConst.ACQUIRE);r.deny()
        r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG);r.allow()
        r.permission(CollectionConst.CROSS_ORG);r.deny()
        r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false};r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["draft","permanent"])
    fun `acquisition confidential originals preserve current role duty without acquisition permission`(kind:String)=fixture {r ->
        if(kind=="draft") r.db {AcquisitionDao.new("draft") {orgId="museum";name="征集协议";contractAttachmentIds="proof"}}
        else r.seal(CollectionConst.BIZ_ACQUISITION)
        r.permission();r.deny()
        val roleId=r.db {actorRole("reader",CollectionConst.ROLE_DIRECTOR)};r.allow()
        r.db {RoleUsers.deleteWhere {(RoleUsers.userId eq "reader") and (RoleUsers.roleId eq roleId)}}
        r.deny()
    }

    @Test fun `acquisition shared draft file requires every museum to satisfy deployment scope`()=fixture {r ->
        r.db {
            AcquisitionDao.new("local") {orgId="museum";name="本馆征集";contractAttachmentIds="proof"}
            AcquisitionDao.new("foreign") {orgId="other";name="外馆征集";contractAttachmentIds="proof"}
        }
        r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG);r.deny()
    }

    @Test fun `acquisition current draft retains explicit multi museum duty and revocation`()=multiFixture {r ->
        r.db {AcquisitionDao.new("foreign") {orgId="other";name="外馆征集";contractAttachmentIds="proof"}}
        r.permission(CollectionConst.ACQUIRE);r.deny()
        r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG);r.allow()
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.deny()
    }

    @ParameterizedTest @ValueSource(strings=[CollectionConst.BIZ_ACQUISITION, CollectionConst.BIZ_ACQUISITION_PLAN, DesignatedReceptionEvidence.BIZ, AccessionReceiptService.BIZ])
    fun `acquisition cached list preview temporary read and zip cannot bypass single museum`(biz:String)=fixture {r ->
        val archive=r.seal(biz);r.permission(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG)
        val files=r.c.getBean(FileService::class.java);val storage=r.c.getBean(FileObjectStorageService::class.java)
        val front=FileFrontController(mock(FileSystemStorageService::class.java),files)
        fun listing()=front.findByContent(archive,ParamsFile(),MockHttpServletRequest())
        listing();listing()
        r.db {
            CollectionPermanentArchiveDao[archive].orgId="other"
            CollectionPermanentArchiveFileDao.all().forEach {
                val metadata=JsonUtils.mapper.readTree(it.metadata)
                it.orgId="other"
                it.metadata=CollectionArchiveFileIdentity.metadata(FileDao[it.fileId],"other",biz,it.bizId,
                    CollectionArchiveFileIdentity.candidates(metadata),CollectionArchiveFileIdentity.content(metadata),CollectionArchiveFileIdentity.revision(metadata))
            }
        }
        assertThrows(BadRequestException::class.java){listing()}
        val converter=mock(FileConvertService::class.java);val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java){FilePreviewController(files,converter,storage,r.access).previewDocument("proof","pdf",response)}
        verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
        val zipped=MockHttpServletResponse();assertThrows(BadRequestException::class.java){files.zipContentFile(archive,zipped)};assertEquals(0,zipped.contentAsByteArray.size)
        assertThrows(BadRequestException::class.java){storage.withTempFile("proof.txt","LOCAL",".txt"){throw AssertionError("Must reject before opening")}}
    }

    @Test fun `personnel education keeps self access and denies other staff originals`() = fixture { r ->
        r.db { CollectionStaffEducationDao.new("education") { orgId="museum"; userId="reader"; requestKey="key"; original=StaffEducationOriginal("education","museum","reader",StaffEducationCreate("key","reader",employmentFileIds="proof",plan=StaffEducationPlan(EnumStaffEducationKind.POST_TRAINING,"课程","保管","技能",at,"proof")),"reader",at) } }
        r.seal(CollectionStaffEducationService.BIZ,id="education"); r.permission(); r.allow()
        r.db { originalReader("peer", CollectionConst.SELECT) }; login("peer"); r.deny()
    }
    @Test fun `leadership role only applies to actual museum membership`() = fixture { r ->
        r.seal(LeadershipDepartureEvidence.BIZ); r.permission(); r.db { actorRole("reader",CollectionConst.ROLE_DIRECTOR) }; r.allow()
        r.db { UserDao["reader"].groupId="other"; actorRole("reader","FILE_reader",setOf(CollectionConst.CROSS_ORG)) }; r.deny()
    }
    @Test fun `care original file keeps original museum access after current collection transfer`()=fixture {r ->
        r.seal(CollectionConst.BIZ_CARE,content=mapOf("collectionId" to "one"))
        r.permission(CollectionConst.CARE);r.allow()
        r.db {CollectionDao["one"].apply {orgId="other";name="接收馆当前内部名称"}}
        r.allow();r.permission();r.deny()
    }
    @Test fun `care original files require explicit current multi museum grant`()=multiFixture {r ->
        r.seal(CollectionConst.BIZ_CARE,org="other")
        r.permission(CollectionConst.CARE);r.deny()
        r.permission(CollectionConst.CARE,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false};r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["COLLECTION_AUXILIARY",CollectionConst.BIZ_DEREGISTER])
    fun `ledger and exit originals obey single museum configuration despite current cross grant`(biz:String)=fixture {r ->
        r.seal(biz,phase="REGISTERED")
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.db {
            CollectionPermanentArchiveDao.all().single().orgId="other"
            CollectionPermanentArchiveFileDao.all().forEach {
                val metadata=JsonUtils.mapper.readTree(it.metadata)
                it.orgId="other"
                it.metadata=CollectionArchiveFileIdentity.metadata(FileDao[it.fileId],"other",biz,it.bizId,
                    CollectionArchiveFileIdentity.candidates(metadata),CollectionArchiveFileIdentity.content(metadata),CollectionArchiveFileIdentity.revision(metadata))
            }
        }
        val error=Assertions.assertThrows(BadRequestException::class.java){r.access.requireReadable("proof.txt","LOCAL")}
        assertTrue(error.message!!.contains("当前配置收藏单位"),error.message)
        r.deny()
    }

    @ParameterizedTest @ValueSource(strings=["COLLECTION_AUXILIARY",CollectionConst.BIZ_DEREGISTER])
    fun `ledger and exit originals preserve explicit multi museum history while revocation denies byte output`(biz:String)=multiFixture {r ->
        r.seal(biz,org="other",phase="REGISTERED")
        r.permission(CollectionConst.SELECT);r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.permission(CollectionConst.CROSS_ORG);r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
        r.db {UserDao["reader"].enabled=false};r.deny()
    }

}
