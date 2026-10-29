package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.AcquisitionImportRowVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AcquisitionImportValidatorTest {

    @Test
    fun `designated reception cannot be validated as an ordinary acquisition import`() {
        for(source in listOf("DESIGNATED","行政指定接收")) {
            val result=AcquisitionImportValidator.validateRows(listOf(AcquisitionImportRowVo(rowNo=2,name="指定对象",source=source,quantity=1,unit="件",holder="原单位",reason="指定接收")),emptySet())
            assertFalse(result.valid)
            assertTrue(result.items.isEmpty())
            assertTrue(result.errors.any {it.field=="source" && it.message.contains("指定接收登记与承接入口")})
        }
    }

    @Test
    fun `valid import row converts labels and trims fields`() {
        val result = AcquisitionImportValidator.validateRows(
            rows = listOf(
                AcquisitionImportRowVo(
                    rowNo = 2,
                    name = " 青花瓷碗 ",
                    categoryId = "cat-1",
                    era = "明",
                    texture = "瓷",
                    proposedLevel = "未定级",
                    source = "捐赠",
                    quantity = 2,
                    unit = "件",
                    acquireDate = LocalDate.of(2026, 7, 2),
                    height = "12cm",
                    weight = "350g",
                    completeness = "完整",
                    completeStatus = "口沿微磕",
                    holder = "张三",
                    proposedPrice = 120000,
                    appraisedValue = 150000,
                    reason = "符合征集方向",
                    images = "file-1,file-2",
                    contractAttachmentIds = "contract-1",
                    invoiceAttachmentIds = "invoice-1",
                ),
            ),
            categoryIds = setOf("cat-1"),
        )

        assertTrue(result.valid)
        val item = result.items.single()
        assertEquals("青花瓷碗", item.name)
        assertEquals(EnumRelicLevel.UNRATED, item.proposedLevel)
        assertEquals(EnumSource.DONATE, item.source)
        assertEquals("12cm", item.dimensions?.height)
        assertEquals("350g", item.weight)
        assertEquals(EnumCompleteness.COMPLETE, item.completeness)
        assertEquals("contract-1", item.contractAttachmentIds)
        assertEquals(2, item.quantity)
        assertEquals(LocalDate.of(2026, 7, 2), item.acquireDate)
        assertEquals("file-1,file-2", item.images)
    }

    @Test
    fun `validate rows reports required name invalid category and negative numbers`() {
        val result = AcquisitionImportValidator.validateRows(
            rows = listOf(
                AcquisitionImportRowVo(
                    rowNo = 3,
                    name = "",
                    categoryId = "missing-cat",
                    proposedLevel = "BAD",
                    source = "BAD",
                    quantity = 0,
                    proposedPrice = -1,
                    appraisedValue = -2,
                ),
            ),
            categoryIds = setOf("cat-1"),
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "name" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "categoryId" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "proposedLevel" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "source" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "quantity" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "acquireDate" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "proposedPrice" })
        assertTrue(result.errors.any { it.rowNo == 3 && it.field == "appraisedValue" })
    }
}
