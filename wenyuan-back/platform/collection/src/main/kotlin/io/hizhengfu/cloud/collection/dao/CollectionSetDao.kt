package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumSetRelation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 套件/附件/分件关系，支撑“件/套”统计口径。 */
object CollectionSets : OrgAuditUIDTable("col_set") {
    val parentCollectionId = varchar("parent_collection_id", 64).index(shortIndexName("parent_collection_id"))
        .comment("主件藏品")
    val childCollectionId = varchar("child_collection_id", 64).nullable().comment("附件/分件藏品")
    val partNo = varchar("part_no", 32).nullable().comment("分件号")
    val partName = varchar("part_name", 200).nullable().comment("分件名称")
    val relation = enumeration<EnumSetRelation>("relation").comment("主件/附件/分件")
    val quantity = integer("quantity", checkConstraintName = shortCheckConstraintName("quantity"))
        .default(1).comment("分件数量")
    val unit = varchar("unit", 16).default("件").comment("分件计量单位")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)

    init {
        index(shortIndexName("org_id_parent_collection_id"), false, orgId, parentCollectionId)
    }
}

class CollectionSetDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionSets) {
    companion object : OrgAuditUIDEntityClass<CollectionSetDao>(CollectionSets)

    var parentCollectionId by CollectionSets.parentCollectionId
    var childCollectionId by CollectionSets.childCollectionId
    var partNo by CollectionSets.partNo
    var partName by CollectionSets.partName
    var relation by CollectionSets.relation
    var quantity by CollectionSets.quantity
    var unit by CollectionSets.unit
    var sort by CollectionSets.sort

    fun toModel() = CollectionSetVo(
        id = id.value,
        orgId = orgId,
        parentCollectionId = parentCollectionId,
        childCollectionId = childCollectionId,
        partNo = partNo,
        partName = partName,
        relation = relation,
        quantity = quantity,
        unit = unit,
        sort = sort,
        createTime = createTime,
    )
}

data class CollectionSetVo(
    val id: String? = null,
    val orgId: String? = null,
    val parentCollectionId: String? = null,
    val childCollectionId: String? = null,
    val partNo: String? = null,
    val partName: String? = null,
    @field:NotNull val relation: EnumSetRelation? = null,
    @field:Positive val quantity: Int? = 1,
    @field:NotBlank val unit: String? = "件",
    val sort: Int? = 0,
    val createTime: LocalDateTime? = null,
)

data class CollectionSetEntryPartVo(
    @field:NotBlank val partNo: String? = null,
    @field:NotBlank val partName: String? = null,
    val relation: EnumSetRelation = EnumSetRelation.PART,
    @field:Positive val quantity: Int = 1,
    @field:NotBlank val unit: String = "件",
    val sort: Int? = null,
    /** 分件作为独立藏品登账；未填字段会继承主件的分类、来源、级别和入藏信息。 */
    val collection: CollectionVo? = null,
)

data class CollectionSetEntryVo(
    @field:NotNull val main: CollectionVo? = null,
    val parts: List<CollectionSetEntryPartVo> = emptyList(),
)

data class CollectionSetEntryResultVo(
    val mainCollectionId: String,
    val childCollectionIds: List<String> = emptyList(),
    val relationIds: List<String> = emptyList(),
)
