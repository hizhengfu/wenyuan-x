package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils

/** 每次查询在同一业务事务内取得当前授权，计数、关联查询及结果投影共用该范围。 */
data class CollectionSearchReadAccess(val orgIds: Set<String>, val acquisitionConfidential: Boolean, private val user: SecurityUser) {
    fun currentOrgId(scope: CollectionScopeResolver): String = scope.currentOrgId(user).also {
        OrgScopedReadPolicy.requireVisible(orgIds, it, "当前收藏单位")
    }
    companion object {
        fun require(scope: CollectionScopeResolver, acquisition: Boolean = false, additionalPermissions: Set<String> = emptySet(),
            crossOrgPermission: String = CollectionConst.CROSS_ORG, allowHistoricalOrgs: Boolean = false): CollectionSearchReadAccess {
            val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后查询藏品资料")
            val user = CollectionActorDirectory(scope).requireActive(id)
            val permissions = setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.SELECT) +
                (if (acquisition) setOf(CollectionConst.ACQUIRE) else emptySet()) + additionalPermissions
            if (EnumRole.ADMIN.value !in user.roles && user.permissions.none(permissions::contains))
                throw BadRequestException("当前账号没有查询藏品资料的权限，请核对当前授权")
            val current = scope.currentReadableOrgIds(user, crossOrgPermission)
            val configured = if (scope.isMulti()) null else scope.currentOrgId()
            val orgIds = CollectionOrgDao.all().filter {
                ((it.status == EnumContentStatus.NORMAL && it.orgStatus == EnumOrgStatus.ENABLED) || (allowHistoricalOrgs && current == null)) &&
                    (current == null || it.id.value in current) && (configured == null || it.id.value == configured)
            }.map { it.id.value }.toSet()
            return CollectionSearchReadAccess(orgIds, AcquisitionConfidentialAccess.hasDuty(user), user)
        }
    }
}
