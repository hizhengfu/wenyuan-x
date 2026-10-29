package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionFragilityAssessments : OrgAuditUIDTable("col_fragility_assessment"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val catalog=json("catalog",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCatalogContentVo::class.java)!!})
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragilityAssessmentVo::class.java)!!})
    val conservation=json("conservation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val keeper=json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("collection_version",unique=true),collectionId,version)}
}
class CollectionFragilityAssessmentDao(id: EntityID<String>): OrgAuditUIDEntity(id,CollectionFragilityAssessments) {
    companion object: OrgAuditUIDEntityClass<CollectionFragilityAssessmentDao>(CollectionFragilityAssessments)
    var collectionId by CollectionFragilityAssessments.collectionId
    var version by CollectionFragilityAssessments.version
    var catalog by CollectionFragilityAssessments.catalog
    var content by CollectionFragilityAssessments.content
    var conservation by CollectionFragilityAssessments.conservation
    var keeper by CollectionFragilityAssessments.keeper
}
object CollectionFragileUsePlans : OrgAuditUIDTable("col_fragile_use_plan"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val sourceBizType=varchar("source_biz_type",64)
    val sourceBizId=varchar("source_biz_id",64)
    val sourceSnapshot=text("source_snapshot")
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileUsePlanVo::class.java)!!})
    val conservation=json("conservation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val keeper=json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init {index(shortIndexName("collection_source"),false,collectionId,sourceBizType,sourceBizId)}
}
class CollectionFragileUsePlanDao(id: EntityID<String>): OrgAuditUIDEntity(id,CollectionFragileUsePlans) {
    companion object: OrgAuditUIDEntityClass<CollectionFragileUsePlanDao>(CollectionFragileUsePlans)
    var collectionId by CollectionFragileUsePlans.collectionId
    var sourceBizType by CollectionFragileUsePlans.sourceBizType
    var sourceBizId by CollectionFragileUsePlans.sourceBizId
    var sourceSnapshot by CollectionFragileUsePlans.sourceSnapshot
    var content by CollectionFragileUsePlans.content
    var conservation by CollectionFragileUsePlans.conservation
    var keeper by CollectionFragileUsePlans.keeper
}
object CollectionFragileUseRecords : OrgAuditUIDTable("col_fragile_use_record"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val environmentArchiveId=varchar("environment_archive_id",64).nullable()
    val authorizationAdjustmentId=varchar("authorization_adjustment_id",64).nullable()
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val planId=varchar("plan_id",64).nullable().index(shortIndexName("plan_id"))
    val started=json("started",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileUseStartVo::class.java)!!}).nullable()
    val ended=json("ended",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileUseEndVo::class.java)!!}).nullable()
    val review=json("review",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileUseReviewVo::class.java)!!}).nullable()
    val history=json("history",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileUseHistoryVo::class.java)!!}).nullable()
}
class CollectionFragileUseRecordDao(id: EntityID<String>): OrgAuditUIDEntity(id,CollectionFragileUseRecords) {
    companion object: OrgAuditUIDEntityClass<CollectionFragileUseRecordDao>(CollectionFragileUseRecords)
    var collectionId by CollectionFragileUseRecords.collectionId
    var planId by CollectionFragileUseRecords.planId
    var environmentArchiveId by CollectionFragileUseRecords.environmentArchiveId
    var authorizationAdjustmentId by CollectionFragileUseRecords.authorizationAdjustmentId
    var started by CollectionFragileUseRecords.started
    var ended by CollectionFragileUseRecords.ended
    var review by CollectionFragileUseRecords.review
    var history by CollectionFragileUseRecords.history
}

object CollectionFragileUseAdjustments : OrgAuditUIDTable("col_fragile_use_adjustment") {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val proposedBy=varchar("proposed_by",64)
    val factsSnapshot=text("facts_snapshot")
    val actors=json("actors",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileAdjustmentActors::class.java)!!})
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileUseAdjustmentVo::class.java)!!})
    val conservation=json("conservation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val department=json("department",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val keeper=json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val director=json("director",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val withdrawal=json("withdrawal",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,FragileAdjustmentWithdrawalVo::class.java)!!}).nullable()
}
class CollectionFragileUseAdjustmentDao(id: EntityID<String>): OrgAuditUIDEntity(id,CollectionFragileUseAdjustments) {
    companion object: OrgAuditUIDEntityClass<CollectionFragileUseAdjustmentDao>(CollectionFragileUseAdjustments)
    var collectionId by CollectionFragileUseAdjustments.collectionId
    var planId by CollectionFragileUseAdjustments.planId
    var proposedBy by CollectionFragileUseAdjustments.proposedBy
    var factsSnapshot by CollectionFragileUseAdjustments.factsSnapshot
    var actors by CollectionFragileUseAdjustments.actors
    var content by CollectionFragileUseAdjustments.content
    var conservation by CollectionFragileUseAdjustments.conservation
    var department by CollectionFragileUseAdjustments.department
    var keeper by CollectionFragileUseAdjustments.keeper
    var director by CollectionFragileUseAdjustments.director
    var withdrawal by CollectionFragileUseAdjustments.withdrawal
}
