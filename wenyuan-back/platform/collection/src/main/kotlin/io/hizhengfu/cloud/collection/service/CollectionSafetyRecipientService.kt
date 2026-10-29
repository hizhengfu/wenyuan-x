package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.stereotype.Component

/** 收件资格每次从实际账号重新读取；调度线程不借用管理员身份。 */
@Component
class CollectionSafetyRecipientService(
    private val scope:CollectionScopeResolver,
    private val users:CollectionResponsibleUserService,
    private val directory:CollectionActorDirectory,
) {
    companion object {
        fun canRead(user:SecurityUser,domain:EnumCollectionSafetyDomain):Boolean {
            val allowed=when(domain) {
                EnumCollectionSafetyDomain.EMERGENCY->setOf(CollectionConst.EMERGENCY)
                EnumCollectionSafetyDomain.LOAN->setOf(CollectionConst.STORAGE,CollectionConst.SELECT)
                else->setOf(CollectionConst.CARE)
            }
            return EnumRole.ADMIN.value in user.roles || user.permissions.any {it in allowed || it=="ADMIN" || it==CollectionConst.ADMIN}
        }
        fun canManage(user:SecurityUser):Boolean = EnumRole.ADMIN.value in user.roles || user.permissions.any {it in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.SAFETY_REMINDER)}
    }
    fun eligible(orgId:String,domain:EnumCollectionSafetyDomain,assignee:CollectionSafetyAssignee):Boolean {
        val identity=directory.findActive(assignee.userId) ?: return false
        if(!identity.isAccountNonExpired || !scope.isMuseumMember(identity,orgId) || !canRead(identity,domain))return false
        if(assignee.roles.isNotEmpty() && identity.roleCodes.intersect(assignee.roles).isEmpty())return false
        if(assignee.departmentId!=null)try {users.requireDepartmentMember(assignee.userId,orgId,assignee.departmentId)} catch(_:BadRequestException) {return false}
        return true
    }
    fun resolve(task:CollectionSafetyTask,coordinators:Set<String>):CollectionSafetyRecipients {
        val direct=task.assignees.filter {eligible(task.orgId,task.domain,it)}.map {it.userId}.toSet()
        val unavailable=task.assignees.map {it.userId}.toSet()-direct
        val needsCoordinator=task.assignees.isEmpty() || unavailable.isNotEmpty()
        val fallback=if(needsCoordinator)coordinators.filter {eligible(task.orgId,task.domain,CollectionSafetyAssignee(it))}.toSet() else emptySet()
        return CollectionSafetyRecipients(direct+fallback,unavailable,fallback,needsCoordinator && fallback.isEmpty())
    }
}
data class CollectionSafetyRecipients(val userIds:Set<String>,val unavailableUserIds:Set<String>,val coordinatorUserIds:Set<String>,val coordinationMissing:Boolean)
