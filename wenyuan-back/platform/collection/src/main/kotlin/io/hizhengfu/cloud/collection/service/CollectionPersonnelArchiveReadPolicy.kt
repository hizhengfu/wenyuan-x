package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.stereotype.Component

@Component
class CollectionPersonnelArchiveReadPolicy(
    private val education: StaffEducationAccess,
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
) : CollectionArchiveReadPolicy {
    override val businessTypes = setOf(CollectionStaffEducationService.BIZ, WarehouseStaffingHandoverService.BIZ, LeadershipDepartureEvidence.BIZ)
    override fun requireReadable(reference: CollectionArchiveFileReference) {
        if (reference.businessType == CollectionStaffEducationService.BIZ) {
            val row = CollectionStaffEducationDao.findById(reference.businessId) ?: throw BadRequestException("岗位教育原件所属记录缺失")
            if (row.orgId != reference.orgId) throw BadRequestException("岗位教育原件馆别不一致")
            StaffEducationReadIdentity.require(row)
            education.require(row.orgId, row.userId)
            return
        }
        if (!scope.isMulti() && reference.orgId != scope.currentOrgId()) throw BadRequestException("岗位交接原件不属于当前配置收藏单位")
        val user = directory.requireActive(SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后查阅岗位交接原件"))
        val permissions = if (reference.businessType == WarehouseStaffingHandoverService.BIZ)
            setOf(CollectionConst.ADMIN, CollectionConst.WAREHOUSE_MANAGE)
        else setOf(CollectionConst.ADMIN, CollectionConst.ORG_MANAGE, CollectionConst.STORAGE)
        val responsible = scope.isMuseumMember(user, reference.orgId) && user.roleCodes.any { it in setOf(CollectionConst.ROLE_DIRECTOR, CollectionConst.ROLE_STORAGE_HEAD) }
        if (EnumRole.ADMIN.value !in user.roles && user.permissions.none(permissions::contains) && !responsible)
            throw BadRequestException("当前账号缺少该馆岗位交接原件查阅职责")
        if (CollectionOrgDao.findById(reference.orgId) == null) throw BadRequestException("岗位交接原件所属收藏单位不存在")
        OrgScopedReadPolicy.requireVisible(scope.currentReadableOrgIds(user, CollectionConst.CROSS_ORG), reference.orgId, "岗位交接原件")
    }
}
