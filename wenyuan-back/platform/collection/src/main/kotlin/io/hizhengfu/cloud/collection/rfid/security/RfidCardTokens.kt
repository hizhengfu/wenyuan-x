package io.hizhengfu.cloud.collection.rfid.security

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import io.jsonwebtoken.Jwts
import jakarta.servlet.http.HttpServletRequest
import java.util.Base64
import java.util.Date

/** 使用 sys 的密钥和验签工具；签名头区分刷卡会话与平台的普通登录令牌。 */
object RfidCardTokens {
    private const val TYPE = "collection-rfid+jwt"

    fun issue(jwt: JwtTokenUtil, username: String, expirationMillis: Long): String {
        require(expirationMillis > 0) { "RFID 刷卡令牌有效期必须大于 0" }
        val now = Date()
        return Jwts.builder().header().type(TYPE).and()
            .subject(username).issuedAt(now).expiration(Date(Math.addExact(now.time, expirationMillis)))
            .signWith(jwt.key.value, Jwts.SIG.HS512).compact()
    }

    fun requestToken(jwt: JwtTokenUtil, request: HttpServletRequest): String? {
        // 工作台的标签页令牌优先于普通登录 Cookie，避免串用另一名操作员的身份。
        val header = request.getHeader(jwt.tokenHeader)
        return if (!header.isNullOrBlank()) jwt.getToken(header) else jwt.getRequestToken(request)
    }

    /** 只用于选择过滤链，不能据此授予身份；过滤器必须继续校验完整签名、时效和账号。 */
    fun isCardToken(token: String?): Boolean {
        if (token == null) return false
        val headerEnd = token.indexOf('.')
        if (headerEnd !in 1..1024) return false
        return runCatching {
            val header = Base64.getUrlDecoder().decode(token.substring(0, headerEnd)).toString(Charsets.UTF_8)
            JsonUtils.fromJson<Map<String, Any?>>(header)?.get("typ") == TYPE
        }.getOrDefault(false)
    }
}
