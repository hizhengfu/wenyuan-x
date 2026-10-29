package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuVisitor
import io.hizhengfu.cloud.museum.dao.MuVisitors
import io.hizhengfu.cloud.museum.service.MuVisitorService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 游客表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/visitor")
class MuVisitorController(val muVisitorService: MuVisitorService) {

    /**
     * 添加游客表
     * */
    @Log("游客表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_CREATE')")
    fun create(@RequestBody @Validated muVisitor: MuVisitor): ApiData {
        val apiData = ApiData()

        val result = muVisitorService.create(muVisitor)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新游客表
     * */
    @Log("游客表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_EDIT')")
    fun update(@RequestBody @Validated muVisitor: MuVisitor): ApiData {
        val muVisitorDao = muVisitorService.update(muVisitor)
        return ApiData("保存成功")
    }


    /**
    * 复制游客表
    * */
    @Log("游客表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muVisitorDao = muVisitorService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除游客表
     * */
    @Log("游客表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muVisitorDao = muVisitorService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除游客表
    * */
    @Log("批量游客表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muVisitorService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表游客表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_SELECT')")
    fun list(muVisitor: MuVisitor, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muVisitorService.findPage(request, muVisitor)

        page?.apply {
            list = data?.map { MuVisitors.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询游客表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUVISITOR_ALL','MUVISITOR_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muVisitorDao = muVisitorService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muVisitorDao.toModel()

        return apiData
    }

}
