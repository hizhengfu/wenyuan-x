package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.sys.system.dao.Groups
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.stereotype.Component

/** 法定业务职责由实际人员承担；ADMIN 权限不会自动取得保管负责人或主要负责人身份。 */
@Component
class CollectionResponsibleUserService(
    private val directory: CollectionActorDirectory,
    private val scope: CollectionScopeResolver,
) {
    fun requireExternalToMuseum(userId: String, orgId: String) {
        val identity=directory.requireActive(userId)
        CollectionOrgDao.findById(orgId) ?: throw BadRequestException("收藏单位不存在")
        if (scope.isMuseumMember(identity,orgId)) throw BadRequestException("本馆人员不得担任退出评估专家")
    }

    fun requireDepartmentMember(userId: String, orgId: String, departmentId: String) {
        val org=CollectionOrgs.select(CollectionOrgs.systemGroupId).where {CollectionOrgs.id eq orgId}.singleOrNull()
            ?: throw BadRequestException("收藏单位不存在")
        val root=org[CollectionOrgs.systemGroupId]?.takeIf(String::isNotBlank) ?: throw BadRequestException("请先配置收藏单位关联的授权机构，再选择馆内提用部门")
        requireDepartmentUnderRoot(departmentId,root)
        val identity=directory.requireActive(userId)
        if (!scope.isMuseumMember(identity,orgId)) throw BadRequestException("须为本馆实际人员")
        if (departmentId !in identity.groupIds + setOfNotNull(identity.groupId)) throw BadRequestException("所选人员须归属本次提用部门，不能用跨馆查看权限代替部门身份")
    }
    /** 授权以当前父级链为准；旧 parentIds 可缺失或过期，不能作为部门任职依据。 */
    private fun requireDepartmentUnderRoot(departmentId: String, root: String) {
        var current = departmentId
        val visited = mutableSetOf<String>()
        while (visited.add(current)) {
            val node = Groups.select(Groups.parentId, Groups.enabled).where { Groups.id eq current }.singleOrNull()
                ?: throw BadRequestException("提用部门或其上级机构不存在，请核对当前机构树")
            if (!node[Groups.enabled]) throw BadRequestException("提用部门及其上级授权机构须处于启用状态")
            if (current == root) return
            current = node[Groups.parentId].takeIf { it.isNotBlank() && it != "0" }
                ?: throw BadRequestException("提用部门须为本馆授权机构或其下属部门")
        }
        throw BadRequestException("提用部门机构树存在循环，请核对当前归属")
    }

    fun requireDepartmentHead(userId: String,orgId: String,departmentId: String) {
        requireDepartmentMember(userId,orgId,departmentId)
        requireRole(userId,orgId,CollectionConst.ROLE_DEPARTMENT_HEAD)
    }

    fun requireMuseumMember(userId: String, orgId: String) {
        if (!scope.isMuseumMember(directory.requireActive(userId), orgId)) throw BadRequestException("须为本馆实际人员，跨馆管理权限不能代替任职身份")
    }

    fun requireRole(userId: String, orgId: String, roleCode: String) {
        val identity = directory.requireActive(userId)
        if (roleCode == CollectionConst.ROLE_KEEPER) io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy.requireStorageOperation(identity.permissions, identity.roleCodes)
        if (roleCode == CollectionConst.ROLE_LEDGER_CUSTODIAN) io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy.requireLedgerOperation(identity.permissions, identity.roleCodes)
        if (roleCode !in identity.roleCodes) throw BadRequestException("所选人员未配置职责角色：$roleCode")
        if (!scope.isMuseumMember(identity,orgId)) throw BadRequestException("职责角色须由本馆实际人员承担，管理员或总馆数据权限不能代替任职身份")
    }
}
