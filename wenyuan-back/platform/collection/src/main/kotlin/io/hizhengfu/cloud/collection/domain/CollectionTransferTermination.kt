package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 已批准但尚未实际交接的方案另行终止；原流程批准结论不改写。 */
enum class EnumTransferTerminationRecord {
    PROPOSAL, SOURCE_KEEPER, SOURCE_LEADER, TARGET_KEEPER, TARGET_LEADER, CLOSED,
}

data class CollectionTransferTerminationProposalVo(
    val proposedAt: LocalDateTime,
    val proposerUserId: String,
    val reason: String,
    val authorityDocumentNo: String,
    val authorityFileIds: String,
    val custodyStatement: String,
    val custodyFileIds: String,
)

data class CollectionTransferTerminationConfirmationVo(
    val proposalRecordId: String,
    val nodeKey: String,
    val confirmedAt: LocalDateTime,
    val confirmerUserId: String,
    val opinion: String,
    val signedFileIds: String,
)
