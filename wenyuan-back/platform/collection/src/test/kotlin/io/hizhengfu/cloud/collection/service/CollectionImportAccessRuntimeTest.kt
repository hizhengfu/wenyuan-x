package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.system.dao.*
import io.hizhengfu.cloud.sys.file.service.FileSystemStorageService
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.file.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate

/** Actual Spring transaction proxies plus current directory and SQLite business rows. */
class CollectionImportAccessRuntimeTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true) class Transactions

    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        val imports = c.getBean(CollectionImportService::class.java)
        val migration = c.getBean(CollectionMigrationService::class.java)
        val verification = c.getBean(CollectionMigrationVerificationService::class.java)
        val catalog = c.getBean(CollectionCatalogService::class.java)
        val word = c.getBean(CollectionWordImportService::class.java)
        val scope = c.getBean(CollectionScopeResolver::class.java)
        val binder = c.getBean(BusinessAttachmentBinder::class.java)
        val parser = c.getBean(CollectionWordDocumentParser::class.java)
        val file = MockMultipartFile("file", "sample.docx", "application/octet-stream", byteArrayOf(1, 2, 3))
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun grant(vararg permissions: String) = db { RolePermissions.deleteAll(); actorRole("reader", "CURRENT_IMPORT", permissions.toSet()) }
        fun damage(kind: String) = db { when (kind) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["reader"].enabled = false
            "role_disabled" -> RoleDao.find { Roles.code eq "FILE_reader" }.single().enabled = false
            "moved" -> { UserDao["reader"].groupId = "other"; actorRole("reader", "CROSS", setOf(CollectionConst.CROSS_ORG)) }
            "configured" -> ReflectionTestUtils.setField(scope, "defaultOrgId", "other")
            "museum_disabled" -> CollectionOrgDao["museum"].orgStatus = EnumOrgStatus.DISABLED
            "group_disabled" -> GroupDao["museum"].enabled = false
            "storage" -> actorRole("reader", "STORAGE", setOf(CollectionConst.STORAGE))
            "keeper" -> actorRole("reader", CollectionConst.ROLE_KEEPER)
            else -> error(kind)
        } }
        fun row(reg: String = "NEW", category: String = "category") = CollectionImportRowVo(totalRegNo = reg,
            name = "新藏品", categoryId = category, level = "GENERAL", source = "OLD", acquireDate = LocalDate.of(2020, 1, 1), quantity = 1, unit = "件")
        fun snapshot(): List<List<List<Any?>>> = db {
            listOf(CollectionOrgs, Collections, CollectionRegistrationNumbers, CollectionCatalogs, CollectionCatalogVersions,
                CollectionLedgerLogs, CollectionWordImports, CollectionTrackingEvents, StorageMovements, CollectionAttachments, FileContents, CollectionLedgerStatEvents).map { table ->
                table.selectAll().map { row -> table.columns.map { row[it] } }
            }
        }
        fun preview(type: EnumCollectionWordDocumentType = EnumCollectionWordDocumentType.REGISTRATION) {
            val value = CollectionWordImportPreviewVo(valid = true, documentType = type, sourceHash = "new-word", fingerprint = "format",
                sourceFileName = "sample.docx", totalRegNo = "EXISTING", collectionName = "原藏品",
                registration = if (type == EnumCollectionWordDocumentType.REGISTRATION) row(category = "瓷器") else null,
                events = listOf(CollectionTrackingEventPreviewVo(EnumCollectionTrackingEventType.NOTE, "历史记录", "原始内容")),
                movements = if (type == EnumCollectionWordDocumentType.DYNAMIC_TRACKING) listOf(CollectionWordMovementPreviewVo(
                    rowNo = 2, operatedAt = java.time.LocalDateTime.of(2020, 1, 1, 9, 0), type = EnumMoveType.IN,
                    toWarehouseName = "保管库房", operator = "原经办人")) else emptyList())
            doReturn(value).`when`(parser).parse(any(ByteArray::class.java) ?: byteArrayOf(), anyString() ?: "")
        }
        fun entry(name: String): Any? = when (name) {
            "validate" -> imports.validate(listOf(row()))
            "commit" -> imports.commit(listOf(row()))
            "empty_validate" -> imports.validate(emptyList())
            "empty_commit" -> imports.commit(emptyList())
            "word_validate" -> word.validate(file)
            "word_commit" -> word.commit(file)
            else -> error(name)
        }
    }

    private fun fixture(mode: String = "single", onUpload: ((Runtime) -> Unit)? = null, body: (Runtime) -> Unit) {
        val old = SecurityUser(id = "reader", name = "旧管理员登录", username = "reader", password = "", groupId = "museum",
            groupIds = setOf("museum"), postIds = emptySet(), dataType = 0, roles = setOf(EnumRole.ADMIN.value), enabled = true,
            authorities = listOf("ADMIN", CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT, CollectionConst.STORAGE, CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority))
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(old, null, old.authorities)
        val pgConfig = (System.getProperty("collection.test.import.pg.config") ?: System.getProperty("collection.test.pg.config"))
        val dataSource = if (pgConfig == null) SQLiteDataSource().apply { url = "jdbc:sqlite:${root.resolve("import.db")}" }
        else {
            val properties = java.util.Properties().apply { java.nio.file.Files.newInputStream(Path.of(pgConfig)).use { load(it) } }
            org.postgresql.ds.PGSimpleDataSource().apply {
                setURL(properties.getProperty("url"))
                user = properties.getProperty("user")
                password = properties.getProperty("password")
                currentSchema = "import_" + java.util.UUID.randomUUID().toString().replace("-", "")
                connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $currentSchema") } }
            }
        }
        val manager = SpringTransactionManager(dataSource, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            lateinit var r: Runtime
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver(mode, "museum"))
            c.beanFactory.registerSingleton("binder", mock(BusinessAttachmentBinder::class.java))
            c.beanFactory.registerSingleton("migrationArchives", mock(CollectionPermanentArchiveService::class.java))
            c.beanFactory.registerSingleton("parser", mock(CollectionWordDocumentParser::class.java))
            c.beanFactory.registerSingleton("grading", mock(CollectionGradingService::class.java))
            c.beanFactory.registerSingleton("accession", mock(AccessionReceiptService::class.java))
            c.beanFactory.registerSingleton("change", mock(CollectionChangeService::class.java))
            c.beanFactory.registerSingleton("retained", mock(CollectionRetainedLedgerService::class.java))
            c.beanFactory.registerSingleton("disclosure", mock(CollectionDisclosureService::class.java))
            c.beanFactory.registerSingleton("fileStorage", mock(FileService::class.java).also { files ->
                `when`(files.resolveFileId(anyString())).thenAnswer { it.getArgument<String>(0) }
                doAnswer { call -> FileContents.insert {it[fileId]=call.getArgument<String>(0);it[contentId]=call.getArgument<String>(1)}; null }
                    .`when`(files).insertFileContent(anyString(),anyString(),nullable(String::class.java))
            })
            c.beanFactory.registerSingleton("uploads", mock(FileSystemStorageService::class.java) { call ->
                if (call.method.name == "upload") {
                    FileDao.new("uploaded-word") {name="word.docx";path="word.docx";hash="uploaded";size=3;ext="docx"}
                    FileUsers.insert {it[fileId]="uploaded-word";it[userId]="reader";it[uploadDate]=java.time.LocalDateTime.now()}
                    onUpload?.invoke(r); FileVo(id = "uploaded-word")
                } else RETURNS_DEFAULTS.answer(call)
            })
            c.register(Transactions::class.java, CollectionFileService::class.java, CollectionOperationLockService::class.java, CollectionRegistrationNumberService::class.java,
                CollectionCatalogService::class.java, CollectionService::class.java, CollectionImportService::class.java, CollectionWordImportService::class.java, CollectionMigrationService::class.java, CollectionMigrationVerificationService::class.java, CollectionOrgCodeService::class.java, StorageMovementService::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            r = Runtime(c, TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema()
                    SchemaUtils.create(Files, FileUsers, FileContents, CollectionMedias, CollectionMediaFolders, CollectionAttachments, CollectionOrgCodes, CollectionOrgCodeChanges, CollectionOrgs, Collections, CollectionCategories, Warehouses, CollectionOperationLocks,
                        CollectionRegistrationNumbers, CollectionRetainedLedgers, CollectionCatalogs, CollectionCatalogVersions, CollectionLedgerLogs,
                        CollectionWordImports, CollectionTrackingEvents, StorageMovements, CollectionLedgerStatEvents)
                    originalReader("reader", CollectionConst.LEDGER_CREATE)
                    CollectionCategoryDao.new("category") { name = "瓷器"; code="03"; parentId = "0"; parentIds = "0," }
                    for (org in listOf("museum", "other")) {
                        CollectionOrgDao.new(org) { name = org; code = org; systemGroupId = org }
                        CollectionCategoryDao.new("category-$org") { orgId = if(org=="museum") null else org; name = "铜器"; code="04"; parentId = "0"; parentIds = "0," }
                        WarehouseDao.new("warehouse-$org") { orgId = org; name = "保管库房"; parentId = "0"; parentIds = "0," }
                        CollectionDao.new("item-$org") {
                            orgId = org; name = "原藏品"; totalRegNo = "EXISTING"; categoryId = "category"
                            acquireDate = LocalDate.of(2020, 1, 1); lifeStatus = EnumCollectionStatus.PENDING; catalogStatus = EnumCatalogStatus.DRAFT
                        }
                        CollectionWordImportDao.new("history-$org") {
                            orgId = org; collectionId = "item-$org"; documentType = EnumCollectionWordDocumentType.DYNAMIC_TRACKING
                            sourceFileName = "history.docx"; sourceHash = "history"; fingerprint = "format"
                        }
                        CollectionTrackingEventDao.new("event-$org") {
                            orgId = org; collectionId = "item-$org"; importId = "history-$org"
                            eventType = EnumCollectionTrackingEventType.NOTE; title = "历史"; content = "原文-$org"
                        }
                    }
                }
                r.preview(); body(r)
            } finally {
                SecurityContextHolder.clearContext()
                if (dataSource is org.postgresql.ds.PGSimpleDataSource) dataSource.connection.use { connection ->
                    connection.createStatement().use { it.execute("DROP SCHEMA ${dataSource.currentSchema} CASCADE") }
                }
            }
        }
    }

    private fun loginMigrationActor(id: String) {
        val user = SecurityUser(id=id, name=id, username=id, password="", groupId="museum", groupIds=setOf("museum"),
            postIds=emptySet(), dataType=0, roles=emptySet(), authorities=emptyList(), enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    private fun unverifiedMigration(r: Runtime): String = unverifiedBatch(r, 1).single()
    private fun unverifiedBatch(r: Runtime, count: Int): List<String> {
        `when`(r.binder.resolveSingle("evidence")).thenReturn("evidence")
        `when`(r.c.getBean(CollectionPermanentArchiveService::class.java).prepareFiles("evidence")).thenReturn(mapOf(
            "evidence" to CollectionPermanentArchiveService.PreparedFile("evidence.pdf", io.hizhengfu.cloud.common.file.EnumFileStorageType.LOCAL,
                setOf("evidence.pdf"), CollectionOriginalContent.read("original".byteInputStream()), null)))
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.CATALOG_EDIT, CollectionConst.CATALOG_AUDIT)
        r.db { originalReader("reviewer", CollectionConst.CATALOG_AUDIT) }
        val request = CollectionMigrationRequestVo("old-system", "原账待核实", true, (1..count).map { index ->
            CollectionMigrationRowVo(r.row(if(count==1) "LEGACY" else "LEGACY-$index").copy(acquireDate=LocalDate.of(2000,1,1)), if(count==1) "source" else "source-$index", "museum", "RELIC", "IN_STORE", verification="UNVERIFIED") })
        val preview = r.migration.validate(request)
        assertTrue(preview.valid, preview.errors.toString())
        return r.migration.commit(request, preview.confirmation!!.orgId, preview.confirmation.validationHash).createdIds
    }
    private fun verificationContent() = CollectionMigrationVerificationContent(
        java.time.LocalDateTime.of(2009,1,1,0,0), "原登记人", "evidence", false, true, "核对纸质原账第12页，本件身份及性质等级数量一致", classificationMissingReason="原账未编分类号")

    @Test fun `unverified migration is confirmed by another actor then cataloged without a second registration`() = fixture { r ->
        val id = unverifiedMigration(r)
        r.db { actorRole("reader", "ORG_MANAGER", setOf(CollectionConst.ORG_MANAGE)) }
        val codes = r.c.getBean(CollectionOrgCodeService::class.java)
        codes.correct("museum", CollectionOrgCodeCorrectionVo("MUSEUM-NEW", "核对原机构登记资料后更正"), codes.context("museum").stateHash)
        r.db { assertTrue(CollectionOrgCodeRegistry.matches("museum", "museum")) }
        assertTrue(r.catalog.get(id).migrationVerificationRequired)
        val blocked = assertThrows(BadRequestException::class.java) { r.catalog.submitConfirmed(id, r.catalog.get(id).stateHash!!) }
        assertTrue(blocked.message!!.contains("旧系统迁移"))
        val initial = r.verification.get(id)
        val draft = r.verification.save(id, verificationContent().copy(originalRegisteredAt=null), initial.stateHash, false)
        assertEquals(EnumMigrationVerificationStatus.DRAFT, draft.record!!.status)
        val submitted = r.verification.save(id, verificationContent(), draft.stateHash, true)
        assertEquals(2, submitted.history.size)
        assertTrue(submitted.canWithdraw); assertFalse(submitted.canReview)
        assertThrows(BadRequestException::class.java) { r.verification.review(id, CollectionMigrationVerificationReviewVo(true,"同意"), submitted.stateHash) }
        loginMigrationActor("reviewer")
        val review = r.verification.get(id)
        assertTrue(review.canReview); assertFalse(review.canEdit)
        clearInvocations(r.binder)
        val confirmed = r.verification.review(id, CollectionMigrationVerificationReviewVo(true,"已核对原账及登记依据，同意"), review.stateHash)
        verify(r.binder, never()).bindSingle(anyString(), anyString(), nullable(String::class.java))
        assertEquals("CONFIRMED", confirmed.source.verification)
        assertEquals(3, confirmed.history.size)
        assertThrows(BadRequestException::class.java) { r.verification.review(id, CollectionMigrationVerificationReviewVo(true,"再次确认"), review.stateHash) }
        r.db {
            assertEquals(verificationContent().originalRegisteredAt, CollectionDao[id].registeredAt)
            assertEquals(EnumCatalogStatus.DRAFT, CollectionDao[id].catalogStatus)
            assertEquals(1, CollectionLedgerStatEventDao.all().count())
            assertEquals(2, CollectionLedgerLogDao.find { (CollectionLedgerLogs.collectionId eq id) and (CollectionLedgerLogs.field eq "legacyMigration") }.count())
            assertTrue(CollectionMigrationCatalogPolicy.initial(CollectionDao[id]))
        }
        loginMigrationActor("reader")
        val catalog = r.catalog.get(id)
        assertTrue(catalog.historicalMigration); assertFalse(catalog.migrationVerificationRequired)
        r.catalog.submitConfirmed(id, catalog.stateHash!!)
        loginMigrationActor("reviewer")
        r.catalog.reviewConfirmed(id, CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"编目核验通过"), r.catalog.get(id).stateHash!!)
        r.db {
            assertEquals(verificationContent().originalRegisteredAt, CollectionDao[id].registeredAt)
            assertEquals(EnumCatalogStatus.APPROVED, CollectionDao[id].catalogStatus)
            assertEquals(1, CollectionLedgerStatEventDao.all().count())
        }
        verifyNoInteractions(r.c.getBean(AccessionReceiptService::class.java), r.c.getBean(CollectionGradingService::class.java))
    }

    @Test fun `undated historical registration is official stock and catalog never fabricates a new registration`() = fixture { r ->
        val id=unverifiedMigration(r)
        val content=verificationContent().copy(originalRegisteredAt=null,originalRegisteredBy=null,
            originalDateMissingReason="纸账未载日期，已查原账与交接材料",originalRegistrarMissingReason="原账未署名")
        val saved=r.verification.save(id,content,r.verification.get(id).stateHash,true)
        loginMigrationActor("reviewer")
        val confirmed=r.verification.review(id,CollectionMigrationVerificationReviewVo(true,"原登记事实有据，缺项照实保留"),saved.stateHash)
        assertEquals(setOf("分类号","原登记日期","原登记人"),confirmed.missingFields.toSet())
        r.db {
            val row=CollectionDao[id]; assertTrue(row.registered); assertNull(row.registeredAt); assertNotNull(row.historicalConfirmedAt)
            val stats=CollectionStatBuilder.build(listOf(row.toModel()))
            assertEquals(1,stats.total);assertEquals(0,stats.registeredThisYear);assertEquals(0,stats.unverifiedRegistrationTotal)
            assertEquals(0,CollectionLedgerStatEventDao.all().count())
        }
        loginMigrationActor("reader")
        assertThrows(BadRequestException::class.java) { r.db { r.catalog.requireInitialCatalog(CollectionDao[id]) } }
        r.catalog.submitConfirmed(id,r.catalog.get(id).stateHash!!)
        loginMigrationActor("reviewer")
        r.catalog.reviewConfirmed(id,CollectionCatalogReviewVo(EnumCatalogStatus.APPROVED,"编目核验通过"),r.catalog.get(id).stateHash!!)
        loginMigrationActor("reader")
        r.grant(CollectionConst.LEDGER_CREATE,CollectionConst.STAT)
        r.db {
            assertNull(CollectionDao[id].registeredAt);assertTrue(CollectionDao[id].registered)
            assertEquals(0,CollectionLedgerStatEventDao.all().count())
            for (year in listOf(2009,LocalDate.now().year)) {
                val annual=CollectionLedgerStatisticsService(r.scope).preview(year)
                assertEquals(0,annual.newTotal);assertEquals(0,annual.openingTotal);assertEquals(0,annual.closingTotal)
                assertTrue(annual.issues.any { it.collectionId==id && it.description.contains("原登记日期待考") })
            }
        }
    }

    @Test fun `batch submission and independent review are atomic and bind every item to its batch and hash`() = fixture { r ->
        val ids=unverifiedBatch(r,2)
        val views=ids.map(r.verification::get);val batch=views.first().source.batchId
        assertEquals(2,r.verification.batch(batch).size)
        val items=views.map { CollectionMigrationBatchItem(it.collectionId,it.stateHash,verificationContent()) }
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java) { r.verification.processBatch(CollectionMigrationBatchRequest("other",items)) }
        assertThrows(BadRequestException::class.java) { r.verification.processBatch(CollectionMigrationBatchRequest(batch,items.mapIndexed { i,v -> if(i==1)v.copy(expectedStateHash="stale") else v })) }
        assertEquals(before,r.snapshot())
        val submitted=r.verification.processBatch(CollectionMigrationBatchRequest(batch,items))
        assertTrue(submitted.all { it.record?.status==EnumMigrationVerificationStatus.SUBMITTED })
        val reviewing=submitted.map { CollectionMigrationBatchItem(it.collectionId,it.stateHash) }
        assertThrows(BadRequestException::class.java) { r.verification.processBatch(CollectionMigrationBatchRequest(batch,reviewing,CollectionMigrationVerificationReviewVo(true,"本人不能复核"))) }
        loginMigrationActor("reviewer")
        val snapshot=r.snapshot()
        assertThrows(BadRequestException::class.java) { r.verification.processBatch(CollectionMigrationBatchRequest(batch,reviewing.mapIndexed { i,v -> if(i==1)v.copy(expectedStateHash="stale") else v },CollectionMigrationVerificationReviewVo(true,"核对"))) }
        assertEquals(snapshot,r.snapshot())
        val approved=r.verification.processBatch(CollectionMigrationBatchRequest(batch,reviewing,CollectionMigrationVerificationReviewVo(true,"逐件独立核对原账")))
        assertTrue(approved.all { it.source.verification=="CONFIRMED" && it.record?.status==EnumMigrationVerificationStatus.APPROVED })
        r.db { assertEquals(2,CollectionLedgerStatEventDao.all().count()) }
    }

    @Test fun `migration after a code correction accepts this museums historical code without rewriting it`() = fixture { r ->
        r.grant(CollectionConst.ORG_MANAGE)
        val codes = r.c.getBean(CollectionOrgCodeService::class.java)
        codes.correct("museum", CollectionOrgCodeCorrectionVo("MUSEUM-NEW", "核对原机构资料"), codes.context("museum").stateHash)
        val id = unverifiedMigration(r)
        r.db {
            assertEquals("museum", CollectionDao[id].orgId)
            assertEquals("LEGACY", CollectionDao[id].totalRegNo)
            assertEquals("museum", CollectionMigrationHistory.source(CollectionDao[id])!!.originalOrgCode)
            assertFalse(CollectionOrgCodeRegistry.matches("other", "museum"))
        }
        assertEquals("museum", r.verification.get(id).source.originalOrgCode)
    }

    @Test fun `verification supports withdrawal rejection and resubmission while retaining each original`() = fixture { r ->
        val id=unverifiedMigration(r)
        val first=r.verification.save(id,verificationContent(),r.verification.get(id).stateHash,true)
        val withdrawn=r.verification.withdraw(id,first.stateHash)
        assertEquals(EnumMigrationVerificationStatus.WITHDRAWN,withdrawn.record!!.status)
        val second=r.verification.save(id,verificationContent(),withdrawn.stateHash,true)
        loginMigrationActor("reviewer")
        assertThrows(BadRequestException::class.java) { r.verification.withdraw(id,second.stateHash) }
        assertThrows(BadRequestException::class.java) { r.verification.review(id,CollectionMigrationVerificationReviewVo(false,""),second.stateHash) }
        val rejected=r.verification.review(id,CollectionMigrationVerificationReviewVo(false,"请补充原账页码"),second.stateHash)
        loginMigrationActor("reader")
        val final=r.verification.save(id,verificationContent().copy(reason="已补核纸质原账第13页"),rejected.stateHash,true)
        assertEquals(5,final.history.size)
        assertEquals("请补充原账页码",final.history[1].opinion)
        r.db { assertEquals(null,CollectionDao[id].registeredAt);assertEquals(0,CollectionLedgerStatEventDao.count()) }
    }

    @Test fun `stale pages and changed collection snapshots cannot approve historical registration`() = fixture { r ->
        val id=unverifiedMigration(r)
        val initial=r.verification.get(id)
        val submitted=r.verification.save(id,verificationContent(),initial.stateHash,true)
        assertThrows(BadRequestException::class.java) { r.verification.save(id,verificationContent(),initial.stateHash,false) }
        r.db { CollectionDao[id].quantity=2 }
        loginMigrationActor("reviewer")
        assertThrows(BadRequestException::class.java) { r.verification.review(id,CollectionMigrationVerificationReviewVo(true,"同意"),submitted.stateHash) }
        val fresh=r.verification.get(id)
        assertThrows(BadRequestException::class.java) { r.verification.review(id,CollectionMigrationVerificationReviewVo(true,"同意"),fresh.stateHash) }
        assertEquals(EnumMigrationVerificationStatus.REJECTED,r.verification.review(id,CollectionMigrationVerificationReviewVo(false,"原账数量有差异"),fresh.stateHash).record!!.status)
        r.db { assertEquals(null,CollectionDao[id].registeredAt) }
    }

    @Test fun `unreadable evidence blocks approval but leaves migration detail readable and state unchanged`() = fixture { r ->
        val id=unverifiedMigration(r)
        r.verification.save(id,verificationContent(),r.verification.get(id).stateHash,true)
        loginMigrationActor("reviewer")
        val view=r.verification.get(id)
        val before=r.snapshot()
        doThrow(BadRequestException("原件文件缺失")).`when`(r.binder).requireReadableFileId("evidence")
        assertEquals(view.record!!.id,r.verification.get(id).record!!.id)
        assertThrows(BadRequestException::class.java) { r.verification.review(id,CollectionMigrationVerificationReviewVo(true,"同意"),view.stateHash) }
        assertEquals(before,r.snapshot())
        assertEquals(EnumMigrationVerificationStatus.REJECTED,r.verification.review(id,CollectionMigrationVerificationReviewVo(false,"原件缺失，请补充"),view.stateHash).record!!.status)
    }

    @ParameterizedTest @ValueSource(strings=["revoked","disabled","moved","configured","museum_disabled","storage"])
    fun `migration verification rechecks current writer authorization`(damage:String)=fixture { r ->
        val id=unverifiedMigration(r)
        val view=r.verification.get(id)
        r.damage(damage)
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java) { r.verification.save(id,verificationContent(),view.stateHash,true) }
        assertEquals(before,r.snapshot())
    }

    @Test fun `changed original file bytes cannot be approved under an unchanged record id`() = fixture { r ->
        val id=unverifiedMigration(r)
        r.verification.save(id,verificationContent(),r.verification.get(id).stateHash,true)
        loginMigrationActor("reviewer")
        val before=r.snapshot();val view=r.verification.get(id)
        `when`(r.c.getBean(CollectionPermanentArchiveService::class.java).prepareFiles("evidence")).thenReturn(mapOf(
            "evidence" to CollectionPermanentArchiveService.PreparedFile("evidence.pdf", io.hizhengfu.cloud.common.file.EnumFileStorageType.LOCAL,
                setOf("evidence.pdf"), CollectionOriginalContent.read("changed".byteInputStream()), null)))
        assertThrows(BadRequestException::class.java) { r.verification.review(id,CollectionMigrationVerificationReviewVo(true,"同意"),view.stateHash) }
        assertEquals(before,r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings=["revoked","disabled","moved","museum_disabled"])
    fun `migration approval checks current reviewer authorization`(damage:String)=fixture { r ->
        val id=unverifiedMigration(r)
        r.verification.save(id,verificationContent(),r.verification.get(id).stateHash,true)
        loginMigrationActor("reviewer");val view=r.verification.get(id)
        r.db { when(damage) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["reviewer"].enabled=false
            "moved" -> UserDao["reviewer"].groupId="other"
            "museum_disabled" -> CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        } }
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java) { r.verification.review(id,CollectionMigrationVerificationReviewVo(true,"同意"),view.stateHash) }
        assertEquals(before,r.snapshot())
    }

    @Test fun `registered migration preserves old dates splits uncertain rows and keeps catalog drafts`() = fixture { r ->
        `when`(r.binder.resolveSingle("evidence")).thenReturn("evidence")
        val request=CollectionMigrationRequestVo("old-system","逐件核对原总账",true,listOf(
            CollectionMigrationRowVo(r.row("OLD-1"),"source-1","museum","RELIC","IN_STORE","2009-01-01","原登记员","evidence","CONFIRMED","NO"),
            CollectionMigrationRowVo(r.row("OLD-2"),"source-2","museum","RELIC","ON_LOAN",null,null,null,"UNVERIFIED","NO")))
        val preview=r.migration.validate(request)
        assertTrue(preview.valid,preview.errors.toString());assertEquals(0,preview.registeredCount);assertEquals(2,preview.unverifiedCount)
        val result=r.migration.commit(request,preview.confirmation!!.orgId,preview.confirmation.validationHash)
        assertTrue(result.valid);assertEquals(2,result.createdIds.size)
        r.db {
            val first=CollectionDao[result.createdIds[0]];val second=CollectionDao[result.createdIds[1]]
            assertNull(first.registeredAt); assertFalse(first.registered)
            assertEquals(null,second.registeredAt);assertEquals(EnumCollectionStatus.ON_LOAN,second.lifeStatus)
            assertEquals(EnumCatalogStatus.DRAFT,first.catalogStatus);assertEquals(EnumCatalogStatus.DRAFT,second.catalogStatus)
            assertEquals(0,CollectionLedgerStatEventDao.all().count())
            assertEquals(2,CollectionLedgerLogDao.find { CollectionLedgerLogs.field eq "legacyMigration" }.count())
            val snap=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(CollectionLedgerLogDao.find { CollectionLedgerLogs.field eq "legacyMigration" }.first().after,CollectionMigrationSnapshotVo::class.java)!!
            assertEquals("old-system",snap.sourceSystem);assertEquals("reader",snap.migratedBy)
        }
        r.grant(CollectionConst.LEDGER_CREATE,CollectionConst.STAT,CollectionConst.CATALOG_EDIT)
        `when`(r.c.getBean(CollectionPermanentArchiveService::class.java).prepareFiles("evidence")).thenReturn(mapOf(
            "evidence" to CollectionPermanentArchiveService.PreparedFile("evidence.pdf", io.hizhengfu.cloud.common.file.EnumFileStorageType.LOCAL,
                setOf("evidence.pdf"), CollectionOriginalContent.read("original".byteInputStream()), null)))
        r.db { originalReader("reviewer", CollectionConst.CATALOG_AUDIT) }
        val waiting=r.verification.get(result.createdIds[0])
        assertEquals("UNVERIFIED",waiting.source.verification); assertEquals("CONFIRMED",waiting.source.declaredVerification)
        val submitted=r.verification.save(waiting.collectionId,verificationContent(),waiting.stateHash,true)
        loginMigrationActor("reviewer")
        r.verification.review(waiting.collectionId,CollectionMigrationVerificationReviewVo(true,"独立核对通过"),submitted.stateHash)
        loginMigrationActor("reader")
        val content=r.db {
            val first=CollectionDao[result.createdIds[0]]
            assertTrue(CollectionMigrationCatalogPolicy.initial(first))
            CollectionCatalogMapper.fromCollection(first.toModel())
        }
        val catalogs=r.c.getBean(CollectionCatalogService::class.java)
        catalogs.save(result.createdIds[0],content.copy(researchNote="迁移后补充研究说明"))
        val view = catalogs.get(result.createdIds[0])
        assertTrue(view.historicalMigration)
        assertEquals(CollectionCatalogPolicy.completeness(view.content, historical = true), view.completeness)
        assertTrue(view.completeness.complete)
        assertFalse(catalogs.get(result.createdIds[1]).historicalMigration)
        assertThrows(BadRequestException::class.java) { catalogs.save(result.createdIds[0],content.copy(quantity=9,actualQuantity=9)) }
        assertEquals(1, catalogs.submit(result.createdIds[0]))
        r.db {
            assertEquals(1,CollectionDao[result.createdIds[0]].quantity)
            assertEquals(java.time.LocalDateTime.of(2009,1,1,0,0),CollectionDao[result.createdIds[0]].registeredAt)
            val annual=CollectionLedgerStatisticsService(r.scope).preview(java.time.LocalDate.now().year)
            assertEquals(0,annual.newTotal)
            assertTrue(annual.issues.any { it.collectionId==result.createdIds[0] && it.description.contains("旧系统迁移") })
        }
        assertThrows(BadRequestException::class.java) { r.db { catalogs.requireInitialCatalog(CollectionDao[result.createdIds[0]]) } }
        val before=r.snapshot()
        assertFalse(r.migration.commit(request,preview.confirmation.orgId,preview.confirmation.validationHash).valid)
        assertEquals(before,r.snapshot())
        assertFalse(r.migration.validate(request.copy(rows=listOf(request.rows[0].copy(item=r.row("NEW-NUMBER"))))).valid)
        assertFalse(r.migration.validate(request.copy(rows=listOf(request.rows[0].copy(sourceRecordId="new-source",originalOrgCode="other",item=r.row("NEW"))))).valid)
    }
    @Test fun `migration confirmation rejects changed data or revoked access and rolls back later row failure`() = fixture { r ->
        `when`(r.binder.resolveSingle("evidence")).thenReturn("evidence")
        val request=CollectionMigrationRequestVo("old-system","核对依据不足暂存",true,listOf(
            CollectionMigrationRowVo(r.row("OLD-A"),"a","museum","RELIC","IN_STORE","2009-01-01","原登记人","evidence","CONFIRMED"),
            CollectionMigrationRowVo(r.row("OLD-B"),"b","museum","RELIC","IN_STORE",verification="UNVERIFIED")))
        val preview=r.migration.validate(request);assertTrue(preview.valid,preview.errors.toString())
        val confirmation=preview.confirmation!!;val before=r.snapshot()
        assertThrows(BadRequestException::class.java) { r.migration.commit(request.copy(reason="改动核验说明"),confirmation.orgId,confirmation.validationHash) }
        assertEquals(before,r.snapshot())
        doNothing().doThrow(BadRequestException("第二条依据绑定失败")).`when`(r.binder).bindSingle(anyString() ?: "",eq("旧系统登记迁移依据") ?: "",nullable(String::class.java))
        assertThrows(BadRequestException::class.java) { r.migration.commit(request,confirmation.orgId,confirmation.validationHash) }
        assertEquals(before,r.snapshot())
        r.damage("revoked")
        assertThrows(BadRequestException::class.java) { r.migration.commit(request,confirmation.orgId,confirmation.validationHash) }
        assertEquals(before,r.snapshot())
    }

    @Test fun `ordinary initial editing persists standard details and synchronizes the catalog draft`() = fixture { r ->
        val service=r.c.getBean(CollectionService::class.java)
        val initial=r.db { CollectionDao["item-museum"].toModel() }
        val details=CollectionRegistrationDetailsVo(eraMeaning=EnumCollectionEraMeaning.MANUFACTURED,
            eraNotation=EnumCollectionEraNotation.CHINESE_HISTORY,measurementMethod="软尺测量",measuredBy="登记员")
        val edited=initial.copy(nature=EnumCollectionNature.RELIC,actualQuantity=2,actualQuantityUnit="只",dimensionUnit="mm",
            registrationDetails=details,era="清",texture="玻璃",textureCategory1="单一质地",textureCategory2="无机质/非金属材料")
        service.update("item-museum",edited)
        r.db {
            val saved=CollectionDao["item-museum"].toModel()
            assertEquals(edited.nature,saved.nature);assertEquals(2,saved.actualQuantity);assertEquals("只",saved.actualQuantityUnit)
            assertEquals("mm",saved.dimensionUnit);assertEquals(details,saved.registrationDetails)
            assertEquals(initial.entryDate,saved.entryDate);assertEquals(initial.receiptCompletedAt,saved.receiptCompletedAt)
            val catalog=CollectionCatalogDao.find {CollectionCatalogs.collectionId eq "item-museum"}.single()
            assertEquals(details,catalog.content.registrationDetails);assertEquals(2,catalog.content.actualQuantity)
            assertEquals("玻璃",catalog.content.texture);assertEquals(EnumCatalogStatus.DRAFT,catalog.catalogStatus)
        }
        assertThrows(BadRequestException::class.java) { service.update("item-museum",edited.copy(actualQuantityUnit="套")) }
        r.db {CollectionDao["item-museum"].catalogStatus=EnumCatalogStatus.APPROVED}
        assertThrows(BadRequestException::class.java) { service.update("item-museum",edited.copy(actualQuantity=3)) }
    }

    @ParameterizedTest @CsvSource("revoked,validate", "revoked,commit", "revoked,empty_validate", "revoked,empty_commit", "revoked,word_validate", "revoked,word_commit",
        "disabled,validate", "disabled,commit", "disabled,word_validate", "disabled,word_commit",
        "role_disabled,validate", "role_disabled,commit", "role_disabled,word_validate", "role_disabled,word_commit",
        "moved,validate", "moved,commit", "moved,word_validate", "moved,word_commit",
        "configured,validate", "configured,commit", "configured,word_validate", "configured,word_commit",
        "museum_disabled,validate", "museum_disabled,commit", "museum_disabled,word_validate", "museum_disabled,word_commit",
        "group_disabled,validate", "group_disabled,commit", "group_disabled,word_validate", "group_disabled,word_commit",
        "storage,validate", "storage,commit", "storage,word_validate", "storage,word_commit",
        "keeper,validate", "keeper,commit", "keeper,word_validate", "keeper,word_commit")
    fun `manual imports reject current authority or scope changes without mutating business records`(damage: String, entry: String) = fixture { r ->
        r.damage(damage); val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.entry(entry) }; assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings = ["foreign_category", "deleted_category", "empty_category", "foreign_warehouse", "deleted_warehouse", "empty_warehouse", "exhibition", "wrong_type"])
    fun `prevalidation and commit reject unavailable references including empty directories`(kind: String) = fixture { r ->
        val row = r.db { when (kind) {
            "foreign_category" -> r.row(category = "category-other")
            "deleted_category" -> { CollectionCategoryDao["category"].status = EnumContentStatus.DELETE; r.row() }
            "empty_category" -> { CollectionCategories.deleteAll(); r.row() }
            "foreign_warehouse" -> r.row().copy(warehouseId = "warehouse-other")
            "deleted_warehouse" -> { WarehouseDao["warehouse-museum"].status = EnumContentStatus.DELETE; r.row().copy(warehouseId = "warehouse-museum") }
            "empty_warehouse" -> { Warehouses.deleteAll(); r.row().copy(warehouseId = "warehouse-museum") }
            "exhibition" -> { WarehouseDao["warehouse-museum"].sceneType = EnumLocationScene.EXHIBITION; r.row().copy(warehouseId = "warehouse-museum") }
            else -> { WarehouseDao["warehouse-museum"].type = EnumWarehouseType.SHOWCASE; r.row().copy(warehouseId = "warehouse-museum") }
        } }
        val before = r.snapshot()
        val validation = r.imports.validate(listOf(row)); assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.field == if (kind.contains("category")) "categoryId" else "warehouseId" })
        assertFalse(r.imports.commit(listOf(row)).valid); assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings = ["row", "reason", "mode", "matched_record", "new_match"])
    fun `ordinary commit rejects changes to the confirmed import basis`(change: String) = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
        val original = if (change == "matched_record") r.row("EXISTING") else r.row()
        val confirmed = r.imports.validate(listOf(original), EnumCollectionImportMode.UPSERT, "原依据").confirmation!!
        if (change == "matched_record") r.db { CollectionDao["item-museum"].remark = "另一人补录" }
        if (change == "new_match") r.db { CollectionDao.new("new-match") { orgId = "museum"; totalRegNo = "NEW"; name = "后来建账" } }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) {
            r.imports.commit(listOf(if (change == "row") original.copy(name = "已换文件") else original),
                if (change == "mode") EnumCollectionImportMode.CREATE_ONLY else EnumCollectionImportMode.UPSERT,
                if (change == "reason") "另一个依据" else "原依据", confirmed.orgId, confirmed.validationHash)
        }
        assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings=["upsert","coexisting-clean","create-only"])
    fun `historical exact number import targets the original record`(operation:String)=fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT)
        val number=" EXISTING "
        r.db {
            CollectionDao["item-museum"].totalRegNo=number
            if(operation=="coexisting-clean") CollectionDao.new("clean-item") {
                orgId="museum";totalRegNo="EXISTING";name="另一件干净号码藏品"
                acquireDate=LocalDate.of(2020,1,1);lifeStatus=EnumCollectionStatus.PENDING;catalogStatus=EnumCatalogStatus.DRAFT
            }
        }
        val rows=listOf(r.row(number).copy(remark="历史原号回导"))
        val before=r.snapshot()
        val mode=if(operation=="create-only")EnumCollectionImportMode.CREATE_ONLY else EnumCollectionImportMode.UPSERT
        val preview=r.imports.validate(rows,mode,"历史资料补录")
        if(operation=="create-only") {
            assertFalse(preview.valid)
            assertTrue(preview.errors.any { it.field=="totalRegNo" })
            assertFalse(r.imports.commit(rows,mode,"历史资料补录").valid)
            assertEquals(before,r.snapshot())
        } else {
            assertTrue(preview.valid);assertEquals(0,preview.createCount);assertEquals(1,preview.updateCount)
            assertEquals(number,preview.items.single().totalRegNo)
            val confirmation=preview.confirmation!!
            val result=r.imports.commit(rows,mode,"历史资料补录",confirmation.orgId,confirmation.validationHash)
            assertTrue(result.valid);assertTrue(result.createdIds.isEmpty());assertEquals(listOf("item-museum"),result.updatedIds)
            r.db {
                assertEquals(number,CollectionDao["item-museum"].totalRegNo)
                assertEquals("历史原号回导",CollectionDao["item-museum"].remark)
                assertEquals("EXISTING",CollectionDao["item-other"].totalRegNo)
                assertEquals("原藏品",CollectionDao["item-other"].name)
                if(operation=="coexisting-clean") {
                    assertEquals("另一件干净号码藏品",CollectionDao["clean-item"].name)
                    assertEquals("EXISTING",CollectionDao["clean-item"].totalRegNo)
                }
            }
        }
    }

    @Test fun `name based import resolves only current museum and preserves confirmation`() = fixture { r ->
        val rows = listOf(r.row().copy(categoryId = null, categoryName = "铜器", warehouseName = "保管库房"))
        val preview = r.imports.validate(rows)
        assertTrue(preview.valid, preview.errors.toString())
        assertEquals("category-museum", preview.items.single().categoryId)
        assertEquals("warehouse-museum", preview.items.single().warehouseId)
        val confirmation = preview.confirmation!!
        val committed = r.imports.commit(rows, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        assertTrue(committed.valid)
        r.db { assertEquals("category-museum", CollectionDao[committed.createdIds.single()].categoryId) }
    }

    @Test fun `name changes after preview and ambiguous names cannot silently redirect import`() = fixture { r ->
        val rows = listOf(r.row().copy(categoryId = null, categoryName = "铜器"))
        val confirmation = r.imports.validate(rows).confirmation!!
        r.db { CollectionCategoryDao["category-museum"].name = "更名分类" }
        val rejected = r.imports.commit(rows, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        assertFalse(rejected.valid)
        assertEquals("categoryName", rejected.errors.single().field)
        r.db {
            CollectionCategoryDao["category-museum"].apply { name = "瓷器"; code="03" }
        }
        val ambiguous = r.imports.validate(listOf(r.row().copy(categoryId = null, categoryName = "瓷器")))
        assertFalse(ambiguous.valid)
        assertTrue(ambiguous.errors.single().message.contains("重名"))
    }

    @Test fun `confirmed ordinary batch succeeds without rereading another target map`() = fixture { r ->
        val rows = listOf(r.row()); val confirmation = r.imports.validate(rows).confirmation!!
        assertEquals("museum", confirmation.orgId); assertEquals("museum", confirmation.orgName)
        assertEquals(64, confirmation.validationHash.length)
        val result = r.imports.commit(rows, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        assertTrue(result.valid); assertEquals(1, result.createdIds.size)
    }

    @ParameterizedTest @ValueSource(strings = ["ordinary", "word"])
    fun `current valid multi museum reassignment still rejects a previously confirmed target`(entry: String) = fixture("multi") { r ->
        val confirmation = if (entry == "ordinary") r.imports.validate(listOf(r.row())).confirmation!! else r.word.validate(r.file).confirmation!!
        r.db { UserDao["reader"].groupId = "other" }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) {
            if (entry == "ordinary") r.imports.commit(listOf(r.row()), expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
            else r.word.commit(r.file, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        }
        assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings = ["registration", "dynamic"])
    fun `Word confirmed file and parsed contents are fixed through commit`(kind: String) = fixture { r ->
        if (kind == "dynamic") { r.grant(CollectionConst.STORAGE); r.preview(EnumCollectionWordDocumentType.DYNAMIC_TRACKING) }
        val confirmation = r.word.validate(r.file).confirmation!!
        val changed = MockMultipartFile("file", "sample.docx", "application/octet-stream", byteArrayOf(9, 8, 7))
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.word.commit(changed, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash) }
        assertEquals(before, r.snapshot())
        assertTrue(r.word.commit(r.file, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash).valid)
    }

    @Test fun `Word registration confirmation detects changes to matched existing ledger`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
        val parsed = CollectionWordImportPreviewVo(valid = true, documentType = EnumCollectionWordDocumentType.REGISTRATION,
            sourceHash = "word-update", fingerprint = "format", registration = r.row("EXISTING", "瓷器"))
        doReturn(parsed).`when`(r.parser).parse(any(ByteArray::class.java) ?: byteArrayOf(), anyString() ?: "")
        val confirmation = r.word.validate(r.file, EnumCollectionImportMode.UPSERT, "原依据").confirmation!!
        r.db { CollectionDao["item-museum"].remark = "其他人更新" }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.word.commit(r.file, EnumCollectionImportMode.UPSERT, "原依据",
            expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash) }
        assertEquals(before, r.snapshot())
    }

    @Test fun `Word dynamic confirmation detects changes to its current matched collection`() = fixture { r ->
        r.grant(CollectionConst.STORAGE); r.preview(EnumCollectionWordDocumentType.DYNAMIC_TRACKING)
        val confirmation = r.word.validate(r.file).confirmation!!
        r.db { CollectionDao["item-museum"].remark = "当前保管情况已变化" }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.word.commit(r.file, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash) }
        assertEquals(before, r.snapshot())
    }

    @Test fun `create update and mixed batches require their current specific permissions before resolving files`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val update = r.row("EXISTING").copy(coverImage = "private-cover")
        assertThrows(BadRequestException::class.java) { r.imports.validate(listOf(update), EnumCollectionImportMode.UPSERT, "修订") }
        r.grant(CollectionConst.LEDGER_CORRECT)
        assertThrows(BadRequestException::class.java) { r.imports.validate(listOf(r.row().copy(coverImage = "private-cover"))) }
        assertThrows(BadRequestException::class.java) { r.imports.commit(listOf(r.row(), update), EnumCollectionImportMode.UPSERT, "修订") }
        verifyNoInteractions(r.binder)
    }

    @Test fun `current registrar imports mixed batch and logs updates while reserving new numbers`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
        val rows = listOf(r.row("EXISTING"), r.row().copy(categoryId = "category-museum", warehouseId = "warehouse-museum"))
        val validation = r.imports.validate(rows, EnumCollectionImportMode.UPSERT, "历史补录")
        assertTrue(validation.valid); assertEquals(1, validation.createCount); assertEquals(1, validation.updateCount)
        val result = r.imports.commit(rows, EnumCollectionImportMode.UPSERT, "历史补录")
        assertTrue(result.valid); assertEquals(1, result.createdIds.size); assertEquals(listOf("item-museum"), result.updatedIds)
        r.db {
            assertEquals("新藏品", CollectionDao["item-museum"].name); assertEquals("原藏品", CollectionDao["item-other"].name)
            val created = CollectionDao[result.createdIds.single()]
            assertEquals("museum", created.orgId); assertEquals(EnumCollectionStatus.PENDING, created.lifeStatus)
            assertEquals(1L, CollectionRegistrationNumberDao.count()); assertEquals(2L, CollectionCatalogDao.count())
            assertEquals(2L, CollectionLedgerLogDao.count())
        }
    }

    @ParameterizedTest @ValueSource(strings = ["USED", "RETIRED", "RETAINED"])
    fun `ordinary import preview rejects a number held by another collection`(stateName: String) = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        r.db {
            if (stateName == "RETAINED") {
                CollectionRetainedLedgerDao.new("retained-number") {
                    orgId = "museum"
                    totalRegNo = "OLD"
                    collectionId = "historical-owner"
                    transferId = "historical-transfer"
                    targetOrgId = "other"
                    name = "原馆历史藏品"
                    archiveId = "historical-archive"
                    snapshot = CollectionRetainedLedgerSnapshot(CollectionVo())
                }
            } else {
                CollectionRegistrationNumberDao.new("occupied-number") {
                    orgId = "museum"
                    totalRegNo = "OLD"
                    collectionId = "historical-owner"
                    state = EnumRegistrationNoState.valueOf(stateName)
                    reason = "历史总账占号"
                }
            }
        }
        val rows = listOf(r.row("OLD"))
        val preview = r.imports.validate(rows)
        assertFalse(preview.valid)
        assertTrue(preview.errors.any { it.field == "totalRegNo" && it.message.contains("已被占用") })
        val before = r.snapshot()
        val commit = r.imports.commit(rows)
        assertFalse(commit.valid)
        assertTrue(commit.errors.any { it.field == "totalRegNo" && it.message.contains("已被占用") })
        assertEquals(before, r.snapshot(), "导入拒绝后主档、占号、编目及留痕都不得变化")
    }

    @Test fun `ordinary import rechecks a number retired after its preview`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val rows = listOf(r.row("LATER"))
        val preview = r.imports.validate(rows)
        assertTrue(preview.valid)
        val confirmation = preview.confirmation!!
        r.db {
            CollectionRegistrationNumberDao.new("later-retired") {
                orgId = "museum"
                totalRegNo = "LATER"
                collectionId = "historical-owner"
                state = EnumRegistrationNoState.RETIRED
                reason = "预校验后登记的历史退出号"
            }
        }
        val before = r.snapshot()
        val result = r.imports.commit(rows, expectedOrgId = confirmation.orgId,
            expectedValidationHash = confirmation.validationHash)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.field == "totalRegNo" && it.message.contains("已被占用") })
        assertEquals(before, r.snapshot())
    }

    @Test @org.junit.jupiter.api.Tag("postgres-concurrency")
    fun `concurrent automatic number imports with one confirmation create exactly one batch`() = fixture { r ->
        check((System.getProperty("collection.test.import.pg.config") ?: System.getProperty("collection.test.pg.config")) != null) { "Requires isolated PostgreSQL" }
        val rows = listOf(r.row().copy(totalRegNo = null))
        val confirmation = r.imports.validate(rows).confirmation!!
        val authentication = SecurityContextHolder.getContext().authentication
        val ready = java.util.concurrent.CountDownLatch(2)
        val start = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit(java.util.concurrent.Callable {
                    SecurityContextHolder.getContext().authentication = authentication
                    try {
                        ready.countDown()
                        check(start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        runCatching { r.imports.commit(rows, expectedOrgId = confirmation.orgId,
                            expectedValidationHash = confirmation.validationHash) }
                    } finally { SecurityContextHolder.clearContext() }
                })
            }
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); start.countDown()
            val results = futures.map { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(1, results.count { it.isSuccess })
            val rejected = results.single { it.isFailure }.exceptionOrNull()!!
            assertTrue(rejected is BadRequestException, rejected.toString())
            assertTrue(rejected.message!!.contains("重新预校验"))
            val result = results.single { it.isSuccess }.getOrThrow()
            assertTrue(result.valid); assertEquals(1, result.createdIds.size)
            r.db {
                assertEquals(3L, CollectionDao.count()) // two original fixtures plus one import
                assertEquals(1L, CollectionRegistrationNumberDao.count())
                assertEquals(1L, CollectionCatalogDao.count())
                assertEquals(1L, CollectionLedgerLogDao.count())
                assertEquals(EnumCollectionStatus.PENDING, CollectionDao[result.createdIds.single()].lifeStatus)
            }
        } finally { start.countDown(); pool.shutdownNow() }
    }

    @Test fun `automatic number import confirmation cannot create a second batch after success`() = fixture { r ->
        val rows = listOf(r.row().copy(totalRegNo = null))
        val confirmation = r.imports.validate(rows).confirmation!!
        val result = r.imports.commit(rows, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        assertTrue(result.valid); assertEquals(1, result.createdIds.size)
        val before = r.snapshot()
        val failure = assertThrows(BadRequestException::class.java) {
            r.imports.commit(rows, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        }
        assertTrue(failure.message!!.contains("重新预校验"))
        assertEquals(before, r.snapshot())
    }

    @Test fun `failed later update rolls back earlier new row catalog number and log in actual Spring transaction`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
        r.db { CollectionDao["item-museum"].registeredAt = java.time.LocalDateTime.now() }
        val rows = listOf(r.row(), r.row("EXISTING"))
        val validation = r.imports.validate(rows, EnumCollectionImportMode.UPSERT, "修订")
        assertTrue(validation.valid)
        assertEquals(1, validation.createCount); assertEquals(1, validation.updateCount)
        val confirmation = validation.confirmation!!
        val before = r.snapshot()
        repeat(2) {
            val failure = assertThrows(BadRequestException::class.java) {
                r.imports.commit(rows, EnumCollectionImportMode.UPSERT, "修订", confirmation.orgId, confirmation.validationHash)
            }
            assertTrue(failure.message!!.contains("已登记或审核的藏品"), "必须到达后项正式记录保护，不能将预校验或权限拒绝误作回滚")
            assertEquals(before, r.snapshot())
        }
        // 回滚后原先拟占用的新号仍可通过正常导入使用。
        val retryRows = listOf(r.row())
        val retryConfirmation = r.imports.validate(retryRows).confirmation!!
        val result = r.imports.commit(retryRows, expectedOrgId = retryConfirmation.orgId,
            expectedValidationHash = retryConfirmation.validationHash)
        assertTrue(result.valid); assertEquals(1, result.createdIds.size)
        r.db {
            assertEquals("NEW", CollectionDao[result.createdIds.single()].totalRegNo)
            assertEquals(1L, CollectionRegistrationNumberDao.count())
            assertEquals(1L, CollectionCatalogDao.count())
            assertEquals(1L, CollectionLedgerLogDao.count())
        }
    }

    @ParameterizedTest @ValueSource(strings = ["moved", "revoked"])
    fun `revoke or move during file normalization cannot reach internal registration`(change: String) = fixture("multi") { r ->
        doAnswer {
            if (change == "moved") UserDao["reader"].groupId = "other" else RolePermissions.deleteAll()
            "resolved-cover"
        }.`when`(r.binder).resolveSingle("cover")
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.imports.commit(listOf(r.row().copy(coverImage = "cover"))) }
        assertEquals(before, r.snapshot())
    }

    @Test fun `ambiguous historical registration number is never resolved by picking one row`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
        r.db {
            // 仅此隔离夹具模拟未完成唯一索引升级的旧库，正常新库仍保留数据库唯一约束。
            val index = Collections.indices.single { it.columns == listOf(Collections.orgId, Collections.totalRegNo) }.indexName
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP INDEX \"" + index.replace("\"", "\"\"") + "\"")
            CollectionDao.new("duplicate") { orgId = "museum"; totalRegNo = "EXISTING"; name = "另一件历史藏品" }
        }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.imports.validate(listOf(r.row("EXISTING")), EnumCollectionImportMode.UPSERT, "补录") }
        assertThrows(BadRequestException::class.java) { r.imports.commit(listOf(r.row("EXISTING")), EnumCollectionImportMode.UPSERT, "补录") }
        assertEquals(before, r.snapshot())
    }

    @Test fun `Word parser cannot move the import target during a single submission`() = fixture("multi") { r ->
        doAnswer {
            UserDao["reader"].groupId = "other"
            CollectionWordImportPreviewVo(valid = true, documentType = EnumCollectionWordDocumentType.REGISTRATION,
                registration = r.row(category = "瓷器"), sourceHash = "moved", fingerprint = "format")
        }.`when`(r.parser).parse(any(ByteArray::class.java) ?: byteArrayOf(), anyString() ?: "")
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.word.commit(r.file) }
        assertEquals(before, r.snapshot())
    }

    @Test fun `actual administrator needs a ledger duty and incompatible keeper still rejects imports`() = fixture { r ->
        r.db {
            Roles.insert { it[Roles.id] = EnumRole.ADMIN.value; it[code] = "ACTUAL_ADMIN"; it[name] = "管理员" }
            RoleUsers.insert { it[userId] = "reader"; it[roleId] = EnumRole.ADMIN.value }
        }
        r.grant(); assertThrows(BadRequestException::class.java) { r.imports.commit(listOf(r.row())) }
        r.db { actorRole("reader", CollectionConst.ROLE_LEDGER_CUSTODIAN) }
        assertTrue(r.imports.commit(listOf(r.row())).valid)
        r.damage("keeper"); assertThrows(BadRequestException::class.java) { r.imports.validate(listOf(r.row("SECOND"))) }
    }

    @Test fun `Word registration and history use current multi museum assignment consistently`() = fixture("multi") { r ->
        r.db { UserDao["reader"].groupId = "other" }
        val result = r.word.commit(r.file)
        assertTrue(result.valid)
        r.db {
            assertEquals("other", CollectionDao[result.collectionId!!].orgId)
            val record = CollectionWordImportDao[result.importId!!]
            assertEquals("other", record.orgId); assertEquals(result.collectionId, record.collectionId)
            assertEquals("uploaded-word", record.sourceFileId)
            assertEquals("other", CollectionTrackingEventDao.find { CollectionTrackingEvents.importId eq result.importId!! }.single().orgId)
        }
        r.grant(CollectionConst.SELECT)
        assertEquals(1, r.word.history(result.collectionId!!).imports.size)
        assertThrows(BadRequestException::class.java) { r.word.history("item-museum") }
    }

    @Test fun `dynamic Word requires current storage permission independently of ledger duties`() = fixture { r ->
        r.preview(EnumCollectionWordDocumentType.DYNAMIC_TRACKING)
        assertThrows(BadRequestException::class.java) { r.word.validate(r.file) }
        r.grant(CollectionConst.STORAGE)
        val result = r.word.commit(r.file); assertTrue(result.valid); assertEquals("item-museum", result.collectionId)
        assertFalse(result.createdCollection); assertFalse(result.updatedCollection)
        r.db {
            val original = CollectionAttachmentDao.all().single()
            assertEquals("uploaded-word", original.fileId); assertEquals(result.importId, original.bizId)
            assertEquals("museum", original.orgId); assertEquals("item-museum", original.collectionId)
            assertEquals(1L, FileContents.selectAll().count())
            val movement = StorageMovementDao.all().single()
            assertEquals("museum", movement.orgId); assertEquals("item-museum", movement.collectionId)
            assertEquals(result.importId, movement.refBizId); assertEquals("warehouse-museum", movement.toWarehouseId)
            assertEquals(EnumCollectionStatus.PENDING, CollectionDao["item-museum"].lifeStatus)
        }
        r.grant(CollectionConst.LEDGER_CREATE)
        assertThrows(BadRequestException::class.java) { r.word.history("item-museum") }
    }

    @ParameterizedTest @ValueSource(strings = ["ledger_permission", "ledger_role", "admin_without_storage_duty"])
    fun `dynamic import enforces current storage separation even when old login had administrator rights`(kind: String) = fixture { r ->
        r.preview(EnumCollectionWordDocumentType.DYNAMIC_TRACKING)
        r.grant(CollectionConst.STORAGE)
        r.db { when (kind) {
            "ledger_permission" -> actorRole("reader", "LEDGER", setOf(CollectionConst.LEDGER_CREATE))
            "ledger_role" -> actorRole("reader", CollectionConst.ROLE_LEDGER_CUSTODIAN)
            else -> { RolePermissions.deleteAll(); actorRole("reader", "C_ADMIN", setOf(CollectionConst.ADMIN)) }
        } }
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.word.validate(r.file) }
        assertThrows(BadRequestException::class.java) { r.word.commit(r.file) }
        assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings = ["revoked", "disabled", "moved", "configured", "museum_disabled", "ledger"])
    fun `direct historical movement writer uses current permission scope and storage separation`(kind: String) = fixture { r ->
        r.grant(CollectionConst.STORAGE)
        if (kind == "ledger") r.db { actorRole("reader", "LEDGER", setOf(CollectionConst.LEDGER_CREATE)) } else r.damage(kind)
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) {
            r.c.getBean(StorageMovementService::class.java).createHistoricalImport("item-museum", "history-museum", 1,
                CollectionWordMovementPreviewVo(2, java.time.LocalDateTime.of(2020, 1, 1, 9, 0), EnumMoveType.IN))
        }
        assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings = ["revoked", "disabled", "moved", "configured", "museum_disabled"])
    fun `Word history checks current read authority and museum despite old admin login`(damage: String) = fixture { r ->
        r.grant(CollectionConst.SELECT); r.damage(damage)
        if (damage == "moved") r.grant(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { r.word.history("item-museum") }
    }

    @Test fun `foreign history is filtered and local event with mismatched original import is rejected`() = fixture { r ->
        r.grant(CollectionConst.SELECT)
        r.db {
            CollectionWordImportDao["history-other"].collectionId = "item-museum"
            CollectionTrackingEventDao["event-other"].collectionId = "item-museum"
        }
        val history = r.word.history("item-museum")
        assertEquals(listOf("history-museum"), history.imports.map { it.id }); assertEquals(listOf("event-museum"), history.events.map { it.id })
        r.db { CollectionTrackingEventDao["event-museum"].importId = "history-other" }
        assertThrows(BadRequestException::class.java) { r.word.history("item-museum") }
    }
    @Test fun `Word import after current reassignment writes detailed catalog through its ledger authorized internal path`() = fixture("multi") { r ->
        r.db { UserDao["reader"].groupId = "other" }
        val parsed = r.parser.parse(r.file.bytes, "sample.docx").copy(catalog = CollectionCatalogContentVo(formalName = "Word 详细著录", researchNote = "原件研究记录"))
        doReturn(parsed).`when`(r.parser).parse(any(ByteArray::class.java) ?: byteArrayOf(), anyString() ?: "")
        val confirmation = r.word.validate(r.file).confirmation!!
        val result = r.word.commit(r.file, expectedOrgId = confirmation.orgId, expectedValidationHash = confirmation.validationHash)
        assertTrue(result.valid)
        r.db {
            val catalog = CollectionCatalogDao.find { CollectionCatalogs.collectionId eq result.collectionId!! }.single()
            assertEquals("other", catalog.orgId); assertEquals("Word 详细著录", catalog.content.formalName)
            assertEquals("原件研究记录", catalog.content.researchNote)
            assertEquals(EnumCollectionStatus.PENDING, CollectionDao[result.collectionId!!].lifeStatus)
        }
    }

    @ParameterizedTest @ValueSource(strings=["registration","dynamic"])
    fun `current permission revoked during Word upload rolls back import records and original binding`(kind:String)=fixture(onUpload={it.grant()}) {r ->
        if(kind=="dynamic") {r.grant(CollectionConst.STORAGE);r.preview(EnumCollectionWordDocumentType.DYNAMIC_TRACKING)}
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.word.commit(r.file)}
        assertEquals(before,r.snapshot())
    }

}
