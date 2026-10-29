package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 任职、派单及安全通知使用当前关系表，不复用登录身份或 UserService 的人员/角色缓存。 */
@Component
class CollectionActorDirectory(private val scope: CollectionScopeResolver) {
    private val userColumns = listOf(Users.id, Users.name, Users.loginName, Users.groupId, Users.enabled, Users.status,
        Users.accountNonExpired, Users.accountNonLocked, Users.credentialsNonExpired, Users.expireTime)

    fun requireActive(userId: String): SecurityUser = findActive(userId)
        ?: throw BadRequestException("责任人员账号不存在、已停用、锁定、删除或过期，请核对当前实际人员")

    /** 仅用于业务身份核对的快照，不作为登录凭据；不读取密码、密钥或其他个人资料。 */
    fun findActive(userId: String): SecurityUser? {
        val row = Users.select(userColumns).where { Users.id eq userId }.singleOrNull()?.takeIf(::available) ?: return null
        val roles = RoleUsers.join(Roles, JoinType.INNER, RoleUsers.roleId, Roles.id)
            .select(Roles.id, Roles.code, Roles.name).where { (RoleUsers.userId eq userId) and (Roles.enabled eq true) }.toList()
        val roleIds = roles.map { it[Roles.id].value }.toSet()
        val permissions = if (roleIds.isEmpty()) emptySet() else RolePermissions.join(Permissions, JoinType.INNER,
            RolePermissions.permissionId, Permissions.id).select(Permissions.code)
            .where { RolePermissions.roleId inList roleIds }.map { it[Permissions.code] }.toSet()
        return SecurityUser(id = userId, name = row[Users.name], username = row[Users.loginName], password = "",
            groupId = row[Users.groupId], groupIds = groups(setOf(userId))[userId].orEmpty(), postIds = emptySet(), dataType = 0,
            roles = roleIds, roleCodes = roles.map { it[Roles.code] }.toSet(), rolesName = roles.joinToString { it[Roles.name] },
            authorities = permissions.map(::SimpleGrantedAuthority), enabled = true)
    }

    fun roleMembers(roleIds: List<String>, orgId: String): List<Pair<String, String>> {
        if (roleIds.isEmpty()) return emptyList()
        val rows = RoleUsers.join(Roles, JoinType.INNER, RoleUsers.roleId, Roles.id)
            .join(Users, JoinType.INNER, RoleUsers.userId, Users.id)
            .select(userColumns).where { (Roles.id inList roleIds) and (Roles.enabled eq true) }
            .withDistinct().toList().filter(::available)
        val groups = groups(rows.map { it[Users.id].value }.toSet())
        val belongs = scope.membershipChecker()
        return rows.filter { belongs(groups[it[Users.id].value].orEmpty() + setOfNotNull(it[Users.groupId]), orgId) }
            .map { it[Users.id].value to it[Users.name] }.distinctBy { it.first }.sortedBy { it.first }
    }

    private fun groups(ids: Set<String>): Map<String, Set<String>> {
        if (ids.isEmpty()) return emptyMap()
        return UserGroups.join(Groups, JoinType.INNER, UserGroups.groupId, Groups.id)
            .select(UserGroups.userId, UserGroups.groupId).where { (UserGroups.userId inList ids) and (Groups.enabled eq true) }
            .groupBy({ it[UserGroups.userId] }, { it[UserGroups.groupId] }).mapValues { it.value.toSet() }
    }

    private fun available(row: ResultRow): Boolean = row[Users.enabled] == true && row[Users.status] == EnumContentStatus.NORMAL &&
        row[Users.accountNonExpired] && row[Users.accountNonLocked] && row[Users.credentialsNonExpired] &&
        (row[Users.expireTime]?.isAfter(LocalDateTime.now()) != false)
}
