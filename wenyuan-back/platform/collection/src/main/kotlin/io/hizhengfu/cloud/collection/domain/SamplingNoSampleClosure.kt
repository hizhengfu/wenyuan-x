package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 已启动后没有取得样品的独立结案，不生成样品、分析或普通完成事实。 */
data class SamplingNoSampleReviewVo(
    val previousRevision: String,
    val basisRevision: String,
    val planId: String,
    val reviewedAt: LocalDateTime,
    val recorderUserId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    val reason: String,
    val actualOperations: String,
    val conditionAfter: String,
    val actualAftercare: String,
    val followUp: String,
    val collectionArchiveIndex: String,
    val noSampleEvidenceFileIds: String,
    val conditionFileIds: String,
    val signedReviewFileIds: String,
)

data class SamplingNoSampleConfirmationVo(
    val previousRevision: String,
    val reviewSequence: Int,
    val confirmedAt: LocalDateTime,
    val opinion: String,
    val signedFileIds: String,
)

data class SamplingNoSampleEventVo(
    val sequence: Int,
    val review: SamplingNoSampleReviewVo? = null,
    val confirmation: SamplingNoSampleConfirmationVo? = null,
    val operatorUserId: String,
)

data class SamplingNoSampleClosureVo(
    val revision: String,
    val basisRevision: String,
    val eligible: Boolean,
    val events: List<SamplingNoSampleEventVo>,
    val reviewSequence: Int? = null,
    val confirmedUserIds: Set<String> = emptySet(),
    val closedAt: LocalDateTime? = null,
    val reviewCurrent: Boolean = false,
)
