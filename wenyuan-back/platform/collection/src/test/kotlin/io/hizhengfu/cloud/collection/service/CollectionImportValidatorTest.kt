package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionRegistrationDetailsVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionEraMeaning
import io.hizhengfu.cloud.collection.domain.EnumCollectionEraNotation
import io.hizhengfu.cloud.collection.domain.CollectionImportRowVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumCollectionImportMode
import io.hizhengfu.cloud.collection.domain.EnumPreserve
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionImportValidatorTest {

    @Test
    fun `other collection import accepts absent grade but refuses relic grades and preserves nature`() {
        val row = CollectionImportRowVo(name = "标本", categoryId = "cat-1", nature = "其他藏品", source = "捐赠", acquireDate = LocalDate.parse("2026-06-23"), quantity = 1, unit = "件")
        fun check(value: CollectionImportRowVo) = CollectionImportValidator.validateRows(listOf(value), emptySet(), setOf("cat-1"), emptySet())
        val valid = check(row)
        assertTrue(valid.valid, valid.errors.toString())
        assertEquals(io.hizhengfu.cloud.collection.domain.EnumCollectionNature.OTHER, valid.items.single().nature)
        assertEquals(EnumRelicLevel.UNRATED, valid.items.single().level)
        assertFalse(check(row.copy(level = "一级")).valid)
        assertFalse(check(row.copy(nature = "自然资源一级保护")).valid)
        assertFalse(check(row.copy(nature = "科学化石")).valid)
        assertTrue(check(row.copy(nature = "科学化石", level = "未定级")).valid)
    }

    @Test
    fun `upsert mode accepts existing registration number and reports update count`() {
        val result = CollectionImportValidator.validateRows(
            rows = listOf(
                CollectionImportRowVo(
                    rowNo = 2,
                    totalRegNo = "000001",
                    name = "修改后的青花瓷碗",
                    categoryId = "cat-1",
                    level = "未定级",
                    source = "捐赠",
                    acquireDate = LocalDate.parse("2026-06-23"),
                    quantity = 1,
                    unit = "件",
                ),
            ),
            existingRegNos = setOf("000001"),
            categoryIds = setOf("cat-1"),
            warehouseIds = emptySet(),
            mode = EnumCollectionImportMode.UPSERT,
        )

        assertTrue(result.valid)
        assertEquals(0, result.createCount)
        assertEquals(1, result.updateCount)
    }

    @Test
    fun `upsert mode still rejects duplicate registration number inside same file`() {
        val row = CollectionImportRowVo(
            totalRegNo = "000001",
            name = "青花瓷碗",
            categoryId = "cat-1",
            level = "未定级",
            source = "捐赠",
            acquireDate = LocalDate.parse("2026-06-23"),
            quantity = 1,
            unit = "件",
        )
        val result = CollectionImportValidator.validateRows(
            rows = listOf(row.copy(rowNo = 2), row.copy(rowNo = 3)),
            existingRegNos = setOf("000001"),
            categoryIds = setOf("cat-1"),
            warehouseIds = emptySet(),
            mode = EnumCollectionImportMode.UPSERT,
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "totalRegNo" })
    }

    @Test
    fun `validate rows reports missing required catalog fields and duplicate registration numbers`() {
        val result = CollectionImportValidator.validateRows(
            rows = listOf(
                CollectionImportRowVo(rowNo = 2, totalRegNo = "000001", name = "", quantity = 1),
                CollectionImportRowVo(
                    rowNo = 3,
                    totalRegNo = "000001",
                    name = "青花瓷碗",
                    categoryId = "cat-1",
                    level = "未定级",
                    source = "捐赠",
                    acquireDate = LocalDate.parse("2026-06-23"),
                    quantity = 1,
                    unit = "件",
                ),
            ),
            existingRegNos = emptySet(),
            categoryIds = setOf("cat-1"),
            warehouseIds = emptySet(),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 2 && it.field == "name" })
        assertTrue(result.errors.any { it.rowNo == 2 && it.field == "categoryId" })
        assertTrue(result.errors.any { it.rowNo == 2 && it.field == "level" })
        assertTrue(result.errors.any { it.rowNo == 2 && it.field == "source" })
        assertTrue(result.errors.any { it.rowNo == 2 && it.field == "acquireDateRange" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "totalRegNo" })
    }

    @Test
    fun `valid import row converts to collection vo`() {
        val result = CollectionImportValidator.validateRows(
            rows = listOf(
                CollectionImportRowVo(
                    rowNo = 2,
                    totalRegNo = "000010",
                    classNo = "瓷-001",
                    name = "青花瓷碗",
                    level = "FIRST",
                    source = "DONATE",
                    acquireDate = LocalDate.parse("2026-06-23"),
                    quantity = 2,
                    unit = "件",
                    categoryId = "cat-1",
                    warehouseId = "wh-1",
                    coverImage = "image-file-1",
                    height = "12.5cm",
                    mouth = "8.2cm",
                    bottom = "4.1cm",
                    belly = "9.6cm",
                    length = "13cm",
                    width = "11cm",
                    diameter = "8.2cm",
                ),
            ),
            existingRegNos = emptySet(),
            categoryIds = setOf("cat-1"),
            warehouseIds = setOf("wh-1"),
        )

        assertTrue(result.valid)
        assertEquals(1, result.items.size)
        assertEquals("青花瓷碗", result.items.first().name)
        assertEquals(2, result.items.first().quantity)
        assertEquals("image-file-1", result.items.first().coverImage)
        assertEquals("12.5cm", result.items.first().dimensions?.height)
        assertEquals("8.2cm", result.items.first().dimensions?.mouth)
        assertEquals("4.1cm", result.items.first().dimensions?.bottom)
        assertEquals("9.6cm", result.items.first().dimensions?.belly)
        assertEquals("13cm", result.items.first().dimensions?.length)
        assertEquals("11cm", result.items.first().dimensions?.width)
        assertEquals("8.2cm", result.items.first().dimensions?.diameter)
    }

    @Test
    fun `import row accepts dictionary labels`() {
        val result = CollectionImportValidator.validateRows(
            rows = listOf(
                "状态稳定，不需修复",
                "部分损腐，需要修复",
                "腐蚀损毁严重，急需修复",
            ).mapIndexed { index, preserveState ->
                CollectionImportRowVo(
                    rowNo = 2,
                    name = "青花瓷碗${index + 1}",
                    categoryId = "cat-1",
                    level = "未定级",
                    source = "捐赠",
                    acquireDate = LocalDate.parse("2026-06-23"),
                    completeness = "完整",
                    preserveState = preserveState,
                    quantity = 1,
                    unit = "件",
                )
            },
            existingRegNos = emptySet(),
            categoryIds = setOf("cat-1"),
            warehouseIds = emptySet(),
        )

        assertTrue(result.valid)
        val item = result.items.first()
        assertEquals(EnumRelicLevel.UNRATED, item.level)
        assertEquals(EnumSource.DONATE, item.source)
        assertEquals(EnumCompleteness.COMPLETE, item.completeness)
        assertEquals(
            listOf(EnumPreserve.A, EnumPreserve.B, EnumPreserve.C),
            result.items.map { it.preserveState },
        )
    }

    @Test
    fun `range only import preserves complete ledger fields`() {
        val result = CollectionImportValidator.validateRows(
            rows = listOf(
                CollectionImportRowVo(
                    rowNo = 2,
                    name = "民国青白釉印花鸟纹芒口碗",
                    originalName = "近仿宋缠枝印花牡丹碗",
                    categoryId = "cat-1",
                    eraMeaning = "制造年代",
                    eraNotation = "中国历史学年代",
                    era = "中华民国（1912-1949年）",
                    textureCategory1 = "单一质地",
                    textureCategory2 = "无机质/非金属材料",
                    texture = "瓷",
                    level = "三级",
                    source = "拨交",
                    quantity = 1,
                    unit = "件",
                    weightRange = "0.01-1 kg",
                    weight = "0.17",
                    weightUnit = "kg",
                    acquireDateRange = "1949.10.1-1965",
                    acquireYear = "2009",
                    author = "佚名",
                    edition = "民国版",
                    volumeNo = "第一卷",
                ),
            ),
            existingRegNos = emptySet(),
            categoryIds = setOf("cat-1"),
            warehouseIds = emptySet(),
        )

        assertTrue(result.valid)
        val item = result.items.single()
        assertEquals("近仿宋缠枝印花牡丹碗", item.originalName)
        assertEquals("中华民国（1912-1949年）", item.era)
        assertEquals(EnumCollectionEraMeaning.MANUFACTURED, item.registrationDetails?.eraMeaning)
        assertEquals(EnumCollectionEraNotation.CHINESE_HISTORY, item.registrationDetails?.eraNotation)
        assertEquals("单一质地", item.textureCategory1)
        assertEquals("无机质/非金属材料", item.textureCategory2)
        assertEquals("0.01-1 kg", item.weightRange)
        assertEquals("0.17", item.weight)
        assertEquals("kg", item.weightUnit)
        assertEquals("1949.10.1-1965", item.acquireDateRange)
        assertEquals("2009", item.acquireYear)
        assertEquals("佚名", item.author)
        assertEquals("民国版", item.edition)
        assertEquals("第一卷", item.volumeNo)
    }
}
