package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

enum class DonationRecognitionKind { SPIRITUAL, MATERIAL }

data class DonationRecognitionVo(
    val requestId: String,
    val kind: DonationRecognitionKind,
    val recipient: String,
    val occurredOn: LocalDate,
    val description: String,
    val evidenceFileIds: String,
    val necessity: String? = null,
    val materialDescription: String? = null,
    val amountCents: Long? = null,
    val approvalFileIds: String? = null,
    val receiptFileIds: String? = null,
    val correctsArchiveId: String? = null,
    val correctionReason: String? = null,
)

object DonationRecognitionPolicy {
    fun requireValid(vo: DonationRecognitionVo, acquiredOn: LocalDate, today: LocalDate = LocalDate.now()) {
        if (!vo.requestId.matches(Regex("[A-Za-z0-9-]{8,40}"))) throw BadRequestException("请重新打开鼓励记录表单后提交")
        if (vo.recipient.isBlank() || vo.description.isBlank() || vo.evidenceFileIds.isBlank()) throw BadRequestException("鼓励对象、实际内容和原件必填")
        if (vo.recipient.length > 200 || vo.description.length > 4000) throw BadRequestException("鼓励对象或内容过长")
        if (vo.occurredOn < acquiredOn || vo.occurredOn > today) throw BadRequestException("鼓励日期须在实际取得之后且不能晚于今天")
        if (vo.correctsArchiveId != null && (vo.correctsArchiveId.isBlank() || vo.correctionReason.isNullOrBlank())) throw BadRequestException("更正须指定原记录并说明原因")
        if (vo.correctsArchiveId == null && !vo.correctionReason.isNullOrBlank()) throw BadRequestException("请指定需要更正的原记录")
        if (vo.kind == DonationRecognitionKind.MATERIAL) {
            if (listOf(vo.necessity,vo.materialDescription,vo.approvalFileIds,vo.receiptFileIds).any { it.isNullOrBlank() }) throw BadRequestException("物质奖励须说明必要性、奖励内容并保留批准及领取凭证")
            if (vo.amountCents != null && vo.amountCents < 0) throw BadRequestException("奖励金额不能为负数")
        } else if (listOf(vo.necessity,vo.materialDescription,vo.approvalFileIds,vo.receiptFileIds).any { !it.isNullOrBlank() } || vo.amountCents != null) {
            throw BadRequestException("精神鼓励不能夹带物质奖励字段，请分别登记")
        }
    }
}
