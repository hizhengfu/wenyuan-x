package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.file.dao.FileChunkSessions
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

class CollectionUploadSchemaDiagnosticCheckTest {
    @TempDir lateinit var dir: Path
    private fun source() = SQLiteDataSource().apply {
        url = "jdbc:sqlite:${dir.resolve("upload.db")}"
        val db = Database.connect(this)
        transaction(db) { SchemaUtils.create(*CollectionUploadSchemaDiagnosticCheck.REQUIRED_TABLES.toTypedArray()) }
    }
    private fun execute(source: DataSource, sql: String) = source.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    private fun snapshot(source: DataSource) = source.connection.use { c -> c.createStatement().use { s ->
        s.executeQuery("SELECT name,sql FROM sqlite_master ORDER BY name").use { rs -> buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) } }
    } }
    private fun replaceTask(source: DataSource, change: (String) -> String) {
        val ddl = snapshot(source).single { it.first == FileChunkSessions.tableName }.second!!
        execute(source, "DROP TABLE ${FileChunkSessions.tableName}")
        execute(source, change(ddl))
    }
    private fun type(source: DataSource, column: String, type: String) = replaceTask(source) { ddl ->
        val regex = Regex("(\\b$column\"?\\s+)(?:VARCHAR\\(\\d+\\)|BIGINT|TEXT)", RegexOption.IGNORE_CASE)
        assertTrue(regex.containsMatchIn(ddl), ddl)
        regex.replace(ddl) { it.groupValues[1] + type }
    }

    @Test fun `declared dependency schema passes without modifying schema or stored receipt`() {
        val source = source()
        execute(source, "INSERT INTO sys_file_chunk_session (id,owner_id,storage_scope,state,created_at,expires_at,updated_at,revision,receipt) VALUES ('old','private-owner','private-storage','REVIEW',1780000000123,1780000100123,1780000000999,7,'private-receipt')")
        val before = snapshot(source)
        repeat(2) {
            val result = CollectionUploadSchemaDiagnosticCheck(source).check()
            assertEquals(EnumDeploymentCheckState.PASS, result.state, result.toString())
            assertFalse(result.toString().contains("private-"))
        }
        assertEquals(before, snapshot(source))
        source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT * FROM sys_file_chunk_session").use { rs ->
            assertTrue(rs.next()); assertEquals(1780000000123L, rs.getLong("created_at")); assertEquals(7L, rs.getLong("revision")); assertEquals("private-receipt", rs.getString("receipt")); assertFalse(rs.next())
        } } }
    }

    @ParameterizedTest @ValueSource(strings = ["task-table", "receipt", "users-table", "owner-nullable", "primary-key"])
    fun `incomplete dependencies or task constraints fail without repairing schema`(kind: String) {
        val source = source()
        when (kind) {
            "task-table" -> execute(source, "DROP TABLE ${FileChunkSessions.tableName}")
            "receipt" -> execute(source, "ALTER TABLE ${FileChunkSessions.tableName} DROP COLUMN receipt")
            "users-table" -> execute(source, "DROP TABLE ${Users.tableName}")
            "owner-nullable" -> replaceTask(source) { it.replace(Regex("(owner_id\\s+VARCHAR\\(64\\)) NOT NULL", RegexOption.IGNORE_CASE), "$1 NULL") }
            "primary-key" -> replaceTask(source) { it.replace(" PRIMARY KEY", "") }
        }
        val before = snapshot(source)
        val result = CollectionUploadSchemaDiagnosticCheck(source).check()
        assertEquals(EnumDeploymentCheckState.FAIL, result.state, result.toString())
        assertEquals(before, snapshot(source))
    }

    @ParameterizedTest @ValueSource(strings = ["created_at", "revision", "receipt", "state", "id"])
    fun `incompatible task affinities fail`(column: String) {
        val source = source()
        type(source, column, if (column in setOf("created_at", "revision")) "REAL" else "BLOB")
        val result = CollectionUploadSchemaDiagnosticCheck(source).check()
        assertEquals(EnumDeploymentCheckState.FAIL, result.state, result.toString())
        assertEquals("COLLECTION_UPLOAD_COLUMN_TYPE_INCOMPATIBLE", result.reasonCode)
        assertTrue(result.safeFacts.single { it.name == "taskTypeIncompatibleColumns" }.value.contains(".$column"))
    }

    @Test fun `SQLite integer and unbounded text affinity use actual engine capacity`() {
        val source = source()
        type(source, "created_at", "INTEGER")
        type(source, "receipt", "VARCHAR(5)")
        execute(source, "INSERT INTO sys_file_chunk_session (id,owner_id,storage_scope,state,created_at,expires_at,updated_at,receipt) VALUES ('old','owner','scope','REVIEW',1780000000123,1780000100123,1780000000999,'a receipt longer than five characters')")
        assertEquals(EnumDeploymentCheckState.PASS, CollectionUploadSchemaDiagnosticCheck(source).check().state)
        source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT created_at,receipt FROM sys_file_chunk_session").use { rs ->
            assertTrue(rs.next()); assertEquals(1780000000123L, rs.getLong(1)); assertEquals("a receipt longer than five characters", rs.getString(2))
        } } }
    }

    @Test fun `metadata connection failure is unverified and does not expose connection secrets`() {
        val source = mock(DataSource::class.java)
        `when`(source.connection).thenThrow(SQLException("jdbc:secret-host password=private-password"))
        val result = CollectionUploadSchemaDiagnosticCheck(source).check()
        assertEquals(EnumDeploymentCheckState.ERROR, result.state)
        assertEquals("COLLECTION_UPLOAD_SCHEMA_METADATA_UNAVAILABLE", result.reasonCode)
        assertFalse(result.toString().contains("private-password")); assertFalse(result.toString().contains("secret-host"))
    }

    @Test fun `unavailable task type name is an error rather than compatible or incompatible`() {
        val actual = source()
        fun <T> proxy(type: Class<T>, target: T, intercept: (String, Array<out Any?>?) -> Any?): T = type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                val replaced = intercept(method.name, args)
                if (replaced !== Unit) replaced else try { method.invoke(target, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.targetException }
            }
        )
        val source = mock(DataSource::class.java)
        `when`(source.connection).thenAnswer {
            val connection = actual.connection
            val metadata = connection.metaData
            val wrapper = proxy(DatabaseMetaData::class.java, metadata) { method, args ->
                if (method == "getColumns") {
                    val rows = metadata.getColumns(args!![0] as String?, args[1] as String?, args[2] as String?, args[3] as String?)
                    proxy(ResultSet::class.java, rows) { name, parameters ->
                        if (name == "getString" && parameters?.firstOrNull() == "TYPE_NAME") null else Unit
                    }
                } else Unit
            }
            proxy(Connection::class.java, connection) { method, _ -> if (method == "getMetaData") wrapper else Unit }
        }
        val result = CollectionUploadSchemaDiagnosticCheck(source).check()
        assertEquals(EnumDeploymentCheckState.ERROR, result.state, result.toString())
        assertEquals("COLLECTION_UPLOAD_COLUMN_TYPE_UNVERIFIED", result.reasonCode)
    }
}
