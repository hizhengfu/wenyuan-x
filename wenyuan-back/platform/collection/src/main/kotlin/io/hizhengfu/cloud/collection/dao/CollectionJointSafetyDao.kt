package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionJointSafetyPlans : OrgAuditUIDTable("col_joint_safety_plan") {
    val requisitionId=varchar("requisition_id",64).index(shortIndexName("requisition_id"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val sourceSnapshot=text("source_snapshot")
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionJointSafetyPlanVo::class.java)!!})
    val keeper=json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val use=json("use_confirmation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val security=json("security",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("source_version",unique=true),requisitionId,version)}
}
class CollectionJointSafetyPlanDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionJointSafetyPlans) {
    companion object:OrgAuditUIDEntityClass<CollectionJointSafetyPlanDao>(CollectionJointSafetyPlans)
    var requisitionId by CollectionJointSafetyPlans.requisitionId
    var version by CollectionJointSafetyPlans.version
    var sourceSnapshot by CollectionJointSafetyPlans.sourceSnapshot
    var content by CollectionJointSafetyPlans.content
    var keeper by CollectionJointSafetyPlans.keeper
    var use by CollectionJointSafetyPlans.use
    var security by CollectionJointSafetyPlans.security
}
object CollectionJointSafetyRounds : OrgAuditUIDTable("col_joint_safety_round") {
    val requisitionId=varchar("requisition_id",64).index(shortIndexName("requisition_id"))
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val checksSnapshot=text("checks_snapshot")
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionJointSafetyRoundVo::class.java)!!})
    val keeper=json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val use=json("use_confirmation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val security=json("security",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val report=json("report",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionJointSafetyReportVo::class.java)!!}).nullable()
    val director=json("director",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
}
class CollectionJointSafetyRoundDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionJointSafetyRounds) {
    companion object:OrgAuditUIDEntityClass<CollectionJointSafetyRoundDao>(CollectionJointSafetyRounds)
    var requisitionId by CollectionJointSafetyRounds.requisitionId
    var planId by CollectionJointSafetyRounds.planId
    var checksSnapshot by CollectionJointSafetyRounds.checksSnapshot
    var content by CollectionJointSafetyRounds.content
    var keeper by CollectionJointSafetyRounds.keeper
    var use by CollectionJointSafetyRounds.use
    var security by CollectionJointSafetyRounds.security
    var report by CollectionJointSafetyRounds.report
    var director by CollectionJointSafetyRounds.director
}
