package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumMoveReason
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

enum class EnumReturnExecutionState(val description: String) {
    PENDING("待现场归还"),
    EXECUTED("已接收保管"),
}

/** 归还流程：确认后登记实际接收，逐件区分已验收与继续待核查；保留历史执行记录。 */
object CollectionReturns : FlowUIDTable("col_return") {
    val keeperUserId = varchar("keeper_user_id",64).nullable()
    val inspection = json("inspection",{ JsonUtils.toJson(it)!! },{ JsonUtils.fromJson(it,ReturnInspectionVo::class.java)!! }).nullable()
    val sourceSnapshot = json("source_snapshot",{ JsonUtils.toJson(it)!! },{ JsonUtils.fromJson(it,ReturnSourceSnapshotVo::class.java)!! }).nullable()
    val acceptedCollectionIds = text("accepted_collection_ids").nullable().comment("已完成真实性及状况验收的藏品；旧已执行单为空时沿用原清单")
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val sourceBizType = varchar("source_biz_type", 100).comment("来源业务类型")
    val sourceBizId = varchar("source_biz_id", 64).comment("来源业务ID")
    val sourceDocumentNo = varchar("source_document_no", 100).nullable().comment("来源单号")
    val collectionIds = text("collection_ids").comment("归还藏品 id，逗号分隔")
    val reason = enumeration<EnumMoveReason>("reason").nullable().comment("原出库事由")
    val basis = varchar("basis", 500).nullable().comment("归还依据")
    val department = varchar("department", 100).nullable().comment("使用/借入部门")
    val operator = varchar("operator", 64).nullable().comment("经办人")
    val reviewer = varchar("reviewer", 64).nullable().comment("复核人")
    val custodian = varchar("custodian", 64).nullable().comment("保管员")
    val handoverUser = varchar("handover_user", 64).nullable().comment("交接人")
    val reviewOpinion = varchar("review_opinion", 500).nullable().comment("归还验收意见")
    val attachmentIds = text("attachment_ids").nullable().comment("附件 fileId，逗号分隔")
    val toWarehouseId = varchar("to_warehouse_id", 64).nullable().comment("归还入库库位")
    val expectedReturnDate = date("expected_return_date").nullable().comment("预计归还日期")
    val returnDate = date("return_date").nullable().comment("实际归还日期")
    val damageNote = text("damage_note").nullable().comment("损伤确认记录（双方当面确认后录入）")
    val damagePhotoIds = text("damage_photo_ids").nullable().comment("损伤照片 fileId，逗号分隔")
    val signedVoucherAttachmentIds = text("signed_voucher_attachment_ids").nullable().comment("双方签署归还凭证 fileId，逗号分隔")
    /**
     * 默认 EXECUTED 用于兼容升级前已经在审批回调中完成归库的历史 PASS 数据；
     * 所有新建归还单由业务服务显式写入 PENDING。
     */
    val executionState = enumeration<EnumReturnExecutionState>("execution_state")
        .default(EnumReturnExecutionState.EXECUTED)
        .index(shortIndexName("execution_state"))
        .comment("现场归还执行状态")
    val executedAt = datetime("executed_at").nullable().comment("实际执行归库时间")
    val executedBy = varchar("executed_by", 64).nullable().comment("实际执行归库人员")

    init {
        index(shortIndexName("org_id_source_biz_type_source_biz_id"), false, orgId, sourceBizType, sourceBizId)
    }
}

class CollectionReturnDao(id: EntityID<String>) : FlowUIDEntity(id, CollectionReturns) {
    companion object : FlowUIDEntityClass<CollectionReturnDao>(CollectionReturns)

    var keeperUserId by CollectionReturns.keeperUserId
    var inspection by CollectionReturns.inspection
    var sourceSnapshot by CollectionReturns.sourceSnapshot
    var acceptedCollectionIds by CollectionReturns.acceptedCollectionIds
    var orgId by CollectionReturns.orgId
    var sourceBizType by CollectionReturns.sourceBizType
    var sourceBizId by CollectionReturns.sourceBizId
    var sourceDocumentNo by CollectionReturns.sourceDocumentNo
    var collectionIds by CollectionReturns.collectionIds
    var reason by CollectionReturns.reason
    var basis by CollectionReturns.basis
    var department by CollectionReturns.department
    var operator by CollectionReturns.operator
    var reviewer by CollectionReturns.reviewer
    var custodian by CollectionReturns.custodian
    var handoverUser by CollectionReturns.handoverUser
    var reviewOpinion by CollectionReturns.reviewOpinion
    var attachmentIds by CollectionReturns.attachmentIds
    var toWarehouseId by CollectionReturns.toWarehouseId
    var expectedReturnDate by CollectionReturns.expectedReturnDate
    var returnDate by CollectionReturns.returnDate
    var damageNote by CollectionReturns.damageNote
    var damagePhotoIds by CollectionReturns.damagePhotoIds
    var signedVoucherAttachmentIds by CollectionReturns.signedVoucherAttachmentIds
    var executionState by CollectionReturns.executionState
    var executedAt by CollectionReturns.executedAt
    var executedBy by CollectionReturns.executedBy

    fun toModel() = CollectionReturnVo(
        keeperUserId=keeperUserId,inspection=inspection,sourceSnapshot=sourceSnapshot,acceptedCollectionIds=acceptedCollectionIds,
        id = id.value,
        orgId = orgId,
        sourceBizType = sourceBizType,
        sourceBizId = sourceBizId,
        sourceDocumentNo = sourceDocumentNo,
        collectionIds = collectionIds,
        reason = reason,
        basis = basis,
        department = department,
        operator = operator,
        reviewer = reviewer,
        custodian = custodian,
        handoverUser = handoverUser,
        reviewOpinion = reviewOpinion,
        attachmentIds = attachmentIds,
        toWarehouseId = toWarehouseId,
        expectedReturnDate = expectedReturnDate,
        returnDate = returnDate,
        damageNote = damageNote,
        damagePhotoIds = damagePhotoIds,
        signedVoucherAttachmentIds = signedVoucherAttachmentIds,
        executionState = executionState,
        executedAt = executedAt,
        executedBy = executedBy,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
    )
}

data class CollectionReturnVo(
    val keeperUserId: String? = null,
    val inspection: ReturnInspectionVo? = null,
    val sourceSnapshot: ReturnSourceSnapshotVo? = null,
    val acceptedCollectionIds: String? = null,
    val pendingRequirements: List<Map<String,Any?>> = emptyList(),
    val complianceRecords: List<Map<String,Any?>> = emptyList(),
    val archives: List<Map<String,Any?>> = emptyList(),
    val evidenceErrors: List<String> = emptyList(),
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val sourceBizType: String? = null,
    @field:NotBlank val sourceBizId: String? = null,
    val sourceDocumentNo: String? = null,
    @field:NotBlank val collectionIds: String? = null,
    val collectionSummaries: List<CollectionSummaryVo> = emptyList(),
    val reason: EnumMoveReason? = null,
    val basis: String? = null,
    val department: String? = null,
    val operator: String? = null,
    var operatorName: String? = null,
    val reviewer: String? = null,
    var reviewerName: String? = null,
    val custodian: String? = null,
    val handoverUser: String? = null,
    val reviewOpinion: String? = null,
    val attachmentIds: String? = null,
    val toWarehouseId: String? = null,
    val expectedReturnDate: LocalDate? = null,
    val returnDate: LocalDate? = null,
    val damageNote: String? = null,
    val damagePhotoIds: String? = null,
    val signedVoucherAttachmentIds: String? = null,
    val executionState: EnumReturnExecutionState? = null,
    val executedAt: LocalDateTime? = null,
    val executedBy: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
)
