package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 当前清点数据须对应留存原件；旧版本保留，签署后继续调查不能改写签署时的事实。 */
internal class InventoryArchiveEvidence(private val archives: CollectionPermanentArchiveService) {
    private val incidentEvidence = CollectionIncidentArchiveEvidence(archives)
    private fun fail(): Nothing = throw BadRequestException("清点数据与永久原件或文件引用不一致，须核对历史记录后办理")
    private fun files(vararg values: String?) = values.filterNotNull().joinToString(",")
    private fun tree(value: Any) = JsonUtils.mapper.readTree(JsonUtils.toJson(value)!!)
    /** Set 字段重载后顺序可变；只规范化该字段的比较副本，不改写原档案或其摘要。 */
    private fun planTree(value: tools.jackson.databind.JsonNode?): tools.jackson.databind.JsonNode? {
        val plan = value as? tools.jackson.databind.node.ObjectNode ?: return value
        val ids = plan.get("keeperStaffingChangeIds") ?: return value
        if (!ids.isArray || ids.any { !it.isString }) fail()
        val values = JsonUtils.fromJson(ids.toString(), Array<String>::class.java) ?: fail()
        if (values.distinct().size != values.size) fail()
        plan.set("keeperStaffingChangeIds", tree(values.sorted()))
        return plan
    }
    private fun snapshotTree(value: tools.jackson.databind.JsonNode) = value.also { planTree(it.get("plan")) }
    private inline fun <reified T> decode(value: tools.jackson.databind.JsonNode?): T =
        value?.takeUnless { it.isNull }?.let { JsonUtils.fromJson(it.toString(), T::class.java) } ?: fail()
    private fun checkFiles(v: InventoryItemCheckVo) = files(v.ledgerFileIds, v.cardFileIds, v.photoFileIds, v.signedCheckFileIds)
    private fun actionFiles(v: InventoryIssueActionVo) = files(v.evidenceFileIds, v.reportFileIds, v.signedResolutionFileIds)
    private fun completionFiles(v: InventoryCompletionVo) = files(v.reportFileIds, v.handoverFileIds, v.appointmentFileIds, v.supervisionFileIds, v.departureDelayFileIds)
    fun errors(batch: InventoryBatchDao): List<String> = try { verify(batch); emptyList() } catch (e: BadRequestException) { listOf(e.message ?: "清点原件待核对") }

    /** 年度范围以原方案并行查找，避免当前年份、所属馆或方案被清除后掩盖原批次。 */
    fun archivedBatchIds(orgId: String, year: Int): Set<String> = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq InventoryComplianceService.BIZ) and
            (CollectionPermanentArchives.orgId eq orgId) and (CollectionPermanentArchives.phase eq "STARTED")
    }.filter { row ->
        try {
            val originalYear = JsonUtils.mapper.readTree(row.snapshot)?.get("plan")?.get("year")
            originalYear == null || !originalYear.isIntegralNumber || originalYear.intValue() == year
        } catch (_: tools.jackson.core.JacksonException) { true }
    }.map { it.bizId }.toSet()

    fun verify(batch: InventoryBatchDao) {
        try { verifyRecords(batch) } catch (_: tools.jackson.core.JacksonException) { fail() }
    }
    private fun verifyRecords(batch: InventoryBatchDao) {
        val rows = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq InventoryComplianceService.BIZ) and (CollectionPermanentArchives.bizId eq batch.id.value)
        }.sortedWith(compareBy({it.createTime}, {it.id.value}))
        if (batch.plan == null) {
            if (rows.isNotEmpty() || batch.baseline != null || batch.checks != null || batch.issueActions != null || batch.completion != null) fail()
            return
        }
        fun source(row: CollectionPermanentArchiveDao) = JsonUtils.mapper.readTree(row.snapshot).also {
            if (row.orgId != batch.orgId || row.digest != CollectionCareEvidence.digest(row.snapshot)) fail()
        }
        val start = rows.singleOrNull { it.phase == "STARTED" } ?: fail()
        val startProof = source(start)
        archives.verifyExisting(start, batch.orgId, InventoryComplianceService.BIZ, batch.id.value, "STARTED",
            startProof, files(batch.plan!!.scopeFileIds, batch.plan!!.responsibilityFileIds, batch.plan!!.replanFileIds))
        if (snapshotTree(startProof) != snapshotTree(tree(mapOf("plan" to batch.plan, "baseline" to batch.baseline)))) fail()
        val baseline = batch.baseline ?: fail()
        if (WarehouseStaffingArchiveEvidence(archives).forPlan(batch.orgId, batch.plan!!) != baseline.keeperStaffingEvidence) fail()
        if (batch.plan!!.leadershipDepartureId != null) LeadershipDepartureEvidence(archives).requirePlan(batch.orgId, batch.plan!!, baseline.ownership)
        if (baseline.items.map { it.collectionId }.toSet().size != baseline.items.size || splitCollectionIds(batch.collectionIds).toSet() != baseline.items.map { it.collectionId }.toSet()) fail()
        val checks = rows.filter { it.phase.startsWith("CHECK_") }.map { row ->
            val proof = source(row)
            decode<InventoryItemCheckVo>(proof).also { value ->
                archives.verifyExisting(row, batch.orgId, InventoryComplianceService.BIZ, batch.id.value,
                    row.phase, proof, checkFiles(value))
            }
        }
        val actions = rows.filter { it.phase.startsWith("ACTION_") }.map { row ->
            val proof = source(row)
            decode<InventoryIssueActionVo>(proof).also { value ->
                archives.verifyExisting(row, batch.orgId, InventoryComplianceService.BIZ, batch.id.value,
                    row.phase, proof, actionFiles(value))
            }
        }
        actions.filter { it.resolved == true && checks.any { c -> c.collectionId == it.collectionId && c.abnormal() } }.forEach { action ->
            val clearance = rows.filter { it.phase.startsWith("INCIDENT_CLEARANCE_") }.firstOrNull { row ->
                source(row).get("action") == tree(action)
            } ?: fail()
            val snapshot = source(clearance)
            val frozenProof = snapshot.get("incidentProof")?.takeIf { it.isObject } ?: fail()
            val confirmationId = frozenProof.get("confirmationId")?.takeIf { it.isString }
                ?.stringValue()?.takeIf(String::isNotBlank) ?: fail()
            val incidentId = action.incidentId?.takeIf(String::isNotBlank) ?: fail()
            val proof = incidentEvidence.clearanceProof(
                incidentId, batch.orgId, action.collectionId, IncidentSourceVo(InventoryComplianceService.BIZ, batch.id.value),
                action.resolutionApprovedAt ?: fail(), action.keeperUserId ?: fail(), action.leaderUserId ?: fail(), confirmationId,
                if (action.major == true) listOf(IncidentReportRequirement(action.collectionId, EnumIncidentTarget.COMPETENT, EnumIncidentReportKind.INCIDENT)) else emptyList(),
                purpose = "清点",
            )
            if (frozenProof != tree(proof)) fail()
            archives.verifyExisting(clearance, batch.orgId, InventoryComplianceService.BIZ, batch.id.value,
                clearance.phase, snapshot, files(action.evidenceFileIds, action.signedResolutionFileIds))
        }
        val currentChecks = batch.checks?.items.orEmpty(); val currentActions = batch.issueActions?.items.orEmpty()
        if (currentChecks.size != currentChecks.map { it.collectionId }.toSet().size || currentActions.size != currentActions.map { it.collectionId }.toSet().size) fail()
        if (tree(checks.associateBy { it.collectionId }) != tree(currentChecks.associateBy { it.collectionId }) || tree(actions.associateBy { it.collectionId }) != tree(currentActions.associateBy { it.collectionId })) fail()
        if (currentChecks.any { c -> baseline.items.none { it.collectionId == c.collectionId } } || currentActions.any { a -> currentChecks.none { it.collectionId == a.collectionId } }) fail()
        val complete = rows.singleOrNull { it.phase == "COMPLETED" }
        if ((complete == null) != (batch.completion == null)) fail()
        if (complete != null) {
            val proof = source(complete)
            val value = decode<InventoryCompletionVo>(proof.get("completion"))
            archives.verifyExisting(complete, batch.orgId, InventoryComplianceService.BIZ, batch.id.value,
                "COMPLETED", proof, completionFiles(value))
            // 通过类型补齐新加的可空字段，兼容升级前未含迟办字段的原签署 JSON。
            if (tree(value) != tree(batch.completion!!) || planTree(proof.get("plan")) != planTree(tree(batch.plan!!)) || proof.get("baseline") != tree(baseline) || proof.get("checks") != tree(batch.checks ?: fail())) fail()
            if (tree(batch.finishedAt ?: fail()) != tree(value.signedAt) || batch.reportAttachmentIds != value.reportFileIds) fail()
            val signedActions = decode<InventoryIssueActionsVo>(proof.get("issueActions"))
            if (signedActions.items.any { a -> actions.none { tree(it) == tree(a) } }) fail()
        }
    }

    fun annual(record: InventoryAnnualReportDao) {
        try {
            val row = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq InventoryComplianceService.ANNUAL) and (CollectionPermanentArchives.bizId eq record.id.value) and (CollectionPermanentArchives.phase eq "REPORTED")
            }.singleOrNull() ?: fail()
            val report = JsonUtils.fromJson(record.content, InventoryAnnualReportVo::class.java) ?: fail()
            val proof = JsonUtils.mapper.readTree(row.snapshot)
            val coverage=JsonUtils.fromJson(record.coverage,InventoryCoverageVo::class.java) ?: fail()
            if(coverage.year!=record.year)fail()
            if (record.year != report.year || row.orgId != record.orgId || row.digest != CollectionCareEvidence.digest(row.snapshot) || proof.get("report") != tree(report) || proof.get("coverage") != JsonUtils.mapper.readTree(record.coverage)) fail()
            archives.verifyExisting(row, record.orgId, InventoryComplianceService.ANNUAL, record.id.value,
                "REPORTED", proof, files(report.signedReportFileIds, report.receiptFileIds))
        } catch (_: tools.jackson.core.JacksonException) { fail() }
    }
    fun annualErrors(record: InventoryAnnualReportDao): List<String> = try { annual(record); emptyList() } catch (e: BadRequestException) { listOf(e.message ?: "年度报告原件待核对") }
}
