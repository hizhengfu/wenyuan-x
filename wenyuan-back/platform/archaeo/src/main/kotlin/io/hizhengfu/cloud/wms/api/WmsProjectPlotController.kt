package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsProjectPlotVo
import io.hizhengfu.cloud.wms.dao.WmsProjectPlots
import io.hizhengfu.cloud.wms.service.WmsProjectPlotService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 项目探方接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/project/plot")
class WmsProjectPlotController(val wmsProjectPlotService: WmsProjectPlotService) {

    /**
     * 添加项目探方
     * */
    @Log("项目探方创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_CREATE')")
    fun create(@RequestBody @Validated wmsProjectPlot: WmsProjectPlotVo): ApiData {
        val apiData = ApiData()

        val result = wmsProjectPlotService.create(wmsProjectPlot)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新项目探方
     * */
    @Log("项目探方更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_EDIT')")
    fun update(@RequestBody @Validated wmsProjectPlot: WmsProjectPlotVo): ApiData {
        val wmsProjectPlotDao = wmsProjectPlotService.update(wmsProjectPlot)
        return ApiData("保存成功")
    }


    /**
     * 复制项目探方
     * */
    @Log("项目探方复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsProjectPlotDao = wmsProjectPlotService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除项目探方
     * */
    @Log("项目探方删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsProjectPlotDao = wmsProjectPlotService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除项目探方
     * */
    @Log("批量项目探方删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsProjectPlotService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表项目探方
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun list(wmsProjectPlot: WmsProjectPlotVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsProjectPlotService.findPage(request, wmsProjectPlot)

        page?.apply {
            list = data?.map { WmsProjectPlots.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询项目探方
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val wmsProjectPlotDao = wmsProjectPlotService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = wmsProjectPlotDao.toModel()

        return apiData
    }

    /**
     * 查询考古项目探方表
     * */
    @GetMapping("tree")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun tree(projectId: String): ApiData {
        val list = wmsProjectPlotService.tree(projectId)
        return ApiData(data = list)
    }
}