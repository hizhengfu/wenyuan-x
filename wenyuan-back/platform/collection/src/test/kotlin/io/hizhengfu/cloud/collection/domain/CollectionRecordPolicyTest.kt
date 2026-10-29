package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionRecordPolicyTest {

    @Test
    fun `draft or rejected catalog can be edited before active circulation`() {
        assertDoesNotThrow {
            CollectionRecordPolicy.requireEditable(EnumCatalogStatus.DRAFT, EnumCollectionStatus.PENDING)
            CollectionRecordPolicy.requireEditable(EnumCatalogStatus.REJECTED, EnumCollectionStatus.IN_STORE)
        }
    }

    @Test
    fun `approved or circulating collection cannot use ordinary edit`() {
        assertThrows(BadRequestException::class.java) {
            CollectionRecordPolicy.requireEditable(EnumCatalogStatus.APPROVED, EnumCollectionStatus.IN_STORE)
        }
        assertThrows(BadRequestException::class.java) {
            CollectionRecordPolicy.requireEditable(EnumCatalogStatus.SUBMITTED, EnumCollectionStatus.PENDING)
        }
        assertThrows(BadRequestException::class.java) {
            CollectionRecordPolicy.requireEditable(EnumCatalogStatus.PENDING, EnumCollectionStatus.PENDING)
        }
        assertThrows(BadRequestException::class.java) {
            CollectionRecordPolicy.requireEditable(EnumCatalogStatus.REJECTED, EnumCollectionStatus.ON_LOAN)
        }
    }

    @Test
    fun `catalog rejection preserves current storage state`() {
        assertEquals(
            EnumCollectionStatus.IN_STORE,
            CollectionRecordPolicy.lifeStatusAfterCatalogDecision(
                EnumCollectionStatus.IN_STORE,
                EnumCatalogStatus.REJECTED,
            ),
        )
        assertEquals(
            EnumCollectionStatus.IN_STORE,
            CollectionRecordPolicy.lifeStatusAfterCatalogDecision(
                EnumCollectionStatus.PENDING,
                EnumCatalogStatus.APPROVED,
            ),
        )
    }
}
