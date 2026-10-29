package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.IncidentWorkService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/incident-work")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_INCIDENT_MANAGE')")
class IncidentWorkController(private val service: IncidentWorkService) {
    @GetMapping fun list(@RequestParam incidentId: String) = ApiData(data = service.list(incidentId))
    @GetMapping("context") fun context(@RequestParam collectionId: String) = ApiData(data = service.context(collectionId))
    @GetMapping("{id}") fun detail(@PathVariable id: String) = ApiData(data = service.detail(id))
    @PostMapping @Log("登记事故现场专项防护方案") fun create(@RequestBody v: IncidentWorkPlanVo) = ApiData(data = service.create(v))
    @PostMapping("{id}/confirm") @Log("本人确认事故现场防护原件") fun confirm(@PathVariable id: String, @RequestBody v: IncidentWorkConfirmationVo) = ApiData(data = service.confirm(id, v))
    @PostMapping("{id}/start") @Log("登记事故现场防护实际开始") fun start(@PathVariable id: String, @RequestBody v: IncidentWorkStartVo) = ApiData(data = service.start(id, v))
    @PostMapping("{id}/observation") @Log("登记事故现场防护实际观察") fun observation(@PathVariable id: String, @RequestBody v: IncidentWorkObservationVo) = ApiData(data = service.observation(id, v))
    @PostMapping("{id}/end") @Log("登记事故现场防护实际停止结束") fun end(@PathVariable id: String, @RequestBody v: IncidentWorkEndVo) = ApiData(data = service.end(id, v))
    @PostMapping("{id}/cancel") @Log("取消尚未实施的事故现场防护") fun cancel(@PathVariable id: String, @RequestBody v: IncidentWorkCancelVo) = ApiData(data = service.cancel(id, v))
}
