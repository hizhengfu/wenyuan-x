package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WarehouseEntryTimePolicyTest {
    private val start = LocalDateTime.parse("2026-08-21T09:00:00")
    private val end = LocalDateTime.parse("2026-08-21T11:00:00")

    @Test
    fun `approved application can check in during authorized window`() {
        assertDoesNotThrow {
            WarehouseEntryTimePolicy.requireCheckInAllowed(
                EnumFlowStatus.PASS,
                start,
                end,
                null,
                start,
            )
        }
    }

    @Test
    fun `unapproved or expired application cannot check in`() {
        assertThrows(BadRequestException::class.java) { WarehouseEntryTimePolicy.requireCheckInAllowed(EnumFlowStatus.PASS,start,end,null,start.minusSeconds(1)) }
        assertThrows(BadRequestException::class.java) {
            WarehouseEntryTimePolicy.requireCheckInAllowed(EnumFlowStatus.AUDIT, start, end, null, start)
        }
        assertThrows(BadRequestException::class.java) {
            WarehouseEntryTimePolicy.requireCheckInAllowed(EnumFlowStatus.PASS, start, end, null, end.plusSeconds(1))
        }
    }

    @Test
    fun `check out requires a prior check in and monotonic time`() {
        assertThrows(BadRequestException::class.java) {
            WarehouseEntryTimePolicy.requireCheckOutAllowed(null, null, end)
        }
        assertThrows(BadRequestException::class.java) {
            WarehouseEntryTimePolicy.requireCheckOutAllowed(start, null, start.minusSeconds(1))
        }
        assertDoesNotThrow {
            WarehouseEntryTimePolicy.requireCheckOutAllowed(start, null, end)
        }
    }
}
