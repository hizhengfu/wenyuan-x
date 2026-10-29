package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidHandheldReadBatches
import io.hizhengfu.cloud.collection.rfid.dao.RfidRealtimeEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanExpectedItems
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanReads
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandOutcome
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadBatchVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanSessionCreateVo
import io.hizhengfu.cloud.collection.service.CollectionReturnService
import io.hizhengfu.cloud.collection.service.InventoryService
import io.hizhengfu.cloud.collection.service.LoanService
import io.hizhengfu.cloud.collection.service.RequisitionService
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class RfidHandheldReadBatchServiceTest {

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `retrying a workstation WebSocket batch does not repeat a fixed-reader observation`() {
        val database = Database.connect("jdbc:sqlite:file:rfid-workstation-batch?mode=memory&cache=shared", driver = "org.sqlite.JDBC")
        val scope = CollectionScopeResolver("single", "")
        val service = RfidScanSessionService(
            scope = scope, commandPort = NoopDeviceCommandPort,
            loanService = mock(LoanService::class.java), requisitionService = mock(RequisitionService::class.java),
            inventoryService = mock(InventoryService::class.java), returnService = mock(CollectionReturnService::class.java),
            realtime = RfidRealtimeEventService(scope), createdTimeoutSeconds = 300,
            commandTimeoutSeconds = 180, maxReadingSeconds = 14_400, previewTimeoutSeconds = 86_400,
        )
        transaction(database) {
            SchemaUtils.create(RfidTags, RfidScanSessions, RfidScanExpectedItems, RfidScanReads, RfidHandheldReadBatches, RfidRealtimeEvents)
            insertReadingSession(directInput = false)
            val reads = listOf(RfidHandheldReadVo(epc = "300833B2DDD9014000000001", tid = "E2000017221101441890ABCD", antenna = 1))
            val stream = "9cf346b1-f093-4b55-972f-4f68d6e93d07"
            assertEquals(1, service.recordDeviceReadBatch(SESSION_ID, DEVICE_ID, stream, 0, reads))
            assertEquals(1, service.recordDeviceReadBatch(SESSION_ID, DEVICE_ID, stream, 0, reads))
            assertEquals(1, RfidHandheldReadBatches.selectAll().count())
            assertEquals(1, RfidScanReads.selectAll().single()[RfidScanReads.readCount])
            assertEquals(1, RfidRealtimeEvents.selectAll().count())
        }
    }

    @Test
    fun `retrying the same handheld batch keeps reads and realtime events idempotent`() {
        authenticate("USER-1")
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-handheld-service?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val scope = CollectionScopeResolver("single", "")
        val service = RfidScanSessionService(
            scope = scope,
            commandPort = NoopDeviceCommandPort,
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
            SchemaUtils.create(
                RfidTags,
                RfidScanSessions,
                RfidScanExpectedItems,
                RfidScanReads,
                RfidHandheldReadBatches,
                RfidRealtimeEvents,
            )
            insertReadingSession()

            val batch = RfidHandheldReadBatchVo(
                deviceId = DEVICE_ID,
                localSessionId = "LOCAL-SESSION-1",
                batchSequence = 7,
                reads = listOf(
                    RfidHandheldReadVo(
                        epc = "300833B2DDD9014000000001",
                        tid = "E2000017221101441890ABCD",
                        antenna = 1,
                        direction = EnumRfidDirection.UNKNOWN,
                        rssi = -42,
                        occurredAt = System.currentTimeMillis(),
                    ),
                ),
            )

            val accepted = service.recordReadBatch(SESSION_ID, batch)
            val duplicate = service.recordReadBatch(SESSION_ID, batch)

            assertFalse(accepted.duplicate)
            assertEquals(1, accepted.accepted)
            assertTrue(duplicate.duplicate)
            assertEquals(1, RfidHandheldReadBatches.selectAll().count())
            assertEquals(1, RfidScanReads.selectAll().count())
            assertEquals(1, RfidRealtimeEvents.selectAll().count())
            assertEquals(1, duplicate.preview.unknown)
            assertEquals(1, duplicate.preview.reads.single().readCount)
            assertEquals(
                1,
                RfidScanSessions.selectAll().single()[RfidScanSessions.readCount],
            )

            authenticate("USER-2")
            val denied = assertThrows(BadRequestException::class.java) {
                service.stop(SESSION_ID)
            }
            assertTrue(denied.message.orEmpty().contains("领取任务的操作员"))
            assertEquals(
                EnumRfidScanSessionState.READING,
                RfidScanSessions.selectAll().single()[RfidScanSessions.sessionState],
            )
        }
    }

    @Test
    fun `legacy handheld request cannot attach itself to a fixed workstation`() {
        authenticate("USER-2")
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-handheld-session-idempotency?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val scope = CollectionScopeResolver("single", "")
        val service = RfidScanSessionService(
            scope = scope,
            commandPort = NoopDeviceCommandPort,
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
            SchemaUtils.create(RfidWorkstations, RfidWorkstationDeviceBindings, RfidScanSessions)
            insertHandheldWorkstation()
            insertReadingSession(clientRequestId = CLIENT_REQUEST_ID, businessId = "BATCH-1")

            val denied = assertThrows(BadRequestException::class.java) {
                service.create(
                    RfidScanSessionCreateVo(
                        workstationId = "WORKSTATION-1",
                        deviceId = DEVICE_ID,
                        type = EnumRfidScanSessionType.INVENTORY,
                        businessId = "BATCH-1",
                        clientRequestId = CLIENT_REQUEST_ID,
                        directInput = true,
                    ),
                )
            }

            assertTrue(denied.message.orEmpty().contains("移动端作业不关联固定工作站"))
            assertEquals(1, RfidScanSessions.selectAll().count())
        }
    }

    private fun insertReadingSession(clientRequestId: String? = null, businessId: String? = null, directInput: Boolean = true) {
        val now = LocalDateTime.now()
        RfidScanSessions.insert {
            it[id] = SESSION_ID
            it[orgId] = ORG_ID
            it[sessionNo] = "RFID-$SESSION_ID"
            it[workstationId] = "WORKSTATION-1"
            it[deviceId] = DEVICE_ID
            it[sessionType] = EnumRfidScanSessionType.INVENTORY
            it[RfidScanSessions.businessType] = "INVENTORY"
            it[RfidScanSessions.businessId] = businessId
            it[operatorId] = "USER-1"
            it[RfidScanSessions.directInput] = directInput
            it[sessionState] = EnumRfidScanSessionState.READING
            it[startedAt] = now
            it[expectedCount] = 0
            it[readCount] = 0
            it[RfidScanSessions.clientRequestId] = clientRequestId
            it[version] = 0
            it[createTime] = now
            it[createBy] = "USER-1"
            it[updateTime] = now
            it[updateBy] = "USER-1"
        }
    }

    private fun insertHandheldWorkstation() {
        val now = LocalDateTime.now()
        RfidWorkstations.insert {
            it[id] = "WORKSTATION-1"
            it[orgId] = ORG_ID
            it[code] = "RFID-WS-HANDHELD"
            it[name] = "手持机测试工作站"
            it[capabilitiesJson] = "[\"INVENTORY\"]"
            it[enrollmentTokenHash] = RfidHex.sha256("unused")
            it[enabled] = true
            it[createTime] = now
            it[createBy] = "USER-1"
            it[updateTime] = now
            it[updateBy] = "USER-1"
        }
        RfidWorkstationDeviceBindings.insert {
            it[id] = "BINDING-1"
            it[workstationId] = "WORKSTATION-1"
            it[deviceId] = DEVICE_ID
            it[purpose] = EnumRfidWorkstationDevicePurpose.RFID_READ
            it[isDefault] = true
            it[priority] = 0
            it[enabled] = true
            it[createTime] = now
            it[createBy] = "USER-1"
            it[updateTime] = now
            it[updateBy] = "USER-1"
        }
    }

    private fun authenticate(userId: String) {
        val user = SecurityUser(
            id = userId,
            name = "手持机测试用户",
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

    private object NoopDeviceCommandPort : RfidDeviceCommandPort {
        override fun startInventory(deviceId: String, sessionId: String, antennaMask: Long) = error("not expected")
        override fun stopInventory(deviceId: String, sessionId: String) = error("not expected")
        override fun writeEpc(deviceId: String, jobId: String, epc: String, tidFilter: String) = error("not expected")
        override fun syncAllowlist(deviceId: String, businessId: String, epcs: List<String>) = error("not expected")
        override fun supportsAllowlist(deviceId: String) = false
        override fun alarmOutput(deviceId: String, businessId: String, outputIndex: Int, durationMillis: Long) =
            error("not expected")
        override fun outcome(commandId: String): RfidDeviceCommandOutcome? = null
        override fun allowsDirectInput(deviceId: String) = true
    }

    private companion object {
        const val ORG_ID = "ORG-1"
        const val SESSION_ID = "SESSION-1"
        const val DEVICE_ID = "HANDHELD-1"
        const val CLIENT_REQUEST_ID = "CLIENT-REQUEST-1"
    }
}
