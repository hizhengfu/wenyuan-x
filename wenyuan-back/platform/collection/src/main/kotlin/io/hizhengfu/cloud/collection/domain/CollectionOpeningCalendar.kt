package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

data class CollectionWeeklyClosureVo(
    val startOn: LocalDate,
    val endOn: LocalDate,
    val weekdays: Set<Int>,
    val remark: String? = null,
)
data class CollectionOpeningExceptionVo(val date: LocalDate, val open: Boolean, val reason: String)

/** Museum opening arrangements are separate from statutory working-day deadlines. */
data class CollectionOpeningCalendarVo(
    val year: Int,
    val revision: Int = 0,
    val weeklyClosures: List<CollectionWeeklyClosureVo> = emptyList(),
    val exceptions: List<CollectionOpeningExceptionVo> = emptyList(),
    val remark: String? = null,
) {
    fun validate() {
        if (year !in 1900..2200 || revision < 0) throw BadRequestException("开放日历年度或版本不正确")
        if (weeklyClosures.size > 100 || exceptions.size > 366) throw BadRequestException("日历规则数量过多")
        weeklyClosures.forEach {
            if (it.startOn.year != year || it.endOn.year != year || it.endOn < it.startOn) throw BadRequestException("每周规则的日期范围必须在本年度内且先后正确")
            if (it.weekdays.isEmpty() || it.weekdays.any { day -> day !in 1..7 }) throw BadRequestException("请选择有效的休馆星期")
            if ((it.remark?.length ?: 0) > 1000) throw BadRequestException("规则说明过长")
        }
        if (exceptions.map { it.date }.distinct().size != exceptions.size) throw BadRequestException("同一天不能登记多个开放例外")
        exceptions.forEach {
            if (it.date.year != year) throw BadRequestException("例外日期必须在本年度内")
            if (it.reason.isBlank() || it.reason.length > 1000) throw BadRequestException("请填写临时开放或闭馆原因（不超过1000字）")
        }
        if ((remark?.length ?: 0) > 2000) throw BadRequestException("日历说明过长")
    }
    fun isOpen(date: LocalDate): Boolean {
        if (date.year != year) throw BadRequestException("开放日历年度不匹配")
        exceptions.firstOrNull { it.date == date }?.let { return it.open }
        return weeklyClosures.none { date >= it.startOn && date <= it.endOn && date.dayOfWeek.value in it.weekdays }
    }
}
