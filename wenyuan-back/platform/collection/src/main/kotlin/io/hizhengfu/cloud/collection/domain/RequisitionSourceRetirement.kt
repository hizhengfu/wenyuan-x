package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonInclude

/** 来源业务整体重建时保留原提用；新来源仍须独立生成并审批其送修单。 */
@JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS)
data class RequisitionSourceSnapshot(
    val requisitionId: String,
    val orgId: String,
    val originalFlowStatus: EnumFlowStatus,
    val originalInstanceId: String?,
    val proposal: String,
    val archiveDigests: Map<String, String>,
    val archiveFileIds: Map<String, String>,
)
@JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS)
data class RequisitionRetirementContext(
    val sourceBizType: String,
    val sourceId: String,
    val replacementSourceId: String,
    val reason: String,
    val appointmentFileIds: String,
    val withdrawalFileIds: String,
    val authorizedBy: String,
    val authorizedAt: LocalDateTime,
    val requisitions: Map<String, RequisitionSourceSnapshot>,
    val originalFileIds: List<String> = emptyList(),
)
data class RequisitionSourceRetirementRecord(
    val rootArchiveId: String,
    val rootArchiveDigest: String,
    val context: RequisitionRetirementContext,
)
