package io.hizhengfu.cloud.collection.rfid.api

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceRuntimeSnapshot
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationDeviceBindingVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationPeripheralRuntimeVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationRuntimeVo
import io.hizhengfu.cloud.collection.rfid.service.RfidWorkstationRuntimeService
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.handle.GlobalExceptionHandler
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.MapPropertySource
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockServletContext
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 验证真实 MVC 路由、方法级鉴权、Cookie 提取、项目 JSON 与异常响应。
 * Service 在此处隔离；数据库授权由 Service 测试覆盖，不将本测试视为 JWT 过滤器或部署验收。
 */
class RfidWorkstationRuntimeControllerTest {
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mvc: MockMvc
    private lateinit var service: RfidWorkstationRuntimeService

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            environment.propertySources.addFirst(MapPropertySource("rfid-test", mapOf("apiPath" to "/api/v1")))
            register(WebConfiguration::class.java)
            refresh()
        }
        service = context.getBean(RfidWorkstationRuntimeService::class.java)
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        if (::context.isInitialized) context.close()
    }

    @Test
    fun `authenticated request uses the cookie and returns only the public runtime projection`() {
        authenticate()
        `when`(service.runtime(CREDENTIAL)).thenReturn(runtime())

        val response = mvc.perform(
            get(ENDPOINT)
                .cookie(Cookie(RFID_WORKSTATION_COOKIE, CREDENTIAL))
                .param("workstationId", "OTHER-STATION")
                .param("stationCode", "OTHER-CODE")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(RfidWorkstationRuntimeController::class.java))
            .andExpect(handler().methodName("runtime"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(200))
            .andReturn().response

        val expected = JsonUtils.mapper.readTree(
            """{
              "workstationId":"WS-1","operatorName":"运行态测试员","checkedAt":1788850000000,
              "peripherals":[{
                "binding":{"id":"BIND-1","deviceId":"DEVICE-1","purpose":"RFID_READ","isDefault":true,"priority":0,"enabled":true},
                "device":{"deviceId":"DEVICE-1","deviceName":"借还读写器","enabled":true,"connectionManaged":true,"connectionState":"ONLINE","lastSeenAt":1788849999000}
              }]
            }""",
        )
        assertEquals(expected, JsonUtils.mapper.readTree(response.contentAsByteArray).get("data"))
        verify(service).runtime(CREDENTIAL)
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `request without authentication is rejected before accessing workstation data`() {
        mvc.perform(get(ENDPOINT).cookie(Cookie(RFID_WORKSTATION_COOKIE, CREDENTIAL)))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @Test
    fun `anonymous authentication is not accepted as an authenticated operator`() {
        SecurityContextHolder.getContext().authentication = AnonymousAuthenticationToken(
            "test-anonymous", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
        )
        // 本测试仅启用方法安全，403 由真实全局异常处理器生成；生产过滤链可能先返回 401。
        mvc.perform(get(ENDPOINT).cookie(Cookie(RFID_WORKSTATION_COOKIE, CREDENTIAL)))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `query parameters and headers cannot replace a missing browser cookie`() {
        authenticate()
        `when`(service.runtime(null)).thenThrow(BadRequestException("工作站注册信息无效"))

        mvc.perform(
            get(ENDPOINT)
                .cookie(Cookie("UNRELATED_COOKIE", CREDENTIAL))
                .header(RFID_WORKSTATION_COOKIE, CREDENTIAL)
                .header("X-RFID-Workstation", CREDENTIAL)
                .param("browserCredential", CREDENTIAL)
                .param("workstationId", "WS-1"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("工作站注册信息无效"))
        verify(service).runtime(null)
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `invalid cookie is not replaced by a credential from another request field`() {
        authenticate()
        `when`(service.runtime("invalid-cookie")).thenThrow(BadRequestException("工作站注册信息无效"))

        mvc.perform(
            get(ENDPOINT)
                .cookie(Cookie(RFID_WORKSTATION_COOKIE, "invalid-cookie"))
                .header(RFID_WORKSTATION_COOKIE, CREDENTIAL)
                .param("browserCredential", CREDENTIAL),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("工作站注册信息无效"))
        verify(service).runtime("invalid-cookie")
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `unmanaged and missing device status never serialize as online`() {
        authenticate()
        val sample = runtime()
        val peripheral = sample.peripherals.single()
        `when`(service.runtime(CREDENTIAL)).thenReturn(
            sample.copy(peripherals = listOf(
                peripheral.copy(device = peripheral.device!!.copy(
                    connectionManaged = false, connectionState = null, lastSeenAt = null,
                )),
                RfidWorkstationPeripheralRuntimeVo(peripheral.binding.copy(id = "BIND-2", deviceId = "DEVICE-2"), null),
            )),
        )

        mvc.perform(get(ENDPOINT).cookie(Cookie(RFID_WORKSTATION_COOKIE, CREDENTIAL)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.peripherals[0].device.connectionManaged").value(false))
            .andExpect(jsonPath("$.data.peripherals[0].device.connectionState").doesNotExist())
            .andExpect(jsonPath("$.data.peripherals[0].device.lastSeenAt").doesNotExist())
            .andExpect(jsonPath("$.data.peripherals[1].binding.deviceId").value("DEVICE-2"))
            .andExpect(jsonPath("$.data.peripherals[1].device").doesNotExist())
    }

    @Test
    fun `empty bindings remain an empty array for the workstation status component`() {
        authenticate()
        `when`(service.runtime(CREDENTIAL)).thenReturn(runtime().copy(peripherals = emptyList()))

        mvc.perform(get(ENDPOINT).cookie(Cookie(RFID_WORKSTATION_COOKIE, CREDENTIAL)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.peripherals").isArray)
            .andExpect(jsonPath("$.data.peripherals").isEmpty)
    }

    private fun authenticate() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "operator", null, listOf(SimpleGrantedAuthority("AUTHENTICATED_TEST_OPERATOR")),
        )
    }

    private fun runtime() = RfidWorkstationRuntimeVo(
        workstationId = "WS-1", operatorName = "运行态测试员", checkedAt = 1788850000000L,
        peripherals = listOf(RfidWorkstationPeripheralRuntimeVo(
            RfidWorkstationDeviceBindingVo("BIND-1", "DEVICE-1", EnumRfidWorkstationDevicePurpose.RFID_READ, true, 0, true),
            RfidDeviceRuntimeSnapshot("DEVICE-1", "借还读写器", true, true, "ONLINE", 1788849999000L),
        )),
    )

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableMethodSecurity(proxyTargetClass = true)
    @Import(RfidWorkstationRuntimeController::class)
    class WebConfiguration : WebMvcConfigurer {
        @Bean
        fun runtimeService(): RfidWorkstationRuntimeService = mock(RfidWorkstationRuntimeService::class.java)

        @Bean
        fun exceptionHandler() = GlobalExceptionHandler(errorDetail = false)

        override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
            builder.withJsonConverter(JacksonJsonHttpMessageConverter(JsonUtils.mapper))
        }
    }

    companion object {
        private const val ENDPOINT = "/api/v1/collection/rfid/workstation-runtime"
        private const val CREDENTIAL = "test-only-browser-credential"
    }
}
