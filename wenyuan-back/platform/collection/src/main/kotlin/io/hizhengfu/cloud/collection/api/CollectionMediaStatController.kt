package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionFileService
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 藏品影像全局统计，独立于单件藏品资源接口。 */
@RestController
@RequestMapping("\${apiPath}/collection/media")
class CollectionMediaStatController(private val fileService: CollectionFileService) {
    @GetMapping("stat")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun stat(): ApiData = ApiData(data = fileService.mediaStat())
}
