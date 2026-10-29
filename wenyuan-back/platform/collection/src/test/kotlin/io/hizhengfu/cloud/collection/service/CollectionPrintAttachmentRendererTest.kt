package io.hizhengfu.cloud.collection.service

import com.aspose.words.Document
import com.aspose.words.DocumentBuilder
import com.aspose.words.LayoutEntityType
import com.aspose.words.LayoutEnumerator
import com.aspose.words.NodeType
import com.aspose.words.SaveFormat
import com.aspose.words.Shape
import com.aspose.words.Run
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.common.utils.word.AsposeWordsUtils
import io.hizhengfu.cloud.sys.file.converter.WordFileConverter
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
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

/** Real Aspose DOCX/PDF output; all files and images are explicit development fixtures. */
class CollectionPrintAttachmentRendererTest {
    private val fileIds = listOf("2107050000000000001", "2107050000000000002", "2107050000000000003")

    private fun print(number: String = "000012") = CollectionPrintVo(
        type = EnumCollectionPrintType.ENTRY_VOUCHER,
        title = "DEV-SYNTHETIC 藏品附件打印",
        documentNo = number,
        orgName = "开发合成附件核验馆",
        generatedAt = LocalDateTime.of(2026, 10, 5, 15, 0),
        fields = listOf(
            CollectionPrintFieldVo("附件原件", fileIds.joinToString(","), fileReferences = fileIds),
            CollectionPrintFieldVo("说明", "仅开发软件测试，无真实签署或实物点交"),
        ),
        collections = listOf(CollectionVo(totalRegNo = "000009", name = "DEV-SYNTHETIC 附件样本", quantity = 1, unit = "件")),
    )

    private fun image(width: Int, height: Int, label: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().useGraphics { graphics ->
            graphics.color = Color(232, 241, 250)
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color(31, 56, 85)
            graphics.drawRect(2, 2, width - 5, height - 5)
            graphics.drawString("DEV-SYNTHETIC $label", 12, minOf(height - 8, 30))
        }
        return ByteArrayOutputStream().use { out -> ImageIO.write(image, "png", out); out.toByteArray() }
    }

    private inline fun java.awt.Graphics2D.useGraphics(block: (java.awt.Graphics2D) -> Unit) {
        try { block(this) } finally { dispose() }
    }

    private fun attachments() = listOf(
        CollectionPrintAttachment("正面影像", "DEV-WIDE.png", "collection/2026/10/dev-wide.png", image(440, 145, "WIDE")),
        CollectionPrintAttachment("侧面影像", "DEV-TALL.png", "collection/2026/10/dev-tall.png", image(180, 520, "TALL")),
        CollectionPrintAttachment("核收依据", "DEV-RECEIPT.pdf", "collection/2026/10/dev-receipt.pdf"),
    )

    private fun pdf(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { out ->
        WordFileConverter().convert(ByteArrayInputStream(bytes), "pdf", out)
        out.toByteArray()
    }

    private fun compact(text: String) = text.replace(Regex("[\\s\u200B]+"), "")

    private fun export(name: String, docx: ByteArray, pdf: ByteArray) {
        System.getProperty("collection.print.attachment.artifactDir")?.let { configured ->
            val dir = Path.of(configured)
            Files.createDirectories(dir)
            Files.write(dir.resolve("$name.docx"), docx)
            Files.write(dir.resolve("$name.pdf"), pdf)
        }
    }

    private fun assertPageBounds(document: Document) {
        document.updatePageLayout()
        val layout = LayoutEnumerator(document)
        fun visit() {
            if (layout.type == LayoutEntityType.CELL) {
                val rect = layout.rectangle
                val page = document.getPageInfo(layout.pageIndex - 1)
                assertTrue(rect.x >= -1 && rect.maxX <= page.widthInPoints + 1, "Cell outside paper: $rect")
            }
            if (layout.moveFirstChild()) {
                do { visit() } while (layout.moveNext())
                layout.moveParent()
            }
        }
        do { visit() } while (layout.moveNext())
    }

    @Test
    fun `default form embeds proportional pictures before signature and hides attachment IDs`() {
        val bytes = CollectionPrintDocxRenderer.renderToBytes(print(), attachmentResolver = { attachments() })
        val document = AsposeWordsUtils.instance(ByteArrayInputStream(bytes))
        val shapes = document.getChildNodes(NodeType.SHAPE, true).filterIsInstance<Shape>().filter { it.hasImage() }
        assertEquals(2, shapes.size)
        val ratios = shapes.map { it.width / it.height }.sorted()
        assertEquals(180.0 / 520.0, ratios[0], 0.001)
        assertEquals(440.0 / 145.0, ratios[1], 0.001)
        shapes.forEach { shape ->
            assertTrue(shape.width <= 200.01 && shape.height <= 110.01, "Oversized thumbnail: ${shape.width} x ${shape.height}")
        }
        XWPFDocument(ByteArrayInputStream(bytes)).use { assertEquals(2, it.allPictures.size) }
        val text = document.toString(SaveFormat.TEXT)
        assertTrue(text.contains("3 个文件，详见附件清单"), text)
        fileIds.forEach { assertFalse(text.contains(it), "Raw attachment ID printed: $it") }
        val signature = CollectionPrintTemplateRegistry.resolve(print()).signatures.first()
        assertTrue(text.indexOf("附件清单") < text.indexOf("$signature：__________"), text)
        assertPageBounds(document)
        val renderedPdf = pdf(bytes)
        Loader.loadPDF(renderedPdf).use { result ->
            val pdfText = compact(PDFTextStripper().getText(result))
            attachments().forEach { assertTrue(pdfText.contains(it.name), pdfText) }
            val pictureCount = result.pages.sumOf { page -> page.resources.xObjectNames.count { page.resources.getXObject(it) is PDImageXObject } }
            assertTrue(pictureCount >= 2, "PDF must embed the pictures")
        }
        export("default-attachments", bytes, renderedPdf)
    }

    @Test
    fun `non image names long relative paths and unreadable status are readable in PDF`() {
        val longPath = "collection/2026/10/" + "longrelativepath".repeat(13) + "/DEV-BOUNDARY.pdf"
        val files = listOf(
            CollectionPrintAttachment("实际依据", "DEV-BOUNDARY.pdf", longPath),
            CollectionPrintAttachment("缺失附件", "DEV-MISSING.pdf", "collection/2026/10/dev-missing.pdf", status = "原件缺失，未嵌入"),
        )
        val bytes = CollectionPrintDocxRenderer.renderToBytes(print(), attachmentResolver = { files })
        val document = AsposeWordsUtils.instance(ByteArrayInputStream(bytes))
        assertPageBounds(document)
        assertEquals(0, document.getChildNodes(NodeType.SHAPE, true).filterIsInstance<Shape>().count { it.hasImage() })
        val renderedPdf = pdf(bytes)
        Loader.loadPDF(renderedPdf).use { result ->
            val text = compact(PDFTextStripper().getText(result))
            files.forEach { file -> assertTrue(text.contains(file.name) && text.contains(file.path), text) }
            assertTrue(text.contains("原件缺失，未嵌入"), text)
            fileIds.forEach { assertFalse(text.contains(it)) }
        }
        export("long-path-attachments", bytes, renderedPdf)
    }

    @Test
    fun `many pictures flow across pages without missing captions or blank pages`() {
        val files = (1..20).map { index ->
            CollectionPrintAttachment("影像 $index", "DEV-IMAGE-${index.toString().padStart(2, '0')}.png", "collection/2026/10/gallery/image-$index.png", image(440, 145, "IMAGE-$index"))
        }
        val bytes = CollectionPrintDocxRenderer.renderToBytes(print(), attachmentResolver = { files })
        val document = AsposeWordsUtils.instance(ByteArrayInputStream(bytes))
        assertEquals(20, document.getChildNodes(NodeType.SHAPE, true).filterIsInstance<Shape>().count { it.hasImage() })
        val pathRuns = document.getChildNodes(NodeType.RUN, true).filterIsInstance<Run>().filter { it.text.startsWith("路径：") }
        assertEquals(20, pathRuns.size)
        pathRuns.forEach { assertFalse(it.font.bold, "Attachment paths must use regular text") }
        assertPageBounds(document)
        val renderedPdf = pdf(bytes)
        Loader.loadPDF(renderedPdf).use { result ->
            assertTrue(result.numberOfPages > 1)
            val text = compact(PDFTextStripper().getText(result))
            files.forEach { assertEquals(1, Regex(Regex.escape(it.name)).findAll(text).count(), "Missing or repeated ${it.name}") }
            for (page in 1..result.numberOfPages) {
                val pageText = PDFTextStripper().apply { startPage = page; endPage = page }.getText(result)
                assertTrue(pageText.isNotBlank(), "Blank page $page")
            }
            val firstPage = compact(PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(result))
            assertTrue(firstPage.contains("000009") && firstPage.contains("DEV-IMAGE-01.png"), "The gallery must use the remaining space below business content: $firstPage")
            assertTrue(result.getPage(0).resources.xObjectNames.any { result.getPage(0).resources.getXObject(it) is PDImageXObject }, "The first page must contain a gallery picture")
            val lastPage = compact(PDFTextStripper().apply { startPage = result.numberOfPages; endPage = result.numberOfPages }.getText(result))
            val signature = CollectionPrintTemplateRegistry.resolve(print()).signatures.first()
            assertTrue(lastPage.contains(signature) && lastPage.contains("DEV-IMAGE-20.png"), "The signature must accompany the final gallery row: $lastPage")
        }
        export("twenty-image-attachments", bytes, renderedPdf)
    }

    @Test
    fun `custom template keeps geometry and appends attachments to rendered business content`() {
        val original = Document()
        DocumentBuilder(original).apply {
            pageSetup.pageWidth = 700.0
            pageSetup.pageHeight = 850.0
            pageSetup.leftMargin = 50.0
            pageSetup.rightMargin = 65.0
            writeln("CUSTOM <<[documentNo]>>")
            writeln("<<foreach [row in fieldRows]>><<[row.label1]>>: <<[row.value1]>><</foreach>>")
            writeln("CUSTOM-END")
        }
        val templateBytes = ByteArrayOutputStream().use { out -> original.save(out, SaveFormat.DOCX); out.toByteArray() }
        val custom = print().copy(template = CollectionPrintTemplateRegistry.resolve(print()).copy(custom = true, templatePath = "DEV-SYNTHETIC-custom.docx"))
        val bytes = ByteArrayOutputStream().use { out ->
            CollectionPrintDocxRenderer.render(custom, emptyMap(), out, { templateBytes }, { attachments() })
            out.toByteArray()
        }
        val result = AsposeWordsUtils.instance(ByteArrayInputStream(bytes))
        assertEquals(700.0, result.firstSection.pageSetup.pageWidth, 0.1)
        assertEquals(850.0, result.firstSection.pageSetup.pageHeight, 0.1)
        assertEquals(50.0, result.firstSection.pageSetup.leftMargin, 0.1)
        assertEquals(65.0, result.firstSection.pageSetup.rightMargin, 0.1)
        val text = result.toString(SaveFormat.TEXT)
        assertTrue(text.contains("CUSTOM 000012") && text.contains("3 个文件，详见附件清单"), text)
        assertTrue(text.indexOf("CUSTOM-END") < text.indexOf("附件清单（"), text)
        fileIds.forEach { assertFalse(text.contains(it)) }
        assertEquals(2, result.getChildNodes(NodeType.SHAPE, true).filterIsInstance<Shape>().count { it.hasImage() })
        assertPageBounds(result)
        val renderedPdf = pdf(bytes)
        Loader.loadPDF(renderedPdf).use { doc -> assertTrue(compact(PDFTextStripper().getText(doc)).contains("collection/2026/10/dev-wide.png")) }
        export("custom-attachments", bytes, renderedPdf)
    }

    @Test
    fun `batch resolves each form separately and adds no empty separator page`() {
        val prints = listOf(print("000101"), print("000102"))
        val resolved = mutableListOf<String?>()
        val resolver: (CollectionPrintVo) -> List<CollectionPrintAttachment> = { item ->
            resolved += item.documentNo
            listOf(CollectionPrintAttachment("批量影像", "DEV-${item.documentNo}.png", "collection/batch/${item.documentNo}.png", image(440, 145, item.documentNo!!)))
        }
        val expectedPages = prints.sumOf { item -> Loader.loadPDF(pdf(CollectionPrintDocxRenderer.renderToBytes(item, attachmentResolver = resolver))).use { it.numberOfPages } }
        resolved.clear()
        val bytes = CollectionPrintDocxRenderer.renderBatchToBytes(prints, attachmentResolver = resolver)
        assertEquals(listOf("000101", "000102"), resolved)
        val renderedPdf = pdf(bytes)
        Loader.loadPDF(renderedPdf).use { doc ->
            assertEquals(expectedPages, doc.numberOfPages)
            val text = compact(PDFTextStripper().getText(doc))
            assertTrue(text.indexOf("DEV-000101.png") < text.indexOf("DEV-000102.png"), text)
            prints.forEach { assertEquals(1, Regex("DEV-${it.documentNo}\\.png").findAll(text).count()) }
            for (page in 1..doc.numberOfPages) {
                assertTrue(PDFTextStripper().apply { startPage = page; endPage = page }.getText(doc).isNotBlank(), "Blank batch page $page")
            }
        }
        export("batch-attachments", bytes, renderedPdf)
    }

    @Test
    fun `caller without resolver gets visible unresolved summary instead of file IDs`() {
        val bytes = CollectionPrintDocxRenderer.renderToBytes(print())
        val text = AsposeWordsUtils.instance(ByteArrayInputStream(bytes)).toString(SaveFormat.TEXT)
        assertTrue(text.contains("3 个文件，详见附件清单") && text.contains("附件信息未解析"), text)
        fileIds.forEach { assertFalse(text.contains(it), "Raw file reference printed") }
    }
}
