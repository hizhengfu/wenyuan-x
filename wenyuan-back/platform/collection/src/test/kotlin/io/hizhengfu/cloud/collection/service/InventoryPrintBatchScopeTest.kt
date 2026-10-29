package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class InventoryPrintBatchScopeTest {

    @Test
    fun `same named batch includes only records from the same org and batch`() {
        val seed = inventory("seed", "org-a", "2026年度库房清查", 9)
        val sameBatchLater = inventory("same-later", "org-a", "2026年度库房清查", 11)
        val sameBatchEarlier = inventory("same-earlier", "org-a", "2026年度库房清查", 8)
        val otherBatch = inventory("other-batch", "org-a", "2026春季库房清查", 10)
        val otherOrg = inventory("other-org", "org-b", "2026年度库房清查", 10)
        val blankBatch = inventory("blank", "org-a", "", 10)

        val scoped = InventoryPrintBatchScope.filterBatch(
            seed = seed,
            seedOrgId = "org-a",
            candidates = listOf(otherBatch, sameBatchLater, blankBatch, seed, otherOrg, sameBatchEarlier),
            orgIdOf = { if (it.id == "other-org") "org-b" else "org-a" },
        )

        assertEquals(listOf("same-earlier", "seed", "same-later"), scoped.map { it.id })
    }

    @Test
    fun `blank batch prints only selected record`() {
        val seed = inventory("seed", "org-a", "", 9)
        val candidate = inventory("candidate", "org-a", "", 10)

        val scoped = InventoryPrintBatchScope.filterBatch(
            seed = seed,
            seedOrgId = "org-a",
            candidates = listOf(seed, candidate),
            orgIdOf = { "org-a" },
        )

        assertEquals(listOf("seed"), scoped.map { it.id })
    }

    private fun inventory(
        id: String,
        orgId: String,
        batchName: String,
        hour: Int,
    ) = InventoryVo(
        id = id,
        batchName = batchName,
        collectionId = "$orgId-$id",
        checkedAt = LocalDateTime.of(2026, 6, 26, hour, 0),
    )
}
