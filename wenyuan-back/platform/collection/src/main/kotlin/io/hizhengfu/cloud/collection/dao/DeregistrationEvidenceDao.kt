package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object DeregistrationExpertOpinions : OrgAuditUIDTable("col_dereg_expert_opinion") {
    val deregistrationId = varchar("deregistration_id", 64).index(shortIndexName("deregistration_id"))
    val expertKey = varchar("expert_key", 100)
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, DeregistrationExpertOpinionVo::class.java)!! })
    init { uniqueIndex(shortIndexName("deregistration_id_expert_key", unique = true), deregistrationId, expertKey) }
}
class DeregistrationExpertOpinionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, DeregistrationExpertOpinions) {
    companion object : OrgAuditUIDEntityClass<DeregistrationExpertOpinionDao>(DeregistrationExpertOpinions)
    var deregistrationId by DeregistrationExpertOpinions.deregistrationId
    var expertKey by DeregistrationExpertOpinions.expertKey
    var content by DeregistrationExpertOpinions.content
}

object DeregistrationNotices : OrgAuditUIDTable("col_dereg_notice") {
    val deregistrationId = varchar("deregistration_id", 64).index(shortIndexName("deregistration_id"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, DeregistrationNoticeVo::class.java)!! })
}
class DeregistrationNoticeDao(id: EntityID<String>) : OrgAuditUIDEntity(id, DeregistrationNotices) {
    companion object : OrgAuditUIDEntityClass<DeregistrationNoticeDao>(DeregistrationNotices)
    var deregistrationId by DeregistrationNotices.deregistrationId
    var content by DeregistrationNotices.content
}

object CollectionAdministrativeRecords : OrgAuditUIDTable("col_administrative_record"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val bizType = varchar("biz_type", 64)
    val bizId = varchar("biz_id", 64)
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionAdministrativeEvidenceVo::class.java)!! })
    init { index(shortIndexName("biz_type_biz_id"), false, bizType, bizId) }
}
class CollectionAdministrativeRecordDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionAdministrativeRecords) {
    companion object : OrgAuditUIDEntityClass<CollectionAdministrativeRecordDao>(CollectionAdministrativeRecords)
    var bizType by CollectionAdministrativeRecords.bizType
    var bizId by CollectionAdministrativeRecords.bizId
    var content by CollectionAdministrativeRecords.content
}
