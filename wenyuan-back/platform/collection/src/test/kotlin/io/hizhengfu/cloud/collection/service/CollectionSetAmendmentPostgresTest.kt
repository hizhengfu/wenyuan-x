package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionChangeDao
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.dao.CollectionSetDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 相同服务场景在真实PG事务、JSON和微秒时间列上执行；每个用例独立schema。 */
@Tag("postgres-concurrency")
class CollectionSetAmendmentPostgresTest : CollectionSetAmendmentPersistenceTest() {
    private var source: PGSimpleDataSource? = null
    override fun connectDatabase(): Database {
        val props = Properties().apply {
            Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load)
        }
        val ds = PGSimpleDataSource().apply {
            setURL(props.getProperty("url")); user = props.getProperty("user"); password = props.getProperty("password")
            currentSchema = "set_amendment_" + UUID.randomUUID().toString().replace("-", "")
        }
        ds.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA ${ds.currentSchema}") } }
        source = ds
        return Database.connect(ds)
    }
    override fun closeDatabase() {
        source?.let { ds -> ds.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA ${ds.currentSchema} CASCADE") } } }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `information change waits for collection lock and uses committed ledger duty`(rollback: Boolean) {
        val id = preparedApprovedChange()
        val ds = source ?: error("PostgreSQL fixture was not initialized")
        val context = SecurityContextHolder.getContext()
        val executor = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val waiterName = "change-lock-" + UUID.randomUUID().toString().take(8)
        fun <T> withUser(block: () -> T): T {
            SecurityContextHolder.setContext(context)
            try { return block() } finally { SecurityContextHolder.clearContext() }
        }
        val first = executor.submit<Result<String>> {
            withUser { runCatching { transaction(testDatabase()) {
                CollectionDao.find { Collections.id eq "object-1" }.forUpdate().single()
                RolePermissions.deleteWhere { RolePermissions.permissionId eq CollectionConst.LEDGER_CORRECT }
                entered.countDown()
                check(release.await(15, TimeUnit.SECONDS)) { "Lock holder timed out" }
                if (rollback) error("rollback ledger revocation")
                "revoked"
            } } }
        }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS), "Lock holder did not reach the row lock")
            val second = executor.submit<Result<String>> {
                withUser { runCatching { transaction(testDatabase()) {
                    exec("SET application_name = '$waiterName'")
                    executeApprovedChange(id)
                } } }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var waiting = false
            while (System.nanoTime() < deadline && !waiting) {
                waiting = ds.connection.use { connection ->
                    connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock')").use { statement ->
                        statement.setString(1, waiterName)
                        statement.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
                    }
                }
                if (!waiting) {
                    assertFalse(second.isDone, "Execution completed before the holder released its collection lock")
                    Thread.sleep(20)
                }
            }
            assertTrue(waiting, "Execution did not reach an observed PostgreSQL lock wait")
            release.countDown()
            val revocation = first.get(15, TimeUnit.SECONDS)
            val execution = second.get(15, TimeUnit.SECONDS)
            if (rollback) {
                assertEquals("rollback ledger revocation", revocation.exceptionOrNull()?.message)
                assertEquals(id, execution.getOrThrow())
            } else {
                assertEquals("revoked", revocation.getOrThrow())
                assertTrue(execution.exceptionOrNull() is BadRequestException, "Revoked registrar should not execute: $execution")
            }
            transaction(testDatabase()) {
                assertEquals(rollback, CollectionChangeDao[id].appliedAt != null)
                assertEquals("旧定名", CollectionDao["object-1"].name)
                assertEquals(if (rollback) "订正分件" else "原分件", CollectionSetDao["relation-1"].partName)
                assertEquals(if (rollback) 2L else 0L, CollectionLedgerLogDao.all().count())
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS))
        }
    }
}
