package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogs
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
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
import java.time.LocalDateTime

class CollectionLegacyRegistrationLogAuditTest {
    @TempDir lateinit var root: Path

    private fun fixture(): TransactionTemplate {
        val source = SQLiteDataSource().apply { url = "jdbc:sqlite:${root.resolve("legacy-register.db")}" }
        val tx = TransactionTemplate(SpringTransactionManager(source, DatabaseConfig {}))
        tx.executeWithoutResult {
            SchemaUtils.create(Collections, CollectionLedgerLogs)
            CollectionDao.new("draft") {
                orgId = "museum"; totalRegNo = "001"; name = "历史草稿"
                catalogStatus = EnumCatalogStatus.DRAFT
            }
            CollectionDao.new("formal") {
                orgId = "museum"; totalRegNo = "002"; name = "正式登记"
                registeredAt = LocalDateTime.of(2020, 1, 1, 12, 0)
                catalogStatus = EnumCatalogStatus.APPROVED
            }
            CollectionLedgerLogDao.new("legacy") {
                orgId = "museum"; collectionId = "draft"; totalRegNo = "001"
                action = EnumLedgerAction.REGISTER; after = "001"; reason = "登记入账"
            }
            CollectionLedgerLogDao.new("valid") {
                orgId = "museum"; collectionId = "formal"; totalRegNo = "002"
                action = EnumLedgerAction.REGISTER; after = "002"; reason = "正式登记"
            }
            CollectionLedgerLogDao.new("reserved") {
                orgId = "museum"; collectionId = "draft"; totalRegNo = "001"
                action = EnumLedgerAction.RESERVE_REG_NO; after = "001"; reason = "预留登记号"
            }
        }
        return tx
    }

    @Test fun `legacy register claim is annotated once without changing original or draft`() {
        val tx = fixture()
        val install = CollectionInstall("multi")
        tx.executeWithoutResult { install.annotateUnverifiedLegacyRegistrationLogs() }
        tx.executeWithoutResult { install.annotateUnverifiedLegacyRegistrationLogs() }
        tx.executeWithoutResult {
            val original = CollectionLedgerLogDao["legacy"]
            assertEquals(EnumLedgerAction.REGISTER, original.action)
            assertEquals("登记入账", original.reason)
            assertNull(original.refBizId)
            assertNull(CollectionDao["draft"].registeredAt)
            assertEquals(EnumCatalogStatus.DRAFT, CollectionDao["draft"].catalogStatus)

            val notes = CollectionLedgerLogDao.all().filter { it.field == "legacyRegistrationClaim" }
            assertEquals(1, notes.size)
            val note = notes.single()
            assertEquals(EnumLedgerAction.CORRECT, note.action)
            assertEquals("REGISTER", note.before)
            assertEquals("UNVERIFIED", note.after)
            assertEquals("legacy", note.refBizId)
            assertEquals("SYSTEM_COLLECTION_INSTALL", note.createBy)
            assertEquals(4L, CollectionLedgerLogDao.all().count())
        }
    }

    @Test fun `failed upgrade transaction does not leave a partial annotation`() {
        val tx = fixture()
        val install = CollectionInstall("multi")
        assertThrows(IllegalStateException::class.java) {
            tx.executeWithoutResult {
                install.annotateUnverifiedLegacyRegistrationLogs()
                throw IllegalStateException("模拟后续升级失败")
            }
        }
        tx.executeWithoutResult { assertEquals(3L, CollectionLedgerLogDao.all().count()) }
        tx.executeWithoutResult { install.annotateUnverifiedLegacyRegistrationLogs() }
        tx.executeWithoutResult { assertEquals(4L, CollectionLedgerLogDao.all().count()) }
    }
}
