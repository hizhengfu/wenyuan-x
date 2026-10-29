package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/joint-safety")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class CollectionJointSafetyController(private val service:CollectionJointSafetyService) {
    @GetMapping("page") fun page(request:HttpServletRequest)=ApiData(data=service.page(request))
    @GetMapping("{id}") fun detail(@PathVariable id:String)=ApiData(data=service.detail(id))
    @PostMapping("{id}/round/{roundId}/report-current") @Log("据实登记向现任主要负责人的首次问题报告") fun reportCurrent(@PathVariable id:String,@PathVariable roundId:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionJointCurrentReportVo)=ApiData(data=service.reportToCurrentDirector(id,roundId,v,expectedOrgId))
    @PostMapping("{id}/round/{roundId}/report-handover") @Log("登记共同检查问题报告实际移交") fun handOverReport(@PathVariable id:String,@PathVariable roundId:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionJointReportHandoverVo)=ApiData(data=service.handOverReport(id,roundId,v,expectedOrgId))
    @PostMapping("{id}/round/{roundId}/report-handover/{recordId}/{role}") @Log("本人核对报告移交及新任负责人收悉处理") fun signReportHandover(@PathVariable id:String,@PathVariable roundId:String,@PathVariable recordId:String,@PathVariable role:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.signReportHandover(id,roundId,recordId,role,v,expectedOrgId))
    @PostMapping("{id}/custody") @Log("登记实际提用后在用责任承接") fun custody(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:RequisitionCustodyRequest)=ApiData(data=service.registerCustody(id,v,expectedOrgId))
    @PostMapping("{id}/custody/{recordId}/{role}") @Log("本人确认实际在用责任承接") fun signCustody(@PathVariable id:String,@PathVariable recordId:String,@PathVariable role:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.signCustody(id,recordId,role,v,expectedOrgId))
    @PostMapping("{id}/plan") @Log("制定提用藏品多部门定期安全检查方案") fun plan(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionJointSafetyPlanVo)=ApiData(data=service.savePlan(id,v,expectedOrgId))
    @PostMapping("{id}/plan/{planId}/{role}") @Log("本人确认多部门安全检查职责及方案") fun signPlan(@PathVariable id:String,@PathVariable planId:String,@PathVariable role:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.signPlan(id,planId,role,v,expectedOrgId))
    @PostMapping("{id}/round") @Log("登记实际多部门共同安全检查") fun round(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionJointSafetyRoundVo)=ApiData(data=service.recordRound(id,v,expectedOrgId))
    @PostMapping("{id}/round/{roundId}/sign/{role}") @Log("本人核对共同检查原件并确认实际参与") fun signRound(@PathVariable id:String,@PathVariable roundId:String,@PathVariable role:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.signRound(id,roundId,role,v,expectedOrgId))
    @PostMapping("{id}/round/{roundId}/report") @Log("登记共同检查问题向主要负责人实际报告") fun report(@PathVariable id:String,@PathVariable roundId:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionJointSafetyReportVo)=ApiData(data=service.report(id,roundId,v,expectedOrgId))
    @PostMapping("{id}/round/{roundId}/director") @Log("主要负责人本人收悉共同检查报告及处理意见") fun director(@PathVariable id:String,@PathVariable roundId:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.acknowledge(id,roundId,v,expectedOrgId))
}
