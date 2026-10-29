package io.hizhengfu.cloud.collection.rfid.security

import io.hizhengfu.cloud.app.security.ApplicationSecurityContract
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.mockito.Mockito.mock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.util.ClassUtils
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/** 不依赖用户登录模块时，应用签名与 RFID 仍各自工作，其他请求继续受到兜底保护。 */
class CollectionApplicationSecurityTest : ApplicationSecurityContract() {
    override fun authenticationConfiguration() = TestConfiguration::class.java

    @Test
    fun `collection application signing does not require either user authentication module`() {
        assertFalse(ClassUtils.isPresent("io.hizhengfu.cloud.jwt.Jwt2AutoConfiguration", javaClass.classLoader))
        assertFalse(ClassUtils.isPresent("io.hizhengfu.cloud.oauth.OAuth2AutoConfiguration", javaClass.classLoader))
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(RfidCardSecurityConfiguration::class)
    class TestConfiguration {
        @Bean fun jwtTokenUtil() = JwtTokenUtil(true, "collection-app-security-test")
        @Bean fun users(): SystemUserDetailsService = mock(SystemUserDetailsService::class.java)
    }
}
