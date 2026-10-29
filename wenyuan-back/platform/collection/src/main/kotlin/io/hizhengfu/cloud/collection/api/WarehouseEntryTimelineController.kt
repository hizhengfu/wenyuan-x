package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.WarehouseEntryTimelineService
import io.hizhengfu.cloud.collection.service.WarehouseEntryTimelineReviewVo
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/warehouse-entry")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
class WarehouseEntryTimelineController(private val service:WarehouseEntryTimelineService) {
    @GetMapping("{id}/timeline") fun get(@PathVariable id:String)=ApiData(data=service.get(id))
    @Log("登记人员离库与原件操作时间核查材料")
    @PostMapping("{id}/timeline/review") fun review(@PathVariable id:String,@RequestBody value:WarehouseEntryTimelineReviewVo)=ApiData(data=service.review(id,value))
}
