package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID

object IncidentRelocations : OrgAuditUIDTable("col_incident_relocation"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val collectionId = varchar("collection_id",64).index(shortIndexName("collection"))
    val mode = enumerationByName<EnumIncidentRelocationMode>("mode",24)
    val state = enumerationByName<EnumIncidentRelocationState>("state",20).default(EnumIncidentRelocationState.PLAN)
    val sequence = integer("sequence", checkConstraintName = shortCheckConstraintName("sequence")).default(0)
}
class IncidentRelocationDao(id: EntityID<String>) : OrgAuditUIDEntity(id, IncidentRelocations) {
    companion object : OrgAuditUIDEntityClass<IncidentRelocationDao>(IncidentRelocations)
    var collectionId by IncidentRelocations.collectionId
    var mode by IncidentRelocations.mode
    var state by IncidentRelocations.state
    var sequence by IncidentRelocations.sequence
}
object IncidentRelocationRecords : OrgAuditUIDTable("col_incident_relocation_record"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val relocationId = varchar("relocation_id",64).index(shortIndexName("relocation"))
    val sequence = integer("sequence", checkConstraintName = shortCheckConstraintName("sequence"))
    val kind = enumerationByName<EnumIncidentRelocationKind>("kind",24)
    val content = text("content")
    init { uniqueIndex(shortIndexName("sequence",unique=true),relocationId,sequence) }
}
class IncidentRelocationRecordDao(id: EntityID<String>) : OrgAuditUIDEntity(id, IncidentRelocationRecords) {
    companion object : OrgAuditUIDEntityClass<IncidentRelocationRecordDao>(IncidentRelocationRecords)
    var relocationId by IncidentRelocationRecords.relocationId
    var sequence by IncidentRelocationRecords.sequence
    var kind by IncidentRelocationRecords.kind
    var content by IncidentRelocationRecords.content
}
