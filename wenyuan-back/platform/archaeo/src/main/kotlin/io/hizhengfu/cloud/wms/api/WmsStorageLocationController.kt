package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsStorageLocationVo
import io.hizhengfu.cloud.wms.dao.WmsStorageLocations
import io.hizhengfu.cloud.wms.service.WmsStorageLocationService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 库存位置表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/storage/location")
class WmsStorageLocationController(val wmsStorageLocationService: WmsStorageLocationService) {

    /**
     * 添加库存位置表
     * */
    @Log("库存位置表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_CREATE')")
    fun create(@RequestBody @Validated wmsStorageLocation: WmsStorageLocationVo): ApiData {
        val apiData = ApiData()

        val result = wmsStorageLocationService.create(wmsStorageLocation)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新库存位置表
     * */
    @Log("库存位置表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_EDIT')")
    fun update(@RequestBody @Validated wmsStorageLocation: WmsStorageLocationVo): ApiData {
        val wmsStorageLocationDao = wmsStorageLocationService.update(wmsStorageLocation)
        return ApiData("保存成功")
    }


    /**
    * 复制库存位置表
    * */
    @Log("库存位置表复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsStorageLocationDao = wmsStorageLocationService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除库存位置表
     * */
    @Log("库存位置表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsStorageLocationDao = wmsStorageLocationService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除库存位置表
    * */
    @Log("批量库存位置表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsStorageLocationService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表库存位置表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_SELECT')")
    fun list(wmsStorageLocation: WmsStorageLocationVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsStorageLocationService.findPage(request, wmsStorageLocation)

        page?.apply {
            list = data?.map { WmsStorageLocations.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询库存位置表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val wmsStorageLocationDao = wmsStorageLocationService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = wmsStorageLocationDao.toModel()

        return apiData
    }
}