package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.Loans
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchives
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.CollectionOutboundExecutionPolicy
import io.hizhengfu.cloud.collection.domain.CollectionOutboundExecutionVo
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
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

internal data class LoanRecordValues(
    val title: String?,
    val collectionIds: String?,
    val borrower: String?,
    val protocolNo: String?,
    val insurance: String?,
    val approveLevel: EnumApproveLevel?,
    val approvalDocNo: String?,
    val attachmentIds: String?,
    val operator: String?,
    val reviewer: String?,
    val loanDate: LocalDate?,
    val returnDate: LocalDate?,
) {
    companion object {
        fun fromSave(vo: LoanVo) = LoanRecordValues(
            title = vo.title,
            collectionIds = vo.collectionIds,
            borrower = vo.borrower,
            protocolNo = vo.protocolNo,
            insurance = vo.insurance,
            approveLevel = vo.approveLevel,
            approvalDocNo = vo.approvalDocNo,
            attachmentIds = vo.attachmentIds,
            operator = vo.operator,
            reviewer = vo.reviewer,
            loanDate = vo.loanDate,
            returnDate = vo.returnDate,
        )
    }
}

internal object LoanReviewPolicy {
    fun requireForLevels(levels: List<EnumRelicLevel?>, operator: String?, reviewer: String?) {
        if (levels.none { it == EnumRelicLevel.FIRST }) return
        val cleanOperator = operator?.takeIf { it.isNotBlank() } ?: throw BadRequestException("含一级文物，必须指定经办人")
        val cleanReviewer = reviewer?.takeIf { it.isNotBlank() } ?: throw BadRequestException("含一级文物，必须指定复核人")
        if (cleanReviewer == cleanOperator) throw BadRequestException("复核人不能与经办人相同")
    }


}

/** 借展/外展（流程驱动）。出馆审批通过→藏品借出；returnBack 为审批后返还动作。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class LoanService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val returnService: CollectionReturnService,
    private val businessItemService: CollectionBusinessItemService,
    private val operationLockService: CollectionOperationLockService,
    private val statutory: LoanStatutoryService,
    private val archives: CollectionPermanentArchiveService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<LoanDao>(LoanDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_LOAN
    override fun definitionKey() = CollectionConst.FLOW_LOAN
    override fun businessTypeName() = "借展外展"
    override fun todoListRoute() = "/collection/loan"
    override fun authorizeUserOperation(entity: LoanDao, operation: FlowUserOperation) {
        if(operation==FlowUserOperation.FLOW_INFO) CollectionCirculationReadAccess.require(scope).require(entity.orgId,CollectionConst.BIZ_LOAN,entity.id.value)
        else requireCurrentWrite(entity.orgId)
        if(operation==FlowUserOperation.SAVE_DRAFT)statutory.requireEditable(entity)
        if(operation in setOf(FlowUserOperation.CANCEL,FlowUserOperation.VOID) && entity.outExecuted)throw BadRequestException("已实际出借不能撤销或作废，应办理归还")
    }
    private fun requireCurrentWrite(orgId: String) =
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.STORAGE),"借用办理")

    override fun userOperationVerifier(entity: LoanDao, operation: FlowUserOperation): () -> Unit = {
        if (operation == FlowUserOperation.FLOW_INFO)
            CollectionCirculationReadAccess.require(scope).require(entity.orgId,CollectionConst.BIZ_LOAN,entity.id.value)
        else requireCurrentWrite(entity.orgId)
    }

    override fun buildTitle(entity: LoanDao) = "藏品借用：${entity.title ?: entity.borrower ?: ""}"
    override fun buildFormData(entity: LoanDao) = statutory.proposal(entity)+mapOf("administrativeEvidence" to statutory.administrativeViews(entity.id.value))
    override fun buildSelfSelectAssignees(entity: LoanDao)=statutory.actors(entity).mapValues { listOf(it.value) }
    override fun beforeSubmit(entity: LoanDao) {
        if(entity.flowInstanceId!=null)throw BadRequestException("已提交申请须保留，请撤回后另建核实方案")
        statutory.requirePrepared(entity);statutory.requireAdministrativeBeforeOut(entity)
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_LOAN, entity.id.value)
        archives.seal(entity.orgId,businessType(),entity.id.value,"SUBMITTED",buildFormData(entity),statutory.allFiles(entity))
    }

    protected override fun acquireApprovalResources(entity: LoanDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, splitCollectionIds(entity.collectionIds))

    protected override fun releaseApprovalResources(entity: LoanDao) {
        if(entity.flowStatus==EnumFlowStatus.PASS && !entity.outExecuted)return
        operationLockService.release(businessType(),entity.id.value)
    }

    fun findPage(request: HttpServletRequest): GenericPage<LoanVo> {
        val page = GenericPage<LoanVo>().build(request)
        val access=CollectionCirculationReadAccess.require(scope)
        val q = Loans.selectAll()
        q.andWhere { Loans.orgId inList access.orgIds }
        val filters = LoanQueryFilters.from { request.getParameter(it) }
        filters.keyword?.let { kw ->
            val collectionMatch=access.matchingItems(Loans.orgId,Loans.id,CollectionConst.BIZ_LOAN,kw,true)
            q.andWhere {
                (Loans.title like "%$kw%") or
                    collectionMatch or
                    (Loans.borrower like "%$kw%") or
                    (Loans.protocolNo like "%$kw%") or
                    (Loans.approvalDocNo like "%$kw%") or
                    (Loans.insurance like "%$kw%") or
                    (Loans.operator like "%$kw%") or
                    (Loans.reviewer like "%$kw%") or
                    (Loans.attachmentIds like "%$kw%")
            }
        }
        filters.title?.let { q.andWhere { Loans.title like "%$it%" } }
        filters.collectionId?.let {
            q.andWhere { access.matchingItems(Loans.orgId,Loans.id,CollectionConst.BIZ_LOAN,it,false) }
        }
        filters.borrower?.let { q.andWhere { Loans.borrower like "%$it%" } }
        filters.protocolNo?.let { q.andWhere { Loans.protocolNo like "%$it%" } }
        filters.approveLevel?.let { q.andWhere { Loans.approveLevel eq it } }
        filters.approvalDocNo?.let { q.andWhere { Loans.approvalDocNo like "%$it%" } }
        filters.operator?.let { q.andWhere { Loans.operator eq it } }
        filters.reviewer?.let { q.andWhere { Loans.reviewer eq it } }
        filters.flowStatus?.let { q.andWhere { Loans.flowStatus eq it } }
        filters.returned?.let { q.andWhere { Loans.returned eq it } }
        filters.hasAttachment?.let { has ->
            if (has) q.andWhere { Loans.attachmentIds.isNotNull() and Loans.attachmentIds.neq("") }
            else q.andWhere { Loans.attachmentIds.isNull() or Loans.attachmentIds.eq("") }
        }
        filters.loanDateStart?.let { q.andWhere { Loans.loanDate greaterEq it } }
        filters.loanDateEnd?.let { q.andWhere { Loans.loanDate lessEq it } }
        q.orderBy(Loans.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<LoanDao>)
        page.list = daoPage.data?.map { access.require(it.orgId,CollectionConst.BIZ_LOAN,it.id.value); it.toModel().copy(obligations=statutory.obligations(it)) }?.let { enrichCollectionSummaries(it,access) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<LoanVo>
    }

    fun get(id:String):LoanVo? {
        val access=CollectionCirculationReadAccess.require(scope)
        val row=LoanDao.findById(id) ?: return null
        access.require(row.orgId,businessType(),id)
        val inspected=archives.inspectForAuthorizedOrg(businessType(),id,row.orgId)
        if(inspected.issues.isNotEmpty()) {
            // 保存的业务内容仍可查阅；原件异常时不输出依赖完整原件的履行结论或办理能力。
            return enrichCollectionSummaries(listOf(row.toModel().copy(
                administrativeEvidence=statutory.administrativeViews(id),monitoringRecords=statutory.monitoringViews(id),
                archives=inspected.archives,evidenceErrors=inspected.issues,
                obligations=mapOf("evidenceUnavailable" to true),proposalEditable=false)),access).single()
        }
        return enrichCollectionSummaries(listOf(row.toModel().copy(administrativeEvidence=statutory.administrativeViews(id),
            monitoringRecords=statutory.monitoringViews(id),obligations=statutory.obligations(row),
            exhibitionEnds=statutory.exhibitionEndViews(row),exhibitionEndAvailable=statutory.exhibitionEndAvailable(row),overseasFilings=statutory.overseasFilingViews(row),overseasFilingAvailable=statutory.overseasFilingAvailable(row),overseasPorts=statutory.portViews(row),portAvailable=statutory.portAvailable(row),overseasExtensions=statutory.extensionViews(row),extensionSourceRevision=statutory.extensionRevision(row),extensionAvailable=statutory.extensionAvailable(row),
            archives=inspected.archives,proposalEditable=proposalEditable(row))),access).single()
    }

    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        val access=CollectionCirculationReadAccess.require(scope)
        val row=LoanDao.findById(id) ?: throw BadRequestException("借用案卷不存在")
        access.require(row.orgId,businessType(),id)
        CollectionCirculationArchivedOriginal.require(listOf(archives.archiveForAuthorizedOrg(businessType(),id,row.orgId,archiveId)),archiveId,fileId)
    }

    private fun proposalEditable(row: LoanDao): Boolean = try {
        requireCurrentWrite(row.orgId)
        statutory.editable(row)
    } catch (_: BadRequestException) {
        false
    }

    @CacheEvict(allEntries = true)
    fun save(vo: LoanVo): String {
        val values = LoanRecordValues.fromSave(vo)
        val ids = splitIds(values.collectionIds)
        if (ids.isEmpty()) throw BadRequestException("请选择借出藏品")
        val colls = ids.map { id -> CollectionDao.findById(id) ?: throw BadRequestException("藏品不存在：$id") }
        if(colls.map { it.orgId }.distinct().size!=1)throw BadRequestException("一张借用单不能混合不同收藏单位的藏品")
        ids.forEach { operationLockService.requireUnlocked(it) }
        requireCurrentWrite(colls.first().orgId)
        colls.forEach { CollectionOutboundPolicy.requireInStore(it.lifeStatus, "借展", it.inventoryHold) }
        LoanReviewPolicy.requireForLevels(colls.map { it.level }, values.operator, values.reviewer)

        val entity = saveDraft(vo.id, "借展单不存在", createEntity = {
            dao.new { orgId = colls.first().orgId; collectionIds=values.collectionIds;createBy=SecurityUtils.getUserId();createTime=LocalDateTime.now() }
        }) {
            requireCurrentWrite(orgId)
            if(orgId!=colls.first().orgId)throw BadRequestException("借用单不能更换所属收藏单位")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_LOAN, id.value)
            dossier=vo.dossier;baseline=statutory.baseline(colls)
            title = values.title
            collectionIds = values.collectionIds
            borrower = values.borrower
            protocolNo = values.protocolNo
            insurance = values.insurance
            approveLevel = values.approveLevel
            approvalDocNo = values.approvalDocNo
            attachmentIds = values.attachmentIds
            operator = values.operator
            reviewer = values.reviewer
            loanDate = vo.dossier?.agreedOutAt?.toLocalDate() ?: values.loanDate
            returnDate = vo.dossier?.agreedReturnAt?.toLocalDate() ?: values.returnDate
        }
        businessItemService.bind(entity.orgId, CollectionConst.BIZ_LOAN, entity.id.value, entity.collectionIds)
        originalFiles?.remember(CollectionConst.BIZ_LOAN, entity.id.value)
        requireCurrentWrite(entity.orgId)
        return entity.id.value
    }

    override fun onFlowTaskCompleted(entity: LoanDao,event: FlowTaskCompletedEvent) {
        if(event.action=="APPROVE")statutory.requireActor(entity,event.nodeKey,event.operator,event.comment)
    }
    override fun onFlowCompleted(entity: LoanDao,event: FlowCompletedEvent) {
        if(entity.flowInstanceId!=event.instanceId || entity.outExecuted)throw BadRequestException("借用实例不一致或已实际出库")
        if(entity.flowStatus==EnumFlowStatus.PASS){statutory.requireCurrentApproval(entity);return}
        statutory.confirmApproval(entity)
        super.onFlowCompleted(entity,event)
        splitIds(entity.collectionIds).forEach { collectionService.changeLifeStatus(it,EnumCollectionStatus.PRE_OUT,sourceBizType=businessType(),sourceBizId=entity.id.value) }
    }
    override fun beforeFlowReentry(entity: LoanDao, targetNodeKey: String) {
        if(entity.outExecuted || targetNodeKey!="keeper")throw BadRequestException("未出库借用重新审批须从保管负责人开始，已出借应办理归还")
        statutory.requirePrepared(entity,setOf(EnumCollectionStatus.IN_STORE,EnumCollectionStatus.PRE_OUT))
        restorePending(entity)
    }
    override fun onFlowCanceled(entity: LoanDao,event: FlowCanceledEvent) {
        if(entity.outExecuted)throw BadRequestException("已出借不能撤销，请办理归还")
        restorePending(entity);super.onFlowCanceled(entity,event)
    }
    override fun onFlowRejected(entity: LoanDao,event: FlowRejectedEvent) {
        if(entity.outExecuted)throw BadRequestException("已出借不能改为驳回")
        restorePending(entity);super.onFlowRejected(entity,event)
    }
    private fun restorePending(entity: LoanDao) {
        splitIds(entity.collectionIds).forEach { id ->
            val c=CollectionDao.findById(id) ?: return@forEach
            if(c.orgId==entity.orgId && c.lifeStatus==EnumCollectionStatus.PRE_OUT){operationLockService.requireNoOtherApproval(id,businessType(),entity.id.value);collectionService.changeLifeStatus(id,EnumCollectionStatus.IN_STORE,sourceBizType=businessType(),sourceBizId=entity.id.value)}
        }
    }
    @CacheEvict(allEntries = true)
    fun executeOut(id: String,vo: CollectionOutboundExecutionVo): String {
        val entity=LoanDao.find { Loans.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("借用单不存在")
        requireCurrentWrite(entity.orgId)
        if(entity.flowStatus!=EnumFlowStatus.PASS)throw BadRequestException("仅本轮已批准借用可执行出库")
        if(entity.outExecuted) {
            val original=CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq businessType()) and
                    (CollectionPermanentArchives.bizId eq id) and
                    (CollectionPermanentArchives.phase eq "OUT_EXECUTED")
            }.singleOrNull() ?: throw BadRequestException("已出库借用缺少实际出库永久原件，请核查原记录")
            val handover=entity.handover?.content ?: throw BadRequestException("已出库借用缺少实际点交记录")
            val obligations=try { JsonUtils.mapper.readTree(original.snapshot).get("obligations") }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("借用出库永久原件无法解析") }
                ?: throw BadRequestException("借用出库永久原件缺少当时义务快照")
            val evidenceIds=listOfNotNull(statutory.allFiles(entity),statutory.handoverFiles(handover),vo.conditionPhotoIds,vo.signedVoucherAttachmentIds).joinToString(",")
            archives.verifyExisting(original,entity.orgId,businessType(),id,"OUT_EXECUTED",
                mapOf("request" to entity.toModel().copy(returnDate=entity.dossier?.agreedReturnAt?.toLocalDate(),returned=false),
                    "additionalVerification" to vo,"obligations" to obligations),evidenceIds)
            return id
        }
        val handover=statutory.requireHandover(entity)
        splitIds(entity.collectionIds).forEach { operationLockService.requireUnlocked(it,businessType(),id) }
        entity.conditionNote=handover.items.joinToString("；") { "${it.collectionId}：${it.condition}" }
        entity.conditionPhotoIds=handover.items.mapNotNull { it.imageFileIds }.joinToString(",").takeIf(String::isNotBlank)
        entity.signedVoucherAttachmentIds=handover.signedHandoverFileIds
        entity.outExecuted=true;entity.outExecutedTime=handover.handedAt;entity.flush()
        val evidenceIds=listOfNotNull(statutory.allFiles(entity),statutory.handoverFiles(handover),vo.conditionPhotoIds,vo.signedVoucherAttachmentIds).joinToString(",")
        handover.items.forEach { item ->
            val itemCollection=CollectionDao[item.collectionId]
            val movement=StorageMovementDao.new {
                orgId=entity.orgId;documentNo=entity.protocolNo;collectionId=item.collectionId;type=EnumMoveType.OUT;reason=EnumMoveReason.LOAN
                totalRegNoAtMovement=itemCollection.totalRegNo;collectionNameAtMovement=itemCollection.name
                basis=entity.protocolNo;department=entity.borrower;operator=SecurityUtils.getUserId();reviewer=handover.reviewerUserId
                custodian=handover.custodianUserIds.first();handoverUser=handover.receivingPersonNames.first()
                fromWarehouseId=itemCollection.warehouseId;operatedAt=handover.handedAt
                reviewOpinion=item.condition.take(500);attachmentIds=evidenceIds;refBizId=id
            }
            CollectionMovementFacts.capture(movement,archives)
            BusinessAttachmentBindingPlanner.plan(movement.id.value,"借用逐件点交原件",evidenceIds).forEach { file ->
                if (originalFiles?.bindMovementReference(CollectionConst.BIZ_LOAN,entity.id.value,movement.id.value,file.fileId,file.aliasName) != true)
                    attachmentBinder.bindSingle(movement.id.value,file.aliasName,file.fileId)
            }
            collectionService.changeLifeStatus(item.collectionId,EnumCollectionStatus.ON_LOAN,sourceBizType=businessType(),sourceBizId=id)
        }
        archives.seal(entity.orgId,businessType(),id,"OUT_EXECUTED",mapOf("request" to entity.toModel(),"additionalVerification" to vo,"obligations" to statutory.obligations(entity)),evidenceIds)
        operationLockService.release(businessType(),id)
        returnService.ensureDraftFromLoan(id)
        requireCurrentWrite(entity.orgId)
        return id
    }

    /** 兼容旧入口：创建或返回返还流程草稿，不再直接回库。 */
    @CacheEvict(allEntries = true)
    fun returnBack(id: String): String = returnService.ensureDraftFromLoan(id)

    private fun splitIds(s: String?): List<String> = splitCollectionIds(s)

    private fun enrichCollectionSummaries(items: List<LoanVo>,access:CollectionCirculationReadAccess): List<LoanVo> {
        val progress = returnService.returnedIdsBySource(CollectionConst.BIZ_LOAN, items.mapNotNull { it.id })
        val held=returnService.heldIdsBySource(CollectionConst.BIZ_LOAN,items.mapNotNull { it.id })
        items.forEach { it.heldForReviewCollectionIds=held[it.id]?.toList().orEmpty();it.returnedCollectionIds = it.id?.let { id -> progress[id] }?.toList() ?: emptyList() }
        val summaries=access.summaries(items.flatMap {splitCollectionIds(it.collectionIds)}.toSet())
        return items.map {MultiCollectionSummaryEnricher.enrichLoans(listOf(it),summaries[it.orgId].orEmpty()).single()}
    }
}
