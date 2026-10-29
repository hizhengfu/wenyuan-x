package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import org.jetbrains.exposed.v1.javatime.datetime

/** 离线业务操作的确认凭据；与业务写入同事务，避免应答丢失后重复签署或覆盖后续更正。 */
object MobileOperationReceipts : UIDTable("col_mobile_operation_receipt") {
    val orgId = varchar("org_id", 64)
    val userId = varchar("user_id", 64)
    val operationId = varchar("operation_id", 100)
    val target = varchar("target", 100)
    val payloadHash = varchar("payload_hash", 64)
    val resultId = varchar("result_id", 64)
    val createdAt = datetime("created_at")
    init { uniqueIndex(shortIndexName("mobile_operation", unique = true), orgId, userId, operationId) }
}
