package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RfidCheckPlannerTest {

    @Test
    fun `rfid check classifies match missing and abnormal`() {
        val plan = RfidCheckPlanner.plan(
            expectedTagged = setOf("a", "b", "c"),
            scanned = setOf("b", "c", "x"),
        )
        assertEquals(EnumInventoryResult.MISSING, plan["a"])
        assertEquals(EnumInventoryResult.MATCH, plan["b"])
        assertEquals(EnumInventoryResult.MATCH, plan["c"])
        assertEquals(EnumInventoryResult.ABNORMAL, plan["x"])
        assertEquals(4, plan.size)
    }

    @Test
    fun `rfid check with no scans marks all expected missing`() {
        val plan = RfidCheckPlanner.plan(setOf("a"), emptySet())
        assertEquals(mapOf("a" to EnumInventoryResult.MISSING), plan)
    }
}
