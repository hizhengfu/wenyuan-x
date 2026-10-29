package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuAlarmRule
import io.hizhengfu.cloud.museum.dao.MuAlarmRules
import io.hizhengfu.cloud.museum.service.MuAlarmRuleService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * mu_alarm_rule接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/rule")
class MuAlarmRuleController(val muAlarmRuleService: MuAlarmRuleService) {

    /**
     * 添加mu_alarm_rule
     * */
    @Log("mu_alarm_rule创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_CREATE')")
    fun create(@RequestBody @Validated muAlarmRule: MuAlarmRule): ApiData {
        val apiData = ApiData()

        val result = muAlarmRuleService.create(muAlarmRule)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新mu_alarm_rule
     * */
    @Log("mu_alarm_rule更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_EDIT')")
    fun update(@RequestBody @Validated muAlarmRule: MuAlarmRule): ApiData {
        val muAlarmRuleDao = muAlarmRuleService.update(muAlarmRule)
        return ApiData("保存成功")
    }


    /**
    * 复制mu_alarm_rule
    * */
    @Log("mu_alarm_rule删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muAlarmRuleDao = muAlarmRuleService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除mu_alarm_rule
     * */
    @Log("mu_alarm_rule删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muAlarmRuleDao = muAlarmRuleService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除mu_alarm_rule
    * */
    @Log("批量mu_alarm_rule删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muAlarmRuleService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表mu_alarm_rule
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_SELECT')")
    fun list(muAlarmRule: MuAlarmRule, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muAlarmRuleService.findPage(request, muAlarmRule)

        page?.apply {
            list = data?.map { MuAlarmRules.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询mu_alarm_rule
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUALARMRULE_ALL','MUALARMRULE_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muAlarmRuleDao = muAlarmRuleService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muAlarmRuleDao.toModel()

        return apiData
    }

}
