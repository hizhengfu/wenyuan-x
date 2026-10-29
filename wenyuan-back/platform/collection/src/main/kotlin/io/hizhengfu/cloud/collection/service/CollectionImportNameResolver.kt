package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException

/** 每批建立索引，只匹配调用方已经按收藏单位、状态和用途过滤的候选项。 */
internal class CollectionImportNameResolver(entries: List<Entry>) {
    data class Entry(val id: String, val name: String, val parentId: String?)
    private val byId = entries.associateBy { it.id }
    private fun path(entry: Entry): String? {
        val names = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var current: Entry? = entry
        while (current != null) {
            if (!seen.add(current.id)) return null
            names.add(current.name.trim())
            val parent = current.parentId?.takeIf { it.isNotBlank() && it != "0" }
            current = if (parent == null) null else byId[parent] ?: return null
        }
        return names.asReversed().joinToString("/")
    }
    private val byName = entries.groupBy { it.name.trim() }
    private val byPath = entries.mapNotNull { entry -> path(entry)?.let { it to entry } }.groupBy({ it.first }, { it.second })

    fun resolve(name: String?, id: String?, label: String): String? {
        val cleanId = id?.trim()?.takeIf { it.isNotEmpty() }
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return cleanId
        val matches = if ('/' in cleanName) byPath[cleanName].orEmpty() else byName[cleanName].orEmpty()
        if (matches.isEmpty()) throw BadRequestException("$label「$cleanName」不存在或不属于当前可用范围")
        if (matches.size != 1) throw BadRequestException("$label「$cleanName」重名，请填写完整路径（上级名称/名称）")
        val matched = matches.single().id
        if (cleanId != null && cleanId != matched) throw BadRequestException("$label 名称与ID不一致")
        return matched
    }
}
