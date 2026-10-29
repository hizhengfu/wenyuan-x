package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.collection.domain.CollectionSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

/** 借展/外展（流程驱动，出馆审批）。批准后持锁待出库；真实逐件点交后借出，返还另行逐件验收。 */
object Loans : FlowUIDTable("col_loan") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val title = varchar("title", 200).nullable().comment("展览/事由")
    val collectionIds = text("collection_ids").nullable().comment("借出藏品 id（逗号分隔）")
    val borrower = varchar("borrower", 200).nullable().comment("借入方")
    val protocolNo = varchar("protocol_no", 128).nullable().comment("协议号")
    val insurance = varchar("insurance", 255).nullable().comment("保险")
    val approveLevel = enumeration<EnumApproveLevel>("approve_level").nullable().comment("出馆审批层级")
    val approvalDocNo = varchar("approval_doc_no", 128).nullable().comment("出馆批文号")
    val attachmentIds = text("attachment_ids").nullable().comment("协议/批文/保险等附件 fileId，逗号分隔")
    val conditionNote = text("condition_note").nullable().comment("出库前完残状况")
    val conditionPhotoIds = text("condition_photo_ids").nullable().comment("出库前多角度照片 fileId，逗号分隔")
    val signedVoucherAttachmentIds = text("signed_voucher_attachment_ids").nullable().comment("签署出库凭证 fileId，逗号分隔")
    val outExecuted = bool("out_executed").default(false).comment("是否完成现场出库")
    val outExecutedTime = datetime("out_executed_time").nullable().comment("现场出库时间")
    val operator = varchar("operator", 64).nullable().comment("经办人（系统用户 id）")
    val reviewer = varchar("reviewer", 64).nullable().comment("复核人（一级文物必填）")
    val loanDate = date("loan_date").nullable()
    val returnDate = date("return_date").nullable()
    val dossier=json("statutory_dossier", {JsonUtils.toJson(it)!!}, {JsonUtils.fromJson(it,LoanDossierVo::class.java)!!}).nullable()
    val baseline=json("ledger_baseline", {JsonUtils.toJson(it)!!}, {JsonUtils.fromJson(it,LoanBaselineVo::class.java)!!}).nullable()
    val handover=json("signed_handover", {JsonUtils.toJson(it)!!}, {JsonUtils.fromJson(it,LoanHandoverRecordVo::class.java)!!}).nullable()
    val returned = bool("returned").default(false).comment("是否已返还")
}

class LoanDao(id: EntityID<String>) : FlowUIDEntity(id, Loans) {
    companion object : FlowUIDEntityClass<LoanDao>(Loans)

    var orgId by Loans.orgId
    var title by Loans.title
    var collectionIds by Loans.collectionIds
    var borrower by Loans.borrower
    var protocolNo by Loans.protocolNo
    var insurance by Loans.insurance
    var approveLevel by Loans.approveLevel
    var approvalDocNo by Loans.approvalDocNo
    var attachmentIds by Loans.attachmentIds
    var conditionNote by Loans.conditionNote
    var conditionPhotoIds by Loans.conditionPhotoIds
    var signedVoucherAttachmentIds by Loans.signedVoucherAttachmentIds
    var outExecuted by Loans.outExecuted
    var outExecutedTime by Loans.outExecutedTime
    var operator by Loans.operator
    var reviewer by Loans.reviewer
    var loanDate by Loans.loanDate
    var returnDate by Loans.returnDate
    var dossier by Loans.dossier
    var baseline by Loans.baseline
    var handover by Loans.handover
    var returned by Loans.returned

    fun toModel() = LoanVo(
        dossier=dossier,baseline=baseline,handover=handover,
        id = id.value,
        orgId = orgId,
        title = title,
        collectionIds = collectionIds,
        borrower = borrower,
        protocolNo = protocolNo,
        insurance = insurance,
        approveLevel = approveLevel,
        approvalDocNo = approvalDocNo,
        attachmentIds = attachmentIds,
        conditionNote = conditionNote,
        conditionPhotoIds = conditionPhotoIds,
        signedVoucherAttachmentIds = signedVoucherAttachmentIds,
        outExecuted = outExecuted,
        outExecutedTime = outExecutedTime,
        operator = operator,
        reviewer = reviewer,
        loanDate = loanDate,
        returnDate = returnDate,
        returned = returned,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
        createBy = createBy,
    )
}

data class LoanVo(
    val dossier: LoanDossierVo? = null,
    val baseline: LoanBaselineVo? = null,
    val handover: LoanHandoverRecordVo? = null,
    val administrativeEvidence: List<Map<String,Any?>> = emptyList(),
    val monitoringRecords: List<Map<String,Any?>> = emptyList(),
    val exhibitionEnds: List<Map<String,Any?>> = emptyList(),
    val exhibitionEndAvailable: Boolean = false,
    val overseasFilings: List<Map<String,Any?>> = emptyList(),
    val overseasFilingAvailable: Boolean = false,
    val overseasPorts: List<Map<String,Any?>> = emptyList(),
    val portAvailable: Boolean = false,
    val overseasExtensions: List<Map<String,Any?>> = emptyList(),
    val extensionSourceRevision: String? = null,
    val extensionAvailable: Boolean = false,
    val obligations: Map<String,Any?> = emptyMap(),
    val archives: List<Map<String,Any?>> = emptyList(),
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    val evidenceErrors: List<String> = emptyList(),
    val proposalEditable: Boolean? = null,
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val title: String? = null,
    @field:NotBlank val collectionIds: String? = null,
    val collectionSummaries: List<CollectionSummaryVo> = emptyList(),
    @field:NotBlank val borrower: String? = null,
    val protocolNo: String? = null,
    val insurance: String? = null,
    val approveLevel: EnumApproveLevel? = null,
    val approvalDocNo: String? = null,
    val attachmentIds: String? = null,
    val conditionNote: String? = null,
    val conditionPhotoIds: String? = null,
    val signedVoucherAttachmentIds: String? = null,
    val outExecuted: Boolean? = null,
    val outExecutedTime: LocalDateTime? = null,
    val operator: String? = null,
    val reviewer: String? = null,
    @field:NotNull val loanDate: LocalDate? = null,
    val returnDate: LocalDate? = null,
    val returned: Boolean? = null,
    /** 已返还藏品 id（部分归库进度，enrich 注入） */
    var returnedCollectionIds: List<String>? = null,
    var heldForReviewCollectionIds: List<String> = emptyList(),
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
)
