package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionManagementRulesRecords : OrgAuditUIDTable("col_management_rules") {
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val rules = json("rules", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionManagementRulesVo::class.java)!! })
    val submission = json("submission", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionRulesSubmissionVo::class.java)!! }).nullable()
    val filing = json("filing", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionRulesFilingVo::class.java)!! }).nullable()
    init { uniqueIndex(shortIndexName("org_version", unique = true), orgId, version) }
}

class CollectionManagementRulesDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionManagementRulesRecords) {
    companion object : OrgAuditUIDEntityClass<CollectionManagementRulesDao>(CollectionManagementRulesRecords)
    var version by CollectionManagementRulesRecords.version
    var rules by CollectionManagementRulesRecords.rules
    var submission by CollectionManagementRulesRecords.submission
    var filing by CollectionManagementRulesRecords.filing
}
