package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

object MovementExecutionPolicy {
    fun requireBusinessExecution(): Nothing = throw BadRequestException(
        "出入库流水由提用、借用、归还、调拨或修复业务的实际点交和验收生成；首次入馆接收事实请查入馆核收清册，生成待登记及首次登账不补记实时流水；历史记录请通过动态跟踪记录原件导入",
    )
}
