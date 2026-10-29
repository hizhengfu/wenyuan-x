package io.hizhengfu.cloud.museum.api

import EnumDeviceType
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuDevice
import io.hizhengfu.cloud.museum.dao.MuDevices
import io.hizhengfu.cloud.museum.service.MuDeviceService
import io.hizhengfu.cloud.museum.service.YsService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 设备表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/device")
class MuDeviceController(val muDeviceService: MuDeviceService, val ysService: YsService) {

    /**
     * 添加设备表
     * */
    @Log("设备表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_CREATE')")
    fun create(@RequestBody @Validated muDevice: MuDevice): ApiData {
        val apiData = ApiData()

        val result = muDeviceService.create(muDevice)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新设备表
     * */
    @Log("设备表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_EDIT')")
    fun update(@RequestBody @Validated muDevice: MuDevice): ApiData {
        val muDeviceDao = muDeviceService.update(muDevice)
        return ApiData("保存成功")
    }


    /**
     * 复制设备表
     * */
    @Log("设备表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muDeviceDao = muDeviceService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除设备表
     * */
    @Log("设备表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muDeviceDao = muDeviceService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除设备表
     * */
    @Log("批量设备表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muDeviceService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表设备表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_SELECT')")
    fun list(muDevice: MuDevice, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muDeviceService.findPage(request, muDevice)

        if (muDevice.type == EnumDeviceType.CAMERA) {
            page?.apply {
                list = data?.map {
                    val m = MuDevices.toModel(it)
                    //地址存在且地址非链接
                    if (m.addr != null && !(m.addr.startsWith("https://") || m.addr.startsWith("http://") || m.addr.startsWith("ws://"))) {
                        m.copy(addr = ysService.getStream(m.addr, quality = 1))
                    } else {
                        m
                    }
                }
            }
        } else {
            page?.apply {
                list = data?.map { MuDevices.toModel(it) }
            }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询设备表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUDEVICE_ALL','MUDEVICE_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muDeviceDao = muDeviceService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muDeviceDao.toModel()

        return apiData
    }


    /**
     * 获取视频播放数据
     * */
    @GetMapping("video")
    fun videoUrl(deviceSerial: String, protocol: Int?, quality: Int?): ApiData {
        val videoUrl = ysService.getStream(deviceSerial, protocol ?: 4, quality ?: 1)
        return ApiData(data = videoUrl)
    }

}
