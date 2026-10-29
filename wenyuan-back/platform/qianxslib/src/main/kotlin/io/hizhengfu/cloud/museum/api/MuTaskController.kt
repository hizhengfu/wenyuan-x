package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuTask
import io.hizhengfu.cloud.museum.dao.MuTasks
import io.hizhengfu.cloud.museum.domain.TaskDetailParam
import io.hizhengfu.cloud.museum.service.MuTaskService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 任务表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/task")
class MuTaskController(val muTaskService: MuTaskService) {

    /**
     * 添加任务表
     * */
    @Log("任务表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_CREATE')")
    fun create(@RequestBody @Validated muTask: MuTask): ApiData {
        val apiData = ApiData()

        val result = muTaskService.create(muTask)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新任务表
     * */
    @Log("任务表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_EDIT')")
    fun update(@RequestBody @Validated muTask: MuTask): ApiData {
        val muTaskDao = muTaskService.update(muTask)
        return ApiData("保存成功")
    }


    /**
     * 复制任务表
     * */
    @Log("任务表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muTaskDao = muTaskService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除任务表
     * */
    @Log("任务表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muTaskDao = muTaskService.delete(id)
        return ApiData("删除成功")
    }

    /**
     * 批量删除任务表
     * */
    @Log("批量任务表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muTaskService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表任务表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_SELECT')")
    fun list(muTask: MuTask, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muTaskService.findPage(request, muTask)

        page?.apply {
            list = data?.map { MuTasks.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询任务表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUTASK_ALL','MUTASK_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muTaskDao = muTaskService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muTaskDao.toModel()

        return apiData
    }


    @GetMapping("listTaskDetail")
    fun listTaskDetail(taskId: String): ApiData {
        val data = muTaskService.listTaskDetail(taskId)
        return ApiData(data = data)
    }

    @PostMapping("addTaskDetail")
    fun addTaskDetail(@RequestBody param: TaskDetailParam): ApiData {
        val id = muTaskService.addTaskDetail(param)
        return ApiData(data = id)
    }

    @PostMapping("saveTaskDetail")
    fun updateTaskDetail(@RequestBody params: List<TaskDetailParam>): ApiData {
        muTaskService.saveTaskDetail(params)
        return ApiData(message = "保存任务成功")
    }

    @PostMapping("updateTaskSort")
    fun updateTaskSort(@RequestBody params: List<GenericUpdateSort>): ApiData {
        muTaskService.updateSort(params)
        return ApiData(message = "更新任务排序成功")
    }

    @PostMapping("deleteTaskDetail")
    fun updateTaskDetail(id: String): ApiData {
        muTaskService.deleteTaskDetail(id)
        return ApiData(message = "删除任务成功")
    }
}
