package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 仅允许导入的两种馆藏 Word 结构。 */
enum class EnumCollectionWordDocumentType { REGISTRATION, DYNAMIC_TRACKING }

/** 动态跟踪表中的管理事项类别；实际业务单据仍须走各自审批流程。 */
enum class EnumCollectionTrackingEventType {
    EXTRACT_RETURN,
    OBSERVATION,
    RESTORATION,
    DIGITIZATION,
    APPRAISAL_CHANGE,
    RESEARCH_USE,
    INCIDENT,
    DEREGISTRATION,
    NOTE,
    REGISTRATION_DETAIL,
}

data class CollectionTrackingEventPreviewVo(
    val eventType: EnumCollectionTrackingEventType,
    val title: String,
    val content: String,
    val occurredAt: LocalDateTime? = null,
    val endedAt: LocalDateTime? = null,
    val sort: Int = 0,
)

data class CollectionWordMovementPreviewVo(
    val rowNo: Int,
    val operatedAt: LocalDateTime,
    val type: EnumMoveType,
    val reason: EnumMoveReason? = null,
    val fromWarehouseName: String? = null,
    val toWarehouseName: String? = null,
    val fromWarehouseId: String? = null,
    val toWarehouseId: String? = null,
    val basis: String? = null,
    val operator: String? = null,
    val remark: String? = null,
)

/** Word 预校验结果。valid=false 时 commit 不执行任何数据库或文件写入。 */
data class CollectionWordImportPreviewVo(
    val valid: Boolean,
    val documentType: EnumCollectionWordDocumentType? = null,
    val fingerprint: String? = null,
    val sourceFileName: String? = null,
    val sourceHash: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val collectionName: String? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val registration: CollectionImportRowVo? = null,
    val catalog: CollectionCatalogContentVo? = null,
    val events: List<CollectionTrackingEventPreviewVo> = emptyList(),
    val movements: List<CollectionWordMovementPreviewVo> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val confirmation: CollectionImportConfirmationVo? = null,
    @get:com.fasterxml.jackson.annotation.JsonIgnore val registrationValidationHash: String? = null,
)

data class CollectionWordImportCommitResultVo(
    val valid: Boolean,
    val importId: String? = null,
    val collectionId: String? = null,
    val documentType: EnumCollectionWordDocumentType? = null,
    val createdCollection: Boolean = false,
    val updatedCollection: Boolean = false,
    val eventCount: Int = 0,
    val movementCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class CollectionWordImportRecordVo(
    val id: String,
    val documentType: EnumCollectionWordDocumentType,
    val sourceFileId: String? = null,
    val sourceFileName: String,
    val sourceHash: String,
    val fingerprint: String,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val createTime: LocalDateTime? = null,
)

data class CollectionTrackingEventVo(
    val id: String,
    val importId: String,
    val eventType: EnumCollectionTrackingEventType,
    val title: String,
    val content: String,
    val occurredAt: LocalDateTime? = null,
    val endedAt: LocalDateTime? = null,
    val sort: Int = 0,
    val createTime: LocalDateTime? = null,
)

data class CollectionTrackingHistoryVo(
    val imports: List<CollectionWordImportRecordVo> = emptyList(),
    val events: List<CollectionTrackingEventVo> = emptyList(),
)
