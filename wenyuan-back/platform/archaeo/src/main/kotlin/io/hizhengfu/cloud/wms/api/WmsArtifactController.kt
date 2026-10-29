package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.ExcelExportUtils
import io.hizhengfu.cloud.common.utils.ExcelImportUtils
import io.hizhengfu.cloud.wms.dao.WmsArtifactExtends
import io.hizhengfu.cloud.wms.dao.WmsArtifactVo
import io.hizhengfu.cloud.wms.dao.WmsArtifacts
import io.hizhengfu.cloud.wms.domain.EnumArtifactPreserveStatus
import io.hizhengfu.cloud.wms.domain.WmsArtifactModel
import io.hizhengfu.cloud.wms.domain.WmsArtifactParams
import io.hizhengfu.cloud.wms.service.WmsArtifactExportService
import io.hizhengfu.cloud.wms.service.WmsArtifactService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * 文物基本信息表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/artifact")
class WmsArtifactController(
    val wmsArtifactService: WmsArtifactService,
    val wmsArtifactExportService: WmsArtifactExportService,
) {
    val logger = LoggerFactory.getLogger(WmsArtifactController::class.java)

    /**
     * 添加文物基本信息表
     * */
    @Log("文物基本信息表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_CREATE')")
    fun create(@RequestBody @Validated wmsArtifact: WmsArtifactVo): ApiData {
        val apiData = ApiData()

        val result = wmsArtifactService.create(wmsArtifact)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新文物基本信息表
     * */
    @Log("文物基本信息表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_EDIT')")
    fun update(@RequestBody @Validated wmsArtifact: WmsArtifactVo): ApiData {
        val wmsArtifactDao = wmsArtifactService.update(wmsArtifact)
        return ApiData("保存成功")
    }

    /**
     * 更新文物研究信息表
     * */
    @Log("文物研究信息表更新")
    @PostMapping("updateExtend")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_EDIT')")
    fun updateExtend(@RequestBody @Validated params: WmsArtifactParams): ApiData {
        val wmsArtifactDao = wmsArtifactService.saveExtend(params)
        return ApiData("保存成功")
    }

    /**
     * 查询文物基本信息表
     * */
    @GetMapping("extend/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_SELECT')")
    fun getExtend(@PathVariable id: String): ApiData {
        val resultRow = wmsArtifactService.getExtend(id)
        return ApiData(data = resultRow?.let { WmsArtifactExtends.toModel(it) })
    }


    /**
     * 复制文物基本信息表
     * */
    @Log("文物基本信息表复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsArtifactDao = wmsArtifactService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除文物基本信息表
     * */
    @Log("文物基本信息表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsArtifactDao = wmsArtifactService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除文物基本信息表
     * */
    @Log("批量文物基本信息表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsArtifactService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表文物基本信息表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_SELECT')")
    fun list(wmsArtifact: WmsArtifactVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsArtifactService.findPage(request, wmsArtifact)

        page?.apply {
            list = data?.map { WmsArtifacts.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询文物基本信息表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val resultRow = wmsArtifactService.get(id) ?: throw BadRequestException("数据不存在")
        return ApiData(data = WmsArtifacts.toModel(resultRow))
    }

    @Log("文物批量导入")
    @PostMapping("import")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_CREATE')")
    fun import(file: MultipartFile): ApiData {
        var total = 0
        var success = 0
        ExcelImportUtils.read(file.inputStream, WmsArtifactModel::class.java) {
            total += it.size
            success += wmsArtifactService.batchImport(it)
        }

        val message = "数据共「${total}」条,成功「${success}」条数据"

        return ApiData(message = message)
    }

    /**
     * 导出模板
     * */
    @GetMapping("exportTemplate")
    fun exportTemplate(response: HttpServletResponse) {
        val data = mutableListOf<WmsArtifactModel>()
        val excelModel = WmsArtifactModel()
        excelModel.name = "文物名称（必填）"
        excelModel.project = "遗址名称（必填）"
        excelModel.code = "编号"
        excelModel.material = "质地（必填）"
        excelModel.quantity = 1
        excelModel.preservation = EnumArtifactPreserveStatus.entries.joinToString(separator = ",") { it.description }
//        excelModel.quantityUnit = EnumArtifactUnitType.entries.joinToString(separator = ",") { it.description }
        excelModel.unearthDate = LocalDate.now()
        excelModel.projectCell = "出土单位"
        excelModel.recorderBy = "记录人"
        excelModel.remarks = "如有请输入备注"

        data.add(excelModel)

        ExcelExportUtils.download(clazz = WmsArtifactModel::class.java, data = data, response = response)
    }

    /**
     * 文物卡片导出 Word
     *
     * 示例：POST /api/wms/artifact/exportCard?template=STANDARD
     *      body: ["id1","id2",...]
     */
    @Log("文物卡片 Word 导出")
    @PostMapping("exportCard")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_EXTEND_ALL','WMS_ARTIFACT_EXTEND_SELECT')")
    fun exportCard(
        @RequestBody ids: List<String>,
        @RequestParam(defaultValue = "STANDARD") template: WmsArtifactExportService.EnumCardTemplate,
        @RequestParam(required = false) includeShape: Boolean?,
        @RequestParam(required = false) includeCover: Boolean?,
        @RequestParam(required = false) includeAngles: Boolean?,
        @RequestParam(required = false) includeLine: Boolean?,
        @RequestParam(required = false) includeProfile: Boolean?,
        response: HttpServletResponse,
    ) {
        if (ids.isEmpty()) throw BadRequestException("请至少选择一件文物")

        response.contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        response.characterEncoding = StandardCharsets.UTF_8.name()

        val options = WmsArtifactExportService.CardOptions(
            includeShape = includeShape,
            includeCover = includeCover,
            includeAngles = includeAngles,
            includeLine = includeLine,
            includeProfile = includeProfile,
        )
        val buffer = java.io.ByteArrayOutputStream()
        val fileName = wmsArtifactExportService.export(ids, template, options, buffer)

        val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"$encoded\"; filename*=UTF-8''$encoded"
        )
        response.setContentLength(buffer.size())
        response.outputStream.use { it.write(buffer.toByteArray()) }
    }
}