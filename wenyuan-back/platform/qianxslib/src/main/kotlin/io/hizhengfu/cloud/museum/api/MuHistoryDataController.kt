package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuHistoryData
import io.hizhengfu.cloud.museum.dao.MuHistoryDatas
import io.hizhengfu.cloud.museum.service.MuHistoryDataService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 采集数据表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/data")
class MuHistoryDataController(val muHistoryDataService: MuHistoryDataService) {

    /**
     * 添加采集数据表
     * */
    @Log("采集数据表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_CREATE')")
    fun create(@RequestBody @Validated muHistoryData: MuHistoryData): ApiData {
        val apiData = ApiData()

        val result = muHistoryDataService.create(muHistoryData)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新采集数据表
     * */
    @Log("采集数据表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_EDIT')")
    fun update(@RequestBody @Validated muHistoryData: MuHistoryData): ApiData {
        val muHistoryDataDao = muHistoryDataService.update(muHistoryData)
        return ApiData("保存成功")
    }


    /**
     * 复制采集数据表
     * */
    @Log("采集数据表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muHistoryDataDao = muHistoryDataService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除采集数据表
     * */
    @Log("采集数据表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muHistoryDataDao = muHistoryDataService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除采集数据表
     * */
    @Log("批量采集数据表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muHistoryDataService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表采集数据表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_SELECT')")
    fun list(muHistoryData: MuHistoryData, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muHistoryDataService.findPage(request, muHistoryData)

        page?.apply {
            list = data?.map { MuHistoryDatas.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询采集数据表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUHISTORYDATA_ALL','MUHISTORYDATA_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muHistoryDataDao = muHistoryDataService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muHistoryDataDao.toModel()

        return apiData
    }

}
