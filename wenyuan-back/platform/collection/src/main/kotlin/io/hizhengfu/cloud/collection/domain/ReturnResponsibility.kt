package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 实际接收后未结异常的责任承接，不修改原归还签署。 */
data class ReturnResponsibilityVo(
    val previousId: String? = null,
    val expectedEffectiveId: String? = null,
    val receivedDigest: String,
    val collectionIds: Set<String>,
    val handedAt: LocalDateTime,
    val keeperUserId: String,
    val directorUserId: String,
    val reason: String,
    val actualInventory: String,
    val outstandingMatters: String,
    val appointmentFileIds: String,
    val handoverFileIds: String,
)
data class ReturnResponsibilityRecord(
    val id: String,
    val returnId: String,
    val orgId: String,
    val receivedArchiveId: String,
    val request: ReturnResponsibilityVo,
    val registeredBy: String,
    val registeredAt: LocalDateTime,
)
