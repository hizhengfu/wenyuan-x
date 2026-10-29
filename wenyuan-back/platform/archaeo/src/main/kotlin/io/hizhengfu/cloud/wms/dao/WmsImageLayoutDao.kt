package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/**
 * 图片排版图（图片排版模块）
 *
 * - 一份排版 = 多件文物图片在一张幅面上的位置 / 比例尺 / 图号 / 连接线 等
 * - content 是前端 canvas 编辑器序列化出的 JSON：
 *   {
 *     "width": 800, "height": 1200, "unit":"mm",
 *     "items": [
 *       { "type":"image", "src":"...", "x":10, "y":20, "w":80, "h":120, "rotate":0,
 *         "scaleRatio":2.5, "artifactId":"...", "label":"1", "linkedTo":[3,4] },
 *       { "type":"label", "x":50, "y":150, "text":"1：陶罐 H001" },
 *       { "type":"scale", "x":700, "y":1100, "length":10 }
 *     ]
 *   }
 * - thumbnailUrl 由前端导出后回填（用于列表预览）
 */
object WmsImageLayouts : ContentAuditUIDTable("wms_image_layout") {
    val title = varchar("title", 255).nullable().comment("图名")
    val projectId = varchar("project_id", 64).nullable().comment("遗址")
    val projectName = varchar("project_name", 255).nullable().comment("遗址名称冗余")
    val layout = varchar("layout", 20).default("A4_PORTRAIT").comment("纸张布局")
    val width = integer("width").default(794).comment("画布宽（像素）")
    val height = integer("height").default(1123).comment("画布高（像素）")
    val content = largeText("content").nullable().comment("画布 JSON（含 base64 图片，故用 LONGTEXT）")
    val thumbnailUrl = varchar("thumbnail_url", 255).nullable().comment("缩略图 URL")
}

class WmsImageLayoutDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsImageLayouts) {
    companion object : ContentAuditUIDEntityClass<WmsImageLayoutDao>(WmsImageLayouts)

    var title by WmsImageLayouts.title
    var projectId by WmsImageLayouts.projectId
    var projectName by WmsImageLayouts.projectName
    var layout by WmsImageLayouts.layout
    var width by WmsImageLayouts.width
    var height by WmsImageLayouts.height
    var content by WmsImageLayouts.content
    var thumbnailUrl by WmsImageLayouts.thumbnailUrl

    fun toModel(): WmsImageLayoutVo = WmsImageLayoutVo(
        id = id.value,
        title = title,
        projectId = projectId,
        projectName = projectName,
        layout = layout,
        width = width,
        height = height,
        content = content,
        thumbnailUrl = thumbnailUrl,
        status = status,
        createTime = createTime,
        createBy = createBy,
        updateTime = updateTime,
        updateBy = updateBy,
    )
}

data class WmsImageLayoutVo(
    val id: String? = null,
    @field:NotBlank val title: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val layout: String? = "A4_PORTRAIT",
    val width: Int? = null,
    val height: Int? = null,
    val content: String? = null,
    val thumbnailUrl: String? = null,
    val status: EnumContentStatus? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val updateBy: String? = null,
)
