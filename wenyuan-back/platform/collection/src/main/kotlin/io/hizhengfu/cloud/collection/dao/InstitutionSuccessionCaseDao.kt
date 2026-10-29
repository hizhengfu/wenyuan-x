package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json

object InstitutionSuccessionCases : OrgAuditUIDTable("col_institution_succession") {
    val sourceKey = varchar("source_key",64).uniqueIndex(shortIndexName("source_key",unique=true))
    val content = json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,InstitutionSuccessionCaseVo::class.java)!!})
}
class InstitutionSuccessionCaseDao(id:EntityID<String>):OrgAuditUIDEntity(id,InstitutionSuccessionCases) {
    companion object:OrgAuditUIDEntityClass<InstitutionSuccessionCaseDao>(InstitutionSuccessionCases)
    var sourceKey by InstitutionSuccessionCases.sourceKey
    var content by InstitutionSuccessionCases.content
}
object InstitutionSuccessionItems : OrgAuditUIDTable("col_institution_succession_item") {
    val caseId = varchar("case_id",64).index(shortIndexName("case_id"))
    val itemKey = varchar("item_key",100)
    val sourceKind = enumeration<EnumSuccessionSource>("source_kind")
    val sourceId = varchar("source_id",64)
    val baseline = text("baseline")
    init { uniqueIndex(shortIndexName("case_item",unique=true),caseId,itemKey) }
}
class InstitutionSuccessionItemDao(id:EntityID<String>):OrgAuditUIDEntity(id,InstitutionSuccessionItems) {
    companion object:OrgAuditUIDEntityClass<InstitutionSuccessionItemDao>(InstitutionSuccessionItems)
    var caseId by InstitutionSuccessionItems.caseId
    var itemKey by InstitutionSuccessionItems.itemKey
    var sourceKind by InstitutionSuccessionItems.sourceKind
    var sourceId by InstitutionSuccessionItems.sourceId
    var baseline by InstitutionSuccessionItems.baseline
}
object InstitutionSuccessionRecords : OrgAuditUIDTable("col_institution_succession_record") {
    val caseId = varchar("case_id",64).index(shortIndexName("case_id"))
    val kind = enumeration<EnumSuccessionCaseRecord>("kind")
    val actorUserId = varchar("actor_user_id",64)
    val recordedAt = datetime("recorded_at")
    val content = text("content")
}
class InstitutionSuccessionRecordDao(id:EntityID<String>):OrgAuditUIDEntity(id,InstitutionSuccessionRecords) {
    companion object:OrgAuditUIDEntityClass<InstitutionSuccessionRecordDao>(InstitutionSuccessionRecords)
    var caseId by InstitutionSuccessionRecords.caseId
    var kind by InstitutionSuccessionRecords.kind
    var actorUserId by InstitutionSuccessionRecords.actorUserId
    var recordedAt by InstitutionSuccessionRecords.recordedAt
    var content by InstitutionSuccessionRecords.content
}
