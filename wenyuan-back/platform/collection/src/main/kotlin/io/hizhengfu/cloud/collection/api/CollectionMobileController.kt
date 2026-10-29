package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.collection.service.CollectionMobileMutationService
import io.hizhengfu.cloud.collection.service.MobileInventoryCheckRequest
import io.hizhengfu.cloud.collection.service.MobileCareCheckRequest
import io.hizhengfu.cloud.collection.service.MobileInventoryCompletionRequest
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody

/** 原生客户端启动上下文。认证由平台提供，业务仍由各原有服务核验当前职责。 */
@RestController
@RequestMapping("\${apiPath}/collection/mobile")
class CollectionMobileController(private val scope: CollectionScopeResolver, private val mutations: CollectionMobileMutationService) {
    @Log("移动端提交实际盘库核验")
    @PostMapping("inventory/{id}/check")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun check(@PathVariable id: String, @RequestBody request: MobileInventoryCheckRequest) =
        ApiData(data = mutations.inventoryCheck(id, request))

    @Log("移动端核对并签署盘库结果")
    @PostMapping("inventory/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun complete(@PathVariable id:String,@RequestBody request:MobileInventoryCompletionRequest)=
        ApiData(data=mutations.inventoryComplete(id,request))

    @Log("移动端提交实际检查养护记录")
    @PostMapping("care/{id}/check")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','${CollectionConst.CARE}')")
    fun careCheck(@PathVariable id: String, @RequestBody request: MobileCareCheckRequest) =
        ApiData(data = mutations.careCheck(id, request))

    @GetMapping("context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_RFID_SCAN','${CollectionConst.CARE}')")
    @Transactional(readOnly = true)
    fun context(): ApiData {
        val actor = CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserDetail().id)
        val readable = scope.currentBusinessReadableOrgIds(actor, CollectionConst.CROSS_ORG)
        val writable = scope.currentWritableOrgIds(actor)
        val orgs = CollectionOrgDao.all().filter {
            it.status == EnumContentStatus.NORMAL && it.orgStatus == EnumOrgStatus.ENABLED &&
                (readable == null || it.id.value in readable)
        }.map { mapOf("id" to it.id.value, "name" to it.name,
            "writable" to (writable == null || it.id.value in writable)) }
        return ApiData(data = mapOf("userId" to actor.id, "name" to actor.name,
            "permissions" to actor.permissions, "roles" to actor.roles, "roleCodes" to actor.roleCodes, "orgs" to orgs,
            "currentOrgId" to scope.currentOrgId(actor), "apiContract" to "MUSEUM_MOBILE_V1",
            "serverZoneId" to java.time.ZoneId.systemDefault().id))
    }
}
