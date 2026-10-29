package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 已取得样品但停止本方案剩余工作的独立结案，不冒充研究完成。 */
data class SamplingStoppedReviewVo(
    val previousRevision: String,
    val basisRevision: String,
    val planId: String,
    val stoppedAt: LocalDateTime,
    val reviewedAt: LocalDateTime,
    val recorderUserId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    val reason: String,
    val actualWork: String,
    val unfinishedWork: String,
    val originalConditionAndCare: String,
    val sampleCustodyAndFollowUp: String,
    val authorityAndFutureWork: String,
    val collectionArchiveIndex: String,
    val stoppingBasisFileIds: String,
    val actualWorkAndCustodyFileIds: String,
    val signedReviewFileIds: String,
)

data class SamplingStoppedConfirmationVo(
    val previousRevision: String,
    val reviewSequence: Int,
    val confirmedAt: LocalDateTime,
    val opinion: String,
    val signedFileIds: String,
)

data class SamplingStoppedEventVo(
    val sequence: Int,
    val review: SamplingStoppedReviewVo? = null,
    val confirmation: SamplingStoppedConfirmationVo? = null,
    val operatorUserId: String,
)

data class SamplingStoppedClosureVo(
    val revision: String,
    val basisRevision: String,
    val eligible: Boolean,
    val events: List<SamplingStoppedEventVo>,
    val reviewSequence: Int? = null,
    val confirmedUserIds: Set<String> = emptySet(),
    val closedAt: LocalDateTime? = null,
    val reviewCurrent: Boolean = false,
)
