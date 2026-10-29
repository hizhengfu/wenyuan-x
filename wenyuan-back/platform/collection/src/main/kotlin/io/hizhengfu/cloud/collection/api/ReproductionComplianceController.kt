package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.ReproductionComplianceService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/reproduction-compliance")
class ReproductionComplianceController(private val service: ReproductionComplianceService,
    private val originals: FileOriginalDownloadService) {
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun detail(@PathVariable id: String) = ApiData(data=service.detail(id))

    @GetMapping("{id}/plan/{planId}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    @Log("查阅复制拓印指定阶段永久原件")
    fun archivedOriginal(@PathVariable id:String,@PathVariable planId:String,
        @PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        service.requireArchivedOriginal(id,planId,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @GetMapping("{id}/units")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun units(@PathVariable id: String) = ApiData(data=service.units(id))

    @PostMapping("{id}/plan")
    @Log("登记复制拓印实际方案与资质核查原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun plan(@PathVariable id: String,@RequestBody v: ReproductionPlanVo)=ApiData(data=service.savePlan(id,v))

    @PostMapping("{id}/administrative")
    @Log("登记复制拓印真实行政批文")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun administrative(@PathVariable id: String,@RequestBody v: ReproductionAdministrativeVo)=ApiData(data=service.administrative(id,v))

    @PostMapping("{id}/start")
    @Log("登记复制拓印实际开始及安全核查")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun start(@PathVariable id: String,@RequestBody v: ReproductionStartVo)=ApiData(data=service.start(id,v))

    @PostMapping("{id}/safety")
    @Log("登记复制拓印实际安全检查及处置")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun safety(@PathVariable id: String,@RequestBody v: ReproductionSafetyVo)=ApiData(data=service.safety(id,v))

    @PostMapping("{id}/output")
    @Log("登记复制品拓片数量标识和实际保管")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun output(@PathVariable id: String,@RequestBody v: ReproductionOutputVo)=ApiData(data=service.output(id,v))

    @PostMapping("{id}/complete")
    @Log("登记复制拓印实际完成及原件状况")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun complete(@PathVariable id: String,@RequestBody v: ReproductionCompletionVo)=ApiData(data=service.complete(id,v))

    @PostMapping("{id}/stop")
    @Log("登记复制拓印实际停止及原件保管")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun stop(@PathVariable id:String,@RequestBody v:ReproductionStopVo)=ApiData(data=service.stop(id,v))

    @PostMapping("{id}/clear-stop")
    @Log("核对复制拓印停止后的实际事故处置")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun clearStop(@PathVariable id:String,@RequestBody v:IncidentClearanceVo)=ApiData(data=service.clearStop(id,v))

    @PostMapping("{id}/output/{outputId}/use")
    @Log("登记复制品拓片实际使用")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun use(@PathVariable id: String,@PathVariable outputId: String,@RequestBody v: ReproductionOutputUseVo)=ApiData(data=service.recordUse(id,outputId,v))

    @PostMapping("{id}/output/{outputId}/custody")
    @Log("登记复制品拓片实际保管交接")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun custody(@PathVariable id:String,@PathVariable outputId:String,@RequestBody v:ReproductionOutputCustodyVo)=ApiData(data=service.transferCustody(id,outputId,v))

    @PostMapping("{id}/output/{outputId}/use/{useId}/return")
    @Log("登记复制品拓片实际归还签署原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun returned(@PathVariable id: String,@PathVariable outputId: String,@PathVariable useId: String,@RequestBody v: ReproductionOutputReturnVo)=ApiData(data=service.returnOutput(id,outputId,useId,v))
}
