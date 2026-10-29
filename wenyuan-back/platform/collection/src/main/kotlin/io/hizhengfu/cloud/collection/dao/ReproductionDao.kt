package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.EnumRepType
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime

/** 复制 / 拓印 / 影像采集。 */
object Reproductions : FlowUIDTable("col_reproduction"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id"))
    val repType = enumeration<EnumRepType>("rep_type").nullable().comment("复制/拓印/3D/影像")
    val images = text("images").nullable().comment("产物影像 fileId")
    val purpose = varchar("purpose", 500).nullable().comment("用途")
    val repDate = date("rep_date").nullable()
}

class ReproductionDao(id: EntityID<String>) : FlowUIDEntity(id, Reproductions) {
    companion object : FlowUIDEntityClass<ReproductionDao>(Reproductions)

    var orgId by Reproductions.orgId
    var collectionId by Reproductions.collectionId
    var repType by Reproductions.repType
    var images by Reproductions.images
    var purpose by Reproductions.purpose
    var repDate by Reproductions.repDate

    fun toModel() = ReproductionVo(
        id = id.value, orgId = orgId, collectionId = collectionId, repType = repType,
        images = images, purpose = purpose, repDate = repDate, flowStatus = flowStatus,
        flowInstanceId = flowInstanceId, createTime = createTime,
    )
}

data class ReproductionVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val collectionId: String? = null,
    val collectionTotalRegNo: String? = null,
    val collectionName: String? = null,
    @field:NotNull val repType: EnumRepType? = null,
    val images: String? = null,
    @field:NotBlank val purpose: String? = null,
    @field:NotNull val repDate: LocalDate? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
)
