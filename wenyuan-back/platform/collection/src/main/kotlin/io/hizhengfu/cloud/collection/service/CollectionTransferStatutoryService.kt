package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagJobStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime

/** 行政原件、双方真实审批和实际交接与可配置流程分开核验。 */
@Component
@Transactional
class CollectionTransferStatutoryService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val binder: BusinessAttachmentBinder,
    private val archives: CollectionPermanentArchiveService,
    private val archivePackets: CollectionArchivePacketService,
    private val disclosures: CollectionDisclosureService,
    private val retainedLedgers: CollectionRetainedLedgerService = CollectionRetainedLedgerService(scope, archives),
    private val registrationNumbers: CollectionRegistrationNumberService = CollectionRegistrationNumberService(),
) {
    fun requireAccess(entity: CollectionTransferDao, writable: Boolean = false, permission: String = CollectionConst.STORAGE) {
        if (writable) requireWrite(entity.orgId,permission)
        else {
            val ids=readableOrgIds()
            if (entity.orgId !in ids && entity.toOrgId !in ids) throw BadRequestException("无权查看该交换调拨申请")
        }
    }

    internal fun requireWrite(orgId: String, permission: String = CollectionConst.STORAGE) =
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(permission),"交换调拨申请")

    /** 在同一事务入口内转换为只读提示，预期的不可编辑不能把外层详情事务标为回滚。 */
    internal fun proposalEditable(entity: CollectionTransferDao): Boolean = try {
        requireWrite(entity.orgId)
        requireEditable(entity)
        true
    } catch (_: BadRequestException) { false }

    internal fun readableOrgIds(): Set<String> = CollectionSearchReadAccess.require(
        scope, additionalPermissions=setOf(CollectionConst.CROSS_ORG, CollectionConst.STORAGE, CollectionConst.LEDGER_CORRECT),
        allowHistoricalOrgs=true,
    ).orgIds

    fun scoped(id: String, writable: Boolean = false, permission: String = CollectionConst.STORAGE) = (CollectionTransferDao.findById(id) ?: throw BadRequestException("交换调拨申请不存在")).also { requireAccess(it,writable,permission) }

    /** 跨馆联系目录仅返回单位标识和名称；库位仅向该申请的双方开放。 */
    fun parties(): List<Map<String,String>> {
        readableOrgIds() // 联系目录仍可选择另一馆，但先核验当前账号和业务权限。
        return CollectionOrgDao.find { CollectionOrgs.orgStatus eq EnumOrgStatus.ENABLED }.map { mapOf("value" to it.id.value,"label" to it.name) }
    }
    fun receivingLocations(id: String): List<Map<String,String>> {
        val entity=scoped(id)
        return WarehouseDao.find { (Warehouses.orgId eq (entity.toOrgId ?: "")) and
            (Warehouses.sceneType eq EnumLocationScene.STORAGE) and (Warehouses.status eq EnumContentStatus.NORMAL) }
            .map { mapOf("value" to it.id.value,"label" to listOfNotNull(it.code,it.name).joinToString(" · ")) }
    }

    fun requireEditable(entity: CollectionTransferDao) {
        if (entity.flowStatus != EnumFlowStatus.DRAFT || entity.flowInstanceId != null || entity.executedAt != null || administrative(entity.id.value).isNotEmpty()) {
            throw BadRequestException("已提交或已有行政原件的方案永久保留，请另建核实申请")
        }
        // 交换整组共用合同；任一件开始正式办理后，各件范围、对象和责任人都不能换掉。
        val ids=entity.dossier?.exchangeTransferIds.orEmpty()
        if (ids.any { id -> id != entity.id.value && CollectionTransferDao.findById(id)?.let {
            it.flowInstanceId != null || administrative(id).isNotEmpty()
        } == true }) throw BadRequestException("同组交换已开始正式办理，不能修改共同交换方案")
    }

    fun requireBaseline(entity: CollectionTransferDao): CollectionDao {
        val collection=CollectionDao.findById(entity.collectionId) ?: throw BadRequestException("藏品不存在")
        if (collection.orgId != entity.orgId || entity.fromOrgId != entity.orgId || entity.baseline == null ||
            !CollectionCatalogMapper.matchesSnapshot(entity.baseline, collection.toModel())) throw BadRequestException("藏品数据已变化或旧申请缺少核实基线，请新建交换调拨申请")
        CollectionOutboundPolicy.requireInStore(collection.lifeStatus, "交换调拨", collection.inventoryHold)
        return collection
    }

    fun requireProposal(entity: CollectionTransferDao, checkExchange: Boolean = true): CollectionDao {
        val collection=requireBaseline(entity)
        val d=entity.dossier ?: throw BadRequestException("请补齐新办法交换调拨方案，旧流程记录不能直接过户")
        val source=CollectionOrgDao.findById(entity.orgId) ?: throw BadRequestException("源收藏单位不存在")
        val target=CollectionOrgDao.findById(entity.toOrgId ?: "") ?: throw BadRequestException("接收收藏单位不存在")
        if (source.orgStatus != EnumOrgStatus.ENABLED || target.orgStatus != EnumOrgStatus.ENABLED || source.id == target.id) throw BadRequestException("须由两个不同的已启用收藏单位办理")
        // 行政材料登记和提交前即核验原号；实际过户仍会再次检查，防止审批期间占号变化。
        registrationNumbers.requireTransferAvailable(target.id.value, collection.totalRegNo, collection.id.value)
        if (entity.reason.isNullOrBlank() || entity.createBy.isNullOrBlank()) throw BadRequestException("逐件转移事由和经办人必填")
        if (collection.catalogStatus != EnumCatalogStatus.APPROVED) throw BadRequestException("须先完成正式编目审核，再办理交换调拨")
        val archived=CollectionCatalogVersionDao.find { (CollectionCatalogVersions.collectionId eq collection.id.value) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.any()
        CollectionTransferCompliancePolicy.requireScope(d,source.ownership,target.ownership,collection.nature,collection.source ?: throw BadRequestException("请先核实藏品取得来源"),collection.registered,archived)
        if (entity.executedAt == null && listOf(d.transportStandardReference,d.transportStandardApplicability,d.transportCarrierName,
                d.transportCarrierCapabilityFileIds,d.transportPlanFileIds).any { it.isNullOrBlank() })
            throw BadRequestException("跨馆交接前须明确适用包装运输标准及依据、承运馆或机构资信能力和签署运输方案")
        archivePackets.requireEstablished(collection)
        CollectionInstitutionSuccessionPolicy.requireProposal(d,source.name,target.name,target.ownership,collection.nature!!,collection.id.value,collection.totalRegNo)
        expectedActors(entity).forEach { (node,actor) -> requireResponsibleActor(entity,node,actor) }
        val location=WarehouseDao.findById(d.targetWarehouseId) ?: throw BadRequestException("接收库位不存在")
        if (location.orgId != target.id.value || location.status != EnumContentStatus.NORMAL ||
            location.sceneType != EnumLocationScene.STORAGE) throw BadRequestException("接收库位须属于接收馆的有效保管空间")
        if (CollectionPlacementDao.find { (CollectionPlacements.collectionId eq collection.id.value) and (CollectionPlacements.approved eq true) and CollectionPlacements.validTo.isNull() }.any()) throw BadRequestException("藏品仍在已批准的展陈安排中，请先结束该安排并核实归库")
        val pending=setOf(EnumRfidTagJobStatus.CREATED,EnumRfidTagJobStatus.WRITE_PENDING,EnumRfidTagJobStatus.WRITING,EnumRfidTagJobStatus.VERIFY_PENDING,EnumRfidTagJobStatus.VERIFIED)
        if (RfidTagJobs.selectAll().where { (RfidTagJobs.collectionId eq collection.id.value) and (RfidTagJobs.jobStatus inList pending) }.any()) throw BadRequestException("请先完成或取消尚未结束的发标换标任务")
        allFiles(entity).split(',').filter(String::isNotBlank).forEach { binder.resolveSingle(it) }
        if (checkExchange && d.kind == EnumCollectionTransferKind.STATE_EXCHANGE) exchangeMembers(entity).filter { it.executedAt == null }.forEach { requireProposal(it,false) }
        return collection
    }

    fun exchangeMembers(entity: CollectionTransferDao): List<CollectionTransferDao> {
        val d=entity.dossier ?: throw BadRequestException("缺少交换方案")
        if (d.kind != EnumCollectionTransferKind.STATE_EXCHANGE) return listOf(entity)
        if (d.exchangeTransferIds.size < 2 || entity.id.value !in d.exchangeTransferIds) throw BadRequestException("同组交换申请须包含本件及对方申请")
        val members=d.exchangeTransferIds.sorted().map { CollectionTransferDao.findById(it) ?: throw BadRequestException("同组交换申请不存在") }
        val museums=setOf(entity.orgId,entity.toOrgId)
        if (members.map { it.collectionId }.toSet().size != members.size || members.none { it.orgId == entity.toOrgId }) throw BadRequestException("交换须包含双方不同藏品，不能重复关联同一藏品")
        if (members.any { member ->
            val other=member.dossier
            other == null || other.kind != EnumCollectionTransferKind.STATE_EXCHANGE ||
                other.exchangeTransferIds != d.exchangeTransferIds || setOf(member.orgId,member.toOrgId) != museums ||
                fileSet(other.exchangeAgreementFileIds) != fileSet(d.exchangeAgreementFileIds) ||
                member.flowStatus in setOf(EnumFlowStatus.REJECT,EnumFlowStatus.CANCEL)
        }) throw BadRequestException("交换整组须为同一对收藏单位、同一份共同协议且相互关联；终止的申请须另建后重新确认")
        return members
    }

    fun requirePrepared(entity: CollectionTransferDao) {
        val collection=requireProposal(entity)
        CollectionTransferCompliancePolicy.requireAdministrative(entity.dossier!!,CollectionOrgDao.findById(entity.orgId)!!.ownership!!,collection.level,administrative(entity.id.value))
    }

    fun recordAdministrative(id: String, vo: CollectionAdministrativeEvidenceVo): String {
        val entity=scoped(id,true)
        if (entity.flowStatus != EnumFlowStatus.DRAFT || entity.flowInstanceId != null) throw BadRequestException("只能在提交前登记行政原件")
        requireProposal(entity)
        DeregistrationCompliancePolicy.requireAdministrativeEvidence(vo)
        val content=vo.copy(fileIds=requiredFiles(vo.fileIds))
        archives.seal(entity.orgId,CollectionConst.BIZ_TRANSFER,id,"PROPOSAL",proposalSnapshot(entity),allFiles(entity))
        val row=CollectionAdministrativeRecordDao.new { orgId=entity.orgId; bizType=CollectionConst.BIZ_TRANSFER; bizId=id; this.content=content }
        archives.seal(entity.orgId,CollectionConst.BIZ_TRANSFER,id,"ADMIN_${row.id.value}",content,content.fileIds)
        // 执行延迟归档写入后再核对当前职责，避免使用写入前的人员及馆状态。
        TransactionManager.current().entityCache.flush()
        entity.refresh(flush=false)
        CollectionOrgDao.findById(entity.orgId)?.refresh(flush=false)
        requireAccess(entity,true)
        return row.id.value
    }

    data class Actor(val userId: String,val orgId: String,val role: String)
    private fun requireResponsibleActor(entity: CollectionTransferDao,node:String,actor:Actor) {
        if(node=="source_leader" && entity.dossier?.kind==EnumCollectionTransferKind.INSTITUTION_SUCCESSION) {
            val s=entity.dossier?.succession ?: throw BadRequestException("缺少终止合并依法授权人员依据")
            if(s.authorizedRepresentativeUserId!=actor.userId || s.representativeAuthorizationFileIds.isBlank())throw BadRequestException("须由本次清算或合并依法授权的责任人本人确认")
            users.requireMuseumMember(actor.userId,actor.orgId)
        } else users.requireRole(actor.userId,actor.orgId,actor.role)
    }
    fun expectedActors(entity: CollectionTransferDao): Map<String,Actor> {
        val d=entity.dossier ?: throw BadRequestException("缺少双方责任人")
        val target=entity.toOrgId ?: throw BadRequestException("接收收藏单位必填")
        return linkedMapOf(
            "source_keeper" to Actor(d.sourceKeeperUserId,entity.orgId,CollectionConst.ROLE_STORAGE_HEAD),
            "source_leader" to Actor(d.sourceLeaderUserId,entity.orgId,CollectionConst.ROLE_DIRECTOR),
            "target_keeper" to Actor(d.targetKeeperUserId,target,CollectionConst.ROLE_STORAGE_HEAD),
            "target_leader" to Actor(d.targetLeaderUserId,target,CollectionConst.ROLE_DIRECTOR),
        )
    }
    fun requireActor(entity: CollectionTransferDao,node: String?,operator: String?,comment: String?) {
        val actor=expectedActors(entity)[node] ?: return
        if (actor.userId != operator || comment.isNullOrBlank()) throw BadRequestException("双方指定责任人须本人填写确认意见，不得代办")
        requireResponsibleActor(entity,node!!,actor)
    }
    private fun currentApprovals(entity: CollectionTransferDao,instanceId: String): List<FlowInstanceLogDao> {
        val logs=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instanceId)
        val required=expectedActors(entity).map { (node,actor) ->
            logs.filter { it.nodeKey == node && it.operator == actor.userId && !it.comment.isNullOrBlank() && it.taskId != null }.lastOrNull()
                ?: throw BadRequestException("缺少本轮双方保管负责人确认和主要负责人批准，不能通过旧流程或跳节点过户")
        }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(required)
        return required
    }
    fun confirmApproval(entity: CollectionTransferDao,instanceId: String) {
        requirePrepared(entity)
        val approvals=currentApprovals(entity,instanceId)
        archives.seal(entity.orgId,CollectionConst.BIZ_TRANSFER,entity.id.value,"APPROVED_"+approvals.last().taskId!!.takeLast(40),mapOf("proposal" to proposalSnapshot(entity),"administrative" to administrative(entity.id.value),"approvals" to approvals.map { it.toVo() }),allFiles(entity))
    }
    fun requireCurrentApprovalEvidence(entity: CollectionTransferDao): LocalDateTime {
        val approvals=currentApprovals(entity,entity.flowInstanceId ?: throw BadRequestException("缺少审批实例"))
        val phase="APPROVED_"+approvals.last().taskId!!.takeLast(40)
        // 内部核验，访问权限由业务申请的双馆边界负责；不开放原馆其他永久档案。
        val archive=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionPermanentArchives.bizId eq entity.id.value) and (CollectionPermanentArchives.phase eq phase) }.singleOrNull() ?: throw BadRequestException("缺少本轮双馆批准的归档意见")
        val expected=mapOf("proposal" to proposalSnapshot(entity),"administrative" to administrative(entity.id.value),"approvals" to approvals.map { it.toVo() })
        if(archive.orgId!=entity.orgId || archive.digest!=CollectionCareEvidence.digest(archive.snapshot) || io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(archive.snapshot)!=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(expected)!!))throw BadRequestException("本轮双馆批准原件与当前逐件方案、行政依据或本人意见不一致")
        return approvals.last().createTime
    }
    fun requireExecution(entity: CollectionTransferDao,vo: CollectionTransferExecutionVo): CollectionTransferExecutionVo {
        requirePrepared(entity)
        val members=exchangeMembers(entity)
        members.forEach {
            if (it.flowStatus != EnumFlowStatus.PASS) throw BadRequestException("交换整组均批准后才能登记实际交接")
            if (it.executedAt == null) requirePrepared(it)
        }
        val approvedOn=members.maxOf { requireCurrentApprovalEvidence(it).toLocalDate() }
        CollectionTransferCompliancePolicy.requireExecution(vo,approvedOn)
        if (vo.appliedTransportStandardReference?.trim() != entity.dossier?.transportStandardReference?.trim())
            throw BadRequestException("实际采用的包装运输标准须与双方批准方案一致")
        CollectionInstitutionSuccessionPolicy.requireHandover(entity.dossier?.succession,vo.handedOn)
        users.requireRole(vo.sourceCustodianUserId,entity.orgId,CollectionConst.ROLE_KEEPER)
        users.requireRole(vo.targetCustodianUserId,entity.toOrgId!!,CollectionConst.ROLE_KEEPER)
        return vo.copy(signedHandoverFileIds=requiredFiles(vo.signedHandoverFileIds),conditionImageFileIds=requiredFiles(vo.conditionImageFileIds),signedLedgerChangeFileIds=requiredFiles(vo.signedLedgerChangeFileIds),receivingLedgerReceiptFileIds=requiredFiles(vo.receivingLedgerReceiptFileIds),packingCheckFileIds=requiredFiles(vo.packingCheckFileIds!!))
    }
    fun requireNoExecutedExchange(entity: CollectionTransferDao) {
        if (entity.executedAt != null || entity.dossier?.exchangeTransferIds.orEmpty().any { CollectionTransferDao.findById(it)?.executedAt != null }) throw BadRequestException("本件或同组交换已经实际交接，不能撤销或重新审批")
    }

    fun publicReviewOptions(id: String): List<Map<String, Any?>> {
        val entity = scoped(id, true)
        return disclosures.statutoryReviewOptions(entity.orgId, EnumDisclosureSubject.TRANSFER_RESULT, id)
    }
    fun recordNotice(id: String,vo: DeregistrationNoticeVo): String {
        disclosures.lockStatutorySubject(EnumDisclosureSubject.TRANSFER_RESULT, id)
        val entity=scoped(id,true,CollectionConst.LEDGER_CORRECT)
        if (entity.executedAt == null || entity.execution == null) throw BadRequestException("实际交接后才能登记结果公告")
        if (vo.kind != EnumExitNoticeKind.RESULT || vo.channel == EnumExitNoticeChannel.AUTHORITY_WEBSITE) throw BadRequestException("请登记博物馆现场或官网的转移退出结果公告")
        if (vo.startedOn.isBefore(entity.execution!!.handedOn) || vo.startedOn.isAfter(LocalDate.now()) || vo.urlOrLocation.isBlank()) throw BadRequestException("公告实发日期和地点或链接不正确")
        if (vo.channel == EnumExitNoticeChannel.MUSEUM_WEBSITE && runCatching { URI(vo.urlOrLocation).let { it.scheme in setOf("http","https") && !it.host.isNullOrBlank() } }.getOrDefault(false).not()) throw BadRequestException("官网公告须记录真实 http(s) 链接")
        val content=vo.copy(publicContentFileIds=requiredFiles(vo.publicContentFileIds),publicationEvidenceFileIds=requiredFiles(vo.publicationEvidenceFileIds))
        disclosures.requireStatutoryNotice(entity.orgId, EnumDisclosureSubject.TRANSFER_RESULT, id, content)
        val old=notices(id).filter { it.disclosureReviewId == content.disclosureReviewId }
        if (old.any { it.channel == vo.channel }) throw BadRequestException("该渠道公告已留存")
        if (old.any { fileSet(it.publicContentFileIds) != fileSet(content.publicContentFileIds) }) throw BadRequestException("现场与官网公告须对应同一份退出结果")
        val row=CollectionTransferNoticeDao.new { orgId=entity.orgId; transferId=id; this.content=content }
        archives.seal(entity.orgId,CollectionConst.BIZ_TRANSFER,id,"NOTICE_${row.id.value}",content,content.publicContentFileIds+","+content.publicationEvidenceFileIds)
        if (entity.announcedAt == null && notices(id).filter { it.disclosureReviewId == content.disclosureReviewId }.map { it.channel }.toSet().containsAll(setOf(EnumExitNoticeChannel.MUSEUM_ONSITE,EnumExitNoticeChannel.MUSEUM_WEBSITE))) {
            entity.announcedAt=LocalDateTime.now()
            archives.seal(entity.orgId,CollectionConst.BIZ_TRANSFER,id,"CLOSED",enrich(entity),allFiles(entity))
        }
        return row.id.value
    }
    fun administrative(id: String) = CollectionAdministrativeRecordDao.find { (CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_TRANSFER) and (CollectionAdministrativeRecords.bizId eq id) }.sortedBy { it.createTime }.map { it.content.copy(id=it.id.value) }
    fun notices(id: String) = CollectionTransferNoticeDao.find { CollectionTransferNotices.transferId eq id }.sortedBy { it.createTime }.map { it.content.copy(id=it.id.value) }
    fun summary(entity: CollectionTransferDao) = entity.toModel().copy(
        collectionTotalRegNo=if(entity.executedAt!=null)retainedLedgers.transferNumber(entity) else CollectionDao.findById(entity.collectionId)?.takeIf { it.orgId==entity.orgId }?.totalRegNo,
        terminationClosed=CollectionTransferTerminationEvidence.isClosed(entity.id.value),
        terminationPending=CollectionTransferTerminationEvidence.isPending(entity.id.value),
        currentNoticeComplete=if(entity.executedAt!=null)disclosures.currentResultNoticeComplete(entity.orgId,EnumDisclosureSubject.TRANSFER_RESULT,entity.id.value,notices(entity.id.value)) else false)
    fun enrich(entity: CollectionTransferDao) = summary(entity).copy(administrativeEvidence=administrative(entity.id.value),notices=notices(entity.id.value),collectionName=entity.baseline?.formalName)
    fun proposalSnapshot(entity: CollectionTransferDao) = mapOf("id" to entity.id.value,"collectionId" to entity.collectionId,"fromOrgId" to entity.orgId,"toOrgId" to entity.toOrgId,"reason" to entity.reason,"approvalDocNo" to entity.approvalDocNo,"attachmentIds" to entity.attachmentIds,"baseline" to entity.baseline,"dossier" to entity.dossier,"createBy" to entity.createBy)
    fun allFiles(entity: CollectionTransferDao): String {
        val d=entity.dossier
        return (listOfNotNull(entity.attachmentIds,d?.archiveHandoverFileIds,d?.exchangeAgreementFileIds,d?.donationAgreementFileIds,d?.donorNoticeFileIds,d?.donorWrittenConsentFileIds,d?.provincialJurisdictionFileIds,d?.legalBasisFileIds,d?.transportCarrierCapabilityFileIds,d?.transportPlanFileIds)+CollectionInstitutionSuccessionPolicy.files(d?.succession)).joinToString(",")
    }
    private fun requiredFiles(value: String): String = fileSet(value).map { binder.resolveSingle(it)!! }.joinToString(",").takeIf(String::isNotBlank) ?: throw BadRequestException("请上传真实原始凭据")
    private fun fileSet(value: String?) = value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
}
