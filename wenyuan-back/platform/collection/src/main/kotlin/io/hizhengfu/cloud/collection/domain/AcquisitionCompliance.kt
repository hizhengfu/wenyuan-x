package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** 取得前调查、鉴定与来源识别的原始依据，不代替专家书面原件。 */
data class AcquisitionDossierVo(
    val nature: EnumCollectionNature,
    val investigatedOn: LocalDate,
    val standardsFileIds: String,
    val authenticityValueFileIds: String,
    val sourceInvestigationFileIds: String,
    val lawfulSource: Boolean,
    val conflictDisclosureFileIds: String,
    val interestedPartyUserIds: Set<String> = emptySet(),
    val intentionFileIds: String? = null,
)

data class AcquisitionExpertOpinionVo(
    val id: String? = null,
    val expertKey: String,
    val expertName: String,
    val userId: String? = null,
    val organization: String,
    val professionalTitle: String,
    val specialty: String,
    val qualificationFileIds: String,
    val independenceFileIds: String,
    val authenticityOpinion: String,
    val valueOpinion: String,
    val provenanceOpinion: String,
    val proposedPriceCents: Long? = null,
    val agree: Boolean,
    val signedOpinionFileIds: String,
    val assessedOn: LocalDate,
)

data class AcquisitionCompletionVo(
    val decidedOn: LocalDate,
    val collectiveDecisionFileIds: String,
    val agreementFileIds: String,
    val agreedOn: LocalDate,
    val actualAcquiredOn: LocalDate,
    val handoverReceiptFileIds: String,
    val publicDisclosurePlan: String,
    // 购买估价与谈判资料，金额统一为分；估价依据必须包含评估均值及同类藏品、市场成交参照。
    val comparablePriceFileIds: String? = null,
    val suggestedPriceCents: Long? = null,
    val valuationReason: String? = null,
    val highValueAssessmentRequired: Boolean? = null,
    val highValueDeterminationFileIds: String? = null,
    val assetAssessmentPriceCents: Long? = null,
    val assetAssessmentFileIds: String? = null,
    val negotiatedPriceCents: Long? = null,
    val negotiatedOn: LocalDate? = null,
    val negotiationFileIds: String? = null,
    val paymentFileIds: String? = null,
    // 捐赠合同的逐项条款位置/原文摘录，便于审核原件是否齐备。
    val donation: DonationAgreementTermsVo? = null,
)
data class DonationAgreementTermsVo(
    val itemDetails: String,
    val lawfulSourceAndClearTitle: String,
    val handoverAcceptance: String,
    val ownershipTransfer: String,
    val rightsAndDuties: String,
    val specialCircumstances: String,
    val disputeResolution: String,
)

object AcquisitionCompliancePolicy {
    fun requireDossier(dossier: AcquisitionDossierVo,source: EnumSource?,today: LocalDate=LocalDate.now()) {
        if (!dossier.lawfulSource) throw BadRequestException("不得征集来源不合法或来源不明的藏品")
        if (listOf(dossier.standardsFileIds,dossier.authenticityValueFileIds,dossier.sourceInvestigationFileIds,dossier.conflictDisclosureFileIds).any(String::isBlank)) throw BadRequestException("入藏标准、真伪价值鉴定、合法来源调查和利益回避资料必填")
        if (source == EnumSource.PURCHASE && dossier.intentionFileIds.isNullOrBlank()) throw BadRequestException("购买征集须保留调查及意向确定资料")
        if (dossier.investigatedOn.isAfter(today)) throw BadRequestException("调查日期不能晚于今天")
    }
    fun requireExpert(opinion: AcquisitionExpertOpinionVo,dossier: AcquisitionDossierVo,source: EnumSource?,today: LocalDate=LocalDate.now()) {
        if (listOf(opinion.expertKey,opinion.expertName,opinion.organization,opinion.professionalTitle,opinion.specialty,opinion.qualificationFileIds,opinion.independenceFileIds,opinion.authenticityOpinion,opinion.valueOpinion,opinion.provenanceOpinion,opinion.signedOpinionFileIds).any(String::isBlank)) throw BadRequestException("专家身份、高级职称、研究方向、独立声明及真伪、价值、流传经历的逐件书面意见必填")
        if (opinion.userId != null && opinion.userId in dossier.interestedPartyUserIds) throw BadRequestException("利益相关人员应当回避，不得参与征集鉴定评估")
        if (opinion.assessedOn.isAfter(today) || opinion.assessedOn.isBefore(dossier.investigatedOn)) throw BadRequestException("专家评估应在调查后实际完成")
        if (source == EnumSource.PURCHASE && (opinion.proposedPriceCents == null || opinion.proposedPriceCents<0)) throw BadRequestException("购买征集每名专家须独立提出评估价格")
    }
    fun averagePrice(opinions: List<AcquisitionExpertOpinionVo>): BigDecimal {
        if (opinions.isEmpty() || opinions.any { it.proposedPriceCents == null || it.proposedPriceCents<0 }) throw BadRequestException("专家价格不完整")
        return opinions.fold(BigDecimal.ZERO) { sum,item -> sum.add(BigDecimal.valueOf(item.proposedPriceCents!!)) }.divide(BigDecimal.valueOf(opinions.size.toLong()),2,RoundingMode.HALF_UP)
    }
    fun requireCompletion(source: EnumSource?,dossier: AcquisitionDossierVo,opinions: List<AcquisitionExpertOpinionVo>,completion: AcquisitionCompletionVo,today: LocalDate=LocalDate.now()) {
        requireDossier(dossier,source,today)
        if (opinions.any { !it.agree }) throw BadRequestException("一名专家不同意征集，程序已经终止")
        if (listOf(completion.collectiveDecisionFileIds,completion.agreementFileIds,completion.handoverReceiptFileIds,completion.publicDisclosurePlan).any(String::isBlank)) throw BadRequestException("集体决策、签订协议、取得交接原件及信息公布安排必填")
        if (completion.decidedOn.isBefore(dossier.investigatedOn) || completion.agreedOn.isBefore(completion.decidedOn) || completion.actualAcquiredOn.isBefore(completion.agreedOn) || completion.actualAcquiredOn.isAfter(today)) throw BadRequestException("须依次完成调查、集体决策、订立协议及实际取得，不得记录未来的完成事实")
        if (opinions.any { it.assessedOn.isAfter(completion.decidedOn) }) throw BadRequestException("集体决策应在全部专家评估后进行")
        if (source == EnumSource.PURCHASE) {
            if (opinions.map { it.expertKey }.distinct().size<3) throw BadRequestException("购买征集须有至少三名相应研究方向高级专家的独立书面意见")
            averagePrice(opinions)
            if (listOf(completion.comparablePriceFileIds,completion.valuationReason,completion.highValueDeterminationFileIds,completion.negotiationFileIds,completion.paymentFileIds).any { it.isNullOrBlank() } || completion.highValueAssessmentRequired == null) throw BadRequestException("估价比较依据、估价说明、高价资产评估适用依据、谈判及支付凭证必填")
            val ceiling=completion.suggestedPriceCents ?: throw BadRequestException("估价建议必填")
            val agreed=completion.negotiatedPriceCents ?: throw BadRequestException("实际谈判成交价必填")
            if (ceiling<0 || agreed<0 || agreed>ceiling) throw BadRequestException("谈判或协商成交价不得超过保密估价建议上限")
            if (completion.highValueAssessmentRequired && (completion.assetAssessmentPriceCents != ceiling || completion.assetAssessmentFileIds.isNullOrBlank())) throw BadRequestException("高价征集须依法资产评估，并以评估结果作为估价建议")
            val negotiation=completion.negotiatedOn ?: throw BadRequestException("实际谈判日期必填")
            if (negotiation.isBefore(completion.decidedOn) || negotiation.isAfter(completion.agreedOn)) throw BadRequestException("谈判应在形成估价建议及决策之后、订立协议之前")
        }
        if (source == EnumSource.DONATE) {
            val terms=completion.donation ?: throw BadRequestException("捐赠协议须逐项核实法定条款")
            if (listOf(terms.itemDetails,terms.lawfulSourceAndClearTitle,terms.handoverAcceptance,terms.ownershipTransfer,terms.rightsAndDuties,terms.specialCircumstances,terms.disputeResolution).any(String::isBlank)) throw BadRequestException("捐赠协议须包含详细信息、来源及权利保证、交接验收、所有权转移、双方权利义务、特殊情形及争议解决")
        }
    }
}
