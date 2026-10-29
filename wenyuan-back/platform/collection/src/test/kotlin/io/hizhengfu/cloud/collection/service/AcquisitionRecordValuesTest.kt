package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AcquisitionRecordValuesTest {

    @Test
    fun `save values keep acquisition evidence and price in cents`() {
        val values = AcquisitionRecordValues.fromSave(
            AcquisitionVo(
                name = "青铜鼎",
                categoryId = "cat-bronze",
                era = "商代",
                texture = "BRONZE",
                proposedLevel = EnumRelicLevel.FIRST,
                source = EnumSource.PURCHASE,
                quantity = 1,
                unit = "件",
                acquireDate = LocalDate.of(2026, 7, 2),
                dimensions = DimensionVo(height = "42cm", width = "31cm"),
                weight = "18kg",
                completeness = EnumCompleteness.BASIC,
                completeStatus = "鼎足旧补",
                holder = "张三",
                proposedPrice = 1200000,
                appraisedValue = 1500000,
                reason = "具有重要历史价值",
                images = "report-1,image-2",
                expertOpinionAttachmentIds = "expert-1",
                approvalAttachmentIds = "approval-1",
                contractAttachmentIds = "contract-1",
                invoiceAttachmentIds = "invoice-1",
            ),
        )

        assertEquals("青铜鼎", values.name)
        assertEquals("cat-bronze", values.categoryId)
        assertEquals(EnumRelicLevel.FIRST, values.proposedLevel)
        assertEquals(EnumSource.PURCHASE, values.source)
        assertEquals(LocalDate.of(2026, 7, 2), values.acquireDate)
        assertEquals(1200000, values.proposedPrice)
        assertEquals(1500000, values.appraisedValue)
        assertEquals("report-1,image-2", values.images)
        assertEquals("42cm", values.dimensions?.height)
        assertEquals("18kg", values.weight)
        assertEquals(EnumCompleteness.BASIC, values.completeness)
        assertEquals("expert-1", values.expertOpinionAttachmentIds)
        assertEquals("contract-1", values.contractAttachmentIds)
    }
}
