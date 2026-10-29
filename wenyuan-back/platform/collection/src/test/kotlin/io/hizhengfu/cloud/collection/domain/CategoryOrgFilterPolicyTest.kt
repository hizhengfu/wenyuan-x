package io.hizhengfu.cloud.collection.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoryOrgFilterPolicyTest {

    @Test
    fun `category list keeps standard categories and visible org custom categories`() {
        assertTrue(CategoryOrgFilterPolicy.isVisible(null, null))
        assertTrue(CategoryOrgFilterPolicy.isVisible(null, "org-b"))
        assertTrue(CategoryOrgFilterPolicy.isVisible(setOf("org-a"), null))
        assertTrue(CategoryOrgFilterPolicy.isVisible(setOf("org-a"), "org-a"))
        assertFalse(CategoryOrgFilterPolicy.isVisible(setOf("org-a"), "org-b"))
    }
}
