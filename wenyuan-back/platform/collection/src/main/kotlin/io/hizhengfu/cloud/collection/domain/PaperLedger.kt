package io.hizhengfu.cloud.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class EnumPaperLedgerCondition { GOOD, ISSUE }
data class PaperLedgerCustodyVo(
    val checkedAt: LocalDateTime, val keeperUserId: String, val storageLocation: String,
    val condition: EnumPaperLedgerCondition, val conditionDescription: String,
    val signedCustodyFileIds: String,
    val fromKeeperUserId: String? = null, val handoverFileIds: String? = null,
    val reason: String? = null, val handlingDescription: String? = null,
    val handlingFileIds: String? = null, val nextCheckOn: LocalDate? = null,
    val correctedMetadata: PaperLedgerMetadataVo? = null, val metadataCorrectionFileIds: String? = null,
)
data class PaperLedgerMetadataVo(
    val bookNo: String, val title: String, val openedOn: LocalDate, val pageCount: Int,
    val coverageDescription: String, val contentsFileIds: String,
)
data class PaperLedgerBookVo(
    val bookNo: String, val title: String, val openedOn: LocalDate, val pageCount: Int,
    val coverageDescription: String, val contentsFileIds: String, val custody: PaperLedgerCustodyVo,
)
data class LedgerAccountEntryVo(val totalRegNo: String, val name: String, val quantity: Long, val unit: String)
data class LedgerAccountComparisonVo(
    val lineNo: Int, val bookId: String, val paperPageRef: String,
    val financialRecordRef: String? = null, val collectionId: String? = null,
    val paper: LedgerAccountEntryVo? = null, val financial: LedgerAccountEntryVo? = null,
    val issueExplanation: String? = null, val plannedAction: String? = null,
)
data class LedgerReconciliationVo(
    val referenceNo: String, val periodFrom: LocalDate, val periodTo: LocalDate,
    val checkedAt: LocalDateTime, val ledgerKeeperUserId: String, val financeUserId: String,
    val scopeDescription: String, val bookIds: Set<String>,
    val signedReportFileIds: String, val paperScheduleFileIds: String, val financialScheduleFileIds: String,
    val nextCheckOn: LocalDate, val rows: List<LedgerAccountComparisonVo> = emptyList(),
    val emptyScopeReason: String? = null,
    val replacementReason: String? = null, val replacementFileIds: String? = null,
    val omittedLineReasons: Map<Int, String> = emptyMap(),
)
data class LedgerSystemReferenceVo(
    val lineNo: Int, val collectionId: String, val source: String,
    val capturedAt: LocalDateTime, val item: LedgerSystemEntryVo,
)
data class LedgerSystemEntryVo(val totalRegNo: String?, val name: String?, val quantity: Long?, val unit: String?)
data class LedgerBookReferencesVo(val items: Map<String, PaperLedgerMetadataVo> = emptyMap())

/** Compare only the two actual signed schedules, never infer past values from the current master. */
object LedgerAccountComparison {
    fun differences(row: LedgerAccountComparisonVo): List<String> {
        val result = mutableListOf<String>()
        val paper = row.paper; val financial = row.financial
        if (paper == null) result += "纸质总账缺对应记录"
        if (financial == null) result += "财务账缺对应记录"
        if (paper != null && financial != null) {
            if (paper.totalRegNo != financial.totalRegNo) result += "总登记号不一致"
            if (paper.name != financial.name) result += "名称不一致"
            if (paper.quantity != financial.quantity) result += "数量不一致"
            if (paper.unit != financial.unit) result += "计量单位不一致"
        }
        if (listOfNotNull(paper, financial).any { it.quantity < 0 }) result += "账面数量为负，须核实"
        return result
    }
}
