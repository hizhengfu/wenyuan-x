package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionReturns
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.EnumReturnExecutionState
import io.hizhengfu.cloud.collection.dao.Loans
import io.hizhengfu.cloud.collection.dao.Requisitions
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanExpectedItems
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanReads
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAnomalyType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidReadClassification
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.LocalDateTime

internal data class RfidPassageBusinessCheck(
    val allowed: Boolean,
    val reason: String,
    val anomalyType: EnumRfidAnomalyType? = null,
    val sessionId: String? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val direction: EnumRfidDirection? = null,
)

/** 复用已完成的扫描及业务执行事实，不另建门禁授权，也不依赖扫描工作站或门禁绑定。 */
internal object RfidPassageBusinessVerifier {
    fun verify(collection: ResultRow, epc: String, tid: String?, direction: EnumRfidDirection, at: LocalDateTime): RfidPassageBusinessCheck {
        val collectionId = collection[Collections.id].value
        val orgId = collection[Collections.orgId]
        val session = RfidScanExpectedItems
            .join(RfidScanSessions, JoinType.INNER, RfidScanExpectedItems.sessionId, RfidScanSessions.id)
            .selectAll().where {
                (RfidScanExpectedItems.collectionId eq collectionId) and
                    (RfidScanSessions.orgId eq orgId) and
                    (RfidScanSessions.sessionState eq EnumRfidScanSessionState.FINALIZED) and
                    (RfidScanSessions.sessionType inList MOVEMENT_TYPES) and
                    (RfidScanSessions.finalizedAt lessEq at)
            }.orderBy(RfidScanSessions.finalizedAt to SortOrder.DESC, RfidScanSessions.id to SortOrder.DESC)
            .limit(1).firstOrNull()
            ?: return RfidPassageBusinessCheck(false, "未完成 RFID 出入库流程", EnumRfidAnomalyType.UNAUTHORIZED_PASSAGE)

        val sessionId = session[RfidScanSessions.id].value
        val sourceId = session[RfidScanSessions.businessId]
        val type = session[RfidScanSessions.sessionType]
        val expectedDirection = if (type == EnumRfidScanSessionType.RETURN_IN) EnumRfidDirection.IN else EnumRfidDirection.OUT
        val sourceType = when (type) {
            EnumRfidScanSessionType.LOAN_OUT -> CollectionConst.BIZ_LOAN
            EnumRfidScanSessionType.REQUISITION_OUT -> CollectionConst.BIZ_REQUISITION
            else -> CollectionConst.BIZ_RETURN
        }
        fun result(allowed: Boolean, reason: String, anomaly: EnumRfidAnomalyType? = null) =
            RfidPassageBusinessCheck(allowed, reason, anomaly, sessionId, sourceType, sourceId, expectedDirection)

        val read = RfidScanReads.selectAll().where {
            (RfidScanReads.sessionId eq sessionId) and (RfidScanReads.epc eq epc) and
                (RfidScanReads.resolvedCollectionId eq collectionId) and
                (RfidScanReads.classification eq EnumRfidReadClassification.EXPECTED)
        }.limit(1).firstOrNull()
        if (session[RfidScanExpectedItems.expectedEpc] != epc || read == null) {
            return result(false, "标签未通过已完成出入库流程的 RFID 核验", EnumRfidAnomalyType.TAG_CONFLICT)
        }
        if (tid != null && listOfNotNull(session[RfidScanExpectedItems.expectedTid], read[RfidScanReads.tid]).any { it != tid }) {
            return result(false, "标签 TID 与出入库核验记录不一致", EnumRfidAnomalyType.TAG_CONFLICT)
        }
        if (direction != EnumRfidDirection.UNKNOWN && direction != expectedDirection) {
            return result(false, "通行方向与已完成的出入库流程不符", EnumRfidAnomalyType.WRONG_DIRECTION)
        }
        fun contains(ids: String?) = ids?.split(',')?.any { it.trim() == collectionId } == true
        // 部分归还不会把整张借展/提用单置为 returned；已归还的这一件也不能重用旧出库依据。
        if (expectedDirection == EnumRfidDirection.OUT && CollectionReturns.selectAll().where {
                (CollectionReturns.orgId eq orgId) and (CollectionReturns.sourceBizType eq sourceType) and
                    (CollectionReturns.sourceBizId eq (sourceId ?: "")) and
                    (CollectionReturns.flowStatus eq EnumFlowStatus.PASS) and
                    (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED)
            }.any { contains(it[CollectionReturns.collectionIds]) }) {
            return result(false, "该出库流程中的藏品已办理归还", EnumRfidAnomalyType.STATE_MISMATCH)
        }
        val completed = when (type) {
            EnumRfidScanSessionType.LOAN_OUT -> Loans.selectAll().where { Loans.id eq (sourceId ?: "") }.limit(1).firstOrNull()?.let {
                it[Loans.orgId] == orgId && contains(it[Loans.collectionIds]) && it[Loans.flowStatus] == EnumFlowStatus.PASS &&
                    it[Loans.outExecuted] && !it[Loans.returned] && collection[Collections.lifeStatus] == EnumCollectionStatus.ON_LOAN
            } == true
            EnumRfidScanSessionType.REQUISITION_OUT -> Requisitions.selectAll().where { Requisitions.id eq (sourceId ?: "") }.limit(1).firstOrNull()?.let {
                it[Requisitions.orgId] == orgId && contains(it[Requisitions.collectionIds]) && it[Requisitions.flowStatus] == EnumFlowStatus.PASS &&
                    it[Requisitions.outExecuted] && !it[Requisitions.returned] && it[Requisitions.supersededById] == null &&
                    collection[Collections.lifeStatus] == EnumCollectionStatus.IN_USE
            } == true
            EnumRfidScanSessionType.RETURN_IN -> CollectionReturns.selectAll().where { CollectionReturns.id eq (sourceId ?: "") }.limit(1).firstOrNull()?.let {
                it[CollectionReturns.orgId] == orgId && contains(it[CollectionReturns.collectionIds]) &&
                    contains(it[CollectionReturns.acceptedCollectionIds] ?: it[CollectionReturns.collectionIds]) &&
                    it[CollectionReturns.flowStatus] == EnumFlowStatus.PASS &&
                    it[CollectionReturns.executionState] == EnumReturnExecutionState.EXECUTED &&
                    collection[Collections.lifeStatus] == EnumCollectionStatus.IN_STORE
            } == true
            else -> false
        }
        return if (completed) result(true, "RFID ${if (expectedDirection == EnumRfidDirection.OUT) "出库" else "入库"}流程已完成")
        else result(false, "出入库单据未完成、已失效或藏品当前状态不符", EnumRfidAnomalyType.STATE_MISMATCH)
    }

    private val MOVEMENT_TYPES = listOf(EnumRfidScanSessionType.LOAN_OUT, EnumRfidScanSessionType.REQUISITION_OUT, EnumRfidScanSessionType.RETURN_IN)
}
