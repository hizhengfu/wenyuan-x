package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.DeregistrationDossierVo
import io.hizhengfu.cloud.collection.domain.DeregistrationExpertOpinionVo
import io.hizhengfu.cloud.collection.domain.DeregistrationNoticeVo
import io.hizhengfu.cloud.collection.domain.CollectionAdministrativeEvidenceVo
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json
import java.time.LocalDate
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.collection.domain.EnumApproveLevel
import io.hizhengfu.cloud.collection.domain.EnumDeregReason
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/**
 * 注销申请（流程驱动，逐级报批）。审批通过后等待签署执行，实际退出保留原总账和总登记号。
 */
object Deregistrations : FlowUIDTable("col_deregistration") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val reason = enumeration<EnumDeregReason>("reason").nullable().comment("注销原因")
    val detail = text("detail").nullable()
    val dossier = json("dossier", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, DeregistrationDossierVo::class.java)!! }).nullable()
    val baseline = json("baseline", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java)!! }).nullable()
    val vetoUntil = date("veto_until").nullable().comment("专家否决后的五年禁重启截止日，不得清空")
    val executedAt = datetime("executed_at").nullable()
    val announcedAt = datetime("announced_at").nullable()

    val approveLevel = enumeration<EnumApproveLevel>("approve_level").nullable().comment("审批层级")
    val approvalDocNo = varchar("approval_doc_no", 128).nullable().comment("批文号")
    val expertOpinion = text("expert_opinion").nullable().comment("专家评委会评估认定意见")
    val approvedBy = varchar("approved_by", 100).nullable().comment("最终审批人")
    val destination = varchar("destination", 500).nullable().comment("注销后去向")
    val attachmentIds = text("attachment_ids").nullable().comment("专家论证/审批批文附件 fileId，逗号分隔")
}

class DeregistrationDao(id: EntityID<String>) : FlowUIDEntity(id, Deregistrations) {
    companion object : FlowUIDEntityClass<DeregistrationDao>(Deregistrations)

    var orgId by Deregistrations.orgId
    var collectionId by Deregistrations.collectionId
    var reason by Deregistrations.reason
    var detail by Deregistrations.detail
    var dossier by Deregistrations.dossier
    var baseline by Deregistrations.baseline
    var vetoUntil by Deregistrations.vetoUntil
    var executedAt by Deregistrations.executedAt
    var announcedAt by Deregistrations.announcedAt
    var approveLevel by Deregistrations.approveLevel
    var approvalDocNo by Deregistrations.approvalDocNo
    var expertOpinion by Deregistrations.expertOpinion
    var approvedBy by Deregistrations.approvedBy
    var destination by Deregistrations.destination
    var attachmentIds by Deregistrations.attachmentIds

    fun toModel() = DeregistrationVo(
        id = id.value,
        orgId = orgId,
        collectionId = collectionId,
        reason = reason,
        detail = detail, dossier = dossier, vetoUntil = vetoUntil, executedAt = executedAt, announcedAt = announcedAt,
        approveLevel = approveLevel,
        approvalDocNo = approvalDocNo,
        expertOpinion = expertOpinion,
        approvedBy = approvedBy,
        destination = destination,
        attachmentIds = attachmentIds,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
        createBy = createBy,
    )
}

data class DeregistrationVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val collectionId: String? = null,
    val collectionTotalRegNo: String? = null,
    val collectionName: String? = null,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    val canOperate: Boolean = false,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val sourceIssue: String? = null,
    val evidenceErrors: List<String> = emptyList(),
    @field:NotNull val reason: EnumDeregReason? = null,
    @field:NotBlank val detail: String? = null,
    val dossier: DeregistrationDossierVo? = null,
    val vetoUntil: LocalDate? = null,
    val executedAt: LocalDateTime? = null,
    val announcedAt: LocalDateTime? = null,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    val currentNoticeComplete: Boolean = false,
    val experts: List<DeregistrationExpertOpinionVo> = emptyList(),
    val notices: List<DeregistrationNoticeVo> = emptyList(),
    val administrativeEvidence: List<CollectionAdministrativeEvidenceVo> = emptyList(),
    val approveLevel: EnumApproveLevel? = null,
    val approvalDocNo: String? = null,
    val expertOpinion: String? = null,
    val approvedBy: String? = null,
    val destination: String? = null,
    val attachmentIds: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
)
