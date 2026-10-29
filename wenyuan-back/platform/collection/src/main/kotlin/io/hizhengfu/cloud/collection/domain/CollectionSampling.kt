package io.hizhengfu.cloud.collection.domain

import java.math.BigDecimal
import java.time.LocalDateTime

enum class EnumSamplingPurpose { CONSERVATION, RESEARCH }
enum class EnumSamplingUnit { MG, G, MM3, CM3, UL, ML }

data class SamplingAlternativeVo(
    val description: String,
    val samePeriod: Boolean,
    val sameType: Boolean,
    val sameMaterial: Boolean,
    val canReplace: Boolean,
    val assessment: String,
    val fileIds: String,
)

data class SamplingPointVo(
    val key: String,
    val location: String,
    val locationFileIds: String,
    val material: String,
    val method: String,
    val minimumInterventionReason: String,
    val maximumQuantity: BigDecimal,
    val unit: EnumSamplingUnit,
    val maximumCount: Int,
    val protectionMeasures: String,
)

data class SamplingPlanVo(
    val sourceRevision: String,
    val preparedAt: LocalDateTime,
    val conservationDepartmentId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    val purpose: EnumSamplingPurpose,
    val specialNeed: String,
    val nonDestructiveAlternatives: String,
    val alternativesFileIds: String,
    val substituteSearchScope: String,
    val substituteSearchFileIds: String,
    val alternatives: List<SamplingAlternativeVo> = emptyList(),
    val originalIndispensable: Boolean,
    val indispensabilityReason: String,
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val location: String,
    val points: List<SamplingPointVo>,
    val laboratoryName: String,
    val analysisMethod: String,
    val analysisMayConsumeSample: Boolean,
    val remainingSamplePlan: String,
    val safetyAndAftercarePlan: String,
    val conditionFileIds: String,
    val signedJointPlanFileIds: String,
    val replacementReason: String? = null,
    val replacementFileIds: String? = null,
)

data class SamplingStartVo(
    val planId: String,
    val startedAt: LocalDateTime,
    val operatorUserId: String,
    val requisitionId: String? = null,
    val onSiteWarehouseLogId: String? = null,
    val originalSuitable: Boolean,
    val siteSafe: Boolean,
    val safetyChecks: String,
    val safetyFileIds: String,
    val signedStartFileIds: String,
)

/** 真实取样事实独立于合规结论；超出批准范围或意外损伤须如实登记并进入异常待处置。 */
data class SamplingTakeVo(
    val number: String,
    val pointKey: String,
    val takenAt: LocalDateTime,
    val operatorUserId: String,
    val actualLocation: String,
    val quantity: BigDecimal,
    val unit: EnumSamplingUnit,
    val withinApprovedScope: Boolean,
    val unexpectedDamage: Boolean,
    val conditionAfter: String,
    val actualMeasures: String,
    val sampleLocation: String,
    val custodianUserId: String,
    val markingFileIds: String,
    val conditionFileIds: String,
    val signedTakeFileIds: String,
    val exceptionExplanation: String? = null,
    val exceptionFileIds: String? = null,
)

data class SamplingExtractionEndVo(
    val endedAt: LocalDateTime,
    val conservationUserId: String,
    val keeperUserId: String,
    val sampleIds: Set<String> = emptySet(),
    val conditionAfter: String,
    val actualAftercare: String,
    val safeToReleaseOriginal: Boolean,
    val conditionFileIds: String,
    val signedEndFileIds: String,
)

/** 本轮分析后已消耗量与剩余样品量必须守恒；保留真实样品保管及签署依据。 */
data class SamplingAnalysisVo(
    val analyzedAt: LocalDateTime,
    val laboratoryName: String,
    val operatorUserId: String,
    val methodAndResult: String,
    val consumedQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val remainingLocation: String?,
    val remainingCustodianUserId: String?,
    val reportFileIds: String,
    val signedAnalysisAndCustodyFileIds: String,
)

data class SamplingCompletionVo(
    val completedAt: LocalDateTime,
    val conservationUserId: String,
    val keeperUserId: String,
    val sampleIds: Set<String>,
    val review: String,
    val collectionArchiveIndex: String,
    val signedReviewFileIds: String,
)
