package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionCarePlans : OrgAuditUIDTable("col_care_plan"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id",64).index(shortIndexName("collection_id"))
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val catalog = json("catalog",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCatalogContentVo::class.java)!!})
    val content = json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCarePlanVo::class.java)!!})
    val conservation = json("conservation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    val keeper = json("keeper",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareSignature::class.java)!!}).nullable()
    init { uniqueIndex(shortIndexName("collection_version",unique=true),collectionId,version) }
}
class CollectionCarePlanDao(id: EntityID<String>) : OrgAuditUIDEntity(id,CollectionCarePlans) {
    companion object : OrgAuditUIDEntityClass<CollectionCarePlanDao>(CollectionCarePlans)
    var collectionId by CollectionCarePlans.collectionId
    var version by CollectionCarePlans.version
    var catalog by CollectionCarePlans.catalog
    var content by CollectionCarePlans.content
    var conservation by CollectionCarePlans.conservation
    var keeper by CollectionCarePlans.keeper
}
object CollectionCareChecks : OrgAuditUIDTable("col_care_check"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id",64).index(shortIndexName("collection_id"))
    val catalog = json("catalog",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCatalogContentVo::class.java)!!})
    val content = json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareCheckVo::class.java)!!})
    val clearance = json("clearance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareClearanceSnapshot::class.java)!!}).nullable()
}
class CollectionCareCheckDao(id: EntityID<String>) : OrgAuditUIDEntity(id,CollectionCareChecks) {
    companion object : OrgAuditUIDEntityClass<CollectionCareCheckDao>(CollectionCareChecks)
    var collectionId by CollectionCareChecks.collectionId
    var catalog by CollectionCareChecks.catalog
    var content by CollectionCareChecks.content
    var clearance by CollectionCareChecks.clearance
}
object CollectionCareWorks : OrgAuditUIDTable("col_care_work"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id",64).index(shortIndexName("collection_id"))
    val planId = varchar("plan_id",64).index(shortIndexName("plan_id"))
    val started = json("started",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareStartVo::class.java)!!})
    val ended = json("ended",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareEndVo::class.java)!!}).nullable()
    val clearance = json("clearance",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionCareClearanceSnapshot::class.java)!!}).nullable()
}
class CollectionCareWorkDao(id: EntityID<String>) : OrgAuditUIDEntity(id,CollectionCareWorks) {
    companion object : OrgAuditUIDEntityClass<CollectionCareWorkDao>(CollectionCareWorks)
    var collectionId by CollectionCareWorks.collectionId
    var planId by CollectionCareWorks.planId
    var started by CollectionCareWorks.started
    var ended by CollectionCareWorks.ended
    var clearance by CollectionCareWorks.clearance
}
