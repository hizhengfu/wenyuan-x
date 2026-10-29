package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.CollectionCareClearanceSnapshot
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json

/** 调度状态可以重建；不是人工检查、实际停止或解限证明。id 对应实际使用记录。 */
object CollectionFragileMonitorRuntimes: OrgAuditUIDTable("col_fragile_monitor_runtime"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val nextAttemptAt=datetime("next_attempt_at").index(shortIndexName("next_attempt_at"))
    val lastAttemptAt=datetime("last_attempt_at").nullable()
    val lastError=text("last_error").nullable()
    val completed=bool("completed").default(false)
}
class CollectionFragileMonitorRuntimeDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionFragileMonitorRuntimes) {
    companion object:OrgAuditUIDEntityClass<CollectionFragileMonitorRuntimeDao>(CollectionFragileMonitorRuntimes)
    var collectionId by CollectionFragileMonitorRuntimes.collectionId
    var nextAttemptAt by CollectionFragileMonitorRuntimes.nextAttemptAt
    var lastAttemptAt by CollectionFragileMonitorRuntimes.lastAttemptAt
    var lastError by CollectionFragileMonitorRuntimes.lastError
    var completed by CollectionFragileMonitorRuntimes.completed
}
/** 系统区间证据独立保存，不写人工核验请求。 */
object CollectionFragileAutomaticChecks:OrgAuditUIDTable("col_fragile_auto_check") {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val useRecordId=varchar("use_record_id",64).index(shortIndexName("use_record_id"))
    val observedAt=datetime("observed_at")
    val fromAt=datetime("from_at")
    val toAt=datetime("to_at")
    val needsHandling=bool("needs_handling")
    val evidenceSnapshot=text("evidence_snapshot")
    val clearance=json("clearance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareClearanceSnapshot::class.java)!!}).nullable()
}
class CollectionFragileAutomaticCheckDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionFragileAutomaticChecks) {
    companion object:OrgAuditUIDEntityClass<CollectionFragileAutomaticCheckDao>(CollectionFragileAutomaticChecks)
    var collectionId by CollectionFragileAutomaticChecks.collectionId
    var useRecordId by CollectionFragileAutomaticChecks.useRecordId
    var observedAt by CollectionFragileAutomaticChecks.observedAt
    var fromAt by CollectionFragileAutomaticChecks.fromAt
    var toAt by CollectionFragileAutomaticChecks.toAt
    var needsHandling by CollectionFragileAutomaticChecks.needsHandling
    var evidenceSnapshot by CollectionFragileAutomaticChecks.evidenceSnapshot
    var clearance by CollectionFragileAutomaticChecks.clearance
}
