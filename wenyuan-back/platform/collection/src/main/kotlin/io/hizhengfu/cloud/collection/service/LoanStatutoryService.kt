package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class LoanStatutoryService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val binder: BusinessAttachmentBinder,
    private val archives: CollectionPermanentArchiveService,
    private val calendar: CollectionCalendarService,
    private val archivePackets: CollectionArchivePacketService,
) {
    fun editable(e: LoanDao) = e.flowStatus==EnumFlowStatus.DRAFT && e.flowInstanceId==null && !e.outExecuted && e.handover==null && records(e.id.value).isEmpty()
    fun requireEditable(e: LoanDao) { if(!editable(e)) throw BadRequestException("已经提交或登记行政材料的借用方案须保留原件；变更清单或协议请撤回后新建申请") }
    fun baseline(collections: List<CollectionDao>): LoanBaselineVo {
        val org=CollectionOrgDao.findById(collections.first().orgId) ?: throw BadRequestException("收藏单位不存在")
        return LoanBaselineVo(org.ownership ?: throw BadRequestException("请先核实出借馆举办主体"),collections.map { RequisitionCollectionBaselineVo(it.id.value,CollectionCatalogMapper.fromCollection(it.toModel())) })
    }
    fun requirePrepared(e: LoanDao,statuses: Set<EnumCollectionStatus> = setOf(EnumCollectionStatus.IN_STORE)): List<CollectionDao> {
        val d=e.dossier ?: throw BadRequestException("请补齐借用主体、逐件评估、集体研究、协议及运输监管方案")
        val baseline=e.baseline ?: throw BadRequestException("旧借展单未核实新规借用清单，请重新建立申请")
        val source=CollectionOrgDao.findById(e.orgId) ?: throw BadRequestException("出借馆不存在")
        if(source.ownership!=baseline.sourceOwnership || source.orgStatus!=EnumOrgStatus.ENABLED) throw BadRequestException("出借馆举办主体已变化或已停用，请重新核实方案")
        val ids=splitCollectionIds(e.collectionIds)
        if(ids.isEmpty() || ids.toSet()!=baseline.items.map { it.collectionId }.toSet() || ids.size!=baseline.items.size) throw BadRequestException("借用藏品清单与核实基线不一致")
        val collections=ids.map { id ->
            val c=CollectionDao.findById(id) ?: throw BadRequestException("借用藏品不存在：$id")
            if(c.orgId!=e.orgId || c.lifeStatus !in statuses || !CollectionCatalogMapper.matchesSnapshot(baseline.items.single { it.collectionId==id }.content, c.toModel())) throw BadRequestException("藏品所属馆、登记资料或在库状态已变化，请重新核实申请")
            if(!c.registered || c.totalRegNo.isBlank() || c.catalogStatus!=EnumCatalogStatus.APPROVED || CollectionCatalogVersionDao.find { (CollectionCatalogVersions.collectionId eq id) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.empty()) throw BadRequestException("未完成建账、建档手续的藏品不得出借：${c.totalRegNo}")
            archivePackets.requireEstablished(c)
            c
        }
        if(listOf(e.title,e.borrower,e.protocolNo,e.operator).any { it.isNullOrBlank() }) throw BadRequestException("借用事由、单位全称、协议号及经办人员必填")
        if(listOf(d.borrowerRegistrationNo,d.borrowerIdentityFileIds,d.purposeDescription,d.keeperUserId,d.leaderUserId,d.supervisionUserId,d.safetyResponsibilities,d.transportRequirements,d.storageRequirements,d.useSite,d.carrierName,d.carrierCapabilityFileIds,d.transportSupervisorName,d.transportSupervisorOrganization,d.transportAssignmentFileIds,d.inspectionPlanFileIds).any(String::isBlank)) throw BadRequestException("借用主体、责任人、使用安全、运输保管及监督检查安排必填")
        if(!e.outExecuted && listOf(d.transportStandardReference,d.transportStandardApplicability,d.transportPlanFileIds).any { it.isNullOrBlank() })
            throw BadRequestException("出借前须明确适用的国家或行业包装运输标准及版本、适用依据和签署方案原件")
        if(e.borrower?.trim()==source.name.trim()) throw BadRequestException("本馆内部提用应办理馆内提用，不能以本馆作为借用单位")
        if(!d.agreedReturnAt.isAfter(d.agreedOutAt) || d.agreedReturnAt.isAfter(d.agreedOutAt.plusYears(3))) throw BadRequestException("借用协议须明确出借和归还时间，约定期限不得超过三年")
        if(e.loanDate!=d.agreedOutAt.toLocalDate() || e.returnDate!=d.agreedReturnAt.toLocalDate()) throw BadRequestException("借出及应还日期须与签署协议一致")
        if(d.agreementSignedAt.isBefore(d.collectiveDecisionAt) || d.agreementSignedAt.isAfter(d.agreedOutAt) || d.agreementSignedAt.isAfter(LocalDateTime.now())) throw BadRequestException("须先经集体研究同意再签订借用协议，签订时间不得晚于约定出借时间或当前时间")
        if(d.collectiveParticipantNames.map(String::trim).filter(String::isNotBlank).distinct().size<2) throw BadRequestException("集体研究须记录实际参会人员和同意借用的会议原件，不能以个人审批代替")
        if(d.keeperUserId==d.leaderUserId) throw BadRequestException("保管确认和主要负责人批准须由不同实际责任人办理")
        users.requireRole(d.keeperUserId,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(d.leaderUserId,e.orgId,CollectionConst.ROLE_DIRECTOR)
        users.requireRole(d.supervisionUserId,e.orgId,CollectionConst.ROLE_KEEPER)
        if(d.firstInspectionOn.isBefore(d.agreedOutAt.toLocalDate()) || d.firstInspectionOn.isAfter(d.agreedReturnAt.toLocalDate()) || d.inspectionIntervalDays !in 1..3660) throw BadRequestException("须在借用期内安排首次检查，并明确馆方确定的后续检查周期")
        if(d.assessments.size!=ids.size || d.assessments.map { it.collectionId }.toSet()!=ids.toSet()) throw BadRequestException("保存现状、必要性和风险应覆盖每件拟出借藏品")
        d.assessments.forEach { a ->
            val c=collections.single { it.id.value==a.collectionId }
            CollectionNaturePolicy.requireLoanKind(c.nature,a.objectKind)
            if(a.poorCondition || a.transportDamageRisk || c.preserveState in setOf(EnumPreserve.B,EnumPreserve.C)) throw BadRequestException("保存状况不佳或易因运输包装受损的藏品不得出借：${c.totalRegNo}")
            if(listOf(a.condition,a.necessity,a.riskAssessment,a.assessmentFileIds).any(String::isBlank)) throw BadRequestException("逐件现状、借用必要性、风险结论及评估原件必填")
            requiredFiles(a.assessmentFileIds)
        }
        if(d.purpose==EnumLoanPurpose.OTHER_LAWFUL) requiredFiles(d.otherLegalBasisFileIds)
        if(relicManaged(e) && d.borrowerKind in setOf(EnumLoanBorrowerKind.NON_STATE_MUSEUM,EnumLoanBorrowerKind.OTHER_ORGANIZATION,EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION) && (baseline.sourceOwnership==EnumOwnership.STATE || d.borrowerKind==EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION) && d.purpose!=EnumLoanPurpose.EXHIBITION) throw BadRequestException("非国有馆、其他单位借用国有文物及境外机构借用文物应核实展览用途；本单不支持以普通事由绕过法定程序")
        if(d.purpose==EnumLoanPurpose.EXHIBITION && d.exhibitionOverseas==null)throw BadRequestException("展览借用须明确实物是否出境，不能仅依据借用单位国别推断")
        if(relicManaged(e) && d.borrowerKind==EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION) {
            requiredFiles(d.overseasProcedureFileIds)
            if(d.overseasRequiredApprovalLevels.isEmpty())throw BadRequestException("境外机构借用须依据适用法律程序明确行政审批机关层级")
        }
        if(d.exhibitionOverseas==true && relicManaged(e))requireOverseasExhibition(e)
        allFiles(e).split(',').filter(String::isNotBlank).forEach { requiredFiles(it) }
        requiredFiles(d.collectiveDecisionFileIds);requiredFiles(d.signedAgreementFileIds);requiredFiles(d.agreedCollectionListFileIds)
        return collections
    }
    private fun relicManaged(e: LoanDao)=e.dossier!!.assessments.any { it.objectKind!=EnumLoanObjectKind.OTHER }
    private fun firstLevel(e: LoanDao)=e.baseline!!.items.any { item -> item.content.level==EnumRelicLevel.FIRST && e.dossier!!.assessments.any { it.collectionId==item.collectionId && it.objectKind!=EnumLoanObjectKind.OTHER } }
    fun filingLevels(e: LoanDao): List<EnumAuthorityLevel> = if(e.dossier!=null && e.baseline?.sourceOwnership==EnumOwnership.STATE && e.dossier!!.borrowerKind==EnumLoanBorrowerKind.STATE_MUSEUM && relicManaged(e)) listOf(EnumAuthorityLevel.COMPETENT)+if(firstLevel(e))listOf(EnumAuthorityLevel.NATIONAL)else emptyList() else emptyList()
    private fun overseasApprovalLevel(e: LoanDao): EnumAuthorityLevel {
        val p=e.dossier!!.overseasExhibition ?: throw BadRequestException("缺少完整出境展览方案")
        return if(p.firstLevelExhibits>120 || p.firstLevelExhibits.toLong()*5>p.totalRelicExhibits) EnumAuthorityLevel.STATE_COUNCIL else EnumAuthorityLevel.NATIONAL
    }
    private fun requireOverseasExhibition(e: LoanDao) {
        val d=e.dossier!!
        if(d.purpose!=EnumLoanPurpose.EXHIBITION)throw BadRequestException("本业务实物出境仅支持依法批准的出境展览")
        val p=d.overseasExhibition ?: throw BadRequestException("实物出境展览须核实全展数量、时间、禁出目录及国内展出记录")
        if(p.exhibitionEndsOn.isBefore(p.exhibitionStartsOn) || p.exhibitionEndsOn.isAfter(p.exhibitionStartsOn.plusYears(1)) || p.exhibitionStartsOn.isBefore(d.agreedOutAt.toLocalDate()) || p.exhibitionEndsOn.isAfter(d.agreedReturnAt.toLocalDate()))throw BadRequestException("出境展览初次批准展期不得超过一年，并须处于借用协议期限内；延期应另行取得原审批机关批准")
        val draftOn=p.draftAgreementSignedOn ?: throw BadRequestException("出境展览须保留用于报批的草签协议日期和草案原件")
        if(draftOn.isAfter(d.agreementSignedAt.toLocalDate()))throw BadRequestException("草签协议日期不得晚于正式协议签订日期")
        requiredFiles(p.draftAgreementFileIds)
        val items=e.baseline!!.items
        val firstCount=items.count { it.content.level==EnumRelicLevel.FIRST }
        if(p.totalRelicExhibits<items.size || p.firstLevelExhibits<firstCount || p.firstLevelExhibits>p.totalRelicExhibits)throw BadRequestException("全展文物总数和一级文物数量不能少于本单实际清单，应包含所有参展馆的展品")
        if(p.objects.size!=items.size || p.objects.map { it.collectionId }.toSet()!=items.map { it.collectionId }.toSet())throw BadRequestException("出境限制核查须覆盖每件藏品")
        p.objects.forEach { item ->
            if(item.prohibitedFromExit || (items.single { it.collectionId==item.collectionId }.content.level==EnumRelicLevel.FIRST && item.uniqueOrFragileFirstLevel))throw BadRequestException("禁止出境目录中的文物及一级文物孤品、易损品不得出境展览")
            requiredFiles(item.domesticExhibitionEvidenceFileIds)
        }
        requiredFiles(p.wholeExhibitionListFileIds);requiredFiles(p.prohibitedCatalogCheckFileIds)
    }
    private fun approvalLevels(e: LoanDao): List<EnumAuthorityLevel> {
        if(!relicManaged(e))return emptyList()
        val d=e.dossier!!
        val required=linkedSetOf<EnumAuthorityLevel>()
        // 境外机构同样属于借用国有馆藏文物的其他单位；表单指定的适用层级不能低于法定下限。
        if(e.baseline!!.sourceOwnership==EnumOwnership.STATE && d.borrowerKind in setOf(EnumLoanBorrowerKind.NON_STATE_MUSEUM,EnumLoanBorrowerKind.OTHER_ORGANIZATION))
            required.add(if(firstLevel(e))EnumAuthorityLevel.NATIONAL else EnumAuthorityLevel.COMPETENT)
        if(d.borrowerKind==EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION) {
            required.addAll(d.overseasRequiredApprovalLevels)
            if(e.baseline!!.sourceOwnership==EnumOwnership.STATE) {
                if(firstLevel(e))required.add(EnumAuthorityLevel.NATIONAL)
                else if(required.none { it in setOf(EnumAuthorityLevel.COMPETENT,EnumAuthorityLevel.NATIONAL,EnumAuthorityLevel.STATE_COUNCIL) })required.add(EnumAuthorityLevel.COMPETENT)
            }
        }
        if(d.exhibitionOverseas==true)required.add(overseasApprovalLevel(e))
        return required.toList()
    }
    fun requireAdministrativeBeforeOut(e: LoanDao,outAt: LocalDateTime=e.dossier!!.agreedOutAt) {
        val evidence=administrative(e.id.value).map { it.second }
        val domesticAdvance=e.baseline?.sourceOwnership==EnumOwnership.STATE && e.dossier!!.borrowerKind in setOf(EnumLoanBorrowerKind.NON_STATE_MUSEUM,EnumLoanBorrowerKind.OTHER_ORGANIZATION)
        approvalLevels(e).forEach { level ->
            val valid=evidence.any { r -> r.level==level && r.action==EnumAdministrativeAction.APPROVAL && !r.issuedOn.isAfter(outAt.toLocalDate()) &&
                (e.dossier!!.exhibitionOverseas!=true || !r.issuedOn.isAfter(e.dossier!!.agreementSignedAt.toLocalDate())) &&
                (!domesticAdvance || !calendar.deadline(e.orgId,r.submittedOn,20).isAfter(outAt.toLocalDate())) }
            if(!valid)throw BadRequestException(if(e.dossier!!.borrowerKind==EnumLoanBorrowerKind.OVERSEAS_ORGANIZATION) "境外机构借用须按文物级别取得法定批准，并保留适用程序依据" else "须按借用主体及文物级别取得相应行政批准，并保留出借前至少二十个工作日报送的凭据")
        }
        if(e.dossier!!.exhibitionOverseas==true && relicManaged(e)) {
            val p=e.dossier!!.overseasExhibition ?: throw BadRequestException("缺少出境展览方案")
            if(evidence.none { it.action==EnumAdministrativeAction.APPROVAL && it.level==overseasApprovalLevel(e) && !it.submittedOn.isAfter(p.exhibitionStartsOn.minusMonths(6)) && !it.issuedOn.isAfter(outAt.toLocalDate()) })throw BadRequestException("出境展览须提前六个月申请；一级文物超过120件套或全展20%时须报国务院批准")
        }
        // 先确认出借后的法定期限可计算，不能出库后才发现缺少跨年工作日历。
        if(filingLevels(e).isNotEmpty())calendar.deadline(e.orgId,outAt.toLocalDate(),30)
    }
    fun actors(e: LoanDao)=e.dossier?.let { linkedMapOf("keeper" to it.keeperUserId,"leader" to it.leaderUserId) } ?: throw BadRequestException("缺少借用责任人")
    fun requireActor(e: LoanDao,node: String?,operator: String?,comment: String?) {
        val expected=actors(e)[node] ?: return
        if(operator!=expected || comment.isNullOrBlank())throw BadRequestException("本单指定保管负责人和主要负责人须本人核对集体决策、协议及行政原件并填写意见")
        users.requireRole(expected,e.orgId,if(node=="keeper")CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR)
    }
    fun currentApprovals(e: LoanDao): List<FlowInstanceLogDao> {
        val instance=e.flowInstanceId ?: throw BadRequestException("缺少借用审批实例")
        val all=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instance)
        val approvals=actors(e).map { (node,user) -> all.lastOrNull { it.nodeKey==node && it.operator==user && !it.comment.isNullOrBlank() && it.taskId!=null } ?: throw BadRequestException("缺少本轮指定责任人实际依次确认，旧流程或跳过节点不能代替借用批准") }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(approvals)
        return approvals
    }
    fun confirmApproval(e: LoanDao) {
        requirePrepared(e);requireAdministrativeBeforeOut(e)
        val approvals=currentApprovals(e)
        archives.seal(e.orgId,CollectionConst.BIZ_LOAN,e.id.value,approvalPhase(approvals.last().taskId!!),mapOf("proposal" to (proposal(e)+mapOf("administrativeEvidence" to administrativeViews(e.id.value))),"approvals" to approvals.map { it.toVo() }),allFiles(e))
    }
    fun requireCurrentApproval(e: LoanDao): FlowInstanceLogDao {
        val last=currentApprovals(e).last()
        if(CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_LOAN) and (CollectionPermanentArchives.bizId eq e.id.value) and (CollectionPermanentArchives.phase eq approvalPhase(last.taskId!!)) }.empty())throw BadRequestException("缺少本轮借用批准档案")
        return last
    }
    fun recordAdministrative(id: String,vo: LoanAdministrativeVo): String {
        val e=locked(id)
        if(!e.outExecuted && e.flowStatus !in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.AUDIT,EnumFlowStatus.PASS))throw BadRequestException("已撤销的借用申请不能继续登记行政材料")
        if(vo.collectionIds!=splitCollectionIds(e.collectionIds).toSet())throw BadRequestException("行政文书应明确覆盖本单完整藏品清单")
        if(listOf(vo.authorityName,vo.documentNo,vo.submissionFileIds,vo.fileIds).any(String::isBlank) || vo.submittedOn.isAfter(vo.issuedOn) || vo.issuedOn.isAfter(LocalDate.now()))throw BadRequestException("请登记实际报送、批准或备案日期，以及行政机关、文号和原件")
        if(e.dossier==null || e.baseline==null)throw BadRequestException("旧单须先核实完整借用方案，行政文书不能代替缺失的清单和协议")
        val d=e.dossier!!
        if(d.exhibitionOverseas==true && relicManaged(e) && vo.action==EnumAdministrativeAction.APPROVAL) {
            val p=d.overseasExhibition ?: throw BadRequestException("缺少出境展览方案")
            val draftOn=p.draftAgreementSignedOn ?: throw BadRequestException("出境展览报批须记录草签协议日期")
            requiredFiles(p.draftAgreementFileIds)
            if(vo.submittedOn.isBefore(draftOn) || vo.issuedOn.isAfter(d.agreementSignedAt.toLocalDate()))
                throw BadRequestException("出境展览须先草签草案并报批，取得批准后才能签订正式协议")
        } else if(vo.submittedOn.isBefore(d.agreementSignedAt.toLocalDate()))throw BadRequestException("应依据已签署借用协议和清单办理行政报送，请核对实际日期")
        if(e.outExecuted && vo.action!=EnumAdministrativeAction.FILING)throw BadRequestException("出借后的备案不能补充为事前批准；请通过监管记录如实记录问题及报告")
        if(vo.action==EnumAdministrativeAction.FILING && (!e.outExecuted || e.outExecutedTime==null || vo.submittedOn.isBefore(e.outExecutedTime!!.toLocalDate())))throw BadRequestException("出借备案须依据实际出库事实及日期登记，不能提前声称完成出借后备案")
        val content=vo.copy(submissionFileIds=requiredFiles(vo.submissionFileIds),fileIds=requiredFiles(vo.fileIds))
        if(!e.outExecuted) {
            requirePrepared(e,if(e.flowStatus==EnumFlowStatus.PASS)setOf(EnumCollectionStatus.PRE_OUT)else setOf(EnumCollectionStatus.IN_STORE))
            archives.seal(e.orgId,CollectionConst.BIZ_LOAN,id,"PROPOSAL_CONFIRMED",proposal(e),allFiles(e))
        }
        return append(e,"ADMINISTRATIVE",content,listOf(content.submissionFileIds,content.fileIds).joinToString(","))
    }
    fun recordHandover(id: String,vo: LoanHandoverVo): String {
        val e=locked(id)
        if(e.flowStatus!=EnumFlowStatus.PASS || e.outExecuted)throw BadRequestException("仅为本轮已批准、待出库借用登记实际点交")
        val approval=requireCurrentApproval(e)
        val content=validateHandover(e,vo,approval.createTime)
        if(e.handover?.let { it.approvalTaskId==approval.taskId && it.content==content }==true)return id
        if(e.handover!=null && content.replacementReason.isNullOrBlank())throw BadRequestException("更正签署点交原件须说明原因，原版本仍永久保存")
        val record=LoanHandoverRecordVo(content,approval.taskId!!,SecurityUtils.getUserId() ?: throw BadRequestException("请登录"),LocalDateTime.now())
        archives.seal(e.orgId,CollectionConst.BIZ_LOAN,id,"HANDOVER_${IdGenerate.nextId()}",record,handoverFiles(content))
        e.handover=record
        recheckCurrentWrite(e)
        return id
    }
    fun requireHandover(e: LoanDao): LoanHandoverVo {
        val approval=requireCurrentApproval(e)
        val record=e.handover ?: throw BadRequestException("请先登记借用逐件点交、双方各不少于两名实际人员及签署原件；RFID 不能代替点交")
        if(record.approvalTaskId!=approval.taskId)throw BadRequestException("点交原件属于上一轮审批，须重新核实签署")
        return validateHandover(e,record.content,approval.createTime)
    }
    private fun validateHandover(e: LoanDao,v: LoanHandoverVo,approvedAt: LocalDateTime): LoanHandoverVo {
        requirePrepared(e,setOf(EnumCollectionStatus.PRE_OUT));requireAdministrativeBeforeOut(e,v.handedAt)
        val d=e.dossier!!
        if(v.reviewedAt.isBefore(approvedAt) || v.handedAt.isBefore(v.reviewedAt) || v.handedAt.isBefore(d.agreedOutAt) || v.handedAt.isAfter(d.agreedReturnAt) || v.handedAt.isAfter(LocalDateTime.now()))throw BadRequestException("实际审查指派和点交须在本轮批准后、协议借用期限内如实登记")
        if(v.reviewerUserId!=d.keeperUserId)throw BadRequestException("须由本单保管负责人审核协议、清单、批准材料并指派保管人员")
        if(v.custodianUserIds.size<2 || v.receivingPersonNames.map(String::trim).filter(String::isNotBlank).distinct().size<2 || !v.distinctPartiesConfirmed)throw BadRequestException("借用出库点交双方分别不得少于两名实际人员，并核实双方人员不同")
        v.custodianUserIds.forEach { users.requireRole(it,e.orgId,CollectionConst.ROLE_KEEPER) }
        if(!v.safetyMeasuresConfirmed || !v.siteSafetyConfirmed)throw BadRequestException("未落实安全防护或存放场所不具备安全条件，不得出库")
        if(!e.outExecuted && (v.appliedTransportStandardReference?.trim()!=d.transportStandardReference?.trim() ||
                    v.packingStandardConfirmed!=true || v.packingCheckFileIds.isNullOrBlank()))
            throw BadRequestException("须核对实际采用的包装运输标准与批准方案一致，并留存逐件包装核验原件")
        val ids=splitCollectionIds(e.collectionIds)
        if(v.items.size!=ids.size || v.items.map { it.collectionId }.toSet()!=ids.toSet())throw BadRequestException("须对批准清单内每件藏品逐件点交，不得缺件、重复或替换")
        val items=v.items.map { item -> if(item.condition.isBlank())throw BadRequestException("每件藏品具体状况必填");val images=files(item.imageFileIds);if(item.textNeedsImages && images.isNullOrBlank())throw BadRequestException("文字不能充分记录的部分须补充影像");item.copy(imageFileIds=images) }
        return v.copy(items=items,reviewAndAssignmentFileIds=requiredFiles(v.reviewAndAssignmentFileIds),receivingAuthorizationFileIds=requiredFiles(v.receivingAuthorizationFileIds),safetyCheckFileIds=requiredFiles(v.safetyCheckFileIds),signedHandoverFileIds=requiredFiles(v.signedHandoverFileIds),packingCheckFileIds=if(e.outExecuted)files(v.packingCheckFileIds)else requiredFiles(v.packingCheckFileIds))
    }
    fun recordMonitoring(id: String,vo: LoanMonitoringVo): String {
        // 与实际展运开始、使用变更共同锁定藏品，避免核查后并发追加风险却仍放行。
        val source=LoanDao.findById(id) ?: throw BadRequestException("借用单不存在")
        requireCurrentWrite(source)
        splitCollectionIds(source.collectionIds).sorted().forEach { collectionId -> CollectionDao.find { Collections.id eq collectionId }.forUpdate().toList() }
        val e=locked(id)
        if(!e.outExecuted || e.outExecutedTime==null)throw BadRequestException("实际出借后方可登记借用期间监管、催还及报告")
        if(vo.occurredAt.isBefore(e.outExecutedTime) || vo.occurredAt.isAfter(LocalDateTime.now()) || vo.collectionIds.isEmpty() || !splitCollectionIds(e.collectionIds).containsAll(vo.collectionIds))throw BadRequestException("监管日期应在实际出借后，记录应明确对应本单藏品")
        if(listOf(vo.lenderUserId,vo.borrowerRepresentative,vo.description,vo.fileIds).any(String::isBlank))throw BadRequestException("请填写双方实际人员、监管或处理情况和真实原件")
        users.requireRole(vo.lenderUserId,e.orgId,CollectionConst.ROLE_KEEPER)
        if(vo.kind==EnumLoanMonitoringKind.SAFETY_CHECK && vo.safetyConfirmed==null)throw BadRequestException("双方安全检查须明确藏品安全状况和保管条件结论")
        if(vo.kind==EnumLoanMonitoringKind.RECALL && (vo.recallReturnBy==null || vo.recallReturnBy.isBefore(vo.occurredAt.toLocalDate())))throw BadRequestException("催还须明确要求归还的期限")
        if(vo.kind in setOf(EnumLoanMonitoringKind.MAJOR_REPORT,EnumLoanMonitoringKind.ISSUE_RESOLVED)) {
            val issue=monitoring(id).find { it.first.id.value==vo.relatedRecordId }?.second ?: throw BadRequestException("报告或处理完成须关联已登记的异常或重大情况")
            if((issue.safetyConfirmed!=false && !issue.majorSituation && issue.kind!=EnumLoanMonitoringKind.RECALL) || vo.occurredAt.isBefore(issue.occurredAt) || !vo.collectionIds.containsAll(issue.collectionIds))throw BadRequestException("处理或报告须在该问题发生后覆盖相关藏品")
            if(vo.kind==EnumLoanMonitoringKind.MAJOR_REPORT && (vo.authorityName.isNullOrBlank() || vo.reportNo.isNullOrBlank()))throw BadRequestException("重大情况报告须保留主管文物行政部门、实际报送编号及凭据")
            if(vo.kind==EnumLoanMonitoringKind.ISSUE_RESOLVED && issue.majorSituation && monitoring(id).none { it.second.kind==EnumLoanMonitoringKind.MAJOR_REPORT && it.second.relatedRecordId==vo.relatedRecordId })throw BadRequestException("重大情况须先履行报告程序，不能仅标记处理完成")
        }
        return append(e,"MONITORING",vo.copy(fileIds=requiredFiles(vo.fileIds)),vo.fileIds)
    }
    fun extensionRevision(e:LoanDao,ignoreId:String?=null):String = CollectionCareEvidence.digest(JsonUtils.toJson(mapOf(
        "proposal" to proposal(e),"outExecuted" to e.outExecuted,"outExecutedTime" to e.outExecutedTime,"returned" to e.returned,
        "records" to records(e.id.value).filter {it.id.value!=ignoreId}.sortedBy {it.id.value}.map {listOf(it.id.value,it.kind,it.content)},
    ))!!)

    private fun requireRecordOriginal(e:LoanDao,row:LoanComplianceRecordDao) = LoanOverseasExtensionEvidence.requireRecordOriginal(e,row)
    private fun extensions(e:LoanDao) = LoanOverseasExtensionEvidence.extensions(e)
    fun extensionViews(e:LoanDao)=extensions(e).map { (row,value) -> mapOf("id" to row.id.value,"content" to value,"recordedAt" to row.createTime) }
    fun extensionAvailable(e:LoanDao)=e.outExecuted && !e.returned && e.flowStatus==EnumFlowStatus.PASS &&
        e.dossier?.exhibitionOverseas==true && e.dossier?.overseasExhibition!=null && relicManaged(e) && exhibitionEnds(e).isEmpty() && overseasFilings(e).none {it.second.request.kind==EnumOverseasFilingKind.CONCLUSION}

    fun recordExtension(id:String,v:LoanOverseasExtensionVo,expectedOrgId:String):String {
        val tx=TransactionManager.current();tx.entityCache.flush()
        val initial=LoanDao.findById(id) ?: throw BadRequestException("借用单不存在")
        fun access(e:LoanDao) {
            CollectionCurrentOperationAccess.require(scope,e.orgId,true,setOf(CollectionConst.STORAGE),"出境展览延期")
            if(e.orgId!=expectedOrgId)throw BadRequestException("借用所属馆已变化，请重新核对")
        }
        access(initial)
        val ids=splitCollectionIds(initial.collectionIds).sorted()
        ids.forEach { CollectionDao.find {Collections.id eq it}.forUpdate().toList() }
        val e=LoanDao.find {Loans.id eq id}.forUpdate().single();e.refresh();access(e)
        if(splitCollectionIds(e.collectionIds).sorted()!=ids)throw BadRequestException("借用清单已变化，请重新核对")
        val actor=SecurityUtils.getUserId() ?: throw BadRequestException("请登录")
        val existing=extensions(e)
        existing.lastOrNull()?.takeIf {it.second.request==v && it.second.recordedBy==actor}?.let {return it.first.id.value}
        if(v.sourceRevision!=extensionRevision(e) || v.previousId!=existing.lastOrNull()?.first?.id?.value)throw BadRequestException("借用或延期依据已变化，请重新读取")
        val d=e.dossier ?: throw BadRequestException("缺少原借用协议")
        val p=d.overseasExhibition ?: throw BadRequestException("缺少原出境展览方案")
        if(!extensionAvailable(e))
            throw BadRequestException("仅为已实际出借且尚未全部归还的出境文物展览登记延期")
        val approval=administrative(id).singleOrNull {it.first.id.value==v.originalApprovalId}
            ?: throw BadRequestException("请选择本单原出境展览批准记录")
        requireRecordOriginal(e,approval.first)
        if(approval.second.action!=EnumAdministrativeAction.APPROVAL || approval.second.level!=overseasApprovalLevel(e) ||
            approval.second.issuedOn.isAfter(e.outExecutedTime?.toLocalDate() ?: throw BadRequestException("缺少实际出借时间")) ||
            v.authorityName!=approval.second.authorityName || existing.any {it.second.request.originalApprovalId!=v.originalApprovalId})
            throw BadRequestException("延期须由本单原出境展览审批机关批准，不能替换原批准")
        val previousEnd=existing.lastOrNull()?.second?.request?.exhibitionEndsOn ?: p.exhibitionEndsOn
        val previousReturn=existing.lastOrNull()?.second?.request?.agreedReturnAt ?: d.agreedReturnAt
        val previousRequest=existing.lastOrNull()?.second?.request
        val today=LocalDate.now()
        if(listOf(v.specialNeed,v.authorityName,v.documentNo).any(String::isBlank) || v.submittedOn.isBefore(approval.second.issuedOn) ||
            v.approvedOn.isBefore(v.submittedOn) || v.approvedOn.isAfter(today) || v.agreementSignedOn.isBefore(v.approvedOn) || v.agreementSignedOn.isAfter(today) ||
            previousRequest?.let {v.approvedOn.isBefore(it.approvedOn) || v.agreementSignedOn.isBefore(it.agreementSignedOn)}==true)
            throw BadRequestException("须按实际顺序登记特殊需要、申请、原机关批准及双方补充协议，不得预填未来事实")
        if(!v.exhibitionEndsOn.isAfter(previousEnd) || v.exhibitionEndsOn.isAfter(p.exhibitionEndsOn.plusYears(1)) ||
            v.exhibitionEndsOn.isBefore(v.approvedOn) || v.agreedReturnAt.isBefore(previousReturn) ||
            v.agreedReturnAt.toLocalDate().isBefore(v.exhibitionEndsOn) || v.agreedReturnAt.isAfter(d.agreedOutAt.plusYears(3)))
            throw BadRequestException("累计延长展期不得超过原展期届满后一年；双方归还约定须覆盖展期且借用总期限不超过三年")
        val request=v.copy(submissionFileIds=requiredFiles(v.submissionFileIds),approvalFileIds=requiredFiles(v.approvalFileIds),agreementFileIds=requiredFiles(v.agreementFileIds))
        val value=LoanOverseasExtensionRecordVo(request,previousEnd,previousReturn,v.approvedOn.isAfter(previousEnd),actor)
        val result=append(e,"OVERSEAS_EXTENSION",value,listOf(request.submissionFileIds,request.approvalFileIds,request.agreementFileIds).joinToString(","))
        tx.entityCache.flush();tx.entityCache.clear()
        val current=LoanDao[id];access(current)
        if(SecurityUtils.getUserId()!=actor || extensionRevision(current,result)!=v.sourceRevision)throw BadRequestException("归档期间借用或延期依据已变化，本次办理已回滚")
        extensions(current)
        return result
    }

    private fun portRecords(e:LoanDao):List<Pair<LoanComplianceRecordDao,LoanPortClearanceRecordVo>> {
        val result=records(e.id.value).filter {it.kind=="OVERSEAS_PORT"}.sortedBy {it.id.value}.map {row ->
            requireRecordOriginal(e,row)
            val value=JsonUtils.fromJson(row.content,LoanPortClearanceRecordVo::class.java) ?: throw BadRequestException("口岸手续原件缺失")
            val approval=records(e.id.value).singleOrNull {it.kind=="ADMINISTRATIVE" && it.id.value==value.request.originalApprovalId}
                ?: throw BadRequestException("口岸手续对应的行政批准缺失")
            requireRecordOriginal(e,approval)
            row to value
        }
        val seen=mutableMapOf<String,LoanPortClearanceVo>()
        val exits=mutableSetOf<String>();val entries=mutableSetOf<String>()
        result.forEach {(row,value)->
            val v=value.request
            if(v.collectionIds.isEmpty() || !splitCollectionIds(e.collectionIds).containsAll(v.collectionIds) || v.identifiers.keys!=v.collectionIds)
                throw BadRequestException("口岸原件与借用清单不一致")
            if(v.stage==EnumLoanPortStage.EXIT) {
                if(v.exitRecordId!=null || v.collectionIds.any {it in exits})throw BadRequestException("出境原件清单重复或来源错误")
                exits.addAll(v.collectionIds)
            } else {
                val exit=seen[v.exitRecordId] ?: throw BadRequestException("复进境对应的原出境原件缺失")
                if(exit.stage!=EnumLoanPortStage.EXIT || exit.reviewAgency!=v.reviewAgency || exit.originalApprovalId!=v.originalApprovalId ||
                    !exit.collectionIds.containsAll(v.collectionIds) || v.collectionIds.any {exit.identifiers[it]!=v.identifiers[it] || it in entries} || v.reviewedAt.isBefore(exit.customsAt))
                    throw BadRequestException("复进境清单、标识、原审核机构或顺序与出境原件不一致")
                entries.addAll(v.collectionIds)
            }
            seen[row.id.value]=v
        }
        return result
    }
    fun portViews(e:LoanDao)=portRecords(e).map {(row,value)->mapOf("id" to row.id.value,"content" to value,"recordedAt" to row.createTime)}
    fun portAvailable(e:LoanDao)=e.outExecuted && e.flowStatus==EnumFlowStatus.PASS && e.dossier?.exhibitionOverseas==true && e.dossier?.overseasExhibition!=null && relicManaged(e)

    fun recordPort(id:String,v:LoanPortClearanceVo,expectedOrgId:String):String {
        val tx=TransactionManager.current();tx.entityCache.flush()
        val initial=LoanDao.findById(id) ?: throw BadRequestException("借用单不存在")
        fun access(e:LoanDao) {
            CollectionCurrentOperationAccess.require(scope,e.orgId,true,setOf(CollectionConst.STORAGE),"出入境口岸手续")
            if(e.orgId!=expectedOrgId)throw BadRequestException("借用所属馆已变化，请重新核对")
        }
        access(initial)
        val ids=splitCollectionIds(initial.collectionIds).sorted()
        ids.forEach {CollectionDao.find {Collections.id eq it}.forUpdate().toList()}
        val e=LoanDao.find {Loans.id eq id}.forUpdate().single();e.refresh();access(e)
        if(splitCollectionIds(e.collectionIds).sorted()!=ids)throw BadRequestException("借用清单已变化，请重新核对")
        val actor=SecurityUtils.getUserId() ?: throw BadRequestException("请登录")
        val prior=portRecords(e)
        prior.lastOrNull()?.takeIf {it.second.request==v && it.second.recordedBy==actor}?.let {return it.first.id.value}
        if(v.sourceRevision!=extensionRevision(e))throw BadRequestException("借用或口岸手续依据已变化，请重新读取")
        if(!portAvailable(e))throw BadRequestException("仅为已实际出库的出境文物展览登记口岸手续")
        if(v.collectionIds.isEmpty() || !ids.containsAll(v.collectionIds) || v.identifiers.keys!=v.collectionIds || v.identifiers.values.any(String::isBlank))
            throw BadRequestException("口岸审核清单须为本单藏品，审核登记标识须逐件完整对应")
        val approval=administrative(id).singleOrNull {it.first.id.value==v.originalApprovalId}
            ?: throw BadRequestException("请选择本单出境展览批准记录")
        requireRecordOriginal(e,approval.first)
        if(approval.second.action!=EnumAdministrativeAction.APPROVAL || approval.second.level!=overseasApprovalLevel(e) || approval.second.collectionIds!=ids.toSet())
            throw BadRequestException("口岸手续须依据覆盖本单清单的出境展览批准")
        if(listOf(v.reviewAgency,v.certificateNo,v.portName,v.customsDocumentNo).any(String::isBlank) ||
            v.reviewedAt.toLocalDate().isBefore(approval.second.issuedOn) || v.customsAt.isBefore(v.reviewedAt) ||
            v.customsAt.isAfter(LocalDateTime.now()) || v.customsAt.isBefore(e.outExecutedTime ?: throw BadRequestException("缺少实际出库时间")))
            throw BadRequestException("请按实际批准、审核和海关手续顺序登记，不得预填未来事实")
        val received=CollectionReturnDao.find {(CollectionReturns.sourceBizType eq CollectionConst.BIZ_LOAN) and (CollectionReturns.sourceBizId eq id) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED)}.onEach {CollectionCirculationReadIntegrity.requireReturnSource(it)}.toList()
        if(received.any {r->splitCollectionIds(r.collectionIds).any {it in v.collectionIds} && (r.executedAt==null || v.customsAt.isAfter(r.executedAt!!))})
            throw BadRequestException("出入境实际手续时间不能晚于相关藏品的实际归库接收时间")
        if(v.stage==EnumLoanPortStage.EXIT) {
            if(v.exitRecordId!=null || prior.any {it.second.request.stage==EnumLoanPortStage.EXIT && it.second.request.collectionIds.any {cid->cid in v.collectionIds}})
                throw BadRequestException("同一借用藏品不能重复登记出境，复进境请关联原出境记录")
            requiredFiles(v.designatedPortFileIds)
        } else {
            val exit=prior.singleOrNull {it.first.id.value==v.exitRecordId && it.second.request.stage==EnumLoanPortStage.EXIT}?.second?.request
                ?: throw BadRequestException("复进境须关联本单原出境手续")
            if(exit.originalApprovalId!=v.originalApprovalId || exit.reviewAgency!=v.reviewAgency || !exit.collectionIds.containsAll(v.collectionIds) ||
                v.collectionIds.any {exit.identifiers[it]!=v.identifiers[it]} || v.reviewedAt.isBefore(exit.customsAt))
                throw BadRequestException("复进境须由原审核机构核验原出境清单和登记标识，时间不得早于出境")
            if(prior.any {it.second.request.stage==EnumLoanPortStage.REENTRY && it.second.request.collectionIds.any {cid->cid in v.collectionIds}})
                throw BadRequestException("这些藏品已有复进境结项记录，不得重复登记")
        }
        val request=v.copy(certificateFileIds=requiredFiles(v.certificateFileIds),customsFileIds=requiredFiles(v.customsFileIds),designatedPortFileIds=files(v.designatedPortFileIds))
        val result=append(e,"OVERSEAS_PORT",LoanPortClearanceRecordVo(request,actor),listOfNotNull(request.certificateFileIds,request.customsFileIds,request.designatedPortFileIds).joinToString(","))
        tx.entityCache.flush();tx.entityCache.clear()
        val current=LoanDao[id];access(current)
        if(SecurityUtils.getUserId()!=actor || extensionRevision(current,result)!=v.sourceRevision)throw BadRequestException("归档期间借用或口岸手续依据已变化，本次办理已回滚")
        portRecords(current)
        return result
    }

    private fun exhibitionEnds(e:LoanDao)=LoanExhibitionEndEvidence.records(e)
    fun exhibitionEndViews(e:LoanDao)=exhibitionEnds(e).map {(row,value)->mapOf("id" to row.id.value,"content" to value,"recordedAt" to row.createTime)}
    fun exhibitionEndAvailable(e:LoanDao)=e.outExecuted && overseasFilingAvailable(e)
    fun recordExhibitionEnd(id:String,v:LoanExhibitionEndVo,expectedOrgId:String):String {
        val tx=TransactionManager.current();tx.entityCache.flush()
        val initial=LoanDao.findById(id) ?: throw BadRequestException("借用单不存在")
        fun access(e:LoanDao) {
            CollectionCurrentOperationAccess.require(scope,e.orgId,true,setOf(CollectionConst.STORAGE),"核实展览实际结束")
            if(e.orgId!=expectedOrgId)throw BadRequestException("借用所属馆已变化，请重新核对")
        }
        access(initial)
        val ids=splitCollectionIds(initial.collectionIds).sorted()
        ids.forEach {CollectionDao.find {Collections.id eq it}.forUpdate().toList()}
        val e=LoanDao.find {Loans.id eq id}.forUpdate().single();e.refresh();access(e)
        if(splitCollectionIds(e.collectionIds).sorted()!=ids)throw BadRequestException("借用清单已变化，请重新核对")
        val actor=SecurityUtils.getUserId() ?: throw BadRequestException("请登录")
        val previous=exhibitionEnds(e).lastOrNull()
        previous?.takeIf {it.second.request==v && it.second.recordedBy==actor}?.let {return it.first.id.value}
        if(v.sourceRevision!=extensionRevision(e) || v.previousId!=previous?.first?.id?.value)throw BadRequestException("展览结束事实依据已变化，请重新读取")
        if(previous!=null && v.correctionReason.isNullOrBlank())throw BadRequestException("更正实际结束事实须说明原因，原件永久保留")
        if(!exhibitionEndAvailable(e))throw BadRequestException("须依据已实际出库的出境文物展览核实结束事实")
        val p=e.dossier!!.overseasExhibition!!
        if(v.actualEndedOn.isBefore(p.exhibitionStartsOn) || v.actualEndedOn.isAfter(LocalDate.now()) ||
            v.actualEndedOn.isBefore(e.outExecutedTime?.toLocalDate() ?: throw BadRequestException("缺少实际出库时间")))
            throw BadRequestException("实际结束日期须在展览开始和实际出库后，不得登记未来事实")
        val request=v.copy(evidenceFileIds=requiredFiles(v.evidenceFileIds))
        val result=append(e,"EXHIBITION_END",LoanExhibitionEndRecordVo(request,actor),request.evidenceFileIds)
        tx.entityCache.flush();tx.entityCache.clear()
        val current=LoanDao[id];access(current)
        if(SecurityUtils.getUserId()!=actor || extensionRevision(current,result)!=v.sourceRevision)throw BadRequestException("归档期间结束事实依据已变化，本次办理已回滚")
        exhibitionEnds(current)
        return result
    }

    private fun overseasFilings(e:LoanDao):List<Pair<LoanComplianceRecordDao,LoanOverseasFilingRecordVo>> {
        val last=mutableMapOf<EnumOverseasFilingKind,String>()
        return records(e.id.value).filter {it.kind=="OVERSEAS_FILING"}.sortedBy {it.id.value}.map {row ->
            requireRecordOriginal(e,row)
            val value=JsonUtils.fromJson(row.content,LoanOverseasFilingRecordVo::class.java) ?: throw BadRequestException("出境备案原件缺失")
            if(value.request.previousId!=last[value.request.kind])throw BadRequestException("出境备案更正链与原记录不一致")
            value.request.endingRecordId?.let {endId ->
                val end=exhibitionEnds(e).singleOrNull {it.first.id.value==endId}?.second?.request ?: throw BadRequestException("结项报送对应的结束原件缺失")
                if(value.request.kind!=EnumOverseasFilingKind.CONCLUSION || value.request.actualExhibitionEndedOn!=end.actualEndedOn || value.request.endingEvidenceFileIds!=end.evidenceFileIds)
                    throw BadRequestException("结项报送日期或依据与关联结束原件不一致")
            }
            last[value.request.kind]=row.id.value
            row to value
        }
    }
    fun overseasFilingViews(e:LoanDao)=overseasFilings(e).map {(row,value)->mapOf("id" to row.id.value,"content" to value,"recordedAt" to row.createTime)}
    fun overseasFilingAvailable(e:LoanDao)=e.dossier?.exhibitionOverseas==true && e.dossier?.overseasExhibition!=null && relicManaged(e) &&
        e.flowStatus in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.AUDIT,EnumFlowStatus.PASS) && administrative(e.id.value).any {it.second.action==EnumAdministrativeAction.APPROVAL && it.second.level==overseasApprovalLevel(e)}

    fun recordOverseasFiling(id:String,v:LoanOverseasFilingVo,expectedOrgId:String):String {
        val tx=TransactionManager.current();tx.entityCache.flush()
        val initial=LoanDao.findById(id) ?: throw BadRequestException("借用单不存在")
        fun access(e:LoanDao) {
            CollectionCurrentOperationAccess.require(scope,e.orgId,true,setOf(CollectionConst.STORAGE),"出境协议备案及展览结项")
            if(e.orgId!=expectedOrgId)throw BadRequestException("借用所属馆已变化，请重新核对")
        }
        access(initial)
        val ids=splitCollectionIds(initial.collectionIds).sorted()
        ids.forEach {CollectionDao.find {Collections.id eq it}.forUpdate().toList()}
        val e=LoanDao.find {Loans.id eq id}.forUpdate().single();e.refresh();access(e)
        if(splitCollectionIds(e.collectionIds).sorted()!=ids)throw BadRequestException("借用清单已变化，请重新核对")
        val actor=SecurityUtils.getUserId() ?: throw BadRequestException("请登录")
        val previous=overseasFilings(e).lastOrNull {it.second.request.kind==v.kind}
        previous?.takeIf {it.second.request==v && it.second.recordedBy==actor}?.let {return it.first.id.value}
        if(v.sourceRevision!=extensionRevision(e) || v.previousId!=previous?.first?.id?.value)throw BadRequestException("出境备案依据已变化，请重新读取")
        if(previous!=null && v.correctionReason.isNullOrBlank())throw BadRequestException("补充或更正备案须说明原因并保留历次原件")
        if(!overseasFilingAvailable(e))throw BadRequestException("须先核实出境文物展览方案及原行政批准")
        val d=e.dossier!!;val p=d.overseasExhibition!!
        val approval=administrative(id).filter {it.second.action==EnumAdministrativeAction.APPROVAL && it.second.level==overseasApprovalLevel(e)}
            .firstOrNull { !it.second.issuedOn.isAfter(d.agreementSignedAt.toLocalDate()) } ?: throw BadRequestException("须取得出境批准后签订正式协议")
        requireRecordOriginal(e,approval.first)
        if(v.authorityLevel!=EnumAuthorityLevel.NATIONAL || listOf(v.authorityName,v.submissionNo).any(String::isBlank) || v.submittedOn.isAfter(LocalDate.now()))
            throw BadRequestException("应记录实际向国家文物行政部门报送的机关、编号、日期及接收凭据")
        val basis=if(v.kind==EnumOverseasFilingKind.AGREEMENT) {
            if(v.endingRecordId!=null || v.actualExhibitionEndedOn!=null || listOf(v.endingEvidenceFileIds,v.conclusionFormFileIds,v.conclusionReportFileIds,v.audiovisualFileIds).any {!it.isNullOrBlank()})
                throw BadRequestException("协议备案与展览结项应分别登记")
            d.agreementSignedAt.toLocalDate()
        } else {
            val end=exhibitionEnds(e).lastOrNull() ?: throw BadRequestException("请先独立核实并登记展览实际结束事实")
            if(v.endingRecordId!=end.first.id.value || v.actualExhibitionEndedOn!=end.second.request.actualEndedOn || v.endingEvidenceFileIds!=end.second.request.evidenceFileIds)
                throw BadRequestException("结项报送须关联最新结束原件，不能在报送时另填结束事实")
            val ended=end.second.request.actualEndedOn
            if(!e.outExecuted || ended.isBefore(p.exhibitionStartsOn) || ended.isAfter(LocalDate.now()) || ended.isBefore(e.outExecutedTime?.toLocalDate() ?: throw BadRequestException("缺少实际出库时间")))
                throw BadRequestException("展览结项须在实际出库和展览开始后如实登记结束事实")
            listOf(v.endingEvidenceFileIds,v.conclusionFormFileIds,v.conclusionReportFileIds,v.audiovisualFileIds).forEach {requiredFiles(it)}
            ended
        }
        if(v.submittedOn.isBefore(basis))throw BadRequestException("报送日期不能早于正式协议签订或展览实际结束日期")
        val due=basis.plusMonths(if(v.kind==EnumOverseasFilingKind.AGREEMENT)1 else 2)
        val request=v.copy(submissionReceiptFileIds=requiredFiles(v.submissionReceiptFileIds),endingEvidenceFileIds=files(v.endingEvidenceFileIds),conclusionFormFileIds=files(v.conclusionFormFileIds),conclusionReportFileIds=files(v.conclusionReportFileIds),audiovisualFileIds=files(v.audiovisualFileIds))
        val attachments=listOfNotNull(request.submissionReceiptFileIds,request.endingEvidenceFileIds,request.conclusionFormFileIds,request.conclusionReportFileIds,request.audiovisualFileIds,
            d.signedAgreementFileIds.takeIf {v.kind==EnumOverseasFilingKind.AGREEMENT}).joinToString(",")
        val result=append(e,"OVERSEAS_FILING",LoanOverseasFilingRecordVo(request,due,request.submittedOn.isAfter(due),actor),attachments)
        tx.entityCache.flush();tx.entityCache.clear()
        val current=LoanDao[id];access(current)
        if(SecurityUtils.getUserId()!=actor || extensionRevision(current,result)!=v.sourceRevision)throw BadRequestException("归档期间备案依据已变化，本次办理已回滚")
        overseasFilings(current)
        return result
    }
    private fun overseasFilingObligations(e:LoanDao,today:LocalDate):List<Map<String,Any?>> {
        if(e.dossier?.exhibitionOverseas!=true || e.dossier?.overseasExhibition==null || !relicManaged(e))return emptyList()
        val records=overseasFilings(e)
        val ending=exhibitionEnds(e).lastOrNull()
        return EnumOverseasFilingKind.entries.map {kind ->
            val historic=records.lastOrNull {it.second.request.kind==kind}
            val row=historic?.takeIf {kind==EnumOverseasFilingKind.AGREEMENT || ending==null || it.second.request.endingRecordId==ending.first.id.value}
            val due=if(kind==EnumOverseasFilingKind.AGREEMENT)e.dossier!!.agreementSignedAt.toLocalDate().plusMonths(1) else ending?.second?.request?.actualEndedOn?.plusMonths(2) ?: row?.second?.dueOn
            mapOf("kind" to kind,"dueOn" to due,"submittedOn" to row?.second?.request?.submittedOn,"late" to row?.second?.late,
                "endingRecordId" to ending?.first?.id?.value.takeIf {kind==EnumOverseasFilingKind.CONCLUSION},
                "actualExhibitionEndedOn" to (ending?.second?.request?.actualEndedOn ?: row?.second?.request?.actualExhibitionEndedOn).takeIf {kind==EnumOverseasFilingKind.CONCLUSION},
                "endDateNeedsVerification" to (kind==EnumOverseasFilingKind.CONCLUSION && ending==null && row==null && e.outExecuted),
                "filingNeedsReverification" to (historic!=null && row==null),
                "overdue" to (row==null && due!=null && today.isAfter(due)),"lateHistoryIds" to records.filter {it.second.request.kind==kind && it.second.late}.map {it.first.id.value})
        }
    }

    fun obligations(e: LoanDao): Map<String,Any?> = obligations(e,LocalDateTime.now())
    /** 日期型历史核验从当日零点计算；实际业务读取使用精确时间。 */
    fun obligations(e: LoanDao,today: LocalDate): Map<String,Any?> = obligations(e,today.atStartOfDay())
    /** 安全待办将无效履行原件转为核查事项，读取失败不应标记外层查询事务回滚。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun obligations(e: LoanDao,at: LocalDateTime): Map<String,Any?> {
        val today=at.toLocalDate()
        if(e.dossier==null || e.baseline==null)return mapOf("legacyIncomplete" to true)
        val d=e.dossier!!
        val ports=portRecords(e).map {it.second.request}
        val exitIds=ports.filter {it.stage==EnumLoanPortStage.EXIT}.flatMap {it.collectionIds}.toSet()
        val reentryIds=ports.filter {it.stage==EnumLoanPortStage.REENTRY}.flatMap {it.collectionIds}.toSet()
        val portPending=if(d.exhibitionOverseas==true && relicManaged(e) && e.outExecuted)splitCollectionIds(e.collectionIds) else emptyList()
        val extensions=extensions(e)
        val effectiveReturnAt=extensions.lastOrNull()?.second?.request?.agreedReturnAt ?: d.agreedReturnAt
        val records=monitoring(e.id.value)
        val returned=CollectionReturnDao.find { (CollectionReturns.sourceBizType eq CollectionConst.BIZ_LOAN) and (CollectionReturns.sourceBizId eq e.id.value) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED) }.onEach {CollectionCirculationReadIntegrity.requireReturnSource(it)}.flatMap { splitCollectionIds(it.collectionIds) }.toSet()
        val accepted=CollectionReturnDao.find { (CollectionReturns.sourceBizType eq CollectionConst.BIZ_LOAN) and (CollectionReturns.sourceBizId eq e.id.value) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED) }.onEach {CollectionCirculationReadIntegrity.requireReturnSource(it)}.flatMap { splitCollectionIds(it.acceptedCollectionIds ?: it.collectionIds) }.toSet()
        val held=returned-accepted
        val remaining=splitCollectionIds(e.collectionIds).filter { it !in returned }
        val filing=if(e.outExecutedTime!=null)filingLevels(e).map { level ->
            val due=calendar.deadline(e.orgId,e.outExecutedTime!!.toLocalDate(),30)
            val actual=administrative(e.id.value).map { it.second }.filter { it.level==level && it.action==EnumAdministrativeAction.FILING }.minByOrNull { it.submittedOn }
            mapOf("level" to level,"dueOn" to due,"submittedOn" to actual?.submittedOn,"completedOn" to actual?.issuedOn,"onTime" to actual?.let { !it.submittedOn.isAfter(due) },"overdue" to (actual==null && today.isAfter(due)))
        }else emptyList()
        val returnDeadlines=remaining.map { id ->
            val recalls=records.filter { it.second.kind==EnumLoanMonitoringKind.RECALL && id in it.second.collectionIds }
            val recallDue=recalls.mapNotNull { it.second.recallReturnBy }.minOrNull()
            val agreementDue=effectiveReturnAt.toLocalDate()
            // 催还只缩短本件归还期限；问题处理记录不等于归还或获准延长协议。
            val due=recallDue?.let { minOf(it,agreementDue) } ?: agreementDue
            val dueAt=minOf(effectiveReturnAt,recallDue?.plusDays(1)?.atStartOfDay() ?: effectiveReturnAt)
            mapOf("collectionId" to id,"originalAgreementDueOn" to d.agreedReturnAt.toLocalDate(),"agreementDueOn" to agreementDue,"recallDueOn" to recallDue,
                "dueOn" to due,"dueAt" to dueAt,"recallRecordIds" to recalls.map { it.first.id.value },
                "overdue" to (e.outExecuted && !at.isBefore(dueAt)))
        }
        val inspections=remaining.map { id ->
            val last=records.map { it.second }.filter { it.kind==EnumLoanMonitoringKind.SAFETY_CHECK && id in it.collectionIds }.maxOfOrNull { it.occurredAt.toLocalDate() }
            val due=last?.plusDays(d.inspectionIntervalDays.toLong()) ?: d.firstInspectionOn
            mapOf("collectionId" to id,"nextDueOn" to due,"overdue" to (e.outExecuted && today.isAfter(due)))
        }
        val issues=records.filter { it.second.kind !in setOf(EnumLoanMonitoringKind.MAJOR_REPORT,EnumLoanMonitoringKind.ISSUE_RESOLVED) && (it.second.safetyConfirmed==false || it.second.majorSituation || it.second.kind==EnumLoanMonitoringKind.RECALL) }
        val firstFilings=administrative(e.id.value).map { it.second }.filter { it.action==EnumAdministrativeAction.FILING && it.level in filingLevels(e) }.groupBy { it.level }.mapValues { it.value.minOf { r -> r.submittedOn } }
        val simultaneous=if(filingLevels(e).size>1 && firstFilings.size==filingLevels(e).size) firstFilings.values.distinct().size==1 else null
        return mapOf("overseasFilings" to overseasFilingObligations(e,today),"missingExitClearanceCollectionIds" to portPending.filter {it !in exitIds},"pendingReentryCollectionIds" to portPending.filter {it !in reentryIds},"returnedWithoutReentryCollectionIds" to portPending.filter {it in returned && it !in reentryIds},"heldForReviewCollectionIds" to held,"simultaneousFirstLevelFiling" to simultaneous,"filings" to filing,"returnDueAt" to d.agreedReturnAt,"effectiveReturnDueAt" to effectiveReturnAt,"effectiveExhibitionEndsOn" to (extensions.lastOrNull()?.second?.request?.exhibitionEndsOn ?: d.overseasExhibition?.exhibitionEndsOn),"lateExtensionIds" to extensions.filter {it.second.approvalLate}.map {it.first.id.value},"remainingCollectionIds" to remaining,"returnDeadlines" to returnDeadlines,"returnOverdue" to returnDeadlines.any { it["overdue"]==true },"inspections" to inspections,
            "unresolvedIssueIds" to issues.filter { issue -> records.none { it.second.kind==EnumLoanMonitoringKind.ISSUE_RESOLVED && it.second.relatedRecordId==issue.first.id.value } }.map { it.first.id.value },
            "pendingMajorReportIds" to issues.filter { issue -> issue.second.majorSituation && records.none { it.second.kind==EnumLoanMonitoringKind.MAJOR_REPORT && it.second.relatedRecordId==issue.first.id.value } }.map { it.first.id.value },
            "lastRecallAt" to records.map { it.second }.filter { it.kind==EnumLoanMonitoringKind.RECALL }.maxOfOrNull { it.occurredAt })
    }
    fun proposal(e: LoanDao): Map<String,Any?> = mapOf("id" to e.id.value,"orgId" to e.orgId,"title" to e.title,"collectionIds" to e.collectionIds,"borrower" to e.borrower,"protocolNo" to e.protocolNo,"insurance" to e.insurance,"attachmentIds" to e.attachmentIds,"operator" to e.operator,"reviewer" to e.reviewer,"loanDate" to e.loanDate,"returnDate" to e.returnDate,"dossier" to e.dossier,"baseline" to e.baseline)
    fun administrativeViews(id: String)=administrative(id).map { (row,value) -> mapOf("id" to row.id.value,"content" to value,"recordedAt" to row.createTime) }
    fun monitoringViews(id: String)=monitoring(id).map { (row,value) -> mapOf("id" to row.id.value,"content" to value,"recordedAt" to row.createTime) }
    private fun records(id:String):List<LoanComplianceRecordDao> {
        val owner=LoanDao.findById(id)?.orgId ?: throw BadRequestException("借用单不存在")
        return LoanComplianceRecordDao.find {LoanComplianceRecords.loanId eq id}.toList().also { rows ->
            if(rows.any {it.orgId!=owner})throw BadRequestException("借用行政或监管记录所属馆与原业务不一致")
        }
    }
    private fun administrative(id: String)=records(id).filter { it.kind=="ADMINISTRATIVE" }.map { it to JsonUtils.fromJson(it.content,LoanAdministrativeVo::class.java)!! }
    private fun monitoring(id: String)=records(id).filter { it.kind=="MONITORING" }.map { it to JsonUtils.fromJson(it.content,LoanMonitoringVo::class.java)!! }
    private fun requireCurrentWrite(e: LoanDao) =
        CollectionCurrentOperationAccess.require(scope,e.orgId,true,setOf(CollectionConst.STORAGE),"借用办理")

    private fun recheckCurrentWrite(e: LoanDao) {
        // 先执行延迟写入，再刷新实际业务与馆状态，不能用 DAO 缓存代替归档后的复验。
        TransactionManager.current().entityCache.flush()
        e.refresh(flush=false)
        CollectionOrgDao.findById(e.orgId)?.refresh(flush=false)
        requireCurrentWrite(e)
    }

    private fun locked(id: String): LoanDao {
        val e=LoanDao.find { Loans.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("借用单不存在")
        requireCurrentWrite(e)
        return e
    }
    private fun append(e: LoanDao,kind: String,value: Any,fileIds: String): String {
        requireCurrentWrite(e)
        val json=JsonUtils.toJson(value)!!
        records(e.id.value).find { it.kind==kind && it.content==json }?.let { return it.id.value }
        val row=LoanComplianceRecordDao.new { orgId=e.orgId;loanId=e.id.value;this.kind=kind;content=json }
        archives.seal(e.orgId,CollectionConst.BIZ_LOAN,e.id.value,"${kind}_${row.id.value}",value,fileIds)
        recheckCurrentWrite(e)
        return row.id.value
    }
    fun allFiles(e: LoanDao): String {
        val d=e.dossier
        return (listOfNotNull(e.attachmentIds,d?.borrowerIdentityFileIds,d?.collectiveDecisionFileIds,d?.signedAgreementFileIds,d?.agreedCollectionListFileIds,d?.carrierCapabilityFileIds,d?.transportAssignmentFileIds,d?.transportPlanFileIds,d?.inspectionPlanFileIds,d?.otherLegalBasisFileIds,d?.overseasProcedureFileIds,d?.overseasExhibition?.wholeExhibitionListFileIds,d?.overseasExhibition?.prohibitedCatalogCheckFileIds,d?.overseasExhibition?.draftAgreementFileIds)+d?.assessments.orEmpty().map { it.assessmentFileIds }+d?.overseasExhibition?.objects.orEmpty().map { it.domesticExhibitionEvidenceFileIds }).joinToString(",")
    }
    fun handoverFiles(v: LoanHandoverVo)=(listOfNotNull(v.reviewAndAssignmentFileIds,v.receivingAuthorizationFileIds,v.safetyCheckFileIds,v.signedHandoverFileIds,v.packingCheckFileIds)+v.items.mapNotNull { it.imageFileIds }).joinToString(",")
    private fun approvalPhase(taskId: String)="APPROVED_"+taskId.takeLast(40)
    private fun requiredFiles(value: String?)=files(value) ?: throw BadRequestException("真实签署及行政依据原件必填")
    private fun files(value: String?)=value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).distinct().map { binder.resolveSingle(it)!! }.joinToString(",").takeIf(String::isNotBlank)
}
