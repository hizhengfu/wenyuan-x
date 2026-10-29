package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgerDao
import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgers
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionRegistrationNumberDao
import io.hizhengfu.cloud.collection.dao.CollectionRegistrationNumbers
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.domain.EnumRegistrationNoState
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

object CollectionRegistrationNumberPolicy {
    fun nextAvailable(
        existingCollectionNumbers: Set<String>,
        occupiedNumbers: Set<String>,
        width: Int = 6,
    ): String = CollectionRegistrationNumberGenerator.nextAvailable(
        existingCollectionNumbers + occupiedNumbers,
        width,
    )

    fun canUseManual(
        totalRegNo: String,
        occupiedNumbers: Map<String, EnumRegistrationNoState>,
    ): Boolean = totalRegNo.isNotBlank() && !occupiedNumbers.containsKey(totalRegNo)

    fun canKeepCurrent(
        collectionId: String?,
        holderCollectionId: String?,
        state: EnumRegistrationNoState,
    ): Boolean = collectionId != null &&
        holderCollectionId == collectionId &&
        state == EnumRegistrationNoState.USED
}

@Component
@Transactional
class CollectionRegistrationNumberService {
    fun nextRegNo(orgId: String): String =
        CollectionRegistrationNumberPolicy.nextAvailable(
            existingCollectionNumbers = existingCollectionNumbers(orgId),
            occupiedNumbers = occupiedNumbers(orgId).keys,
        )

    fun requireAvailable(orgId: String, totalRegNo: String, currentCollectionId: String? = null) {
        requireExactAvailable(orgId, totalRegNo.trim(), currentCollectionId)
    }

    /** 已存号码按原账精确匹配；输入规范化仅适用于新建预留。 */
    private fun requireExactAvailable(orgId: String, clean: String, currentCollectionId: String?) {
        if (clean.isBlank()) throw BadRequestException("总登记号不能为空")
        requireRetainedOwner(orgId, clean, currentCollectionId)
        val occupied = occupiedNumbers(orgId)
        val occupiedRow = find(orgId, clean)
        val canKeepCurrent = occupiedRow != null &&
            CollectionRegistrationNumberPolicy.canKeepCurrent(
                collectionId = currentCollectionId,
                holderCollectionId = occupiedRow.collectionId,
                state = occupiedRow.state,
            )
        if (!canKeepCurrent && !CollectionRegistrationNumberPolicy.canUseManual(clean, occupied)) {
            throw BadRequestException("总登记号「$clean」已被占用，不能重复使用")
        }
        requireMainOwner(orgId, clean, currentCollectionId)
    }

    /** 同一实物依法调回原馆时恢复原号的在藏状态；绝不把退出号分配给别的藏品。 */
    internal fun requireTransferAvailable(orgId: String,totalRegNo: String,collectionId: String) {
        requireRetainedOwner(orgId,totalRegNo,collectionId)
        val row=find(orgId,totalRegNo)
        if (row?.state == EnumRegistrationNoState.RETIRED && row.collectionId == collectionId &&
            CollectionRetainedLedgerDao.find { (CollectionRetainedLedgers.orgId eq orgId) and (CollectionRetainedLedgers.totalRegNo eq totalRegNo) and (CollectionRetainedLedgers.collectionId eq collectionId) }.any()) {
            if (CollectionDao.find { (Collections.orgId eq orgId) and (Collections.totalRegNo eq totalRegNo) }.any { it.id.value != collectionId }) throw BadRequestException("接收馆原号已被其他藏品占用，请核查总账")
            return
        }
        requireExactAvailable(orgId,totalRegNo,collectionId)
    }
    internal fun markTransferred(orgId: String,totalRegNo: String,collectionId: String,reason: String,refBizId: String?) {
        requireTransferAvailable(orgId,totalRegNo,collectionId)
        val row=find(orgId,totalRegNo)
        if (row?.state == EnumRegistrationNoState.RETIRED) {
            row.state=EnumRegistrationNoState.USED; row.reason=reason; row.refBizId=refBizId
        } else markUsed(orgId,totalRegNo,collectionId,reason,refBizId)
    }

    fun markUsed(orgId: String, totalRegNo: String, collectionId: String, reason: String, refBizId: String? = null) {
        // 调拨传入主档原号，新建调用者已完成输入规范化，不能在此改写历史身份。
        val clean = totalRegNo
        if (clean.isBlank()) throw BadRequestException("总登记号不能为空")
        val row = find(orgId, clean)
        if (row == null) {
            CollectionRegistrationNumberDao.new {
                this.orgId = orgId
                this.totalRegNo = clean
                this.collectionId = collectionId
                state = EnumRegistrationNoState.USED
                this.reason = reason
                this.refBizId = refBizId
            }
            return
        }
        if (row.state == EnumRegistrationNoState.RETIRED) {
            throw BadRequestException("总登记号「$clean」已被占用，不能重复使用")
        }
        if (row.collectionId != null && row.collectionId != collectionId) {
            throw BadRequestException("总登记号「$clean」已被占用，不能重复使用")
        }
        row.collectionId = collectionId
        row.state = EnumRegistrationNoState.USED
        row.reason = reason
        row.refBizId = refBizId
    }

    fun retire(orgId: String, totalRegNo: String, collectionId: String?, reason: String, refBizId: String? = null) {
        val clean = totalRegNo.takeIf { it.isNotBlank() } ?: return
        requireMainOwner(orgId, clean, collectionId)
        val row = find(orgId, clean)
        if (row == null) {
            CollectionRegistrationNumberDao.new {
                this.orgId = orgId
                this.totalRegNo = clean
                this.collectionId = collectionId
                state = EnumRegistrationNoState.RETIRED
                this.reason = reason
                this.refBizId = refBizId
            }
            return
        }
        if (row.collectionId != null && row.collectionId != collectionId) {
            throw BadRequestException("总登记号「$clean」占号归属与当前藏品不一致，请先核查总账")
        }
        row.state = EnumRegistrationNoState.RETIRED
        row.reason = reason
        row.refBizId = refBizId
    }

    /** 旧库缺失唯一索引时也须检查所有同号主档，不能只信第一条或占号表。 */
    private fun requireMainOwner(orgId: String, totalRegNo: String, collectionId: String?) {
        if (CollectionDao.find {
            (Collections.orgId eq orgId) and (Collections.totalRegNo eq totalRegNo)
        }.any { it.id.value != collectionId }) {
            throw BadRequestException("总登记号「$totalRegNo」已存在于其他藏品主档，请先核查总账")
        }
    }

    private fun find(orgId: String, totalRegNo: String): CollectionRegistrationNumberDao? {
        val rows = CollectionRegistrationNumberDao.find {
            (CollectionRegistrationNumbers.orgId eq orgId) and
                (CollectionRegistrationNumbers.totalRegNo eq totalRegNo)
        }.limit(2).toList()
        if (rows.size > 1) {
            throw BadRequestException("总登记号「$totalRegNo」存在多条占号记录，请先核查历史数据")
        }
        return rows.singleOrNull()
    }

    private fun requireRetainedOwner(orgId: String,totalRegNo: String,collectionId: String?) {
        val retained = CollectionRetainedLedgers.select(CollectionRetainedLedgers.collectionId).where {
            (CollectionRetainedLedgers.orgId eq orgId) and (CollectionRetainedLedgers.totalRegNo eq totalRegNo)
        }
        if (retained.any { it[CollectionRetainedLedgers.collectionId] != collectionId }) {
            throw BadRequestException("总登记号「$totalRegNo」已记入原馆留存总账，不能分配给其他藏品")
        }
    }

    private fun existingCollectionNumbers(orgId: String): Set<String> =
        CollectionDao.find { Collections.orgId eq orgId }.map { it.totalRegNo }.toSet() +
            CollectionRetainedLedgers.select(CollectionRetainedLedgers.totalRegNo)
                .where { CollectionRetainedLedgers.orgId eq orgId }
                .map { it[CollectionRetainedLedgers.totalRegNo] }.toSet()

    private fun occupiedNumbers(orgId: String): Map<String, EnumRegistrationNoState> =
        CollectionRegistrationNumberDao.find { CollectionRegistrationNumbers.orgId eq orgId }
            .associate { it.totalRegNo to it.state }
}
