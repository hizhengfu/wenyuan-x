package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionTransferVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionTransferRecordValuesTest {

    @Test
    fun `save values keep approval document and attachments`() {
        val values = CollectionTransferRecordValues.fromSave(
            CollectionTransferVo(
                toOrgId = "org-target",
                reason = "馆际调拨用于专题展陈",
                approvalDocNo = "调拨批〔2026〕8号",
                attachmentIds = "approval-1,handover-2",
            ),
        )

        assertEquals("org-target", values.toOrgId)
        assertEquals("馆际调拨用于专题展陈", values.reason)
        assertEquals("调拨批〔2026〕8号", values.approvalDocNo)
        assertEquals("approval-1,handover-2", values.attachmentIds)
    }

    @Test
    fun `movement values keep approval document reason and attachments`() {
        val values = CollectionTransferMovementValues.fromRecord(
            approvalDocNo = "调拨批〔2026〕8号",
            reason = "馆际调拨用于专题展陈",
            attachmentIds = "approval-1,handover-2",
            operator = "operator-1",
            refBizId = "transfer-1",
        )

        assertEquals("调拨批〔2026〕8号", values.documentNo)
        assertEquals("馆际调拨用于专题展陈", values.basis)
        assertEquals("approval-1,handover-2", values.attachmentIds)
        assertEquals("operator-1", values.operator)
        assertEquals("transfer-1", values.refBizId)
    }
}
