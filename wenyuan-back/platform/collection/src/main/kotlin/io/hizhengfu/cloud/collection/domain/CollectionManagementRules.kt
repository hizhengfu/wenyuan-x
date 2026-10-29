package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class EnumCollectionRuleTopic {
    ACQUISITION, IDENTIFICATION, GRADING, GENERAL_LEDGER, ARCHIVES, WAREHOUSE,
    MOVEMENT, INVENTORY, EXIT, RESTORATION, REPRODUCTION, RUBBING,
}

/** A real institution-issued compilation; coverage is checked against its originals. */
data class CollectionManagementRulesVo(
    val title: String,
    val documentNo: String,
    val issuedAt: LocalDateTime,
    val effectiveOn: LocalDate,
    val responsibleUserId: String,
    val originalFileIds: String,
    val workflowFileIds: String,
    val topicReferences: Map<EnumCollectionRuleTopic, String> = emptyMap(),
    val replacementReason: String? = null,
    val replacementFileIds: String? = null,
)

data class CollectionRulesSubmissionVo(
    val authorityName: String, val reportNo: String,
    val submittedAt: LocalDateTime, val submissionFileIds: String,
)

data class CollectionRulesFilingVo(
    val authorityName: String, val filingNo: String,
    val filedAt: LocalDateTime, val receiptFileIds: String,
)
