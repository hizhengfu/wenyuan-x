package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumReturnSecurityIncident { NONE, THEFT, ROBBERY, LOSS }
enum class EnumReturnReportTarget { COMPETENT, PROVINCE, NATIONAL, POLICE }
enum class EnumReturnReportKind { INCIDENT, RESULT_FILING }

/** 每条均对应实际收到的实物；缺件应留在来源未归清单，不得虚报归还。 */
data class ReturnItemCheckVo(
    val collectionId: String,
    val objectKind: EnumLoanObjectKind,
    val authenticityMatches: Boolean,
    val authenticityMethod: String,
    val conditionMatches: Boolean,
    val condition: String,
    val textNeedsImages: Boolean,
    val imageFileIds: String? = null,
    val damaged: Boolean,
    val securityIncident: EnumReturnSecurityIncident,
    val anomalyDescription: String? = null,
    val protectiveMeasures: String? = null,
) {
    fun abnormal() = !authenticityMatches || !conditionMatches || damaged || securityIncident != EnumReturnSecurityIncident.NONE
}

data class ReturnInspectionVo(
    val inspectedAt: LocalDateTime,
    val custodianUserIds: Set<String> = emptySet(),
    val returningUserIds: Set<String> = emptySet(),
    val returningPersonNames: Set<String> = emptySet(),
    val returningAuthorizationFileIds: String,
    val distinctPartiesConfirmed: Boolean,
    val originalDocumentsChecked: Boolean,
    val items: List<ReturnItemCheckVo> = emptyList(),
    val signedHandoverFileIds: String,
    val replacementReason: String? = null,
    /** 借用归还运输的实际事实；旧已执行归还保持原记录。 */
    val transportStandardReference: String? = null,
    val transportStandardApplicability: String? = null,
    val transportCarrierName: String? = null,
    val transportCarrierCapabilityFileIds: String? = null,
    val transportSupervisorName: String? = null,
    val transportSupervisionFileIds: String? = null,
    val packingStandardConfirmed: Boolean? = null,
    val packingCheckFileIds: String? = null,
    /** 补充或更正时所见上一版逐件点交永久档案 ID；首次登记留空。 */
    val expectedInspectionArchiveId: String? = null,
)

data class ReturnSourceItemVo(val collectionId: String,val content: CollectionCatalogContentVo,val outboundCondition: RequisitionConditionVo)
data class ReturnSourceSnapshotVo(
    val outAt: LocalDateTime,
    val outSignedFileIds: String,
    val agreementFileIds: String,
    val keeperUserId: String,
    val departmentId: String? = null,
    val items: List<ReturnSourceItemVo> = emptyList(),
)

data class ReturnReportVo(
    val collectionId: String,
    val target: EnumReturnReportTarget,
    val kind: EnumReturnReportKind,
    val authorityName: String,
    val reportNo: String,
    val reportedAt: LocalDateTime,
    val description: String,
    val fileIds: String,
)

/** 处理完成必须依据双方书面说明、原因调查、负责人员签署和实际行政报告。 */
data class ReturnResolutionVo(
    val collectionId: String,
    val completedAt: LocalDateTime,
    val cause: String,
    val custodianExplanationFileIds: String,
    val returningExplanationFileIds: String,
    val investigationFileIds: String,
    val confirmedAuthentic: Boolean,
    val safeForStorage: Boolean,
    val finalCondition: String,
    val resultFileIds: String,
    val keeperUserId: String,
    val leaderUserId: String,
    val approvedAt: LocalDateTime,
    val signedApprovalFileIds: String,
    val reportDetermination: String,
    val reportDeterminationFileIds: String,
    val additionalReportTargets: Set<EnumReturnReportTarget> = emptySet(),
    val incidentId: String? = null,
    val expectedResponsibilityId: String? = null,
)
