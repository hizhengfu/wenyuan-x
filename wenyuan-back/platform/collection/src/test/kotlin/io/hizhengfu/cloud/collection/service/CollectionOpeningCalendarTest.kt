package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionOpeningCalendarTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private fun calendar() = CollectionOpeningCalendarVo(2026, weeklyClosures = listOf(CollectionWeeklyClosureVo(LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), setOf(1))))
    @Test fun `weekly closure has a bounded range and all other days are open`() {
        val value = calendar(); value.validate()
        assertFalse(value.isOpen(monday)); assertTrue(value.isOpen(monday.plusDays(1)))
        val bounded = value.copy(weeklyClosures = listOf(CollectionWeeklyClosureVo(monday.plusDays(7),monday.plusDays(14),setOf(1))))
        bounded.validate(); assertTrue(bounded.isOpen(monday)); assertFalse(bounded.isOpen(monday.plusDays(7)))
    }
    @Test fun `temporary openings and closures override weekly rules`() {
        val value=calendar().copy(exceptions=listOf(CollectionOpeningExceptionVo(monday,true,"节日期间开放"),CollectionOpeningExceptionVo(monday.plusDays(1),false,"设备检修")))
        value.validate(); assertTrue(value.isOpen(monday)); assertFalse(value.isOpen(monday.plusDays(1)))
        assertFalse(value.copy(exceptions=emptyList()).isOpen(monday))
    }
    @Test fun `invalid ranges weekdays duplicate dates and reasons are rejected`() {
        assertThrows(BadRequestException::class.java) { calendar().copy(weeklyClosures=listOf(CollectionWeeklyClosureVo(monday,monday.minusDays(1),setOf(1)))).validate() }
        assertThrows(BadRequestException::class.java) { calendar().copy(weeklyClosures=listOf(CollectionWeeklyClosureVo(monday,monday,setOf(0)))).validate() }
        assertThrows(BadRequestException::class.java) { calendar().copy(exceptions=listOf(CollectionOpeningExceptionVo(monday,true,""))).validate() }
        assertThrows(BadRequestException::class.java) { calendar().copy(exceptions=listOf(CollectionOpeningExceptionVo(monday,true,"开放"),CollectionOpeningExceptionVo(monday,false,"关闭"))).validate() }
        assertThrows(BadRequestException::class.java) { calendar().copy(exceptions=listOf(CollectionOpeningExceptionVo(LocalDate.of(2027,1,1),true,"开放"))).validate() }
    }
    @Test fun `opening calendar cannot change statutory workday classification`() {
        val work = CollectionWorkingCalendar.national(2026)
        val value = calendar()
        val ordinaryMonday = LocalDate.of(2026,6,1)
        assertFalse(value.isOpen(ordinaryMonday)); assertTrue(work.isWorking(ordinaryMonday))
        assertThrows(BadRequestException::class.java) { value.isOpen(LocalDate.of(2027,1,1)) }
    }
}
