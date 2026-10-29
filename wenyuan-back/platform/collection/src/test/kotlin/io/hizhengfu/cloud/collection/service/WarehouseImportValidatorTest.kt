package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.WarehouseImportRowVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarehouseImportValidatorTest {

    @Test
    fun `validate rows accepts type labels and resolves parent by code`() {
        val result = WarehouseImportValidator.validateRows(
            rows = listOf(
                WarehouseImportRowVo(
                    rowNo = 2,
                    parentCode = "W-01",
                    code = "W-01-A",
                    name = "A 区",
                    thumbnail = "collection/warehouse/a-area.jpg",
                    type = "库区",
                    capacity = 120,
                    temperature = "18-22℃ / 45-55%RH",
                    sort = 10,
                ),
            ),
            warehouseIds = setOf("wh-1"),
            warehouseByCode = mapOf("W-01" to "wh-1"),
        )

        assertTrue(result.valid)
        val item = result.items.single()
        assertEquals("wh-1", item.parentId)
        assertEquals("W-01-A", item.code)
        assertEquals("A 区", item.name)
        assertEquals("collection/warehouse/a-area.jpg", item.thumbnail)
        assertEquals(EnumWarehouseType.AREA, item.type)
        assertEquals(120, item.capacity)
    }

    @Test
    fun `validate rows reports missing name invalid type parent and negative capacity`() {
        val result = WarehouseImportValidator.validateRows(
            rows = listOf(
                WarehouseImportRowVo(
                    rowNo = 3,
                    parentId = "missing-parent",
                    name = "",
                    type = "BAD",
                    capacity = -1,
                ),
            ),
            warehouseIds = setOf("wh-1"),
            warehouseByCode = emptyMap(),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "name" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "type" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "parentId" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "capacity" })
    }

    @Test
    fun `validate rows reports cabinet code format by row`() {
        val result = WarehouseImportValidator.validateRows(
            rows = listOf(WarehouseImportRowVo(rowNo = 7, code = "A-01", name = "一号柜", type = "柜架")),
            warehouseIds = emptySet(),
            warehouseByCode = emptyMap(),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 7 && it.field == "code" })
    }

    @Test
    fun `validate rows accepts exhibition showcase`() {
        val result = WarehouseImportValidator.validateRows(
            rows = listOf(
                WarehouseImportRowVo(
                    rowNo = 2,
                    code = "CASE-01",
                    name = "一号展柜",
                    sceneType = "展陈",
                    type = "展柜",
                ),
            ),
            warehouseIds = emptySet(),
            warehouseByCode = emptyMap(),
        )

        assertTrue(result.valid)
        assertEquals(EnumLocationScene.EXHIBITION, result.items.single().sceneType)
        assertEquals(EnumWarehouseType.SHOWCASE, result.items.single().type)
    }

    @Test
    fun `blank type defaults according to explicit scene`() {
        val result = WarehouseImportValidator.validateRows(
            rows = listOf(WarehouseImportRowVo(rowNo = 2, name = "临展厅", sceneType = "展陈")),
            warehouseIds = emptySet(),
            warehouseByCode = emptyMap(),
        )

        assertTrue(result.valid)
        assertEquals(EnumLocationScene.EXHIBITION, result.items.single().sceneType)
        assertEquals(EnumWarehouseType.EXHIBITION_HALL, result.items.single().type)
    }
}
