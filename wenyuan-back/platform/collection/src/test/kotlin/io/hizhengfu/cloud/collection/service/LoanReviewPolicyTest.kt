package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LoanReviewPolicyTest {

    @Test
    fun `loan save values keep operator and reviewer`() {
        val values = LoanRecordValues.fromSave(
            LoanVo(
                title = "外展",
                collectionIds = "col-1,col-2",
                borrower = "借入单位",
                protocolNo = "LOAN-2026-01",
                approveLevel = EnumApproveLevel.PROVINCE,
                approvalDocNo = "文物出馆批〔2026〕1号",
                operator = "operator-1",
                reviewer = "reviewer-1",
            ),
        )

        assertEquals("operator-1", values.operator)
        assertEquals("reviewer-1", values.reviewer)
        assertEquals(EnumApproveLevel.PROVINCE, values.approveLevel)
        assertEquals("文物出馆批〔2026〕1号", values.approvalDocNo)
    }

    @Test
    fun `first level loan requires operator and distinct reviewer`() {
        assertThrows(BadRequestException::class.java) {
            LoanReviewPolicy.requireForLevels(listOf(EnumRelicLevel.FIRST), null, "reviewer-1")
        }
        assertThrows(BadRequestException::class.java) {
            LoanReviewPolicy.requireForLevels(listOf(EnumRelicLevel.FIRST), "operator-1", null)
        }
        assertThrows(BadRequestException::class.java) {
            LoanReviewPolicy.requireForLevels(listOf(EnumRelicLevel.FIRST), "same-user", "same-user")
        }

        LoanReviewPolicy.requireForLevels(listOf(EnumRelicLevel.FIRST), "operator-1", "reviewer-1")
        LoanReviewPolicy.requireForLevels(listOf(EnumRelicLevel.SECOND), null, null)
    }

}
