package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionDescriptionPolicy
import io.hizhengfu.cloud.collection.domain.CollectionRegistrationDetailsVo
import com.aspose.words.NodeType
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionImportRowVo
import io.hizhengfu.cloud.collection.domain.CollectionTrackingEventPreviewVo
import io.hizhengfu.cloud.collection.domain.CollectionWordImportPreviewVo
import io.hizhengfu.cloud.collection.domain.CollectionWordMovementPreviewVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionTrackingEventType
import io.hizhengfu.cloud.collection.domain.EnumCollectionWordDocumentType
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.common.utils.word.AsposeWordsUtils
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 藏品登记表/动态跟踪表结构解析器。
 *
 * 结构识别不依赖文件名和物理页码；只有命中完整内容指纹的 Word 才会产生可提交预览。
 */
@Component
class CollectionWordDocumentParser {
    fun parse(bytes: ByteArray, fileName: String): CollectionWordImportPreviewVo {
        val hash = bytes.sha256()
        val base = CollectionWordImportPreviewVo(
            valid = false,
            sourceFileName = fileName,
            sourceHash = hash,
        )
        validateContainer(bytes, fileName)?.let { return base.withError("file", it) }

        val document = try {
            AsposeWordsUtils.instance(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            return base.withError("file", "文件不是可解析的 Word 文档，或文档已经损坏")
        }

        val paragraphs = mutableListOf<String>()
        AsposeWordsUtils.parsePageDocument(document) { nodes, _ ->
            nodes.filter { it.type == NodeType.PARAGRAPH }
                .mapTo(paragraphs) { cleanText(it.content.toString()) }
        }
        val tables = AsposeWordsUtils.parseTables(document)
        val hasRegistrationTitle = paragraphs.any { compact(it) == REGISTRATION_TITLE }
        val hasTrackingTitle = paragraphs.any { compact(it) == TRACKING_TITLE }

        if (hasRegistrationTitle && hasTrackingTitle) {
            return base.withError("structure", "同一文件同时出现两种导入表标题，无法确定文档类型")
        }
        return when {
            hasRegistrationTitle -> parseRegistration(base, tables)
            hasTrackingTitle -> parseTracking(base, paragraphs, tables)
            else -> base.withError(
                "structure",
                "不支持该 Word：仅允许“文物藏品登记表”或“文物藏品动态跟踪记录汇总表”",
            )
        }
    }

    private fun parseRegistration(
        base: CollectionWordImportPreviewVo,
        tables: List<List<List<String>>>,
    ): CollectionWordImportPreviewVo {
        val fields = extractFields(tables).toMutableMap()
        // 原件标题按原文识别；导入后的日期字段统一使用取得日期。
        if (!fields.containsKey("取得日期")) fields["入藏日期"]?.let { fields["取得日期"] = it }
        val missing = REGISTRATION_REQUIRED.filterNot(fields::containsKey)
        if (missing.isNotEmpty()) {
            return base.withError("structure", "登记表结构不完整，缺少：${missing.joinToString("、")}")
        }
        val name = fields["名称"].cleanValue()
        val regNo = fields["总登记号"].cleanValue()
        if (name == null || regNo == null) {
            return base.withError("structure", "登记表的名称和总登记号不能为空")
        }

        val dimensions = fields["尺寸"].orEmpty()
        val weightText = fields["质量"].orEmpty()
        val volumeText = fields["容积"].orEmpty()
        val acquireText = fields["取得日期"].orEmpty()
        val acquireYear = YEAR.find(acquireText)?.value
        val feature = joinNonBlank(
            fields["形态特征"],
            fields["工艺技法"]?.let { "工艺技法：$it" },
        )
        val sourceDesc = joinNonBlank(
            fields["来源单位或个人"]?.let { "来源单位或个人：$it" },
            fields["搜集经过"]?.let { "搜集经过：$it" },
            fields["流传经历"]?.let { "流传经历：$it" },
            fields["出土情况"]?.let { "出土情况：$it" },
        )
        val quantity = fields["传统数量"]?.firstInteger()
        val actualQuantity = fields["实际数量"]?.firstInteger()
        val quantityUnit = fields["传统数量"]?.unitAfterNumber() ?: fields["计量单位"].cleanValue()
        val actualUnit = fields["实际数量"]?.unitAfterNumber() ?: fields["实际数量单位"].cleanValue()
        val row = CollectionImportRowVo(
            rowNo = 2,
            totalRegNo = regNo,
            name = name,
            originalName = fields["曾用名"].cleanValue(),
            categoryId = fields["类别"].cleanValue(), // 服务层按当前馆分类名称解析为 ID
            eraMeaning = fields["年代类型"].cleanValue()?.takeIf { it in CollectionDescriptionPolicy.eraMeanings.values },
            eraNotation = (fields["年代表示方式"] ?: fields["年代类型"]).cleanValue()?.takeIf { it in CollectionDescriptionPolicy.eraNotations.values },
            era = joinNonBlank(fields["年代"], fields["年代研究信息"]),
            texture = fields["质地"].cleanValue(),
            level = fields["级别"].cleanValue(),
            quantity = quantity,
            unit = quantityUnit,
            actualQuantity = actualQuantity,
            actualQuantityUnit = actualUnit,
            dimensionUnit = dimensions.dimensionUnit(),
            source = fields["来源方式"].cleanValue(),
            sourceDesc = sourceDesc,
            acquireDateRange = acquireYear ?: acquireText.cleanValue(),
            acquireYear = acquireYear,
            completeness = fields["完残程度"].cleanValue(),
            completeStatus = fields["完残状况"].cleanValue(),
            height = dimensions.numberAfter("高"),
            mouth = dimensions.numberAfter("口径"),
            bottom = dimensions.numberAfter("底径"),
            belly = dimensions.numberAfter("腹径"),
            length = dimensions.numberAfter("通长"),
            width = dimensions.numberAfter("通宽"),
            diameter = dimensions.numberAfter("直径"),
            weight = weightText.firstDecimal(),
            weightUnit = weightText.unitAfterNumber(),
            region = fields["地域"].cleanValue(),
            volume = volumeText.firstDecimal(),
            volumeUnit = volumeText.unitAfterNumber(),
            color = fields["颜色/光泽"].cleanValue(),
            sourceNo = fields["来源号"].cleanValue(),
            culture = fields["人文类型"].cleanValue(),
            inscription = joinNonBlank(fields["题识情况"], fields["文字种类/字体/字迹颜色"]),
            feature = feature,
            remark = fields["备注"].cleanValue(),
        )
        val catalog = CollectionCatalogContentVo(
            totalName = name,
            formalName = name,
            originalName = row.originalName,
            comprehensiveCategory = fields["类别"].cleanValue(),
            era = row.era,
            registrationDetails = CollectionRegistrationDetailsVo(
                eraMeaning = CollectionDescriptionPolicy.eraMeanings.entries.find { it.value == row.eraMeaning }?.key,
                eraNotation = CollectionDescriptionPolicy.eraNotations.entries.find { it.value == row.eraNotation }?.key,
            ),
            region = row.region,
            culture = row.culture,
            texture = row.texture,
            color = row.color,
            level = parseLevel(row.level),
            appraisalOpinion = fields["鉴定情况"].cleanValue(),
            quantity = quantity,
            unit = quantityUnit,
            actualQuantity = actualQuantity,
            actualQuantityUnit = actualUnit,
            dimensions = DimensionVo(
                height = row.height,
                mouth = row.mouth,
                bottom = row.bottom,
                belly = row.belly,
                length = row.length,
                width = row.width,
                diameter = row.diameter,
            ),
            dimensionUnit = dimensions.dimensionUnit(),
            weight = row.weight,
            weightUnit = row.weightUnit,
            volume = row.volume,
            volumeUnit = row.volumeUnit,
            completeness = parseCompleteness(row.completeness),
            completeStatus = row.completeStatus,
            source = parseSource(row.source),
            sourceNo = row.sourceNo,
            sourceDesc = row.sourceDesc,
            acquireDateRange = row.acquireDateRange,
            acquireYear = row.acquireYear,
            feature = row.feature,
            inscription = row.inscription,
            bibliography = fields["文献及研究论著目录"].cleanValue(),
            researchNote = fields["年 代研究信息"].cleanValue() ?: fields["年代研究信息"].cleanValue(),
            attachmentDescription = fields["附属物情况"].cleanValue(),
            remark = row.remark,
        )
        val events = registrationEvents(fields)
        return base.copy(
            valid = true,
            documentType = EnumCollectionWordDocumentType.REGISTRATION,
            fingerprint = REGISTRATION_FINGERPRINT,
            totalRegNo = regNo,
            collectionName = name,
            registration = row,
            catalog = catalog,
            events = events,
        )
    }

    private fun parseTracking(
        base: CollectionWordImportPreviewVo,
        paragraphs: List<String>,
        tables: List<List<List<String>>>,
    ): CollectionWordImportPreviewVo {
        val summary = tables.firstOrNull { table ->
            val labels = table.flatten().map(::compact).toSet()
            "藏品名称" in labels && "总登记号" in labels
        } ?: return base.withError("structure", "动态跟踪记录汇总表缺少藏品名称/总登记号汇总表")
        val fields = extractFields(listOf(summary))
        val missing = TRACKING_REQUIRED.filterNot { key ->
            fields.containsKey(key) || (key == "藏品退出馆藏记录" && fields.containsKey("藏品注销记录"))
        }
        if (missing.isNotEmpty()) {
            return base.withError("structure", "动态跟踪记录汇总表结构不完整，缺少：${missing.joinToString("、")}")
        }
        val name = fields["藏品名称"].cleanValue()
        val regNo = fields["总登记号"].cleanValue()
        if (name == null || regNo == null) {
            return base.withError("structure", "动态跟踪记录汇总表的藏品名称和总登记号不能为空")
        }
        val detailTable = tables.firstOrNull { table ->
            table.firstOrNull()?.map(::compact) == MOVEMENT_HEADER
        }
        val extractContent = fields["提取退还记录"].cleanValue()
        if (extractContent != null && detailTable == null) {
            return base.withError("structure", "提取退还记录有内容，但附页缺少“时间/出库/入库/事由/经手人/备注”明细表")
        }
        val movements = detailTable?.drop(1)?.mapIndexedNotNull { index, cells ->
            parseMovement(index + 2, cells)
        }.orEmpty()
        if (detailTable != null) {
            val invalidRows = detailTable.drop(1).mapIndexedNotNull { index, row ->
                val nonBlank = row.any { it.isNotBlank() }
                if (nonBlank && parseMovement(index + 2, row) == null) index + 2 else null
            }
            if (invalidRows.isNotEmpty()) {
                return base.withError("movements", "附页第 ${invalidRows.joinToString("、")} 行无法识别：时间必填，且出库/入库只能填写一项")
            }
        }
        val summaryTimes = extractContent?.let(::extractDateTimes).orEmpty()
        if (summaryTimes.isNotEmpty() && movements.isNotEmpty()) {
            val detailTimes = movements.map { it.operatedAt }
            if (summaryTimes != detailTimes) {
                return base.withError("movements", "提取退还汇总时间与附页出入库明细时间不一致")
            }
        }
        val events = trackingEvents(fields)
        if (events.isEmpty() && movements.isEmpty()) {
            return base.withError("structure", "动态跟踪记录汇总表没有任何可导入的管理记录")
        }
        val yearRange = paragraphs.asSequence().mapNotNull(::parseYearRange).firstOrNull()
        return base.copy(
            valid = true,
            documentType = EnumCollectionWordDocumentType.DYNAMIC_TRACKING,
            fingerprint = TRACKING_FINGERPRINT,
            totalRegNo = regNo,
            collectionName = name,
            startYear = yearRange?.first,
            endYear = yearRange?.second,
            events = events,
            movements = movements,
        )
    }

    private fun extractFields(tables: List<List<List<String>>>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        tables.flatten().forEach { row ->
            var index = 0
            while (index < row.size) {
                val key = compact(row[index])
                if (key in KNOWN_LABELS) {
                    val value = row.getOrNull(index + 1)?.let(::cleanText).orEmpty()
                    result.putIfAbsent(key, value)
                    index += 2
                } else {
                    index++
                }
            }
        }
        return result
    }

    private fun parseMovement(rowNo: Int, cells: List<String>): CollectionWordMovementPreviewVo? {
        if (cells.all { it.isBlank() }) return null
        if (cells.size < MOVEMENT_HEADER.size) return null
        val operatedAt = parseDateTime(cells[0]) ?: return null
        val out = cells[1].cleanValue()
        val inside = cells[2].cleanValue()
        if ((out == null) == (inside == null)) return null
        val basis = cells[3].cleanValue()
        return CollectionWordMovementPreviewVo(
            rowNo = rowNo,
            operatedAt = operatedAt,
            type = if (out != null) EnumMoveType.OUT else EnumMoveType.IN,
            reason = inferReason(basis),
            fromWarehouseName = out,
            toWarehouseName = inside,
            basis = basis,
            operator = cells[4].cleanValue(),
            remark = cells[5].cleanValue(),
        )
    }

    private fun trackingEvents(fields: Map<String, String>): List<CollectionTrackingEventPreviewVo> =
        TRACKING_EVENT_FIELDS.mapIndexedNotNull { index, (label, defaultType) ->
            val content = fields[label].cleanValue() ?: return@mapIndexedNotNull null
            val type = if (label == "保护修复记录" && DIGITIZATION_KEYWORDS.any(content::contains)) {
                EnumCollectionTrackingEventType.DIGITIZATION
            } else defaultType
            val times = extractDateTimes(content)
            CollectionTrackingEventPreviewVo(
                eventType = type,
                title = label,
                content = content,
                occurredAt = times.firstOrNull(),
                endedAt = times.drop(1).firstOrNull(),
                sort = index,
            )
        }

    private fun registrationEvents(fields: Map<String, String>): List<CollectionTrackingEventPreviewVo> {
        val sections = listOf(
            "当前状况" to fields["当前状况"],
            "保存与保护" to joinNonBlank(fields["保存条件"], fields["损坏原因"], fields["保护优先等级"], fields["拟采取的保护措施"]),
            "历次保护记录" to fields["历次保护记录"],
            "主要利用情况记录" to fields["主要利用情况记录"],
            "其他声像资料目录" to fields["其他声像资料目录"],
        )
        return sections.mapIndexedNotNull { index, (title, raw) ->
            raw.cleanValue()?.let {
                CollectionTrackingEventPreviewVo(
                    eventType = EnumCollectionTrackingEventType.REGISTRATION_DETAIL,
                    title = title,
                    content = it,
                    sort = index,
                )
            }
        }
    }

    private fun validateContainer(bytes: ByteArray, fileName: String): String? {
        if (bytes.isEmpty()) return "文件不能为空"
        if (bytes.size > MAX_FILE_SIZE) return "Word 文件不能超过 20MB"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("doc", "docx")) return "仅支持 .doc 或 .docx 文件"
        val ole = bytes.startsWith(OLE_MAGIC)
        val zip = bytes.startsWith(ZIP_MAGIC)
        return when (ext) {
            "doc" -> if (!ole) "文件扩展名为 .doc，但内容不是 Word 97-2003 文档" else null
            "docx" -> if (!zip) "文件扩展名为 .docx，但内容不是 Office Open XML 文档" else null
            else -> "仅支持 .doc 或 .docx 文件"
        }
    }

    private fun CollectionWordImportPreviewVo.withError(field: String, message: String) = copy(
        valid = false,
        errors = errors + CollectionImportErrorVo(0, field, message),
    )

    private fun inferReason(text: String?): EnumMoveReason? = when {
        text == null -> null
        DIGITIZATION_KEYWORDS.any(text::contains) -> EnumMoveReason.DIGITIZE
        listOf("修复", "保护处理").any(text::contains) -> EnumMoveReason.RESTORE
        listOf("展览", "展陈").any(text::contains) -> EnumMoveReason.EXHIBIT
        listOf("研究", "科研").any(text::contains) -> EnumMoveReason.RESEARCH
        listOf("盘点", "清点").any(text::contains) -> EnumMoveReason.INVENTORY
        listOf("调拨", "移交").any(text::contains) -> EnumMoveReason.TRANSFER
        listOf("借展", "外借").any(text::contains) -> EnumMoveReason.LOAN
        else -> null
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        val match = DATE_TIME.find(cleanText(value)) ?: return null
        return runCatching {
            LocalDateTime.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
                match.groupValues[5].toInt(),
            )
        }.getOrNull()
    }

    private fun extractDateTimes(value: String): List<LocalDateTime> =
        DATE_TIME.findAll(cleanText(value)).mapNotNull { parseDateTime(it.value) }.toList()

    private fun parseYearRange(value: String): Pair<Int, Int>? {
        val match = YEAR_RANGE.find(value) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private fun parseLevel(value: String?): EnumRelicLevel? = when (value?.let(::compact)) {
        "一级" -> EnumRelicLevel.FIRST
        "二级" -> EnumRelicLevel.SECOND
        "三级" -> EnumRelicLevel.THIRD
        "一般文物" -> EnumRelicLevel.GENERAL
        "未定级" -> EnumRelicLevel.UNRATED
        else -> null
    }

    private fun parseCompleteness(value: String?): EnumCompleteness? = when (value?.let(::compact)) {
        "完整" -> EnumCompleteness.COMPLETE
        "基本完整" -> EnumCompleteness.BASIC
        "残缺" -> EnumCompleteness.DAMAGED
        "严重残缺" -> EnumCompleteness.SEVERE
        else -> null
    }

    private fun parseSource(value: String?): EnumSource? = when (value?.let(::compact)) {
        "考古发掘" -> EnumSource.EXCAVATION
        "采集" -> EnumSource.COLLECT
        "收购" -> EnumSource.PURCHASE
        "调拨" -> EnumSource.ALLOCATE
        "拨交" -> EnumSource.TRANSFER
        "交换" -> EnumSource.EXCHANGE
        "捐赠" -> EnumSource.DONATE
        "移交" -> EnumSource.HANDOVER
        "拣选" -> EnumSource.PICK
        "旧藏" -> EnumSource.OLD
        "复制" -> EnumSource.REPRO
        else -> null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private fun String?.cleanValue(): String? = this?.let(::cleanText)?.takeIf(String::isNotBlank)
    private fun String.firstInteger(): Int? = INTEGER.find(this)?.value?.toIntOrNull()
    private fun String.firstDecimal(): String? = DECIMAL.find(this)?.value
    private fun String.unitAfterNumber(): String? = NUMBER_WITH_UNIT.find(this)?.groupValues?.get(2)?.takeIf(String::isNotBlank)
    private fun String.numberAfter(label: String): String? =
        Regex("${Regex.escape(label)}\\s*([0-9]+(?:\\.[0-9]+)?)").find(this)?.groupValues?.get(1)
    private fun String.dimensionUnit(): String? = Regex("(?i)(mm|cm|m)(?:\\b|[^a-z])").find(this)?.groupValues?.get(1)

    private fun joinNonBlank(vararg values: String?): String? = values.mapNotNull { it.cleanValue() }
        .takeIf(List<String>::isNotEmpty)?.joinToString("；")

    private fun cleanText(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u200B'.toString(), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun compact(value: String): String = cleanText(value).replace(" ", "")

    private companion object {
        const val MAX_FILE_SIZE = 20 * 1024 * 1024
        const val REGISTRATION_TITLE = "文物藏品登记表"
        const val TRACKING_TITLE = "文物藏品动态跟踪记录汇总表"
        const val REGISTRATION_FINGERPRINT = "collection-registration-v1"
        const val TRACKING_FINGERPRINT = "collection-dynamic-tracking-v1"

        val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val YEAR = Regex("(?<!\\d)\\d{4}(?!\\d)")
        val INTEGER = Regex("\\d+")
        val DECIMAL = Regex("\\d+(?:\\.\\d+)?")
        val NUMBER_WITH_UNIT = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z\\u4e00-\\u9fa5³3]+)")
        val DATE_TIME = Regex("(\\d{4})[年./-](\\d{1,2})[月./-](\\d{1,2})日?\\s*(\\d{1,2})[:：](\\d{1,2})")
        val YEAR_RANGE = Regex("(\\d{4})\\s*年?\\s*至\\s*(\\d{4})\\s*年?")
        val MOVEMENT_HEADER = listOf("时间", "出库", "入库", "事由", "经手人", "备注")
        val DIGITIZATION_KEYWORDS = listOf("数字化", "高清", "拍照", "扫描", "三维")

        val REGISTRATION_REQUIRED = setOf(
            "名称", "曾用名", "总登记号", "类别", "级别", "年代类型", "年代", "质地", "尺寸",
            "完残程度", "完残状况", "来源方式", "取得日期", "当前状况", "保存条件", "保护优先等级",
        )
        val TRACKING_REQUIRED = setOf(
            "藏品名称", "总登记号", "提取退还记录", "观察监测记录", "保护修复记录", "鉴定变更记录",
            "研究利用情况", "事故情况记录", "藏品退出馆藏记录", "备注",
        )
        val TRACKING_EVENT_FIELDS = listOf(
            "提取退还记录" to EnumCollectionTrackingEventType.EXTRACT_RETURN,
            "观察监测记录" to EnumCollectionTrackingEventType.OBSERVATION,
            "保护修复记录" to EnumCollectionTrackingEventType.RESTORATION,
            "鉴定变更记录" to EnumCollectionTrackingEventType.APPRAISAL_CHANGE,
            "研究利用情况" to EnumCollectionTrackingEventType.RESEARCH_USE,
            "事故情况记录" to EnumCollectionTrackingEventType.INCIDENT,
            "藏品退出馆藏记录" to EnumCollectionTrackingEventType.DEREGISTRATION,
            "藏品注销记录" to EnumCollectionTrackingEventType.DEREGISTRATION,
            "备注" to EnumCollectionTrackingEventType.NOTE,
        )
        val KNOWN_LABELS = REGISTRATION_REQUIRED + TRACKING_REQUIRED + TRACKING_EVENT_FIELDS.map { it.first } + setOf(
            "入藏日期", "年代表示方式", "入馆登记号", "分类账号", "入藏库房", "年代研究信息", "年 代研究信息", "地域类型", "地域", "人文类型",
            "人物传略", "功能类别", "传统数量", "计量单位", "实际数量", "实际数量单位", "容积", "质量", "形态特征", "工艺技法", "独特标记",
            "颜色/光泽", "文字种类/字体/字迹颜色", "题识情况", "附属物情况", "来源号", "来源单位或个人",
            "搜集经过", "流传经历", "出土情况", "鉴定情况", "损坏原因", "拟采取的保护措施", "历次保护记录",
            "主要利用情况记录", "文献及研究论著目录", "其他声像资料目录", "填表日期",
        )
    }
}
