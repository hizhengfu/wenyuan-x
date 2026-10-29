package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.AcquisitionPlanDao
import io.hizhengfu.cloud.collection.dao.AcquisitionPlanVo
import io.hizhengfu.cloud.collection.dao.AcquisitionPlans
import io.hizhengfu.cloud.collection.dao.Acquisitions
import io.hizhengfu.cloud.collection.domain.EnumPlanPeriod
import io.hizhengfu.cloud.collection.domain.AcquisitionPlanOfflineApprovalVo
import io.hizhengfu.cloud.collection.domain.AcquisitionPlanOfflineApprovalPolicy
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 征集计划（流程驱动）：年度/月度计划编制→专家评审→分管馆长审批；征集单挂计划看执行进度。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_ACQUISITION])
class AcquisitionPlanService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
    private val permanentArchives: CollectionPermanentArchiveService? = null,
) : FlowService<AcquisitionPlanDao>(AcquisitionPlanDao, flowInstanceService, CollectionConst.CACHE_ACQUISITION) {

    override fun businessType() = CollectionConst.BIZ_ACQUISITION_PLAN
    override fun definitionKey() = CollectionConst.FLOW_ACQUISITION_PLAN
    override fun businessTypeName() = "征集计划"
    override fun authorizeUserOperation(entity: AcquisitionPlanDao, operation: FlowUserOperation) {
        if (operation == FlowUserOperation.FLOW_INFO) {
            val access = CollectionSearchReadAccess.require(scope, acquisition = true)
            OrgScopedReadPolicy.requireVisible(access.orgIds, entity.orgId, "征集计划")
        } else {
            scope.requireFlowAccess(entity.orgId, operation, "征集计划")
            if (entity.flowStatus == EnumFlowStatus.PASS && entity.flowInstanceId == null)
                throw BadRequestException("线下已批准的计划须保留原件，调整请另立计划")
        }
    }
    override fun buildTitle(entity: AcquisitionPlanDao) = "征集计划：${entity.title}"

    override fun beforeFlowReentry(entity: AcquisitionPlanDao, targetNodeKey: String) {
        if (targetNodeKey != "committee") throw BadRequestException("征集计划重新审批须从委员会审核开始")
        if (Acquisitions.selectAll().where { Acquisitions.planId eq entity.id.value }.limit(1).any()) {
            throw BadRequestException("该计划已被征集单引用，请保留原批准依据，后续调整另立计划")
        }
        if (entity.title.isBlank() || (entity.planType == EnumPlanPeriod.MONTH && entity.month !in 1..12)) {
            throw BadRequestException("请先核对原征集计划名称及月份")
        }
    }
    override fun buildFormData(entity: AcquisitionPlanDao) = mapOf(
        "title" to entity.title,
        "planType" to entity.planType.name,
        "year" to entity.year,
        "month" to entity.month,
        "budget" to entity.budget,
        "offlineApproval" to offlineApproval(entity),
    )

    fun findPage(request: HttpServletRequest): GenericPage<AcquisitionPlanVo> {
        val page = GenericPage<AcquisitionPlanVo>().build(request)
        val access = CollectionSearchReadAccess.require(scope, acquisition = true)
        val q = AcquisitionPlans.selectAll()
        q.andWhere { AcquisitionPlans.orgId inList access.orgIds }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            q.andWhere { (AcquisitionPlans.title like "%$kw%") or (AcquisitionPlans.content like "%$kw%") }
        }
        request.getParameter("year")?.trim()?.toIntOrNull()?.let { q.andWhere { AcquisitionPlans.year eq it } }
        request.getParameter("planType")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumPlanPeriod.valueOf(value) }.getOrNull()?.let { q.andWhere { AcquisitionPlans.planType eq it } }
        }
        request.getParameter("flowStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumFlowStatus.valueOf(value) }.getOrNull()?.let { q.andWhere { AcquisitionPlans.flowStatus eq it } }
        }
        q.orderBy(AcquisitionPlans.year to SortOrder.DESC, AcquisitionPlans.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<AcquisitionPlanDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let(::enrich)
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<AcquisitionPlanVo>
    }

    fun get(id: String): AcquisitionPlanVo? {
        val access = CollectionSearchReadAccess.require(scope, acquisition = true)
        return findById(id)
            ?.also { OrgScopedReadPolicy.requireVisible(access.orgIds, it.orgId, "征集计划") }
            ?.toModel()
            ?.let { enrich(listOf(it)).first() }
    }

    @CacheEvict(allEntries = true)
    fun save(vo: AcquisitionPlanVo): String {
        val title = vo.title?.takeIf { it.isNotBlank() } ?: throw BadRequestException("计划名称必填")
        val year = vo.year ?: throw BadRequestException("计划年度必填")
        val planType = vo.planType ?: EnumPlanPeriod.YEAR
        if (planType == EnumPlanPeriod.MONTH && (vo.month == null || vo.month !in 1..12)) {
            throw BadRequestException("月度计划请填写 1-12 的月份")
        }
        val entity = saveDraft(vo.id, "征集计划不存在", createEntity = {
            dao.new { orgId = scope.currentOrgId() }
        }) {
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "征集计划")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_ACQUISITION_PLAN, id.value)
            this.title = title
            this.planType = planType
            this.year = year
            month = if (planType == EnumPlanPeriod.MONTH) vo.month else null
            content = vo.content
            budget = vo.budget
            attachmentIds = vo.attachmentIds
        }
        originalFiles?.remember(CollectionConst.BIZ_ACQUISITION_PLAN, entity.id.value)
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "征集计划附件", entity.attachmentIds).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_ACQUISITION_PLAN, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        return entity.id.value
    }

    /** 不启动虚构的审批实例；沿用永久档案保存纸质批准事实和原件。 */
    @CacheEvict(allEntries = true)
    fun archiveOfflineApproval(id: String, request: AcquisitionPlanOfflineApprovalVo): String {
        val entity = AcquisitionPlanDao.find { AcquisitionPlans.id eq id }.forUpdate().singleOrNull()
            ?: throw BadRequestException("征集计划不存在")
        val actor = CollectionCurrentOperationAccess.require(scope, entity.orgId, true, setOf(CollectionConst.ACQUIRE), "征集计划归档")
        val value = AcquisitionPlanOfflineApprovalPolicy.normalize(request)
        val signedIds = splitCollectionIds(value.signedFileIds).map {
            attachmentBinder.resolveSingle(it) ?: throw BadRequestException("批准原件不存在")
        }.distinct().joinToString(",")
        if (signedIds.isBlank()) throw BadRequestException("请上传实际签署批准的计划原件")
        val normalized = value.copy(signedFileIds = signedIds)
        if (entity.flowStatus == EnumFlowStatus.PASS && entity.flowInstanceId == null) {
            if (offlineApproval(entity) == normalized) return id
            throw BadRequestException("该计划已归档，批准事实及原件不能覆盖")
        }
        if (entity.flowStatus != EnumFlowStatus.DRAFT || entity.flowInstanceId != null)
            throw BadRequestException("请使用尚未发起线上审批的计划草稿登记线下批准")
        if (entity.title.isBlank() || entity.year !in 1..9999 ||
            (entity.planType == EnumPlanPeriod.MONTH && entity.month !in 1..12))
            throw BadRequestException("请先核对计划名称、年度及月份")
        if (entity.createTime?.let { value.approvedAt!!.isBefore(it) } == true && value.supplementaryReason.isNullOrBlank())
            throw BadRequestException("补录此前已批准的计划，请说明补录原因")
        val archiveService = permanentArchives ?: throw BadRequestException("计划永久归档服务不可用")
        val allFiles = (splitCollectionIds(entity.attachmentIds) + splitCollectionIds(signedIds)).distinct().joinToString(",")
        attachmentBinder.bind(id, "线下批准计划原件", signedIds)
        archiveService.seal(entity.orgId, businessType(), id, "OFFLINE_APPROVED",
            mapOf("plan" to entity.toModel(), "approval" to normalized, "recordedBy" to actor.id), allFiles)
        entity.attachmentIds = allFiles
        entity.flowStatus = EnumFlowStatus.PASS
        entity.updateBy = actor.id
        entity.updateTime = LocalDateTime.now()
        originalFiles?.remember(businessType(), id)
        TransactionManager.current().entityCache.flush()
        entity.refresh(flush = false)
        if (entity.flowStatus != EnumFlowStatus.PASS || entity.flowInstanceId != null || offlineApproval(entity) != normalized)
            throw BadRequestException("线下批准归档结果不一致，请重新核对")
        CollectionCurrentOperationAccess.require(scope, entity.orgId, true, setOf(CollectionConst.ACQUIRE), "征集计划归档")
        return id
    }

    private fun offlineApproval(entity: AcquisitionPlanDao): AcquisitionPlanOfflineApprovalVo? {
        if (entity.flowStatus != EnumFlowStatus.PASS || entity.flowInstanceId != null) return null
        val archive = permanentArchives?.phaseForAuthorizedOrg(businessType(), entity.id.value, entity.orgId, "OFFLINE_APPROVED")
            ?: return null
        val snapshot = JsonUtils.mapper.readTree(archive["snapshot"] as String)
        return JsonUtils.fromJson(snapshot.path("approval").toString(), AcquisitionPlanOfflineApprovalVo::class.java)
    }

    /** 只统计与已授权计划同馆的征集单；历史误关联不能改变计划执行进度。 */
    private fun enrich(items: List<AcquisitionPlanVo>): List<AcquisitionPlanVo> {
        val ids = items.mapNotNull { it.id }.toSet()
        if (ids.isEmpty()) return items
        val rows = Acquisitions.join(AcquisitionPlans, JoinType.INNER, Acquisitions.planId, AcquisitionPlans.id)
            .select(Acquisitions.planId, Acquisitions.collectionId)
            .where { (Acquisitions.planId inList ids) and (Acquisitions.orgId eq AcquisitionPlans.orgId) }
            .map { it[Acquisitions.planId] to it[Acquisitions.collectionId] }
        val totalByPlan = rows.groupingBy { it.first }.eachCount()
        val registeredByPlan = rows.filter { !it.second.isNullOrBlank() }.groupingBy { it.first }.eachCount()
        items.forEach {
            it.acquisitionCount = (totalByPlan[it.id] ?: 0).toLong()
            it.registeredCount = (registeredByPlan[it.id] ?: 0).toLong()
            it.offlineApproval = it.id?.let { id -> findById(id)?.let(::offlineApproval) }
        }
        return items
    }
}
