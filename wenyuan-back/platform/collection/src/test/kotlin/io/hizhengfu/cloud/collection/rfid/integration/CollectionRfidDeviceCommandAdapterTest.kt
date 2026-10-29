package io.hizhengfu.cloud.collection.rfid.integration

import io.hizhengfu.cloud.collection.rfid.api.RfidIngestCallbackController
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.museum.device.domain.EnumDeviceCommandStatus
import io.hizhengfu.cloud.museum.device.domain.EnumDeviceCommandType
import io.hizhengfu.cloud.museum.device.domain.MuseumDeviceCommandVo
import io.hizhengfu.cloud.museum.device.service.MuseumRfidDeviceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController

class CollectionRfidDeviceCommandAdapterTest {
    @Test
    fun `adapter is a collection component implementing the device command port`() {
        val adapterType = CollectionRfidDeviceCommandAdapter::class.java

        assertTrue(RfidDeviceCommandPort::class.java.isAssignableFrom(adapterType))
        assertNotNull(adapterType.getAnnotation(Component::class.java))
    }

    @Test
    fun `ingest callback is packaged as a collection controller`() {
        assertNotNull(RfidIngestCallbackController::class.java.getAnnotation(RestController::class.java))
    }

    @Test
    fun `command outcomes preserve terminal state success and device failure reason`() {
        val deviceService = mock(MuseumRfidDeviceService::class.java)
        val adapter = CollectionRfidDeviceCommandAdapter(deviceService)
        `when`(deviceService.findCommandForIntegration("ACKED")).thenReturn(command(EnumDeviceCommandStatus.ACKED))
        `when`(deviceService.findCommandForIntegration("SUCCEEDED")).thenReturn(command(EnumDeviceCommandStatus.SUCCEEDED))
        `when`(deviceService.findCommandForIntegration("FAILED")).thenReturn(
            command(EnumDeviceCommandStatus.FAILED, "等待设备应答超时"),
        )
        `when`(deviceService.findCommandForIntegration("TIMED_OUT")).thenReturn(
            command(EnumDeviceCommandStatus.TIMED_OUT, "命令超过业务有效期"),
        )
        `when`(deviceService.findCommandForIntegration("CANCELLED")).thenReturn(
            command(EnumDeviceCommandStatus.CANCELLED, "设备连接已关闭"),
        )

        val acknowledged = adapter.outcome("ACKED")!!
        assertFalse(acknowledged.terminal)
        assertFalse(acknowledged.successful)

        val succeeded = adapter.outcome("SUCCEEDED")!!
        assertTrue(succeeded.terminal)
        assertTrue(succeeded.successful)

        listOf(
            "FAILED" to "等待设备应答超时",
            "TIMED_OUT" to "命令超过业务有效期",
            "CANCELLED" to "设备连接已关闭",
        ).forEach { (id, expectedMessage) ->
            val outcome = adapter.outcome(id)!!
            assertTrue(outcome.terminal)
            assertFalse(outcome.successful)
            assertEquals(expectedMessage, outcome.message)
        }
    }

    private fun command(status: EnumDeviceCommandStatus, resultSummary: String? = null) = MuseumDeviceCommandVo(
        id = status.name,
        orgId = "ORG-1",
        deviceId = "RFID-DEVICE-1",
        commandType = EnumDeviceCommandType.START_INVENTORY,
        request = emptyMap(),
        status = status,
        requestedBy = "USER-1",
        workstationId = "WORKSTATION-1",
        businessType = "RFID_SCAN_SESSION",
        businessId = "SESSION-1",
        runtimeInstanceId = "RUNTIME-1",
        createdAt = 1L,
        dispatchedAt = 2L,
        finishedAt = 3L,
        expiresAt = 4L,
        resultCode = status.name,
        resultSummary = resultSummary,
    )
}
