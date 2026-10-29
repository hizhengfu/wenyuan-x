package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException

internal object ReturnClosurePolicy {
    fun requireReturnAllowed(flowStatus: EnumFlowStatus?, returned: Boolean) {
        if (flowStatus != EnumFlowStatus.PASS) throw BadRequestException("审批通过后才能办理返还")
        if (returned) throw BadRequestException("该单据已办理返还，不能重复返还")
    }

    fun requireCollectionStatus(status: EnumCollectionStatus, expected: EnumCollectionStatus, actionName: String) {
        if (status != expected) {
            throw BadRequestException("藏品当前状态不匹配，不能办理$actionName")
        }
    }
}

internal object ReturnEvidencePolicy {
    fun requireSubmittable(
        reviewOpinion: String?,
        toWarehouseId: String?,
        returnDate: java.time.LocalDate?,
        operator: String?,
        reviewer: String?,
        custodian: String?,
        handoverUser: String?,
        damageNote: String?,
        damagePhotoIds: String?,
        signedVoucherAttachmentIds: String?,
    ) {
        if (reviewOpinion.isNullOrBlank()) throw BadRequestException("归还验收意见必填")
        if (toWarehouseId.isNullOrBlank()) throw BadRequestException("归还入库库位必填")
        if (returnDate == null) throw BadRequestException("实际归还日期必填")
        if (operator.isNullOrBlank() || reviewer.isNullOrBlank() || custodian.isNullOrBlank() || handoverUser.isNullOrBlank()) {
            throw BadRequestException("经办人、复核人、保管员和交接人均必填")
        }
        if (signedVoucherAttachmentIds.isNullOrBlank()) throw BadRequestException("双方签署归还凭证扫描件必填")
        if (!damageNote.isNullOrBlank() && damagePhotoIds.isNullOrBlank()) throw BadRequestException("存在损伤记录时必须上传损伤照片")
    }
}

/** 部分归库：一张来源单可拆多张归还单，按子集归还，全部归完才关闭来源单。 */
internal object PartialReturnPolicy {
    /** 未归明细 = 来源全量 − 已完成归还单持有的明细。 */
    fun outstanding(sourceIds: List<String>, returnedIds: Collection<String>): List<String> =
        sourceIds.filterNot { returnedIds.contains(it) }

    /**
     * 解析本次归还子集：未显式指定时默认取全部可归明细；
     * 显式指定时必须 ⊆ 可归明细（未归且未被其他进行中归还单占用）。
     */
    fun resolveSubset(requested: List<String>, available: List<String>): List<String> {
        if (available.isEmpty()) throw BadRequestException("该单据没有可归还的藏品")
        if (requested.isEmpty()) return available
        val availableSet = available.toSet()
        val invalid = requested.filterNot { availableSet.contains(it) }
        if (invalid.isNotEmpty()) {
            throw BadRequestException("以下藏品不在可归还范围（已归还或已被其他归还单占用）：${invalid.joinToString(",")}")
        }
        return requested
    }

    /** 本批归还后是否全部归完。 */
    fun fullyReturned(sourceIds: List<String>, returnedIds: Collection<String>, batch: Collection<String>): Boolean =
        outstanding(sourceIds, returnedIds.toSet() + batch).isEmpty()
}
