package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 保留原 PASS 审批和四阶段原件，另记未交接方案的终止执行及双方本人确认。 */
@Component
@Transactional
class CollectionTransferTerminationService(
    private val scope: CollectionScopeResolver,
    private val statutory: CollectionTransferStatutoryService,
    private val locks: CollectionOperationLockService,
    private val archives: CollectionPermanentArchiveService,
    private val binder: BusinessAttachmentBinder,
    private val users: CollectionResponsibleUserService,
) {
    private val nodes = listOf("source_keeper", "source_leader", "target_keeper", "target_leader")

    private fun rows(id: String) = CollectionTransferTerminationDao.find {
        CollectionTransferTerminations.transferId eq id
    }.sortedBy { it.id.value }.onEach(::verify)
    private fun verify(row: CollectionTransferTerminationDao) {
        val archive = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and
                (CollectionPermanentArchives.bizId eq row.transferId) and
                (CollectionPermanentArchives.phase eq "TERMINATION_${row.kind.name}")
        }.singleOrNull() ?: throw BadRequestException("终止办理永久原件缺失")
        val expected = mapOf("recordId" to row.id.value,"transferId" to row.transferId,"kind" to row.kind,
            "actorUserId" to row.actorUserId,"recordedAt" to row.recordedAt,"content" to JsonUtils.mapper.readTree(row.content))
        if (archive.orgId != row.orgId || archive.digest != CollectionCareEvidence.digest(archive.snapshot) ||
            JsonUtils.mapper.readTree(archive.snapshot) != JsonUtils.mapper.readTree(JsonUtils.toJson(expected)!!))
            throw BadRequestException("终止办理记录与永久原件不一致")
    }
    private fun kind(node: String) = when (node) {
        "source_keeper" -> EnumTransferTerminationRecord.SOURCE_KEEPER
        "source_leader" -> EnumTransferTerminationRecord.SOURCE_LEADER
        "target_keeper" -> EnumTransferTerminationRecord.TARGET_KEEPER
        "target_leader" -> EnumTransferTerminationRecord.TARGET_LEADER
        else -> throw BadRequestException("终止确认节点无效")
    }
    private fun files(value: String): String = value.split(',').map(String::trim).filter(String::isNotBlank).distinct()
        .map { binder.resolveSingle(it) ?: throw BadRequestException("终止原件不存在") }.joinToString(",")
        .takeIf(String::isNotBlank) ?: throw BadRequestException("请上传终止办理原件")
    private fun members(entity: CollectionTransferDao) = statutory.exchangeMembers(entity).sortedBy { it.id.value }
    private fun sameAuthority(left: CollectionTransferTerminationProposalVo, right: CollectionTransferTerminationProposalVo) =
        left.authorityDocumentNo.trim() == right.authorityDocumentNo.trim() &&
            left.authorityFileIds.split(',').map(String::trim).filter(String::isNotBlank).toSet() ==
            right.authorityFileIds.split(',').map(String::trim).filter(String::isNotBlank).toSet()

    private fun locked(id: String): CollectionTransferDao {
        val visible = CollectionTransferDao.findById(id) ?: throw BadRequestException("跨馆转移申请不存在")
        val target = visible.toOrgId ?: throw BadRequestException("接收馆缺失")
        CollectionLocationMutation.acquireAll(listOf(visible.orgId, target))
        val ids = members(visible).map { it.id.value }
        val group = ids.map { memberId -> CollectionTransferDao.find { CollectionTransfers.id eq memberId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("同组交换申请不存在") }.onEach { it.refresh(flush = false) }
        val entity = group.single { it.id.value == id }
        if (entity.orgId != visible.orgId || entity.toOrgId != target || members(entity).map { it.id.value } != ids)
            throw BadRequestException("双方馆别或同组范围已变化，请重新读取")
        val closed = group.map { CollectionTransferTerminationEvidence.isClosed(it.id.value) }
        if (closed.any { it } && !closed.all { it }) throw BadRequestException("同组交换终止状态不一致，请先核实永久原件")
        for (member in group) {
            if (member.flowStatus != EnumFlowStatus.PASS || member.executedAt != null || member.execution != null)
                throw BadRequestException("仅能整组终止已批准且尚未实际交接的交换方案")
            val memberId = member.id.value
            if (StorageMovementDao.find { StorageMovements.refBizId eq memberId }.count() > 0 ||
                CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq memberId }.count() > 0 ||
                CollectionTransferNoticeDao.find { CollectionTransferNotices.transferId eq memberId }.count() > 0)
                throw BadRequestException("同组已有交接流水、留存账或结果公告，不能按未交接终止")
            // 完成后的重放仍核验原方案和本人身份；藏品此后可能经另一申请合法移馆。
            if (closed.all { it }) continue
            val collection = CollectionDao.find { Collections.id eq member.collectionId }.forUpdate().singleOrNull()
                ?: throw BadRequestException("原藏品不存在，须先核实保管责任")
            collection.refresh(flush = false)
            if (collection.orgId != member.orgId || collection.lifeStatus != EnumCollectionStatus.IN_STORE)
                throw BadRequestException("同组藏品已经离开原馆或不在库，不能按未交接终止")
            val held = CollectionOperationLockDao.find { CollectionOperationLocks.collectionId eq member.collectionId }.forUpdate().toList()
            if (held.size != 1 || held.single().orgId != member.orgId || held.single().bizType != CollectionConst.BIZ_TRANSFER || held.single().bizId != memberId)
                throw BadRequestException("同组业务锁与批准方案不一致，请先核实")
        }
        return entity
    }

    private fun record(entity: CollectionTransferDao, kind: EnumTransferTerminationRecord, actor: String, content: Any, fileIds: String): String {
        val serialized = JsonUtils.toJson(content)!!
        val row = CollectionTransferTerminationDao.new {
            orgId = entity.orgId; transferId = entity.id.value; this.kind = kind
            actorUserId = actor; recordedAt = LocalDateTime.now(); this.content = serialized
        }
        archives.seal(entity.orgId, CollectionConst.BIZ_TRANSFER, entity.id.value,
            "TERMINATION_${kind.name}", mapOf("recordId" to row.id.value,"transferId" to entity.id.value,"kind" to kind,
                "actorUserId" to actor,"recordedAt" to row.recordedAt,"content" to content), fileIds)
        return row.id.value
    }

    fun detail(id: String): Map<String, Any?> {
        val entity = statutory.scoped(id)
        val group = members(entity)
        val records = rows(id)
        val closed = records.any { it.kind == EnumTransferTerminationRecord.CLOSED }
        return mapOf("transferId" to id,"approved" to (entity.flowStatus == EnumFlowStatus.PASS),
            "executed" to (entity.executedAt != null),"closed" to closed,
            "groupTransferIds" to group.map { it.id.value },
            "groupClosedCount" to group.count { CollectionTransferTerminationEvidence.isClosed(it.id.value) },
            "groupReadyCount" to group.count { member ->
                val evidence = if (member.id.value == id) records else rows(member.id.value)
                nodes.all { node -> evidence.any { it.kind == kind(node) } }
            },
            "groupPending" to group.any { CollectionTransferTerminationEvidence.isPending(it.id.value) },
            "currentUserId" to SecurityUtils.getUserId(),"actors" to entity.dossier?.let { statutory.expectedActors(entity).mapValues { pair -> pair.value.userId } },
            "nextNode" to if(closed || records.none { it.kind == EnumTransferTerminationRecord.PROPOSAL }) null else nodes.firstOrNull { node -> records.none { it.kind == kind(node) } },
            "records" to records.map { mapOf("id" to it.id.value,"kind" to it.kind,"actorUserId" to it.actorUserId,
                "recordedAt" to it.recordedAt,"content" to JsonUtils.mapper.readTree(it.content)) })
    }

    fun propose(id: String, value: CollectionTransferTerminationProposalVo): String {
        val entity = locked(id)
        CollectionCurrentOperationAccess.require(scope, entity.orgId, true, setOf(CollectionConst.STORAGE), "跨馆转移终止")
        if (value.proposerUserId != SecurityUtils.getUserId()) throw BadRequestException("须由实际经办人本人提出终止")
        users.requireMuseumMember(value.proposerUserId, entity.orgId)
        val previous = rows(id)
        for (member in members(entity).filter { it.id.value != id }) {
            val other = rows(member.id.value).singleOrNull { it.kind == EnumTransferTerminationRecord.PROPOSAL } ?: continue
            val otherValue = JsonUtils.fromJson(other.content, CollectionTransferTerminationProposalVo::class.java)!!
            if (!sameAuthority(value, otherValue)) throw BadRequestException("同组交换须引用同一撤回依据文号和原件")
        }
        val existing = previous.singleOrNull { it.kind == EnumTransferTerminationRecord.PROPOSAL }
        if (existing != null) {
            if (JsonUtils.mapper.readTree(existing.content) == JsonUtils.mapper.readTree(JsonUtils.toJson(value)!!)) return existing.id.value
            throw BadRequestException("终止方案已经留存，不得覆盖原依据")
        }
        CollectionTransferTerminationEvidence.requireNotClosed(id)
        if (previous.isNotEmpty()) throw BadRequestException("终止办理记录不完整，须先核实")
        val approvedAt = statutory.requireCurrentApprovalEvidence(entity)
        if (value.proposedAt.isBefore(approvedAt) || value.proposedAt.isAfter(LocalDateTime.now()) ||
            listOf(value.reason,value.authorityDocumentNo,value.authorityFileIds,value.custodyStatement,value.custodyFileIds).any(String::isBlank))
            throw BadRequestException("须提交批准后的真实终止时间、原因、行政依据及当前保管核对原件")
        val proof = files(value.authorityFileIds + "," + value.custodyFileIds)
        val result = record(entity, EnumTransferTerminationRecord.PROPOSAL, value.proposerUserId, value, proof)
        CollectionCurrentOperationAccess.require(scope, entity.orgId, true, setOf(CollectionConst.STORAGE), "跨馆转移终止")
        users.requireMuseumMember(value.proposerUserId, entity.orgId)
        return result
    }

    private fun closeReadyGroup(group: List<CollectionTransferDao>) {
        val records = group.associate { it.id.value to rows(it.id.value) }
        if (records.values.any { list -> list.any { it.kind == EnumTransferTerminationRecord.CLOSED } }) {
            if (records.values.all { list -> list.any { it.kind == EnumTransferTerminationRecord.CLOSED } }) return
            throw BadRequestException("同组交换终止状态不一致，请先核实永久原件")
        }
        val required = setOf(EnumTransferTerminationRecord.PROPOSAL) + nodes.map(::kind)
        if (records.values.any { list -> required.any { needed -> list.none { it.kind == needed } } }) return
        if (records.values.any { it.size != required.size }) throw BadRequestException("同组交换终止记录不完整或重复")
        val proposals = group.associate { member ->
            member.id.value to JsonUtils.fromJson(records.getValue(member.id.value).single { it.kind == EnumTransferTerminationRecord.PROPOSAL }.content,
                CollectionTransferTerminationProposalVo::class.java)!!
        }
        if (proposals.values.any { !sameAuthority(it, proposals.values.first()) })
            throw BadRequestException("同组交换终止依据不一致，不能部分解除批准")
        val evidence = group.map { member ->
            val id = member.id.value
            val list = records.getValue(id)
            val proposal = list.single { it.kind == EnumTransferTerminationRecord.PROPOSAL }
            users.requireMuseumMember(proposal.actorUserId, member.orgId)
            val confirmations = nodes.map { node ->
                val row = list.single { it.kind == kind(node) }
                val value = JsonUtils.fromJson(row.content, CollectionTransferTerminationConfirmationVo::class.java)!!
                if (value.nodeKey != node || value.proposalRecordId != proposal.id.value || value.confirmerUserId != row.actorUserId)
                    throw BadRequestException("同组本人确认与原方案不一致")
                statutory.requireActor(member, node, row.actorUserId, value.opinion)
                row
            }
            statutory.requireCurrentApprovalEvidence(member)
            val approved = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and
                    (CollectionPermanentArchives.bizId eq id)
            }.singleOrNull { it.phase.startsWith("APPROVED_") } ?: throw BadRequestException("同组原批准永久原件缺失")
            if (approved.orgId != member.orgId || approved.digest != CollectionCareEvidence.digest(approved.snapshot))
                throw BadRequestException("同组原批准永久原件摘要或馆别不一致")
            mapOf("transferId" to id,"approvedArchiveId" to approved.id.value,"approvedDigest" to approved.digest,
                "proposalId" to proposal.id.value,"confirmations" to confirmations.map { it.id.value })
        }
        val closure = mapOf("groupTransferIds" to group.map { it.id.value },"members" to evidence)
        for (member in group) {
            val id = member.id.value
            val final = records.getValue(id).single { it.kind == EnumTransferTerminationRecord.TARGET_LEADER }
            val signed = JsonUtils.fromJson(final.content, CollectionTransferTerminationConfirmationVo::class.java)!!.signedFileIds
            record(member, EnumTransferTerminationRecord.CLOSED, final.actorUserId, closure,
                files(proposals.getValue(id).authorityFileIds + "," + signed))
        }
        locks.releaseAll(CollectionConst.BIZ_TRANSFER, group.map { it.id.value })
    }

    fun confirm(id: String, value: CollectionTransferTerminationConfirmationVo): String {
        val entity = locked(id)
        val node = value.nodeKey
        val index = nodes.indexOf(node)
        if (index < 0) throw BadRequestException("终止确认节点无效")
        val actor = statutory.expectedActors(entity)[node] ?: throw BadRequestException("双方责任人缺失")
        CollectionCurrentOperationAccess.require(scope, actor.orgId, true, setOf(CollectionConst.STORAGE), "跨馆转移终止确认", setOf(actor.role))
        if (value.confirmerUserId != SecurityUtils.getUserId()) throw BadRequestException("须由本节点责任人本人确认")
        statutory.requireActor(entity, node, value.confirmerUserId, value.opinion)
        val all = rows(id)
        val proposal = all.singleOrNull { it.kind == EnumTransferTerminationRecord.PROPOSAL }
            ?: throw BadRequestException("须先留存终止执行依据")
        if (proposal.id.value != value.proposalRecordId) throw BadRequestException("终止方案版本已变化，请重新核对")
        val thisKind = kind(node)
        all.singleOrNull { it.kind == thisKind }?.let {
            if (JsonUtils.mapper.readTree(it.content) == JsonUtils.mapper.readTree(JsonUtils.toJson(value)!!)) return it.id.value
            throw BadRequestException("本人确认已留存，不得改写")
        }
        if (all.any { it.kind == EnumTransferTerminationRecord.CLOSED } ||
            nodes.take(index).any { prior -> all.none { it.kind == kind(prior) } } ||
            nodes.drop(index+1).any { later -> all.any { it.kind == kind(later) } })
            throw BadRequestException("双方须按保管负责人、主要负责人顺序确认同一终止方案")
        val proposedAt = JsonUtils.fromJson(proposal.content, CollectionTransferTerminationProposalVo::class.java)!!.proposedAt
        val latest = all.maxOf { it.recordedAt }
        if (value.confirmedAt.isBefore(proposedAt) || value.confirmedAt.isBefore(latest) || value.confirmedAt.isAfter(LocalDateTime.now()) ||
            value.opinion.isBlank() || value.signedFileIds.isBlank()) throw BadRequestException("请核实本人确认时间、意见与签署原件")
        val proof = files(value.signedFileIds)
        val confirmationId = record(entity, thisKind, value.confirmerUserId, value, proof)
        CollectionCurrentOperationAccess.require(scope, actor.orgId, true, setOf(CollectionConst.STORAGE), "跨馆转移终止确认", setOf(actor.role))
        statutory.requireActor(entity, node, value.confirmerUserId, value.opinion)
        if (index == nodes.lastIndex) {
            closeReadyGroup(members(entity))
            CollectionCurrentOperationAccess.require(scope, actor.orgId, true, setOf(CollectionConst.STORAGE), "跨馆转移终止确认", setOf(actor.role))
            statutory.requireActor(entity, node, value.confirmerUserId, value.opinion)
        }
        return confirmationId
    }
}

internal object CollectionTransferTerminationEvidence {
    private fun groupIds(id: String): Set<String> = CollectionTransferDao.findById(id)?.dossier
        ?.takeIf { it.kind == EnumCollectionTransferKind.STATE_EXCHANGE }?.exchangeTransferIds.orEmpty().ifEmpty { setOf(id) }
    private fun ownClosed(id: String): Boolean = CollectionTransferTerminationDao.find {
        (CollectionTransferTerminations.transferId eq id) and
            (CollectionTransferTerminations.kind eq EnumTransferTerminationRecord.CLOSED)
    }.count() > 0
    fun isPending(id: String): Boolean = CollectionTransferTerminationDao.find {
        CollectionTransferTerminations.transferId inList groupIds(id)
    }.any { it.kind != EnumTransferTerminationRecord.CLOSED } && !groupIds(id).all(::ownClosed)
    fun isClosed(id: String): Boolean = ownClosed(id)
    fun requireNotClosed(id: String) {
        if (groupIds(id).any(::ownClosed)) throw BadRequestException("本次已批准交换方案已整组终止执行，须另建申请并重新审批")
    }
    fun requireExecutable(id: String) {
        requireNotClosed(id)
        if (isPending(id)) throw BadRequestException("本次已批准方案正在办理终止执行，完成双方确认前不得实际交接")
    }
}
