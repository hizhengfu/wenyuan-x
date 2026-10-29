package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 限值由本件专业评估确定，0 表示禁止该类常规使用，不是法规统一数字。 */
data class FragileUseLimitsVo(
    val windowDays: Int,
    val maximumExtractions: Int,
    val maximumTransports: Int,
    val maximumDisplayHours: Int,
    val minimumRestHours: Int,
)
data class FragilityAssessmentVo(
    val previousAssessmentId: String? = null,
    val sourceRevision: String,
    val assessedAt: LocalDateTime,
    val fragileMaterial: Boolean,
    val materialAndCondition: String,
    val environmentRequirements: String,
    val storageProtection: String,
    val useRiskAssessment: String,
    val conservationUserId: String,
    val keeperUserId: String,
    val historyVerifiedFrom: LocalDateTime,
    val historyReviewFileIds: String,
    val assessmentFileIds: String,
    val limits: FragileUseLimitsVo? = null,
    val environmentCriteria: List<FragileEnvironmentCriterionVo> = emptyList(),
)
data class FragileTransportPlanVo(val departsAt: LocalDateTime,val arrivesAt: LocalDateTime,val route: String,val protection: String)
data class FragileUsePlanVo(
    val assessmentId: String,
    val sourceBizType: String,
    val sourceBizId: String,
    val sourceRevision: String,
    val displayFrom: LocalDateTime? = null,
    val displayTo: LocalDateTime? = null,
    val transports: List<FragileTransportPlanVo> = emptyList(),
    val environmentAndProtection: String,
    val necessityAndAlternatives: String,
    val signedPlanFileIds: String,
    val environmentSources: List<FragileEnvironmentSourceVo> = emptyList(),
)
enum class EnumFragileUseKind { DISPLAY, TRANSPORT }
data class FragileUseStartVo(
    val kind: EnumFragileUseKind,
    val segmentIndex: Int = 0,
    val startedAt: LocalDateTime,
    val operatorUserId: String,
    val actualLocationOrRoute: String,
    val actualEnvironmentAndProtection: String,
    val signedFileIds: String,
    val environmentReadings: List<FragileEnvironmentReadingVo> = emptyList(),
)
data class FragileUseEndVo(val endedAt: LocalDateTime,val actualLocation: String,val actualResult: String,val signedFileIds: String)
data class FragileUseReviewVo(val checkId: String,val signedFileIds: String)
/** 既有事实补录不产生当时的授权，不改变出入库或当前生命周期。 */
data class FragileUseHistoryVo(
    val kind: EnumFragileUseKind,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val reference: String,
    val actualDetails: String,
    val fileIds: String,
)

/** 调整只处理尚未开始的原计划段；不补造既往批准，不改写已发生事实。 */
enum class EnumFragileAdjustmentAction { CANCEL, RESCHEDULE }
data class FragileSegmentAdjustmentVo(
    val kind: EnumFragileUseKind,
    val segmentIndex: Int,
    val action: EnumFragileAdjustmentAction,
    val reason: String,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    val route: String? = null,
    val protection: String? = null,
)
data class FragileUseAdjustmentVo(
    val previousAdjustmentId: String? = null,
    val factsRevision: String,
    val reviewedAt: LocalDateTime,
    val checkId: String,
    val reason: String,
    val actualUnstartedEvidence: String,
    val adjustments: List<FragileSegmentAdjustmentVo>,
    val signedFileIds: String,
    val borrowerAgreementFileIds: String? = null,
)
data class FragileAdjustmentActors(val conservation: String,val department: String?,val keeper: String,val director: String)
data class FragileAdjustmentWithdrawalVo(val withdrawnAt: LocalDateTime,val reason: String,val signedFileIds: String)
