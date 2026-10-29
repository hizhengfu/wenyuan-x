package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.wms.service.WmsDocumentService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 库存操作单据下载接口
 *
 * 提供入库单/出库单/借调申请单/移库单/归库单的 docx 下载。
 * 借调申请单：出库单 outPurpose=T6 时，title 自动改为"借调申请单"。
 */
@RestController
@RequestMapping("\${apiPath}/wms/document")
class WmsDocumentController(private val wmsDocumentService: WmsDocumentService) {

    /**
     * 下载库存操作单据（docx）
     *
     * 示例：GET /api/wms/document/operate/{recordId}.docx
     */
    @Log("下载库存操作单据")
    @GetMapping("operate/{recordId}.docx")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_SELECT')")
    fun downloadDocx(@PathVariable recordId: String, response: HttpServletResponse) {
        response.contentType =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        response.characterEncoding = StandardCharsets.UTF_8.name()

        // 先暂存内存，是为了拿到 doc.fileName 作为响应头；体量不大（单据通常 < 1MB）
        val buffer = java.io.ByteArrayOutputStream()
        val doc = wmsDocumentService.renderDocx(recordId, buffer)

        val fileName = wmsDocumentService.fileName(doc, "docx")
        val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"$encoded\"; filename*=UTF-8''$encoded"
        )
        response.setContentLength(buffer.size())
        response.outputStream.use { it.write(buffer.toByteArray()) }
    }
}
