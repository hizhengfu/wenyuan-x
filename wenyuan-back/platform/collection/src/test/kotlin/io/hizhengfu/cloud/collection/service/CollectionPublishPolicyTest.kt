package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionPublishPolicyTest {

    @Test
    fun `only approved non deregistered collections can be published`() {
        CollectionPublishPolicy.requirePublishable(EnumCatalogStatus.APPROVED, EnumCollectionStatus.IN_STORE)

        assertThrows(BadRequestException::class.java) {
            CollectionPublishPolicy.requirePublishable(EnumCatalogStatus.PENDING, EnumCollectionStatus.IN_STORE)
        }
        assertThrows(BadRequestException::class.java) {
            CollectionPublishPolicy.requirePublishable(EnumCatalogStatus.APPROVED, EnumCollectionStatus.DEREGISTERED)
        }
    }
}
