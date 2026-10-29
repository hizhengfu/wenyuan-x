package io.hizhengfu.cloud.museum.api

import EnumAlarmStatus
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuAlarm
import io.hizhengfu.cloud.museum.dao.MuAlarmEvents
import io.hizhengfu.cloud.museum.domain.AlarmEventParam
import io.hizhengfu.cloud.museum.service.MuAlarmEventService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * 异常事件表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/alarm")
class MuAlarmEventController(val muAlarmEventService: MuAlarmEventService) {

    /**
     * 添加异常事件表
     * */
    @Log("异常事件表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_CREATE')")
    fun create(@RequestBody @Validated muAlarm: MuAlarm): ApiData {
        val apiData = ApiData()

        val result = muAlarmEventService.create(muAlarm)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新异常事件表
     * */
    @Log("异常事件表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_EDIT')")
    fun update(@RequestBody @Validated muAlarm: MuAlarm): ApiData {
        val muAlarmDao = muAlarmEventService.update(muAlarm)
        return ApiData("保存成功")
    }


    /**
     * 复制异常事件表
     * */
    @Log("异常事件表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muAlarmDao = muAlarmEventService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除异常事件表
     * */
    @Log("异常事件表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muAlarmDao = muAlarmEventService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除异常事件表
     * */
    @Log("批量异常事件表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muAlarmEventService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }


    /**
     * 批量删除异常事件表
     * */
    @Log("批量异常事件表删除")
    @PostMapping("deleteAll")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_DELETE')")
    fun deleteAll(): ApiData {
        val count = muAlarmEventService.delete()
        return ApiData("删除成功${count}条数据")
    }


    /**
     * 列表异常事件表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_SELECT')")
    fun list(
        muAlarm: MuAlarm,
        request: HttpServletRequest,
        @RequestParam("dateTimeRange[]") dateTimeRange: List<LocalDateTime>?,
    ): ApiData {
        val apiData = ApiData()

        val page = muAlarmEventService.findPage(request, muAlarm.copy(dateTimeRange = dateTimeRange))

        page?.apply {
            list = data?.map { MuAlarmEvents.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询异常事件表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARM_ALL','MUALARM_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muAlarmDao = muAlarmEventService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muAlarmDao.toModel()

        return apiData
    }

    /**
     * 事件处置
     * */
    @PostMapping("dispose")
    fun dispose(@RequestBody params: AlarmEventParam): ApiData {
        muAlarmEventService.dispose(params.copy(status = EnumAlarmStatus.FINISH))
        return ApiData(message = "人工处置完成")
    }

    @GetMapping("logs")
    fun logs(id: String): ApiData {
        return ApiData(data = muAlarmEventService.alarmDisposeLogs(id))
    }

}
