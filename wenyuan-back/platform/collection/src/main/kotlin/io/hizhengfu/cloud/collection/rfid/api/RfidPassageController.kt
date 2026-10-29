package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.rfid.domain.RfidAnomalyProcessVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageEventCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageGrantCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageGrantRevokeVo
import io.hizhengfu.cloud.collection.rfid.service.RfidPassageService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/rfid/passage")
class RfidPassageController(private val service: RfidPassageService) {
    @GetMapping("devices")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}')")
    fun devices() = ApiData(data = service.gateDevices())

    @Log("创建 RFID 通行授权")
    @PostMapping("grants")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}')")
    fun createGrant(@RequestBody vo: RfidPassageGrantCreateVo) = ApiData(data = service.createGrant(vo))

    @GetMapping("grants")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}')")
    fun grants(request: HttpServletRequest) = ApiData(data = service.grants(request))

    @Log("撤销 RFID 通行授权")
    @PostMapping("grants/{id}/revoke")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}')")
    fun revokeGrant(@PathVariable id: String, @RequestBody vo: RfidPassageGrantRevokeVo) =
        ApiData(data = service.revokeGrant(id, vo))

    @GetMapping("events")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}')")
    fun events(request: HttpServletRequest) = ApiData(data = service.events(request))

    @GetMapping("anomalies")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}','${CollectionConst.RFID_ANOMALY_PROCESS}')")
    fun anomalies(request: HttpServletRequest) = ApiData(data = service.anomalies(request))

    @GetMapping("anomalies/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_PASSAGE}','${CollectionConst.RFID_ANOMALY_PROCESS}')")
    fun anomaly(@PathVariable id: String) = ApiData(data = service.anomaly(id))

    @Log("处置 RFID 通行异常")
    @PostMapping("anomalies/{id}/process")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_ANOMALY_PROCESS}')")
    fun process(@PathVariable id: String, @RequestBody vo: RfidAnomalyProcessVo) = ApiData(data = service.processAnomaly(id, vo))

    /** 采集运行时写入不可变通行事实；生产部署应限制为采集服务身份。 */
    @PostMapping("runtime/events")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUSEUM_DEVICE_CONTROL')")
    fun runtimeEvent(@RequestBody vo: RfidPassageEventCreateVo) = ApiData(data = service.recordEvent(vo))
}
