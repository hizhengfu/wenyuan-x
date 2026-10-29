package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumProcessState
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime

/** 展览项目：流程审批记录展陈方案；出入库动作仍由提用/借展流程处理。 */
object Exhibitions : FlowUIDTable("col_exhibition") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val title = varchar("title", 200).comment("展览名称")
    val exhibitionNo = varchar("exhibition_no", 100).nullable().comment("展览编号")
    val venueId = varchar("venue_id", 64).nullable().index(shortIndexName("venue_id"))
        .comment("展馆/展厅位置 ID")
    val venue = varchar("venue", 200).nullable().comment("展览地点")
    val organizer = varchar("organizer", 200).nullable().comment("主办/承办单位")
    val collectionIds = text("collection_ids").nullable().comment("参展藏品 id，逗号分隔")
    val coverImage = varchar("cover_image", 128).nullable().comment("展览封面/现场图 fileId")
    val attachmentIds = text("attachment_ids").nullable().comment("展陈方案/批复/清单附件 fileId")
    val startDate = date("start_date").nullable().comment("开始日期")
    val endDate = date("end_date").nullable().comment("结束日期")
    val state = enumeration<EnumProcessState>("state").default(EnumProcessState.DRAFT).comment("展览状态")
    val remark = text("remark").nullable().comment("备注")

    init {
        index(shortIndexName("org_id_state"), false, orgId, state)
        index(shortIndexName("org_id_start_date"), false, orgId, startDate)
    }
}

class ExhibitionDao(id: EntityID<String>) : FlowUIDEntity(id, Exhibitions) {
    companion object : FlowUIDEntityClass<ExhibitionDao>(Exhibitions)

    var orgId by Exhibitions.orgId
    var title by Exhibitions.title
    var exhibitionNo by Exhibitions.exhibitionNo
    var venueId by Exhibitions.venueId
    var venue by Exhibitions.venue
    var organizer by Exhibitions.organizer
    var collectionIds by Exhibitions.collectionIds
    var coverImage by Exhibitions.coverImage
    var attachmentIds by Exhibitions.attachmentIds
    var startDate by Exhibitions.startDate
    var endDate by Exhibitions.endDate
    var state by Exhibitions.state
    var remark by Exhibitions.remark

    fun toModel() = ExhibitionVo(
        id = id.value,
        orgId = orgId,
        title = title,
        exhibitionNo = exhibitionNo,
        venueId = venueId,
        venue = venue,
        organizer = organizer,
        collectionIds = collectionIds,
        coverImage = coverImage,
        attachmentIds = attachmentIds,
        startDate = startDate,
        endDate = endDate,
        state = state,
        remark = remark,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
    )
}

data class ExhibitionVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val title: String? = null,
    val exhibitionNo: String? = null,
    /** 展陈位置树中的展馆/展厅 ID；venue 保留名称快照并兼容历史自由文本。 */
    val venueId: String? = null,
    val venue: String? = null,
    val organizer: String? = null,
    val collectionIds: String? = null,
    val collectionSummaries: List<CollectionSummaryVo> = emptyList(),
    /** 每件参展藏品的展柜分配；多个元素可以使用同一个 locationId。 */
    val placements: List<ExhibitionPlacementVo>? = null,
    val coverImage: String? = null,
    val attachmentIds: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val state: EnumProcessState? = null,
    val remark: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
)
