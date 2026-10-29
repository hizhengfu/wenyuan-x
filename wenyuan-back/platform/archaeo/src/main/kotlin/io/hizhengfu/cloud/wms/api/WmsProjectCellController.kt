package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsProjectCellVo
import io.hizhengfu.cloud.wms.dao.WmsProjectCells
import io.hizhengfu.cloud.wms.domain.WmsProjectPlotCellAssociation
import io.hizhengfu.cloud.wms.service.WmsProjectCellService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 项目单元接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/project/cell")
class WmsProjectCellController(val wmsProjectCellService: WmsProjectCellService) {

    /**
     * 添加项目单元
     * */
    @Log("项目单元创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_CREATE')")
    fun create(@RequestBody @Validated wmsProjectCell: WmsProjectCellVo): ApiData {
        val apiData = ApiData()

        val result = wmsProjectCellService.create(wmsProjectCell)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新项目单元
     * */
    @Log("项目单元更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_EDIT')")
    fun update(@RequestBody @Validated wmsProjectCell: WmsProjectCellVo): ApiData {
        val wmsProjectCellDao = wmsProjectCellService.update(wmsProjectCell)
        return ApiData("保存成功")
    }


    /**
     * 复制项目单元
     * */
    @Log("项目单元复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsProjectCellDao = wmsProjectCellService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除项目单元
     * */
    @Log("项目单元删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsProjectCellDao = wmsProjectCellService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除项目单元
     * */
    @Log("批量项目单元删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsProjectCellService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表项目单元
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun list(wmsProjectCell: WmsProjectCellVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsProjectCellService.findPage(request, wmsProjectCell)

        page?.apply {
            list = data?.map { WmsProjectCells.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询项目单元
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val wmsProjectCellDao = wmsProjectCellService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = wmsProjectCellDao.toModel()

        return apiData
    }

    /**
     * 更新排序
     * */
    @PreAuthorize("hasAnyAuthority('ADMIN','GROUP_ALL','GROUP_EDIT')")
    @PostMapping("sort")
    fun updateSort(@RequestBody sorts: List<GenericUpdateSort>): ApiData {
        wmsProjectCellService.updateSort(sorts)
        return ApiData(message = "更新顺序成功")
    }

    /**
     * 已经关联探方的遗迹单位
     * */
    @GetMapping("plot")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun findByPlotId(plotId: String): ApiData {
        val list = wmsProjectCellService.findByPlotId(plotId)
        return ApiData(data = list)
    }

    /**
     * 已经关联探方的遗迹单位
     * */
    @PostMapping("plot")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun updatePlotId(@RequestBody data: WmsProjectPlotCellAssociation): ApiData {
        wmsProjectCellService.updatePlotId(data.plotId, data.ids)
        return ApiData(message = "更新成功")
    }

    /**
     * 遗迹单位树形
     * */
    @GetMapping("tree")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun tree(plotId: String? = null, projectId: String): ApiData {
        val list = wmsProjectCellService.tree(projectId = projectId, plotId = plotId)
        return ApiData(data = list)
    }
}