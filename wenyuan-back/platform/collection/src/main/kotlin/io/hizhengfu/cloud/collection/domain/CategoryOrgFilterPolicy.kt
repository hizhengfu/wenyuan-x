package io.hizhengfu.cloud.collection.domain

object CategoryOrgFilterPolicy {
    fun isVisible(accessibleOrgIds: Set<String>?, categoryOrgId: String?): Boolean =
        accessibleOrgIds == null || categoryOrgId == null || categoryOrgId in accessibleOrgIds
}
