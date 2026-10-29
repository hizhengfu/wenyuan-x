package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 以完整馆内提用方案明确第三十二条的责任、用途、安全和时间。 */
data class RequisitionDossierVo(
    val departmentGroupId: String,
    val departmentHeadUserId: String,
    val keeperUserId: String,
    val leaderUserId: String,
    val useResponsibleUserId: String,
    val usageMethod: String,
    val purposeDescription: String,
    val usageSite: String,
    val safetyRequirements: String,
    val safetyPlanFileIds: String,
    val expectedOutAt: LocalDateTime,
    val expectedReturnAt: LocalDateTime,
    /** 明确本次馆内提用是否发生需包装运输的实体移动；历史单据保持未知。 */
    val transportRequired: Boolean? = null,
    val transportDecisionReason: String? = null,
    val transportStandardReference: String? = null,
    val transportStandardApplicability: String? = null,
    val transportCarrierName: String? = null,
    val transportCarrierCapabilityFileIds: String? = null,
    val transportPlanFileIds: String? = null,
)
data class RequisitionCollectionBaselineVo(val collectionId: String,val content: CollectionCatalogContentVo)
data class RequisitionBaselineVo(val items: List<RequisitionCollectionBaselineVo> = emptyList())
data class RequisitionConditionVo(
    val collectionId: String,
    val condition: String,
    val textNeedsImages: Boolean,
    val imageFileIds: String? = null,
)
/** 记录真实纸质/电子签署，不把填入人员账号本身当作其签名。 */
data class RequisitionHandoverVo(
    val handedAt: LocalDateTime,
    val reviewedAt: LocalDateTime,
    val reviewerUserId: String,
    val reviewAndAssignmentFileIds: String,
    val custodianUserIds: Set<String> = emptySet(),
    val receivingUserIds: Set<String> = emptySet(),
    val items: List<RequisitionConditionVo> = emptyList(),
    val safetyMeasuresConfirmed: Boolean,
    val siteSafetyConfirmed: Boolean,
    val safetyCheckFileIds: String,
    val signedHandoverFileIds: String,
    val replacementReason: String? = null,
    val appliedTransportStandardReference: String? = null,
    val packingStandardConfirmed: Boolean? = null,
    val packingCheckFileIds: String? = null,
)
data class RequisitionHandoverRecordVo(
    val content: RequisitionHandoverVo,
    val approvalTaskId: String,
    val recordedBy: String,
    val recordedAt: LocalDateTime,
)
