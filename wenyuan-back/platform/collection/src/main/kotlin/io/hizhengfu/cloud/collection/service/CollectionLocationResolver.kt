package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionPlacementDao
import io.hizhengfu.cloud.collection.dao.CollectionPlacements
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 统一解析某个位置在指定时刻实际包含的藏品。
 *
 * 保管位置读取在库藏品的 warehouseId；展陈位置读取已审批且在有效期内的 placement。
 * 查询上层节点时会包含整棵下级位置树。
 */
@Component
@Transactional(readOnly = true)
class CollectionLocationResolver(private val overrides: List<CollectionLocationOverrideProvider> = emptyList()) {

    /** 一次解析同馆多个有效位置，重叠子树、展陈及应急流水在本轮共享读取。 */
    fun collectionsAt(orgId: String, locationIds: Collection<String>, at: LocalDateTime = LocalDateTime.now()): Map<String, List<LocatedCollection>> =
        resolveLocationCollections(orgId, locationIds, at, overrides)

    fun collectionIdsAt(locationId: String, at: LocalDateTime = LocalDateTime.now()): List<String> {
        val root = WarehouseDao.findById(locationId) ?: return emptyList()
        val orgId = root.orgId
        val locationIds = WarehouseTreeStructure.subtree(root).map { it.id.value }

        val storedIds = CollectionDao.find {
            (Collections.orgId eq orgId) and (Collections.warehouseId inList locationIds) and
                (Collections.lifeStatus eq EnumCollectionStatus.IN_STORE)
        }.map { it.id.value }

        val displayedIds = CollectionPlacementDao.find {
            (CollectionPlacements.orgId eq orgId) and (CollectionPlacements.locationId inList locationIds) and
                (CollectionPlacements.approved eq true)
        }.filter { placement ->
            val startsInTime = placement.validFrom?.let { !at.isBefore(it) } ?: false
            val endsInTime = placement.validTo?.let { !at.isAfter(it) } ?: true
            startsInTime && endsInTime
        }.map { it.collectionId }

        val resolved = overrides.fold((storedIds+displayedIds).toSet()) {ids,provider->provider.resolve(orgId,locationIds.toSet(),at,ids)}
        if (resolved.isEmpty()) return emptyList()
        return CollectionDao.find { (Collections.orgId eq orgId) and (Collections.id inList resolved) }.map { it.id.value }
    }

    fun texturesAt(locationId: String, at: LocalDateTime = LocalDateTime.now()): Set<String> {
        val ids = collectionIdsAt(locationId, at)
        if (ids.isEmpty()) return emptySet()
        return CollectionDao.find { Collections.id inList ids }
            .mapNotNull { it.texture?.takeIf(String::isNotBlank) }
            .toSet()
    }

    internal fun descendantLocationIds(locationId: String): List<String> {
        val root = WarehouseDao.findById(locationId) ?: return emptyList()
        return WarehouseTreeStructure.subtree(root).map { it.id.value }
    }
}
