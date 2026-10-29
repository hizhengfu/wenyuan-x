package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryBatchPlannerTest {

    @Test
    fun `resolve reuses existing batch by normalized name within org`() {
        val decision = InventoryBatchPlanner.resolve(
            orgId = "org-1",
            batchName = " 2026年度清查 ",
            existingBatchIdsByName = mapOf("2026年度清查" to "batch-1"),
        )

        assertEquals("2026年度清查", decision.batchName)
        assertEquals("batch-1", decision.batchId)
        assertTrue(decision.reuseExisting)
    }

    @Test
    fun `resolve marks new normalized batch when none exists`() {
        val decision = InventoryBatchPlanner.resolve(
            orgId = "org-1",
            batchName = "2026年度清查",
            existingBatchIdsByName = emptyMap(),
        )

        assertEquals("2026年度清查", decision.batchName)
        assertEquals(null, decision.batchId)
        assertEquals(false, decision.reuseExisting)
    }
}
