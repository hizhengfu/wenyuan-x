package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionPlacements
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.Warehouses
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.time.LocalDateTime

/** 当前事务的位置解析结果；不把 Exposed 实体或跨轮缓存传给编译器。 */
data class LocatedCollection(val id: String, val texture: String?, val status: EnumContentStatus)

internal fun resolveLocationCollections(
    orgId: String,
    requestedIds: Collection<String>,
    at: LocalDateTime,
    overrides: List<CollectionLocationOverrideProvider>,
): Map<String, List<LocatedCollection>> {
    if (requestedIds.isEmpty()) return emptyMap()
    // 同事务内刚修改的材质、位置和流水也必须参与本次编译。
    TransactionManager.current().entityCache.flush()
    val columns = listOf(Warehouses.id, Warehouses.parentId, Warehouses.orgId, Warehouses.sceneType, Warehouses.status)
    val roots = requestedIds.distinct().chunked(200).flatMap { batch ->
        Warehouses.select(columns).where {
            (Warehouses.id inList batch) and (Warehouses.orgId eq orgId) and
                (Warehouses.status eq EnumContentStatus.NORMAL)
        }.toList()
    }.associateBy { it[Warehouses.id].value }
    if (roots.isEmpty()) return emptyMap()

    // 多个根共享一次逐层读取；按真实 parentId 校验，不信任派生的 parentIds。
    val nodes = roots.toMutableMap()
    val children = mutableMapOf<String, MutableSet<String>>()
    val loaded = hashSetOf<String>()
    var pending = roots.keys.toList()
    while (pending.isNotEmpty()) {
        loaded += pending
        val next = linkedSetOf<String>()
        pending.chunked(200).forEach { batch ->
            Warehouses.select(columns).where { Warehouses.parentId inList batch }.forEach { child ->
                val parentId = child[Warehouses.parentId]
                val parent = nodes.getValue(parentId)
                if (child[Warehouses.orgId] != orgId || child[Warehouses.sceneType] != parent[Warehouses.sceneType]) {
                    throw BadRequestException("位置分支存在其他收藏单位或其他场景的下级，请先核对结构")
                }
                val id = child[Warehouses.id].value
                nodes[id] = child
                children.getOrPut(parentId) { linkedSetOf() } += id
                if (id !in loaded) next += id
            }
        }
        pending = next.toList()
    }
    val descendants = roots.keys.associateWith { root ->
        val ids = linkedSetOf<String>()
        val queue = ArrayDeque<String>().also { it.add(root) }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!ids.add(id)) throw BadRequestException("位置下级关系存在循环，不能解析藏品范围")
            children[id]?.let(queue::addAll)
        }
        ids.toSet()
    }
    val members = mutableMapOf<String, MutableSet<String>>()
    val collections = mutableMapOf<String, LocatedCollection>()
    nodes.keys.toList().chunked(200).forEach { batch ->
        Collections.select(Collections.id, Collections.warehouseId, Collections.texture, Collections.status).where {
            (Collections.orgId eq orgId) and (Collections.warehouseId inList batch) and
                (Collections.lifeStatus eq EnumCollectionStatus.IN_STORE)
        }.forEach { row ->
            val id = row[Collections.id].value
            collections[id] = LocatedCollection(id, row[Collections.texture], row[Collections.status])
            members.getOrPut(requireNotNull(row[Collections.warehouseId])) { linkedSetOf() } += id
        }
        CollectionPlacements.select(CollectionPlacements.collectionId, CollectionPlacements.locationId).where {
            (CollectionPlacements.orgId eq orgId) and (CollectionPlacements.locationId inList batch) and
                (CollectionPlacements.approved eq true) and (CollectionPlacements.validFrom lessEq at) and
                (CollectionPlacements.validTo.isNull() or (CollectionPlacements.validTo greaterEq at))
        }.forEach { row ->
            members.getOrPut(row[CollectionPlacements.locationId]) { linkedSetOf() } += row[CollectionPlacements.collectionId]
        }
    }
    val initial = descendants.mapValues { (_, ids) -> ids.flatMapTo(linkedSetOf()) { members[it].orEmpty() }.toSet() }
    val resolved = overrides.fold(initial) { current, provider -> provider.resolveAll(orgId, descendants, at, current) }
    val missing = resolved.values.flatten().toSet() - collections.keys
    missing.toList().chunked(200).forEach { batch ->
        Collections.select(Collections.id, Collections.texture, Collections.status).where {
            (Collections.orgId eq orgId) and (Collections.id inList batch)
        }.forEach { row ->
            val id = row[Collections.id].value
            collections[id] = LocatedCollection(id, row[Collections.texture], row[Collections.status])
        }
    }
    // 只返回同馆真实主档，阻止展陈或覆盖器引入缺失/跨馆藏品。
    return roots.keys.associateWith { id -> resolved[id].orEmpty().sorted().mapNotNull(collections::get) }
}
