package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchives
import io.hizhengfu.cloud.collection.dao.WarehouseEntryApplications
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.sql.SQLException
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types
import javax.sql.DataSource

class CollectionLargeContentSchemaCheckTest {
    @TempDir lateinit var dir: Path
    @ParameterizedTest
    @CsvSource(value = [
        "DM DBMS|12|VARCHAR2|4000|true|false|BOUNDED", "DM DBMS|2005|CLOB|0|true|false|LARGE",
        "DM DBMS|2011|NCLOB|0|false|false|LARGE", "DM DBMS|1111|JSONB|0|true|true|LARGE",
        "PostgreSQL|12|text|2147483647|false|false|LARGE", "PostgreSQL|12|varchar|65535|true|false|BOUNDED",
        "PostgreSQL|12|varchar|2147483647|false|false|LARGE", "PostgreSQL|1111|jsonb|0|true|false|LARGE",
        "PostgreSQL|1111|jsonb|0|false|false|UNKNOWN", "PostgreSQL|12|varchar|0|false|false|UNKNOWN",
        "MySQL|-1|TEXT|65535|false|false|LARGE", "MySQL|-1|TINYTEXT|255|true|false|BOUNDED",
        "MySQL|12|JSON|0|true|false|LARGE", "MySQL|12|VARCHAR|1000000|true|false|BOUNDED",
        "SQLite|12|VARCHAR(5)|5|false|false|LARGE", "SQLite|2004|BLOB|0|true|true|LARGE",
        "SQLite|2004|BLOB|0|true|false|INCOMPATIBLE", "SQLite|12|CHARINT|0|true|false|INCOMPATIBLE", "PostgreSQL|2004|bytea|0|true|true|INCOMPATIBLE",
        "OpenGauss|1111|json|0|true|false|LARGE", "unknown|1111|JSON|0|true|false|UNKNOWN"
    ], delimiter = '|')
    fun `storage families distinguish fixed width from driver large fields`(vendor: String, jdbc: Int, name: String, size: Long, json: Boolean, binary: Boolean, expected: String) {
        assertEquals(CollectionLargeContentSchemaCheck.Capacity.valueOf(expected), CollectionLargeContentSchemaCheck.StoredType(jdbc, name, size).capacity(vendor, json, binary))
    }

    @Test fun `missing type metadata cannot pass`() {
        assertEquals(CollectionLargeContentSchemaCheck.Capacity.UNKNOWN, CollectionLargeContentSchemaCheck.StoredType(null, "TEXT", 65535).capacity("PostgreSQL", false, false))
        assertEquals(CollectionLargeContentSchemaCheck.Capacity.UNKNOWN, CollectionLargeContentSchemaCheck.StoredType(Types.CLOB, null, 0).capacity("DM DBMS", true, false))
    }

    private fun source() = SQLiteDataSource().apply {
        url = "jdbc:sqlite:${dir.resolve("content.db")}";
        transaction(Database.connect(this)) { SchemaUtils.create(CollectionPermanentArchives, WarehouseEntryApplications) }
    }
    private fun ddl(source: DataSource) = source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT name,sql FROM sqlite_master ORDER BY name").use { r -> buildList { while (r.next()) add(r.getString(1) to r.getString(2)) } } } }

    @Test fun `actual archive and personnel dossier columns are checked without reading content or changing DDL`() {
        val source = source(); val before = ddl(source)
        val result = CollectionLargeContentSchemaCheck.check(source, setOf(CollectionPermanentArchives, WarehouseEntryApplications), "合成档案")
        assertEquals(EnumDeploymentCheckState.PASS, result.state, result.toString())
        assertTrue(result.safeFacts.single { it.name == "declaredLargeContentColumns" }.value.toInt() >= 5)
        assertEquals(before, ddl(source))
        source.connection.use { c -> c.createStatement().use { it.execute("ALTER TABLE col_permanent_archive DROP COLUMN snapshot") } }
        val missing = CollectionLargeContentSchemaCheck.check(source, setOf(CollectionPermanentArchives), "合成档案")
        assertEquals(EnumDeploymentCheckState.FAIL, missing.state)
        assertEquals("col_permanent_archive.snapshot", missing.safeFacts.single { it.name == "missingLargeContentColumns" }.value)
    }

    @Test fun `missing table fails and tables without large content skip`() {
        val source = source()
        val absent = object : Table("absent_content") { val body = text("body") }
        assertEquals(EnumDeploymentCheckState.FAIL, CollectionLargeContentSchemaCheck.check(source, setOf(absent), "未安装表").state)
        assertEquals(EnumDeploymentCheckState.SKIPPED, CollectionLargeContentSchemaCheck.check(source, setOf(Table("unused")), "无正文").state)
    }

    @Test fun `connection error does not expose configuration or claim compatibility`() {
        val source = mock(DataSource::class.java)
        `when`(source.connection).thenThrow(SQLException("jdbc:private password=private-secret"))
        val result = CollectionLargeContentSchemaCheck.check(source, setOf(CollectionPermanentArchives), "档案")
        assertEquals(EnumDeploymentCheckState.ERROR, result.state)
        assertFalse(result.toString().contains("private"))
    }

    @Test fun `driver requiring a schema cannot pass when current namespace is unavailable`() {
        val source = mock(DataSource::class.java); val connection = mock(Connection::class.java); val metadata = mock(DatabaseMetaData::class.java)
        `when`(source.connection).thenReturn(connection); `when`(connection.metaData).thenReturn(metadata)
        `when`(metadata.supportsSchemasInTableDefinitions()).thenReturn(true)
        val result = CollectionLargeContentSchemaCheck.check(source, setOf(CollectionPermanentArchives), "档案")
        assertEquals(EnumDeploymentCheckState.ERROR, result.state)
        assertEquals("COLLECTION_CONTENT_SCOPE_UNKNOWN", result.reasonCode)
        verify(metadata, never()).getColumns(any(), any(), any(), any())
        verify(connection).close()
    }
}
