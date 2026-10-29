package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.domain.EnumIncidentRecordKind
import org.jetbrains.exposed.v1.core.dao.id.EntityID

object CollectionIncidents : OrgAuditUIDTable("col_incident"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val title = varchar("title", 500)
    val sequence = integer("sequence", checkConstraintName = shortCheckConstraintName("sequence")).default(0)
    val closed = bool("closed").default(false)
}
class CollectionIncidentDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionIncidents) {
    companion object : OrgAuditUIDEntityClass<CollectionIncidentDao>(CollectionIncidents)
    var title by CollectionIncidents.title
    var sequence by CollectionIncidents.sequence
    var closed by CollectionIncidents.closed
}
object CollectionIncidentRecords : OrgAuditUIDTable("col_incident_record"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val incidentId = varchar("incident_id", 64).index(shortIndexName("incident"))
    val sequence = integer("sequence", checkConstraintName = shortCheckConstraintName("sequence"))
    val kind = enumerationByName<EnumIncidentRecordKind>("kind", 24)
    val content = text("content")
    init { uniqueIndex(shortIndexName("sequence", unique = true), incidentId, sequence) }
}
class CollectionIncidentRecordDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionIncidentRecords) {
    companion object : OrgAuditUIDEntityClass<CollectionIncidentRecordDao>(CollectionIncidentRecords)
    var incidentId by CollectionIncidentRecords.incidentId
    var sequence by CollectionIncidentRecords.sequence
    var kind by CollectionIncidentRecords.kind
    var content by CollectionIncidentRecords.content
}
object CollectionIncidentItems : OrgAuditUIDTable("col_incident_item"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val incidentId = varchar("incident_id", 64).index(shortIndexName("incident"))
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection"))
    val restricted = bool("restricted").default(true)
    init { uniqueIndex(shortIndexName("item", unique = true), incidentId, collectionId) }
}
class CollectionIncidentItemDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionIncidentItems) {
    companion object : OrgAuditUIDEntityClass<CollectionIncidentItemDao>(CollectionIncidentItems)
    var incidentId by CollectionIncidentItems.incidentId
    var collectionId by CollectionIncidentItems.collectionId
    var restricted by CollectionIncidentItems.restricted
}
