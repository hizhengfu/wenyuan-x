package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionGradings : OrgAuditUIDTable("col_grading") {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id"))
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val totalRegNo = varchar("total_reg_no", 64)
    val application = json("application", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, GradingApplicationVo::class.java)!! })
    val result = json("grading_result", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, GradingResultVo::class.java)!! }).nullable()
    val nationalFiling = json("national_filing", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, GradingNationalFilingVo::class.java)!! }).nullable()
    init { uniqueIndex(shortIndexName("org_collection_version", unique = true), orgId, collectionId, version) }
}

class CollectionGradingDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionGradings) {
    companion object : OrgAuditUIDEntityClass<CollectionGradingDao>(CollectionGradings)
    var collectionId by CollectionGradings.collectionId
    var version by CollectionGradings.version
    var totalRegNo by CollectionGradings.totalRegNo
    var application by CollectionGradings.application
    var result by CollectionGradings.result
    var nationalFiling by CollectionGradings.nationalFiling
}
