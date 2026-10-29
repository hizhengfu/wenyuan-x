package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidOperatorCards
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.ClassUtils
import java.time.LocalDateTime
import java.util.function.Supplier

class RfidCardAuthenticationTest {
    @AfterEach
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `collection runtime does not bring in either platform authentication module`() {
        val loader = javaClass.classLoader
        assertFalse(ClassUtils.isPresent("io.hizhengfu.cloud.jwt.autoconfigure.JwtSecurityConfig", loader))
        assertFalse(ClassUtils.isPresent("io.hizhengfu.cloud.oauth.autoconfigure.OAuth2ClientSecurityConfig", loader))
    }

    @Test
    fun `workstation starts without issuer and accepts a platform authenticated operator`() = withWorkstation { service ->
        val user = SecurityUser(
            id = "USER-1", name = "操作员", username = "operator", password = "unused",
            groupIds = emptySet(), postIds = emptySet(), dataType = 0, roles = emptySet(),
            authorities = REQUIRED.map(::SimpleGrantedAuthority), enabled = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, "platform-access-token", user.authorities)

        assertEquals("WS-1", service.authorizeCurrentOperator(CREDENTIAL).workstationId)
        val denied = assertThrows(BadRequestException::class.java) { service.cardLogin(LOGIN, CREDENTIAL) }
        assertEquals("当前系统未配置 RFID 卡登录令牌签发器", denied.message)
        assertNull(RfidWorkstations.selectAll().single()[RfidWorkstations.lastLoginAt])
    }

    @Test
    fun `card login forwards issuer token lifetime and user identity`() {
        val issuer = mock(RfidCardTokenIssuer::class.java)
        `when`(issuer.issue("USER-1", "ORG-1", REQUIRED))
            .thenReturn(RfidIssuedUserToken("opaque-platform-token", 300_000L, "平台用户"))
        withWorkstation(issuer) { service ->
            val result = service.cardLogin(LOGIN, CREDENTIAL)
            assertEquals("opaque-platform-token", result.token)
            assertEquals(300_000L, result.expirationMillis)
            assertEquals("平台用户", result.userName)
            assertEquals("USER-1", result.userId)
            assertEquals("WS-1", result.workstationId)
            assertNotNull(RfidWorkstations.selectAll().single()[RfidWorkstations.lastLoginAt])
            verify(issuer).issue("USER-1", "ORG-1", REQUIRED)
        }
    }

    @Test
    fun `invalid browser never reaches platform issuer`() {
        val issuer = mock(RfidCardTokenIssuer::class.java)
        withWorkstation(issuer) { service ->
            assertThrows(BadRequestException::class.java) { service.cardLogin(LOGIN, "wrong-browser") }
            verifyNoInteractions(issuer)
            assertNull(RfidWorkstations.selectAll().single()[RfidWorkstations.lastLoginAt])
        }
    }

    @Test
    fun `invalid card capability or account never reaches platform issuer`() {
        val invalidations: List<() -> Unit> = listOf(
            { RfidOperatorCards.update { it[cardStatus] = EnumRfidOperatorCardStatus.REVOKED }; Unit },
            { RfidOperatorCards.update { it[validUntil] = LocalDateTime.now().minusMinutes(1) }; Unit },
            { RfidOperatorCards.update { it[allowedCapabilities] = "TAG_ISSUE" }; Unit },
            { Users.update { it[enabled] = false }; Unit },
            { Users.update { it[expireTime] = LocalDateTime.now().minusMinutes(1) }; Unit },
        )
        invalidations.forEach { invalidate ->
            val issuer = mock(RfidCardTokenIssuer::class.java)
            withWorkstation(issuer) { service ->
                invalidate()
                assertThrows(BadRequestException::class.java) { service.cardLogin(LOGIN, CREDENTIAL) }
                verifyNoInteractions(issuer)
                assertNull(RfidWorkstations.selectAll().single()[RfidWorkstations.lastLoginAt])
            }
        }
    }

    @Test
    fun `issuer failure does not record a successful card login`() {
        val issuer = mock(RfidCardTokenIssuer::class.java)
        `when`(issuer.issue("USER-1", "ORG-1", REQUIRED)).thenThrow(BadRequestException("刷卡令牌签发失败"))
        withWorkstation(issuer) { service ->
            val failure = assertThrows(BadRequestException::class.java) { service.cardLogin(LOGIN, CREDENTIAL) }
            assertEquals("刷卡令牌签发失败", failure.message)
            assertNull(RfidWorkstations.selectAll().single()[RfidWorkstations.lastLoginAt])
        }
    }

    private fun withWorkstation(issuer: RfidCardTokenIssuer? = null, block: (RfidWorkstationService) -> Unit) {
        var runner = ApplicationContextRunner()
            .withUserConfiguration(RfidWorkstationService::class.java)
            .withBean(CollectionScopeResolver::class.java, Supplier { CollectionScopeResolver("single", "ORG-1") })
        if (issuer != null) runner = runner.withBean(RfidCardTokenIssuer::class.java, Supplier { issuer })
        runner.run { context ->
            assertNull(context.startupFailure)
            val database = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
            transaction(database) {
                SchemaUtils.create(Users, RfidWorkstations, RfidOperatorCards)
                val now = LocalDateTime.now()
                Users.insert {
                    it[id] = "USER-1"; it[loginName] = "operator"; it[name] = "操作员"; it[password] = "unused"
                    it[enabled] = true; it[status] = EnumContentStatus.NORMAL
                }
                RfidWorkstations.insert {
                    it[id] = "WS-1"; it[orgId] = "ORG-1"; it[code] = "RFID-WS-1"; it[name] = "工作站"
                    it[capabilitiesJson] = "[\"BORROW_RETURN\"]"
                    it[enrollmentTokenHash] = RfidHex.sha256("unused")
                    it[browserCredentialHash] = RfidHex.sha256(CREDENTIAL)
                }
                RfidOperatorCards.insert {
                    it[id] = "CARD-1"; it[orgId] = "ORG-1"; it[userId] = "USER-1"
                    it[cardUidHash] = RfidHex.sha256("AABBCCDD"); it[cardLast4] = "CCDD"
                    it[allowedCapabilities] = "BORROW_RETURN"; it[validFrom] = now.minusDays(1)
                    it[cardStatus] = EnumRfidOperatorCardStatus.ACTIVE; it[issuedAt] = now
                }
                block(context.getBean(RfidWorkstationService::class.java))
            }
        }
    }

    private companion object {
        const val CREDENTIAL = "browser-credential"
        val LOGIN = RfidCardLoginVo("RFID-WS-1", "aa:bb:cc:dd")
        val REQUIRED = setOf(CollectionConst.RFID_DESK, CollectionConst.SELECT, CollectionConst.RFID_SCAN, CollectionConst.RFID_FINALIZE)
    }
}
