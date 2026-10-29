package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 借用主体按法定身份分类，不以自由文本单位名或馆内审批角色推断。 */
enum class EnumLoanBorrowerKind { STATE_MUSEUM, NON_STATE_MUSEUM, OTHER_ORGANIZATION, OVERSEAS_ORGANIZATION }
enum class EnumLoanPurpose { EXHIBITION, RESEARCH, OTHER_LAWFUL }
enum class EnumLoanObjectKind { RELIC, SCIENTIFIC_FOSSIL, OTHER }

data class LoanAssessmentVo(
    val collectionId: String,
    val objectKind: EnumLoanObjectKind,
    val condition: String,
    val poorCondition: Boolean,
    val transportDamageRisk: Boolean,
    val necessity: String,
    val riskAssessment: String,
    val assessmentFileIds: String,
)

data class LoanDossierVo(
    val borrowerKind: EnumLoanBorrowerKind,
    val borrowerRegistrationNo: String,
    val borrowerIdentityFileIds: String,
    val purpose: EnumLoanPurpose,
    val purposeDescription: String,
    val keeperUserId: String,
    val leaderUserId: String,
    val supervisionUserId: String,
    val agreedOutAt: LocalDateTime,
    val agreedReturnAt: LocalDateTime,
    val collectiveDecisionAt: LocalDateTime,
    val collectiveDecisionFileIds: String,
    val collectiveParticipantNames: Set<String> = emptySet(),
    val agreementSignedAt: LocalDateTime,
    val signedAgreementFileIds: String,
    val agreedCollectionListFileIds: String,
    val safetyResponsibilities: String,
    val transportRequirements: String,
    val storageRequirements: String,
    val useSite: String,
    val carrierName: String,
    val carrierCapabilityFileIds: String,
    val transportSupervisorName: String,
    val transportSupervisorOrganization: String,
    val transportAssignmentFileIds: String,
    val firstInspectionOn: LocalDate,
    val inspectionIntervalDays: Int,
    val inspectionPlanFileIds: String,
    val assessments: List<LoanAssessmentVo> = emptyList(),
    val otherLegalBasisFileIds: String? = null,
    val overseasProcedureFileIds: String? = null,
    /** 境外机构在境内办展也须核实适用审批；不把机构国别自动当作实物出境。 */
    val exhibitionOverseas: Boolean? = null,
    val overseasRequiredApprovalLevels: Set<EnumAuthorityLevel> = emptySet(),
    val overseasExhibition: LoanOverseasExhibitionVo? = null,
    /** 引用实际适用的国家或行业标准及版本；旧案卷缺失时不反写历史方案。 */
    val transportStandardReference: String? = null,
    val transportStandardApplicability: String? = null,
    val transportPlanFileIds: String? = null,
)

data class LoanBaselineVo(
    val sourceOwnership: EnumOwnership,
    val items: List<RequisitionCollectionBaselineVo> = emptyList(),
)

/** submittedOn 是实际向行政机关报送日期，issuedOn 是实际批准/备案日期。 */
data class LoanAdministrativeVo(
    val level: EnumAuthorityLevel,
    val action: EnumAdministrativeAction,
    val authorityName: String,
    val documentNo: String,
    val submittedOn: LocalDate,
    val issuedOn: LocalDate,
    val submissionFileIds: String,
    val fileIds: String,
    val collectionIds: Set<String> = emptySet(),
)

data class LoanHandoverVo(
    val reviewedAt: LocalDateTime,
    val reviewerUserId: String,
    val reviewAndAssignmentFileIds: String,
    val handedAt: LocalDateTime,
    val custodianUserIds: Set<String> = emptySet(),
    /** 借用单位实际人员姓名，同名人员须附单位内部编号区分；不录入身份证号码。 */
    val receivingPersonNames: Set<String> = emptySet(),
    val receivingAuthorizationFileIds: String,
    val distinctPartiesConfirmed: Boolean,
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
data class LoanHandoverRecordVo(val content: LoanHandoverVo,val approvalTaskId: String,val recordedBy: String,val recordedAt: LocalDateTime)

enum class EnumLoanMonitoringKind { TECHNICAL_GUIDANCE, SAFETY_CHECK, RECALL, MAJOR_REPORT, ISSUE_RESOLVED }
data class LoanMonitoringVo(
    val kind: EnumLoanMonitoringKind,
    val occurredAt: LocalDateTime,
    val collectionIds: Set<String> = emptySet(),
    val lenderUserId: String,
    val borrowerRepresentative: String,
    val description: String,
    val fileIds: String,
    val safetyConfirmed: Boolean? = null,
    val majorSituation: Boolean = false,
    val relatedRecordId: String? = null,
    val authorityName: String? = null,
    val reportNo: String? = null,
    val recallReturnBy: LocalDate? = null,
)

/** 整个出境展览的数量和期限，不以本馆单批件数代替全展统计。 */
data class LoanOverseasExhibitionVo(
    val exhibitionStartsOn: LocalDate,
    val exhibitionEndsOn: LocalDate,
    val totalRelicExhibits: Int,
    val firstLevelExhibits: Int,
    val wholeExhibitionListFileIds: String,
    val prohibitedCatalogCheckFileIds: String,
    val objects: List<LoanOverseasObjectVo> = emptyList(),
    /** 草签草案用于项目报批，正式协议须在批准后签订。旧资料缺失时不自动补造。 */
    val draftAgreementSignedOn: LocalDate? = null,
    val draftAgreementFileIds: String? = null,
)
data class LoanOverseasObjectVo(
    val collectionId: String,
    val uniqueOrFragileFirstLevel: Boolean,
    val prohibitedFromExit: Boolean,
    val domesticExhibitionEvidenceFileIds: String,
)

/** 追加实际延期批准及双方补充协议，不改写原展期或原借用协议。 */
data class LoanOverseasExtensionVo(
    val sourceRevision: String,
    val previousId: String? = null,
    val originalApprovalId: String,
    val specialNeed: String,
    val authorityName: String,
    val documentNo: String,
    val submittedOn: LocalDate,
    val approvedOn: LocalDate,
    val exhibitionEndsOn: LocalDate,
    val agreedReturnAt: LocalDateTime,
    val agreementSignedOn: LocalDate,
    val submissionFileIds: String,
    val approvalFileIds: String,
    val agreementFileIds: String,
)

data class LoanOverseasExtensionRecordVo(
    val request: LoanOverseasExtensionVo,
    val previousExhibitionEndsOn: LocalDate,
    val previousReturnAt: LocalDateTime,
    val approvalLate: Boolean,
    val recordedBy: String,
)

enum class EnumLoanPortStage { EXIT, REENTRY }

/** 登记实际出境放行或复进境结项，不代替行政机关审核。 */
data class LoanPortClearanceVo(
    val sourceRevision: String,
    val stage: EnumLoanPortStage,
    val originalApprovalId: String,
    val exitRecordId: String? = null,
    val collectionIds: Set<String>,
    val reviewAgency: String,
    val reviewedAt: LocalDateTime,
    val certificateNo: String,
    val certificateFileIds: String,
    val identifiers: Map<String,String>,
    val portName: String,
    val designatedPortFileIds: String? = null,
    val customsAt: LocalDateTime,
    val customsDocumentNo: String,
    val customsFileIds: String,
)
data class LoanPortClearanceRecordVo(val request: LoanPortClearanceVo, val recordedBy: String)

enum class EnumOverseasFilingKind { AGREEMENT, CONCLUSION }
data class LoanOverseasFilingVo(
    val sourceRevision:String,
    val kind:EnumOverseasFilingKind,
    val previousId:String?=null,
    val correctionReason:String?=null,
    val authorityLevel:EnumAuthorityLevel=EnumAuthorityLevel.NATIONAL,
    val authorityName:String,
    val submissionNo:String,
    val submittedOn:LocalDate,
    val submissionReceiptFileIds:String,
    val endingRecordId:String?=null,
    val actualExhibitionEndedOn:LocalDate?=null,
    val endingEvidenceFileIds:String?=null,
    val conclusionFormFileIds:String?=null,
    val conclusionReportFileIds:String?=null,
    val audiovisualFileIds:String?=null,
)
data class LoanOverseasFilingRecordVo(val request:LoanOverseasFilingVo,val dueOn:LocalDate,val late:Boolean,val recordedBy:String)

data class LoanExhibitionEndVo(
    val sourceRevision:String,
    val actualEndedOn:LocalDate,
    val evidenceFileIds:String,
    val previousId:String?=null,
    val correctionReason:String?=null,
)
data class LoanExhibitionEndRecordVo(val request:LoanExhibitionEndVo,val recordedBy:String)
