package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuEvent
import io.hizhengfu.cloud.museum.dao.MuEvents
import io.hizhengfu.cloud.museum.service.MuEventService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 异况表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/event")
class MuEventController(val muEventService: MuEventService) {

    /**
     * 添加异况表
     * */
    @Log("异况表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_CREATE')")
    fun create(@RequestBody @Validated muEvent: MuEvent): ApiData {
        val apiData = ApiData()

        val result = muEventService.create(muEvent)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新异况表
     * */
    @Log("异况表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_EDIT')")
    fun update(@RequestBody @Validated muEvent: MuEvent): ApiData {
        val muEventDao = muEventService.update(muEvent)
        return ApiData("保存成功")
    }

    /**
     * 更新异况表
     * */
    @Log("异况表任务执行")
    @PostMapping("process/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_EDIT')")
    fun process(@PathVariable id: String): ApiData {
        val muEventDao = muEventService.resetEventFinalDateTime(id)
        return ApiData("任务执行成功")
    }


    /**
     * 复制异况表
     * */
    @Log("异况表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muEventDao = muEventService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除异况表
     * */
    @Log("异况表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muEventDao = muEventService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除异况表
     * */
    @Log("批量异况表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muEventService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表异况表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_SELECT')")
    fun list(muEvent: MuEvent, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muEventService.findPage(request, muEvent)

        page?.apply {
            list = data?.map { MuEvents.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询异况表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUEVENT_ALL','MUEVENT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muEventDao = muEventService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muEventDao.toModel()

        return apiData
    }

}
