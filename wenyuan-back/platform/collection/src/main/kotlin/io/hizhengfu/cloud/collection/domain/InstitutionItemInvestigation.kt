package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 本次核查的实际现状；不覆盖原交接批次的不一致事实。 */
data class InstitutionItemInvestigationPieceVo(
    val pieceNo:String,val identification:String,val condition:String,
    val nonCollectionConfirmed:Boolean,val identityConfirmed:Boolean,val present:Boolean,val safeCustody:Boolean,
    val recipientName:String?=null,val recipientIdentity:String?=null,
    val actualLocation:String?=null,val custodianDescription:String?=null,val photoFileIds:String?=null,
)
data class InstitutionItemInvestigationVo(
    val handoverRecordId:String,val basisDigest:String,val investigatedAt:LocalDateTime,val operatorUserId:String,
    val cause:String,val actions:String,val investigationFileIds:String,val signedFileIds:String,
    val pieces:List<InstitutionItemInvestigationPieceVo>,
    val thirdPartyRightsResolved:Boolean,val thirdPartyRightsFileIds:String,
    val reportingAssessment:String,val reportingBasisFileIds:String,val reportingRequired:Boolean,val reportingCompleted:Boolean,
    val allIssuesResolved:Boolean,
    val senderStatementFileIds:String?=null,val receiverStatementFileIds:String?=null,val reportFileIds:String?=null,
    val remainingIssues:String?=null,val nextCheckAt:LocalDateTime?=null,val responsibleUserId:String?=null,
    val previousInvestigationId:String?=null,val revisionReason:String?=null,val revisionFileIds:String?=null,
)
data class InstitutionItemInvestigationConfirmationVo(
    val investigationRecordId:String,val confirmedAt:LocalDateTime,val confirmerUserId:String,
    val conclusion:String,val signedFileIds:String,
)
