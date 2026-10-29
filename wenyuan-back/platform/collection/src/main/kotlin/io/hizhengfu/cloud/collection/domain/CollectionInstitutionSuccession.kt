package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

/** 第二十八条逐件承接依据；不表示整个法人清算或机构合并已完成。 */
enum class EnumInstitutionSuccession { TERMINATION, MERGER }

data class CollectionSuccessionDesignationVo(
    val level: EnumAuthorityLevel,
    val authorityCode: String,
    val authorityName: String,
    val documentNo: String,
    val issuedOn: LocalDate,
    val sourceMuseumName: String,
    val targetMuseumName: String,
    val collectionId: String,
    val totalRegNo: String,
    val fileIds: String,
)

data class CollectionInstitutionSuccessionVo(
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
    val inventoryFileIds: String,
    val ownership: EnumOwnership,
    val movable: Boolean,
    val propertyBasisFileIds: String,
    val publicBenefitPurpose: String,
    val noPrivateDistribution: Boolean,
    val thirdPartyRightsConclusion: String,
    val thirdPartyRightsFileIds: String,
    val thirdPartyRightsResolved: Boolean,
    val authorizedRepresentativeUserId: String,
    val representativeAuthorizationFileIds: String,
    val designation: CollectionSuccessionDesignationVo? = null,
)

object CollectionInstitutionSuccessionPolicy {
    private fun requireText(vararg value: String) {
        if (value.any(String::isBlank)) throw BadRequestException("请完整核实终止或合并依据、逐件权属、公益处置、第三方权利及依法授权人员原件")
    }

    fun requireProposal(
        dossier: CollectionTransferDossierVo,
        sourceName: String,
        targetName: String,
        targetOwnership: EnumOwnership?,
        nature: EnumCollectionNature,
        collectionId: String,
        totalRegNo: String,
        today: LocalDate = LocalDate.now(),
    ) {
        val value = dossier.succession
        if (dossier.kind != EnumCollectionTransferKind.INSTITUTION_SUCCESSION) {
            if (value != null) throw BadRequestException("终止、合并承接须使用对应转移类别，不能隐藏在普通调拨或其他转移中")
            return
        }
        val s = value ?: throw BadRequestException("须核实本次终止或合并及逐件藏品承接依据")
        requireText(s.sourceMuseumName, s.sourceRegistrationNo, s.eventDocumentNo, s.eventFileIds,
            s.nonprofitLegalBasis, s.legalBasisFileIds, s.liquidationOrMergerPlanFileIds, s.inventoryFileIds,
            s.propertyBasisFileIds, s.publicBenefitPurpose, s.thirdPartyRightsConclusion, s.thirdPartyRightsFileIds,
            s.authorizedRepresentativeUserId, s.representativeAuthorizationFileIds)
        if (s.sourceMuseumName != sourceName || s.authorizedRepresentativeUserId != dossier.sourceLeaderUserId) {
            throw BadRequestException("终止或合并的原单位和依法授权责任人须与本次源馆及确认人员一致")
        }
        if (s.decidedOn > today || s.effectiveOn < s.decidedOn) throw BadRequestException("请核对已经形成的终止或合并决定及实际生效安排")
        if (!s.noPrivateDistribution) throw BadRequestException("非营利组织藏品处置不得向出资人、设立人或会员分配剩余财产，请核实实际公益处置方案")
        if (!s.thirdPartyRightsResolved) throw BadRequestException("尚有未依法或依约处理的第三方权利事项，不能确认逐件承接方案")
        if (!s.movable) throw BadRequestException("本入口办理实际可移动藏品的跨馆交接；不可移动或尚未核清的对象须先依法确认适用处置程序")
        if (s.ownership == EnumOwnership.STATE && s.movable && CollectionNaturePolicy.usesRelicRules(nature)) {
            if (targetOwnership != EnumOwnership.STATE) throw BadRequestException("国家所有的可移动文物须由省级文物行政部门指定其他国有博物馆收藏")
            val d = s.designation ?: throw BadRequestException("缺少省级文物行政部门对本件及接收国有博物馆的指定决定")
            requireText(d.authorityCode, d.authorityName, d.documentNo, d.sourceMuseumName, d.targetMuseumName, d.collectionId, d.totalRegNo, d.fileIds)
            if (d.level != EnumAuthorityLevel.PROVINCE || d.issuedOn > today || d.sourceMuseumName != sourceName ||
                d.targetMuseumName != targetName || d.collectionId != collectionId || d.totalRegNo != totalRegNo) {
                throw BadRequestException("省级指定原件的部门层级、源馆、接收馆和原总登记号须逐件一致")
            }
        } else if (s.designation != null) {
            throw BadRequestException("请核对逐件权属、可移动性及文物性质，不得将第二十八条的国家所有可移动文物指定决定套用于其他对象")
        }
    }

    fun requireHandover(s: CollectionInstitutionSuccessionVo?, handedOn: LocalDate) {
        if (s != null && (handedOn < s.decidedOn || s.designation?.issuedOn?.let { handedOn < it } == true)) {
            throw BadRequestException("实际承接不能早于本次终止合并处置决定或省级指定决定")
        }
    }

    fun files(s: CollectionInstitutionSuccessionVo?): List<String> = s?.let {
        listOfNotNull(it.eventFileIds, it.legalBasisFileIds, it.liquidationOrMergerPlanFileIds, it.inventoryFileIds,
            it.propertyBasisFileIds, it.thirdPartyRightsFileIds, it.representativeAuthorizationFileIds, it.designation?.fileIds)
    }.orEmpty()
}
