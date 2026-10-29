package io.hizhengfu.cloud.collection.dao

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarehouseThumbnailSchemaTest {

    @Test
    fun `warehouse table contains thumbnail column`() {
        assertTrue("thumbnail" in Warehouses.columns.map { it.name }.toSet())
    }
}
