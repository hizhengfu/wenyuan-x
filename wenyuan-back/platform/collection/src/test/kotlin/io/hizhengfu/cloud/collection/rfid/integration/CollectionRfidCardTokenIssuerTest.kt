package io.hizhengfu.cloud.collection.rfid.integration

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.collection.rfid.security.RfidCardAuthenticationFilter
import io.hizhengfu.cloud.collection.rfid.security.RfidCardTokens
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CollectionRfidCardTokenIssuerTest {

    @Test
    fun `operator card token uses its dedicated expiration instead of the platform default`() {
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-card-token-issuer?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val jwt = JwtTokenUtil(
            enableCookie = false,
            secret = "rfid-card-token-test-secret",
            defaultExpiration = PLATFORM_EXPIRATION_MILLIS,
            tokenHeader = "Authorization",
        )
        val userDetailsService = mock(SystemUserDetailsService::class.java)
        `when`(userDetailsService.loadUserByUsername(LOGIN_NAME)).thenReturn(
            testUser(
                CollectionConst.RFID_DESK,
                CollectionConst.SELECT,
                CollectionConst.RFID_SCAN,
                CollectionConst.RFID_FINALIZE,
            ),
        )
        val issuer = CollectionRfidCardTokenIssuer(
            jwt,
            userDetailsService,
            CollectionScopeResolver("single", ""),
            CARD_EXPIRATION_MILLIS,
        )
        val requiredAuthorities = setOf(
            CollectionConst.RFID_DESK,
            CollectionConst.SELECT,
            CollectionConst.RFID_SCAN,
            CollectionConst.RFID_FINALIZE,
        )

        transaction(database) {
            SchemaUtils.create(Users)
            Users.insert {
                it[id] = USER_ID
                it[loginName] = LOGIN_NAME
                it[name] = "测试操作员"
                it[password] = "unused"
            }

            val issued = issuer.issue(USER_ID, WORKSTATION_ORG_ID, requiredAuthorities)

            assertEquals(CARD_EXPIRATION_MILLIS, issued.expirationMillis)
            assertEquals(LOGIN_NAME, jwt.getUsernameFromToken(issued.token))
            assertEquals("测试操作员", issued.userName)
            assertTrue(RfidCardTokens.isCardToken(issued.token))
            assertEquals(
                CARD_EXPIRATION_MILLIS,
                jwt.getExpirationDateInterval(issued.token),
            )

            // 不加载 JWT/OAuth2 模块，使用 collection 自己的过滤器完成后续身份恢复。
            val filter = RfidCardAuthenticationFilter(jwt, userDetailsService) { false }
            val request = MockHttpServletRequest("GET", "/api/v1/collection/rfid/workstation-access").apply {
                addHeader("Authorization", "Bearer ${issued.token}")
            }
            SecurityContextHolder.clearContext()
            try {
                var authenticated = false
                filter.doFilter(request, MockHttpServletResponse()) { _, _ ->
                    assertEquals(USER_ID, SecurityUtils.getUserDetail().id)
                    assertTrue(SecurityUtils.getUserDetail().permissions.containsAll(requiredAuthorities))
                    authenticated = true
                }
                assertTrue(authenticated)
            } finally {
                SecurityContextHolder.clearContext()
            }

            `when`(userDetailsService.loadUserByUsername(LOGIN_NAME)).thenReturn(
                testUser(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN),
            )
            val denied = assertThrows(BadRequestException::class.java) {
                issuer.issue(USER_ID, WORKSTATION_ORG_ID, requiredAuthorities)
            }
            assertTrue(denied.message.orEmpty().contains("工作站所需权限"))

            `when`(userDetailsService.loadUserByUsername(LOGIN_NAME)).thenReturn(
                testUser(
                    CollectionConst.RFID_DESK,
                    CollectionConst.SELECT,
                    CollectionConst.RFID_SCAN,
                    CollectionConst.RFID_FINALIZE,
                ),
            )
            val crossOrgIssuer = CollectionRfidCardTokenIssuer(
                jwt,
                userDetailsService,
                CollectionScopeResolver("multi", ""),
                CARD_EXPIRATION_MILLIS,
            )
            val crossOrgDenied = assertThrows(BadRequestException::class.java) {
                crossOrgIssuer.issue(USER_ID, WORKSTATION_ORG_ID, requiredAuthorities)
            }
            assertTrue(crossOrgDenied.message.orEmpty().contains("无权操作该 RFID 工作站"))
        }
    }

    private fun testUser(vararg authorities: String) = SecurityUser(
        id = USER_ID,
        name = "测试操作员",
        username = LOGIN_NAME,
        password = "unused",
        groupIds = emptySet(),
        postIds = emptySet(),
        dataType = 0,
        roles = emptySet(),
        authorities = authorities.map(::SimpleGrantedAuthority),
        enabled = true,
    )

    private companion object {
        const val USER_ID = "USER-1"
        const val LOGIN_NAME = "rfid-card-operator"
        const val WORKSTATION_ORG_ID = "ORG-1"
        const val CARD_EXPIRATION_MILLIS = 30 * 60 * 1000L
        const val PLATFORM_EXPIRATION_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
