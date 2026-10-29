package io.hizhengfu.cloud.collection.service

import com.aspose.words.Document
import com.aspose.words.DocumentBuilder
import com.aspose.words.SaveFormat
import io.hizhengfu.cloud.collection.domain.EnumCollectionTrackingEventType
import io.hizhengfu.cloud.collection.domain.EnumCollectionWordDocumentType
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class CollectionWordDocumentParserTest {
    private val parser = CollectionWordDocumentParser()

    @Test
    fun `recognizes configured real reference documents`() {
        val registrationPath = System.getenv("COLLECTION_REGISTRATION_WORD")
        val trackingPath = System.getenv("COLLECTION_TRACKING_WORD")
        assumeTrue(!registrationPath.isNullOrBlank() && !trackingPath.isNullOrBlank())

        val registration = File(registrationPath!!)
        val tracking = File(trackingPath!!)
        val registrationResult = parser.parse(registration.readBytes(), registration.name)
        val trackingResult = parser.parse(tracking.readBytes(), tracking.name)

        assertTrue(registrationResult.valid) { registrationResult.errors.toString() }
        assertTrue(trackingResult.valid) { trackingResult.errors.toString() }
        assertEquals(EnumCollectionWordDocumentType.REGISTRATION, registrationResult.documentType)
        assertEquals(EnumCollectionWordDocumentType.DYNAMIC_TRACKING, trackingResult.documentType)
        assertEquals("0696.3", registrationResult.totalRegNo)
        assertEquals("0696.3", trackingResult.totalRegNo)
        assertEquals(2, trackingResult.movements.size)
    }

    @Test
    fun `recognizes registration document only when full structural fingerprint exists`() {
        val result = parser.parse(registrationDocument(), "登记表.docx")

        assertTrue(result.valid) { result.errors.toString() }
        assertEquals(EnumCollectionWordDocumentType.REGISTRATION, result.documentType)
        assertEquals("0696.3", result.registration?.totalRegNo)
        assertEquals("清粉彩过枝九桃纹盘", result.registration?.name)
        assertEquals("46.7", result.registration?.mouth)
        assertEquals("3.700", result.registration?.weight)
    }

    @Test
    fun `rejects a lookalike document that only copies registration title`() {
        val bytes = word {
            writeln("文物藏品登记表")
            table(listOf(listOf("名称", "伪造文档"), listOf("总登记号", "X-1")))
        }

        val result = parser.parse(bytes, "伪装.docx")

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.field == "structure" && it.message.contains("结构不完整") })
    }

    @Test
    fun `rejects a Word file whose extension does not match its container`() {
        val result = parser.parse(registrationDocument(), "把docx改名为doc.doc")

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.field == "file" && it.message.contains("扩展名") })
    }

    @Test
    fun `recognizes dynamic tracking summary and movement detail`() {
        val result = parser.parse(trackingDocument(), "动态表.docx")

        assertTrue(result.valid) { result.errors.toString() }
        assertEquals(EnumCollectionWordDocumentType.DYNAMIC_TRACKING, result.documentType)
        assertEquals(2, result.movements.size)
        assertEquals(EnumMoveType.OUT, result.movements.first().type)
        assertEquals("2号库", result.movements.first().fromWarehouseName)
        assertTrue(result.events.any { it.eventType == EnumCollectionTrackingEventType.DIGITIZATION })
    }

    @Test
    fun `rejects tracking document when summary times conflict with detail`() {
        val bytes = trackingDocument(returnTime = "2026年8月26日 13：00")

        val result = parser.parse(bytes, "动态表.docx")

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.field == "movements" && it.message.contains("时间不一致") })
    }

    private fun registrationDocument(): ByteArray = word {
        writeln("文物藏品登记表")
        table(
            listOf(
                listOf("名 称", "清粉彩过枝九桃纹盘"),
                listOf("曾 用 名", "光绪白地桃花小盂"),
                listOf("总登记号", "0696.3", "入藏日期", "1956年"),
                listOf("类 别", "瓷器", "级 别", "二级"),
                listOf("年代类型", "清(1616~1911)", "年 代", "清代"),
                listOf("质地", "瓷"),
                listOf("尺寸", "口径46.7，底径26.5，高7.5(cm)cm"),
                listOf("传统数量", "1件", "实际数量", "1件"),
                listOf("质量", "3.700kg"),
                listOf("完残程度", "残缺"),
                listOf("完残状况", "盘面小局部磨划伤"),
                listOf("来源方式", "拨交"),
                listOf("当前状况", "基本稳定"),
                listOf("保存条件", "温度15℃，湿度55%。"),
                listOf("保护优先等级", "日常维护"),
            ),
        )
    }

    @Test
    fun `tracking document accepts either current or legacy exit label without requiring both`() {
        listOf("藏品退出馆藏记录", "藏品注销记录").forEach { label ->
            val result = parser.parse(trackingDocument(exitLabel = label), "动态表.docx")
            assertTrue(result.valid) { result.errors.toString() }
            assertEquals(2, result.movements.size)
        }
    }

    private fun trackingDocument(returnTime: String = "2026年8月26日 12：00", exitLabel: String = "藏品退出馆藏记录"): ByteArray = word {
        writeln("文物藏品动态跟踪记录汇总表")
        writeln("2026 年至 2027 年")
        table(
            listOf(
                listOf("藏品名称", "清粉彩过枝九桃纹盘", "总登记号", "0696.3"),
                listOf("提取退还记录", "提取:2026年8月26日 11：12退还: $returnTime"),
                listOf("观察监测记录", ""),
                listOf("保护修复记录", "数字化拍摄高清拍照；三维扫描"),
                listOf("鉴定变更记录", ""),
                listOf("研究利用情况", ""),
                listOf("事故情况记录", ""),
                listOf(exitLabel, ""),
                listOf("备注", ""),
            ),
        )
        writeln("附页")
        table(
            listOf(
                listOf("时间", "出库", "入库", "事由", "经手人", "备注"),
                listOf("2026.8.26 11：12", "2号库", "", "文物高清图像数字化采集工作", "饶鸿傲、邹卓伶", ""),
                listOf("2026.8.26 12：00", "", "2号库", "文物高清图像数字化采集工作", "饶鸿傲、邹卓伶", ""),
            ),
        )
    }

    private fun word(block: BuilderDsl.() -> Unit): ByteArray {
        val document = Document()
        BuilderDsl(DocumentBuilder(document)).block()
        return ByteArrayOutputStream().use { output ->
            document.save(output, SaveFormat.DOCX)
            output.toByteArray()
        }
    }

    private class BuilderDsl(private val builder: DocumentBuilder) {
        fun writeln(text: String) {
            builder.writeln(text)
        }

        fun table(rows: List<List<String>>) {
            rows.forEach { row ->
                row.forEach { value ->
                    builder.insertCell()
                    builder.write(value)
                }
                builder.endRow()
            }
            builder.endTable()
        }
    }
}
