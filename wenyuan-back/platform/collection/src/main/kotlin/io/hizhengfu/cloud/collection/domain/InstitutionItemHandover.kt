package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumInstitutionItemRoute { RECORD_LINK, NON_COLLECTION_SUCCESSION, RETURN_NON_COLLECTION_CUSTODY }
enum class EnumInstitutionRecipient { PUBLIC_BENEFIT_ORGANIZATION, STATE_TEACHING_RESEARCH, LAWFUL_OWNER }
data class InstitutionHandoverPieceVo(val pieceNo:String,val identification:String,val condition:String,val photoFileIds:String)
data class InstitutionItemProposalVo(
    val itemKey:String,val proposedAt:LocalDateTime,val operatorUserId:String,
    val route:EnumInstitutionItemRoute,val identificationConclusion:String,val identificationFileIds:String,
    val linkedItemKey:String?=null,
    val previousProposalId:String?=null,val revisionReason:String?=null,val revisionFileIds:String?=null,
    val nonCollectionConfirmed:Boolean?=null,val propertyBasisFileIds:String?=null,
    val ownership:EnumOwnership?=null,val ownerName:String?=null,val ownerIdentity:String?=null,
    val recipientKind:EnumInstitutionRecipient?=null,val recipientName:String?=null,val recipientIdentity:String?=null,
    val recipientQualificationFileIds:String?=null,val dispositionBasisFileIds:String?=null,
    val thirdPartyRightsResolved:Boolean?=null,val thirdPartyRightsFileIds:String?=null,
    val publicBenefitPurpose:String?=null,val noPrivateDistribution:Boolean?=null,
    val custodyAgreementFileIds:String?=null,val returnAuthorizationFileIds:String?=null,
    val pieces:List<InstitutionHandoverPieceVo> = emptyList(),
)
data class InstitutionItemApprovalVo(
    val proposalRecordId:String,val confirmedAt:LocalDateTime,val confirmerUserId:String,
    val conclusion:String,val signedFileIds:String,
)
data class InstitutionItemHandoverVo(
    val proposalRecordId:String,val handedAt:LocalDateTime,val operatorUserId:String,
    val recipientName:String,val recipientIdentity:String,val actualReceivingLocation:String,
    val senderNames:Set<String>,val receiverNames:Set<String>,val pieces:List<InstitutionHandoverPieceVo>,
    val identityMatches:Boolean,val conditionMatches:Boolean,val discrepancy:String?=null,val protectiveMeasures:String?=null,
    val signedReceiptFileIds:String,val archiveHandoverFileIds:String,
)
