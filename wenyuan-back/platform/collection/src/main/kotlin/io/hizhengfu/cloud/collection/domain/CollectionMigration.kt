package io.hizhengfu.cloud.collection.domain

/** 本馆旧系统迁移；不是外馆调拨接收，也不是重新办理新藏品登记。 */
data class CollectionMigrationRowVo(
    val item: CollectionImportRowVo,
    val sourceRecordId: String? = null,
    val originalOrgCode: String? = null,
    val nature: String? = null,
    val currentStatus: String? = null,
    val originalRegisteredAt: String? = null,
    val originalRegisteredBy: String? = null,
    val registrationEvidence: String? = null,
    val verification: String? = null,
    val historyVerified: String? = null,
    val registrationEvidenceName: String? = null,
)
data class CollectionMigrationRequestVo(
    val sourceSystem: String = "",
    val reason: String = "",
    val belongsToCurrentMuseum: Boolean = false,
    val rows: List<CollectionMigrationRowVo> = emptyList(),
)
data class CollectionMigrationValidateVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val createCount: Int = 0,
    val updateCount: Int = 0,
    val registeredCount: Int = 0,
    val unverifiedCount: Int = 0,
    val confirmation: CollectionImportConfirmationVo? = null,
)
data class CollectionMigrationSnapshotVo(
    val batchId: String, val sourceSystem: String, val sourceRecordId: String,
    val originalOrgCode: String, val originalRegisteredAt: String?, val originalRegisteredBy: String?,
    val registrationEvidence: String?, val verification: String, val historyVerified: Boolean,
    val migratedAt: String, val migratedBy: String, val reason: String,
    val declaredVerification: String? = null,
    val originalDateMissingReason: String? = null,
    val originalRegistrarMissingReason: String? = null,
    val classificationMissingReason: String? = null,
)
