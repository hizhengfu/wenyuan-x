package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CollectionReferenceQueryFiltersTest {

    @Test
    fun `movement query filters parse old-system style list conditions`() {
        val filters = MovementQueryFilters.from { key ->
            mapOf(
                "keyword" to "RK2026",
                "collectionId" to "col-1",
                "documentNo" to "RK2026-01",
                "type" to "IN",
                "reason" to "RESTORE",
                "operator" to "张三",
                "hasAttachment" to "true",
                "operatedAtStart" to "2026-06-01",
                "operatedAtEnd" to "2026-06-30",
            )[key]
        }

        assertEquals("RK2026", filters.keyword)
        assertEquals("col-1", filters.collectionId)
        assertEquals("RK2026-01", filters.documentNo)
        assertEquals(EnumMoveType.IN, filters.type)
        assertEquals(EnumMoveReason.RESTORE, filters.reason)
        assertEquals("张三", filters.operator)
        assertEquals(true, filters.hasAttachment)
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), filters.operatedAtStart)
        assertEquals(LocalDateTime.of(2026, 6, 30, 23, 59, 59, 999_999_999), filters.operatedAtEnd)
    }

    @Test
    fun `ledger log query filters parse action collection and date conditions`() {
        val filters = LedgerLogQueryFilters.from { key ->
            mapOf(
                "keyword" to "更正",
                "collectionId" to "col-2",
                "totalRegNo" to "0001",
                "refBizId" to "business-2",
                "action" to "CORRECT",
                "field" to "level",
                "createTimeStart" to "2026-05-01",
                "createTimeEnd" to "2026-05-31",
            )[key]
        }

        assertEquals("更正", filters.keyword)
        assertEquals("col-2", filters.collectionId)
        assertEquals("0001", filters.totalRegNo)
        assertEquals("business-2", filters.refBizId)
        assertEquals(EnumLedgerAction.CORRECT, filters.action)
        assertEquals("level", filters.field)
        assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), filters.createTimeStart)
        assertEquals(LocalDateTime.of(2026, 5, 31, 23, 59, 59, 999_999_999), filters.createTimeEnd)
    }

    @Test
    fun `reference query filters ignore invalid enum boolean and date values`() {
        val movement = MovementQueryFilters.from { key ->
            mapOf("type" to "BAD", "reason" to "NOPE", "hasAttachment" to "maybe", "operatedAtEnd" to "bad-date")[key]
        }
        val ledger = LedgerLogQueryFilters.from { key ->
            mapOf("action" to "WRONG", "createTimeStart" to "bad-date")[key]
        }

        assertNull(movement.type)
        assertNull(movement.reason)
        assertNull(movement.hasAttachment)
        assertNull(movement.operatedAtEnd)
        assertNull(ledger.action)
        assertNull(ledger.createTimeStart)
    }
}
