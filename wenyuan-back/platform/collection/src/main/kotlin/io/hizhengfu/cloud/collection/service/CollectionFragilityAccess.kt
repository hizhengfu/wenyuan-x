package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 人工脆弱保护入口使用当前身份；后台自动监测不通过此登录入口。 */
internal object CollectionFragilityAccess {
    fun require(scope: CollectionScopeResolver, orgId: String, write: Boolean) =
        CollectionCurrentOperationAccess.require(scope, orgId, write, setOf(CollectionConst.CARE), "材质脆弱性与使用保护")

    fun collection(scope: CollectionScopeResolver, id: String, write: Boolean = false, expectedOrgId: String? = null): CollectionDao {
        if (write) TransactionManager.current().entityCache.flush()
        val row = (if (write) CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull()
            else CollectionDao.findById(id)) ?: throw BadRequestException("藏品不存在")
        if (write) row.refresh()
        if (expectedOrgId != null && expectedOrgId != row.orgId) throw BadRequestException("藏品所属收藏单位已变化，请重新读取资料后办理")
        require(scope, row.orgId, write)
        return row
    }

    fun canOperate(scope: CollectionScopeResolver, orgId: String): Boolean {
        if (SecurityUtils.getNullUserDetail()?.authorities?.none {
            it.authority in setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.CARE)
        } != false) return false
        return try { require(scope, orgId, true); true } catch (_: BadRequestException) { false }
    }

    fun finish(scope: CollectionScopeResolver, row: CollectionDao, id: String, expectedOrgId: String? = null): String {
        // DAO 写入可能延迟到提交；先写齐归档，再核对期间变化的来源和当前资格。
        TransactionManager.current().entityCache.flush()
        row.refresh(flush = false)
        if (expectedOrgId != null && expectedOrgId != row.orgId) throw BadRequestException("藏品所属收藏单位已变化，本次办理已撤销，请重新读取")
        require(scope, row.orgId, true)
        return id
    }
}
