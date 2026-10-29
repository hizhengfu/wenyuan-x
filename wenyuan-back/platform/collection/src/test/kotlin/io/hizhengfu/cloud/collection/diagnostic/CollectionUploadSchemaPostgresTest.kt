package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.postgresql.ds.PGSimpleDataSource
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

/** 仅使用显式指定的临时 PostgreSQL，每例独立 schema 并清理。 */
@Tag("postgres-concurrency")
class CollectionUploadSchemaPostgresTest {
    private fun execute(source: PGSimpleDataSource, sql: String) = source.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    private fun fixture(body: (PGSimpleDataSource) -> Unit) {
        val props = Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
        val schema = "upload_schema_" + UUID.randomUUID().toString().replace("-", "")
        val source = PGSimpleDataSource().apply { setURL(props.getProperty("url")); user = props.getProperty("user"); password = props.getProperty("password"); currentSchema = schema }
        execute(source, "CREATE SCHEMA $schema")
        try {
            val db = Database.connect(source)
            transaction(db) { SchemaUtils.create(*CollectionUploadSchemaDiagnosticCheck.REQUIRED_TABLES.toTypedArray()) }
            body(source)
        } finally { execute(source, "DROP SCHEMA $schema CASCADE") }
    }

    @Test fun `complete PostgreSQL task types pass preserving millisecond times and private receipt`() = fixture { source ->
        execute(source, "INSERT INTO sys_file_chunk_session (id,owner_id,storage_scope,state,created_at,expires_at,updated_at,revision,receipt) VALUES ('old','private-owner','scope','REVIEW',1780000000123,1780000100123,1780000000999,7,'private-receipt')")
        repeat(2) {
            val result = CollectionUploadSchemaDiagnosticCheck(source).check()
            assertEquals(EnumDeploymentCheckState.PASS, result.state, result.toString())
            assertFalse(result.toString().contains("private-"))
        }
        source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT created_at,revision,receipt FROM sys_file_chunk_session").use { rs ->
            assertTrue(rs.next()); assertEquals(1780000000123L, rs.getLong(1)); assertEquals(7L, rs.getLong(2)); assertEquals("private-receipt", rs.getString(3)); assertFalse(rs.next())
        } } }
    }

    @ParameterizedTest
    @CsvSource(value = ["created_at|INTEGER|FAIL", "updated_at|DOUBLE PRECISION|FAIL", "revision|NUMERIC(18,0)|FAIL", "revision|NUMERIC(19,0)|PASS", "revision|NUMERIC(20,1)|FAIL", "state|VARCHAR(19)|FAIL", "owner_id|VARCHAR(63)|FAIL", "receipt|VARCHAR(255)|FAIL", "receipt|VARCHAR(65535)|PASS"], delimiter = '|')
    fun `task column capacity uses current PostgreSQL metadata`(column: String, type: String, expected: String) = fixture { source ->
        execute(source, "ALTER TABLE sys_file_chunk_session ALTER COLUMN $column TYPE $type")
        val result = CollectionUploadSchemaDiagnosticCheck(source).check()
        assertEquals(EnumDeploymentCheckState.valueOf(expected), result.state, result.toString())
        if (expected == "FAIL") {
            assertEquals("COLLECTION_UPLOAD_COLUMN_TYPE_INCOMPATIBLE", result.reasonCode)
            assertTrue(result.safeFacts.single { it.name == "taskTypeIncompatibleColumns" }.value.contains(".$column"))
        }
    }

    @Test fun `complete task in other schema cannot conceal absent current table`() = fixture { source ->
        val other = source.currentSchema + "_decoy"
        execute(source, "CREATE SCHEMA $other")
        try {
            execute(source, "ALTER TABLE sys_file_chunk_session SET SCHEMA $other")
            val result = CollectionUploadSchemaDiagnosticCheck(source).check()
            assertEquals(EnumDeploymentCheckState.FAIL, result.state, result.toString())
            assertTrue(result.safeFacts.single { it.name == "missingTables" }.value.contains("sys_file_chunk_session"))
        } finally { execute(source, "DROP SCHEMA $other CASCADE") }
    }

    @Test fun `wide task in other schema cannot conceal narrow current time column`() = fixture { source ->
        val other = source.currentSchema + "_decoy"
        execute(source, "CREATE SCHEMA $other")
        try {
            execute(source, "CREATE TABLE $other.sys_file_chunk_session (LIKE sys_file_chunk_session INCLUDING ALL)")
            execute(source, "ALTER TABLE sys_file_chunk_session ALTER COLUMN created_at TYPE INTEGER")
            val result = CollectionUploadSchemaDiagnosticCheck(source).check()
            assertEquals(EnumDeploymentCheckState.FAIL, result.state, result.toString())
            assertEquals("COLLECTION_UPLOAD_COLUMN_TYPE_INCOMPATIBLE", result.reasonCode)
        } finally { execute(source, "DROP SCHEMA $other CASCADE") }
    }
}
