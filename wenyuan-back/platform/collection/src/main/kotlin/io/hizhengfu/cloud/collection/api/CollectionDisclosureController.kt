package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionDisclosureService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/disclosure")
class CollectionDisclosureController(private val service: CollectionDisclosureService) {
    @GetMapping("feed")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH','COLLECTION_SELECT')")
    fun feed() = ApiData(data = service.feed())
    @GetMapping("{orgId}/{subject}/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    fun detail(@PathVariable orgId: String, @PathVariable subject: EnumDisclosureSubject, @PathVariable id: String) = ApiData(data = service.detail(orgId, subject, id))
    @PostMapping("{orgId}/{subject}/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    @Log("登记公开稿脱敏核查及不公开实际依据")
    fun review(@PathVariable orgId: String, @PathVariable subject: EnumDisclosureSubject, @PathVariable id: String, @RequestBody value: CollectionDisclosureReviewVo) = ApiData(data = service.review(orgId, subject, id, value))
    @PostMapping("{orgId}/review/{id}/enable")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    @Log("向CMS提供已核查公开稿")
    fun enable(@PathVariable orgId: String, @PathVariable id: String) = ApiData(data = service.enable(orgId, id))
    @PostMapping("{orgId}/review/{id}/stop")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    @Log("停止向CMS提供该版公开稿")
    fun stop(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: DisclosureFeedStopVo) = ApiData(data = service.stop(orgId, id, value))
    @PostMapping("{orgId}/review/{id}/event")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    @Log("登记实际公开或撤下凭据")
    fun event(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: CollectionDisclosureEventVo) = ApiData(data = service.event(orgId, id, value))
}
