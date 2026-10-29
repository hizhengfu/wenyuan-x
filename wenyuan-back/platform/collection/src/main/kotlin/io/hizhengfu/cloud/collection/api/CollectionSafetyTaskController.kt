package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionSafetyReminderService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/safety-task")
class CollectionSafetyTaskController(private val service:CollectionSafetyReminderService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE','COLLECTION_EMERGENCY_MANAGE','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request:HttpServletRequest,@RequestParam(required=false) orgId:String?,@RequestParam(required=false) domain:EnumCollectionSafetyDomain?,@RequestParam(required=false) status:String?,@RequestParam(required=false) keyword:String?)=ApiData(data=service.page(GenericPage<Map<String,Any?>>().build(request),orgId,domain,status,keyword))
    @GetMapping("options")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE','COLLECTION_EMERGENCY_MANAGE','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun options()=ApiData(data=service.options())
    @GetMapping("{orgId}/context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE','COLLECTION_EMERGENCY_MANAGE','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun context(@PathVariable orgId:String)=ApiData(data=service.context(orgId))
    @GetMapping("{orgId}/settings")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SAFETY_REMINDER_MANAGE')")
    fun settings(@PathVariable orgId:String)=ApiData(data=service.settings(orgId))
    @PostMapping("{orgId}/settings") @Log("调整安全待办站内提醒设置")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SAFETY_REMINDER_MANAGE')")
    fun settings(@PathVariable orgId:String,@RequestBody v:CollectionSafetyReminderSettingsVo)=ApiData(data=service.saveSettings(orgId,v))
    @GetMapping("{orgId}/deliveries")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SAFETY_REMINDER_MANAGE')")
    fun deliveries(@PathVariable orgId:String,request:HttpServletRequest)=ApiData(data=service.history(orgId,GenericPage<Map<String,Any?>>().build(request)))
}
