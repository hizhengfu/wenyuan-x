package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionOutboundPolicyTest {

    @Test
    fun `outbound business requires collection currently in store`() {
        CollectionOutboundPolicy.requireInStore(EnumCollectionStatus.IN_STORE, "提用")

        listOf(
            EnumCollectionStatus.PENDING,
            EnumCollectionStatus.IN_USE,
            EnumCollectionStatus.RESTORING,
            EnumCollectionStatus.ON_LOAN,
            EnumCollectionStatus.MISSING,
            EnumCollectionStatus.DEREGISTERED,
        ).forEach { status ->
            assertThrows(BadRequestException::class.java) {
                CollectionOutboundPolicy.requireInStore(status, "提用")
            }
        }
    }
}
