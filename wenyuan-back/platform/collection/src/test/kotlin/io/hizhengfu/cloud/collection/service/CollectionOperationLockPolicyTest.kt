package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionOperationLockPolicyTest {
    @Test
    fun `same business may reacquire but another approval conflicts`() {
        val existing = listOf(
            CollectionOperationLockRef("c1", "REQ", "r1"),
            CollectionOperationLockRef("c2", "LOAN", "l1"),
        )

        assertEquals(
            emptyList<CollectionOperationLockRef>(),
            CollectionOperationLockPolicy.conflicts(listOf("c1"), "REQ", "r1", existing),
        )
        assertEquals(
            listOf(existing[1]),
            CollectionOperationLockPolicy.conflicts(listOf("c2", "c3"), "REQ", "r1", existing),
        )
    }
}
