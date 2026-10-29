package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.InventoryImportRowVo
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryImportValidatorTest {

    @Test
    fun `validate rows resolves collection by registration number and keeps attachments`() {
        val result = InventoryImportValidator.validateRows(
            rows = listOf(
                InventoryImportRowVo(
                    rowNo = 2,
                    batchName = "2026年度清查",
                    totalRegNo = "000001",
                    result = "MATCH",
                    note = "账物相符",
                    attachmentIds = "file-1,file-2",
                ),
            ),
            collectionIds = emptySet(),
            collectionByRegNo = mapOf("000001" to "col-1"),
        )

        assertTrue(result.valid)
        assertEquals(1, result.items.size)
        assertEquals("col-1", result.items.first().collectionId)
        assertEquals("file-1,file-2", result.items.first().attachmentIds)
    }

    @Test
    fun `validate rows accepts inventory result labels from import template`() {
        val result = InventoryImportValidator.validateRows(
            rows = listOf(
                InventoryImportRowVo(
                    rowNo = 2,
                    batchName = "2026年度清查",
                    totalRegNo = "000001",
                    result = "账物相符",
                ),
            ),
            collectionIds = emptySet(),
            collectionByRegNo = mapOf("000001" to "col-1"),
        )

        assertTrue(result.valid)
        assertEquals(EnumInventoryResult.MATCH, result.items.first().result)
    }

    @Test
    fun `validate rows reports missing collection and invalid result`() {
        val result = InventoryImportValidator.validateRows(
            rows = listOf(
                InventoryImportRowVo(rowNo = 3, totalRegNo = "000404", result = "BAD"),
            ),
            collectionIds = emptySet(),
            collectionByRegNo = emptyMap(),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "collectionId" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "result" })
    }

    @Test
    fun `validate rows allows surplus without collection only when note explains the physical item`() {
        val valid = InventoryImportValidator.validateRows(
            rows = listOf(
                InventoryImportRowVo(rowNo = 2, batchName = "2026年度清查", result = "盘盈", note = "库房发现未登账瓷片 1 件"),
            ),
            collectionIds = emptySet(),
            collectionByRegNo = emptyMap(),
        )

        assertTrue(valid.valid)
        assertEquals(EnumInventoryResult.SURPLUS, valid.items.first().result)
        assertEquals(null, valid.items.first().collectionId)
        assertEquals("库房发现未登账瓷片 1 件", valid.items.first().note)

        val missingNote = InventoryImportValidator.validateRows(
            rows = listOf(InventoryImportRowVo(rowNo = 3, result = "盘盈")),
            collectionIds = emptySet(),
            collectionByRegNo = emptyMap(),
        )
        assertFalse(missingNote.valid)
        assertTrue(missingNote.errors.any { it.rowNo == 3 && it.field == "note" })

        val nonSurplus = InventoryImportValidator.validateRows(
            rows = listOf(InventoryImportRowVo(rowNo = 4, result = "盘亏", note = "未找到")),
            collectionIds = emptySet(),
            collectionByRegNo = emptyMap(),
        )
        assertFalse(nonSurplus.valid)
        assertTrue(nonSurplus.errors.any { it.rowNo == 4 && it.field == "collectionId" })
    }
}
