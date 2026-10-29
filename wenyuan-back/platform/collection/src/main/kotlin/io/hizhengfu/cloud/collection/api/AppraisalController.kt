package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.AppraisalDao
import io.hizhengfu.cloud.collection.dao.AppraisalVo
import io.hizhengfu.cloud.collection.service.AppraisalService
import io.hizhengfu.cloud.collection.service.CollectionPrintService
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

/** 鉴定。 */
@RestController
@RequestMapping("\${apiPath}/collection/appraisal")
class AppraisalController(
    private val appraisalService: AppraisalService,
    private val printService: CollectionPrintService,
) : FlowController<AppraisalDao>() {

    override fun flowService() = appraisalService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = appraisalService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = appraisalService.get(id))

    @Log("提交鉴定")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    fun apply(@RequestBody @Validated vo: AppraisalVo): ApiData = ApiData(data = appraisalService.apply(vo))

    @Log("提交鉴定审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销鉴定审批")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废鉴定审批")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @Log("依据鉴定发起藏品信息变更")
    @PostMapping("{id}/change")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT')")
    fun createChange(@PathVariable id: String): ApiData = ApiData(data = appraisalService.createChange(id))

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.appraisalPrint(id))
}
