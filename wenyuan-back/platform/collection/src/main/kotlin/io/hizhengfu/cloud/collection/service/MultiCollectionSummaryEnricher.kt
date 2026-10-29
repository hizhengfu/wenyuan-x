package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo

internal fun splitCollectionIds(value: String?): List<String> =
    value?.split(",", "，", ";", "；", "\n")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

object MultiCollectionSummaryEnricher {
    fun enrichExhibitions(items: List<ExhibitionVo>, summaryById: Map<String, CollectionSummaryVo>): List<ExhibitionVo> =
        items.map { item -> item.copy(collectionSummaries = summariesFor(item.collectionIds, summaryById)) }

    fun enrichLoans(items: List<LoanVo>, summaryById: Map<String, CollectionSummaryVo>): List<LoanVo> =
        items.map { item -> item.copy(collectionSummaries = summariesFor(item.collectionIds, summaryById)) }

    fun enrichRequisitions(items: List<RequisitionVo>, summaryById: Map<String, CollectionSummaryVo>): List<RequisitionVo> =
        items.map { item -> item.copy(collectionSummaries = summariesFor(item.collectionIds, summaryById)) }

    private fun summariesFor(collectionIds: String?, summaryById: Map<String, CollectionSummaryVo>): List<CollectionSummaryVo> =
        splitCollectionIds(collectionIds).mapNotNull { summaryById[it] }
}
