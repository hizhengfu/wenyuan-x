package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 出入库单优先采用流水留存的身份和事件库位；旧流水缺失身份时由打印字段说明主档或冻结档案来源。 */
internal object MovementPrintRowBuilder {
    fun build(
        movements: List<MovementVo>,
        collection: (MovementVo) -> CollectionVo?,
        locationLabel: (MovementVo, String?) -> String?,
    ): List<CollectionVo> = movements.mapNotNull { movement ->
        val id = movement.collectionId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val current = collection(movement) ?: return@mapNotNull null
        if (current.id != id) throw BadRequestException("出入库打印关联藏品与流水不一致")
        val eventLocationId = when (movement.type) {
            EnumMoveType.OUT -> movement.fromWarehouseId
            EnumMoveType.IN -> movement.toWarehouseId
            null -> null
        }
        current.copy(
            totalRegNo = movement.totalRegNo?.takeIf(String::isNotBlank) ?: current.totalRegNo,
            name = movement.collectionName?.takeIf(String::isNotBlank) ?: current.name,
            warehouseId = eventLocationId,
            warehouseName = locationLabel(movement, eventLocationId),
        )
    }.distinctBy { it.id }
}
