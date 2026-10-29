package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import java.time.LocalDateTime

/** 现任主要负责人核实未点交旧单的终止依据，创建须重新三方审批的新申请。 */
data class RequisitionReplacementRequest(
    val expectedInstanceId: String,
    val expectedSubmittedDigest: String,
    val reason: String,
    val appointmentFileIds: String,
    val withdrawalFileIds: String,
    val dossier: RequisitionDossierVo,
    val operatorUserId: String,
    val reviewerUserId: String? = null,
)

data class RequisitionReplacementRecord(
    val sourceId: String,
    val replacementId: String,
    val request: RequisitionReplacementRequest,
    val originalFlowStatus: EnumFlowStatus,
    val sourceArchiveDigests: Map<String, String>,
    val initialProposal: String,
    val authorizedBy: String,
    val authorizedAt: LocalDateTime,
)
