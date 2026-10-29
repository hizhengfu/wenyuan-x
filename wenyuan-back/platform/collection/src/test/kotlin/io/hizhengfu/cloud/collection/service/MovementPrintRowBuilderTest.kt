package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MovementPrintRowBuilderTest {
    @Test
    fun `out and in documents retain their own recorded location after master changes`() {
        val current = CollectionVo(id = "c1", totalRegNo = "new-number", name = "current-name",
            warehouseId = "safe", warehouseName = "safe-room")
        val out = MovementVo(id = "out", collectionId = "c1", type = EnumMoveType.OUT,
            totalRegNo = "000014", collectionName = "recorded-name",
            fromWarehouseId = "old", toWarehouseId = null)
        val intake = MovementVo(id = "in", collectionId = "c1", type = EnumMoveType.IN,
            totalRegNo = "000014", collectionName = "recorded-name",
            fromWarehouseId = "old", toWarehouseId = "safe")
        val label: (MovementVo, String?) -> String? = { _, id -> id?.let { "location-$it" } }

        val outRow = MovementPrintRowBuilder.build(listOf(out), { current }, label).single()
        val inRow = MovementPrintRowBuilder.build(listOf(intake), { current }, label).single()

        assertEquals("000014", outRow.totalRegNo)
        assertEquals("recorded-name", outRow.name)
        assertEquals("old", outRow.warehouseId)
        assertEquals("location-old", outRow.warehouseName)
        assertEquals("safe", inRow.warehouseId)
        assertEquals("location-safe", inRow.warehouseName)
        assertEquals("safe", current.warehouseId)
    }
}
