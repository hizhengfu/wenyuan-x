package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionPrintDocxRendererTest {

    private fun numericIdentifierPrint() = CollectionPrintVo(
        type = EnumCollectionPrintType.ENTRY_VOUCHER,
        title = "藏品入馆凭证及实际核收清册",
        documentNo = "000012",
        orgName = "开发合成打印核验馆",
        fields = listOf(CollectionPrintFieldVo("原登记号", "000009"), CollectionPrintFieldVo("档案编号", "000123")),
        collections = listOf(CollectionVo(totalRegNo = "000009", name = "开发合成核收物品", quantity = 1, unit = "件")),
    )

    @Test
    fun `print preserves numeric looking identifiers including leading zeroes`() {
        val bytes = CollectionPrintDocxRenderer.renderToBytes(numericIdentifierPrint())
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            assertTrue(doc.paragraphs.any { it.text.contains("000012") }, documentText(doc))
            assertTrue(doc.tables.flatMap { it.rows }.flatMap { it.tableCells }.any { it.text == "000009" }, documentText(doc))
            assertTrue(documentText(doc).contains("000123"), documentText(doc))
        }
    }

    @Test
    fun `default print tables remain within printable page width with long receipt text`() {
        val print = numericIdentifierPrint().copy(fields = listOf(
            CollectionPrintFieldVo("逐件实际核收情况", "合成核收说明和原件引用".repeat(40)),
            CollectionPrintFieldVo("关联原件", "2101899879612272640"),
        ))
        val bytes = CollectionPrintDocxRenderer.renderToBytes(print)
        val doc = com.aspose.words.Document(ByteArrayInputStream(bytes))
        doc.updatePageLayout()
        val layout = com.aspose.words.LayoutEnumerator(doc)
        var cells = 0
        fun inspect() {
            if (layout.type == com.aspose.words.LayoutEntityType.CELL) {
                cells++
                val rect = layout.rectangle
                val page = doc.getPageInfo(layout.pageIndex - 1)
                assertTrue(rect.x >= 0 && rect.maxX <= page.widthInPoints + 1, "Cell outside paper: $rect / ${page.widthInPoints}")
            }
            if (layout.moveFirstChild()) {
                do { inspect() } while (layout.moveNext())
                layout.moveParent()
            }
        }
        do { inspect() } while (layout.moveNext())
        assertTrue(cells >= 24, "Expected rendered business and collection table cells, got $cells")
    }

    @Test
    fun `multi page PDF retains every collection identifier and repeats the list header`() {
        val items = (1..60).map { index ->
            CollectionVo(totalRegNo = index.toString().padStart(6, '0'), name = "ITEM-${index.toString().padStart(4, '0')}",
                quantity = 1, unit = "件", warehouseName = "合成库位")
        }
        val bytes = CollectionPrintDocxRenderer.renderToBytes(numericIdentifierPrint().copy(collections = items))
        val pdf = ByteArrayOutputStream().use { out ->
            io.hizhengfu.cloud.sys.file.converter.WordFileConverter().convert(ByteArrayInputStream(bytes), "pdf", out)
            out.toByteArray()
        }
        org.apache.pdfbox.Loader.loadPDF(pdf).use { doc ->
            assertTrue(doc.numberOfPages > 1)
            val text = org.apache.pdfbox.text.PDFTextStripper().getText(doc).replace(Regex("\\s+"), "")
            items.forEach { item ->
                assertTrue(text.contains(item.totalRegNo!!), "Missing identifier ${item.totalRegNo}")
                assertEquals(1, Regex(Regex.escape(item.name!!)).findAll(text).count(), "Missing or duplicate item ${item.name}")
            }
            for (page in 1..doc.numberOfPages) {
                val content = org.apache.pdfbox.text.PDFTextStripper().apply { startPage = page; endPage = page }
                    .getText(doc).replace(Regex("\\s+"), "")
                if (content.contains("ITEM-")) assertTrue(content.contains("总登记号"), "Missing header on page $page")
            }
        }
    }

    @Test
    fun `custom template preserves identifier text and its own page geometry`() {
        val print = numericIdentifierPrint()
        val template = CollectionPrintTemplateRegistry.resolve(print).copy(custom = true, templatePath = "custom.docx")
        val original = com.aspose.words.Document()
        com.aspose.words.DocumentBuilder(original).apply {
            pageSetup.pageWidth = 700.0
            pageSetup.leftMargin = 50.0
            writeln("<<[documentNo]>>")
            writeln("<<foreach [item in collections]>><<[item.totalRegNo]>><</foreach>>")
        }
        val templateBytes = ByteArrayOutputStream().use { out -> original.save(out, com.aspose.words.SaveFormat.DOCX); out.toByteArray() }
        val bytes = ByteArrayOutputStream().use { out ->
            CollectionPrintDocxRenderer.render(print.copy(template = template), emptyMap(), out) { templateBytes }
            out.toByteArray()
        }
        val result = com.aspose.words.Document(ByteArrayInputStream(bytes))
        assertEquals(700.0, result.firstSection.pageSetup.pageWidth, 0.1)
        assertEquals(50.0, result.firstSection.pageSetup.leftMargin, 0.1)
        val text = result.toString(com.aspose.words.SaveFormat.TEXT)
        assertTrue(text.contains("000012"), text)
        assertTrue(text.contains("000009"), text)
    }

    @Test
    fun `renderer produces docx from central Word template and prints dictionary labels`() {
        val print = CollectionPrintVo(
            type = EnumCollectionPrintType.MOVEMENT,
            title = "馆藏文物入库单",
            documentNo = "RK-2026-001",
            orgName = "庐山博物馆",
            generatedAt = LocalDateTime.of(2026, 6, 26, 10, 30),
            fields = listOf(
                CollectionPrintFieldVo("出入库类型", "IN", CollectionConst.DICT_MOVE_TYPE),
                CollectionPrintFieldVo("事由", "TRANSFER", CollectionConst.DICT_MOVE_REASON),
                CollectionPrintFieldVo("经办人", "张三"),
            ),
            collections = listOf(
                CollectionVo(
                    totalRegNo = "LS-0001",
                    name = "青釉瓷碗",
                    categoryName = "陶瓷器",
                    era = "宋",
                    texture = "瓷",
                    level = EnumRelicLevel.FIRST,
                    quantity = 1,
                    unit = "件",
                    warehouseName = "一号库-A01",
                    mediaCount = 2,
                    attachmentCount = 1,
                ),
            ),
        )
        val dicts = mapOf(
            CollectionConst.DICT_MOVE_TYPE to mapOf("IN" to "入库", "OUT" to "出库"),
            CollectionConst.DICT_MOVE_REASON to mapOf("TRANSFER" to "调拨"),
            CollectionConst.DICT_RELIC_LEVEL to mapOf("FIRST" to "一级", "UNRATED" to "未定级"),
        )

        val bytes = CollectionPrintDocxRenderer.renderToBytes(print, dicts)
        val text = XWPFDocument(ByteArrayInputStream(bytes)).use { documentText(it) }

        assertTrue(bytes.size > 2_000)
        assertTrue(text.contains("馆藏文物入库单"))
        assertTrue(text.contains("入库"))
        assertTrue(text.contains("调拨"))
        assertTrue(text.contains("LS-0001"))
        assertTrue(text.contains("一级"))
        assertFalse(text.contains("{{"))
        assertFalse(text.contains("IN"))
    }

    @Test
    fun `renderer can merge multiple print payloads into one batch Word document`() {
        val first = CollectionPrintVo(
            type = EnumCollectionPrintType.MOVEMENT,
            title = "馆藏文物入库单",
            documentNo = "RK-2026-001",
            orgName = "庐山博物馆",
        )
        val second = first.copy(documentNo = "RK-2026-002")

        val bytes = CollectionPrintDocxRenderer.renderBatchToBytes(listOf(first, second))
        val text = XWPFDocument(ByteArrayInputStream(bytes)).use { documentText(it) }

        assertTrue(bytes.size > 2_000)
        assertTrue(text.contains("RK-2026-001"))
        assertTrue(text.contains("RK-2026-002"))
    }

    @Test
    fun `batch PDF has no blank separator pages between single and multi page forms`() {
        val prints = listOf(
            numericIdentifierPrint().copy(documentNo = "000101"),
            numericIdentifierPrint().copy(documentNo = "000102", fields = (1..48).map {
                CollectionPrintFieldVo("FIELD-$it", "VALUE-$it ".repeat(8))
            }),
            numericIdentifierPrint().copy(documentNo = "000103"),
        )
        fun pdf(bytes: ByteArray) = ByteArrayOutputStream().use { out ->
            io.hizhengfu.cloud.sys.file.converter.WordFileConverter().convert(ByteArrayInputStream(bytes), "pdf", out)
            out.toByteArray()
        }
        val expectedPages = prints.sumOf { print ->
            org.apache.pdfbox.Loader.loadPDF(pdf(CollectionPrintDocxRenderer.renderToBytes(print))).use { it.numberOfPages }
        }
        org.apache.pdfbox.Loader.loadPDF(pdf(CollectionPrintDocxRenderer.renderBatchToBytes(prints))).use { doc ->
            assertEquals(expectedPages, doc.numberOfPages, "Batch must not add empty separator pages")
            for (page in 1..doc.numberOfPages) {
                val text = org.apache.pdfbox.text.PDFTextStripper().apply { startPage = page; endPage = page }
                    .getText(doc).trim()
                assertTrue(text.isNotEmpty(), "Blank batch page $page")
            }
            val text = org.apache.pdfbox.text.PDFTextStripper().getText(doc)
            prints.forEach { assertTrue(text.contains(it.documentNo!!), "Missing form ${it.documentNo}") }
        }
    }

    @Test
    fun `renderer uses Aspose reporting syntax in custom Word templates`() {
        val template = CollectionPrintTemplateVo(
            code = EnumCollectionPrintType.MOVEMENT.name,
            type = EnumCollectionPrintType.MOVEMENT,
            name = "Aspose模板",
            title = "藏品出入库凭证",
            documentLabel = "出入库单号",
            collectionTitle = "出入库藏品清单",
            templatePath = "custom.docx",
            signatures = emptyList(),
            sort = 1,
            custom = true,
        )
        val print = CollectionPrintVo(
            type = EnumCollectionPrintType.MOVEMENT,
            title = "馆藏文物入库单",
            documentNo = "RK-2026-001",
            orgName = "庐山博物馆",
            generatedAt = LocalDateTime.of(2026, 6, 26, 10, 30),
            template = template,
        )
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            CollectionPrintDocxRenderer.render(print, emptyMap(), out) { asposeSyntaxTemplateBytes() }
            out.toByteArray()
        }
        val text = XWPFDocument(ByteArrayInputStream(bytes)).use { documentText(it) }

        assertTrue(text.contains("馆藏文物入库单"), text)
        assertTrue(text.contains("RK-2026-001"), text)
        assertFalse(text.contains("<<"), text)
    }

    @Test
    fun `custom Word templates must use Aspose reporting syntax`() {
        val template = CollectionPrintTemplateVo(
            code = EnumCollectionPrintType.MOVEMENT.name,
            type = EnumCollectionPrintType.MOVEMENT,
            name = "旧语法模板",
            title = "藏品出入库凭证",
            documentLabel = "出入库单号",
            collectionTitle = "出入库藏品清单",
            templatePath = "legacy.docx",
            signatures = emptyList(),
            sort = 1,
            custom = true,
        )
        val print = CollectionPrintVo(
            type = EnumCollectionPrintType.MOVEMENT,
            title = "馆藏文物入库单",
            documentNo = "RK-2026-001",
            orgName = "庐山博物馆",
            generatedAt = LocalDateTime.of(2026, 6, 26, 10, 30),
            template = template,
        )

        val error = assertThrows(BadRequestException::class.java) {
            ByteArrayOutputStream().use { out ->
                CollectionPrintDocxRenderer.render(print, emptyMap(), out) { legacySyntaxTemplateBytes() }
            }
        }

        assertTrue(error.message!!.contains("Aspose"), error.message)
    }

    @Test
    fun `default Aspose template is built with Aspose document APIs`() {
        val source = rendererSource()
        val defaultTemplateBody = source
            .substringAfter("private fun createDefaultAsposeTemplate()")
            .substringBefore("private fun AsposeDocumentBuilder.writeFieldTable")

        assertTrue(defaultTemplateBody.contains("AsposeDocumentBuilder("), defaultTemplateBody)
        assertFalse(defaultTemplateBody.contains("XWPFDocument().apply"), defaultTemplateBody)
    }

    @Test
    fun `renderer does not use POI table cell helpers in Aspose render paths`() {
        val source = rendererSource()

        assertFalse(source.contains("XWPFDocument"), source)
        assertFalse(source.contains("targetRow.setCell("), source)
        assertFalse(source.contains("XWPFTableRow.setCell"), source)
        assertFalse(source.contains("setTextValue("), source)
        assertFalse(source.contains("XWPFTableCell"), source)
        assertFalse(source.contains("XWPFRun"), source)
    }

    @Test
    fun `template loading service returns template bytes instead of POI documents`() {
        val source = templateServiceSource()

        assertTrue(source.contains("fun loadTemplateBytes"), source)
        assertFalse(source.contains("XWPFDocument"), source)
    }

    @Test
    fun `collection print template separates regular and emphasis fonts`() {
        val source = rendererSource()

        assertTrue(source.contains("private const val REGULAR_FONT = \"仿宋\""), source)
        assertTrue(source.contains("private const val EMPHASIS_FONT = \"黑体\""), source)
        assertFalse(source.contains("font.name = \"SimSun\""), source)
    }

    @Test
    fun `rendered data values are regular instead of bold`() {
        val print = CollectionPrintVo(
            type = EnumCollectionPrintType.MOVEMENT,
            title = "馆藏文物入库单",
            documentNo = "RK-2026-001",
            orgName = "庐山博物馆",
            generatedAt = LocalDateTime.of(2026, 6, 26, 10, 30),
            fields = listOf(CollectionPrintFieldVo("经办人", "张三")),
            collections = listOf(CollectionVo(totalRegNo = "LS-0001", name = "青釉瓷碗")),
        )
        val bytes = CollectionPrintDocxRenderer.renderToBytes(print)
        val doc = XWPFDocument(ByteArrayInputStream(bytes))

        listOf("张三", "LS-0001", "青釉瓷碗").forEach { value ->
            val runs = runsContaining(doc, value)
            assertTrue(runs.isNotEmpty(), value)
            assertTrue(runs.all { it.isBold != true }, value)
        }
    }

    @Test
    fun `word to pdf conversion uses regular Chinese fallback font`() {
        val bytes = CollectionPrintDocxRenderer.renderToBytes(numericIdentifierPrint())
        val document = io.hizhengfu.cloud.common.utils.word.AsposeWordsUtils.instance(ByteArrayInputStream(bytes))
        val settings = document.fontSettings
        val fallback = settings.substitutionSettings.defaultFontSubstitution.defaultFontName
        val regularFaces = settings.fontsSources.flatMap { it.availableFonts }.filter {
            !it.fullFontName.contains(Regex("(?i)(bold|black|heavy|semibold|demi)"))
        }
        assertTrue(regularFaces.any { it.fontFamilyName == fallback }, "No regular face for fallback $fallback")
        assertFalse(fallback.contains("Alimama", ignoreCase = true))
        val values = document.getChildNodes(com.aspose.words.NodeType.RUN, true)
            .map { it as com.aspose.words.Run }.filter { it.text.contains("000123") }
        assertTrue(values.isNotEmpty())
        assertTrue(values.all { it.font.nameFarEast == "仿宋" && !it.font.bold })
    }

    private fun documentText(doc: XWPFDocument): String {
        val paragraphs = doc.paragraphs.joinToString("\n") { it.text }
        val tables = doc.tables.joinToString("\n") { table ->
            table.rows.joinToString("\n") { row ->
                row.tableCells.joinToString("\t") { it.text }
            }
        }
        return "$paragraphs\n$tables"
    }

    private fun runsContaining(doc: XWPFDocument, value: String) =
        (
            doc.paragraphs.flatMap { it.runs } +
                doc.tables.flatMap { table ->
                    table.rows.flatMap { row ->
                        row.tableCells.flatMap { cell -> cell.paragraphs.flatMap { it.runs } }
                    }
                }
            ).filter { it.text().contains(value) }

    private fun asposeSyntaxTemplateBytes(): ByteArray = XWPFDocument().apply {
        createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            createRun().setText("<<[title]>>")
        }
        createParagraph().createRun().setText("<<[documentLabel]>>：<<[documentNo]>>")
    }.toBytes()

    private fun legacySyntaxTemplateBytes(): ByteArray = XWPFDocument().apply {
        createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            createRun().setText("{{title}}")
        }
        createParagraph().createRun().setText("{{documentLabel}}：{{documentNo}}")
    }.toBytes()

    private fun XWPFDocument.toBytes(): ByteArray =
        ByteArrayOutputStream().use { out ->
            use { doc -> doc.write(out) }
            out.toByteArray()
        }

    private fun rendererSource(): String {
        val sourcePath = listOf(
            Path.of("src/main/kotlin/io/hizhengfu/cloud/collection/service/CollectionPrintDocxRenderer.kt"),
            Path.of("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/service/CollectionPrintDocxRenderer.kt"),
        ).first(Files::exists)
        return Files.readString(sourcePath)
    }

    private fun templateServiceSource(): String {
        val sourcePath = listOf(
            Path.of("src/main/kotlin/io/hizhengfu/cloud/collection/service/CollectionPrintTemplateService.kt"),
            Path.of("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/service/CollectionPrintTemplateService.kt"),
        ).first(Files::exists)
        return Files.readString(sourcePath)
    }

    private fun asposeWordsUtilsSource(): String {
        val sourcePath = listOf(
            Path.of("../common/src/main/kotlin/io/hizhengfu/cloud/common/utils/word/AsposeWordsUtils.kt"),
            Path.of("module/common/src/main/kotlin/io/hizhengfu/cloud/common/utils/word/AsposeWordsUtils.kt"),
        ).first(Files::exists)
        return Files.readString(sourcePath)
    }
}
