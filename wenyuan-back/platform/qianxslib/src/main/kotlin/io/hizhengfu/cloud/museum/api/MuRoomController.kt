package io.hizhengfu.cloud.museum.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.dao.MuRoom
import io.hizhengfu.cloud.museum.dao.MuRooms
import io.hizhengfu.cloud.museum.service.MuRoomService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 场馆表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/museum/room")
class MuRoomController(val muRoomService: MuRoomService) {

    /**
     * 添加场馆表
     * */
    @Log("场馆表创建")
    @PostMapping("create")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_CREATE')")
    fun create(@RequestBody @Validated muRoom: MuRoom): ApiData {
        val apiData = ApiData()

        val result = muRoomService.create(muRoom)

        if (result.id.value.isBlank()) {
            apiData.message = "保存失败"
        } else {
            apiData.data = result.id.value
        }
        return apiData
    }

    /**
     * 更新场馆表
     * */
    @Log("场馆表更新")
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_EDIT')")
    fun update(@RequestBody @Validated muRoom: MuRoom): ApiData {
        val muRoomDao = muRoomService.update(muRoom)
        return ApiData("保存成功")
    }


    /**
    * 复制场馆表
    * */
    @Log("场馆表删除")
    @PostMapping("copy/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_EDIT')")
    fun copy(@PathVariable id: String): ApiData {
        val muRoomDao = muRoomService.copy(id)
        return ApiData("复制成功")
    }


    /**
     * 删除场馆表
     * */
    @Log("场馆表删除")
    @PostMapping("delete/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_DELETE')")
    fun delete(@PathVariable id: String): ApiData {
        val muRoomDao = muRoomService.delete(id)
        return ApiData("删除成功")
    }

    /**
    * 批量删除场馆表
    * */
    @Log("批量场馆表删除")
    @PostMapping("delete")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_DELETE')")
    fun delete(@RequestBody ids: List<String>): ApiData {
        val count = muRoomService.delete(ids)
        return ApiData("删除成功${count}条数据")
    }

    /**
     * 列表场馆表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_SELECT')")
    fun list(muRoom: MuRoom, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = muRoomService.findPage(request, muRoom)

        page?.apply {
            list = data?.map { MuRooms.toModel(it) }
        }

        apiData.data = page

        return apiData
    }


    /**
     * 查询场馆表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','MUROOM_ALL','MUROOM_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        val apiData = ApiData()
        val muRoomDao = muRoomService.findById(id) ?: throw BadRequestException("数据不存在")

        apiData.data = muRoomDao.toModel()

        return apiData
    }

}
