package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class CollectionDateParamTest {
    @Test
    fun `date parameter accepts iso date and frontend epoch milliseconds`() {
        val date = LocalDate.of(2026, 8, 23)
        val epoch = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli().toString()

        assertEquals(date, parseCollectionDateParam("2026-08-23"))
        assertEquals(date, parseCollectionDateParam(epoch))
    }

    @Test
    fun `invalid date parameter is ignored`() {
        assertNull(parseCollectionDateParam("not-a-date"))
        assertNull(parseCollectionDateParam(" "))
    }
}
