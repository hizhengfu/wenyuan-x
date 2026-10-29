package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionSetAmendment
import io.hizhengfu.cloud.collection.domain.CollectionChangeVo
import io.hizhengfu.cloud.collection.domain.CollectionChangeCorrectionOrigin
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json

/** 提交后不可改写的变更申请；批准与纸质订正完成后才物化到总账。 */
object CollectionChanges : FlowUIDTable("col_change") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id"))
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id"))
    val reason = text("reason")
    val before = json("before_content", { JsonUtils.toJson(it) ?: "{}" }, { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java) ?: CollectionCatalogContentVo() })
    val content = json("proposed_content", { JsonUtils.toJson(it) ?: "{}" }, { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java) ?: CollectionCatalogContentVo() })
    val baseline = json("ledger_baseline", { JsonUtils.toJson(it) ?: "{}" }, { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java) ?: CollectionCatalogContentVo() })
    val keeperUserId = varchar("keeper_user_id", 64).nullable()
    val leaderUserId = varchar("leader_user_id", 64).nullable()
    val evidenceFileIds = text("evidence_file_ids").nullable()
    val signedChangeFileIds = text("signed_change_file_ids").nullable()
    val paperLedgerRef = varchar("paper_ledger_ref", 500).nullable()
    val paperLedgerBookId = varchar("paper_ledger_book_id", 64).nullable()
    val paperLedgerPageNo = integer("paper_ledger_page_no", checkConstraintName = shortCheckConstraintName("paper_ledger_page_no")).nullable()
    val paperUpdatedAt = date("paper_updated_at").nullable()
    val appliedAt = datetime("applied_at").nullable()
    val appliedBy = varchar("applied_by", 64).nullable()
    val previousChangeId = varchar("previous_change_id", 64).nullable().index(shortIndexName("previous_change_id"))
    val correctionRequestKey = varchar("correction_request_key", 64).nullable().uniqueIndex(shortIndexName("correction_request_key", unique = true))
    val setAmendment = json<CollectionSetAmendment>("set_amendment", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionSetAmendment::class.java)!! }).nullable()
    val correction = json<CollectionChangeCorrectionOrigin>("correction_origin", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionChangeCorrectionOrigin::class.java)!! }).nullable()
}

class CollectionChangeDao(id: EntityID<String>) : FlowUIDEntity(id, CollectionChanges) {
    companion object : FlowUIDEntityClass<CollectionChangeDao>(CollectionChanges)
    var orgId by CollectionChanges.orgId
    var collectionId by CollectionChanges.collectionId
    var reason by CollectionChanges.reason
    var before by CollectionChanges.before
    var content by CollectionChanges.content
    var baseline by CollectionChanges.baseline
    var keeperUserId by CollectionChanges.keeperUserId
    var leaderUserId by CollectionChanges.leaderUserId
    var evidenceFileIds by CollectionChanges.evidenceFileIds
    var signedChangeFileIds by CollectionChanges.signedChangeFileIds
    var paperLedgerRef by CollectionChanges.paperLedgerRef
    var paperLedgerBookId by CollectionChanges.paperLedgerBookId
    var paperLedgerPageNo by CollectionChanges.paperLedgerPageNo
    var paperUpdatedAt by CollectionChanges.paperUpdatedAt
    var appliedAt by CollectionChanges.appliedAt
    var appliedBy by CollectionChanges.appliedBy
    var previousChangeId by CollectionChanges.previousChangeId
    var correctionRequestKey by CollectionChanges.correctionRequestKey
    var setAmendment by CollectionChanges.setAmendment
    var correction by CollectionChanges.correction
    fun toModel() = CollectionChangeVo(
        id = id.value, orgId = orgId, collectionId = collectionId, reason = reason,
        content = content, before = before, keeperUserId = keeperUserId, leaderUserId = leaderUserId,
        evidenceFileIds = evidenceFileIds, signedChangeFileIds = signedChangeFileIds,
        paperLedgerRef = paperLedgerRef, paperLedgerBookId = paperLedgerBookId, paperLedgerPageNo = paperLedgerPageNo,
        paperUpdatedAt = paperUpdatedAt,
        appliedAt = appliedAt, appliedBy = appliedBy, flowStatus = flowStatus,
        flowInstanceId = flowInstanceId, createTime = createTime,
        correction = correction, setAmendment = setAmendment,
    )
}

/** 记录实际办理人，避免委托、转交或管理员跳节点被当作法定责任人确认。 */
object CollectionChangeApprovals : OrgAuditUIDTable("col_change_approval") {
    val changeId = varchar("change_id", 64)
    val instanceId = varchar("instance_id", 64)
    val nodeKey = varchar("node_key", 64)
    val operator = varchar("operator", 64)
    val opinion = text("opinion")
    val taskId = varchar("task_id", 64).uniqueIndex(shortIndexName("task_id", unique = true))
    init { index(shortIndexName("change_id_instance_id"), false, changeId, instanceId) }
}

class CollectionChangeApprovalDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionChangeApprovals) {
    companion object : OrgAuditUIDEntityClass<CollectionChangeApprovalDao>(CollectionChangeApprovals)
    var changeId by CollectionChangeApprovals.changeId
    var instanceId by CollectionChangeApprovals.instanceId
    var nodeKey by CollectionChangeApprovals.nodeKey
    var operator by CollectionChangeApprovals.operator
    var opinion by CollectionChangeApprovals.opinion
    var taskId by CollectionChangeApprovals.taskId
}
