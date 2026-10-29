package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumInventoryMethod { FULL, SPECIAL, SAMPLE }
enum class EnumInventoryScopeSelection { FULL_MUSEUM, EXPLICIT_COLLECTIONS, CURRENT_LOCATION }
enum class EnumInventoryPurpose { ROUTINE, DIRECTOR_DEPARTURE, KEEPER_DEPARTURE }

data class InventoryPlanVo(
    val year: Int,
    val method: EnumInventoryMethod,
    val purpose: EnumInventoryPurpose,
    val scopeDescription: String,
    val keeperUserId: String,
    val leaderUserId: String,
    val scopeFileIds: String,
    val leavingUserId: String? = null,
    val successorUserId: String? = null,
    val leavingAt: LocalDateTime? = null,
    val responsibilityFileIds: String? = null,
    val leadershipDepartureId: String? = null,
    val replacesInventoryBatchId: String? = null,
    val replanReason: String? = null,
    val replanFileIds: String? = null,
    val keeperStaffingChangeIds: Set<String> = emptySet(),
)

data class InventoryBaselineItemVo(
    val collectionId: String,
    val content: CollectionCatalogContentVo,
    val categoryId: String?,
    val locationKey: String,
    val locationName: String,
    val precious: Boolean,
    val usedThisYear: Boolean,
    val overdue: Boolean,
    val priorityAfter: LocalDateTime? = null,
    val overdueAt: LocalDateTime? = null,
    val locationVerified: Boolean = true,
)
data class InventoryKeeperStaffingProof(
    val warehouseId: String,
    val previousAssignmentArchiveId: String,
    val previousAssignmentDigest: String,
    val assignmentArchiveId: String,
    val assignmentDigest: String,
)
data class InventoryBaselineVo(
    val capturedAt: LocalDateTime,
    val ownership: EnumOwnership,
    val items: List<InventoryBaselineItemVo>,
    val keeperStaffingEvidence: List<InventoryKeeperStaffingProof> = emptyList(),
    val scopeSelection: EnumInventoryScopeSelection? = null,
)

/** 范围不足与档案损坏分开：原逐件核验可保留，不能据此认定完整离任交接。 */
fun inventoryDepartureScopeErrors(plan: InventoryPlanVo?, baseline: InventoryBaselineVo?, configuredWarehouseId: String?): List<String> {
    if (plan?.purpose != EnumInventoryPurpose.KEEPER_DEPARTURE) return emptyList()
    return buildList {
        if (plan.method == EnumInventoryMethod.SAMPLE) add("保管人员离任抽检不能作为完整责任清点移交")
        if (baseline?.scopeSelection == EnumInventoryScopeSelection.CURRENT_LOCATION || !configuredWarehouseId.isNullOrBlank()) add("仅按当前库位确定的离任范围须重新核对完整保管责任")
    }
}

/** 明确记录每一个核验结论；缺失字段不能被解释成已核验。 */
data class InventoryItemCheckVo(
    val collectionId: String,
    val checkedAt: LocalDateTime,
    val checkerUserIds: Set<String>,
    val actualLocation: String,
    val ledgerMatches: Boolean?,
    val objectMatches: Boolean?,
    val cardMatches: Boolean?,
    val photoMatches: Boolean?,
    val condition: String,
    val damaged: Boolean?,
    val ledgerFileIds: String?,
    val cardFileIds: String?,
    val photoFileIds: String?,
    val signedCheckFileIds: String,
    val findings: String? = null,
    val protectiveMeasures: String? = null,
    val replacementReason: String? = null,
) {
    fun abnormal() = ledgerMatches != true || objectMatches != true || cardMatches != true || photoMatches != true || damaged == true
}
data class InventoryChecksVo(val items: List<InventoryItemCheckVo> = emptyList())

data class InventoryIssueActionVo(
    val collectionId: String,
    val recordedAt: LocalDateTime,
    val cause: String,
    val actions: String,
    val responsibleUserId: String,
    val resolved: Boolean?,
    val followUpAt: LocalDateTime?,
    val major: Boolean?,
    val significanceReason: String,
    val evidenceFileIds: String,
    val authorityName: String? = null,
    val reportNo: String? = null,
    val reportedAt: LocalDateTime? = null,
    val reportFileIds: String? = null,
    val safeForUse: Boolean? = null,
    val keeperUserId: String? = null,
    val leaderUserId: String? = null,
    val resolutionApprovedAt: LocalDateTime? = null,
    val signedResolutionFileIds: String? = null,
    val incidentId: String? = null,
)
data class InventoryIssueActionsVo(val items: List<InventoryIssueActionVo> = emptyList())

data class InventoryCompletionVo(
    val signedAt: LocalDateTime,
    val keeperUserId: String,
    val leaderUserId: String,
    val reportFileIds: String,
    val actualHandoverAt: LocalDateTime? = null,
    val handoverFileIds: String? = null,
    val supervisorAuthority: String? = null,
    val supervisorNames: Set<String> = emptySet(),
    val supervisionAt: LocalDateTime? = null,
    val appointmentFileIds: String? = null,
    val supervisionFileIds: String? = null,
    val departureDelayReason: String? = null,
    val departureDelayFileIds: String? = null,
)

enum class EnumInventoryDepartureStatus { NOT_APPLICABLE, PENDING, OVERDUE, COMPLETED_BEFORE_DEPARTURE, COMPLETED_LATE, SCOPE_REVIEW_REQUIRED }
fun inventoryDepartureStatus(plan: InventoryPlanVo?, completion: InventoryCompletionVo?, now: LocalDateTime = LocalDateTime.now()): EnumInventoryDepartureStatus {
    if (plan == null || plan.purpose == EnumInventoryPurpose.ROUTINE) return EnumInventoryDepartureStatus.NOT_APPLICABLE
    val leaving = plan.leavingAt ?: return EnumInventoryDepartureStatus.PENDING
    if (completion == null) return if (now.isBefore(leaving)) EnumInventoryDepartureStatus.PENDING else EnumInventoryDepartureStatus.OVERDUE
    return if (completion.actualHandoverAt?.isBefore(leaving) == true && completion.signedAt.isBefore(leaving)) EnumInventoryDepartureStatus.COMPLETED_BEFORE_DEPARTURE else EnumInventoryDepartureStatus.COMPLETED_LATE
}

data class InventoryAnnualReportVo(
    val year: Int,
    val signedAt: LocalDateTime,
    val leaderUserId: String,
    val signedReportFileIds: String,
    val authorityName: String,
    val reportNo: String,
    val reportedAt: LocalDateTime,
    val receiptFileIds: String,
    val supplementReason: String? = null,
)
data class InventoryCoverageVo(
    val year: Int,
    val asOf: LocalDateTime,
    val population: List<InventoryBaselineItemVo> = emptyList(),
    val checkedCollectionIds: Set<String> = emptySet(),
    val completedBatchIds: Set<String> = emptySet(),
    val missingCategories: Set<String> = emptySet(),
    val missingLocations: Map<String, String> = emptyMap(),
    val uncheckedPriorityIds: Set<String> = emptySet(),
    val unclassifiedIds: Set<String> = emptySet(),
    val unhandledIssueIds: Set<String> = emptySet(),
    val pendingIssueIds: Set<String> = emptySet(),
    val unverifiedBatchIds: Set<String> = emptySet(),
    val departureLateBatchIds: Set<String> = emptySet(),
    val departureScopeReviewBatchIds: Set<String> = emptySet(),
) {
    fun ready() = missingCategories.isEmpty() && missingLocations.isEmpty() && uncheckedPriorityIds.isEmpty() && unclassifiedIds.isEmpty() && unhandledIssueIds.isEmpty() && unverifiedBatchIds.isEmpty()
}
