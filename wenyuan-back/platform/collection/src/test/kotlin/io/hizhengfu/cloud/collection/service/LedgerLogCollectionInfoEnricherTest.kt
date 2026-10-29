package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.LedgerLogVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LedgerLogCollectionInfoEnricherTest {

    @Test
    fun `enrich fills collection name for ledger log rows`() {
        val rows = LedgerLogCollectionInfoEnricher.enrich(
            listOf(
                LedgerLogVo(id = "l1", collectionId = "c1", totalRegNo = "000001"),
                LedgerLogVo(id = "l2", collectionId = "missing", totalRegNo = "000002"),
            ),
            mapOf("c1" to LedgerLogCollectionInfo(name = "青花碗")),
        )

        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionName)
    }
}
