package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.CollectionCalendarYearVo
import io.hizhengfu.cloud.collection.service.CollectionCalendarService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/calendar")
class CollectionCalendarController(private val service: CollectionCalendarService) {
    @GetMapping("{orgId}/{year}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun get(@PathVariable orgId: String, @PathVariable year: Int) = ApiData(data=service.get(orgId,year))

    @Log("登记工作日历及正式通知")
    @PostMapping("{orgId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN')")
    fun save(@PathVariable orgId: String, @RequestBody vo: CollectionCalendarYearVo) = ApiData(data=service.save(orgId,vo))
}
