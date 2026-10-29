package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.dao.alertSubscribers
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.*

internal fun settledSafetySelectionState(vararg tables: Table): Map<String, List<List<String?>>> {
    repeat(2) {
        TransactionManager.current().entityCache.flush()
        TransactionManager.current().alertSubscribers()
    }
    TransactionManager.current().entityCache.flush()
    return tables.associate { table -> table.tableName to table.selectAll().map { row ->
        table.columns.map { row[it]?.toString() }
    }.sortedBy { it.toString() } }
}

internal fun assertSafetySelectionRealAudit(table:OrgAuditUIDTable,entity:OrgAuditUIDEntity,change:()->Unit) {
    settledSafetySelectionState(table)
    val previous=table.selectAll().single {it[table.id]==entity.id}[table.updateTime]!!
    Thread.sleep(3)
    change()
    val stable=settledSafetySelectionState(table)
    val current=table.selectAll().single {it[table.id]==entity.id}
    assertTrue(current[table.updateTime]!!.isAfter(previous))
    assertEquals(SecurityUtils.getUserDetail().id,current[table.updateBy])
    assertEquals(stable,settledSafetySelectionState(table))
}

internal fun assertSafetySelectionIntegrity(vararg actions: () -> Unit) {
    actions.forEach { action ->
        val error = assertThrows(BadRequestException::class.java) { action() }
        assertTrue(error.message.orEmpty().contains(Regex("原件|不一致|缺失|归属")), error.message)
    }
}

/** Each tagged case owns its schema, including cleanup after a failed assertion. */
internal fun withSafetySelectionPostgres(block: (Database) -> Unit) {
    val path = System.getProperty("collection.test.pg.config") ?: error("Explicit PostgreSQL test configuration required")
    val properties = java.util.Properties().apply { java.nio.file.Files.newInputStream(java.nio.file.Path.of(path)).use(::load) }
    fun source() = org.postgresql.ds.PGSimpleDataSource().apply {
        setURL(properties.getProperty("url")); user = properties.getProperty("user"); password = properties.getProperty("password")
    }
    val admin = source()
    val schema = "operation_safety_" + java.util.UUID.randomUUID().toString().replace("-", "")
    admin.connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
    try {
        block(Database.connect(source().apply { currentSchema = schema }, databaseConfig = org.jetbrains.exposed.v1.core.DatabaseConfig { useNestedTransactions = true }))
    } finally {
        admin.connection.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
    }
}
