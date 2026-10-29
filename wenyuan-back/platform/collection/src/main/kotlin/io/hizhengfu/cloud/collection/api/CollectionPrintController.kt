package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionPrintTemplateSaveVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintRenderRequest
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.collection.service.CollectionPrintFileFormat
import io.hizhengfu.cloud.collection.service.CollectionPrintFileResult
import io.hizhengfu.cloud.collection.service.CollectionPrintFileService
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.CollectionPrintTemplateService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("\${apiPath}/collection/print")
class CollectionPrintController(
    private val printService: CollectionPrintService,
    private val fileService: CollectionPrintFileService,
    private val templateService: CollectionPrintTemplateService,
) {
    @GetMapping("templates")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun templates(): ApiData = ApiData(data = templateService.list())

    @GetMapping("templates/{type}")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun template(@PathVariable type: String): ApiData = ApiData(data = templateService.get(parseType(type)))

    @Log("维护藏品打印模板")
    @PutMapping("templates/{type}")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.ADMIN}')")
    fun saveTemplate(
        @PathVariable type: String,
        @RequestBody @Valid input: CollectionPrintTemplateSaveVo,
    ): ApiData = ApiData(data = templateService.save(parseType(type), input))

    @Log("恢复默认藏品打印模板")
    @DeleteMapping("templates/{type}")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.ADMIN}')")
    fun resetTemplate(@PathVariable type: String): ApiData {
        templateService.reset(parseType(type))
        return ApiData()
    }

    @GetMapping("templates/{type}/fields")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun templateFields(@PathVariable type: String): ApiData = ApiData(data = templateService.usage(parseType(type)))

    @GetMapping("{type}/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun printData(@PathVariable type: String, @PathVariable id: String): ApiData =
        ApiData(data = printService.print(parseType(type), id))

    @Log("下载藏品打印Word表单")
    @GetMapping("{type}/{id}.docx")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun downloadDocx(
        @PathVariable type: String,
        @PathVariable id: String,
        response: HttpServletResponse,
    ) {
        writeFile(fileService.render(parseType(type), id, CollectionPrintFileFormat.DOCX), response)
    }

    @Log("预览藏品打印PDF表单")
    @GetMapping("{type}/{id}.pdf")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun previewPdf(
        @PathVariable type: String,
        @PathVariable id: String,
        response: HttpServletResponse,
    ) {
        writeFile(fileService.render(parseType(type), id, CollectionPrintFileFormat.PDF), response, inline = true)
    }

    @Log("批量下载藏品打印Word表单")
    @GetMapping("{type}/batch.docx")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun downloadBatchDocx(
        @PathVariable type: String,
        @RequestParam ids: List<String>,
        response: HttpServletResponse,
    ) {
        writeFile(fileService.renderBatch(parseType(type), ids, CollectionPrintFileFormat.DOCX), response)
    }

    @Log("批量预览藏品打印PDF表单")
    @GetMapping("{type}/batch.pdf")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun previewBatchPdf(
        @PathVariable type: String,
        @RequestParam ids: List<String>,
        response: HttpServletResponse,
    ) {
        writeFile(fileService.renderBatch(parseType(type), ids, CollectionPrintFileFormat.PDF), response, inline = true)
    }

    @Log("生成藏品打印Word表单")
    @PostMapping("render.docx")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun renderDocx(
        @RequestBody request: CollectionPrintRenderRequest,
        response: HttpServletResponse,
    ) {
        writeFile(fileService.render(request, CollectionPrintFileFormat.DOCX), response)
    }

    @Log("生成藏品打印PDF表单")
    @PostMapping("render.pdf")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}')")
    fun renderPdf(
        @RequestBody request: CollectionPrintRenderRequest,
        response: HttpServletResponse,
    ) {
        writeFile(fileService.render(request, CollectionPrintFileFormat.PDF), response, inline = true)
    }

    private fun parseType(type: String): EnumCollectionPrintType = runCatching {
        EnumCollectionPrintType.valueOf(type.uppercase())
    }.getOrElse {
        throw BadRequestException("打印表单类型不支持：$type")
    }

    private fun writeFile(
        file: CollectionPrintFileResult,
        response: HttpServletResponse,
        inline: Boolean = false,
    ) {
        val encoded = URLEncoder.encode(file.fileName, StandardCharsets.UTF_8).replace("+", "%20")
        val disposition = if (inline) "inline" else "attachment"
        response.contentType = file.contentType
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "$disposition; filename=\"$encoded\"; filename*=UTF-8''$encoded",
        )
        response.setContentLength(file.bytes.size)
        response.outputStream.use { it.write(file.bytes) }
    }
}
