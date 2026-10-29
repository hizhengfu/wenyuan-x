package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionSamplingDao
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionSamplingService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

data class SamplingCreateRequest(val collectionId:String,val title:String)
data class SamplingAdministrativeRequest(val planId:String,val evidence:CollectionAdministrativeEvidenceVo)

@RestController
@RequestMapping("\${apiPath}/collection/sampling")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SAMPLING_MANAGE')")
class CollectionSamplingController(
    private val service:CollectionSamplingService,
    private val originals:FileOriginalDownloadService,
):FlowController<CollectionSamplingDao>() {
    override fun flowService()=service
    @GetMapping("page") fun page(request:HttpServletRequest)=ApiData(data=service.page(request))
    @GetMapping("{id}") @PreAuthorize("isAuthenticated()")
    fun detail(@PathVariable id:String)=ApiData(data=service.detail(id))
    @GetMapping("{id}/original/{archiveId}/{fileId}") @PreAuthorize("isAuthenticated()") @Log("查阅有损取样归档原件")
    fun original(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        service.requireOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
    @PostMapping @Log("创建有损取样申请") fun create(@RequestBody v:SamplingCreateRequest)=ApiData(data=service.create(v.collectionId,v.title))
    @PostMapping("{id}/continue-extraction") @Log("关联前案并创建变更后续取样申请") fun continueExtraction(@PathVariable id:String,@RequestBody v:SamplingContinuationRequestVo)=ApiData(data=service.continueExtraction(id,v))
    @PostMapping("{id}/plan") @Log("登记保护修复与保管共同取样方案") fun plan(@PathVariable id:String,@RequestBody v:SamplingPlanVo)=ApiData(data=service.savePlan(id,v))
    @PostMapping("{id}/administrative") @Log("登记实际有损取样行政批准") fun administrative(@PathVariable id:String,@RequestBody v:SamplingAdministrativeRequest)=ApiData(data=service.administrative(id,v.planId,v.evidence))
    @PostMapping("{id}/start") @Log("登记有损取样实际开始") fun start(@PathVariable id:String,@RequestBody v:SamplingStartVo)=ApiData(data=service.start(id,v))
    @PostMapping("{id}/take") @Log("登记实际取样与样品保管") fun take(@PathVariable id:String,@RequestBody v:SamplingTakeVo)=ApiData(data=service.take(id,v))
    @PostMapping("{id}/end-extraction") @Log("登记实际取样结束与原件状况") fun end(@PathVariable id:String,@RequestBody v:SamplingExtractionEndVo)=ApiData(data=service.endExtraction(id,v))
    @PostMapping("{id}/sample/{sampleId}/analysis") @Log("登记实际样品分析消耗和剩余保管") fun analysis(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingAnalysisVo)=ApiData(data=service.analysis(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-batch") @Log("追加样品实际分批分析原件") fun analyzeBatch(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingBatchAnalysisVo)=ApiData(data=service.analyzeBatch(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/handover") @Log("登记样品实际交接及保管") fun handover(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleHandoverVo)=ApiData(data=service.handoverSample(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/discrepancy") @Log("登记样品实际差异及跟进") fun discrepancy(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleDiscrepancyVo)=ApiData(data=service.recordSampleDiscrepancy(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/discrepancy-review") @Log("登记样品差异调查与处置原件") fun reviewDiscrepancy(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleResolutionVo)=ApiData(data=service.reviewSampleDiscrepancy(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/discrepancy-confirm") @Log("本人核实样品差异调查结果") fun confirmDiscrepancy(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleConfirmationVo)=ApiData(data=service.confirmSampleDiscrepancy(id,sampleId,v))
    @PostMapping("{id}/complete-current") @Log("核对最新样品顺序记录并共同归档") fun completeCurrent(@PathVariable id:String,@RequestBody v:SamplingCompletionReviewVo)=ApiData(data=service.completeCurrent(id,v))
    @PostMapping("{id}/clear-incident") @Log("核对事故处置并解除取样原件限制") fun clearIncident(@PathVariable id:String,@RequestBody v:IncidentClearanceVo)=ApiData(data=service.clearIncident(id,v))
    @PostMapping("{id}/complete") @Log("登记取样共同复核与归档") fun complete(@PathVariable id:String,@RequestBody v:SamplingCompletionVo)=ApiData(data=service.complete(id,v))
    @PostMapping("{id}/no-sample-review") @Log("登记未取得样品的实际结案核查") fun noSampleReview(@PathVariable id:String,@RequestBody v:SamplingNoSampleReviewVo)=ApiData(data=service.reviewNoSampleClosure(id,v))
    @PostMapping("{id}/no-sample-confirm") @Log("本人确认未取得样品结案") fun noSampleConfirm(@PathVariable id:String,@RequestBody v:SamplingNoSampleConfirmationVo)=ApiData(data=service.confirmNoSampleClosure(id,v))
    @PostMapping("{id}/sample/{sampleId}/custody-handover") @Log("登记结案后样品实际保管交接") fun custodyHandover(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleHandoverVo)=ApiData(data=service.handoverSample(id,sampleId,v,true))
    @PostMapping("{id}/sample/{sampleId}/custody-discrepancy") @Log("登记结案后样品保管差异") fun custodyDiscrepancy(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleDiscrepancyVo)=ApiData(data=service.recordSampleDiscrepancy(id,sampleId,v,true))
    @PostMapping("{id}/sample/{sampleId}/custody-discrepancy-review") @Log("登记结案后样品差异调查处置") fun custodyReview(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleResolutionVo)=ApiData(data=service.reviewSampleDiscrepancy(id,sampleId,v,true))
    @PostMapping("{id}/sample/{sampleId}/custody-discrepancy-confirm") @Log("本人核实结案后样品保管调查") fun custodyConfirm(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleConfirmationVo)=ApiData(data=service.confirmSampleDiscrepancy(id,sampleId,v,true))
    @PostMapping("{id}/sample/{sampleId}/split") @Log("登记样品实际拆分与分份保管") fun splitSample(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleSplitVo)=ApiData(data=service.splitSample(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/custody-split") @Log("登记结案后样品实际拆分与保管") fun custodySplit(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleSplitVo)=ApiData(data=service.splitSample(id,sampleId,v,true))
    @PostMapping("{id}/sample/{sampleId}/disposition") @Log("登记样品实际非分析处置原件") fun disposition(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleDispositionVo)=ApiData(data=service.recordSampleDisposition(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/disposition-confirm") @Log("本人核实样品非分析处置") fun dispositionConfirm(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleDispositionConfirmationVo)=ApiData(data=service.confirmSampleDisposition(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/custody-disposition") @Log("登记结案后样品实际非分析处置") fun custodyDisposition(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleDispositionVo)=ApiData(data=service.recordSampleDisposition(id,sampleId,v,true))
    @PostMapping("{id}/sample/{sampleId}/custody-disposition-confirm") @Log("本人核实结案后样品非分析处置") fun custodyDispositionConfirm(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingSampleDispositionConfirmationVo)=ApiData(data=service.confirmSampleDisposition(id,sampleId,v,true))
    @PostMapping("{id}/stopped-review") @Log("登记已有样品项目的实际停作结案核查") fun stoppedReview(@PathVariable id:String,@RequestBody v:SamplingStoppedReviewVo)=ApiData(data=service.reviewStoppedClosure(id,v))
    @PostMapping("{id}/stopped-confirm") @Log("本人确认取样项目实际停作结案") fun stoppedConfirm(@PathVariable id:String,@RequestBody v:SamplingStoppedConfirmationVo)=ApiData(data=service.confirmStoppedClosure(id,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-proposal") @Log("登记旧样品后续分析共同方案") fun proposeExistingAnalysis(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingAnalysisProposalVo)=ApiData(data=service.proposeExistingAnalysis(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-confirm") @Log("本人核实旧样品分析方案或结束") fun confirmExistingAnalysis(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingAnalysisConfirmationVo)=ApiData(data=service.confirmExistingAnalysis(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-administrative") @Log("登记旧样品分析适用批准") fun approveExistingAnalysis(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingAnalysisAdministrativeVo)=ApiData(data=service.approveExistingAnalysis(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-activate") @Log("核实手续并启用旧样品分析方案") fun activateExistingAnalysis(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingAnalysisActivationVo)=ApiData(data=service.activateExistingAnalysis(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-existing") @Log("按变更方案登记旧样品实际分析") fun analyzeExistingSample(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingBatchAnalysisVo)=ApiData(data=service.analyzeExistingSample(id,sampleId,v))
    @PostMapping("{id}/sample/{sampleId}/analysis-close") @Log("登记旧样品分析实际结束核查") fun closeExistingAnalysis(@PathVariable id:String,@PathVariable sampleId:String,@RequestBody v:SamplingAnalysisClosureVo)=ApiData(data=service.closeExistingAnalysis(id,sampleId,v))

}
