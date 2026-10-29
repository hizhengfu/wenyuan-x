package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime

/** 库房日志：保管员记录当日库房事件（记录人由审计字段自动填）。 */
object WarehouseLogs : OrgAuditUIDTable("col_warehouse_log") {
    val warehouseId = varchar("warehouse_id", 64).nullable().index(shortIndexName("warehouse_id"))
        .comment("库房（空=整库/通用）")
    val logDate = date("log_date").comment("日志日期")
    val content = text("content").comment("事件内容")
    val attachmentIds = text("attachment_ids").nullable().comment("附件 fileId，逗号分隔")

    val diary = json("statutory_diary", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseDiaryVo::class.java)!! }).nullable()
    val entries = json("entry_snapshot", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseDiaryEntriesVo::class.java)!! }).nullable()

    init {
        index(shortIndexName("org_id_log_date"), false, orgId, logDate)
    }
}

class WarehouseLogDao(id: EntityID<String>) : OrgAuditUIDEntity(id, WarehouseLogs) {
    companion object : OrgAuditUIDEntityClass<WarehouseLogDao>(WarehouseLogs)

    var warehouseId by WarehouseLogs.warehouseId
    var logDate by WarehouseLogs.logDate
    var content by WarehouseLogs.content
    var attachmentIds by WarehouseLogs.attachmentIds

    var diary by WarehouseLogs.diary
    var entries by WarehouseLogs.entries

    fun toModel() = WarehouseLogVo(
        id = id.value,
        diary = diary,
        entries = entries,

        orgId = orgId,
        warehouseId = warehouseId,
        logDate = logDate,
        content = content,
        attachmentIds = attachmentIds,
        createBy = createBy,
        createTime = createTime,
    )
}

data class WarehouseLogVo(
    val id: String? = null,
    val diary: WarehouseDiaryVo? = null,
    val entries: WarehouseDiaryEntriesVo? = null,
    val archives: List<Map<String, Any?>> = emptyList(),
    val currentEntries: WarehouseDiaryEntriesVo? = null,
    val entriesChanged: Boolean = false,
    val orgId: String? = null,
    val warehouseId: String? = null,
    var warehouseName: String? = null,
    @field:NotNull val logDate: LocalDate? = null,
    @field:NotBlank val content: String? = null,
    val attachmentIds: String? = null,
    val createBy: String? = null,
    var createByName: String? = null,
    val createTime: LocalDateTime? = null,
    /** 列表隐藏未核实正文；详情通过快照核验后可展示记录，并提示原件不可用。 */
    val evidenceIssue: String? = null,
)
