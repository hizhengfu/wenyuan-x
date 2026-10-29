package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuLayer
import io.hizhengfu.cloud.museum.dao.MuLayers
import io.hizhengfu.cloud.museum.domain.AddPointPosition
import io.hizhengfu.cloud.museum.domain.UpdatePointPosition
import io.hizhengfu.cloud.museum.service.MuLayerService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 图层表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/layer")
class MuLayerController(val muLayerService: MuLayerService) {

    /**
     * 添加图层表
     * */
    @Log("图层表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_CREATE')")
    fun create(@RequestBody @Validated muLayer: MuLayer): ApiData {
        val apiData = ApiData()

        val result = muLayerService.create(muLayer)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新图层表
     * */
    @Log("图层表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_EDIT')")
    fun update(@RequestBody @Validated muLayer: MuLayer): ApiData {
        val muLayerDao = muLayerService.update(muLayer)
        return ApiData("保存成功")
    }


    /**
     * 复制图层表
     * */
    @Log("图层表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muLayerDao = muLayerService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除图层表
     * */
    @Log("图层表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muLayerDao = muLayerService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除图层表
     * */
    @Log("批量图层表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muLayerService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表图层表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_SELECT')")
    fun list(muLayer: MuLayer, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muLayerService.findPage(request, muLayer)

        page?.apply {
            list = data?.map { MuLayers.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询图层表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MULAYER_ALL','MULAYER_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muLayerDao = muLayerService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muLayerDao.toModel()

        return apiData
    }

    @GetMapping("listPoint")
    fun listPoint(layerId: String, scene: String? = null, isDesign: Boolean?): ApiData {
        val list = muLayerService.listPoint(layerId, isDesign = isDesign ?: false)
        return ApiData(data = list)
    }

    /**
     * 更新点位位置信息
     * */
    @PostMapping("updatePoint")
    fun updatePoint(@RequestBody @Validated params: UpdatePointPosition): ApiData {
        muLayerService.updatePoint(params)
        return ApiData()
    }

    /**
     * 创建点位
     * */
    @PostMapping("addPoint")
    fun addPoint(@RequestBody @Validated params: AddPointPosition): ApiData {
        val id = muLayerService.addPoint(params)
        return ApiData(data = id)
    }

    /**
     * 删除点位
     * */
    @PostMapping("removePoint")
    fun removePoint(id: String): ApiData {
        muLayerService.removePoint(id)
        return ApiData()
    }

}
