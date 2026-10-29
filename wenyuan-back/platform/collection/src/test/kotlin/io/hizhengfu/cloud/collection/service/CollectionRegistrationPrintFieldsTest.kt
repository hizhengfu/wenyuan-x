package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CollectionRegistrationPrintFieldsTest {
    @Test fun `all business supplements use explicit ledger counts actual counts and standardized measurements`() {
        val coll=CollectionVo(totalRegNo="REG-1",auxNo="OLD-1",classNo="C-1",name="一套两件",quantity=1,unit="套",actualQuantity=2,actualQuantityUnit="件",dimensions=DimensionVo(height="125"),dimensionUnit="mm",weight="350.5",weightUnit="g",entryDate=LocalDate.of(2026,7,1),entryVoucherNo="RJ-1",registrationDetails=CollectionRegistrationDetailsVo(weightExcludesAccessories=true))
        for(type in EnumCollectionPrintType.entries) {
            val form=CollectionRegistrationPrintFields.supplement(CollectionPrintVo(type,"表单",documentNo="D1",orgName="本馆",collections=listOf(coll)))
            fun value(label:String)=form.fields.single { it.label.endsWith("）$label") }.value
            assertEquals("1 套",value("登记件套数"));assertEquals("2 件",value("实际数量"))
            assertEquals("通高：12.5 cm",value("尺寸"));assertEquals("0.351 kg（不含附件）",value("质量"))
            assertEquals("RJ-1",value("入馆凭证号"));assertEquals("2026-07-01",value("实收入馆日期"))
        }
    }
    @Test fun `legacy unknown quantities and measurements are labeled rather than assigned defaults`() {
        val fields=CollectionRegistrationPrintFields.collectionFields(CollectionVo(quantity=1,unit="套",dimensions=DimensionVo(height="12"),weight="300"))
        assertEquals("待核实",fields.single {it.label=="实际数量"}.value)
        assertTrue(fields.single {it.label=="尺寸"}.value!!.contains("单位待核实"))
        assertTrue(fields.single {it.label=="质量"}.value!!.contains("单位待核实"))
    }
}
