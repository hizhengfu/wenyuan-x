package io.hizhengfu.cloud.collection.rfid.diagnostic

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.rfid.dao.CollectionRfidAuditUIDTable
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageGrants
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAlarmStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidPassageDecision
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidPassageGrantStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWhitelistSyncStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class RfidSchemaDiagnosticCheckTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `complete rfid tables do not hide a missing collection master table`() {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${tempDir.resolve("rfid-without-collections.db").toAbsolutePath()}"
        }
        val database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(*RfidSchemaDiagnosticCheck.RFID_TABLES.toTypedArray())
        }

        val result = RfidSchemaDiagnosticCheck(dataSource).check()

        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals("COLLECTION_RFID_SCHEMA_INCOMPLETE", result.reasonCode)
        assertEquals("col_collection", result.safeFacts.single { it.name == "missingTables" }.value)
    }

    @Test
    fun `diagnostic catches unmigrated collection columns without altering the schema`() {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${tempDir.resolve("rfid-legacy-collections.db").toAbsolutePath()}"
        }
        val database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(Collections, *RfidSchemaDiagnosticCheck.RFID_TABLES.toTypedArray())
            exec("ALTER TABLE col_collection DROP COLUMN collection_nature")
            exec("ALTER TABLE col_collection DROP COLUMN inventory_hold")
        }

        val result = RfidSchemaDiagnosticCheck(dataSource).check()

        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals("0", result.safeFacts.single { it.name == "missingTableCount" }.value)
        assertEquals("2", result.safeFacts.single { it.name == "missingColumnCount" }.value)
        assertEquals(
            setOf("col_collection.collection_nature", "col_collection.inventory_hold"),
            result.safeFacts.single { it.name == "missingColumns" }.value.split(", ").toSet(),
        )
        dataSource.connection.use { connection ->
            connection.metaData.getColumns(null, null, "col_collection", "%").use { rows ->
                val actualColumns = buildSet { while (rows.next()) add(rows.getString("COLUMN_NAME")) }
                assertTrue("collection_nature" !in actualColumns)
                assertTrue("inventory_hold" !in actualColumns)
            }
        }
    }

    @Test
    fun `complete rfid and collection master schemas pass`() {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${tempDir.resolve("rfid-complete-collections.db").toAbsolutePath()}"
        }
        val database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(Collections, *RfidSchemaDiagnosticCheck.RFID_TABLES.toTypedArray())
        }

        val result = RfidSchemaDiagnosticCheck(dataSource).check()

        assertEquals(EnumDeploymentCheckState.PASS, result.state)
        assertEquals("16", result.safeFacts.single { it.name == "declaredTableCount" }.value)
        assertEquals("0", result.safeFacts.single { it.name == "missingTableCount" }.value)
        assertEquals("0", result.safeFacts.single { it.name == "missingColumnCount" }.value)
    }

    @Test
    fun `diagnostic reports missing columns even when the rfid table already exists`() {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${tempDir.resolve("legacy-rfid.db").toAbsolutePath()}"
        }
        val database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(LegacyRfidWorkstations)
        }

        val result = RfidSchemaDiagnosticCheck(dataSource).check()

        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals("COLLECTION_RFID_SCHEMA_INCOMPLETE", result.reasonCode)
        val missingColumns = result.safeFacts.single { it.name == "missingColumns" }.value
        assertTrue(missingColumns.contains("${RfidWorkstations.tableName}.passage_debounce_seconds"))
        assertTrue(missingColumns.contains("${RfidWorkstations.tableName}.alarm_output_index"))
        assertTrue(missingColumns.contains("${RfidWorkstations.tableName}.alarm_duration_millis"))
        assertTrue(missingColumns.contains("${RfidWorkstations.tableName}.active_lease_instance_hash"))
        assertTrue(missingColumns.contains("${RfidWorkstations.tableName}.active_lease_until"))
    }

    @Test
    fun `generic installer migration adds all nine rfid columns and preserves legacy rows`() {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${tempDir.resolve("rfid-103-upgrade.db").toAbsolutePath()}"
        }
        val database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(LegacyRfidWorkstations, LegacyRfidPassageGrants, LegacyRfidPassageEvents)
            val now = LocalDateTime.now()
            LegacyRfidWorkstations.insert {
                it[id] = "station-legacy"
                it[orgId] = "org-1"
                it[code] = "RFID-WS-LEGACY"
                it[name] = "旧借还台"
                it[enrollmentTokenHash] = "hash"
            }
            LegacyRfidPassageGrants.insert {
                it[id] = "grant-legacy"
                it[orgId] = "org-1"
                it[collectionId] = "collection-1"
                it[epc] = "300833B2DDD9014000000001"
                it[sourceType] = "LOAN"
                it[sourceId] = "loan-1"
                it[gateId] = "gate-1"
                it[direction] = EnumRfidDirection.OUT
                it[validFrom] = now.minusMinutes(1)
                it[validUntil] = now.plusMinutes(1)
                it[grantStatus] = EnumRfidPassageGrantStatus.ACTIVE
                it[whitelistSyncStatus] = EnumRfidWhitelistSyncStatus.NOT_REQUIRED
            }
            LegacyRfidPassageEvents.insert {
                it[id] = "event-legacy"
                it[orgId] = "org-1"
                it[deviceId] = "device-1"
                it[gateId] = "gate-1"
                it[epc] = "300833B2DDD9014000000001"
                it[direction] = EnumRfidDirection.OUT
                it[occurredAt] = now
                it[receivedAt] = now
                it[decision] = EnumRfidPassageDecision.AUTHORIZED
                it[sourceEventId] = "legacy-event-1"
            }

            SchemaUtils.createMissingTablesAndColumns(
                RfidWorkstations,
                RfidPassageGrants,
                RfidPassageEvents,
            )

            val station = RfidWorkstations.selectAll()
                .where { RfidWorkstations.id eq "station-legacy" }
                .single()
            assertEquals("RFID-WS-LEGACY", station[RfidWorkstations.code])
            assertEquals(3, station[RfidWorkstations.passageDebounceSeconds])
            assertEquals(1, station[RfidWorkstations.alarmOutputIndex])
            assertEquals(3_000L, station[RfidWorkstations.alarmDurationMillis])
            assertNull(station[RfidWorkstations.activeLeaseInstanceHash])
            assertNull(station[RfidWorkstations.activeLeaseUntil])

            val grant = RfidPassageGrants.selectAll()
                .where { RfidPassageGrants.id eq "grant-legacy" }
                .single()
            assertEquals("collection-1", grant[RfidPassageGrants.collectionId])
            assertNull(grant[RfidPassageGrants.revokedAt])

            val event = RfidPassageEvents.selectAll()
                .where { RfidPassageEvents.id eq "event-legacy" }
                .single()
            assertEquals("legacy-event-1", event[RfidPassageEvents.sourceEventId])
            assertNull(event[RfidPassageEvents.alarmCommandId])
            assertEquals(EnumRfidAlarmStatus.NOT_REQUIRED, event[RfidPassageEvents.alarmStatus])
            assertNull(event[RfidPassageEvents.alarmMessage])
        }
    }

    private object LegacyRfidWorkstations : CollectionRfidAuditUIDTable("col_rfid_workstation") {
        val orgId = varchar("org_id", 64)
        val code = varchar("code", 64)
        val name = varchar("name", 100)
        val defaultCapability = enumerationByName<EnumRfidWorkstationCapability>("default_capability", 32).nullable()
        val locationId = varchar("location_id", 64).nullable()
        val capabilitiesJson = text("capabilities_json").default("[]")
        val antennaDirectionsJson = text("antenna_directions_json").default("{}")
        val enrollmentTokenHash = varchar("enrollment_token_hash", 64)
        val browserCredentialHash = varchar("browser_credential_hash", 64).nullable()
        val enabled = bool("enabled").default(true)
        val lastLoginAt = datetime("last_login_at").nullable()
    }

    private object LegacyRfidPassageGrants : CollectionRfidAuditUIDTable("col_rfid_passage_grant") {
        val orgId = varchar("org_id", 64)
        val collectionId = varchar("collection_id", 64)
        val epc = varchar("epc", 128)
        val sourceType = varchar("source_type", 64)
        val sourceId = varchar("source_id", 64)
        val gateId = varchar("gate_id", 64)
        val direction = enumerationByName<EnumRfidDirection>("direction", 16)
        val validFrom = datetime("valid_from")
        val validUntil = datetime("valid_until")
        val grantStatus = enumerationByName<EnumRfidPassageGrantStatus>("grant_status", 16)
        val approvedBy = varchar("approved_by", 64).nullable()
        val revokedBy = varchar("revoked_by", 64).nullable()
        val revokeReason = varchar("revoke_reason", 500).nullable()
        val whitelistSyncStatus = enumerationByName<EnumRfidWhitelistSyncStatus>("whitelist_sync_status", 16)
        val whitelistCommandId = varchar("whitelist_command_id", 64).nullable()
    }

    private object LegacyRfidPassageEvents : UIDTable("col_rfid_passage_event") {
        val orgId = varchar("org_id", 64)
        val deviceId = varchar("device_id", 64)
        val gateId = varchar("gate_id", 64)
        val antenna = integer("antenna").nullable()
        val epc = varchar("epc", 128)
        val tid = varchar("tid", 128).nullable()
        val direction = enumerationByName<EnumRfidDirection>("direction", 16)
        val occurredAt = datetime("occurred_at")
        val receivedAt = datetime("received_at")
        val collectionId = varchar("collection_id", 64).nullable()
        val grantId = varchar("grant_id", 64).nullable()
        val decision = enumerationByName<EnumRfidPassageDecision>("decision", 16)
        val sourceEventId = varchar("source_event_id", 100)
        val anomalyId = varchar("anomaly_id", 64).nullable()
        val contextJson = text("context_json").default("{}")
    }
}
