package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 一物一档影像目录；目录只在单件藏品内生效。 */
object CollectionMediaFolders : OrgAuditUIDTable("col_media_folder") {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val parentId = varchar("parent_id", 64).nullable().index(shortIndexName("parent_id")).comment("上级目录")
    val name = varchar("name", 100).comment("目录名称")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)

    init {
        index(shortIndexName("org_id_collection_id_parent_id"), false, orgId, collectionId, parentId)
    }
}

class CollectionMediaFolderDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionMediaFolders) {
    companion object : OrgAuditUIDEntityClass<CollectionMediaFolderDao>(CollectionMediaFolders)

    var collectionId by CollectionMediaFolders.collectionId
    var parentId by CollectionMediaFolders.parentId
    var name by CollectionMediaFolders.name
    var sort by CollectionMediaFolders.sort

    fun toModel() = CollectionMediaFolderVo(
        id = id.value,
        orgId = orgId,
        collectionId = collectionId,
        parentId = parentId,
        name = name,
        sort = sort,
    )
}

data class CollectionMediaFolderVo(
    val id: String? = null,
    val orgId: String? = null,
    val collectionId: String? = null,
    val parentId: String? = null,
    @field:NotBlank val name: String? = null,
    val sort: Int? = null,
)
