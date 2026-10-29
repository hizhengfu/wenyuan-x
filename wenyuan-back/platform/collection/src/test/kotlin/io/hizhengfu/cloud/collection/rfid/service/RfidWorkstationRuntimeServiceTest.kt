package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.*
import io.hizhengfu.cloud.collection.rfid.integration.CollectionRfidDeviceRuntimeReader
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.domain.EnumRfidDeviceRole
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class RfidWorkstationRuntimeServiceTest {
    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `only bound same-org enabled peripherals are exposed and expired connections are offline`() = withDatabase {
        insertDevice("ONLINE", online = true)
        insertDevice("EXPIRED", online = false)
        insertDevice("DISABLED", enabled = false)
        insertDevice("HANDHELD", protocol = "ANDROID_SDK")
        insertDevice("SIKE", protocol = "SIKE_RFID_WS_V1")
        insertDevice("OTHER", orgId = "ORG-2")
        val reader = CollectionRfidDeviceRuntimeReader()
        val snapshots = reader.snapshots("ORG-1", setOf("ONLINE", "EXPIRED", "DISABLED", "HANDHELD", "SIKE", "OTHER"))
            .associateBy { it.deviceId }
        assertEquals(setOf("ONLINE", "EXPIRED", "DISABLED", "HANDHELD", "SIKE"), snapshots.keys)
        assertTrue(snapshots.getValue("SIKE").connectionManaged)
        assertEquals("ONLINE", snapshots.getValue("SIKE").connectionState)
        assertEquals("ONLINE", snapshots.getValue("ONLINE").connectionState)
        assertEquals("OFFLINE", snapshots.getValue("EXPIRED").connectionState)
        assertFalse(snapshots.getValue("DISABLED").enabled)
        assertEquals("OFFLINE", snapshots.getValue("DISABLED").connectionState)
        assertFalse(snapshots.getValue("HANDHELD").connectionManaged)
        assertNull(snapshots.getValue("HANDHELD").connectionState)
        assertNull(snapshots.getValue("HANDHELD").lastSeenAt)
        assertTrue(reader.snapshots("ORG-1", emptySet()).isEmpty())
        // 状态读取不修改实际连接投影或续租。
        assertTrue(MuseumDeviceConnections.selectAll().all { it[MuseumDeviceConnections.state] == EnumRfidConnectionState.ONLINE })
    }

    @Test
    fun `runtime authenticates the browser and operator before reading only that workstation`() = withDatabase {
        insertStation()
        insertDevice("BOUND")
        insertDevice("UNBOUND")
        insertBinding("BOUND", enabled = true)
        insertBinding("UNBOUND", enabled = false)
        authenticate(allowed = true)
        val runtime = runtimeService().runtime("browser-credential")
        assertEquals("WS-1", runtime.workstationId)
        assertEquals("运行态测试员", runtime.operatorName)
        assertEquals(listOf("BOUND"), runtime.peripherals.map { it.binding.deviceId })
        assertEquals("设备 BOUND", runtime.peripherals.single().device?.deviceName)
        assertTrue(runtime.checkedAt > 0)
        assertThrows(BadRequestException::class.java) { runtimeService().runtime("invalid") }
        authenticate(allowed = false)
        assertThrows(BadRequestException::class.java) { runtimeService().runtime("browser-credential") }
    }

    @Test
    fun `missing device integration reports unavailable metadata instead of claiming online`() = withDatabase {
        insertStation()
        insertBinding("BOUND", enabled = true)
        authenticate(allowed = true)
        assertNull(runtimeService(integrated = false).runtime("browser-credential").peripherals.single().device)
    }

    private fun withDatabase(block: () -> Unit) {
        val database = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        transaction(database) {
            SchemaUtils.create(RfidWorkstations, RfidWorkstationDeviceBindings, MuseumDevices, MuseumRfidDeviceProfiles, MuseumDeviceConnections)
            block()
        }
    }

    private fun insertStation() {
        val now = museumUtcNow()
        RfidWorkstations.insert {
            it[id] = "WS-1"; it[orgId] = "ORG-1"; it[code] = "RFID-WS-1"; it[name] = "工作站一"
            it[capabilitiesJson] = "[\"BORROW_RETURN\"]"
            it[enrollmentTokenHash] = RfidHex.sha256("unused")
            it[browserCredentialHash] = RfidHex.sha256("browser-credential")
            it[enabled] = true; it[createTime] = now; it[updateTime] = now
        }
    }

    private fun insertBinding(device: String, enabled: Boolean) {
        RfidWorkstationDeviceBindings.insert {
            it[id] = "BIND-$device"; it[workstationId] = "WS-1"; it[deviceId] = device
            it[purpose] = EnumRfidWorkstationDevicePurpose.RFID_READ; it[isDefault] = true
            it[priority] = 0; it[RfidWorkstationDeviceBindings.enabled] = enabled
        }
    }

    private fun insertDevice(id: String, orgId: String = "ORG-1", online: Boolean = true, enabled: Boolean = true, protocol: String = "CK_RFID_C5_V1") {
        val now = museumUtcNow()
        MuseumDevices.insert {
            it[MuseumDevices.id] = id; it[MuseumDevices.orgId] = orgId; it[code] = id; it[name] = "设备 $id"
            it[modelCode] = "MODEL"; it[gatewayId] = "GATEWAY"; it[protocolCode] = protocol; it[externalDeviceId] = id
        }
        MuseumRfidDeviceProfiles.insert {
            it[MuseumRfidDeviceProfiles.id] = "PROFILE-$id"; it[MuseumRfidDeviceProfiles.orgId] = orgId
            it[deviceId] = id; it[role] = EnumRfidDeviceRole.DESK_READER
            it[MuseumRfidDeviceProfiles.enabled] = enabled; it[status] = EnumContentStatus.NORMAL
        }
        MuseumDeviceConnections.insert {
            it[MuseumDeviceConnections.id] = "CONN-$id"; it[deviceId] = id
            it[runtimeInstanceId] = "runtime-secret"; it[connectionId] = "socket-secret"; it[remoteAddress] = "private-address"
            it[connectedAt] = now; it[lastSeenAt] = now
            it[state] = EnumRfidConnectionState.ONLINE; it[leaseUntil] = if (online) now.plusMinutes(1) else now.minusSeconds(1)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun runtimeService(integrated: Boolean = true): RfidWorkstationRuntimeService {
        val provider = mock(ObjectProvider::class.java) as ObjectProvider<RfidDeviceRuntimePort>
        if (integrated) `when`(provider.ifAvailable).thenReturn(CollectionRfidDeviceRuntimeReader())
        return RfidWorkstationRuntimeService(
            RfidWorkstationService(
                CollectionScopeResolver("single", "ORG-1"),
                mock(ObjectProvider::class.java) as ObjectProvider<RfidCardTokenIssuer>,
                mock(ObjectProvider::class.java) as ObjectProvider<RfidDeviceCommandPort>,
                45,
            ),
            provider,
        )
    }

    private fun authenticate(allowed: Boolean) {
        val permissions = if (allowed) listOf(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN, CollectionConst.RFID_FINALIZE) else emptyList()
        val user = SecurityUser(
            id = "USER-1", name = "运行态测试员", username = "operator", password = "unused",
            groupIds = emptySet(), postIds = emptySet(), dataType = 0, roles = emptySet(),
            authorities = permissions.map(::SimpleGrantedAuthority), enabled = true,
        )
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
}
