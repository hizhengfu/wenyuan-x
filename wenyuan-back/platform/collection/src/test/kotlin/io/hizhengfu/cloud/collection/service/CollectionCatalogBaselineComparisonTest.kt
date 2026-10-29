package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionCatalogBaselineComparisonTest {
    private val current=CollectionCatalogContentVo(formalName="一套两件",quantity=1,unit="套",dimensions=DimensionVo(height="12"))
    private val old=current.copy(actualQuantity=1,actualQuantityUnit="套",dimensionUnit="cm")
    private fun source(c:CollectionCatalogContentVo, purpose:String="研究")=JsonUtils.toJson(mapOf("collectionId" to "1", "catalog" to c, "purpose" to purpose))!!
    @Test fun `old implicit defaults can support an unchanged current business without rewriting either side`() {
        val before=source(old);val now=source(current)
        assertTrue(CollectionCatalogBaselineComparison.sameCatalog(old,current))
        assertTrue(CollectionCatalogBaselineComparison.sameSource(before,now))
        assertEquals(before,source(old));assertNull(current.actualQuantity)
    }
    @Test fun `real changed dates counts units descriptions and other business fields invalidate the basis`() {
        for(changed in listOf(current.copy(actualQuantity=2,actualQuantityUnit="件"),current.copy(dimensionUnit="mm"),
            current.copy(entryDate=LocalDate.of(2026,1,1)), current.copy(formalName="改名"),
            current.copy(registrationDetails=CollectionRegistrationDetailsVo(coverIsFrontView=true))))
            assertFalse(CollectionCatalogBaselineComparison.sameSource(source(old),source(changed)))
        assertFalse(CollectionCatalogBaselineComparison.sameSource(source(old),source(current,"展览")))
        assertFalse(CollectionCatalogBaselineComparison.sameSource(source(old.copy(actualQuantity=9)),source(current)))
    }
    @Test fun `unknown or deleted fields cannot disappear through deserialization`() {
        val withExtra=source(old).replace("\"catalog\":{","\"catalog\":{\"unknownFact\":true,")
        assertFalse(CollectionCatalogBaselineComparison.sameSource(withExtra,source(current)))
        assertFalse(CollectionCatalogBaselineComparison.sameSource("{}",source(current)))
    }
}
