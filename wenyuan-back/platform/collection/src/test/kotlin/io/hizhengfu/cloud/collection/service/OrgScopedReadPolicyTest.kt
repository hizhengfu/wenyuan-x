package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrgScopedReadPolicyTest {

    @Test
    fun `read scope allows unrestricted mode and matching organizations only`() {
        assertTrue(OrgScopedReadPolicy.isVisible(null, "org-a"))
        assertTrue(OrgScopedReadPolicy.isVisible(setOf("org-a", "org-b"), "org-b"))
        assertFalse(OrgScopedReadPolicy.isVisible(setOf("org-a"), "org-b"))
        assertFalse(OrgScopedReadPolicy.isVisible(setOf("org-a"), null))
    }

    @Test
    fun `read scope throws for invisible records`() {
        assertDoesNotThrow {
            OrgScopedReadPolicy.requireVisible(setOf("org-a"), "org-a", "藏品")
        }

        val error = assertThrows(BadRequestException::class.java) {
            OrgScopedReadPolicy.requireVisible(setOf("org-a"), "org-b", "藏品")
        }
        assertTrue(error.message!!.contains("无权查看藏品"))
    }
}
