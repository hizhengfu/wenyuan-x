package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionPermanentRemoteRecoveryRequest
import io.hizhengfu.cloud.collection.service.CollectionPermanentRemoteRecoveryService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 目标对象版本另行留痕，不改写历史永久档案及旧版本标识。 */
@RestController
@RequestMapping("\${apiPath}/collection/original-identity/remote-recoveries")
class CollectionPermanentRemoteRecoveryController(private val service: CollectionPermanentRemoteRecoveryService) {
    @PostMapping
    @Log("登记永久原件远端历史版本恢复承接")
    @PreAuthorize("hasAuthority('ADMIN')")
    fun record(@RequestBody request: CollectionPermanentRemoteRecoveryRequest) = ApiData(data = service.record(request))
}
