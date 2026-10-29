package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.rfid.service.RfidWorkstationRuntimeService
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/rfid/workstation-runtime")
class RfidWorkstationRuntimeController(private val service: RfidWorkstationRuntimeService) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun runtime(request: HttpServletRequest) = ApiData(data = service.runtime(request.rfidWorkstationCredential()))
}
