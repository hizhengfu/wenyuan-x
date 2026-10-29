package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.stereotype.Component

/** 交接双方可按当前权限查阅同一申请，归入双方档案的共同原件不要求两馆兼任。 */
@Component
class CollectionTransferArchiveReadPolicy(
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
    private val authorization: CollectionFileReadAuthorization,
    private val disclosures: CollectionDisclosureService,
    private val mainFiles: CollectionMainFileReadPolicy? = null,
) : CollectionArchiveReadPolicy {
    override val businessTypes = setOf(CollectionConst.BIZ_TRANSFER)
    /** 只共享已封存逐件承接方案实际采用的机构决定，不授权全馆清册或后续办理材料。 */
    internal fun requireSuccessionBasisReadable(reference: CollectionArchiveFileReference): Boolean {
        if (reference.businessType != InstitutionSuccessionCaseEvidence.BIZ || reference.phase != "CASE") return false
        val case = InstitutionSuccessionCaseDao.findById(reference.businessId) ?: return false
        if (case.orgId != reference.orgId) return false
        val p = case.content
        val headerFiles = listOf(p.eventFileIds, p.legalBasisFileIds, p.liquidationOrMergerPlanFileIds, p.representativeAuthorizationFileIds)
            .flatMap { it.split(',') }.map(String::trim).filter(String::isNotBlank)
        if (headerFiles.none { value ->
            (FileDao.findById(value) ?: FileDao.find { Files.path eq value }.singleOrNull())?.id?.value == reference.fileId
        }) return false
        val candidates = CollectionTransferDao.find { CollectionTransfers.orgId eq case.orgId }.filter { transfer ->
            val d = transfer.dossier
            val s = d?.succession
            d?.kind == EnumCollectionTransferKind.INSTITUTION_SUCCESSION && s != null &&
                transfer.fromOrgId == case.orgId && transfer.toOrgId != case.orgId &&
                transfer.flowStatus in setOf(EnumFlowStatus.AUDIT, EnumFlowStatus.PASS) &&
                s.eventKind == p.eventKind && s.sourceMuseumName == p.sourceMuseumName &&
                s.sourceRegistrationNo == p.sourceRegistrationNo && s.eventDocumentNo == p.eventDocumentNo &&
                s.decidedOn == p.decidedOn && s.effectiveOn == p.effectiveOn &&
                s.eventFileIds == p.eventFileIds && s.nonprofitLegalBasis == p.nonprofitLegalBasis &&
                s.legalBasisFileIds == p.legalBasisFileIds && s.liquidationOrMergerPlanFileIds == p.liquidationOrMergerPlanFileIds &&
                s.authorizedRepresentativeUserId == p.authorizedRepresentativeUserId &&
                s.representativeAuthorizationFileIds == p.representativeAuthorizationFileIds
        }
        if (candidates.isEmpty()) return false
        val evidence = InstitutionSuccessionCaseEvidence
        evidence.exact(case.orgId, case.id.value, "CASE", mapOf("orgId" to case.orgId, "sourceKey" to case.sourceKey, "content" to p))
        for (transfer in candidates) {
            val proposal = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and
                    (CollectionPermanentArchives.bizId eq transfer.id.value) and
                    (CollectionPermanentArchives.phase eq "PROPOSAL")
            }.singleOrNull() ?: continue
            if (proposal.orgId != case.orgId || proposal.digest != CollectionCareEvidence.digest(proposal.snapshot)) continue
            val frozen = evidence.node(proposal.snapshot)
            val identity = mapOf("id" to transfer.id.value, "collectionId" to transfer.collectionId,
                "fromOrgId" to transfer.fromOrgId, "toOrgId" to transfer.toOrgId, "dossier" to transfer.dossier)
            if (identity.any { (key, value) -> value == null || frozen.get(key) != evidence.node(evidence.json(value)) }) continue
            try {
                requireReadable(reference.copy(archiveId = proposal.id.value, businessType = CollectionConst.BIZ_TRANSFER,
                    businessId = transfer.id.value, phase = "PROPOSAL"))
                return true
            } catch (_: BadRequestException) {
                // 另一个已封存方案可能属于当前接收馆；每个候选均重新核对当前账号资格。
            }
        }
        return false
    }
    override fun requireReadable(reference: CollectionArchiveFileReference) {
        if (disclosures.isPublicNoticeArchiveFile(reference)) return
        val transfer = CollectionTransferDao.findById(reference.businessId) ?: throw BadRequestException("交换调拨原件所属申请不存在")
        val parties = setOfNotNull(transfer.orgId, transfer.toOrgId)
        if (reference.orgId !in parties) throw BadRequestException("交换调拨原件不属于申请双方")
        if (mainFiles?.requireReceivedOriginalReadable(reference) == true) return
        val user = authorization.currentReadActor() ?: directory.requireActive(SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后查阅交换调拨原件"))
        // 内容读取只共享本阶段输入，结束鲜读复验；未进入内容阶段时保留目录查询的边界。
        val visible = authorization.readableOrgIds(user, CollectionConst.CROSS_ORG)
        val org = if (visible == null) reference.orgId else parties.firstOrNull(visible::contains)
            ?: throw BadRequestException("当前账号无权查阅该交换调拨申请")
        authorization.requireReadable(setOf(org), setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.CROSS_ORG, CollectionConst.SELECT, CollectionConst.STORAGE, CollectionConst.LEDGER_CORRECT), CollectionConst.CROSS_ORG, "交换调拨原件")
    }
}
