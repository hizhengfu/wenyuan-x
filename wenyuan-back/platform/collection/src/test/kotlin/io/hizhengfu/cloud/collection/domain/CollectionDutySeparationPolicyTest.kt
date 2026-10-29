package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionDutySeparationPolicyTest {
    @Test
    fun `ledger and storage permissions cannot cross duties`() {
        assertThrows(BadRequestException::class.java) {
            CollectionDutySeparationPolicy.requireLedgerOperation(listOf(CollectionConst.LEDGER_CREATE, CollectionConst.STORAGE))
        }
        assertThrows(BadRequestException::class.java) {
            CollectionDutySeparationPolicy.requireStorageOperation(listOf(CollectionConst.STORAGE, CollectionConst.LEDGER_CORRECT))
        }
        assertDoesNotThrow {
            CollectionDutySeparationPolicy.requireLedgerOperation(listOf(CollectionConst.LEDGER_CREATE))
            CollectionDutySeparationPolicy.requireStorageOperation(listOf(CollectionConst.STORAGE))
        }
        assertThrows(BadRequestException::class.java) {
            CollectionDutySeparationPolicy.requireStorageOperation(listOf("ADMIN", CollectionConst.LEDGER_CREATE))
        }
        assertThrows(BadRequestException::class.java) {
            CollectionDutySeparationPolicy.requireLedgerOperation(listOf("ADMIN", CollectionConst.STORAGE))
        }
    }

    @Test
    fun `manual movements require formal business execution`() {
        assertThrows(BadRequestException::class.java) {
            MovementExecutionPolicy.requireBusinessExecution()
        }
    }
}
