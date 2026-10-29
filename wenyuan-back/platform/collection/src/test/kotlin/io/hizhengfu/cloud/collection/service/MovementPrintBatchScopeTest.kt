package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MovementPrintBatchScopeTest {

    @Test
    fun `same document movement print includes only the same org document and type`() {
        val seed = movement("seed", "org-a", "RK202601", EnumMoveType.IN, 9)
        val sameDocumentLater = movement("same-later", "org-a", "RK202601", EnumMoveType.IN, 11)
        val sameDocumentEarlier = movement("same-earlier", "org-a", "RK202601", EnumMoveType.IN, 8)
        val otherType = movement("other-type", "org-a", "RK202601", EnumMoveType.OUT, 10)
        val otherOrg = movement("other-org", "org-b", "RK202601", EnumMoveType.IN, 10)
        val blankDocument = movement("blank", "org-a", "", EnumMoveType.IN, 10)

        val scoped = MovementPrintBatchScope.filterBatch(
            seed = seed,
            candidates = listOf(otherType, sameDocumentLater, blankDocument, seed, otherOrg, sameDocumentEarlier),
        )

        assertEquals(listOf("same-earlier", "seed", "same-later"), scoped.map { it.id })
    }

    @Test
    fun `blank document movement print falls back to the selected row only`() {
        val seed = movement("seed", "org-a", "", EnumMoveType.OUT, 9)
        val candidate = movement("candidate", "org-a", "", EnumMoveType.OUT, 10)

        val scoped = MovementPrintBatchScope.filterBatch(seed, listOf(seed, candidate))

        assertEquals(listOf("seed"), scoped.map { it.id })
    }

    private fun movement(
        id: String,
        orgId: String,
        documentNo: String,
        type: EnumMoveType,
        hour: Int,
    ) = MovementVo(
        id = id,
        orgId = orgId,
        documentNo = documentNo,
        type = type,
        operatedAt = LocalDateTime.of(2026, 6, 26, hour, 0),
    )
}
