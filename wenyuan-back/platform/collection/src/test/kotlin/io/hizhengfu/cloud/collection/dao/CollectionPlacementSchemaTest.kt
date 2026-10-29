package io.hizhengfu.cloud.collection.dao

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionPlacementSchemaTest {

    @Test
    fun `placement table keeps collection and showcase as independent indexed fields`() {
        val columns = CollectionPlacements.columns.map { it.name }.toSet()

        assertTrue("collection_id" in columns)
        assertTrue("location_id" in columns)
        assertTrue("source_biz_id" in columns)
        assertTrue("approved" in columns)
    }
}
