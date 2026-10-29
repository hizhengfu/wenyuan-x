package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionPlacements
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class CollectionUsageItemVo(
    val id: String,
    val orgId: String,
    val quantity: Int?,
    val unit: String?,
    val onLoan: Boolean,
    val onExhibition: Boolean,
    val warehouseId: String? = null,
    val inStore: Boolean = false,
)

data class CollectionUsageSummaryVo(
    val total: Int,
    val loanCount: Int,
    val exhibitionCount: Int,
    val quantityByUnit: Map<String, Long>,
    val loanQuantityByUnit: Map<String, Long>,
    val exhibitionQuantityByUnit: Map<String, Long>,
    val quantityUnverifiedTotal: Int,
    val items: List<CollectionUsageItemVo>,
)

internal object CollectionUsageSummaryBuilder {
    fun build(items: List<CollectionUsageItemVo>): CollectionUsageSummaryVo {
        val rows = items.distinctBy { it.orgId to it.id }
        fun quantities(values: List<CollectionUsageItemVo>) = values
            .filter { (it.quantity ?: 0) > 0 && !it.unit.isNullOrBlank() }
            .groupBy { it.unit!!.trim() }.mapValues { (_, group) -> group.sumOf { it.quantity!!.toLong() } }
        return CollectionUsageSummaryVo(rows.size, rows.count { it.onLoan }, rows.count { it.onExhibition },
            quantities(rows), quantities(rows.filter { it.onLoan }), quantities(rows.filter { it.onExhibition }),
            rows.count { (it.quantity ?: 0) <= 0 || it.unit.isNullOrBlank() }, rows)
    }
}

/** 展出只取已批准且实际布展到位的记录；提用状态和计划日期都不能代替实际展出。 */
@Component
@Transactional(readOnly = true)
class CollectionUsageSummaryService(private val scope: CollectionScopeResolver) {
    fun summary(at: LocalDateTime = LocalDateTime.now()): CollectionUsageSummaryVo {
        val orgIds = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.STAT)).orgIds
        if (orgIds.isEmpty()) return CollectionUsageSummaryBuilder.build(emptyList())
        val displayed = CollectionPlacements.selectAll().where {
            (CollectionPlacements.orgId inList orgIds) and
                (CollectionPlacements.sourceBizType eq CollectionConst.BIZ_EXHIBITION) and
                (CollectionPlacements.approved eq true)
        }.filter { row ->
            row[CollectionPlacements.validFrom]?.let { !it.isAfter(at) } == true &&
                row[CollectionPlacements.validTo]?.let { it.isAfter(at) } != false
        }.map { it[CollectionPlacements.orgId] to it[CollectionPlacements.collectionId] }.toSet()
        val rows = CollectionDao.find { Collections.orgId inList orgIds }
            .filter { it.registered && it.lifeStatus != EnumCollectionStatus.DEREGISTERED }
            .map { CollectionUsageItemVo(it.id.value, it.orgId, it.quantity, it.unit,
                it.lifeStatus == EnumCollectionStatus.ON_LOAN,
                it.lifeStatus == EnumCollectionStatus.IN_USE && (it.orgId to it.id.value) in displayed,
                it.warehouseId, it.lifeStatus == EnumCollectionStatus.IN_STORE) }
        return CollectionUsageSummaryBuilder.build(rows)
    }
}
