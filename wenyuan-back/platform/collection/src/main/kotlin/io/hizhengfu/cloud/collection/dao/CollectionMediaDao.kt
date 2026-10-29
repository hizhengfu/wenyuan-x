package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumCollectionMediaType
import io.hizhengfu.cloud.collection.domain.CollectionMediaCaptureVo
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import org.jetbrains.exposed.v1.json.json
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 藏品影像：正面/背面/细节/款识/拓片/三维/视频等，fileId 复用 sys_file。 */
object CollectionMedias : OrgAuditUIDTable("col_media") {
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val folderId = varchar("folder_id", 64).nullable().index(shortIndexName("folder_id")).comment("影像目录")
    val fileId = varchar("file_id", 64).index(shortIndexName("file_id")).comment("文件")
    val mediaType = enumeration<EnumCollectionMediaType>("media_type").default(EnumCollectionMediaType.OTHER).comment("影像类型")
    val capture = json<CollectionMediaCaptureVo>("capture", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionMediaCaptureVo::class.java)!! }).nullable().comment("规范影像采集元数据")
    val title = varchar("title", 200).nullable().comment("影像标题")
    val isCover = bool("is_cover").default(false).comment("是否封面")
    val bizId = varchar("biz_id", 64).nullable().comment("关联业务单据")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)
    val remark = varchar("remark", 500).nullable()

    init {
        index(shortIndexName("org_id_collection_id"), false, orgId, collectionId)
        index(shortIndexName("org_id_collection_id_media_type"), false, orgId, collectionId, mediaType)
        index(shortIndexName("org_id_biz_id"), false, orgId, bizId)
    }
}

class CollectionMediaDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionMedias) {
    companion object : OrgAuditUIDEntityClass<CollectionMediaDao>(CollectionMedias)

    var collectionId by CollectionMedias.collectionId
    var folderId by CollectionMedias.folderId
    var fileId by CollectionMedias.fileId
    var mediaType by CollectionMedias.mediaType
    var capture by CollectionMedias.capture
    var title by CollectionMedias.title
    var isCover by CollectionMedias.isCover
    var bizId by CollectionMedias.bizId
    var sort by CollectionMedias.sort
    var remark by CollectionMedias.remark

    fun toModel() = CollectionMediaVo(
        id = id.value,
        orgId = orgId,
        collectionId = collectionId,
        folderId = folderId,
        fileId = fileId,
        mediaType = mediaType,
        capture = capture,
        title = title,
        isCover = isCover,
        bizId = bizId,
        sort = sort,
        remark = remark,
        createTime = createTime,
        createBy = createBy,
    )
}

data class CollectionMediaVo(
    val id: String? = null,
    val orgId: String? = null,
    val collectionId: String? = null,
    val folderId: String? = null,
    @field:NotBlank val fileId: String? = null,
    val mediaType: EnumCollectionMediaType? = null,
    val title: String? = null,
    val isCover: Boolean? = null,
    val bizId: String? = null,
    val sort: Int? = null,
    val remark: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
    val capture: CollectionMediaCaptureVo? = null,
)
