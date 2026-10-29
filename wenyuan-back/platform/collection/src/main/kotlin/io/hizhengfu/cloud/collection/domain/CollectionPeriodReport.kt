package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class PeriodUsageItemVo(
    val movementId:String, val businessId:String, val collectionId:String, val occurredAt:LocalDateTime,
    val scope:String, val reason:EnumMoveReason?, val categoryId:String?, val categoryName:String?,
    val quantity:Long?, val unit:String?, val actualQuantity:Long?, val actualUnit:String?,
)
data class PeriodUsageRowVo(
    val scope:String, val reason:EnumMoveReason?, val categoryId:String?, val categoryName:String?,
    val useCount:Int, val collectionOccurrences:Int, val ledgerQuantities:Map<String,Long> = emptyMap(),
    val actualQuantities:Map<String,Long> = emptyMap(), val unknownQuantityCount:Int,
)
data class CollectionPeriodReportVo(
    val ledger:PeriodLedgerPreviewVo, val usage:List<PeriodUsageRowVo> = emptyList(), val usageItems:List<PeriodUsageItemVo> = emptyList(),
    val useCount:Int, val collectionOccurrences:Int, val usageIssues:List<String> = emptyList(), val digest:String,
)
data class CollectionPeriodReportSignVo(
    val orgId:String, val startDate:LocalDate, val endDate:LocalDate, val digest:String,
    val statisticianUserId:String, val keeperUserId:String, val leaderUserId:String,
    val signedAt:LocalDateTime, val signedReportFileIds:String,
    val reconciliationNote:String?=null, val reconciliationFileIds:String?=null,
    val previousId:String?=null, val replacementReason:String?=null, val replacementFileIds:String?=null,
)
data class CollectionPeriodReportArchiveVo(
    val id:String, val version:Int, val signed:CollectionPeriodReportSignVo, val calculated:CollectionPeriodReportVo,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val evidenceIssue:String?=null,
)
