package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionReturnDao
import io.hizhengfu.cloud.collection.dao.CollectionReturnVo
import io.hizhengfu.cloud.collection.service.ReturnStatutoryService
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionReturnService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 藏品归还/返还流程。审批通过后才执行入库回库。 */
@RestController
@RequestMapping("\${apiPath}/collection/return")
class CollectionReturnController(
    private val returnService: CollectionReturnService,
    private val statutory: ReturnStatutoryService,
    private val originals: FileOriginalDownloadService,
) : FlowController<CollectionReturnDao>() {

    override fun flowService() = returnService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = returnService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = returnService.get(id))

    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    @Log("查阅归还案卷指定阶段永久原件")
    fun archivedOriginal(@PathVariable id:String,@PathVariable archiveId:String,
        @PathVariable fileId:String,response:HttpServletResponse) {
        returnService.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @Log("保存归还单")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun save(@RequestBody @Validated vo: CollectionReturnVo): ApiData = ApiData(data = returnService.save(vo))

    @Log("生成提用归还草稿")
    @PostMapping("requisition/{id}/ensure")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun ensureRequisition(@PathVariable id: String): ApiData = ApiData(data = returnService.ensureDraftFromRequisition(id))

    @Log("生成借展返还草稿")
    @PostMapping("loan/{id}/ensure")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun ensureLoan(@PathVariable id: String): ApiData = ApiData(data = returnService.ensureDraftFromLoan(id))

    @Log("提交归还审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销归还单")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废归还单")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @Log("登记归还逐件验收及双方签署")
    @PostMapping("{id}/inspection")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun inspection(@PathVariable id: String,@RequestBody vo: ReturnInspectionVo)=ApiData(data=statutory.recordInspection(id,vo))

    @Log("登记归还实际接收保管")
    @PostMapping("{id}/execute")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun execute(@PathVariable id: String)=ApiData(data=returnService.executeApprovedReturn(id))

    @Log("登记归还异常实际报告")
    @PostMapping("{id}/report")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun report(@PathVariable id: String,@RequestParam expectedInspectionArchiveId:String,@RequestBody vo: ReturnReportVo)=
        ApiData(data=statutory.recordReport(id,vo,expectedInspectionArchiveId))

    @Log("登记归还异常处理及解除待核查")
    @PostMapping("{id}/resolve")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun resolve(@PathVariable id: String,@RequestParam expectedOrgId:String,@RequestBody vo: ReturnResolutionVo)=ApiData(data=returnService.resolveIssue(id,vo,expectedOrgId))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)
}
