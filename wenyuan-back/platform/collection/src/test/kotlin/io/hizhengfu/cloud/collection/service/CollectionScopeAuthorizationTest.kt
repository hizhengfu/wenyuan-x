package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class CollectionScopeAuthorizationTest {

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `cross organization user may inspect flow but may not mutate it`() {
        val user = SecurityUser(
            id = "USER-1",
            name = "测试用户",
            username = "user-1",
            password = "",
            groupIds = emptySet(),
            postIds = emptySet(),
            dataType = 0,
            roles = emptySet(),
            authorities = listOf(SimpleGrantedAuthority(CollectionConst.CROSS_ORG)),
            enabled = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val scope = CollectionScopeResolver("multi", "")

        assertDoesNotThrow { scope.requireFlowAccess("ORG-OTHER", FlowUserOperation.FLOW_INFO, "征集计划") }
        assertThrows(BadRequestException::class.java) {
            scope.requireFlowAccess("ORG-OTHER", FlowUserOperation.SUBMIT, "征集计划")
        }
    }
}
