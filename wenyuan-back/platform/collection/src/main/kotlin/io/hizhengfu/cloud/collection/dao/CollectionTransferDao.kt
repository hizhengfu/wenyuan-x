package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/**
 * 跨馆调拨（流程驱动，多馆）。orgId（继承的隔离列）=源馆，toOrgId=目标馆。
 * 批准只完成确认；实际签署交接后执行过户、双馆流水及永久留痕。
 */
object CollectionTransfers : FlowUIDTable("col_transfer") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("源收藏单位（隔离列）")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val fromOrgId = varchar("from_org_id", 64).nullable().comment("源收藏单位")
    val toOrgId = varchar("to_org_id", 64).nullable().comment("目标收藏单位")
    val reason = varchar("reason", 500).nullable()
    val approvalDocNo = varchar("approval_doc_no", 128).nullable().comment("调拨批文号")
    val attachmentIds = text("attachment_ids").nullable().comment("调拨批文/交接材料附件 fileId，逗号分隔")
    val dossier = json("statutory_dossier", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionTransferDossierVo::class.java)!! }).nullable()
    val baseline = json("ledger_baseline", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java)!! }).nullable()
    val execution = json("signed_execution", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionTransferExecutionVo::class.java)!! }).nullable()
    val executedAt = datetime("executed_at").nullable()
    val executedBy = varchar("executed_by", 64).nullable()
    val announcedAt = datetime("announced_at").nullable()

}

class CollectionTransferDao(id: EntityID<String>) : FlowUIDEntity(id, CollectionTransfers) {
    companion object : FlowUIDEntityClass<CollectionTransferDao>(CollectionTransfers)

    var orgId by CollectionTransfers.orgId
    var collectionId by CollectionTransfers.collectionId
    var fromOrgId by CollectionTransfers.fromOrgId
    var toOrgId by CollectionTransfers.toOrgId
    var reason by CollectionTransfers.reason
    var approvalDocNo by CollectionTransfers.approvalDocNo
    var attachmentIds by CollectionTransfers.attachmentIds

    var dossier by CollectionTransfers.dossier
    var baseline by CollectionTransfers.baseline
    var execution by CollectionTransfers.execution
    var executedAt by CollectionTransfers.executedAt
    var executedBy by CollectionTransfers.executedBy
    var announcedAt by CollectionTransfers.announcedAt

    fun toModel() = CollectionTransferVo(
        id = id.value,
        orgId = orgId,
        collectionId = collectionId,
        fromOrgId = fromOrgId,
        toOrgId = toOrgId,
        reason = reason,
        approvalDocNo = approvalDocNo,
        attachmentIds = attachmentIds,
        dossier = dossier, baseline = baseline, execution = execution, executedAt = executedAt, executedBy = executedBy, announcedAt = announcedAt,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
        createBy = createBy,
    )
}

data class CollectionTransferVo(
    val id: String? = null,
    val orgId: String? = null,
    @field:NotBlank val collectionId: String? = null,
    val collectionTotalRegNo: String? = null,
    val collectionName: String? = null,
    val fromOrgId: String? = null,
    @field:NotBlank val toOrgId: String? = null,
    @field:NotBlank val reason: String? = null,
    val approvalDocNo: String? = null,
    val attachmentIds: String? = null,
    val sourceWritable: Boolean? = null,
    val proposalEditable: Boolean? = null,
    val dossier: CollectionTransferDossierVo? = null,
    val baseline: CollectionCatalogContentVo? = null,
    val execution: CollectionTransferExecutionVo? = null,
    val executedAt: LocalDateTime? = null,
    val executedBy: String? = null,
    val announcedAt: LocalDateTime? = null,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    val currentNoticeComplete: Boolean = false,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    val terminationClosed: Boolean = false,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    val terminationPending: Boolean = false,
    val administrativeEvidence: List<CollectionAdministrativeEvidenceVo> = emptyList(),
    val notices: List<DeregistrationNoticeVo> = emptyList(),
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
)
