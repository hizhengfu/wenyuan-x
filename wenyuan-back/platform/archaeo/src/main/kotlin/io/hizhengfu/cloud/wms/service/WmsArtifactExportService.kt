package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.file.service.FileSystemStorageService
import io.hizhengfu.cloud.wms.dao.WmsArtifactDao
import io.hizhengfu.cloud.wms.dao.WmsArtifactExtends
import io.hizhengfu.cloud.wms.dao.WmsArtifactStockDao
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.OutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 文物卡片 Word 导出 — 服务端权威生成
 *
 * 设计要点：
 * 1. 不依赖外部 docx 模板，使用 Apache POI 直接生成（与 [WmsDocumentService] 一致），
 *    模板差异通过 [EnumCardTemplate] 切换；后续需要"上传自定义模板"时再切到 Aspose Words 模板。
 * 2. 多文物导出 — 每件文物一页，最后导出一个 docx；前端只下载一次。
 * 3. 图片字段：封面 image / 多角度照片（contentId=artifactId）/ 线图（contentId=lineDiagram_$id）
 *    / 平剖面图（contentId=profile_$id），按 [EnumCardTemplate] 决定是否插入。
 *
 * 文件名规则：单件用文物名_编号；多件用 "文物卡片_批量_N件"。
 */
@Component
@Transactional(readOnly = true)
class WmsArtifactExportService(
    private val fileService: FileService,
    private val fileSystemStorageService: FileSystemStorageService,
) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    enum class EnumCardTemplate(val description: String, val includeImages: Boolean, val includeLineDiagram: Boolean) {
        STANDARD("标准卡片（文字 + 封面）", includeImages = true, includeLineDiagram = false),
        WITH_LINE("含线图卡片（文字 + 封面 + 线图）", includeImages = true, includeLineDiagram = true),
        TEXT_ONLY("纯文字卡片", includeImages = false, includeLineDiagram = false),
        FULL("全字段卡片（文字 + 多角度 + 线图 + 平剖面）", includeImages = true, includeLineDiagram = true),
    }

    /**
     * 字段勾选项 —— 任一项为 null 时回落到 [EnumCardTemplate] 的预设；非 null 时覆盖。
     * 让用户可单独控制「形制」与各类图片是否导出。
     */
    data class CardOptions(
        val includeShape: Boolean? = null,
        val includeCover: Boolean? = null,
        val includeAngles: Boolean? = null,
        val includeLine: Boolean? = null,
        val includeProfile: Boolean? = null,
    )

    fun export(ids: List<String>, template: EnumCardTemplate, options: CardOptions, out: OutputStream): String {
        if (ids.isEmpty()) throw BadRequestException("请至少选择一件文物")

        XWPFDocument().use { doc ->
            ids.forEachIndexed { idx, id ->
                if (idx > 0) {
                    // 分页
                    doc.createParagraph().createRun().addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE)
                }
                renderOne(doc, id, template, options)
            }
            doc.write(out)
        }

        return if (ids.size == 1) {
            val a = WmsArtifactDao.findById(ids[0])
            val safe = "${a?.name ?: ids[0]}_${a?.code ?: ""}".trim('_').replace(Regex("[\\\\/:*?\"<>|]"), "_")
            "文物卡片_$safe.docx"
        } else {
            "文物卡片_批量_${ids.size}件.docx"
        }
    }

    private fun renderOne(doc: XWPFDocument, id: String, template: EnumCardTemplate, options: CardOptions) {
        val a = WmsArtifactDao.findById(id) ?: throw BadRequestException("文物不存在: $id")
        // 字段勾选优先，其次回落到模板预设
        val incShape = options.includeShape ?: true
        val incCover = options.includeCover ?: template.includeImages
        val incAngles = options.includeAngles ?: (template == EnumCardTemplate.FULL)
        val incLine = options.includeLine ?: template.includeLineDiagram
        val incProfile = options.includeProfile ?: (template == EnumCardTemplate.FULL)
        val stock = WmsArtifactStockDao.findById(id)
        val ext = WmsArtifactExtends.selectAll().where { WmsArtifactExtends.id.eq(id) }.singleOrNull()

        // 标题：文物名称（编号）
        doc.createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            spacingAfter = 200
            createRun().apply {
                isBold = true; fontSize = 18
                setText("${a.name}${a.code?.let { "（$it）" } ?: ""}")
            }
        }

        // 基础信息表（4 列：标签 / 值 / 标签 / 值）
        val rows = mutableListOf<Pair<String, String?>>(
            "材质" to a.material,
            "数量" to "${a.quantity}${a.quantityUnit}",
            "出土日期" to a.unearthDate?.format(dateFmt),
            "出土单位" to (a.projectCellId ?: ""),
            "出土坐标" to a.coordinate,
            "记录人" to a.recorderBy,
            "保存状况" to (stock?.saveStatus ?: ""),
            "存放位置" to (stock?.locationId ?: ""),
        )
        ext?.let {
            rows += listOf(
                "时代" to it[WmsArtifactExtends.timePeriod],
                "文化属性" to it[WmsArtifactExtends.culturalAttribute],
                "用途" to it[WmsArtifactExtends.purpose],
                "器型" to it[WmsArtifactExtends.artifactType],
                "尺寸" to it[WmsArtifactExtends.dimensions]?.let { d -> "${d}cm" },
                "重量" to it[WmsArtifactExtends.weight]?.let { w -> "${w}kg" },
                "纹饰" to it[WmsArtifactExtends.decoration],
                "制作工艺" to it[WmsArtifactExtends.craftingTechnique],
                "型 / 式" to listOfNotNull(it[WmsArtifactExtends.artifactStyle], it[WmsArtifactExtends.artifactStage]).joinToString(" / ").ifBlank { null },
                "陶质" to it[WmsArtifactExtends.potteryQuality],
                "陶色" to it[WmsArtifactExtends.potteryColor],
                "窑口" to it[WmsArtifactExtends.kilnSite],
                "窑系" to it[WmsArtifactExtends.kilnSystem],
                "釉色" to it[WmsArtifactExtends.glazeColor],
                "款识" to it[WmsArtifactExtends.markText],
                "铭文" to it[WmsArtifactExtends.inscription],
            )
        }

        val nonEmpty = rows.filter { !it.second.isNullOrBlank() }
        val tableRows = (nonEmpty.size + 1) / 2
        if (tableRows > 0) {
            val table = doc.createTable(tableRows, 4)
            table.setWidth("100%")
            nonEmpty.forEachIndexed { i, (k, v) ->
                val r = i / 2
                val c = (i % 2) * 2
                val row = table.getRow(r)
                row.getCell(c).also { cell ->
                    cell.text = ""
                    val p = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
                    p.createRun().apply { isBold = true; fontSize = 11; setText(k) }
                }
                row.getCell(c + 1).also { cell ->
                    cell.text = ""
                    val p = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
                    p.createRun().apply { fontSize = 11; setText(v ?: "") }
                }
            }
        }

        // 形制（长文本，单独一段）
        ext?.let {
            val shape = it[WmsArtifactExtends.shape]
            if (incShape && !shape.isNullOrBlank()) {
                doc.createParagraph().apply {
                    spacingBefore = 200
                    createRun().apply { isBold = true; fontSize = 11; setText("形制：") }
                    createRun().apply { fontSize = 11; setText(shape) }
                }
            }
        }

        // 图片：封面
        if (incCover && !a.image.isNullOrBlank()) {
            insertImage(doc, a.image!!, "封面", maxWidthCm = 8.0)
        }
        // 多角度照片
        if (incAngles) {
            insertImagesByContent(doc, id, "多角度照片")
        }
        // 线图
        if (incLine) {
            insertImagesByContent(doc, "lineDiagram_$id", "线图")
        }
        // 平剖面图
        if (incProfile) {
            insertImagesByContent(doc, "profile_$id", "平剖面图")
        }

        // 备注
        if (!a.remarks.isNullOrBlank()) {
            doc.createParagraph().apply {
                spacingBefore = 200
                createRun().apply { isBold = true; fontSize = 11; setText("备注：") }
                createRun().apply { fontSize = 11; setText(a.remarks) }
            }
        }
        // 页脚
        doc.createParagraph().apply {
            spacingBefore = 200
            alignment = ParagraphAlignment.RIGHT
            createRun().apply {
                fontSize = 9
                setText("打印时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            }
        }
    }

    private fun insertImagesByContent(doc: XWPFDocument, contentId: String, label: String) {
        val files = runCatching { fileService.findAllByContent(contentId) }.getOrDefault(emptyList())
        if (files.isEmpty()) return
        doc.createParagraph().apply {
            spacingBefore = 200
            createRun().apply { isBold = true; fontSize = 11; setText("$label：") }
        }
        files.forEach { f -> insertImage(doc, f.path, label, maxWidthCm = 6.0) }
    }

    /**
     * @param dbPath FileDao.path（如 /image/20251001/x.png）
     */
    private fun insertImage(doc: XWPFDocument, dbPath: String, label: String, maxWidthCm: Double) {
        val realPath = fileSystemStorageService.resolvePath(dbPath)
        if (!Files.exists(realPath)) return
        val ext = realPath.fileName.toString().substringAfterLast('.', "png").lowercase()
        val picType = when (ext) {
            "png" -> XWPFDocument.PICTURE_TYPE_PNG
            "jpg", "jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG
            "gif" -> XWPFDocument.PICTURE_TYPE_GIF
            "bmp" -> XWPFDocument.PICTURE_TYPE_BMP
            "tiff", "tif" -> XWPFDocument.PICTURE_TYPE_TIFF
            else -> XWPFDocument.PICTURE_TYPE_PNG
        }
        val p = doc.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        Files.newInputStream(realPath).use { input ->
            p.createRun().addPicture(
                input,
                picType,
                realPath.fileName.toString(),
                Units.toEMU(maxWidthCm * 28.3464567), // cm → pt → EMU
                Units.toEMU(maxWidthCm * 28.3464567 * 0.75),
            )
        }
    }
}
