package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.MovementVo
import java.time.LocalDateTime

/** Builds the printable scope for one storage movement document. */
object MovementPrintBatchScope {

    fun filterBatch(seed: MovementVo, candidates: List<MovementVo>): List<MovementVo> {
        val documentNo = seed.documentNo?.trim().orEmpty()
        if (documentNo.isBlank()) return listOf(seed)

        val scoped = candidates
            .filter { candidate ->
                candidate.documentNo?.trim() == documentNo &&
                    candidate.orgId == seed.orgId &&
                    candidate.type == seed.type
            }
            .let { items ->
                if (items.any { it.id == seed.id }) items else items + seed
            }
            .distinctBy { it.id ?: "${it.orgId}:${it.documentNo}:${it.collectionId}:${it.operatedAt}" }

        return scoped.sortedWith(compareBy<MovementVo> { it.operatedAt ?: LocalDateTime.MIN }.thenBy { it.id ?: "" })
    }
}
