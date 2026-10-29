package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 现场非损伤性防护；不包含送修、取样、复制或改变存放场所。 */
enum class EnumIncidentWorkMethod { SUPPORT, PACKAGING, FIXATION, ISOLATION, ENVIRONMENT_SHIELDING }
enum class EnumIncidentWorkState { PLAN, APPROVED, STARTED, ENDED, COMPLETED, CANCELED }
enum class EnumIncidentWorkRecordKind { PLAN, CONFIRMATION, STARTED, OBSERVATION, ENDED, CANCELED }
enum class EnumIncidentWorkStage { PLAN, END }

data class IncidentWorkPlanVo(
    val collectionId: String,
    val incidentIds: Set<String>,
    val preparedAt: LocalDateTime,
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val location: String,
    val conditionBefore: String,
    val methods: Set<EnumIncidentWorkMethod>,
    val necessity: String,
    val interventionBoundary: String,
    val procedure: String,
    val safetyRequirements: String,
    val stopConditions: String,
    val departmentId: String,
    val conservationUserId: String,
    val departmentHeadUserId: String,
    val keeperUserId: String,
    val leaderUserId: String,
    val securityUserId: String,
    val siteAndAssignmentFileIds: String,
    val signedPlanFileIds: String,
)
data class IncidentWorkPlanSnapshot(val plan: IncidentWorkPlanVo, val caseFacts: Map<String, String>, val originalContext: String)
data class IncidentWorkConfirmationVo(val stage: EnumIncidentWorkStage, val basisRevision: String, val signedAt: LocalDateTime, val opinion: String, val fileIds: String)
data class IncidentWorkConfirmationSnapshot(val content: IncidentWorkConfirmationVo, val actorUserId: String, val role: String)
data class IncidentWorkStartVo(
    val startedAt: LocalDateTime,
    val location: String,
    val conservationUserId: String,
    val custodianUserId: String,
    val securityUserId: String,
    val physicallyPresent: Boolean,
    val siteSafe: Boolean,
    val noRelocation: Boolean,
    val noRestorationOrSampling: Boolean,
    val condition: String,
    val actualProtection: String,
    val signedStartFileIds: String,
)
data class IncidentWorkObservationVo(val observedAt: LocalDateTime, val observerUserId: String, val condition: String, val unsafe: Boolean, val unexpectedDamage: Boolean, val actualMeasures: String, val fileIds: String)
data class IncidentWorkEndVo(
    val endedAt: LocalDateTime,
    val workPerformed: Boolean,
    val actualMeasures: String,
    val conditionAfter: String,
    val withinApprovedScope: Boolean,
    val unexpectedDamage: Boolean,
    val stopReason: String,
    val followUp: String,
    val signedEndFileIds: String,
)
data class IncidentWorkCancelVo(val canceledAt: LocalDateTime, val reason: String, val fileIds: String)
