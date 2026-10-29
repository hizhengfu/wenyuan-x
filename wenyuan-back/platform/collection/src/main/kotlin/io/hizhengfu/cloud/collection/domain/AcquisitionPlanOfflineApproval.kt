package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDateTime

/** 记录纸质批准事实，批准人可以是馆内或外部人员，不冒充线上流程签署。 */
data class AcquisitionPlanOfflineApprovalVo(
    val approvedAt: LocalDateTime? = null,
    val approvedBy: String? = null,
    val approvalBasis: String? = null,
    val signedFileIds: String? = null,
    val supplementaryReason: String? = null,
)

internal object AcquisitionPlanOfflineApprovalPolicy {
    fun normalize(value: AcquisitionPlanOfflineApprovalVo, now: LocalDateTime = LocalDateTime.now()): AcquisitionPlanOfflineApprovalVo {
        val time = value.approvedAt ?: throw BadRequestException("请填写纸质计划实际批准时间")
        if (time.isAfter(now)) throw BadRequestException("线下批准时间不能填写未来时间")
        fun text(value: String?, label: String) = value?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("请填写$label")
        return value.copy(approvedAt = time.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
            approvedBy = text(value.approvedBy, "纸质计划批准人或批准机构"),
            approvalBasis = text(value.approvalBasis, "批准文号或批准依据"),
            signedFileIds = text(value.signedFileIds, "已经完成签署批准的计划原件"),
            supplementaryReason = value.supplementaryReason?.trim()?.takeIf { it.isNotBlank() })
    }
}
