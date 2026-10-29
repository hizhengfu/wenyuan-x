package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionTransferStatutoryService
import io.hizhengfu.cloud.collection.dao.CollectionTransferDao
import io.hizhengfu.cloud.collection.dao.CollectionTransferVo
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.CollectionTransferService
import io.hizhengfu.cloud.collection.service.CollectionTransferTerminationService
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

/** 跨馆调拨（流程驱动，多馆）。审批在待办中心确认，实际签署交接后才过户。 */
@RestController
@RequestMapping("\${apiPath}/collection/transfer")
class CollectionTransferController(
    private val transferService: CollectionTransferService,
    private val printService: CollectionPrintService,
    private val statutory: CollectionTransferStatutoryService,
    private val terminations: CollectionTransferTerminationService,
    private val originals: FileOriginalDownloadService,
) : FlowController<CollectionTransferDao>() {

    override fun flowService() = transferService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = transferService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = transferService.get(id))

    @GetMapping("{id}/archive")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT')")
    fun archive(@PathVariable id: String): ApiData = ApiData(data = transferService.archive(id))

    @GetMapping("{id}/original/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT')")
    @Log("查阅跨馆调拨案卷原件")
    fun original(@PathVariable id: String, @PathVariable fileId: String, response: HttpServletResponse) {
        transferService.requireOriginal(id, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.download(fileId, response)
    }

    @GetMapping("parties")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_LEDGER_CORRECT')")
    fun parties() = ApiData(data=statutory.parties())

    @GetMapping("{id}/receiving-locations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_LEDGER_CORRECT')")
    fun locations(@PathVariable id: String) = ApiData(data=statutory.receivingLocations(id))

    @Log("保存跨馆调拨")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun save(@RequestBody @Validated vo: CollectionTransferVo): ApiData = ApiData(data = transferService.save(vo))

    @Log("登记交换调拨行政原件")
    @PostMapping("{id}/administrative")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun administrative(@PathVariable id: String,@RequestBody vo: CollectionAdministrativeEvidenceVo) = ApiData(data=statutory.recordAdministrative(id,vo))

    @Log("签署执行交换调拨")
    @PostMapping("{id}/execute")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT')")
    fun execute(@PathVariable id: String,@RequestBody vo: CollectionTransferExecutionVo) = ApiData(data=transferService.execute(id,vo))

    @Log("登记交换调拨结果公告")
    @PostMapping("{id}/notice")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT')")
    fun notice(@PathVariable id: String,@RequestBody vo: DeregistrationNoticeVo) = ApiData(data=statutory.recordNotice(id,vo))

    @Log("提交调拨审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销调拨")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @GetMapping("{id}/termination")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT','COLLECTION_STORAGE')")
    fun termination(@PathVariable id: String) = ApiData(data = terminations.detail(id))

    @Log("提出已批准跨馆转移终止执行")
    @PostMapping("{id}/termination/propose")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun proposeTermination(@PathVariable id: String,@RequestBody value: CollectionTransferTerminationProposalVo) =
        ApiData(data = terminations.propose(id,value))

    @Log("本人确认已批准跨馆转移终止执行")
    @PostMapping("{id}/termination/confirm")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun confirmTermination(@PathVariable id: String,@RequestBody value: CollectionTransferTerminationConfirmationVo) =
        ApiData(data = terminations.confirm(id,value))

    @Log("作废调拨")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/public-reviews")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun publicReviews(@PathVariable id: String) = ApiData(data=statutory.publicReviewOptions(id))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CROSS_ORG','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.transferPrint(id))
}
