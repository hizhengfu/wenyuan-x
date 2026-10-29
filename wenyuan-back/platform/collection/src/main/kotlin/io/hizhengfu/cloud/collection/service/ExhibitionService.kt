package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionPlacementDao
import io.hizhengfu.cloud.collection.dao.CollectionPlacements
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.ExhibitionDao
import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.dao.Exhibitions
import io.hizhengfu.cloud.collection.dao.Requisitions
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.dao.WarehouseVo
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumProcessState
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.common.domain.GenericPage
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

internal data class ExhibitionRecordValues(
    val title: String,
    val exhibitionNo: String?,
    val venueId: String?,
    val venue: String?,
    val organizer: String?,
    val collectionIds: String?,
    val coverImage: String?,
    val attachmentIds: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val state: EnumProcessState,
    val remark: String?,
) {
    companion object {
        fun from(vo: ExhibitionVo): ExhibitionRecordValues {
            val startDate = vo.startDate
            val endDate = vo.endDate
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                throw BadRequestException("展览结束日期不能早于开始日期")
            }
            return ExhibitionRecordValues(
                title = vo.title?.trim()?.takeIf { it.isNotBlank() } ?: throw BadRequestException("展览名称必填"),
                exhibitionNo = vo.exhibitionNo?.trim()?.takeIf { it.isNotBlank() },
                venueId = vo.venueId?.trim()?.takeIf { it.isNotBlank() },
                venue = vo.venue?.trim()?.takeIf { it.isNotBlank() },
                organizer = vo.organizer?.trim()?.takeIf { it.isNotBlank() },
                collectionIds = normalizeIds(vo.collectionIds),
                coverImage = vo.coverImage?.trim()?.takeIf { it.isNotBlank() },
                attachmentIds = normalizeIds(vo.attachmentIds),
                startDate = startDate,
                endDate = endDate,
                state = vo.state ?: EnumProcessState.DRAFT,
                remark = vo.remark?.trim()?.takeIf { it.isNotBlank() },
            )
        }

        private fun normalizeIds(value: String?): String? =
            value?.split(",", "，", ";", "；", "\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.joinToString(",")
                ?.takeIf { it.isNotBlank() }
    }
}

internal data class ExhibitionVenueValues(
    val id: String?,
    val name: String?,
)

/** 展馆选择必须引用本收藏单位的展陈位置；无 ID 时仅兼容历史自由文本。 */
internal object ExhibitionVenueSelectionPolicy {
    fun resolve(
        venueId: String?,
        legacyVenue: String?,
        orgId: String,
        selected: WarehouseVo?,
    ): ExhibitionVenueValues {
        val normalizedId = venueId?.trim()?.takeIf { it.isNotBlank() }
            ?: return ExhibitionVenueValues(null, legacyVenue?.trim()?.takeIf { it.isNotBlank() })
        val venue = selected ?: throw BadRequestException("所选展馆不存在")
        if (venue.id != normalizedId) throw BadRequestException("所选展馆不存在")
        if (venue.orgId != orgId) throw BadRequestException("展馆与展览不属于同一收藏单位")
        if (venue.sceneType != EnumLocationScene.EXHIBITION || venue.type != EnumWarehouseType.EXHIBITION_HALL) {
            throw BadRequestException("展览地点只能选择展陈位置中的展馆/展厅")
        }
        val name = venue.name?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("所选展馆名称不能为空")
        return ExhibitionVenueValues(normalizedId, name)
    }
}

/** 展览项目管理。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class ExhibitionService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val requisitionService: RequisitionService,
    private val businessItemService: CollectionBusinessItemService,
    private val operationLockService: CollectionOperationLockService,
    private val placementService: CollectionPlacementService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<ExhibitionDao>(ExhibitionDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_EXHIBITION
    override fun definitionKey() = CollectionConst.FLOW_EXHIBITION
    override fun businessTypeName() = "藏品展览"
    override fun authorizeUserOperation(entity: ExhibitionDao, operation: FlowUserOperation) =
        scope.requireFlowAccess(entity.orgId, operation, "展览项目")
    override fun buildTitle(entity: ExhibitionDao) = "藏品展览：${entity.title}"
    override fun buildFormData(entity: ExhibitionDao) = mapOf(
        "title" to entity.title,
        "exhibitionNo" to entity.exhibitionNo,
        "venueId" to entity.venueId,
        "venue" to entity.venue,
        "organizer" to entity.organizer,
        "collectionIds" to entity.collectionIds,
        "placements" to placementService.findExhibitionPlacements(entity.id.value),
        "coverImage" to entity.coverImage,
        "attachmentIds" to entity.attachmentIds,
        "startDate" to entity.startDate,
        "endDate" to entity.endDate,
        "state" to entity.state,
        "remark" to entity.remark,
    )

    protected override fun acquireApprovalResources(entity: ExhibitionDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, splitCollectionIds(entity.collectionIds))

    protected override fun releaseApprovalResources(entity: ExhibitionDao) =
        operationLockService.release(businessType(), entity.id.value)

    protected override fun beforeSubmit(entity: ExhibitionDao) {
        placementService.requireComplete(entity)
    }

    override fun beforeFlowReentry(entity: ExhibitionDao, targetNodeKey: String) {
        if (targetNodeKey != "approve") throw BadRequestException("展览重新审批须从保管部门审核开始")
        if (entity.state != EnumProcessState.DRAFT || CollectionPlacementDao.find {
                (CollectionPlacements.sourceBizType eq businessType()) and (CollectionPlacements.sourceBizId eq entity.id.value)
            }.any { it.validFrom != null || it.validTo != null } || Requisitions.selectAll().where {
                (Requisitions.sourceBizType eq businessType()) and (Requisitions.sourceBizId eq entity.id.value) and Requisitions.flowInstanceId.isNotNull()
            }.limit(1).any()) {
            throw BadRequestException("展览已经实施或提用已经进入办理，请保留原批准与实物记录，后续变更另立方案")
        }
        placementService.requireComplete(entity)
        placementService.setExhibitionApproved(entity.id.value, false)
    }

    fun findPage(request: HttpServletRequest): GenericPage<ExhibitionVo> {
        val page = GenericPage<ExhibitionVo>().build(request)
        val filters = ExhibitionQueryFilters.from(request::getParameter)
        val q = Exhibitions.selectAll()
        scope.readableOrgIds()?.let { ids -> q.andWhere { Exhibitions.orgId inList ids } }
        filters.keyword?.let { kw ->
            val matchedCollectionIds = collectionIdsMatchingKeyword(kw)
            val matchedBusinessIds = businessItemService.businessIdsForCollections(
                CollectionConst.BIZ_EXHIBITION,
                matchedCollectionIds + kw,
            )
            val collectionMatch: Op<Boolean> = Exhibitions.id inList matchedBusinessIds.ifEmpty { setOf("__NO_COLLECTION_MATCH__") }
            q.andWhere {
                (Exhibitions.title like "%$kw%") or
                    collectionMatch or
                    (Exhibitions.exhibitionNo like "%$kw%") or
                    (Exhibitions.venue like "%$kw%") or
                    (Exhibitions.organizer like "%$kw%") or
                    (Exhibitions.remark like "%$kw%")
            }
        }
        filters.title?.let { q.andWhere { Exhibitions.title like "%$it%" } }
        filters.collectionId?.let {
            val businessIds = businessItemService.businessIdsForCollections(CollectionConst.BIZ_EXHIBITION, listOf(it))
            q.andWhere { Exhibitions.id inList businessIds.ifEmpty { setOf("__NO_COLLECTION_MATCH__") } }
        }
        filters.exhibitionNo?.let { q.andWhere { Exhibitions.exhibitionNo like "%$it%" } }
        filters.venueId?.let { q.andWhere { Exhibitions.venueId eq it } }
        filters.venue?.let { q.andWhere { Exhibitions.venue like "%$it%" } }
        filters.organizer?.let { q.andWhere { Exhibitions.organizer like "%$it%" } }
        filters.state?.let { q.andWhere { Exhibitions.state eq it } }
        filters.flowStatus?.let { q.andWhere { Exhibitions.flowStatus eq it } }
        filters.hasAttachment?.let { has ->
            if (has) q.andWhere { Exhibitions.attachmentIds.isNotNull() and Exhibitions.attachmentIds.neq("") }
            else q.andWhere { Exhibitions.attachmentIds.isNull() or Exhibitions.attachmentIds.eq("") }
        }
        filters.hasImage?.let { has ->
            if (has) q.andWhere { Exhibitions.coverImage.isNotNull() and Exhibitions.coverImage.neq("") }
            else q.andWhere { Exhibitions.coverImage.isNull() or Exhibitions.coverImage.eq("") }
        }
        filters.startDateStart?.let { q.andWhere { Exhibitions.startDate greaterEq it } }
        filters.startDateEnd?.let { q.andWhere { Exhibitions.startDate lessEq it } }
        filters.endDateStart?.let { q.andWhere { Exhibitions.endDate greaterEq it } }
        filters.endDateEnd?.let { q.andWhere { Exhibitions.endDate lessEq it } }
        q.orderBy(Exhibitions.startDate to SortOrder.DESC, Exhibitions.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<ExhibitionDao>)
        page.list = daoPage.data?.map { it.toModel() }
            ?.let { enrichCollectionSummaries(it) }
            ?.let { enrichPlacements(it) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<ExhibitionVo>
    }

    fun get(id: String): ExhibitionVo? =
        findById(id)
            ?.also { OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), it.orgId, "展览项目") }
            ?.toModel()
            ?.let { model ->
                enrichCollectionSummaries(listOf(model)).first().copy(
                    placements = placementService.findExhibitionPlacements(id),
                )
            }

    @CacheEvict(allEntries = true)
    fun save(vo: ExhibitionVo): String {
        val values = ExhibitionRecordValues.from(vo)
        val collections = validateCollections(values.collectionIds)
        val targetOrgId = vo.id?.takeIf(String::isNotBlank)?.let {
            findById(it)?.orgId ?: throw BadRequestException("展览项目不存在")
        } ?: collections.firstOrNull()?.orgId ?: scope.currentOrgId()
        checkWritable(targetOrgId)
        CollectionLocationMutation.acquire(targetOrgId)
        val selectedVenue = CollectionLocationMutation.reference(targetOrgId, values.venueId)
        val entity = saveDraft(vo.id, "展览项目不存在", createEntity = {
            dao.new {
                orgId = collections.firstOrNull()?.orgId ?: scope.currentOrgId()
            }
        }) {
            checkWritable(orgId)
            if (collections.isNotEmpty() && collections.any { it.orgId != orgId }) {
                throw BadRequestException("参展藏品必须属于同一收藏单位")
            }
            val venueValues = ExhibitionVenueSelectionPolicy.resolve(
                venueId = values.venueId,
                legacyVenue = values.venue,
                orgId = orgId,
                selected = selectedVenue?.toModel(),
            )
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_EXHIBITION, id.value)
            apply(values, venueValues)
        }
        originalFiles?.remember(CollectionConst.BIZ_EXHIBITION, entity.id.value)
        (BusinessAttachmentBindingPlanner.planSingle(entity.id.value, "展览封面影像", entity.coverImage) +
            BusinessAttachmentBindingPlanner.plan(entity.id.value, "展陈材料附件", entity.attachmentIds)).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_EXHIBITION, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        businessItemService.bind(entity.orgId, CollectionConst.BIZ_EXHIBITION, entity.id.value, entity.collectionIds)
        vo.placements?.let { placementService.replaceExhibitionPlacements(entity, it) }
            ?: placementService.syncExhibitionDates(entity)
        return entity.id.value
    }

    @CacheEvict(allEntries = true)
    fun deleteChecked(id: String) {
        val entity = findById(id) ?: return
        checkWritable(entity.orgId)
        requireFlowDeletable(entity, "展览项目")
        placementService.removeExhibitionPlacements(id)
        delete(id)
    }

    override fun onFlowCompleted(entity: ExhibitionDao, event: FlowCompletedEvent) {
        super.onFlowCompleted(entity, event)
        placementService.setExhibitionApproved(entity.id.value, true)
        requisitionService.ensureDraftFromExhibition(entity, event.initiator)
    }

    private fun validateCollections(collectionIds: String?): List<CollectionDao> {
        val ids = splitIds(collectionIds)
        val scopeIds = scope.writableOrgIds()
        return ids.map { id ->
            val coll = CollectionDao.findById(id) ?: throw BadRequestException("藏品不存在：$id")
            if (scopeIds != null && !scopeIds.contains(coll.orgId)) throw BadRequestException("无权引用藏品 ${coll.totalRegNo}")
            CollectionOutboundPolicy.requireInStore(coll.lifeStatus, "展览", coll.inventoryHold)
            coll
        }
    }

    private fun checkWritable(orgId: String) {
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "展览项目")
    }

    private fun splitIds(value: String?): List<String> = splitCollectionIds(value)

    private fun enrichCollectionSummaries(items: List<ExhibitionVo>): List<ExhibitionVo> {
        val ids = items.flatMap { splitCollectionIds(it.collectionIds) }.toSet()
        if (ids.isEmpty()) return items
        return MultiCollectionSummaryEnricher.enrichExhibitions(items, collectionSummaryById(ids))
    }

    private fun enrichPlacements(items: List<ExhibitionVo>): List<ExhibitionVo> {
        val exhibitionIds = items.mapNotNull { it.id }.toSet()
        if (exhibitionIds.isEmpty()) return items
        val locationNames = WarehouseDao.all().associate { it.id.value to it.name }
        val placementsByExhibition = CollectionPlacementDao.find {
            (CollectionPlacements.sourceBizType eq CollectionConst.BIZ_EXHIBITION) and
                (CollectionPlacements.sourceBizId inList exhibitionIds)
        }.toList().groupBy { it.sourceBizId }
        return items.map { item ->
            item.copy(
                placements = placementsByExhibition[item.id].orEmpty()
                    .sortedBy { it.sort }
                    .map { it.toExhibitionModel().copy(locationName = locationNames[it.locationId]) },
            )
        }
    }

    private fun collectionSummaryById(ids: Set<String>): Map<String, CollectionSummaryVo> {
        val q = Collections.selectAll().where { Collections.id inList ids }
        scope.readableOrgIds()?.let { orgIds -> q.andWhere { Collections.orgId inList orgIds } }
        return q.associate {
            it[Collections.id].value to CollectionSummaryVo(
                id = it[Collections.id].value,
                totalRegNo = it[Collections.totalRegNo],
                name = it[Collections.name],
            )
        }
    }

    private fun collectionIdsMatchingKeyword(keyword: String): List<String> {
        val q = Collections.selectAll()
        scope.readableOrgIds()?.let { ids -> q.andWhere { Collections.orgId inList ids } }
        q.andWhere {
            (Collections.totalRegNo like "%$keyword%") or
                (Collections.name like "%$keyword%")
        }
        return q.map { it[Collections.id].value }
    }

    private fun ExhibitionDao.apply(values: ExhibitionRecordValues, venueValues: ExhibitionVenueValues) {
        title = values.title
        exhibitionNo = values.exhibitionNo
        venueId = venueValues.id
        venue = venueValues.name
        organizer = values.organizer
        collectionIds = values.collectionIds
        coverImage = values.coverImage
        attachmentIds = values.attachmentIds
        startDate = values.startDate
        endDate = values.endDate
        state = values.state
        remark = values.remark
    }
}
