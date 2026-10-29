package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/** 盘点清查（账物核对，观察结果须经实际核验）。 */
object Inventories : FlowUIDTable("col_inventory") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val batchId = varchar("batch_id", 64).nullable().index(shortIndexName("batch_id")).comment("盘点批次")
    val batchName = varchar("batch_name", 100).nullable().comment("盘点批次")
    val collectionId = varchar("collection_id", 64).nullable().index(shortIndexName("collection_id"))
    val result = enumeration<EnumInventoryResult>("result").nullable().comment("账物相符/盘盈/盘亏/状态异常")
    val note = varchar("note", 500).nullable()
    val attachmentIds = text("attachment_ids").nullable().comment("盘点照片/清查报告附件 fileId，逗号分隔")
    val checkedAt = datetime("checked_at").nullable()

    init {
        index(shortIndexName("org_id_batch_name"), false, orgId, batchName)
    }
}

class InventoryDao(id: EntityID<String>) : FlowUIDEntity(id, Inventories) {
    companion object : FlowUIDEntityClass<InventoryDao>(Inventories)

    var orgId by Inventories.orgId
    var batchId by Inventories.batchId
    var batchName by Inventories.batchName
    var collectionId by Inventories.collectionId
    var result by Inventories.result
    var note by Inventories.note
    var attachmentIds by Inventories.attachmentIds
    var checkedAt by Inventories.checkedAt

    fun toModel() = InventoryVo(
        id = id.value, orgId = orgId, batchName = batchName, collectionId = collectionId,
        batchId = batchId,
        result = result, note = note, attachmentIds = attachmentIds, checkedAt = checkedAt,
        flowStatus = flowStatus, flowInstanceId = flowInstanceId, createTime = createTime,
    )
}

data class InventoryVo(
    val id: String? = null,
    val orgId: String? = null,
    val batchId: String? = null,
    @field:NotBlank val batchName: String? = null,
    val collectionId: String? = null,
    val collectionTotalRegNo: String? = null,
    val collectionName: String? = null,
    @field:NotNull val result: EnumInventoryResult? = null,
    val note: String? = null,
    val attachmentIds: String? = null,
    val checkedAt: LocalDateTime? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
)
