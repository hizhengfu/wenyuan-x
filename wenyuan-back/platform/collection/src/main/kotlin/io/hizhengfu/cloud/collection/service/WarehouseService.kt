package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.dao.WarehouseVo
import io.hizhengfu.cloud.collection.dao.Warehouses
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionImportTemplateColumnVo
import io.hizhengfu.cloud.collection.domain.WarehouseImportCommitResultVo
import io.hizhengfu.cloud.collection.domain.WarehouseImportRowVo
import io.hizhengfu.cloud.collection.domain.WarehouseImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.WarehouseLocationCheckVo
import io.hizhengfu.cloud.collection.domain.WarehouseLocationIssueVo
import io.hizhengfu.cloud.collection.domain.WarehouseLocationPolicy
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.collection.domain.WarehouseLocationCorrectionVo
import io.hizhengfu.cloud.collection.domain.WarehouseLocationCorrectionResultVo
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.TreeCrudService
import io.hizhengfu.cloud.museum.device.dao.MuseumAlarmRules
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumLocationSources
import io.hizhengfu.cloud.museum.device.dao.MuseumLocations
import io.hizhengfu.cloud.museum.device.domain.EnumAlarmRuleRuntimeStatus
import io.hizhengfu.cloud.museum.device.service.MuseumIngestConfigService
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 库房库位（树）管理。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_WAREHOUSE])
class WarehouseService(
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val originalFiles: CollectionBusinessOriginalFiles? = null,
    private val locationReferences: CollectionLocationReferenceGuard = CollectionLocationReferenceGuard(),
    private val archives: CollectionPermanentArchiveService? = null,
    private val ingestConfig: MuseumIngestConfigService? = null,
) : TreeCrudService<WarehouseDao>(WarehouseDao, CollectionConst.CACHE_WAREHOUSE) {

    companion object { const val CORRECTION_BIZ = "COLLECTION_LOCATION_CORRECTION" }

    /** 当前馆的库位（多馆：仅本馆；单馆：全部）。 */
    fun findAll(): List<WarehouseVo> {
        val ids = scope.readableOrgIds()
        val list = if (ids == null) dao.find { Warehouses.status eq EnumContentStatus.NORMAL }.toList()
        else dao.find { (Warehouses.orgId inList ids) and (Warehouses.status eq EnumContentStatus.NORMAL) }.toList()
        return list.sortedWith(compareBy({ it.parentIds }, { it.sort })).map { it.toModel() }
    }

    fun get(id: String): WarehouseVo? =
        findById(id)
            ?.also { OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), it.orgId, "库位") }
            ?.toModel()

    fun importTemplate(): List<CollectionImportTemplateColumnVo> = listOf(
        CollectionImportTemplateColumnVo("rowNo", "行号", sample = "2"),
        CollectionImportTemplateColumnVo("parentName", "上级库位名称/路径", sample = "一号库房/A区"),
        CollectionImportTemplateColumnVo("parentCode", "上级库位编码", sample = "W-01"),
        CollectionImportTemplateColumnVo("code", "库位编码", sample = "W-01-A"),
        CollectionImportTemplateColumnVo("name", "库位名称", required = true, sample = "A 区"),
        CollectionImportTemplateColumnVo("thumbnailName", "缩略图文件名", sample = "库位照片.jpg"),
        CollectionImportTemplateColumnVo("sceneType", "位置场景", dictType = CollectionConst.DICT_LOCATION_SCENE, sample = "保管"),
        CollectionImportTemplateColumnVo("type", "位置类型（依据场景）", dictType = CollectionConst.DICT_WAREHOUSE_TYPE, sample = "库区"),
        CollectionImportTemplateColumnVo("capacity", "容量", sample = "120"),
        CollectionImportTemplateColumnVo("temperature", "温湿度", sample = "18-22℃ / 45-55%RH"),
        CollectionImportTemplateColumnVo("custodian", "保管责任人", sample = "张三"),
        CollectionImportTemplateColumnVo("sort", "排序", sample = "10"),
    )

    fun validateImport(rows: List<WarehouseImportRowVo>): WarehouseImportValidateResultVo {
        val org = scope.currentOrgId()
        val visible = WarehouseDao.all().filter { it.orgId == org && it.status == EnumContentStatus.NORMAL }.map { it.toModel() }
        val ids = visible.mapNotNull { it.id }.toSet()
        val codeGroups = visible.filter { !it.code.isNullOrBlank() }.groupBy({ it.code!!.trim() }, { it.id!! })
        val byCode = codeGroups.filterValues { it.distinct().size == 1 }.mapValues { it.value.single() }
        val names = CollectionImportNameResolver(visible.map { CollectionImportNameResolver.Entry(it.id!!, it.name.orEmpty(), it.parentId) })
        val errors = mutableListOf<CollectionImportErrorVo>()
        val normalized = rows.mapIndexed { index, row ->
            val refs = CollectionImportRowReferences(row.rowNo.takeIf { it > 0 } ?: index + 2, errors)
            row.copy(
                parentId = refs.read("parentName") {
                    val named = names.resolve(row.parentName, row.parentId, "上级库位")
                    refs.byCode(row.parentCode, named, codeGroups, "上级库位")
                },
                thumbnail = refs.read("thumbnailName") {
                    if (row.thumbnailName.isNullOrBlank()) row.thumbnail else {
                        val id = attachmentBinder.resolveByName(row.thumbnailName)
                        if (!row.thumbnail.isNullOrBlank() && attachmentBinder.resolveSingle(row.thumbnail) != id)
                            throw BadRequestException("缩略图文件名与旧文件引用不一致")
                        id
                    }
                },
            )
        }
        val result = WarehouseImportValidator.validateRows(normalized, ids, byCode)
        return if (errors.isEmpty()) result else result.copy(valid = false, errors = result.errors + errors, items = emptyList())
    }

    fun commitImport(rows: List<WarehouseImportRowVo>): WarehouseImportCommitResultVo {
        CollectionLocationMutation.acquire(scope.currentOrgId())
        val result = validateImport(rows)
        if (!result.valid) return WarehouseImportCommitResultVo(valid = false, errors = result.errors)
        val ids = result.items.map { create(it) }
        return WarehouseImportCommitResultVo(valid = true, createdIds = ids)
    }

    @CacheEvict(allEntries = true)
    fun create(vo: WarehouseVo): String = createForOrg(vo, scope.currentOrgId())

    private fun createForOrg(vo: WarehouseVo, orgId: String): String {
        val name = vo.name?.takeIf { it.isNotBlank() } ?: throw BadRequestException("名称必填")
        val type = vo.type ?: WarehouseLocationPolicy.defaultType(vo.sceneType ?: EnumLocationScene.STORAGE)
        val scene = vo.sceneType ?: WarehouseLocationPolicy.defaultScene(type)
        CollectionLocationMutation.acquire(orgId)
        WarehouseLocationPolicy.requireValidCode(type, vo.code)
        WarehouseLocationPolicy.requireSceneCompatible(scene, type)
        val parentId = vo.parentId?.takeIf(String::isNotBlank) ?: treeRootId
        requireValidFields(orgId, parentId, vo)
        val parents = WarehouseTreeStructure.ancestors(parentId, orgId, scene)
        val parentPath = WarehouseTreeStructure.parentPath(parents)
        val node = dao.new {
            this.orgId = orgId
            this.name = name
            thumbnail = vo.thumbnail?.trim()?.ifBlank { null }
            code = vo.code?.trim()?.takeIf(String::isNotBlank)
            sceneType = scene
            this.type = type
            capacity = vo.capacity
            temperature = vo.temperature
            custodian = vo.custodian
            sort = vo.sort ?: 0
            this.parentId = parentId
            parentIds = parentPath
        }
        parents.lastOrNull()?.isLeaf = false
        attachmentBinder.bindSingle(node.id.value, "位置缩略图", node.thumbnail)
        originalFiles?.remember(CollectionBusinessOriginalFileSource.WAREHOUSE, node.id.value)
        return node.id.value
    }

    @CacheEvict(allEntries = true)
    fun update(id: String, vo: WarehouseVo): String {
        val node = findById(id) ?: throw BadRequestException("库位不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), node.orgId, "库位")
        CollectionLocationMutation.acquire(node.orgId)
        node.refresh(flush = true)
        if (node.status != EnumContentStatus.NORMAL) throw BadRequestException("位置已退役，不能修改历史位置")
        val type = vo.type ?: node.type
        val scene = vo.sceneType ?: node.sceneType
        WarehouseLocationPolicy.requireValidCode(type, vo.code)
        WarehouseLocationPolicy.requireSceneCompatible(scene, type)
        val targetParentId = vo.parentId?.takeIf(String::isNotBlank) ?: node.parentId
        requireValidFields(node.orgId, targetParentId, vo, id)
        val structuralChange = type != node.type || scene != node.sceneType || targetParentId != node.parentId
        val branch = if (structuralChange) WarehouseTreeStructure.subtree(node) else emptyList()
        val branchIds = branch.map { it.id.value }.toSet()
        val parents = WarehouseTreeStructure.ancestors(targetParentId, node.orgId, scene, branchIds + id)
        val paths = if (structuralChange) {
            WarehouseTreeStructure.ancestors(node.parentId, node.orgId, node.sceneType, branchIds)
            if (branch.any { it.staffing != null }) throw BadRequestException("位置分支已登记库管任职，不能直接改变保管位置结构")
            if (branch.drop(1).any { it.sceneType != scene }) throw BadRequestException("下级位置与目标场景不一致，不能直接切换位置场景")
            locationReferences.requireUnreferenced(branchIds)
            WarehouseTreeStructure.paths(branch, parents)
        } else emptyMap()
        val oldParentId = node.parentId
        originalFiles?.rememberExistingBusiness(CollectionBusinessOriginalFileSource.WAREHOUSE, id)
        vo.name?.takeIf { it.isNotBlank() }?.let { node.name = it }
        node.code = vo.code?.trim()?.takeIf(String::isNotBlank)
        node.thumbnail = vo.thumbnail?.trim()?.ifBlank { null }
        node.sceneType = scene
        node.type = type
        node.capacity = vo.capacity
        node.temperature = vo.temperature
        node.custodian = vo.custodian
        vo.sort?.let { node.sort = it }
        if (structuralChange) {
            node.parentId = targetParentId
            branch.forEach { it.parentIds = paths.getValue(it.id.value) }
            parents.lastOrNull()?.isLeaf = false
            refreshLeaf(oldParentId)
        }
        BusinessAttachmentBindingPlanner.planSingle(id, "位置缩略图", node.thumbnail).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionBusinessOriginalFileSource.WAREHOUSE, id, node.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(id, item.aliasName, item.fileId)
        }
        originalFiles?.remember(CollectionBusinessOriginalFileSource.WAREHOUSE, id)
        return id
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WarehouseDao {
        val existing=findById(id) ?: throw BadRequestException("库房不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(),existing.orgId,"库房")
        CollectionLocationMutation.acquire(existing.orgId)
        existing.refresh(flush = true)
        if(existing.status!=EnumContentStatus.NORMAL) throw BadRequestException("位置已退役，不能删除历史位置")
        if(existing.staffing!=null) throw BadRequestException("库房已形成实际任职和保管档案，不能删除")
        val referencedBy = CollectionOrgDao.find { CollectionOrgs.locationRootId eq id }
            .limit(1)
            .firstOrNull()
        if (referencedBy != null) {
            throw BadRequestException("该位置已作为收藏单位「${referencedBy.name}」的关联安装位置，不能删除")
        }
        if (dao.find { Warehouses.parentId eq id }.limit(1).any()) throw BadRequestException("有下级位置不能删除")
        WarehouseTreeStructure.ancestors(existing.parentId, existing.orgId, existing.sceneType, setOf(id))
        locationReferences.requireUnreferenced(id)
        originalFiles?.rememberExistingBusiness(CollectionBusinessOriginalFileSource.WAREHOUSE, id)
        val deleted = super.delete(id)
        refreshLeaf(existing.parentId)
        return deleted
    }

    /** 历史位置不换 ID、不改类型；新建展厅并用永久原件关联两个版本。 */
    @CacheEvict(allEntries = true)
    fun retireAsExhibition(id: String, request: WarehouseLocationCorrectionVo): WarehouseLocationCorrectionResultVo {
        val old = findById(id) ?: throw BadRequestException("待订正位置不存在")
        CollectionCurrentOperationAccess.require(scope, old.orgId, true, setOf(CollectionConst.WAREHOUSE_MANAGE), "位置订正")
        CollectionLocationMutation.acquire(old.orgId)
        old.refresh(flush = true)
        if (old.status != EnumContentStatus.NORMAL || old.sceneType != EnumLocationScene.STORAGE ||
            old.type !in setOf(EnumWarehouseType.WAREHOUSE, EnumWarehouseType.AREA))
            throw BadRequestException("只可将仍在当前目录的误分类保管库房订正为展厅")
        if (old.staffing != null || WarehouseDao.find { Warehouses.parentId eq id }.any())
            throw BadRequestException("位置已有任职或下级位置，须先核对实际保管责任及位置结构")
        if (CollectionOrgDao.find { CollectionOrgs.locationRootId eq id }.any())
            throw BadRequestException("位置仍为收藏单位安装根，不能退役")
        locationReferences.requireNoBusinessReferences(id)
        val successor = request.replacement
        if (successor.id != null || successor.orgId != null || successor.staffing != null ||
            successor.parentId?.let { it != "0" } == true ||
            successor.sceneType != EnumLocationScene.EXHIBITION || successor.type != EnumWarehouseType.EXHIBITION_HALL)
            throw BadRequestException("订正后的新位置必须是本馆独立展厅，不能复用旧位置 ID")
        val reason = request.reason.trim()
        if (reason.isBlank() || request.signedFileIds.isBlank())
            throw BadRequestException("位置订正须填写原因并上传签署依据")
        val archiveService = archives ?: throw BadRequestException("位置订正永久档案服务未启用")
        val mappings = if (MuseumLocationSources.exists()) MuseumLocationSources.selectAll().where {
            (MuseumLocationSources.sourceType eq "BUSINESS_MASTER") and
                (MuseumLocationSources.sourceCode eq "COL_WAREHOUSE") and (MuseumLocationSources.externalId eq id)
        }.toList() else emptyList()
        if (mappings.any { it[MuseumLocationSources.orgId] != old.orgId })
            throw BadRequestException("该位置还映射其他场馆，须先核对跨馆位置承接")
        if (mappings.isNotEmpty() && ingestConfig == null)
            throw BadRequestException("采集位置同步服务未启用，不能留下未同步的退役映射")
        val unifiedIds = mappings.map { it[MuseumLocationSources.locationId] }.distinct()
        val db = TransactionManager.current().db
        unifiedIds.forEach { locationId ->
            val query = MuseumLocations.selectAll().where { MuseumLocations.id eq locationId }
            val row = if (db.supportsSelectForUpdate && !db.vendor.contains("sqlite", ignoreCase = true))
                query.forUpdate().singleOrNull() else query.singleOrNull()
            if (row == null || row[MuseumLocations.orgId] != old.orgId)
                throw BadRequestException("统一采集位置归属异常，请先核对位置来源")
            if (MuseumDevices.select(MuseumDevices.id).where {
                    (MuseumDevices.locationId eq locationId) and (MuseumDevices.status eq EnumContentStatus.NORMAL)
                }.limit(1).any())
                throw BadRequestException("统一采集位置仍有启用设备，须先办理设备迁移或停用")
            if (MuseumAlarmRules.select(MuseumAlarmRules.id).where {
                    (MuseumAlarmRules.locationId eq locationId) and
                        (MuseumAlarmRules.status eq EnumContentStatus.NORMAL) and
                        (MuseumAlarmRules.runtimeStatus eq EnumAlarmRuleRuntimeStatus.ACTIVE)
                }.limit(1).any())
                throw BadRequestException("统一采集位置仍有运行中的告警规则，须先办理规则停用")
        }
        val replacementId = createForOrg(successor, old.orgId)
        old.status = EnumContentStatus.DELETE
        val archiveId = archiveService.seal(old.orgId, CORRECTION_BIZ, id, "RETIRED", mapOf(
            "oldLocationId" to id, "oldName" to old.name, "oldScene" to old.sceneType.name,
            "oldType" to old.type.name, "replacementLocationId" to replacementId,
            "replacementName" to successor.name, "replacementScene" to successor.sceneType.name,
            "replacementType" to successor.type.name, "unifiedLocationIds" to unifiedIds,
            "reason" to reason, "actorUserId" to io.hizhengfu.cloud.common.utils.SecurityUtils.getUserId(),
            "recordedAt" to java.time.LocalDateTime.now(),
        ), request.signedFileIds)
        if (mappings.isNotEmpty()) {
            ingestConfig!!.synchronizeLocationCatalog(old.orgId)
            if (MuseumLocationSources.selectAll().where {
                    (MuseumLocationSources.sourceType eq "BUSINESS_MASTER") and
                        (MuseumLocationSources.sourceCode eq "COL_WAREHOUSE") and
                        (MuseumLocationSources.externalId eq id) and
                        (MuseumLocationSources.status eq EnumContentStatus.NORMAL)
                }.any()) throw BadRequestException("旧位置采集来源仍有效，订正已回滚")
        }
        return WarehouseLocationCorrectionResultVo(id, replacementId, archiveId)
    }

    fun correctionHistory(id: String): List<Map<String, Any?>> {
        val old = findById(id) ?: throw BadRequestException("位置不存在")
        CollectionCurrentOperationAccess.require(scope, old.orgId, false,
            setOf(CollectionConst.WAREHOUSE_MANAGE, CollectionConst.SELECT), "位置订正原件")
        return archives?.listForAuthorizedOrg(CORRECTION_BIZ, id, old.orgId)
            ?: throw BadRequestException("位置订正永久档案服务未启用")
    }

    fun requireCorrectionOriginal(id: String, archiveId: String, fileId: String) {
        if (listOf(id, archiveId, fileId).any { it.isBlank() || it.length > 100 })
            throw BadRequestException("位置订正原件标识无效")
        val archive = correctionHistory(id).singleOrNull { it["id"] == archiveId }
            ?: throw BadRequestException("该永久档案不属于所选位置订正")
        val files = (archive["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (files.none { it["fileId"] == fileId })
            throw BadRequestException("该文件不是所选位置订正永久档案的原件")
    }

    private fun refreshLeaf(parentId: String) {
        val parent = findById(parentId) ?: return
        parent.isLeaf = dao.find { Warehouses.parentId eq parentId }.limit(1).none()
    }

    /** 调用方已持有馆级位置锁；单条保存和批量导入使用同一校验。 */
    private fun requireValidFields(orgId: String, parentId: String, vo: WarehouseVo, currentId: String? = null) {
        if ((vo.name?.length ?: 0) > 100) throw BadRequestException("位置名称不得超过100字")
        val code = vo.code?.trim()?.takeIf(String::isNotBlank)
        if ((code?.length ?: 0) > 64) throw BadRequestException("库位编码不得超过64字")
        if (vo.capacity != null && vo.capacity < 0) throw BadRequestException("容量不能小于0")
        if (code != null && dao.find { (Warehouses.orgId eq orgId) and (Warehouses.parentId eq parentId) and
                (Warehouses.status eq EnumContentStatus.NORMAL) }.any { it.id.value != currentId && it.code?.trim() == code })
            throw BadRequestException("同一收藏单位、同一上级下的库位编码不能重复")
    }

    /** 核对在库藏品台账库位是否存在且属于同一收藏单位。 */
    fun checkLocations(): WarehouseLocationCheckVo {
        val ids = scope.readableOrgIds()
        val collections = if (ids == null) CollectionDao.find { Collections.lifeStatus eq EnumCollectionStatus.IN_STORE }.toList()
        else CollectionDao.find { (Collections.orgId inList ids) and (Collections.lifeStatus eq EnumCollectionStatus.IN_STORE) }.toList()
        val warehouseMap = findAll().mapNotNull { row -> row.id?.let { it to row } }.toMap()
        val issues = collections.mapNotNull { collection ->
            val warehouseId = collection.warehouseId
            val warehouse = warehouseId?.let(warehouseMap::get)
            val reason = when {
                warehouseId.isNullOrBlank() -> "台账未登记当前库位"
                warehouse == null -> "台账库位不存在或已超出当前单位权限"
                warehouse.orgId != collection.orgId -> "藏品与库位不属于同一收藏单位"
                warehouse.sceneType != EnumLocationScene.STORAGE -> "在库藏品不能登记到展陈位置，请通过展览分配展柜"
                else -> null
            }
            reason?.let {
                WarehouseLocationIssueVo(
                    collectionId = collection.id.value,
                    totalRegNo = collection.totalRegNo,
                    collectionName = collection.name,
                    warehouseId = warehouseId,
                    reason = it,
                )
            }
        }
        return WarehouseLocationCheckVo(
            checked = collections.size,
            matched = collections.size - issues.size,
            issues = issues,
        )
    }
}
