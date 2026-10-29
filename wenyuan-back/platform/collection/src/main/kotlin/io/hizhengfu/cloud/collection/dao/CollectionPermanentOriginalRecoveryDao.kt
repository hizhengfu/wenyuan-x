package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 跨文件系统恢复后的逐文件身份承接；不改写原档案的冻结键。 */
object CollectionPermanentOriginalRecoveries : OrgAuditUIDTable("col_permanent_original_recovery") {
    val fileId = varchar("file_id", 64).index(shortIndexName("file_id"))
    val orgIds = text("org_ids")
    val frozenNativeKey = varchar("frozen_native_key", 64)
    val restoredNativeKey = varchar("restored_native_key", 64)
    val restoredStableKey = varchar("restored_stable_key", 64)
    val contentSha256 = varchar("content_sha256", 64)
    val contentSize = long("content_size", checkConstraintName = shortCheckConstraintName("content_size", typeCode = "l"))
    val sourceInventorySha256 = varchar("source_inventory_sha256", 64)
    val databaseBackupSha256 = varchar("database_backup_sha256", 64)
    val fileBackupSha256 = varchar("file_backup_sha256", 64)
    val restoreReportSha256 = varchar("restore_report_sha256", 64)
    val reason = text("reason")
    val evidenceDigest = varchar("evidence_digest", 64)
}

class CollectionPermanentOriginalRecoveryDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPermanentOriginalRecoveries) {
    companion object : OrgAuditUIDEntityClass<CollectionPermanentOriginalRecoveryDao>(CollectionPermanentOriginalRecoveries)
    var fileId by CollectionPermanentOriginalRecoveries.fileId
    var orgIds by CollectionPermanentOriginalRecoveries.orgIds
    var frozenNativeKey by CollectionPermanentOriginalRecoveries.frozenNativeKey
    var restoredNativeKey by CollectionPermanentOriginalRecoveries.restoredNativeKey
    var restoredStableKey by CollectionPermanentOriginalRecoveries.restoredStableKey
    var contentSha256 by CollectionPermanentOriginalRecoveries.contentSha256
    var contentSize by CollectionPermanentOriginalRecoveries.contentSize
    var sourceInventorySha256 by CollectionPermanentOriginalRecoveries.sourceInventorySha256
    var databaseBackupSha256 by CollectionPermanentOriginalRecoveries.databaseBackupSha256
    var fileBackupSha256 by CollectionPermanentOriginalRecoveries.fileBackupSha256
    var restoreReportSha256 by CollectionPermanentOriginalRecoveries.restoreReportSha256
    var reason by CollectionPermanentOriginalRecoveries.reason
    var evidenceDigest by CollectionPermanentOriginalRecoveries.evidenceDigest
}
