package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 真实初步鉴选及向省级文物行政部门提交的定级申请。 */
data class GradingApplicationVo(
    val preliminaryAt: LocalDateTime,
    val preliminaryFileIds: String,
    val proposedLevel: EnumRelicLevel,
    val itemDescription: String,
    val identificationPhotoFileIds: String,
    val provincialAuthority: String,
    val applicationNo: String,
    val submittedAt: LocalDateTime,
    val applicationFileIds: String,
    val receiptFileIds: String,
    val replacementReason: String? = null,
)

/** 省级部门组织定级的正式结果及省级备案原件，不使用馆内审批状态代替。 */
data class GradingResultVo(
    val provincialAuthority: String,
    val documentNo: String,
    val determinedAt: LocalDateTime,
    val level: EnumRelicLevel,
    val resultFileIds: String,
    val provincialFilingNo: String,
    val provincialFiledAt: LocalDateTime,
    val provincialFilingFileIds: String,
)

data class GradingNationalFilingVo(
    val submittingProvincialAuthority: String,
    val nationalAuthority: String,
    val submittedAt: LocalDateTime,
    val submissionFileIds: String,
    val filingNo: String,
    val filedAt: LocalDateTime,
    val receiptFileIds: String,
)
