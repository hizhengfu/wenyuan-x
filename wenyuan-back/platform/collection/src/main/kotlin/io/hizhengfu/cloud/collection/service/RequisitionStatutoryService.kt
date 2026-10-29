package io.hizhengfu.cloud.collection.service

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
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class RequisitionStatutoryService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val binder: BusinessAttachmentBinder,
    private val archives: CollectionPermanentArchiveService,
    private val restrictedOperations: CollectionRestrictedOperationService = CollectionRestrictedOperationService(),
) {
    fun requireEditable(entity: RequisitionDao) {
        RequisitionReplacementEvidence(archives).requireActive(entity)
        if (entity.flowStatus != EnumFlowStatus.DRAFT || entity.flowInstanceId != null || entity.outExecuted || entity.handover != null) throw BadRequestException("已提交提用方案和点交原件须永久保留，请新建申请后重新确认")
    }
    fun baseline(collections: List<CollectionDao>) = RequisitionBaselineVo(collections.map { RequisitionCollectionBaselineVo(it.id.value,CollectionCatalogMapper.fromCollection(it.toModel())) })
    fun requireBaseline(entity: RequisitionDao,allowedStatuses: Set<EnumCollectionStatus>): List<CollectionDao> {
        val ids=splitCollectionIds(entity.collectionIds)
        val recorded=entity.baseline?.items ?: throw BadRequestException("旧提用单没有核实藏品清单和著录基线，请重新建立申请")
        if (ids.isEmpty() || ids.toSet() != recorded.map { it.collectionId }.toSet() || recorded.size != ids.size) throw BadRequestException("提用清单与核实基线不一致")
        return ids.map { id ->
            val collection=CollectionDao.findById(id) ?: throw BadRequestException("提用藏品不存在：$id")
            if (collection.orgId != entity.orgId || !CollectionCatalogMapper.matchesSnapshot(recorded.single { it.collectionId == id }.content, collection.toModel())) throw BadRequestException("提用藏品所属馆或登记资料已变化，请重新核实申请")
            if (collection.inventoryHold && !restrictedOperations.permits(id,CollectionConst.BIZ_REQUISITION,entity.id.value))throw BadRequestException("藏品仍有清点异常，缺少本次指定送修的专项授权")
            if (collection.lifeStatus !in allowedStatuses) throw BadRequestException("藏品 ${collection.totalRegNo} 当前状态不允许本次提用办理")
            collection
        }
    }
    fun requirePrepared(entity: RequisitionDao,allowedStatuses: Set<EnumCollectionStatus> = setOf(EnumCollectionStatus.IN_STORE)): List<CollectionDao> {
        RequisitionReplacementEvidence(archives).requireActive(entity)
        val collections=requireBaseline(entity,allowedStatuses)
        val d=entity.dossier ?: throw BadRequestException("请补齐提用部门、使用方式、安全要求、出归库时间和三方责任人")
        if (listOf(d.departmentGroupId,d.departmentHeadUserId,d.keeperUserId,d.leaderUserId,d.useResponsibleUserId,d.usageMethod,d.purposeDescription,d.usageSite,d.safetyRequirements,d.safetyPlanFileIds).any(String::isBlank)) throw BadRequestException("提用方案的部门、责任人、用途、使用方式、地点和安全要求必填")
        if (entity.documentNo.isNullOrBlank() || entity.department.isNullOrBlank() || entity.basis.isNullOrBlank() || entity.operator.isNullOrBlank()) throw BadRequestException("提用凭证号、部门、经办人和提用依据必填")
        if (entity.purpose !in setOf(EnumMoveReason.RESEARCH,EnumMoveReason.EXHIBIT,EnumMoveReason.RESTORE,EnumMoveReason.DIGITIZE,EnumMoveReason.INVENTORY,EnumMoveReason.RELOCATION,EnumMoveReason.OTHER)) throw BadRequestException("馆内提用用于研究、展览、修复、数字采集、实物核查、库房搬迁或其他馆内事项；借用和调拨应办理相应业务")
        if (setOf(d.departmentHeadUserId,d.keeperUserId,d.leaderUserId).size != 3) throw BadRequestException("提用部门确认、保管部门确认、主要负责人批准须分别由对应责任人办理")
        if (!d.expectedReturnAt.isAfter(d.expectedOutAt)) throw BadRequestException("预计归库时间须晚于预计出库时间")
        if (entity.expectReturnDate != d.expectedReturnAt.toLocalDate()) throw BadRequestException("预计归还日期须与方案明确的归库时间一致")
        if (!entity.outExecuted) {
            if (d.transportRequired == null || d.transportDecisionReason.isNullOrBlank())
                throw BadRequestException("须明确馆内提用是否需要包装运输及判断依据")
            if (d.transportRequired && listOf(d.transportStandardReference,d.transportStandardApplicability,d.transportCarrierName,d.transportCarrierCapabilityFileIds,d.transportPlanFileIds).any { it.isNullOrBlank() })
                throw BadRequestException("需要包装运输的提用须明确适用标准版本、适用依据、承运方能力及签署方案")
        }
        users.requireDepartmentHead(d.departmentHeadUserId,entity.orgId,d.departmentGroupId)
        users.requireDepartmentMember(d.useResponsibleUserId,entity.orgId,d.departmentGroupId)
        users.requireRole(d.keeperUserId,entity.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(d.leaderUserId,entity.orgId,CollectionConst.ROLE_DIRECTOR)
        val returnLocation=WarehouseDao.findById(entity.toWarehouseId ?: "") ?: throw BadRequestException("请明确归库保管位置")
        if (returnLocation.orgId != entity.orgId ||
            returnLocation.status != io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL ||
            returnLocation.sceneType != EnumLocationScene.STORAGE) throw BadRequestException("归库位置须属于本馆有效保管空间")
        files(d.safetyPlanFileIds)
        files(d.transportCarrierCapabilityFileIds)
        files(d.transportPlanFileIds)
        files(entity.attachmentIds)
        return collections
    }
    fun actors(entity: RequisitionDao): Map<String,String> {
        val d=entity.dossier ?: throw BadRequestException("缺少提用三方责任人")
        return linkedMapOf("department" to d.departmentHeadUserId,"keeper" to d.keeperUserId,"leader" to d.leaderUserId)
    }
    fun requireActor(entity: RequisitionDao,node: String?,operator: String?,comment: String?) {
        val expected=actors(entity)[node] ?: return
        if (expected != operator || comment.isNullOrBlank()) throw BadRequestException("指定责任人须本人填写确认意见，不得代办")
        val d=entity.dossier!!
        when(node) {
            "department" -> users.requireDepartmentHead(expected,entity.orgId,d.departmentGroupId)
            "keeper" -> users.requireRole(expected,entity.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            "leader" -> users.requireRole(expected,entity.orgId,CollectionConst.ROLE_DIRECTOR)
        }
    }
    fun currentApprovals(entity: RequisitionDao): List<FlowInstanceLogDao> {
        val instance=entity.flowInstanceId ?: throw BadRequestException("缺少提用审批实例")
        val logs=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instance)
        val result=actors(entity).map { (node,user) -> logs.filter { it.nodeKey == node && it.operator == user && !it.comment.isNullOrBlank() && it.taskId != null }.lastOrNull()
            ?: throw BadRequestException("缺少本轮提用部门负责人、保管负责人及主要负责人本人依次确认，旧流程或跳节点不能代替") }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(result)
        return result
    }
    fun confirmApproval(entity: RequisitionDao) {
        requirePrepared(entity)
        val approvals=currentApprovals(entity)
        archives.seal(entity.orgId,CollectionConst.BIZ_REQUISITION,entity.id.value,approvalPhase(approvals.last().taskId!!),mapOf("proposal" to proposal(entity),"approvals" to approvals.map { it.toVo() }),allFiles(entity))
    }
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun requireCurrentApproval(entity: RequisitionDao): FlowInstanceLogDao {
        RequisitionReplacementEvidence(archives).requireActive(entity)
        val last=currentApprovals(entity).last()
        if (CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq entity.id.value) and (CollectionPermanentArchives.phase eq approvalPhase(last.taskId!!)) }.empty()) throw BadRequestException("缺少本轮提用批准档案")
        return last
    }
    fun recordHandover(id: String,vo: RequisitionHandoverVo): String {
        val entity=(RequisitionDao.find { Requisitions.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("提用单不存在"))
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),entity.orgId,"提用点交")
        if (entity.flowStatus != EnumFlowStatus.PASS || entity.outExecuted || entity.returned) throw BadRequestException("仅可为已批准、尚未执行出库且未归还的提用单登记实际点交原件")
        val approval=requireCurrentApproval(entity)
        val content=validateHandover(entity,vo,approval.createTime)
        val old=entity.handover
        if (old?.content == content && old.approvalTaskId == approval.taskId) return id
        if (old != null && content.replacementReason.isNullOrBlank()) throw BadRequestException("补充或更正已签署点交材料须说明原因；原始版本仍永久保存")
        val record=RequisitionHandoverRecordVo(content,approval.taskId!!,SecurityUtils.getUserId() ?: throw BadRequestException("请登录后登记"),LocalDateTime.now())
        archives.seal(entity.orgId,CollectionConst.BIZ_REQUISITION,id,"HANDOVER_${IdGenerate.nextId()}",record,handoverFiles(content))
        entity.handover=record
        return id
    }
    private fun validateHandover(entity: RequisitionDao,vo: RequisitionHandoverVo,approvedAt: LocalDateTime): RequisitionHandoverVo {
        requirePrepared(entity,setOf(EnumCollectionStatus.PRE_OUT))
        val d=entity.dossier!!
        if (vo.reviewedAt.isBefore(approvedAt) || vo.handedAt.isBefore(vo.reviewedAt) || vo.handedAt.isAfter(LocalDateTime.now()) || vo.handedAt.isBefore(d.expectedOutAt) || vo.handedAt.isAfter(d.expectedReturnAt)) throw BadRequestException("材料审查应晚于本轮批准，实际点交应在审查之后且处于批准使用时段内")
        if (vo.reviewerUserId != d.keeperUserId) throw BadRequestException("须由本单保管部门负责人查验提用凭证、清单及审批材料并指派保管人员")
        if (!vo.safetyMeasuresConfirmed || !vo.siteSafetyConfirmed) throw BadRequestException("未采取安全防护措施或存放场所不具备安全条件的，不得出库")
        if (d.transportRequired == true && (vo.appliedTransportStandardReference?.trim() != d.transportStandardReference?.trim() || vo.packingStandardConfirmed != true || vo.packingCheckFileIds.isNullOrBlank()))
            throw BadRequestException("提用实际包装运输须核对批准标准、逐件包装及签署核验原件")
        if (vo.custodianUserIds.isEmpty() || vo.receivingUserIds.isEmpty() || (vo.custodianUserIds + vo.receivingUserIds).any(String::isBlank) || vo.custodianUserIds.intersect(vo.receivingUserIds).isNotEmpty()) throw BadRequestException("保管人员和提用人员须由不同实际人员逐件点交并分别签署")
        vo.custodianUserIds.forEach { users.requireRole(it,entity.orgId,CollectionConst.ROLE_KEEPER) }
        vo.receivingUserIds.forEach { users.requireDepartmentMember(it,entity.orgId,d.departmentGroupId) }
        val ids=splitCollectionIds(entity.collectionIds)
        if (vo.items.size != ids.size || vo.items.map { it.collectionId }.toSet() != ids.toSet()) throw BadRequestException("应对清单内每件藏品逐件记录现状，不能缺件、重复或混入其他藏品")
        val items=vo.items.map { item ->
            if (item.condition.isBlank()) throw BadRequestException("每件藏品的具体保存现状必填")
            val images=files(item.imageFileIds)
            if (item.textNeedsImages && images.isNullOrBlank()) throw BadRequestException("文字不能充分说明的现状须补充影像")
            item.copy(imageFileIds=images)
        }
        return vo.copy(items=items,reviewAndAssignmentFileIds=requiredFiles(vo.reviewAndAssignmentFileIds),safetyCheckFileIds=requiredFiles(vo.safetyCheckFileIds),signedHandoverFileIds=requiredFiles(vo.signedHandoverFileIds),packingCheckFileIds=if(d.transportRequired == true)requiredFiles(vo.packingCheckFileIds.orEmpty())else files(vo.packingCheckFileIds))
    }
    fun requireHandover(entity: RequisitionDao): RequisitionHandoverVo {
        val approval=requireCurrentApproval(entity)
        val record=entity.handover ?: throw BadRequestException("请先登记保管负责人审查指派、逐件状况及双方签署点交原件，RFID 核验不能代替点交")
        if (record.approvalTaskId != approval.taskId) throw BadRequestException("点交记录不对应本轮批准，须重新核对并签署")
        return validateHandover(entity,record.content,approval.createTime)
    }
    fun proposal(entity: RequisitionDao): Map<String,Any?> = requisitionProposal(entity)
    fun allFiles(entity: RequisitionDao) = listOfNotNull(entity.attachmentIds,entity.dossier?.safetyPlanFileIds,entity.dossier?.transportCarrierCapabilityFileIds,entity.dossier?.transportPlanFileIds).joinToString(",")
    fun handoverFiles(vo: RequisitionHandoverVo) = (listOfNotNull(vo.reviewAndAssignmentFileIds,vo.safetyCheckFileIds,vo.signedHandoverFileIds,vo.packingCheckFileIds)+vo.items.mapNotNull { it.imageFileIds }).joinToString(",")
    private fun approvalPhase(taskId: String) = "APPROVED_"+taskId.takeLast(40)
    private fun requiredFiles(value: String) = files(value) ?: throw BadRequestException("真实签署原件必填")
    private fun files(value: String?) = value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).distinct().map { binder.resolveSingle(it)!! }.joinToString(",").takeIf(String::isNotBlank)
}

/** 同一原申请快照用于提交和承接核验，避免调用现任人员校验改写历史。 */
internal fun requisitionProposal(entity: RequisitionDao): Map<String, Any?> = mapOf("id" to entity.id.value,"collectionIds" to entity.collectionIds,"documentNo" to entity.documentNo,"sourceBizType" to entity.sourceBizType,"sourceBizId" to entity.sourceBizId,"purpose" to entity.purpose,"basis" to entity.basis,"department" to entity.department,"operator" to entity.operator,"reviewer" to entity.reviewer,"attachmentIds" to entity.attachmentIds,"toWarehouseId" to entity.toWarehouseId,"expectReturnDate" to entity.expectReturnDate,"dossier" to entity.dossier,"baseline" to entity.baseline,"createBy" to entity.createBy)
