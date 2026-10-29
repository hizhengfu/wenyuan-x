package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class AcquisitionComplianceService(private val scope: CollectionScopeResolver,private val binder: BusinessAttachmentBinder,private val archives: CollectionPermanentArchiveService,
    private val originalFiles: AcquisitionOriginalFiles? = null,
    private val businessOriginalFiles: CollectionBusinessOriginalFiles? = null) {
    fun rememberOriginals(id: String) { originalFiles?.remember(id) }
    internal fun rememberExistingOriginals(id: String) { originalFiles?.rememberExisting(id) }
    internal fun bindOriginals(entity: AcquisitionDao,files: String?) {
        BusinessAttachmentBindingPlanner.plan(entity.id.value,"征集鉴定审批材料",files).forEach { item ->
            if(businessOriginalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_ACQUISITION,entity.id.value,entity.orgId,item.fileId,item.aliasName)!=true)
                binder.bindSingle(entity.id.value,item.aliasName,item.fileId)
        }
    }
    fun case(id: String) = AcquisitionComplianceCaseDao.find { AcquisitionComplianceCases.acquisitionId eq id }.singleOrNull()
    fun experts(id: String) = AcquisitionExpertOpinionDao.find { AcquisitionExpertOpinions.acquisitionId eq id }.sortedBy { it.createTime }.map { it.content.copy(id=it.id.value) }

    fun requireEditable(entity: AcquisitionDao) {
        if(entity.designatedReceptionId!=null || entity.source==EnumSource.DESIGNATED)throw BadRequestException("指定接收对象须通过原指定接收业务核验，不能改为普通征集")
        val case=case(entity.id.value)
        if (case?.vetoOn != null || case?.completion != null || experts(entity.id.value).isNotEmpty()) throw BadRequestException("已记录独立评估或协议履行的征集对象与来源资料不能覆盖；原件永久保留")
    }
    fun saveDossier(id:String,vo:AcquisitionDossierVo):String {
        val entity=collecting(id)
        requireEditable(entity)
        AcquisitionCompliancePolicy.requireDossier(vo,entity.source)
        rememberExistingOriginals(id)
        val content=vo.copy(standardsFileIds=files(vo.standardsFileIds),authenticityValueFileIds=files(vo.authenticityValueFileIds),sourceInvestigationFileIds=files(vo.sourceInvestigationFileIds),conflictDisclosureFileIds=files(vo.conflictDisclosureFileIds),intentionFileIds=vo.intentionFileIds?.let(::files))
        val row=case(id) ?: AcquisitionComplianceCaseDao.new { orgId=entity.orgId;acquisitionId=id;subject=subject(entity);dossier=content }
        row.subject=subject(entity);row.dossier=content
        rememberOriginals(id)
        return id
    }
    fun recordExpert(id:String,vo:AcquisitionExpertOpinionVo):String {
        val entity=collecting(id);val case=requireCase(entity)
        if (case.completion != null) throw BadRequestException("已完成估价及协议登记，不能追加或替换原专家意见")
        AcquisitionCompliancePolicy.requireExpert(vo,case.dossier,entity.source)
        if (vo.expertKey.trim().length>100) throw BadRequestException("专家编号不得超过100字")
        if (experts(id).any { it.expertKey==vo.expertKey.trim() || (it.expertName.trim()==vo.expertName.trim() && it.organization.trim()==vo.organization.trim()) }) throw BadRequestException("同一专家只计一次，不得覆盖原独立意见")
        val content=vo.copy(expertKey=vo.expertKey.trim(),qualificationFileIds=files(vo.qualificationFileIds),independenceFileIds=files(vo.independenceFileIds),signedOpinionFileIds=files(vo.signedOpinionFileIds))
        rememberExistingOriginals(id)
        sealProposal(entity,case)
        val row=AcquisitionExpertOpinionDao.new { orgId=entity.orgId;acquisitionId=id;expertKey=content.expertKey;this.content=content }
        archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,id,"EXPERT_${row.id.value}",content,listOf(content.qualificationFileIds,content.independenceFileIds,content.signedOpinionFileIds).joinToString(","))
        if (!content.agree) {
            case.vetoOn=content.assessedOn;entity.flowStatus=EnumFlowStatus.REJECT
            archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,id,"VETO",mapOf("subject" to case.subject,"expert" to content),content.signedOpinionFileIds)
        }
        rememberOriginals(id)
        return row.id.value
    }
    fun complete(id:String,vo:AcquisitionCompletionVo):String {
        val entity=collecting(id);val case=requireCase(entity)
        if (case.completion != null) throw BadRequestException("已归档协议及履行材料不能覆盖")
        AcquisitionCompliancePolicy.requireCompletion(entity.source,case.dossier,experts(id),vo)
        val content=vo.copy(collectiveDecisionFileIds=files(vo.collectiveDecisionFileIds),agreementFileIds=files(vo.agreementFileIds),handoverReceiptFileIds=files(vo.handoverReceiptFileIds),comparablePriceFileIds=vo.comparablePriceFileIds?.let(::files),highValueDeterminationFileIds=vo.highValueDeterminationFileIds?.let(::files),assetAssessmentFileIds=vo.assetAssessmentFileIds?.let(::files),negotiationFileIds=vo.negotiationFileIds?.let(::files),paymentFileIds=vo.paymentFileIds?.let(::files))
        rememberExistingOriginals(id)
        sealProposal(entity,case)
        case.completion=content
        entity.acquireDate=content.actualAcquiredOn
        entity.contractAttachmentIds=content.agreementFileIds
        entity.approvalAttachmentIds=content.collectiveDecisionFileIds
        if (entity.source==EnumSource.PURCHASE) {
            entity.proposedPrice=content.negotiatedPriceCents;entity.appraisedValue=content.suggestedPriceCents;entity.invoiceAttachmentIds=content.paymentFileIds
        }
        entity.expertOpinionAttachmentIds=experts(id).joinToString(",") { it.signedOpinionFileIds }.ifBlank { case.dossier.authenticityValueFileIds }
        archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,id,"AGREEMENT",mapOf("completion" to content,"expertAveragePriceCents" to if(entity.source==EnumSource.PURCHASE) AcquisitionCompliancePolicy.averagePrice(experts(id)) else null),completionFiles(content))
        rememberOriginals(id)
        return id
    }
    fun completedSource(entity:AcquisitionDao):AcquisitionPreparedSource? {
        if(entity.designatedReceptionId!=null || entity.source==EnumSource.DESIGNATED)return DesignatedReceptionEvidence.prepared(entity)
        val c=case(entity.id.value) ?: return null
        return c.completion?.let {AcquisitionPreparedSource(c.dossier.nature,it.actualAcquiredOn)}
    }
    internal fun sourceFileIds(entity: AcquisitionDao): Set<String> {
        val case = case(entity.id.value)
        val dossier = case?.dossier
        val originals = listOf(entity.images, entity.expertOpinionAttachmentIds, entity.approvalAttachmentIds,
            entity.contractAttachmentIds, entity.invoiceAttachmentIds, dossier?.standardsFileIds,
            dossier?.authenticityValueFileIds, dossier?.sourceInvestigationFileIds, dossier?.conflictDisclosureFileIds,
            dossier?.intentionFileIds, case?.completion?.let(::completionFiles)) + experts(entity.id.value).flatMap {
            listOf(it.qualificationFileIds, it.independenceFileIds, it.signedOpinionFileIds)
        }
        return originals.flatMap(::splitCollectionIds).toSet()
    }
    fun requirePrepared(entity:AcquisitionDao):AcquisitionPreparedSource {
        if(entity.designatedReceptionId!=null || entity.source==EnumSource.DESIGNATED)return DesignatedReceptionEvidence.prepared(entity)
        val case=requireCase(entity)
        AcquisitionCompliancePolicy.requireCompletion(entity.source,case.dossier,experts(entity.id.value),case.completion ?: throw BadRequestException("请补齐估价、集体决策、协议与实际取得资料"))
        return AcquisitionPreparedSource(case.dossier.nature,case.completion!!.actualAcquiredOn)
    }
    fun sealPhase(entity:AcquisitionDao,phase:String) {
        requirePrepared(entity)
        if(entity.designatedReceptionId!=null) {
            if(phase!="ENTERED")throw BadRequestException("行政指定接收不生成普通征集的审批完成事实")
            archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,entity.id.value,"ENTERED",
                mapOf("designatedReceptionId" to entity.designatedReceptionId,"source" to DesignatedReceptionEvidence.acquisitionValue(entity),"collectionId" to entity.collectionId),null)
            return
        }
        val case=requireCase(entity)
        if (phase == "ENTERED") {
            // 登记员沿用已签署取得依据的冻结身份，不读取或再分发估价、谈判及协议附件。
            val agreement = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_ACQUISITION) and
                    (CollectionPermanentArchives.bizId eq entity.id.value) and (CollectionPermanentArchives.phase eq "AGREEMENT")
            }.singleOrNull() ?: throw BadRequestException("征集实际取得依据尚未永久归档，请先核对原征集手续")
            val completion = case.completion!!
            archives.requireExistingMetadata(agreement, entity.orgId, CollectionConst.BIZ_ACQUISITION, entity.id.value, "AGREEMENT",
                mapOf("completion" to completion, "expertAveragePriceCents" to if(entity.source==EnumSource.PURCHASE) AcquisitionCompliancePolicy.averagePrice(experts(entity.id.value)) else null), completionFiles(completion))
            val receipt = AccessionReceiptDao.find { AccessionReceipts.collectionId eq entity.collectionId.orEmpty() }
                .maxByOrNull { it.version } ?: throw BadRequestException("缺少本次关联的逐件核收依据")
            if (receipt.orgId != entity.orgId || receipt.acquisitionId != entity.id.value || receipt.content.accepted != true)
                throw BadRequestException("逐件核收与本次征集登记关联不一致")
            archives.seal(entity.orgId, CollectionConst.BIZ_ACQUISITION, entity.id.value, phase,
                mapOf("collectionId" to entity.collectionId, "receiptId" to receipt.id.value,
                    "acquisitionId" to entity.id.value, "agreementArchiveId" to agreement.id.value, "agreementDigest" to agreement.digest), null)
            return
        }
        val archivePhase=if(phase=="APPROVED") "APPROVED_${entity.flowInstanceId}" else phase
        archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,entity.id.value,archivePhase,mapOf(
            "subject" to case.subject,"dossier" to case.dossier,"experts" to experts(entity.id.value),"completion" to case.completion,
            "instanceId" to entity.flowInstanceId.takeUnless { phase=="SUBMITTED" },
            "collectionId" to entity.collectionId.takeIf { phase=="ENTERED" },
        ),completionFiles(case.completion!!))
    }
    fun detail(id:String):Map<String,Any?> {
        val entity=AcquisitionDao.findById(id) ?: throw BadRequestException("征集申请不存在")
        requireConfidentialAccess(scope,entity.orgId)
        val case=case(id)
        val experts=experts(id)
        return mapOf("acquisition" to entity.toModel(),"dossier" to case?.dossier,"experts" to experts,"completion" to case?.completion,"vetoOn" to case?.vetoOn,
            "expertAveragePriceCents" to if(experts.isNotEmpty() && experts.all { it.proposedPriceCents != null }) AcquisitionCompliancePolicy.averagePrice(experts) else null,
            "entryDeadline" to case?.completion?.actualAcquiredOn?.plusMonths(3),"archives" to archives.readForDisplay(CollectionConst.BIZ_ACQUISITION,id,entity.orgId).archives)
    }
    /** 仅允许下载本征集案卷指定阶段的冻结原件，防止复用文件号时选错版本。 */
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        val entity=AcquisitionDao.findById(id) ?: throw BadRequestException("征集申请不存在")
        requireConfidentialAccess(scope,entity.orgId)
        if (!archiveId.matches(Regex("[0-9]{15,20}")) || !fileId.matches(Regex("[0-9]{15,20}")))
            throw BadRequestException("归档原件标识不正确")
        val archive=archives.listForAuthorizedOrg(CollectionConst.BIZ_ACQUISITION,id,entity.orgId)
            .singleOrNull { it["id"]==archiveId } ?: throw BadRequestException("该永久档案不属于本征集申请")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as List<Map<String,Any?>>
        if (files.none { it["fileId"]==fileId }) throw BadRequestException("该文件不是所选征集永久档案的原件")
    }
    private fun requireCase(entity:AcquisitionDao):AcquisitionComplianceCaseDao {
        val case=case(entity.id.value) ?: throw BadRequestException("请补录新办法要求的入藏标准、来源调查、鉴定和回避资料")
        if (case.vetoOn != null) throw BadRequestException("有专家不同意征集，本次程序已经终止，不得重新激活或继续执行")
        if (case.orgId != entity.orgId || case.subject != subject(entity)) throw BadRequestException("征集对象或调查基础发生变化，请重新核实调查资料")
        return case
    }
    private fun collecting(id:String):AcquisitionDao {
        val entity=AcquisitionDao.findById(id) ?: throw BadRequestException("征集申请不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),entity.orgId,"征集申请")
        if (entity.flowStatus != EnumFlowStatus.DRAFT || entity.flowInstanceId != null || entity.collectionId != null) throw BadRequestException("只能为尚未提交的征集申请登记原始材料")
        return entity
    }
    private fun subject(entity:AcquisitionDao)=JsonUtils.toJson(mapOf("orgId" to entity.orgId,"name" to entity.name,"categoryId" to entity.categoryId,"source" to entity.source,"quantity" to entity.quantity,"unit" to entity.unit,"era" to entity.era,"texture" to entity.texture,"holder" to entity.holder,"reason" to entity.reason,"dimensions" to entity.dimensions,"completeness" to entity.completeness,"completeStatus" to entity.completeStatus,"images" to entity.images))!!
    private fun sealProposal(entity:AcquisitionDao,case:AcquisitionComplianceCaseDao)=archives.seal(entity.orgId,CollectionConst.BIZ_ACQUISITION,entity.id.value,"PROPOSAL",mapOf("subject" to case.subject,"dossier" to case.dossier),listOfNotNull(case.dossier.standardsFileIds,case.dossier.authenticityValueFileIds,case.dossier.sourceInvestigationFileIds,case.dossier.conflictDisclosureFileIds,case.dossier.intentionFileIds).joinToString(","))
    private fun completionFiles(vo:AcquisitionCompletionVo)=listOfNotNull(vo.collectiveDecisionFileIds,vo.agreementFileIds,vo.handoverReceiptFileIds,vo.comparablePriceFileIds,vo.highValueDeterminationFileIds,vo.assetAssessmentFileIds,vo.negotiationFileIds,vo.paymentFileIds).joinToString(",")
    private fun files(value:String):String=BusinessAttachmentBindingPlanner.plan("","",value).map { binder.resolveSingle(it.fileId) ?: throw BadRequestException("原始资料文件不存在") }.distinct().joinToString(",").takeIf(String::isNotBlank) ?: throw BadRequestException("原始资料文件必填")
    companion object {
        fun canReadConfidential(scope:CollectionScopeResolver,orgId:String?=null):Boolean = AcquisitionConfidentialAccess.canRead(scope,orgId)
        fun requireConfidentialAccess(scope:CollectionScopeResolver,orgId:String?=null) { if(!canReadConfidential(scope,orgId)) throw BadRequestException("征集估价和谈判资料保密，仅向当前获授权征集人员提供") }
        fun visibleRecord(scope:CollectionScopeResolver,vo:AcquisitionVo)=if(canReadConfidential(scope,vo.orgId)) vo else vo.copy(proposedPrice=null,appraisedValue=null,images=null,reason=null,expertOpinionAttachmentIds=null,approvalAttachmentIds=null,contractAttachmentIds=null,invoiceAttachmentIds=null)
    }
}
