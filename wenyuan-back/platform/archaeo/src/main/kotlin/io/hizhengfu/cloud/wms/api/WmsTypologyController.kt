package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.wms.dao.WmsTypologyTableVo
import io.hizhengfu.cloud.wms.domain.WmsTypologyAutoBuildParams
import io.hizhengfu.cloud.wms.service.WmsTypologyService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * 型式分期表
 *
 * 表层只做 HTTP 解析，所有 DB / 缓存在 [WmsTypologyService]。
 */
@RestController
@RequestMapping("\${apiPath}/wms/typology")
class WmsTypologyController(private val typologyService: WmsTypologyService) {

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_SELECT')")
    fun page(
        params: WmsTypologyTableVo,
        @RequestParam(required = false) year: Int?,
        request: HttpServletRequest,
    ): ApiData = ApiData(data = typologyService.findPage(params, year, request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = typologyService.get(id))

    /**
     * 新建型式分期概要：按遗址联动材质、器型、期段与统计预览。
     */
    @GetMapping("summary")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_SELECT')")
    fun summary(
        @RequestParam projectId: String,
        @RequestParam(required = false) material: String?,
    ): ApiData = ApiData(data = typologyService.summary(projectId, material))

    @Log("新建型式分期表")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_CREATE')")
    fun create(@RequestBody vo: WmsTypologyTableVo): ApiData = ApiData(data = typologyService.create(vo))

    @Log("更新型式分期表")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_EDIT')")
    fun update(@RequestBody vo: WmsTypologyTableVo): ApiData {
        typologyService.update(vo)
        return ApiData("保存成功")
    }

    @Log("删除型式分期表")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        typologyService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 智能分期：根据 ArtifactExtend 字段拼装一份 content JSON 给前端做初始填充。
     */
    @PostMapping("autoBuild")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_CREATE')")
    fun autoBuild(@RequestBody params: WmsTypologyAutoBuildParams): ApiData =
        ApiData(data = typologyService.autoBuild(params.projectId, params.material, params.categoryName, params.artifactTypes))

    /**
     * 自动分期统计预览（每类器物分几型/几式/几期/几段）
     */
    @GetMapping("autoStat")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_SELECT')")
    fun autoStat(
        @RequestParam projectId: String,
        @RequestParam(required = false) material: String?,
    ): ApiData = ApiData(data = typologyService.autoStat(projectId, material))

    /**
     * 标型器候选：编辑"式"单元格时列出可选文物（带线图、图名）
     */
    @GetMapping("typicalArtifacts")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_TYPOLOGY_ALL','WMS_TYPOLOGY_SELECT')")
    fun typicalArtifacts(
        @RequestParam projectId: String,
        @RequestParam artifactType: String,
        @RequestParam(required = false) material: String?,
        @RequestParam(required = false) stage: String?,
        @RequestParam(required = false) artifactStyle: String?,
        @RequestParam(required = false) artifactSubStyle: String?,
        @RequestParam(required = false) phaseName: String?,
        @RequestParam(required = false) sectionName: String?,
    ): ApiData = ApiData(
        data = typologyService.typicalArtifacts(
            projectId = projectId,
            artifactType = artifactType,
            material = material,
            stage = stage,
            artifactStyle = artifactStyle,
            artifactSubStyle = artifactSubStyle,
            phaseName = phaseName,
            sectionName = sectionName,
        )
    )
}
