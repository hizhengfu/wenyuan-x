package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.exchange.domain.DataExchangeAuthVerifyRequest
import io.hizhengfu.cloud.sys.exchange.security.SensitiveOperationClientFingerprint
import io.hizhengfu.cloud.sys.exchange.security.SensitiveOperationGrantService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** RFID 高风险操作复用系统的一次性、短时、绑定用户和浏览器的二次认证授权。 */
@RestController
@RequestMapping("\${apiPath}/collection/rfid/sensitive-auth")
class RfidSensitiveOperationController(
    private val grantService: SensitiveOperationGrantService,
) {
    @PostMapping("verify")
    @Log("RFID 高风险操作二次认证")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.RFID_FINALIZE}','${CollectionConst.RFID_TAG_WRITE}','${CollectionConst.RFID_TAG_RETIRE}')")
    fun verify(
        @RequestBody request: DataExchangeAuthVerifyRequest,
        servletRequest: HttpServletRequest,
    ): ApiData = ApiData(
        data = grantService.verify(request, SensitiveOperationClientFingerprint.fromUserAgent(servletRequest.getHeader("User-Agent"))),
    )
}
