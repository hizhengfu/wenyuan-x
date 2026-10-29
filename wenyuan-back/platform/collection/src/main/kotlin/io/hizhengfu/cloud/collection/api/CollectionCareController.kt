package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionCareService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/care/{collectionId}")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class CollectionCareController(private val service: CollectionCareService) {
    @GetMapping fun detail(@PathVariable collectionId: String)=ApiData(data=service.detail(collectionId))
    @GetMapping("history") fun history(@PathVariable collectionId: String,@RequestParam orgId:String)=ApiData(data=service.history(collectionId,orgId))
    @PostMapping("plan") @Log("登记日常检查养护方案") fun plan(@PathVariable collectionId: String,@RequestBody v: CollectionCarePlanVo)=ApiData(data=service.savePlan(collectionId,v))
    @PostMapping("plan/{planId}/sign") @Log("本人确认检查养护方案") fun sign(@PathVariable collectionId: String,@PathVariable planId: String,@RequestBody v: CollectionCareSignatureVo)=ApiData(data=service.sign(collectionId,planId,v))
    @PostMapping("check") @Log("登记实际藏品检查及病害") fun check(@PathVariable collectionId: String,@RequestBody v: CollectionCareCheckVo,@RequestParam(required=false) sourceRevision:String?)=ApiData(data=service.inspect(collectionId,v,sourceRevision))
    @PostMapping("start") @Log("登记实际日常养护开始") fun start(@PathVariable collectionId: String,@RequestBody v: CollectionCareStartVo)=ApiData(data=service.start(collectionId,v))
    @PostMapping("work/{workId}/end") @Log("登记实际养护完成或停止及保管") fun end(@PathVariable collectionId: String,@PathVariable workId: String,@RequestBody v: CollectionCareEndVo)=ApiData(data=service.end(collectionId,workId,v))
    @PostMapping("record/{recordId}/clear") @Log("依据实际事故处置核对检查养护异常") fun clear(@PathVariable collectionId: String,@PathVariable recordId: String,@RequestBody v: IncidentClearanceVo)=ApiData(data=service.clear(collectionId,recordId,v))
}
