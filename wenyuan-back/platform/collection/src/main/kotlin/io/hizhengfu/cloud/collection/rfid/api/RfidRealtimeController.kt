package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.rfid.service.RfidRealtimeEventService
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("\${apiPath}/collection/rfid/realtime")
class RfidRealtimeController(private val service: RfidRealtimeEventService) {
    @GetMapping("events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_DESK}','${CollectionConst.RFID_SCAN}')")
    fun events(
        @RequestParam workstationId: String,
        @RequestHeader(name = "Last-Event-ID", required = false) lastEventId: String?,
    ): SseEmitter = service.subscribe(workstationId, lastEventId?.toLongOrNull())

    @GetMapping("scope-events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}')")
    fun scopeEvents(
        @RequestHeader(name = "Last-Event-ID", required = false) lastEventId: String?,
    ): SseEmitter = service.subscribeScope(lastEventId?.toLongOrNull())
}
