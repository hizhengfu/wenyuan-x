package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import java.time.LocalDateTime

interface CollectionLocationOverrideProvider {
    fun resolve(orgId:String,locationIds:Set<String>,at:LocalDateTime,existing:Set<String>):Set<String>

    /** 未实现批量接口的扩展仍按原有顺序运行；调用方统一核验藏品所属馆。 */
    fun resolveAll(orgId: String, locations: Map<String, Set<String>>, at: LocalDateTime,
                   existing: Map<String, Set<String>>): Map<String, Set<String>> =
        locations.mapValues { (id, ids) -> resolve(orgId, ids, at, existing[id].orEmpty()) }
}

/** 实际应急流水覆盖旧位置显示；后续正常业务流水重新成为位置依据。 */
@Component
class IncidentRelocationLocationResolver : CollectionLocationOverrideProvider {
    /** 每个候选最多返回一条当前流水和一条未来应急出库流水，历史长度不再扩大 JVM 读取量。 */
    fun latest(orgId: String, ids: Set<String>, at: LocalDateTime): Map<String, String?> {
        if (ids.isEmpty()) return emptyMap()
        TransactionManager.current().entityCache.flush()
        val m = StorageMovements
        val result = linkedMapOf<String, String?>()
        ids.toList().chunked(200).forEach { batch ->
            val newer = m.alias("later_movement")
            val later = newer.select(newer[m.id]).where {
                (newer[m.orgId] eq orgId) and (newer[m.collectionId] eq m.collectionId) and
                    (newer[m.operatedAt] lessEq at) and
                    ((newer[m.operatedAt] greater m.operatedAt) or
                        ((newer[m.operatedAt] eq m.operatedAt) and (newer[m.id] greater m.id)))
            }
            val current = m.select(m.collectionId, m.reason, m.type, m.toWarehouseId).where {
                (m.orgId eq orgId) and (m.collectionId inList batch) and
                    (m.operatedAt lessEq at) and notExists(later)
            }.associateBy { it[m.collectionId] }
            val earlier = m.alias("earlier_emergency")
            val preceding = earlier.select(earlier[m.id]).where {
                (earlier[m.orgId] eq orgId) and (earlier[m.collectionId] eq m.collectionId) and
                    (earlier[m.reason] eq EnumMoveReason.EMERGENCY) and (earlier[m.type] eq EnumMoveType.OUT) and
                    (earlier[m.operatedAt] greater at) and
                    ((earlier[m.operatedAt] less m.operatedAt) or
                        ((earlier[m.operatedAt] eq m.operatedAt) and (earlier[m.id] less m.id)))
            }
            val upcoming = m.select(m.collectionId, m.fromWarehouseId).where {
                (m.orgId eq orgId) and (m.collectionId inList batch) and
                    (m.reason eq EnumMoveReason.EMERGENCY) and (m.type eq EnumMoveType.OUT) and
                    (m.operatedAt greater at) and notExists(preceding)
            }.associateBy { it[m.collectionId] }
            io.hizhengfu.cloud.common.utils.RuntimeWorkMetrics.rows(current.size + upcoming.size)
            batch.forEach { id ->
                val last = current[id]
                val next = upcoming[id]
                when {
                    last?.get(m.reason) == EnumMoveReason.EMERGENCY ->
                        result[id] = if (last[m.type] == EnumMoveType.IN) last[m.toWarehouseId] else null
                    next != null && last == null -> result[id] = next[m.fromWarehouseId]
                    next != null && last?.get(m.type) == EnumMoveType.IN && last[m.toWarehouseId] != null ->
                        result[id] = last[m.toWarehouseId]
                }
            }
        }
        return result
    }

    override fun resolve(orgId: String, locationIds: Set<String>, at: LocalDateTime, existing: Set<String>): Set<String> =
        resolveAll(orgId, mapOf("root" to locationIds), at, mapOf("root" to existing)).getValue("root").let { ids ->
            if (ids.isEmpty()) emptySet() else CollectionDao.find {
                (Collections.id inList ids) and (Collections.orgId eq orgId)
            }.map { it.id.value }.toSet()
        }

    override fun resolveAll(orgId: String, locations: Map<String, Set<String>>, at: LocalDateTime,
                            existing: Map<String, Set<String>>): Map<String, Set<String>> {
        if (locations.isEmpty()) return emptyMap()
        TransactionManager.current().entityCache.flush()
        val m = StorageMovements
        val emergencyIds = hashSetOf<String>()
        existing.values.flatten().distinct().chunked(200).forEach { batch ->
            m.select(m.collectionId).where {
                (m.orgId eq orgId) and (m.reason eq EnumMoveReason.EMERGENCY) and (m.collectionId inList batch)
            }.withDistinct().forEach { emergencyIds += it[m.collectionId] }
        }
        val byLocation = mutableMapOf<String, MutableSet<String>>()
        locations.values.flatten().distinct().chunked(200).forEach { batch ->
            // 分开读取来源、目标便于各自使用范围索引；重复历史在数据库中折叠为身份与位置的关系。
            listOf(m.fromWarehouseId, m.toWarehouseId).forEach { location ->
                m.select(m.collectionId, location).where {
                    (m.orgId eq orgId) and (m.reason eq EnumMoveReason.EMERGENCY) and (location inList batch)
                }.withDistinct().forEach { row ->
                    byLocation.getOrPut(requireNotNull(row[location])) { linkedSetOf() } += row[m.collectionId]
                }
            }
        }
        val candidates = locations.mapValues { (id, ids) ->
            existing[id].orEmpty().filterTo(linkedSetOf(), emergencyIds::contains).also { candidates ->
                ids.forEach { candidates += byLocation[it].orEmpty() }
            }
        }
        val updates = latest(orgId, candidates.values.flatten().toSet(), at)
        return locations.mapValues { (id, ids) ->
            val applicable = candidates.getValue(id).filter(updates::containsKey).associateWith(updates::get)
            (existing[id].orEmpty() - applicable.keys) + applicable.filterValues { it in ids }.keys
        }
    }
}
