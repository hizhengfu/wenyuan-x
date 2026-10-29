package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionUnifiedSearchPolicyTest {

    @Test
    fun `keyword normalizes whitespace and neutralizes sql wildcard input`() {
        assertEquals("青铜 鼎", CollectionUnifiedSearchPolicy.normalizeKeyword("  青铜  %_ 鼎  "))
        assertEquals(listOf("青铜", "鼎"), CollectionUnifiedSearchPolicy.terms("青铜 鼎 青铜"))
    }

    @Test
    fun `blank and overlong keywords are rejected`() {
        assertThrows(BadRequestException::class.java) {
            CollectionUnifiedSearchPolicy.normalizeKeyword(" %_ ")
        }
        assertThrows(BadRequestException::class.java) {
            CollectionUnifiedSearchPolicy.normalizeKeyword("藏".repeat(101))
        }
    }

    @Test
    fun `annual report terms identify a year without treating unrelated numbers as years`() {
        assertEquals(2025, CollectionUnifiedSearchPolicy.annualReportYear(listOf("2025")))
        assertEquals(2025, CollectionUnifiedSearchPolicy.annualReportYear(listOf("2025年报")))
        assertEquals(2025, CollectionUnifiedSearchPolicy.annualReportYear(listOf("2025", "年度报表")))
        assertEquals(0, CollectionUnifiedSearchPolicy.annualReportYear(listOf("备案")))
        assertEquals(null, CollectionUnifiedSearchPolicy.annualReportYear(listOf("REG-2025")))
        assertEquals(null, CollectionUnifiedSearchPolicy.annualReportYear(listOf("2024", "2025")))
    }
}
