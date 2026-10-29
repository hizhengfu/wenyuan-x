package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 当前核收清册中的每一件（套），pieceNo 为原件上能够持续对应的临时标识。 */
data class AccessionPieceVo(
    val pieceNo: String,
    val receivedAt: LocalDateTime,
    val identification: String,
    val condition: String,
    val identityMatches: Boolean?,
    val conditionMatches: Boolean?,
    val photoFileIds: String,
    val discrepancy: String? = null,
    val actualQuantity: Int? = null,
    val actualQuantityUnit: String? = null,
)

/** 累计核收的实际清册快照；未收齐和未解决差异同样留档，不算完成入藏。 */
data class AccessionReceiptVo(
    val acquisitionId: String,
    val checkedAt: LocalDateTime,
    val warehouseId: String,
    val keeperUserIds: Set<String>,
    val handoverNames: Set<String>,
    val pieces: List<AccessionPieceVo>,
    val accepted: Boolean?,
    val signedReceiptFileIds: String,
    val issueDescription: String? = null,
    val protectiveMeasures: String? = null,
    val followUpAt: LocalDateTime? = null,
    val lateReason: String? = null,
    val lateHandlingFileIds: String? = null,
    val replacementReason: String? = null,
    val correctionFileIds: String? = null,
)
