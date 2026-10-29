package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidRealtimeEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandOutcome
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.service.CollectionReturnService
import io.hizhengfu.cloud.collection.service.InventoryService
import io.hizhengfu.cloud.collection.service.LoanService
import io.hizhengfu.cloud.collection.service.RequisitionService
import io.hizhengfu.cloud.common.domain.SecurityUser
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class RfidScanSessionDeviceFailureRecoveryTest {

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `polling reconciles failed start and stop device commands into failed sessions`() {
        authenticate()
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-session-device-failure?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val commandPort = FailedDeviceCommandPort(
            mapOf(
                START_COMMAND_ID to "等待设备应答超时",
                STOP_COMMAND_ID to "连接在命令完成前关闭",
            ),
        )
        val scope = CollectionScopeResolver("single", "")
        val service = RfidScanSessionService(
            scope = scope,
            commandPort = commandPort,
            loanService = mock(LoanService::class.java),
            requisitionService = mock(RequisitionService::class.java),
            inventoryService = mock(InventoryService::class.java),
            returnService = mock(CollectionReturnService::class.java),
            realtime = RfidRealtimeEventService(scope),
            createdTimeoutSeconds = 300,
            commandTimeoutSeconds = 180,
            maxReadingSeconds = 14_400,
            previewTimeoutSeconds = 86_400,
        )

        transaction(database) {
            SchemaUtils.create(RfidScanSessions, RfidRealtimeEvents)
            insertSession("SESSION-START", EnumRfidScanSessionState.STARTING, startCommandId = START_COMMAND_ID)
            insertSession("SESSION-STOP", EnumRfidScanSessionState.STOPPING, stopCommandId = STOP_COMMAND_ID)

            val startSession = service.get("SESSION-START")
            val stopSession = service.get("SESSION-STOP")

            assertEquals(EnumRfidScanSessionState.FAILED, startSession.state)
            assertEquals("等待设备应答超时", startSession.errorMessage)
            assertEquals(EnumRfidScanSessionState.FAILED, stopSession.state)
            assertEquals("连接在命令完成前关闭", stopSession.errorMessage)
            assertEquals(2, RfidRealtimeEvents.selectAll().count())
            assertEquals(
                setOf(EnumRfidScanSessionState.FAILED),
                RfidScanSessions.selectAll().map { it[RfidScanSessions.sessionState] }.toSet(),
            )
        }
    }

    @Test
    fun `offline workstation reader cannot leave a reading session ready for business confirmation`() {
        authenticate()
        val database = Database.connect("jdbc:sqlite:file:rfid-local-device-offline?mode=memory&cache=shared", driver = "org.sqlite.JDBC")
        val scope = CollectionScopeResolver("single", "")
        val service = RfidScanSessionService(
            scope = scope, commandPort = FailedDeviceCommandPort(emptyMap(), localOffline = true),
            loanService = mock(LoanService::class.java), requisitionService = mock(RequisitionService::class.java),
            inventoryService = mock(InventoryService::class.java), returnService = mock(CollectionReturnService::class.java),
            realtime = RfidRealtimeEventService(scope), createdTimeoutSeconds = 300,
            commandTimeoutSeconds = 180, maxReadingSeconds = 14_400, previewTimeoutSeconds = 86_400,
        )
        transaction(database) {
            SchemaUtils.create(RfidScanSessions, RfidRealtimeEvents)
            insertSession("SESSION-LOCAL", EnumRfidScanSessionState.READING)
            insertSession("SESSION-WATCHDOG", EnumRfidScanSessionState.READING)
            assertEquals(EnumRfidScanSessionState.FAILED, service.get("SESSION-LOCAL").state)
            assertEquals(1, service.recoverStaleSessions())
            assertEquals(EnumRfidScanSessionState.FAILED, service.get("SESSION-WATCHDOG").state)
            assertEquals(2, RfidRealtimeEvents.selectAll().count())
        }
    }

    private fun insertSession(
        id: String,
        state: EnumRfidScanSessionState,
        startCommandId: String? = null,
        stopCommandId: String? = null,
    ) {
        val now = LocalDateTime.now()
        RfidScanSessions.insert {
            it[RfidScanSessions.id] = id
            it[orgId] = ORG_ID
            it[sessionNo] = "RFID-$id"
            it[workstationId] = "WORKSTATION-1"
            it[deviceId] = "RFID-DEVICE-1"
            it[sessionType] = EnumRfidScanSessionType.INVENTORY
            it[operatorId] = USER_ID
            it[directInput] = false
            it[sessionState] = state
            it[RfidScanSessions.startCommandId] = startCommandId
            it[RfidScanSessions.stopCommandId] = stopCommandId
            it[expectedCount] = 0
            it[readCount] = 0
            it[version] = 0
            it[createTime] = now
            it[createBy] = USER_ID
            it[updateTime] = now
            it[updateBy] = USER_ID
        }
    }

    private fun authenticate() {
        val user = SecurityUser(
            id = USER_ID,
            name = "RFID 测试用户",
            username = "rfid-test",
            password = "",
            groupIds = emptySet(),
            postIds = emptySet(),
            dataType = 0,
            roles = emptySet(),
            authorities = emptyList(),
            enabled = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    private class FailedDeviceCommandPort(
        private val messages: Map<String, String>,
        private val localOffline: Boolean = false,
    ) : RfidDeviceCommandPort {
        override fun startInventory(deviceId: String, sessionId: String, antennaMask: Long) = error("not expected")
        override fun stopInventory(deviceId: String, sessionId: String) = error("not expected")
        override fun writeEpc(deviceId: String, jobId: String, epc: String, tidFilter: String) = error("not expected")
        override fun syncAllowlist(deviceId: String, businessId: String, epcs: List<String>) = error("not expected")
        override fun supportsAllowlist(deviceId: String) = false
        override fun alarmOutput(deviceId: String, businessId: String, outputIndex: Int, durationMillis: Long) =
            error("not expected")

        override fun outcome(commandId: String): RfidDeviceCommandOutcome? = messages[commandId]?.let {
            RfidDeviceCommandOutcome(terminal = true, successful = false, message = it)
        }

        override fun allowsDirectInput(deviceId: String) = false
        override fun isWorkstationLocalDevice(deviceId: String) = localOffline
        override fun isOnline(deviceId: String) = !localOffline
    }

    private companion object {
        const val ORG_ID = "ORG-1"
        const val USER_ID = "USER-1"
        const val START_COMMAND_ID = "COMMAND-START-FAILED"
        const val STOP_COMMAND_ID = "COMMAND-STOP-FAILED"
    }
}
