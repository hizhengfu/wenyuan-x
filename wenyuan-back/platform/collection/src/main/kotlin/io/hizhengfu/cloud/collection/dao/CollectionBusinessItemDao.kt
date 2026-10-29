package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 业务单据与藏品的明细关系表，替代逗号分隔 ID 作为查询和约束依据。 */
object CollectionBusinessItems : OrgAuditUIDTable("col_biz_collection_item") {
    val bizType = varchar("biz_type", 100).index(shortIndexName("biz_type")).comment("业务类型")
    val bizId = varchar("biz_id", 64).index(shortIndexName("biz_id")).comment("业务单据 ID")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品 ID")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)
        .comment("单据内排序")

    init {
        uniqueIndex(
            shortIndexName("org_id_biz_type_biz_id_collection_id", unique = true),
            orgId,
            bizType,
            bizId,
            collectionId,
        )
        index(shortIndexName("org_id_biz_type_biz_id"), false, orgId, bizType, bizId)
        index(shortIndexName("org_id_biz_type_collection_id"), false, orgId, bizType, collectionId)
    }
}

class CollectionBusinessItemDao(id: EntityID<String>) :
    OrgAuditUIDEntity(id, CollectionBusinessItems) {
    companion object : OrgAuditUIDEntityClass<CollectionBusinessItemDao>(CollectionBusinessItems)

    var bizType by CollectionBusinessItems.bizType
    var bizId by CollectionBusinessItems.bizId
    var collectionId by CollectionBusinessItems.collectionId
    var sort by CollectionBusinessItems.sort
}
