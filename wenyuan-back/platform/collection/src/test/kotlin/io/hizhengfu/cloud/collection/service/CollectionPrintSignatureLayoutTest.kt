package io.hizhengfu.cloud.collection.service

import com.aspose.words.NodeType
import com.aspose.words.Paragraph
import com.aspose.words.ParagraphAlignment
import com.aspose.words.Run
import com.aspose.words.SaveFormat
import com.aspose.words.Table
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.common.utils.word.AsposeWordsUtils
import io.hizhengfu.cloud.sys.file.converter.WordFileConverter
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import javax.imageio.ImageIO

class CollectionPrintSignatureLayoutTest {
    private fun image(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(232, 241, 250); fillRect(0, 0, width, height)
            color = Color(31, 56, 85); drawRect(3, 3, width - 6, height - 6)
            drawString("DEV-SYNTHETIC", 12, 25)
            dispose()
        }
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "png", out); out.toByteArray() }
    }

    @Test
    fun `short and long signature labels stay grouped before notes after attachments`() {
        val attachments = listOf(
            CollectionPrintAttachment("正面影像", "DEV-WIDE.png", "dev/signature/wide.png", image(440, 145)),
            CollectionPrintAttachment("侧面影像", "DEV-TALL.png", "dev/signature/tall.png", image(180, 520)),
            CollectionPrintAttachment("说明材料", "DEV-NOTE.txt", "dev/signature/note.txt"),
        )
        for (type in listOf(EnumCollectionPrintType.INVENTORY, EnumCollectionPrintType.TRANSFER)) {
            val print = CollectionPrintVo(type = type, title = "DEV-SYNTHETIC 签署区排版验证",
                documentNo = "DEV-SIGNATURE-LAYOUT", orgName = "开发合成核验馆",
                generatedAt = LocalDateTime.of(2026, 10, 6, 9, 0),
                fields = listOf(CollectionPrintFieldVo("说明", "仅验证软件排版，无真实签署或实物事实")),
                collections = listOf(CollectionVo(totalRegNo = "DEV-000001", name = "开发合成排版样本", quantity = 1, unit = "件")))
            val template = CollectionPrintTemplateRegistry.resolve(print)
            val docx = CollectionPrintDocxRenderer.renderToBytes(print, attachmentResolver = { attachments })
            val doc = AsposeWordsUtils.instance(ByteArrayInputStream(docx))
            val paragraphs = doc.getChildNodes(NodeType.PARAGRAPH, true).filterIsInstance<Paragraph>()
            val number = paragraphs.single { it.toString(SaveFormat.TEXT).trim() == "${template.documentLabel}：DEV-SIGNATURE-LAYOUT" }
            val metadata = paragraphs.single { it.toString(SaveFormat.TEXT).contains("收藏单位：开发合成核验馆") }
            assertFalse(number.toString(SaveFormat.TEXT).contains("生成时间"))
            assertTrue(metadata.toString(SaveFormat.TEXT).contains("生成时间：2026-10-06 09:00:00"))
            listOf(number, metadata).forEach { paragraph ->
                assertEquals(ParagraphAlignment.CENTER, paragraph.paragraphFormat.alignment)
                assertTrue(paragraph.getChildNodes(NodeType.RUN, true).filterIsInstance<Run>().all { it.font.size == 10.0 })
            }
            val signature = doc.getChildNodes(NodeType.TABLE, true).filterIsInstance<Table>().single { table ->
                table.toString(SaveFormat.TEXT).contains(template.signatures.first() + "：__________")
            }
            val expectedColumns = if (type == EnumCollectionPrintType.INVENTORY) 3 else 2
            assertTrue(signature.rows.all { it.cells.count == expectedColumns })
            assertFalse(signature.allowAutoFit)
            val cells = signature.rows.flatMap { it.cells.toList() }
            template.signatures.forEach { label ->
                val group = "$label：__________"
                val cell = cells.single { it.toString(SaveFormat.TEXT).trim() == group }
                assertFalse(cell.cellFormat.wrapText, "A signature label and its line must be indivisible")
                assertTrue(cell.getChildNodes(NodeType.RUN, true).filterIsInstance<Run>().all { it.font.size == 11.0 })
            }
            val text = doc.toString(SaveFormat.TEXT)
            assertTrue(text.indexOf("附件清单") < text.indexOf(template.signatures.first() + "：__________"))
            assertTrue(text.indexOf(template.signatures.last() + "：__________") < text.indexOf("本表为业务记录打印件"))
            val pdf = ByteArrayOutputStream().use { out ->
                WordFileConverter().convert(ByteArrayInputStream(docx), "pdf", out); out.toByteArray()
            }
            Loader.loadPDF(pdf).use { document ->
                val lines = PDFTextStripper().getText(document).lines().map { it.replace(Regex("\\s+"), "") }
                assertEquals(1, lines.count { it.contains("生成时间：2026-10-0609:00:00") }, "Date and time must not split across lines: $lines")
                template.signatures.forEach { label ->
                    assertEquals(1, lines.count { it.contains("$label：__________") }, "Split signature group $label: $lines")
                }
            }
            System.getProperty("collection.print.signature.artifactDir")?.let { configured ->
                val output = Path.of(configured); Files.createDirectories(output)
                val name = if (type == EnumCollectionPrintType.INVENTORY) "signature-layout" else "long-signature-layout"
                Files.write(output.resolve("$name.docx"), docx)
                Files.write(output.resolve("$name.pdf"), pdf)
            }
        }
    }
}
