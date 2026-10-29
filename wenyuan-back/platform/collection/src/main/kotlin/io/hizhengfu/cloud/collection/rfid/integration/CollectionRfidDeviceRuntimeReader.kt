package io.hizhengfu.cloud.collection.rfid.integration

import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceRuntimePort
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceRuntimeSnapshot
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.museum.device.common.toMuseumUtcEpochMillis
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 只读取同馆绑定设备的最小状态，不向工作台暴露地址、凭据、序列号或采集实例。 */
@Component
@Transactional(readOnly = true)
class CollectionRfidDeviceRuntimeReader : RfidDeviceRuntimePort {
    override fun snapshots(orgId: String, deviceIds: Set<String>): List<RfidDeviceRuntimeSnapshot> {
        if (deviceIds.isEmpty()) return emptyList()
        val devices = MuseumDevices.selectAll().where {
            (MuseumDevices.orgId eq orgId) and (MuseumDevices.id inList deviceIds)
        }.toList()
        val visibleIds = devices.map { it[MuseumDevices.id].value }
        if (visibleIds.isEmpty()) return emptyList()
        val profiles = MuseumRfidDeviceProfiles.selectAll().where {
            (MuseumRfidDeviceProfiles.orgId eq orgId) and (MuseumRfidDeviceProfiles.deviceId inList visibleIds)
        }.associateBy { it[MuseumRfidDeviceProfiles.deviceId] }
        val connections = MuseumDeviceConnections.selectAll().where {
            MuseumDeviceConnections.deviceId inList visibleIds
        }.associateBy { it[MuseumDeviceConnections.deviceId] }
        val now = museumUtcNow()
        return devices.mapNotNull { device ->
            val id = device[MuseumDevices.id].value
            val profile = profiles[id] ?: return@mapNotNull null
            val enabled = device[MuseumDevices.status] == EnumContentStatus.NORMAL &&
                profile[MuseumRfidDeviceProfiles.status] == EnumContentStatus.NORMAL &&
                profile[MuseumRfidDeviceProfiles.enabled]
            val managed = device[MuseumDevices.protocolCode] in setOf("CK_RFID_C5_V1", "SIKE_RFID_WS_V1")
            val connection = connections[id]
            val state = when {
                !managed -> null
                !enabled || connection == null || !connection[MuseumDeviceConnections.leaseUntil].isAfter(now) ->
                    EnumRfidConnectionState.OFFLINE
                else -> connection[MuseumDeviceConnections.state]
            }
            RfidDeviceRuntimeSnapshot(
                deviceId = id,
                deviceName = device[MuseumDevices.name],
                enabled = enabled,
                connectionManaged = managed,
                connectionState = state?.name,
                lastSeenAt = if (managed) connection?.get(MuseumDeviceConnections.lastSeenAt)?.toMuseumUtcEpochMillis() else null,
            )
        }
    }
}
