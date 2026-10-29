package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class CollectionWorkingCalendarTest {
    @Test fun `national holidays and make up weekends are counted from verified notice`() {
        val cal=CollectionWorkingCalendar.national2026
        assertFalse(cal.isWorking(LocalDate.of(2026,10,1)))
        assertTrue(cal.isWorking(LocalDate.of(2026,10,10)))
        assertTrue(cal.isWorking(LocalDate.of(2026,9,20)))
        assertEquals(LocalDate.of(2026,10,10), CollectionWorkingCalendar.addWorkingDays(LocalDate.of(2026,9,30),3))
        assertEquals(3, CollectionWorkingCalendar.completedDays(LocalDate.of(2026,10,1),LocalDate.of(2026,10,10),LocalDate.of(2026,10,11)))
    }
    @Test fun `unfinished notice and unknown calendar cannot satisfy statutory deadlines`() {
        assertThrows(BadRequestException::class.java) {
            CollectionWorkingCalendar.completedDays(LocalDate.of(2026,9,1),LocalDate.of(2026,10,1),LocalDate.of(2026,10,1))
        }
        assertThrows(BadRequestException::class.java) { CollectionWorkingCalendar.addWorkingDays(LocalDate.of(2026,12,31),1) }
    }
}
