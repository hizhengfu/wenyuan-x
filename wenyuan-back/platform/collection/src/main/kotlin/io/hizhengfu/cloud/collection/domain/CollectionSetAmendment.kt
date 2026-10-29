package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 套件订正的业务快照；不把可变的关系行或藏品主档当作历史资料。 */
data class CollectionSetRelationSnapshot(
    val id: String? = null,
    val childCollectionId: String? = null,
    val partNo: String,
    val partName: String,
    val relation: EnumSetRelation,
    val quantity: Int = 1,
    val unit: String = "件",
    val sort: Int = 0,
)

data class CollectionSetIdentitySnapshot(
    val collectionId: String,
    val orgId: String,
    val totalRegNo: String,
    val name: String,
    val registeredAt: LocalDateTime?,
    val historicalConfirmedAt: LocalDateTime? = null,
)

/** before 必须匹配当前关系；identities 始终由服务端核定，客户端不能指定历史身份。 */
data class CollectionSetAmendment(
    val before: List<CollectionSetRelationSnapshot> = emptyList(),
    val after: List<CollectionSetRelationSnapshot> = emptyList(),
    val identities: List<CollectionSetIdentitySnapshot> = emptyList(),
)
