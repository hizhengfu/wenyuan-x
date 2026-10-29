package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardTokenIssuer
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidOperatorCardSaveVo
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.beans.factory.ObjectProvider
import java.time.LocalDateTime

class RfidWorkstationLeaseServiceTest {

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `one browser window holds the lease until release or explicit takeover`() {
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-workstation-lease?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val service = service()

        transaction(database) {
            SchemaUtils.create(RfidWorkstations, RfidWorkstationDeviceBindings)
            insertWorkstation()

            val first = service.acquireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-A")
            assertEquals(WORKSTATION_ID, first.workstationId)
            assertTrue(first.leaseUntil > System.currentTimeMillis())
            assertDoesNotThrow {
                service.requireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-A", WORKSTATION_ID)
            }

            val conflict = assertThrows(BadRequestException::class.java) {
                service.acquireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B")
            }
            assertTrue(conflict.message.orEmpty().contains("另一窗口"))

            service.acquireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B", force = true)
            assertThrows(BadRequestException::class.java) {
                service.requireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-A", WORKSTATION_ID)
            }
            assertDoesNotThrow {
                service.requireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B", WORKSTATION_ID)
            }

            assertTrue(service.releaseBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B"))
            assertThrows(BadRequestException::class.java) {
                service.requireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B", WORKSTATION_ID)
            }
        }
    }

    @Test
    fun `live Sike connection prevents forced takeover until the old reader connection closes`() {
        val database = Database.connect("jdbc:sqlite:file:rfid-sike-takeover?mode=memory&cache=shared", driver = "org.sqlite.JDBC")
        val service = service()
        transaction(database) {
            SchemaUtils.create(RfidWorkstations, RfidWorkstationDeviceBindings, MuseumDevices, MuseumDeviceConnections)
            insertWorkstation()
            RfidWorkstationDeviceBindings.insert {
                it[id] = "BIND-1"; it[workstationId] = WORKSTATION_ID; it[deviceId] = "SIKE-1"
                it[purpose] = EnumRfidWorkstationDevicePurpose.RFID_READ; it[isDefault] = true
                it[priority] = 0; it[enabled] = true
            }
            MuseumDevices.insert {
                it[id] = "SIKE-1"; it[orgId] = "ORG-1"; it[code] = "SIKE-1"; it[name] = "斯科读写器"
                it[modelCode] = "SIKE_C5_WS_READER_V1"; it[gatewayId] = "GATEWAY";
                it[protocolCode] = "SIKE_RFID_WS_V1"; it[externalDeviceId] = "SIKE-1"
            }
            val now = museumUtcNow()
            MuseumDeviceConnections.insert {
                it[id] = "CONN-1"; it[deviceId] = "SIKE-1"
                it[runtimeInstanceId] = rfidWorkstationRuntimeId(BROWSER_CREDENTIAL, "WINDOW-A")
                it[connectionId] = "SOCKET-A"; it[remoteAddress] = "WORKSTATION_LOCAL"
                it[connectedAt] = now; it[lastSeenAt] = now
                it[state] = EnumRfidConnectionState.ONLINE; it[leaseUntil] = now.plusSeconds(35)
            }
            service.acquireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-A")
            val denied = assertThrows(BadRequestException::class.java) {
                service.acquireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B", force = true)
            }
            assertTrue(denied.message.orEmpty().contains("斯科读写器"))
            MuseumDeviceConnections.update({ MuseumDeviceConnections.deviceId eq "SIKE-1" }) {
                it[state] = EnumRfidConnectionState.OFFLINE
            }
            assertEquals(WORKSTATION_ID, service.acquireBrowserLease(BROWSER_CREDENTIAL, "WINDOW-B", force = true).workstationId)
        }
    }

    @Test
    fun `password operator is authorized against the bound workstation capabilities`() {
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-workstation-password-access?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val service = service()

        transaction(database) {
            SchemaUtils.create(RfidWorkstations)
            insertWorkstation()

            authenticate(CollectionConst.RFID_DESK)
            val denied = assertThrows(BadRequestException::class.java) {
                service.authorizeCurrentOperator(BROWSER_CREDENTIAL)
            }
            assertTrue(denied.message.orEmpty().contains("工作站所需权限"))

            authenticate(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN)
            val finalizeDenied = assertThrows(BadRequestException::class.java) {
                service.authorizeCurrentOperator(BROWSER_CREDENTIAL)
            }
            assertTrue(finalizeDenied.message.orEmpty().contains("工作站所需权限"))

            authenticate(
                CollectionConst.RFID_DESK,
                CollectionConst.SELECT,
                CollectionConst.RFID_SCAN,
                CollectionConst.RFID_FINALIZE,
            )
            val context = service.authorizeCurrentOperator(BROWSER_CREDENTIAL)
            assertEquals(WORKSTATION_ID, context.workstationId)
            assertTrue(context.enabled)

            val crossOrgDenied = assertThrows(BadRequestException::class.java) {
                service(mode = "multi").authorizeCurrentOperator(BROWSER_CREDENTIAL)
            }
            assertTrue(crossOrgDenied.message.orEmpty().contains("无权操作该 RFID 工作站"))
        }
    }

    @Test
    fun `operator card cannot silently expand an empty capability selection to every capability`() {
        val error = assertThrows(BadRequestException::class.java) {
            service(defaultOrgId = "ORG-1").saveOperatorCard(
                RfidOperatorCardSaveVo(
                    cardUid = "AABBCCDD",
                    userId = "USER-1",
                    allowedCapabilities = emptySet(),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("至少选择一个"))
    }

    private fun insertWorkstation() {
        val now = LocalDateTime.now()
        RfidWorkstations.insert {
            it[id] = WORKSTATION_ID
            it[orgId] = "ORG-1"
            it[code] = "RFID-WS-LEASE"
            it[name] = "租约测试工作站"
            it[capabilitiesJson] = "[\"BORROW_RETURN\"]"
            it[enrollmentTokenHash] = RfidHex.sha256("unused-token")
            it[browserCredentialHash] = RfidHex.sha256(BROWSER_CREDENTIAL)
            it[enabled] = true
            it[createTime] = now
            it[createBy] = "USER-1"
            it[updateTime] = now
            it[updateBy] = "USER-1"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun service(mode: String = "single", defaultOrgId: String = "") = RfidWorkstationService(
        scope = CollectionScopeResolver(mode, defaultOrgId),
        tokenIssuerProvider = mock(ObjectProvider::class.java) as ObjectProvider<RfidCardTokenIssuer>,
        devicePortProvider = mock(ObjectProvider::class.java) as ObjectProvider<RfidDeviceCommandPort>,
        workstationLeaseSeconds = 45,
    )

    private fun authenticate(vararg permissions: String) {
        val user = SecurityUser(
            id = "USER-1",
            name = "测试操作员",
            username = "rfid-operator",
            password = "unused",
            groupIds = emptySet(),
            postIds = emptySet(),
            dataType = 0,
            roles = emptySet(),
            authorities = permissions.map(::SimpleGrantedAuthority),
            enabled = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    private companion object {
        const val WORKSTATION_ID = "WORKSTATION-1"
        const val BROWSER_CREDENTIAL = "browser-credential"
    }
}
