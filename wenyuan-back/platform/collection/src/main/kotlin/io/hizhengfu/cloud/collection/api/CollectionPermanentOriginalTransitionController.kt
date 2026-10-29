package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionPermanentOriginalTransitionRequest
import io.hizhengfu.cloud.collection.service.CollectionPermanentOriginalTransitionService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 原件身份技术承接须留痕，业务永久档案本身保持只追加、不可改写。 */
@RestController
@RequestMapping("\${apiPath}/collection/original-identity/transitions")
class CollectionPermanentOriginalTransitionController(private val service: CollectionPermanentOriginalTransitionService) {
    @PostMapping
    @Log("登记永久原件卷设备号变更承接")
    @PreAuthorize("hasAuthority('ADMIN')")
    fun record(@RequestBody request: CollectionPermanentOriginalTransitionRequest) = ApiData(data = service.record(request))
}
