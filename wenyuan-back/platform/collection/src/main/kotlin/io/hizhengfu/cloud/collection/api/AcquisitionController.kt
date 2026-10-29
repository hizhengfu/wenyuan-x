package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.AcquisitionDao
import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.domain.AcquisitionImportRowVo
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.collection.service.AcquisitionService
import io.hizhengfu.cloud.collection.service.CollectionPrintService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 征集入藏（流程驱动）。审批通过进入「待入馆」，由入馆凭证批量登账。 */
@RestController
@RequestMapping("\${apiPath}/collection/acquisition")
class AcquisitionController(
    private val acquisitionService: AcquisitionService,
    private val printService: CollectionPrintService,
    private val compliance: io.hizhengfu.cloud.collection.service.AcquisitionComplianceService,
    private val recognition: io.hizhengfu.cloud.collection.service.DonationRecognitionService,
    private val originals: FileOriginalDownloadService,
) : FlowController<AcquisitionDao>() {

    @GetMapping("{id}/recognitions")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun recognitions(@PathVariable id:String) = ApiData(data=recognition.list(id))

    @Log("登记捐赠鼓励及留痕更正")
    @PostMapping("{id}/recognitions")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun recognition(@PathVariable id:String,@RequestBody vo:io.hizhengfu.cloud.collection.domain.DonationRecognitionVo) = ApiData(data=recognition.record(id,vo))

    override fun flowService() = acquisitionService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun page(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) proposedLevel: EnumRelicLevel?,
        @RequestParam(required = false) source: EnumSource?,
        @RequestParam(required = false) flowStatus: EnumFlowStatus?,
        request: HttpServletRequest,
    ): ApiData = ApiData(data = acquisitionService.findPage(request, keyword, proposedLevel, source, flowStatus))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = acquisitionService.get(id))

    @GetMapping("{id}/compliance")
    @PreAuthorize("isAuthenticated()")
    fun compliance(@PathVariable id:String) = ApiData(data=compliance.detail(id))

    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("isAuthenticated()")
    @Log("查阅征集指定阶段保密归档原件")
    fun archivedOriginal(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        compliance.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @Log("登记征集入藏标准与来源调查")
    @PostMapping("{id}/dossier")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun dossier(@PathVariable id:String,@RequestBody vo:io.hizhengfu.cloud.collection.domain.AcquisitionDossierVo) = ApiData(data=compliance.saveDossier(id,vo))

    @Log("登记征集专家逐件独立意见")
    @PostMapping("{id}/expert")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun expert(@PathVariable id:String,@RequestBody vo:io.hizhengfu.cloud.collection.domain.AcquisitionExpertOpinionVo) = ApiData(data=compliance.recordExpert(id,vo))

    @Log("归档征集估价决策与协议履行材料")
    @PostMapping("{id}/completion")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun completion(@PathVariable id:String,@RequestBody vo:io.hizhengfu.cloud.collection.domain.AcquisitionCompletionVo) = ApiData(data=compliance.complete(id,vo))

    @Log("保存征集申请")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun save(@RequestBody @Validated(io.hizhengfu.cloud.collection.domain.AcquisitionDraftValidation::class) vo: AcquisitionVo): ApiData = ApiData(data = acquisitionService.save(vo))

    @GetMapping("import/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun importTemplate(): ApiData = ApiData(data = acquisitionService.importTemplate())

    @PostMapping("import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun validateImport(@RequestBody rows: List<AcquisitionImportRowVo>): ApiData = ApiData(data = acquisitionService.validateImport(rows))

    @Log("批量导入征集申请")
    @PostMapping("import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun commitImport(@RequestBody rows: List<AcquisitionImportRowVo>): ApiData = ApiData(data = acquisitionService.commitImport(rows))

    @Log("提交征集审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销征集申请")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废征集申请")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.acquisitionPrint(id))
}
