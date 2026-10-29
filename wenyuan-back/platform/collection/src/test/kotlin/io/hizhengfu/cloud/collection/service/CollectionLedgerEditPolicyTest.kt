package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionLedgerEditPolicyTest {
    private val existing = CollectionVo(
        totalRegNo = "000123",
        name = "青花碗",
        level = EnumRelicLevel.FIRST,
        source = EnumSource.PURCHASE,
        acquireDate = LocalDate.of(2026, 8, 9),
        acquireDateRange = "2026-08-09",
        acquireYear = "2026",
    )

    @Test
    fun `ordinary edit accepts unchanged critical fields and derived acquisition values`() {
        assertDoesNotThrow {
            CollectionLedgerEditPolicy.requirePreserved(
                existing,
                existing.copy(acquireDateRange = null, acquireYear = null, remark = "补充说明"),
            )
        }
    }

    @Test
    fun `ordinary edit rejects ledger critical changes`() {
        val ex = assertThrows(BadRequestException::class.java) {
            CollectionLedgerEditPolicy.requirePreserved(existing, existing.copy(name = "青花盘"))
        }

        assertTrue(ex.message.orEmpty().contains("名称"))
        assertTrue(ex.message.orEmpty().contains("信息变更申请"))
    }

    @Test
    fun `published collection cannot be changed through ordinary edit`() {
        val ex = assertThrows(BadRequestException::class.java) {
            CollectionLedgerEditPolicy.requirePreserved(existing.copy(published = true), existing.copy(published = true))
        }

        assertTrue(ex.message.orEmpty().contains("信息变更申请"))
    }
}
