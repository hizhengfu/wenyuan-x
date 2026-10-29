package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumMigrationVerificationStatus { DRAFT, SUBMITTED, REJECTED, APPROVED, WITHDRAWN }

/** 补充原登记事实，不接收登记号、性质、等级等正式主档修改。 */
data class CollectionMigrationVerificationContent(
    val originalRegisteredAt: LocalDateTime? = null,
    val originalRegisteredBy: String? = null,
    val registrationEvidence: String? = null,
    val historyVerified: Boolean = false,
    val matchesOriginalLedger: Boolean = false,
    val reason: String = "",
    val originalDateMissingReason: String? = null,
    val originalRegistrarMissingReason: String? = null,
    val classificationMissingReason: String? = null,
)

/** 每次保存、提交和复核均追加独立快照，原导入及退回版本不覆盖。 */
data class CollectionMigrationVerificationRecord(
    val id: String,
    val status: EnumMigrationVerificationStatus,
    val content: CollectionMigrationVerificationContent,
    val collectionSnapshot: CollectionCatalogContentVo,
    val basisHash: String,
    val evidenceHash: String? = null,
    val submittedBy: String? = null,
    val submittedAt: LocalDateTime? = null,
    val reviewedBy: String? = null,
    val reviewedAt: LocalDateTime? = null,
    val opinion: String? = null,
    val recordedBy: String,
    val recordedAt: LocalDateTime,
)

data class CollectionMigrationVerificationVo(
    val collectionId: String,
    val totalRegNo: String,
    val source: CollectionMigrationSnapshotVo,
    val record: CollectionMigrationVerificationRecord?,
    val history: List<CollectionMigrationVerificationRecord>,
    val stateHash: String,
    val canEdit: Boolean,
    val canReview: Boolean,
    val canWithdraw: Boolean,
    val blockReason: String?,
    val missingFields: List<String> = emptyList(),
    val collectionSnapshot: CollectionCatalogContentVo? = null,
)

data class CollectionMigrationVerificationReviewVo(val approved: Boolean, val opinion: String = "")

/** 批处理仍逐件提交状态指纹与材料，任一项失败则整批回滚。 */
data class CollectionMigrationBatchItem(val collectionId: String, val expectedStateHash: String, val content: CollectionMigrationVerificationContent? = null)
data class CollectionMigrationBatchRequest(val batchId: String, val items: List<CollectionMigrationBatchItem>, val review: CollectionMigrationVerificationReviewVo? = null)
data class CollectionMigrationBatchSummary(val batchId: String, val sourceSystem: String, val migratedAt: String, val total: Int)
