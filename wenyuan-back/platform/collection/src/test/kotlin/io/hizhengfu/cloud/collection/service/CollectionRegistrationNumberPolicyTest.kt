package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumRegistrationNoState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionRegistrationNumberPolicyTest {

    @Test
    fun `next registration number skips numbers already occupied by ledger reservations`() {
        val next = CollectionRegistrationNumberPolicy.nextAvailable(
            existingCollectionNumbers = setOf("000001", "000003"),
            occupiedNumbers = setOf("000002"),
        )

        assertEquals("000004", next)
    }

    @Test
    fun `manual registration number cannot reuse retired or used numbers`() {
        val occupied = mapOf(
            "000001" to EnumRegistrationNoState.USED,
            "000002" to EnumRegistrationNoState.RETIRED,
        )

        assertFalse(CollectionRegistrationNumberPolicy.canUseManual("000001", occupied))
        assertFalse(CollectionRegistrationNumberPolicy.canUseManual("000002", occupied))
        assertTrue(CollectionRegistrationNumberPolicy.canUseManual("000003", occupied))
    }

    @Test
    fun `current collection can only keep registration number while it is still used`() {
        assertTrue(
            CollectionRegistrationNumberPolicy.canKeepCurrent(
                collectionId = "C1",
                holderCollectionId = "C1",
                state = EnumRegistrationNoState.USED,
            ),
        )
        assertFalse(
            CollectionRegistrationNumberPolicy.canKeepCurrent(
                collectionId = "C1",
                holderCollectionId = "C1",
                state = EnumRegistrationNoState.RETIRED,
            ),
        )
        assertFalse(
            CollectionRegistrationNumberPolicy.canKeepCurrent(
                collectionId = "C1",
                holderCollectionId = "C2",
                state = EnumRegistrationNoState.USED,
            ),
        )
    }
}
