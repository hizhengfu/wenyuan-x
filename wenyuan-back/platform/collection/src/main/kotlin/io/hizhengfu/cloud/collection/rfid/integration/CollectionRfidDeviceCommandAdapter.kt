package io.hizhengfu.cloud.collection.rfid.integration

import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandOutcome
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs
import io.hizhengfu.cloud.museum.device.domain.EnumDeviceCommandStatus
import io.hizhengfu.cloud.museum.device.domain.EnumDeviceCommandType
import io.hizhengfu.cloud.museum.device.domain.EnumRfidDeviceRole
import io.hizhengfu.cloud.museum.device.domain.MuseumDeviceCommandCreateVo
import io.hizhengfu.cloud.museum.device.service.MuseumRfidDeviceService
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/** 把藏品 RFID 业务命令连接到统一设备命令队列。 */
@Component
class CollectionRfidDeviceCommandAdapter(
    private val deviceService: MuseumRfidDeviceService,
) : RfidDeviceCommandPort {
    override fun startInventory(deviceId: String, sessionId: String, antennaMask: Long): String =
        create(
            deviceId,
            sessionId,
            EnumDeviceCommandType.START_INVENTORY,
            mapOf(
                "sessionId" to sessionId,
                "antennaMask" to antennaMask,
                "includeTid" to true,
            ),
        )

    override fun stopInventory(deviceId: String, sessionId: String): String =
        create(deviceId, sessionId, EnumDeviceCommandType.STOP_INVENTORY, emptyMap())

    override fun writeEpc(deviceId: String, jobId: String, epc: String, tidFilter: String): String =
        create(deviceId, jobId, EnumDeviceCommandType.WRITE_EPC, mapOf("epc" to epc, "tidFilter" to tidFilter))

    override fun syncAllowlist(deviceId: String, businessId: String, epcs: List<String>): String =
        create(
            deviceId,
            businessId,
            EnumDeviceCommandType.SYNC_ALLOWLIST,
            mapOf("epcs" to epcs, "replace" to true),
        )

    /** C5 资料只公开了透明 packetContent，未公开 EPC 列表到该字段的编码规则。 */
    override fun supportsAllowlist(deviceId: String): Boolean = false

    override fun alarmOutput(
        deviceId: String,
        businessId: String,
        outputIndex: Int,
        durationMillis: Long,
    ): String = deviceService.createCommandForIntegration(
        MuseumDeviceCommandCreateVo(
            deviceId = deviceId,
            commandType = EnumDeviceCommandType.ALARM_OUTPUT,
            request = mapOf(
                "enabled" to true,
                "continuous" to false,
                "outputIndex" to outputIndex,
                "durationMillis" to durationMillis,
            ),
            clientRequestId = "RFID:ALARM_OUTPUT:$businessId",
            businessType = "COLLECTION_RFID_ANOMALY",
            businessId = businessId,
        ),
    )

    override fun outcome(commandId: String): RfidDeviceCommandOutcome? =
        deviceService.findCommandForIntegration(commandId)?.let { command ->
            val terminal = command.status in setOf(
                EnumDeviceCommandStatus.SUCCEEDED,
                EnumDeviceCommandStatus.FAILED,
                EnumDeviceCommandStatus.TIMED_OUT,
                EnumDeviceCommandStatus.CANCELLED,
            )
            RfidDeviceCommandOutcome(
                terminal = terminal,
                successful = command.status == EnumDeviceCommandStatus.SUCCEEDED,
                message = command.resultSummary,
            )
        }

    override fun allowsDirectInput(deviceId: String): Boolean = deviceService.isHandheldForIntegration(deviceId)

    override fun isOnline(deviceId: String): Boolean = deviceService.isOnlineForIntegration(deviceId)

    override fun isWorkstationLocalDevice(deviceId: String): Boolean = MuseumDevices.selectAll().where {
        (MuseumDevices.id eq deviceId) and (MuseumDevices.protocolCode eq "SIKE_RFID_WS_V1")
    }.limit(1).any()

    override fun supportsAnyRole(deviceId: String, orgId: String, roleCodes: Set<String>): Boolean {
        val roles = roleCodes.mapNotNull { runCatching { EnumRfidDeviceRole.valueOf(it) }.getOrNull() }.toSet()
        return roles.size == roleCodes.size && deviceService.supportsAnyRoleForIntegration(deviceId, orgId, roles)
    }

    private fun create(
        deviceId: String,
        businessId: String,
        type: EnumDeviceCommandType,
        request: Map<String, Any?>,
    ) = deviceService.createCommand(
        MuseumDeviceCommandCreateVo(
            deviceId = deviceId,
            commandType = type,
            request = request,
            clientRequestId = "RFID:${type.name}:$businessId",
            workstationId = when (type) {
                EnumDeviceCommandType.START_INVENTORY, EnumDeviceCommandType.STOP_INVENTORY ->
                    RfidScanSessions.selectAll().where { RfidScanSessions.id eq businessId }
                        .limit(1).firstOrNull()?.get(RfidScanSessions.workstationId)
                EnumDeviceCommandType.WRITE_EPC ->
                    RfidTagJobs.selectAll().where { RfidTagJobs.id eq businessId }
                        .limit(1).firstOrNull()?.get(RfidTagJobs.workstationId)
                else -> null
            },
            businessType = "COLLECTION_RFID",
            businessId = businessId,
        ),
    )
}
