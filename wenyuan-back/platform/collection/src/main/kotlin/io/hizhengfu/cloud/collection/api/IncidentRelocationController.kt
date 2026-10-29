package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.IncidentRelocationService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/incident-relocation")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_INCIDENT_MANAGE')")
class IncidentRelocationController(private val service:IncidentRelocationService) {
    @GetMapping fun list(@RequestParam incidentId:String)=ApiData(data=service.list(incidentId))
    @GetMapping("context") fun context(@RequestParam collectionId:String)=ApiData(data=service.context(collectionId))
    @GetMapping("{id}") fun detail(@PathVariable id:String)=ApiData(data=service.detail(id))
    @PostMapping @Log("登记事故专项搬移方案") fun create(@RequestBody v:IncidentRelocationPlanVo)=ApiData(data=service.create(v))
    @PostMapping("emergency") @Log("补记真实已发生紧急搬移") fun emergency(@RequestBody v:IncidentEmergencyRelocationVo)=ApiData(data=service.recordEmergency(v))
    @PostMapping("{id}/confirm") @Log("本人核对事故搬移原件") fun confirm(@PathVariable id:String,@RequestBody v:IncidentWorkConfirmationVo)=ApiData(data=service.confirm(id,v))
    @PostMapping("{id}/depart") @Log("登记事故搬移实际搬出") fun depart(@PathVariable id:String,@RequestBody v:IncidentRelocationDepartureVo)=ApiData(data=service.depart(id,v))
    @PostMapping("{id}/observation") @Log("登记事故搬移实际运输观察") fun observation(@PathVariable id:String,@RequestBody v:IncidentWorkObservationVo)=ApiData(data=service.observation(id,v))
    @PostMapping("{id}/receive") @Log("登记事故搬移实际接收") fun receive(@PathVariable id:String,@RequestBody v:IncidentRelocationReceiptVo)=ApiData(data=service.receive(id,v))
    @PostMapping("{id}/cancel") @Log("取消未实施事故搬移方案") fun cancel(@PathVariable id:String,@RequestBody v:IncidentWorkCancelVo)=ApiData(data=service.cancel(id,v))
}
