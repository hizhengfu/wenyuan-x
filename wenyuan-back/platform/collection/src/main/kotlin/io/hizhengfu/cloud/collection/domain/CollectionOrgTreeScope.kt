package io.hizhengfu.cloud.collection.domain

object CollectionOrgTreeScope {
    data class Node(val id: String, val parentId: String?)

    fun expand(seedIds: Set<String>, nodes: List<Node>): Set<String> {
        if (seedIds.isEmpty()) return emptySet()
        val childrenByParent = nodes.groupBy { it.parentId }
        val result = linkedSetOf<String>()
        val queue = ArrayDeque(seedIds)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!result.add(id)) continue
            childrenByParent[id].orEmpty().forEach { queue.addLast(it.id) }
        }
        return result
    }
}
