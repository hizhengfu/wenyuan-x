package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AcquisitionCollectionInfoEnricherTest {

    @Test
    fun `enrich fills registered collection registration number and name for acquisition rows`() {
        val rows = AcquisitionCollectionInfoEnricher.enrich(
            listOf(
                AcquisitionVo(id = "a1", collectionId = "c1"),
                AcquisitionVo(id = "a2", collectionId = "missing"),
                AcquisitionVo(id = "a3", collectionId = null),
            ),
            mapOf(
                "a1" to AcquisitionCollectionInfo(totalRegNo = "000001", name = "青花碗"),
            ),
        )

        assertEquals("000001", rows[0].collectionTotalRegNo)
        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionTotalRegNo)
        assertNull(rows[2].collectionName)
    }
}
