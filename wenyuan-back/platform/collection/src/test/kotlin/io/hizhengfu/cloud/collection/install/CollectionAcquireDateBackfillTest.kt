package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogs
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate

class CollectionAcquireDateBackfillTest {
    @TempDir lateinit var root: Path

    private fun fixture(): TransactionTemplate {
        val source = SQLiteDataSource().apply { url = "jdbc:sqlite:${root.resolve("backfill.db")}" }
        val tx = TransactionTemplate(SpringTransactionManager(source, DatabaseConfig {}))
        tx.executeWithoutResult {
            SchemaUtils.create(Collections, CollectionLedgerLogs)
            CollectionDao.new("missing-both") {
                orgId = "museum"
                totalRegNo = "001"
                name = "合成藏品一"
                acquireDate = LocalDate.of(2020, 5, 6)
            }
            CollectionDao.new("missing-year") {
                orgId = "museum"
                totalRegNo = "002"
                name = "合成藏品二"
                acquireDate = LocalDate.of(2021, 7, 8)
                acquireDateRange = "原记录范围"
            }
            CollectionDao.new("without-date") {
                orgId = "museum"
                totalRegNo = "003"
                name = "无精确日期藏品"
            }
        }
        return tx
    }

    @Test
    fun `backfill records each changed field and is idempotent`() {
        val tx = fixture()
        val install = CollectionInstall("multi")
        tx.executeWithoutResult { install.backfillAcquireDateRanges() }
        tx.executeWithoutResult { install.backfillAcquireDateRanges() }

        tx.executeWithoutResult {
            assertEquals("2020-05-06", CollectionDao["missing-both"].acquireDateRange)
            assertEquals("2020", CollectionDao["missing-both"].acquireYear)
            assertEquals("原记录范围", CollectionDao["missing-year"].acquireDateRange)
            assertEquals("2021", CollectionDao["missing-year"].acquireYear)
            assertNull(CollectionDao["without-date"].acquireDateRange)
            assertNull(CollectionDao["without-date"].acquireYear)

            val logs = CollectionLedgerLogDao.all().map {
                Triple(it.collectionId, it.field, it.after) to it
            }
            assertEquals(
                setOf(
                    Triple("missing-both", "acquireDateRange", "2020-05-06"),
                    Triple("missing-both", "acquireYear", "2020"),
                    Triple("missing-year", "acquireYear", "2021"),
                ),
                logs.map { it.first }.toSet(),
            )
            assertEquals(3, logs.size)
            logs.forEach { (_, log) ->
                assertEquals(EnumLedgerAction.CORRECT, log.action)
                assertEquals("museum", log.orgId)
                assertNull(log.before)
                assertEquals("SYSTEM_COLLECTION_INSTALL", log.createBy)
            }
        }
    }

    @Test
    fun `failed audit write rolls back the historical field update`() {
        val tx = fixture()
        val install = CollectionInstall("multi")
        tx.executeWithoutResult { SchemaUtils.drop(CollectionLedgerLogs) }

        assertThrows(Exception::class.java) {
            tx.executeWithoutResult { install.backfillAcquireDateRanges() }
        }
        tx.executeWithoutResult {
            assertNull(CollectionDao["missing-both"].acquireDateRange)
            assertNull(CollectionDao["missing-both"].acquireYear)
        }

        tx.executeWithoutResult { SchemaUtils.create(CollectionLedgerLogs) }
        tx.executeWithoutResult { install.backfillAcquireDateRanges() }
        tx.executeWithoutResult { assertEquals(3L, CollectionLedgerLogDao.all().count()) }
    }
}
