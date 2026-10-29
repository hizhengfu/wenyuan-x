package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.MovementImportRowVo
import io.hizhengfu.cloud.collection.domain.MovementImportValidateResultVo

/** 出入库导入行级校验；一行对应同一单据中的一件藏品。 */
object MovementImportValidator {
    private val moveTypeLabels = mapOf(
        "入库" to EnumMoveType.IN.name,
        "出库" to EnumMoveType.OUT.name,
    )

    private val moveReasonLabels = mapOf(
        "研究" to EnumMoveReason.RESEARCH.name,
        "展览" to EnumMoveReason.EXHIBIT.name,
        "修复" to EnumMoveReason.RESTORE.name,
        "外借" to EnumMoveReason.LOAN.name,
        "数字化" to EnumMoveReason.DIGITIZE.name,
        "盘点" to EnumMoveReason.INVENTORY.name,
        "调拨" to EnumMoveReason.TRANSFER.name,
    )

    fun validateRows(
        rows: List<MovementImportRowVo>,
        collectionIds: Set<String>,
        collectionByRegNo: Map<String, String>,
        firstLevelCollectionIds: Set<String>,
    ): MovementImportValidateResultVo {
        val errors = mutableListOf<CollectionImportErrorVo>()
        val items = mutableListOf<MovementVo>()

        rows.forEachIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            val documentNo = row.documentNo?.trim()?.ifBlank { null }
            if (documentNo == null) {
                errors += CollectionImportErrorVo(rowNo, "documentNo", "出入库单号必填")
            }

            val collectionId = resolveCollectionId(row, rowNo, collectionIds, collectionByRegNo, errors)
            val type = parseEnum<EnumMoveType>(row.type, rowNo, "type", "出入库类型", moveTypeLabels, errors)
            val reason = parseEnum<EnumMoveReason>(row.reason, rowNo, "reason", "出入库事由", moveReasonLabels, errors)
            validateFirstLevelReviewer(row, rowNo, collectionId, firstLevelCollectionIds, errors)

            items += MovementVo(
                documentNo = documentNo,
                collectionId = collectionId,
                type = type,
                reason = reason,
                basis = row.basis?.trim()?.ifBlank { null },
                department = row.department?.trim()?.ifBlank { null },
                fromWarehouseId = row.fromWarehouseId?.trim()?.ifBlank { null },
                toWarehouseId = row.toWarehouseId?.trim()?.ifBlank { null },
                operator = row.operator?.trim()?.ifBlank { null },
                reviewer = row.reviewer?.trim()?.ifBlank { null },
                custodian = row.custodian?.trim()?.ifBlank { null },
                handoverUser = row.handoverUser?.trim()?.ifBlank { null },
                reviewOpinion = row.reviewOpinion?.trim()?.ifBlank { null },
                attachmentIds = row.attachmentIds?.trim()?.ifBlank { null },
                refBizId = row.refBizId?.trim()?.ifBlank { null },
            )
        }

        return MovementImportValidateResultVo(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) items else emptyList(),
        )
    }

    private fun resolveCollectionId(
        row: MovementImportRowVo,
        rowNo: Int,
        collectionIds: Set<String>,
        collectionByRegNo: Map<String, String>,
        errors: MutableList<CollectionImportErrorVo>,
    ): String? {
        val id = row.collectionId?.trim()?.takeIf { it.isNotBlank() }
        if (id != null) {
            if (collectionIds.isNotEmpty() && !collectionIds.contains(id)) {
                errors += CollectionImportErrorVo(rowNo, "collectionId", "藏品不存在或无权操作")
            }
            return id
        }
        val regNo = row.totalRegNo?.trim()?.takeIf { it.isNotBlank() }
        if (regNo != null) {
            return collectionByRegNo[regNo] ?: run {
                errors += CollectionImportErrorVo(rowNo, "collectionId", "总登记号对应藏品不存在或无权操作")
                null
            }
        }
        errors += CollectionImportErrorVo(rowNo, "collectionId", "藏品ID或总登记号必填")
        return null
    }

    private inline fun <reified E : Enum<E>> parseEnum(
        value: String?,
        rowNo: Int,
        field: String,
        label: String,
        labels: Map<String, String>,
        errors: MutableList<CollectionImportErrorVo>,
    ): E? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        if (clean == null) {
            errors += CollectionImportErrorVo(rowNo, field, "$label 必填")
            return null
        }
        val normalized = labels[clean] ?: clean
        return enumValues<E>().firstOrNull { it.name == normalized } ?: run {
            errors += CollectionImportErrorVo(rowNo, field, "$label「$clean」无效")
            null
        }
    }

    private fun validateFirstLevelReviewer(
        row: MovementImportRowVo,
        rowNo: Int,
        collectionId: String?,
        firstLevelCollectionIds: Set<String>,
        errors: MutableList<CollectionImportErrorVo>,
    ) {
        if (collectionId == null || !firstLevelCollectionIds.contains(collectionId)) return
        val reviewer = row.reviewer?.trim()?.takeIf { it.isNotBlank() }
        val operator = row.operator?.trim()?.takeIf { it.isNotBlank() }
        if (reviewer == null) {
            errors += CollectionImportErrorVo(rowNo, "reviewer", "一级文物出入库须填写复核人")
            return
        }
        if (operator != null && reviewer == operator) {
            errors += CollectionImportErrorVo(rowNo, "reviewer", "复核人不能与经办人相同")
        }
    }
}
