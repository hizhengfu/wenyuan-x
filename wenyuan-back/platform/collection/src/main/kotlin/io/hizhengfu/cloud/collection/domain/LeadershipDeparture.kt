package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

enum class EnumLeadershipPost { LEGAL_REPRESENTATIVE, PRINCIPAL, BOTH }
data class LeadershipDepartureRequest(
    val post: EnumLeadershipPost,
    val leavingUserId: String,
    val appointedAt: LocalDateTime,
    val leavingAt: LocalDateTime,
    val appointmentFileIds: String,
    val departureFileIds: String,
    val successorUserId: String,
    val successorAppointmentFileIds: String,
    val keeperUserId: String,
    val responsibilityStatement: String,
    val responsibilityFileIds: String,
    val previousId: String? = null,
    val replacementReason: String? = null,
)
data class LeadershipDepartureRecord(
    val id: String,
    val request: LeadershipDepartureRequest,
    val leavingUserName: String,
    val successorUserName: String,
    val ownership: EnumOwnership,
    val registeredBy: String,
    val registeredAt: LocalDateTime,
)
data class LeadershipDepartureCompletionReview(
    val inventoryArchiveId: String,
    val inventoryArchiveDigest: String,
    val signature: CollectionCareSignature,
)
