package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.UnauthorizedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class CollectionScopeResolverTest {

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `single mode remains unrestricted without authentication`() {
        val scope = CollectionScopeResolver("single", "")

        assertNull(scope.readableOrgIds())
        assertNull(scope.writableOrgIds())
    }

    @Test
    fun `multi mode fails closed without authentication`() {
        val scope = CollectionScopeResolver("multi", "")

        assertThrows(UnauthorizedException::class.java) { scope.readableOrgIds() }
        assertThrows(UnauthorizedException::class.java) { scope.writableOrgIds() }
    }

    @Test
    fun `administrator role id can read and write every organization`() {
        authenticate(user(roles = setOf(EnumRole.ADMIN.value)))
        val scope = CollectionScopeResolver("multi", "")

        assertNull(scope.readableOrgIds())
        assertNull(scope.writableOrgIds())
    }

    @Test
    fun `cross organization authority expands reads but not writes`() {
        authenticate(user(authorities = setOf(CollectionConst.CROSS_ORG)))
        val scope = CollectionScopeResolver("multi", "")

        assertNull(scope.readableOrgIds())
        assertEquals(setOf("__none__"), scope.writableOrgIds())
    }

    @ParameterizedTest @ValueSource(strings = ["cross", "admin"])
    fun `current privileged reads remain bounded to the single configured museum`(grant: String) {
        val current = if (grant == "admin") user(roles = setOf(EnumRole.ADMIN.value))
            else user(authorities = setOf(CollectionConst.CROSS_ORG))
        assertEquals(setOf("configured"), CollectionScopeResolver("single", "configured").currentBusinessReadableOrgIds(current, CollectionConst.CROSS_ORG))
    }

    @Test fun `single current default does not grant an unbound ordinary reader museum membership`() {
        val current = user(authorities = setOf(CollectionConst.SELECT))
        assertEquals(setOf("__none__"), CollectionScopeResolver("single", "configured").currentBusinessReadableOrgIds(current, CollectionConst.CROSS_ORG))
    }

    @Test
    fun `permission codes stored in roles do not expand scope`() {
        authenticate(user(roles = setOf("ADMIN", CollectionConst.CROSS_ORG)))
        val scope = CollectionScopeResolver("multi", "")

        assertEquals(setOf("__none__"), scope.readableOrgIds())
        assertEquals(setOf("__none__"), scope.writableOrgIds())
    }

    private fun authenticate(user: SecurityUser) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    private fun user(
        roles: Set<String> = emptySet(),
        authorities: Set<String> = emptySet(),
    ) = SecurityUser(
        id = "USER-1",
        name = "测试用户",
        username = "user-1",
        password = "",
        groupIds = emptySet(),
        postIds = emptySet(),
        dataType = 0,
        roles = roles,
        authorities = authorities.map(::SimpleGrantedAuthority),
        enabled = true,
    )
}
