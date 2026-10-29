package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InventoryCollectionInfoEnricherTest {

    @Test
    fun `enrich fills collection registration number and name for inventory rows`() {
        val rows = InventoryCollectionInfoEnricher.enrich(
            listOf(
                InventoryVo(id = "i1", collectionId = "c1"),
                InventoryVo(id = "i2", collectionId = "missing"),
            ),
            mapOf(
                "c1" to InventoryCollectionInfo(totalRegNo = "000001", name = "青花碗"),
            ),
        )

        assertEquals("000001", rows[0].collectionTotalRegNo)
        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionTotalRegNo)
        assertNull(rows[1].collectionName)
    }
}
