package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.CollectionOpeningCalendarVo
import io.hizhengfu.cloud.collection.service.CollectionOpeningCalendarService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/opening-calendar")
class CollectionOpeningCalendarController(private val service: CollectionOpeningCalendarService) {
    @GetMapping("{orgId}/{year}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun get(@PathVariable orgId: String, @PathVariable year: Int) = ApiData(data = service.get(orgId, year))

    @Log("保存开放与休馆日历")
    @PostMapping("{orgId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN')")
    fun save(@PathVariable orgId: String, @RequestBody value: CollectionOpeningCalendarVo) = ApiData(data = service.save(orgId, value))
}
