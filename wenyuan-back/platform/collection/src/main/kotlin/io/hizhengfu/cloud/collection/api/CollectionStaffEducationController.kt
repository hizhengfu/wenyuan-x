package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionStaffEducationService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/** 每个入口在服务中重新核对当前人员、权限和馆别；登录快照不替代当前资格。 */
@RestController
@RequestMapping("\${apiPath}/collection/staff-education")
@PreAuthorize("isAuthenticated()")
class CollectionStaffEducationController(private val service:CollectionStaffEducationService) {
    @GetMapping("context")
    fun context()=ApiData(data=service.context())
    @GetMapping("people")
    fun people(@RequestParam orgId:String)=ApiData(data=service.people(orgId))
    @GetMapping
    fun list(@RequestParam orgId:String,@RequestParam(required=false) userId:String?,@RequestParam(defaultValue="1") pageNum:Int,@RequestParam(defaultValue="20") pageSize:Int)=ApiData(data=service.list(orgId,userId,pageNum,pageSize))
    @GetMapping("{id}")
    fun detail(@PathVariable id:String)=ApiData(data=service.detail(id))
    @PostMapping
    @Log("登记保管人员岗位教育需求及任职依据")
    fun create(@RequestParam orgId:String,@RequestBody v:StaffEducationCreate)=ApiData(data=service.create(orgId,v))
    @PostMapping("{id}/revision")
    @Log("追加岗位教育参加情况及更正版本")
    fun revise(@PathVariable id:String,@RequestBody v:StaffEducationRevision)=ApiData(data=service.revise(id,v))
    @PostMapping("{id}/versions/{version}/confirmation")
    @Log("本人核实岗位培训或继续教育需求完成情况")
    fun confirm(@PathVariable id:String,@PathVariable version:Int,@RequestBody v:StaffEducationConfirmationVo)=ApiData(data=service.confirm(id,version,v))
}
