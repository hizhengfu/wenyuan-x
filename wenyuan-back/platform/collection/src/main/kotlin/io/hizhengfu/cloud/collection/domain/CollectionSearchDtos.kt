package io.hizhengfu.cloud.collection.domain

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

/** 统一检索请求。group 为空时返回各分组摘要，指定分组时返回该组分页结果。 */
data class CollectionUnifiedSearchQueryVo(
    val keyword: String? = null,
    val group: String? = null,
    val pageNum: Int = 1,
    val pageSize: Int = 20,
    val type: String? = null,
)

data class CollectionSearchCollectionRefVo(
    val id: String,
    val totalRegNo: String,
    val name: String,
    val coverImage: String? = null,
)

data class CollectionSearchItemVo(
    val id: String,
    val type: String,
    val typeLabel: String,
    val title: String,
    val fileId: String? = null,
    val subtitle: String? = null,
    val summary: String? = null,
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val matchedFields: List<String> = emptyList(),
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val relatedCollections: List<CollectionSearchCollectionRefVo> = emptyList(),
    val relatedCollectionCount: Int = relatedCollections.size,
    val route: String,
    val targetId: String? = null,
    val targetYear: Int? = null,
    val occurredAt: LocalDateTime? = null,
)

data class CollectionSearchGroupVo(
    val key: String,
    val label: String,
    val description: String,
    val total: Long,
    val pageNum: Int,
    val pageSize: Int,
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val items: List<CollectionSearchItemVo> = emptyList(),
)

data class CollectionUnifiedSearchVo(
    val keyword: String,
    val total: Long,
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val groups: List<CollectionSearchGroupVo> = emptyList(),
)
