package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuTour
import io.hizhengfu.cloud.museum.dao.MuTours
import io.hizhengfu.cloud.museum.service.MuTourService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 导览表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/tour")
class MuTourController(val muTourService: MuTourService) {

    /**
     * 添加导览表
     * */
    @Log("导览表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_CREATE')")
    fun create(@RequestBody @Validated muTour: MuTour): ApiData {
        val apiData = ApiData()

        val result = muTourService.create(muTour)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新导览表
     * */
    @Log("导览表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_EDIT')")
    fun update(@RequestBody @Validated muTour: MuTour): ApiData {
        val muTourDao = muTourService.update(muTour)
        return ApiData("保存成功")
    }


    /**
     * 复制导览表
     * */
    @Log("导览表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muTourDao = muTourService.copy(id)
        return ApiData("复制成功")
    }

    /**
     * 同步导览表
     * */
    @Log("导览表删除")
    @PostMapping("syncData")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_EDIT')")
    fun syncData(): ApiData {
        muTourService.syncData()
        return ApiData("同步成功")
    }


    /**
     * 删除导览表
     * */
    @Log("导览表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muTourDao = muTourService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除导览表
     * */
    @Log("批量导览表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muTourService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表导览表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_SELECT')")
    fun list(muTour: MuTour, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muTourService.findPage(request, muTour)

        page?.apply {
            list = data?.map { MuTours.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询导览表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTOUR_ALL','MUTOUR_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muTourDao = muTourService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muTourDao.toModel()

        return apiData
    }

}
