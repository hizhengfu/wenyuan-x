package io.hizhengfu.cloud.collection.rfid.integration

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardTokenIssuer
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.util.ClassUtils
import java.util.function.Supplier

class CollectionRfidAuthenticationConfigurationTest {
    private val userDetails = mock(SystemUserDetailsService::class.java).also {
        `when`(it.loadUserByUsername("operator")).thenReturn(SecurityUser(
            id = "USER-1", name = "操作员", username = "operator", password = "unused",
            groupIds = emptySet(), postIds = emptySet(), dataType = 0, roles = emptySet(),
            authorities = emptyList(), enabled = true,
        ))
    }
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(CollectionRfidCardTokenIssuer::class.java)
        .withBean(JwtTokenUtil::class.java, Supplier { JwtTokenUtil(false, "card-config-test", 86_400_000L) })
        .withBean(SystemUserDetailsService::class.java, Supplier { userDetails })
        .withBean(CollectionScopeResolver::class.java, Supplier { CollectionScopeResolver("single", "ORG-1") })

    @Test
    fun `collection registers local issuer with default and configured card lifetime`() {
        listOf(null to 1_800_000L, "900000" to 900_000L).forEach { (configured, expected) ->
            val configuredRunner = if (configured == null) runner else
                runner.withPropertyValues("collection.rfid.card-session-expiration-millis=$configured")
            configuredRunner.run { context ->
                assertNull(context.startupFailure)
                assertEquals(1, context.getBeansOfType(RfidCardTokenIssuer::class.java).size)
                val database = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
                transaction(database) {
                    SchemaUtils.create(Users)
                    Users.insert {
                        it[id] = "USER-1"; it[loginName] = "operator"; it[name] = "操作员"; it[password] = "unused"
                    }
                    val issued = context.getBean(RfidCardTokenIssuer::class.java).issue("USER-1", "ORG-1", emptySet())
                    assertEquals(expected, issued.expirationMillis)
                    assertEquals(expected, context.getBean(JwtTokenUtil::class.java).getExpirationDateInterval(issued.token))
                }
            }
        }
    }

    @Test
    fun `local card login is available without JWT or OAuth2 authentication modules`() {
        runner.run { context ->
            assertNull(context.startupFailure)
            assertFalse(ClassUtils.isPresent("io.hizhengfu.cloud.jwt.autoconfigure.JwtSecurityConfig", context.classLoader))
            assertFalse(ClassUtils.isPresent("io.hizhengfu.cloud.oauth.autoconfigure.OAuth2ClientSecurityConfig", context.classLoader))
            assertNotNull(context.getBean(JwtTokenUtil::class.java))
            assertEquals(1, context.getBeansOfType(RfidCardTokenIssuer::class.java).size)
            assertTrue(context.getBean(RfidCardTokenIssuer::class.java) is CollectionRfidCardTokenIssuer)
        }
    }
}
