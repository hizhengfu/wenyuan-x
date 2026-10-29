package io.hizhengfu.cloud.collection.domain

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

enum class EnumStaffEducationKind { POST_TRAINING, CONTINUING_EDUCATION }
enum class EnumStaffEducationOutcome { ATTENDED, COMPLETED, NOT_COMPLETED }

/** 岗位需求与期限由馆方真实依据确定，不预设法定学时、频次或证书有效期。 */
data class StaffEducationPlan(
    val kind: EnumStaffEducationKind,
    val title: String,
    val dutyScope: String,
    val skillsNeeded: String,
    val identifiedAt: LocalDateTime,
    val needsFileIds: String,
    val dueAt: LocalDateTime? = null,
    val dueBasisFileIds: String? = null,
)
data class StaffEducationParticipation(
    val provider: String,
    val courseTitle: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val actualMinutes: Int? = null,
    val skillsCovered: String,
    val outcome: EnumStaffEducationOutcome,
    val evidenceFileIds: String,
    val assessment: String? = null,
)
data class StaffEducationWithdrawal(val reason: String, val fileIds: String)
@JsonInclude(value=JsonInclude.Include.ALWAYS,content=JsonInclude.Include.ALWAYS)
data class StaffEducationContent(
    val plan: StaffEducationPlan,
    val participation: StaffEducationParticipation? = null,
    val withdrawal: StaffEducationWithdrawal? = null,
)
data class StaffEducationCreate(
    val requestKey: String,
    val userId: String,
    val historicalPerson: Boolean = false,
    val employmentFileIds: String,
    val plan: StaffEducationPlan,
)
data class StaffEducationRevision(
    val expectedVersion: Int,
    val content: StaffEducationContent,
    val reason: String,
    val fileIds: String,
)
data class StaffEducationConfirmationVo(val meetsNeed: Boolean, val opinion: String, val fileIds: String)
data class StaffEducationConfirmation(val actorUserId: String, val confirmedAt: LocalDateTime, val content: StaffEducationConfirmationVo)
data class StaffEducationOriginal(val id: String, val orgId: String, val personName: String, val request: StaffEducationCreate, val registeredBy: String, val registeredAt: LocalDateTime)
@JsonInclude(value=JsonInclude.Include.ALWAYS,content=JsonInclude.Include.ALWAYS)
data class StaffEducationVersionRecord(
    val educationId: String,
    val version: Int,
    val content: StaffEducationContent,
    val previousDigest: String?,
    val reason: String?,
    val fileIds: String?,
    val registeredBy: String,
    val registeredAt: LocalDateTime,
)
