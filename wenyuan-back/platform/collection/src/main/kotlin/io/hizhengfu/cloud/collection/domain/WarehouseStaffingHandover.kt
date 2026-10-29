package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumStaffingHandoverKind { DUTY_DEPARTURE, RECORD_CORRECTION }

data class WarehouseStaffingHandoverRequest(
    val assignmentArchiveId: String,
    val leavingUserId: String,
    val kind: EnumStaffingHandoverKind,
    val inventoryBatchIds: Set<String> = emptySet(),
    val scopeStatement: String,
    val scopeFileIds: String,
    val fullResponsibilityScopeConfirmed: Boolean,
    val keeperUserId: String,
    val leaderUserId: String,
    val previousId: String? = null,
    val replacementReason: String? = null,
)

/** 冻结清点完成时的原件；后续调查可继续追加，不改写本次交接核对。 */
data class WarehouseStaffingHandoverRecord(
    val id: String,
    val request: WarehouseStaffingHandoverRequest,
    val previousAssignmentArchiveId: String,
    val previousAssignmentDigest: String,
    val assignmentDigest: String,
    val inventoryArchiveDigests: Map<String, String> = emptyMap(),
    val registeredBy: String,
    val registeredAt: LocalDateTime,
)

data class WarehouseStaffingHandoverItem(
    val assignmentArchiveId: String,
    val previousAssignmentArchiveId: String,
    val leavingUserId: String,
    val assignedAt: LocalDateTime,
    val successorUserIds: Set<String>,
    val record: WarehouseStaffingHandoverRecord? = null,
    val keeper: CollectionCareSignature? = null,
    val leader: CollectionCareSignature? = null,
    val status: String = "PENDING",
    val lateBatchIds: Set<String> = emptySet(),
    val canConfirmKeeper: Boolean = false,
    val canConfirmLeader: Boolean = false,
    val departureScopeErrors: List<String> = emptyList(),
)
