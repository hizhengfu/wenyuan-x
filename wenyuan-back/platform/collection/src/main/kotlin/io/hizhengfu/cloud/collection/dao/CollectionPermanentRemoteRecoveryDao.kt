package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 新桶或端点上的逐文件版本承接记录；原档案与旧版本信息保持冻结。 */
object CollectionPermanentRemoteRecoveries : OrgAuditUIDTable("col_permanent_remote_recovery") {
    val fileId = varchar("file_id", 64).index(shortIndexName("file_id"))
    val orgIds = text("org_ids")
    val frozenPath = varchar("frozen_path", 255)
    val frozenRevisions = text("frozen_revisions")
    val restoredStorageIdentity = varchar("restored_storage_identity", 64)
    val restoredVersionId = text("restored_version_id").nullable()
    val restoredETag = text("restored_etag").nullable()
    val contentSha256 = varchar("content_sha256", 64)
    val contentSize = long("content_size", checkConstraintName = shortCheckConstraintName("content_size", typeCode = "l"))
    val sourceInventorySha256 = varchar("source_inventory_sha256", 64)
    val databaseBackupSha256 = varchar("database_backup_sha256", 64)
    val objectBackupSha256 = varchar("object_backup_sha256", 64)
    val restoreReportSha256 = varchar("restore_report_sha256", 64)
    val reason = text("reason")
    val evidenceDigest = varchar("evidence_digest", 64)
}

class CollectionPermanentRemoteRecoveryDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPermanentRemoteRecoveries) {
    companion object : OrgAuditUIDEntityClass<CollectionPermanentRemoteRecoveryDao>(CollectionPermanentRemoteRecoveries)
    var fileId by CollectionPermanentRemoteRecoveries.fileId
    var orgIds by CollectionPermanentRemoteRecoveries.orgIds
    var frozenPath by CollectionPermanentRemoteRecoveries.frozenPath
    var frozenRevisions by CollectionPermanentRemoteRecoveries.frozenRevisions
    var restoredStorageIdentity by CollectionPermanentRemoteRecoveries.restoredStorageIdentity
    var restoredVersionId by CollectionPermanentRemoteRecoveries.restoredVersionId
    var restoredETag by CollectionPermanentRemoteRecoveries.restoredETag
    var contentSha256 by CollectionPermanentRemoteRecoveries.contentSha256
    var contentSize by CollectionPermanentRemoteRecoveries.contentSize
    var sourceInventorySha256 by CollectionPermanentRemoteRecoveries.sourceInventorySha256
    var databaseBackupSha256 by CollectionPermanentRemoteRecoveries.databaseBackupSha256
    var objectBackupSha256 by CollectionPermanentRemoteRecoveries.objectBackupSha256
    var restoreReportSha256 by CollectionPermanentRemoteRecoveries.restoreReportSha256
    var reason by CollectionPermanentRemoteRecoveries.reason
    var evidenceDigest by CollectionPermanentRemoteRecoveries.evidenceDigest
}
