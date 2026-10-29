package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.dao.CollectionTransferVo
import io.hizhengfu.cloud.collection.dao.DeregistrationVo
import io.hizhengfu.cloud.collection.dao.ReproductionVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProcessCollectionInfoEnricherTest {

    @Test
    fun `enrich fills collection registration number and name for appraisal rows`() {
        val rows = AppraisalCollectionInfoEnricher.enrich(
            listOf(AppraisalVo(id = "a1", collectionId = "c1"), AppraisalVo(id = "a2", collectionId = "missing")),
            mapOf("c1" to AppraisalCollectionInfo(totalRegNo = "000001", name = "青花碗")),
        )

        assertEquals("000001", rows[0].collectionTotalRegNo)
        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionTotalRegNo)
        assertNull(rows[1].collectionName)
    }

    @Test
    fun `enrich fills collection registration number and name for reproduction rows`() {
        val rows = ReproductionCollectionInfoEnricher.enrich(
            listOf(ReproductionVo(id = "r1", collectionId = "c1"), ReproductionVo(id = "r2", collectionId = "missing")),
            mapOf("c1" to ReproductionCollectionInfo(totalRegNo = "000001", name = "青花碗")),
        )

        assertEquals("000001", rows[0].collectionTotalRegNo)
        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionTotalRegNo)
        assertNull(rows[1].collectionName)
    }

    @Test
    fun `enrich fills collection registration number and name for deregistration rows`() {
        val rows = DeregistrationCollectionInfoEnricher.enrich(
            listOf(DeregistrationVo(id = "d1", collectionId = "c1"), DeregistrationVo(id = "d2", collectionId = "missing")),
            mapOf("c1" to DeregistrationCollectionInfo(totalRegNo = "000001", name = "青花碗")),
        )

        assertEquals("000001", rows[0].collectionTotalRegNo)
        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionTotalRegNo)
        assertNull(rows[1].collectionName)
    }

    @Test
    fun `enrich fills collection registration number and name for transfer rows`() {
        val rows = CollectionTransferInfoEnricher.enrich(
            listOf(CollectionTransferVo(id = "t1", collectionId = "c1"), CollectionTransferVo(id = "t2", collectionId = "missing")),
            mapOf("c1" to CollectionTransferInfo(totalRegNo = "000001", name = "青花碗")),
        )

        assertEquals("000001", rows[0].collectionTotalRegNo)
        assertEquals("青花碗", rows[0].collectionName)
        assertNull(rows[1].collectionTotalRegNo)
        assertNull(rows[1].collectionName)
    }
    @Test
    fun `executed legacy transfer without saved name does not invent one from current master`() {
        val row = CollectionTransferInfoEnricher.enrich(
            listOf(CollectionTransferVo(id="legacy",collectionId="c1",executedAt=java.time.LocalDateTime.of(2020,1,1,0,0))),
            mapOf("c1" to CollectionTransferInfo("000001","接收馆当前名称")),
        ).single()
        assertNull(row.collectionName)
    }

}
