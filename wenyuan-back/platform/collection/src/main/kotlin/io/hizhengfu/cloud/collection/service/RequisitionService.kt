package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.ExhibitionDao
import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.dao.RequisitionDao
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.dao.Requisitions
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchives
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.CollectionOutboundExecutionPolicy
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.flow.domain.FlowTaskCompletedEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import io.hizhengfu.cloud.flow.domain.FlowCanceledEvent
import io.hizhengfu.cloud.flow.domain.FlowRejectedEvent
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.collection.domain.CollectionOutboundExecutionVo
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
import org.jetbrains.exposed.v1.core.less
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
import java.time.format.DateTimeFormatter

internal data class RequisitionRecordValues(
    val documentNo: String?,
    val sourceBizType: String?,
    val sourceBizId: String?,
    val collectionIds: String?,
    val purpose: EnumMoveReason?,
    val basis: String?,
    val department: String?,
    val operator: String?,
    val reviewer: String?,
    val custodian: String?,
    val handoverUser: String?,
    val reviewOpinion: String?,
    val attachmentIds: String?,
    val toWarehouseId: String?,
    val expectReturnDate: LocalDate?,
) {
    companion object {
        fun from(vo: RequisitionVo) = RequisitionRecordValues(
            documentNo = vo.documentNo,
            sourceBizType = vo.sourceBizType,
            sourceBizId = vo.sourceBizId,
            collectionIds = vo.collectionIds,
            purpose = vo.purpose,
            basis = vo.basis,
            department = vo.department,
            operator = vo.operator,
            reviewer = vo.reviewer,
            custodian = vo.custodian,
            handoverUser = vo.handoverUser,
            reviewOpinion = vo.reviewOpinion,
            attachmentIds = vo.attachmentIds,
            toWarehouseId = vo.toWarehouseId,
            expectReturnDate = vo.expectReturnDate,
        )

        fun fromExhibition(vo: ExhibitionVo, operator: String?) = RequisitionRecordValues(
            documentNo = null,
            sourceBizType = CollectionConst.BIZ_EXHIBITION,
            sourceBizId = vo.id,
            collectionIds = vo.collectionIds,
            purpose = EnumMoveReason.EXHIBIT,
            basis = exhibitionBasis(vo.title, vo.exhibitionNo),
            department = vo.organizer?.takeIf { it.isNotBlank() } ?: vo.venue,
            operator = operator,
            reviewer = null,
            custodian = null,
            handoverUser = null,
            reviewOpinion = "展览审批通过后自动生成提用草稿",
            attachmentIds = vo.attachmentIds,
            toWarehouseId = null,
            expectReturnDate = vo.endDate,
        )

        private fun exhibitionBasis(title: String?, exhibitionNo: String?): String {
            val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() } ?: "未命名展览"
            val cleanNo = exhibitionNo?.trim()?.takeIf { it.isNotBlank() }
            return if (cleanNo == null) "展览：$cleanTitle" else "展览：$cleanTitle（$cleanNo）"
        }
    }

    fun toVo(id: String? = null) = RequisitionVo(
        id = id,
        documentNo = documentNo,
        sourceBizType = sourceBizType,
        sourceBizId = sourceBizId,
        collectionIds = collectionIds,
        purpose = purpose,
        basis = basis,
        department = department,
        operator = operator,
        reviewer = reviewer,
        custodian = custodian,
        handoverUser = handoverUser,
        reviewOpinion = reviewOpinion,
        attachmentIds = attachmentIds,
        toWarehouseId = toWarehouseId,
        expectReturnDate = expectReturnDate,
    )
}

/**
 * 提用单（出入库流程）。三方确认后待实际点交；签署执行后生成出库流水，归还由独立单据办理。
 * 经办人/复核人为系统用户；一级文物强制双人（reviewer 必填且 ≠ operator）。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class RequisitionService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val userResolver: CollectionUserResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val returnService: CollectionReturnService,
    private val businessItemService: CollectionBusinessItemService,
    private val operationLockService: CollectionOperationLockService,
    private val placementService: CollectionPlacementService,
    private val statutory: RequisitionStatutoryService,
    private val archives: CollectionPermanentArchiveService,
    private val restrictedOperations: CollectionRestrictedOperationService = CollectionRestrictedOperationService(),
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<RequisitionDao>(RequisitionDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_REQUISITION
    override fun definitionKey() = CollectionConst.FLOW_REQUISITION
    override fun businessTypeName() = "藏品提用"
    override fun todoListRoute() = "/collection/requisition"
    override fun authorizeUserOperation(entity: RequisitionDao,operation: FlowUserOperation) {
        if(operation==FlowUserOperation.FLOW_INFO) CollectionCirculationReadAccess.require(scope).require(entity.orgId,CollectionConst.BIZ_REQUISITION,entity.id.value)
        else scope.requireFlowAccess(entity.orgId,operation,"提用单")
        if (operation == FlowUserOperation.SAVE_DRAFT) statutory.requireEditable(entity)
        if (operation in setOf(FlowUserOperation.CANCEL,FlowUserOperation.VOID) && entity.outExecuted) throw BadRequestException("已实际出库的提用单不能撤销，请办理归还")
    }
    override fun buildTitle(entity: RequisitionDao) = "藏品提用（${entity.department ?: entity.documentNo}）"
    override fun buildFormData(entity: RequisitionDao) = statutory.proposal(entity)
    override fun buildSelfSelectAssignees(entity: RequisitionDao) = statutory.actors(entity).mapValues { listOf(it.value) }
    override fun beforeSubmit(entity: RequisitionDao) {
        if (entity.flowInstanceId != null) throw BadRequestException("已提交提用申请不能覆盖，请另建方案后确认")
        statutory.requirePrepared(entity)
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_REQUISITION, entity.id.value)
        archives.seal(entity.orgId,businessType(),entity.id.value,"SUBMITTED",statutory.proposal(entity),statutory.allFiles(entity))
    }

    protected override fun acquireApprovalResources(entity: RequisitionDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, splitCollectionIds(entity.collectionIds))

    protected override fun releaseApprovalResources(entity: RequisitionDao) {
        if (entity.flowStatus == EnumFlowStatus.PASS && !entity.outExecuted) return
        operationLockService.release(businessType(),entity.id.value)
    }

    fun findPage(request: HttpServletRequest): GenericPage<RequisitionVo> {
        val page = GenericPage<RequisitionVo>().build(request)
        val access=CollectionCirculationReadAccess.require(scope)
        val q = Requisitions.selectAll()
        q.andWhere { Requisitions.orgId inList access.orgIds }
        val filters = RequisitionQueryFilters.from { request.getParameter(it) }
        filters.sourceBizType?.let { q.andWhere { Requisitions.sourceBizType eq it } }
        filters.sourceBizId?.let { q.andWhere { Requisitions.sourceBizId eq it } }
        filters.keyword?.let { kw ->
            val collectionMatch=access.matchingItems(Requisitions.orgId,Requisitions.id,CollectionConst.BIZ_REQUISITION,kw,true)
            q.andWhere {
                (Requisitions.documentNo like "%$kw%") or
                    collectionMatch or
                    (Requisitions.basis like "%$kw%") or
                    (Requisitions.department like "%$kw%") or
                    (Requisitions.operator like "%$kw%") or
                    (Requisitions.reviewer like "%$kw%") or
                    (Requisitions.custodian like "%$kw%") or
                    (Requisitions.handoverUser like "%$kw%") or
                    (Requisitions.reviewOpinion like "%$kw%") or
                    (Requisitions.attachmentIds like "%$kw%")
            }
        }
        filters.collectionId?.let {
            q.andWhere { access.matchingItems(Requisitions.orgId,Requisitions.id,CollectionConst.BIZ_REQUISITION,it,false) }
        }
        filters.documentNo?.let { q.andWhere { Requisitions.documentNo like "%$it%" } }
        filters.purpose?.let { q.andWhere { Requisitions.purpose eq it } }
        filters.basis?.let { q.andWhere { Requisitions.basis like "%$it%" } }
        filters.department?.let { q.andWhere { Requisitions.department like "%$it%" } }
        filters.operator?.let { q.andWhere { Requisitions.operator eq it } }
        filters.reviewer?.let { q.andWhere { Requisitions.reviewer eq it } }
        filters.flowStatus?.let { q.andWhere { Requisitions.flowStatus eq it } }
        filters.returned?.let { q.andWhere { Requisitions.returned eq it } }
        filters.hasAttachment?.let { has ->
            if (has) q.andWhere { Requisitions.attachmentIds.isNotNull() and Requisitions.attachmentIds.neq("") }
            else q.andWhere { Requisitions.attachmentIds.isNull() or Requisitions.attachmentIds.eq("") }
        }
        filters.expectReturnDateStart?.let { q.andWhere { Requisitions.expectReturnDate greaterEq it } }
        filters.expectReturnDateEnd?.let { q.andWhere { Requisitions.expectReturnDate lessEq it } }
        filters.createTimeStart?.let { q.andWhere { Requisitions.createTime greaterEq it } }
        filters.createTimeEnd?.let { q.andWhere { Requisitions.createTime lessEq it } }
        q.orderBy(Requisitions.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<RequisitionDao>)
        page.list = daoPage.data?.map { access.require(it.orgId,CollectionConst.BIZ_REQUISITION,it.id.value); it.toModel() }?.let { enrich(it,access) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<RequisitionVo>
    }

    fun get(id: String): RequisitionVo? {
        val access=CollectionCirculationReadAccess.require(scope)
        val row=RequisitionDao.findById(id) ?: return null
        access.require(row.orgId,businessType(),id)
        val errors=RequisitionReplacementEvidence(archives).errors(row).toMutableList()
        val originals=try { archives.listForAuthorizedOrg(businessType(),id,row.orgId) }
            catch(e:BadRequestException) { errors+=e.message ?: "提用原件待核对"; emptyList() }
        return enrich(listOf(row.toModel()),access).single().copy(archives=originals,replacementErrors=errors.distinct())
    }

    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        val detail=get(id) ?: throw BadRequestException("提用案卷不存在")
        CollectionCirculationArchivedOriginal.require(detail.archives,archiveId,fileId)
    }

    private fun enrich(list: List<RequisitionVo>,access:CollectionCirculationReadAccess): List<RequisitionVo> {
        val withSummaries = enrichCollectionSummaries(list,access)
        val names = userResolver.names(withSummaries.flatMap { listOf(it.operator, it.reviewer) })
        val progress = returnService.returnedIdsBySource(CollectionConst.BIZ_REQUISITION, withSummaries.mapNotNull { it.id })
        val held=returnService.heldIdsBySource(CollectionConst.BIZ_REQUISITION,withSummaries.mapNotNull { it.id })
        withSummaries.forEach {
            it.operatorName = it.operator?.let { u -> names[u] }
            it.reviewerName = it.reviewer?.let { u -> names[u] }
            it.heldForReviewCollectionIds=held[it.id]?.toList().orEmpty()
            it.returnedCollectionIds = it.id?.let { id -> progress[id] }?.toList() ?: emptyList()
        }
        return withSummaries
    }

    @CacheEvict(allEntries = true)
    fun save(vo: RequisitionVo): String = saveRecord(vo)

    /** 仅由责任重建在核实原已提交来源后调用；能力不来自公开 DTO。 */
    internal fun saveForResponsibilityReplacement(vo: RequisitionVo, originals: CollectionPermanentArchiveService.RequisitionReplacementOriginals): String {
        if (!vo.id.isNullOrBlank()) throw BadRequestException("责任承接须建立独立新草稿")
        if (originals.basisFileIds.isNotEmpty() && originalFiles == null) throw BadRequestException("责任承接原件留存服务未配置")
        return saveRecord(vo, originals)
    }

    private fun saveRecord(vo: RequisitionVo, replacementOriginals: CollectionPermanentArchiveService.RequisitionReplacementOriginals? = null): String {
        val values = RequisitionRecordValues.from(vo)
        val ids = splitIds(values.collectionIds)
        if (ids.isEmpty()) throw BadRequestException("请选择提用藏品")
        val colls = ids.mapNotNull { CollectionDao.findById(it) }
        if (colls.size != ids.size) throw BadRequestException("提用清单含不存在的藏品")
        if (colls.map { it.orgId }.distinct().size != 1) throw BadRequestException("一张馆内提用单不能混合不同收藏单位的藏品")
        val existing=vo.id?.takeIf(String::isNotBlank)?.let(RequisitionDao::findById)
        existing?.replacesId?.let { previousId ->
            val previous = RequisitionDao.findById(previousId) ?: throw BadRequestException("责任变更所依据的原提用单不存在")
            if (ids.toSet() != splitCollectionIds(previous.collectionIds).toSet() || values.purpose != previous.purpose ||
                (values.sourceBizType ?: existing.sourceBizType) != previous.sourceBizType || (values.sourceBizId ?: existing.sourceBizId) != previous.sourceBizId) throw BadRequestException("承接草稿不能替换原提用实物、事由或派生业务来源")
        }
        if(existing!=null && existing.orgId!=colls.first().orgId)throw BadRequestException("提用单不能更换收藏单位")
        if(existing?.sourceBizType=="COLLECTION_RESTORATION" && existing.collectionIds!=values.collectionIds)throw BadRequestException("修复来源的送修单不能替换实物清单")
        if(existing?.sourceBizType=="COLLECTION_RESTORATION" && ((values.sourceBizType!=null && values.sourceBizType!=existing.sourceBizType) || (values.sourceBizId!=null && values.sourceBizId!=existing.sourceBizId)))throw BadRequestException("送修来源不能替换")
        // 草稿只核对占用；完整使用保护前提在提交获取审批资源时检查，不能阻止补齐草稿。
        ids.forEach { operationLockService.requireNoOtherApproval(it,businessType(),vo.id) }
        val scopeIds = scope.writableOrgIds()
        colls.forEach { if (scopeIds != null && !scopeIds.contains(it.orgId)) throw BadRequestException("无权操作藏品 ${it.totalRegNo}") }
        colls.forEach { CollectionOutboundPolicy.requireInStore(it.lifeStatus, "提用", it.inventoryHold && !restrictedOperations.permits(it.id.value,businessType(),vo.id)) }
        // 红线：含一级文物须双人，复核人不能与经办人相同
        if (colls.any { it.level == EnumRelicLevel.FIRST }) {
            val reviewer = values.reviewer?.takeIf { it.isNotBlank() } ?: throw BadRequestException("含一级文物，必须指定复核人")
            if (reviewer == values.operator) throw BadRequestException("复核人不能与经办人相同")
        }
        val org = colls.first().orgId
        val persistedCollectionIds = values.collectionIds.orEmpty()
        val entity = saveDraft(vo.id, "提用单不存在", createEntity = {
            dao.new {
                orgId = org
                // Exposed 在 new 闭包结束时执行 INSERT，达梦非空列必须在首次插入前赋值。
                collectionIds = persistedCollectionIds
                createBy=SecurityUtils.getUserId(); createTime=LocalDateTime.now()
            }
        }) {
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "提用单")
            if (orgId != org) throw BadRequestException("提用单不能更换收藏单位")
            val returnLocation = CollectionLocationMutation.reference(orgId, values.toWarehouseId)
            if (returnLocation != null && returnLocation.sceneType != EnumLocationScene.STORAGE)
                throw BadRequestException("归库位置须属于本馆保管空间")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_REQUISITION, id.value)
            documentNo = resolveDocumentNo(values.documentNo ?: documentNo, org)
            sourceBizType = values.sourceBizType ?: sourceBizType
            sourceBizId = values.sourceBizId ?: sourceBizId
            collectionIds = persistedCollectionIds
            purpose = values.purpose
            basis = values.basis
            department = values.department
            operator = values.operator
            reviewer = values.reviewer
            custodian = values.custodian
            handoverUser = values.handoverUser
            reviewOpinion = values.reviewOpinion
            attachmentIds = values.attachmentIds
            toWarehouseId = returnLocation?.id?.value
            expectReturnDate = vo.dossier?.expectedReturnAt?.toLocalDate() ?: values.expectReturnDate
            dossier = vo.dossier
            baseline = statutory.baseline(colls)
        }
        businessItemService.bind(entity.orgId, CollectionConst.BIZ_REQUISITION, entity.id.value, entity.collectionIds)
        if (replacementOriginals == null) originalFiles?.remember(CollectionConst.BIZ_REQUISITION, entity.id.value)
        else originalFiles?.rememberRequisitionReplacement(entity.id.value, replacementOriginals)
        return entity.id.value
    }

    @CacheEvict(allEntries = true)
    fun ensureDraftFromExhibition(entity: ExhibitionDao, operator: String?): String? {
        if (entity.collectionIds.isNullOrBlank()) return null
        val sourceId = entity.id.value
        findDerivedDraft(CollectionConst.BIZ_EXHIBITION, sourceId)?.let { return it.id.value }
        val values = RequisitionRecordValues.fromExhibition(entity.toModel(), operator)
        return save(values.toVo())
    }

    private fun findDerivedDraft(sourceBizType: String, sourceBizId: String): RequisitionDao? =
        RequisitionDao.find {
            (Requisitions.sourceBizType eq sourceBizType) and (Requisitions.sourceBizId eq sourceBizId)
        }.firstOrNull { it.supersededById == null && it.sourceRetirement == null }

    private fun resolveDocumentNo(input: String?, orgId: String): String {
        val manual = input?.trim()?.takeIf { it.isNotBlank() }
        if (manual != null) return manual
        val today = LocalDate.now()
        return RequisitionDocumentNumberGenerator.resolve(
            input = null,
            today = today,
            existingForDay = existingDocumentNumbersForDay(orgId, today),
        )
    }

    private fun existingDocumentNumbersForDay(orgId: String, today: LocalDate): Set<String> {
        val prefix = RequisitionDocumentNumberGenerator.prefix(today)
        return Requisitions.selectAll()
            .where {
                (Requisitions.orgId eq orgId) and
                    (Requisitions.documentNo like "$prefix%") and
                    (Requisitions.createTime greaterEq today.atStartOfDay()) and
                    (Requisitions.createTime less today.plusDays(1).atStartOfDay())
            }
            .mapNotNull { it[Requisitions.documentNo] }
            .toSet()
    }

    override fun onFlowTaskCompleted(entity: RequisitionDao,event: FlowTaskCompletedEvent) {
        if (event.action == "APPROVE") statutory.requireActor(entity,event.nodeKey,event.operator,event.comment)
    }
    /** 三方实际确认后等待现场点交，不凭可配置流程的 PASS 自动出库。 */
    override fun onFlowCompleted(entity: RequisitionDao,event: FlowCompletedEvent) {
        if (entity.flowInstanceId != event.instanceId || entity.outExecuted) throw BadRequestException("审批实例不一致或已实际出库")
        if (entity.flowStatus == EnumFlowStatus.PASS) { statutory.requireCurrentApproval(entity); return }
        statutory.confirmApproval(entity)
        super.onFlowCompleted(entity,event)
        splitIds(entity.collectionIds).forEach { collectionService.changeLifeStatus(it,EnumCollectionStatus.PRE_OUT,businessType(),entity.id.value) }
    }
    override fun beforeFlowReentry(entity: RequisitionDao, targetNodeKey: String) {
        if (entity.outExecuted) throw BadRequestException("已出库提用不能重新审批，请办理归还")
        if (targetNodeKey != "department") throw BadRequestException("重新审批须从提用部门负责人确认开始")
        statutory.requirePrepared(entity,setOf(EnumCollectionStatus.IN_STORE,EnumCollectionStatus.PRE_OUT))
        restorePending(entity)
    }
    override fun onFlowCanceled(entity: RequisitionDao,event: FlowCanceledEvent) {
        if (entity.outExecuted) throw BadRequestException("已出库提用不能撤销，请办理归还")
        restorePending(entity)
        super.onFlowCanceled(entity,event)
    }
    override fun onFlowRejected(entity: RequisitionDao,event: FlowRejectedEvent) {
        if (entity.outExecuted) throw BadRequestException("已实际出库的提用不能改为驳回")
        restorePending(entity)
        super.onFlowRejected(entity,event)
    }
    private fun restorePending(entity: RequisitionDao) {
        splitIds(entity.collectionIds).forEach { id ->
            val node=CollectionDao.findById(id) ?: throw BadRequestException("提用藏品不存在")
            if (node.orgId != entity.orgId) throw BadRequestException("提用藏品所属馆已变化")
            if (node.lifeStatus == EnumCollectionStatus.PRE_OUT) {
                operationLockService.requireNoOtherApproval(id,businessType(),entity.id.value)
                collectionService.changeLifeStatus(id,EnumCollectionStatus.IN_STORE,sourceBizType=businessType(),sourceBizId=entity.id.value)
            }
        }
    }

    /** 只由已核实本人授权及未点交事实的责任变更服务调用，事务保留原批准历史。 */
    internal fun releaseForResponsibilityReplacement(entity: RequisitionDao) {
        if (entity.outExecuted || entity.handover != null) throw BadRequestException("已有实际点交或出库，不得撤换为未执行申请")
        restorePending(entity)
        operationLockService.release(businessType(), entity.id.value)
    }

    @CacheEvict(allEntries = true)
    fun executeOut(id: String,vo: CollectionOutboundExecutionVo): String {
        val entity=RequisitionDao.find { Requisitions.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("提用单不存在")
        CollectionCurrentOperationAccess.require(scope,entity.orgId,true,setOf(CollectionConst.STORAGE),"提用出库")
        if (entity.flowStatus != EnumFlowStatus.PASS) throw BadRequestException("仅审批通过的提用单可执行出库")
        if (entity.outExecuted) {
            val original=CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq businessType()) and
                    (CollectionPermanentArchives.bizId eq id) and
                    (CollectionPermanentArchives.phase eq "OUT_EXECUTED")
            }.singleOrNull() ?: throw BadRequestException("已出库提用缺少实际出库永久原件，请核查原记录")
            val handover=entity.handover?.content ?: throw BadRequestException("已出库提用缺少实际点交记录")
            requireDesignatedOutExecutor(entity.orgId,handover)
            val evidenceIds=listOfNotNull(statutory.allFiles(entity),statutory.handoverFiles(handover),vo.conditionPhotoIds,vo.signedVoucherAttachmentIds).joinToString(",")
            archives.verifyExisting(original,entity.orgId,businessType(),id,"OUT_EXECUTED",
                mapOf("request" to entity.toModel().copy(returnDate=null,returned=false),"additionalVerification" to vo),evidenceIds)
            return id
        }
        if (entity.returned) throw BadRequestException("本提用单已归还，不能再次执行出库；旧状态矛盾须核查原记录")
        val handover=statutory.requireHandover(entity)
        requireDesignatedOutExecutor(entity.orgId,handover)
        val ids=splitIds(entity.collectionIds)
        ids.forEach { operationLockService.requireUnlocked(it,businessType(),id) }
        entity.conditionNote=handover.items.joinToString("；") { "${it.collectionId}：${it.condition}" }
        entity.conditionPhotoIds=handover.items.mapNotNull { it.imageFileIds }.joinToString(",").takeIf(String::isNotBlank)
        entity.signedVoucherAttachmentIds=handover.signedHandoverFileIds
        entity.custodian=handover.custodianUserIds.first()
        entity.handoverUser=handover.receivingUserIds.first()
        val evidenceIds=listOfNotNull(statutory.allFiles(entity),statutory.handoverFiles(handover),vo.conditionPhotoIds,vo.signedVoucherAttachmentIds).joinToString(",")
        handover.items.forEach { item ->
            collectionService.changeLifeStatus(item.collectionId,EnumCollectionStatus.IN_USE,businessType(),id)
            val itemCollection=CollectionDao[item.collectionId]
            val movement=StorageMovementDao.new {
                orgId=entity.orgId; documentNo=entity.documentNo; collectionId=item.collectionId; type=EnumMoveType.OUT
                totalRegNoAtMovement=itemCollection.totalRegNo; collectionNameAtMovement=itemCollection.name
                reason=entity.purpose; basis=entity.basis; department=entity.department
                operator=SecurityUtils.getUserId(); reviewer=handover.reviewerUserId
                custodian=entity.custodian; handoverUser=entity.handoverUser
                fromWarehouseId=itemCollection.warehouseId
                reviewOpinion=item.condition.take(500); attachmentIds=evidenceIds; refBizId=id; operatedAt=handover.handedAt
            }
            CollectionMovementFacts.capture(movement,archives)
            BusinessAttachmentBindingPlanner.plan(movement.id.value, "提用逐件点交原件", evidenceIds).forEach { file ->
                if (originalFiles?.bindMovementReference(CollectionConst.BIZ_REQUISITION, entity.id.value, movement.id.value, file.fileId, file.aliasName) != true)
                    attachmentBinder.bindSingle(movement.id.value, file.aliasName, file.fileId)
            }
        }
        entity.outExecuted=true; entity.outExecutedTime=handover.handedAt; entity.flush()
        if (entity.sourceBizType == CollectionConst.BIZ_EXHIBITION && !entity.sourceBizId.isNullOrBlank()) placementService.openExhibitionPlacements(entity.sourceBizId!!,ids,handover.handedAt)
        archives.seal(entity.orgId,businessType(),id,"OUT_EXECUTED",mapOf("request" to entity.toModel(),"additionalVerification" to vo),evidenceIds)
        operationLockService.release(businessType(),id)
        returnService.ensureDraftFromRequisition(id)
        requireDesignatedOutExecutor(entity.orgId,handover)
        return id
    }

    /** 审查指派和双方签署不授权其他同馆人员代替本单指定保管人员执行出库。 */
    private fun requireDesignatedOutExecutor(orgId:String,handover:RequisitionHandoverVo) {
        val current=CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.STORAGE),"提用出库")
        if(current.id !in handover.custodianUserIds)throw BadRequestException("须由本单指定保管人员本人执行提用出库")
    }

    /** 兼容旧入口：创建或返回归还流程草稿，不再直接回库。 */
    @CacheEvict(allEntries = true)
    fun returnBack(id: String): String = returnService.ensureDraftFromRequisition(id)

    private fun splitIds(s: String?): List<String> = splitCollectionIds(s)

    private fun enrichCollectionSummaries(items:List<RequisitionVo>,access:CollectionCirculationReadAccess):List<RequisitionVo> {
        val summaries=access.summaries(items.flatMap {splitCollectionIds(it.collectionIds)}.toSet())
        return items.map {MultiCollectionSummaryEnricher.enrichRequisitions(listOf(it),summaries[it.orgId].orEmpty()).single()}
    }

}

internal object RequisitionDocumentNumberGenerator {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun prefix(today: LocalDate): String = "TY-${today.format(dateFormatter)}-"

    fun resolve(input: String?, today: LocalDate, existingForDay: Set<String>): String {
        input?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val prefix = prefix(today)
        val nextSequence = (existingForDay.asSequence()
            .mapNotNull { existing ->
                existing.trim()
                    .takeIf { it.startsWith(prefix) }
                    ?.removePrefix(prefix)
                    ?.toIntOrNull()
            }
            .maxOrNull() ?: 0) + 1
        return prefix + nextSequence.toString().padStart(4, '0')
    }
}
