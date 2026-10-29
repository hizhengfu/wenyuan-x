package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionAcquireRangePolicyTest {

    @Test
    fun `custom range is preserved and trimmed`() {
        assertEquals(
            "1949.10.1-1965",
            CollectionAcquireRangePolicy.resolve(" 1949.10.1-1965 ", LocalDate.of(2026, 8, 9)),
        )
    }

    @Test
    fun `exact date supplies range when range text is empty`() {
        assertEquals(
            "2026-08-09",
            CollectionAcquireRangePolicy.resolve(null, LocalDate.of(2026, 8, 9)),
        )
    }

    @Test
    fun `range or exact date is required`() {
        assertThrows(BadRequestException::class.java) {
            CollectionAcquireRangePolicy.resolve(" ", null)
        }
    }
}
