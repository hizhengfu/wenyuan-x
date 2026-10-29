package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class CollectionOrgCodeService(
    private val scope: CollectionScopeResolver,
    private val installers: ObjectProvider<AbstractInstall>? = null,
) {
    fun context(orgId: String): CollectionOrgCodeContextVo {
        requireAccess(orgId, false)
        val node = CollectionOrgDao.findById(orgId) ?: fail("收藏单位不存在")
        val history = history(orgId)
        val used = used(node)
        return CollectionOrgCodeContextVo(orgId, node.code, used, runCatching { requireAccess(orgId, true) }.isSuccess,
            hash(node, used, history), history)
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE_ORG, CollectionConst.CACHE], allEntries = true)
    fun correct(orgId: String, request: CollectionOrgCodeCorrectionVo, expectedStateHash: String): CollectionOrgCodeContextVo {
        val node = lock(orgId)
        val actor = requireAccess(orgId, true)
        val reason = request.reason.trim()
        if (reason.isBlank() || reason.length > 500) fail("请填写代码更正原因，且不超过500字")
        val used = used(node)
        requireSame(node, used, expectedStateHash)
        change(node, request.newCode, reason, used, actor)
        return context(orgId)
    }

    /** 普通资料更新仅允许修改尚无业务引用的代码；拒绝静默忽略或绕过留痕入口。 */
    internal fun updateUnused(node: CollectionOrgDao, proposed: String?, expectedStateHash: String?) {
        if (proposed == null || proposed == node.code) return
        val code = CollectionOrgCodeRegistry.normalize(proposed)
        if (code == node.code) return
        val actor = requireAccess(node.id.value, true)
        val used = used(node)
        if (used) fail("收藏单位已有业务记录，请使用“代码留痕更正”，不能直接修改")
        requireSame(node, false, expectedStateHash.orEmpty())
        change(node, code, "未使用收藏单位代码修改", false, actor)
    }

    internal fun lock(orgId: String): CollectionOrgDao {
        TransactionManager.current().entityCache.flush()
        return CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull()?.also { it.refresh() }
            ?: fail("收藏单位不存在")
    }

    private fun change(node: CollectionOrgDao, proposed: String, reason: String, correction: Boolean, actor: String) {
        val code = CollectionOrgCodeRegistry.normalize(proposed)
        if (code == node.code) fail("新代码与当前代码相同")
        CollectionOrgCodeRegistry.claim(node.id.value, node.code)
        CollectionOrgCodeRegistry.claim(node.id.value, code)
        requireAccess(node.id.value, true)
        CollectionOrgCodeChanges.insert {
            it[id] = IdGenerate.nextId(); it[orgId] = node.id.value
            it[oldCode] = node.code; it[newCode] = code; it[this.reason] = reason
            it[this.correction] = correction; it[operatorId] = actor; it[operatedAt] = LocalDateTime.now()
        }
        node.code = code
        node.flush()
    }

    private fun requireAccess(orgId: String, write: Boolean): String {
        val id = SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后管理收藏单位代码")
        val actor = CollectionActorDirectory(scope).requireActive(id)
        if (EnumRole.ADMIN.value !in actor.roles && actor.permissions.none { it in setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.ORG_MANAGE) })
            fail("当前账号没有收藏单位管理权限")
        OrgScopedReadPolicy.requireVisible(if (write) scope.currentWritableOrgIds(actor) else scope.currentReadableOrgIds(actor, CollectionConst.CROSS_ORG), orgId, "收藏单位代码")
        return id
    }

    private fun history(orgId: String) = CollectionOrgCodeChanges.selectAll().where { CollectionOrgCodeChanges.orgId eq orgId }
        .orderBy(CollectionOrgCodeChanges.id to SortOrder.DESC).map {
            CollectionOrgCodeChangeVo(it[CollectionOrgCodeChanges.id].value, it[CollectionOrgCodeChanges.oldCode], it[CollectionOrgCodeChanges.newCode],
                it[CollectionOrgCodeChanges.reason], it[CollectionOrgCodeChanges.correction], it[CollectionOrgCodeChanges.operatorId], it[CollectionOrgCodeChanges.operatedAt])
        }

    private fun hash(node: CollectionOrgDao, used: Boolean, history: List<CollectionOrgCodeChangeVo>) =
        CollectionImportConfirmation.hash(listOf("org-code/v1", node.id.value, node.code, used, history.firstOrNull()?.id))

    private fun requireSame(node: CollectionOrgDao, used: Boolean, expected: String) {
        if (expected.isBlank() || expected != hash(node, used, history(node.id.value))) fail("收藏单位代码或使用情况已变化，请重新加载后办理")
    }

    private fun used(node: CollectionOrgDao): Boolean {
        val orgId = node.id.value
        if (CollectionOrgDao.find { CollectionOrgs.parentId eq orgId }.any() || !node.locationRootId.isNullOrBlank()) return true
        // 读取统一安装目录，包含已安装的保管、修复等扩展模块；停用/历史业务同样算使用过。
        val tables = installers?.orderedStream()?.flatMap { it.initTables().orEmpty().stream() }?.toList()?.takeIf { it.isNotEmpty() }
            ?: CollectionInstall("multi").initTables().toList()
        return tables.distinct().filter { it !in setOf(CollectionOrgs, CollectionOrgCodes, CollectionOrgCodeChanges) }.any { table ->
            val columns = table.columns.filter { it.name == "org_id" || it.name.endsWith("_org_id") }
            columns.isNotEmpty() && table.exists() && table.select(columns.first()).where {
                columns.map { @Suppress("UNCHECKED_CAST") ((it as Column<String?>) eq orgId) }.compoundOr()
            }.limit(1).any()
        }
    }

    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
