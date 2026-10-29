package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 总账留痕（append-only，Service 永不 update/delete —— 法定留痕） */
object CollectionLedgerLogs : OrgAuditUIDTable("col_ledger_log"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val totalRegNo = varchar("total_reg_no", 64).nullable().comment("总登记号（冗余便于追溯）")
    val action = enumeration<EnumLedgerAction>("action").comment("动作")
    val field = varchar("field", 64).nullable().comment("更正字段")
    val before = text("before").nullable()
    val after = text("after").nullable()
    val reason = varchar("reason", 500).nullable().comment("更正/注销事由")
    val refBizId = varchar("ref_biz_id", 64).nullable()

    init {
        index(shortIndexName("org_id_collection_id"), false, orgId, collectionId)
    }
}

class CollectionLedgerLogDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionLedgerLogs) {
    companion object : OrgAuditUIDEntityClass<CollectionLedgerLogDao>(CollectionLedgerLogs)

    var collectionId by CollectionLedgerLogs.collectionId
    var totalRegNo by CollectionLedgerLogs.totalRegNo
    var action by CollectionLedgerLogs.action
    var field by CollectionLedgerLogs.field
    var before by CollectionLedgerLogs.before
    var after by CollectionLedgerLogs.after
    var reason by CollectionLedgerLogs.reason
    var refBizId by CollectionLedgerLogs.refBizId

    fun toModel() = LedgerLogVo(
        id = id.value,
        collectionId = collectionId,
        totalRegNo = totalRegNo,
        action = action,
        field = field,
        before = before,
        after = after,
        reason = reason,
        refBizId = refBizId,
        createBy = createBy,
        createTime = createTime,
    )
}

data class LedgerLogVo(
    val id: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val collectionName: String? = null,
    val action: EnumLedgerAction? = null,
    val field: String? = null,
    val before: String? = null,
    val after: String? = null,
    val reason: String? = null,
    val refBizId: String? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
)
