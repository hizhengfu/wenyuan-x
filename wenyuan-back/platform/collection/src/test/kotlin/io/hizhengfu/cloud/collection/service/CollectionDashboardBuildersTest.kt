package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth

class CollectionDashboardBuildersTest {

    @Test
    fun `movement trend fills empty months and counts by direction`() {
        val now = YearMonth.of(2026, 7)
        val rows = listOf(
            LocalDateTime.of(2026, 7, 2, 10, 0) to EnumMoveType.OUT,
            LocalDateTime.of(2026, 7, 15, 10, 0) to EnumMoveType.IN,
            LocalDateTime.of(2026, 5, 1, 8, 0) to EnumMoveType.OUT,
        )
        val trend = MovementTrendBuilder.build(rows, months = 3, now = now)

        assertEquals(listOf("2026-05", "2026-06", "2026-07"), trend.map { it.month })
        assertEquals(1, trend[0].outCount)
        assertEquals(0, trend[1].inCount + trend[1].outCount)
        assertEquals(1, trend[2].inCount)
        assertEquals(1, trend[2].outCount)
    }

    @Test
    fun `audit summary computes pass rate over decided only`() {
        val summary = CatalogAuditSummaryBuilder.build(
            mapOf(
                EnumCatalogStatus.DRAFT to 4,
                EnumCatalogStatus.PENDING to 2,
                EnumCatalogStatus.SUBMITTED to 3,
                EnumCatalogStatus.APPROVED to 3,
                EnumCatalogStatus.REJECTED to 1,
            ),
        )
        assertEquals(4, summary.draft)
        assertEquals(5, summary.pending)
        assertEquals(3, summary.approved)
        assertEquals(1, summary.rejected)
        assertEquals(75, summary.passRate)

        assertEquals(0, CatalogAuditSummaryBuilder.build(mapOf(EnumCatalogStatus.PENDING to 2)).passRate)
    }

    @Test
    fun `annual stat splits opening new dereg closing by year boundary`() {
        val items = listOf(
            // 期初在册，年内未注销 → 期初 + 期末
            AnnualStatBuilder.Snapshot(LocalDateTime.of(2024, 3, 1, 0, 0), null, "PURCHASE", "FIRST", "陶瓷"),
            // 期初在册，年内注销 → 期初，不计期末
            AnnualStatBuilder.Snapshot(LocalDateTime.of(2024, 5, 1, 0, 0), LocalDateTime.of(2026, 6, 1, 0, 0), "DONATE", "SECOND", "陶瓷"),
            // 年内新增 → 新增 + 期末
            AnnualStatBuilder.Snapshot(LocalDateTime.of(2026, 2, 1, 0, 0), null, "DONATE", "GENERAL", "书画"),
            // 未登账不参与
            AnnualStatBuilder.Snapshot(null, null, "PURCHASE", "GENERAL", null),
        )
        val movements = listOf(
            LocalDateTime.of(2026, 3, 1, 0, 0) to EnumMoveType.OUT,
            LocalDateTime.of(2026, 4, 1, 0, 0) to EnumMoveType.IN,
            LocalDateTime.of(2025, 12, 31, 0, 0) to EnumMoveType.OUT,
        )
        val deregLogs = listOf(
            LocalDateTime.of(2026, 6, 1, 0, 0) to "严重破损",
            LocalDateTime.of(2025, 1, 1, 0, 0) to "调拨",
        )

        val stat = AnnualStatBuilder.build(2026, items, movements, deregLogs)

        assertEquals(2, stat.openingTotal)
        assertEquals(1, stat.newTotal)
        assertEquals(mapOf("DONATE" to 1), stat.newBySource)
        assertEquals(1, stat.deregTotal)
        assertEquals(mapOf("严重破损" to 1), stat.deregByReason)
        assertEquals(2, stat.closingTotal)
        assertEquals(1, stat.inCount)
        assertEquals(1, stat.outCount)
        assertEquals(mapOf("FIRST" to 1, "GENERAL" to 1), stat.closingByLevel)
        assertEquals(mapOf("陶瓷" to 1, "书画" to 1), stat.closingByCategory)
    }
}
