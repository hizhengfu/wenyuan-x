package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.museum.device.dao.MuseumAlarmOrgLocks
import io.hizhengfu.cloud.museum.device.install.MuseumDeviceInstall
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.postgresql.ds.PGSimpleDataSource
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

@Tag("postgres-concurrency")
class CollectionIngestSchemaPostgresTest {
    @ParameterizedTest
    @ValueSource(strings = ["complete", "claim-column", "lock-table", "lock-unique", "lock-primary", "lock-not-null", "other-schema"])
    fun `actual PostgreSQL dependency scan only accepts complete current schema`(kind: String) {
        val props = Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
        val schema = "ingest_schema_" + UUID.randomUUID().toString().replace("-", "")
        val source = PGSimpleDataSource().apply { setURL(props.getProperty("url")); user = props.getProperty("user"); password = props.getProperty("password"); currentSchema = schema }
        fun execute(sql: String) = source.connection.use { c -> c.createStatement().use { it.execute(sql) } }
        execute("CREATE SCHEMA $schema")
        val install = MuseumDeviceInstall()
        try {
            transaction(Database.connect(source)) { SchemaUtils.create(*install.initTables().toTypedArray()) }
            when(kind) {
                "claim-column" -> execute("ALTER TABLE ing_inbox DROP COLUMN processing_token")
                "lock-table" -> execute("DROP TABLE ing_alarm_org_lock")
                "lock-unique" -> execute("ALTER TABLE ing_alarm_org_lock DROP CONSTRAINT ${MuseumAlarmOrgLocks.indices.single { it.unique }.indexName}")
                "lock-primary" -> source.connection.use { c -> c.metaData.getPrimaryKeys(c.catalog, schema, "ing_alarm_org_lock").use { r ->
                    assertTrue(r.next()); execute("ALTER TABLE ing_alarm_org_lock DROP CONSTRAINT \"${r.getString("PK_NAME")}\"")
                } }
                "lock-not-null" -> execute("ALTER TABLE ing_alarm_org_lock ALTER COLUMN org_id DROP NOT NULL")
                "other-schema" -> {
                    execute("CREATE SCHEMA ${schema}_other")
                    execute("CREATE TABLE ${schema}_other.ing_inbox (LIKE ing_inbox INCLUDING ALL)")
                    execute("ALTER TABLE ing_inbox DROP COLUMN processing_token")
                }
            }
            fun snapshot() = source.connection.use { c -> c.createStatement().use { s ->
                s.executeQuery("SELECT table_name,column_name,is_nullable FROM information_schema.columns WHERE table_schema='$schema' ORDER BY table_name,ordinal_position").use { r -> buildList { while(r.next()) add(listOf(r.getString(1),r.getString(2),r.getString(3))) } }
            } }
            val before = snapshot()
            val result = CollectionIngestSchemaDiagnosticCheck(source, install).check()
            assertEquals(if(kind == "complete") EnumDeploymentCheckState.PASS else EnumDeploymentCheckState.FAIL, result.state, result.toString())
            if(kind in listOf("claim-column", "other-schema")) assertTrue(result.safeFacts.single { it.name == "missingColumns" }.value.contains("ing_inbox.processing_token"))
            assertEquals(before, snapshot())
        } finally {
            execute("DROP SCHEMA IF EXISTS ${schema}_other CASCADE")
            execute("DROP SCHEMA $schema CASCADE")
        }
    }
}
