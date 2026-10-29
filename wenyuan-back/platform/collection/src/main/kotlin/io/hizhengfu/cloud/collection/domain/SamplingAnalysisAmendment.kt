package io.hizhengfu.cloud.collection.domain

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

enum class EnumExistingSampleApproval { NEW_APPROVAL, EXISTING_APPROVAL_COVERS, NOT_APPLICABLE }

data class SamplingAnalysisTargetVo(
    val portionKey: String? = null,
    val laboratoryName: String,
    val method: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val maximumConsumption: BigDecimal,
    val remainingSamplePlan: String,
)

/** 仅使用已存在的样品，不含原藏品取样、原件提用或实际开始字段。 */
data class SamplingAnalysisProposalVo(
    val previousRevision: String,
    val sampleRevision: String,
    val sourceRevision: String,
    val preparedAt: LocalDateTime,
    val recorderUserId: String,
    val conservationDepartmentId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    val changeReason: String,
    val researchNeedAndPriorWork: String,
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val targets: List<SamplingAnalysisTargetVo>,
    val approvalTreatment: EnumExistingSampleApproval,
    val existingApprovalId: String? = null,
    val applicabilityExplanation: String,
    val applicabilityFileIds: String,
    val assessmentFileIds: String,
    val signedPlanFileIds: String,
)

data class SamplingAnalysisConfirmationVo(
    val previousRevision: String,
    val subjectSequence: Int,
    val confirmedAt: LocalDateTime,
    val opinion: String,
    val signedFileIds: String,
)

data class SamplingAnalysisAdministrativeVo(
    val previousRevision: String,
    val proposalSequence: Int,
    val evidence: CollectionAdministrativeEvidenceVo,
)

data class SamplingAnalysisActivationVo(
    val previousRevision: String,
    val proposalSequence: Int,
    val sampleRevision: String,
    val activatedAt: LocalDateTime,
    val verification: String,
    val signedFileIds: String,
)

data class SamplingAnalysisClosureVo(
    val previousRevision: String,
    val proposalSequence: Int,
    val sampleRevision: String,
    val endedAt: LocalDateTime,
    val reviewedAt: LocalDateTime,
    val recorderUserId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    val completedAsPlanned: Boolean,
    val actualWorkAndResults: String,
    val unfinishedWorkAndReason: String,
    val remainingCustodyAndFollowUp: String,
    val resultFileIds: String,
    val signedReviewFileIds: String,
)

data class SamplingAnalysisAmendmentEventVo(
    val sequence: Int,
    val operatorUserId: String,
    val proposal: SamplingAnalysisProposalVo? = null,
    val confirmation: SamplingAnalysisConfirmationVo? = null,
    val administrative: SamplingAnalysisAdministrativeVo? = null,
    val activation: SamplingAnalysisActivationVo? = null,
    val closure: SamplingAnalysisClosureVo? = null,
)

data class SamplingAnalysisAmendmentVo(
    val revision: String,
    val events: List<SamplingAnalysisAmendmentEventVo>,
    val proposalSequence: Int? = null,
    val confirmedUserIds: Set<String> = emptySet(),
    val administrativeSequence: Int? = null,
    val activationSequence: Int? = null,
    val closureSequence: Int? = null,
    val closureConfirmedUserIds: Set<String> = emptySet(),
    val closedAt: LocalDateTime? = null,
)

/** 启用原件同时追加到样品顺序账，只更新依据版本，不增加样品或消耗量。 */
data class SamplingAnalysisAuthorizationVo(
    val previousRevision: String,
    val proposalSequence: Int,
    val activationSequence: Int,
    val activatedAt: LocalDateTime,
)
