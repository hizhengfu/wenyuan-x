package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CollectionRegistrationTest {
    private val received = LocalDateTime.of(2026, 7, 1, 10, 0)
    private fun receipt() = AccessionReceiptVo("acq", received.plusDays(3), "store", setOf("keeper"), setOf("sender"),
        listOf(AccessionPieceVo("set-1", received, "一套两件", "完好", true, true, "photo", actualQuantity=2, actualQuantityUnit="件")), true, "signed")

    @Test fun `set quantity and physical quantity differ and receipt date is not checked date`() {
        val value=receipt()
        assertEquals(1,value.pieces.size)
        assertEquals(2 to "件",AccessionRegistrationFacts.requireActualQuantity(value))
        assertEquals(received.toLocalDate(),AccessionRegistrationFacts.entryDate(value))
        val second=value.pieces.single().copy(pieceNo="set-2",receivedAt=received.plusDays(1),actualQuantity=3)
        val batch=value.copy(pieces=value.pieces+second)
        assertEquals(5 to "件",AccessionRegistrationFacts.requireActualQuantity(batch))
        assertEquals(received.plusDays(1).toLocalDate(),AccessionRegistrationFacts.entryDate(batch))
    }
    @Test fun `unknown legacy counts and mixed units are never inferred`() {
        val value=receipt()
        for(piece in listOf(value.pieces[0].copy(actualQuantity=null),value.pieces[0].copy(actualQuantity=0),value.pieces[0].copy(actualQuantityUnit="套"))) {
            assertThrows(BadRequestException::class.java){AccessionRegistrationFacts.requireActualQuantity(value.copy(pieces=listOf(piece)))}
        }
        assertNull(AccessionRegistrationFacts.actualQuantity(value.copy(pieces=value.pieces+value.pieces[0].copy(actualQuantityUnit="册"))))
    }
    @Test fun `standard measurement output converts explicit units and respects precision`() {
        assertEquals("12.5 cm",CollectionMeasurementPolicy.lengthText("125", "mm"))
        assertEquals("1.26 m",CollectionMeasurementPolicy.lengthText("126.1", "cm"))
        assertEquals("100.0 cm",CollectionMeasurementPolicy.lengthText("1", "m"))
        assertEquals("0.351 kg",CollectionMeasurementPolicy.massText("350.5", "g"))
        assertNull(CollectionMeasurementPolicy.lengthText("12",null))
        assertNull(CollectionMeasurementPolicy.lengthText("12cm","mm"))
        assertNull(CollectionMeasurementPolicy.massText("约 350g",null))
    }
    @Test fun `unmeasurable parts require reasons and preserve them in print and exchange data`() {
        val part=CatalogPartMeasurementVo(partNo="01",name="固定构件")
        val content=CollectionCatalogContentVo(registrationDetails=CollectionRegistrationDetailsVo(parts=listOf(part)))
        assertEquals(2,CollectionMeasurementPolicy.issues(content).size)
        val explained=content.copy(registrationDetails=content.registrationDetails!!.copy(parts=listOf(part.copy(dimensionUnknownReason="固定在原构架中不能拆卸测量",weightUnknownReason="结构承重不能拆卸称量"))))
        assertTrue(CollectionMeasurementPolicy.issues(explained).isEmpty())
        val fields=io.hizhengfu.cloud.collection.service.CollectionRegistrationPrintFields.fields(explained)
        assertTrue(fields.single {it.label.startsWith("分件")}.value!!.contains("不能拆卸称量"))
        assertEquals(explained,JsonUtils.fromJson(JsonUtils.toJson(explained)!!,CollectionCatalogContentVo::class.java))
    }
    @Test fun `free text dimension description is not parsed as a numeric measurement`() {
        val content = CollectionCatalogContentVo(dimensions = DimensionVo(height = "12", extra = mapOf("description" to "展开后测量，附件另记", "画心纵" to "15")), dimensionUnit = "cm")
        assertTrue(CollectionMeasurementPolicy.issues(content).isEmpty())
        assertEquals(2, CollectionMeasurementPolicy.dimensions(content.dimensions).size)
        val printed = io.hizhengfu.cloud.collection.service.CollectionRegistrationPrintFields.dimensions(content)
        assertTrue(printed.contains("补充说明：展开后测量，附件另记"))
        assertFalse(printed.contains("数值或单位待核实"))
        assertTrue(CollectionMeasurementPolicy.dimensions(DimensionVo(extra = mapOf("description" to "尚未测量"))).isEmpty())
        assertTrue(CollectionMeasurementPolicy.issues(content.copy(dimensions = DimensionVo(height = "高约十二"))).isNotEmpty())
    }
    @Test fun `new optional receipt facts preserve legacy serialized evidence`() {
        val original="""{"pieceNo":"old","receivedAt":1782871200000,"identification":"原件","condition":"完好","identityMatches":true,"conditionMatches":true,"photoFileIds":"photo"}"""
        val value=JsonUtils.fromJson(original,AccessionPieceVo::class.java)!!
        assertNull(value.actualQuantity)
        val serialized=JsonUtils.toJson(value)!!
        assertFalse(serialized.contains("actualQuantity"))
        assertEquals(value,JsonUtils.fromJson(serialized,AccessionPieceVo::class.java))
    }
}
