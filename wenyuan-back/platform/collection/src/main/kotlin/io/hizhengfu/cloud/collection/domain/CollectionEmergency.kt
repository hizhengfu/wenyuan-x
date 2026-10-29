package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumEmergencyScenario { FIRE, FLOOD, EARTHQUAKE, THEFT, ROBBERY, LOSS, DAMAGE, ENVIRONMENT, OTHER }
enum class EnumEmergencyDrillMode { TABLETOP, FIELD }
data class EmergencyScenarioPlanVo(val scenario:EnumEmergencyScenario,val objectives:String,val drillIntervalDays:Int,val firstDueAt:LocalDateTime)
/** 实际印发原件；系统核对日期不冒充当年的印发或批准日期。 */
data class CollectionEmergencyPlanVo(
    val previousPlanId:String?=null,val title:String,val documentNo:String,
    val issuedAt:LocalDateTime,val effectiveAt:LocalDateTime,
    val keeperUserId:String,val securityUserId:String,val directorUserId:String,
    val commandAndDuties:String,val warningAndActivation:String,val emergencyContacts:String,
    val siteProtectionAndTransfer:String,val resourcesAndSupport:String,
    val firstGradeDamageReporting:String,val otherRelicDamageReporting:String,val theftRobberyLossReporting:String,
    val recoveryAndReview:String,val scenarios:List<EmergencyScenarioPlanVo>,val originalFileIds:String,
    val replacementReason:String?=null,val replacementFileIds:String?=null,
)
data class EmergencyDrillIssueVo(val title:String,val findings:String,val ownerUserId:String,val dueAt:LocalDateTime,val requiredActions:String,val requiresPlanRevision:Boolean)
/** 仅记录真实演练；涉及实物的原业务和真实事故分别办理。 */
data class CollectionEmergencyDrillVo(
    val planId:String,val title:String,val mode:EnumEmergencyDrillMode,
    val startedAt:LocalDateTime,val endedAt:LocalDateTime,val scenarios:Set<EnumEmergencyScenario>,
    val conductorUserId:String,val evaluatorUserId:String,val participantUserIds:Set<String>,val externalParticipants:String?=null,
    val actualLocation:String,val actualActions:String,val communicationRehearsal:String,val actualOutcome:String,
    val involvesOriginals:Boolean,val originalUseBasisVerified:Boolean?=null,val originalOperations:String?=null,val originalOperationFileIds:String?=null,val requisitionIds:Set<String> = emptySet(),
    val hasIssues:Boolean,val issues:List<EmergencyDrillIssueVo> = emptyList(),
    val hasRealIncident:Boolean,val actualIncidentIds:Set<String> = emptySet(),val attendanceFileIds:String,val signedReportFileIds:String,
)
data class EmergencyImprovementVo(
    val issueNumber:Int,val previousId:String?=null,val completedAt:LocalDateTime,val ownerUserId:String,val verifierUserId:String,
    val actualActions:String,val result:String,val fileIds:String,val revisedPlanId:String?=null,val replacementReason:String?=null,
)
data class EmergencyImprovementReviewVo(val accepted:Boolean,val opinion:String,val fileIds:String)
data class EmergencyImprovementReview(val actorUserId:String,val confirmedAt:LocalDateTime,val record:EmergencyImprovementReviewVo)

/** 同一次演练追加订正；旧问题顺序保留，新发现只能追加，不能借订正删除处置义务。 */
data class EmergencyDrillRevisionVo(val expectedReportId:String,val report:CollectionEmergencyDrillVo,val reason:String,val fileIds:String)
data class EmergencyDrillRevisionRecord(val request:EmergencyDrillRevisionVo,val submittedBy:String,val submittedAt:LocalDateTime)
