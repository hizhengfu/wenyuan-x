package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.sys.system.dao.DictDao
import io.hizhengfu.cloud.sys.system.dao.Dicts
import io.hizhengfu.cloud.sys.system.dao.DictTypeDao
import io.hizhengfu.cloud.sys.system.dao.DictTypes
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionBusinessTerminologyUpgradeTest {
    @TempDir lateinit var root: Path

    @Test
    fun `upgrade preserves dictionary identities custom labels and historical reasons on repeat`() {
        val source = SQLiteDataSource().apply { url = "jdbc:sqlite:${root.resolve("labels.db")}" }
        val tx = TransactionTemplate(SpringTransactionManager(source, DatabaseConfig {}))
        tx.executeWithoutResult {
            SchemaUtils.create(Dicts, DictTypes)
            listOf(
                Triple("life", "col_life_status", "DEREGISTERED" to "已注销"),
                Triple("approval", "col_attachment_type", "DEREG_APPROVAL" to "注销批文"),
                Triple("action", "col_ledger_action", "DEREGISTER" to "注销"),
                Triple("print", "col_print_type", "DEREGISTRATION" to "注销审批表"),
                Triple("history", "col_dereg_reason", "TRANSFER" to "调拨注销（历史）"),
                Triple("custom", "col_print_type", "DEREGISTRATION" to "本馆退出记录单"),
                Triple("rfid", "col_rfid_operator_card_status", "REVOKED" to "已注销"),
                Triple("local", "col_life_status", "DEREGISTERED" to "已注销"),
            ).forEach { (id, kind, entry) -> DictDao.new(id) {
                type = kind; value = entry.first; label = entry.second
                isSys = id != "local"; enabled = id != "approval"; sort = 17
            } }
            DictTypeDao.new("reason-kind") { type = "col_dereg_reason"; name = "注销原因"; remarks = "注销原因"; isSys = true }
            CollectionBusinessTerminologyUpgrade.apply()
            CollectionBusinessTerminologyUpgrade.apply()
            assertEquals("已退出馆藏", DictDao["life"].label)
            assertEquals("退出馆藏批准文件", DictDao["approval"].label)
            assertEquals(false, DictDao["approval"].enabled)
            assertEquals(17, DictDao["approval"].sort)
            assertEquals("退出馆藏", DictDao["action"].label)
            assertEquals("退出馆藏审批表", DictDao["print"].label)
            assertEquals("调拨注销（历史）", DictDao["history"].label)
            assertEquals("本馆退出记录单", DictDao["custom"].label)
            assertEquals("已注销", DictDao["rfid"].label)
            assertEquals("已注销", DictDao["local"].label)
            assertEquals("退出馆藏原因", DictTypeDao["reason-kind"].name)
            assertEquals(8L, DictDao.all().count())
        }
    }
}
