package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils

/** 统筹与指定接收的选择器、列表和办理使用同一份当前职责规则。 */
internal class CollectionInstitutionOperationAccess(
    private val scope: CollectionScopeResolver,
    private val label: String,
    private val readPermissions: Set<String>,
    private val writePermissions: Set<String>,
) {
    fun user(write: Boolean = false): SecurityUser {
        val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后访问$label")
        val user = CollectionActorDirectory(scope).requireActive(id)
        val permissions = if (write) writePermissions else readPermissions
        if (EnumRole.ADMIN.value !in user.roles && user.permissions.none { it in permissions || it in setOf("ADMIN", CollectionConst.ADMIN) })
            throw BadRequestException("当前账号没有${label}权限")
        return user
    }

    fun require(org: String, write: Boolean = false) = CollectionCurrentOperationAccess.require(
        scope, org, write, if (write) writePermissions else readPermissions, label,
    )

    fun currentOrg(write: Boolean = false): String = scope.currentOrgId(user(write))

    fun contextOrg(): String? {
        val current = user()
        if (!scope.isMulti()) return scope.currentOrgId(current)
        // 跨馆查阅或多个实际任职不等于已选定办理馆；列表仍可查，建案必须明确馆别。
        return try { scope.currentOrgId(current) } catch (_: BadRequestException) { null }
    }

    fun readableOrgs(): Set<String> {
        val current = scope.currentReadableOrgIds(user(), CollectionConst.CROSS_ORG)
        val configured = if (scope.isMulti()) null else scope.currentOrgId()
        return CollectionOrgDao.all().filter {
            (current == null || it.id.value in current) && (configured == null || it.id.value == configured) &&
                (current == null || (it.status == EnumContentStatus.NORMAL && it.orgStatus == EnumOrgStatus.ENABLED))
        }.map { it.id.value }.toSet()
    }

    fun canWrite(org: String): Boolean {
        // 当前授予职责不刷新旧 HTTP 登录门槛；页面不能据此承诺操作必然可提交。
        if (SecurityUtils.getNullUserDetail()?.authorities?.none {
                it.authority in writePermissions || it.authority in setOf("ADMIN", CollectionConst.ADMIN)
            } != false) return false
        return try { require(org, true); true } catch (_: BadRequestException) { false }
    }

    fun writableOrgs(): Set<String> {
        user() // 即使空列表也先核验当前账号和查阅职责。
        return CollectionOrgDao.all().filter { canWrite(it.id.value) }.map { it.id.value }.toSet()
    }
}
