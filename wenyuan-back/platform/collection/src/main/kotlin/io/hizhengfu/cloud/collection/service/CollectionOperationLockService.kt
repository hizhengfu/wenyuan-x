package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionOperationLockDao
import io.hizhengfu.cloud.collection.dao.CollectionOperationLocks
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CollectionOperationLockRef(
    val collectionId: String,
    val bizType: String,
    val bizId: String,
)

internal object CollectionOperationLockPolicy {
    fun conflicts(
        requestedCollectionIds: Collection<String>,
        bizType: String,
        bizId: String,
        existing: Collection<CollectionOperationLockRef>,
    ): List<CollectionOperationLockRef> {
        val requested = requestedCollectionIds.map(String::trim).filter(String::isNotBlank).toSet()
        return existing.filter {
            it.collectionId in requested && (it.bizType != bizType || it.bizId != bizId)
        }
    }
}

@Component
@Transactional
class CollectionOperationLockService(private val safetyGuards: List<CollectionOperationSafetyGuard> = emptyList()) {
    fun acquire(orgId: String, bizType: String, bizId: String, collectionIds: Collection<String>) {
        val ids = collectionIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return
        ids.sorted().forEach { id ->
            CollectionDao.find { Collections.id eq id }.forUpdate().toList()
            safetyGuards.forEach { it.requireAllowed(id, bizType, bizId) }
        }
        val existing = CollectionOperationLockDao.find {
            (CollectionOperationLocks.orgId eq orgId) and
                (CollectionOperationLocks.collectionId inList ids)
        }.map { CollectionOperationLockRef(it.collectionId, it.bizType, it.bizId) }
        val conflicts = CollectionOperationLockPolicy.conflicts(ids, bizType, bizId, existing)
        if (conflicts.isNotEmpty()) {
            val details = conflicts.joinToString("、") { lock ->
                val collection = CollectionDao.findById(lock.collectionId)
                "${collection?.totalRegNo ?: lock.collectionId}（${lock.bizType}/${lock.bizId}）"
            }
            throw BadRequestException("以下藏品正在其他审批流程中，不能重复提交：$details")
        }

        release(bizType, bizId)
        val userId = SecurityUtils.getNullUserDetail()?.id
        ids.forEach { collectionId ->
            CollectionOperationLockDao.new {
                this.orgId = orgId
                this.collectionId = collectionId
                this.bizType = bizType
                this.bizId = bizId
                lockedBy = userId
            }
        }
    }

    fun release(bizType: String, bizId: String) {
        CollectionOperationLockDao.find {
            (CollectionOperationLocks.bizType eq bizType) and
                (CollectionOperationLocks.bizId eq bizId)
        }.forEach { it.delete() }
    }

    fun releaseAll(bizType: String, bizIds: Collection<String>) {
        val ids = bizIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return
        CollectionOperationLocks.deleteWhere {
            (CollectionOperationLocks.bizType eq bizType) and (CollectionOperationLocks.bizId inList ids)
        }
    }

    /** 读页面可捕获预期占用限制；写入口仍让异常逃逸并回滚完整操作。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun requireUnlocked(collectionId: String, exceptBizType: String? = null, exceptBizId: String? = null) {
        CollectionDao.find { Collections.id eq collectionId }.forUpdate().toList()
        safetyGuards.forEach { it.requireAllowed(collectionId, exceptBizType, exceptBizId) }
        requireNoOtherApproval(collectionId, exceptBizType, exceptBizId)
    }

    /** 用于草稿编辑及未实施流程撤回时核对其他审批占用；不授权提交或取用，也不解除独立事故或清点限制。 */
    fun requireNoOtherApproval(collectionId: String, exceptBizType: String? = null, exceptBizId: String? = null) {
        CollectionDao.find { Collections.id eq collectionId }.forUpdate().toList()
        val lock = CollectionOperationLockDao.find {
            CollectionOperationLocks.collectionId eq collectionId
        }.firstOrNull() ?: return
        if (lock.bizId == exceptBizId && (exceptBizType == null || lock.bizType == exceptBizType)) return
        throw BadRequestException("藏品正在审批流程中（${lock.bizType}/${lock.bizId}），当前操作已锁定")
    }
}
