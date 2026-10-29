package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 新原件取样申请的前案关联，不产生取样、分析、批准或移交事实。 */
data class SamplingContinuationRequestVo(
    val predecessorPlanId: String,
    val basisRevision: String,
    val requestedAt: LocalDateTime,
    val recorderUserId: String,
    val title: String,
    val changeReason: String,
    val changedScope: String,
    val previousWorkAndSamplesAssessment: String,
    val cumulativeInterventionAssessment: String,
    val changeBasisFileIds: String,
    val assessmentFileIds: String,
)

data class SamplingContinuationVo(
    val predecessorId: String,
    val successorId: String,
    val collectionId: String,
    val closedRevision: String,
    val priorWorkSnapshot: String,
    val request: SamplingContinuationRequestVo,
)

data class SamplingContinuationStartVo(
    val predecessor: SamplingContinuationVo,
    val currentPriorWorkSnapshot: String,
    val startedAt: LocalDateTime,
)
