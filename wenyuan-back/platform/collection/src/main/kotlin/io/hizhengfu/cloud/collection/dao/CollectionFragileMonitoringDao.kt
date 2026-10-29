package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionFragileMonitorings: OrgAuditUIDTable("col_fragile_monitoring"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val useRecordId=varchar("use_record_id",64).index(shortIndexName("use_record_id"))
    val request=json("request",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileEnvironmentMonitoringVo::class.java)!!})
    val evidenceSnapshot=text("evidence_snapshot")
    val clearance=json("clearance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareClearanceSnapshot::class.java)!!}).nullable()
}
class CollectionFragileMonitoringDao(id: EntityID<String>): OrgAuditUIDEntity(id,CollectionFragileMonitorings) {
    companion object: OrgAuditUIDEntityClass<CollectionFragileMonitoringDao>(CollectionFragileMonitorings)
    var collectionId by CollectionFragileMonitorings.collectionId
    var useRecordId by CollectionFragileMonitorings.useRecordId
    var request by CollectionFragileMonitorings.request
    var evidenceSnapshot by CollectionFragileMonitorings.evidenceSnapshot
    var clearance by CollectionFragileMonitorings.clearance
}
