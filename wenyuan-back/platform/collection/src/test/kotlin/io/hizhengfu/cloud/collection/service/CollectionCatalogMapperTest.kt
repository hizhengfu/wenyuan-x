package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CatalogAccessoryVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionCatalogMapperTest {
    @Test
    fun `ledger creates a catalog draft with identity and descriptive fields`() {
        val content = CollectionCatalogMapper.fromCollection(
            CollectionVo(
                classNo = "C-001",
                auxNo = "OLD-9",
                name = "铜镜",
                era = "汉",
                texture = "铜",
                level = EnumRelicLevel.THIRD,
                quantity = 1,
                actualQuantity = 2,
                actualQuantityUnit = "件",
                dimensionUnit = "mm",
                unit = "件",
                dimensions = DimensionVo(height = "12.5", width = "8"),
            ),
        )

        assertEquals("C-001", content.classificationNo)
        assertEquals("OLD-9", content.originalNo)
        assertEquals("铜镜", content.formalName)
        assertEquals(2, content.actualQuantity)
        assertEquals(1, content.quantity)
        assertEquals("件", content.actualQuantityUnit)
        assertEquals("12.5", content.dimensions?.height)
        assertEquals("8", content.dimensions?.width)
        assertEquals("mm", content.dimensionUnit)
    }

    @Test
    fun `legacy missing units and physical counts remain unknown`() {
        val content = CollectionCatalogMapper.fromCollection(CollectionVo(quantity=1, unit="套", dimensions=DimensionVo(height="12")))
        org.junit.jupiter.api.Assertions.assertNull(content.actualQuantity)
        org.junit.jupiter.api.Assertions.assertNull(content.dimensionUnit)
    }

    @Test
    fun `old implicit baseline defaults compare without becoming new main facts`() {
        val current=CollectionVo(quantity=1,unit="套",dimensions=DimensionVo(height="12"))
        val old=CollectionCatalogMapper.fromCollection(current).copy(actualQuantity=1,actualQuantityUnit="套",dimensionUnit="cm")
        org.junit.jupiter.api.Assertions.assertTrue(CollectionCatalogMapper.matchesSnapshot(old,current))
        org.junit.jupiter.api.Assertions.assertFalse(CollectionCatalogMapper.matchesSnapshot(old,current.copy(actualQuantity=2,actualQuantityUnit="件")))
        org.junit.jupiter.api.Assertions.assertFalse(CollectionCatalogMapper.matchesSnapshot(old.copy(formalName="被改动"),current))
        org.junit.jupiter.api.Assertions.assertNull(CollectionCatalogMapper.fromCollection(current).actualQuantity)
    }

    @Test
    fun `ledger sync keeps catalog only descriptions`() {
        val current = CollectionCatalogContentVo(
            formalName = "旧定名",
            colophon = "题跋内容",
            researchNote = "研究说明",
            accessories = listOf(CatalogAccessoryVo(kind = "附件", name = "木盒")),
        )
        val merged = CollectionCatalogMapper.mergeCollection(
            current,
            CollectionVo(
                name = "新定名",
                quantity = 2,
                unit = "套",
                dimensions = DimensionVo(length = "20"),
            ),
        )

        assertEquals("新定名", merged.formalName)
        assertEquals("题跋内容", merged.colophon)
        assertEquals("研究说明", merged.researchNote)
        assertEquals("木盒", merged.accessories.single().name)
        assertEquals("20", merged.dimensions?.length)
    }
}
