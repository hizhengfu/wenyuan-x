package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumIncidentRelocationMode { PLANNED, EMERGENCY_RECORD }
enum class EnumIncidentRelocationState { PLAN, APPROVED, DEPARTED, RECEIVED, COMPLETED, CANCELED }
enum class EnumIncidentRelocationKind { PLAN, EMERGENCY, CONFIRMATION, DEPARTURE, OBSERVATION, RECEIPT, CANCELED }

data class IncidentRelocationPlanVo(
    val collectionId: String, val incidentIds: Set<String>, val preparedAt: LocalDateTime,
    val plannedDeparture: LocalDateTime, val plannedArrival: LocalDateTime,
    val fromLocationId: String, val toLocationId: String,
    val necessity: String, val conditionBefore: String, val handlingMethod: String,
    val packingMethod: String, val route: String, val safetyRequirements: String, val stopConditions: String,
    val destinationCheckedAt: LocalDateTime, val destinationSafe: Boolean,
    val departmentId: String, val conservationUserId: String, val departmentHeadUserId: String,
    val keeperUserId: String, val leaderUserId: String,
    val fromCustodianUserId: String, val toCustodianUserId: String, val securityUserId: String,
    val carrierName: String, val transportSupervisorUserId: String,
    val sourceLocationFileIds: String, val destinationSafetyFileIds: String,
    val carrierCapabilityFileIds: String, val signedPlanFileIds: String,
    val transportStandardReference: String? = null,
    val transportStandardApplicability: String? = null,
)
data class IncidentRelocationPlanSnapshot(
    val plan: IncidentRelocationPlanVo, val caseFacts: Map<String,String>,
    val originalContext: String, val fromLocation: String, val toLocation: String,
)
data class IncidentRelocationDepartureVo(
    val departedAt: LocalDateTime, val fromLocationId: String,
    val releasingUserIds: Set<String> = emptySet(), val transportSupervisorUserId: String = "",
    val physicallyPresent: Boolean, val routeSafe: Boolean,
    val condition: String, val actualPackaging: String,
    val accessRecordFileIds: String, val signedFileIds: String,
    val appliedTransportStandardReference: String? = null,
    val packingStandardConfirmed: Boolean? = null,
    val packingCheckFileIds: String? = null,
)
data class IncidentRelocationReceiptVo(
    val receivedAt: LocalDateTime, val toLocationId: String,
    val receivingUserIds: Set<String> = emptySet(), val custodianUserId: String = "",
    val physicallyPresent: Boolean, val authenticityConfirmed: Boolean,
    val conditionMatchesDeparture: Boolean, val unexpectedDamage: Boolean, val safeForCustody: Boolean,
    val condition: String, val actualMeasures: String, val followUp: String,
    val accessRecordFileIds: String, val signedFileIds: String,
)
/** 真实紧急搬移补记，明确没有由本流程事先批准；不构造方案或事前签署。 */
data class IncidentEmergencyRelocationVo(
    val collectionId: String, val incidentIds: Set<String>, val reason: String,
    val actualInstruction: String, val instructionFileIds: String,
    val conservationUserId: String, val keeperUserId: String,
    val departure: IncidentRelocationDepartureVo, val receipt: IncidentRelocationReceiptVo? = null,
)
data class IncidentEmergencyRelocationSnapshot(
    val event: IncidentEmergencyRelocationVo, val caseFacts: Map<String,String>,
    val contextAtRecording: String, val updatesCurrentLocation: Boolean,
)

data class IncidentRelocationReceiptSnapshot(val receipt: IncidentRelocationReceiptVo, val updatesCurrentLocation: Boolean, val previousLocationId: String?)
