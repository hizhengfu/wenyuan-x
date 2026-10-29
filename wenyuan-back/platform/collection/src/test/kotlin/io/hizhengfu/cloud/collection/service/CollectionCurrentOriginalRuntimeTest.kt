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

class CollectionCurrentOriginalRuntimeTest {
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
    private fun login(id: String = "reader") {
        val user = SecurityUser(id=id, name=id, username=id, password="", groupId="museum", groupIds=setOf("museum"), postIds=emptySet(), dataType=0,
            roles=setOf(EnumRole.ADMIN.value), roleCodes=setOf(CollectionConst.ROLE_DIRECTOR), authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    private fun fixture(body: (Runtime) -> Unit)=fixture(CollectionScopeResolver("single","museum"),body)
    private fun fixture(scope:CollectionScopeResolver,body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("current-original.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("localStorage", LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator", org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope", scope)
            c.beanFactory.registerSingleton("users", mock(CollectionResponsibleUserService::class.java))
            c.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier { FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java), mock(FileStorageConfigReader::class.java), mock(RustFsStorageClient::class.java), c.getBean(FileReadAccess::class.java)) })
            c.registerBean(FileService::class.java, java.util.function.Supplier { FileService(dir.toString(), FileFolderService(), c.getBean(FileObjectStorageService::class.java), listOf(CollectionArchiveFileRetentionGuard(), c.getBean(CollectionBusinessOriginalFileRetentionGuard::class.java)), c.getBean(FileReadAccess::class.java)) })
            c.beanFactory.registerSingleton("flowInstances", mock(io.hizhengfu.cloud.flow.service.FlowInstanceService::class.java))
            c.register(WarehouseService::class.java, AcquisitionPlanService::class.java, CollectionBusinessOriginalFileSource::class.java, CollectionBusinessOriginalFiles::class.java, CollectionOriginalIdentityReadPolicy::class.java, CollectionBusinessOriginalFileReadGuard::class.java, CollectionBusinessOriginalFileRetentionGuard::class.java)
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java, FileReadAccess::class.java,
                BusinessAttachmentBinder::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                CollectionBusinessArchiveReadPolicy::class.java, CollectionPersonnelArchiveReadPolicy::class.java, CollectionTransferArchiveReadPolicy::class.java,
                CollectionDisclosureArchiveReadPolicy::class.java, CollectionDisclosureService::class.java, StaffEducationAccess::class.java,
                AcquisitionOriginalFiles::class.java, AcquisitionFileReadGuard::class.java, DigitalAcquisitionFileReadGuard::class.java,
                CollectionEmergencyFileReadGuard::class.java, CollectionStaffEducationFileReadGuard::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager), dir)
            r.db {
                originalReaderSchema()
                SchemaUtils.create(*CollectionBusinessOriginalFileSource().tables.map { it.table }.distinct().toTypedArray())
                SchemaUtils.create(IncidentRelocations)
                SchemaUtils.create(CollectionOrgs, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents, FileUsers,
                    Acquisitions, AcquisitionComplianceCases, AcquisitionExpertOpinions, DigitalAcquisitionFileAccesses, CollectionEmergencyFileAccesses,
                    CollectionStaffEducations, CollectionStaffEducationFileAccesses, CollectionTransfers, CollectionDisclosureReviews, CollectionDisclosureEvents,
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

    private fun loan(r: Runtime, kind: String = "attachment") = r.db {
        LoanDao.new("loan") {
            orgId="museum"
            when(kind) {
                "attachment" -> attachmentIds="proof"
                "condition" -> conditionPhotoIds="proof"
                "signature" -> signedVoucherAttachmentIds="proof"
            }
        }
        if(kind=="binding") FileContents.insert { it[fileId]="proof"; it[contentId]="loan" }
    }
    private fun plan(id: String? = null, file: String? = "proof") = AcquisitionPlanVo(id=id,title="年度征集计划",year=2026,attachmentIds=file)

    private fun approvedExhibitionSource(r: Runtime) {
        r.db {
            FileUsers.deleteAll()
            listOf("proof", "review").forEach { id -> FileUsers.insert { it[fileId]=id;it[userId]="archivist";it[uploadDate]=at } }
            ExhibitionDao.new("exhibition") { orgId="museum";title="本馆展览";collectionIds="one";attachmentIds="proof";flowStatus=EnumFlowStatus.PASS }
            RequisitionDao.new("derived") { orgId="museum";collectionIds="one";purpose=EnumMoveReason.EXHIBIT;sourceBizType=CollectionConst.BIZ_EXHIBITION;sourceBizId="exhibition";attachmentIds="proof" }
        }
        login("archivist")
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionConst.BIZ_EXHIBITION,"exhibition")
        r.permission(CollectionConst.STORAGE)
        val user=SecurityUser(id="reader",name="保管审批人",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,
            roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun originalPersistenceState(r:Runtime)=r.db {
        listOf(CollectionPermanentArchives,CollectionPermanentArchiveFiles,FileContents).associate {table ->
            table.tableName to table.selectAll().map {row -> table.columns.map {column -> row[column]}}.sortedBy {it.joinToString("|")}
        }
    }
    @Test fun `ordinary approver inherits approved exhibition original into matching derived requisition without uploader grant`() = fixture { r ->
        approvedExhibitionSource(r)
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionConst.BIZ_REQUISITION,"derived")
        r.db {
            val source=CollectionPermanentArchiveDao.all().single {it.bizId=="exhibition"}
            val derived=CollectionPermanentArchiveDao.all().single {it.bizId=="derived"}
            assertEquals(CollectionBusinessOriginalFiles.BIZ,derived.bizType)
            assertEquals(CollectionConst.BIZ_REQUISITION,JsonUtils.mapper.readTree(derived.snapshot).path("businessType").asString())
            val from=CollectionPermanentArchiveFileDao.all().single {it.archiveId==source.id.value}
            val to=CollectionPermanentArchiveFileDao.all().single {it.archiveId==derived.id.value}
            assertEquals("proof",to.fileId)
            assertEquals(JsonUtils.mapper.readTree(from.metadata).path("_archiveContent"),JsonUtils.mapper.readTree(to.metadata).path("_archiveContent"))
            assertTrue(FileUsers.selectAll().where {(FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader")}.empty())
        }
    }
    @ParameterizedTest @ValueSource(strings=["draft","wrong_org","wrong_collection","wrong_purpose","wrong_source","not_source_file","missing_retained_source","damaged_bytes"])
    fun `derived exhibition inheritance rejects invalid source and foreign new material with full archive rollback`(kind:String) = fixture { r ->
        approvedExhibitionSource(r)
        r.db { when(kind) {
            "draft" -> ExhibitionDao["exhibition"].flowStatus=EnumFlowStatus.DRAFT
            "wrong_org" -> ExhibitionDao["exhibition"].orgId="other"
            "wrong_collection" -> RequisitionDao["derived"].collectionIds="different"
            "wrong_purpose" -> RequisitionDao["derived"].purpose=EnumMoveReason.RESEARCH
            "wrong_source" -> RequisitionDao["derived"].sourceBizId="missing"
            "not_source_file" -> RequisitionDao["derived"].attachmentIds="review"
            "missing_retained_source" -> { CollectionPermanentArchiveFileDao.all().toList().forEach {it.delete()};CollectionPermanentArchiveDao.all().toList().forEach {it.delete()} }
        } }
        if(kind=="damaged_bytes")java.nio.file.Files.writeString(dir.resolve("proof.txt"),"XONTENT:proof.txt")
        val before=originalPersistenceState(r)
        assertThrows(BadRequestException::class.java) {r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionConst.BIZ_REQUISITION,"derived")}
        r.db {
            assertTrue(FileUsers.selectAll().where {(FileUsers.fileId eq "proof") and (FileUsers.userId eq "reader")}.empty())
        }
        assertEquals(before,originalPersistenceState(r))
    }

    @ParameterizedTest @ValueSource(strings=["attachment","condition","signature","binding"])
    fun `unarchived loan originals are private even without permanent archive`(kind:String) = fixture { r ->
        loan(r,kind); r.db { assertEquals(0L,CollectionPermanentArchiveDao.count()) }
        r.allow(); r.permission(); r.deny()
    }
    @Test fun `catalog JSON file is protected without ordinary attachment binding`() = fixture { r ->
        r.db { CollectionChangeDao.new("change") {
            orgId="museum";collectionId="one";reason="修改照片"
            before=CollectionCatalogContentVo();content=CollectionCatalogContentVo(coverImage="proof");baseline=before
        } }
        r.allow(); r.permission();r.deny()
    }
    @Test fun `only explicit JSON file fields are recognized`() {
        val data=JsonUtils.mapper.readTree("""{"reason":"proof","arbitraryId":"proof","nested":[{"signedFileIds":["review","replacement"]}],"coverImage":"public"}""")
        assertEquals(listOf("review","replacement","public"),CollectionOriginalJsonFiles.references(data))
    }
    @Test fun `child signatures retain parent authority and cannot be unlinked by child id`() = fixture { r ->
        r.db {
            AppraisalDao.new("appraisal") { orgId="museum";collectionId="one" }
            AppraisalMemberDao.new("member") { orgId="museum";appraisalId="appraisal";memberName="专家";signatureFileId="proof" }
            FileContents.insert { it[fileId]="proof";it[contentId]="member" }
        }
        r.allow()
        assertThrows(BadRequestException::class.java) { r.c.getBean(FileService::class.java).deleteByFileIdAndContentId("proof","member") }
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionConst.BIZ_APPRAISAL,"appraisal")
        r.db { AppraisalMemberDao["member"].delete(); FileContents.deleteAll() }
        r.permission();r.deny()
        r.db { assertEquals("appraisal",CollectionPermanentArchiveDao.all().single().bizId) }
    }
    @ParameterizedTest @ValueSource(strings=["missing", "wrong_org"])
    fun `orphaned or cross museum appraisal child cannot release a signature`(kind:String) = fixture { r ->
        r.db {
            if(kind=="wrong_org") AppraisalDao.new("parent") { orgId="other";collectionId="one" }
            AppraisalMemberDao.new("child") { orgId="museum";appraisalId="parent";memberName="专家";signatureFileId="proof" }
        };r.deny()
    }
    @Test fun `real plan saves retain replaced material without changing approval state`() = fixture { r ->
        val service=r.c.getBean(AcquisitionPlanService::class.java)
        val id=service.save(plan())
        service.save(plan(id,"replacement"));service.save(plan(id,"replacement"))
        r.db {
            assertEquals(EnumFlowStatus.DRAFT,AcquisitionPlanDao[id].flowStatus)
            org.junit.jupiter.api.Assertions.assertNull(AcquisitionPlanDao[id].flowInstanceId)
            assertEquals(2L,CollectionPermanentArchiveDao.count())
            assertTrue(r.archives.list(CollectionConst.BIZ_ACQUISITION_PLAN,id).isEmpty())
            assertTrue(CollectionPermanentArchiveDao.all().all { it.phase.startsWith(CollectionBusinessOriginalFiles.PHASE) })
            FileContents.deleteAll()
        }
        r.permission();r.deny();r.deny("replacement.txt")
    }
    @Test fun `replacement of a legacy draft captures its previous unbound original`() = fixture { r ->
        r.db { AcquisitionPlanDao.new("legacy") { orgId="museum";title="旧草稿";year=2025;attachmentIds="proof" } }
        r.c.getBean(AcquisitionPlanService::class.java).save(plan("legacy","replacement"))
        r.db { FileContents.deleteAll() }
        r.permission();r.deny();r.deny("replacement.txt")
    }
    @Test fun `missing replacement file rolls back both draft and identity archives`() = fixture { r ->
        r.db { AcquisitionPlanDao.new("legacy") { orgId="museum";title="旧草稿";year=2025;attachmentIds="proof" } }
        assertThrows(BadRequestException::class.java) { r.c.getBean(AcquisitionPlanService::class.java).save(plan("legacy","missing")) }
        r.db {
            assertEquals("proof",AcquisitionPlanDao["legacy"].attachmentIds)
            assertEquals("旧草稿",AcquisitionPlanDao["legacy"].title)
            assertEquals(0L,CollectionPermanentArchiveDao.count())
        }
    }
    @Test fun `current originals resist file deletion and ordinary unbinding`() = fixture { r ->
        loan(r,"binding")
        val files=r.c.getBean(FileService::class.java)
        assertThrows(BadRequestException::class.java) { files.delete("proof") }
        assertThrows(BadRequestException::class.java) { files.deleteByIds(listOf("public","proof")) }
        assertThrows(BadRequestException::class.java) { files.deleteByFileIdAndContentId("proof","loan") }
        r.db { org.junit.jupiter.api.Assertions.assertNotNull(FileDao.findById("public")); org.junit.jupiter.api.Assertions.assertNotNull(FileDao.findById("proof")); assertEquals(1L,FileContents.selectAll().count()) }
        files.delete("public")
        r.db { org.junit.jupiter.api.Assertions.assertNull(FileDao.findById("public")) }
    }
    @ParameterizedTest @ValueSource(strings=["disabled","locked","role_removed","role_disabled","moved","anonymous"])
    fun `current permissions override the old privileged login for drafts`(kind:String) = fixture { r ->
        loan(r);r.allow()
        r.db { when(kind) {
            "disabled" -> UserDao["reader"].enabled=false
            "locked" -> Users.update({ Users.id eq "reader" }) { it[accountNonLocked]=false }
            "role_removed" -> RoleUsers.deleteAll()
            "role_disabled" -> Roles.update { it[enabled]=false }
            "moved" -> UserDao["reader"].groupId="other"
            "anonymous" -> SecurityContextHolder.clearContext()
        } };r.deny()
    }
    @Test fun `shared draft origins all require current organization access`() = fixture(CollectionScopeResolver("multi","")) { r ->
        loan(r)
        r.db { LoanDao.new("other-loan") { orgId="other";attachmentIds="proof" } }
        r.deny();r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG);r.allow()
    }
    @Test fun `single museum refuses shared foreign draft original despite current cross permission`() = fixture { r ->
        loan(r)
        r.db { LoanDao.new("other-loan") { orgId="other";attachmentIds="proof" } }
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        r.deny()
    }
    private fun transfer(r:Runtime) = r.db {
        CollectionTransferDao.new("transfer") { orgId="museum";collectionId="one";toOrgId="other" }
        listOf("museum","other").forEach { org -> StorageMovementDao.new("movement-$org") {
            orgId=org;collectionId="one";type=EnumMoveType.IN;reason=EnumMoveReason.TRANSFER;refBizId="transfer";attachmentIds="proof"
        } }
    }
    @Test fun `derived transfer movements keep joint authority for actual ledger operator`() = fixture { r ->
        transfer(r);r.permission(CollectionConst.LEDGER_CORRECT);r.allow()
        r.db { UserDao["reader"].groupId="other" };r.allow()
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionBusinessOriginalFileSource.MOVEMENT,"movement-museum")
        r.c.getBean(CollectionBusinessOriginalFiles::class.java).remember(CollectionBusinessOriginalFileSource.MOVEMENT,"movement-other")
        r.db { assertEquals(setOf("museum","other"),CollectionPermanentArchiveDao.all().map { it.orgId }.toSet());assertTrue(CollectionPermanentArchiveDao.all().all { JsonUtils.mapper.readTree(it.snapshot).path("businessType").asString()==CollectionConst.BIZ_TRANSFER }) }
        r.permission();r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["missing","wrong_org","wrong_collection"])
    fun `invalid movement origin cannot become an ordinary readable attachment`(kind:String) = fixture { r ->
        transfer(r)
        r.db { when(kind) {
            "missing" -> StorageMovementDao["movement-museum"].refBizId="missing"
            "wrong_org" -> StorageMovementDao["movement-museum"].orgId="unrelated"
            "wrong_collection" -> StorageMovementDao["movement-museum"].collectionId="wrong"
        } };r.deny()
    }
    @Test fun `emergency movement originals use incident authority`() = fixture { r ->
        r.db {
            IncidentRelocationDao.new("relocation") { orgId="museum";collectionId="one";mode=EnumIncidentRelocationMode.PLANNED }
            StorageMovementDao.new("movement") { orgId="museum";collectionId="one";type=EnumMoveType.OUT;reason=EnumMoveReason.EMERGENCY;refBizId="relocation";attachmentIds="proof" }
        }
        r.deny();r.permission(CollectionConst.INCIDENT);r.allow()
    }
    @ParameterizedTest @ValueSource(strings=["symlink","hardlink","rename"])
    fun `remembered draft identity protects aliases and renamed local files`(kind:String) = fixture { r ->
        r.c.getBean(AcquisitionPlanService::class.java).save(plan())
        val alias=dir.resolve("alias.txt")
        when(kind) {
            "symlink" -> java.nio.file.Files.createSymbolicLink(alias,Path.of("proof.txt"))
            "hardlink" -> java.nio.file.Files.createLink(alias,dir.resolve("proof.txt"))
            "rename" -> java.nio.file.Files.move(dir.resolve("proof.txt"),alias)
        }
        r.permission();r.deny("alias.txt")
    }
    @ParameterizedTest @ValueSource(strings=["digest", "file", "unknown", "recursive"])
    fun `retained identity cannot lose its source rule after business removal`(kind:String) = fixture { r ->
        val id=r.c.getBean(AcquisitionPlanService::class.java).save(plan())
        r.db {
            AcquisitionPlanDao[id].delete();FileContents.deleteAll()
            val archive=CollectionPermanentArchiveDao.all().single()
            if(kind=="digest") archive.digest="changed" else {
                val data=JsonUtils.mapper.readTree(archive.snapshot) as tools.jackson.databind.node.ObjectNode
                if(kind=="file") data.put("fileId","another") else data.put("businessType",if(kind=="recursive") CollectionBusinessOriginalFiles.BIZ else "MISSING_MODULE")
                archive.snapshot=data.toString();archive.digest=CollectionCareEvidence.digest(archive.snapshot)
            }
        };r.deny()
    }
    @Test fun `warehouse edit and delete preserve both thumbnail originals`() = fixture { r ->
        r.db { SchemaUtils.create(*io.hizhengfu.cloud.collection.install.CollectionInstall("single").initTables().toTypedArray()) }
        val service=r.c.getBean(WarehouseService::class.java)
        val id=service.create(WarehouseVo(name="库房",thumbnail="proof",type=EnumWarehouseType.WAREHOUSE))
        service.update(id,WarehouseVo(name="库房",thumbnail="replacement",type=EnumWarehouseType.WAREHOUSE))
        service.delete(id)
        r.db { FileContents.deleteAll();assertEquals(2L,CollectionPermanentArchiveDao.count()) }
        r.permission();r.deny();r.deny("replacement.txt")
    }
    @ParameterizedTest @CsvSource("missing_file,preview", "renamed_file,preview", "hardlink,preview", "no_read,preview", "missing_file,quarantine", "renamed_file,quarantine", "hardlink,quarantine", "no_read,quarantine")
    fun `orphan cleanup retains archived originals after file catalog drift`(kind: String, action: String) = fixture { r ->
        val original = dir.resolve("files/proof.txt")
        java.nio.file.Files.createDirectories(original.parent)
        java.nio.file.Files.move(dir.resolve("proof.txt"), original)
        r.db { FileDao["proof"].path = "files/proof.txt" }
        loan(r)
        r.seal(CollectionConst.BIZ_LOAN, id = "loan")
        r.db {
            if (kind == "renamed_file") FileDao["proof"].path = "files/renamed.txt"
            else FileDao["proof"].delete()
        }
        if (kind == "hardlink") java.nio.file.Files.createLink(dir.resolve("files/alias.txt"), original)
        if (kind == "no_read") SecurityContextHolder.clearContext()
        val cleanup = FileOrphanCleanupService(
            LocalStoredFilePathSource { r.c.getBean(FileService::class.java).findLocalStoredPaths() },
            dir.toString(), dir.resolveSibling("${dir.fileName}-quarantine").toString(), "files", 0,
            r.access,
        )
        val request = io.hizhengfu.cloud.sys.file.domain.FileOrphanCleanupRequest(confirm = true)
        if (action == "preview") assertEquals(0, cleanup.preview(request).candidateCount, "永久档案原件不能成为孤立候选")
        else assertEquals(0, cleanup.quarantine(request).movedCount, "永久档案原件不能被移走")
        assertEquals("CONTENT:proof.txt", java.nio.file.Files.readString(original))
        if (kind == "hardlink") assertTrue(java.nio.file.Files.exists(dir.resolve("files/alias.txt")))
    }

    @ParameterizedTest @ValueSource(strings=["valid", "missing_header", "invalid_metadata"])
    fun `orphan cleanup rechecks late archive before moving the original`(kind: String) = fixture { r ->
        val original = dir.resolve("files/proof.txt")
        java.nio.file.Files.createDirectories(original.parent)
        java.nio.file.Files.move(dir.resolve("proof.txt"), original)
        r.db { FileDao["proof"].path = "files/proof.txt" }
        var reads = 0
        val cleanup = FileOrphanCleanupService(LocalStoredFilePathSource {
            if (++reads == 2) {
                loan(r)
                val archive = r.seal(CollectionConst.BIZ_LOAN, id = "loan")
                r.db {
                    FileDao["proof"].delete()
                    if (kind == "missing_header") CollectionPermanentArchiveDao[archive].delete()
                    if (kind == "invalid_metadata") CollectionPermanentArchiveFileDao.all().single().metadata = "{"
                }
            }
            emptySet()
        }, dir.toString(), dir.resolveSibling("${dir.fileName}-quarantine").toString(), "files", 0, r.access)
        val result = cleanup.quarantine(io.hizhengfu.cloud.sys.file.domain.FileOrphanCleanupRequest(confirm = true))
        assertEquals(2, reads)
        assertEquals(1, result.candidateCount)
        assertEquals(0, result.movedCount)
        assertEquals(if (kind == "valid") 1 else 0, result.skippedReferencedCount)
        assertEquals(if (kind == "valid") 0 else 1, result.failedCount)
        assertEquals("CONTENT:proof.txt", java.nio.file.Files.readString(original))
        val manifest = java.nio.file.Files.readString(Path.of(requireNotNull(result.manifestPath)))
        assertTrue(manifest.contains(if (kind == "valid") "SKIPPED_REFERENCED" else "FAILED"))
    }

    @ParameterizedTest @ValueSource(strings = ["upload", "merge", "delete"])
    fun `chunk operations reject a hardlink to a permanent original`(action: String) = fixture { r ->
        loan(r)
        r.seal(CollectionConst.BIZ_LOAN, id = "loan")
        val temporary = dir.resolve("temp/session_0")
        java.nio.file.Files.createDirectories(temporary.parent)
        java.nio.file.Files.createLink(temporary, dir.resolve("proof.txt"))
        val storage = FileSystemStorageService(dir.toString(), r.c.getBean(FileService::class.java), FileFolderService(), r.c,
            r.c.getBean(FileObjectStorageService::class.java), r.access)
        assertThrows(BadRequestException::class.java) {
            when (action) {
                "upload" -> storage.upload(java.io.ByteArrayInputStream("other".toByteArray()), "proof.txt", "unused", uuid = "session", startOffset = 0)
                "merge" -> storage.mergeChunks("session", "proof.txt", java.nio.file.Files.size(temporary))
                else -> storage.delete("session")
            }
        }
        assertEquals("CONTENT:proof.txt", java.nio.file.Files.readString(dir.resolve("proof.txt")))
        assertTrue(java.nio.file.Files.exists(temporary))
        r.db { assertEquals(1L, CollectionPermanentArchiveDao.count()); assertEquals(1L, CollectionPermanentArchiveFileDao.count()) }
    }

    @Test fun `ordinary chunk completion persists file and clears only its own chunks`() = fixture { r ->
        val config = mock(FileStorageConfigReader::class.java)
        org.mockito.Mockito.`when`(config.settings()).thenReturn(FileStorageSettings(EnumFileStorageType.LOCAL, null, null, null, "unused", "unused", null, false))
        val objects = FileObjectStorageService(r.c.getBean(LocalFileObjectStorage::class.java), config, mock(RustFsStorageClient::class.java), r.access)
        val storage = FileSystemStorageService(dir.toString(), r.c.getBean(FileService::class.java), FileFolderService(), r.c, objects, r.access)
        val bytes = "new original evidence".toByteArray()
        storage.upload(java.io.ByteArrayInputStream(bytes), "evidence.txt", "unused", uuid = "new-session", startOffset = 0)
        storage.upload(java.io.ByteArrayInputStream("other".toByteArray()), "other.txt", "unused", uuid = "other-session", startOffset = 0)
        val chunk = storage.buildTempPath("new-session", 0)
        val result = requireNotNull(storage.mergeChunks("new-session", "evidence.txt", bytes.size.toLong()))
        Assertions.assertNotNull(result.id)
        assertFalse(java.nio.file.Files.exists(chunk))
        assertEquals("other", java.nio.file.Files.readString(storage.buildTempPath("other-session", 0)))
        objects.openStream(requireNotNull(result.path), result.storageType).use { assertArrayEquals(bytes, it.readAllBytes()) }
        r.db { Assertions.assertNotNull(FileDao.findById(requireNotNull(result.id))) }
    }

    @Test fun `file locations batch queries preserve ID precedence legacy ambiguity and current changes`() = fixture { r ->
        r.db {
            listOf("legacy.txt" to "id-first.txt", "path-match" to "legacy.txt", "unique" to "unique.txt",
                "ambiguous-a" to "ambiguous.txt", "ambiguous-b" to "ambiguous.txt").forEach { (id, path) ->
                FileDao.new(id) { name=id; this.path=path; hash=id; ext="txt"; size=1 }
            }
        }
        r.db {
            val result = CollectionOriginalFileLocations.find(listOf("legacy.txt", "unique.txt", "ambiguous.txt", "missing.txt"))
            assertEquals("id-first.txt", result["legacy.txt"]?.path)
            assertEquals("unique.txt", result["unique.txt"]?.path)
            assertFalse(result.containsKey("ambiguous.txt"))
            assertFalse(result.containsKey("missing.txt"))
            FileDao.findById("unique")!!.path="moved.txt"
            FileDao.findById("unique")!!.storageType=EnumFileStorageType.RUSTFS
            assertEquals(CollectionOriginalFileLocations.Location("moved.txt", "RUSTFS"),
                CollectionOriginalFileLocations.find(listOf("unique"))["unique"])
        }
        r.db { assertEquals("moved.txt", CollectionOriginalFileLocations.find(listOf("unique"))["unique"]?.path) }
    }

    @Test fun `hundreds of unrelated original file locations do not query each file`() = fixture { r ->
        val ids=(0 until 450).map { "bulk-file-$it" }
        r.db { ids.forEach { id -> FileDao.new(id) { name=id; path="$id.txt"; hash=id; ext="txt"; size=1 } } }
        r.db {
            var selects=0
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(object : SqlLogger {
                override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext, transaction:Transaction) {
                    if(context.statement.type==org.jetbrains.exposed.v1.core.statements.StatementType.SELECT) selects++
                }
            })
            val found=CollectionOriginalFileLocations.find(ids + ids + ids)
            assertEquals(ids.toSet(), found.keys)
            assertTrue(selects <= 3, "450 distinct file IDs required $selects SELECTs")
        }
    }

    @Test fun `movement original parent checks batch queries and keep unresolved ownership`() = fixture { r ->
        val count=240
        r.db {
            for(i in 0 until count) {
                CollectionTransferDao.new("bulk-transfer-$i") { orgId="museum"; toOrgId="other"; collectionId="one" }
                StorageMovementDao.new("bulk-movement-$i") {
                    orgId=if(i==count-1)"unrelated" else "museum"; collectionId="one"
                    type=EnumMoveType.OUT; reason=EnumMoveReason.TRANSFER; refBizId="bulk-transfer-$i"; attachmentIds="proof"
                }
            }
        }
        r.db {
            var selects=0
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(object : SqlLogger {
                override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext, transaction:Transaction) {
                    if(context.statement.type==org.jetbrains.exposed.v1.core.statements.StatementType.SELECT) selects++
                }
            })
            val refs=CollectionBusinessOriginalFileSource().references(CollectionBusinessOriginalFileSource.MOVEMENT, null)
            assertEquals(count,refs.size)
            assertEquals(count-1,refs.count { it.businessType==CollectionConst.BIZ_TRANSFER })
            assertEquals("COLLECTION_UNRESOLVED_MOVEMENT", refs.single {it.businessId=="bulk-movement-${count-1}"}.businessType)
            assertTrue(selects <= 7, "240 movement source checks required $selects SELECTs")
        }
    }

    @Test fun `preview rechecks a foreign source added after materialization and emits no old bytes`() = fixture(CollectionScopeResolver("multi","")) { r ->
        loan(r)
        r.allow()
        val original=r.c.getBean(FileObjectStorageService::class.java)
        var added=false
        val lateSource=object : FileObjectStorage by original {
            override fun <T> withTempFile(path:String,storageType:String?,suffix:String?,block:(java.io.File)->T):T =
                original.withTempFile(path,storageType,suffix) { file ->
                    r.db { LoanDao.new("late-foreign") {orgId="other";attachmentIds="proof"} }
                    added=true
                    block(file)
                }
        }
        val files=r.c.getBean(FileService::class.java)
        val preview=FilePreviewController(files,FileConvertService(files,emptyList()),lateSource,r.access)
        val response=MockHttpServletResponse()
        preview.previewDocument("proof",null,response)
        assertTrue(added,"The late foreign source must be committed before the output recheck")
        assertEquals(400,response.status)
        assertEquals(0,response.contentAsByteArray.size)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        r.deny()
        r.permission(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        r.allow()
    }

    @Test fun `an unrelated public file remains readable anonymously`() = fixture { r ->
        loan(r);SecurityContextHolder.clearContext()
        val response=MockHttpServletResponse()
        r.controller.downloadFile(MockHttpServletRequest("GET","/userfiles/public.txt"),response)
        assertEquals("CONTENT:public.txt",response.contentAsString)
    }
}
