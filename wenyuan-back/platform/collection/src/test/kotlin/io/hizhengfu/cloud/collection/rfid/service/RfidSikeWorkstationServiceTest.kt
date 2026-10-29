package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationContextVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationDeviceBindingVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationVo
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.domain.EnumRfidDeviceRole
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.museum.device.service.MuseumRfidDeviceService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class RfidSikeWorkstationServiceTest {
    @Test
    fun `only enabled bound same-organization Sike devices expose loopback configuration`() = withDatabase {
        insert("C5", "ORG-1", "SIKE_C5_WS_READER_V1")
        insert("5A", "ORG-1", "SIKE_5A_WS_READER_V1")
        insert("TCP", "ORG-1", "OTHER", "CK_RFID_C5_V1")
        insert("OTHER_ORG", "ORG-2", "SIKE_C5_WS_READER_V1")
        insert("UNBOUND", "ORG-1", "SIKE_C5_WS_READER_V1")
        val service = service(listOf("C5", "5A", "TCP", "OTHER_ORG"))

        val configs = service.configurations("token", "window")
        assertEquals(setOf("C5", "5A"), configs.map { it.deviceId }.toSet())
        assertEquals("ws://127.0.0.1:9090/rfidService/1234", configs.first().config.serviceUrl)
        assertThrows(BadRequestException::class.java) { service.connect("token", "window", "UNBOUND") }
    }

    @Test
    fun `remote WebSocket URL cannot be delivered to the workstation browser`() = withDatabase {
        insert("C5", "ORG-1", "SIKE_C5_WS_READER_V1", config = """{"serviceUrl":"ws://192.168.1.10:9090/rfidService/1234","connectType":1,"connectAddress":"192.168.1.11:8160"}""")
        assertThrows(BadRequestException::class.java) { service(listOf("C5")).configurations("token", "window") }
    }

    @Test
    fun `Sike device requires a registered hardware serial before browser connection`() = withDatabase {
        insert("C5", "ORG-1", "SIKE_C5_WS_READER_V1", hardwareSerial = null)
        assertThrows(BadRequestException::class.java) { service(listOf("C5")).configurations("token", "window") }
    }

    @Test
    fun `command polling is restricted to the current workstation and connection`() = withDatabase {
        insert("C5", "ORG-1", "SIKE_C5_WS_READER_V1")
        val now = museumUtcNow()
        MuseumDeviceConnections.insert {
            it[id] = "CONN-1"; it[deviceId] = "C5"
            it[runtimeInstanceId] = rfidWorkstationRuntimeId("token", "window")
            it[connectionId] = "SOCKET-1"; it[remoteAddress] = "WORKSTATION_LOCAL"
            it[connectedAt] = now; it[lastSeenAt] = now
            it[state] = EnumRfidConnectionState.ONLINE; it[leaseUntil] = now.plusSeconds(35)
        }
        val devices = mock(MuseumRfidDeviceService::class.java)
        `when`(devices.claimCommands(rfidWorkstationRuntimeId("token", "window"), setOf("C5"), 1, "WS-1"))
            .thenReturn(emptyList())
        service(listOf("C5"), devices).commands("token", "window", "C5", "SOCKET-1")
        verify(devices).claimCommands(rfidWorkstationRuntimeId("token", "window"), setOf("C5"), 1, "WS-1")
        assertThrows(BadRequestException::class.java) {
            service(listOf("C5"), devices).commands("token", "window", "C5", "WRONG-SOCKET")
        }
    }

    @Test
    fun `active physical reader connection cannot be replaced by another workstation window`() = withDatabase {
        insert("C5", "ORG-1", "SIKE_C5_WS_READER_V1")
        val now = museumUtcNow()
        MuseumDeviceConnections.insert {
            it[id] = "CONN-1"; it[deviceId] = "C5"
            it[runtimeInstanceId] = "RFID_WS:OTHER"; it[connectionId] = "SOCKET-OTHER"
            it[remoteAddress] = "WORKSTATION_LOCAL"; it[connectedAt] = now; it[lastSeenAt] = now
            it[state] = EnumRfidConnectionState.ONLINE; it[leaseUntil] = now.plusSeconds(35)
        }
        assertThrows(BadRequestException::class.java) { service(listOf("C5")).connect("token", "window", "C5") }
    }

    private fun withDatabase(block: () -> Unit) {
        val db = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(MuseumDevices, MuseumRfidDeviceProfiles, MuseumDeviceConnections)
            block()
        }
    }

    private fun service(
        boundIds: List<String>, deviceService: MuseumRfidDeviceService = mock(MuseumRfidDeviceService::class.java),
    ): RfidSikeWorkstationService {
        val workstation = mock(RfidWorkstationService::class.java)
        val station = RfidWorkstationVo(
            id = "WS-1", orgId = "ORG-1", code = "WS", name = "工作站", defaultCapability = null,
            locationId = null, capabilities = emptySet(),
            deviceBindings = boundIds.map { RfidWorkstationDeviceBindingVo("B-$it", it, EnumRfidWorkstationDevicePurpose.RFID_READ, true, 0, true) },
            antennaDirections = emptyMap(), passageDebounceSeconds = 3, alarmOutputIndex = 1,
            alarmDurationMillis = 3000, enabled = true, lastLoginAt = null,
        )
        `when`(workstation.workstationForCurrentOperator("token")).thenReturn(station)
        `when`(workstation.requireBrowserLease("token", "window", "WS-1")).thenReturn(
            RfidWorkstationContextVo("WS-1", "WS", "工作站", null, emptySet(), true),
        )
        return RfidSikeWorkstationService(
            workstation, deviceService,
            mock(RfidScanSessionService::class.java), mock(RfidTagService::class.java),
        )
    }

    private fun insert(
        id: String, orgId: String, model: String, protocol: String = "SIKE_RFID_WS_V1",
        config: String = """{"serviceUrl":"ws://127.0.0.1:9090/rfidService/1234","connectType":1,"connectAddress":"192.168.1.100:8160","antennaMask":1,"tidWordLength":6}""",
        hardwareSerial: String? = "SERIAL-$id",
    ) {
        MuseumDevices.insert {
            it[MuseumDevices.id] = id; it[MuseumDevices.orgId] = orgId; it[code] = id; it[name] = id
            it[modelCode] = model; it[gatewayId] = "GATEWAY"; it[protocolCode] = protocol
            it[externalDeviceId] = id; it[instanceConfig] = config; it[status] = EnumContentStatus.NORMAL
        }
        MuseumRfidDeviceProfiles.insert {
            it[MuseumRfidDeviceProfiles.id] = "PROFILE-$id"; it[MuseumRfidDeviceProfiles.orgId] = orgId
            it[deviceId] = id; it[role] = EnumRfidDeviceRole.DESK_READER
            it[MuseumRfidDeviceProfiles.hardwareSerial] = hardwareSerial
            it[MuseumRfidDeviceProfiles.enabled] = true; it[status] = EnumContentStatus.NORMAL
        }
    }
}
