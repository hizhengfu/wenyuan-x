package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object ReproductionPlans: OrgAuditUIDTable("col_reproduction_plan"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val reproductionId=varchar("reproduction_id",64).index(shortIndexName("reproduction_id"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val sourceSnapshot=text("source_snapshot")
    val qualification=json("qualification",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ConservationQualificationEvidence::class.java)!!}).nullable()
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionPlanVo::class.java)!!})
    val started=json("started",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionStartVo::class.java)!!}).nullable()
    val completed=json("completed",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionCompletionVo::class.java)!!}).nullable()
    val stopped=json("stopped",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionStopVo::class.java)!!}).nullable()
    val clearance=json("clearance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionStopClearanceSnapshot::class.java)!!}).nullable()
    init { uniqueIndex(shortIndexName("reproduction_version",unique=true),reproductionId,version) }
}
class ReproductionPlanDao(id: EntityID<String>): OrgAuditUIDEntity(id,ReproductionPlans) {
    companion object: OrgAuditUIDEntityClass<ReproductionPlanDao>(ReproductionPlans)
    var reproductionId by ReproductionPlans.reproductionId
    var version by ReproductionPlans.version
    var sourceSnapshot by ReproductionPlans.sourceSnapshot
    var qualification by ReproductionPlans.qualification
    var content by ReproductionPlans.content
    var started by ReproductionPlans.started
    var completed by ReproductionPlans.completed
    var stopped by ReproductionPlans.stopped
    var clearance by ReproductionPlans.clearance
}
object ReproductionSafetyRecords: OrgAuditUIDTable("col_reproduction_safety"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionSafetyVo::class.java)!!})
}
class ReproductionSafetyDao(id: EntityID<String>): OrgAuditUIDEntity(id,ReproductionSafetyRecords) {
    companion object: OrgAuditUIDEntityClass<ReproductionSafetyDao>(ReproductionSafetyRecords)
    var planId by ReproductionSafetyRecords.planId
    var content by ReproductionSafetyRecords.content
}
/** 产物专册与原藏品总登记账分开，编号逐件永久保留。 */
object ReproductionOutputs: OrgAuditUIDTable("col_reproduction_output"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val number=varchar("number",100)
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionOutputVo::class.java)!!})
    val custody=largeText("custody").transform(
        wrap={JsonUtils.fromJson(it,ReproductionOutputCustodyLedger::class.java)!!},
        unwrap={JsonUtils.toJson(it)!!},
    ).nullable()
    init { uniqueIndex(shortIndexName("org_number",unique=true),orgId,number) }
}
class ReproductionOutputDao(id: EntityID<String>): OrgAuditUIDEntity(id,ReproductionOutputs) {
    companion object: OrgAuditUIDEntityClass<ReproductionOutputDao>(ReproductionOutputs)
    var planId by ReproductionOutputs.planId
    var number by ReproductionOutputs.number
    var content by ReproductionOutputs.content
    var custody by ReproductionOutputs.custody
}
object ReproductionOutputUses: OrgAuditUIDTable("col_reproduction_output_use"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val returned=json("returned",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionOutputReturnVo::class.java)!!}).nullable()
    val outputId=varchar("output_id",64).index(shortIndexName("output_id"))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,ReproductionOutputUseVo::class.java)!!})
}
class ReproductionOutputUseDao(id: EntityID<String>): OrgAuditUIDEntity(id,ReproductionOutputUses) {
    companion object: OrgAuditUIDEntityClass<ReproductionOutputUseDao>(ReproductionOutputUses)
    var returned by ReproductionOutputUses.returned
    var outputId by ReproductionOutputUses.outputId
    var content by ReproductionOutputUses.content
}
