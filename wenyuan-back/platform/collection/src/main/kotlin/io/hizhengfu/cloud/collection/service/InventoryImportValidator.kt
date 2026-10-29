package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import io.hizhengfu.cloud.collection.domain.InventoryImportRowVo
import io.hizhengfu.cloud.collection.domain.InventoryImportValidateResultVo

/** 盘点导入行级校验；支持用藏品 ID 或总登记号定位藏品。 */
object InventoryImportValidator {
    private val resultAliases = mapOf(
        "账物相符" to EnumInventoryResult.MATCH.name,
        "盘盈" to EnumInventoryResult.SURPLUS.name,
        "盘亏" to EnumInventoryResult.MISSING.name,
        "状态异常" to EnumInventoryResult.ABNORMAL.name,
    )

    fun validateRows(
        rows: List<InventoryImportRowVo>,
        collectionIds: Set<String>,
        collectionByRegNo: Map<String, String>,
    ): InventoryImportValidateResultVo {
        val errors = mutableListOf<CollectionImportErrorVo>()
        val items = mutableListOf<InventoryVo>()

        rows.forEachIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            val batchName = row.batchName?.trim()?.ifBlank { null }
            if (batchName == null) {
                errors += CollectionImportErrorVo(rowNo, "batchName", "盘点批次必填")
            }
            val result = parseResult(row.result, rowNo, errors)
            val collectionId = resolveCollectionId(row, rowNo, result, collectionIds, collectionByRegNo, errors)
            val note = row.note?.trim()?.ifBlank { null }
            if (result == EnumInventoryResult.SURPLUS && collectionId == null && note == null) {
                errors += CollectionImportErrorVo(rowNo, "note", "盘盈且未绑定藏品时需填写实物说明")
            }
            items += InventoryVo(
                batchName = batchName,
                collectionId = collectionId,
                result = result,
                note = note,
                attachmentIds = row.attachmentIds?.trim()?.ifBlank { null },
            )
        }

        return InventoryImportValidateResultVo(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) items else emptyList(),
        )
    }

    private fun resolveCollectionId(
        row: InventoryImportRowVo,
        rowNo: Int,
        result: EnumInventoryResult?,
        collectionIds: Set<String>,
        collectionByRegNo: Map<String, String>,
        errors: MutableList<CollectionImportErrorVo>,
    ): String? {
        val id = row.collectionId?.trim()?.takeIf { it.isNotBlank() }
        if (id != null) {
            if (!collectionIds.contains(id)) {
                errors += CollectionImportErrorVo(rowNo, "collectionId", "藏品不存在或无权操作")
            }
            val regNo = row.totalRegNo?.trim()?.takeIf { it.isNotEmpty() }
            if (regNo != null && collectionByRegNo[regNo] != id)
                errors += CollectionImportErrorVo(rowNo, "totalRegNo", "藏品ID与总登记号不一致")
            return id
        }
        val regNo = row.totalRegNo?.trim()?.takeIf { it.isNotBlank() }
        if (regNo != null) {
            return collectionByRegNo[regNo] ?: run {
                errors += CollectionImportErrorVo(rowNo, "collectionId", "总登记号对应藏品不存在或无权操作")
                null
            }
        }
        if (result != EnumInventoryResult.SURPLUS) {
            errors += CollectionImportErrorVo(rowNo, "collectionId", "藏品ID或总登记号必填")
        }
        return null
    }

    private fun parseResult(
        value: String?,
        rowNo: Int,
        errors: MutableList<CollectionImportErrorVo>,
    ): EnumInventoryResult? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        if (clean == null) {
            errors += CollectionImportErrorVo(rowNo, "result", "盘点结果必填")
            return null
        }
        val normalized = resultAliases[clean] ?: clean
        return enumValues<EnumInventoryResult>().firstOrNull { it.name == normalized } ?: run {
            errors += CollectionImportErrorVo(rowNo, "result", "盘点结果「$clean」无效")
            null
        }
    }
}
