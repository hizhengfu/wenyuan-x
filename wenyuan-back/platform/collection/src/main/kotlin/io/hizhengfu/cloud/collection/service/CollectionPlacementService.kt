package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionPlacementDao
import io.hizhengfu.cloud.collection.dao.CollectionPlacements
import io.hizhengfu.cloud.collection.dao.ExhibitionDao
import io.hizhengfu.cloud.collection.dao.ExhibitionPlacementVo
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.LocalTime

internal data class ExhibitionPlacementPlan(
    val collectionId: String,
    val locationId: String,
    val sort: Int,
)

internal object ExhibitionPlacementPlanner {
    fun plan(placements: List<ExhibitionPlacementVo>): List<ExhibitionPlacementPlan> {
        val rows = placements.mapIndexed { index, row ->
            ExhibitionPlacementPlan(
                collectionId = row.collectionId?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw BadRequestException("展柜分配缺少藏品"),
                locationId = row.locationId?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw BadRequestException("每件参展藏品都必须选择展柜"),
                sort = index,
            )
        }
        val duplicateCollection = rows.groupingBy { it.collectionId }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicateCollection != null) throw BadRequestException("同一参展藏品不能重复分配展柜")
        return rows
    }
}

/** 展览藏品的展柜分配；位置主数据仍由 WarehouseService 维护。 */
@Component
@Transactional
class CollectionPlacementService {

    fun replaceExhibitionPlacements(entity: ExhibitionDao, placements: List<ExhibitionPlacementVo>) {
        // 草稿允许尚未完成全部展柜分配；提交审批时由 requireComplete 做完整性校验。
        val plans = ExhibitionPlacementPlanner.plan(placements.filter { !it.locationId.isNullOrBlank() })
        val selectedIds = splitCollectionIds(entity.collectionIds).toSet()
        val assignedIds = plans.map { it.collectionId }.toSet()
        val outside = assignedIds - selectedIds
        if (outside.isNotEmpty()) throw BadRequestException("展柜分配包含未选中的参展藏品")

        CollectionLocationMutation.acquire(entity.orgId)
        plans.forEach { plan -> requireShowcase(plan.locationId, entity.orgId) }
        removeExhibitionPlacements(entity.id.value)
        plans.forEach { plan ->
            CollectionPlacementDao.new {
                orgId = entity.orgId
                sourceBizType = CollectionConst.BIZ_EXHIBITION
                sourceBizId = entity.id.value
                collectionId = plan.collectionId
                locationId = plan.locationId
                validFrom = null
                validTo = entity.endDate?.atTime(LocalTime.MAX)
                approved = false
                sort = plan.sort
            }
        }
    }

    fun syncExhibitionDates(entity: ExhibitionDao) {
        findEntities(entity.id.value).forEach {
            it.validTo = entity.endDate?.atTime(LocalTime.MAX)
        }
    }

    fun findExhibitionPlacements(exhibitionId: String): List<ExhibitionPlacementVo> {
        val locations = WarehouseDao.all().associateBy { it.id.value }
        return findEntities(exhibitionId).map { placement ->
            placement.toExhibitionModel().copy(locationName = locations[placement.locationId]?.name)
        }
    }

    fun requireComplete(entity: ExhibitionDao) {
        val selectedIds = splitCollectionIds(entity.collectionIds).toSet()
        if (selectedIds.isEmpty()) throw BadRequestException("请至少选择一件参展藏品")
        val placements = findEntities(entity.id.value)
        val assignedIds = placements.map { it.collectionId }.toSet()
        if (assignedIds != selectedIds) {
            throw BadRequestException("每件参展藏品都必须且只能分配一个展柜")
        }
        placements.forEach { requireShowcase(it.locationId, entity.orgId) }
    }

    fun setExhibitionApproved(exhibitionId: String, approved: Boolean) {
        findEntities(exhibitionId).forEach { it.approved = approved }
    }

    /** 展览提用实际出库完成后，藏品才开始计入展柜内。 */
    fun openExhibitionPlacements(exhibitionId: String, collectionIds: Collection<String>, placedAt: LocalDateTime) {
        if (collectionIds.isEmpty()) return
        val ids = collectionIds.toSet()
        findEntities(exhibitionId)
            .filter { it.collectionId in ids }
            .forEach { placement ->
                if (placement.validFrom == null || placement.validFrom!!.isAfter(placedAt)) {
                    placement.validFrom = placedAt
                }
            }
    }

    /** 部分或全部归库时只截断对应藏品的展柜有效期，保留既有展陈位置历史。 */
    fun closeExhibitionPlacements(exhibitionId: String, collectionIds: Collection<String>, returnedAt: LocalDateTime) {
        if (collectionIds.isEmpty()) return
        val ids = collectionIds.toSet()
        findEntities(exhibitionId)
            .filter { it.collectionId in ids }
            .forEach { placement ->
                if (placement.validTo == null || placement.validTo!!.isAfter(returnedAt)) {
                    placement.validTo = returnedAt
                }
            }
    }

    fun removeExhibitionPlacements(exhibitionId: String) {
        findEntities(exhibitionId).forEach { it.delete() }
    }

    private fun findEntities(exhibitionId: String): List<CollectionPlacementDao> =
        CollectionPlacementDao.find {
            (CollectionPlacements.sourceBizType eq CollectionConst.BIZ_EXHIBITION) and
                (CollectionPlacements.sourceBizId eq exhibitionId)
        }.orderBy(CollectionPlacements.sort to SortOrder.ASC).toList()

    private fun requireShowcase(locationId: String, orgId: String): WarehouseDao {
        val location = WarehouseDao.findById(locationId) ?: throw BadRequestException("展柜不存在：$locationId")
        try {
            location.refresh(flush = true)
        } catch (_: EntityNotFoundException) {
            throw BadRequestException("展柜已被删除，请重新分配展柜")
        }
        WarehouseTreeStructure.ancestors(location.parentId, orgId, EnumLocationScene.EXHIBITION, setOf(locationId))
        if (location.orgId != orgId) throw BadRequestException("展柜与展览不属于同一收藏单位")
        if (location.status != io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL)
            throw BadRequestException("展柜已退役，请重新选择有效展柜")
        if (location.sceneType != EnumLocationScene.EXHIBITION || location.type != EnumWarehouseType.SHOWCASE) {
            throw BadRequestException("参展藏品只能分配到展陈位置中的展柜")
        }
        return location
    }
}
