package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadAuthorizationPhase
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.jetbrains.exposed.v1.jdbc.select

/** 独立原件入口只使用登录主体 ID；每次读取均从当前关系表核对资格和馆别。 */
@Component
class CollectionFileReadAuthorization(
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
) : FileReadAuthorizationPhase {
    private class MetadataIdentity(val user: SecurityUser, val museums: List<List<Any?>>) {
        val visible = mutableMapOf<String, Set<String>?>()
        var defaultMuseum: String? = null
    }
    private val metadataIdentity = ThreadLocal<MetadataIdentity?>()
    private data class OrgFact(val id: String, val parentId: String, val systemGroupId: String?,
                               val status: EnumContentStatus, val orgStatus: EnumOrgStatus)
    private class ContentIdentity(val actorId: String?) {
        var user: SecurityUser? = null
        var orgFacts: Map<String, OrgFact>? = null
        val visible = mutableMapOf<String, Set<String>?>()
        var multi: Boolean? = null
        var defaultMuseum: String? = null
    }
    private val contentIdentity = ThreadLocal<ContentIdentity?>()

    private fun orgFacts(): Map<String, OrgFact> {
        val o = CollectionOrgs
        return o.select(o.id, o.parentId, o.systemGroupId, o.status, o.orgStatus).orderBy(o.id)
            .associate { row -> row[o.id].value.let { id ->
                id to OrgFact(id, row[o.parentId], row[o.systemGroupId], row[o.status], row[o.orgStatus])
            } }
    }

    private fun requireSameActor(read: ContentIdentity) {
        if (SecurityUtils.getNullUserDetail()?.id != read.actorId)
            throw BadRequestException("读取期间登录身份已变化，请重新核查原件")
    }

    /** 仅共享当前一次内容核验阶段的身份输入；嵌套调用和输出复核均建立新阶段。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    override fun <T> withReadPhase(body: () -> T): T {
        if (metadataIdentity.get() != null)
            throw BadRequestException("历史目录核验不能代替原件内容读取核验")
        val previous = contentIdentity.get()
        val read = ContentIdentity(SecurityUtils.getNullUserDetail()?.id)
        contentIdentity.set(read)
        try {
            val result = body()
            requireSameActor(read)
            read.user?.let { before ->
                // 直接鲜读目录与范围，不能调用下面会复用本阶段输入的 getter。
                val current = directory.requireActive(before.id)
                if (identity(current) != identity(before) || read.visible.any { (permission, visible) ->
                        scope.currentReadableOrgIds(current, permission) != visible
                    }) throw BadRequestException("读取期间人员资格或馆别范围已变化，请重新核查原件")
            }
            if (read.orgFacts?.let { orgFacts() != it } == true ||
                read.multi?.let { scope.isMulti() != it } == true ||
                read.defaultMuseum?.let { scope.currentOrgId() != it } == true)
                throw BadRequestException("读取期间收藏单位或后端默认馆已变化，请重新核查原件")
            requireSameActor(read)
            return result
        } finally {
            if (previous == null) contentIdentity.remove() else contentIdentity.set(previous)
        }
    }

    /** 没有受保护授权时不初始化账号，匿名普通文件仍可按原入口读取。 */
    internal fun currentReadActor(): SecurityUser? {
        val read = contentIdentity.get() ?: return currentMetadataActor()
        requireSameActor(read)
        if (read.user == null) {
            val id = read.actorId ?: throw BadRequestException("请登录后查阅业务原件")
            read.user = directory.requireActive(id)
            requireSameActor(read)
        }
        return read.user
    }

    internal fun readableOrgIds(user: SecurityUser, permission: String): Set<String>? {
        val read = contentIdentity.get() ?: return metadataReadableOrgIds(user, permission)
        requireSameActor(read)
        val actor = currentReadActor() ?: throw BadRequestException("请登录后查阅业务原件")
        if (actor.id != user.id) throw BadRequestException("读取原件的人员身份不一致")
        if (!read.visible.containsKey(permission))
            read.visible[permission] = scope.currentReadableOrgIds(actor, permission)?.toSet()
        return read.visible[permission]
    }

    private fun contentOrgFacts(read: ContentIdentity): Map<String, OrgFact> {
        requireSameActor(read)
        return read.orgFacts ?: orgFacts().also { read.orgFacts = it }
    }
    private fun identity(user: SecurityUser) = listOf(user.id, user.roles, user.roleCodes, user.permissions,
        user.groupId, user.groupIds, user.dataType)
    private fun museums(): List<List<Any?>> {
        val columns = listOf(CollectionOrgs.id, CollectionOrgs.systemGroupId, CollectionOrgs.status, CollectionOrgs.orgStatus)
        return CollectionOrgs.select(columns).orderBy(CollectionOrgs.id).map { row -> columns.map { row[it] } }
    }
    /** 仅在一次历史目录查询内使用当前数据库身份；返回前复核，不延续到字节读取或下一次请求。 */
    internal fun <T> withMetadataReadIdentity(body: () -> T): T {
        val previous = metadataIdentity.get()
        if (previous != null) return body()
        val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后查阅历史材料")
        val read = MetadataIdentity(directory.requireActive(id), museums())
        metadataIdentity.set(read)
        try {
            val result = body()
            val current = directory.requireActive(id)
            if (SecurityUtils.getNullUserDetail()?.id != id || identity(current) != identity(read.user) ||
                museums() != read.museums || read.visible.any { (permission, visible) ->
                    scope.currentReadableOrgIds(current, permission) != visible
                } || read.defaultMuseum?.let { scope.isMulti() || scope.currentOrgId() != it } == true)
                throw BadRequestException("查询期间历史材料查阅身份或馆别已变化，请重新核查")
            return result
        } finally {
            metadataIdentity.remove()
        }
    }
    internal fun currentMetadataActor(): SecurityUser? = metadataIdentity.get()?.user
    internal fun metadataReadableOrgIds(user: SecurityUser, permission: String): Set<String>? {
        val read = metadataIdentity.get() ?: return scope.currentReadableOrgIds(user, permission)
        if (!read.visible.containsKey(permission)) read.visible[permission] = scope.currentReadableOrgIds(user, permission)?.toSet()
        return read.visible[permission]
    }

    /** 业务原件读取还须受当前单馆部署配置约束。 */
    fun requireBusinessReadable(orgId: String, permissions: Set<String>, crossOrgPermission: String, subject: String, roleCodes: Set<String> = emptySet()) {
        requireBusinessReadable(setOf(orgId), permissions, crossOrgPermission, subject, roleCodes)
    }

    /** 同一原件可被多家馆留存；先一次性核对全部引用，全部通过后才允许输出。 */
    fun requireBusinessReadable(orgIds: Set<String>, permissions: Set<String>, crossOrgPermission: String, subject: String, roleCodes: Set<String> = emptySet()) {
        requireReadable(orgIds, permissions, crossOrgPermission, subject, roleCodes)
        requireDeploymentScope(orgIds, subject)
    }

    /** 与所选档案复用同一文件的来源只能收窄单馆范围；不替代所选档案的当前业务授权。 */
    fun requireDeploymentScope(orgIds: Set<String>, subject: String) {
        val content = contentIdentity.get()
        content?.let(::requireSameActor)
        val multi = if (content == null) scope.isMulti() else
            content.multi ?: scope.isMulti().also { content.multi = it }
        if (!multi && orgIds.isNotEmpty()) {
            val configuredOrgId = if (content == null) scope.currentOrgId() else
                content.defaultMuseum ?: scope.currentOrgId().also { content.defaultMuseum = it }
            metadataIdentity.get()?.let { read ->
                if (read.defaultMuseum != null && read.defaultMuseum != configuredOrgId)
                    throw BadRequestException("查询期间后端默认馆已变化，请重新核查")
                read.defaultMuseum = configuredOrgId
            }
            if (orgIds.any { it != configuredOrgId }) {
                throw BadRequestException("${subject}不属于当前配置收藏单位")
            }
        }
    }

    fun requireReadable(orgIds: Set<String>, permissions: Set<String>, crossOrgPermission: String, subject: String, roleCodes: Set<String> = emptySet()) {
        val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请先登录并取得${subject}读取权限")
        val user = currentReadActor()?.takeIf { it.id == id } ?: directory.requireActive(id)
        if (EnumRole.ADMIN.value !in user.roles && user.permissions.none { it in permissions } && user.roleCodes.none { it in roleCodes }) {
            throw BadRequestException("当前账号缺少${subject}读取权限")
        }
        val visible = readableOrgIds(user, crossOrgPermission)
        val facts = if (orgIds.isEmpty()) null else contentIdentity.get()?.let(::contentOrgFacts)
        orgIds.forEach { orgId ->
            val fact = facts?.get(orgId)
            val museum = if (facts == null) CollectionOrgDao.findById(orgId) else null
            if (fact == null && museum == null) throw BadRequestException("${subject}所属收藏单位不存在，请核对原记录")
            // 停用不销毁历史原件；当前明确的跨馆读取授权或实际管理员可继续查阅。
            if (visible != null && ((fact?.status ?: museum!!.status) != EnumContentStatus.NORMAL ||
                        (fact?.orgStatus ?: museum!!.orgStatus) != EnumOrgStatus.ENABLED)) {
                throw BadRequestException("${subject}所属收藏单位已停用，须核对当前历史原件查阅授权")
            }
            OrgScopedReadPolicy.requireVisible(visible, orgId, subject)
        }
    }
}
