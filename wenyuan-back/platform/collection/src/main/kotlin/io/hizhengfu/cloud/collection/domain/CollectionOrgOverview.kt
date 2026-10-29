package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

data class CollectionOrgOverviewOrg(
    val id: String,
    val parentId: String?,
    val name: String,
    val code: String?,
    val sort: Long = 0,
    val createTime: LocalDateTime? = null,
)

data class CollectionOrgOverviewCollection(
    val orgId: String,
    val level: EnumRelicLevel?,
    val lifeStatus: EnumCollectionStatus?,
    val hasImage: Boolean,
    val hasAttachment: Boolean,
)

data class CollectionOrgOverviewStats(
    val total: Int = 0,
    val byLevel: Map<String, Int> = emptyMap(),
    val byStatus: Map<String, Int> = emptyMap(),
    val imageCount: Int = 0,
    val attachmentCount: Int = 0,
)

data class CollectionOrgOverviewNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val code: String?,
    val direct: CollectionOrgOverviewStats,
    val aggregate: CollectionOrgOverviewStats,
    val children: List<CollectionOrgOverviewNode> = emptyList(),
)

object CollectionOrgOverviewBuilder {
    fun build(
        orgs: List<CollectionOrgOverviewOrg>,
        collections: List<CollectionOrgOverviewCollection>,
    ): List<CollectionOrgOverviewNode> {
        val orgIds = orgs.map { it.id }.toSet()
        val directStats = collections
            .filter { it.orgId in orgIds }
            .groupBy { it.orgId }
            .mapValues { (_, rows) -> rows.toStats() }
        val childrenByParent = orgs.groupBy { it.parentId }

        fun buildNode(org: CollectionOrgOverviewOrg): CollectionOrgOverviewNode {
            val children = childrenByParent[org.id].orEmpty()
                .sortedWith(orgDisplayOrder)
                .map(::buildNode)
            val direct = directStats[org.id] ?: CollectionOrgOverviewStats()
            return CollectionOrgOverviewNode(
                id = org.id,
                parentId = org.parentId,
                name = org.name,
                code = org.code,
                direct = direct,
                aggregate = mergeStats(listOf(direct) + children.map { it.aggregate }),
                children = children,
            )
        }

        return orgs
            .filter { it.parentId.isNullOrBlank() || it.parentId == "0" || it.parentId !in orgIds }
            .sortedWith(orgDisplayOrder)
            .map(::buildNode)
    }

    private val orgDisplayOrder = compareBy<CollectionOrgOverviewOrg> { it.sort }
        .thenByDescending { it.createTime ?: LocalDateTime.MIN }
        .thenBy { it.name }

    private fun List<CollectionOrgOverviewCollection>.toStats() = CollectionOrgOverviewStats(
        total = size,
        byLevel = mapNotNull { it.level?.name }.groupingBy { it }.eachCount(),
        byStatus = mapNotNull { it.lifeStatus?.name }.groupingBy { it }.eachCount(),
        imageCount = count { it.hasImage },
        attachmentCount = count { it.hasAttachment },
    )

    private fun mergeStats(items: List<CollectionOrgOverviewStats>) = CollectionOrgOverviewStats(
        total = items.sumOf { it.total },
        byLevel = mergeMaps(items.map { it.byLevel }),
        byStatus = mergeMaps(items.map { it.byStatus }),
        imageCount = items.sumOf { it.imageCount },
        attachmentCount = items.sumOf { it.attachmentCount },
    )

    private fun mergeMaps(items: List<Map<String, Int>>): Map<String, Int> =
        items.flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.sum() }
}
