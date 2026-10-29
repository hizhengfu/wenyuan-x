package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionTrackingEventVo
import io.hizhengfu.cloud.collection.domain.CollectionWordImportRecordVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionTrackingEventType
import io.hizhengfu.cloud.collection.domain.EnumCollectionWordDocumentType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime

/** 一次经过结构校验并正式提交的馆藏 Word 原件。 */
object CollectionWordImports : OrgAuditUIDTable("col_word_import") {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id"))
    val documentType = enumerationByName<EnumCollectionWordDocumentType>("document_type", 32)
    val sourceFileId = varchar("source_file_id", 64).nullable()
    val sourceFileName = varchar("source_file_name", 255)
    val sourceHash = varchar("source_hash", 64)
    val fingerprint = varchar("fingerprint", 64)
    val startYear = integer("start_year", checkConstraintName = shortCheckConstraintName("start_year")).nullable()
    val endYear = integer("end_year", checkConstraintName = shortCheckConstraintName("end_year")).nullable()

    init {
        uniqueIndex(shortIndexName("org_id_source_hash", unique = true), orgId, sourceHash)
        index(shortIndexName("org_id_collection_id_document_type"), false, orgId, collectionId, documentType)
    }
}

class CollectionWordImportDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionWordImports) {
    companion object : OrgAuditUIDEntityClass<CollectionWordImportDao>(CollectionWordImports)

    var collectionId by CollectionWordImports.collectionId
    var documentType by CollectionWordImports.documentType
    var sourceFileId by CollectionWordImports.sourceFileId
    var sourceFileName by CollectionWordImports.sourceFileName
    var sourceHash by CollectionWordImports.sourceHash
    var fingerprint by CollectionWordImports.fingerprint
    var startYear by CollectionWordImports.startYear
    var endYear by CollectionWordImports.endYear

    fun toModel() = CollectionWordImportRecordVo(
        id = id.value,
        documentType = documentType,
        sourceFileId = sourceFileId,
        sourceFileName = sourceFileName,
        sourceHash = sourceHash,
        fingerprint = fingerprint,
        startYear = startYear,
        endYear = endYear,
        createTime = createTime,
    )
}

/** 动态跟踪表与登记表扩展区的只读管理记录；不替代需审批的修复、鉴定、注销业务单。 */
object CollectionTrackingEvents : OrgAuditUIDTable("col_tracking_event") {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id"))
    val importId = varchar("import_id", 64).index(shortIndexName("import_id"))
    val eventType = enumerationByName<EnumCollectionTrackingEventType>("event_type", 32)
    val title = varchar("title", 100)
    val content = text("content")
    val occurredAt = datetime("occurred_at").nullable()
    val endedAt = datetime("ended_at").nullable()
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)

    init {
        index(shortIndexName("org_id_collection_id_occurred_at"), false, orgId, collectionId, occurredAt)
    }
}

class CollectionTrackingEventDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionTrackingEvents) {
    companion object : OrgAuditUIDEntityClass<CollectionTrackingEventDao>(CollectionTrackingEvents)

    var collectionId by CollectionTrackingEvents.collectionId
    var importId by CollectionTrackingEvents.importId
    var eventType by CollectionTrackingEvents.eventType
    var title by CollectionTrackingEvents.title
    var content by CollectionTrackingEvents.content
    var occurredAt by CollectionTrackingEvents.occurredAt
    var endedAt by CollectionTrackingEvents.endedAt
    var sort by CollectionTrackingEvents.sort

    fun toModel() = CollectionTrackingEventVo(
        id = id.value,
        importId = importId,
        eventType = eventType,
        title = title,
        content = content,
        occurredAt = occurredAt,
        endedAt = endedAt,
        sort = sort,
        createTime = createTime,
    )
}
