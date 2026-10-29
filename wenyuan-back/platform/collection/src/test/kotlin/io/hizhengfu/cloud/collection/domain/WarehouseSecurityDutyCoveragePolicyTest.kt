package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WarehouseSecurityDutyCoveragePolicyTest {
    private val day = LocalDate.of(2026, 9, 20)
    private val start = day.atStartOfDay()
    private val end = day.plusDays(1).atStartOfDay()
    private fun interval(from: Int, to: Int) = SecurityDutyInterval(start.plusHours(from.toLong()), start.plusHours(to.toLong()))
    private fun worked(from: Int, to: Int) = interval(from, to).let { WarehouseSecurityDutyCoverageSource(it, it.from, it.to) }
    private fun assess(vararg records: WarehouseSecurityDutyCoverageSource, now: LocalDateTime = end) =
        WarehouseSecurityDutyCoveragePolicy.assess(day, records.toList(), now)

    @Test fun `three adjoining shifts cover full day regardless of input order`() {
        val result = assess(worked(16, 24), worked(0, 8), worked(8, 16))
        assertEquals(emptyList<SecurityDutyInterval>(), result.plannedGaps)
        assertEquals(emptyList<SecurityDutyInterval>(), result.actualGaps)
        assertEquals(86400L, result.actualCoveredSeconds)
        assertTrue(result.actualDayComplete)
    }

    @Test fun `overlapping and duplicate shifts cannot conceal uncovered hours`() {
        val result = assess(worked(0, 12), worked(0, 12), worked(4, 8))
        assertEquals(listOf(interval(12, 24)), result.actualGaps)
        assertEquals(43200L, result.actualCoveredSeconds)
        assertFalse(result.actualDayComplete)
    }

    @Test fun `shifts crossing both day boundaries are clipped to the selected day`() {
        val result = assess(worked(-8, 8), worked(8, 16), worked(16, 32), now = end.plusHours(8))
        assertEquals(86400L, result.actualCoveredSeconds)
        assertTrue(result.actualDayComplete)
    }

    @Test fun `complete roster without attendance leaves actual whole day missing`() {
        val result = assess(WarehouseSecurityDutyCoverageSource(interval(0, 24)))
        assertTrue(result.plannedGaps.isEmpty())
        assertEquals(listOf(interval(0, 24)), result.actualGaps)
        assertEquals(0L, result.actualCoveredSeconds)
        assertFalse(result.actualDayComplete)
    }

    @Test fun `late arrival and early departure remain separate actual gaps`() {
        val result = assess(WarehouseSecurityDutyCoverageSource(interval(0, 24), start.plusHours(1), end.minusHours(1)))
        assertEquals(listOf(interval(0, 1), interval(23, 24)), result.actualGaps)
        assertTrue(result.plannedGaps.isEmpty())
    }

    @Test fun `open attendance covers only elapsed time and today cannot be complete`() {
        val result = assess(WarehouseSecurityDutyCoverageSource(interval(0, 24), start), now = start.plusHours(10))
        assertTrue(result.actualGaps.isEmpty())
        assertEquals(36000L, result.actualCoveredSeconds)
        assertEquals(start.plusHours(10), result.assessedThrough)
        assertFalse(result.actualDayComplete)
    }

    @Test fun `future day never reports completed actual coverage`() {
        val result = assess(WarehouseSecurityDutyCoverageSource(interval(0, 24)), now = start.minusHours(1))
        assertEquals(0L, result.actualCoveredSeconds)
        assertEquals(start, result.assessedThrough)
        assertFalse(result.actualDayComplete)
    }

    @Test fun `unclosed old attendance cannot prove completed day`() {
        val result = assess(WarehouseSecurityDutyCoverageSource(interval(0, 8), start), now = end.plusDays(2))
        assertFalse(result.actualDayComplete)
    }

    @Test fun `empty and unrelated records leave the whole day missing`() {
        for (records in listOf(emptyArray(), arrayOf(worked(-24, 0), worked(24, 48)))) {
            val result = assess(*records, now = end.plusDays(1))
            assertEquals(listOf(interval(0, 24)), result.plannedGaps)
            assertEquals(listOf(interval(0, 24)), result.actualGaps)
        }
    }

    @Test fun `one second handover gap is not rounded away`() {
        val result = assess(worked(0, 8), WarehouseSecurityDutyCoverageSource(interval(8, 24), start.plusHours(8).plusSeconds(1), end))
        assertEquals(listOf(SecurityDutyInterval(start.plusHours(8), start.plusHours(8).plusSeconds(1))), result.actualGaps)
        assertEquals(86399L, result.actualCoveredSeconds)
        assertFalse(result.actualDayComplete)
    }

    @Test fun `malformed or future actual times fail instead of yielding green coverage`() {
        val bad = listOf(
            WarehouseSecurityDutyCoverageSource(interval(8, 8)),
            WarehouseSecurityDutyCoverageSource(interval(8, 0)),
            WarehouseSecurityDutyCoverageSource(interval(0, 24), actualEnd = end),
            WarehouseSecurityDutyCoverageSource(interval(0, 24), end.plusSeconds(1)),
            WarehouseSecurityDutyCoverageSource(interval(0, 24), start, end.plusSeconds(1)),
            WarehouseSecurityDutyCoverageSource(interval(0, 24), start.plusHours(1), start),
            WarehouseSecurityDutyCoverageSource(interval(0, 24), start, start),
        )
        bad.forEach { assertThrows(BadRequestException::class.java) { assess(it) } }
    }
}
