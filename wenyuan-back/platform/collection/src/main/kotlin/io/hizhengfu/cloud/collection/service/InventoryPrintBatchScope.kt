package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryVo
import java.time.LocalDateTime

/** Builds the printable scope for one inventory batch. */
object InventoryPrintBatchScope {

    fun filterBatch(
        seed: InventoryVo,
        seedOrgId: String?,
        candidates: List<InventoryVo>,
        orgIdOf: (InventoryVo) -> String?,
    ): List<InventoryVo> {
        val batchName = seed.batchName?.trim().orEmpty()
        if (batchName.isBlank() || seedOrgId.isNullOrBlank()) return listOf(seed)

        val scoped = candidates
            .filter { candidate ->
                candidate.batchName?.trim() == batchName &&
                    orgIdOf(candidate) == seedOrgId
            }
            .let { items ->
                if (items.any { it.id == seed.id }) items else items + seed
            }
            .distinctBy { it.id ?: "${it.collectionId}:${it.batchName}:${it.checkedAt}" }

        return scoped.sortedWith(compareBy<InventoryVo> { it.checkedAt ?: LocalDateTime.MIN }.thenBy { it.id ?: "" })
    }
}
