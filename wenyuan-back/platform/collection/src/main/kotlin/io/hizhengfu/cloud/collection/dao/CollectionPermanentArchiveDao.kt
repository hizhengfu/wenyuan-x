package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 永久业务档案：每个阶段只追加一次快照，不提供覆盖/删除接口。 */
object CollectionPermanentArchives : OrgAuditUIDTable("col_permanent_archive"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val bizType = varchar("biz_type", 64)
    val bizId = varchar("biz_id", 64)
    val phase = varchar("phase", 64)
    val snapshot = text("snapshot")
    val digest = varchar("digest", 64)
    init {
        uniqueIndex(shortIndexName("biz_type_biz_id_phase", unique = true), bizType, bizId, phase)
        index(shortIndexName("biz_type_phase"), false, bizType, phase)
    }
}
class CollectionPermanentArchiveDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPermanentArchives) {
    companion object : OrgAuditUIDEntityClass<CollectionPermanentArchiveDao>(CollectionPermanentArchives)
    var bizType by CollectionPermanentArchives.bizType
    var bizId by CollectionPermanentArchives.bizId
    var phase by CollectionPermanentArchives.phase
    var snapshot by CollectionPermanentArchives.snapshot
    var digest by CollectionPermanentArchives.digest
}

/** 文件当时的名称、内容摘要与存储位置随档案保留，阻止业务关联解绑和文件删除。 */
object CollectionPermanentArchiveFiles : OrgAuditUIDTable("col_permanent_archive_file"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val archiveId = varchar("archive_id", 64).index(shortIndexName("archive_id"))
    val bizId = varchar("biz_id", 64)
    val fileId = varchar("file_id", 64).index(shortIndexName("file_id"))
    val metadata = text("metadata")
    init { uniqueIndex(shortIndexName("archive_id_file_id", unique = true), archiveId, fileId) }
}
class CollectionPermanentArchiveFileDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPermanentArchiveFiles) {
    companion object : OrgAuditUIDEntityClass<CollectionPermanentArchiveFileDao>(CollectionPermanentArchiveFiles)
    var archiveId by CollectionPermanentArchiveFiles.archiveId
    var bizId by CollectionPermanentArchiveFiles.bizId
    var fileId by CollectionPermanentArchiveFiles.fileId
    var metadata by CollectionPermanentArchiveFiles.metadata
}
