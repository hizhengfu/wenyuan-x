package io.hizhengfu.cloud.collection.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionOrgTreeScopeTest {

    @Test
    fun `main org scope expands to all descendants`() {
        val nodes = listOf(
            CollectionOrgTreeScope.Node("main", "0"),
            CollectionOrgTreeScope.Node("branch-a", "main"),
            CollectionOrgTreeScope.Node("branch-b", "main"),
            CollectionOrgTreeScope.Node("store-room", "branch-a"),
            CollectionOrgTreeScope.Node("other-main", "0"),
        )

        val ids = CollectionOrgTreeScope.expand(setOf("main"), nodes)

        assertEquals(setOf("main", "branch-a", "branch-b", "store-room"), ids)
    }

    @Test
    fun `branch org scope does not include siblings or parent`() {
        val nodes = listOf(
            CollectionOrgTreeScope.Node("main", "0"),
            CollectionOrgTreeScope.Node("branch-a", "main"),
            CollectionOrgTreeScope.Node("branch-b", "main"),
            CollectionOrgTreeScope.Node("store-room", "branch-a"),
        )

        val ids = CollectionOrgTreeScope.expand(setOf("branch-a"), nodes)

        assertEquals(setOf("branch-a", "store-room"), ids)
    }
}
