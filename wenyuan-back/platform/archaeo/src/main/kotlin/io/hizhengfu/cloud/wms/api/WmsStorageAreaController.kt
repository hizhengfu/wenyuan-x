package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsStorageAreaVo
import io.hizhengfu.cloud.wms.dao.WmsStorageAreas
import io.hizhengfu.cloud.wms.service.WmsStorageAreaService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 库区信息表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/storage/area")
class WmsStorageAreaController(val wmsStorageAreaService: WmsStorageAreaService) {

    /**
     * 添加库区信息表
     * */
    @Log("库区信息表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_CREATE')")
    fun create(@RequestBody @Validated wmsStorageArea: WmsStorageAreaVo): ApiData {
        val apiData = ApiData()

        val result = wmsStorageAreaService.create(wmsStorageArea)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新库区信息表
     * */
    @Log("库区信息表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_EDIT')")
    fun update(@RequestBody @Validated wmsStorageArea: WmsStorageAreaVo): ApiData {
        val wmsStorageAreaDao = wmsStorageAreaService.update(wmsStorageArea)
        return ApiData("保存成功")
    }


    /**
    * 复制库区信息表
    * */
    @Log("库区信息表复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsStorageAreaDao = wmsStorageAreaService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除库区信息表
     * */
    @Log("库区信息表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsStorageAreaDao = wmsStorageAreaService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除库区信息表
    * */
    @Log("批量库区信息表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsStorageAreaService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表库区信息表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_SELECT')")
    fun list(wmsStorageArea: WmsStorageAreaVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsStorageAreaService.findPage(request, wmsStorageArea)

        page?.apply {
            list = data?.map { WmsStorageAreas.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询库区信息表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val wmsStorageAreaDao = wmsStorageAreaService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = wmsStorageAreaDao.toModel()

        return apiData
    }
}