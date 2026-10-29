package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumIncidentSecurity { NONE, THEFT, ROBBERY, LOSS }
enum class EnumIncidentTarget { KEEPER, DIRECTOR, COMPETENT, PROVINCE, NATIONAL, POLICE }
enum class EnumIncidentReportKind { INCIDENT, RESULT_FILING }
enum class EnumIncidentDisposition { PRESENT_SAFE, PRESENT_RESTRICTED, NOT_RECOVERED, DESTROYED }
enum class EnumIncidentRecordKind { FACTS, REPORT, HANDLING, CONFIRMATION }

data class IncidentSourceVo(val bizType: String, val bizId: String, val recordId: String? = null)
data class IncidentItemVo(
    val collectionId: String,
    val damaged: Boolean,
    val security: EnumIncidentSecurity,
    val major: Boolean,
    val findings: String,
    val imageFileIds: String? = null,
)
/** 首报可以只记录已知事实；缺少影像或尚未定级不能成为延误报案的理由。 */
data class IncidentFactsVo(
    val title: String,
    val discoveredAt: LocalDateTime,
    val occurredAt: LocalDateTime? = null,
    val location: String,
    val reporterUserId: String,
    val description: String,
    val immediateMeasures: String,
    val items: List<IncidentItemVo> = emptyList(),
    val evidenceFileIds: String? = null,
    val source: IncidentSourceVo? = null,
    val replacementReason: String? = null,
    val replacementFileIds: String? = null,
    val relatedWorkIds: Set<String> = emptySet(),
    val relatedRelocationIds: Set<String> = emptySet(),
    val relatedRestorationPlanIds: Set<String> = emptySet(),
)
data class IncidentFactItemSnapshot(val item: IncidentItemVo, val catalog: CollectionCatalogContentVo)
data class IncidentFactsSnapshot(val facts: IncidentFactsVo, val items: List<IncidentFactItemSnapshot>, val sourceEvidence: String? = null)
data class IncidentReportVo(
    val factsId: String,
    val collectionIds: Set<String> = emptySet(),
    val target: EnumIncidentTarget,
    val kind: EnumIncidentReportKind,
    val authorityName: String,
    val recipientUserId: String? = null,
    val reportNo: String,
    val reportedAt: LocalDateTime,
    val description: String,
    val fileIds: String,
    val handlingId: String? = null,
)
data class IncidentAuthorityResultVo(
    val target: EnumIncidentTarget,
    val authorityName: String,
    val collectionIds: Set<String> = emptySet(),
    val completedAt: LocalDateTime,
    val reportIds: Set<String> = emptySet(),
    val result: String,
    val fileIds: String,
)
data class IncidentItemResolutionVo(
    val collectionId: String,
    val disposition: EnumIncidentDisposition,
    val authenticityConfirmed: Boolean,
    val condition: String,
    val actualLocation: String,
    val custodianUserId: String? = null,
    val protectiveMeasures: String,
    val followUp: String,
    val fileIds: String,
)
data class IncidentHandlingVo(
    val factsId: String,
    val completedAt: LocalDateTime,
    val investigatorUserId: String,
    val cause: String,
    val actions: String,
    val investigationFileIds: String,
    val items: List<IncidentItemResolutionVo> = emptyList(),
    val authorityResults: List<IncidentAuthorityResultVo> = emptyList(),
    val keeperUserId: String,
    val leaderUserId: String,
    val archiveIndex: String,
    val signedHandlingFileIds: String,
    val replacementReason: String? = null,
    val replacementFileIds: String? = null,
)
data class IncidentConfirmationVo(val basisRevision: String, val signedAt: LocalDateTime, val opinion: String, val fileIds: String)
data class IncidentConfirmationSnapshot(val request: IncidentConfirmationVo, val actorUserId: String, val role: String, val evidenceIds: List<String>)
data class IncidentReportRequirement(val collectionId: String, val target: EnumIncidentTarget, val kind: EnumIncidentReportKind)
data class IncidentClearanceVo(val incidentId: String, val confirmedAt: LocalDateTime, val conservationUserId: String, val keeperUserId: String, val condition: String, val actualMeasures: String, val signedFileIds: String)

data class SamplingIncidentClearanceSnapshot(val record: IncidentClearanceVo, val incidentProof: Map<String, String>)
