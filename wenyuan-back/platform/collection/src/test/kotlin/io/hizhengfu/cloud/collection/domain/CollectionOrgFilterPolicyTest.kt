package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionOrgFilterPolicyTest {

    @Test
    fun `requested org is restricted by accessible orgs`() {
        assertEquals(setOf("branch-a"), CollectionOrgFilterPolicy.resolve(setOf("main", "branch-a"), "branch-a"))
        assertEquals(setOf("main", "branch-a"), CollectionOrgFilterPolicy.resolve(setOf("main", "branch-a"), null))
        assertEquals(setOf("branch-a"), CollectionOrgFilterPolicy.resolve(null, "branch-a"))
        assertNull(CollectionOrgFilterPolicy.resolve(null, null))

        assertThrows(BadRequestException::class.java) {
            CollectionOrgFilterPolicy.resolve(setOf("branch-a"), "branch-b")
        }
    }

    @Test
    fun `org list scope follows accessible org ids`() {
        assertEquals(setOf("main", "branch-a"), CollectionOrgFilterPolicy.listScope(setOf("main", "branch-a")))
        assertNull(CollectionOrgFilterPolicy.listScope(null))
    }
}
