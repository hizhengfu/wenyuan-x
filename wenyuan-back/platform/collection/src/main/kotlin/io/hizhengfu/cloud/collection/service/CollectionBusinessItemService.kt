package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionBusinessItemDao
import io.hizhengfu.cloud.collection.dao.CollectionBusinessItems
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CollectionBusinessItemPlan(
    val orgId: String,
    val bizType: String,
    val bizId: String,
    val collectionId: String,
    val sort: Int,
)

object CollectionBusinessItemPlanner {
    fun plan(orgId: String, bizType: String, bizId: String, collectionIds: String?): List<CollectionBusinessItemPlan> =
        splitCollectionIds(collectionIds)
            .distinct()
            .mapIndexed { index, collectionId ->
                CollectionBusinessItemPlan(
                    orgId = orgId,
                    bizType = bizType,
                    bizId = bizId,
                    collectionId = collectionId,
                    sort = index,
                )
            }
}

@Component
@Transactional
class CollectionBusinessItemService {
    fun bind(orgId: String, bizType: String, bizId: String, collectionIds: String?) {
        CollectionBusinessItemDao.find {
            (CollectionBusinessItems.orgId eq orgId) and
                (CollectionBusinessItems.bizType eq bizType) and
                (CollectionBusinessItems.bizId eq bizId)
        }.forEach { it.delete() }

        CollectionBusinessItemPlanner.plan(orgId, bizType, bizId, collectionIds).forEach { item ->
            CollectionBusinessItemDao.new {
                this.orgId = item.orgId
                this.bizType = item.bizType
                this.bizId = item.bizId
                this.collectionId = item.collectionId
                this.sort = item.sort
            }
        }
    }

    fun businessIdsForCollections(bizType: String, collectionIds: Collection<String>): Set<String> {
        val ids = collectionIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.toSet()
        if (ids.isEmpty()) return emptySet()
        return CollectionBusinessItemDao.find {
            (CollectionBusinessItems.bizType eq bizType) and
                (CollectionBusinessItems.collectionId inList ids)
        }.map { it.bizId }.toSet()
    }

    fun collectionIdsForBusiness(bizType: String, bizId: String): List<String> =
        CollectionBusinessItemDao.find {
            (CollectionBusinessItems.bizType eq bizType) and
                (CollectionBusinessItems.bizId eq bizId)
        }
            .orderBy(CollectionBusinessItems.sort to SortOrder.ASC)
            .map { it.collectionId }
}
