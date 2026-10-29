package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object ArchiveJurisdictions : OrgAuditUIDTable("col_archive_jurisdiction") {
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, ArchiveJurisdictionVo::class.java)!! })
    init { uniqueIndex(shortIndexName("org_version", unique = true), orgId, version) }
}
class ArchiveJurisdictionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, ArchiveJurisdictions) {
    companion object : OrgAuditUIDEntityClass<ArchiveJurisdictionDao>(ArchiveJurisdictions)
    var version by ArchiveJurisdictions.version
    var content by ArchiveJurisdictions.content
    fun snapshot() = ArchiveJurisdictionSnapshotVo(id.value, version, content)
}
object CollectionArchivePackets : OrgAuditUIDTable("col_archive_packet") {
    val collectionId = varchar("collection_id", 64)
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionArchivePacketVo::class.java)!! })
    val sourceSnapshot = json("source", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, ArchivePacketSourceVo::class.java)!! })
    val jurisdiction = json("jurisdiction", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, ArchiveJurisdictionSnapshotVo::class.java)!! }).nullable()
    init { uniqueIndex(shortIndexName("org_collection_version", unique = true), orgId, collectionId, version) }
}
class CollectionArchivePacketDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionArchivePackets) {
    companion object : OrgAuditUIDEntityClass<CollectionArchivePacketDao>(CollectionArchivePackets)
    var collectionId by CollectionArchivePackets.collectionId
    var version by CollectionArchivePackets.version
    var content by CollectionArchivePackets.content
    var source by CollectionArchivePackets.sourceSnapshot
    var jurisdiction by CollectionArchivePackets.jurisdiction
}
object CollectionArchiveSubmissions : OrgAuditUIDTable("col_archive_submission") {
    val packetId = varchar("packet_id", 64)
    val target = enumeration<EnumArchiveFilingTarget>("target")
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, ArchivePacketSubmissionVo::class.java)!! })
    val receipt = json("receipt", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, ArchivePacketReceiptVo::class.java)!! }).nullable()
    init { uniqueIndex(shortIndexName("packet_target", unique = true), packetId, target) }
}
class CollectionArchiveSubmissionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionArchiveSubmissions) {
    companion object : OrgAuditUIDEntityClass<CollectionArchiveSubmissionDao>(CollectionArchiveSubmissions)
    var packetId by CollectionArchiveSubmissions.packetId
    var target by CollectionArchiveSubmissions.target
    var content by CollectionArchiveSubmissions.content
    var receipt by CollectionArchiveSubmissions.receipt
}
