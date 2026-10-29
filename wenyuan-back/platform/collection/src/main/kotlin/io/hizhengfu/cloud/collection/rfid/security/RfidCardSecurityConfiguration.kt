package io.hizhengfu.cloud.collection.rfid.security

import io.hizhengfu.cloud.app.security.ApplicationSecurityConfiguration
import io.hizhengfu.cloud.security.jwt.JwtAccessDeniedHandler
import io.hizhengfu.cloud.security.jwt.JwtAuthenticationEntryPoint
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/** 刷卡会话由 collection 完成；普通登录请求继续交给平台的后续过滤链。 */
@Configuration(proxyBeanMethods = false)
@Import(ApplicationSecurityConfiguration::class)
@EnableWebSecurity
@EnableMethodSecurity
class RfidCardSecurityConfiguration(
    @param:Value("\${apiPath:/api/v1}") private val apiPath: String,
    private val jwt: JwtTokenUtil,
    private val users: SystemUserDetailsService,
    private val beanFactory: ListableBeanFactory,
) {
    @Bean
    @Order(1)
    fun rfidCardSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val paths = PathPatternRequestMatcher.withDefaults()
        val publicRequests = OrRequestMatcher(
            paths.matcher("$apiPath/collection/rfid/public/**"),
            paths.matcher("$apiPath/collection/rfid/runtime/**"),
        )
        val apiRequests = paths.matcher("$apiPath/**")
        val cardRequests = RequestMatcher { request ->
            apiRequests.matches(request) && RfidCardTokens.isCardToken(RfidCardTokens.requestToken(jwt, request))
        }
        // 应用签名链仅接收 /app 请求，不算普通用户认证链；其存在不能取消 RFID 的兜底保护。
        val onlyCardChain by lazy {
            beanFactory.getBeanNamesForType(SecurityFilterChain::class.java)
                .count { it != ApplicationSecurityConfiguration.FILTER_CHAIN_BEAN_NAME } == 1
        }
        return http
            .securityMatcher(RequestMatcher { publicRequests.matches(it) || cardRequests.matches(it) || onlyCardChain })
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .cors { }
            .authorizeHttpRequests {
                it.requestMatchers(publicRequests).permitAll()
                // 保留工作台所需的藏品业务、用户信息、菜单等 API，方法权限仍使用当前用户的真实权限。
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(JwtAuthenticationEntryPoint())
                it.accessDeniedHandler(JwtAccessDeniedHandler())
            }
            .addFilterBefore(RfidCardAuthenticationFilter(jwt, users, publicRequests), UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}
