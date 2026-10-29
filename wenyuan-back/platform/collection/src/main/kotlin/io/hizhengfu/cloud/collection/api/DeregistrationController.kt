package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.DeregistrationDao
import io.hizhengfu.cloud.collection.dao.DeregistrationVo
import io.hizhengfu.cloud.collection.service.DeregistrationService
import io.hizhengfu.cloud.collection.service.DeregistrationStatutoryService
import io.hizhengfu.cloud.collection.domain.DeregistrationExpertOpinionVo
import io.hizhengfu.cloud.collection.domain.DeregistrationNoticeVo
import io.hizhengfu.cloud.collection.domain.DeregistrationExecutionVo
import io.hizhengfu.cloud.collection.domain.CollectionAdministrativeEvidenceVo
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 藏品退出馆藏（流程驱动，逐级报批）。审批在流程待办中心进行。 */
@RestController
@RequestMapping("\${apiPath}/collection/deregistration")
class DeregistrationController(private val deregistrationService: DeregistrationService, private val statutory: DeregistrationStatutoryService,
    private val originals: FileOriginalDownloadService) : FlowController<DeregistrationDao>() {

    override fun flowService() = deregistrationService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_DEREGISTER')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = deregistrationService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_DEREGISTER')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = deregistrationService.get(id))

    @GetMapping("{id}/archive")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_DEREGISTER')")
    fun archive(@PathVariable id: String) = ApiData(data=deregistrationService.archive(id))

    @GetMapping("{id}/original/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_DEREGISTER')")
    @Log("查阅藏品退出案卷原件")
    fun original(@PathVariable id: String, @PathVariable fileId: String, response: HttpServletResponse) {
        deregistrationService.requireOriginal(id, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.download(fileId, response)
    }

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_DEREGISTER')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = deregistrationService.print(id))

    @Log("保存退出馆藏申请")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    fun save(@RequestBody @Validated vo: DeregistrationVo): ApiData = ApiData(data = deregistrationService.save(vo))

    @Log("提交退出馆藏审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销退出馆藏申请")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废退出馆藏申请")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @Log("登记藏品退出专家独立意见")
    @PostMapping("{id}/expert")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    fun expert(@PathVariable id: String,@RequestBody vo: DeregistrationExpertOpinionVo) = ApiData(data=statutory.recordExpert(id,vo))

    @GetMapping("{id}/public-reviews")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    fun publicReviews(@PathVariable id: String) = ApiData(data=statutory.publicReviewOptions(id))

    @Log("登记藏品退出公示公告实发凭据")
    @PostMapping("{id}/notice")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    fun notice(@PathVariable id: String,@RequestBody vo: DeregistrationNoticeVo) = ApiData(data=statutory.recordNotice(id,vo))

    @Log("登记藏品退出行政批准备案凭证")
    @PostMapping("{id}/administrative")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    fun administrative(@PathVariable id: String,@RequestBody vo: CollectionAdministrativeEvidenceVo) = ApiData(data=statutory.recordAdministrative(id,vo))

    @Log("执行藏品退出并永久归档")
    @PostMapping("{id}/execute")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")
    fun execute(@PathVariable id: String,@RequestBody vo: DeregistrationExecutionVo) = ApiData(data=deregistrationService.execute(id,vo))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_DEREGISTER')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)
}
