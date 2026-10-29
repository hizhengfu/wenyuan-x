package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 提用单（出入库流程载体）。申请→三方确认批准→实际逐件签署点交出库→归还入库。
 * 经办人 [operator] / 复核人 [reviewer] 均为系统用户 id（sys_user）。一级文物 reviewer 必填且 ≠ operator。
 * 三方确认批准后等待实际点交出库；归库由独立归还单办理。
 */
object Requisitions : FlowUIDTable("col_requisition"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val documentNo = varchar("document_no", 64).nullable().comment("提用单号")
    val sourceBizType = varchar("source_biz_type", 100).nullable().comment("来源业务类型")
    val sourceBizId = varchar("source_biz_id", 64).nullable().comment("来源业务ID")
    val collectionIds = text("collection_ids").default("").comment("提用藏品 id（逗号分隔）")
    val purpose = enumeration<EnumMoveReason>("purpose").nullable().comment("提用事由：研究/展览/修复/拍摄")
    val basis = varchar("basis", 500).nullable().comment("提用依据")
    val department = varchar("department", 100).nullable().comment("提用部门")
    val operator = varchar("operator", 64).nullable().comment("经办人（系统用户 id）")
    val reviewer = varchar("reviewer", 64).nullable().comment("复核人（系统用户 id，一级文物必填）")
    val custodian = varchar("custodian", 64).nullable().comment("保管员")
    val handoverUser = varchar("handover_user", 64).nullable().comment("交接人")
    val reviewOpinion = varchar("review_opinion", 500).nullable().comment("审核/验收意见")
    val attachmentIds = text("attachment_ids").nullable().comment("附件 fileId，逗号分隔")
    val conditionNote = text("condition_note").nullable().comment("出库前完残状况")
    val conditionPhotoIds = text("condition_photo_ids").nullable().comment("出库前多角度照片 fileId，逗号分隔")
    val signedVoucherAttachmentIds = text("signed_voucher_attachment_ids").nullable().comment("签署出库凭证 fileId，逗号分隔")
    val outExecuted = bool("out_executed").default(false).comment("是否完成现场出库")
    val outExecutedTime = datetime("out_executed_time").nullable().comment("现场出库时间")
    val toWarehouseId = varchar("to_warehouse_id", 64).nullable().comment("归还入库库位")
    val expectReturnDate = date("expect_return_date").nullable().comment("预计归还日期")
    val returnDate = date("return_date").nullable()
    val returned = bool("returned").default(false).comment("是否已归还")
    val dossier = json("statutory_dossier", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, RequisitionDossierVo::class.java)!! }).nullable()
    val baseline = json("ledger_baseline", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, RequisitionBaselineVo::class.java)!! }).nullable()
    val handover = json("signed_handover", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, RequisitionHandoverRecordVo::class.java)!! }).nullable()
    val sourceRetirement = json("source_retirement", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, RequisitionSourceRetirementRecord::class.java)!! }).nullable()
    val supersededById = varchar("superseded_by_id", 64).nullable().comment("责任变更后须重新审批的新提用单")
    val replacesId = varchar("replaces_id", 64).nullable().comment("责任变更所保留的原提用单")

}

class RequisitionDao(id: EntityID<String>) : FlowUIDEntity(id, Requisitions) {
    companion object : FlowUIDEntityClass<RequisitionDao>(Requisitions)

    var orgId by Requisitions.orgId
    var documentNo by Requisitions.documentNo
    var sourceBizType by Requisitions.sourceBizType
    var sourceBizId by Requisitions.sourceBizId
    var collectionIds by Requisitions.collectionIds
    var purpose by Requisitions.purpose
    var basis by Requisitions.basis
    var department by Requisitions.department
    var operator by Requisitions.operator
    var reviewer by Requisitions.reviewer
    var custodian by Requisitions.custodian
    var handoverUser by Requisitions.handoverUser
    var reviewOpinion by Requisitions.reviewOpinion
    var attachmentIds by Requisitions.attachmentIds
    var conditionNote by Requisitions.conditionNote
    var conditionPhotoIds by Requisitions.conditionPhotoIds
    var signedVoucherAttachmentIds by Requisitions.signedVoucherAttachmentIds
    var outExecuted by Requisitions.outExecuted
    var outExecutedTime by Requisitions.outExecutedTime
    var toWarehouseId by Requisitions.toWarehouseId
    var expectReturnDate by Requisitions.expectReturnDate
    var returnDate by Requisitions.returnDate
    var returned by Requisitions.returned

    var dossier by Requisitions.dossier
    var baseline by Requisitions.baseline
    var handover by Requisitions.handover
    var sourceRetirement by Requisitions.sourceRetirement
    var supersededById by Requisitions.supersededById
    var replacesId by Requisitions.replacesId

    fun toModel() = RequisitionVo(
        dossier = dossier, baseline = baseline, handover = handover,
        sourceRetirement = sourceRetirement, supersededById = supersededById, replacesId = replacesId,
        id = id.value,
        orgId = orgId,
        documentNo = documentNo,
        sourceBizType = sourceBizType,
        sourceBizId = sourceBizId,
        collectionIds = collectionIds,
        purpose = purpose,
        basis = basis,
        department = department,
        operator = operator,
        reviewer = reviewer,
        custodian = custodian,
        handoverUser = handoverUser,
        reviewOpinion = reviewOpinion,
        attachmentIds = attachmentIds,
        conditionNote = conditionNote,
        conditionPhotoIds = conditionPhotoIds,
        signedVoucherAttachmentIds = signedVoucherAttachmentIds,
        outExecuted = outExecuted,
        outExecutedTime = outExecutedTime,
        toWarehouseId = toWarehouseId,
        expectReturnDate = expectReturnDate,
        returnDate = returnDate,
        returned = returned,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
        createBy = createBy,
    )
}

data class RequisitionVo(
    val sourceRetirement: RequisitionSourceRetirementRecord? = null,
    val supersededById: String? = null,
    val replacesId: String? = null,
    val replacementErrors: List<String> = emptyList(),
    val archives: List<Map<String, Any?>> = emptyList(),
    val dossier: RequisitionDossierVo? = null,
    val baseline: RequisitionBaselineVo? = null,
    val handover: RequisitionHandoverRecordVo? = null,
    val id: String? = null,
    val orgId: String? = null,
    val documentNo: String? = null,
    val sourceBizType: String? = null,
    val sourceBizId: String? = null,
    @field:NotBlank val collectionIds: String? = null,
    val collectionSummaries: List<CollectionSummaryVo> = emptyList(),
    @field:NotNull val purpose: EnumMoveReason? = null,
    @field:NotBlank val basis: String? = null,
    @field:NotBlank val department: String? = null,
    @field:NotBlank val operator: String? = null,
    var operatorName: String? = null,
    val reviewer: String? = null,
    var reviewerName: String? = null,
    val custodian: String? = null,
    val handoverUser: String? = null,
    val reviewOpinion: String? = null,
    val attachmentIds: String? = null,
    val conditionNote: String? = null,
    val conditionPhotoIds: String? = null,
    val signedVoucherAttachmentIds: String? = null,
    val outExecuted: Boolean? = null,
    val outExecutedTime: LocalDateTime? = null,
    val toWarehouseId: String? = null,
    val expectReturnDate: LocalDate? = null,
    val returnDate: LocalDate? = null,
    val returned: Boolean? = null,
    /** 已归还藏品 id（部分归库进度，enrich 注入） */
    var returnedCollectionIds: List<String>? = null,
    var heldForReviewCollectionIds: List<String> = emptyList(),
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
)
