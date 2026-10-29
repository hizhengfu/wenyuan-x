package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionCatalogPolicyTest {
    private fun completeContent() = CollectionCatalogContentVo(
        nature = EnumCollectionNature.RELIC,
        totalName = "青花云龙纹罐",
        formalName = "青花云龙纹罐",
        categoryId = "ceramic",
        era = "明",
        texture = "瓷", textureCategory1="单一质地", textureCategory2="无机质/非金属材料",
        level = EnumRelicLevel.SECOND,
        quantity = 1, unit = "套", classificationNo = "C-1",
        entryDate = LocalDate.of(1980,1,2), warehouseId = "store",
        dimensions = DimensionVo(height="125"), dimensionUnit = "mm",
        weight = "350.5", weightUnit = "g", completeStatus = "完好", feature = "青花纹饰",
        preserveState = EnumPreserve.entries.first(), coverImage = "front",
        registrationDetails = CollectionRegistrationDetailsVo(coverIsFrontView=true, weightExcludesAccessories=true, eraMeaning=EnumCollectionEraMeaning.MANUFACTURED, eraNotation=EnumCollectionEraNotation.CHINESE_HISTORY),
        actualQuantity = 1,
        actualQuantityUnit = "件",
        source = EnumSource.OLD,
        acquireDate = LocalDate.of(1980, 1, 1),
        completeness = EnumCompleteness.COMPLETE,
    )

    @Test
    fun `complete catalog can be submitted from draft or rejected`() {
        val content = completeContent()
        assertTrue(CollectionCatalogPolicy.completeness(content).complete)
        assertTrue(CollectionCatalogPolicy.completeness(content).missingFields.isEmpty())
        assertDoesNotThrow { CollectionCatalogPolicy.requireSubmittable(EnumCatalogStatus.DRAFT, content) }
        assertDoesNotThrow { CollectionCatalogPolicy.requireSubmittable(EnumCatalogStatus.REJECTED, content) }
    }

    @Test
    fun `submit reports every missing required field`() {
        val completeness = CollectionCatalogPolicy.completeness(CollectionCatalogContentVo())
        assertTrue(completeness.missingFields.containsAll(listOf("正式定名", "文物分类", "年代")))
        assertTrue("尺寸或无法测量原因" in completeness.missingFields)
        assertTrue("尺寸单位" !in completeness.missingFields)
        assertTrue("基本特征" in completeness.missingFields)
        assertEquals(0, completeness.percent)
        assertThrows(BadRequestException::class.java) {
            CollectionCatalogPolicy.requireSubmittable(EnumCatalogStatus.DRAFT, CollectionCatalogContentVo())
        }
    }

    @Test
    fun `other collections do not require relic descriptors or a relic grade`() {
        val content = completeContent().copy(nature = EnumCollectionNature.OTHER, level = null,
            textureCategory1 = null, textureCategory2 = null, unit = "件", actualQuantity = null, actualQuantityUnit = null,
            registrationDetails = CollectionRegistrationDetailsVo(coverIsFrontView = true))
        assertTrue(CollectionCatalogPolicy.completeness(content).complete)
        assertTrue(CollectionCatalogPolicy.completeness(content.copy(level = EnumRelicLevel.UNRATED)).complete)
        assertThrows(BadRequestException::class.java) { CollectionCatalogPolicy.requireComplete(content.copy(level = EnumRelicLevel.FIRST)) }
        assertTrue(CollectionCatalogPolicy.completeness(content.copy(era = null)).missingFields.contains("年代"))
    }

    @Test
    fun `relics and article 61 fossils retain standard requirements and nature must be confirmed`() {
        for (nature in listOf(EnumCollectionNature.RELIC, EnumCollectionNature.SCIENTIFIC_FOSSIL)) {
            val content = completeContent().copy(nature = nature, registrationDetails = null, textureCategory1 = null, textureCategory2 = null)
            assertTrue(CollectionCatalogPolicy.completeness(content).missingFields.containsAll(listOf("年代类型", "年代表示方式", "质地类别", "质地细类")))
        }
        assertTrue("藏品性质" in CollectionCatalogPolicy.completeness(completeContent().copy(nature = null)).missingFields)
    }

    @Test
    fun `individual items do not require duplicate counts but sets and multiple parts do`() {
        val individual = completeContent().copy(unit = "件", actualQuantity = null, actualQuantityUnit = null)
        assertTrue(CollectionCatalogPolicy.completeness(individual).complete)
        assertTrue("实际数量" in CollectionCatalogPolicy.completeness(individual.copy(unit = "套")).missingFields)
        val parts = individual.copy(registrationDetails = individual.registrationDetails!!.copy(parts = listOf(
            CatalogPartMeasurementVo(partNo = "1", name = "上部"), CatalogPartMeasurementVo(partNo = "2", name = "下部"))))
        assertTrue("实际数量" in CollectionCatalogPolicy.completeness(parts).missingFields)
        assertThrows(BadRequestException::class.java) { CollectionCatalogPolicy.requireComplete(individual.copy(actualQuantity = 2)) }
    }

    @Test
    fun `verified historical catalog uses original count and does not invent new fields`() {
        val historical = completeContent().copy(registrationDetails = null, actualQuantity = null, actualQuantityUnit = null,
            textureCategory1 = null, textureCategory2 = null, dimensions = null, weight = null, entryDate = null, coverImage = null)
        assertTrue(CollectionCatalogPolicy.completeness(historical, historical = true).complete)
        assertTrue(CollectionCatalogPolicy.completeness(historical.copy(era = null, texture = null, completeness = null), historical = true).complete)
        assertThrows(BadRequestException::class.java) { CollectionCatalogPolicy.requireComplete(historical) }
        assertThrows(BadRequestException::class.java) { CollectionCatalogPolicy.requireComplete(historical.copy(nature = EnumCollectionNature.OTHER), historical = true) }
    }

    @Test
    fun `only submitted state is reviewable and rejection needs opinion`() {
        assertDoesNotThrow { CollectionCatalogPolicy.requireReviewable(EnumCatalogStatus.SUBMITTED) }
        assertDoesNotThrow { CollectionCatalogPolicy.requireReviewable(EnumCatalogStatus.PENDING) }
        assertThrows(BadRequestException::class.java) { CollectionCatalogPolicy.requireReviewable(EnumCatalogStatus.DRAFT) }
        assertThrows(BadRequestException::class.java) {
            CollectionCatalogPolicy.requireDecision(EnumCatalogStatus.REJECTED, " ")
        }
        assertEquals("缺少尺寸", CollectionCatalogPolicy.requireDecision(EnumCatalogStatus.REJECTED, " 缺少尺寸 "))
    }
}
