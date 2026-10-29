package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumEmergencyDutyTarget { PLAN, DRILL, ISSUE, IMPROVEMENT }
enum class EnumEmergencyDutyRole { KEEPER, SECURITY, DIRECTOR, RECORDER, EVALUATOR, OWNER, REVIEWER }
enum class EnumEmergencyDutyQualification { MEMBER, KEEPER, SECURITY, DIRECTOR }
data class EmergencyDutyTarget(val type:EnumEmergencyDutyTarget,val targetId:String,val role:EnumEmergencyDutyRole,val issueNumber:Int=0)
/** 只指定原件核对和后续办理职责，不改写实际参加人员或既有签署。 */
data class EmergencyDutyAppointmentVo(
    val target:EmergencyDutyTarget,val expectedReportId:String?=null,val previousId:String?=null,
    val toUserId:String,val qualification:EnumEmergencyDutyQualification,
    val appointedAt:LocalDateTime,val effectiveAt:LocalDateTime,val reason:String,val fileIds:String,
    val predecessorUnavailable:Boolean,val unavailableReason:String?=null,val unavailableFileIds:String?=null,
)
data class EmergencyDutyAppointment(
    val request:EmergencyDutyAppointmentVo,val originalUserId:String,val fromUserId:String,
    val issuerUserId:String,val submittedAt:LocalDateTime,
)
