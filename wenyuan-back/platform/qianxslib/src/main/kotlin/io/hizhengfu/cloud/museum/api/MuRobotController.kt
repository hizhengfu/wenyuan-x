package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuRobot
import io.hizhengfu.cloud.museum.dao.MuRobots
import io.hizhengfu.cloud.museum.service.MuRobotService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 机器人表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/robot")
class MuRobotController(val muRobotService: MuRobotService) {

    /**
     * 添加机器人表
     * */
    @Log("机器人表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_CREATE')")
    fun create(@RequestBody @Validated muRobot: MuRobot): ApiData {
        val apiData = ApiData()

        val result = muRobotService.create(muRobot)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新机器人表
     * */
    @Log("机器人表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_EDIT')")
    fun update(@RequestBody @Validated muRobot: MuRobot): ApiData {
        val muRobotDao = muRobotService.update(muRobot)
        return ApiData("保存成功")
    }


    /**
    * 复制机器人表
    * */
    @Log("机器人表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muRobotDao = muRobotService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除机器人表
     * */
    @Log("机器人表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muRobotDao = muRobotService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除机器人表
    * */
    @Log("批量机器人表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muRobotService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表机器人表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_SELECT')")
    fun list(muRobot: MuRobot, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muRobotService.findPage(request, muRobot)

        page?.apply {
            list = data?.map { MuRobots.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询机器人表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROBOT_ALL','MUROBOT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muRobotDao = muRobotService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muRobotDao.toModel()

        return apiData
    }

}
