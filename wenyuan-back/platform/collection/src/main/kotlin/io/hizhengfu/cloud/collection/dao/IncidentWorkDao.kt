package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID

object IncidentWorks : OrgAuditUIDTable("col_incident_work"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection"))
    val state = enumerationByName<EnumIncidentWorkState>("state", 20).default(EnumIncidentWorkState.PLAN)
    val sequence = integer("sequence", checkConstraintName = shortCheckConstraintName("sequence")).default(0)
}
class IncidentWorkDao(id: EntityID<String>) : OrgAuditUIDEntity(id, IncidentWorks) {
    companion object : OrgAuditUIDEntityClass<IncidentWorkDao>(IncidentWorks)
    var collectionId by IncidentWorks.collectionId
    var state by IncidentWorks.state
    var sequence by IncidentWorks.sequence
}
object IncidentWorkRecords : OrgAuditUIDTable("col_incident_work_record"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val workId = varchar("work_id", 64).index(shortIndexName("work"))
    val sequence = integer("sequence", checkConstraintName = shortCheckConstraintName("sequence"))
    val kind = enumerationByName<EnumIncidentWorkRecordKind>("kind", 24)
    val content = text("content")
    init { uniqueIndex(shortIndexName("sequence", unique = true), workId, sequence) }
}
class IncidentWorkRecordDao(id: EntityID<String>) : OrgAuditUIDEntity(id, IncidentWorkRecords) {
    companion object : OrgAuditUIDEntityClass<IncidentWorkRecordDao>(IncidentWorkRecords)
    var workId by IncidentWorkRecords.workId
    var sequence by IncidentWorkRecords.sequence
    var kind by IncidentWorkRecords.kind
    var content by IncidentWorkRecords.content
}
