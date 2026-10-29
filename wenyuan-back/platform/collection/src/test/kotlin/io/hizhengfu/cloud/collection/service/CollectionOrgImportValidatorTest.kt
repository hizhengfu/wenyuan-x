package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionOrgImportRowVo
import io.hizhengfu.cloud.collection.domain.EnumOrgLevel
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.collection.domain.EnumOwnership
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionOrgImportValidatorTest {

    @Test
    fun `validate rows accepts dictionary labels and resolves parent by code`() {
        val result = CollectionOrgImportValidator.validateRows(
            rows = listOf(
                CollectionOrgImportRowVo(
                    rowNo = 2,
                    parentCode = "MAIN",
                    code = "BR-01",
                    name = "一号分馆",
                    shortName = "一号馆",
                    level = "国家二级博物馆",
                    ownership = "国有",
                    legalPerson = "张三",
                    address = "东街 1 号",
                    contact = "李四",
                    phone = "010-12345678",
                    sourceId = "YPG-001",
                    locationRootId = "BUILDING-1",
                    status = "启用",
                    sort = 10,
                ),
            ),
            orgIds = setOf("org-main"),
            orgByCode = mapOf("MAIN" to "org-main"),
            existingCodes = setOf("MAIN"),
            locationRootIds = setOf("BUILDING-1"),
        )

        assertTrue(result.valid)
        val item = result.items.single()
        assertEquals("org-main", item.parentId)
        assertEquals("BR-01", item.code)
        assertEquals("一号分馆", item.name)
        assertEquals("一号馆", item.shortName)
        assertEquals(EnumOrgLevel.NATIONAL_2, item.level)
        assertEquals(EnumOwnership.STATE, item.ownership)
        assertEquals(EnumOrgStatus.ENABLED, item.status)
        assertEquals("BUILDING-1", item.locationRootId)
        assertEquals(10L, item.sort)
    }

    @Test
    fun `validate rows reports required fields duplicate code invalid dictionaries and parent`() {
        val result = CollectionOrgImportValidator.validateRows(
            rows = listOf(
                CollectionOrgImportRowVo(
                    rowNo = 3,
                    parentId = "missing-parent",
                    code = "MAIN",
                    name = "",
                    level = "BAD",
                    ownership = "BAD",
                    status = "BAD",
                    locationRootId = "MISSING-LOCATION",
                    sort = -1,
                ),
                CollectionOrgImportRowVo(rowNo = 4, code = "MAIN", name = "重复分馆"),
            ),
            orgIds = setOf("org-main"),
            orgByCode = mapOf("MAIN" to "org-main"),
            existingCodes = setOf("MAIN"),
            locationRootIds = setOf("BUILDING-1"),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "name" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "code" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "level" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "ownership" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "status" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "sort" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "parentId" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "locationRootId" })
        assertTrue(result.errors.any { it.rowNo == 4 && it.field == "code" })
    }
}
