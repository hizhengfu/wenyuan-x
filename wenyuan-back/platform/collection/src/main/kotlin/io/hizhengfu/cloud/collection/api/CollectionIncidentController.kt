package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionIncidentService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

data class IncidentSourceRequest(val expectedFactsId:String,val source:IncidentSourceVo,val reason:String,val fileIds:String)

data class IncidentFactsAmendRequest(val expectedFactsId: String, val facts: IncidentFactsVo)

@RestController
@RequestMapping("\${apiPath}/collection/incident")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_INCIDENT_MANAGE')")
class CollectionIncidentController(private val service: CollectionIncidentService) {
    @GetMapping("page") fun page(request: HttpServletRequest) = ApiData(data = service.page(request))
    @GetMapping("{id}") fun detail(@PathVariable id: String) = ApiData(data = service.detail(id))
    @PostMapping @Log("登记藏品事故发现事实") fun create(@RequestBody v: IncidentFactsVo) = ApiData(data = service.create(v))
    @PostMapping("{id}/facts") @Log("追加藏品事故事实版本") fun facts(@PathVariable id: String, @RequestBody v: IncidentFactsAmendRequest) = ApiData(data = service.amend(id, v.facts, v.expectedFactsId))
    @PostMapping("{id}/source") @Log("补充事故实际业务来源") fun source(@PathVariable id:String,@RequestBody v:IncidentSourceRequest)=ApiData(data=service.linkSource(id,v.expectedFactsId,v.source,v.reason,v.fileIds))
    @PostMapping("{id}/report") @Log("登记藏品事故实际报案及报告") fun report(@PathVariable id: String, @RequestBody v: IncidentReportVo) = ApiData(data = service.report(id, v))
    @PostMapping("{id}/handling") @Log("登记藏品事故实际核查处理结果") fun handling(@PathVariable id: String, @RequestBody v: IncidentHandlingVo) = ApiData(data = service.recordHandling(id, v))
    @PostMapping("{id}/confirm") @Log("本人签署藏品事故处理案卷") fun confirm(@PathVariable id: String, @RequestBody v: IncidentConfirmationVo) = ApiData(data = service.confirm(id, v))
}
