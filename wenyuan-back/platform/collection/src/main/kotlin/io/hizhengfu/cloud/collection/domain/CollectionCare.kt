package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumCareMethod { DUST_REMOVAL, PEST_CONTROL, DISINFECTION, SUPPORT, PACKAGING, FIXING }

/** 馆方按本件实际风险制定频次和方法；无全国统一养护周期的默认值。 */
data class CollectionCarePlanVo(
    val previousPlanId: String? = null,
    val sourceRevision: String,
    val preparedAt: LocalDateTime,
    val conservationUserId: String,
    val keeperUserId: String,
    val inspectionIntervalDays: Int,
    val careIntervalDays: Int,
    val methods: Set<EnumCareMethod> = emptySet(),
    val instructions: String,
    val environmentRequirements: String,
    val storageProtection: String,
    val safetyInstructions: String,
    val planFileIds: String,
)
data class CollectionCareSignatureVo(val opinion: String, val fileIds: String)
data class CollectionCareSignature(val actorUserId: String, val confirmedAt: LocalDateTime, val record: CollectionCareSignatureVo)
data class CollectionCareCheckVo(
    val planId: String? = null,
    val checkedAt: LocalDateTime,
    val operatorUserId: String,
    val actualLocation: String,
    val physicallyPresent: Boolean,
    val custodianUserId: String? = null,
    val condition: String,
    val diseases: String,
    val environmentObservation: String,
    val damaged: Boolean,
    val safeForRoutineCare: Boolean,
    val actualMeasures: String,
    val followUp: String,
    val conditionFileIds: String,
    val signedFileIds: String,
)
data class CollectionCareStartVo(
    val planId: String,
    val checkId: String,
    val startedAt: LocalDateTime,
    val operatorUserId: String,
    val methods: Set<EnumCareMethod> = emptySet(),
    val requisitionId: String? = null,
    val onSiteWarehouseLogId: String? = null,
    val actualLocation: String,
    val safetyChecks: String,
    val signedFileIds: String,
)
data class CollectionCareEndVo(
    val endedAt: LocalDateTime,
    val completed: Boolean,
    val actualMethods: Set<EnumCareMethod> = emptySet(),
    val actualWork: String,
    val condition: String,
    val diseases: String,
    val unexpectedDamage: Boolean,
    val safeToRelease: Boolean,
    val physicallyPresent: Boolean,
    val actualLocation: String,
    val custodianUserId: String? = null,
    val actualMeasures: String,
    val followUp: String,
    val conditionFileIds: String,
    val signedFileIds: String,
)

data class CollectionCareClearanceSnapshot(val record: IncidentClearanceVo, val incidentProof: Map<String,String>, val version: Int = 1)
