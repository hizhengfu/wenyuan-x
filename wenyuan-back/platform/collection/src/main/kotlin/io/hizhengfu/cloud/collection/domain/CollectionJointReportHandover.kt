package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

data class CollectionJointCurrentReportVo(
    val expectedRoundDigest: String,
    val appointmentFileIds: String,
    val changeReason: String,
    val report: CollectionJointSafetyReportVo,
)

data class CollectionJointCurrentReportRecord(
    val orgId: String,
    val requisitionId: String,
    val roundId: String,
    val request: CollectionJointCurrentReportVo,
    val registeredAt: LocalDateTime,
)

/** 对已实际形成、尚待主要负责人处理的报告另行移交，不替旧接收人补签。 */
data class CollectionJointReportHandoverVo(
    val previousId: String? = null,
    val expectedReportDigest: String,
    val handedAt: LocalDateTime,
    val keeperUserId: String,
    val directorUserId: String,
    val reason: String,
    val outstandingMatters: String,
    val appointmentFileIds: String,
    val handoverFileIds: String,
)

data class CollectionJointReportHandoverRecord(
    val id: String,
    val orgId: String,
    val requisitionId: String,
    val roundId: String,
    val request: CollectionJointReportHandoverVo,
    val reportArchiveId: String,
    val registeredBy: String,
    val registeredAt: LocalDateTime,
)
