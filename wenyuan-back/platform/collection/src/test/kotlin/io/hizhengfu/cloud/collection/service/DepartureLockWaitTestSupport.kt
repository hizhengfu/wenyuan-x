package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Assertions.*
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** Every PostgreSQL case owns its schema; the development public schema is never written. */
internal fun departureTestDatabase(path: Path, block: (DataSource) -> Unit) {
    val schema = "departure_lock_" + UUID.randomUUID().toString().replace("-", "")
    val source = System.getProperty("collection.test.pg.config")?.let { name ->
        val config = Properties().apply { Path.of(name).toFile().inputStream().use(::load) }
        PGSimpleDataSource().apply {
            setURL(config.getProperty("url")); user = config.getProperty("user"); password = config.getProperty("password")
            connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
            currentSchema = schema
            applicationName = schema
        }
    } ?: SQLiteDataSource().apply { url = "jdbc:sqlite:$path" }
    try { block(source) } finally {
        if (source is PGSimpleDataSource) source.connection.use {
            it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") }
        }
    }
}

/** Includes personnel, roles, inventories, originals and warehouse facts. Coordination counters are separate. */
internal fun departureDeclaredState() = listOf(Groups, Users, UserGroups, Roles, RoleUsers, Permissions,
    RolePermissions, CollectionOrgs, Collections, Warehouses, Loans, Requisitions, CollectionReturns,
    InventoryBatches, Inventories, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files).associate { table ->
    table.tableName to table.selectAll().map { row ->
        table.columns.filter { it != CollectionOrgs.locationMutationVersion }.map { row[it] }.toString()
    }.sorted()
}

/** The observer uses a fresh autocommit connection, so a cached pg_stat_activity snapshot cannot fake a wait. */
internal fun assertDepartureLockWait(source: DataSource, actor: SecurityUser, change: String, commit: Boolean,
    label: String, operation: () -> String, state: () -> Map<String, List<String>>, login: () -> Unit) {
    assertInstanceOf(PGSimpleDataSource::class.java, source)
    val pg = source as PGSimpleDataSource
    val before = state()
    val pool = Executors.newSingleThreadExecutor()
    val held = source.connection.apply { autoCommit = false }
    val mutation = when (change) {
        "account" -> "UPDATE sys_user SET enabled=false WHERE id='${actor.id}'"
        "role" -> "UPDATE sys_role SET enabled=false WHERE id IN (SELECT role_id FROM sys_role_user WHERE user_id='${actor.id}')"
        "museum" -> "UPDATE sys_user SET group_id='departed-museum' WHERE id='${actor.id}'"
        else -> error(change)
    }
    val restore = when (change) {
        "account" -> "UPDATE sys_user SET enabled=true WHERE id='${actor.id}'"
        "role" -> "UPDATE sys_role SET enabled=true WHERE id IN (SELECT role_id FROM sys_role_user WHERE user_id='${actor.id}')"
        else -> "UPDATE sys_user SET group_id='museum' WHERE id='${actor.id}'"
    }
    try {
        held.createStatement().use { it.executeQuery("SELECT id FROM col_org WHERE id='museum' FOR UPDATE").close() }
        held.createStatement().use { it.executeUpdate(mutation) }
        val job = pool.submit<Result<String>> {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
            try { runCatching(operation) } finally { SecurityContextHolder.clearContext() }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var writerPid: Int? = null
        while (System.nanoTime() < deadline && writerPid == null) {
            writerPid = source.connection.use { observer -> observer.createStatement().use {
                it.executeQuery("SELECT pid FROM pg_stat_activity WHERE application_name='${pg.applicationName}' AND wait_event_type='Lock' AND query LIKE '%col_org%'").use { rows ->
                    if (rows.next()) rows.getInt(1).also { assertFalse(rows.next(), "Only this writer may wait in the owned schema") } else null
                }
            } }
            if (writerPid == null) Thread.sleep(10)
        }
        assertNotNull(writerPid, "$label must reach an actual museum row-lock wait")
        assertFalse(job.isDone)
        println("DEPARTURE_LOCK_OBSERVED label=$label change=$change commit=$commit writerPid=$writerPid")
        if (commit) held.commit() else held.rollback()
        val result = job.get(20, TimeUnit.SECONDS)
        if (commit) {
            assertInstanceOf(BadRequestException::class.java, result.exceptionOrNull(), "$label accepted committed $change revocation")
            source.connection.use { it.createStatement().use { sql -> sql.executeUpdate(restore) } }
            assertEquals(before, state(), "Rejected writes must preserve all declared facts")
        } else assertTrue(result.isSuccess, "$label rejected a rolled back qualification change: $result")
        login()
        val id = operation()
        val settled = state()
        assertEquals(id, operation())
        assertEquals(settled, state(), "An unchanged repeat must retain the original archive and references")
    } finally {
        held.rollback(); held.close(); pool.shutdownNow()
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS))
    }
}
