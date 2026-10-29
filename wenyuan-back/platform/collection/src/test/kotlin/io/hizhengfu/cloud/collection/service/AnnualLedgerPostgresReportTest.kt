package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.anyString
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID

/** An isolated PostgreSQL schema proves the nonzero, multi-unit signed-report path. */
@Tag("postgres-concurrency")
class AnnualLedgerPostgresReportTest {
    @Test fun `nonzero multi unit year is signed submitted and filed from historical ledger events`() {
        val properties = Properties().apply {
            Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)
        }
        val admin = PGSimpleDataSource().apply {
            setURL(properties.getProperty("url"))
            user = properties.getProperty("user")
            password = properties.getProperty("password")
        }
        val schema = "annual_nonzero_" + UUID.randomUUID().toString().replace("-", "")
        admin.connection.use { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val testDataSource = PGSimpleDataSource().apply {
            setURL(properties.getProperty("url"))
            user = properties.getProperty("user")
            password = properties.getProperty("password")
            currentSchema = schema
        }
        val context = StaticApplicationContext()
        try {
            context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            context.refresh()
            SpringContextHolder().setApplicationContext(context)
            val user = SecurityUser(id = "statistician", name = "统计经办", username = "statistician", password = "",
                groupId = "museum", groupIds = setOf("museum"), postIds = emptySet(), dataType = 0,
                roles = emptySet(), authorities = listOf(SimpleGrantedAuthority(CollectionConst.STAT)), enabled = true)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
            val db = Database.connect(testDataSource)
            transaction(db) {
                originalReaderSchema()
                originalReader("statistician", CollectionConst.STAT)
                SchemaUtils.create(Collections, CollectionOrgs, CollectionRetainedLedgers, CollectionLedgerStatEvents,
                    CollectionLedgerLogs, CollectionMedias, CollectionAttachments, CollectionCategories, Warehouses,
                    CollectionOperationLocks, AnnualReportEvidenceRecords, AnnualReportArchives,
                    CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files)
                CollectionOrgDao.new("museum") { name = "合成源馆"; code = "M"; systemGroupId = "museum" }
                CollectionOrgDao.new("target") { name = "合成接收馆"; code = "T"; systemGroupId = "target" }
                listOf("sign", "submission", "filing").forEach { id ->
                    FileDao.new(id) { name = "$id.pdf"; path = "/files/$id.pdf"; hash = id; size = 100; ext = "pdf" }
                }
                fun item(id: String, unitName: String, count: Int, registered: LocalDateTime) = CollectionDao.new(id) {
                    orgId = "museum"; totalRegNo = id; name = "合成藏品$id"
                    nature = EnumCollectionNature.OTHER; level = EnumRelicLevel.UNRATED
                    source = EnumSource.DONATE; quantity = count; unit = unitName
                    registeredAt = registered; catalogStatus = EnumCatalogStatus.APPROVED
                    lifeStatus = EnumCollectionStatus.IN_STORE
                }
                val first = item("piece-1", "件", 2, LocalDateTime.of(2025, 3, 1, 10, 0))
                val moved = item("piece-2", "套", 1, LocalDateTime.of(2025, 4, 1, 10, 0))
                val retained = item("piece-3", "套", 1, LocalDateTime.of(2025, 5, 1, 10, 0))
                listOf(first, moved, retained).forEach { row ->
                    CollectionLedgerStatRecorder.record(row, EnumLedgerStatEvent.REGISTER, "register-${row.id.value}", row.registeredAt!!)
                }
                first.quantity = 3
                CollectionLedgerStatRecorder.record(first, EnumLedgerStatEvent.CHANGE, "quantity-change", LocalDateTime.of(2025, 6, 1, 10, 0))
                CollectionLedgerStatRecorder.record(moved, EnumLedgerStatEvent.TRANSFER_OUT, "transfer", LocalDateTime.of(2025, 7, 1, 10, 0))
                moved.orgId = "target"
                CollectionLedgerStatRecorder.record(moved, EnumLedgerStatEvent.TRANSFER_IN, "transfer", LocalDateTime.of(2025, 7, 2, 10, 0))

                val scope = CollectionScopeResolver("multi", "")
                val statistics = CollectionLedgerStatisticsService(scope)
                val preview = statistics.preview(2025)
                assertTrue(preview.completeYear)
                assertTrue(preview.issues.isEmpty())
                assertEquals(listOf(0, 3, 0, 1, 0, 2), listOf(preview.openingTotal, preview.newTotal,
                    preview.transferInTotal, preview.transferOutTotal, preview.deregTotal, preview.closingTotal))
                assertEquals(mapOf("件" to 3L, "套" to 1L), preview.quantityBalances.associate { it.unit to it.closing })
                val figures = AnnualDeclaredFiguresVo(0, 3, 0, 1, 0, 2,
                    listOf(AnnualDeclaredUnitVo("件", 0, 2, 0, 0, 0, 1, 3),
                        AnnualDeclaredUnitVo("套", 0, 2, 0, 1, 0, 0, 1)),
                    "合成新增三条、跨馆调出一条，件数订正增加一件")
                assertEquals(figures.quantities.map { it.balance() }.sortedBy { it.unit }, preview.quantityBalances.sortedBy { it.unit })
                val binder = mock(BusinessAttachmentBinder::class.java).also {
                    `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0) }
                }
                val archives = CollectionPermanentArchiveService(binder, scope, readAccess = unprotectedArchiveReadAccessProvider())
                val reports = AnnualReportEvidenceService(scope, statistics, mock(CollectionResponsibleUserService::class.java), archives)
                val signedAt = LocalDateTime.of(2026, 1, 2, 12, 0)
                val signed = AnnualReportSignedVo(2025, "statistician", "head", "leader", signedAt, "sign", figures)
                val id = reports.signed(signed, "museum")
                val submission = AnnualReportSubmissionVo("合成主管部门", "DEV-2025-REPORT", signedAt.plusDays(1), "submission")
                val filing = AnnualReportFilingVo("合成主管部门", "DEV-2025-FILING", signedAt.plusDays(2), "filing")
                assertEquals(id, reports.submission(id, submission))
                assertEquals(id, reports.filing(id, filing))
                assertEquals(id, reports.signed(signed, "museum"))
                assertEquals(id, reports.submission(id, submission))
                assertEquals(id, reports.filing(id, filing))
                assertEquals(1L, AnnualReportEvidenceDao.count())
                val frozen = AnnualReportEvidenceDao[id].calculated
                assertEquals(preview.copy(events = emptyList()), frozen.copy(events = emptyList()))
                assertEquals(preview.events.map { it.id to it.item }, frozen.events.map { it.id to it.item })
                assertEquals(setOf("SIGNED", "SUBMITTED", "FILED"), CollectionPermanentArchiveDao.all()
                    .filter { it.bizType == AnnualReportEvidenceService.BIZ && it.bizId == id }.map { it.phase }.toSet())
                val detail = reports.detail(2025)
                assertEquals(1, (detail["records"] as List<*>).size)
            }
        } finally {
            SecurityContextHolder.clearContext()
            context.close()
            admin.connection.use { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}
