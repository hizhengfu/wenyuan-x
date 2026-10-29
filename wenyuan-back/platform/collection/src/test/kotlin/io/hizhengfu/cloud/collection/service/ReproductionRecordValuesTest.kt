package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.ReproductionVo
import io.hizhengfu.cloud.collection.domain.EnumRepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReproductionRecordValuesTest {

    @Test
    fun `create values keep reproduction type image and date`() {
        val repDate = LocalDate.of(2026, 6, 24)
        val values = ReproductionRecordValues.fromCreate(
            ReproductionVo(
                repType = EnumRepType.SCAN_3D,
                images = "scan-1,scan-2",
                purpose = "展陈数字化",
                repDate = repDate,
            ),
        )

        assertEquals(EnumRepType.SCAN_3D, values.repType)
        assertEquals("scan-1,scan-2", values.images)
        assertEquals("展陈数字化", values.purpose)
        assertEquals(repDate, values.repDate)
    }
}
