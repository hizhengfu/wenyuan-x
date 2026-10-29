package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AttachmentPresenceQueryFiltersTest {

    @Test
    fun `loan query filters parse attachment presence`() {
        val filters = LoanQueryFilters.from { key ->
            mapOf(
                "keyword" to "外展",
                "operator" to "operator-1",
                "reviewer" to "reviewer-1",
                "hasAttachment" to "true",
            )[key]
        }

        assertEquals("外展", filters.keyword)
        assertEquals("operator-1", filters.operator)
        assertEquals("reviewer-1", filters.reviewer)
        assertEquals(true, filters.hasAttachment)
    }

    @Test
    fun `inventory query filters parse attachment presence`() {
        val filters = InventoryQueryFilters.from { key ->
            mapOf(
                "keyword" to "清查",
                "hasAttachment" to "false",
            )[key]
        }

        assertEquals("清查", filters.keyword)
        assertEquals(false, filters.hasAttachment)
    }

    @Test
    fun `attachment filter ignores invalid boolean values`() {
        assertNull(LoanQueryFilters.from { "maybe" }.hasAttachment)
        assertNull(InventoryQueryFilters.from { "maybe" }.hasAttachment)
    }
}
