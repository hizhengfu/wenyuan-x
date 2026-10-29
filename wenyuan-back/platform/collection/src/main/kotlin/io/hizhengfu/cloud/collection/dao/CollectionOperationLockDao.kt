package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 审批期间的藏品占用锁；一件藏品同一时间只能被一张在批业务单占用。 */
object CollectionOperationLocks : OrgAuditUIDTable("col_collection_operation_lock"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val bizType = varchar("biz_type", 100).index(shortIndexName("biz_type")).comment("业务类型")
    val bizId = varchar("biz_id", 64).index(shortIndexName("biz_id")).comment("业务单据")
    val lockedBy = varchar("locked_by", 64).nullable().comment("提交人")

    init {
        uniqueIndex(shortIndexName("org_id_collection_id", unique = true), orgId, collectionId)
        index(shortIndexName("org_id_biz_type_biz_id"), false, orgId, bizType, bizId)
    }
}

class CollectionOperationLockDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionOperationLocks) {
    companion object : OrgAuditUIDEntityClass<CollectionOperationLockDao>(CollectionOperationLocks)

    var collectionId by CollectionOperationLocks.collectionId
    var bizType by CollectionOperationLocks.bizType
    var bizId by CollectionOperationLocks.bizId
    var lockedBy by CollectionOperationLocks.lockedBy
}
