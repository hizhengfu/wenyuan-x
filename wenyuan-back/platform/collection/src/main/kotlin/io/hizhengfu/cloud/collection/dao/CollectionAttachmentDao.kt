package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumCollectionAttachmentType
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 藏品业务附件：合同、鉴定意见、批文、签收单等，fileId 复用 sys_file。 */
object CollectionAttachments : OrgAuditUIDTable("col_attachment") {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val fileId = varchar("file_id", 64).index(shortIndexName("file_id")).comment("文件")
    val attachmentType = enumeration<EnumCollectionAttachmentType>("attachment_type")
        .default(EnumCollectionAttachmentType.OTHER).comment("附件类型")
    val name = varchar("name", 200).nullable().comment("附件名称")
    val bizId = varchar("biz_id", 64).nullable().comment("关联业务单据")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)
    val remark = varchar("remark", 500).nullable()

    init {
        index(shortIndexName("org_id_collection_id"), false, orgId, collectionId)
        index(shortIndexName("org_id_collection_id_attachment_type"), false, orgId, collectionId, attachmentType)
        index(shortIndexName("org_id_biz_id"), false, orgId, bizId)
    }
}

class CollectionAttachmentDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionAttachments) {
    companion object : OrgAuditUIDEntityClass<CollectionAttachmentDao>(CollectionAttachments)

    var collectionId by CollectionAttachments.collectionId
    var fileId by CollectionAttachments.fileId
    var attachmentType by CollectionAttachments.attachmentType
    var name by CollectionAttachments.name
    var bizId by CollectionAttachments.bizId
    var sort by CollectionAttachments.sort
    var remark by CollectionAttachments.remark

    fun toModel() = CollectionAttachmentVo(
        id = id.value,
        orgId = orgId,
        collectionId = collectionId,
        fileId = fileId,
        attachmentType = attachmentType,
        name = name,
        bizId = bizId,
        sort = sort,
        remark = remark,
        createTime = createTime,
        createBy = createBy,
    )
}

data class CollectionAttachmentVo(
    val id: String? = null,
    val orgId: String? = null,
    val collectionId: String? = null,
    @field:NotBlank val fileId: String? = null,
    val attachmentType: EnumCollectionAttachmentType? = null,
    val name: String? = null,
    val bizId: String? = null,
    val sort: Int? = null,
    val remark: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
)
