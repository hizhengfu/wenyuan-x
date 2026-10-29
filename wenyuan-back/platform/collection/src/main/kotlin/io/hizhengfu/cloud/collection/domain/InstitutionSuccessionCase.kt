package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 全馆统筹清册管理工作记录，不替代法人登记、行政决定或真实清点。 */
data class InstitutionSuccessionCaseVo(
    val orgId: String? = null,
    val eventKind: EnumInstitutionSuccession,
    val sourceMuseumName: String,
    val sourceRegistrationNo: String,
    val eventDocumentNo: String,
    val decidedOn: LocalDate,
    val effectiveOn: LocalDate,
    val eventFileIds: String,
    val nonprofitLegalBasis: String,
    val legalBasisFileIds: String,
    val liquidationOrMergerPlanFileIds: String,
    val keeperUserId: String,
    val authorizedRepresentativeUserId: String,
    val representativeAuthorizationFileIds: String,
)
enum class EnumSuccessionSource { COLLECTION, AUXILIARY, ACQUISITION, DESIGNATED, UNRECORDED }
enum class EnumSuccessionCaseRecord { SCOPE, FOLLOW_UP, RESOLUTION, KEEPER_REVIEW, REPRESENTATIVE_REVIEW, ITEM_PROPOSAL, ITEM_KEEPER, ITEM_AUTHORIZATION, ITEM_HANDOVER, ITEM_INVESTIGATION, ITEM_INVESTIGATION_KEEPER, ITEM_INVESTIGATION_AUTHORIZATION }
enum class EnumSuccessionResolution { INSTITUTION_TRANSFER, DESIGNATED_REASSIGNMENT, ACQUISITION_NOT_COMPLETED, LINKED_ITEM, OTHER_ITEM_HANDOVER, DRAFT_NOT_ACQUIRED }

data class InstitutionScopeCaptureVo(
    val scanDigest: String,
    val checkedAt: LocalDateTime,
    val operatorUserId: String,
    val coverageDescription: String,
    val inventoryFileIds: String,
    val signedFileIds: String,
)
data class InstitutionUnrecordedVo(
    val recordNo: String, val name: String, val quantity: Int, val unit: String,
    val actualLocation: String, val custodianDescription: String,
    val foundAt: LocalDateTime, val operatorUserId: String,
    val identification: String, val photoFileIds: String, val signedFileIds: String,
)
data class InstitutionItemFollowUpVo(
    val itemKey: String, val recordedAt: LocalDateTime, val operatorUserId: String,
    val responsibleUserId: String, val issue: String, val actualMeasures: String,
    val nextCheckAt: LocalDateTime, val evidenceFileIds: String,
)
data class InstitutionItemResolutionVo(
    val itemKey: String, val kind: EnumSuccessionResolution, val businessId: String,
    val recordedAt: LocalDateTime, val operatorUserId: String,
    val conclusion: String, val signedFileIds: String,
)
data class InstitutionScopeReviewVo(
    val scopeRecordId: String, val reviewDigest: String, val reviewedAt: LocalDateTime,
    val reviewerUserId: String, val allLocationsAndObligationsChecked: Boolean,
    val archiveReceiverName: String, val archiveHandoverFileIds: String,
    val unresolvedStatement: String, val signedFileIds: String,
)
