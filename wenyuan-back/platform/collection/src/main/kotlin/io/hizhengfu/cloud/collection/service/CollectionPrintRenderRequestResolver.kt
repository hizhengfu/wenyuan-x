package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintRenderRequest
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.common.exception.BadRequestException

object CollectionPrintRenderRequestResolver {
    fun resolve(
        request: CollectionPrintRenderRequest,
        fetch: (EnumCollectionPrintType, String) -> CollectionPrintVo,
        resolveTemplate: (CollectionPrintVo) -> CollectionPrintVo = { it.copy(template = CollectionPrintTemplateRegistry.resolve(it)) },
    ): List<CollectionPrintVo> {
        val provided = when {
            request.items.isNotEmpty() -> request.items
            request.data != null -> listOf(request.data)
            else -> emptyList()
        }
        if (provided.isNotEmpty()) return provided.map { print ->
            val id = print.businessId?.trim()?.takeIf(String::isNotBlank)
                ?: throw BadRequestException("业务表单打印必须提供原业务标识")
            val current = fetch(print.type, id)
            current.withCurrentNames().withResolvedTemplate(resolveTemplate)
        }

        val ids = (listOfNotNull(request.id) + request.ids)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (ids.isEmpty()) throw BadRequestException("请选择需要打印的业务数据")
        val type = request.type ?: throw BadRequestException("按业务ID生成打印文件时必须指定表单类型")

        return ids.map { id -> fetch(type, id).withCurrentNames().withResolvedTemplate(resolveTemplate) }
    }

    /** 名称仅从本次已授权的业务数据转换，保留无法解析的原值供核对。 */
    private fun CollectionPrintVo.withCurrentNames(): CollectionPrintVo = copy(fields = fields.map { field ->
        val value = field.value?.trim().orEmpty()
        val name = when {
            Regex("文物分类|藏品分类").containsMatchIn(field.label) ->
                collections.firstOrNull { it.categoryId == value || it.categoryName == value }?.categoryName
            Regex("登账藏品|入账藏品|关联藏品").containsMatchIn(field.label) ->
                value.split(Regex("[,，;\\n]")).map { raw ->
                    val key = raw.trim()
                    val item = collections.firstOrNull { it.id == key || it.totalRegNo == key }
                    item?.name?.takeIf(String::isNotBlank) ?: item?.totalRegNo?.takeIf(String::isNotBlank) ?: raw
                }.joinToString("，")
            else -> null
        }
        if (name.isNullOrBlank()) field else field.copy(value = name, dictType = null)
    })

    private fun CollectionPrintVo.withResolvedTemplate(resolveTemplate: (CollectionPrintVo) -> CollectionPrintVo): CollectionPrintVo =
        if (template == null) resolveTemplate(this) else this
}
