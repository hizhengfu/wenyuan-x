package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionReturnRecordValuesTest {

    @Test
    fun `requisition return draft keeps source and return handover fields`() {
        val expected = LocalDate.of(2026, 7, 20)
        val values = CollectionReturnRecordValues.fromRequisition(
            RequisitionVo(
                id = "req-1",
                orgId = "org-1",
                documentNo = "TY-001",
                collectionIds = "c1,c2",
                purpose = EnumMoveReason.EXHIBIT,
                basis = "展览任务 EX-1",
                department = "陈列部",
                operator = "op-1",
                reviewer = "rv-1",
                custodian = "cu-1",
                handoverUser = "hd-1",
                reviewOpinion = "出库核对无误",
                attachmentIds = "file-1",
                toWarehouseId = "wh-1",
                expectReturnDate = expected,
            ),
        )

        assertEquals(CollectionConst.BIZ_REQUISITION, values.sourceBizType)
        assertEquals("req-1", values.sourceBizId)
        assertEquals("TY-001", values.sourceDocumentNo)
        assertEquals("org-1", values.orgId)
        assertEquals("c1,c2", values.collectionIds)
        assertEquals(EnumMoveReason.EXHIBIT, values.reason)
        assertEquals("展览任务 EX-1", values.basis)
        assertEquals("陈列部", values.department)
        assertEquals("op-1", values.operator)
        assertEquals("rv-1", values.reviewer)
        assertEquals("cu-1", values.custodian)
        assertEquals("hd-1", values.handoverUser)
        assertEquals("出库核对无误", values.reviewOpinion)
        assertEquals("file-1", values.attachmentIds)
        assertEquals("wh-1", values.toWarehouseId)
        assertEquals(expected, values.expectedReturnDate)
    }

    @Test
    fun `loan return draft uses loan approval material as return basis`() {
        val expected = LocalDate.of(2026, 9, 30)
        val values = CollectionReturnRecordValues.fromLoan(
            LoanVo(
                id = "loan-1",
                orgId = "org-2",
                title = "外展",
                collectionIds = "c9",
                borrower = "省博",
                protocolNo = "HT-01",
                approveLevel = EnumApproveLevel.PROVINCE,
                approvalDocNo = "批文-01",
                attachmentIds = "loan-file",
                operator = "op-2",
                reviewer = "rv-2",
                returnDate = expected,
            ),
        )

        assertEquals(CollectionConst.BIZ_LOAN, values.sourceBizType)
        assertEquals("loan-1", values.sourceBizId)
        assertEquals("HT-01", values.sourceDocumentNo)
        assertEquals("org-2", values.orgId)
        assertEquals("c9", values.collectionIds)
        assertEquals(EnumMoveReason.LOAN, values.reason)
        assertEquals("批文-01", values.basis)
        assertEquals("省博", values.department)
        assertEquals("op-2", values.operator)
        assertEquals("rv-2", values.reviewer)
        assertEquals("loan-file", values.attachmentIds)
        assertEquals(expected, values.expectedReturnDate)
    }
}
