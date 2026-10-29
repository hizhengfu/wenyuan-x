package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionSurveyExportBuilderTest {

    @Test
    fun `survey export row includes fourteen basic indicators and image evidence`() {
        val row = CollectionSurveyExportBuilder.build(
            collection = CollectionVo(
                totalRegNo = "000001",
                classNo = "瓷-001",
                name = "青花瓷碗",
                categoryName = "瓷器",
                era = "明",
                texture = "瓷",
                level = EnumRelicLevel.SECOND,
                source = EnumSource.DONATE,
                sourceDesc = "王某捐赠",
                acquireDate = LocalDate.of(2026, 6, 24),
                completeness = EnumCompleteness.COMPLETE,
                completeStatus = "完整",
                dimensions = DimensionVo(height = "12cm", mouth = "18cm", bottom = "8cm"),
                weight = "350g",
                coverImage = "cover-file-1",
            ),
            orgName = "省博物馆",
        )

        assertEquals("青花瓷碗", row.name)
        assertEquals("明", row.era)
        assertEquals("瓷器", row.categoryName)
        assertEquals("瓷", row.texture)
        assertEquals("通高：12cm；口径：18cm；底径：8cm", row.dimensions)
        assertEquals("350g", row.weight)
        assertEquals("COMPLETE", row.completeness)
        assertEquals("完整", row.completeStatus)
        assertEquals("DONATE", row.source)
        assertEquals("2026-06-24", row.acquireDate)
        assertEquals("SECOND", row.level)
        assertEquals("王某捐赠", row.sourceDesc)
        assertEquals("省博物馆", row.orgName)
        assertEquals("cover-file-1", row.image)
    }
}
