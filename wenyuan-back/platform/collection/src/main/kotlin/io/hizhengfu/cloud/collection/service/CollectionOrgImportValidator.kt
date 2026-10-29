package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionOrgVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionOrgImportRowVo
import io.hizhengfu.cloud.collection.domain.CollectionOrgImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.EnumOrgLevel
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.collection.domain.EnumOwnership

/** 收藏单位导入行级校验；上级支持现有 parentId 或现有收藏单位编码 parentCode。 */
object CollectionOrgImportValidator {
    private val orgLevelLabels = mapOf(
        "国家一级博物馆" to EnumOrgLevel.NATIONAL_1.name,
        "国家二级博物馆" to EnumOrgLevel.NATIONAL_2.name,
        "国家三级博物馆" to EnumOrgLevel.NATIONAL_3.name,
        "未定级" to EnumOrgLevel.UNRATED.name,
    )
    private val ownershipLabels = mapOf(
        "国有" to EnumOwnership.STATE.name,
        "非国有" to EnumOwnership.NON_STATE.name,
    )
    private val statusLabels = mapOf(
        "启用" to EnumOrgStatus.ENABLED.name,
        "停用" to EnumOrgStatus.DISABLED.name,
    )

    fun validateRows(
        rows: List<CollectionOrgImportRowVo>,
        orgIds: Set<String>,
        orgByCode: Map<String, String>,
        existingCodes: Set<String>,
        locationRootIds: Set<String>? = null,
    ): CollectionOrgImportValidateResultVo {
        val errors = mutableListOf<CollectionImportErrorVo>()
        val items = mutableListOf<CollectionOrgVo>()
        val seenCodes = mutableSetOf<String>()

        rows.forEachIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            val code = row.code?.trim().orEmpty()
            val name = row.name?.trim().orEmpty()

            if (code.isBlank()) {
                errors += CollectionImportErrorVo(rowNo, "code", "收藏单位代码必填")
            } else {
                if (code in existingCodes) {
                    errors += CollectionImportErrorVo(rowNo, "code", "收藏单位代码「$code」已存在")
                }
                if (!seenCodes.add(code)) {
                    errors += CollectionImportErrorVo(rowNo, "code", "导入文件内收藏单位代码「$code」重复")
                }
            }
            if (name.isBlank()) {
                errors += CollectionImportErrorVo(rowNo, "name", "名称必填")
            }

            val parentId = resolveParentId(row, rowNo, orgIds, orgByCode, errors)
            val level = parseEnum(row.level, orgLevelLabels, rowNo, "level", "单位级别", errors, EnumOrgLevel::valueOf)
            val ownership = parseEnum(row.ownership, ownershipLabels, rowNo, "ownership", "举办主体", errors, EnumOwnership::valueOf)
            val status = parseEnum(row.status, statusLabels, rowNo, "status", "状态", errors, EnumOrgStatus::valueOf)
                ?: EnumOrgStatus.ENABLED
            val sort = row.sort ?: 0L
            if (sort < 0) {
                errors += CollectionImportErrorVo(rowNo, "sort", "排序不能小于 0")
            }
            val locationRootId = row.locationRootId?.trim()?.takeIf(String::isNotBlank)
            if (locationRootId != null && locationRootIds != null && locationRootId !in locationRootIds) {
                errors += CollectionImportErrorVo(rowNo, "locationRootId", "关联安装位置不存在、已停用或无权访问")
            }

            items += CollectionOrgVo(
                parentId = parentId,
                code = code.ifBlank { null },
                name = name.ifBlank { null },
                shortName = row.shortName?.trim()?.ifBlank { null },
                level = level,
                ownership = ownership,
                legalPerson = row.legalPerson?.trim()?.ifBlank { null },
                areaId = row.areaId?.trim()?.ifBlank { null },
                address = row.address?.trim()?.ifBlank { null },
                contact = row.contact?.trim()?.ifBlank { null },
                phone = row.phone?.trim()?.ifBlank { null },
                sourceId = row.sourceId?.trim()?.ifBlank { null },
                locationRootId = locationRootId,
                status = status,
                sort = sort,
            )
        }

        return CollectionOrgImportValidateResultVo(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) items else emptyList(),
        )
    }

    private fun resolveParentId(
        row: CollectionOrgImportRowVo,
        rowNo: Int,
        orgIds: Set<String>,
        orgByCode: Map<String, String>,
        errors: MutableList<CollectionImportErrorVo>,
    ): String? {
        val parentId = row.parentId?.trim()?.takeIf { it.isNotBlank() }
        if (parentId != null) {
            if (parentId !in orgIds) {
                errors += CollectionImportErrorVo(rowNo, "parentId", "上级收藏单位不存在或无权操作")
            }
            return parentId
        }
        val parentCode = row.parentCode?.trim()?.takeIf { it.isNotBlank() }
        if (parentCode != null) {
            return orgByCode[parentCode] ?: run {
                errors += CollectionImportErrorVo(rowNo, "parentCode", "上级收藏单位代码不存在或无权操作")
                null
            }
        }
        return null
    }

    private fun <T : Enum<T>> parseEnum(
        value: String?,
        labels: Map<String, String>,
        rowNo: Int,
        field: String,
        title: String,
        errors: MutableList<CollectionImportErrorVo>,
        parser: (String) -> T,
    ): T? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = labels[clean] ?: clean
        return runCatching { parser(normalized) }.getOrNull() ?: run {
            errors += CollectionImportErrorVo(rowNo, field, "$title「$clean」无效")
            null
        }
    }
}
