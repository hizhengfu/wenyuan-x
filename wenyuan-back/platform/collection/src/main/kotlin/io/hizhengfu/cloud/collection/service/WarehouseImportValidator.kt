package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.WarehouseVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.collection.domain.WarehouseImportRowVo
import io.hizhengfu.cloud.collection.domain.WarehouseImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.WarehouseLocationPolicy

/** 库房库位导入行级校验；上级支持现有 parentId 或现有库位编码 parentCode。 */
object WarehouseImportValidator {
    private val warehouseTypeLabels = mapOf(
        "库房" to EnumWarehouseType.WAREHOUSE.name,
        "库区" to EnumWarehouseType.AREA.name,
        "柜架" to EnumWarehouseType.CABINET.name,
        "层格" to EnumWarehouseType.LAYER.name,
        "囊匣" to EnumWarehouseType.BOX.name,
        "展厅" to EnumWarehouseType.EXHIBITION_HALL.name,
        "展区" to EnumWarehouseType.DISPLAY_AREA.name,
        "展柜" to EnumWarehouseType.SHOWCASE.name,
    )
    private val sceneLabels = mapOf("保管" to EnumLocationScene.STORAGE.name, "展陈" to EnumLocationScene.EXHIBITION.name)

    fun validateRows(
        rows: List<WarehouseImportRowVo>,
        warehouseIds: Set<String>,
        warehouseByCode: Map<String, String>,
    ): WarehouseImportValidateResultVo {
        val errors = mutableListOf<CollectionImportErrorVo>()
        val items = mutableListOf<WarehouseVo>()

        rows.forEachIndexed { index, row ->
            val rowNo = row.rowNo.takeIf { it > 0 } ?: index + 2
            val name = row.name?.trim().orEmpty()
            if (name.isBlank()) {
                errors += CollectionImportErrorVo(rowNo, "name", "库位名称必填")
            }
            val parentId = resolveParentId(row, rowNo, warehouseIds, warehouseByCode, errors)
            val parsedType = parseWarehouseType(row.type, rowNo, errors)
            val scene = parseScene(row.sceneType, parsedType, rowNo, errors)
            val type = parsedType ?: scene?.let(WarehouseLocationPolicy::defaultType)
            if (type != null && !WarehouseLocationPolicy.isValidCode(type, row.code)) {
                errors += CollectionImportErrorVo(rowNo, "code", "柜架、层格和囊匣编码必须采用 N.N.N 三段数字格式")
            }
            val capacity = row.capacity
            if (capacity != null && capacity < 0) {
                errors += CollectionImportErrorVo(rowNo, "capacity", "容量不能小于 0")
            }

            items += WarehouseVo(
                parentId = parentId,
                code = row.code?.trim()?.ifBlank { null },
                name = name.ifBlank { null },
                thumbnail = row.thumbnail?.trim()?.ifBlank { null },
                sceneType = scene,
                type = type,
                capacity = capacity,
                temperature = row.temperature?.trim()?.ifBlank { null },
                custodian = row.custodian?.trim()?.ifBlank { null },
                sort = row.sort ?: 0,
            )
        }

        return WarehouseImportValidateResultVo(
            valid = errors.isEmpty(),
            errors = errors,
            items = if (errors.isEmpty()) items else emptyList(),
        )
    }

    private fun resolveParentId(
        row: WarehouseImportRowVo,
        rowNo: Int,
        warehouseIds: Set<String>,
        warehouseByCode: Map<String, String>,
        errors: MutableList<CollectionImportErrorVo>,
    ): String? {
        val parentId = row.parentId?.trim()?.takeIf { it.isNotBlank() }
        if (parentId != null) {
            if (!warehouseIds.contains(parentId)) {
                errors += CollectionImportErrorVo(rowNo, "parentId", "上级库位不存在或无权操作")
            }
            return parentId
        }
        val parentCode = row.parentCode?.trim()?.takeIf { it.isNotBlank() }
        if (parentCode != null) {
            return warehouseByCode[parentCode] ?: run {
                errors += CollectionImportErrorVo(rowNo, "parentCode", "上级库位编码不存在或无权操作")
                null
            }
        }
        return null
    }

    private fun parseWarehouseType(
        value: String?,
        rowNo: Int,
        errors: MutableList<CollectionImportErrorVo>,
    ): EnumWarehouseType? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = warehouseTypeLabels[clean] ?: clean
        return enumValues<EnumWarehouseType>().firstOrNull { it.name == normalized } ?: run {
            errors += CollectionImportErrorVo(rowNo, "type", "库位类型「$clean」无效")
            null
        }
    }

    private fun parseScene(
        value: String?,
        type: EnumWarehouseType?,
        rowNo: Int,
        errors: MutableList<CollectionImportErrorVo>,
    ): EnumLocationScene? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        val scene = if (clean == null) type?.let(WarehouseLocationPolicy::defaultScene) ?: EnumLocationScene.STORAGE else {
            val normalized = sceneLabels[clean] ?: clean
            enumValues<EnumLocationScene>().firstOrNull { it.name == normalized } ?: run {
                errors += CollectionImportErrorVo(rowNo, "sceneType", "位置场景「$clean」无效")
                return null
            }
        }
        if (type != null && !WarehouseLocationPolicy.isSceneCompatible(scene, type)) {
            val expected = if (scene == EnumLocationScene.STORAGE) "库房、库区、柜架、层格或囊匣" else "展厅、展区或展柜"
            errors += CollectionImportErrorVo(rowNo, "type", "${sceneLabels.entries.first { it.value == scene.name }.key}场景只能选择${expected}类型")
        }
        return scene
    }
}
