package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.MovementImportRowVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MovementImportValidatorTest {

    @Test
    fun `validate rows resolves collection and accepts dictionary labels`() {
        val result = MovementImportValidator.validateRows(
            rows = listOf(
                MovementImportRowVo(
                    rowNo = 2,
                    documentNo = "CK-2026-001",
                    totalRegNo = "000001",
                    type = "出库",
                    reason = "展览",
                    basis = "展览批准文件",
                    department = "展陈部",
                    operator = "u1",
                    reviewer = "u2",
                    attachmentIds = "file-1,file-2",
                ),
            ),
            collectionIds = emptySet(),
            collectionByRegNo = mapOf("000001" to "col-1"),
            firstLevelCollectionIds = setOf("col-1"),
        )

        assertTrue(result.valid)
        val item = result.items.single()
        assertEquals("col-1", item.collectionId)
        assertEquals(EnumMoveType.OUT, item.type)
        assertEquals(EnumMoveReason.EXHIBIT, item.reason)
        assertEquals("file-1,file-2", item.attachmentIds)
    }

    @Test
    fun `validate rows reports required document type collection and reviewer for first level`() {
        val result = MovementImportValidator.validateRows(
            rows = listOf(
                MovementImportRowVo(
                    rowNo = 3,
                    documentNo = "",
                    collectionId = "first-1",
                    type = "",
                    reason = "BAD",
                    operator = "u1",
                    reviewer = "",
                ),
                MovementImportRowVo(
                    rowNo = 4,
                    totalRegNo = "000404",
                    type = "IN",
                ),
            ),
            collectionIds = setOf("first-1"),
            collectionByRegNo = emptyMap(),
            firstLevelCollectionIds = setOf("first-1"),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "documentNo" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "type" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "reason" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "reviewer" })
        assertTrue(result.errors.any { it.rowNo == 4 && it.field == "collectionId" })
    }
}
