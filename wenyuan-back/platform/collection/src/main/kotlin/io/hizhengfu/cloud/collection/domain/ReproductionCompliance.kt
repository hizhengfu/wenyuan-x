package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

data class ReproductionPlanVo(
    val sourceRevision: String,
    val preparedAt: LocalDateTime,
    val keeperUserId: String,
    val leaderUserId: String,
    val undertakingUnitId: String?,
    val undertakingUnitName: String,
    val scopeCheckedAt: LocalDateTime?,
    val scopeConfirmed: Boolean?,
    val scopeReviewFileIds: String?,
    val maxQuantity: Int,
    val usesOriginal: Boolean,
    val massProduction: Boolean,
    val rubbingYuanOrEarlier: Boolean?,
    val originalSuitable: Boolean,
    val noDamageMethod: String,
    val noHardToolBeating: Boolean,
    val conditionFileIds: String,
    val eraAndMethodBasisFileIds: String,
    val limitedQuantityReason: String,
    val siteAddress: String,
    val safetyStandards: String,
    val safetyCheckIntervalDays: Int,
    val safetyPlanFileIds: String,
    val contractFileIds: String,
    val signedPlanFileIds: String,
    val confidentialityAssessment: String,
    val confidentialityFileIds: String,
    val replacementReason: String?=null,
    val replacementFileIds: String?=null,
)
data class ReproductionAdministrativeVo(val planId: String,val evidence: CollectionAdministrativeEvidenceVo)
data class ReproductionStartVo(
    val planId: String,val startedAt: LocalDateTime,val operatorUserId: String,
    val requisitionId: String?,val onSiteWarehouseLogId: String?,
    val siteCheckedAt: LocalDateTime,val siteSafe: Boolean,val safetyCheckFileIds: String,val signedStartFileIds: String,
)
data class ReproductionSafetyVo(val checkedAt: LocalDateTime,val checkerUserId: String,val safe: Boolean,val findings: String,val measures: String,val fileIds: String,val resolvesRecordIds: Set<String> = emptySet())
data class ReproductionCompletionVo(val completedAt: LocalDateTime,val operatorUserId: String,val originalUndamaged: Boolean,val condition: String,val conditionFileIds: String,val signedCompletionFileIds: String)
data class ReproductionOutputVo(val number: String,val name: String,val madeAt: LocalDateTime,val registeredAt: LocalDateTime,val marked: Boolean,val markedUnitName: String,val markedMadeAt: LocalDateTime?,val quantitySerial: Int,val markingFileIds: String,val location: String,val custodianUserId: String,val purpose: String,val receiptFileIds: String)
data class ReproductionOutputUseVo(val occurredAt: LocalDateTime,val purpose: String,val useLocation: String,val responsibleUserId: String,val signedFileIds: String)

data class ReproductionOutputReturnVo(val returnedAt: LocalDateTime,val location: String,val custodianUserId: String,val condition: String,val signedFileIds: String)

data class ReproductionOutputCustodyVo(
    val expectedOrgId: String, val expectedRevision: String, val handedOverAt: LocalDateTime,
    val fromCustodianUserId: String, val fromLocation: String,
    val toCustodianUserId: String, val toLocation: String,
    val condition: String, val reason: String, val signedFileIds: String,
)
data class ReproductionOutputCustodyPosition(val custodianUserId:String,val location:String,val since:LocalDateTime,val inUse:Boolean=false)
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
data class ReproductionOutputCustodyRecord(
    val id:String,val outputId:String,val version:Int,val record:ReproductionOutputCustodyVo,
    val observedUseIds:List<String>,val previous:ReproductionOutputCustodyPosition,
)
data class ReproductionOutputCustodyLedger(val records:List<ReproductionOutputCustodyRecord>)

/** 实际停止独立于正常完成，不自动确认归库或安全解限。 */
data class ReproductionStopVo(
    val planId: String,val stoppedAt: LocalDateTime,val operatorUserId: String,val reason: String,
    val originalUndamaged: Boolean,val physicallyPresent: Boolean,val safeForCustody: Boolean,
    val actualLocation: String,val custodianUserId: String?,val condition: String,
    val actualMeasures: String,val followUp: String,val conditionFileIds: String,val signedStopFileIds: String,
)
data class ReproductionStopClearanceSnapshot(val record: IncidentClearanceVo,val incidentProof: Map<String,String>,val version: Int=1)
