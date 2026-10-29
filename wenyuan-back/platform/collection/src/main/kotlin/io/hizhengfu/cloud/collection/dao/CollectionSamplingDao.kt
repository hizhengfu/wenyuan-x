package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionSamplings : FlowUIDTable("col_sampling") {
    val orgId = varchar("org_id",64).index(shortIndexName("org_id"))
    val collectionId = varchar("collection_id",64).index(shortIndexName("collection_id"))
    val title = varchar("title",500)
}
class CollectionSamplingDao(id:EntityID<String>):FlowUIDEntity(id,CollectionSamplings) {
    companion object:FlowUIDEntityClass<CollectionSamplingDao>(CollectionSamplings)
    var orgId by CollectionSamplings.orgId
    var collectionId by CollectionSamplings.collectionId
    var title by CollectionSamplings.title
}

object CollectionSamplingPlans:OrgAuditUIDTable("col_sampling_plan") {
    val samplingId=varchar("sampling_id",64).index(shortIndexName("sampling_id"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val sourceSnapshot=text("source_snapshot")
    val authority=enumerationByName<EnumAuthorityLevel>("authority",20)
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingPlanVo::class.java)!!})
    val started=json("started",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingStartVo::class.java)!!}).nullable()
    val ended=json("ended",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingExtractionEndVo::class.java)!!}).nullable()
    val incidentClearance=json("incident_clearance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingIncidentClearanceSnapshot::class.java)!!}).nullable()
    val completed=json("completed",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingCompletionVo::class.java)!!}).nullable()
    init { uniqueIndex(shortIndexName("sampling_version",unique=true),samplingId,version) }
}
class CollectionSamplingPlanDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionSamplingPlans) {
    companion object:OrgAuditUIDEntityClass<CollectionSamplingPlanDao>(CollectionSamplingPlans)
    var samplingId by CollectionSamplingPlans.samplingId
    var version by CollectionSamplingPlans.version
    var sourceSnapshot by CollectionSamplingPlans.sourceSnapshot
    var authority by CollectionSamplingPlans.authority
    var content by CollectionSamplingPlans.content
    var started by CollectionSamplingPlans.started
    var ended by CollectionSamplingPlans.ended
    var incidentClearance by CollectionSamplingPlans.incidentClearance
    var completed by CollectionSamplingPlans.completed
}

object CollectionSamples:OrgAuditUIDTable("col_sampling_sample") {
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val number=varchar("number",100)
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingTakeVo::class.java)!!})
    val analysis=json("analysis",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,SamplingAnalysisVo::class.java)!!}).nullable()
    init { uniqueIndex(shortIndexName("org_number",unique=true),orgId,number) }
}
class CollectionSampleDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionSamples) {
    companion object:OrgAuditUIDEntityClass<CollectionSampleDao>(CollectionSamples)
    var planId by CollectionSamples.planId
    var number by CollectionSamples.number
    var content by CollectionSamples.content
    var analysis by CollectionSamples.analysis
}
