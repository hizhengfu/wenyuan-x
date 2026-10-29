package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.domain.AcquisitionImportRowVo
import io.hizhengfu.cloud.collection.domain.AcquisitionImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness

/** 征集入藏导入行级校验；校验通过后生成征集草稿 VO。 */
object AcquisitionImportValidator {
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
    )

    fun validateRows(
        rows: List<AcquisitionImportRowVo>,
        categoryIds: Set<String>,
    ): AcquisitionImportValidateResultVo {
        val errors = mutableListOf<CollectionImportErrorVo>()
        val items = mutableListOf<AcquisitionVo>()

        rows.forEachIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            val name = row.name?.trim().orEmpty()
            if (name.isBlank()) {
                errors += CollectionImportErrorVo(rowNo, "name", "拟征集名称必填")
            }

            val quantity = row.quantity ?: 1
            if (quantity <= 0) {
                errors += CollectionImportErrorVo(rowNo, "quantity", "数量必须大于 0")
            }
            val unit = row.unit?.trim()?.ifBlank { null }
            if (unit == null) {
                errors += CollectionImportErrorVo(rowNo, "unit", "计量单位必填")
            }
            if (row.acquireDate == null) {
                errors += CollectionImportErrorVo(rowNo, "acquireDate", "取得日期必填")
            }
            val holder = row.holder?.trim()?.ifBlank { null }
            if (holder == null) {
                errors += CollectionImportErrorVo(rowNo, "holder", "持有人/来源人必填")
            }
            val reason = row.reason?.trim()?.ifBlank { null }
            if (reason == null) {
                errors += CollectionImportErrorVo(rowNo, "reason", "征集理由必填")
            }
            if ((row.proposedPrice ?: 0L) < 0L) {
                errors += CollectionImportErrorVo(rowNo, "proposedPrice", "拟征集价不能小于 0")
            }
            if ((row.appraisedValue ?: 0L) < 0L) {
                errors += CollectionImportErrorVo(rowNo, "appraisedValue", "评估价不能小于 0")
            }

            val categoryId = row.categoryId?.trim()?.ifBlank { null }
            if (categoryId != null && !categoryIds.contains(categoryId)) {
                errors += CollectionImportErrorVo(rowNo, "categoryId", "文物分类不存在")
            }

            val proposedLevel = parseEnum<EnumRelicLevel>(row.proposedLevel, rowNo, "proposedLevel", "拟定级别", errors)
            val source = if(row.source?.trim()=="行政指定接收") EnumSource.DESIGNATED else parseRequiredEnum<EnumSource>(row.source, rowNo, "source", "征集方式", errors)
            if(source==EnumSource.DESIGNATED)errors += CollectionImportErrorVo(rowNo,"source","行政指定接收须使用指定接收登记与承接入口，不能批量导入为普通征集")
            val completeness = parseEnum<EnumCompleteness>(row.completeness, rowNo, "completeness", "完残程度", errors)

            items += AcquisitionVo(
                name = name.ifBlank { null },
                categoryId = categoryId,
                era = row.era?.trim()?.ifBlank { null },
                texture = row.texture?.trim()?.ifBlank { null },
                proposedLevel = proposedLevel,
                source = source,
                quantity = quantity,
                unit = unit,
                acquireDate = row.acquireDate,
                dimensions = DimensionVo(
                    height = row.height?.trim()?.ifBlank { null },
                    mouth = row.mouth?.trim()?.ifBlank { null },
                    bottom = row.bottom?.trim()?.ifBlank { null },
                    belly = row.belly?.trim()?.ifBlank { null },
                    length = row.length?.trim()?.ifBlank { null },
                    width = row.width?.trim()?.ifBlank { null },
                    diameter = row.diameter?.trim()?.ifBlank { null },
                ).takeUnless { dimension ->
                    listOf(
                        dimension.height,
                        dimension.mouth,
                        dimension.bottom,
                        dimension.belly,
                        dimension.length,
                        dimension.width,
                        dimension.diameter,
                    ).all(String?::isNullOrBlank)
                },
                weight = row.weight?.trim()?.ifBlank { null },
                completeness = completeness,
                completeStatus = row.completeStatus?.trim()?.ifBlank { null },
                holder = holder,
                proposedPrice = row.proposedPrice,
                appraisedValue = row.appraisedValue,
                reason = reason,
                images = row.images?.trim()?.ifBlank { null },
                expertOpinionAttachmentIds = row.expertOpinionAttachmentIds?.trim()?.ifBlank { null },
                approvalAttachmentIds = row.approvalAttachmentIds?.trim()?.ifBlank { null },
                contractAttachmentIds = row.contractAttachmentIds?.trim()?.ifBlank { null },
                invoiceAttachmentIds = row.invoiceAttachmentIds?.trim()?.ifBlank { null },
            )
        }

        return AcquisitionImportValidateResultVo(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) items else emptyList(),
        )
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
