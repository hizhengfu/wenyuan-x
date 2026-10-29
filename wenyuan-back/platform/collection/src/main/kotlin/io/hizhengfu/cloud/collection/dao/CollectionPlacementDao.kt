package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 藏品在业务期间的实际位置。
 *
 * 当前由展览业务写入；一件藏品在同一展览中只有一个展柜，但同一展柜可对应任意多件藏品。
 * Collections.warehouseId 继续表示归属/回库位置，不会在展陈期间被覆盖。
 */
object CollectionPlacements : OrgAuditUIDTable("col_collection_placement") {
    val sourceBizType = varchar("source_biz_type", 100).index(shortIndexName("source_biz_type"))
        .comment("来源业务类型")
    val sourceBizId = varchar("source_biz_id", 64).index(shortIndexName("source_biz_id"))
        .comment("来源业务 ID")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品 ID")
    val locationId = varchar("location_id", 64).index(shortIndexName("location_id"))
        .comment("实际位置 ID(col_warehouse)")
    val validFrom = datetime("valid_from").nullable().comment("实际进入位置时间；空表示尚未布展到位")
    val validTo = datetime("valid_to").nullable().comment("位置失效时间")
    val approved = bool("approved").default(false).comment("来源业务是否已审批生效")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)
        .comment("业务单据内排序")

    init {
        uniqueIndex(
            shortIndexName("org_id_source_biz_type_source_biz_id_collection_id", unique = true),
            orgId,
            sourceBizType,
            sourceBizId,
            collectionId,
        )
        index(shortIndexName("org_id_location_id_approved"), false, orgId, locationId, approved)
        index(shortIndexName("org_id_source_biz_type_source_biz_id"), false, orgId, sourceBizType, sourceBizId)
    }
}

class CollectionPlacementDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionPlacements) {
    companion object : OrgAuditUIDEntityClass<CollectionPlacementDao>(CollectionPlacements)

    var sourceBizType by CollectionPlacements.sourceBizType
    var sourceBizId by CollectionPlacements.sourceBizId
    var collectionId by CollectionPlacements.collectionId
    var locationId by CollectionPlacements.locationId
    var validFrom by CollectionPlacements.validFrom
    var validTo by CollectionPlacements.validTo
    var approved by CollectionPlacements.approved
    var sort by CollectionPlacements.sort

    fun toExhibitionModel() = ExhibitionPlacementVo(
        collectionId = collectionId,
        locationId = locationId,
        validFrom = validFrom,
        validTo = validTo,
    )
}

data class ExhibitionPlacementVo(
    val collectionId: String? = null,
    val locationId: String? = null,
    val locationName: String? = null,
    val validFrom: LocalDateTime? = null,
    val validTo: LocalDateTime? = null,
)
