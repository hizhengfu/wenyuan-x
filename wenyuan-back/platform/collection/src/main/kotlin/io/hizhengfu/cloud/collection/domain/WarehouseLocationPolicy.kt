package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

/** 柜架及其下级库位使用 N.N.N 三段数字编码。 */
object WarehouseLocationPolicy {
    private val cabinetCode = Regex("^[1-9]\\d*\\.[1-9]\\d*\\.[1-9]\\d*$")
    val storageTypes: Set<EnumWarehouseType> = linkedSetOf(
        EnumWarehouseType.WAREHOUSE,
        EnumWarehouseType.AREA,
        EnumWarehouseType.CABINET,
        EnumWarehouseType.LAYER,
        EnumWarehouseType.BOX,
    )
    val exhibitionTypes: Set<EnumWarehouseType> = linkedSetOf(
        EnumWarehouseType.EXHIBITION_HALL,
        EnumWarehouseType.DISPLAY_AREA,
        EnumWarehouseType.SHOWCASE,
    )

    fun isValidCode(type: EnumWarehouseType, code: String?): Boolean =
        type !in setOf(EnumWarehouseType.CABINET, EnumWarehouseType.LAYER, EnumWarehouseType.BOX) ||
            cabinetCode.matches(code?.trim().orEmpty())

    fun requireValidCode(type: EnumWarehouseType, code: String?) {
        if (!isValidCode(type, code)) {
            throw BadRequestException("柜架、层格和囊匣编码必须采用 N.N.N 三段数字格式")
        }
    }

    fun typesForScene(scene: EnumLocationScene): Set<EnumWarehouseType> = when (scene) {
        EnumLocationScene.STORAGE -> storageTypes
        EnumLocationScene.EXHIBITION -> exhibitionTypes
    }

    fun defaultType(scene: EnumLocationScene): EnumWarehouseType = when (scene) {
        EnumLocationScene.STORAGE -> EnumWarehouseType.WAREHOUSE
        EnumLocationScene.EXHIBITION -> EnumWarehouseType.EXHIBITION_HALL
    }

    fun defaultScene(type: EnumWarehouseType): EnumLocationScene =
        if (type in exhibitionTypes) EnumLocationScene.EXHIBITION else EnumLocationScene.STORAGE

    fun isSceneCompatible(scene: EnumLocationScene, type: EnumWarehouseType): Boolean =
        defaultScene(type) == scene

    fun requireSceneCompatible(scene: EnumLocationScene, type: EnumWarehouseType) {
        if (!isSceneCompatible(scene, type)) {
            val sceneLabel = if (scene == EnumLocationScene.STORAGE) "保管" else "展陈"
            val typeLabels = if (scene == EnumLocationScene.STORAGE) "库房、库区、柜架、层格或囊匣" else "展厅、展区或展柜"
            throw BadRequestException("${sceneLabel}场景只能选择${typeLabels}类型")
        }
    }
}

data class WarehouseLocationIssueVo(
    val collectionId: String,
    val totalRegNo: String? = null,
    val collectionName: String? = null,
    val warehouseId: String? = null,
    val reason: String,
)

data class WarehouseLocationCheckVo(
    val checked: Int = 0,
    val matched: Int = 0,
    val issues: List<WarehouseLocationIssueVo> = emptyList(),
)
