package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import io.hizhengfu.cloud.collection.domain.InventoryBatchSummaryVo
import java.time.LocalDateTime

object InventoryBatchSummaryBuilder {
    private const val UNNAMED_BATCH = "未命名批次"

    fun build(items: List<InventoryVo>): List<InventoryBatchSummaryVo> =
        items
            .groupBy { it.batchName?.trim()?.takeIf(String::isNotEmpty) ?: UNNAMED_BATCH }
            .map { (batchName, rows) ->
                val checkedTimes = rows.mapNotNull { it.checkedAt }
                val firstInventoryId = rows
                    .sortedWith(compareBy<InventoryVo> { it.checkedAt ?: LocalDateTime.MAX }.thenBy { it.id ?: "" })
                    .firstOrNull()
                    ?.id
                InventoryBatchSummaryVo(
                    batchName = batchName,
                    firstInventoryId = firstInventoryId,
                    total = rows.size,
                    matchCount = rows.count { it.result == EnumInventoryResult.MATCH },
                    surplusCount = rows.count { it.result == EnumInventoryResult.SURPLUS },
                    missingCount = rows.count { it.result == EnumInventoryResult.MISSING },
                    abnormalCount = rows.count { it.result == EnumInventoryResult.ABNORMAL },
                    attachmentCount = rows.count { !it.attachmentIds.isNullOrBlank() },
                    firstCheckedAt = checkedTimes.minOrNull(),
                    lastCheckedAt = checkedTimes.maxOrNull(),
                )
            }
            .sortedWith(compareByDescending<InventoryBatchSummaryVo> { it.lastCheckedAt ?: LocalDateTime.MIN }.thenBy { it.batchName })
}
