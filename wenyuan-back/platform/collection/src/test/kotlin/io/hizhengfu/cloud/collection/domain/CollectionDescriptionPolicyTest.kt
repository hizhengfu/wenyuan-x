package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollectionDescriptionPolicyTest {
    @Test fun `all standard material branches accept concrete and compound material descriptions`() {
        for ((category, details) in CollectionDescriptionPolicy.materialCategories) for (detail in details) {
            assertDoesNotThrow { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo(textureCategory1=category, textureCategory2=detail, texture="木、漆、金")) }
        }
        assertEquals(8, CollectionDescriptionPolicy.materialCategories.values.sumOf { it.size })
        assertTrue(CollectionDescriptionPolicy.materials.containsAll(listOf("玻璃","皮革","竹")))
    }
    @Test fun `draft allows omitted classification but rejects impossible branch and ambiguous actual units`() {
        assertDoesNotThrow { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo()) }
        assertThrows(BadRequestException::class.java) { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo(textureCategory1="单一质地",textureCategory2="有机无机复合或组合质地")) }
        assertThrows(BadRequestException::class.java) { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo(actualQuantity=2,actualQuantityUnit="套")) }
        assertThrows(BadRequestException::class.java) { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo(actualQuantity=2)) }
        assertThrows(BadRequestException::class.java) { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo(actualQuantityUnit="只")) }
        assertDoesNotThrow { CollectionDescriptionPolicy.requireValid(CollectionCatalogContentVo(actualQuantity=2,actualQuantityUnit="只")) }
    }
    @Test fun `catalog submission requires one canonical era and complete material hierarchy`() {
        val missing=CollectionCatalogPolicy.completeness(CollectionCatalogContentVo(era="清",texture="竹")).missingFields
        assertTrue(missing.containsAll(listOf("年代类型","年代表示方式","质地类别","质地细类")))
    }
}
