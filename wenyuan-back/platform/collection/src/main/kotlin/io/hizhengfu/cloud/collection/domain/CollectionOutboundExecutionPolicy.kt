package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

data class CollectionOutboundExecutionVo(
    val conditionNote: String? = null,
    val conditionPhotoIds: String? = null,
    val signedVoucherAttachmentIds: String? = null,
)

/** 审批通过后的现场出库交接证据校验。 */
object CollectionOutboundExecutionPolicy {
    fun requireEvidence(vo: CollectionOutboundExecutionVo) {
        if (vo.conditionNote.isNullOrBlank()) throw BadRequestException("出库前完残状况记录必填")
        if (vo.conditionPhotoIds.isNullOrBlank()) throw BadRequestException("出库前多角度状况照片必填")
        if (vo.signedVoucherAttachmentIds.isNullOrBlank()) throw BadRequestException("签署后的出库凭证扫描件必填")
    }
}
