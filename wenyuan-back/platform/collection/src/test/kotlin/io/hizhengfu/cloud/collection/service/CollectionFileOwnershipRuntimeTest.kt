package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.sys.system.dao.*
import org.springframework.test.util.ReflectionTestUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.FileReadGuard
import java.time.LocalDateTime
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionFileOwnershipRuntimeTest {
    @Test fun `media files cannot be attached twice while editing their existing entry remains valid`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val id = r.service.saveMedia("one",CollectionMediaVo(fileId="new",title="正面"))
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.service.saveMedia("one",CollectionMediaVo(fileId="new",title="重复")) }
        assertEquals(before,r.snapshot())
        assertEquals(id,r.service.saveMedia("one",CollectionMediaVo(id=id,fileId="new",title="更新说明")))
        r.db { assertEquals(1L,CollectionMediaDao.count()) }
    }

    @Test fun `media folders reject indirect cycles and long names but allow reparenting to root`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val a=r.service.saveMediaFolder("one",CollectionMediaFolderVo(name="A"))
        val b=r.service.saveMediaFolder("one",CollectionMediaFolderVo(name="B",parentId=a))
        val c=r.service.saveMediaFolder("one",CollectionMediaFolderVo(name="C",parentId=b))
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java) { r.service.saveMediaFolder("one",CollectionMediaFolderVo(id=a,name="A",parentId=c)) }
        assertThrows(BadRequestException::class.java) { r.service.saveMediaFolder("one",CollectionMediaFolderVo(name="长".repeat(101))) }
        assertEquals(before,r.snapshot())
        r.service.saveMediaFolder("one",CollectionMediaFolderVo(id=b,name="B"))
        r.service.saveMediaFolder("one",CollectionMediaFolderVo(id=a,name="A",parentId=c))
        r.db { CollectionMediaFolderDao[b].parentId=a }
        r.service.saveMediaFolder("one",CollectionMediaFolderVo(id=b,name="B"))
        assertEquals(a,r.service.deleteMediaFolder("one",a))
    }
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    private class Runtime(val service: CollectionFileService, val files: FileService, val tx: TransactionTemplate, val scope: CollectionScopeResolver) {
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun grant(vararg permissions: String) = db { RolePermissions.deleteAll(); actorRole("officer", "CURRENT_FILES", permissions.toSet()) }
        fun damage(kind: String) = db { when(kind) {
            "moved" -> UserDao["officer"].groupId="other"
            "configured" -> ReflectionTestUtils.setField(scope,"defaultOrgId","other")
            "disabled" -> UserDao["officer"].enabled=false
            "role" -> RoleDao.find { Roles.code eq "FILE_officer" }.single().enabled=false
            "group" -> GroupDao["museum"].enabled=false
            "museum" -> CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        } }
        fun snapshot() = db { listOf(Collections,CollectionMedias,CollectionAttachments,CollectionMediaFolders,FileContents,CollectionWordImports).map { t ->
            t.selectAll().map { row -> t.columns.map {row[it]} }
        } }
        fun entry(name: String): Any? = when(name) {
            "media-read" -> service.listMedia("one")
            "attachment-read" -> service.listAttachments("one")
            "folder-read" -> service.listMediaFolders("one")
            "stat" -> service.mediaStat()
            "media" -> save("media",null)
            "attachment" -> save("attachment",null)
            "folder" -> save("folder",null)
            "media-delete" -> service.deleteMedia("one","entry")
            "attachment-delete" -> service.deleteAttachment("one","entry")
            "folder-delete" -> service.deleteMediaFolder("one","entry")
            "validate" -> service.validateBatchFiles(listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1")))
            "commit" -> service.bindBatchFiles(listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1")))
            "empty-validate" -> service.validateBatchFiles(emptyList())
            "empty-commit" -> service.bindBatchFiles(emptyList())
            else -> error(name)
        }
        fun word(type: EnumCollectionWordDocumentType) = db { CollectionWordImportDao.new("word") {
            orgId="museum";collectionId="one";documentType=type;sourceFileId="new";sourceFileName="original.docx";sourceHash="hash";fingerprint="format"
        } }
        fun save(kind: String, id: String?) = when (kind) {
            "media" -> service.saveMedia("one", CollectionMediaVo(id = id, fileId = "new", isCover = true))
            "attachment" -> service.saveAttachment("one", CollectionAttachmentVo(id = id, fileId = "new"))
            else -> service.saveMediaFolder("one", CollectionMediaFolderVo(id = id, name = "更新目录"))
        }
        fun seed(kind: String, org: String = "other", collection: String = "one") = db {
            when (kind) {
                "media" -> CollectionMediaDao.new("entry") { orgId = org; collectionId = collection; fileId = "old" }
                "attachment" -> CollectionAttachmentDao.new("entry") { orgId = org; collectionId = collection; fileId = "old" }
                else -> CollectionMediaFolderDao.new("entry") { orgId = org; collectionId = collection; name = "原始目录" }
            }
        }
        fun noBinding() {
            assertFalse(mockingDetails(files).invocations.any { it.method.name == "insertFileContent" })
            db { assertEquals(0L, FileContents.selectAll().count()) }
        }
    }

    private fun fixture(safetyGuard: CollectionOperationSafetyGuard? = null, mode: String = "single", realFileService: Boolean = false, body: (Runtime) -> Unit) {
        val user = SecurityUser(id = "officer", name = "经办", username = "officer", password = "", groupId = "museum",
            groupIds = setOf("museum"), postIds = emptySet(), dataType = 0, roles = setOf("ADMIN"),
            authorities = listOf(SimpleGrantedAuthority("ADMIN")), enabled = true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("ownership.db")}" }, DatabaseConfig {})
        try {
            AnnotationConfigApplicationContext().use { context ->
                val scope = CollectionScopeResolver(mode, "museum")
                val files = mock(FileService::class.java)
                `when`(files.fileObjectStorage).thenReturn(LocalFileObjectStorage(dir.toString()))
                `when`(files.resolveFileId(anyString())).thenAnswer { it.getArgument<String>(0) }
                doAnswer { call ->
                    val file = call.getArgument<String>(0)
                    val owner = call.getArgument<String>(1)
                    if (FileContents.selectAll().where { (FileContents.fileId eq file) and (FileContents.contentId eq owner) }.empty()) {
                        FileContents.insert { it[fileId] = file; it[contentId] = owner }
                    }
                    null
                }.`when`(files).insertFileContent(anyString(), anyString(), nullable(String::class.java))
                context.beanFactory.registerSingleton("transactionManager", manager)
                context.beanFactory.registerSingleton("scope", scope)
                if (realFileService) {
                    context.registerBean("files", FileService::class.java, java.util.function.Supplier {
                        FileService(dir.toString(), mock(io.hizhengfu.cloud.sys.file.service.FileFolderService::class.java),
                            fileReadAccess = FileReadAccess(context.getBeanProvider(FileReadGuard::class.java)))
                    })
                } else context.beanFactory.registerSingleton("files", files)
                context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
                safetyGuard?.let { context.beanFactory.registerSingleton("safetyGuard", it) }
                context.register(Transactions::class.java, CollectionFileService::class.java, CollectionOperationLockService::class.java)
                context.refresh(); SpringContextHolder().setApplicationContext(context)
                val runtime = Runtime(context.getBean(CollectionFileService::class.java), context.getBean(FileService::class.java), TransactionTemplate(manager), scope)
                runtime.db {
                    originalReaderSchema()
                    originalReader("officer", CollectionConst.LEDGER_CREATE)
                    actorRole("officer", "READ_FILES", setOf(CollectionConst.SELECT))
                    SchemaUtils.create(CollectionOrgs)
                    for (org in listOf("museum", "other")) CollectionOrgDao.new(org) { name=org; code=org; systemGroupId=org }
                    SchemaUtils.create(CollectionCatalogs, Files, FileUsers, CollectionWordImports, Collections, CollectionMedias, CollectionAttachments, CollectionMediaFolders, FileContents, CollectionOperationLocks)
                    for (id in listOf("old", "new")) {
                        FileDao.new(id) {name="$id.txt";path="$id.txt";hash=id;ext="txt";size=3}
                        FileUsers.insert {it[fileId]=id;it[userId]="officer";it[uploadDate]=LocalDateTime.now()}
                        java.nio.file.Files.writeString(dir.resolve("$id.txt"), id)
                    }
                    CollectionDao.new("one") { orgId = "museum"; name = "本馆藏品"; totalRegNo = "A1"; coverImage = "old" }
                    CollectionDao.new("two") { orgId = "other"; name = "他馆藏品"; totalRegNo = "B1" }
                }
                body(runtime)
            }
        } finally { SecurityContextHolder.clearContext() }
    }

    @Test fun `missing batch file returns row error through real transactional file service`() = fixture(realFileService = true) { r ->
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(r.files))
        val before = r.snapshot()
        val items = listOf(CollectionFileImportItemVo(rowNo = 7, totalRegNo = "A1", fileId = "missing-file"))
        val result = r.service.validateBatchFiles(items)
        assertFalse(result.valid)
        assertEquals(7, result.errors.single().rowNo)
        assertEquals("fileId", result.errors.single().field)
        assertTrue(result.errors.single().message.contains("文件不存在"))
        assertEquals(before, r.snapshot())
        val commit = r.service.bindBatchFiles(items, "museum", "unused-invalid-batch")
        assertFalse(commit.valid)
        assertEquals(result.errors, commit.errors)
        assertEquals(before, r.snapshot())
        assertThrows(BadRequestException::class.java) {
            r.db {
                CollectionAttachmentDao.new("must-rollback") {
                    orgId = "museum"; collectionId = "one"; fileId = "new"
                }
                r.files.resolveFileId("missing-file")
            }
        }
        assertEquals(before, r.snapshot(), "Uncaught failure must still roll back the outer write transaction")
    }

    @ParameterizedTest @ValueSource(strings = ["media", "attachment", "folder"])
    fun `wrong museum ownership cannot be rewritten by editing`(kind: String) = fixture { r ->
        r.seed(kind)
        assertThrows(BadRequestException::class.java) { r.save(kind, "entry") }
        r.noBinding()
        r.db {
            assertEquals("old", CollectionDao["one"].coverImage)
            when (kind) {
                "media" -> { assertEquals("other", CollectionMediaDao["entry"].orgId); assertEquals("old", CollectionMediaDao["entry"].fileId) }
                "attachment" -> { assertEquals("other", CollectionAttachmentDao["entry"].orgId); assertEquals("old", CollectionAttachmentDao["entry"].fileId) }
                else -> { assertEquals("other", CollectionMediaFolderDao["entry"].orgId); assertEquals("原始目录", CollectionMediaFolderDao["entry"].name) }
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = ["media", "attachment", "folder"])
    fun `missing edit ID cannot create a replacement record`(kind: String) = fixture { r ->
        assertThrows(BadRequestException::class.java) { r.save(kind, "missing") }
        r.noBinding()
        r.db { assertEquals(0L, CollectionMediaDao.count() + CollectionAttachmentDao.count() + CollectionMediaFolderDao.count()) }
    }

    @ParameterizedTest @ValueSource(strings = ["media", "attachment", "folder"])
    fun `foreign collection edit rejected before attachment binding`(kind: String) = fixture { r ->
        r.seed(kind, collection = "two")
        assertThrows(BadRequestException::class.java) { r.save(kind, "entry") }
        r.noBinding()
    }

    @ParameterizedTest @ValueSource(strings = ["media", "attachment", "folder"])
    fun `inconsistent child ownership is not listed as ordinary collection material`(kind: String) = fixture { r ->
        r.seed(kind)
        assertThrows(BadRequestException::class.java) { r.service.listMedia("one") }
        assertThrows(BadRequestException::class.java) { r.service.listAttachments("one") }
        assertThrows(BadRequestException::class.java) { r.service.listMediaFolders("one") }
        r.noBinding()
    }

    @Test fun `batch unchanged detection cannot hide wrong ownership`() = fixture { r ->
        r.seed("media")
        val item = CollectionFileImportItemVo(fileId = "old", totalRegNo = "A1", kind = EnumCollectionFileImportKind.MEDIA)
        val result = r.service.bindBatchFiles(listOf(item))
        assertFalse(result.valid)
        assertTrue(result.unchangedIds.isEmpty())
        assertTrue(result.createdIds.isEmpty())
        assertEquals(1, result.errors.size)
        r.noBinding()
    }

    @Test fun `foreign or missing folders rejected before binding file`() = fixture { r ->
        r.seed("folder", collection = "two")
        for (folder in listOf("entry", "missing")) {
            assertThrows(BadRequestException::class.java) { r.service.saveMedia("one", CollectionMediaVo(fileId = "new", folderId = folder)) }
        }
        r.noBinding()
    }

    @Test fun `media statistics do not trust a child museum that contradicts its parent`() = fixture { r ->
        r.seed("media", org = "museum", collection = "two")
        assertThrows(BadRequestException::class.java) { r.service.mediaStat() }
    }

    @Test fun `photo metadata persists with unique views and replacing a draft front clears confirmation`()=fixture { r ->
        r.db {
            CollectionDao["one"].registrationDetails=io.hizhengfu.cloud.collection.domain.CollectionRegistrationDetailsVo(coverIsFrontView=true)
            CollectionCatalogDao.new("catalog") { orgId="museum";collectionId="one";content=CollectionCatalogMapper.fromCollection(CollectionDao["one"].toModel());catalogStatus=io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.DRAFT }
        }
        val capture=io.hizhengfu.cloud.collection.domain.CollectionMediaCaptureVo(view=io.hizhengfu.cloud.collection.domain.EnumCollectionPhotoView.A,sequence=1,institution="本馆",location="摄影室",photographer="摄影人",photographedOn=java.time.LocalDate.of(2026,1,1))
        val input=CollectionMediaVo(fileId="new",isCover=true,mediaType=io.hizhengfu.cloud.collection.domain.EnumCollectionMediaType.FRONT,capture=capture)
        val id=r.service.saveMedia("one",input)
        r.db {
            assertEquals(capture,CollectionMediaDao[id].capture)
            assertEquals("new",CollectionCatalogDao["catalog"].content.coverImage)
            assertNull(CollectionCatalogDao["catalog"].content.registrationDetails?.coverIsFrontView)
            assertNull(CollectionDao["one"].registrationDetails?.coverIsFrontView)
        }
        assertThrows(BadRequestException::class.java) {r.service.saveMedia("one",input.copy(fileId="old",isCover=false))}
        r.db {CollectionCatalogDao["catalog"].catalogStatus=io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.SUBMITTED}
        assertThrows(BadRequestException::class.java) {r.service.deleteMedia("one",id)}
        r.db {CollectionCatalogDao["catalog"].catalogStatus=io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.DRAFT;CollectionDao["one"].registeredAt=java.time.LocalDateTime.of(2026,1,2,10,0)}
        assertThrows(BadRequestException::class.java) {r.service.deleteMedia("one",id)}
        assertEquals(id,r.service.listMedia("one").single().id)
    }

    @Test fun `same museum create edit and delete keep normal behavior`() = fixture { r ->
        val folder = r.save("folder", null)
        r.save("folder", folder)
        val media = r.save("media", null)
        val attachment = r.save("attachment", null)
        r.save("media", media); r.save("attachment", attachment)
        assertEquals(media, r.service.listMedia("one").single().id)
        assertEquals(attachment, r.service.listAttachments("one").single().id)
        r.service.deleteMedia("one", media)
        r.service.deleteAttachment("one", attachment)
        r.service.deleteMediaFolder("one", folder)
        r.db { assertEquals(0L, CollectionMediaDao.count() + CollectionAttachmentDao.count() + CollectionMediaFolderDao.count()); assertNull(CollectionDao["one"].coverImage) }
    }

    @ParameterizedTest @ValueSource(strings = ["new-media", "edit-media", "delete-media", "new-attachment", "edit-attachment", "delete-attachment", "new-folder", "edit-folder", "delete-folder", "batch"])
    fun `active approval blocks every collection file mutation before binding`(operation: String) = fixture { r ->
        r.seed("media", "museum"); r.seed("attachment", "museum"); r.seed("folder", "museum")
        r.db { CollectionOperationLockDao.new("lock") { orgId = "museum"; collectionId = "one"; bizType = "COLLECTION_TRANSFER"; bizId = "transfer" } }
        assertThrows(BadRequestException::class.java) {
            when (operation) {
                "new-media" -> r.save("media", null)
                "edit-media" -> r.save("media", "entry")
                "delete-media" -> r.service.deleteMedia("one", "entry")
                "new-attachment" -> r.save("attachment", null)
                "edit-attachment" -> r.save("attachment", "entry")
                "delete-attachment" -> r.service.deleteAttachment("one", "entry")
                "new-folder" -> r.save("folder", null)
                "edit-folder" -> r.save("folder", "entry")
                "delete-folder" -> r.service.deleteMediaFolder("one", "entry")
                else -> r.service.bindBatchFiles(listOf(CollectionFileImportItemVo(fileId = "new", totalRegNo = "A1")))
            }
        }
        r.noBinding()
        r.db {
            assertEquals("old", CollectionDao["one"].coverImage)
            assertEquals("old", CollectionMediaDao["entry"].fileId)
            assertEquals("old", CollectionAttachmentDao["entry"].fileId)
            assertEquals("原始目录", CollectionMediaFolderDao["entry"].name)
            assertEquals(1L, CollectionOperationLockDao.count())
        }
    }

    @Test fun `read and preview remain available during approval but execution is refused`() = fixture { r ->
        r.seed("media", "museum"); r.seed("attachment", "museum"); r.seed("folder", "museum")
        r.db { CollectionOperationLockDao.new("lock") { orgId = "museum"; collectionId = "one"; bizType = "COLLECTION_TRANSFER"; bizId = "transfer" } }
        assertEquals(1, r.service.listMedia("one").size)
        assertEquals(1, r.service.listAttachments("one").size)
        assertEquals(1, r.service.listMediaFolders("one").size)
        val items = listOf(CollectionFileImportItemVo(fileId = "old", totalRegNo = "A1"))
        assertTrue(r.service.validateBatchFiles(items).valid)
        assertThrows(BadRequestException::class.java) { r.service.bindBatchFiles(items) }
        r.noBinding()
    }

    @Test fun `later locked item prevents partial batch writes and unlocking permits retry`() = fixture { r ->
        r.db {
            CollectionDao["two"].orgId = "museum"
            CollectionOperationLockDao.new("lock") { orgId = "museum"; collectionId = "two"; bizType = "COLLECTION_TRANSFER"; bizId = "transfer" }
        }
        val items = listOf(CollectionFileImportItemVo(fileId = "new", totalRegNo = "A1", isCover = true), CollectionFileImportItemVo(fileId = "new", totalRegNo = "B1"))
        assertThrows(BadRequestException::class.java) { r.service.bindBatchFiles(items) }
        r.noBinding()
        r.db { assertEquals(0L, CollectionMediaDao.count()); CollectionOperationLockDao["lock"].delete() }
        val result = r.service.bindBatchFiles(items)
        assertTrue(result.valid)
        assertEquals(2, result.createdIds.size)
        r.db { assertEquals(2L, CollectionMediaDao.count()); assertEquals("new", CollectionDao["one"].coverImage); assertEquals("new", CollectionDao["two"].coverImage) }
    }

    @Test fun `batch locks in stable order and invokes current safety guards before writes`() {
        val checked = mutableListOf<String>()
        val guard = object : CollectionOperationSafetyGuard {
            override fun requireAllowed(collectionId: String, bizType: String?, bizId: String?) {
                checked += collectionId
                if (collectionId == "two") throw BadRequestException("事故处置未完成")
            }
        }
        fixture(guard) { r ->
            r.db { CollectionDao["two"].orgId = "museum" }
            val items = listOf(CollectionFileImportItemVo(fileId = "new", totalRegNo = "B1"), CollectionFileImportItemVo(fileId = "new", totalRegNo = "A1"))
            assertThrows(BadRequestException::class.java) { r.service.bindBatchFiles(items) }
            assertEquals(listOf("one", "two"), checked)
            r.noBinding()
        }
    }

    @Test fun `batch does not reuse stale museum from its initial DAO match`() = fixture { r ->
        `when`(r.files.resolveFileId("new")).thenAnswer {
            // Controlled database change after initial matching; this is not a multi-host lock test.
            Collections.update({ Collections.id eq "one" }) { it[orgId] = "other" }
            "new"
        }
        assertThrows(BadRequestException::class.java) { r.service.bindBatchFiles(listOf(CollectionFileImportItemVo(fileId = "new", totalRegNo = "A1"))) }
        r.noBinding()
        r.db { assertEquals("museum", CollectionDao["one"].orgId); assertEquals(0L, CollectionMediaDao.count()) }
    }

    @Test fun `batch recomputes existing file matches after acquiring locks`() {
        var checked = false
        val guard = object : CollectionOperationSafetyGuard {
            override fun requireAllowed(collectionId: String, bizType: String?, bizId: String?) {
                if (!checked) {
                    checked = true
                    CollectionMediaDao.findById("entry")?.delete()
                }
            }
        }
        fixture(guard) { r ->
            r.seed("media", "museum")
            val result = r.service.bindBatchFiles(listOf(CollectionFileImportItemVo(fileId = "old", totalRegNo = "A1")))
            assertTrue(result.valid)
            assertTrue(result.unchangedIds.isEmpty())
            assertEquals(1, result.createdIds.size)
            r.db { assertEquals(1L, CollectionMediaDao.count()); assertNull(CollectionMediaDao.findById("entry")) }
        }
    }
    @ParameterizedTest @ValueSource(strings=["media-read","attachment-read","folder-read","stat","media","attachment","folder","media-delete","attachment-delete","folder-delete","validate","commit","empty-validate","empty-commit"])
    fun `every file entry checks current revoked identity even for empty input`(entry:String)=fixture {r ->
        r.grant();val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry(entry)};assertEquals(before,r.snapshot());verifyNoInteractions(r.files)
        r.grant(CollectionConst.SELECT,CollectionConst.LEDGER_CREATE);r.damage("disabled")
        assertThrows(BadRequestException::class.java){r.entry(entry)};assertEquals(before,r.snapshot());verifyNoInteractions(r.files)
    }
    @ParameterizedTest @ValueSource(strings=["moved","configured","role","group","museum"])
    fun `changed current membership and enabled state reject file writes and batch planning`(damage:String)=fixture {r ->
        r.seed("media","museum");r.seed("attachment","museum");r.seed("folder","museum");r.damage(damage)
        val before=r.snapshot()
        for(entry in listOf("media","attachment","folder","media-delete","attachment-delete","folder-delete","validate","commit")) {
            assertThrows(BadRequestException::class.java){r.entry(entry)};assertEquals(before,r.snapshot())
        }
        verifyNoInteractions(r.files)
    }
    @Test fun `current reassignment selects the new museum and cross viewing does not grant writes`()=fixture(mode="multi") {r ->
        r.damage("moved")
        val result=r.service.bindBatchFiles(listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="B1")))
        assertTrue(result.valid);assertEquals("other",r.db {CollectionMediaDao[result.createdIds.single()].orgId})
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG)
        assertEquals(1,r.service.mediaStat().total);assertEquals(1,r.service.listMedia("two").size)
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry("media")};assertThrows(BadRequestException::class.java){r.entry("commit")};assertEquals(before,r.snapshot())
    }
    @Test fun `single museum statistics do not count foreign material with old admin login`()=fixture {r ->
        r.seed("media","other","two");assertEquals(0,r.service.mediaStat().total)
        assertThrows(BadRequestException::class.java){r.service.listMedia("two")}
        r.grant(CollectionConst.STAT);assertEquals(0,r.service.mediaStat().total)
        assertThrows(BadRequestException::class.java){r.service.listMedia("one")}
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment"])
    fun `revocation while binding a file rolls back child cover and binding`(kind:String)=fixture {r ->
        doAnswer { call ->
            FileContents.insert {it[fileId]=call.getArgument<String>(0);it[contentId]=call.getArgument<String>(1)}
            r.grant();null
        }.`when`(r.files).insertFileContent(anyString(),anyString(),nullable(String::class.java))
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.save(kind,null)};assertEquals(before,r.snapshot())
    }
    @Test fun `batch planning rechecks current museum after file resolution`()=fixture(mode="multi") {r ->
        `when`(r.files.resolveFileId("new")).thenAnswer {r.damage("moved");"new"}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry("validate")};assertEquals(before,r.snapshot());r.noBinding()
    }
    @ParameterizedTest @ValueSource(strings=["REGISTRATION","DYNAMIC_TRACKING"])
    fun `Word original follows document duty without granting generic attachment edits`(name:String)=fixture {r ->
        val type=EnumCollectionWordDocumentType.valueOf(name);r.word(type)
        r.grant(if(type==EnumCollectionWordDocumentType.REGISTRATION)CollectionConst.LEDGER_CORRECT else CollectionConst.STORAGE)
        val id=r.service.saveWordOriginal("word","museum")
        r.db {val file=CollectionAttachmentDao[id];assertEquals("one",file.collectionId);assertEquals("new",file.fileId);assertEquals("word",file.bizId);assertEquals("museum",file.orgId)}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.save("attachment",null)};assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["target","org","source","duty","separation","revoked"])
    fun `Word original rejects mismatched evidence and current incompatible duties`(damage:String)=fixture {r ->
        r.word(EnumCollectionWordDocumentType.DYNAMIC_TRACKING);r.grant(CollectionConst.STORAGE)
        r.db { when(damage) {
            "target" -> CollectionWordImportDao["word"].collectionId="two"
            "org" -> CollectionWordImportDao["word"].orgId="other"
            "source" -> CollectionWordImportDao["word"].sourceFileId=null
            "duty" -> r.grant(CollectionConst.LEDGER_CREATE)
            "separation" -> actorRole("officer","INCOMPATIBLE",setOf(CollectionConst.LEDGER_CREATE))
            "revoked" -> r.grant()
        } }
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.saveWordOriginal("word","museum")};assertEquals(before,r.snapshot());r.noBinding()
    }

    @ParameterizedTest @ValueSource(strings=["validate","commit"])
    fun `legacy duplicate registration numbers cannot select an arbitrary file target`(entry:String)=fixture {r ->
        r.db {
            val index=Collections.indices.single {it.columns==listOf(Collections.orgId,Collections.totalRegNo)}.indexName
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP INDEX \""+index.replace("\"","\"\"")+"\"")
            CollectionDao.new("duplicate"){orgId="museum";totalRegNo="A1";name="另一件原藏品"}
        }
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry(entry)};assertEquals(before,r.snapshot());verifyNoInteractions(r.files)
    }

    @ParameterizedTest @ValueSource(strings=["historical-only","coexisting-clean","stale-preview"])
    fun `explicit historical number binds files only to the exact original`(operation:String)=fixture {r ->
        val number=" A1 "
        r.db {
            CollectionDao["one"].totalRegNo=number
            if(operation!="historical-only") CollectionDao.new("clean-item") {
                orgId="museum";totalRegNo="A1";name="另一件相近号码藏品"
            }
        }
        val items=listOf(CollectionFileImportItemVo(fileId="new",totalRegNo=number))
        val preview=r.service.validateBatchFiles(items)
        assertTrue(preview.valid,preview.errors.toString())
        assertEquals("one",preview.matches.single().collectionId)
        assertEquals(number,preview.matches.single().totalRegNo)
        val c=preview.confirmation!!
        if(operation=="stale-preview") {
            r.db {CollectionDao["one"].totalRegNo="RENAMED-BY-EXTERNAL-HISTORY"}
            val before=r.snapshot()
            assertThrows(BadRequestException::class.java){r.service.bindBatchFiles(items,c.orgId,c.validationHash)}
            assertEquals(before,r.snapshot());r.noBinding()
        } else {
            val result=r.service.bindBatchFiles(items,c.orgId,c.validationHash)
            assertTrue(result.valid);assertEquals("one",result.matches.single().collectionId)
            assertEquals(number,result.matches.single().totalRegNo)
            r.db {
                assertEquals("one",CollectionMediaDao[result.createdIds.single()].collectionId)
                assertEquals(number,CollectionDao["one"].totalRegNo)
                assertEquals("B1",CollectionDao["two"].totalRegNo)
                assertTrue(CollectionMediaDao.all().none {it.collectionId!="one"})
                if(operation=="coexisting-clean")assertEquals("A1",CollectionDao["clean-item"].totalRegNo)
            }
            assertEquals("new",java.nio.file.Files.readString(dir.resolve("new.txt")))
        }
    }

    @Test fun `confirmed batch returns exact file and target receipts and old confirmation cannot be replayed`()=fixture {r ->
        val items=listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1"))
        val preview=r.service.validateBatchFiles(items);val match=preview.matches.single()
        assertEquals("one",match.collectionId);assertEquals("new",match.fileId);assertEquals("A1",match.totalRegNo)
        assertEquals(3,match.size);assertEquals("new.txt",match.fileName);assertTrue(match.contentSha256.matches(Regex("[0-9a-f]{64}")))
        val c=preview.confirmation!!;val result=r.service.bindBatchFiles(items,c.orgId,c.validationHash)
        assertEquals(c,result.confirmation);assertEquals(result.createdIds.single(),result.matches.single().bindingId)
        assertEquals(match,result.matches.single().copy(bindingId=null))
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.bindBatchFiles(items,c.orgId,c.validationHash)};assertEquals(before,r.snapshot())
        val again=r.service.validateBatchFiles(items);assertEquals(result.createdIds,again.unchangedIds)
        val repeat=r.service.bindBatchFiles(items,again.confirmation!!.orgId,again.confirmation.validationHash)
        assertTrue(repeat.createdIds.isEmpty());assertEquals(result.createdIds,repeat.unchangedIds)
    }
    @ParameterizedTest @ValueSource(strings=["bytes","metadata","path","collection-name","museum-name","cover","target","existing"])
    fun `changed content or matched identity invalidates a prior file confirmation`(damage:String)=fixture {r ->
        val items=listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1"));val c=r.service.validateBatchFiles(items).confirmation!!
        r.db {when(damage) {
            "bytes" -> java.nio.file.Files.writeString(dir.resolve("new.txt"),"NEW")
            "metadata" -> FileDao["new"].hash="other-hash"
            "path" -> FileDao["new"].path="old.txt"
            "collection-name" -> CollectionDao["one"].name="重新定名"
            "museum-name" -> CollectionOrgDao["museum"].name="新馆名"
            "cover" -> CollectionDao["one"].coverImage=null
            "target" -> {CollectionDao["one"].delete();CollectionDao.new("replacement"){orgId="museum";totalRegNo="A1";name="另一件藏品"}}
            "existing" -> CollectionMediaDao.new("later"){orgId="museum";collectionId="one";fileId="new"}
        }}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.bindBatchFiles(items,c.orgId,c.validationHash)};assertEquals(before,r.snapshot());r.noBinding()
    }
    @ParameterizedTest @ValueSource(strings=["file","kind","title","cover","sort","remark","row"])
    fun `changed submitted file input cannot reuse the confirmed batch`(damage:String)=fixture {r ->
        val item=CollectionFileImportItemVo(fileId="new",totalRegNo="A1");val c=r.service.validateBatchFiles(listOf(item)).confirmation!!
        val changed=when(damage) {
            "file"->item.copy(fileId="old");"kind"->item.copy(kind=EnumCollectionFileImportKind.ATTACHMENT);"title"->item.copy(title="另一个说明")
            "cover"->item.copy(isCover=true);"sort"->item.copy(sort=9);"remark"->item.copy(remark="另一依据");else->item.copy(rowNo=8)
        }
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.bindBatchFiles(listOf(changed),c.orgId,c.validationHash)};assertEquals(before,r.snapshot());r.noBinding()
    }
    @Test fun `file content changed during actual binding rolls back all database associations`()=fixture {r ->
        val items=listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1"));val c=r.service.validateBatchFiles(items).confirmation!!
        doAnswer {call ->
            FileContents.insert {it[fileId]=call.getArgument<String>(0);it[contentId]=call.getArgument<String>(1)}
            java.nio.file.Files.writeString(dir.resolve("new.txt"),"NEW");null
        }.`when`(r.files).insertFileContent(anyString(),anyString(),nullable(String::class.java))
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.bindBatchFiles(items,c.orgId,c.validationHash)};assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["media","attachment","validate","commit"])
    fun `old login administrator cannot adopt another users unbound upload`(entry:String)=fixture {r ->
        r.db {FileUsers.deleteWhere {fileId eq "new"}}
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.entry(entry)};assertEquals(before,r.snapshot());r.noBinding()
    }
    @Test fun `current actual administrator can use an existing file without an upload relation`()=fixture {r ->
        r.db {
            Roles.insert {it[Roles.id]=io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value;it[code]="ADMIN";it[name]="管理员"}
            RoleUsers.insert {it[userId]="officer";it[roleId]=io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value}
            FileUsers.deleteWhere {fileId eq "new"}
        }
        val items=listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1"));val c=r.service.validateBatchFiles(items).confirmation!!
        assertEquals(1,r.service.bindBatchFiles(items,c.orgId,c.validationHash).createdIds.size)
    }
    @Test fun `confirmed batch museum cannot follow a later actual reassignment`()=fixture(mode="multi") {r ->
        val items=listOf(CollectionFileImportItemVo(fileId="new",totalRegNo="A1"));val c=r.service.validateBatchFiles(items).confirmation!!;r.damage("moved")
        val before=r.snapshot();assertThrows(BadRequestException::class.java){r.service.bindBatchFiles(items,c.orgId,c.validationHash)};assertEquals(before,r.snapshot())
    }

}
