package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionChangePolicy
import io.hizhengfu.cloud.collection.domain.CollectionIdentityPolicy
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionCorrectionContractTest {
    @Test
    fun `date range and year remain independent in proposed correction`() {
        val original = CollectionCatalogContentVo(acquireDate = LocalDate.of(2026, 1, 2), acquireDateRange = "1950-1960", acquireYear = "1955")
        val proposed = CollectionChangePolicy.correct(original, "acquireDate", "1956-02-03")
        assertEquals(LocalDate.of(1956, 2, 3), proposed.acquireDate)
        assertEquals(original.acquireDateRange, proposed.acquireDateRange)
        assertEquals(original.acquireYear, proposed.acquireYear)
        assertEquals(LocalDate.of(2026, 1, 2), original.acquireDate)
        assertEquals("1957", CollectionChangePolicy.correct(original, "acquireYear", "1957").acquireYear)
    }

    @Test
    fun `registration number cannot change through correction or ordinary update`() {
        assertThrows(BadRequestException::class.java) { CollectionChangePolicy.correct(CollectionCatalogContentVo(), "totalRegNo", "000002") }
        assertThrows(BadRequestException::class.java) { CollectionIdentityPolicy.requireUnchanged("000001", "000002") }
        assertDoesNotThrow { CollectionIdentityPolicy.requireUnchanged("000001", null) }
    }
}
