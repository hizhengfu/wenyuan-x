package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.sys.file.converter.WordFileConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

/** 全类型共用真实渲染器的版式样张，数据均为软件核验样例，不代表实际审批或签署。 */
class CollectionStandardsPrintArtifactTest {
    @Test fun `every configured form retains required registration facts and signature roles`() {
        val c=CollectionVo(id="sample",orgId="sample-museum",totalRegNo="000001",auxNo="OLD-0001",classNo="TC-001",
            name="青花缠枝莲纹对瓶（开发合成样例）",originalName="旧称青花瓶",categoryId="陶瓷",era="清代",texture="陶瓷",
            quantity=1,unit="套",actualQuantity=2,actualQuantityUnit="件",dimensions=DimensionVo(height="125",mouth="42",extra=mapOf("腹围" to "160")),dimensionUnit="mm",
            weight="350.5",weightUnit="g",source=EnumSource.DONATE,sourceDesc="合成捐赠核验案例",level=EnumRelicLevel.THIRD,
            completeness=EnumCompleteness.COMPLETE,completeStatus="通体完整，釉面细小开片；本描述仅用于排版核验",preserveState=EnumPreserve.A,
            acquireDate=LocalDate.of(2026,5,1),entryDate=LocalDate.of(2026,5,2),receiptCompletedAt=LocalDateTime.of(2026,5,3,10,0),registeredAt=LocalDateTime.of(2026,5,8,10,0),
            entryVoucherNo="RJ-2026-0001",warehouseId="STORE-001",feature="成对器物，纹饰相同，两件分别标识；不将分件测量记录重复计为新增藏品",inscription="合成示例款识",
            registrationDetails=CollectionRegistrationDetailsVo(paperLedgerBookNo="合成总账册01",paperLedgerPage="第12页",weightExcludesAccessories=true,measurementMethod="卡尺测量，电子天平称量（合成数据）",measuredBy="核验人员",measuredOn=LocalDate.of(2026,5,3),
                appraisalInstitution="合成鉴定机构",appraisers="示例鉴定人员",appraisedOn=LocalDate.of(2026,5,5),
                provenance=listOf(CatalogProvenanceVo(periodDescription="取得前流传不详",evidence="合成捐赠资料，仅供软件核验")),
                parts=listOf(CatalogPartMeasurementVo(partNo="000001-1",name="瓶一",dimensions=DimensionVo(height="125"),dimensionUnit="mm",weight="175.2",weightUnit="g",includesAccessories=false))))
        c.warehouseName="合成一号库房／A柜／第一层"
        val prints=EnumCollectionPrintType.entries.map { type ->
            val template=CollectionPrintTemplateRegistry.get(type)
            CollectionRegistrationPrintFields.supplement(CollectionPrintVo(type,template.title,documentNo="DEV-${type.name}-0001",orgName="开发合成核验馆",
                generatedAt=LocalDateTime.of(2026,10,13,9,0),fields=listOf(
                    CollectionPrintFieldVo("样张用途","软件字段及版式核验；不代表真实文物、审批、交接或签署"),
                    CollectionPrintFieldVo("业务依据","此处应由真实业务的已批准方案、行政手续和逐件点交原件提供"),
                    CollectionPrintFieldVo("实际签署原件","本合成样张没有真实签署原件")),collections=listOf(c)))
        }
        val bytes=ByteArrayOutputStream().use { out->CollectionPrintDocxRenderer.renderBatch(prints,mapOf("col_relic_level" to mapOf("THIRD" to "三级文物"), "col_completeness" to mapOf("COMPLETE" to "完整"), "col_preserve" to mapOf("A" to "状态稳定"), "col_source" to mapOf("DONATE" to "捐赠")),out);out.toByteArray() }
        val doc=com.aspose.words.Document(ByteArrayInputStream(bytes))
        val text=doc.toString(com.aspose.words.SaveFormat.TEXT)
        for(print in prints) assertTrue(text.contains(print.title))
        assertEquals(prints.size,Regex("实际数量").findAll(text).count())
        for(value in listOf("1 套","2 件","12.5 cm","0.351 kg","2026-05-01","2026-05-02","RJ-2026-0001")) assertTrue(text.contains(value),value)
        assertFalse(text.contains("<<"))
        val signatureTables=doc.getChildNodes(com.aspose.words.NodeType.TABLE,true).filterIsInstance<com.aspose.words.Table>().filter {it.toString(com.aspose.words.SaveFormat.TEXT).contains("__________")}
        assertEquals(prints.size,signatureTables.size,"每份表单的签字栏保持独立表格")
        assertTrue(signatureTables.none {it.toString(com.aspose.words.SaveFormat.TEXT).contains("影像/附件")},"签字表不能与清单合并")
        System.getProperty("collection.standards.artifactDir")?.let { dir ->
            val output=Path.of(dir);Files.createDirectories(output);Files.write(output.resolve("print-forms-sample.docx"),bytes)
            Files.newOutputStream(output.resolve("print-forms-sample.pdf")).use { out->WordFileConverter().convert(ByteArrayInputStream(bytes),"pdf",out) }
        }
    }
}
