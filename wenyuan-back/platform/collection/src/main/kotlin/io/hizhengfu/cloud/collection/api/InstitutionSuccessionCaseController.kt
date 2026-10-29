package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InstitutionSuccessionCaseService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/institution-succession")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_CROSS_ORG','COLLECTION_LEDGER_CORRECT')")
class InstitutionSuccessionCaseController(private val service:InstitutionSuccessionCaseService,private val items:io.hizhengfu.cloud.collection.service.InstitutionItemHandoverService,private val investigations:io.hizhengfu.cloud.collection.service.InstitutionItemInvestigationService,private val originals:FileOriginalDownloadService) {
    @GetMapping("context") fun context()=ApiData(data=service.context())
    @GetMapping("page") fun page(@RequestParam(defaultValue="1") page:Int,@RequestParam(defaultValue="20") pageSize:Int)=ApiData(data=service.page(page,pageSize))
    @GetMapping("{id}") fun detail(@PathVariable id:String,@RequestParam(defaultValue="1") page:Int,@RequestParam(defaultValue="20") pageSize:Int)=ApiData(data=service.detail(id,page,pageSize))
    @GetMapping("{id}/original/{sourceId}") @Log("查阅机构终止合并统筹案卷指定永久原件")
    fun original(@PathVariable id:String,@PathVariable sourceId:String,@RequestParam fileId:String,response:HttpServletResponse) {
        val (archiveId,actualFileId)=service.requireArchivedOriginal(id,sourceId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(actualFileId,archiveId,response)
    }
    @PostMapping @Log("建立终止合并全馆统筹案卷") fun create(@RequestParam expectedOrgId:String,@RequestBody v:InstitutionSuccessionCaseVo)=ApiData(data=service.create(v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/scope") @Log("记录全馆实际清点范围及系统清册版本") fun capture(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionScopeCaptureVo)=ApiData(data=service.capture(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/unrecorded") @Log("登记清点发现的未入系统对象") fun unrecorded(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionUnrecordedVo)=ApiData(data=service.addUnrecorded(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/follow-up") @Log("追加承接未完成事项及实际处理") fun followUp(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemFollowUpVo)=ApiData(data=service.followUp(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/resolution") @Log("核实清册对象真实完成依据") fun resolve(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemResolutionVo)=ApiData(data=service.resolve(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/keeper-review") @Log("保管负责人本人核对全馆承接清册") fun keeper(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionScopeReviewVo)=ApiData(data=service.review(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/representative-review") @Log("依法授权人员本人复核全馆承接清册") fun representative(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionScopeReviewVo)=ApiData(data=service.review(id,v,true,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-proposal") @Log("登记清册对象分类及实际承接方案") fun proposal(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemProposalVo)=ApiData(data=items.propose(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-keeper") @Log("保管负责人本人核对清册对象分类方案") fun itemKeeper(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemApprovalVo)=ApiData(data=items.approve(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-authorization") @Log("依法授权人员本人确认清册对象分类方案") fun itemAuthorization(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemApprovalVo)=ApiData(data=items.approve(id,v,true,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-handover") @Log("登记其他物品实际交接批次原件") fun handover(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemHandoverVo)=ApiData(data=items.handOver(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-investigation") @Log("追加实际交接差异调查及处理原件") fun investigate(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemInvestigationVo)=ApiData(data=investigations.investigate(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-investigation-keeper") @Log("保管负责人本人核对交接差异处理") fun investigationKeeper(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemInvestigationConfirmationVo)=ApiData(data=investigations.confirm(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/item-investigation-authorization") @Log("依法负责人员本人复核交接差异处理") fun investigationAuthorization(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:InstitutionItemInvestigationConfirmationVo)=ApiData(data=investigations.confirm(id,v,true,expectedOrgId=expectedOrgId))
}
