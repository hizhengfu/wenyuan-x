package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.Users
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.EnumArtifactOutboundType
import io.hizhengfu.cloud.wms.domain.EnumStorageOperationType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 单据生成数据（Service → Controller / Word/HTML 渲染）
 *
 * - title: 单据标题，根据 type/outPurpose 动态生成（出库单/借调申请单/入库单/移库单/归库单）
 * - fields: key→value 形式的详情区，按 type 不同字段不同
 * - artifacts: 文物清单
 */
data class WmsDocumentVo(
    val id: String,
    val title: String,
    val code: String?,
    val type: EnumStorageOperationType,
    val operationTime: LocalDateTime,
    val operatorName: String?,
    val sumQuantity: Int,
    val remarks: String?,
    val fields: LinkedHashMap<String, String?>,
    val artifacts: List<WmsArtifactVo>
)

/**
 * 库存操作单据服务
 *
 * 提供入库单/出库单/借调申请单/移库单/归库单的服务端权威生成（docx）。
 * 借调申请单：出库且 outPurpose=T6（借展）时，title 自动改为"借调申请单"。
 */
@Component
@Transactional(readOnly = true)
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsDocumentService(
    private val operationService: WmsOperationService
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 装载单据数据 */
    @Cacheable
    fun loadDocument(recordId: String): WmsDocumentVo {
        val recordDao = WmsOperateRecordDao.findById(recordId)
            ?: throw BadRequestException("单据不存在")

        val operatorName = Users.selectAll()
            .where { Users.id eq recordDao.operatorBy }
            .singleOrNull()
            ?.let { "${it[Users.name]}(${it[Users.loginName]})" }
            ?: recordDao.operatorBy

        val (title, fields) = when (recordDao.type) {
            EnumStorageOperationType.OUTBOUND -> loadOutbound(recordId)
            EnumStorageOperationType.INBOUND -> loadInbound(recordId)
            EnumStorageOperationType.TRANSFER -> loadTransfer(recordId)
            EnumStorageOperationType.RETURN -> loadReturn(recordId)
        }

        val artifacts = operationService.findArtifactList(recordId)
            .map { WmsArtifacts.toModelOperation(it) }

        return WmsDocumentVo(
            id = recordDao.id.value,
            title = title,
            code = recordDao.code,
            type = recordDao.type,
            operationTime = recordDao.operationTime,
            operatorName = operatorName,
            sumQuantity = recordDao.sumQuantity,
            remarks = recordDao.remarks,
            fields = fields,
            artifacts = artifacts
        )
    }

    private fun loadOutbound(recordId: String): Pair<String, LinkedHashMap<String, String?>> {
        val row = WmsOperateOutbounds.selectAll()
            .where { WmsOperateOutbounds.recordId eq recordId }
            .singleOrNull() ?: throw BadRequestException("出库明细不存在")

        val outPurposeCode = row[WmsOperateOutbounds.outPurpose]
        val outPurposeLabel = runCatching { EnumArtifactOutboundType.valueOf(outPurposeCode).description }
            .getOrDefault(outPurposeCode)

        // T6 = 借展 → 借调申请单
        val title = if (outPurposeCode == EnumArtifactOutboundType.T6.name) "借调申请单" else "出库单"

        val fields = linkedMapOf<String, String?>(
            "出库原因" to outPurposeLabel,
            "文物去向" to row[WmsOperateOutbounds.artifactDest],
            "接收方" to row[WmsOperateOutbounds.receiver],
            "是否归还" to if (row[WmsOperateOutbounds.needReturn]) "是" else "否",
            "预计归还" to row[WmsOperateOutbounds.expectedReturn]?.format(dateFormatter)
        )
        return title to fields
    }

    private fun loadInbound(recordId: String): Pair<String, LinkedHashMap<String, String?>> {
        val query = WmsOperateInbounds.join(WmsStorageLocations, JoinType.LEFT) {
            WmsOperateInbounds.locationId eq WmsStorageLocations.id
        }.join(WmsStorageRooms, JoinType.LEFT) {
            WmsStorageLocations.roomId eq WmsStorageRooms.id
        }.select(WmsOperateInbounds.columns + WmsStorageLocations.name + WmsStorageRooms.name)
            .andWhere { WmsOperateInbounds.recordId eq recordId }

        val row = query.singleOrNull() ?: throw BadRequestException("入库明细不存在")
        val fields = linkedMapOf<String, String?>(
            "接收人" to row[WmsOperateInbounds.receiver],
            "移交人" to row[WmsOperateInbounds.transfer],
            "存放库房" to row[WmsStorageRooms.name],
            "存放位置" to row[WmsStorageLocations.name]
        )
        return "入库单" to fields
    }

    private fun loadTransfer(recordId: String): Pair<String, LinkedHashMap<String, String?>> {
        // 简化处理：分别查 from/to 库位名
        val row = WmsOperateTransfers.selectAll()
            .where { WmsOperateTransfers.recordId eq recordId }
            .singleOrNull() ?: throw BadRequestException("移库明细不存在")

        val fromName = WmsStorageLocations.selectAll()
            .where { WmsStorageLocations.id eq row[WmsOperateTransfers.fromLocationId] }
            .singleOrNull()?.get(WmsStorageLocations.name)
        val toName = WmsStorageLocations.selectAll()
            .where { WmsStorageLocations.id eq row[WmsOperateTransfers.toLocationId] }
            .singleOrNull()?.get(WmsStorageLocations.name)

        val fields = linkedMapOf<String, String?>(
            "原库位" to fromName,
            "目标库位" to toName,
            "移库原因" to row[WmsOperateTransfers.reason]
        )
        return "移库单" to fields
    }

    private fun loadReturn(recordId: String): Pair<String, LinkedHashMap<String, String?>> {
        val query = WmsOperateReturns.join(WmsStorageLocations, JoinType.LEFT) {
            WmsOperateReturns.locationId eq WmsStorageLocations.id
        }.join(WmsStorageRooms, JoinType.LEFT) {
            WmsStorageLocations.roomId eq WmsStorageRooms.id
        }.select(WmsOperateReturns.columns + WmsStorageLocations.name + WmsStorageRooms.name)
            .andWhere { WmsOperateReturns.recordId eq recordId }

        val row = query.singleOrNull() ?: throw BadRequestException("归库明细不存在")
        val fields = linkedMapOf<String, String?>(
            "归还人" to row[WmsOperateReturns.returnBy],
            "核对人" to row[WmsOperateReturns.checkBy],
            "接收方" to row[WmsOperateReturns.receiver],
            "存放库房" to row[WmsStorageRooms.name],
            "存放位置" to row[WmsStorageLocations.name],
            "实际归还日期" to row[WmsOperateReturns.actualReturn].format(dateFormatter)
        )
        return "归库单" to fields
    }

    /** 默认文件名（去除路径非法字符） */
    fun fileName(doc: WmsDocumentVo, ext: String): String {
        val base = "${doc.title}_${doc.code ?: doc.id}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "$base.$ext"
    }

    /** 渲染单据为 .docx 流 */
    fun renderDocx(recordId: String, out: OutputStream): WmsDocumentVo {
        val doc = loadDocument(recordId)
        XWPFDocument().use { wd ->
            // 标题
            wd.createParagraph().apply {
                alignment = ParagraphAlignment.CENTER
                spacingAfter = 200
                createRun().apply {
                    isBold = true
                    fontSize = 20
                    setText(doc.title)
                }
            }
            // 单号 / 日期 (一行两端对齐用 tab)
            wd.createParagraph().apply {
                alignment = ParagraphAlignment.LEFT
                createRun().apply {
                    fontSize = 10
                    setText("单号：${doc.code ?: doc.id}        日期：${doc.operationTime.format(dateFormatter)}")
                }
            }

            // 详情字段表（2列 key-value，跨多行）
            if (doc.fields.isNotEmpty()) {
                val info = wd.createTable(doc.fields.size, 2)
                info.styleByDefault()
                doc.fields.entries.forEachIndexed { i, (k, v) ->
                    val row = info.getRow(i)
                    row.getCell(0).apply {
                        text.let { /* clear default empty */ }
                        paragraphs.firstOrNull()?.runs?.firstOrNull()?.let { paragraphs.firstOrNull()?.removeRun(0) }
                        val p = paragraphs.firstOrNull() ?: addParagraph()
                        p.createRun().apply {
                            isBold = true
                            fontSize = 11
                            setText(k)
                        }
                    }
                    row.getCell(1).apply {
                        val p = paragraphs.firstOrNull() ?: addParagraph()
                        p.createRun().apply {
                            fontSize = 11
                            setText(v ?: "")
                        }
                    }
                }
            }

            // 文物清单标题
            wd.createParagraph().apply {
                spacingBefore = 200
                createRun().apply {
                    isBold = true
                    fontSize = 12
                    setText("文物清单（共 ${doc.sumQuantity} 件）")
                }
            }

            // 文物清单表
            val headers = listOf("序号", "文物编号", "文物名称", "材质", "数量", "出土单位", "存放位置")
            val tbl = wd.createTable(doc.artifacts.size + 1, headers.size)
            tbl.styleByDefault()
            // 表头
            val headRow = tbl.getRow(0)
            headers.forEachIndexed { i, h ->
                val cell = headRow.getCell(i)
                val p = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
                p.alignment = ParagraphAlignment.CENTER
                p.createRun().apply {
                    isBold = true
                    fontSize = 11
                    setText(h)
                }
            }
            // 表体
            doc.artifacts.forEachIndexed { idx, a ->
                val row = tbl.getRow(idx + 1)
                val values = listOf(
                    "${idx + 1}",
                    a.code ?: "",
                    a.name ?: "",
                    a.material ?: "",
                    "${a.quantity ?: ""}${a.quantityUnit ?: ""}",
                    a.projectCellName ?: "",
                    a.storageLocation?.name ?: ""
                )
                values.forEachIndexed { i, v ->
                    val cell = row.getCell(i)
                    val p = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
                    p.createRun().apply {
                        fontSize = 10
                        setText(v)
                    }
                }
            }

            // 备注
            if (!doc.remarks.isNullOrBlank()) {
                wd.createParagraph().apply {
                    spacingBefore = 200
                    createRun().apply {
                        fontSize = 11
                        setText("备注：${doc.remarks}")
                    }
                }
            }

            // 签字栏
            wd.createParagraph().apply {
                spacingBefore = 400
                createRun().apply {
                    fontSize = 11
                    setText("移交人签字：__________      接收人签字：__________      登记人签字：__________")
                }
            }
            wd.createParagraph().apply {
                spacingBefore = 200
                createRun().apply {
                    fontSize = 11
                    setText("操作人：${doc.operatorName ?: ""}        打印日期：${LocalDateTime.now().format(dateTimeFormatter)}")
                }
            }

            wd.write(out)
        }
        return doc
    }

    private fun XWPFTable.styleByDefault() {
        // 简单设置100%宽度
        setWidth("100%")
    }
}
