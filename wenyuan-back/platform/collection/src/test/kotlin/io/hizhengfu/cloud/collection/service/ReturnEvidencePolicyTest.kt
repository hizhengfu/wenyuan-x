package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReturnEvidencePolicyTest {
    @Test
    fun `damage record requires damage photos`() {
        assertThrows(BadRequestException::class.java) {
            ReturnEvidencePolicy.requireSubmittable(
                reviewOpinion = "发现新裂纹",
                toWarehouseId = "warehouse",
                returnDate = LocalDate.now(),
                operator = "operator",
                reviewer = "reviewer",
                custodian = "custodian",
                handoverUser = "handover",
                damageNote = "口沿新裂纹",
                damagePhotoIds = null,
                signedVoucherAttachmentIds = "signed",
            )
        }
    }
}
