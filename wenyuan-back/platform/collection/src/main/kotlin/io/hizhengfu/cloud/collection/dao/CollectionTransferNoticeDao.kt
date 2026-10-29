package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.DeregistrationNoticeVo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionTransferNotices : OrgAuditUIDTable("col_transfer_notice") {
    val transferId = varchar("transfer_id", 64).index(shortIndexName("transfer_id"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, DeregistrationNoticeVo::class.java)!! })
}
class CollectionTransferNoticeDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionTransferNotices) {
    companion object : OrgAuditUIDEntityClass<CollectionTransferNoticeDao>(CollectionTransferNotices)
    var transferId by CollectionTransferNotices.transferId
    var content by CollectionTransferNotices.content
}
