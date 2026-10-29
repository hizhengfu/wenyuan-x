package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuQuestion
import io.hizhengfu.cloud.museum.dao.MuQuestions
import io.hizhengfu.cloud.museum.service.MuQuestionService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 问答接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/question")
class MuQuestionController(val muQuestionService: MuQuestionService) {

    /**
     * 添加问答
     * */
    @Log("问答创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_CREATE')")
    fun create(@RequestBody @Validated muQuestion: MuQuestion): ApiData {
        val apiData = ApiData()

        val result = muQuestionService.create(muQuestion)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新问答
     * */
    @Log("问答更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_EDIT')")
    fun update(@RequestBody @Validated muQuestion: MuQuestion): ApiData {
        val muQuestionDao = muQuestionService.update(muQuestion)
        return ApiData("保存成功")
    }


    /**
    * 复制问答
    * */
    @Log("问答删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muQuestionDao = muQuestionService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除问答
     * */
    @Log("问答删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muQuestionDao = muQuestionService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除问答
    * */
    @Log("批量问答删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muQuestionService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表问答
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_SELECT')")
    fun list(muQuestion: MuQuestion, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muQuestionService.findPage(request, muQuestion)

        page?.apply {
            list = data?.map { MuQuestions.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询问答
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUQUESTION_ALL','MUQUESTION_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muQuestionDao = muQuestionService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muQuestionDao.toModel()

        return apiData
    }

}
