package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuPlace
import io.hizhengfu.cloud.museum.dao.MuPlaces
import io.hizhengfu.cloud.museum.service.MuPlaceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 博物馆接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/place")
class MuPlaceController(val muPlaceService: MuPlaceService) {

    /**
     * 添加博物馆
     * */
    @Log("博物馆创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_CREATE')")
    fun create(@RequestBody @Validated muPlace: MuPlace): ApiData {
        val apiData = ApiData()

        val result = muPlaceService.create(muPlace)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新博物馆
     * */
    @Log("博物馆更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_EDIT')")
    fun update(@RequestBody @Validated muPlace: MuPlace): ApiData {
        val muPlaceDao = muPlaceService.update(muPlace)
        return ApiData("保存成功")
    }


    /**
    * 复制博物馆
    * */
    @Log("博物馆删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muPlaceDao = muPlaceService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除博物馆
     * */
    @Log("博物馆删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muPlaceDao = muPlaceService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除博物馆
    * */
    @Log("批量博物馆删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muPlaceService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表博物馆
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_SELECT')")
    fun list(muPlace: MuPlace, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muPlaceService.findPage(request, muPlace)

        page?.apply {
            list = data?.map { MuPlaces.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询博物馆
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUPLACE_ALL','MUPLACE_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muPlaceDao = muPlaceService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muPlaceDao.toModel()

        return apiData
    }

}
