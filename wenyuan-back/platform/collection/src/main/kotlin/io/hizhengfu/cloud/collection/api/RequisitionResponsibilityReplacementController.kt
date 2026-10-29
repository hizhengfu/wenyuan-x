package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.RequisitionReplacementRequest
import io.hizhengfu.cloud.collection.service.RequisitionResponsibilityReplacementService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/requisition/{id}/responsibility-replacement")
class RequisitionResponsibilityReplacementController(private val service: RequisitionResponsibilityReplacementService) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun get(@PathVariable id: String) = ApiData(data = service.get(id))

    @PostMapping
    @Log("登记未点交提用责任变更并创建重新审批草稿")
    @PreAuthorize("isAuthenticated()")
    fun replace(@PathVariable id: String, @RequestBody value: RequisitionReplacementRequest) = ApiData(data = service.replace(id, value))
}
