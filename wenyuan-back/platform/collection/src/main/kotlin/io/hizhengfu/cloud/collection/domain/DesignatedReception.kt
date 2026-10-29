package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 第十五条的指定接收对象，尚未因此成为本馆正式藏品。 */
enum class EnumDesignatedSender { ARCHAEOLOGY, LAW_ENFORCEMENT, JUDICIAL, SUPERVISORY, OTHER_LAWFUL }
enum class EnumDesignatedRecord { ASSESSMENT, CUSTODY, ACCEPTANCE, REFERRAL, REASSIGNMENT, SETTLEMENT }
enum class EnumDesignatedHandover { FROM_THIS_MUSEUM, DIRECT_FROM_SENDER }
data class DesignatedItemVo(
    val sourceItemNo:String,val name:String,val nature:EnumCollectionNature,
    val quantity:Int,val unit:String,val completeness:EnumCompleteness,val condition:String,val imageFileIds:String,
    val categoryId:String?=null,val era:String?=null,val texture:String?=null,val dimensions:DimensionVo?=null,val weight:String?=null,
)
data class DesignatedNoticeVo(
    val orgId:String?=null,val authorityLevel:EnumAuthorityLevel,val authorityCode:String,val authorityName:String,
    val documentNo:String,val issuedOn:LocalDate,val notifiedOn:LocalDate,val designatedReceiverName:String,
    val senderKind:EnumDesignatedSender,val senderName:String,val senderIdentity:String,val transferLegalBasis:String,
    val documentFileIds:String,val item:DesignatedItemVo,
)
data class DesignatedProfessionalOpinionVo(
    val name:String,val organization:String,val specialty:String,val qualificationFileIds:String,
    val authenticityOpinion:String,val valueOpinion:String,val meetsStandards:Boolean,val signedOpinionFileIds:String,
)
data class DesignatedAssessmentVo(
    val assessedAt:LocalDateTime,val operatorUserId:String,val standardsFileIds:String,val standardsConclusion:String,
    val meetsStandards:Boolean,val opinions:List<DesignatedProfessionalOpinionVo>,val signedFileIds:String,
    val previousId:String?=null,val correctionReason:String?=null,val correctionFileIds:String?=null,
)
/** 接收前临时实物保管原件。须记录逐件实际收到的对象，不生成藏品总账或虚构取得日期。 */
data class DesignatedCustodyVo(
    val checkedAt:LocalDateTime,val operatorUserId:String,val warehouseId:String,val custodianUserId:String,
    val handoverNames:Set<String>,val pieces:List<AccessionPieceVo>,val signedFileIds:String,
    val previousId:String?=null,val correctionReason:String?=null,val correctionFileIds:String?=null,
)
/** 本人核对依法取得及接收准备。仍须通过入馆凭证逐件核收和后续登记。 */
data class DesignatedAcceptanceVo(
    val actualAcquiredOn:LocalDate,val acquisitionBasisFileIds:String,val confirmedAt:LocalDateTime,
    val keeperUserId:String,val conclusion:String,val signedFileIds:String,
)
data class DesignatedReferralVo(
    val reportedAt:LocalDateTime,val operatorUserId:String,val authorityCode:String,
    val referenceNo:String,val requestSummary:String,val requestFileIds:String,val deliveryReceiptFileIds:String,
)
data class DesignatedReassignmentVo(
    val decidedOn:LocalDate,val recordedByUserId:String,val authorityCode:String,val authorityName:String,
    val documentNo:String,val recipientName:String,val recipientIdentity:String,val recipientKind:String,
    val sourceItemNo:String,val documentFileIds:String,
)
data class DesignatedSettlementVo(
    val handedAt:LocalDateTime,val recordedByUserId:String,val mode:EnumDesignatedHandover,
    val recipientName:String,val recipientIdentity:String,val senderNames:Set<String>,val receiverNames:Set<String>,
    val pieces:List<AccessionPieceVo>,val actualResult:String,val signedReceiptFileIds:String,
)
/** 取得依据共用的是事实，不把行政指定虚构成购买合同或集体决策。 */
data class AcquisitionPreparedSource(val nature:EnumCollectionNature,val actualAcquiredOn:LocalDate)
