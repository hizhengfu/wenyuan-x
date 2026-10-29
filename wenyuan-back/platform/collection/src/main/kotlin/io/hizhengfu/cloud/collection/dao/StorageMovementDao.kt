package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/** 出入库流水（账物核对核心；一级文物出入库须双人 reviewer ≠ operator） */
object StorageMovements : OrgAuditUIDTable("col_movement"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val documentNo = varchar("document_no", 64).nullable().comment("出入库单号")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val totalRegNoAtMovement = varchar("total_reg_no_at_movement", 64).nullable().comment("本次出入库时的总登记号；旧流水可为空")
    val collectionNameAtMovement = varchar("collection_name_at_movement", 500).nullable().comment("本次出入库时的藏品名称；旧流水可为空")
    val type = enumeration<EnumMoveType>("type").comment("IN/OUT")
    val reason = enumeration<EnumMoveReason>("reason").nullable().comment("事由")
    val basis = varchar("basis", 500).nullable().comment("出入库依据")
    val department = varchar("department", 100).nullable().comment("领用/接收部门")
    val fromWarehouseId = varchar("from_warehouse_id", 64).nullable()
    val toWarehouseId = varchar("to_warehouse_id", 64).nullable()
    val operator = varchar("operator", 64).nullable().comment("经办人")
    val reviewer = varchar("reviewer", 64).nullable().comment("复核人（一级文物必填）")
    val custodian = varchar("custodian", 64).nullable().comment("保管员")
    val handoverUser = varchar("handover_user", 64).nullable().comment("交接人")
    val reviewOpinion = varchar("review_opinion", 500).nullable().comment("审核/验收意见")
    val attachmentIds = text("attachment_ids").nullable().comment("附件 fileId，逗号分隔")
    val operatedAt = datetime("operated_at").clientDefault { LocalDateTime.now() }
    val refBizId = varchar("ref_biz_id", 64).nullable().comment("提用单/借展/修复/调拨")

    init {
        index(shortIndexName("org_id_reason_collection_id"), false, orgId, reason, collectionId)
        index(shortIndexName("org_id_collection_id_operated_at"), false, orgId, collectionId, operatedAt)
        index(shortIndexName("emergency_from_collection"), false, orgId, reason, fromWarehouseId, collectionId)
        index(shortIndexName("emergency_to_collection"), false, orgId, reason, toWarehouseId, collectionId)
    }
}

class StorageMovementDao(id: EntityID<String>) : OrgAuditUIDEntity(id, StorageMovements) {
    companion object : OrgAuditUIDEntityClass<StorageMovementDao>(StorageMovements)

    var documentNo by StorageMovements.documentNo
    var collectionId by StorageMovements.collectionId
    var totalRegNoAtMovement by StorageMovements.totalRegNoAtMovement
    var collectionNameAtMovement by StorageMovements.collectionNameAtMovement
    var type by StorageMovements.type
    var reason by StorageMovements.reason
    var basis by StorageMovements.basis
    var department by StorageMovements.department
    var fromWarehouseId by StorageMovements.fromWarehouseId
    var toWarehouseId by StorageMovements.toWarehouseId
    var operator by StorageMovements.operator
    var reviewer by StorageMovements.reviewer
    var custodian by StorageMovements.custodian
    var handoverUser by StorageMovements.handoverUser
    var reviewOpinion by StorageMovements.reviewOpinion
    var attachmentIds by StorageMovements.attachmentIds
    var operatedAt by StorageMovements.operatedAt
    var refBizId by StorageMovements.refBizId

    fun toModel() = MovementVo(
        id = id.value,
        orgId = orgId,
        documentNo = documentNo,
        collectionId = collectionId,
        totalRegNo = totalRegNoAtMovement,
        collectionName = collectionNameAtMovement,
        type = type,
        reason = reason,
        basis = basis,
        department = department,
        fromWarehouseId = fromWarehouseId,
        toWarehouseId = toWarehouseId,
        operator = operator,
        reviewer = reviewer,
        custodian = custodian,
        handoverUser = handoverUser,
        reviewOpinion = reviewOpinion,
        attachmentIds = attachmentIds,
        operatedAt = operatedAt,
        refBizId = refBizId,
    )
}

data class MovementVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val documentNo: String? = null,
    /** 可用 RFID 标签替代（服务端解析为 collectionId） */
    val rfidTag: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val collectionName: String? = null,
    @field:NotNull val type: EnumMoveType? = null,
    @field:NotNull val reason: EnumMoveReason? = null,
    val basis: String? = null,
    val department: String? = null,
    val fromWarehouseId: String? = null,
    val toWarehouseId: String? = null,
    val operator: String? = null,
    val reviewer: String? = null,
    val custodian: String? = null,
    val handoverUser: String? = null,
    val reviewOpinion: String? = null,
    val attachmentIds: String? = null,
    val operatedAt: LocalDateTime? = null,
    val refBizId: String? = null,
)
