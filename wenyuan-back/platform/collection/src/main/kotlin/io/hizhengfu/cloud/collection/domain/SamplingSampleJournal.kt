package io.hizhengfu.cloud.collection.domain

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.LocalDateTime

enum class EnumSampleCustody { INTERNAL, EXTERNAL }

data class SamplingSampleCustodyVo(
    val kind: EnumSampleCustody,
    val location: String,
    val userId: String? = null,
    val externalUnit: String? = null,
    val externalPerson: String? = null,
    val expectedReturnAt: LocalDateTime? = null,
)

/** 每批消耗针对前一批剩余量；分析本身不代办人员或单位间的实物交接。 */
data class SamplingBatchAnalysisVo(
    val previousRevision: String,
    val batchNumber: String,
    val analyzedAt: LocalDateTime,
    val laboratoryName: String,
    val analystName: String,
    val recorderUserId: String,
    val methodAndResult: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val consumedQuantity: BigDecimal,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val remainingQuantity: BigDecimal,
    val remainingLocation: String?,
    val reportFileIds: String,
    val signedAnalysisAndCustodyFileIds: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val portionKey: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val analysisPlanSequence: Int? = null,
)

/** 交接整份现存样品，发出方来自已经封存的前序记录，数量不因转交而变化。 */
data class SamplingSampleHandoverVo(
    val previousRevision: String,
    val handedAt: LocalDateTime,
    val recorderUserId: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val quantity: BigDecimal,
    val destination: SamplingSampleCustodyVo,
    val condition: String,
    val purposeAndArrangements: String,
    val basisFileIds: String,
    val markingFileIds: String,
    val signedBothPartiesFileIds: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val portionKey: String? = null,
)

data class SamplingSampleEventVo(
    val sampleId: String,
    val sequence: Int,
    val from: SamplingSampleCustodyVo,
    val analysis: SamplingBatchAnalysisVo? = null,
    val handover: SamplingSampleHandoverVo? = null,
    // 新的可选类型省略空值，旧顺序原件及其摘要不因字段扩展而变化。
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val discrepancy: SamplingSampleDiscrepancyVo? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val resolution: SamplingSampleResolutionVo? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val confirmation: SamplingSampleConfirmationRecord? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val split: SamplingSampleSplitVo? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val disposition: SamplingSampleDispositionVo? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val dispositionConfirmation: SamplingSampleDispositionConfirmationRecord? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val analysisAuthorization: SamplingAnalysisAuthorizationVo? = null,
)

/** 实际查见量独立于账面剩余量，发现差异和后续核查都追加原件，不自动损耗或冲销。 */
data class SamplingSampleDiscrepancyVo(
    val previousRevision: String,
    val observedAt: LocalDateTime,
    val recorderUserId: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val observedQuantity: BigDecimal,
    val identityConfirmed: Boolean,
    val conditionSafe: Boolean,
    val observedCustody: SamplingSampleCustodyVo?,
    val findings: String,
    val immediateMeasures: String,
    val reportingAndFollowUp: String,
    val evidenceFileIds: String,
    val signedObservationFileIds: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val portionKey: String? = null,
)

data class SamplingSampleResolutionVo(
    val previousRevision: String,
    val discrepancySequences: Set<Int>,
    val reviewedAt: LocalDateTime,
    val recorderUserId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val actualQuantity: BigDecimal,
    val identityConfirmed: Boolean,
    val safeToContinue: Boolean,
    val actualCustody: SamplingSampleCustodyVo?,
    val investigationAndCause: String,
    val actualMeasures: String,
    val reportingAndHandling: String,
    val followUp: String,
    val investigationFileIds: String,
    val reportingAndHandlingFileIds: String,
    val signedReviewFileIds: String,
    val custodyHandoverFileIds: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val portionKey: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val finalAccounting: SamplingSampleFinalAccountingVo? = null,
)

enum class EnumSampleFinalAccounting { SHORTAGE, EXCESS, NON_ANALYSIS_DISPOSITION }

/** 样品差异按实际处理依据记账，不改变原藏品总账，也不表示短缺已经寻回。 */
data class SamplingSampleFinalAccountingVo(
    val kind: EnumSampleFinalAccounting,
    val decidedAt: LocalDateTime,
    val authorityAndBasis: String,
    val decisionNo: String,
    val decisionFileIds: String,
    val followUpResponsibility: String,
    val followUpFileIds: String,
)

data class SamplingSampleConfirmationVo(
    val previousRevision: String,
    val resolutionSequence: Int,
    val confirmedAt: LocalDateTime,
    val opinion: String,
    val signedFileIds: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val portionKey: String? = null,
)

data class SamplingSampleConfirmationRecord(
    val record: SamplingSampleConfirmationVo,
    val operatorUserId: String,
)

/** 分份编号只在同一原样品内标识分份，不另行生成取样或总登记号。 */
data class SamplingSplitPortionVo(
    val key: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val quantity: BigDecimal,
    val location: String,
)

data class SamplingSampleSplitVo(
    val previousRevision: String,
    val splitAt: LocalDateTime,
    val recorderUserId: String,
    val portions: List<SamplingSplitPortionVo>,
    val reasonAndMethod: String,
    val condition: String,
    val basisFileIds: String,
    val markingFileIds: String,
    val signedSplitAndCustodyFileIds: String,
    val portionKey: String? = null,
)

data class SamplingPortionStateVo(
    val key: String,
    val parentKey: String?,
    val createdAt: LocalDateTime,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val initialQuantity: BigDecimal,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val remainingQuantity: BigDecimal,
    val custody: SamplingSampleCustodyVo,
    val lastOccurredAt: LocalDateTime,
    val analysisCount: Int = 0,
    val openDiscrepancySequences: Set<Int> = emptySet(),
    val pendingResolutionSequence: Int? = null,
    val confirmedResolutionUserIds: Set<String> = emptySet(),
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val observedQuantity: BigDecimal? = null,
    val pendingDispositionSequence: Int? = null,
    val confirmedDispositionUserIds: Set<String> = emptySet(),
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val nonAnalysisDisposedQuantity: BigDecimal = BigDecimal.ZERO,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val accountedShortageQuantity: BigDecimal = BigDecimal.ZERO,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val accountedExcessQuantity: BigDecimal = BigDecimal.ZERO,
    val finalAccountingCount: Int = 0,
)

data class SamplingSampleJournalVo(
    val revision: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val remainingQuantity: BigDecimal,
    val unit: EnumSamplingUnit,
    val custody: SamplingSampleCustodyVo,
    val lastOccurredAt: LocalDateTime,
    val analysisCount: Int,
    val events: List<SamplingSampleEventVo>,
    val openDiscrepancySequences: Set<Int> = emptySet(),
    val pendingResolutionSequence: Int? = null,
    val confirmedResolutionUserIds: Set<String> = emptySet(),
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val observedQuantity: BigDecimal? = null,
    val portions: Map<String, SamplingPortionStateVo> = emptyMap(),
    val usedPortionKeys: Set<String> = emptySet(),
    val pendingDispositionSequence: Int? = null,
    val confirmedDispositionUserIds: Set<String> = emptySet(),
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val nonAnalysisDisposedQuantity: BigDecimal = BigDecimal.ZERO,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val accountedShortageQuantity: BigDecimal = BigDecimal.ZERO,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val accountedExcessQuantity: BigDecimal = BigDecimal.ZERO,
    val finalAccountingCount: Int = 0,
) {
    val dispositionPending: Boolean
        get() = pendingDispositionSequence != null || portions.values.any { it.pendingDispositionSequence != null }
    val readyForCompletion: Boolean
        get() = openDiscrepancySequences.isEmpty() && !dispositionPending &&
            (analysisCount > 0 || remainingQuantity.compareTo(BigDecimal.ZERO) == 0 && (nonAnalysisDisposedQuantity > BigDecimal.ZERO || accountedShortageQuantity > BigDecimal.ZERO)) &&
            (if (portions.isEmpty()) remainingQuantity.compareTo(BigDecimal.ZERO) == 0 || custody.kind == EnumSampleCustody.INTERNAL
            else portions.values.all { it.remainingQuantity.compareTo(BigDecimal.ZERO) == 0 || it.custody.kind == EnumSampleCustody.INTERNAL })
}

/** 已发生的非分析处置据实记录，不授予处置许可；两人核实前保留原账面数量。 */
data class SamplingSampleDispositionVo(
    val previousRevision: String,
    val disposedAt: LocalDateTime,
    val recorderUserId: String,
    val conservationUserId: String,
    val keeperUserId: String,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val disposedQuantity: BigDecimal,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val remainingQuantity: BigDecimal,
    val remainingLocation: String?,
    val methodAndReason: String,
    val authorityAndBasis: String,
    val actualResultAndDestination: String,
    val remainingConditionAndArrangements: String,
    val authorizationFileIds: String,
    val quantityAndResultFileIds: String,
    val signedDispositionFileIds: String,
    val portionKey: String? = null,
)

data class SamplingSampleDispositionConfirmationVo(
    val previousRevision: String,
    val dispositionSequence: Int,
    val confirmedAt: LocalDateTime,
    val opinion: String,
    val signedFileIds: String,
    val portionKey: String? = null,
)

data class SamplingSampleDispositionConfirmationRecord(
    val record: SamplingSampleDispositionConfirmationVo,
    val operatorUserId: String,
)

data class SamplingCompletionReviewVo(
    val completion: SamplingCompletionVo,
    val sampleRevisions: Map<String, String>,
)
