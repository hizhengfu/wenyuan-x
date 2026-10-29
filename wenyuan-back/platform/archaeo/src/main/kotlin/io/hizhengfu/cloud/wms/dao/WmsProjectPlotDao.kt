package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable

import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime


/**
 * 项目探方
 * */
object WmsProjectPlots : ContentAuditUIDTable("wms_project_plot") {
    val name = varchar("name", 100).comment("单元名称")
    val projectId = varchar("project_id", 64).comment("所属项目")
    val image = varchar("image", 255).nullable().comment("缩略图")
    val code = varchar("code", 100).nullable().comment("编号")
    val remarks = text("remarks").nullable().comment("备注")

    fun toModel(row: ResultRow): WmsProjectPlotVo {
        return WmsProjectPlotVo(
                id = row[id]?.value,
                name = row[name],
                projectId = row[projectId],
                project = WmsProjectVo(id = row[WmsProjects.id].value, name = row[WmsProjects.name]),
                image = row[image],
                code = row[code],
                remarks = row[remarks],
                createBy = row[createBy],
                createTime = row[createTime],
                updateBy = row[updateBy],
                updateTime = row[updateTime])
    }
}

class WmsProjectPlotDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsProjectPlots) {
    companion object : ContentAuditUIDEntityClass<WmsProjectPlotDao>(WmsProjectPlots)

    var name by WmsProjectPlots.name
    var projectId by WmsProjectPlots.projectId
    var image by WmsProjectPlots.image
    var code by WmsProjectPlots.code
    var remarks by WmsProjectPlots.remarks

    fun toModel(): WmsProjectPlotVo {
        return WmsProjectPlotVo(
                id = id.value,
                name = name,
                projectId = projectId,
                image = image,
                code = code,
                remarks = remarks,
                createBy = createBy,
                createTime = createTime,
                updateBy = updateBy,
                updateTime = updateTime)
    }
}

data class WmsProjectPlotVo(
    val id: String? = null,
     @field:NotBlank
    val name: String? = null,
     @field:NotBlank
    val projectId: String? = null,
    val project: WmsProjectVo? = null,
    val image: String? = null,
    val code: String? = null,
    val remarks: String? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    val updateBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val files: List<String>? = null)
