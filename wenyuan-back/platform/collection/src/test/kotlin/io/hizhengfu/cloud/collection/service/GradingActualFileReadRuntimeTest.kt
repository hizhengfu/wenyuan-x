package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import io.hizhengfu.cloud.sys.file.service.storage.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.aop.support.AopUtils
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.HexFormat

/** Real isolated SQLite/Spring transactions and LOCAL bytes, without touching the native dev database. */
class GradingActualFileReadRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    private val at = LocalDateTime.of(2026, 8, 1, 10, 0)
    private val files = linkedMapOf(
        "APPLICATION" to "2106800000000000001",
        "PROVINCIAL_RESULT" to "2106800000000000002",
        "NATIONAL_FILING" to "2106800000000000003",
        "USED_catalog-approval" to "2106800000000000004",
    )
    private fun filePath(id: String) = "$id.txt"
    private fun content(id: String) = "DEV-SYNTHETIC-GRADING-ORIGINAL-20261004:$id".toByteArray()

    @ParameterizedTest
    @CsvSource(
        "APPLICATION,replaced", "APPLICATION,missing",
        "PROVINCIAL_RESULT,replaced", "PROVINCIAL_RESULT,missing",
        "NATIONAL_FILING,replaced", "NATIONAL_FILING,missing",
        "USED_catalog-approval,replaced", "USED_catalog-approval,missing",
    )
    fun `each grading stage actual file damage preserves detail but rejects downloads repeats and adoption without resealing`(phase: String, damage: String) = fixture { r ->
        r.complete()
        r.normalRepeatsPreserveProjection()
        val target = files.getValue(phase)
        val selected = r.archive(phase)
        r.readOriginal(selected, target, true)
        val bytes = content(target)
        val file = dir.resolve(filePath(target))
        assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(file))
        val beforeDamage = r.projection()
        if (damage == "replaced") {
            // Same path/inode/length/mtime and unchanged DB metadata: content verification must detect replacement.
            val modified = java.nio.file.Files.getLastModifiedTime(file)
            java.nio.file.Files.write(file, ByteArray(bytes.size) { 'X'.code.toByte() })
            java.nio.file.Files.setLastModifiedTime(file, modified)
            assertEquals(bytes.size.toLong(), java.nio.file.Files.size(file))
            assertFalse(bytes.contentEquals(java.nio.file.Files.readAllBytes(file)))
        } else {
            java.nio.file.Files.delete(file)
            assertFalse(java.nio.file.Files.exists(file))
        }
        assertEquals(beforeDamage, r.projection(), "Actual storage damage does not rewrite the recorded source")
        assertUnavailableOriginals(r.service.detail("one"))
        val errors = r.rejectBusinessEntrances(includeDetail = false)
        r.readOriginal(selected, target, false)
        assertEquals(beforeDamage, r.projection(), "No repair, replacement archive or normal completion may be persisted")
        println("GRADING_ACTUAL_FILE_RESULT " + JsonUtils.toJson(mapOf(
            "phase" to phase, "damage" to damage, "fileId" to target, "originalBytes" to bytes.size,
            "originalSha256" to digest(bytes), "realLocalFileMutation" to true,
            "normalFourStageReady" to listOf(false, false, true, true), "errors" to errors,
            "selectedReadProtectedBytesAfterDamage" to 0, "businessArchiveFileProjectionUnchanged" to true,
            "usedSourceMeaning" to "USED imports provincial filing evidence by the production contract; its selected file is distinct from the other three target files and legitimately shared with PROVINCIAL_RESULT.",
        )))
    }

    @ParameterizedTest @ValueSource(strings = ["empty", "malformed"])
    fun `damaged used snapshot also rejects repeats and adoption in actual storage runtime`(damage: String) = fixture { r ->
        r.complete()
        r.normalRepeatsPreserveProjection()
        files.forEach { (phase, file) -> r.readOriginal(r.archive(phase), file, true) }
        r.db {
            val row = CollectionPermanentArchiveDao[r.archive("USED_catalog-approval")]
            row.snapshot = if (damage == "empty") "" else "{broken"
            row.digest = CollectionCareEvidence.digest(row.snapshot)
        }
        val damaged = r.projection()
        // The retained485 named test already proves detail refusal for these exact two snapshot variants.
        val errors = r.rejectBusinessEntrances(includeDetail = false)
        assertEquals(damaged, r.projection(), "Rejected retries must not reseal or silently normalize the damaged use snapshot")
        println("GRADING_USED_SNAPSHOT_RESULT " + JsonUtils.toJson(mapOf(
            "damage" to damage, "realLocalFilesInitiallyByteExact" to 4, "errors" to errors,
            "businessArchiveFileProjectionUnchanged" to true, "detailRefusalRetainedFromNamed485" to true,
        )))
    }

    private inner class Runtime(val context: AnnotationConfigApplicationContext, val transactions: TransactionTemplate) {
        val service = context.getBean(CollectionGradingService::class.java)
        lateinit var gradingId: String
        private val application = GradingApplicationVo(at, files.getValue("APPLICATION"), EnumRelicLevel.FIRST,
            "本件合成铜器定级依据", files.getValue("APPLICATION"), "省文物局", "DEV-定申1", at.plusDays(1),
            files.getValue("APPLICATION"), files.getValue("APPLICATION"))
        private val result = GradingResultVo("省文物局", "DEV-定1", at.plusDays(2), EnumRelicLevel.FIRST,
            files.getValue("PROVINCIAL_RESULT"), "DEV-省备1", at.plusDays(3), files.getValue("USED_catalog-approval"))
        private val national = GradingNationalFilingVo("省文物局", "国家文物局", at.plusDays(4),
            files.getValue("NATIONAL_FILING"), "DEV-国备1", at.plusDays(5), files.getValue("NATIONAL_FILING"))
        fun <T> db(body: () -> T): T = requireNotNull(transactions.execute { body() })
        fun archive(phase: String): String = db {
            CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionGradingService.BIZ) and
                    (CollectionPermanentArchives.bizId eq gradingId) and (CollectionPermanentArchives.phase eq phase)
            }.single().id.value
        }
        private fun ready(): Boolean {
            @Suppress("UNCHECKED_CAST")
            val records = service.detail("one")["records"] as List<Map<String, Any?>>
            return records.single()["ready"] as Boolean
        }
        fun complete() {
            assertTrue(AopUtils.isAopProxy(service), "A direct constructor cannot prove the real Spring transaction boundary")
            gradingId = service.apply("one", application); assertFalse(ready())
            service.result(gradingId, result); assertFalse(ready())
            service.national(gradingId, national); assertTrue(ready())
            db {
                val row = CollectionDao["one"]
                val content = CollectionCatalogMapper.fromCollection(row.toModel()).copy(nature = EnumCollectionNature.RELIC, level = EnumRelicLevel.FIRST)
                assertEquals(gradingId, service.requireContent(row, content, true))
                service.recordUse(row, gradingId, "catalog-approval", EnumRelicLevel.FIRST)
            }
            assertTrue(ready())
            files.forEach { (phase, file) -> readOriginal(archive(phase), file, true) }
        }
        fun normalRepeatsPreserveProjection() {
            val before = projection()
            assertEquals(gradingId, service.apply("one", application))
            assertEquals(gradingId, service.result(gradingId, result))
            assertEquals(gradingId, service.national(gradingId, national))
            db {
                val row = CollectionDao["one"]
                assertEquals(gradingId, service.requireContent(row, CollectionCatalogMapper.fromCollection(row.toModel()).copy(level = EnumRelicLevel.FIRST), true))
                service.recordUse(row, gradingId, "catalog-approval", EnumRelicLevel.FIRST)
            }
            assertEquals(before, projection(), "Legal repeats preserve all metadata, archived bytes and source versions")
        }
        fun rejectBusinessEntrances(includeDetail: Boolean): Map<String, String> {
            val entrances = linkedMapOf<String, () -> Any?>(
                "repeat-application" to { service.apply("one", application) },
                "repeat-provincial-result" to { service.result(gradingId, result) },
                "repeat-national-filing" to { service.national(gradingId, national) },
                "adopt-content-again" to { db { val row = CollectionDao["one"]; service.requireContent(row,
                    CollectionCatalogMapper.fromCollection(row.toModel()).copy(level = EnumRelicLevel.FIRST), true) } },
                "repeat-used-stage" to { db { service.recordUse(CollectionDao["one"], gradingId, "catalog-approval", EnumRelicLevel.FIRST) } },
                "adopt-new-operation" to { db { service.recordUse(CollectionDao["one"], gradingId, "other-adoption", EnumRelicLevel.FIRST) } },
            )
            if (includeDetail) entrances["detail"] = { service.detail("one") }
            val before = projection()
            return entrances.mapValues { (name, action) ->
                val error = assertThrows(BadRequestException::class.java) { action() }
                val message = error.message.orEmpty()
                assertTrue(message.contains("原件") || message.contains("定级使用"), "Must report evidence checking, not an unrelated missing-stage/role precondition: $name/$message")
                assertEquals(before, projection(), "Rejected $name must not change the captured grading/archive/file projection")
                message
            }
        }
        fun readOriginal(archive: String, file: String, allowed: Boolean) {
            val response = MockHttpServletResponse()
            if (allowed) {
                service.requireArchivedOriginal("one", gradingId, archive, file)
                context.getBean(FileOriginalDownloadService::class.java).downloadFromArchive(file, archive, response)
                assertArrayEquals(content(file), response.contentAsByteArray)
                assertEquals(200, response.status)
                assertEquals("sha256-verified", response.getHeader("X-File-Content-Integrity"))
            } else {
                assertThrows(BadRequestException::class.java) {
                    service.requireArchivedOriginal("one", gradingId, archive, file)
                    context.getBean(FileOriginalDownloadService::class.java).downloadFromArchive(file, archive, response)
                }
                assertEquals(0, response.contentAsByteArray.size)
                assertNotEquals(304, response.status)
            }
        }
        fun projection(): Map<String, List<Map<String, String?>>> = db {
            listOf(Collections, CollectionGradings, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents)
                .associate { table -> table.tableName to table.selectAll().map { row ->
                    table.columns.associate { column -> column.name to row[column]?.toString() }
                }.sortedBy { JsonUtils.toJson(it) } }
        }
    }

    private fun fixture(body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("grading-actual.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { context ->
            context.beanFactory.registerSingleton("transactionManager", manager)
            context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            context.beanFactory.registerSingleton("scope", CollectionScopeResolver("single", "museum"))
            context.beanFactory.registerSingleton("local", LocalFileObjectStorage(dir.toString()))
            val binder = mock(BusinessAttachmentBinder::class.java)
            `when`(binder.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
            context.beanFactory.registerSingleton("binder", binder)
            context.registerBean(CollectionBusinessArchiveReadPolicy::class.java, java.util.function.Supplier {
                CollectionBusinessArchiveReadPolicy(context.getBean(CollectionFileReadAuthorization::class.java), mock(CollectionDisclosureService::class.java))
            })
            context.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier {
                FileObjectStorageService(context.getBean(LocalFileObjectStorage::class.java), mock(FileStorageConfigReader::class.java),
                    mock(RustFsStorageClient::class.java), context.getBean(FileReadAccess::class.java))
            })
            context.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java,
                FileReadAccess::class.java, CollectionPermanentArchiveService::class.java, CollectionPermanentArchiveFileReadGuard::class.java,
                CollectionGradingService::class.java, FileOriginalDownloadService::class.java)
            context.refresh(); SpringContextHolder().setApplicationContext(context)
            val runtime = Runtime(context, TransactionTemplate(manager))
            runtime.db {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs, Collections, CollectionGradings, CollectionPermanentArchives, CollectionPermanentArchiveFiles,
                    CollectionPermanentOriginalTransitions, CollectionPermanentOriginalRecoveries, Files, FileContents)
                CollectionOrgDao.new("museum") { name = "独立合成定级馆"; code = "DEV-GRADING"; systemGroupId = "museum" }
                originalReader("reader", CollectionConst.APPRAISE)
                actorRole("reader", "DEV_GRADING_LEDGER", setOf(CollectionConst.LEDGER_CREATE))
                CollectionDao.new("one") { orgId = "museum"; totalRegNo = "DEV-GRADING-1"; name = "合成铜器"; nature = EnumCollectionNature.RELIC; level = EnumRelicLevel.UNRATED }
                files.values.forEach { id ->
                    val bytes = content(id)
                    FileDao.new(id) { name = filePath(id); path = filePath(id); ext = "txt"; size = bytes.size.toLong(); secure = false
                        hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes)) }
                    java.nio.file.Files.write(dir.resolve(filePath(id)), bytes)
                }
            }
            val user = SecurityUser(id = "reader", name = "ordinary grading reader", username = "reader", password = "", groupId = "museum",
                groupIds = setOf("museum"), postIds = emptySet(), dataType = 0, roles = emptySet(),
                authorities = listOf(CollectionConst.APPRAISE, CollectionConst.LEDGER_CREATE).map(::SimpleGrantedAuthority), enabled = true)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
            try { body(runtime) } finally { SecurityContextHolder.clearContext() }
        }
    }

    private fun digest(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
