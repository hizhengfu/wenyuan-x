package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.AcquisitionPlanDao
import io.hizhengfu.cloud.collection.dao.AcquisitionPlanVo
import io.hizhengfu.cloud.collection.domain.AcquisitionPlanOfflineApprovalVo
import io.hizhengfu.cloud.collection.service.AcquisitionPlanService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 征集计划（流程驱动）：年度/月度计划编制→专家评审→分管馆长审批。 */
@RestController
@RequestMapping("\${apiPath}/collection/acquisition-plan")
class AcquisitionPlanController(
    private val planService: AcquisitionPlanService,
) : FlowController<AcquisitionPlanDao>() {

    override fun flowService() = planService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = planService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = planService.get(id))

    @Log("保存征集计划")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun save(@RequestBody @Validated vo: AcquisitionPlanVo): ApiData = ApiData(data = planService.save(vo))

    @Log("归档线下已批准征集计划")
    @PostMapping("{id}/offline-approval")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun archiveOfflineApproval(@PathVariable id: String, @RequestBody vo: AcquisitionPlanOfflineApprovalVo): ApiData =
        ApiData(data = planService.archiveOfflineApproval(id, vo))

    @Log("提交征集计划审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销征集计划")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废征集计划")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)
}
