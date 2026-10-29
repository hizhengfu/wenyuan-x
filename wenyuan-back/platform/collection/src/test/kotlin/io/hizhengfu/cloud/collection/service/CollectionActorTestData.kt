package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.*

internal fun actorSchema() = SchemaUtils.create(Users, Roles, RoleUsers, UserGroups, Groups, Permissions, RolePermissions)

internal fun actorRole(userId: String, code: String, permissions: Set<String> = emptySet()): String {
    val id = Roles.selectAll().where { Roles.code eq code }.singleOrNull()?.get(Roles.id)?.value ?: "role:$code".also { id ->
        Roles.insert { it[Roles.id] = id; it[Roles.code] = code; it[name] = code }
    }
    RoleUsers.insertIgnore { it[RoleUsers.userId] = userId; it[roleId] = id }
    permissions.forEach { code ->
        Permissions.insertIgnore { it[Permissions.id] = code; it[Permissions.code] = code; it[name] = code; it[parentId] = "0"; it[parentIds] = "0," }
        RolePermissions.insertIgnore { it[roleId] = EntityID(id, Roles); it[permissionId] = EntityID(code, Permissions) }
    }
    return id
}

/** 文件路径既有用例补充实际读取账号，权限撤销另由运行链测试覆盖。 */
internal fun originalReaderSchema() {
    actorSchema()
    listOf("museum", "other").forEach { id ->
        if(GroupDao.findById(id)==null)GroupDao.new(id) { name = id; type = '1'; parentId = "0"; parentIds = "0," }
    }
}
internal fun originalReader(id: String, permission: String, group: String = "museum") {
    UserDao.new(id) { name = id; loginName = id; password = "unused"; enabled = true; groupId = group }
    actorRole(id, "FILE_$id", setOf(permission))
}
