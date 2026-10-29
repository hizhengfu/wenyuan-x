package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumTransferTerminationRecord
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime

object CollectionTransferTerminations : OrgAuditUIDTable("col_transfer_termination") {
    val transferId = varchar("transfer_id", 64).index(shortIndexName("transfer_id"))
    val kind = enumeration<EnumTransferTerminationRecord>("kind")
    val actorUserId = varchar("actor_user_id", 64)
    val recordedAt = datetime("recorded_at")
    val content = text("content")

    init { uniqueIndex(shortIndexName("transfer_kind", unique = true), transferId, kind) }
}

class CollectionTransferTerminationDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionTransferTerminations) {
    companion object : OrgAuditUIDEntityClass<CollectionTransferTerminationDao>(CollectionTransferTerminations)
    var transferId by CollectionTransferTerminations.transferId
    var kind by CollectionTransferTerminations.kind
    var actorUserId by CollectionTransferTerminations.actorUserId
    var recordedAt by CollectionTransferTerminations.recordedAt
    var content by CollectionTransferTerminations.content
}
