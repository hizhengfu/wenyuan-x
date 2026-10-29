package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumWarehouseEntryKind { KEEPERS, VISITORS, STAFF_SHORTAGE }
data class WarehouseStaffingVo(
    val keeperUserIds: Set<String>,
    val keeperUserId: String,
    val leaderUserId: String,
    val assignedAt: LocalDateTime,
    val signedAssignmentFileIds: String,
    val replacementReason: String? = null,
)
data class WarehouseEntryDossierVo(
    val kind: EnumWarehouseEntryKind,
    val escortUserIds: Set<String> = emptySet(),
    val externalNames: Set<String> = emptySet(),
    val keeperUserId: String,
    val leaderUserId: String? = null,
    val identityFileIds: String,
    val shortageReason: String? = null,
    val shortageFileIds: String? = null,
)
data class WarehouseStaffingItemVo(val warehouseId: String, val warehouseName: String, val staffing: WarehouseStaffingVo)
data class WarehouseEntryBaselineVo(val items: List<WarehouseStaffingItemVo>)
data class WarehouseAttendanceVo(
    val occurredAt: LocalDateTime,
    val entrantUserIds: Set<String> = emptySet(),
    val escortUserIds: Set<String> = emptySet(),
    val externalNames: Set<String> = emptySet(),
    val identityChecked: Boolean?,
    val continuousAccompaniment: Boolean?,
    val signedRecordFileIds: String,
    val remark: String? = null,
    val exceptionMeasures: String? = null,
    val exceptionReportFileIds: String? = null,
)
data class WarehouseEnvironmentReadingVo(
    val measuredAt: LocalDateTime,
    val temperature: Double?,
    val humidity: Double?,
    val sourceDescription: String,
    val evidenceFileIds: String,
)
data class WarehouseDiaryVo(
    val inspectedAt: LocalDateTime,
    val inspectorUserIds: Set<String>,
    val entryRecordsComplete: Boolean?,
    val collectionDynamics: String,
    val securitySituation: String,
    val readings: List<WarehouseEnvironmentReadingVo>,
    val hasAnomaly: Boolean?,
    val anomalyDescription: String? = null,
    val handlingMeasures: String? = null,
    val keeperUserId: String? = null,
    val keeperReportedAt: LocalDateTime? = null,
    val keeperReportFileIds: String? = null,
    val major: Boolean? = null,
    val significanceReason: String? = null,
    val leaderUserId: String? = null,
    val leaderReportedAt: LocalDateTime? = null,
    val leaderReportFileIds: String? = null,
    val resolved: Boolean? = null,
    val followUpAt: LocalDateTime? = null,
    val signedDiaryFileIds: String,
    val replacementReason: String? = null,
)
data class WarehouseDiaryVisitVo(
    val applicationId: String,
    val applicationNo: String,
    val purpose: String,
    val entry: WarehouseAttendanceVo?,
    val exit: WarehouseAttendanceVo?,
    val enteredAt: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val legacyEntrantIds: Set<String> = emptySet(),
    val legacyExternalEntrants: String? = null,
    val exitedAt: LocalDateTime?,
)
data class WarehouseDiaryEntriesVo(val items: List<WarehouseDiaryVisitVo> = emptyList())
