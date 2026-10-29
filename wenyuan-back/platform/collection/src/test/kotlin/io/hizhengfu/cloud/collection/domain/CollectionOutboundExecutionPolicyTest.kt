package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionOutboundExecutionPolicyTest {
    @Test
    fun `complete handover evidence is accepted`() {
        CollectionOutboundExecutionPolicy.requireEvidence(
            CollectionOutboundExecutionVo(
                conditionNote = "器身完整，口沿无新增裂纹",
                conditionPhotoIds = "front,back,detail",
                signedVoucherAttachmentIds = "signed-voucher",
            ),
        )
    }

    @Test
    fun `signed voucher is mandatory`() {
        assertThrows(BadRequestException::class.java) {
            CollectionOutboundExecutionPolicy.requireEvidence(
                CollectionOutboundExecutionVo(conditionNote = "完好", conditionPhotoIds = "front,back"),
            )
        }
    }
}
