package io.hizhengfu.cloud.collection.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CollectionOrgOverviewBuilderTest {

    @Test
    fun `overview keeps direct metrics and aggregates descendant metrics`() {
        val tree = CollectionOrgOverviewBuilder.build(
            orgs = listOf(
                CollectionOrgOverviewOrg("main", "0", "主馆", "MAIN"),
                CollectionOrgOverviewOrg("branch-a", "main", "东部分馆", "A"),
                CollectionOrgOverviewOrg("branch-b", "main", "西部分馆", "B"),
            ),
            collections = listOf(
                CollectionOrgOverviewCollection("main", EnumRelicLevel.FIRST, EnumCollectionStatus.IN_STORE, hasImage = true, hasAttachment = false),
                CollectionOrgOverviewCollection("branch-a", EnumRelicLevel.SECOND, EnumCollectionStatus.ON_LOAN, hasImage = false, hasAttachment = true),
                CollectionOrgOverviewCollection("branch-b", EnumRelicLevel.THIRD, EnumCollectionStatus.IN_USE, hasImage = true, hasAttachment = true),
            ),
        )

        val main = tree.single()

        assertEquals(1, main.direct.total)
        assertEquals(3, main.aggregate.total)
        assertEquals(1, main.aggregate.byLevel["FIRST"])
        assertEquals(1, main.aggregate.byLevel["SECOND"])
        assertEquals(1, main.aggregate.byStatus["ON_LOAN"])
        assertEquals(2, main.aggregate.imageCount)
        assertEquals(2, main.aggregate.attachmentCount)
        assertEquals(listOf("branch-a", "branch-b"), main.children.map { it.id })
    }

    @Test
    fun `overview orders root and child nodes like org page list`() {
        val older = LocalDateTime.of(2026, 1, 1, 10, 0)
        val newer = older.plusDays(1)
        val tree = CollectionOrgOverviewBuilder.build(
            orgs = listOf(
                CollectionOrgOverviewOrg("root-b", "0", "乙馆", "B", sort = 20),
                CollectionOrgOverviewOrg("root-a", "0", "甲馆", "A", sort = 10),
                CollectionOrgOverviewOrg("child-older", "root-a", "Alpha Branch", "A-1", sort = 1, createTime = older),
                CollectionOrgOverviewOrg("child-sort-zero", "root-a", "丙分馆", "C-1", sort = 0),
                CollectionOrgOverviewOrg("child-newer", "root-a", "Beta Branch", "B-1", sort = 1, createTime = newer),
            ),
            collections = emptyList(),
        )

        assertEquals(listOf("root-a", "root-b"), tree.map { it.id })
        assertEquals(listOf("child-sort-zero", "child-newer", "child-older"), tree.first().children.map { it.id })
    }
}
