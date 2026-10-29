package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionCatalogAuditVo
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionCatalogAuditTest {

    @Test
    fun `approve catalog marks collection in store`() {
        val decision = CollectionCatalogAuditDecision.from(
            CollectionCatalogAuditVo(
                status = EnumCatalogStatus.APPROVED,
                opinion = "编目信息完整，同意入库",
            ),
        )

        assertEquals(EnumCatalogStatus.APPROVED, decision.status)
        assertEquals(EnumCollectionStatus.IN_STORE, decision.targetLifeStatus)
        assertEquals("编目信息完整，同意入库", decision.opinion)
    }

    @Test
    fun `reject catalog decision requires opinion`() {
        val decision = CollectionCatalogAuditDecision.from(
            CollectionCatalogAuditVo(
                status = EnumCatalogStatus.REJECTED,
                opinion = "缺少尺寸和来源说明",
            ),
        )

        assertEquals(EnumCatalogStatus.REJECTED, decision.status)
        assertEquals(EnumCollectionStatus.PENDING, decision.targetLifeStatus)
        assertEquals("缺少尺寸和来源说明", decision.opinion)
    }

    @Test
    fun `reject catalog without opinion is invalid`() {
        assertThrows(BadRequestException::class.java) {
            CollectionCatalogAuditDecision.from(CollectionCatalogAuditVo(status = EnumCatalogStatus.REJECTED))
        }
    }

    @Test
    fun `pending is not a review decision`() {
        assertThrows(BadRequestException::class.java) {
            CollectionCatalogAuditDecision.from(CollectionCatalogAuditVo(status = EnumCatalogStatus.PENDING))
        }
    }
}
