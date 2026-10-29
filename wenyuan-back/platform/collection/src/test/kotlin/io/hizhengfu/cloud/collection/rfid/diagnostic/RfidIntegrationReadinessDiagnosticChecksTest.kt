package io.hizhengfu.cloud.collection.rfid.diagnostic

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.museum.device.common.MuseumCredentialCipher
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumIngestConnectors
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.domain.EnumIngestAcquisitionMode
import io.hizhengfu.cloud.museum.device.domain.EnumIngestConnectorProbeStatus
import io.hizhengfu.cloud.museum.device.domain.EnumIngestConnectorType
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.domain.EnumRfidDeviceRole
import io.hizhengfu.cloud.museum.device.domain.MuseumHttpConnectorConfig
import io.hizhengfu.cloud.museum.device.service.MuseumIngestPushTargetService
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.util.ReflectionTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RfidIntegrationReadinessDiagnosticChecksTest {
    @Test
    fun `standalone gate does not require a workstation or purpose binding`() {
        transaction(testDatabase("standalone-gate")) {
            createTables()
            insertReadyFixture(profileRole = EnumRfidDeviceRole.WAREHOUSE_GATE)
            RfidWorkstationDeviceBindings.deleteAll()
            RfidWorkstations.deleteAll()
            val result = RfidIntegrationConfigurationDiagnosticCheck(pushTargetService(), true, true).check()
            assertEquals(EnumDeploymentCheckState.PASS, result.state)
            assertEquals("false", result.safeFacts.single { it.name == "workstationRequired" }.value)
        }
    }

    @Test
    fun `spring creates configuration check with push target service and enabled properties`() {
        ApplicationContextRunner()
            .withPropertyValues(
                "collection.rfid.readiness.enabled=true",
                "collection.rfid.readiness.fixed-devices-required=true",
                "museum.device.security.master-key=$TEST_MASTER_KEY",
            )
            .withBean(MuseumCredentialCipher::class.java)
            .withBean(MuseumIngestPushTargetService::class.java)
            .withBean(RfidIntegrationConfigurationDiagnosticCheck::class.java)
            .run { context ->
                val check = context.getBean(RfidIntegrationConfigurationDiagnosticCheck::class.java)
                assertEquals(true, ReflectionTestUtils.getField(check, "readinessEnabled"))
                assertEquals(true, ReflectionTestUtils.getField(check, "fixedDevicesRequired"))
            }
    }

    @Test
    fun `spring injects readiness properties into runtime check`() {
        ApplicationContextRunner()
            .withPropertyValues(
                "collection.rfid.readiness.enabled=true",
                "collection.rfid.readiness.fixed-devices-required=false",
            )
            .withBean(RfidFixedDeviceRuntimeDiagnosticCheck::class.java)
            .run { context ->
                val result = context.getBean(RfidFixedDeviceRuntimeDiagnosticCheck::class.java).check()
                assertEquals(EnumDeploymentCheckState.SKIPPED, result.state)
                assertEquals("true", result.safeFacts.single { it.name == "readinessEnabled" }.value)
                assertEquals("false", result.safeFacts.single { it.name == "fixedDevicesRequired" }.value)
            }
    }

    @Test
    fun `configuration check skips without touching database when readiness is disabled`() {
        val result = RfidIntegrationConfigurationDiagnosticCheck(
            pushTargetService = pushTargetService(),
            readinessEnabled = false,
            fixedDevicesRequired = true,
        ).check()

        assertEquals(EnumDeploymentCheckState.SKIPPED, result.state)
    }

    @Test
    fun `configuration check reports every empty integration boundary without leaking values`() {
        val database = testDatabase("empty")
        transaction(database) {
            createTables()

            val result = RfidIntegrationConfigurationDiagnosticCheck(
                pushTargetService = pushTargetService(),
                readinessEnabled = true,
                fixedDevicesRequired = true,
            ).check()

            assertEquals(EnumDeploymentCheckState.FAIL, result.state)
            assertEquals("COLLECTION_RFID_INTEGRATION_CONFIGURATION_INCOMPLETE", result.reasonCode)
            val problems = result.safeFacts.single { it.name == "problems" }.value
            assertTrue(problems.contains("RFID 设备档案"))
            assertTrue(problems.contains("固定式 RFID 设备档案"))
            assertTrue(problems.contains("业务回调通道"))
            assertTrue(problems.contains("RFID 工作站"))
            assertTrue(problems.contains("用途绑定"))
            assertFalse(result.safeFacts.any { it.value.contains("http://") || it.value.contains("secret") })
        }
    }

    @Test
    fun `configuration and runtime checks pass for verified bound online fixed device`() {
        val database = testDatabase("ready")
        transaction(database) {
            createTables()
            insertReadyFixture()

            val configuration = RfidIntegrationConfigurationDiagnosticCheck(
                pushTargetService = pushTargetService(),
                readinessEnabled = true,
                fixedDevicesRequired = true,
            ).check()
            val runtime = RfidFixedDeviceRuntimeDiagnosticCheck(
                readinessEnabled = true,
                fixedDevicesRequired = true,
            ).check()

            assertEquals(EnumDeploymentCheckState.PASS, configuration.state)
            assertEquals(EnumDeploymentCheckState.PASS, runtime.state)
            assertEquals("1", runtime.safeFacts.single { it.name == "onlineFixedDeviceCount" }.value)
        }
    }

    @Test
    fun `configuration check rejects purpose binding whose device role does not match`() {
        val database = testDatabase("role-mismatch")
        transaction(database) {
            createTables()
            insertReadyFixture(profileRole = EnumRfidDeviceRole.WAREHOUSE_GATE)

            val result = RfidIntegrationConfigurationDiagnosticCheck(
                pushTargetService = pushTargetService(),
                readinessEnabled = true,
                fixedDevicesRequired = true,
            ).check()

            assertEquals(EnumDeploymentCheckState.FAIL, result.state)
            assertEquals("1", result.safeFacts.single { it.name == "invalidBindingCount" }.value)
            assertEquals("1", result.safeFacts.single { it.name == "missingRequiredPurposeCount" }.value)
        }
    }

    @Test
    fun `configuration check requires a fresh successful callback probe`() {
        val database = testDatabase("callback-not-probed")
        transaction(database) {
            createTables()
            insertReadyFixture(callbackProbeSucceeded = false)

            val result = RfidIntegrationConfigurationDiagnosticCheck(
                pushTargetService = pushTargetService(),
                readinessEnabled = true,
                fixedDevicesRequired = true,
            ).check()

            assertEquals(EnumDeploymentCheckState.FAIL, result.state)
            assertEquals("false", result.safeFacts.single { it.name == "callbackAuthenticated" }.value)
            assertTrue(result.safeFacts.single { it.name == "problems" }.value.contains("正向鉴权自检"))
        }
    }

    private fun testDatabase(label: String) = Database.connect(
        "jdbc:sqlite:file:rfid-readiness-$label-${System.nanoTime()}?mode=memory&cache=shared",
        driver = "org.sqlite.JDBC",
    )

    private fun createTables() {
        SchemaUtils.create(
            MuseumIngestConnectors,
            MuseumRfidDeviceProfiles,
            MuseumDeviceConnections,
            RfidWorkstations,
            RfidWorkstationDeviceBindings,
        )
    }

    private fun insertReadyFixture(
        profileRole: EnumRfidDeviceRole = EnumRfidDeviceRole.DESK_READER,
        callbackProbeSucceeded: Boolean = true,
    ) {
        val now = museumUtcNow()
        MuseumIngestConnectors.insert {
            it[id] = "connector-1"
            it[code] = "COLLECTION_RFID_CALLBACK"
            it[name] = "藏品 RFID 回调"
            it[type] = EnumIngestConnectorType.HTTP_CLIENT
            it[acquisitionMode] = EnumIngestAcquisitionMode.PUSH
            it[enabled] = true
            it[configJson] = JsonUtils.toJson(MuseumHttpConnectorConfig(baseUrl = "http://127.0.0.1:8082"))!!
            it[passwordEncrypted] = MuseumCredentialCipher(TEST_MASTER_KEY).encrypt("callback-secret")
            if (callbackProbeSucceeded) {
                it[probeStatus] = EnumIngestConnectorProbeStatus.SUCCESS
                it[probeRequestedAt] = now.minusSeconds(1)
                it[probeStartedAt] = now.minusSeconds(1)
                it[probeCompletedAt] = now
                it[probeRuntimeInstanceId] = "rfid-probe-test"
                it[probeResultCode] = "OK"
                it[probeMessage] = "采集进程已通过回调服务鉴权"
                it[probeDurationMillis] = 10
            }
        }
        MuseumRfidDeviceProfiles.insert {
            it[id] = "profile-1"
            it[orgId] = "org-1"
            it[deviceId] = "device-1"
            it[role] = profileRole
            it[rolesJson] = "[\"${profileRole.name}\"]"
            it[hardwareSerial] = "SERIAL-001"
            it[lastVerifiedAt] = now
            it[enabled] = true
        }
        RfidWorkstations.insert {
            it[id] = "station-1"
            it[orgId] = "org-1"
            it[code] = "RFID-WS-1"
            it[name] = "借还台"
            it[capabilitiesJson] = "[\"BORROW_RETURN\"]"
            it[enrollmentTokenHash] = "hash"
            it[enabled] = true
        }
        RfidWorkstationDeviceBindings.insert {
            it[id] = "binding-1"
            it[workstationId] = "station-1"
            it[deviceId] = "device-1"
            it[purpose] = EnumRfidWorkstationDevicePurpose.RFID_READ
            it[enabled] = true
        }
        MuseumDeviceConnections.insert {
            it[id] = "connection-1"
            it[deviceId] = "device-1"
            it[runtimeInstanceId] = "runtime-1"
            it[connectionId] = "connection-1"
            it[remoteAddress] = "redacted"
            it[connectedAt] = now.minusMinutes(1)
            it[lastSeenAt] = now
            it[state] = EnumRfidConnectionState.ONLINE
            it[leaseUntil] = now.plusMinutes(1)
        }
    }

    private fun pushTargetService() = MuseumIngestPushTargetService(MuseumCredentialCipher(TEST_MASTER_KEY))

    private companion object {
        const val TEST_MASTER_KEY = "rfid-readiness-test-master-key"
    }
}
