package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionRegistrationNumberDao
import io.hizhengfu.cloud.collection.dao.CollectionRegistrationNumbers
import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgers
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.dao.Collections
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.CollectionImportCommitResultVo
import io.hizhengfu.cloud.collection.domain.CollectionImportRowVo
import io.hizhengfu.cloud.collection.domain.CollectionImportTemplateColumnVo
import io.hizhengfu.cloud.collection.domain.CollectionImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.EnumCollectionImportMode
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.WarehouseLocationPolicy
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 藏品批量导入：模板字段、预校验、正式入账。 */
@Component
@Transactional
class CollectionImportService(
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val attachmentBinder: BusinessAttachmentBinder,
) {

    fun template(): List<CollectionImportTemplateColumnVo> = listOf(
        CollectionImportTemplateColumnVo("totalRegNo", "总登记号", sample = "000001"),
        CollectionImportTemplateColumnVo("classNo", "分类号", sample = "瓷-001"),
        CollectionImportTemplateColumnVo("auxNo", "辅助编号/原藏号"),
        CollectionImportTemplateColumnVo("name", "名称", required = true, sample = "青花瓷碗"),
        CollectionImportTemplateColumnVo("nature", "藏品性质", sample = "文物藏品"),
        CollectionImportTemplateColumnVo("originalName", "原名"),
        CollectionImportTemplateColumnVo("categoryName", "文物分类名称", required = true, sample = "瓷器"),
        CollectionImportTemplateColumnVo("eraMeaning", "年代类型", sample = "制造年代"),
        CollectionImportTemplateColumnVo("eraNotation", "年代表示方式", sample = "中国历史学年代"),
        CollectionImportTemplateColumnVo("era", "具体年代", sample = "明"),
        CollectionImportTemplateColumnVo("textureCategory1", "质地类别", sample = "单一质地"),
        CollectionImportTemplateColumnVo("textureCategory2", "质地细类", sample = "无机质/非金属材料"),
        CollectionImportTemplateColumnVo("texture", "质地", dictType = CollectionConst.DICT_TEXTURE, sample = "瓷"),
        CollectionImportTemplateColumnVo("level", "藏品级别", dictType = CollectionConst.DICT_RELIC_LEVEL, sample = "未定级"),
        CollectionImportTemplateColumnVo("quantity", "登记件套数", required = true, sample = "1"),
        CollectionImportTemplateColumnVo("unit", "计量单位", required = true, sample = "件"),
        CollectionImportTemplateColumnVo("actualQuantity", "实际数量", sample = "2"),
        CollectionImportTemplateColumnVo("actualQuantityUnit", "实际数量单位", sample = "件"),
        CollectionImportTemplateColumnVo("dimensionUnit", "尺寸单位", sample = "cm"),
        CollectionImportTemplateColumnVo("entryDate", "实收入馆日期", sample = "2026-06-23"),
        CollectionImportTemplateColumnVo("source", "来源方式", required = true, dictType = CollectionConst.DICT_SOURCE, sample = "捐赠"),
        CollectionImportTemplateColumnVo("sourceDesc", "来源说明"),
        CollectionImportTemplateColumnVo("acquireDateRange", "取得时间范围", required = true, sample = "1949.10.1-1965"),
        CollectionImportTemplateColumnVo("acquireYear", "取得年度", sample = "2009"),
        CollectionImportTemplateColumnVo("acquireDate", "取得日期", sample = "2026-06-23"),
        CollectionImportTemplateColumnVo("completeness", "完残程度", dictType = CollectionConst.DICT_COMPLETENESS, sample = "完整"),
        CollectionImportTemplateColumnVo("completeStatus", "完残状况"),
        CollectionImportTemplateColumnVo("height", "通高"),
        CollectionImportTemplateColumnVo("mouth", "口径"),
        CollectionImportTemplateColumnVo("bottom", "底径"),
        CollectionImportTemplateColumnVo("belly", "腹径"),
        CollectionImportTemplateColumnVo("length", "通长"),
        CollectionImportTemplateColumnVo("width", "通宽"),
        CollectionImportTemplateColumnVo("diameter", "直径"),
        CollectionImportTemplateColumnVo("weightRange", "质量范围"),
        CollectionImportTemplateColumnVo("weight", "具体质量"),
        CollectionImportTemplateColumnVo("weightUnit", "质量单位", sample = "kg"),
        CollectionImportTemplateColumnVo("region", "地域", sample = "江西九江"),
        CollectionImportTemplateColumnVo("volume", "容积"),
        CollectionImportTemplateColumnVo("volumeUnit", "容积单位", sample = "毫升"),
        CollectionImportTemplateColumnVo("color", "色泽"),
        CollectionImportTemplateColumnVo("sourceNo", "来源号"),
        CollectionImportTemplateColumnVo("country", "国别", sample = "中国"),
        CollectionImportTemplateColumnVo("culture", "人文类型"),
        CollectionImportTemplateColumnVo("inscription", "铭文款识"),
        CollectionImportTemplateColumnVo("feature", "特征描述"),
        CollectionImportTemplateColumnVo("author", "著者"),
        CollectionImportTemplateColumnVo("edition", "版本"),
        CollectionImportTemplateColumnVo("volumeNo", "存卷"),
        CollectionImportTemplateColumnVo(
            "preserveState",
            "保存状态",
            dictType = CollectionConst.DICT_PRESERVE,
            sample = "状态稳定，不需修复",
        ),
        CollectionImportTemplateColumnVo("valuation", "估价（分）"),
        CollectionImportTemplateColumnVo("coverImageName", "文物影像/封面文件名"),
        CollectionImportTemplateColumnVo("warehouseName", "库位名称"),
        CollectionImportTemplateColumnVo("remark", "备注"),
    )

    fun validate(
        rows: List<CollectionImportRowVo>,
        mode: EnumCollectionImportMode = EnumCollectionImportMode.CREATE_ONLY,
        reason: String? = null,
        expectedOrgId: String? = null,
    ): CollectionImportValidateResultVo = validate(rows, mode, reason, requireAccess(expectedOrgId))

    private fun validate(rows: List<CollectionImportRowVo>, mode: EnumCollectionImportMode, reason: String?, access: ImportAccess,
        existing: Map<String, CollectionDao> = existingCollections(access.orgId, rows)): CollectionImportValidateResultVo {
        val org = access.orgId
        val categories = CollectionCategoryDao.all().filter(CollectionCategoryPolicy::isStandard)
        val warehouses = WarehouseDao.all().filter { it.orgId == org && it.status == EnumContentStatus.NORMAL && it.sceneType == EnumLocationScene.STORAGE && it.type in WarehouseLocationPolicy.storageTypes }
        val categoryNames = CollectionImportNameResolver(categories.map { CollectionImportNameResolver.Entry(it.id.value, it.name, it.parentId) })
        val warehouseNames = CollectionImportNameResolver(warehouses.map { CollectionImportNameResolver.Entry(it.id.value, it.name, it.parentId) })
        val nameErrors = mutableListOf<CollectionImportErrorVo>()
        val resolvedRows = rows.mapIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            fun resolve(field: String, label: String, name: String?, id: String?, resolver: CollectionImportNameResolver): String? = try {
                resolver.resolve(name, id, label)
            } catch (e: BadRequestException) {
                nameErrors += CollectionImportErrorVo(rowNo, field, e.message ?: "$label 无效")
                null
            }
            row.copy(
                categoryId = resolve("categoryName", "文物分类", row.categoryName, row.categoryId, categoryNames),
                warehouseId = resolve("warehouseName", "库位", row.warehouseName, row.warehouseId, warehouseNames),
            )
        }
        if (nameErrors.isNotEmpty()) return CollectionImportValidateResultVo(valid = false, errors = nameErrors)
        val base = CollectionImportValidator.validateRows(
            rows = resolvedRows,
            existingRegNos = existing.keys,
            categoryIds = categories.map { it.id.value }.toSet(),
            warehouseIds = warehouses.map { it.id.value }.toSet(),
            mode = mode,
        )
        if (!base.valid) return base
        requirePermissions(access.actor, base.createCount, base.updateCount)

        val errors = mutableListOf<CollectionImportErrorVo>()
        if (mode == EnumCollectionImportMode.UPSERT && base.updateCount > 0 && reason.isNullOrBlank()) {
            errors += CollectionImportErrorVo(0, "reason", "批量更新已有藏品时必须填写更正原因")
        }
        val newNumbers = base.items.mapNotNull { item ->
            item.totalRegNo?.takeIf { it !in existing }
        }.toSet()
        if (newNumbers.isNotEmpty()) {
            // 旧号虽不在当前主档，仍可能已被退出占号或原馆留存账永久占用。
            val occupied = CollectionRegistrationNumberDao.find {
                (CollectionRegistrationNumbers.orgId eq org) and
                    (CollectionRegistrationNumbers.totalRegNo inList newNumbers)
            }.map { it.totalRegNo }.toSet()
            val retained = CollectionRetainedLedgers.select(CollectionRetainedLedgers.totalRegNo).where {
                (CollectionRetainedLedgers.orgId eq org) and
                    (CollectionRetainedLedgers.totalRegNo inList newNumbers)
            }.map { it[CollectionRetainedLedgers.totalRegNo] }.toSet()
            base.items.forEachIndexed { index, item ->
                val number = item.totalRegNo ?: return@forEachIndexed
                if (number in existing) return@forEachIndexed
                if (number !in occupied && number !in retained) return@forEachIndexed
                val rowNo = rows.getOrNull(index)?.rowNo?.takeIf { it > 0 } ?: index + 2
                errors += CollectionImportErrorVo(rowNo, "totalRegNo", "总登记号「$number」已被占用，不能重复使用")
            }
        }
        val normalizedItems = base.items.mapIndexed { index, item ->
            val fileName = rows[index].coverImageName?.trim()?.takeIf(String::isNotBlank)
            val reference = item.coverImage?.trim()?.takeIf(String::isNotBlank)
            if (fileName == null && reference == null) return@mapIndexed item
            try {
                val matched = if (fileName != null) attachmentBinder.resolveByName(fileName) else attachmentBinder.resolveSingle(reference)
                if (fileName != null && reference != null && attachmentBinder.resolveSingle(reference) != matched)
                    throw BadRequestException("封面文件名与文件ID不一致")
                item.copy(coverImage = matched)
            } catch (e: BadRequestException) {
                val rowNo = rows.getOrNull(index)?.rowNo?.takeIf { it > 0 } ?: index + 2
                errors += CollectionImportErrorVo(rowNo, if (fileName == null) "coverImage" else "coverImageName", e.message ?: "封面文件无效")
                item
            }
        }
        return base.copy(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) normalizedItems else emptyList(),
            confirmation = if (errors.isEmpty()) CollectionImportConfirmation.create(org, listOf(
                "collection-import/v2", org, mode, reason?.trim().orEmpty(), normalizedItems,
                // 自动编号没有匹配的旧藏品；将已占号码纳入确认依据，成功后旧确认不能再新建一批。
                if (base.createCount > 0) CollectionRegistrationNumberDao.find { CollectionRegistrationNumbers.orgId eq org }
                    .map { it.id.value to it.totalRegNo }.sortedBy { it.first } else emptyList(),
                existing.toSortedMap().mapValues { it.value.toModel() },
            )) else null,
        )
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE], allEntries = true)
    fun commit(
        rows: List<CollectionImportRowVo>,
        mode: EnumCollectionImportMode = EnumCollectionImportMode.CREATE_ONLY,
        reason: String? = null,
        expectedOrgId: String? = null,
        expectedValidationHash: String? = null,
    ): CollectionImportCommitResultVo {
        val access = requireAccess(expectedOrgId)
        // 同馆提交串行核验占号依据，防止两个相同确认同时通过后分别自动编号。
        CollectionOrgs.select(CollectionOrgs.id).where { CollectionOrgs.id eq access.orgId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("收藏单位不存在，请重新核对")
        requireAccess(access.orgId)
        val existing = existingCollections(access.orgId, rows, lock = true)
        val validation = validate(rows, mode, reason, access, existing)
        if (!validation.valid) {
            return CollectionImportCommitResultVo(valid = false, errors = validation.errors)
        }
        CollectionImportConfirmation.requireSame(validation.confirmation, expectedValidationHash)
        val org = access.orgId
        // 原件解析等扩展调用结束后重核当前资格，整个批次仍固定在本次已核实的馆。
        val current = requireAccess(org)
        requirePermissions(current.actor, validation.createCount, validation.updateCount)
        val created = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        validation.items.forEach { item ->
            val matched = item.totalRegNo?.let(existing::get)
            if (matched == null) {
                created += collectionService.createForOrg(org, item)
            } else {
                val changed = collectionService.updateFromImport(matched.id.value, item, reason.orEmpty())
                if (changed) updated += matched.id.value else unchanged += matched.id.value
            }
        }
        return CollectionImportCommitResultVo(
            valid = true,
            createdIds = created,
            updatedIds = updated,
            unchangedIds = unchanged,
        )
    }

    private fun existingCollections(orgId: String, rows: List<CollectionImportRowVo>, lock: Boolean = false): Map<String, CollectionDao> {
        val regNos = rows.flatMap { row ->
            row.totalRegNo?.takeIf(String::isNotBlank)?.let { listOf(it, it.trim()) }.orEmpty()
        }.toSet()
        if (regNos.isEmpty()) return emptyMap()
        val query = CollectionDao.find {
            (Collections.orgId eq orgId) and (Collections.totalRegNo inList regNos)
        }.orderBy(Collections.id to org.jetbrains.exposed.v1.core.SortOrder.ASC)
        val matches = (if (lock) query.forUpdate() else query).onEach { if (lock) it.refresh(flush = true) }.groupBy { it.totalRegNo }
        if (matches.values.any { it.size != 1 }) throw BadRequestException("导入涉及的本馆总登记号不唯一，请先核实原账")
        return matches.mapValues { it.value.single() }
    }

    private data class ImportAccess(val orgId: String, val actor: SecurityUser)

    internal fun currentImportOrgId(): String = requireAccess().orgId

    private fun requireAccess(expectedOrgId: String? = null): ImportAccess {
        val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后导入藏品")
        val actor = CollectionActorDirectory(scope).requireActive(id)
        val orgId = scope.currentOrgId(actor)
        if (expectedOrgId != null && expectedOrgId != orgId) throw BadRequestException("导入收藏单位已变化，请重新核对")
        val current = CollectionCurrentOperationAccess.require(scope, orgId, true,
            setOf(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT), "藏品导入")
        CollectionDutySeparationPolicy.requireLedgerOperation(current.permissions, current.roleCodes)
        return ImportAccess(orgId, current)
    }

    private fun requirePermissions(actor: SecurityUser, createCount: Int, updateCount: Int) {
        val permissions = actor.permissions
        val elevated = EnumRole.ADMIN.value in actor.roles || permissions.any {
            it.equals("ADMIN", ignoreCase = true) || it == CollectionConst.ADMIN
        }
        if (createCount > 0 && !elevated && CollectionConst.LEDGER_CREATE !in permissions) {
            throw BadRequestException("导入包含新增藏品，当前账号缺少总账登记权限")
        }
        if (updateCount > 0 && !elevated && CollectionConst.LEDGER_CORRECT !in permissions) {
            throw BadRequestException("导入包含已有藏品更新，当前账号缺少总账更正权限")
        }
    }
}
