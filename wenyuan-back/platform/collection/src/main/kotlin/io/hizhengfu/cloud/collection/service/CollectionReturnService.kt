package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionReturnDao
import io.hizhengfu.cloud.collection.dao.CollectionReturnVo
import io.hizhengfu.cloud.collection.dao.CollectionReturns
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.EnumReturnExecutionState
import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.RequisitionDao
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal data class CollectionReturnRecordValues(
    val sourceBizType: String,
    val sourceBizId: String,
    val sourceDocumentNo: String?,
    val orgId: String,
    val collectionIds: String,
    val reason: EnumMoveReason?,
    val basis: String?,
    val department: String?,
    val operator: String?,
    val reviewer: String?,
    val custodian: String?,
    val handoverUser: String?,
    val reviewOpinion: String?,
    val attachmentIds: String?,
    val toWarehouseId: String?,
    val expectedReturnDate: LocalDate?,
) {
    companion object {
        fun fromRequisition(vo: RequisitionVo) = CollectionReturnRecordValues(
            sourceBizType = CollectionConst.BIZ_REQUISITION,
            sourceBizId = vo.id ?: throw BadRequestException("提用单ID必填"),
            sourceDocumentNo = vo.documentNo,
            orgId = vo.orgId ?: throw BadRequestException("提用单收藏单位必填"),
            collectionIds = vo.collectionIds ?: throw BadRequestException("提用藏品必填"),
            reason = vo.purpose,
            basis = vo.basis,
            department = vo.department,
            operator = vo.operator,
            reviewer = vo.reviewer,
            custodian = vo.custodian,
            handoverUser = vo.handoverUser,
            reviewOpinion = vo.reviewOpinion,
            attachmentIds = vo.attachmentIds,
            toWarehouseId = vo.toWarehouseId,
            expectedReturnDate = vo.expectReturnDate,
        )

        fun fromLoan(vo: LoanVo) = CollectionReturnRecordValues(
            sourceBizType = CollectionConst.BIZ_LOAN,
            sourceBizId = vo.id ?: throw BadRequestException("借展单ID必填"),
            sourceDocumentNo = vo.protocolNo,
            orgId = vo.orgId ?: throw BadRequestException("借展单收藏单位必填"),
            collectionIds = vo.collectionIds ?: throw BadRequestException("借展藏品必填"),
            reason = EnumMoveReason.LOAN,
            basis = vo.approvalDocNo?.takeIf { it.isNotBlank() } ?: vo.protocolNo,
            department = vo.borrower,
            operator = vo.operator,
            reviewer = vo.reviewer,
            custodian = null,
            handoverUser = null,
            reviewOpinion = null,
            attachmentIds = vo.attachmentIds,
            toWarehouseId = null,
            expectedReturnDate = vo.dossier?.agreedReturnAt?.toLocalDate() ?: vo.returnDate,
        )
    }

    fun toVo(id: String? = null) = CollectionReturnVo(
        id = id,
        orgId = orgId,
        sourceBizType = sourceBizType,
        sourceBizId = sourceBizId,
        sourceDocumentNo = sourceDocumentNo,
        collectionIds = collectionIds,
        reason = reason,
        basis = basis,
        department = department,
        operator = operator,
        reviewer = reviewer,
        custodian = custodian,
        handoverUser = handoverUser,
        reviewOpinion = reviewOpinion,
        attachmentIds = attachmentIds,
        toWarehouseId = toWarehouseId,
        expectedReturnDate = expectedReturnDate,
    )
}

/**
 * 归还/返还流程。审批通过后进入待现场归还，RFID 最终确认后生成入库流水并回写来源单据。
 * 支持部分归库：collectionIds 为本次归还子集，一张来源单可拆多张归还单，全部归完才置来源单 returned。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class CollectionReturnService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val userResolver: CollectionUserResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val businessItemService: CollectionBusinessItemService,
    private val operationLockService: CollectionOperationLockService,
    private val placementService: CollectionPlacementService,
    private val statutory: ReturnStatutoryService,
    private val archives: CollectionPermanentArchiveService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<CollectionReturnDao>(CollectionReturnDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_RETURN
    override fun definitionKey() = CollectionConst.FLOW_RETURN
    override fun businessTypeName() = "归还管理"
    override fun todoListRoute() = "/collection/return"
    override fun authorizeUserOperation(entity: CollectionReturnDao, operation: FlowUserOperation) {
        if(operation==FlowUserOperation.FLOW_INFO) {
            CollectionCirculationReadAccess.require(scope).require(entity.orgId,CollectionConst.BIZ_RETURN,entity.id.value)
            CollectionCirculationReadIntegrity.requireReturnSource(entity)
        }
        else scope.requireFlowAccess(entity.orgId,operation,"归还单")
        if(operation==FlowUserOperation.SAVE_DRAFT)statutory.requireEditable(entity)
        if(entity.executionState==EnumReturnExecutionState.EXECUTED && operation in setOf(FlowUserOperation.CANCEL,FlowUserOperation.VOID))throw BadRequestException("已经实际接收保管的归还不能撤销、作废或删除，请处理异常并保留记录")
    }
    override fun buildTitle(entity: CollectionReturnDao) = "藏品归还：${entity.sourceDocumentNo ?: entity.sourceBizId}"
    override fun buildFormData(entity: CollectionReturnDao)=statutory.proposal(entity)
    override fun buildSelfSelectAssignees(entity: CollectionReturnDao)=mapOf("keeper" to listOf(entity.keeperUserId ?: throw BadRequestException("请指定保管负责人")))

    protected override fun acquireApprovalResources(entity: CollectionReturnDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, splitCollectionIds(entity.collectionIds))

    protected override fun releaseApprovalResources(entity: CollectionReturnDao) {
        if(entity.flowStatus==EnumFlowStatus.PASS)return
        operationLockService.release(businessType(),entity.id.value)
    }
    protected override fun beforeSubmit(entity: CollectionReturnDao) {
        if(entity.flowInstanceId!=null)throw BadRequestException("已启动归还应通过原流程处理，不能重复提交")
        statutory.requirePrepared(entity)
        statutory.requireSealedInspection(entity)
        validateSubsetStatus(entity.sourceBizType,splitCollectionIds(entity.collectionIds))
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_RETURN, entity.id.value)
        archives.seal(entity.orgId,businessType(),entity.id.value,"SUBMITTED",statutory.proposal(entity),statutory.allFiles(entity))
    }

    fun findPage(request: HttpServletRequest): GenericPage<CollectionReturnVo> {
        val page = GenericPage<CollectionReturnVo>().build(request)
        val access=CollectionCirculationReadAccess.require(scope)
        val q = CollectionReturns.selectAll()
        q.andWhere { CollectionReturns.orgId inList access.orgIds }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            val collectionMatch=access.matchingItems(CollectionReturns.orgId,CollectionReturns.id,CollectionConst.BIZ_RETURN,kw,true)
            q.andWhere {
                (CollectionReturns.sourceDocumentNo like "%$kw%") or
                    (CollectionReturns.sourceBizId like "%$kw%") or
                    collectionMatch or
                    (CollectionReturns.basis like "%$kw%") or
                    (CollectionReturns.department like "%$kw%") or
                    (CollectionReturns.operator like "%$kw%") or
                    (CollectionReturns.reviewer like "%$kw%")
            }
        }
        request.getParameter("sourceBizType")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { CollectionReturns.sourceBizType eq it } }
        request.getParameter("sourceBizId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { CollectionReturns.sourceBizId eq it } }
        request.getParameter("collectionId")?.trim()?.takeIf { it.isNotBlank() }?.let {
            q.andWhere { access.matchingItems(CollectionReturns.orgId,CollectionReturns.id,CollectionConst.BIZ_RETURN,it,false) }
        }
        request.getParameter("flowStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { io.hizhengfu.cloud.common.domain.EnumFlowStatus.valueOf(value) }.getOrNull()?.let { q.andWhere { CollectionReturns.flowStatus eq it } }
        }
        request.getParameter("executionState")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumReturnExecutionState.valueOf(value) }.getOrNull()?.let { q.andWhere { CollectionReturns.executionState eq it } }
        }
        request.getParameter("hasAttachment")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            when (value.lowercase()) {
                "true" -> q.andWhere { CollectionReturns.attachmentIds.isNotNull() and CollectionReturns.attachmentIds.neq("") }
                "false" -> q.andWhere { CollectionReturns.attachmentIds.isNull() or CollectionReturns.attachmentIds.eq("") }
            }
        }
        request.getParameter("expectedReturnDateStart")?.let { parseDate(it) }?.let { q.andWhere { CollectionReturns.expectedReturnDate greaterEq it } }
        request.getParameter("expectedReturnDateEnd")?.let { parseDate(it) }?.let { q.andWhere { CollectionReturns.expectedReturnDate lessEq it } }
        q.orderBy(CollectionReturns.expectedReturnDate to SortOrder.ASC, CollectionReturns.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<CollectionReturnDao>)
        page.list = daoPage.data?.map { access.require(it.orgId,CollectionConst.BIZ_RETURN,it.id.value); CollectionCirculationReadIntegrity.requireReturnSource(it); it.toModel() }?.let { enrich(it,access) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<CollectionReturnVo>
    }

    fun get(id:String):CollectionReturnVo? {
        val access=CollectionCirculationReadAccess.require(scope)
        val row=CollectionReturnDao.findById(id) ?: return null
        access.require(row.orgId,businessType(),id)
        CollectionCirculationReadIntegrity.requireReturnSource(row)
        val inspected=archives.readForDisplay(businessType(),id,row.orgId)
        return enrich(listOf(row.toModel().copy(complianceRecords=statutory.records(id),
            archives=inspected.archives,evidenceErrors=inspected.issues,
            pendingRequirements=if(inspected.issues.isEmpty()) statutory.pendingRequirements(row) else emptyList())),access).single()
    }

    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        val access=CollectionCirculationReadAccess.require(scope)
        val row=CollectionReturnDao.findById(id) ?: throw BadRequestException("归还案卷不存在")
        access.require(row.orgId,businessType(),id)
        CollectionCirculationReadIntegrity.requireReturnSource(row)
        CollectionCirculationArchivedOriginal.require(listOf(archives.archiveForAuthorizedOrg(businessType(),id,row.orgId,archiveId)),archiveId,fileId)
    }

    @CacheEvict(allEntries = true)
    fun save(vo: CollectionReturnVo): String {
        val sourceValues = sourceValues(vo.sourceBizType, vo.sourceBizId)
        CollectionLocationMutation.acquire(sourceValues.orgId)
        val targetWarehouseId = CollectionLocationMutation.storageReference(sourceValues.orgId, vo.toWarehouseId ?: sourceValues.toWarehouseId)
        val subset = resolveSubset(sourceValues, splitCollectionIds(vo.collectionIds), excludeReturnId = vo.id)
        validateSubsetStatus(sourceValues.sourceBizType, subset)
        val entity = saveDraft(vo.id, "归还单不存在", createEntity = {
            dao.new {
                orgId = sourceValues.orgId
                executionState = EnumReturnExecutionState.PENDING
                sourceBizType=sourceValues.sourceBizType;sourceBizId=sourceValues.sourceBizId;collectionIds=subset.joinToString(",");createBy=SecurityUtils.getUserId();createTime=LocalDateTime.now()
            }
        }) {
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "归还单")
            if(orgId!=sourceValues.orgId || (sourceBizId.isNotBlank() && (sourceBizId!=sourceValues.sourceBizId || sourceBizType!=sourceValues.sourceBizType)))throw BadRequestException("归还单不能改换所属馆或来源")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_RETURN, id.value)
            sourceBizType = sourceValues.sourceBizType
            sourceBizId = sourceValues.sourceBizId
            sourceDocumentNo = sourceValues.sourceDocumentNo
            collectionIds = subset.joinToString(",")
            reason = sourceValues.reason
            basis = vo.basis ?: sourceValues.basis
            department = vo.department ?: sourceValues.department
            operator = vo.operator ?: sourceValues.operator
            reviewer = vo.reviewer ?: sourceValues.reviewer
            custodian = vo.custodian ?: sourceValues.custodian
            handoverUser = vo.handoverUser ?: sourceValues.handoverUser
            reviewOpinion = vo.reviewOpinion ?: sourceValues.reviewOpinion
            attachmentIds = vo.attachmentIds ?: sourceValues.attachmentIds
            toWarehouseId = targetWarehouseId
            expectedReturnDate = sourceValues.expectedReturnDate
            returnDate = vo.returnDate
            damageNote = vo.damageNote
            damagePhotoIds = vo.damagePhotoIds
            signedVoucherAttachmentIds = vo.signedVoucherAttachmentIds
            sourceSnapshot=statutory.source(this)
            originalFiles?.rememberExistingBusiness(sourceValues.sourceBizType, sourceValues.sourceBizId)
            keeperUserId=vo.keeperUserId ?: sourceSnapshot!!.keeperUserId
        }
        originalFiles?.remember(CollectionConst.BIZ_RETURN, entity.id.value)
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "归还验收附件",
            listOfNotNull(entity.attachmentIds, entity.damagePhotoIds, entity.signedVoucherAttachmentIds).joinToString(",")).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_RETURN, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        businessItemService.bind(entity.orgId, CollectionConst.BIZ_RETURN, entity.id.value, entity.collectionIds)
        return entity.id.value
    }

    @CacheEvict(allEntries = true)
    fun ensureDraftFromRequisition(id: String): String {
        val source = RequisitionDao.findById(id) ?: throw BadRequestException("提用单不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), source.orgId, "提用单")
        return ensureDraft(CollectionReturnRecordValues.fromRequisition(source.toModel()))
    }

    @CacheEvict(allEntries = true)
    fun ensureDraftFromLoan(id: String): String {
        val source = LoanDao.findById(id) ?: throw BadRequestException("借展单不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), source.orgId, "借展单")
        return ensureDraft(CollectionReturnRecordValues.fromLoan(source.toModel()).copy(
                    expectedReturnDate = LoanOverseasExtensionEvidence.effectiveReturnAt(source)?.toLocalDate() ?: source.returnDate,
                ))
    }

    private fun ensureDraft(values: CollectionReturnRecordValues): String {
        openReturns(values.sourceBizType, values.sourceBizId).firstOrNull()?.let { return it.id.value }
        val received=CollectionReturnDao.find { (CollectionReturns.sourceBizType eq values.sourceBizType) and (CollectionReturns.sourceBizId eq values.sourceBizId) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED) }.toList()
        if(received.flatMap { splitCollectionIds(it.collectionIds) }.containsAll(splitCollectionIds(values.collectionIds))) {
            received.firstOrNull { it.inspection!=null && splitCollectionIds(it.acceptedCollectionIds).size<splitCollectionIds(it.collectionIds).size }?.let { return it.id.value }
        }
        return save(values.toVo().copy(collectionIds = ""))
    }

    /** 本单可归子集 = 来源全量 − 已归明细 − 其他进行中归还单占用；未显式指定时默认取全部可归。 */
    private fun resolveSubset(
        values: CollectionReturnRecordValues,
        requested: List<String>,
        excludeReturnId: String?,
    ): List<String> {
        requireSourceReturnable(values)
        val sourceItems = splitCollectionIds(values.collectionIds)
        val received=CollectionReturnDao.find { (CollectionReturns.sourceBizType eq values.sourceBizType) and (CollectionReturns.sourceBizId eq values.sourceBizId) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED) }.filterNot { it.id.value==excludeReturnId }.flatMap { splitCollectionIds(it.collectionIds) }.toSet()
        val blocked = received + returnedIds(values.sourceBizType, values.sourceBizId, excludeReturnId) +
            heldByOpenReturns(values.sourceBizType, values.sourceBizId, excludeReturnId)
        val available = PartialReturnPolicy.outstanding(sourceItems, blocked)
        return PartialReturnPolicy.resolveSubset(requested, available)
    }

    private fun requireSourceReturnable(values: CollectionReturnRecordValues) {
        when (values.sourceBizType) {
            CollectionConst.BIZ_REQUISITION -> {
                val source = RequisitionDao.findById(values.sourceBizId) ?: throw BadRequestException("提用单不存在")
                if(!source.outExecuted)throw BadRequestException("来源尚未实际出库，不能办理归还")
                ReturnClosurePolicy.requireReturnAllowed(source.flowStatus, source.returned)
            }
            CollectionConst.BIZ_LOAN -> {
                val source = LoanDao.findById(values.sourceBizId) ?: throw BadRequestException("借展单不存在")
                if(!source.outExecuted)throw BadRequestException("来源尚未实际出库，不能办理归还")
                ReturnClosurePolicy.requireReturnAllowed(source.flowStatus, source.returned)
            }
            else -> throw BadRequestException("不支持的归还来源：${values.sourceBizType}")
        }
    }

    private fun validateSubsetStatus(sourceBizType: String?, subset: List<String>) {
        val (expected, actionName) = when (sourceBizType) {
            CollectionConst.BIZ_REQUISITION -> EnumCollectionStatus.IN_USE to "提用归还"
            CollectionConst.BIZ_LOAN -> EnumCollectionStatus.ON_LOAN to "借展返还"
            else -> throw BadRequestException("不支持的归还来源：$sourceBizType")
        }
        subset.forEach { cid ->
            val coll = CollectionDao.findById(cid) ?: throw BadRequestException("藏品不存在：$cid")
            ReturnClosurePolicy.requireCollectionStatus(coll.lifeStatus, expected, actionName)
        }
    }

    private val openFlowStatuses = listOf(EnumFlowStatus.DRAFT, EnumFlowStatus.AUDIT, EnumFlowStatus.REJECT)

    private fun openReturns(sourceBizType: String, sourceBizId: String) =
        CollectionReturnDao.find {
            (CollectionReturns.sourceBizType eq sourceBizType) and
                (CollectionReturns.sourceBizId eq sourceBizId) and (
                    (CollectionReturns.flowStatus inList openFlowStatuses) or
                        ((CollectionReturns.flowStatus eq EnumFlowStatus.PASS) and
                            (CollectionReturns.executionState eq EnumReturnExecutionState.PENDING))
                    )
        }

    private fun returnedIds(sourceBizType: String, sourceBizId: String, excludeReturnId: String? = null): Set<String> =
        CollectionReturnDao.find {
            (CollectionReturns.sourceBizType eq sourceBizType) and
                (CollectionReturns.sourceBizId eq sourceBizId) and
                (CollectionReturns.flowStatus eq EnumFlowStatus.PASS) and
                (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED)
        }
            .filterNot { it.id.value == excludeReturnId }
            .flatMap { splitCollectionIds(it.acceptedCollectionIds ?: it.collectionIds) }
            .toSet()

    private fun heldByOpenReturns(sourceBizType: String, sourceBizId: String, excludeReturnId: String?): Set<String> =
        openReturns(sourceBizType, sourceBizId)
            .filterNot { it.id.value == excludeReturnId }
            .flatMap { splitCollectionIds(it.collectionIds) }
            .toSet()

    /** 已归明细（仅统计已经完成现场归库的归还单），供提用/借展列表展示"已归/未归"进度。 */
    fun returnedIdsBySource(sourceBizType: String, sourceBizIds: Collection<String>): Map<String, Set<String>> {
        if (sourceBizIds.isEmpty()) return emptyMap()
        return CollectionReturnDao.find {
            (CollectionReturns.sourceBizType eq sourceBizType) and
                (CollectionReturns.sourceBizId inList sourceBizIds) and
                (CollectionReturns.flowStatus eq EnumFlowStatus.PASS) and
                (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED)
        }.onEach {CollectionCirculationReadIntegrity.requireReturnSource(it)}
            .groupBy({ it.sourceBizId }, { splitCollectionIds(it.acceptedCollectionIds ?: it.collectionIds) })
            .mapValues { (_, lists) -> lists.flatten().toSet() }
    }

    /** 实物已接收但尚未通过真实性/状况核查的明细，不能混同未送回或已验收。 */
    fun heldIdsBySource(sourceBizType: String,sourceBizIds: Collection<String>): Map<String,Set<String>> {
        if(sourceBizIds.isEmpty())return emptyMap()
        return CollectionReturnDao.find { (CollectionReturns.sourceBizType eq sourceBizType) and (CollectionReturns.sourceBizId inList sourceBizIds) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED) }
            .onEach {CollectionCirculationReadIntegrity.requireReturnSource(it)}
            .groupBy({it.sourceBizId},{if(it.inspection==null)emptyList()else splitCollectionIds(it.collectionIds).filter { id -> id !in splitCollectionIds(it.acceptedCollectionIds) }})
            .mapValues { it.value.flatten().toSet() }
    }

    override fun onFlowTaskCompleted(entity: CollectionReturnDao,event: FlowTaskCompletedEvent) {
        if(event.action=="APPROVE")statutory.requireActor(entity,event.nodeKey,event.operator,event.comment)
    }
    override fun onFlowCompleted(entity: CollectionReturnDao, event: FlowCompletedEvent) {
        if(entity.flowInstanceId!=event.instanceId || entity.executionState==EnumReturnExecutionState.EXECUTED)throw BadRequestException("归还实例不一致或实物已接收")
        if(entity.flowStatus==EnumFlowStatus.PASS){statutory.requireApproved(entity);return}
        statutory.confirmApproval(entity)
        super.onFlowCompleted(entity,event)
        entity.executionState = EnumReturnExecutionState.PENDING
    }
    override fun beforeFlowReentry(entity: CollectionReturnDao, targetNodeKey: String) {
        if(entity.executionState==EnumReturnExecutionState.EXECUTED || targetNodeKey!="keeper")throw BadRequestException("实际接收后不能重新审批；待接收归还须从保管负责人重新确认")
        statutory.requirePrepared(entity)
        statutory.requireSealedInspection(entity)
    }
    override fun onFlowCanceled(entity: CollectionReturnDao,event: FlowCanceledEvent) {
        if(entity.executionState==EnumReturnExecutionState.EXECUTED)throw BadRequestException("实际接收后不能撤销")
        super.onFlowCanceled(entity,event)
    }
    override fun onFlowRejected(entity: CollectionReturnDao,event: FlowRejectedEvent) {
        if(entity.executionState==EnumReturnExecutionState.EXECUTED)throw BadRequestException("实际接收后不能驳回")
        super.onFlowRejected(entity,event)
    }

    @CacheEvict(allEntries = true)
    fun executeApprovedReturn(id: String): String {
        val entity=statutory.locked(id)
        CollectionCurrentOperationAccess.require(scope,entity.orgId,true,setOf(CollectionConst.STORAGE),"归还实际接收")
        if(entity.flowStatus!=EnumFlowStatus.PASS)throw BadRequestException("归还单确认通过后才能登记实际接收保管")
        if (entity.executionState == EnumReturnExecutionState.EXECUTED) {
            val original=CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq businessType()) and
                    (CollectionPermanentArchives.bizId eq id) and
                    (CollectionPermanentArchives.phase eq "RECEIVED")
            }.singleOrNull() ?: throw BadRequestException("已接收归还缺少实际接收永久原件，请核查原记录")
            val acceptedAtReceipt=entity.inspection?.items?.filterNot { it.abnormal() }?.map { it.collectionId }?.toSet()
                ?: throw BadRequestException("已接收归还缺少逐件验收记录")
            if (splitCollectionIds(entity.acceptedCollectionIds).toSet()!=statutory.acceptedIds(entity))
                throw BadRequestException("归还验收状态与处理原件不一致，请核查原记录")
            archives.verifyExisting(original,entity.orgId,businessType(),id,"RECEIVED",
                statutory.proposal(entity)+mapOf("acceptedCollectionIds" to acceptedAtReceipt.joinToString(",")),statutory.allFiles(entity))
            return id
        }
        executeReturn(entity)
        entity.executionState=EnumReturnExecutionState.EXECUTED
        entity.executedAt=entity.inspection!!.inspectedAt
        entity.executedBy=SecurityUtils.getUserId()
        entity.updateTime=LocalDateTime.now();entity.updateBy=SecurityUtils.getUserId()
        updateAcceptance(entity)
        archives.seal(entity.orgId,businessType(),id,"RECEIVED",statutory.proposal(entity)+mapOf("acceptedCollectionIds" to entity.acceptedCollectionIds),statutory.allFiles(entity))
        return id
    }
    private fun executeReturn(entity: CollectionReturnDao) {
        CollectionLocationMutation.storageReference(entity.orgId, entity.toWarehouseId)
        lockSource(entity)
        val inspection=statutory.requireApproved(entity)
        val batch=splitCollectionIds(entity.collectionIds)
        validateSubsetStatus(entity.sourceBizType,batch)
        batch.forEach { operationLockService.requireUnlocked(it,businessType(),entity.id.value) }
        inspection.items.forEach { item ->
            val coll=CollectionDao[item.collectionId]
            if(coll.orgId!=entity.orgId)throw BadRequestException("归还藏品所属馆不一致")
            val movement=StorageMovementDao.new {
                orgId=entity.orgId;documentNo=entity.sourceDocumentNo;collectionId=item.collectionId;type=EnumMoveType.IN
                totalRegNoAtMovement=coll.totalRegNo;collectionNameAtMovement=coll.name
                reason=entity.reason;basis=entity.basis;department=entity.department;toWarehouseId=entity.toWarehouseId
                operator=entity.operator;reviewer=entity.keeperUserId;custodian=inspection.custodianUserIds.first()
                handoverUser=if(entity.sourceBizType==CollectionConst.BIZ_LOAN)inspection.returningPersonNames.first()else inspection.returningUserIds.first()
                reviewOpinion=(if(item.abnormal())"归还待核查："else "核验一致：")+item.condition.take(470)
                attachmentIds=statutory.allFiles(entity);refBizId=entity.id.value;operatedAt=inspection.inspectedAt
            }
            CollectionMovementFacts.capture(movement,archives)
            BusinessAttachmentBindingPlanner.plan(movement.id.value, "归还逐件验收及出库对照原件", movement.attachmentIds).forEach { file ->
                if (originalFiles?.bindMovementReference(CollectionConst.BIZ_RETURN, entity.id.value, movement.id.value, file.fileId, file.aliasName) != true)
                    attachmentBinder.bindSingle(movement.id.value, file.aliasName, file.fileId)
            }
            collectionService.changeLifeStatus(item.collectionId,if(item.abnormal())EnumCollectionStatus.RETURN_HOLD else EnumCollectionStatus.IN_STORE,sourceBizType=businessType(),sourceBizId=entity.id.value)
            CollectionLedgerFieldChangeRecorder.warehouse(coll,entity.toWarehouseId,"归还实际接收库位",entity.id.value)
        }
        if(entity.sourceBizType==CollectionConst.BIZ_REQUISITION) {
            val source=RequisitionDao[entity.sourceBizId]
            if(source.sourceBizType==CollectionConst.BIZ_EXHIBITION && !source.sourceBizId.isNullOrBlank())placementService.closeExhibitionPlacements(source.sourceBizId!!,batch,inspection.inspectedAt)
        }
    }
    @CacheEvict(allEntries = true)
    fun resolveIssue(id: String,vo: ReturnResolutionVo,expectedOrgId:String?=null): String {
        val entity=CollectionReturnDao.find {CollectionReturns.id eq id}.forUpdate().singleOrNull() ?: throw BadRequestException("归还单不存在")
        requireResolutionAccess(entity,expectedOrgId);lockSource(entity)
        statutory.recordResolution(entity,vo)
        if(vo.collectionId !in splitCollectionIds(entity.acceptedCollectionIds)) {
            val coll=CollectionDao[vo.collectionId]
            operationLockService.requireUnlocked(vo.collectionId,businessType(),id)
            if(coll.orgId!=entity.orgId || coll.lifeStatus!=EnumCollectionStatus.RETURN_HOLD)throw BadRequestException("待核查藏品状态或所属馆发生变化，请核对保管记录")
            collectionService.changeLifeStatus(vo.collectionId,EnumCollectionStatus.IN_STORE,sourceBizType=businessType(),sourceBizId=id)
        }
        updateAcceptance(entity)
        statutory.requireResolutionSigners(entity,vo)
        requireResolutionAccess(entity,expectedOrgId)
        return id
    }
    private fun requireResolutionAccess(e:CollectionReturnDao,expectedOrgId:String?) {
        if(expectedOrgId!=null && (expectedOrgId.isBlank() || expectedOrgId!=e.orgId))throw BadRequestException("归还单所属馆与打开表单时不一致，请重新读取")
        CollectionCurrentOperationAccess.require(scope,e.orgId,true,setOf(CollectionConst.STORAGE),"归还异常处理")
    }
    private fun lockSource(e: CollectionReturnDao) {
        when(e.sourceBizType) {
            CollectionConst.BIZ_LOAN -> LoanDao.find { Loans.id eq e.sourceBizId }.forUpdate().singleOrNull() ?: throw BadRequestException("借用来源不存在")
            CollectionConst.BIZ_REQUISITION -> RequisitionDao.find { Requisitions.id eq e.sourceBizId }.forUpdate().singleOrNull() ?: throw BadRequestException("提用来源不存在")
            else -> throw BadRequestException("归还来源不支持")
        }
    }
    private fun updateAcceptance(e: CollectionReturnDao) {
        val accepted=statutory.acceptedIds(e)
        e.acceptedCollectionIds=accepted.joinToString(",");e.flush()
        val held=splitCollectionIds(e.collectionIds).filter { it !in accepted }
        if(held.isEmpty())operationLockService.release(businessType(),e.id.value)else operationLockService.acquire(e.orgId,businessType(),e.id.value,held)
        val all=returnedIds(e.sourceBizType,e.sourceBizId)
        val receivedAt=CollectionReturnDao.find { (CollectionReturns.sourceBizType eq e.sourceBizType) and (CollectionReturns.sourceBizId eq e.sourceBizId) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED) }.mapNotNull { it.returnDate }.maxOrNull() ?: e.returnDate
        when(e.sourceBizType) {
            CollectionConst.BIZ_LOAN -> { val source=LoanDao[e.sourceBizId];if(all.containsAll(splitCollectionIds(source.collectionIds))){source.returned=true;source.returnDate=receivedAt} }
            CollectionConst.BIZ_REQUISITION -> { val source=RequisitionDao[e.sourceBizId];if(all.containsAll(splitCollectionIds(source.collectionIds))){source.returned=true;source.returnDate=receivedAt} }
        }
    }

    private fun sourceValues(sourceBizType: String?, sourceBizId: String?): CollectionReturnRecordValues {
        val sourceType = sourceBizType?.takeIf { it.isNotBlank() } ?: throw BadRequestException("来源业务类型必填")
        val sourceId = sourceBizId?.takeIf { it.isNotBlank() } ?: throw BadRequestException("来源业务ID必填")
        return when (sourceType) {
            CollectionConst.BIZ_REQUISITION -> {
                val source = RequisitionDao.findById(sourceId) ?: throw BadRequestException("提用单不存在")
                CollectionReturnRecordValues.fromRequisition(source.toModel())
            }
            CollectionConst.BIZ_LOAN -> {
                val source = LoanDao.findById(sourceId) ?: throw BadRequestException("借展单不存在")
                CollectionReturnRecordValues.fromLoan(source.toModel()).copy(
                    expectedReturnDate = LoanOverseasExtensionEvidence.effectiveReturnAt(source)?.toLocalDate() ?: source.returnDate,
                )
            }
            else -> throw BadRequestException("不支持的归还来源：$sourceType")
        }
    }

    private fun enrich(items: List<CollectionReturnVo>,access:CollectionCirculationReadAccess): List<CollectionReturnVo> {
        val withSummaries = enrichCollectionSummaries(items,access)
        val names = userResolver.names(withSummaries.flatMap { listOf(it.operator, it.reviewer) })
        withSummaries.forEach {
            it.operatorName = it.operator?.let { u -> names[u] }
            it.reviewerName = it.reviewer?.let { u -> names[u] }
        }
        return withSummaries
    }

    private fun enrichCollectionSummaries(items:List<CollectionReturnVo>,access:CollectionCirculationReadAccess):List<CollectionReturnVo> {
        val summaries=access.summaries(items.flatMap {splitCollectionIds(it.collectionIds)}.toSet())
        return items.map {item -> item.copy(collectionSummaries=splitCollectionIds(item.collectionIds).mapNotNull {summaries[item.orgId]?.get(it)})}
    }

    private fun parseDate(value: String?): LocalDate? = parseCollectionDateParam(value)
}
