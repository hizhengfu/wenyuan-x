package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

object CollectionOrgFilterPolicy {
    fun listScope(accessibleOrgIds: Set<String>?): Set<String>? = accessibleOrgIds

    fun resolve(accessibleOrgIds: Set<String>?, requestedOrgId: String?): Set<String>? {
        val requested = requestedOrgId?.trim()?.takeIf { it.isNotBlank() }
        if (accessibleOrgIds == null) return requested?.let { setOf(it) }
        if (requested == null) return accessibleOrgIds
        if (requested !in accessibleOrgIds) throw BadRequestException("无权查看该收藏单位数据")
        return setOf(requested)
    }
}
