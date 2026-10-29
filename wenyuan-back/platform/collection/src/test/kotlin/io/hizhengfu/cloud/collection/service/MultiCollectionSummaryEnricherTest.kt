package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiCollectionSummaryEnricherTest {

    @Test
    fun `enrich fills ordered collection summaries for multi collection rows`() {
        val summaryById = mapOf(
            "c1" to CollectionSummaryVo(id = "c1", totalRegNo = "000001", name = "青花碗"),
            "c2" to CollectionSummaryVo(id = "c2", totalRegNo = "000002", name = "玉璧"),
        )

        val exhibitions = MultiCollectionSummaryEnricher.enrichExhibitions(
            listOf(ExhibitionVo(id = "e1", collectionIds = "c1,c2,missing")),
            summaryById,
        )
        val loans = MultiCollectionSummaryEnricher.enrichLoans(
            listOf(LoanVo(id = "l1", collectionIds = "c2,c1")),
            summaryById,
        )
        val requisitions = MultiCollectionSummaryEnricher.enrichRequisitions(
            listOf(RequisitionVo(id = "r1", collectionIds = "")),
            summaryById,
        )

        assertEquals(listOf("000001", "000002"), exhibitions[0].collectionSummaries.map { it.totalRegNo })
        assertEquals(listOf("玉璧", "青花碗"), loans[0].collectionSummaries.map { it.name })
        assertTrue(requisitions[0].collectionSummaries.isEmpty())
    }
}
