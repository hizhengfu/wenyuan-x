package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class InventoryBatchSummaryBuilderTest {

    @Test
    fun `build groups inventory records by batch and counts results attachments and time range`() {
        val summaries = InventoryBatchSummaryBuilder.build(
            listOf(
                inventory("inv-9", "2026年度库房清查", EnumInventoryResult.MATCH, "photo-1", 9),
                inventory("inv-11", "2026年度库房清查", EnumInventoryResult.MISSING, "", 11),
                inventory("inv-10", "2026年度库房清查", EnumInventoryResult.ABNORMAL, "report-1,photo-2", 10),
                inventory("inv-empty-time", "2026年度库房清查", EnumInventoryResult.SURPLUS, null, null),
                inventory("inv-unnamed", "", EnumInventoryResult.MATCH, null, 12),
            ),
        ).associateBy { it.batchName }

        val annual = summaries.getValue("2026年度库房清查")
        assertEquals("inv-9", annual.firstInventoryId)
        assertEquals(4, annual.total)
        assertEquals(1, annual.matchCount)
        assertEquals(1, annual.surplusCount)
        assertEquals(1, annual.missingCount)
        assertEquals(1, annual.abnormalCount)
        assertEquals(2, annual.attachmentCount)
        assertEquals(LocalDateTime.of(2026, 6, 26, 9, 0), annual.firstCheckedAt)
        assertEquals(LocalDateTime.of(2026, 6, 26, 11, 0), annual.lastCheckedAt)

        val unnamed = summaries.getValue("未命名批次")
        assertEquals("inv-unnamed", unnamed.firstInventoryId)
        assertEquals(1, unnamed.total)
        assertEquals(1, unnamed.matchCount)
    }

    private fun inventory(
        id: String,
        batchName: String,
        result: EnumInventoryResult?,
        attachmentIds: String?,
        hour: Int?,
    ) = InventoryVo(
        id = id,
        batchName = batchName,
        result = result,
        attachmentIds = attachmentIds,
        checkedAt = hour?.let { LocalDateTime.of(2026, 6, 26, it, 0) },
    )
}
