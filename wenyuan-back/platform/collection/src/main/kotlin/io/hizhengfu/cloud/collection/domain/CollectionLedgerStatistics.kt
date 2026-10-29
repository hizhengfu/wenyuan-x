package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 正式登记账的历史事实；物理出入库次数独立统计。 */
enum class EnumLedgerStatEvent { REGISTER, TRANSFER_IN, TRANSFER_OUT, DEREGISTER, CHANGE }
data class LedgerStatItemVo(
    val collectionId: String, val totalRegNo: String, val name: String,
    val nature: EnumCollectionNature?, val level: EnumRelicLevel, val categoryId: String?,
    val source: EnumSource?, val quantity: Long, val unit: String,
    val actualQuantity: Long? = null, val actualQuantityUnit: String? = null,
    val categoryName: String? = null, val changeReason: EnumLedgerChangeReason? = null,
)
data class AnnualUnitBalanceVo(
    val unit: String,
    val opening: Long=0, val added: Long=0, val transferIn: Long=0,
    val transferOut: Long=0, val deregistered: Long=0, val adjustment: Long=0, val closing: Long=0,
)
data class AnnualLedgerIssueVo(val collectionId: String, val totalRegNo: String, val description: String)
data class AnnualLedgerEventVo(
    val id: String, val kind: EnumLedgerStatEvent, val occurredAt: LocalDateTime,
    val item: LedgerStatItemVo, val sourceId: String, val recordedAt: LocalDateTime, val reason: String? = null,
)
data class AnnualLedgerPreviewVo(
    val orgId: String, val year: Int, val cutoff: LocalDateTime, val completeYear: Boolean,
    val openingTotal: Int=0, val newTotal: Int=0, val transferInTotal: Int=0,
    val transferOutTotal: Int=0, val deregTotal: Int=0, val closingTotal: Int=0,
    val quantityBalances: List<AnnualUnitBalanceVo> = emptyList(),
    val closingByLevel: Map<String,Int> = emptyMap(), val closingByCategory: Map<String,Int> = emptyMap(),
    val issues: List<AnnualLedgerIssueVo> = emptyList(),
    val events: List<AnnualLedgerEventVo> = emptyList(),
    val actualQuantityBalances: List<AnnualUnitBalanceVo> = emptyList(),
    val unknownActualQuantityCount: Int? = null,
    val categoryReasonBalances: List<CategoryReasonBalanceVo> = emptyList(),
)

/** 任意期间仍使用正式总账事件，临时提用出入库另列。结束日期为包含当日。 */
data class PeriodLedgerPreviewVo(
    val orgId: String, val startDate: java.time.LocalDate, val endDate: java.time.LocalDate,
    val cutoff: LocalDateTime, val completePeriod: Boolean,
    val openingTotal: Int, val newTotal: Int, val transferInTotal: Int, val transferOutTotal: Int,
    val deregTotal: Int, val closingTotal: Int,
    val quantityBalances: List<AnnualUnitBalanceVo> = emptyList(), val actualQuantityBalances: List<AnnualUnitBalanceVo> = emptyList(),
    val unknownActualQuantityCount: Int,
    val closingByLevel: Map<String,Int> = emptyMap(), val closingByCategory: Map<String,Int> = emptyMap(),
    val issues: List<AnnualLedgerIssueVo> = emptyList(), val events: List<AnnualLedgerEventVo> = emptyList(),
    val categoryReasonBalances: List<CategoryReasonBalanceVo> = emptyList(),
)

/** WW/T 0018 附录A；未知原因单列，不依据自由文本猜测。 */
enum class EnumLedgerChangeReason { HANDOVER, TRANSFER_IN, DONATE, EXCHANGE, OTHER_IN, DAMAGE, TRANSFER_OUT, OTHER_OUT }
data class CategoryReasonBalanceVo(
    val categoryId: String?, val categoryName: String?, val unit: String,
    val opening: Long, val handover: Long, val transferIn: Long, val donated: Long, val exchanged: Long, val otherIn: Long,
    val unknownIn: Long, val increaseTotal: Long, val damaged: Long, val transferOut: Long, val otherOut: Long,
    val unknownOut: Long, val decreaseTotal: Long, val adjustment: Long, val closing: Long,
)
