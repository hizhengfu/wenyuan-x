package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

enum class EnumCollectionTransferKind { STATE_EXCHANGE, STATE_ALLOCATION, OTHER_LAWFUL, INSTITUTION_SUCCESSION }

/** 固定在审批前的跨馆方案，行政原件另行逐件追加。 */
data class CollectionTransferDossierVo(
    val kind: EnumCollectionTransferKind,
    val sourceKeeperUserId: String,
    val sourceLeaderUserId: String,
    val targetKeeperUserId: String,
    val targetLeaderUserId: String,
    val targetWarehouseId: String,
    val archiveHandoverFileIds: String,
    val sourcePaperLedgerRef: String,
    val targetPaperLedgerRef: String,
    val exchangeTransferIds: Set<String> = emptySet(),
    val exchangeAgreementFileIds: String? = null,
    val donationAgreementFileIds: String? = null,
    val donationTransferClause: String? = null,
    val noDonationAgreement: Boolean? = null,
    val donorNoticeFileIds: String? = null,
    val donorWrittenConsentFileIds: String? = null,
    val provincialJurisdictionFileIds: String? = null,
    val legalBasisFileIds: String? = null,
    val succession: CollectionInstitutionSuccessionVo? = null,
    val transportStandardReference: String? = null,
    val transportStandardApplicability: String? = null,
    val transportCarrierName: String? = null,
    val transportCarrierCapabilityFileIds: String? = null,
    val transportPlanFileIds: String? = null,
)

data class CollectionTransferExecutionVo(
    val handedOn: LocalDate,
    val sourceCustodianUserId: String,
    val targetCustodianUserId: String,
    val signedHandoverFileIds: String,
    val conditionDescription: String,
    val conditionImageFileIds: String,
    val authenticityVerified: Boolean,
    val signedLedgerChangeFileIds: String,
    val receivingLedgerReceiptFileIds: String,
    val appliedTransportStandardReference: String? = null,
    val packingStandardConfirmed: Boolean? = null,
    val packingCheckFileIds: String? = null,
)

object CollectionTransferCompliancePolicy {
    fun requireScope(
        dossier:CollectionTransferDossierVo,
        sourceOwnership:EnumOwnership?,
        targetOwnership:EnumOwnership?,
        nature:EnumCollectionNature?,
        source:EnumSource,
        registered:Boolean,
        archived:Boolean,
    ) {
        if (sourceOwnership==null || targetOwnership==null || nature==null) throw BadRequestException("请核实双方收藏单位性质和藏品性质")
        if (!registered || !archived) throw BadRequestException("请先补齐本次跨馆转移所需的建账、建档手续；未建账建档的文物藏品不得交换")
        if (listOf(dossier.sourceKeeperUserId,dossier.sourceLeaderUserId,dossier.targetKeeperUserId,dossier.targetLeaderUserId,
                dossier.targetWarehouseId,dossier.archiveHandoverFileIds,dossier.sourcePaperLedgerRef,dossier.targetPaperLedgerRef).any(String::isBlank)) {
            throw BadRequestException("双方责任人、接收库位、档案交接资料及双方纸质账对应位置必填")
        }
        if (dossier.sourceKeeperUserId==dossier.sourceLeaderUserId || dossier.targetKeeperUserId==dossier.targetLeaderUserId) throw BadRequestException("每馆保管确认与主要负责人批准须由不同责任人办理")
        if (dossier.kind in setOf(EnumCollectionTransferKind.STATE_EXCHANGE,EnumCollectionTransferKind.STATE_ALLOCATION) && (sourceOwnership!=EnumOwnership.STATE || targetOwnership!=EnumOwnership.STATE || !CollectionNaturePolicy.usesRelicRules(nature))) {
            throw BadRequestException("国有文物藏品交换、调拨仅适用于国有收藏单位之间的文物藏品")
        }
        if (dossier.kind==EnumCollectionTransferKind.STATE_EXCHANGE && (dossier.exchangeTransferIds.size < 2 || dossier.exchangeAgreementFileIds.isNullOrBlank())) throw BadRequestException("交换应关联双方整组逐件申请（含本申请）并上传共同交换协议")
        if (dossier.kind==EnumCollectionTransferKind.OTHER_LAWFUL && dossier.legalBasisFileIds.isNullOrBlank()) throw BadRequestException("其他依法转移须提供明确的法律法规和处置依据")
        if (source==EnumSource.DONATE && sourceOwnership==EnumOwnership.STATE && dossier.kind!=EnumCollectionTransferKind.INSTITUTION_SUCCESSION) {
            if (dossier.noDonationAgreement==true) {
                if (dossier.donorNoticeFileIds.isNullOrBlank() || dossier.donorWrittenConsentFileIds.isNullOrBlank()) throw BadRequestException("无捐赠约定协议的，应事先向捐赠人说明并取得书面同意")
            } else if (dossier.noDonationAgreement!=false || dossier.donationAgreementFileIds.isNullOrBlank() || dossier.donationTransferClause.isNullOrBlank()) {
                throw BadRequestException("捐赠藏品须核对原捐赠协议的交换调拨约定并留存依据")
            }
        }
    }
    fun requireAdministrative(dossier:CollectionTransferDossierVo,sourceOwnership:EnumOwnership,level:EnumRelicLevel,records:List<CollectionAdministrativeEvidenceVo>) {
        records.forEach { DeregistrationCompliancePolicy.requireAdministrativeEvidence(it) }
        fun has(level:EnumAuthorityLevel,action:EnumAdministrativeAction)=records.any { it.level==level && it.action==action }
        when(dossier.kind) {
            EnumCollectionTransferKind.STATE_EXCHANGE -> if(!has(EnumAuthorityLevel.PROVINCE,EnumAdministrativeAction.APPROVAL) || !has(EnumAuthorityLevel.NATIONAL,EnumAdministrativeAction.FILING)) throw BadRequestException("国有文物藏品交换须经所在地省级文物行政部门批准并报国务院文物行政部门备案")
            EnumCollectionTransferKind.STATE_ALLOCATION -> {
                if(!has(EnumAuthorityLevel.NATIONAL,EnumAdministrativeAction.APPROVAL)) {
                    if(!has(EnumAuthorityLevel.PROVINCE,EnumAdministrativeAction.APPROVAL) || dossier.provincialJurisdictionFileIds.isNullOrBlank()) throw BadRequestException("须有国务院文物部门调拨决定，或省级部门调拨决定及双方属于其行政区域内主管国有馆的依据")
                }
            }
            EnumCollectionTransferKind.OTHER_LAWFUL, EnumCollectionTransferKind.INSTITUTION_SUCCESSION -> if(sourceOwnership==EnumOwnership.STATE && !has(EnumAuthorityLevel.COMPETENT,EnumAdministrativeAction.FILING)) throw BadRequestException("国有馆依法转移退出须保留逐级报文物行政部门备案的凭证")
        }
        if(sourceOwnership==EnumOwnership.STATE && level==EnumRelicLevel.FIRST && !has(EnumAuthorityLevel.NATIONAL,EnumAdministrativeAction.FILING)) throw BadRequestException("国有馆藏一级文物转移须报国务院文物行政部门备案")
    }
    fun requireExecution(vo:CollectionTransferExecutionVo,approvedOn:LocalDate,today:LocalDate=LocalDate.now()) {
        if(vo.handedOn.isBefore(approvedOn) || vo.handedOn.isAfter(today)) throw BadRequestException("实际交接应在审批之后，日期不得晚于今天")
        if(listOf(vo.sourceCustodianUserId,vo.targetCustodianUserId,vo.signedHandoverFileIds,vo.conditionDescription,vo.conditionImageFileIds,vo.signedLedgerChangeFileIds,vo.receivingLedgerReceiptFileIds).any(String::isBlank)) throw BadRequestException("双方点交人员、逐件状况及影像、签署交接单、变更表和接收馆总账凭据必填")
        if(vo.sourceCustodianUserId==vo.targetCustodianUserId || !vo.authenticityVerified) throw BadRequestException("应由双方不同保管人员核实真实性并签署交接")
        if(vo.appliedTransportStandardReference.isNullOrBlank() || vo.packingStandardConfirmed!=true || vo.packingCheckFileIds.isNullOrBlank())
            throw BadRequestException("实际交接须核对采用的包装运输标准、逐件包装及签署核验原件")
    }
}
