package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReturnClosurePolicyTest {

    @Test
    fun `return closure requires approved and not returned flow`() {
        ReturnClosurePolicy.requireReturnAllowed(EnumFlowStatus.PASS, returned = false)

        assertThrows(BadRequestException::class.java) {
            ReturnClosurePolicy.requireReturnAllowed(EnumFlowStatus.AUDIT, returned = false)
        }
        assertThrows(BadRequestException::class.java) {
            ReturnClosurePolicy.requireReturnAllowed(EnumFlowStatus.PASS, returned = true)
        }
    }

    @Test
    fun `return closure requires collection in matching outbound status`() {
        ReturnClosurePolicy.requireCollectionStatus(EnumCollectionStatus.IN_USE, EnumCollectionStatus.IN_USE, "提用归还")

        assertThrows(BadRequestException::class.java) {
            ReturnClosurePolicy.requireCollectionStatus(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.IN_USE, "提用归还")
        }
        assertThrows(BadRequestException::class.java) {
            ReturnClosurePolicy.requireCollectionStatus(EnumCollectionStatus.IN_USE, EnumCollectionStatus.ON_LOAN, "借展返还")
        }
    }

    @Test
    fun `partial return outstanding excludes already returned items`() {
        val outstanding = PartialReturnPolicy.outstanding(listOf("a", "b", "c"), setOf("b"))
        org.junit.jupiter.api.Assertions.assertEquals(listOf("a", "c"), outstanding)
    }

    @Test
    fun `partial return subset defaults to available and rejects blocked items`() {
        val available = listOf("a", "c")
        org.junit.jupiter.api.Assertions.assertEquals(available, PartialReturnPolicy.resolveSubset(emptyList(), available))
        org.junit.jupiter.api.Assertions.assertEquals(listOf("c"), PartialReturnPolicy.resolveSubset(listOf("c"), available))

        assertThrows(BadRequestException::class.java) {
            PartialReturnPolicy.resolveSubset(listOf("b"), available)
        }
        assertThrows(BadRequestException::class.java) {
            PartialReturnPolicy.resolveSubset(emptyList(), emptyList())
        }
    }

    @Test
    fun `partial return closes source only when all items returned`() {
        val sourceIds = listOf("a", "b", "c")
        org.junit.jupiter.api.Assertions.assertFalse(PartialReturnPolicy.fullyReturned(sourceIds, emptySet(), listOf("a")))
        org.junit.jupiter.api.Assertions.assertFalse(PartialReturnPolicy.fullyReturned(sourceIds, setOf("a"), listOf("b")))
        org.junit.jupiter.api.Assertions.assertTrue(PartialReturnPolicy.fullyReturned(sourceIds, setOf("a", "b"), listOf("c")))
    }
}
