package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionPrintTemplateRegistryTest {

    @Test
    fun `registry covers every formal print type with maintainable Word template metadata`() {
        val templates = CollectionPrintTemplateRegistry.list()

        assertEquals(EnumCollectionPrintType.entries.toSet(), templates.map { it.type }.toSet())
        assertEquals(templates.size, templates.map { it.code }.toSet().size)
        assertEquals(templates.size, templates.map { it.sort }.toSet().size)
        assertTrue(templates.all { it.templatePath.endsWith(".docx") })
        assertTrue(
            templates.all { javaClass.classLoader.getResource(it.templatePath) != null },
            "registered Word template resources must exist",
        )
        assertTrue(templates.all { it.signatures.isNotEmpty() && it.signatures.none(String::isBlank) })
        assertTrue(templates.all { it.documentLabel.isNotBlank() && it.collectionTitle.isNotBlank() })
    }

    @Test
    fun `movement template resolves inbound and outbound collection titles from print data`() {
        val inbound = CollectionPrintTemplateRegistry.resolve(
            CollectionPrintVo(
                type = EnumCollectionPrintType.MOVEMENT,
                title = "馆藏文物入库单",
                documentNo = "RK-001",
                orgName = "主馆",
            ),
        )
        val outbound = CollectionPrintTemplateRegistry.resolve(
            CollectionPrintVo(
                type = EnumCollectionPrintType.MOVEMENT,
                title = "馆藏文物出库单",
                documentNo = "CK-001",
                orgName = "主馆",
            ),
        )

        assertEquals("馆藏文物入库单", inbound.title)
        assertEquals("入库藏品清单", inbound.collectionTitle)
        assertEquals("馆藏文物出库单", outbound.title)
        assertEquals("出库藏品清单", outbound.collectionTitle)
    }

    @Test
    fun `transfer template distinguishes case number from actual administrative document`() {
        val casePrint = CollectionPrintVo(
            type = EnumCollectionPrintType.TRANSFER,
            title = "博物馆藏品跨馆转移交接表",
            businessId = "transfer-1",
            documentNo = "transfer-1",
            orgName = "源馆",
        )
        val caseTemplate = CollectionPrintTemplateRegistry.resolve(casePrint)
        assertEquals("案卷编号", caseTemplate.documentLabel)
        assertEquals("逐件跨馆转移清单", caseTemplate.collectionTitle)
        assertTrue(caseTemplate.signatures.contains("源馆主要负责人或依法授权人员"))
        assertEquals("行政文书号", CollectionPrintTemplateRegistry.resolve(
            casePrint.copy(documentNo = "行政文书-1"),
        ).documentLabel)
    }
}
