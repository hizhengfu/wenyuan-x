package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.RequisitionRecallVo
import io.hizhengfu.cloud.collection.service.RequisitionRecallService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/joint-safety")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class RequisitionRecallController(private val service:RequisitionRecallService) {
    @PostMapping("{id}/recall")
    @Log("保管部门负责人登记提用藏品提前归库要求")
    fun register(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:RequisitionRecallVo)=ApiData(data=service.register(id,v,expectedOrgId))
}
