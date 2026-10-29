package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException

/** Shared org visibility gate for direct-by-id reads in multi-collection-org mode. */
object OrgScopedReadPolicy {
    fun isVisible(accessibleOrgIds: Set<String>?, orgId: String?): Boolean =
        accessibleOrgIds == null || (orgId != null && accessibleOrgIds.contains(orgId))

    fun requireVisible(accessibleOrgIds: Set<String>?, orgId: String?, subject: String) {
        if (!isVisible(accessibleOrgIds, orgId)) throw BadRequestException("无权查看$subject")
    }
}
