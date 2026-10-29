package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.ReproductionDao
import io.hizhengfu.cloud.collection.dao.ReproductionVo
import io.hizhengfu.cloud.collection.dao.Reproductions
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.EnumRepType
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import io.hizhengfu.cloud.flow.domain.*
import jakarta.servlet.http.HttpServletRequest
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
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

internal data class ReproductionRecordValues(
    val repType: EnumRepType?,
    val images: String?,
    val purpose: String?,
    val repDate: LocalDate?,
) {
    companion object {
        fun fromCreate(vo: ReproductionVo) = ReproductionRecordValues(
            repType = vo.repType,
            images = vo.images,
            purpose = vo.purpose,
            repDate = vo.repDate,
        )
    }
}

data class ReproductionCollectionInfo(
    val totalRegNo: String?,
    val name: String?,
)

object ReproductionCollectionInfoEnricher {
    fun enrich(items: List<ReproductionVo>, collectionInfoById: Map<String, ReproductionCollectionInfo>): List<ReproductionVo> =
        items.map { item ->
            val info = item.collectionId?.let { collectionInfoById[it] }
            item.copy(
                collectionTotalRegNo = info?.totalRegNo,
                collectionName = info?.name,
            )
        }
}

/** 复制 / 拓印 / 影像采集。 */
@Component
@Transactional
class ReproductionService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val operationLockService: CollectionOperationLockService,
    private val compliance: ReproductionComplianceService,
    private val digitalAcquisition: DigitalAcquisitionService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<ReproductionDao>(ReproductionDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_REPRODUCTION
    override fun definitionKey() = CollectionConst.FLOW_REPRODUCTION
    override fun definitionKey(entity: ReproductionDao) = when {
        compliance.physical(entity) -> CollectionConst.FLOW_REPRODUCTION_2026
        digitalAcquisition.digital(entity) -> CollectionConst.FLOW_DIGITAL_ACQUISITION_2026
        else -> definitionKey()
    }
    override fun businessTypeName() = "复制拓印"
    override fun authorizeUserOperation(entity: ReproductionDao, operation: FlowUserOperation) {
        val write=operation!=FlowUserOperation.FLOW_INFO
        CollectionCurrentOperationAccess.require(scope,entity.orgId,write,
            if(write)setOf(CollectionConst.STORAGE)else setOf(CollectionConst.STORAGE,CollectionConst.SELECT),"复制拓印记录",
            if(digitalAcquisition.digital(entity))setOf(CollectionConst.ROLE_INFORMATION,CollectionConst.ROLE_STORAGE_HEAD)else emptySet())
        if (operation != FlowUserOperation.FLOW_INFO) {
            if(compliance.physical(entity))compliance.requireEditable(entity)
            if(digitalAcquisition.digital(entity))digitalAcquisition.requireEditable(entity)
        }
    }
    override fun buildTitle(entity: ReproductionDao) = "${if(digitalAcquisition.digital(entity))"数字采集" else "复制拓印"}：${entity.collectionId}"
    override fun buildFormData(entity: ReproductionDao) = mapOf(
        "collectionId" to entity.collectionId,
        "repType" to entity.repType?.name,
        "purpose" to entity.purpose,
        "repDate" to entity.repDate?.toString(),
        "images" to entity.images,
    ) + when {
        compliance.physical(entity) -> compliance.formData(entity)
        digitalAcquisition.digital(entity) -> digitalAcquisition.formData(entity)
        else -> emptyMap()
    }

    override fun buildSelfSelectAssignees(entity: ReproductionDao) = when {
        compliance.physical(entity) -> compliance.actors(entity).mapValues { listOf(it.value) }
        digitalAcquisition.digital(entity) -> digitalAcquisition.actors(entity).mapValues { listOf(it.value) }
        else -> emptyMap()
    }
    override fun beforeSubmit(entity: ReproductionDao) {
        when {
            compliance.physical(entity) -> compliance.submit(entity)
            digitalAcquisition.digital(entity) -> digitalAcquisition.submit(entity)
            else -> throw BadRequestException("须明确复制、拓印或数字采集类型")
        }
    }
    override fun onFlowTaskCompleted(entity: ReproductionDao, event: FlowTaskCompletedEvent) {
        if(event.action == "APPROVE") {
            if(compliance.physical(entity))compliance.requireActor(entity,event.nodeKey,event.operator,event.comment)
            if(digitalAcquisition.digital(entity))digitalAcquisition.requireActor(entity,event.nodeKey,event.operator,event.comment)
        }
    }
    override fun onFlowCompleted(entity: ReproductionDao,event: FlowCompletedEvent) {
        if (compliance.physical(entity)) { compliance.requireEditable(entity); compliance.confirm(entity) }
        if(digitalAcquisition.digital(entity)){digitalAcquisition.requireEditable(entity);digitalAcquisition.confirm(entity)}
        super.onFlowCompleted(entity,event)
    }
    override fun beforeFlowReentry(entity: ReproductionDao, targetNodeKey: String) {
        if (!compliance.physical(entity) && !digitalAcquisition.digital(entity)) throw BadRequestException("须先核实原业务属于复制拓印或数字采集，不能用通用历史类型重新审批")
        if(compliance.physical(entity)) { compliance.requireEditable(entity);if(targetNodeKey!="keeper")throw BadRequestException("复制拓印重新审批须从保管确认开始");compliance.requirePrepared(entity) }
        if(digitalAcquisition.digital(entity)){digitalAcquisition.requireEditable(entity);if(targetNodeKey!="information")throw BadRequestException("数字采集重新确认须从信息化部门开始");digitalAcquisition.prepared(entity)}
    }
    override fun onFlowCanceled(entity: ReproductionDao,event: FlowCanceledEvent) { if(compliance.physical(entity))compliance.requireEditable(entity);if(digitalAcquisition.digital(entity))digitalAcquisition.requireEditable(entity);super.onFlowCanceled(entity,event) }
    override fun onFlowRejected(entity: ReproductionDao,event: FlowRejectedEvent) { if(compliance.physical(entity))compliance.requireEditable(entity);if(digitalAcquisition.digital(entity))digitalAcquisition.requireEditable(entity);super.onFlowRejected(entity,event) }

    protected override fun acquireApprovalResources(entity: ReproductionDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, listOf(entity.collectionId))

    protected override fun releaseApprovalResources(entity: ReproductionDao) =
        operationLockService.release(businessType(), entity.id.value)

    fun findPage(request: HttpServletRequest): GenericPage<ReproductionVo> {
        val access=CollectionSearchReadAccess.require(scope,additionalPermissions=setOf(CollectionConst.STORAGE),allowHistoricalOrgs=true)
        val page = GenericPage<ReproductionVo>().build(request)
        val q = Reproductions.selectAll()
        q.andWhere {Reproductions.orgId inList access.orgIds}
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            val matched=collectionIdsMatchingKeyword(kw,access.orgIds)
            var collectionMatch:org.jetbrains.exposed.v1.core.Op<Boolean> = org.jetbrains.exposed.v1.core.Op.FALSE
            matched.forEach {(org,ids)->collectionMatch=collectionMatch or ((Reproductions.orgId eq org) and (Reproductions.collectionId inList ids))}
            q.andWhere {
                (Reproductions.collectionId like "%$kw%") or
                    collectionMatch or
                    (Reproductions.purpose like "%$kw%") or
                    (Reproductions.images like "%$kw%")
            }
        }
        request.getParameter("collectionId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Reproductions.collectionId eq it } }
        request.getParameter("repType")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumRepType.valueOf(value) }.getOrNull()?.let { q.andWhere { Reproductions.repType eq it } }
        }
        request.getParameter("flowStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumFlowStatus.valueOf(value) }.getOrNull()?.let { q.andWhere { Reproductions.flowStatus eq it } }
        }
        request.getParameter("purpose")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Reproductions.purpose like "%$it%" } }
        request.getParameter("repDateStart")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.let { q.andWhere { Reproductions.repDate greaterEq it } }
        }
        request.getParameter("repDateEnd")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            parseCollectionDateParam(value)?.let { q.andWhere { Reproductions.repDate lessEq it } }
        }
        request.getParameter("hasImage")?.trim()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()?.let { has ->
            if (has) q.andWhere { Reproductions.images.isNotNull() and Reproductions.images.neq("") }
            else q.andWhere { Reproductions.images.isNull() or Reproductions.images.eq("") }
        }
        q.orderBy(Reproductions.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<ReproductionDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let { enrichCollectionInfo(it,access.orgIds) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<ReproductionVo>
    }

    fun save(vo: ReproductionVo): String {
        val collectionId = vo.collectionId?.takeIf { it.isNotBlank() } ?: throw BadRequestException("藏品必填")
        val coll = CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        CollectionCurrentOperationAccess.require(scope,coll.orgId,true,setOf(CollectionConst.STORAGE),"复制拓印记录",
            if(vo.repType in setOf(EnumRepType.PHOTO,EnumRepType.SCAN_3D))setOf(CollectionConst.ROLE_INFORMATION,CollectionConst.ROLE_STORAGE_HEAD)else emptySet())
        CollectionOutboundPolicy.requireInStore(coll.lifeStatus, "复制拓印", coll.inventoryHold)
        val values = ReproductionRecordValues.fromCreate(vo)
        val d = saveDraft(vo.id, "复制拓印记录不存在", createEntity = {
            dao.new {
                orgId = coll.orgId
                this.collectionId = collectionId
            }
        }) {
            CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.STORAGE),"复制拓印记录",
                if(values.repType in setOf(EnumRepType.PHOTO,EnumRepType.SCAN_3D))setOf(CollectionConst.ROLE_INFORMATION,CollectionConst.ROLE_STORAGE_HEAD)else emptySet())
            if (coll.orgId != orgId) throw BadRequestException("复制拓印记录不能改用其他收藏单位的藏品")
            if (io.hizhengfu.cloud.collection.dao.ReproductionPlanDao.find { io.hizhengfu.cloud.collection.dao.ReproductionPlans.reproductionId eq id.value }.any() && (this.collectionId != collectionId || repType != values.repType)) throw BadRequestException("已有法定方案的藏品和活动类型不能替换")
            if(io.hizhengfu.cloud.collection.dao.DigitalAcquisitionPlanDao.find { io.hizhengfu.cloud.collection.dao.DigitalAcquisitionPlans.reproductionId eq id.value }.any() && (this.collectionId!=collectionId || repType!=values.repType))throw BadRequestException("已有数字采集方案的藏品和活动类型不能替换")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_REPRODUCTION, id.value)
            this.collectionId = collectionId
            repType = values.repType
            images = values.images
            purpose = values.purpose
            repDate = values.repDate
        }
        originalFiles?.remember(CollectionConst.BIZ_REPRODUCTION, d.id.value)
        BusinessAttachmentBindingPlanner.plan(d.id.value, "复制拓印影像", d.images).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_REPRODUCTION, d.id.value, d.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(d.id.value, item.aliasName, item.fileId)
        }
        return d.id.value
    }

    fun create(vo: ReproductionVo): String = save(vo)

    private fun enrichCollectionInfo(items:List<ReproductionVo>,orgIds:Set<String>):List<ReproductionVo> {
        val ids=items.mapNotNull {it.collectionId}.toSet()
        if(ids.isEmpty())return items
        val info=Collections.selectAll().where {(Collections.id inList ids) and (Collections.orgId inList orgIds)}
            .groupBy {it[Collections.orgId]}.mapValues {(_,rows)->rows.associate {it[Collections.id].value to ReproductionCollectionInfo(it[Collections.totalRegNo],it[Collections.name])}}
        return items.flatMap {item->ReproductionCollectionInfoEnricher.enrich(listOf(item),info[item.orgId].orEmpty())}
    }
    private fun collectionIdsMatchingKeyword(keyword:String,orgIds:Set<String>):Map<String,List<String>> =
        Collections.selectAll().where {(Collections.orgId inList orgIds) and ((Collections.totalRegNo like "%$keyword%") or (Collections.name like "%$keyword%"))}
            .groupBy({it[Collections.orgId]},{it[Collections.id].value})
}
