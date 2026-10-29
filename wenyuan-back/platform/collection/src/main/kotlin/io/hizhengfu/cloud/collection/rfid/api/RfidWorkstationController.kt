package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidOperatorCardStatus
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardLoginVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidOperatorCardSaveVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationSaveVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationEnrollVo
import io.hizhengfu.cloud.collection.rfid.service.RfidWorkstationService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.time.Duration

@RestController
@RequestMapping("\${apiPath}/collection/rfid")
class RfidWorkstationController(private val service: RfidWorkstationService) {
    @GetMapping("workstations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_DESK}','${CollectionConst.RFID_CARD_MANAGE}','${CollectionConst.RFID_PASSAGE}')")
    fun workstations() = ApiData(data = service.workstations())

    @Log("保存 RFID 工作站")
    @PostMapping("workstations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_CARD_MANAGE}')")
    fun saveWorkstation(@RequestBody vo: RfidWorkstationSaveVo) = ApiData(data = service.saveWorkstation(vo))

    @Log("重置 RFID 工作站令牌")
    @PostMapping("workstations/{id}/reset-token")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_CARD_MANAGE}')")
    fun resetToken(@PathVariable id: String) = ApiData(data = service.resetEnrollmentToken(id))

    @GetMapping("operator-cards")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_CARD_MANAGE}')")
    fun cards() = ApiData(data = service.operatorCards())

    @Log("保存 RFID 人员卡")
    @PostMapping("operator-cards")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_CARD_MANAGE}')")
    fun saveCard(@RequestBody vo: RfidOperatorCardSaveVo) = ApiData(data = service.saveOperatorCard(vo))

    @Log("变更 RFID 人员卡状态")
    @PostMapping("operator-cards/{id}/status")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_CARD_MANAGE}')")
    fun cardStatus(
        @PathVariable id: String,
        @RequestParam status: EnumRfidOperatorCardStatus,
        @RequestParam(required = false) reason: String?,
    ) = ApiData(data = service.updateCardStatus(id, status, reason))

    /** 本机首次录入注册令牌后，只在 HttpOnly Cookie 中保存后端新签发的浏览器凭证。 */
    @PostMapping("public/enroll")
    fun enroll(
        @RequestBody vo: RfidWorkstationEnrollVo,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiData {
        val (result, credential) = service.enrollBrowser(vo)
        val cookie = ResponseCookie.from(RFID_WORKSTATION_COOKIE, credential)
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofDays(365))
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return ApiData(data = result)
    }

    /** 此接口由 Security 配置匿名放行，Service 内执行工作站、卡和用户三重校验。 */
    @PostMapping("public/card-login")
    fun cardLogin(@RequestBody vo: RfidCardLoginVo, request: HttpServletRequest): ApiData {
        val credential = request.rfidWorkstationCredential()
        return ApiData(data = service.cardLogin(vo, credential))
    }

    /** URL 参数不能改变本机绑定；客户端必须以 HttpOnly Cookie 对应的工作站为准。 */
    @GetMapping("public/context")
    fun browserContext(request: HttpServletRequest): ApiData {
        val credential = request.rfidWorkstationCredential()
        return ApiData(data = service.browserContext(credential))
    }

    /** 账号密码登录后进入独立工作台前，按本机工作站全部能力执行一次完整权限校验。 */
    @GetMapping("workstation-access")
    @PreAuthorize("isAuthenticated()")
    fun workstationAccess(request: HttpServletRequest): ApiData = ApiData(
        data = service.authorizeCurrentOperator(request.rfidWorkstationCredential()),
    )

    @PostMapping("public/lease/acquire")
    fun acquireLease(
        @RequestParam(defaultValue = "false") force: Boolean,
        request: HttpServletRequest,
    ): ApiData = ApiData(
        data = service.acquireBrowserLease(
            request.rfidWorkstationCredential(),
            request.rfidWorkstationClientInstance(),
            force,
        ),
    )

    @PostMapping("public/lease/heartbeat")
    fun heartbeatLease(request: HttpServletRequest): ApiData = ApiData(
        data = service.renewBrowserLease(
            request.rfidWorkstationCredential(),
            request.rfidWorkstationClientInstance(),
        ),
    )

    @PostMapping("public/lease/release")
    fun releaseLease(request: HttpServletRequest): ApiData = ApiData(
        data = service.releaseBrowserLease(
            request.rfidWorkstationCredential(),
            request.rfidWorkstationClientInstance(),
        ),
    )
}
