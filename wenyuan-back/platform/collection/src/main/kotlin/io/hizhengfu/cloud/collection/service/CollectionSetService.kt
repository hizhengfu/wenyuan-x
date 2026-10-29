package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionSetDao
import io.hizhengfu.cloud.collection.dao.CollectionSetEntryResultVo
import io.hizhengfu.cloud.collection.dao.CollectionSetEntryVo
import io.hizhengfu.cloud.collection.dao.CollectionSetVo
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.CollectionSets
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumSetRelation
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

internal object CollectionSetEntryPolicy {
    fun requireValid(vo: CollectionSetEntryVo) {
        val main = vo.main ?: throw BadRequestException("主件信息必填")
        if (main.name.isNullOrBlank()) throw BadRequestException("主件名称必填")
        if (main.categoryId.isNullOrBlank()) throw BadRequestException("主件文物分类必填")
        if (main.level == null) throw BadRequestException("主件藏品级别必填")
        if (main.source == null) throw BadRequestException("主件来源方式必填")
        if (main.quantity == null || main.quantity <= 0) throw BadRequestException("主件数量必须大于0")
        if (main.unit.isNullOrBlank()) throw BadRequestException("主件计量单位必填")
        if (vo.parts.isEmpty()) throw BadRequestException("请至少录入一个分件")
        val partNos = mutableSetOf<String>()
        vo.parts.forEachIndexed { index, part ->
            val partNo = part.partNo?.trim()?.takeIf(String::isNotBlank)
                ?: throw BadRequestException("第 ${index + 1} 个分件的分件号必填")
            if (!partNos.add(partNo)) throw BadRequestException("分件号重复：$partNo")
            if (part.partName.isNullOrBlank()) throw BadRequestException("第 ${index + 1} 个分件名称必填")
            if (part.relation == EnumSetRelation.MAIN) throw BadRequestException("分件关系不能选择主件")
            if (part.quantity <= 0) throw BadRequestException("第 ${index + 1} 个分件数量必须大于0")
            if (part.unit.isBlank()) throw BadRequestException("第 ${index + 1} 个分件计量单位必填")
        }
    }
}

@Component
@Transactional
class CollectionSetService(
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val locks: CollectionOperationLockService = CollectionOperationLockService(),
) : CrudService<CollectionSetDao>(CollectionSetDao) {
    private val directory = CollectionActorDirectory(scope)

    private fun requireAccess(orgId: String, write: Boolean) {
        CollectionCurrentOperationAccess.require(scope, orgId, write,
            if (write) setOf(CollectionConst.LEDGER_CREATE) else setOf(CollectionConst.SELECT), "藏品套件关系")
        if (write) {
            val current = directory.requireActive(SecurityUtils.getUserDetail().id)
            CollectionDutySeparationPolicy.requireLedgerOperation(current.permissions, current.roleCodes)
        }
    }

    fun list(collectionId: String): List<CollectionSetVo> {
        val parent = requireCollection(collectionId, writable = false)
        return dao.find { CollectionSets.parentCollectionId eq parent.id.value }
            .orderBy(CollectionSets.sort to SortOrder.ASC, CollectionSets.partNo to SortOrder.ASC)
            .map { requireRelation(it, parent.orgId); it.toModel() }
    }

    fun save(collectionId: String, vo: CollectionSetVo): String {
        val parent = requireCollection(collectionId)
        CollectionSetAmendments.coordinate(parent.orgId)
        val relation = vo.relation ?: throw BadRequestException("套件关系必填")
        val partNo = vo.partNo?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("分件号必填")
        val partName = vo.partName?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("分件名称必填")
        if (partNo.length > 32) throw BadRequestException("分件号不能超过32个字符")
        val quantity = vo.quantity ?: 1
        if (quantity <= 0) throw BadRequestException("分件数量必须大于0")
        val unit = vo.unit?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("分件计量单位必填")
        val currentId = vo.id?.trim()?.takeIf(String::isNotBlank)
        val existing = currentId?.let { id ->
            dao.find { (CollectionSets.id eq id) and (CollectionSets.parentCollectionId eq parent.id.value) }.firstOrNull()
                ?.also { requireRelation(it, parent.orgId) } ?: throw BadRequestException("套件关系不存在")
        }
        if (dao.find {
                (CollectionSets.parentCollectionId eq parent.id.value) and (CollectionSets.partNo eq partNo)
            }.any { it.id.value != currentId }) {
            throw BadRequestException("同一套藏品的分件号不能重复：$partNo")
        }
        val childId = vo.childCollectionId?.takeIf { it.isNotBlank() }
        requireDraftRelations(parent, listOfNotNull(existing?.childCollectionId, childId))
        childId?.let {
            val child = requireCollection(it)
            if (child.orgId != parent.orgId) throw BadRequestException("附件/分件必须属于同一收藏单位")
            if (child.id.value == parent.id.value) throw BadRequestException("附件/分件不能指向主件自身")
            if (dao.find { CollectionSets.childCollectionId eq child.id.value }.any { row -> row.id.value != currentId }) {
                throw BadRequestException("该藏品已作为其他套件的分件")
            }
            requireNoCycle(parent.id.value, child.id.value, currentId, parent.orgId)
        }
        val entity = existing ?: dao.new { orgId = parent.orgId }
        entity.apply {
            orgId = parent.orgId
            parentCollectionId = parent.id.value
            childCollectionId = childId
            this.partNo = partNo
            this.partName = partName
            this.relation = relation
            this.quantity = quantity
            this.unit = unit
            sort = vo.sort ?: 0
        }
        return entity.id.value
    }

    /** 一次登记主件及全部独立分件，并建立结构化套件关系。 */
    fun createSetEntry(vo: CollectionSetEntryVo): CollectionSetEntryResultVo {
        val current = directory.requireActive(SecurityUtils.getUserDetail().id)
        val orgId = scope.currentOrgId(current)
        requireAccess(orgId, write = true)
        CollectionSetEntryPolicy.requireValid(vo)
        val main = vo.main!!
        val mainId = collectionService.createForOrg(orgId, main.copy(lifeStatus = EnumCollectionStatus.PENDING))
        val childIds = mutableListOf<String>()
        val relationIds = mutableListOf<String>()
        vo.parts.forEachIndexed { index, part ->
            val childInput = part.collection ?: io.hizhengfu.cloud.collection.dao.CollectionVo()
            val child = childInput.copy(
                name = childInput.name?.takeIf(String::isNotBlank) ?: part.partName?.trim(),
                categoryId = childInput.categoryId?.takeIf(String::isNotBlank) ?: main.categoryId,
                era = childInput.era ?: main.era,
                texture = childInput.texture ?: main.texture,
                level = childInput.level ?: main.level,
                quantity = part.quantity,
                unit = part.unit.trim(),
                source = childInput.source ?: main.source,
                sourceDesc = childInput.sourceDesc ?: main.sourceDesc,
                acquireDate = childInput.acquireDate ?: main.acquireDate,
                acquireDateRange = childInput.acquireDateRange ?: main.acquireDateRange,
                acquireYear = childInput.acquireYear ?: main.acquireYear,
                completeness = childInput.completeness ?: main.completeness,
                preserveState = childInput.preserveState ?: main.preserveState,
                warehouseId = childInput.warehouseId ?: main.warehouseId,
                lifeStatus = EnumCollectionStatus.PENDING,
                remark = childInput.remark ?: "套件分件：${part.partNo}",
            )
            val childId = collectionService.createForOrg(orgId, child)
            childIds += childId
            relationIds += save(
                mainId,
                CollectionSetVo(
                    childCollectionId = childId,
                    partNo = part.partNo,
                    partName = part.partName,
                    relation = part.relation,
                    quantity = part.quantity,
                    unit = part.unit,
                    sort = part.sort ?: index,
                ),
            )
        }
        return CollectionSetEntryResultVo(mainId, childIds, relationIds)
    }

    fun delete(collectionId: String, id: String) {
        val parent = requireCollection(collectionId)
        CollectionSetAmendments.coordinate(parent.orgId)
        val entity = dao.find { (CollectionSets.id eq id) and (CollectionSets.parentCollectionId eq parent.id.value) }.firstOrNull()
            ?: throw BadRequestException("套件关系不存在")
        requireRelation(entity, parent.orgId)
        requireDraftRelations(parent, listOfNotNull(entity.childCollectionId))
        entity.delete()
    }

    private fun requireDraftRelations(parent: CollectionDao, childIds: List<String>) {
        val members = (listOf(parent.id.value) + childIds).distinct().sorted().map { id ->
            locks.requireUnlocked(id)
            requireCollection(id)
            Collections.selectAll().where { Collections.id eq id }.single().also {
                if (it[Collections.orgId] != parent.orgId) throw BadRequestException("套件关联藏品所属收藏单位已变化，请重新核对")
            }
        }
        if (members.any { (it[Collections.registeredAt] != null || it[Collections.historicalConfirmedAt] != null) || it[Collections.catalogStatus] == io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.APPROVED })
            throw BadRequestException("正式藏品的套件关系须通过信息变更申请办理，批准并签署纸账订正后生效")
        if (members.any { it[Collections.catalogStatus] in setOf(io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.SUBMITTED,
                io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.PENDING) })
            throw BadRequestException("套件关联藏品正在编目审核，请先完成或驳回审核")
    }

    private fun requireCollection(id: String, writable: Boolean = true): CollectionDao {
        val collection = CollectionDao.findById(id) ?: throw BadRequestException("藏品不存在")
        requireAccess(collection.orgId, writable)
        return collection
    }

    /** 历史关系不能借当前主件归属改写馆别，也不能返回外馆或已缺失分件的资料。 */
    private fun requireRelation(row: CollectionSetDao, orgId: String) {
        if (row.orgId != orgId) throw BadRequestException("套件关系所属收藏单位不一致，请保留原关系核对")
        for (id in listOfNotNull(row.parentCollectionId, row.childCollectionId)) {
            val item = CollectionDao.findById(id) ?: throw BadRequestException("套件关联藏品缺失，请保留原关系核对")
            if (item.orgId != orgId) throw BadRequestException("套件关联藏品所属收藏单位不一致，请保留原关系核对")
        }
    }

    private fun requireNoCycle(parentId: String, childId: String, editingId: String?, orgId: String) {
        val visited = mutableSetOf<String>()
        var frontier = setOf(childId)
        while (frontier.isNotEmpty()) {
            if (parentId in frontier) throw BadRequestException("套件关系不能形成循环引用")
            visited += frontier
            frontier = dao.find { CollectionSets.parentCollectionId inList frontier }
                .filterNot { it.id.value == editingId }
                .onEach { requireRelation(it, orgId) }
                .mapNotNull { it.childCollectionId }
                .filterNot(visited::contains)
                .toSet()
        }
    }
}
