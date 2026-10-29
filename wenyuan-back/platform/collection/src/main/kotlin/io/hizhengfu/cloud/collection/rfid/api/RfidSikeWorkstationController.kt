package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.rfid.service.RfidSikeConnectionRequest
import io.hizhengfu.cloud.collection.rfid.service.RfidSikeReadBatchVo
import io.hizhengfu.cloud.collection.rfid.service.RfidSikeResultVo
import io.hizhengfu.cloud.collection.rfid.service.RfidSikeWorkstationService
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/rfid/workstation-sike")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_DESK}')")
class RfidSikeWorkstationController(private val service: RfidSikeWorkstationService) {
    @GetMapping("devices")
    fun devices(request: HttpServletRequest) = ApiData(data = service.configurations(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance()))

    @PostMapping("devices/{deviceId}/connect")
    fun connect(@PathVariable deviceId: String, request: HttpServletRequest) =
        ApiData(data = service.connect(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance(), deviceId))

    @PostMapping("devices/{deviceId}/heartbeat")
    fun heartbeat(@PathVariable deviceId: String, @RequestBody body: RfidSikeConnectionRequest, request: HttpServletRequest) =
        ApiData(data = service.heartbeat(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance(), deviceId, body.connectionId))

    @PostMapping("devices/{deviceId}/disconnect")
    fun disconnect(@PathVariable deviceId: String, @RequestBody body: RfidSikeConnectionRequest, request: HttpServletRequest) =
        ApiData(data = service.disconnect(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance(), deviceId, body.connectionId))

    @GetMapping("devices/{deviceId}/commands")
    fun commands(@PathVariable deviceId: String, @RequestParam connectionId: String, request: HttpServletRequest) =
        ApiData(data = service.commands(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance(), deviceId, connectionId))

    @PostMapping("devices/{deviceId}/commands/{commandId}/complete")
    fun complete(
        @PathVariable deviceId: String, @PathVariable commandId: String,
        @RequestBody body: RfidSikeResultVo, request: HttpServletRequest,
    ) = ApiData(data = service.complete(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance(), deviceId, commandId, body))

    @PostMapping("devices/{deviceId}/scan-sessions/{sessionId}/read-batches")
    fun readBatch(
        @PathVariable deviceId: String, @PathVariable sessionId: String,
        @RequestBody body: RfidSikeReadBatchVo, request: HttpServletRequest,
    ) = ApiData(data = service.readBatch(request.rfidWorkstationCredential(), request.rfidWorkstationClientInstance(), deviceId, sessionId, body))
}
