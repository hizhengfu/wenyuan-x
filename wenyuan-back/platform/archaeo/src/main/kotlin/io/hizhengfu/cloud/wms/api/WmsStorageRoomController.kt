package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsStorageRoomVo
import io.hizhengfu.cloud.wms.dao.WmsStorageRooms
import io.hizhengfu.cloud.wms.service.WmsStorageRoomService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 库房信息表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/storage/room")
class WmsStorageRoomController(val wmsStorageRoomService: WmsStorageRoomService) {

    /**
     * 添加库房信息表
     * */
    @Log("库房信息表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_CREATE')")
    fun create(@RequestBody @Validated wmsStorageRoom: WmsStorageRoomVo): ApiData {
        val apiData = ApiData()

        val result = wmsStorageRoomService.create(wmsStorageRoom)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新库房信息表
     * */
    @Log("库房信息表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_EDIT')")
    fun update(@RequestBody @Validated wmsStorageRoom: WmsStorageRoomVo): ApiData {
        val wmsStorageRoomDao = wmsStorageRoomService.update(wmsStorageRoom)
        return ApiData("保存成功")
    }


    /**
    * 复制库房信息表
    * */
    @Log("库房信息表复制")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val wmsStorageRoomDao = wmsStorageRoomService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除库房信息表
     * */
    @Log("库房信息表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val wmsStorageRoomDao = wmsStorageRoomService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除库房信息表
    * */
    @Log("批量库房信息表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = wmsStorageRoomService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表库房信息表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_SELECT')")
    fun list(wmsStorageRoom: WmsStorageRoomVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsStorageRoomService.findPage(request, wmsStorageRoom)

        page?.apply {
            list = data?.map { WmsStorageRooms.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询库房信息表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_STORAGE_ALL','WMS_STORAGE_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val wmsStorageRoomDao = wmsStorageRoomService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = wmsStorageRoomDao.toModel()

        return apiData
    }
}