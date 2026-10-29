package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionDescriptionPolicy
import io.hizhengfu.cloud.collection.domain.CollectionNaturePolicy
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.CollectionRegistrationDetailsVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionEraMeaning
import io.hizhengfu.cloud.collection.domain.EnumCollectionEraNotation
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionImportRowVo
import io.hizhengfu.cloud.collection.domain.CollectionImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumCollectionImportMode
import io.hizhengfu.cloud.collection.domain.EnumPreserve
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource

/** 批量导入行级校验，保持纯函数便于前端预校验和单元测试。 */
object CollectionImportValidator {
    private val enumImportLabels = mapOf(
        EnumRelicLevel::class.qualifiedName to mapOf(
            "一级" to EnumRelicLevel.FIRST.name,
            "二级" to EnumRelicLevel.SECOND.name,
            "三级" to EnumRelicLevel.THIRD.name,
            "一般文物" to EnumRelicLevel.GENERAL.name,
            "未定级" to EnumRelicLevel.UNRATED.name,
        ),
        EnumSource::class.qualifiedName to mapOf(
            "考古发掘" to EnumSource.EXCAVATION.name,
            "采集" to EnumSource.COLLECT.name,
            "收购" to EnumSource.PURCHASE.name,
            "调拨" to EnumSource.ALLOCATE.name,
            "拨交" to EnumSource.TRANSFER.name,
            "交换" to EnumSource.EXCHANGE.name,
            "捐赠" to EnumSource.DONATE.name,
            "移交" to EnumSource.HANDOVER.name,
            "拣选" to EnumSource.PICK.name,
            "旧藏" to EnumSource.OLD.name,
            "复制" to EnumSource.REPRO.name,
        ),
        EnumCompleteness::class.qualifiedName to mapOf(
            "完整" to EnumCompleteness.COMPLETE.name,
            "基本完整" to EnumCompleteness.BASIC.name,
            "残缺" to EnumCompleteness.DAMAGED.name,
            "严重残缺" to EnumCompleteness.SEVERE.name,
        ),
        EnumPreserve::class.qualifiedName to mapOf(
            "状态稳定，不需修复" to EnumPreserve.A.name,
            "部分损腐，需要修复" to EnumPreserve.B.name,
            "腐蚀损毁严重，急需修复" to EnumPreserve.C.name,
        ),
        EnumCollectionEraMeaning::class.qualifiedName to CollectionDescriptionPolicy.eraMeanings.entries.associate { it.value to it.key.name },
        EnumCollectionEraNotation::class.qualifiedName to CollectionDescriptionPolicy.eraNotations.entries.associate { it.value to it.key.name },
    )

    fun validateRows(
        rows: List<CollectionImportRowVo>,
        existingRegNos: Set<String>,
        categoryIds: Set<String>,
        warehouseIds: Set<String>,
        mode: EnumCollectionImportMode = EnumCollectionImportMode.CREATE_ONLY,
    ): CollectionImportValidateResultVo {
        val errors = mutableListOf<CollectionImportErrorVo>()
        val seenRegNos = HashSet<String>()
        val items = mutableListOf<CollectionVo>()

        rows.forEachIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            val name = row.name?.trim().orEmpty()
            if (name.isBlank()) {
                errors += CollectionImportErrorVo(rowNo, "name", "名称必填")
            }

            val categoryId = row.categoryId?.trim()?.ifBlank { null }
            if (categoryId == null) {
                errors += CollectionImportErrorVo(rowNo, "categoryId", "文物分类必填")
            } else if (!categoryIds.contains(categoryId)) {
                errors += CollectionImportErrorVo(rowNo, "categoryId", "文物分类不存在")
            }

            // 原账完整号码优先；只有未精确匹配历史记录的新输入才规范化。
            val regNo = row.totalRegNo?.takeIf { it in existingRegNos } ?: row.totalRegNo?.trim().orEmpty()
            if (regNo.isNotBlank()) {
                when {
                    mode == EnumCollectionImportMode.CREATE_ONLY && existingRegNos.contains(regNo) ->
                        errors += CollectionImportErrorVo(rowNo, "totalRegNo", "总登记号已存在")
                    !seenRegNos.add(regNo) -> errors += CollectionImportErrorVo(rowNo, "totalRegNo", "导入文件内总登记号重复")
                }
            }

            val quantity = row.quantity
            if (quantity == null) {
                errors += CollectionImportErrorVo(rowNo, "quantity", "数量必填")
            } else if (quantity <= 0) {
                errors += CollectionImportErrorVo(rowNo, "quantity", "数量必须大于 0")
            }
            if(row.actualQuantity!=null && (row.actualQuantity<=0 || row.actualQuantityUnit.isNullOrBlank() || row.actualQuantityUnit.trim()=="套"))
                errors += CollectionImportErrorVo(rowNo,"actualQuantity","实际数量须为正整数并明确件、册等单位；不得填写套数")
            if(row.actualQuantity==null && !row.actualQuantityUnit.isNullOrBlank())
                errors += CollectionImportErrorVo(rowNo,"actualQuantity","填写实际数量单位时须同时填写实际数量")
            val unit = row.unit?.trim()?.ifBlank { null }
            if (unit == null) {
                errors += CollectionImportErrorVo(rowNo, "unit", "计量单位必填")
            }
            if (row.acquireDate == null && row.acquireDateRange.isNullOrBlank()) {
                errors += CollectionImportErrorVo(rowNo, "acquireDateRange", "取得时间范围必填")
            }

            val nature = CollectionNaturePolicy.parse(row.nature)
            if (!row.nature.isNullOrBlank() && nature == null) errors += CollectionImportErrorVo(rowNo, "nature", "藏品性质须填写文物藏品、其他藏品或科学化石")
            val level = if (nature == EnumCollectionNature.OTHER) {
                val parsed = parseEnum<EnumRelicLevel>(row.level, rowNo, "level", "藏品级别", errors)
                if (parsed != null && parsed != EnumRelicLevel.UNRATED) errors += CollectionImportErrorVo(rowNo, "level", "其他藏品不适用文物等级")
                parsed ?: EnumRelicLevel.UNRATED
            } else parseRequiredEnum<EnumRelicLevel>(row.level, rowNo, "level", "藏品级别", errors)
            val source = parseRequiredEnum<EnumSource>(row.source, rowNo, "source", "来源方式", errors)
            val completeness = parseEnum<EnumCompleteness>(row.completeness, rowNo, "completeness", "完残程度", errors)
            val preserve = parseEnum<EnumPreserve>(row.preserveState, rowNo, "preserveState", "保存状态", errors)

            row.warehouseId?.trim()?.takeIf { it.isNotBlank() }?.let {
                if (!warehouseIds.contains(it)) {
                    errors += CollectionImportErrorVo(rowNo, "warehouseId", "库位不存在")
                }
            }

            CollectionDescriptionPolicy.materialIssues(row.textureCategory1?.trim(), row.textureCategory2?.trim()).forEach {
                errors += CollectionImportErrorVo(rowNo, "textureCategory2", it)
            }
            items += CollectionVo(
                nature = nature,
                totalRegNo = regNo.ifBlank { null },
                classNo = row.classNo?.trim()?.ifBlank { null },
                auxNo = row.auxNo?.trim()?.ifBlank { null },
                name = name.ifBlank { null },
                originalName = row.originalName?.trim()?.ifBlank { null },
                categoryId = categoryId,
                era = row.era?.trim()?.ifBlank { null },
                registrationDetails = CollectionRegistrationDetailsVo(
                    eraMeaning = parseEnum<EnumCollectionEraMeaning>(row.eraMeaning, rowNo, "eraMeaning", "年代类型", errors),
                    eraNotation = parseEnum<EnumCollectionEraNotation>(row.eraNotation, rowNo, "eraNotation", "年代表示方式", errors),
                ),
                texture = row.texture?.trim()?.ifBlank { null },
                textureCategory1 = row.textureCategory1?.trim()?.ifBlank { null },
                textureCategory2 = row.textureCategory2?.trim()?.ifBlank { null },
                level = level,
                quantity = quantity,
                actualQuantity = row.actualQuantity,
                actualQuantityUnit = row.actualQuantityUnit?.trim(),
                dimensionUnit = row.dimensionUnit?.trim(),
                entryDate = row.entryDate,
                unit = unit,
                source = source,
                sourceDesc = row.sourceDesc?.trim()?.ifBlank { null },
                acquireDate = row.acquireDate,
                acquireDateRange = row.acquireDateRange?.trim()?.ifBlank { null } ?: row.acquireDate?.toString(),
                acquireYear = row.acquireYear?.trim()?.ifBlank { null } ?: row.acquireDate?.year?.toString(),
                completeness = completeness,
                completeStatus = row.completeStatus?.trim()?.ifBlank { null },
                dimensions = buildDimensions(row),
                weight = row.weight?.trim()?.ifBlank { null },
                weightRange = row.weightRange?.trim()?.ifBlank { null },
                weightUnit = row.weightUnit?.trim()?.ifBlank { null },
                region = row.region?.trim()?.ifBlank { null },
                volume = row.volume?.trim()?.ifBlank { null },
                volumeUnit = row.volumeUnit?.trim()?.ifBlank { null },
                color = row.color?.trim()?.ifBlank { null },
                sourceNo = row.sourceNo?.trim()?.ifBlank { null },
                country = row.country?.trim()?.ifBlank { null },
                culture = row.culture?.trim()?.ifBlank { null },
                inscription = row.inscription?.trim()?.ifBlank { null },
                feature = row.feature?.trim()?.ifBlank { null },
                author = row.author?.trim()?.ifBlank { null },
                edition = row.edition?.trim()?.ifBlank { null },
                volumeNo = row.volumeNo?.trim()?.ifBlank { null },
                preserveState = preserve,
                valuation = row.valuation,
                coverImage = row.coverImage?.trim()?.ifBlank { null },
                warehouseId = row.warehouseId?.trim()?.ifBlank { null },
                remark = row.remark?.trim()?.ifBlank { null },
            )
        }

        return CollectionImportValidateResultVo(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) items else emptyList(),
            createCount = if (errors.isEmpty()) items.count {
                it.totalRegNo.isNullOrBlank() || !existingRegNos.contains(it.totalRegNo)
            } else 0,
            updateCount = if (errors.isEmpty() && mode == EnumCollectionImportMode.UPSERT) items.count {
                !it.totalRegNo.isNullOrBlank() && existingRegNos.contains(it.totalRegNo)
            } else 0,
        )
    }

    private fun buildDimensions(row: CollectionImportRowVo): DimensionVo? {
        val dimensions = DimensionVo(
            height = row.height?.trim()?.ifBlank { null },
            mouth = row.mouth?.trim()?.ifBlank { null },
            bottom = row.bottom?.trim()?.ifBlank { null },
            belly = row.belly?.trim()?.ifBlank { null },
            length = row.length?.trim()?.ifBlank { null },
            width = row.width?.trim()?.ifBlank { null },
            diameter = row.diameter?.trim()?.ifBlank { null },
        )
        return dimensions.takeIf {
            listOf(it.height, it.mouth, it.bottom, it.belly, it.length, it.width, it.diameter)
                .any { value -> !value.isNullOrBlank() }
        }
    }

    private inline fun <reified E : Enum<E>> parseEnum(
        value: String?,
        rowNo: Int,
        field: String,
        label: String,
        errors: MutableList<CollectionImportErrorVo>,
    ): E? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = enumImportLabels[E::class.qualifiedName]?.get(clean) ?: clean
        return enumValues<E>().firstOrNull { it.name == normalized } ?: run {
            errors += CollectionImportErrorVo(rowNo, field, "$label「$clean」无效")
            null
        }
    }

    private inline fun <reified E : Enum<E>> parseRequiredEnum(
        value: String?,
        rowNo: Int,
        field: String,
        label: String,
        errors: MutableList<CollectionImportErrorVo>,
    ): E? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        if (clean == null) {
            errors += CollectionImportErrorVo(rowNo, field, "$label 必填")
            return null
        }
        return parseEnum(clean, rowNo, field, label, errors)
    }
}
