package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityService
import io.hizhengfu.cloud.collection.service.CollectionFragileMonitoringService
import io.hizhengfu.cloud.collection.service.CollectionFragileAdjustmentService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/fragility/{collectionId}")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class CollectionFragilityController(private val service:CollectionFragilityService,private val adjustments:CollectionFragileAdjustmentService,private val monitoring:CollectionFragileMonitoringService) {
    @GetMapping fun detail(@PathVariable collectionId:String)=ApiData(data=service.detail(collectionId))
    @GetMapping("environment-sources") fun environmentSources(@PathVariable collectionId:String)=ApiData(data=service.environmentSources(collectionId))
    @GetMapping("source") fun source(@PathVariable collectionId:String,@RequestParam sourceBizType:String,@RequestParam sourceBizId:String)=ApiData(data=service.source(collectionId,sourceBizType,sourceBizId))
    @PostMapping("assessment") @Log("登记材质脆弱性和使用限值评估") fun assessment(@PathVariable collectionId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragilityAssessmentVo)=ApiData(data=service.assess(collectionId,v,expectedOrgId))
    @PostMapping("plan") @Log("登记本次脆弱材质藏品使用保护方案") fun plan(@PathVariable collectionId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileUsePlanVo)=ApiData(data=service.savePlan(collectionId,v,expectedOrgId))
    @PostMapping("{recordId}/sign") @Log("本人确认材质脆弱性或使用保护方案") fun sign(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.sign(collectionId,recordId,v,expectedOrgId))
    @PostMapping("history") @Log("据实补录既有展出和运输事实") fun history(@PathVariable collectionId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileUseHistoryVo)=ApiData(data=service.history(collectionId,v,expectedOrgId))
    @PostMapping("plan/{planId}/start") @Log("登记实际展出或运输开始") fun start(@PathVariable collectionId:String,@PathVariable planId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileUseStartVo)=ApiData(data=service.start(collectionId,planId,v,expectedOrgId))
    @GetMapping("automatic-status") fun automaticStatus(@PathVariable collectionId:String)=ApiData(data=monitoring.automaticStatus(collectionId))
    @GetMapping("automatic/{recordId}/samples") fun automaticSamples(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam(defaultValue="1") page:Int,@RequestParam(defaultValue="20") pageSize:Int)=ApiData(data=monitoring.samples(collectionId,recordId,page,pageSize,true))
    @GetMapping("record/{recordId}/automatic") fun automaticChecks(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam(defaultValue="1") page:Int,@RequestParam(defaultValue="20") pageSize:Int)=ApiData(data=monitoring.automaticPage(collectionId,recordId,page,pageSize))
    @GetMapping("monitor/{recordId}/samples") fun monitoringSamples(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam(defaultValue="1") page:Int,@RequestParam(defaultValue="20") pageSize:Int)=ApiData(data=monitoring.samples(collectionId,recordId,page,pageSize))
    @PostMapping("record/{recordId}/monitor") @Log("登记使用期间环境核验及实际现状") fun monitor(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileEnvironmentMonitoringVo)=ApiData(data=monitoring.inspect(collectionId,recordId,v,expectedOrgId))
    @PostMapping("monitor/{recordId}/clear") @Log("核对使用期间监测异常的实际处置") fun clearMonitor(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam expectedOrgId:String,@RequestBody v:IncidentClearanceVo)=ApiData(data=monitoring.clear(collectionId,recordId,v,expectedOrgId))
    @PostMapping("record/{recordId}/end") @Log("登记实际展出或运输结束") fun end(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileUseEndVo)=ApiData(data=service.end(collectionId,recordId,v,expectedOrgId))
    @PostMapping("record/{recordId}/review") @Log("核对使用结束后的实际现状检查") fun review(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileUseReviewVo)=ApiData(data=service.review(collectionId,recordId,v,expectedOrgId))
    @PostMapping("plan/{planId}/adjustment") @Log("登记实际使用变更及未实施核查") fun adjustment(@PathVariable collectionId:String,@PathVariable planId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileUseAdjustmentVo)=ApiData(data=adjustments.propose(collectionId,planId,v,expectedOrgId))
    @PostMapping("adjustment/{recordId}/sign/{role}") @Log("本人确认实际使用变更") fun adjustmentSign(@PathVariable collectionId:String,@PathVariable recordId:String,@PathVariable role:String,@RequestParam expectedOrgId:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=adjustments.sign(collectionId,recordId,role,v,expectedOrgId))
    @PostMapping("adjustment/{recordId}/withdraw") @Log("撤回尚未生效的使用变更") fun adjustmentWithdraw(@PathVariable collectionId:String,@PathVariable recordId:String,@RequestParam expectedOrgId:String,@RequestBody v:FragileAdjustmentWithdrawalVo)=ApiData(data=adjustments.withdraw(collectionId,recordId,v,expectedOrgId))
}
