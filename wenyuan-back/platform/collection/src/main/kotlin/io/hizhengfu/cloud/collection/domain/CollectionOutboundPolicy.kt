package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

object CollectionOutboundPolicy {
    fun requireInStore(status: EnumCollectionStatus, actionName: String, inventoryHold: Boolean = false) {
        if (inventoryHold) throw BadRequestException("藏品存在未核实处理的清点异常，不能办理$actionName")
        if (status != EnumCollectionStatus.IN_STORE) {
            throw BadRequestException("藏品当前不在库，不能办理$actionName")
        }
    }
}
