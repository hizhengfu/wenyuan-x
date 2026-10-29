package io.hizhengfu.cloud.collection.rfid.domain

import com.fasterxml.jackson.annotation.JsonInclude

data class RfidDeviceRuntimeSnapshot(
    val deviceId: String,
    val deviceName: String,
    val enabled: Boolean,
    val connectionManaged: Boolean,
    val connectionState: String?,
    val lastSeenAt: Long?,
)

data class RfidWorkstationPeripheralRuntimeVo(
    val binding: RfidWorkstationDeviceBindingVo,
    val device: RfidDeviceRuntimeSnapshot?,
)

data class RfidWorkstationRuntimeVo(
    val workstationId: String,
    val operatorName: String,
    val checkedAt: Long,
    // 无可用外设仍返回空数组，避免被全局 NON_EMPTY 策略省略而破坏前端契约。
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val peripherals: List<RfidWorkstationPeripheralRuntimeVo>,
)

interface RfidDeviceRuntimePort {
    fun snapshots(orgId: String, deviceIds: Set<String>): List<RfidDeviceRuntimeSnapshot>
}
