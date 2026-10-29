package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.Collections
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.select

/** 调用方已逐份核实原件读取权；标题仅用于辨认业务，不替代原件或授予业务编辑权。 */
data class CollectionOriginalBusinessKey(val orgId: String, val businessType: String, val businessId: String)

/** 明确声明可展示的名称/单号列；不从附件名称或任意正文猜测业务关联。 */
data class CollectionOriginalBusinessLabel(
    val table: IdTable<String>, val orgId: Column<String>, val businessType: String,
    val columns: List<Column<*>> = emptyList(), val collectionId: Column<out String?>? = null,
) {
    fun read(requested: Set<CollectionOriginalBusinessKey>): Map<CollectionOriginalBusinessKey, String> {
        val keys = requested.filter { it.businessType == businessType }
        if (keys.isEmpty()) return emptyMap()
        val fields = (listOf(table.id, orgId) + columns + listOfNotNull(collectionId)).distinct()
        val rows = keys.groupBy { it.orgId }.flatMap { (org, group) ->
            group.map { it.businessId }.distinct().chunked(200).flatMap { ids ->
                table.select(fields).where { (orgId eq org) and (table.id inList ids) }.toList()
            }
        }
        val linkedKeys = rows.mapNotNull { row -> collectionId?.let { row[it] }?.let { row[orgId] to it } }.toSet()
        val collections = linkedKeys.map { it.second }.distinct().chunked(200).flatMap { ids ->
            Collections.select(Collections.id, Collections.orgId, Collections.totalRegNo, Collections.name)
                .where { Collections.id inList ids }.toList()
        }.filter { (it[Collections.orgId] to it[Collections.id].value) in linkedKeys }
            .associate { (it[Collections.orgId] to it[Collections.id].value) to listOf(it[Collections.totalRegNo], it[Collections.name]) }
        return rows.mapNotNull { row ->
            val related = collectionId?.let { row[it] }?.let { collections[row[orgId] to it] }.orEmpty()
            val label = (columns.mapNotNull { row[it]?.toString() } + related).map(String::trim)
                .filter(String::isNotBlank).distinct().joinToString(" · ")
            label.takeIf(String::isNotBlank)?.let { CollectionOriginalBusinessKey(row[orgId], businessType, row[table.id].value) to it }
        }.toMap()
    }
}
