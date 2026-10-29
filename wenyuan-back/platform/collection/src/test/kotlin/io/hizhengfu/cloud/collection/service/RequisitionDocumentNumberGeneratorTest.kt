package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RequisitionDocumentNumberGeneratorTest {

    @Test
    fun `blank requisition document number uses first sequence for the day`() {
        val next = RequisitionDocumentNumberGenerator.resolve(
            input = null,
            today = LocalDate.of(2026, 7, 1),
            existingForDay = emptySet(),
        )

        assertEquals("TY-20260701-0001", next)
    }

    @Test
    fun `blank requisition document number appends after existing day sequence`() {
        val next = RequisitionDocumentNumberGenerator.resolve(
            input = " ",
            today = LocalDate.of(2026, 7, 1),
            existingForDay = setOf("TY-20260701-0001", "TY-20260701-0003", "OLD-1"),
        )

        assertEquals("TY-20260701-0004", next)
    }

    @Test
    fun `manual requisition document number is trimmed and preserved`() {
        val next = RequisitionDocumentNumberGenerator.resolve(
            input = "  TY-MANUAL-1  ",
            today = LocalDate.of(2026, 7, 1),
            existingForDay = setOf("TY-20260701-0001"),
        )

        assertEquals("TY-MANUAL-1", next)
    }
}
