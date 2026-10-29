package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.core.greater
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/** 退出法定证据独立于可配置的馆内流程；任何流程版本都必须满足这些条件。 */
@Component
@Transactional
class DeregistrationStatutoryService(
    private val scope: CollectionScopeResolver,
    private val binder: BusinessAttachmentBinder,
    private val archives: CollectionPermanentArchiveService,
    private val calendar: CollectionCalendarService,
    private val users: CollectionResponsibleUserService,
    private val disclosures: CollectionDisclosureService,
) {
    fun requireNoVeto(collectionId: String, today: LocalDate = LocalDate.now()) {
        DeregistrationVetoEvidence.requireNoVeto(collectionId,today,archives)
    }

    fun requireEditable(entity: DeregistrationDao) {
        if (entity.flowInstanceId != null || entity.executedAt != null || entity.vetoUntil != null ||
            experts(entity.id.value).isNotEmpty() || notices(entity.id.value).isNotEmpty() || administrative(entity.id.value).isNotEmpty()) {
            throw BadRequestException("已登记专家、公示、行政材料或开始审批的退出理由和处置方案永久保留，不能覆盖")
        }
        if (entity.flowStatus != EnumFlowStatus.DRAFT) throw BadRequestException("仅草稿可编辑")
    }

    fun normalizeDossier(vo: DeregistrationDossierVo?): DeregistrationDossierVo {
        val dossier=vo ?: throw BadRequestException("请补录新办法要求的退出责任人和处置方案")
        return dossier.copy(
            recipientStateEvidenceFileIds=files(dossier.recipientStateEvidenceFileIds),
            legalBasisFileIds=files(dossier.legalBasisFileIds),
            dispositionPlanFileIds=files(dossier.dispositionPlanFileIds),
        )
    }

    fun recordExpert(id: String, vo: DeregistrationExpertOpinionVo): String {
        val entity=scoped(id)
        requireCollecting(entity)
        if (notices(id).isNotEmpty() || administrative(id).isNotEmpty()) throw BadRequestException("公示或行政办理开始后不得追加专家意见；应保留完整的原评估和公示材料")
        val collection=requireBaseline(entity)
        if (entity.reason !in DeregistrationCompliancePolicy.evaluatedReasons) throw BadRequestException("此退出情形不适用五人专家评估程序")
        DeregistrationCompliancePolicy.requireExpert(vo, entity.orgId)
        val proposedOn=entity.dossier?.proposedOn ?: throw BadRequestException("请登记纸质退出方案的实际形成日期")
        if (vo.assessedAt.isBefore(proposedOn)) throw BadRequestException("专家评估不得早于退出方案形成日期")
        if (experts(id).any { it.expertKey == vo.expertKey.trim() || (it.expertName.trim() == vo.expertName.trim() && it.organization.trim() == vo.organization.trim()) }) {
            throw BadRequestException("该专家已提交个人意见，不得覆盖原意见或重复计数")
        }
        // 关联的系统人员若属于本馆，不能通过填写外部单位名称规避排除条件。
        vo.userId?.let { users.requireExternalToMuseum(it,entity.orgId) }
        val content=vo.copy(expertKey=vo.expertKey.trim(), seniorQualificationFileIds=requiredFiles(vo.seniorQualificationFileIds),
            independenceStatementFileIds=requiredFiles(vo.independenceStatementFileIds), signedOpinionFileIds=requiredFiles(vo.signedOpinionFileIds))
        sealProposal(entity)
        val row=DeregistrationExpertOpinionDao.new { orgId=entity.orgId; deregistrationId=id; expertKey=content.expertKey; this.content=content }
        archives.seal(entity.orgId, CollectionConst.BIZ_DEREGISTER, id, "EXPERT_${row.id.value}", content,
            listOf(content.seniorQualificationFileIds, content.independenceStatementFileIds, content.signedOpinionFileIds).joinToString(","))
        if (!content.agree) {
            entity.vetoUntil=content.assessedAt.plusYears(5)
            entity.flowStatus=EnumFlowStatus.REJECT
            archives.seal(entity.orgId, CollectionConst.BIZ_DEREGISTER, id, "VETO", DeregistrationVetoOriginal(
                collection.id.value,collection.totalRegNo,content,entity.vetoUntil!!,1,entity.orgId,id,row.id.value,content.assessedAt,
                DeregistrationLifecycleEvidence.reference(entity,archives,"PROPOSAL"),
                DeregistrationLifecycleEvidence.reference(entity,archives,"EXPERT_${row.id.value}"),
            ), content.signedOpinionFileIds)
        }
        return row.id.value
    }

    fun publicReviewOptions(id: String): List<Map<String, Any?>> {
        val entity = scoped(id)
        return disclosures.statutoryReviewOptions(entity.orgId, if (entity.executedAt == null) EnumDisclosureSubject.EXIT_CONSULTATION else EnumDisclosureSubject.EXIT_RESULT, id)
    }
    fun recordNotice(id: String, vo: DeregistrationNoticeVo): String {
        val subject = if (vo.kind == EnumExitNoticeKind.CONSULTATION) EnumDisclosureSubject.EXIT_CONSULTATION else EnumDisclosureSubject.EXIT_RESULT
        val entity=scoped(id)
        disclosures.lockStatutorySubject(subject, id)
        if (vo.startedOn.isAfter(LocalDate.now()) || vo.urlOrLocation.isBlank()) throw BadRequestException("请登记已实际发布的公示日期及网址或现场地点")
        if (vo.channel != EnumExitNoticeChannel.MUSEUM_ONSITE && !Regex("^https?://[^\\s/]+(?:/.*)?$").matches(vo.urlOrLocation.trim())) throw BadRequestException("官方网站公示须登记实际可查的网址")
        if (vo.kind == EnumExitNoticeKind.CONSULTATION) {
            requireCollecting(entity)
            if (administrative(id).isNotEmpty()) throw BadRequestException("已进入行政办理，原公示资料不得补换；请核对原行政办理依据")
            DeregistrationCompliancePolicy.requireUnanimous(experts(id))
            val latestAssessment=experts(id).maxOf { it.assessedAt }
            if (vo.startedOn.isBefore(latestAssessment)) throw BadRequestException("公示应在专家一致同意后进行")
            vo.endedOn?.let { if (it.isBefore(vo.startedOn) || !it.isBefore(LocalDate.now())) throw BadRequestException("公示结束日期须已完整经过且不早于开始日期") }
        } else {
            val execution=entity.executedAt ?: throw BadRequestException("退出实际执行后才能登记账物处置结果公告")
            if (vo.channel == EnumExitNoticeChannel.AUTHORITY_WEBSITE) throw BadRequestException("退出结果公告请登记博物馆现场和官方网站")
            if (vo.startedOn.isBefore(execution.toLocalDate())) throw BadRequestException("结果公告不能早于退出执行日期")
        }
        val content=vo.copy(publicContentFileIds=requiredFiles(vo.publicContentFileIds),publicationEvidenceFileIds=requiredFiles(vo.publicationEvidenceFileIds))
        disclosures.requireStatutoryNotice(entity.orgId, subject, id, content)
        val previous=notices(id).firstOrNull { it.kind == vo.kind && it.disclosureReviewId == content.disclosureReviewId }
        if (previous != null && previous.publicContentFileIds.split(',').toSet() != content.publicContentFileIds.split(',').toSet()) {
            throw BadRequestException("同一轮各渠道应使用同一份已归档公示内容，包含藏品情况、全部专家意见和处置方案")
        }
        if (vo.kind == EnumExitNoticeKind.CONSULTATION) sealProposal(entity)
        val row=DeregistrationNoticeDao.new { orgId=entity.orgId; deregistrationId=id; this.content=content }
        archives.seal(entity.orgId, CollectionConst.BIZ_DEREGISTER,id,"NOTICE_${row.id.value}",content,
            listOf(content.publicContentFileIds,content.publicationEvidenceFileIds).joinToString(","))
        if (vo.kind == EnumExitNoticeKind.RESULT && entity.announcedAt == null) {
            val channels=notices(id).filter { it.kind == EnumExitNoticeKind.RESULT && it.disclosureReviewId == content.disclosureReviewId }.map { it.channel }.toSet()
            if (channels.containsAll(setOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE))) {
                entity.announcedAt=LocalDateTime.now()
                val closed=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(enrich(entity))) as tools.jackson.databind.node.ObjectNode
                closed.put("lifecycleVersion",1)
                closed.set("executionArchive",io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(DeregistrationLifecycleEvidence.reference(entity,archives,"EXECUTED"))))
                archives.seal(entity.orgId,CollectionConst.BIZ_DEREGISTER,id,"CLOSED",closed, allFiles(entity))
            }
        }
        return row.id.value
    }

    fun recordAdministrative(id: String, vo: CollectionAdministrativeEvidenceVo): String {
        val entity=scoped(id)
        disclosures.lockStatutorySubject(EnumDisclosureSubject.EXIT_CONSULTATION, id)
        requireCollecting(entity)
        DeregistrationCompliancePolicy.requireAdministrativeEvidence(vo)
        if (entity.reason in DeregistrationCompliancePolicy.evaluatedReasons && vo.action == EnumAdministrativeAction.APPROVAL) {
            val end=requirePublicity(entity)
            if (!vo.issuedAt.isAfter(end)) throw BadRequestException("退出批准应在三十个工作日公示完成后取得")
        }
        val content=vo.copy(fileIds=requiredFiles(vo.fileIds))
        sealProposal(entity)
        val row=CollectionAdministrativeRecordDao.new { orgId=entity.orgId; bizType=CollectionConst.BIZ_DEREGISTER; bizId=id; this.content=content }
        archives.seal(entity.orgId,CollectionConst.BIZ_DEREGISTER,id,"ADMIN_${row.id.value}",content,content.fileIds)
        return row.id.value
    }

    private fun requireProposal(entity: DeregistrationDao): Pair<CollectionDao,CollectionOrgDao> {
        requireNoVeto(entity.collectionId)
        val collection=requireBaseline(entity)
        DeregistrationCompliancePolicy.requireLifecycle(entity.reason,collection.lifeStatus)
        val dossier=entity.dossier ?: throw BadRequestException("旧退出记录缺少法定资料，请另建申请补齐新办法要求")
        val org=CollectionOrgDao.findById(entity.orgId) ?: throw BadRequestException("收藏单位不存在")
        if (org.ownership == null) throw BadRequestException("请先确认收藏单位的国有或非国有性质")
        DeregistrationCompliancePolicy.requireReason(entity.reason,collection.nature,dossier.legalBasisFileIds)
        DeregistrationCompliancePolicy.requireDestination(entity.reason!!,dossier.destinationType,dossier.recipient)
        if (dossier.destinationType == EnumExitDestination.AUXILIARY_ACCOUNT && (dossier.auxiliaryRecordNo.isNullOrBlank() || dossier.auxiliaryLocation.isNullOrBlank())) throw BadRequestException("纳入辅助账册须明确辅助账编号及保管位置")
        if (dossier.proposedOn == null || dossier.proposedOn.isAfter(LocalDate.now())) throw BadRequestException("退出方案实际形成日期必填且不得晚于今天")
        if (entity.detail.isNullOrBlank() || dossier.dispositionPlanFileIds.isNullOrBlank()) throw BadRequestException("逐件退出理由和处置方案原件必填")
        if (dossier.destinationType in setOf(EnumExitDestination.STATE_MUSEUM,EnumExitDestination.STATE_TEACHING_RESEARCH) && dossier.recipientStateEvidenceFileIds.isNullOrBlank()) {
            throw BadRequestException("请上传接收单位国有性质的依据")
        }
        CollectionChangePolicy.requireActors(entity.createBy ?: throw BadRequestException("退出申请缺少经办人"),dossier.keeperUserId,dossier.leaderUserId)
        users.requireRole(dossier.keeperUserId!!,entity.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(dossier.leaderUserId!!,entity.orgId,CollectionConst.ROLE_DIRECTOR)
        if (org.ownership != EnumOwnership.STATE && dossier.legalBasisFileIds.isNullOrBlank()) throw BadRequestException("非国有馆退出须提供适用的法律法规及处置依据")
        if (entity.reason == EnumDeregReason.STATE_EXCHANGE_TRANSFER) {
            throw BadRequestException("国有馆间交换、调拨请通过跨馆调拨流程办理，原馆总账随该流程保留，不得退出馆藏接收馆藏品")
        }
        return collection to org
    }

    fun requirePrepared(entity: DeregistrationDao) {
        val (collection,org)=requireProposal(entity)
        if (org.ownership == EnumOwnership.STATE) {
            if (entity.reason in DeregistrationCompliancePolicy.evaluatedReasons) {
                DeregistrationCompliancePolicy.requireUnanimous(experts(entity.id.value))
                requirePublicity(entity)
            }
            DeregistrationCompliancePolicy.requireClearance(entity.reason!!,collection.level,administrative(entity.id.value))
        }
    }

    fun confirmApproval(entity: DeregistrationDao, instanceId: String) {
        DeregistrationReadEvidence.verify(entity,archives)
        requirePrepared(entity)
        val required=currentApprovals(entity,instanceId)
        val phase="APPROVED_"+required.last().taskId!!.takeLast(40)
        val prior=archives.listForAuthorizedOrg(CollectionConst.BIZ_DEREGISTER,entity.id.value,entity.orgId).filter {(it["phase"] as String).startsWith("APPROVED_") && it["phase"]!=phase}
            .map {mapOf("id" to it["id"],"phase" to it["phase"],"digest" to it["digest"])}
        val submission=archives.listForAuthorizedOrg(CollectionConst.BIZ_DEREGISTER,entity.id.value,entity.orgId).singleOrNull {it["phase"]=="SUBMITTED"}
        archives.seal(entity.orgId,CollectionConst.BIZ_DEREGISTER,entity.id.value,phase,mapOf(
            "request" to enrich(entity),"approvals" to required.map { it.toVo() },"lifecycleVersion" to 1,
            "proposalArchive" to DeregistrationLifecycleEvidence.reference(entity,archives,"PROPOSAL"),"priorApprovals" to prior,"priorApprovalCount" to prior.size,
            "submissionState" to if(submission==null)"NOT_RECORDED"else"RECORDED",
            "submissionArchive" to submission?.let {mapOf("id" to it["id"],"phase" to it["phase"],"digest" to it["digest"])},
        ),allFiles(entity))
        entity.approvedBy=entity.dossier!!.leaderUserId
    }

    /** 执行必须匹配最后一次重新激活后的真实批准，不能使用前一轮的归档记录。 */
    fun requireCurrentApprovalEvidence(entity: DeregistrationDao): LocalDateTime {
        DeregistrationReadEvidence.verify(entity,archives)
        val instanceId=entity.flowInstanceId ?: throw BadRequestException("缺少退出审批实例")
        val required=currentApprovals(entity,instanceId)
        val phase="APPROVED_" + required.last().taskId!!.takeLast(40)
        if (archives.list(CollectionConst.BIZ_DEREGISTER,entity.id.value).none { it["phase"] == phase }) throw BadRequestException("缺少本轮退出确认的归档意见")
        return required.last().createTime
    }

    private fun currentApprovals(entity: DeregistrationDao, instanceId: String): List<FlowInstanceLogDao> {
        val d=entity.dossier ?: throw BadRequestException("缺少退出处置方案")
        val logs=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instanceId)
        val required=listOf("keeper" to d.keeperUserId,"leader" to d.leaderUserId).map { (node,user) ->
            logs.lastOrNull { it.nodeKey == node && it.operator == user && !it.comment.isNullOrBlank() && it.taskId != null }
                ?: throw BadRequestException("退出须由保管负责人和主要负责人本人依次确认、批准，不得通过旧流程或跳节点代替")
        }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(required)
        return required
    }

    /** 三个渠道同时覆盖的完整工作日，取最新已完成记录；公示内容与实发凭据均归档。 */
    fun requirePublicity(entity: DeregistrationDao): LocalDate {
        val reviewId = disclosures.latestStatutoryReviewId(entity.orgId, EnumDisclosureSubject.EXIT_CONSULTATION, entity.id.value)
        val rows=notices(entity.id.value).filter { it.kind == EnumExitNoticeKind.CONSULTATION && it.endedOn != null && it.disclosureReviewId == reviewId }
        val selected=EnumExitNoticeChannel.entries.map { channel -> rows.lastOrNull { it.channel == channel }
            ?: throw BadRequestException("缺少${when(channel) { EnumExitNoticeChannel.AUTHORITY_WEBSITE -> "主管文物部门官网"; EnumExitNoticeChannel.MUSEUM_ONSITE -> "博物馆现场"; EnumExitNoticeChannel.MUSEUM_WEBSITE -> "博物馆官网" }}已完成公示的实发记录") }
        val start=selected.maxOf { it.startedOn }
        val end=selected.minOf { it.endedOn!! }
        val latestAssessment=experts(entity.id.value).maxOfOrNull { it.assessedAt } ?: throw BadRequestException("缺少专家评估")
        if (selected.any { it.startedOn.isBefore(latestAssessment) }) throw BadRequestException("公示须晚于全部专家评估完成")
        if (calendar.completedDays(entity.orgId,start,end) < 30) throw BadRequestException("主管文物部门官网、博物馆现场和官网的共同公示期须达到三十个完整工作日")
        return selected.maxOf { it.endedOn!! }
    }

    fun requireBaseline(entity: DeregistrationDao): CollectionDao {
        val collection=CollectionDao.findById(entity.collectionId) ?: throw BadRequestException("藏品不存在")
        if (collection.orgId != entity.orgId || entity.baseline == null || !CollectionCatalogMapper.matchesSnapshot(entity.baseline, collection.toModel())) {
            throw BadRequestException("藏品信息已变化或旧记录没有核实基线，请重新建立退出申请")
        }
        return collection
    }
    fun summary(entity: DeregistrationDao) = entity.toModel().copy(collectionName=entity.baseline?.formalName ?: entity.baseline?.totalName,currentNoticeComplete=if(entity.executedAt!=null)disclosures.currentResultNoticeComplete(entity.orgId,EnumDisclosureSubject.EXIT_RESULT,entity.id.value,notices(entity.id.value)) else false)
    fun enrich(entity: DeregistrationDao) = summary(entity).copy(experts=experts(entity.id.value),notices=notices(entity.id.value),administrativeEvidence=administrative(entity.id.value))
    fun experts(id: String) = DeregistrationExpertOpinionDao.find { DeregistrationExpertOpinions.deregistrationId eq id }.sortedBy { it.createTime }.map { it.content.copy(id=it.id.value) }
    fun notices(id: String) = DeregistrationNoticeDao.find { DeregistrationNotices.deregistrationId eq id }.sortedBy { it.createTime }.map { it.content.copy(id=it.id.value) }
    fun administrative(id: String) = CollectionAdministrativeRecordDao.find { (CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionAdministrativeRecords.bizId eq id) }
        .sortedBy { it.createTime }.map { it.content.copy(id=it.id.value) }
    fun allFiles(entity: DeregistrationDao): String = listOfNotNull(entity.attachmentIds,entity.dossier?.dispositionPlanFileIds,entity.dossier?.legalBasisFileIds,entity.dossier?.recipientStateEvidenceFileIds).joinToString(",")
    private fun sealProposal(entity: DeregistrationDao) {
        // 先补齐可固定的方案要素，再归档第一份正式材料，避免不完整方案被冻结。
        requireProposal(entity)
        val existing=archives.listForAuthorizedOrg(CollectionConst.BIZ_DEREGISTER,entity.id.value,entity.orgId).singleOrNull {it["phase"]=="PROPOSAL"}
        val printFields=if(existing==null)DeregistrationPrintEvidence.capture(entity)
            else DeregistrationPrintEvidence.fields(entity,io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(existing["snapshot"] as String))
        archives.seal(entity.orgId,CollectionConst.BIZ_DEREGISTER,entity.id.value,"PROPOSAL",mapOf(
            "collectionId" to entity.collectionId,"baseline" to entity.baseline,"reason" to entity.reason,
            "detail" to entity.detail,"dossier" to entity.dossier,"createBy" to entity.createBy,
        )+printFields,allFiles(entity))
    }
    private fun requireCollecting(entity: DeregistrationDao) {
        requireNoVeto(entity.collectionId)
        if (entity.flowStatus != EnumFlowStatus.DRAFT || entity.flowInstanceId != null || entity.executedAt != null) throw BadRequestException("只能为未提交的退出申请追加法定材料")
        requireBaseline(entity)
    }
    internal fun requireAccess(id:String,write:Boolean):DeregistrationDao {
        val orgId=Deregistrations.select(Deregistrations.orgId).where {Deregistrations.id eq id}.singleOrNull()?.get(Deregistrations.orgId) ?: throw BadRequestException("退出申请不存在")
        CollectionCurrentOperationAccess.require(scope,orgId,write,if(write)setOf(CollectionConst.DEREGISTER)else setOf(CollectionConst.SELECT,CollectionConst.DEREGISTER),"退出申请")
        return DeregistrationDao[id]
    }
    private fun scoped(id: String) = requireAccess(id,true).also { DeregistrationReadEvidence.verify(it,archives) }
    private fun requiredFiles(value: String): String = files(value) ?: throw BadRequestException("请上传真实原始凭据")
    private fun files(value: String?): String? = BusinessAttachmentBindingPlanner.plan("","",value).map { binder.resolveSingle(it.fileId)!! }.distinct().joinToString(",").takeIf(String::isNotBlank)
}
