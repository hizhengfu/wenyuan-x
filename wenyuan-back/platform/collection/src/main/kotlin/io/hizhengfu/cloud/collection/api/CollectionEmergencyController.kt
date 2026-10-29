package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionEmergencyService
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/emergency")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_EMERGENCY_MANAGE')")
class CollectionEmergencyController(private val service:CollectionEmergencyService,private val duties:CollectionEmergencyDutyService) {
    @PostMapping("{orgId}/duty") @Log("主要负责人本人指定应急责任承接") fun appointDuty(@PathVariable orgId:String,@RequestBody v:EmergencyDutyAppointmentVo)=ApiData(data=duties.appoint(orgId,v))
    @PostMapping("{orgId}/duty/{id}/{phase}") @Log("原任交接或接任人本人确认应急职责") fun signDuty(@PathVariable orgId:String,@PathVariable id:String,@PathVariable phase:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=duties.sign(orgId,id,phase,v))
    @GetMapping("page") fun page(request:HttpServletRequest)=ApiData(data=service.page(request))
    @GetMapping("{orgId}") fun detail(@PathVariable orgId:String)=ApiData(data=service.detail(orgId))
    @PostMapping("{orgId}/plan") @Log("登记实际印发的藏品应急预案") fun plan(@PathVariable orgId:String,@RequestBody v:CollectionEmergencyPlanVo)=ApiData(data=service.registerPlan(orgId,v))
    @PostMapping("{orgId}/plan/{id}/{role}") @Log("本人核对应急预案原件") fun signPlan(@PathVariable orgId:String,@PathVariable id:String,@PathVariable role:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.confirmPlan(orgId,id,role,v))
    @PostMapping("{orgId}/drill") @Log("登记实际藏品安全演练及问题") fun drill(@PathVariable orgId:String,@RequestBody v:CollectionEmergencyDrillVo)=ApiData(data=service.recordDrill(orgId,v))
    @PostMapping("{orgId}/drill/{id}/revision") @Log("追加同次演练报告订正及新发现") fun reviseDrill(@PathVariable orgId:String,@PathVariable id:String,@RequestBody v:EmergencyDrillRevisionVo)=ApiData(data=service.reviseDrill(orgId,id,v))
    @PostMapping("{orgId}/drill/{id}/revision/{reportId}/sign/{role}") @Log("本人核对本版演练报告") fun signReport(@PathVariable orgId:String,@PathVariable id:String,@PathVariable reportId:String,@PathVariable role:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.confirmDrill(orgId,id,reportId,role,v))
    @PostMapping("{orgId}/drill/{id}/revision/{reportId}/improvement") @Log("按本版演练报告登记实际整改") fun improveReport(@PathVariable orgId:String,@PathVariable id:String,@PathVariable reportId:String,@RequestBody v:EmergencyImprovementVo)=ApiData(data=service.improve(orgId,id,reportId,v))
    @PostMapping("{orgId}/drill/{id}/revision/{reportId}/improvement/{improvementId}/review") @Log("按本版演练报告独立复核整改") fun reviewReport(@PathVariable orgId:String,@PathVariable id:String,@PathVariable reportId:String,@PathVariable improvementId:String,@RequestBody v:EmergencyImprovementReviewVo)=ApiData(data=service.reviewImprovement(orgId,id,reportId,improvementId,v))
    @PostMapping("{orgId}/drill/{id}/sign/{role}") @Log("本人核对实际演练评估及改进安排") fun signDrill(@PathVariable orgId:String,@PathVariable id:String,@PathVariable role:String,@RequestBody v:CollectionCareSignatureVo)=ApiData(data=service.confirmDrill(orgId,id,role,v))
    @PostMapping("{orgId}/drill/{id}/improvement") @Log("本人登记实际演练问题整改") fun improve(@PathVariable orgId:String,@PathVariable id:String,@RequestBody v:EmergencyImprovementVo)=ApiData(data=service.improve(orgId,id,v))
    @PostMapping("{orgId}/drill/{id}/improvement/{improvementId}/review") @Log("独立复核演练问题整改") fun review(@PathVariable orgId:String,@PathVariable id:String,@PathVariable improvementId:String,@RequestBody v:EmergencyImprovementReviewVo)=ApiData(data=service.reviewImprovement(orgId,id,improvementId,v))
}
