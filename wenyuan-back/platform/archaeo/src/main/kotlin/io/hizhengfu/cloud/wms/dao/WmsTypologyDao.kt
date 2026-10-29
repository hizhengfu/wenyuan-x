package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 型式分期表（型式分期模块）
 *
 * 一张分期表 = 一个遗址（projectId）+ 一种材质 / 器类（material 或 categoryId）
 * 表格内容存为 JSON（`content`），结构示例：
 * {
 *   "cols": [ {"artifactType":"鼎"} ],
 *   "rows": [
 *      { "phase":"早期", "section":"前段",
 *        "cells": [ {"text":"Ⅰ型 1式","imageUrl":"...","artifactId":"..."} ]
 *      }
 *   ],
 *   "layout": "A4_PORTRAIT" | "A4_LANDSCAPE"
 * }
 *
 * 前端富表格编辑器只关心 content，后端不强制 schema。
 */
object WmsTypologyTables : ContentAuditUIDTable("wms_typology_table") {
    val title = varchar("title", 255).nullable().comment("标题")
    val projectId = varchar("project_id", 64).comment("遗址")
    val projectName = varchar("project_name", 255).nullable().comment("遗址名称冗余")
    val material = varchar("material", 80).nullable().comment("器物质地")
    val categoryName = varchar("category_name", 80).nullable().comment("器物大类，如陶器、铜器")
    val dataSource = varchar("source", 20).default("AUTO").comment("AUTO=智能分期 / CUSTOM=自定义")
    val layout = varchar("layout", 20).default("A4_LANDSCAPE").comment("纸张布局")
    val content = largeText("content").nullable().comment("表格 JSON 内容（含 base64 图片，故用 LONGTEXT）")
    val excavateDate = datetime("excavate_date").nullable().comment("发掘时间")

    init {
        index(false, projectId, material)
    }
}

class WmsTypologyTableDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsTypologyTables) {
    companion object : ContentAuditUIDEntityClass<WmsTypologyTableDao>(WmsTypologyTables)

    var title by WmsTypologyTables.title
    var projectId by WmsTypologyTables.projectId
    var projectName by WmsTypologyTables.projectName
    var material by WmsTypologyTables.material
    var categoryName by WmsTypologyTables.categoryName
    var source by WmsTypologyTables.dataSource
    var layout by WmsTypologyTables.layout
    var content by WmsTypologyTables.content
    var excavateDate by WmsTypologyTables.excavateDate

    fun toModel(): WmsTypologyTableVo = WmsTypologyTableVo(
        id = id.value,
        title = title,
        projectId = projectId,
        projectName = projectName,
        material = material,
        categoryName = categoryName,
        source = source,
        layout = layout,
        content = content,
        excavateDate = excavateDate,
        status = status,
        createTime = createTime,
        createBy = createBy,
        updateTime = updateTime,
        updateBy = updateBy,
    )
}

data class WmsTypologyTableVo(
    val id: String? = null,
    val title: String? = null,
    @field:NotBlank val projectId: String? = null,
    val projectName: String? = null,
    val material: String? = null,
    val categoryName: String? = null,
    val source: String? = "AUTO",
    val layout: String? = "A4_LANDSCAPE",
    val content: String? = null,
    val excavateDate: LocalDateTime? = null,
    val status: EnumContentStatus? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val updateBy: String? = null,
)
