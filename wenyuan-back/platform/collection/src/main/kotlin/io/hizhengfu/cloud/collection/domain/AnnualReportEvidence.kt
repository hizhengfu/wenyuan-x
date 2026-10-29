package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 签署原报表上明确填写的数值，不能默认采用页面实时估计数。 */
data class AnnualDeclaredFiguresVo(
    val openingTotal: Int, val newTotal: Int, val transferInTotal: Int,
    val transferOutTotal: Int, val deregTotal: Int, val closingTotal: Int,
    val quantities: List<AnnualDeclaredUnitVo> = emptyList(), val changesDescription: String,
)
data class AnnualDeclaredUnitVo(
    val unit: String, val opening: Long, val added: Long, val transferIn: Long,
    val transferOut: Long, val deregistered: Long, val adjustment: Long, val closing: Long,
) { fun balance()=AnnualUnitBalanceVo(unit,opening,added,transferIn,transferOut,deregistered,adjustment,closing) }
data class AnnualReportSignedVo(
    val year: Int, val statisticianUserId: String, val keeperUserId: String, val leaderUserId: String,
    val signedAt: LocalDateTime, val signedReportFileIds: String, val figures: AnnualDeclaredFiguresVo,
    val reconciliationNote: String?=null, val reconciliationFileIds: String?=null,
    val replacementReason: String?=null, val replacementFileIds: String?=null,
)
data class AnnualReportSubmissionVo(
    val authorityName: String, val reportNo: String, val submittedAt: LocalDateTime, val submissionFileIds: String,
)
data class AnnualReportFilingVo(
    val authorityName: String, val filingNo: String, val filedAt: LocalDateTime, val receiptFileIds: String,
)
