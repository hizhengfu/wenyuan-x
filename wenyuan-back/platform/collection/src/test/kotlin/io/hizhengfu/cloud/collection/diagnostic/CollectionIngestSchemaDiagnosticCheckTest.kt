package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.museum.device.dao.MuseumAlarmOrgLocks
import io.hizhengfu.cloud.museum.device.dao.MuseumIngestRecords
import io.hizhengfu.cloud.museum.device.dao.MuseumIngestInboxes
import io.hizhengfu.cloud.museum.device.install.MuseumDeviceInstall
import io.hizhengfu.cloud.sys.diagnostic.check.DeclaredTablesDiagnosticCheck
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.install.service.InstallService
import io.hizhengfu.cloud.sys.system.dao.Configs
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
import java.sql.SQLException
import javax.sql.DataSource

class CollectionIngestSchemaDiagnosticCheckTest {
    @TempDir lateinit var dir: Path
    private val install = MuseumDeviceInstall()
    private fun source() = SQLiteDataSource().apply {
        url = "jdbc:sqlite:${dir.resolve("ingest.db")}"
        transaction(Database.connect(this)) { SchemaUtils.create(Configs, *install.initTables().toTypedArray()) }
    }
    private fun execute(source: DataSource, sql: String) = source.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    private fun schema(source: DataSource) = source.connection.use { c -> c.createStatement().use { s ->
        s.executeQuery("SELECT name,sql FROM sqlite_master ORDER BY name").use { r -> buildList { while(r.next()) add(r.getString(1) to r.getString(2)) } }
    } }
    private fun replaceLock(source: DataSource, change: (String) -> String) {
        val savedSchema = schema(source)
        val original = savedSchema.single { it.first == MuseumAlarmOrgLocks.tableName }.second!!
        val names = MuseumAlarmOrgLocks.indices.map { it.indexName }.toSet()
        val indexes = savedSchema.filter { it.first in names }.mapNotNull { it.second }
        execute(source, "DROP TABLE ing_alarm_org_lock")
        execute(source, change(original))
        indexes.forEach { execute(source, it) }
    }
    @Test
    fun `complete declared dependencies pass without reading or changing private evidence`() {
        val source = source()
        execute(source, "INSERT INTO ing_inbox (id,connector_code,transport,payload_base64,metadata_json,received_at,status,attempts,processing_token) VALUES ('synthetic','TEST','HTTP','private-payload','private-metadata','2026-09-15 00:00:00.123','PROCESSING',3,'private-claim')")
        val before = schema(source)
        val check = CollectionIngestSchemaDiagnosticCheck(source, install)
        repeat(2) {
            val result = check.check()
            assertEquals(EnumDeploymentCheckState.PASS, result.state, result.toString())
            assertEquals(install.initTables().size.toString(), result.safeFacts.single { it.name == "declaredTableCount" }.value)
            assertFalse(result.toString().contains("private-"))
        }
        assertEquals(EnumDeploymentCheckSeverity.CRITICAL, check.descriptor.severity)
        assertEquals(before, schema(source))
        source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT payload_base64,metadata_json,attempts,processing_token FROM ing_inbox").use { r ->
            assertTrue(r.next()); assertEquals("private-payload", r.getString(1)); assertEquals("private-metadata", r.getString(2)); assertEquals(3, r.getInt(3)); assertEquals("private-claim", r.getString(4)); assertFalse(r.next())
        } } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["claim-column", "lock-table", "lock-unique", "lock-primary", "lock-not-null", "fact-identity"])
    fun `missing dependencies fail even when tables alone are insufficient evidence`(kind: String) {
        val source = source()
        when(kind) {
            "claim-column" -> execute(source, "ALTER TABLE ing_inbox DROP COLUMN processing_token")
            "lock-table" -> execute(source, "DROP TABLE ing_alarm_org_lock")
            "lock-unique" -> execute(source, "DROP INDEX ${MuseumAlarmOrgLocks.indices.single { it.unique }.indexName}")
            "lock-primary" -> replaceLock(source) { it.replace(" PRIMARY KEY", "") }
            "lock-not-null" -> replaceLock(source) { it.replace(Regex("(org_id\\s+VARCHAR\\(64\\)) NOT NULL", RegexOption.IGNORE_CASE), "$1 NULL") }
            "fact-identity" -> {
                MuseumIngestRecords.indices.filter { MuseumIngestRecords.inboxId in it.columns }.forEach { execute(source, "DROP INDEX ${it.indexName}") }
                execute(source, "ALTER TABLE ing_record DROP COLUMN inbox_id")
            }
        }
        val before = schema(source)
        val result = CollectionIngestSchemaDiagnosticCheck(source, install).check()
        assertEquals(EnumDeploymentCheckState.FAIL, result.state, result.toString())
        assertEquals("COLLECTION_MODULE_SCHEMA_INCOMPLETE", result.reasonCode)
        assertEquals(before, schema(source), "只读检查不得擅自升级数据库")
        if(kind == "claim-column") {
            assertTrue(result.safeFacts.single { it.name == "missingColumns" }.value.contains("ing_inbox.processing_token"))
            val service = mock(InstallService::class.java)
            `when`(service.installs).thenReturn(lazy { mutableListOf<io.hizhengfu.cloud.common.install.AbstractInstall>(install) })
            transaction(Database.connect(source)) {
                assertEquals(EnumDeploymentCheckState.PASS, DeclaredTablesDiagnosticCheck(service).check().state, "旧表存在检查无法识别缺列")
            }
        }
    }

    @Test
    fun `component discovery registers ingestion schema in deployment diagnostics`() {
        val source = source()
        val candidates = org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true)
            .findCandidateComponents("io.hizhengfu.cloud.collection.diagnostic")
        val definition = candidates.single { it.beanClassName == CollectionIngestSchemaDiagnosticCheck::class.java.name }
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { context ->
            context.registerBean(DataSource::class.java, java.util.function.Supplier { source })
            context.registerBean(MuseumDeviceInstall::class.java, java.util.function.Supplier { install })
            context.registerBeanDefinition("ingestSchemaDiagnosticCheck", definition)
            context.register(io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticRegistry::class.java)
            context.refresh()
            val check = context.getBean(io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticRegistry::class.java).checks.single()
            assertEquals("collection.ingest.database-schema", check.descriptor.id)
            assertEquals(EnumDeploymentCheckState.PASS, check.check().state)
        }
    }

    @Test
    fun `metadata failure is unverified and does not expose connection credentials`() {
        val source = mock(DataSource::class.java)
        `when`(source.connection).thenThrow(SQLException("jdbc:private-server password=private-password"))
        val result = CollectionIngestSchemaDiagnosticCheck(source, install).check()
        assertEquals(EnumDeploymentCheckState.ERROR, result.state)
        assertEquals("COLLECTION_INGEST_SCHEMA_METADATA_UNAVAILABLE", result.reasonCode)
        assertFalse(result.toString().contains("private-"))
    }
}
