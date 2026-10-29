package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.dao.LoanVo
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.LoanStatutoryService
import io.hizhengfu.cloud.collection.service.LoanService
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

/** 借展/外展（流程驱动）。出馆审批在流程待办中心进行；返还为审批后动作。 */
@RestController
@RequestMapping("\${apiPath}/collection/loan")
class LoanController(
    private val loanService: LoanService,
    private val printService: CollectionPrintService,
    private val statutory: LoanStatutoryService,
    private val originals: FileOriginalDownloadService,
) : FlowController<LoanDao>() {

    override fun flowService() = loanService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = loanService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = loanService.get(id))

    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    @Log("查阅借用案卷指定阶段永久原件")
    fun archivedOriginal(@PathVariable id:String,@PathVariable archiveId:String,
        @PathVariable fileId:String,response:HttpServletResponse) {
        loanService.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @Log("保存借展单")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun save(@RequestBody @Validated vo: LoanVo): ApiData = ApiData(data = loanService.save(vo))

    @Log("提交借展审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销借展单")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废借展单")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @Log("借展返还")
    @PostMapping("{id}/return")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun returnBack(@PathVariable id: String): ApiData = ApiData(data = loanService.returnBack(id))

    @Log("登记借用行政报批或备案原件")
    @PostMapping("{id}/administrative")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun administrative(@PathVariable id: String,@RequestBody vo: LoanAdministrativeVo)=ApiData(data=statutory.recordAdministrative(id,vo))

    @Log("登记借用逐件签署点交")
    @PostMapping("{id}/handover")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun handover(@PathVariable id: String,@RequestBody vo: LoanHandoverVo)=ApiData(data=statutory.recordHandover(id,vo))

    @Log("登记在借检查催还及报告")
    @PostMapping("{id}/monitoring")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun monitoring(@PathVariable id: String,@RequestBody vo: LoanMonitoringVo)=ApiData(data=statutory.recordMonitoring(id,vo))

    @Log("登记出境展览实际延期批准及补充协议")
    @PostMapping("{id}/overseas-extension")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun extension(@PathVariable id:String,@org.springframework.web.bind.annotation.RequestParam expectedOrgId:String,@RequestBody vo:LoanOverseasExtensionVo)=
        ApiData(data=statutory.recordExtension(id,vo,expectedOrgId))

    @Log("登记出境或复进境审核及海关手续")
    @PostMapping("{id}/overseas-port")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun port(@PathVariable id:String,@org.springframework.web.bind.annotation.RequestParam expectedOrgId:String,@RequestBody vo:LoanPortClearanceVo)=
        ApiData(data=statutory.recordPort(id,vo,expectedOrgId))

    @Log("登记出境协议备案或展览结项报送")
    @PostMapping("{id}/overseas-filing")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun overseasFiling(@PathVariable id:String,@org.springframework.web.bind.annotation.RequestParam expectedOrgId:String,@RequestBody vo:LoanOverseasFilingVo)=
        ApiData(data=statutory.recordOverseasFiling(id,vo,expectedOrgId))

    @Log("核实出境展览实际结束事实")
    @PostMapping("{id}/exhibition-end")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun exhibitionEnd(@PathVariable id:String,@org.springframework.web.bind.annotation.RequestParam expectedOrgId:String,@RequestBody vo:LoanExhibitionEndVo)=
        ApiData(data=statutory.recordExhibitionEnd(id,vo,expectedOrgId))

    @Log("执行借展出库")
    @PostMapping("{id}/execute-out")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun executeOut(@PathVariable id: String, @RequestBody vo: CollectionOutboundExecutionVo): ApiData =
        ApiData(data = loanService.executeOut(id, vo))

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.loanPrint(id))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)
}
