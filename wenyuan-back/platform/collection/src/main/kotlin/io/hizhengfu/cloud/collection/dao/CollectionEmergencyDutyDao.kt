package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionEmergencyDuties : OrgAuditUIDTable("col_emergency_duty") {
    val targetKey=varchar("target_key",64).index(shortIndexName("target_key"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val appointment=json("appointment",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,EmergencyDutyAppointment::class.java)!!})
    val handover=json("handover",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val acceptance=json("acceptance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("target_version",unique=true),orgId,targetKey,version)}
}
class CollectionEmergencyDutyDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionEmergencyDuties) {
    companion object:OrgAuditUIDEntityClass<CollectionEmergencyDutyDao>(CollectionEmergencyDuties)
    var targetKey by CollectionEmergencyDuties.targetKey
    var version by CollectionEmergencyDuties.version
    var appointment by CollectionEmergencyDuties.appointment
    var handover by CollectionEmergencyDuties.handover
    var acceptance by CollectionEmergencyDuties.acceptance
}
