package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.TreeCrudService
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 收藏单位（总分馆树）管理。 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_ORG])
class CollectionOrgService(
    private val scope: CollectionScopeResolver,
    private val codes: CollectionOrgCodeService = CollectionOrgCodeService(scope),
) : TreeCrudService<CollectionOrgDao>(CollectionOrgDao, CollectionConst.CACHE_ORG) {

    @CacheEvict(allEntries = true)
    override fun delete(id: String): CollectionOrgDao {
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), id, "收藏单位")
        CollectionOrgDao.find { CollectionOrgs.id eq id }.forUpdate().singleOrNull()
            ?: throw BadRequestException("收藏单位不存在")
        if (WarehouseDao.find { Warehouses.orgId eq id }.any() || CollectionLocationDirectoryGuard.hasInstallationData(id) ||
            CollectionOrgDao.find { CollectionOrgs.parentId eq id }.any() ||
            CollectionDao.find { Collections.orgId eq id }.any() ||
            CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.orgId eq id }.any() ||
            CollectionPermanentArchiveDao.find { CollectionPermanentArchives.orgId eq id }.any()) {
            throw BadRequestException("收藏单位已有下级、藏品、位置或永久档案，须保留原归属；请按真实情况停用，不能删除")
        }
        if (CollectionOrgCodeChanges.selectAll().where { CollectionOrgCodeChanges.orgId eq id }.any())
            throw BadRequestException("收藏单位已有代码更正记录，请保留历史并按实际情况停用")
        CollectionOrgCodes.deleteWhere { CollectionOrgCodes.orgId eq id }
        return super.delete(id)
    }

    @CacheEvict(allEntries = true)
    override fun deleteByIds(ids: List<String>): Int {
        ids.distinct().forEach { delete(it) }
        return ids.distinct().size
    }

    fun findAll(): List<CollectionOrgVo> {
        val visible = CollectionOrgFilterPolicy.listScope(scope.readableOrgIds())
        val organizations = CollectionOrgDao.all()
            .orderBy(
                CollectionOrgs.parentId to SortOrder.ASC,
                CollectionOrgs.sort to SortOrder.ASC,
                CollectionOrgs.name to SortOrder.ASC
            )
            .filter { visible?.contains(it.id.value) ?: true }
        return toModels(organizations)
    }

    /** 日常办理使用当前岗位和后端部署馆别；基础设置的总分馆目录不能替代业务范围。 */
    fun businessContext(): Map<String, Any?> {
        val access = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(
            CollectionConst.STAT, CollectionConst.ORG_MANAGE, CollectionConst.LEDGER_CREATE,
            CollectionConst.LEDGER_CORRECT, CollectionConst.LEDGER_FINANCE,
            CollectionConst.CARE, CollectionConst.WAREHOUSE_MANAGE,
        ))
        val organizations = CollectionOrgDao.all().filter { it.id.value in access.orgIds }
            .sortedWith(compareBy({ it.sort }, { it.name }, { it.id.value }))
        val current = try { access.currentOrgId(scope) } catch (_: BadRequestException) { null }
        return mapOf(
            "orgs" to organizations.map { mapOf("value" to it.id.value, "label" to it.name) },
            "defaultOrgId" to current,
        )
    }

    fun get(id: String): CollectionOrgVo? =
        findById(id)
            ?.also { OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), it.id.value, "收藏单位") }
            ?.let { toModels(listOf(it)).single() }

    fun overview(): List<CollectionOrgOverviewNode> {
        val accessible = scope.readableOrgIds()
        val orgs = CollectionOrgDao.all()
            .filter { accessible == null || it.id.value in accessible }
            .map { CollectionOrgOverviewOrg(it.id.value, it.parentId, it.name, it.code, it.sort, it.createTime) }
        val orgIds = orgs.map { it.id }.toSet()
        if (orgIds.isEmpty()) return emptyList()

        val mediaIds = CollectionMedias.selectAll()
            .map { it[CollectionMedias.collectionId] }
            .toSet()
        val attachmentIds = CollectionAttachments.selectAll()
            .map { it[CollectionAttachments.collectionId] }
            .toSet()
        val q = Collections.selectAll()
        q.andWhere { Collections.orgId inList orgIds }
        val collections = CollectionDao.wrapRows(q).filter { it.registered && it.lifeStatus!=io.hizhengfu.cloud.collection.domain.EnumCollectionStatus.DEREGISTERED }.map {
            CollectionOrgOverviewCollection(
                orgId = it.orgId,
                level = it.level,
                lifeStatus = it.lifeStatus,
                hasImage = !it.coverImage.isNullOrBlank() || it.id.value in mediaIds,
                hasAttachment = it.id.value in attachmentIds,
            )
        }
        return CollectionOrgOverviewBuilder.build(orgs, collections)
    }

    fun findPage(keyword: String?, request: HttpServletRequest): GenericPage<CollectionOrgVo> {
        val page = GenericPage<CollectionOrgVo>().build(request)
        val q = CollectionOrgs.selectAll()
        CollectionOrgFilterPolicy.listScope(scope.readableOrgIds())?.let { ids ->
            q.andWhere { CollectionOrgs.id inList ids }
        }
        if (!keyword.isNullOrBlank()) {
            q.andWhere {
                (CollectionOrgs.name like "%$keyword%") or
                        (CollectionOrgs.code like "%$keyword%") or
                        (CollectionOrgs.shortName like "%$keyword%") or
                        (CollectionOrgs.legalPerson like "%$keyword%") or
                        (CollectionOrgs.contact like "%$keyword%") or
                        (CollectionOrgs.phone like "%$keyword%") or
                        (CollectionOrgs.sourceId like "%$keyword%")
            }
        }
        request.getParameter("level")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumOrgLevel.valueOf(value) }.getOrNull()?.let { q.andWhere { CollectionOrgs.level eq it } }
        }
        request.getParameter("ownership")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumOwnership.valueOf(value) }.getOrNull()
                ?.let { q.andWhere { CollectionOrgs.ownership eq it } }
        }
        request.getParameter("status")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumOrgStatus.valueOf(value) }.getOrNull()
                ?.let { q.andWhere { CollectionOrgs.orgStatus eq it } }
        }
        q.orderBy(
            CollectionOrgs.parentId to SortOrder.ASC,
            CollectionOrgs.sort to SortOrder.ASC,
            CollectionOrgs.createTime to SortOrder.DESC
        )
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<CollectionOrgDao>)
        page.list = daoPage.data?.let(::toModels)
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<CollectionOrgVo>
    }

    fun importTemplate(): List<CollectionImportTemplateColumnVo> = listOf(
        CollectionImportTemplateColumnVo("rowNo", "行号", sample = "2"),
        CollectionImportTemplateColumnVo("parentName", "上级单位名称/路径", sample = "总馆/分馆"),
        CollectionImportTemplateColumnVo("parentCode", "上级单位代码", sample = "MAIN"),
        CollectionImportTemplateColumnVo("code", "收藏单位代码", required = true, sample = "BR-01"),
        CollectionImportTemplateColumnVo("name", "收藏单位名称", required = true, sample = "一号分馆"),
        CollectionImportTemplateColumnVo("shortName", "简称", sample = "一号馆"),
        CollectionImportTemplateColumnVo("level", "单位级别", dictType = CollectionConst.DICT_ORG_LEVEL, sample = "国家二级博物馆"),
        CollectionImportTemplateColumnVo("ownership", "举办主体", dictType = CollectionConst.DICT_OWNERSHIP, sample = "国有"),
        CollectionImportTemplateColumnVo("legalPerson", "法定代表人", sample = "张三"),
        CollectionImportTemplateColumnVo("areaName", "行政区划名称/路径"),
        CollectionImportTemplateColumnVo("areaCode", "行政区划编码", sample = "110101"),
        CollectionImportTemplateColumnVo("address", "地址", sample = "东街 1 号"),
        CollectionImportTemplateColumnVo("contact", "联系人", sample = "李四"),
        CollectionImportTemplateColumnVo("phone", "联系电话", sample = "010-12345678"),
        CollectionImportTemplateColumnVo("sourceId", "一普机构编码", sample = "YPG-001"),
        CollectionImportTemplateColumnVo("locationRootName", "关联安装位置名称/路径"),
        CollectionImportTemplateColumnVo("status", "状态", dictType = CollectionConst.DICT_ORG_STATUS, sample = "启用"),
        CollectionImportTemplateColumnVo("sort", "排序", sample = "10"),
    )

    fun validateImport(rows: List<CollectionOrgImportRowVo>): CollectionOrgImportValidateResultVo {
        val visible = findAll().filter { it.status == EnumOrgStatus.ENABLED }
        val ids = visible.mapNotNull { it.id }.toSet()
        val codeGroups = visible.filter { !it.code.isNullOrBlank() }.groupBy({ it.code!!.trim() }, { it.id!! })
        val byCode = codeGroups.filterValues { it.distinct().size == 1 }.mapValues { it.value.single() }
        val names = CollectionImportNameResolver(visible.map { CollectionImportNameResolver.Entry(it.id!!, it.name.orEmpty(), it.parentId) })
        val locationIds = visibleLocationRootIds()
        val locations = WarehouseDao.all().filter { it.id.value in locationIds }
        val locationNames = CollectionImportNameResolver(locations.map { CollectionImportNameResolver.Entry(it.id.value, it.name, it.parentId) })
        // 没有行政区划输入的旧模板不引入额外目录依赖。
        val areas = if (rows.any { !it.areaName.isNullOrBlank() || !it.areaCode.isNullOrBlank() })
            io.hizhengfu.cloud.sys.system.dao.AreaDao.all().filter { it.enabled } else emptyList()
        val areaNames = CollectionImportNameResolver(areas.map { CollectionImportNameResolver.Entry(it.id.value, it.name, it.parentId) })
        val areaCodes = areas.groupBy({ it.code }, { it.id.value })
        val errors = mutableListOf<CollectionImportErrorVo>()
        val normalized = rows.mapIndexed { index, row ->
            val refs = CollectionImportRowReferences(row.rowNo.takeIf { it > 0 } ?: index + 2, errors)
            row.copy(
                parentId = refs.read("parentName") { refs.byCode(row.parentCode, names.resolve(row.parentName, row.parentId, "上级单位"), codeGroups, "上级单位") },
                locationRootId = refs.read("locationRootName") { locationNames.resolve(row.locationRootName, row.locationRootId, "安装位置") },
                areaId = refs.read("areaName") { refs.byCode(row.areaCode, areaNames.resolve(row.areaName, row.areaId, "行政区划"), areaCodes, "行政区划") },
            )
        }
        val existingCodes = CollectionOrgDao.all().map { it.code }.toSet() + CollectionOrgCodes.selectAll().map { it[CollectionOrgCodes.code] }
        val result = CollectionOrgImportValidator.validateRows(normalized, ids, byCode, existingCodes, locationIds)
        return if (errors.isEmpty()) result else result.copy(valid = false, errors = result.errors + errors, items = emptyList())
    }

    @CacheEvict(allEntries = true)
    fun commitImport(rows: List<CollectionOrgImportRowVo>): CollectionOrgImportCommitResultVo {
        val result = validateImport(rows)
        if (!result.valid) return CollectionOrgImportCommitResultVo(valid = false, errors = result.errors)
        val ids = result.items.map { create(it) }
        return CollectionOrgImportCommitResultVo(valid = true, createdIds = ids)
    }

    @CacheEvict(allEntries = true)
    fun create(vo: CollectionOrgVo): String {
        val code = CollectionOrgCodeRegistry.normalize(vo.code ?: "")
        val name = vo.name?.takeIf { it.isNotBlank() } ?: throw BadRequestException("名称必填")
        if (CollectionOrgDao.find { CollectionOrgs.code eq code }.any()) {
            throw BadRequestException("收藏单位代码「$code」已存在")
        }
        validateExchangeCode(vo, null)
        val systemGroupId = requireSystemGroup(vo.systemGroupId)
        val locationRootId = requireLocationRoot(vo.locationRootId)
        val orgId = io.hizhengfu.cloud.common.utils.IdGenerate.nextId()
        CollectionOrgCodeRegistry.claim(orgId, code)
        val node = dao.new(orgId) {
            this.code = code
            this.name = name
            shortName = vo.shortName
            level = vo.level
            ownership = vo.ownership
            legalPerson = vo.legalPerson
            areaId = vo.areaId
            address = vo.address
            contact = vo.contact
            phone = vo.phone
            sourceId = vo.sourceId
            exchangeCode = vo.exchangeCode?.trim()?.takeIf(String::isNotBlank)
            exchangeCodeBasis = vo.exchangeCodeBasis
            this.systemGroupId = systemGroupId
            this.locationRootId = locationRootId
            vo.status?.let { orgStatus = it }
            sort = vo.sort ?: 0
            parentId = vo.parentId ?: treeRootId
        }
        createTreeNode(vo.parentId, node.id.value)
        return node.id.value
    }

    @CacheEvict(allEntries = true)
    fun update(id: String, vo: CollectionOrgVo): String {
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), id, "收藏单位")
        CollectionLocationDirectoryGuard.lockRootChange(id, vo.locationRootId?.trim()?.takeIf(String::isNotBlank))
        val node = codes.lock(id)
        codes.updateUnused(node, vo.code, vo.codeStateHash)
        val locationRootId = requireLocationRoot(vo.locationRootId)
        vo.name?.takeIf { it.isNotBlank() }?.let { node.name = it }
        node.shortName = vo.shortName
        node.level = vo.level
        node.ownership = vo.ownership
        node.legalPerson = vo.legalPerson
        node.areaId = vo.areaId
        node.address = vo.address
        node.contact = vo.contact
        node.phone = vo.phone
        validateExchangeCode(vo, id)
        node.sourceId = vo.sourceId
        node.exchangeCode = vo.exchangeCode?.trim()?.takeIf(String::isNotBlank)
        node.exchangeCodeBasis = vo.exchangeCodeBasis
        node.systemGroupId = requireSystemGroup(vo.systemGroupId)
        node.locationRootId = locationRootId
        vo.status?.let { node.orgStatus = it }
        vo.sort?.let { node.sort = it }
        if (!vo.parentId.isNullOrBlank() && vo.parentId != node.parentId) {
            updateTreeNode(vo.parentId, id)
        }
        return id
    }

    private fun validateExchangeCode(vo: CollectionOrgVo, id: String?) {
        val code=vo.exchangeCode?.trim()?.takeIf(String::isNotBlank) ?: return
        if(!io.hizhengfu.cloud.collection.domain.CollectionMediaStandard.validCode(code) || vo.exchangeCodeBasis.isNullOrBlank())
            throw BadRequestException("规范交换机构代码须为14位数字并填写原赋码依据，不得根据内部代码推算")
        if(CollectionOrgDao.find { CollectionOrgs.exchangeCode eq code }.any {it.id.value!=id})
            throw BadRequestException("规范交换机构代码已用于其他收藏单位")
    }

    private fun requireSystemGroup(value: String?): String? {
        val id = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (GroupDao.findById(id) == null) throw BadRequestException("关联的系统机构不存在")
        return id
    }

    /**
     * 收藏单位与物理空间显式关联，避免按组织父级自动继承整馆位置造成分馆串址。
     * 关联根可以属于当前可写范围内的上级馆，设备侧会把该子树归档到本单位的统一位置目录。
     */
    private fun requireLocationRoot(value: String?): String? {
        val id = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        val location = WarehouseDao.findById(id)
            ?.takeIf { it.status == EnumContentStatus.NORMAL }
            ?: throw BadRequestException("关联安装位置不存在或已停用")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), location.orgId, "关联安装位置")
        CollectionLocationMutation.reference(location.orgId, id)
        if (location.status != EnumContentStatus.NORMAL) throw BadRequestException("关联安装位置不存在或已停用")
        return id
    }

    private fun visibleLocationRootIds(): Set<String> {
        val visibleOrgIds = scope.readableOrgIds()
        return WarehouseDao.find { Warehouses.status eq EnumContentStatus.NORMAL }
            .filter { visibleOrgIds == null || it.orgId in visibleOrgIds }
            .mapTo(linkedSetOf()) { it.id.value }
    }

    private fun toModels(organizations: List<CollectionOrgDao>): List<CollectionOrgVo> {
        val rootIds = organizations.mapNotNullTo(linkedSetOf()) { it.locationRootId }
        val rootNames = if (rootIds.isEmpty()) emptyMap() else WarehouseDao.find {
            Warehouses.id inList rootIds
        }.associate { it.id.value to it.name }
        return organizations.map { it.toModel(it.locationRootId?.let(rootNames::get)) }
    }

    /** 批量更新同级展示顺序。 */
    @CacheEvict(allEntries = true)
    fun updateSort(sorts: List<GenericUpdateSort>) {
        sorts.forEach {
            val node = findById(it.id) ?: throw BadRequestException("收藏单位不存在")
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), node.id.value, "收藏单位")
            node.sort = it.sort
        }
    }
}
