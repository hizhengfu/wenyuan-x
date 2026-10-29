package io.hizhengfu.cloud.collection.rfid.security

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.api.RFID_WORKSTATION_COOKIE
import io.hizhengfu.cloud.collection.rfid.api.RfidWorkstationController
import io.hizhengfu.cloud.collection.rfid.dao.RfidOperatorCards
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidOperatorCardStatus
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.integration.CollectionRfidCardTokenIssuer
import io.hizhengfu.cloud.collection.rfid.service.RfidWorkstationService
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.handle.GlobalExceptionHandler
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import io.hizhengfu.cloud.sys.system.dao.Users
import io.jsonwebtoken.Jwts
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.core.env.MapPropertySource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.LocalDateTime
import java.util.Date

/** 模块类路径没有 JWT/OAuth2 登录模块；后续平台链用测试替身验证路由隔离。 */
class RfidCardSecurityTest {
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mvc: MockMvc
    private lateinit var jwt: JwtTokenUtil
    private lateinit var users: SystemUserDetailsService

    @BeforeEach
    fun setUp() = startContext(WebConfiguration::class.java)

    private fun startContext(configuration: Class<*>) {
        SecurityContextHolder.clearContext()
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            environment.propertySources.addFirst(MapPropertySource("rfid-test", mapOf("apiPath" to "/custom")))
            register(configuration)
            refresh()
        }
        jwt = context.getBean(JwtTokenUtil::class.java)
        users = context.getBean(SystemUserDetailsService::class.java)
        `when`(users.loadUserByUsername("operator")).thenReturn(user())
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean(FilterChainProxy::class.java)).build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        if (::context.isInitialized) context.close()
    }

    @Test
    fun `actual card login issues a usable local session without either platform auth module`() = withWorkstation {
        val response = mvc.perform(post("/custom/collection/rfid/public/card-login")
            .cookie(Cookie(RFID_WORKSTATION_COOKIE, "browser-credential"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"stationCode":"RFID-WS-1","cardUid":"AABBCCDD"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.expirationMillis").value(1_800_000))
            .andReturn().response
        val token = JsonUtils.mapper.readTree(response.contentAsString).path("data").path("token").asString()
        assertTrue(RfidCardTokens.isCardToken(token))
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andExpect(content().string("CARD-USER"))
            .andExpect(header().doesNotExist("X-Test-Platform"))
        mvc.perform(get("/custom/collection/rfid/workstation-access")
            .header("Authorization", "Bearer $token").cookie(Cookie(RFID_WORKSTATION_COOKIE, "browser-credential")))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.workstationId").value("WS-1"))
    }

    @Test
    fun `expired or tampered card tokens return 401 without falling back to platform auth`() {
        val token = cardToken()
        val parts = token.split('.').toMutableList()
        parts[2] = (if (parts[2].startsWith("A")) "B" else "A") + parts[2].drop(1)
        val expired = signedToken(Date(System.currentTimeMillis() - 120_000), Date(System.currentTimeMillis() - 60_000))
        listOf(parts.joinToString("."), expired).forEach {
            mvc.perform(get(IDENTITY).header("Authorization", "Bearer $it"))
                .andExpect(status().isUnauthorized).andExpect(header().doesNotExist("X-Test-Platform"))
        }
        verifyNoInteractions(users)
    }

    @Test
    fun `card tokens without expiry or issuance timestamp are rejected`() {
        listOf(signedToken(null, Date(System.currentTimeMillis() + 60_000)), signedToken(Date(), null)).forEach {
            mvc.perform(get(IDENTITY).header("Authorization", "Bearer $it")).andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(users)
    }

    @Test
    fun `current account status and password reset invalidate a card session`() {
        val token = cardToken()
        listOf(user(enabled = false), user(expireTime = LocalDateTime.now().minusMinutes(1)),
            user(resetAt = Date(System.currentTimeMillis() + 1000))).forEach {
            `when`(users.loadUserByUsername("operator")).thenReturn(it)
            mvc.perform(get(IDENTITY).header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
        }
        `when`(users.loadUserByUsername("operator")).thenThrow(BadCredentialsException("账号不存在"))
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `card authentication preserves method permission checks and reloads permissions`() {
        val token = cardToken()
        mvc.perform(get("/custom/restricted").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        `when`(users.loadUserByUsername("operator")).thenReturn(user(permissions = emptyList()))
        mvc.perform(get("/custom/restricted").header("Authorization", "Bearer $token")).andExpect(status().isForbidden)
    }

    @Test
    fun `ordinary platform tokens and anonymous protected requests stay on the platform chain`() {
        mvc.perform(get(IDENTITY)).andExpect(status().isUnauthorized).andExpect(header().string("X-Test-Platform", "used"))
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer platform-token"))
            .andExpect(status().isOk).andExpect(content().string("PLATFORM-USER"))
            .andExpect(header().string("X-Test-Platform", "used"))
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer ${jwt.generateToken("operator")}"))
            .andExpect(header().string("X-Test-Platform", "used"))
        verifyNoInteractions(users)
    }

    @Test
    fun `without any platform security chain local card login still protects other requests`() {
        context.close()
        startContext(LocalConfiguration::class.java)
        mvc.perform(get(IDENTITY)).andExpect(status().isUnauthorized)
        mvc.perform(get("/outside-api")).andExpect(status().isUnauthorized)
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer ${cardToken()}"))
            .andExpect(status().isOk).andExpect(content().string("CARD-USER"))
        mvc.perform(get("/custom/collection/rfid/public/context")).andExpect(status().isOk)
    }

    @Test
    fun `explicit header takes precedence over a different login cookie`() {
        val token = cardToken()
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer $token").cookie(Cookie("Authorization", "platform-token")))
            .andExpect(status().isOk).andExpect(content().string("CARD-USER"))
        mvc.perform(get(IDENTITY).header("Authorization", "Bearer platform-token").cookie(Cookie("Authorization", token)))
            .andExpect(status().isOk).andExpect(content().string("PLATFORM-USER"))
        mvc.perform(get(IDENTITY).cookie(Cookie("Authorization", token)))
            .andExpect(status().isOk).andExpect(content().string("CARD-USER"))
    }

    @Test
    fun `card requests do not inherit or replace another platform session`() {
        val previous = SecurityContextHolder.createEmptyContext().apply {
            val user = user(id = "PLATFORM-USER")
            authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        }
        val session = MockHttpSession().apply { setAttribute("SPRING_SECURITY_CONTEXT", previous) }
        mvc.perform(get(IDENTITY).session(session).header("Authorization", "Bearer ${cardToken()}"))
            .andExpect(status().isOk).andExpect(content().string("CARD-USER"))
        assertSame(previous, session.getAttribute("SPRING_SECURITY_CONTEXT"))
        assertEquals("PLATFORM-USER", (previous.authentication!!.principal as SecurityUser).id)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `stale card credentials cannot block public workstation context or a fresh card login`() = withWorkstation {
        val stale = signedToken(Date(System.currentTimeMillis() - 120_000), Date(System.currentTimeMillis() - 60_000))
        mvc.perform(get("/custom/collection/rfid/public/context").header("Authorization", "Bearer $stale")
            .cookie(Cookie(RFID_WORKSTATION_COOKIE, "browser-credential")))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.workstationId").value("WS-1"))
        mvc.perform(post("/custom/collection/rfid/public/card-login").header("Authorization", "Bearer $stale")
            .cookie(Cookie(RFID_WORKSTATION_COOKIE, "wrong-browser")).contentType(MediaType.APPLICATION_JSON)
            .content("""{"stationCode":"RFID-WS-1","cardUid":"AABBCCDD"}"""))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(users)
        mvc.perform(post("/custom/collection/rfid/public/card-login").header("Authorization", "Bearer $stale")
            .cookie(Cookie(RFID_WORKSTATION_COOKIE, "browser-credential")).contentType(MediaType.APPLICATION_JSON)
            .content("""{"stationCode":"RFID-WS-1","cardUid":"AABBCCDD"}"""))
            .andExpect(status().isOk)
    }

    private fun cardToken() = RfidCardTokens.issue(jwt, "operator", 1_800_000)
    private fun signedToken(issuedAt: Date?, expiresAt: Date?) = Jwts.builder()
        .header().type("collection-rfid+jwt").and().subject("operator").issuedAt(issuedAt).expiration(expiresAt)
        .signWith(jwt.key.value, Jwts.SIG.HS512).compact()

    private fun withWorkstation(block: () -> Unit) {
        val database = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        transaction(database) {
            SchemaUtils.create(Users, RfidWorkstations, RfidOperatorCards)
            Users.insert {
                it[id] = "CARD-USER"; it[loginName] = "operator"; it[name] = "操作员"; it[password] = "unused"
                it[enabled] = true; it[status] = EnumContentStatus.NORMAL
            }
            RfidWorkstations.insert {
                it[id] = "WS-1"; it[orgId] = "ORG-1"; it[code] = "RFID-WS-1"; it[name] = "工作站"
                it[capabilitiesJson] = "[\"BORROW_RETURN\"]"; it[enrollmentTokenHash] = RfidHex.sha256("unused")
                it[browserCredentialHash] = RfidHex.sha256("browser-credential")
            }
            RfidOperatorCards.insert {
                it[id] = "CARD-1"; it[orgId] = "ORG-1"; it[userId] = "CARD-USER"
                it[cardUidHash] = RfidHex.sha256("AABBCCDD"); it[cardLast4] = "CCDD"
                it[allowedCapabilities] = "BORROW_RETURN"; it[validFrom] = LocalDateTime.now().minusDays(1)
                it[cardStatus] = EnumRfidOperatorCardStatus.ACTIVE; it[issuedAt] = LocalDateTime.now()
            }
            block()
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(RfidCardSecurityConfiguration::class, CollectionRfidCardTokenIssuer::class, RfidWorkstationService::class,
        RfidWorkstationController::class, ProbeController::class, GlobalExceptionHandler::class)
    class LocalConfiguration : WebMvcConfigurer {
        @Bean fun jwt() = JwtTokenUtil(true, "rfid-local-session-test")
        @Bean fun users(): SystemUserDetailsService = mock(SystemUserDetailsService::class.java)
        @Bean fun scope() = CollectionScopeResolver("single", "ORG-1")

        override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
            builder.withJsonConverter(JacksonJsonHttpMessageConverter(JsonUtils.mapper))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LocalConfiguration::class)
    class WebConfiguration {
        @Bean
        @Order(100)
        fun platformChain(http: HttpSecurity): SecurityFilterChain = http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(object : OncePerRequestFilter() {
                override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
                    response.setHeader("X-Test-Platform", "used")
                    if (request.getHeader("Authorization") == "Bearer platform-token") {
                        val user = user(id = "PLATFORM-USER")
                        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
                    }
                    chain.doFilter(request, response)
                }
            }, UsernamePasswordAuthenticationFilter::class.java).build()
    }

    @RestController
    class ProbeController {
        @GetMapping(IDENTITY) fun identity() = SecurityUtils.getUserDetail().id
        @GetMapping("/custom/restricted")
        @PreAuthorize("hasAuthority('COLLECTION_SELECT')")
        fun restricted() = "allowed"
    }

    companion object {
        private const val IDENTITY = "/custom/auth/info"
        private val PERMISSIONS = listOf(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN, CollectionConst.RFID_FINALIZE)
        private fun user(id: String = "CARD-USER", enabled: Boolean = true, expireTime: LocalDateTime? = null,
            resetAt: Date? = null, permissions: List<String> = PERMISSIONS) = SecurityUser(
            id = id, name = "操作员", username = "operator", password = "unused",
            groupIds = emptySet(), postIds = emptySet(), dataType = 0, roles = emptySet(),
            authorities = permissions.map(::SimpleGrantedAuthority), enabled = enabled, expireTime = expireTime,
            lastPasswordResetDate = resetAt,
        )
    }
}
