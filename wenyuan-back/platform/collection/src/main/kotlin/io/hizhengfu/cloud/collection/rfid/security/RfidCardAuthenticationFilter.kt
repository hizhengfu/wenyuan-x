package io.hizhengfu.cloud.collection.rfid.security

import io.hizhengfu.cloud.security.jwt.JwtAuthenticationEntryPoint
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AccountStatusUserDetailsChecker
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/** 仅由 collection 的刷卡过滤链使用，不注册为全局 Servlet Filter。 */
class RfidCardAuthenticationFilter(
    private val jwt: JwtTokenUtil,
    private val users: SystemUserDetailsService,
    private val publicRequests: RequestMatcher,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = publicRequests.matches(request)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            val token = RfidCardTokens.requestToken(jwt, request)
                ?: throw BadCredentialsException("请刷卡登录")
            if (!RfidCardTokens.isCardToken(token)) throw BadCredentialsException("RFID 登录令牌无效")
            val claims = jwt.parseToken(token)
            if (claims.issuedAt == null || claims.expiration == null) throw BadCredentialsException("RFID 登录令牌无效")
            val username = claims.subject?.takeIf(String::isNotBlank)
                ?: throw BadCredentialsException("RFID 登录令牌无效")
            val user = users.loadUserByUsername(username)
            AccountStatusUserDetailsChecker().check(user)
            if (!jwt.validateToken(token, user)) throw BadCredentialsException("RFID 登录已失效，请重新刷卡")
            val authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }
            SecurityContextHolder.getContext().authentication = authentication
        } catch (ex: AuthenticationException) {
            reject(request, response, ex)
            return
        } catch (ex: JwtException) {
            reject(request, response, BadCredentialsException("RFID 登录已失效，请重新刷卡", ex))
            return
        } catch (ex: IllegalArgumentException) {
            reject(request, response, BadCredentialsException("RFID 登录令牌无效", ex))
            return
        }
        chain.doFilter(request, response)
    }

    private fun reject(request: HttpServletRequest, response: HttpServletResponse, ex: AuthenticationException) {
        SecurityContextHolder.clearContext()
        JwtAuthenticationEntryPoint().commence(request, response, ex)
    }
}
