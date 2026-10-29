package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 半开区间 [from, to)，相邻班次在同一时刻交接不会产生空档。 */
data class SecurityDutyInterval(val from: LocalDateTime, val to: LocalDateTime)

data class WarehouseSecurityShiftRequest(
    val requestId: String,
    val officerId: String,
    val planned: SecurityDutyInterval,
    val responsibility: String,
    val fileIds: String,
)

data class WarehouseSecurityAttendanceRequest(
    val requestId: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime? = null,
    val situation: String,
    val handoverNotes: String,
    val fileIds: String,
    val previousId: String? = null,
    val correctionReason: String? = null,
)

data class WarehouseSecurityHandoverRequest(
    val requestId: String,
    val outgoingShiftId: String,
    val outgoingAttendanceId: String,
    val incomingAttendanceId: String,
    val handedOverAt: LocalDateTime,
    val opinion: String,
    val fileIds: String,
)

data class WarehouseSecurityCancellationRequest(val requestId: String, val reason: String, val fileIds: String)

data class WarehouseSecurityDutyRecord<T>(val id: String, val actorId: String, val recordedAt: LocalDateTime, val value: T)

data class WarehouseSecurityShiftView(
    val shift: WarehouseSecurityDutyRecord<WarehouseSecurityShiftRequest>,
    val cancellation: WarehouseSecurityDutyRecord<WarehouseSecurityCancellationRequest>?,
    val attendance: List<WarehouseSecurityDutyRecord<WarehouseSecurityAttendanceRequest>>,
    val handovers: List<WarehouseSecurityDutyRecord<WarehouseSecurityHandoverRequest>>,
)

/** 由业务层从同一库区、经过授权和原件核对的记录生成；排班不能代替实际值守。 */
data class WarehouseSecurityDutyCoverageSource(
    val planned: SecurityDutyInterval,
    val actualStart: LocalDateTime? = null,
    val actualEnd: LocalDateTime? = null,
)

data class WarehouseSecurityDutyCoverage(
    val day: LocalDate,
    val assessedThrough: LocalDateTime,
    val plannedGaps: List<SecurityDutyInterval>,
    val actualGaps: List<SecurityDutyInterval>,
    val plannedCoveredSeconds: Long,
    val actualCoveredSeconds: Long,
    val actualDayComplete: Boolean,
)

/** 第29条的全天覆盖核对。只判断时间覆盖，不据此断言实体值守或交接已经履行。 */
object WarehouseSecurityDutyCoveragePolicy {
    fun assess(day: LocalDate, records: List<WarehouseSecurityDutyCoverageSource>, now: LocalDateTime): WarehouseSecurityDutyCoverage {
        val start = day.atStartOfDay()
        val end = day.plusDays(1).atStartOfDay()
        // 今日仅核对已经过去的时段；将来日期仍可检查排班，但没有已发生的值守。
        val through = now.coerceIn(start, end)
        records.forEach { record ->
            requireInterval(record.planned)
            if (record.actualStart == null && record.actualEnd != null) fail("未登记开始值守，不能登记结束值守")
            record.actualStart?.let { actualStart ->
                if (actualStart.isAfter(now)) fail("实际开始值守时间不得晚于当前时间")
                record.actualEnd?.let { actualEnd ->
                    if (!actualEnd.isAfter(actualStart) || actualEnd.isAfter(now)) fail("实际结束值守时间须晚于开始且不得晚于当前时间")
                }
            }
        }
        val plannedGaps = gaps(start, end, records.map { it.planned })
        // 开放记录只覆盖至核对时刻，不能提前证明未来时段。未签到的排班不提供实际覆盖。
        val actual = records.mapNotNull { record -> record.actualStart?.let { from ->
            SecurityDutyInterval(from, record.actualEnd ?: now).takeIf { it.to.isAfter(it.from) }
        } }
        val actualGaps = gaps(start, through, actual)
        return WarehouseSecurityDutyCoverage(
            day, through, plannedGaps, actualGaps,
            Duration.between(start, end).seconds - plannedGaps.sumOf { Duration.between(it.from, it.to).seconds },
            Duration.between(start, through).seconds - actualGaps.sumOf { Duration.between(it.from, it.to).seconds },
            // 未结束记录可供当班观察，但不能证明某个历史日已完整履行值守。
            through == end && gaps(start, end, records.mapNotNull { record ->
                record.actualEnd?.let { SecurityDutyInterval(record.actualStart!!, it) }
            }).isEmpty(),
        )
    }

    private fun requireInterval(interval: SecurityDutyInterval) {
        if (!interval.to.isAfter(interval.from)) fail("值班结束时间须晚于开始时间")
    }

    private fun gaps(start: LocalDateTime, end: LocalDateTime, intervals: List<SecurityDutyInterval>): List<SecurityDutyInterval> {
        if (start == end) return emptyList()
        val relevant = intervals.filter { it.to.isAfter(start) && it.from.isBefore(end) }.sortedBy { it.from }
        val result = mutableListOf<SecurityDutyInterval>()
        var cursor = start
        relevant.forEach { interval ->
            val from = maxOf(start, interval.from)
            val to = minOf(end, interval.to)
            if (from.isAfter(cursor)) result += SecurityDutyInterval(cursor, from)
            cursor = maxOf(cursor, to)
        }
        if (cursor.isBefore(end)) result += SecurityDutyInterval(cursor, end)
        return result
    }

    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
