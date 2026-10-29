package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobVerifyVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobWriteVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagRetireVo
import io.hizhengfu.cloud.collection.rfid.service.RfidTagService
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/rfid")
class RfidTagController(
    private val service: RfidTagService,
    private val workstationService: RfidWorkstationService,
    private val grantService: SensitiveOperationGrantService,
) {
    @GetMapping("tags")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_SELECT}')")
    fun tags(request: HttpServletRequest) = ApiData(data = service.tags(request))

    @GetMapping("tag-operations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_SELECT}')")
    fun operations(request: HttpServletRequest) = ApiData(data = service.operations(request))

    @Log("创建 RFID 发标任务")
    @PostMapping("tag-jobs")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_WRITE}')")
    fun create(@RequestBody vo: RfidTagJobCreateVo, request: HttpServletRequest): ApiData {
        workstationService.requireBrowserLease(
            request.rfidWorkstationCredential(),
            request.rfidWorkstationClientInstance(),
            vo.workstationId.orEmpty(),
        )
        return ApiData(data = service.createJob(vo))
    }

    @GetMapping("tag-jobs/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_SELECT}','${CollectionConst.RFID_TAG_WRITE}')")
    fun job(@PathVariable id: String, request: HttpServletRequest): ApiData {
        requireJobBinding(id, request)
        return ApiData(data = service.getJob(id))
    }

    @Log("请求写入 RFID 标签")
    @PostMapping("tag-jobs/{id}/write")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_WRITE}')")
    fun write(@PathVariable id: String, @RequestBody vo: RfidTagJobWriteVo, request: HttpServletRequest): ApiData {
        requireJobLease(id, request)
        return ApiData(data = service.requestWrite(id, vo))
    }

    @Log("校验并激活 RFID 标签")
    @PostMapping("tag-jobs/{id}/verify")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_WRITE}')")
    fun verify(
        @PathVariable id: String,
        @RequestBody vo: RfidTagJobVerifyVo,
        @RequestHeader("X-Sensitive-Operation-Grant", required = false) grant: String?,
        request: HttpServletRequest,
    ): ApiData {
        requireJobLease(id, request)
        grantService.consume(
            grant,
            "RFID_TAG_ACTIVATE",
            "RFID_TAG_JOB:$id",
            SensitiveOperationClientFingerprint.fromUserAgent(request.getHeader("User-Agent")),
        )
        return ApiData(data = service.verify(id, vo))
    }

    @Log("报废 RFID 标签")
    @PostMapping("tags/{id}/retire")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_TAG_RETIRE}')")
    fun retire(
        @PathVariable id: String,
        @RequestBody vo: RfidTagRetireVo,
        @RequestHeader("X-Sensitive-Operation-Grant", required = false) grant: String?,
        request: HttpServletRequest,
    ): ApiData {
        grantService.consume(
            grant,
            "RFID_TAG_RETIRE",
            "RFID_TAG:$id",
            SensitiveOperationClientFingerprint.fromUserAgent(request.getHeader("User-Agent")),
        )
        return ApiData(data = service.retire(id, vo))
    }

    private fun requireJobBinding(id: String, request: HttpServletRequest) {
        workstationService.requireBrowserBinding(
            request.rfidWorkstationCredential(),
            service.workstationIdForJob(id),
        )
    }

    private fun requireJobLease(id: String, request: HttpServletRequest) {
        workstationService.requireBrowserLease(
            request.rfidWorkstationCredential(),
            request.rfidWorkstationClientInstance(),
            service.workstationIdForJob(id),
        )
    }
}
