package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CollectionCalendarYearVo(
    val year: Int,
    val holidays: Set<LocalDate> = emptySet(),
    val extraWorkdays: Set<LocalDate> = emptySet(),
    val sourceTitle: String,
    val sourceUrl: String,
    val evidenceFileIds: String? = null,
) {
    fun validate() {
        if (year !in 1900..2200) throw BadRequestException("日历年度不正确")
        if (sourceTitle.isBlank() || sourceUrl.isBlank()) throw BadRequestException("工作日历必须填写正式通知名称和来源")
        if ((holidays + extraWorkdays).any { it.year != year }) throw BadRequestException("休息日和调休工作日必须属于本年度")
        if (holidays.intersect(extraWorkdays).isNotEmpty()) throw BadRequestException("同一天不能同时为休息日和工作日")
    }
    fun isWorking(date: LocalDate): Boolean {
        if (date.year != year) throw BadRequestException("工作日历年度不匹配")
        return date in extraWorkdays || (date !in holidays && date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
    }
}

/** 完整通知已核验的全国工作日历；未知年度必须补录正式通知，不按普通周一至周五猜算。 */
object CollectionWorkingCalendar {
    val national2026 = CollectionCalendarYearVo(
        year=2026,
        holidays=listOf("01-01" to "01-03", "02-15" to "02-23", "04-04" to "04-06", "05-01" to "05-05",
            "06-19" to "06-21", "09-25" to "09-27", "10-01" to "10-07").flatMap { (start, end) ->
            LocalDate.parse("2026-$start").datesUntil(LocalDate.parse("2026-$end").plusDays(1)).toList()
        }.toSet(),
        extraWorkdays=setOf("01-04", "02-14", "02-28", "05-09", "09-20", "10-10").map { LocalDate.parse("2026-$it") }.toSet(),
        sourceTitle="国务院办公厅关于2026年部分节假日安排的通知（国办发明电〔2025〕7号）",
        sourceUrl="https://www.beijing.gov.cn/cs/gncs/zcwj/202603/t20260327_4568275.html",
    )

    fun national(year: Int): CollectionCalendarYearVo = if (year == 2026) national2026
        else throw BadRequestException("尚未配置 $year 年正式工作日历，请先补录休假、调休及通知依据")

    /** 计算已完整经过的公示工作日：[开始日期, 结束日期]，结束日期须早于今天。 */
    fun completedDays(start: LocalDate, end: LocalDate, today: LocalDate = LocalDate.now(), calendar: (Int) -> CollectionCalendarYearVo = ::national): Int {
        requireRange(start, end)
        if (!end.isBefore(today)) throw BadRequestException("公示结束日尚未完整经过，不能提前认定公示完成")
        return start.datesUntil(end.plusDays(1)).filter { calendar(it.year).isWorking(it) }.count().toInt()
    }

    /** 事项发生日不计入期限，从次日起累计 n 个工作日。 */
    fun addWorkingDays(start: LocalDate, days: Int, calendar: (Int) -> CollectionCalendarYearVo = ::national): LocalDate {
        if (days !in 1..1000) throw BadRequestException("工作日期限应为 1 至 1000 天")
        var remaining=days; var date=start
        while (remaining > 0) {
            date=date.plusDays(1)
            if (calendar(date.year).isWorking(date)) remaining--
        }
        return date
    }
    fun requireRange(start: LocalDate, end: LocalDate) {
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 3660) throw BadRequestException("日期范围不正确")
    }
}
