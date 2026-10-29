package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadVo
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.domain.EnumDeviceCommandStatus
import io.hizhengfu.cloud.museum.device.domain.EnumDeviceCommandType
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionEventType
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.domain.MuseumDeviceCommandResultVo
import io.hizhengfu.cloud.museum.device.domain.MuseumDeviceCommandVo
import io.hizhengfu.cloud.museum.device.service.MuseumRfidDeviceService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID

data class RfidSikeLocalConfig(
    val serviceUrl: String = "",
    val connectType: Int = 1,
    val connectAddress: String = "",
    val antennaMask: Long = 1,
    val tidWordLength: Int = 6,
)

data class RfidSikeDeviceVo(
    val deviceId: String, val deviceName: String, val modelCode: String,
    val hardwareSerial: String, val config: RfidSikeLocalConfig,
)
data class RfidSikeConnectionVo(val connectionId: String)
data class RfidSikeConnectionRequest(val connectionId: String = "")
data class RfidSikeResultVo(
    val connectionId: String = "",
    val success: Boolean = false,
    val resultCode: String? = null,
    val message: String? = null,
)
data class RfidSikeReadVo(
    val epc: String? = null,
    val tid: String? = null,
    val antenna: Int? = null,
    val rssi: Int? = null,
    val readerSerialNumber: String? = null,
)
data class RfidSikeReadBatchVo(
    val connectionId: String = "",
    val streamId: String = "",
    val sequence: Long = -1,
    val reads: List<RfidSikeReadVo> = emptyList(),
)

/** 浏览器只是工作站硬件传输通道；业务命令及读数仍由服务端逐项授权和落库。 */
@Component
@Transactional
class RfidSikeWorkstationService(
    private val workstations: RfidWorkstationService,
    private val devices: MuseumRfidDeviceService,
    private val scans: RfidScanSessionService,
    private val tags: RfidTagService,
) {
    @Transactional(readOnly = true)
    fun configurations(credential: String?, instance: String?): List<RfidSikeDeviceVo> {
        val station = authorizedStation(credential, instance)
        return station.deviceBindings.filter { it.enabled }.map { it.deviceId }.distinct()
            .mapNotNull { id -> device(station.orgId, id) }
    }

    fun connect(credential: String?, instance: String?, deviceId: String): RfidSikeConnectionVo {
        val station = authorizedStation(credential, instance)
        val lock = MuseumDevices.selectAll().where { MuseumDevices.id eq deviceId }.limit(1)
        lock.forUpdate()
        lock.firstOrNull() ?: throw BadRequestException("设备不存在")
        requireDevice(station.orgId, station.deviceBindings.filter { it.enabled }.map { it.deviceId }, deviceId)
        val active = MuseumDeviceConnections.selectAll().where {
            MuseumDeviceConnections.deviceId eq deviceId
        }.limit(1).firstOrNull()
        if (active != null && active[MuseumDeviceConnections.state] == EnumRfidConnectionState.ONLINE &&
            active[MuseumDeviceConnections.leaseUntil].isAfter(museumUtcNow())
        ) throw BadRequestException("斯科读写器仍由其他连接占用，请等待旧连接释放")
        val connectionId = UUID.randomUUID().toString()
        devices.registerConnection(deviceId, runtimeId(credential, instance), connectionId, "WORKSTATION_LOCAL", museumUtcNow().plusSeconds(35), "SIKE_WS_V1")
        return RfidSikeConnectionVo(connectionId)
    }

    fun heartbeat(credential: String?, instance: String?, deviceId: String, connectionId: String): Boolean {
        requireConnection(credential, instance, deviceId, connectionId)
        return devices.refreshConnectionLease(deviceId, connectionId, museumUtcNow().plusSeconds(35))
    }

    fun disconnect(credential: String?, instance: String?, deviceId: String, connectionId: String): Boolean {
        requireConnection(credential, instance, deviceId, connectionId)
        val closed = devices.closeConnection(deviceId, connectionId, EnumRfidConnectionEventType.REMOTE_CLOSED, "工作站本机服务连接已关闭")
        if (closed) scans.failLocalDeviceSessions(deviceId, "斯科读写器连接已断开，扫描结果需重新核对")
        return closed
    }

    fun commands(credential: String?, instance: String?, deviceId: String, connectionId: String): List<MuseumDeviceCommandVo> {
        val station = requireConnection(credential, instance, deviceId, connectionId)
        val runtime = runtimeId(credential, instance)
        return devices.claimCommands(runtime, setOf(deviceId), 1, station.id).mapNotNull { command ->
            if (command.commandType !in SUPPORTED_COMMANDS || command.businessType != "COLLECTION_RFID") {
                devices.completeCommand(command.id, runtime, MuseumDeviceCommandResultVo(false, "UNSUPPORTED", "本机读写器不支持该命令"))
                null
            } else {
                command.takeIf { devices.markDispatched(command.id, runtime) }
            }
        }
    }

    fun complete(
        credential: String?, instance: String?, deviceId: String, commandId: String, result: RfidSikeResultVo,
    ): Boolean {
        val station = requireConnection(credential, instance, deviceId, result.connectionId)
        val runtime = runtimeId(credential, instance)
        val command = devices.findCommandForIntegration(commandId) ?: throw BadRequestException("设备命令不存在")
        if (command.deviceId != deviceId || command.runtimeInstanceId != runtime || command.workstationId != station.id ||
            command.businessType != "COLLECTION_RFID" || command.commandType !in SUPPORTED_COMMANDS ||
            command.status !in setOf(EnumDeviceCommandStatus.CLAIMED, EnumDeviceCommandStatus.DISPATCHED, EnumDeviceCommandStatus.ACKED) ||
            command.expiresAt <= System.currentTimeMillis()
        ) throw BadRequestException("设备命令不属于当前工作站连接")
        val success = result.success
        val changed = devices.completeCommand(
            commandId, runtime,
            MuseumDeviceCommandResultVo(success, result.resultCode?.take(64), result.message?.take(500)),
        )
        if (!changed) throw BadRequestException("设备命令状态已变化，请刷新")
        devices.touchConnection(deviceId, result.connectionId, museumUtcNow().plusSeconds(35))
        when (command.commandType) {
            EnumDeviceCommandType.START_INVENTORY -> scans.acknowledgeStart(commandId, success, result.message)
            EnumDeviceCommandType.STOP_INVENTORY -> scans.acknowledgeStop(commandId, success, result.message)
            EnumDeviceCommandType.WRITE_EPC -> tags.acknowledgeWrite(commandId, success, result.message)
            else -> Unit
        }
        return true
    }

    fun readBatch(
        credential: String?, instance: String?, deviceId: String, sessionId: String, batch: RfidSikeReadBatchVo,
    ): Int {
        if (batch.reads.isEmpty() || batch.reads.size > 100) throw BadRequestException("读数批次必须包含 1 至 100 条")
        val station = requireConnection(credential, instance, deviceId, batch.connectionId)
        val session = scans.get(sessionId)
        if (session.directInput || session.workstationId != station.id || session.deviceId != deviceId) {
            throw BadRequestException("读数与当前工作站会话不一致")
        }
        val expectedSerial = requireDevice(station.orgId, station.deviceBindings.filter { it.enabled }.map { it.deviceId }, deviceId).hardwareSerial
        if (batch.reads.any { it.readerSerialNumber?.trim()?.uppercase() != expectedSerial.uppercase() }) {
            throw BadRequestException("读数报告的读写器序列号与设备档案不一致")
        }
        val accepted = scans.recordDeviceReadBatch(
            sessionId, deviceId, batch.streamId, batch.sequence,
            batch.reads.map { item ->
                RfidHandheldReadVo(deviceId = deviceId, epc = item.epc, tid = item.tid, antenna = item.antenna, rssi = item.rssi)
            },
        )
        devices.touchConnection(deviceId, batch.connectionId, museumUtcNow().plusSeconds(35))
        return accepted
    }

    private fun authorizedStation(credential: String?, instance: String?) =
        workstations.workstationForCurrentOperator(credential).also {
            workstations.requireBrowserLease(credential, instance, it.id)
        }

    private fun requireDevice(orgId: String, boundIds: List<String>, deviceId: String): RfidSikeDeviceVo {
        if (deviceId !in boundIds) throw BadRequestException("设备未绑定到当前工作站")
        return device(orgId, deviceId) ?: throw BadRequestException("设备不是已启用的斯科本机读写器")
    }

    private fun device(orgId: String, deviceId: String): RfidSikeDeviceVo? {
        val row = MuseumDevices.selectAll().where {
            (MuseumDevices.id eq deviceId) and (MuseumDevices.orgId eq orgId)
        }.limit(1).firstOrNull() ?: return null
        if (row[MuseumDevices.status] != EnumContentStatus.NORMAL || row[MuseumDevices.protocolCode] != PROTOCOL ||
            row[MuseumDevices.modelCode] !in MODELS
        ) return null
        val profile = MuseumRfidDeviceProfiles.selectAll().where {
            (MuseumRfidDeviceProfiles.deviceId eq deviceId) and (MuseumRfidDeviceProfiles.orgId eq orgId)
        }.limit(1).firstOrNull() ?: return null
        if (profile[MuseumRfidDeviceProfiles.status] != EnumContentStatus.NORMAL || !profile[MuseumRfidDeviceProfiles.enabled]) return null
        val hardwareSerial = profile[MuseumRfidDeviceProfiles.hardwareSerial]?.trim()?.takeIf(String::isNotEmpty)
            ?: throw BadRequestException("斯科读写器需要登记硬件序列号，以核对实际连接设备")
        val config = try {
            JsonUtils.fromJson(row[MuseumDevices.instanceConfig] ?: "", RfidSikeLocalConfig::class.java)
        } catch (_: Exception) { null } ?: throw BadRequestException("斯科设备本机服务配置无效")
        validateConfig(config)
        val antennaCount = profile[MuseumRfidDeviceProfiles.antennaCount]
        if (antennaCount < 16 && config.antennaMask >= (1L shl antennaCount)) {
            throw BadRequestException("斯科设备天线掩码超出已登记的天线数量")
        }
        return RfidSikeDeviceVo(
            deviceId, row[MuseumDevices.name], row[MuseumDevices.modelCode],
            hardwareSerial, config,
        )
    }

    private fun validateConfig(config: RfidSikeLocalConfig) {
        val uri = runCatching { URI(config.serviceUrl) }.getOrNull()
        if (uri?.scheme != "ws" || uri.host != "127.0.0.1" || uri.port !in 1..65535 ||
            uri.rawPath?.matches(Regex("/rfidService/[A-Za-z0-9_-]{1,64}")) != true ||
            uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null
        ) throw BadRequestException("斯科本机服务地址必须是 ws://127.0.0.1:端口/rfidService/标识")
        if (config.connectType !in 1..4 || config.connectAddress.length !in 1..100 ||
            config.antennaMask !in 1..65535 || config.tidWordLength !in 1..16
        ) throw BadRequestException("斯科设备连接参数无效")
    }

    private fun requireConnection(credential: String?, instance: String?, deviceId: String, connectionId: String): io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationVo {
        val station = authorizedStation(credential, instance)
        requireDevice(station.orgId, station.deviceBindings.filter { it.enabled }.map { it.deviceId }, deviceId)
        val current = MuseumDeviceConnections.selectAll().where {
            (MuseumDeviceConnections.deviceId eq deviceId) and
                (MuseumDeviceConnections.connectionId eq connectionId) and
                (MuseumDeviceConnections.runtimeInstanceId eq runtimeId(credential, instance))
        }.limit(1).firstOrNull()
        if (current == null || current[MuseumDeviceConnections.state] != EnumRfidConnectionState.ONLINE ||
            !current[MuseumDeviceConnections.leaseUntil].isAfter(museumUtcNow())
        ) throw BadRequestException("斯科设备连接已失效")
        return station
    }

    private fun runtimeId(credential: String?, instance: String?) =
        rfidWorkstationRuntimeId(credential.orEmpty(), instance.orEmpty())

    companion object {
        private const val PROTOCOL = "SIKE_RFID_WS_V1"
        private val MODELS = setOf("SIKE_C5_WS_READER_V1", "SIKE_5A_WS_READER_V1")
        private val SUPPORTED_COMMANDS = setOf(
            EnumDeviceCommandType.START_INVENTORY, EnumDeviceCommandType.STOP_INVENTORY,
            EnumDeviceCommandType.WRITE_EPC,
        )
    }
}
