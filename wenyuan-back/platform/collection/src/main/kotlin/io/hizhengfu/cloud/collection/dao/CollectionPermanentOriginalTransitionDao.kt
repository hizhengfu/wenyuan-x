package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 旧本机原件身份到稳定身份的逐文件承接凭据；原永久档案及其冻结键不改写。 */
object CollectionPermanentOriginalTransitions : OrgAuditUIDTable("col_permanent_original_transition") {
    val fileId = varchar("file_id", 64).uniqueIndex(shortIndexName("file_id", unique = true))
    val frozenNativeKey = varchar("frozen_native_key", 64)
    val stableKey = varchar("stable_key", 64)
    val contentSha256 = varchar("content_sha256", 64)
    val contentSize = long("content_size", checkConstraintName = shortCheckConstraintName("content_size", typeCode = "l"))
    val formerDevice = varchar("former_device", 16)
    val currentDevice = varchar("current_device", 16)
    val inode = varchar("inode", 32)
    val birthMillis = long("birth_millis", checkConstraintName = shortCheckConstraintName("birth_millis", typeCode = "l"))
    val reason = text("reason")
    val evidenceDigest = varchar("evidence_digest", 64)
}

class CollectionPermanentOriginalTransitionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPermanentOriginalTransitions) {
    companion object : OrgAuditUIDEntityClass<CollectionPermanentOriginalTransitionDao>(CollectionPermanentOriginalTransitions)
    var fileId by CollectionPermanentOriginalTransitions.fileId
    var frozenNativeKey by CollectionPermanentOriginalTransitions.frozenNativeKey
    var stableKey by CollectionPermanentOriginalTransitions.stableKey
    var contentSha256 by CollectionPermanentOriginalTransitions.contentSha256
    var contentSize by CollectionPermanentOriginalTransitions.contentSize
    var formerDevice by CollectionPermanentOriginalTransitions.formerDevice
    var currentDevice by CollectionPermanentOriginalTransitions.currentDevice
    var inode by CollectionPermanentOriginalTransitions.inode
    var birthMillis by CollectionPermanentOriginalTransitions.birthMillis
    var reason by CollectionPermanentOriginalTransitions.reason
    var evidenceDigest by CollectionPermanentOriginalTransitions.evidenceDigest
}
