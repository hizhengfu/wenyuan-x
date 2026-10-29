package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.postgresql.ds.PGSimpleDataSource
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

@Tag("postgres-concurrency")
class CollectionLargeContentSchemaPostgresTest {
    private object Content : Table("content_probe") {
        val body = text("body")
        val dossier = json("dossier", { s: String -> s }, { it })
        val binary = jsonb("binary_body", { s: String -> s }, { it })
    }
    private fun sql(source: PGSimpleDataSource, value: String) = source.connection.use { c -> c.createStatement().use { it.execute(value) } }
    private fun fixture(body: (PGSimpleDataSource) -> Unit) {
        val p = Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
        val schema = "content_scope_" + UUID.randomUUID().toString().replace("-", "")
        val source = PGSimpleDataSource().apply { setURL(p.getProperty("url")); user = p.getProperty("user"); password = p.getProperty("password"); currentSchema = schema }
        sql(source, "CREATE SCHEMA $schema")
        try { transaction(Database.connect(source)) { SchemaUtils.create(Content) }; body(source) }
        finally { sql(source, "DROP SCHEMA $schema CASCADE") }
    }
    private fun check(source: PGSimpleDataSource) = CollectionLargeContentSchemaCheck.check(source, setOf(Content), "合成正文")

    @Test fun `current PostgreSQL native large fields pass without changing existing content`() = fixture { source ->
        val content = "档案正文及签署说明".repeat(1800) + "𠮷"
        source.connection.use { c -> c.prepareStatement("INSERT INTO content_probe VALUES (?,?::json,?::jsonb)").use { s ->
            s.setString(1, content); s.setString(2, "[]"); s.setString(3, "{}"); s.executeUpdate()
        } }
        repeat(2) { assertEquals(EnumDeploymentCheckState.PASS, check(source).state) }
        source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT body,dossier::text,binary_body::text FROM content_probe").use { r ->
            assertTrue(r.next()); assertEquals(content, r.getString(1)); assertEquals("[]", r.getString(2)); assertEquals("{}", r.getString(3)); assertFalse(r.next())
        } } }
    }

    @ParameterizedTest @CsvSource(value = ["body|varchar(4000)|FAIL", "dossier|varchar(4000)|FAIL", "binary_body|varchar(65535)|FAIL", "body|varchar|PASS", "body|integer|FAIL"], delimiter = '|')
    fun `actual current column types determine readiness`(column: String, type: String, expected: String) = fixture { source ->
        sql(source, "ALTER TABLE content_probe ALTER COLUMN $column TYPE $type USING NULL")
        assertEquals(EnumDeploymentCheckState.valueOf(expected), check(source).state)
    }

    @Test fun `neighbor schema cannot hide narrow or missing current columns`() = fixture { source ->
        val other = source.currentSchema + "_decoy"
        sql(source, "CREATE SCHEMA $other")
        try {
            sql(source, "CREATE TABLE $other.content_probe (LIKE content_probe INCLUDING ALL)")
            sql(source, "ALTER TABLE content_probe ALTER COLUMN dossier TYPE varchar(4000)")
            val bounded = check(source)
            assertEquals(EnumDeploymentCheckState.FAIL, bounded.state)
            assertTrue(bounded.safeFacts.single { it.name == "boundedLargeContentColumns" }.value.contains("content_probe.dossier"))
            sql(source, "DROP TABLE content_probe")
            assertEquals(EnumDeploymentCheckState.FAIL, check(source).state)
        } finally { sql(source, "DROP SCHEMA $other CASCADE") }
    }
}
