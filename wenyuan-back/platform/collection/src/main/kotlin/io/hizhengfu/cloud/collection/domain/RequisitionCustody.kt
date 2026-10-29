package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 已实际提用后的责任承接；原审批、点交、已完成检查和签署均不改写。 */
data class RequisitionCustodyRequest(
    val previousId: String? = null,
    val expectedEffectiveId: String? = null,
    val sourceRevision: String,
    val collectionIds: Set<String>,
    val checkIds: Set<String>,
    val handedAt: LocalDateTime,
    val reason: String,
    val actualHandover: String,
    val outstandingMatters: String,
    val keeperUserId: String,
    val useUserId: String,
    val directorUserId: String,
    val appointmentFileIds: String,
    val handoverFileIds: String,
)

data class RequisitionCustodyRecord(
    val id: String,
    val requisitionId: String,
    val orgId: String,
    val request: RequisitionCustodyRequest,
    val sourceSnapshot: String,
    val outboundArchiveId: String,
    val outboundDigest: String,
    val checksSnapshot: String,
    val registeredBy: String,
    val registeredAt: LocalDateTime,
)
