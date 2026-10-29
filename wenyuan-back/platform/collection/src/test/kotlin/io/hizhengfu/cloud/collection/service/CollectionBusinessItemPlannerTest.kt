package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionBusinessItemPlannerTest {

    @Test
    fun `plan builds distinct ordered collection detail rows for a business document`() {
        val plan = CollectionBusinessItemPlanner.plan(
            orgId = "org-1",
            bizType = CollectionConst.BIZ_REQUISITION,
            bizId = "req-1",
            collectionIds = "c2,c1,c2；c3\n",
        )

        assertEquals(listOf("c2", "c1", "c3"), plan.map { it.collectionId })
        assertEquals(listOf(0, 1, 2), plan.map { it.sort })
        assertEquals(setOf("req-1"), plan.map { it.bizId }.toSet())
        assertEquals(setOf(CollectionConst.BIZ_REQUISITION), plan.map { it.bizType }.toSet())
        assertEquals(setOf("org-1"), plan.map { it.orgId }.toSet())
    }
}
