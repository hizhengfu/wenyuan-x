package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.javatime.datetime

object DesignatedReceptions:OrgAuditUIDTable("col_designated_reception") {
    val sourceKey=varchar("source_key",64).uniqueIndex(shortIndexName("source_key",unique=true))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DesignatedNoticeVo::class.java)!!})
    val orgSnapshot=text("org_snapshot")
    val acquisitionId=varchar("acquisition_id",64).nullable().index(shortIndexName("acquisition_id"))
}
class DesignatedReceptionDao(id:EntityID<String>):OrgAuditUIDEntity(id,DesignatedReceptions) {
    companion object:OrgAuditUIDEntityClass<DesignatedReceptionDao>(DesignatedReceptions)
    var sourceKey by DesignatedReceptions.sourceKey
    var content by DesignatedReceptions.content
    var orgSnapshot by DesignatedReceptions.orgSnapshot
    var acquisitionId by DesignatedReceptions.acquisitionId
}
object DesignatedReceptionRecords:OrgAuditUIDTable("col_designated_record") {
    val caseId=varchar("case_id",64).index(shortIndexName("case_id"))
    val kind=enumeration<EnumDesignatedRecord>("kind")
    val actorUserId=varchar("actor_user_id",64)
    val recordedAt=datetime("recorded_at")
    val content=text("content")
    init {index(shortIndexName("case_kind"),false,caseId,kind)}
}
class DesignatedReceptionRecordDao(id:EntityID<String>):OrgAuditUIDEntity(id,DesignatedReceptionRecords) {
    companion object:OrgAuditUIDEntityClass<DesignatedReceptionRecordDao>(DesignatedReceptionRecords)
    var caseId by DesignatedReceptionRecords.caseId
    var kind by DesignatedReceptionRecords.kind
    var actorUserId by DesignatedReceptionRecords.actorUserId
    var recordedAt by DesignatedReceptionRecords.recordedAt
    var content by DesignatedReceptionRecords.content
}
