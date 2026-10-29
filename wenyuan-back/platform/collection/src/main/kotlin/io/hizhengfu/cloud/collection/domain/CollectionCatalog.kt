package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 编目中的实体附件、附属物和分件描述；文件影像仍复用 col_attachment/col_media。 */
data class CatalogAccessoryVo(
    val kind: String? = null,
    val name: String? = null,
    val condition: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val remark: String? = null,
)

/**
 * 编目工作副本。总登记号等法定身份仍来自 col_collection，详细著录在提交审核前独立维护。
 * 审核通过后，共享字段会物化回藏品主档，供总账、检索、统计和后续业务统一读取。
 */
data class CollectionCatalogContentVo(
    val registrationSubNo: String? = null,
    val classificationNo: String? = null,
    val originalNo: String? = null,
    val totalName: String? = null,
    val componentName: String? = null,
    val formalName: String? = null,
    val originalName: String? = null,
    val categoryId: String? = null,
    val comprehensiveCategory: String? = null,
    val topicCategories: List<String> = emptyList(),
    val era: String? = null,
    val region: String? = null,
    val country: String? = null,
    val culture: String? = null,
    val cultureType: String? = null,
    val textureCategory1: String? = null,
    val textureCategory2: String? = null,
    val texture: String? = null,
    val color: String? = null,
    val nature: EnumCollectionNature? = null,
    val level: EnumRelicLevel? = null,
    val appraisalOpinion: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val actualQuantity: Int? = null,
    val actualQuantityUnit: String? = null,
    val dimensions: DimensionVo? = null,
    val dimensionUnit: String? = null,
    val weightRange: String? = null,
    val weight: String? = null,
    val weightUnit: String? = null,
    val volume: String? = null,
    val volumeUnit: String? = null,
    val completeness: EnumCompleteness? = null,
    val completeStatus: String? = null,
    val preserveState: EnumPreserve? = null,
    val source: EnumSource? = null,
    val sourceNo: String? = null,
    val sourceDesc: String? = null,
    val acquireDate: LocalDate? = null,
    val acquireDateRange: String? = null,
    val acquireYear: String? = null,
    val feature: String? = null,
    val inscription: String? = null,
    val colophon: String? = null,
    val bibliography: String? = null,
    val researchNote: String? = null,
    val author: String? = null,
    val edition: String? = null,
    val volumeNo: String? = null,
    val valuation: Long? = null,
    val copyrightOwner: String? = null,
    val rightsRestrictions: String? = null,
    val accessories: List<CatalogAccessoryVo> = emptyList(),
    val attachmentDescription: String? = null,
    val coverImage: String? = null,
    val warehouseId: String? = null,
    val remark: String? = null,
    val entryDate: LocalDate? = null,
    val receiptCompletedAt: LocalDateTime? = null,
    val registrationDetails: CollectionRegistrationDetailsVo? = null,
)

data class CatalogCompletenessVo(
    val percent: Int = 0,
    val missingFields: List<String> = emptyList(),
    val complete: Boolean = false,
)

data class CollectionCatalogVo(
    val id: String? = null,
    val orgId: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val collectionName: String? = null,
    val categoryName: String? = null,
    val warehouseName: String? = null,
    val status: EnumCatalogStatus? = null,
    val version: Int = 0,
    val approvedVersion: Int? = null,
    val submittedVersion: Int? = null,
    val submittedBy: String? = null,
    val submittedAt: LocalDateTime? = null,
    val auditOpinion: String? = null,
    val auditBy: String? = null,
    val auditTime: LocalDateTime? = null,
    val content: CollectionCatalogContentVo = CollectionCatalogContentVo(),
    val completeness: CatalogCompletenessVo = CatalogCompletenessVo(),
    val mediaCount: Long = 0,
    val attachmentCount: Long = 0,
    val createTime: LocalDateTime? = null,
    val updateTime: LocalDateTime? = null,
    val orgName: String? = null,
    val stateHash: String? = null,
    val canEdit: Boolean = false,
    val canReview: Boolean = false,
    /** 已核实原登账事实的旧系统迁入记录；编目补录不重新登账。 */
    val historicalMigration: Boolean = false,
    val migrationVerificationRequired: Boolean = false,
    /** 是否已关联本馆已完成的逐件核收；审核时仍须核验完整原件链。历史已登账迁入使用独立核实路径。 */
    val receiptLinked: Boolean = false,
)

data class CollectionCatalogVersionVo(
    val id: String? = null,
    val catalogId: String? = null,
    val collectionId: String? = null,
    val version: Int = 0,
    val status: EnumCatalogStatus? = null,
    val content: CollectionCatalogContentVo = CollectionCatalogContentVo(),
    val submittedBy: String? = null,
    val submittedAt: LocalDateTime? = null,
    val auditOpinion: String? = null,
    val auditBy: String? = null,
    val auditTime: LocalDateTime? = null,
)

class CollectionCatalogQueryVo {
    var orgId: String? = null
    var keyword: String? = null
    var status: EnumCatalogStatus? = null
    var level: EnumRelicLevel? = null
    var categoryId: String? = null
    var approvedOnly: Boolean? = null
    var auditBy: String? = null
    var submittedBy: String? = null
    var createBy: String? = null
    var updateBy: String? = null
    var auditedByMe: Boolean? = null
    var submittedByMe: Boolean? = null
}

data class CollectionCatalogReviewVo(
    val status: EnumCatalogStatus? = null,
    val opinion: String? = null,
)

data class CollectionCatalogCardVo(
    val catalog: CollectionCatalogVo,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val entryVoucherNo: String? = null,
    val registrationFields: List<CollectionPrintFieldVo> = emptyList(),
)
