package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable

import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime


/**
 * 项目单元
 * */
object WmsProjectCells : ContentAuditUIDTable("wms_project_cell") {
    val name = varchar("name", 100).comment("单元名称")
    val type = varchar("type", 64).nullable().comment("分类")
    val projectId = varchar("project_id", 64).comment("所属项目")
    val plotId = varchar("plot_id", 64).nullable().comment("所属探方")
    val image = varchar("image", 255).nullable().comment("缩略图")
    val code = varchar("code", 100).nullable().comment("编号")
    val remarks = text("remarks").nullable().comment("备注")
    val sort = long("sort").default(0)//字典排序

    fun toModel(row: ResultRow): WmsProjectCellVo {
        return WmsProjectCellVo(
            id = row[id]?.value,
            name = row[name],
            projectId = row[projectId],
            project = WmsProjectVo(id = row[WmsProjects.id].value, name = row[WmsProjects.name]),
            plotId = row[plotId],
            plot = WmsProjectPlotVo(id = row[plotId], name = row[WmsProjectPlots.name]),
            image = row[image],
            code = row[code],
            remarks = row[remarks],
            sort = row[sort],
            type = row[type],
            createBy = row[createBy],
            createTime = row[createTime],
            updateBy = row[updateBy],
            updateTime = row[updateTime]
        )
    }
}

class WmsProjectCellDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsProjectCells) {
    companion object : ContentAuditUIDEntityClass<WmsProjectCellDao>(WmsProjectCells)

    var name by WmsProjectCells.name
    var projectId by WmsProjectCells.projectId
    var plotId by WmsProjectCells.plotId
    var image by WmsProjectCells.image
    var code by WmsProjectCells.code
    var remarks by WmsProjectCells.remarks
    var type by WmsProjectCells.type
    var sort by WmsProjectCells.sort

    fun toModel(): WmsProjectCellVo {
        return WmsProjectCellVo(
            id = id.value,
            name = name,
            projectId = projectId,
            plotId = plotId,
            image = image,
            code = code,
            remarks = remarks,
            sort = sort,
            type = type,
            createBy = createBy,
            createTime = createTime,
            updateBy = updateBy,
            updateTime = updateTime
        )
    }
}

data class WmsProjectCellVo(
    val id: String? = null,
    @field:NotBlank
    val name: String? = null,
    @field:NotBlank
    val projectId: String? = null,
    val project: WmsProjectVo? = null,
    val plotId: String? = null,
    val plot: WmsProjectPlotVo? = null,
    val image: String? = null,
    val code: String? = null,
    val remarks: String? = null,
    @field:NotBlank
    val type: String? = null,
    val sort: Long? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    val updateBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val files: List<String>? = null
)
