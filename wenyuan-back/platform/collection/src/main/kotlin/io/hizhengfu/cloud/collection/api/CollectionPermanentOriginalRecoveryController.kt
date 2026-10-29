package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionPermanentOriginalRecoveryRequest
import io.hizhengfu.cloud.collection.service.CollectionPermanentOriginalRecoveryService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 受控恢复凭据另行留痕，不改写历史永久档案。 */
@RestController
@RequestMapping("\${apiPath}/collection/original-identity/recoveries")
class CollectionPermanentOriginalRecoveryController(private val service: CollectionPermanentOriginalRecoveryService) {
    @PostMapping
    @Log("登记永久原件跨文件系统恢复承接")
    @PreAuthorize("hasAuthority('ADMIN')")
    fun record(@RequestBody request: CollectionPermanentOriginalRecoveryRequest) = ApiData(data = service.record(request))
}
