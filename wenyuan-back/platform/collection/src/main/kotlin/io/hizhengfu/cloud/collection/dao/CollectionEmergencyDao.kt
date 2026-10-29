package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionEmergencyPlans : OrgAuditUIDTable("col_emergency_plan") {
    val keeperDutyId=varchar("keeper_duty_id",64).nullable()
    val securityDutyId=varchar("security_duty_id",64).nullable()
    val directorDutyId=varchar("director_duty_id",64).nullable()
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionEmergencyPlanVo::class.java)!!})
    val keeper=json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val security=json("security",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val director=json("director",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("version_or_file",unique=true),orgId,version)}
}
class CollectionEmergencyPlanDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionEmergencyPlans) {
    companion object:OrgAuditUIDEntityClass<CollectionEmergencyPlanDao>(CollectionEmergencyPlans)
    var keeperDutyId by CollectionEmergencyPlans.keeperDutyId
    var securityDutyId by CollectionEmergencyPlans.securityDutyId
    var directorDutyId by CollectionEmergencyPlans.directorDutyId
    var version by CollectionEmergencyPlans.version
    var content by CollectionEmergencyPlans.content
    var keeper by CollectionEmergencyPlans.keeper
    var security by CollectionEmergencyPlans.security
    var director by CollectionEmergencyPlans.director
}

object CollectionEmergencyDrills : OrgAuditUIDTable("col_emergency_drill") {
    val evaluatorDutyId=varchar("evaluator_duty_id",64).nullable()
    val directorDutyId=varchar("director_duty_id",64).nullable()
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionEmergencyDrillVo::class.java)!!})
    val operationSnapshot=text("operation_snapshot")
    val evaluator=json("evaluator",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val director=json("director",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
}
class CollectionEmergencyDrillDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionEmergencyDrills) {
    companion object:OrgAuditUIDEntityClass<CollectionEmergencyDrillDao>(CollectionEmergencyDrills)
    var evaluatorDutyId by CollectionEmergencyDrills.evaluatorDutyId
    var directorDutyId by CollectionEmergencyDrills.directorDutyId
    var planId by CollectionEmergencyDrills.planId
    var content by CollectionEmergencyDrills.content
    var operationSnapshot by CollectionEmergencyDrills.operationSnapshot
    var evaluator by CollectionEmergencyDrills.evaluator
    var director by CollectionEmergencyDrills.director
}

object CollectionEmergencyImprovements : OrgAuditUIDTable("col_emergency_improvement") {
    val ownerDutyId=varchar("owner_duty_id",64).nullable()
    val reviewDutyId=varchar("review_duty_id",64).nullable()
    val reportRevisionId=varchar("report_revision_id",64).nullable()
    val issueBasis=varchar("issue_basis",64).nullable()
    val drillId=varchar("drill_id",64).index(shortIndexName("drill_id"))
    val issueNumber=integer("issue_number", checkConstraintName = shortCheckConstraintName("issue_number"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,EmergencyImprovementVo::class.java)!!})
    val review=json("review",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,EmergencyImprovementReview::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("version_or_file",unique=true),drillId,issueNumber,version)}
}
class CollectionEmergencyImprovementDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionEmergencyImprovements) {
    companion object:OrgAuditUIDEntityClass<CollectionEmergencyImprovementDao>(CollectionEmergencyImprovements)
    var ownerDutyId by CollectionEmergencyImprovements.ownerDutyId
    var reviewDutyId by CollectionEmergencyImprovements.reviewDutyId
    var reportRevisionId by CollectionEmergencyImprovements.reportRevisionId
    var issueBasis by CollectionEmergencyImprovements.issueBasis
    var drillId by CollectionEmergencyImprovements.drillId
    var issueNumber by CollectionEmergencyImprovements.issueNumber
    var version by CollectionEmergencyImprovements.version
    var content by CollectionEmergencyImprovements.content
    var review by CollectionEmergencyImprovements.review
}

object CollectionEmergencyFileAccesses : OrgAuditUIDTable("col_emergency_file_access") {
    val archiveId=varchar("archive_id",64).index(shortIndexName("archive_id"))
    val fileId=varchar("file_id",64).index(shortIndexName("file_id"))
    val pathKey=varchar("path_key",64).index(shortIndexName("path_key"))
    init {uniqueIndex(shortIndexName("version_or_file",unique=true),archiveId,fileId,pathKey)}
}
class CollectionEmergencyFileAccessDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionEmergencyFileAccesses) {
    companion object:OrgAuditUIDEntityClass<CollectionEmergencyFileAccessDao>(CollectionEmergencyFileAccesses)
    var archiveId by CollectionEmergencyFileAccesses.archiveId
    var fileId by CollectionEmergencyFileAccesses.fileId
    var pathKey by CollectionEmergencyFileAccesses.pathKey
}


object CollectionEmergencyDrillRevisions : OrgAuditUIDTable("col_emergency_drill_revision") {
    val recorderDutyId=varchar("recorder_duty_id",64).nullable()
    val evaluatorDutyId=varchar("evaluator_duty_id",64).nullable()
    val directorDutyId=varchar("director_duty_id",64).nullable()
    val drillId=varchar("drill_id",64).index(shortIndexName("drill_id"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val record=json("record",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,EmergencyDrillRevisionRecord::class.java)!!})
    val operationSnapshot=text("operation_snapshot")
    val evaluator=json("evaluator",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val director=json("director",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("drill_version",unique=true),drillId,version)}
}
class CollectionEmergencyDrillRevisionDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionEmergencyDrillRevisions) {
    companion object:OrgAuditUIDEntityClass<CollectionEmergencyDrillRevisionDao>(CollectionEmergencyDrillRevisions)
    var recorderDutyId by CollectionEmergencyDrillRevisions.recorderDutyId
    var evaluatorDutyId by CollectionEmergencyDrillRevisions.evaluatorDutyId
    var directorDutyId by CollectionEmergencyDrillRevisions.directorDutyId
    var drillId by CollectionEmergencyDrillRevisions.drillId
    var version by CollectionEmergencyDrillRevisions.version
    var record by CollectionEmergencyDrillRevisions.record
    var operationSnapshot by CollectionEmergencyDrillRevisions.operationSnapshot
    var evaluator by CollectionEmergencyDrillRevisions.evaluator
    var director by CollectionEmergencyDrillRevisions.director
}
