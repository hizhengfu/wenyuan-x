package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/** 仅按 VO 中明确的 Set 字段忽略序列化顺序，不改写原档案或其摘要。 */
internal object DigitalArchiveSnapshotComparison {
    fun same(phase: String, saved: String, current: String): Boolean = comparable(phase,saved) == comparable(phase,current)

    private fun comparable(phase: String, json: String): JsonNode {
        val root=JsonUtils.mapper.readTree(json)
        val owner=if(phase.startsWith("RESOURCE_"))root.path("content") else root
        val fields=when {
            phase=="CAPTURED"->listOf("rawFileIds")
            phase=="COMPLETED"->listOf("storedResourceIds")
            phase.startsWith("RESOURCE_")->listOf("fileIds","parentResourceIds")
            else->emptyList()
        }
        if(owner is ObjectNode) fields.forEach { field->
            val value=owner.get(field)
            if(value?.isArray==true) {
                // 排序保留元素类型、数量与重复值，不能将被篡改的数组去重后接受。
                val ordered=value.toList().sortedBy {it.toString()}
                val array=owner.putArray(field)
                ordered.forEach {array.add(it)}
            }
        }
        return root
    }
}
