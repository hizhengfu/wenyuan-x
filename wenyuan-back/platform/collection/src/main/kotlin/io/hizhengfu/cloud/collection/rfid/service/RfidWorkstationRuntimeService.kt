package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceRuntimePort
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationPeripheralRuntimeVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationRuntimeVo
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class RfidWorkstationRuntimeService(
    private val workstations: RfidWorkstationService,
    private val deviceRuntime: ObjectProvider<RfidDeviceRuntimePort>,
) {
    fun runtime(browserCredential: String?): RfidWorkstationRuntimeVo {
        val station = workstations.workstationForCurrentOperator(browserCredential)
        val bindings = station.deviceBindings.filter { it.enabled }
            .sortedWith(compareBy({ it.purpose.ordinal }, { !it.isDefault }, { it.priority }, { it.deviceId }))
        val devices = deviceRuntime.ifAvailable?.snapshots(station.orgId, bindings.map { it.deviceId }.toSet())
            .orEmpty().associateBy { it.deviceId }
        return RfidWorkstationRuntimeVo(
            workstationId = station.id,
            operatorName = SecurityUtils.getUserDetail().name,
            checkedAt = System.currentTimeMillis(),
            peripherals = bindings.map { RfidWorkstationPeripheralRuntimeVo(it, devices[it.deviceId]) },
        )
    }
}
