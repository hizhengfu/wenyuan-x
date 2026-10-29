package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionUsageSummaryService
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/stat")
class CollectionUsageSummaryController(private val service: CollectionUsageSummaryService) {
    @GetMapping("usage-summary")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STAT')")
    fun summary() = ApiData(data = service.summary())
}
