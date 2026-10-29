package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageEventCreateVo
import io.hizhengfu.cloud.collection.rfid.service.RfidPassageService
import io.hizhengfu.cloud.collection.rfid.service.RfidScanSessionService
import io.hizhengfu.cloud.collection.rfid.service.RfidTagService
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.domain.COLLECTION_RFID_CALLBACK_CONNECTOR_CODE
import io.hizhengfu.cloud.museum.device.service.MuseumIngestPushTargetService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * museum-ingest 到 collection 的服务间回调入口。此路径匿名放行仅为了绕过用户登录认证，
 * 每次请求仍必须通过“采集配置”中加密保存的推送通道凭据；浏览器端永远不持有该凭据。
 */
@RestController
@RequestMapping("\${apiPath}/collection/rfid/runtime")
class RfidIngestCallbackController(
    private val scanService: RfidScanSessionService,
    private val passageService: RfidPassageService,
    private val tagService: RfidTagService,
    private val pushTargetService: MuseumIngestPushTargetService,
) {
    /** 无业务副作用的服务间鉴权探针；用于证明请求确实来自持有当前回调凭据的采集进程。 */
    @PostMapping("probe")
    fun probe(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = mapOf("status" to "OK"))
    }

    @PostMapping("commands/{commandId}/started")
    fun started(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
        @RequestBody(required = false) body: Map<String, Any?>?,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = scanService.acknowledgeStart(commandId, success, body?.get("message")?.toString()))
    }

    @PostMapping("commands/{commandId}/stopped")
    fun stopped(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
        @RequestBody(required = false) body: Map<String, Any?>?,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = scanService.acknowledgeStop(commandId, success, body?.get("message")?.toString()))
    }

    @PostMapping("commands/{commandId}/written")
    fun written(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
        @RequestBody(required = false) body: Map<String, Any?>?,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = tagService.acknowledgeWrite(commandId, success, body?.get("message")?.toString()))
    }

    @PostMapping("commands/{commandId}/allowlist-synced")
    fun allowlistSynced(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = passageService.acknowledgeAllowlist(commandId, success))
    }

    @PostMapping("commands/{commandId}/alarm-completed")
    fun alarmCompleted(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
        @RequestBody(required = false) body: Map<String, Any?>?,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = passageService.acknowledgeAlarm(commandId, success, body?.get("message")?.toString()))
    }

    @PostMapping("scan-sessions/{sessionId}/reads")
    fun read(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @PathVariable sessionId: String,
        @RequestBody vo: RfidHandheldReadVo,
    ): ApiData {
        requireApiKey(apiKey)
        if (vo.deviceId.isNullOrBlank()) throw BadRequestException("RFID 回调缺少来源设备")
        return ApiData(data = scanService.recordDeviceRead(sessionId, vo))
    }

    @PostMapping("passage/events")
    fun passage(
        @RequestHeader("X-RFID-Ingest-Key") apiKey: String,
        @RequestBody vo: RfidPassageEventCreateVo,
    ): ApiData {
        requireApiKey(apiKey)
        return ApiData(data = passageService.recordEvent(vo))
    }

    private fun requireApiKey(candidate: String) {
        if (!pushTargetService.authenticate(COLLECTION_RFID_CALLBACK_CONNECTOR_CODE, candidate)) {
            throw BadRequestException("RFID 采集服务凭证无效")
        }
    }
}
