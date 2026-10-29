package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.RequisitionDao
import io.hizhengfu.cloud.collection.dao.RequisitionVo
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.RequisitionService
import io.hizhengfu.cloud.collection.domain.RequisitionHandoverVo
import io.hizhengfu.cloud.collection.service.RequisitionStatutoryService
import io.hizhengfu.cloud.collection.domain.CollectionOutboundExecutionVo
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 提用单（出入库流程）。审批在流程待办中心进行，通过后出库；归还入库为审批后动作。 */
@RestController
@RequestMapping("\${apiPath}/collection/requisition")
class RequisitionController(
    private val requisitionService: RequisitionService,
    private val printService: CollectionPrintService,
    private val statutory: RequisitionStatutoryService,
    private val originals: FileOriginalDownloadService,
) : FlowController<RequisitionDao>() {

    override fun flowService() = requisitionService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = requisitionService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = requisitionService.get(id))

    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    @Log("查阅提用案卷指定阶段永久原件")
    fun archivedOriginal(@PathVariable id:String,@PathVariable archiveId:String,
        @PathVariable fileId:String,response:HttpServletResponse) {
        requisitionService.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @Log("保存提用单")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun save(@RequestBody @Validated vo: RequisitionVo): ApiData = ApiData(data = requisitionService.save(vo))

    @Log("提交提用审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销提用单")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废提用单")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @Log("提用归还入库")
    @PostMapping("{id}/return")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun returnBack(@PathVariable id: String): ApiData = ApiData(data = requisitionService.returnBack(id))

    @Log("登记提用逐件点交原件")
    @PostMapping("{id}/handover")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun handover(@PathVariable id: String,@RequestBody vo: RequisitionHandoverVo) = ApiData(data=statutory.recordHandover(id,vo))

    @Log("执行提用出库")
    @PostMapping("{id}/execute-out")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun executeOut(@PathVariable id: String, @RequestBody vo: CollectionOutboundExecutionVo): ApiData =
        ApiData(data = requisitionService.executeOut(id, vo))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.requisitionPrint(id))
}
