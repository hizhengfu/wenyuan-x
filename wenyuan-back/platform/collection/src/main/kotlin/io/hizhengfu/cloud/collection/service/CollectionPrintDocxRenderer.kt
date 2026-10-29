package io.hizhengfu.cloud.collection.service

import com.aspose.words.NodeType as AsposeNodeType
import com.aspose.words.Paragraph as AsposeParagraph
import com.aspose.words.TableAlignment as AsposeTableAlignment
import com.aspose.words.Table as AsposeTable
import com.aspose.words.LineStyle as AsposeLineStyle
import com.aspose.words.CellVerticalAlignment as AsposeCellVerticalAlignment
import com.aspose.words.Document as AsposeDocument
import com.aspose.words.DocumentBuilder as AsposeDocumentBuilder
import com.aspose.words.ImportFormatMode as AsposeImportFormatMode
import com.aspose.words.License as AsposeLicense
import com.aspose.words.JsonDataLoadOptions
import com.aspose.words.JsonDataSource
import com.aspose.words.JsonSimpleValueParseMode
import com.aspose.words.ParagraphAlignment as AsposeParagraphAlignment
import com.aspose.words.PreferredWidth as AsposePreferredWidth
import com.aspose.words.SaveFormat as AsposeSaveFormat
import com.aspose.words.SectionStart as AsposeSectionStart
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.word.AsposeWordsUtils
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.utils.word.license
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.format.DateTimeFormatter

object CollectionPrintDocxRenderer {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private const val REGULAR_FONT = "仿宋"
    private const val EMPHASIS_FONT = "黑体"

    fun renderToBytes(
        print: CollectionPrintVo,
        dicts: Map<String, Map<String, String>> = emptyMap(),
        attachmentResolver: ((CollectionPrintVo) -> List<CollectionPrintAttachment>)? = null,
    ): ByteArray = ByteArrayOutputStream().use { out ->
        render(print, dicts, out, null, attachmentResolver)
        out.toByteArray()
    }

    fun renderBatchToBytes(
        prints: List<CollectionPrintVo>,
        dicts: Map<String, Map<String, String>> = emptyMap(),
        attachmentResolver: ((CollectionPrintVo) -> List<CollectionPrintAttachment>)? = null,
    ): ByteArray = ByteArrayOutputStream().use { out ->
        renderBatch(prints, dicts, out, null, attachmentResolver)
        out.toByteArray()
    }

    fun render(
        print: CollectionPrintVo,
        dicts: Map<String, Map<String, String>>,
        out: OutputStream,
        templateLoader: ((CollectionPrintTemplateVo) -> ByteArray?)? = null,
    ) = render(print, dicts, out, templateLoader, null)

    // Keep the original overload so an existing trailing lambda remains a template loader.
    fun render(
        print: CollectionPrintVo,
        dicts: Map<String, Map<String, String>>,
        out: OutputStream,
        templateLoader: ((CollectionPrintTemplateVo) -> ByteArray?)?,
        attachmentResolver: ((CollectionPrintVo) -> List<CollectionPrintAttachment>)?,
    ) {
        val template = print.template ?: CollectionPrintTemplateRegistry.resolve(print)
        val attachments = printAttachments(print, attachmentResolver)
        renderAsposeTemplate(template, print, dicts, templateLoader, attachments).save(out, AsposeSaveFormat.DOCX)
    }

    fun renderBatch(
        prints: List<CollectionPrintVo>,
        dicts: Map<String, Map<String, String>>,
        out: OutputStream,
        templateLoader: ((CollectionPrintTemplateVo) -> ByteArray?)? = null,
    ) = renderBatch(prints, dicts, out, templateLoader, null)

    fun renderBatch(
        prints: List<CollectionPrintVo>,
        dicts: Map<String, Map<String, String>>,
        out: OutputStream,
        templateLoader: ((CollectionPrintTemplateVo) -> ByteArray?)?,
        attachmentResolver: ((CollectionPrintVo) -> List<CollectionPrintAttachment>)?,
    ) {
        val renderedDocs = prints.ifEmpty {
            listOf(
                CollectionPrintVo(
                    type = io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType.MOVEMENT,
                    title = "暂无打印内容",
                    documentNo = null,
                    orgName = null,
                ),
            )
        }.map { print ->
            val resolved = print.copy(template = print.template ?: CollectionPrintTemplateRegistry.resolve(print))
            renderAsposeTemplate(resolved.template!!, resolved, dicts, templateLoader, printAttachments(resolved, attachmentResolver))
        }
        val merged = renderedDocs.first()
        renderedDocs.drop(1).forEach { doc ->
            // Appending imports the source section break. A separate page break
            // would leave a blank page between otherwise complete forms.
            doc.firstSection.pageSetup.sectionStart = AsposeSectionStart.NEW_PAGE
            merged.appendDocument(doc, AsposeImportFormatMode.KEEP_SOURCE_FORMATTING)
        }
        merged.save(out, AsposeSaveFormat.DOCX)
    }

    private fun loadTemplateDocument(
        template: CollectionPrintTemplateVo,
        templateLoader: ((CollectionPrintTemplateVo) -> ByteArray?)?,
    ): AsposeDocument? {
        templateLoader?.invoke(template)?.let { return AsposeWordsUtils.instance(ByteArrayInputStream(it)) }
        val loader = Thread.currentThread().contextClassLoader ?: CollectionPrintDocxRenderer::class.java.classLoader
        val stream = loader.getResourceAsStream(template.templatePath) ?: return null
        return stream.use { AsposeWordsUtils.instance(it) }
    }

    private fun renderAsposeTemplate(
        template: CollectionPrintTemplateVo,
        print: CollectionPrintVo,
        dicts: Map<String, Map<String, String>>,
        templateLoader: ((CollectionPrintTemplateVo) -> ByteArray?)?,
        attachments: List<CollectionPrintAttachment>,
    ): AsposeDocument {
        if (!template.custom) {
            return renderAsposeDocument(createDefaultAsposeTemplate(), template, print, dicts, attachments)
        }
        val doc = loadTemplateDocument(template, templateLoader)
            ?: throw BadRequestException("自定义打印模板文件不存在或无法读取：${template.templatePath}")
        if (!containsAsposeSyntax(doc)) {
            throw BadRequestException("自定义打印模板必须使用 Aspose Reporting Engine 语法（<<...>>）")
        }
        val rendered=renderAsposeDocument(doc, template, print, dicts, attachments)
        // 自定义主表不能通过省略占位符丢失规范著录、点交事实或签署角色。
        val supplementTemplate=CollectionPrintTemplateRegistry.resolve(print.copy(template=null))
        val supplement=renderAsposeDocument(createDefaultAsposeTemplate(),supplementTemplate,
            print.copy(title="${print.title}——规范字段核对附表",template=supplementTemplate),dicts,emptyList())
        supplement.firstSection.pageSetup.sectionStart=AsposeSectionStart.NEW_PAGE
        rendered.appendDocument(supplement,AsposeImportFormatMode.KEEP_SOURCE_FORMATTING)
        return rendered
    }

    private fun renderAsposeDocument(
        doc: AsposeDocument,
        template: CollectionPrintTemplateVo,
        print: CollectionPrintVo,
        dicts: Map<String, Map<String, String>>,
        attachments: List<CollectionPrintAttachment>,
    ): AsposeDocument {
        // 登记号、档案号等是标识文本，不能由报表引擎推断为数值后丢失前导零。
        val options = JsonDataLoadOptions().apply {
            simpleValueParseMode = JsonSimpleValueParseMode.STRICT
            exactDateTimeParseFormats = emptyList<String>()
        }
        val source = JsonDataSource(ByteArrayInputStream(
            JsonUtils.toJson(buildAsposeData(template, print, dicts))!!.toByteArray(Charsets.UTF_8),
        ), options)
        val rendered = AsposeWordsUtils.render(doc, source)
        val signature = if (template.custom) null else groupDefaultSignatures(rendered, template)
        if (attachments.isNotEmpty()) appendAttachments(rendered, attachments, signature)
        return rendered
    }

    private fun groupDefaultSignatures(doc: AsposeDocument, template: CollectionPrintTemplateVo): AsposeTable? {
        val text = template.signatures.joinToString("      ") { "$it：__________" }
        if (text.isBlank()) return null
        val paragraph = doc.getChildNodes(AsposeNodeType.PARAGRAPH, true).filterIsInstance<AsposeParagraph>()
            .firstOrNull { it.toString(AsposeSaveFormat.TEXT).trim() == text } ?: return null
        val fragment = AsposeWordsUtils.configureFonts(AsposeDocument())
        val available = doc.lastSection.pageSetup.run { pageWidth - leftMargin - rightMargin }
        // Keep the full label and signature line within a cell at the existing font size.
        val groupWidth = template.signatures.maxOf { label ->
            "$label：__________".sumOf { if (it.code > 0x7f) 11.0 else 5.5 } + 6.0
        }
        val columns = (available / groupWidth).toInt().coerceIn(1, 3)
        val builder = AsposeDocumentBuilder(fragment)
        val table = builder.startTable()
        template.signatures.chunked(columns).forEachIndexed { rowIndex, signatures ->
            repeat(columns) { index ->
                builder.insertCell()
                builder.rowFormat.clearFormatting()
                builder.rowFormat.allowBreakAcrossPages = false
                builder.cellFormat.clearFormatting()
                builder.cellFormat.width = available / columns
                builder.cellFormat.preferredWidth = AsposePreferredWidth.fromPoints(available / columns)
                builder.cellFormat.wrapText = false
                builder.cellFormat.leftPadding = 0.0
                builder.cellFormat.rightPadding = 6.0
                builder.paragraphFormat.clearFormatting()
                builder.paragraphFormat.spaceBefore = if (rowIndex == 0) 18.0 else 0.0
                builder.paragraphFormat.spaceAfter = 6.0
                builder.paragraphFormat.keepWithNext = true
                builder.font.clearFormatting()
                builder.font.name = REGULAR_FONT
                builder.font.size = 11.0
                signatures.getOrNull(index)?.let { builder.write("$it：__________") }
            }
            builder.endRow()
        }
        builder.endTable()
        table.allowAutoFit = false
        table.preferredWidth = AsposePreferredWidth.fromPoints(available)
        table.setBorders(AsposeLineStyle.NONE, 0.0, java.awt.Color.BLACK)
        val imported = doc.importNode(table, true, AsposeImportFormatMode.KEEP_SOURCE_FORMATTING) as AsposeTable
        // Word 会合并相邻且无段落隔开的表格；必须保留分隔段，批量导入也不能改变清单列宽。
        paragraph.removeAllChildren()
        paragraph.paragraphFormat.spaceBefore = 0.0
        paragraph.paragraphFormat.spaceAfter = 0.0
        paragraph.paragraphFormat.keepWithNext = true
        paragraph.paragraphBreakFont.size = 1.0
        paragraph.parentNode.insertAfter(imported, paragraph)
        return imported
    }

    private fun containsAsposeSyntax(doc: AsposeDocument): Boolean =
        doc.toString(AsposeSaveFormat.TEXT).contains("<<")

    private fun buildAsposeData(
        template: CollectionPrintTemplateVo,
        print: CollectionPrintVo,
        dicts: Map<String, Map<String, String>>,
    ): Map<String, Any> {
        val collections = print.collections.mapIndexed { index, item ->
            collectionAsposeRow(index, item, dicts)
        }.ifEmpty {
            listOf(
                mapOf(
                    "index" to "1",
                    "totalRegNo" to "--",
                    "name" to "暂无藏品",
                    "level" to "--",
                    "categoryName" to "--",
                    "era" to "--",
                    "texture" to "--",
                    "quantity" to "--",
                    "warehouseName" to "--",
                    "mediaAttachmentCount" to "--",
                ),
            )
        }
        val prefixes = print.collections.mapIndexed { i, c -> "藏品 ${i+1}（${c.totalRegNo ?: "待登记"}）" }
        val registrationGroups = prefixes.mapNotNull { prefix ->
            val fields = print.fields.filter { it.label.startsWith(prefix) }.map { it.copy(label=it.label.removePrefix(prefix)) }
            if (fields.isEmpty()) null else mapOf("title" to prefix, "rows" to fieldAsposeRows(fields, dicts))
        }
        return mapOf(
            "businessFieldRows" to fieldAsposeRows(print.fields.filterNot { f -> prefixes.any { f.label.startsWith(it) } }, dicts),
            "registrationGroups" to registrationGroups,
            "title" to print.title.ifBlank { template.title },
            "documentLabel" to template.documentLabel,
            "documentNo" to print.documentNo.orBlankValue(),
            "orgName" to print.orgName.orBlankValue(),
            "generatedAt" to print.generatedAt.format(dateTimeFormatter),
            "collectionTitle" to template.collectionTitle,
            "collectionCount" to print.collections.size.toString(),
            "fieldRows" to fieldAsposeRows(print.fields, dicts),
            "collections" to collections,
            "signaturesText" to template.signatures.joinToString("      ") { "$it：__________" },
            "note" to "本表为业务记录打印件；已签署事实以所列签署原件为据。空白签字栏用于实际核对签署，打印不表示已批准、交接或归档。",
        )
    }

    private fun fieldAsposeRows(
        fields: List<CollectionPrintFieldVo>,
        dicts: Map<String, Map<String, String>>,
    ): List<Map<String, String>> =
        fields.chunked(2).ifEmpty { listOf(emptyList()) }.map { rowFields ->
            val first = rowFields.getOrNull(0)
            val second = rowFields.getOrNull(1)
            mapOf(
                "label1" to (first?.label ?: "字段"),
                "value1" to (first?.let { formatField(it, dicts) } ?: "--"),
                "label2" to (second?.label ?: ""),
                "value2" to (second?.let { formatField(it, dicts) } ?: ""),
            )
        }

    private fun collectionAsposeRow(
        index: Int,
        item: CollectionVo,
        dicts: Map<String, Map<String, String>>,
    ): Map<String, String> = mapOf(
        "index" to "${index + 1}",
        "totalRegNo" to item.totalRegNo.orBlankValue(),
        "name" to item.name.orBlankValue(),
        "level" to formatDict(CollectionConst.DICT_RELIC_LEVEL, item.level?.name, dicts).orBlankValue(),
        "categoryName" to (item.categoryName ?: item.categoryId).orBlankValue(),
        "era" to item.era.orBlankValue(),
        "texture" to formatDict(CollectionConst.DICT_TEXTURE, item.texture, dicts).orBlankValue(),
        "quantity" to listOfNotNull(item.quantity?.toString(), item.unit).joinToString(" ").ifBlank { "--" },
        "warehouseName" to (item.warehouseName ?: item.warehouseId).orBlankValue(),
        "mediaAttachmentCount" to "${item.mediaCount} / ${item.attachmentCount}",
    )

    private fun createDefaultAsposeTemplate(): AsposeDocument {
        AsposeLicense().setLicense(ByteArrayInputStream(license.toByteArray()))
        val doc = AsposeWordsUtils.configureFonts(AsposeDocument())
        doc.firstSection.pageSetup.apply {
            paperSize = com.aspose.words.PaperSize.A4
            topMargin = 48.0; bottomMargin = 48.0; leftMargin = 48.0; rightMargin = 48.0
        }
        val builder = AsposeDocumentBuilder(doc)
        builder.moveToHeaderFooter(com.aspose.words.HeaderFooterType.FOOTER_PRIMARY)
        builder.paragraphFormat.alignment = AsposeParagraphAlignment.CENTER
        builder.font.name = REGULAR_FONT; builder.font.size = 9.0
        builder.write("第 "); builder.insertField("PAGE", ""); builder.write(" 页 / 共 "); builder.insertField("NUMPAGES", ""); builder.write(" 页")
        builder.moveToDocumentEnd()

        builder.writeTemplateParagraph(
            "<<[title]>>",
            20.0,
            fontName = EMPHASIS_FONT,
            bold = true,
            align = AsposeParagraphAlignment.CENTER,
            spaceAfter = 9.0,
        )
        builder.writeTemplateParagraph(
            "<<[documentLabel]>>：<<[documentNo]>>",
            10.0,
            align = AsposeParagraphAlignment.CENTER,
            keepWithNext = true,
        )
        builder.writeTemplateParagraph(
            "收藏单位：<<[orgName]>>    生成时间：<<[generatedAt]>>",
            10.0,
            align = AsposeParagraphAlignment.CENTER,
            spaceAfter = 8.0,
        )
        builder.writeSectionTitle("业务信息")
        builder.writeFieldTable("businessFieldRows")
        builder.writeSectionTitle("<<foreach [group in registrationGroups]>><<[group.title]>> · 著录字段")
        builder.writeFieldTable("group.rows")
        builder.writeTemplateParagraph("<</foreach>>", 1.0)
        builder.writeSectionTitle("<<[collectionTitle]>>（共 <<[collectionCount]>> 条）")
        builder.writeCollectionTable()
        builder.writeTemplateParagraph("签署核对：<<[title]>>；<<[documentLabel]>>：<<[documentNo]>>", 9.0, spaceBefore = 8.0, keepWithNext = true)
        builder.writeTemplateParagraph("<<[signaturesText]>>", 11.0, spaceBefore = 18.0, spaceAfter = 6.0)
        builder.writeTemplateParagraph("<<[note]>>", 9.0, spaceBefore = 6.0)

        return doc
    }

    private fun AsposeDocumentBuilder.writeFieldTable(source: String) {
        startTable()
        writeTemplateCell("<<foreach [row in $source]>><<[row.label1]>>", emphasis = true, align = AsposeParagraphAlignment.CENTER, widthPercent = 20.0)
        writeTemplateCell("<<[row.value1]>>", widthPercent = 30.0)
        writeTemplateCell("<<[row.label2]>>", emphasis = true, align = AsposeParagraphAlignment.CENTER, widthPercent = 20.0)
        writeTemplateCell("<<[row.value2]>><</foreach>>", widthPercent = 30.0)
        endRow()
        endTable().apply {
            allowAutoFit = false
            preferredWidth = AsposePreferredWidth.fromPercent(100.0)
        }
    }

    private fun AsposeDocumentBuilder.writeCollectionTable() {
        val headers = listOf("序号", "总登记号", "名称", "级别", "分类", "年代", "质地", "数量", "库位", "影像/附件")
        val widths = listOf(7.0, 12.0, 23.0, 7.0, 8.0, 7.0, 8.0, 7.0, 12.0, 9.0)
        startTable()
        headers.forEachIndexed { index, header ->
            writeTemplateCell(header, emphasis = true, align = AsposeParagraphAlignment.CENTER, widthPercent = widths[index])
        }
        endRow()
        listOf(
            "<<foreach [item in collections]>><<[item.index]>>",
            "<<[item.totalRegNo]>>",
            "<<[item.name]>>",
            "<<[item.level]>>",
            "<<[item.categoryName]>>",
            "<<[item.era]>>",
            "<<[item.texture]>>",
            "<<[item.quantity]>>",
            "<<[item.warehouseName]>>",
            "<<[item.mediaAttachmentCount]>><</foreach>>",
        ).forEachIndexed { index, value ->
            writeTemplateCell(
                value,
                align = if (index == 0) AsposeParagraphAlignment.CENTER else AsposeParagraphAlignment.LEFT,
                widthPercent = widths[index],
            )
        }
        endRow()
        endTable().apply {
            allowAutoFit = false
            preferredWidth = AsposePreferredWidth.fromPercent(100.0)
            firstRow.rowFormat.headingFormat = true
            firstRow.cells.forEach { cell ->
                cell.paragraphs.forEach { paragraph -> paragraph.paragraphFormat.keepWithNext = true }
            }
        }
    }

    private fun AsposeDocumentBuilder.writeSectionTitle(text: String) {
        writeTemplateParagraph(text, 12.0, fontName = EMPHASIS_FONT, spaceBefore = 9.0, spaceAfter = 4.0, keepWithNext = true)
    }

    private fun AsposeDocumentBuilder.writeTemplateParagraph(
        text: String,
        size: Double,
        fontName: String = REGULAR_FONT,
        bold: Boolean = false,
        align: Int = AsposeParagraphAlignment.LEFT,
        spaceBefore: Double = 0.0,
        spaceAfter: Double = 0.0,
        keepWithNext: Boolean = false,
    ) {
        paragraphFormat.clearFormatting()
        paragraphFormat.alignment = align
        paragraphFormat.spaceBefore = spaceBefore
        paragraphFormat.spaceAfter = spaceAfter
        paragraphFormat.keepWithNext = keepWithNext
        font.clearFormatting()
        font.name = fontName
        font.size = size
        font.bold = bold
        writeln(text)
    }

    private fun AsposeDocumentBuilder.writeTemplateCell(
        value: String,
        emphasis: Boolean = false,
        bold: Boolean = false,
        align: Int = AsposeParagraphAlignment.LEFT,
        widthPercent: Double,
    ) {
        insertCell()
        rowFormat.allowBreakAcrossPages = false
        val width = (pageSetup.pageWidth - pageSetup.leftMargin - pageSetup.rightMargin) * widthPercent / 100.0
        cellFormat.width = width
        cellFormat.preferredWidth = AsposePreferredWidth.fromPoints(width)
        cellFormat.verticalAlignment = AsposeCellVerticalAlignment.CENTER
        paragraphFormat.clearFormatting()
        paragraphFormat.alignment = align
        font.clearFormatting()
        font.name = if (emphasis) EMPHASIS_FONT else REGULAR_FONT
        font.size = 10.0
        font.bold = bold
        write(value)
    }

    private fun formatField(
        field: CollectionPrintFieldVo,
        dicts: Map<String, Map<String, String>>,
    ): String = if (field.fileReferences.isNotEmpty()) {
        "${field.fileReferences.distinct().size} 个文件，详见附件清单"
    } else formatDict(field.dictType, field.value, dicts).orBlankValue()

    private fun formatDict(
        dictType: String?,
        value: String?,
        dicts: Map<String, Map<String, String>>,
    ): String? {
        if (value.isNullOrBlank() || dictType.isNullOrBlank()) return value
        val dict = dicts[dictType].orEmpty()
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("，") { dict[it] ?: it }
            .ifBlank { value }
    }

    private fun printAttachments(
        print: CollectionPrintVo,
        resolver: ((CollectionPrintVo) -> List<CollectionPrintAttachment>)?,
    ): List<CollectionPrintAttachment> {
        val attachments = resolver?.invoke(print).orEmpty()
        if (attachments.isNotEmpty()) return attachments
        // A direct renderer caller can omit the resolver. Never fall back to printing raw file IDs.
        return print.fields.filter { it.fileReferences.isNotEmpty() }.map { field ->
            CollectionPrintAttachment(field.label, "${field.fileReferences.distinct().size} 个关联文件", "未提供存储路径", status = "附件信息未解析")
        }
    }

    private fun appendAttachments(
        doc: AsposeDocument,
        attachments: List<CollectionPrintAttachment>,
        signature: AsposeTable?,
    ) {
        // Build a fragment and import it so the original template's sections and geometry remain intact.
        val fragment = AsposeWordsUtils.configureFonts(AsposeDocument())
        val sourceSetup = doc.lastSection.pageSetup
        fragment.firstSection.pageSetup.apply {
            pageWidth = sourceSetup.pageWidth
            pageHeight = sourceSetup.pageHeight
            leftMargin = sourceSetup.leftMargin
            rightMargin = sourceSetup.rightMargin
            topMargin = sourceSetup.topMargin
            bottomMargin = sourceSetup.bottomMargin
        }
        val builder = AsposeDocumentBuilder(fragment)
        builder.writeSectionTitle("附件清单（共 ${attachments.size} 项）")
        attachments.chunked(2).forEach { pair ->
            // One small, page-breakable row per pair avoids one unsplittable table for the entire gallery.
            if (pair.any { it.imageBytes != null }) builder.writeAttachmentImageRow(pair)
            else pair.forEach { builder.writeAttachmentDescription(it) }
        }
        val body = signature?.parentNode ?: doc.lastSection.body
        val nodes = fragment.firstSection.body.getChildNodes(AsposeNodeType.ANY, false).toList().filterNot { node ->
            node === fragment.firstSection.body.lastChild && node is AsposeParagraph && node.toString(AsposeSaveFormat.TEXT).isBlank()
        }
        if (signature != null) {
            // Carry a small final gallery row with the signature instead of leaving a signature-only page.
            nodes.filterIsInstance<AsposeTable>().lastOrNull()?.lastRow?.cells?.forEach { cell ->
                cell.paragraphs.forEach { it.paragraphFormat.keepWithNext = true }
            }
            (nodes.lastOrNull() as? AsposeParagraph)?.paragraphFormat?.keepWithNext = true
            signature.rows.forEach { row -> row.cells.forEach { cell ->
                cell.paragraphs.forEach { it.paragraphFormat.keepWithNext = true }
            } }
        }
        nodes.forEach { node ->
            val imported = doc.importNode(node, true, AsposeImportFormatMode.KEEP_SOURCE_FORMATTING)
            if (signature != null) body.insertBefore(imported, signature) else body.appendChild(imported)
        }
        // Word requires a trailing paragraph after a custom template's appended table.
        if (doc.lastSection.body.lastChild !is AsposeParagraph) doc.lastSection.body.appendChild(AsposeParagraph(doc))
    }

    private fun AsposeDocumentBuilder.writeAttachmentImageRow(attachments: List<CollectionPrintAttachment>) {
        val available = pageSetup.pageWidth - pageSetup.leftMargin - pageSetup.rightMargin
        val table = startTable()
        repeat(2) { index ->
            insertCell()
            rowFormat.clearFormatting()
            rowFormat.allowBreakAcrossPages = true
            cellFormat.clearFormatting()
            cellFormat.width = available / 2
            cellFormat.preferredWidth = AsposePreferredWidth.fromPoints(available / 2)
            cellFormat.verticalAlignment = AsposeCellVerticalAlignment.TOP
            cellFormat.leftPadding = 6.0
            cellFormat.rightPadding = 6.0
            attachments.getOrNull(index)?.let { attachment ->
                attachment.imageBytes?.let { bytes ->
                    paragraphFormat.clearFormatting()
                    paragraphFormat.alignment = AsposeParagraphAlignment.CENTER
                    paragraphFormat.keepWithNext = true
                    val shape = insertImage(bytes)
                    val originalWidth = shape.width
                    val originalHeight = shape.height
                    val scale = minOf(1.0, minOf(200.0, available / 2 - 12.0) / originalWidth, 110.0 / originalHeight)
                    shape.width = originalWidth * scale
                    shape.height = originalHeight * scale
                    shape.alternativeText = "${attachment.label} ${attachment.name}"
                    writeln()
                }
                writeAttachmentDescription(attachment)
            }
        }
        endRow()
        endTable()
        table.allowAutoFit = false
        table.preferredWidth = AsposePreferredWidth.fromPercent(100.0)
        table.alignment = AsposeTableAlignment.CENTER
        table.setBorders(AsposeLineStyle.NONE, 0.0, java.awt.Color.WHITE)
        // A real separator paragraph keeps Aspose/Word from merging adjacent gallery tables.
        // Its short, breakable line also ends the image/caption keep-with-next chain for each pair.
        writeTemplateParagraph("", 1.0, spaceAfter = 2.0)
    }

    private fun AsposeDocumentBuilder.writeAttachmentDescription(attachment: CollectionPrintAttachment) {
        val name = attachment.name.takeIf { it.isNotBlank() } ?: "文件信息不可用"
        val path = attachment.path.takeIf { it.isNotBlank() } ?: "--"
        writeTemplateParagraph("${attachment.label} · $name", 10.0, spaceBefore = 3.0, spaceAfter = 2.0, keepWithNext = true)
        writeTemplateParagraph("路径：${wrappablePath(path)}", 9.0, spaceAfter = 4.0)
        attachment.status?.takeIf { it.isNotBlank() }?.let { writeTemplateParagraph(it, 9.0, spaceAfter = 4.0) }
    }

    private fun wrappablePath(path: String): String = path.replace('\\', '/')
        .split('/').joinToString("/\u200B") { segment -> segment.chunked(20).joinToString("\u200B") }

    private fun String?.orBlankValue(): String = this?.takeIf { it.isNotBlank() } ?: "--"
}
