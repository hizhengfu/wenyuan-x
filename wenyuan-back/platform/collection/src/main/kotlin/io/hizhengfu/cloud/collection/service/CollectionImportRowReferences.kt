package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 解析失败返回具体行/列错误，整个批次不提交部分结果。 */
internal class CollectionImportRowReferences(private val rowNo: Int, private val errors: MutableList<CollectionImportErrorVo>) {
    fun read(field: String, block: () -> String?): String? = try { block() }
    catch (e: BadRequestException) {
        errors += CollectionImportErrorVo(rowNo, field, e.message ?: "引用无效")
        null
    }

    fun byCode(code: String?, id: String?, matches: Map<String, List<String>>, label: String): String? {
        val clean = code?.trim()?.takeIf { it.isNotEmpty() } ?: return id?.trim()?.takeIf { it.isNotEmpty() }
        val ids = matches[clean].orEmpty().distinct()
        if (ids.isEmpty()) throw BadRequestException("$label 编码「$clean」不存在或无权访问")
        if (ids.size != 1) throw BadRequestException("$label 编码「$clean」不唯一，请核实目录")
        if (!id.isNullOrBlank() && id.trim() != ids.single()) throw BadRequestException("$label 编码与ID/名称不一致")
        return ids.single()
    }
}
