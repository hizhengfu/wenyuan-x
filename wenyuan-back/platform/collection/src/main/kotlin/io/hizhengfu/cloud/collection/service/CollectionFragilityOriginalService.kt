package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Resolve only a file in a verified fragility record belonging to the requested museum and collection. */
@Component
@Transactional(readOnly = true)
class CollectionFragilityOriginalService(
    private val scope: CollectionScopeResolver,
    private val archives: CollectionPermanentArchiveService,
) {
    fun requireArchive(collectionId: String, orgId: String, recordId: String, phase: String, fileId: String): String {
        CollectionCurrentOperationAccess.require(scope, orgId, false, setOf(CollectionConst.CARE), "材质脆弱性与使用保护历史")
        val assessment = CollectionFragilityAssessmentDao.findById(recordId)?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
        val plan = CollectionFragileUsePlanDao.findById(recordId)?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
        val use = CollectionFragileUseRecordDao.findById(recordId)?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
        val monitoring = CollectionFragileMonitoringDao.findById(recordId)?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
        val automatic = CollectionFragileAutomaticCheckDao.findById(recordId)?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
        val adjustment = CollectionFragileUseAdjustmentDao.findById(recordId)?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
        val expectedFiles = when {
            assessment != null -> {
                CollectionFragilityEvidence.assessment(assessment)
                when (phase) {
                    "ASSESSMENT" -> "${assessment.content.assessmentFileIds},${assessment.content.historyReviewFileIds}"
                    "CONSERVATION" -> assessment.conservation?.record?.fileIds
                    "KEEPER" -> assessment.keeper?.record?.fileIds
                    else -> null
                }
            }
            plan != null -> {
                CollectionFragilityEvidence.plan(plan)
                when (phase) {
                    "USE_PLAN" -> (listOf(plan.content.signedPlanFileIds) + plan.content.environmentSources.map { it.qualificationFileIds }).joinToString(",")
                    "CONSERVATION" -> plan.conservation?.record?.fileIds
                    "KEEPER" -> plan.keeper?.record?.fileIds
                    else -> null
                }
            }
            use != null -> {
                CollectionFragilityEvidence.record(use)
                when (phase) {
                    "HISTORY" -> use.history?.fileIds
                    "ENVIRONMENT_VERIFIED" -> use.started?.takeIf { use.environmentArchiveId != null }?.let {
                        (listOf(it.signedFileIds) + it.environmentReadings.map { reading -> reading.signedFileIds }).joinToString(",")
                    }
                    "STARTED" -> use.started?.signedFileIds
                    "ENDED" -> use.ended?.signedFileIds
                    "REVIEWED" -> use.review?.signedFileIds
                    else -> null
                }
            }
            monitoring != null -> {
                CollectionFragileMonitoringEvidence.verify(monitoring)
                when (phase) {
                    "MONITORED" -> (listOf(monitoring.request.signedFileIds) + monitoring.request.readings.map { it.signedFileIds }).joinToString(",")
                    else -> CollectionFragileMonitoringEvidence.clearances(monitoring)
                        .firstOrNull { phase == "MONITOR_CLEARED_${it.version}" }?.record?.signedFileIds
                }
            }
            automatic != null -> {
                CollectionFragileAutomaticEvidence.verify(automatic)
                CollectionFragileAutomaticEvidence.clearances(automatic)
                    .firstOrNull { phase == "AUTO_CLEARED_${it.version}" }?.record?.signedFileIds
            }
            adjustment != null -> {
                CollectionFragileAdjustmentEvidence.verify(adjustment)
                when (phase) {
                    "ADJUSTMENT" -> {
                        val check = CollectionCareCheckDao.findById(adjustment.content.checkId)
                            ?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
                            ?: throw BadRequestException("变更核查缺少本馆本件现状检查原件")
                        CollectionCareEvidence.check(check)
                        listOfNotNull(adjustment.content.signedFileIds, adjustment.content.borrowerAgreementFileIds,
                            "${check.content.conditionFileIds},${check.content.signedFileIds}").joinToString(",")
                    }
                    "ADJUSTMENT_WITHDRAWN" -> adjustment.withdrawal?.signedFileIds
                    else -> CollectionFragileAdjustmentEvidence.signatures(adjustment)
                        .firstOrNull { (role, _, _) -> phase == "ADJUSTMENT_$role" }?.third?.record?.fileIds
                }
            }
            else -> null
        }
        if (expectedFiles == null) throw BadRequestException("原件记录或阶段不属于本馆本件脆弱材质保护历史")
        val archive = archives.phaseForAuthorizedOrg(CollectionFragilityEvidence.BIZ, recordId, orgId, phase)
            ?: throw BadRequestException("指定阶段永久原件不存在")
        val archiveId = archive["id"] as? String ?: throw BadRequestException("永久原件编号缺失")
        val sealed = CollectionPermanentArchiveDao[archiveId]
        if (!archives.verifyExisting(sealed, orgId, CollectionFragilityEvidence.BIZ, recordId, phase,
                JsonUtils.mapper.readTree(sealed.snapshot), expectedFiles))
            throw BadRequestException("指定阶段原件缺少内容基线，请核对原件与备份")
        val files = (archive["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (files.none { it["fileId"] == fileId }) throw BadRequestException("文件不属于指定阶段永久原件")
        return archiveId
    }

    /** The source loan original is readable only when the sealed adjustment actually retained this item's monitoring fact. */
    fun requireLoanMonitoringSource(
        collectionId: String, orgId: String, adjustmentId: String, monitoringId: String, fileId: String, verifyContent: Boolean = true,
    ): String {
        CollectionCurrentOperationAccess.require(scope, orgId, false, setOf(CollectionConst.CARE), "材质脆弱性使用变更历史")
        val adjustment = CollectionFragileUseAdjustmentDao.findById(adjustmentId)
            ?.takeIf { it.collectionId == collectionId && it.orgId == orgId }
            ?: throw BadRequestException("变更原件不属于本馆本件藏品")
        CollectionFragileAdjustmentEvidence.verify(adjustment)
        val plan = CollectionFragileUsePlanDao.findById(adjustment.planId)
            ?.takeIf { it.collectionId == collectionId && it.orgId == orgId && it.sourceBizType == CollectionConst.BIZ_LOAN }
            ?: throw BadRequestException("变更原件没有本馆本件借用来源")
        val frozen = JsonUtils.mapper.readTree(adjustment.factsSnapshot).path("loanMonitoring")
            .firstOrNull { it.path("id").stringValue() == monitoringId }
            ?: throw BadRequestException("借用监管记录不属于本次变更核查时的冻结事实")
        val source = CollectionFragilityEvidence.loanMonitoring(orgId, CollectionConst.BIZ_LOAN, plan.sourceBizId)
            .firstOrNull { it.first == monitoringId }?.second
            ?: throw BadRequestException("本馆借用监管原件不存在")
        val sourceRow = LoanComplianceRecordDao.findById(monitoringId)
            ?: throw BadRequestException("本馆借用监管记录不存在")
        if (collectionId !in source.collectionIds ||
            frozen.path("content") != JsonUtils.mapper.readTree(sourceRow.content))
            throw BadRequestException("借用监管原件与本件变更核查事实不一致")
        val archive = (if (verifyContent) archives.phaseForAuthorizedOrg(CollectionConst.BIZ_LOAN, plan.sourceBizId, orgId, "MONITORING_$monitoringId")
            else archives.phaseForDisplay(CollectionConst.BIZ_LOAN, plan.sourceBizId, orgId, "MONITORING_$monitoringId"))
            ?: throw BadRequestException("借用监管永久原件不存在")
        if (JsonUtils.mapper.readTree(archive["snapshot"] as String) != frozen.path("content"))
            throw BadRequestException("借用监管永久原件与变更核查事实不一致")
        val archiveId = archive["id"] as? String ?: throw BadRequestException("永久原件编号缺失")
        archives.requireFileReferences(CollectionPermanentArchiveDao[archiveId], source.fileIds)
        val files = (archive["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (verifyContent && files.none { it["fileId"] == fileId }) throw BadRequestException("文件不属于指定借用监管原件")
        return archiveId
    }
}
