package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.ReturnResponsibilityService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/return/{id}/responsibility")
class ReturnResponsibilityController(private val service:ReturnResponsibilityService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun detail(@PathVariable id:String,@RequestParam expectedOrgId:String)=ApiData(data=service.detail(id,expectedOrgId))

    @PostMapping
    @Log("登记归还异常责任清点移交")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun register(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody vo:ReturnResponsibilityVo)=ApiData(data=service.register(id,vo,expectedOrgId))

    @PostMapping("{recordId}/{role}")
    @Log("本人确认归还异常责任清点移交")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun sign(@PathVariable id:String,@PathVariable recordId:String,@PathVariable role:String,@RequestParam expectedOrgId:String,@RequestBody vo:CollectionCareSignatureVo)=ApiData(data=service.sign(id,recordId,role,vo,expectedOrgId))
}
