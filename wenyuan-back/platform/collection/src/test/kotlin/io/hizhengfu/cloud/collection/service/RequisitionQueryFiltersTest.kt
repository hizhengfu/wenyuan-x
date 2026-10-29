package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RequisitionQueryFiltersTest {
    @Test
    fun `source exhibition filters preserve exact business identity`() {
        val filters=RequisitionQueryFilters.from { mapOf("sourceBizType" to " COLLECTION_EXHIBITION ","sourceBizId" to " exhibition-one ")[it] }
        assertEquals("COLLECTION_EXHIBITION",filters.sourceBizType)
        assertEquals("exhibition-one",filters.sourceBizId)
    }

    @Test
    fun `requisition query filters parse document returned attachments and dates`() {
        val filters = RequisitionQueryFilters.from { key ->
            mapOf(
                "keyword" to "修复",
                "collectionId" to "col-1",
                "documentNo" to "TY-2026-01",
                "purpose" to "RESTORE",
                "department" to "保管部",
                "flowStatus" to "PASS",
                "returned" to "false",
                "hasAttachment" to "true",
                "expectReturnDateStart" to "2026-06-01",
                "expectReturnDateEnd" to "2026-06-30",
                "createTimeStart" to "2026-06-01",
                "createTimeEnd" to "2026-06-30",
            )[key]
        }

        assertEquals("修复", filters.keyword)
        assertEquals("col-1", filters.collectionId)
        assertEquals("TY-2026-01", filters.documentNo)
        assertEquals(EnumMoveReason.RESTORE, filters.purpose)
        assertEquals("保管部", filters.department)
        assertEquals(EnumFlowStatus.PASS, filters.flowStatus)
        assertEquals(false, filters.returned)
        assertEquals(true, filters.hasAttachment)
        assertEquals(LocalDate.of(2026, 6, 1), filters.expectReturnDateStart)
        assertEquals(LocalDate.of(2026, 6, 30), filters.expectReturnDateEnd)
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), filters.createTimeStart)
        assertEquals(LocalDateTime.of(2026, 6, 30, 23, 59, 59, 999_999_999), filters.createTimeEnd)
    }

    @Test
    fun `requisition query filters accept date picker millisecond values`() {
        val start = LocalDate.of(2026, 7, 2)
        val end = LocalDate.of(2026, 7, 20)
        val filters = RequisitionQueryFilters.from { key ->
            mapOf(
                "expectReturnDateStart" to start.toEpochMillis(),
                "expectReturnDateEnd" to end.toEpochMillis(),
            )[key]
        }

        assertEquals(start, filters.expectReturnDateStart)
        assertEquals(end, filters.expectReturnDateEnd)
    }

    @Test
    fun `requisition query filters ignore invalid enum boolean and date values`() {
        val filters = RequisitionQueryFilters.from { key ->
            mapOf(
                "purpose" to "NOPE",
                "flowStatus" to "ALSO_NOPE",
                "returned" to "maybe",
                "hasAttachment" to "unknown",
                "expectReturnDateStart" to "bad-date",
                "createTimeEnd" to "bad-date",
            )[key]
        }

        assertNull(filters.purpose)
        assertNull(filters.flowStatus)
        assertNull(filters.returned)
        assertNull(filters.hasAttachment)
        assertNull(filters.expectReturnDateStart)
        assertNull(filters.createTimeEnd)
    }

    @Test
    fun `attachment presence helper treats blank strings as absent`() {
        assertTrue(hasTextAttachment("file-1"))
        assertFalse(hasTextAttachment(""))
        assertFalse(hasTextAttachment("   "))
        assertFalse(hasTextAttachment(null))
    }

    private fun LocalDate.toEpochMillis(): String =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli().toString()
}
