package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.utils.SecurityUtils

/** 仅从登录上下文取人员 ID；当前职责、馆别及后端单馆设置共同约束保密读取。调用方须位于业务事务内。 */
object AcquisitionConfidentialAccess {
    val PERMISSIONS = setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.ACQUIRE, CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT)
    val ROLES = setOf(CollectionConst.ROLE_STORAGE_HEAD, CollectionConst.ROLE_DIRECTOR,
        CollectionConst.ROLE_VICE_DIRECTOR, CollectionConst.ROLE_APPRAISAL_COMMITTEE)
    internal fun hasDuty(user: SecurityUser): Boolean = EnumRole.ADMIN.value in user.roles ||
        user.permissions.any(PERMISSIONS::contains) || user.roleCodes.any(ROLES::contains)
    fun canRead(scope: CollectionScopeResolver, orgId: String? = null): Boolean {
        val id = SecurityUtils.getNullUserDetail()?.id ?: return false
        val user = CollectionActorDirectory(scope).findActive(id) ?: return false
        if (!hasDuty(user)) return false
        if (orgId == null) return true
        if (!scope.isMulti() && orgId != scope.currentOrgId(user)) return false
        val org = CollectionOrgDao.findById(orgId) ?: return false
        val visible = scope.currentReadableOrgIds(user, CollectionConst.CROSS_ORG)
        return visible == null || (orgId in visible && org.status == EnumContentStatus.NORMAL && org.orgStatus == EnumOrgStatus.ENABLED)
    }
}
