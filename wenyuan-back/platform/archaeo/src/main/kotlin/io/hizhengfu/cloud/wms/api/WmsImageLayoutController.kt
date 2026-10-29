package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.wms.dao.WmsImageLayoutVo
import io.hizhengfu.cloud.wms.service.WmsImageLayoutService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/wms/imageLayout")
class WmsImageLayoutController(private val service: WmsImageLayoutService) {

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_IMAGE_LAYOUT_ALL','WMS_IMAGE_LAYOUT_SELECT')")
    fun page(params: WmsImageLayoutVo, request: HttpServletRequest): ApiData =
        ApiData(data = service.findPage(params, request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_IMAGE_LAYOUT_ALL','WMS_IMAGE_LAYOUT_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = service.get(id))

    @Log("新建图片排版")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_IMAGE_LAYOUT_ALL','WMS_IMAGE_LAYOUT_CREATE')")
    fun create(@RequestBody vo: WmsImageLayoutVo): ApiData = ApiData(data = service.create(vo))

    @Log("更新图片排版")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_IMAGE_LAYOUT_ALL','WMS_IMAGE_LAYOUT_EDIT')")
    fun update(@RequestBody vo: WmsImageLayoutVo): ApiData {
        service.update(vo)
        return ApiData("保存成功")
    }

    @Log("删除图片排版")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_IMAGE_LAYOUT_ALL','WMS_IMAGE_LAYOUT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        service.delete(id)
        return ApiData("删除成功")
    }
}
