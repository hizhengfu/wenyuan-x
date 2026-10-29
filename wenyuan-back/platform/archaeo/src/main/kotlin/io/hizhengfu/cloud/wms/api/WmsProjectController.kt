package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.wms.dao.WmsProjectVo
import io.hizhengfu.cloud.wms.dao.WmsProjects
import io.hizhengfu.cloud.wms.service.WmsProjectService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 考古项目表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/project")
class WmsProjectController(val wmsProjectService: WmsProjectService) {

    /**
     * 添加考古项目表
     * */
    @Log("考古项目表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_CREATE')")
    fun create(@RequestBody @Validated wmsProject: WmsProjectVo): ApiData {
        val apiData = ApiData()

        val result = wmsProjectService.create(wmsProject)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新考古项目表
     * */
    @Log("考古项目表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_EDIT')")
    fun update(@RequestBody @Validated wmsProject: WmsProjectVo): ApiData {
        val wmsProjectDao = wmsProjectService.update(wmsProject)
        return ApiData("保存成功")
    }


    /**
     * 复制考古项目表
     * */
    @Log("考古项目表复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsProjectDao = wmsProjectService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除考古项目表
     * */
    @Log("考古项目表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsProjectDao = wmsProjectService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除考古项目表
     * */
    @Log("批量考古项目表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsProjectService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表考古项目表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun list(wmsProject: WmsProjectVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsProjectService.findPage(request, wmsProject)

        page?.apply {
            list = data?.map { WmsProjects.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询考古项目表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val wmsProjectDao = wmsProjectService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = wmsProjectDao.toModel()

        return apiData
    }

    /**
     * 查询考古项目表
     * */
    @GetMapping("tree")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_PROJECT_ALL','WMS_PROJECT_SELECT')")
    fun tree(id: String? = null): ApiData {
        var groupId: String? = null
        if (!SecurityUtils.isAdminRole()) {
            groupId = SecurityUtils.getGroupId()
        }
        val list = wmsProjectService.tree(id, groupId)
        return ApiData(data = list)
    }
}