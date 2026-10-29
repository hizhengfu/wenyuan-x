package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadBatchVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldSealVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanFinalizeVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanSessionCreateVo
import io.hizhengfu.cloud.collection.rfid.service.RfidScanSessionService
import io.hizhengfu.cloud.collection.rfid.service.RfidWorkstationService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.exchange.security.SensitiveOperationClientFingerprint
import io.hizhengfu.cloud.sys.exchange.security.SensitiveOperationGrantService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/rfid/scan-sessions")
class RfidScanSessionController(
    private val service: RfidScanSessionService,
    private val workstationService: RfidWorkstationService,
    private val grantService: SensitiveOperationGrantService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun list(request: HttpServletRequest) = ApiData(data = service.findPage(request))

    @GetMapping("handheld/workstations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun handheldWorkstations() = ApiData(data = service.handheldWorkstations())

    @GetMapping("handheld/devices")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun handheldDevices() = ApiData(data = service.mobileDevices())

    @GetMapping("handheld/inventory/{id}/manifest")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}','COLLECTION_STORAGE')")
    fun inventoryManifest(@PathVariable id: String) = ApiData(data = service.mobileInventoryManifest(id))

    @GetMapping("handheld/tasks")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}','COLLECTION_STORAGE')")
    fun handheldTasks(request: HttpServletRequest) = ApiData(data = service.handheldTasks(request))

    @GetMapping("handheld/active")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun activeHandheldSessions() = ApiData(data = service.activeHandheldSessions())

    @GetMapping("active")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_DESK}','${CollectionConst.RFID_SCAN}')")
    fun active(@RequestParam workstationId: String, request: HttpServletRequest): ApiData {
        workstationService.requireBrowserBinding(request.rfidWorkstationCredential(), workstationId)
        return ApiData(data = service.active(workstationId))
    }

    @Log("创建 RFID 扫描会话")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun create(@RequestBody vo: RfidScanSessionCreateVo, request: HttpServletRequest): ApiData {
        if (!vo.directInput) workstationService.requireBrowserLease(
            request.rfidWorkstationCredential(),
            request.rfidWorkstationClientInstance(),
            vo.workstationId.orEmpty(),
        )
        return ApiData(data = service.create(vo))
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_DESK}','${CollectionConst.RFID_SCAN}')")
    fun get(@PathVariable id: String, request: HttpServletRequest): ApiData {
        requireFixedSessionBinding(id, request)
        return ApiData(data = service.get(id))
    }

    @Log("开始 RFID 读取")
    @PostMapping("{id}/start")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun start(@PathVariable id: String, request: HttpServletRequest): ApiData {
        requireFixedSessionLease(id, request)
        return ApiData(data = service.start(id))
    }

    @PostMapping("{id}/reads")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun read(@PathVariable id: String, @RequestBody vo: RfidHandheldReadVo) = ApiData(data = service.recordRead(id, vo))

    @PostMapping("{id}/read-batches")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun readBatch(@PathVariable id: String, @RequestBody vo: RfidHandheldReadBatchVo) =
        ApiData(data = service.recordReadBatch(id, vo))

    /** 封存移动盘库观察证据，不执行出入库、不生成盘亏结论，不需要业务执行授权。 */
    @PostMapping("{id}/seal-observations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun sealObservations(@PathVariable id: String, @RequestBody vo: RfidHandheldSealVo) =
        ApiData(data = service.sealMobileObservations(id, vo))

    @Log("停止 RFID 读取")
    @PostMapping("{id}/stop")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun stop(@PathVariable id: String, request: HttpServletRequest): ApiData {
        requireFixedSessionLease(id, request)
        return ApiData(data = service.stop(id))
    }

    @GetMapping("{id}/preview")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_DESK}','${CollectionConst.RFID_SCAN}')")
    fun preview(@PathVariable id: String, request: HttpServletRequest): ApiData {
        requireFixedSessionBinding(id, request)
        return ApiData(data = service.preview(id))
    }

    @Log("确认 RFID 扫描业务")
    @PostMapping("{id}/finalize")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_FINALIZE}')")
    fun finalize(
        @PathVariable id: String,
        @RequestBody vo: RfidScanFinalizeVo,
        @RequestHeader("X-Sensitive-Operation-Grant", required = false) grant: String?,
        request: HttpServletRequest,
    ): ApiData {
        requireFixedSessionLease(id, request)
        val current = service.get(id)
        if (current.state.name != "FINALIZED") {
            grantService.consume(
                grant,
                "RFID_FINALIZE",
                "RFID_SESSION:$id",
                SensitiveOperationClientFingerprint.fromUserAgent(request.getHeader("User-Agent")),
            )
        }
        return ApiData(data = service.finalize(id, vo))
    }

    @Log("取消 RFID 扫描会话")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_SCAN}')")
    fun cancel(@PathVariable id: String, request: HttpServletRequest): ApiData {
        requireFixedSessionLease(id, request)
        return ApiData(data = service.cancel(id))
    }

    @Log("强制终止 RFID 扫描会话")
    @PostMapping("{id}/force-terminate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN')")
    fun forceTerminate(
        @PathVariable id: String,
        @RequestParam(required = false) reason: String?,
    ) = ApiData(data = service.forceTerminate(id, reason))

    private fun requireFixedSessionBinding(id: String, request: HttpServletRequest) {
        service.fixedWorkstationId(id)?.let { workstationId ->
            workstationService.requireBrowserBinding(request.rfidWorkstationCredential(), workstationId)
        }
    }

    private fun requireFixedSessionLease(id: String, request: HttpServletRequest) {
        service.fixedWorkstationId(id)?.let { workstationId ->
            workstationService.requireBrowserLease(
                request.rfidWorkstationCredential(),
                request.rfidWorkstationClientInstance(),
                workstationId,
            )
        }
    }

    /** 采集运行时回写命令结果；生产部署应仅允许内网采集服务身份访问。 */
    @PostMapping("runtime/commands/{commandId}/started")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUSEUM_DEVICE_CONTROL')")
    fun started(
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
        @RequestParam(required = false) message: String?,
    ) = ApiData(data = service.acknowledgeStart(commandId, success, message))

    @PostMapping("runtime/commands/{commandId}/stopped")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUSEUM_DEVICE_CONTROL')")
    fun stopped(
        @PathVariable commandId: String,
        @RequestParam success: Boolean,
        @RequestParam(required = false) message: String?,
    ) = ApiData(data = service.acknowledgeStop(commandId, success, message))
}
