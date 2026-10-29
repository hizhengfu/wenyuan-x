package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.*

/** 盘点批次头表；盘点明细继续落在 Inventories，并通过 batchId 关联。 */
object InventoryBatches : OrgAuditUIDTable("col_inventory_batch") {
    val batchName = varchar("batch_name", 100).comment("盘点批次")
    val scopeDesc = varchar("scope_desc", 500).nullable().comment("盘点范围")
    val cycleMonths = integer("cycle_months", checkConstraintName = shortCheckConstraintName("cycle_months"))
        .nullable().comment("盘点周期（月）")
    val warehouseId = varchar("warehouse_id", 64).nullable().index(shortIndexName("warehouse_id"))
        .comment("目标库位")
    val collectionIds = text("collection_ids").nullable().comment("指定应盘藏品，逗号分隔")
    val startedAt = datetime("started_at").nullable().comment("开始时间")
    val finishedAt = datetime("finished_at").nullable().comment("结束时间")
    val reportAttachmentIds = text("report_attachment_ids").nullable().comment("盘点报告附件")
    val remark = varchar("remark", 1000).nullable()

    val plan = json("statutory_plan", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, InventoryPlanVo::class.java)!! }).nullable()
    val baseline = json("ledger_baseline", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, InventoryBaselineVo::class.java)!! }).nullable()
    val checks = json("actual_checks", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, InventoryChecksVo::class.java)!! }).nullable()
    val issueActions = json("issue_actions", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, InventoryIssueActionsVo::class.java)!! }).nullable()
    val completion = json("signed_completion", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, InventoryCompletionVo::class.java)!! }).nullable()

    init {
        uniqueIndex(shortIndexName("org_id_batch_name", unique = true), orgId, batchName)
    }
}

class InventoryBatchDao(id: EntityID<String>) : OrgAuditUIDEntity(id, InventoryBatches) {
    companion object : OrgAuditUIDEntityClass<InventoryBatchDao>(InventoryBatches)

    var batchName by InventoryBatches.batchName
    var scopeDesc by InventoryBatches.scopeDesc
    var cycleMonths by InventoryBatches.cycleMonths
    var warehouseId by InventoryBatches.warehouseId
    var collectionIds by InventoryBatches.collectionIds
    var startedAt by InventoryBatches.startedAt
    var finishedAt by InventoryBatches.finishedAt
    var reportAttachmentIds by InventoryBatches.reportAttachmentIds
    var remark by InventoryBatches.remark

    var plan by InventoryBatches.plan
    var baseline by InventoryBatches.baseline
    var checks by InventoryBatches.checks
    var issueActions by InventoryBatches.issueActions
    var completion by InventoryBatches.completion

    fun toModel() = InventoryBatchVo(
        id = id.value,
        orgId = orgId,
        batchName = batchName,
        scopeDesc = scopeDesc,
        cycleMonths = cycleMonths,
        warehouseId = warehouseId,
        collectionIds = collectionIds,
        startedAt = startedAt,
        finishedAt = finishedAt,
        reportAttachmentIds = reportAttachmentIds,
        remark = remark,
        plan = plan,
        baseline = baseline,
        checks = checks,
        issueActions = issueActions,
        completion = completion,
        departureStatus = if (inventoryDepartureScopeErrors(plan, baseline, warehouseId).isEmpty()) inventoryDepartureStatus(plan, completion) else EnumInventoryDepartureStatus.SCOPE_REVIEW_REQUIRED,
        departureScopeErrors = inventoryDepartureScopeErrors(plan, baseline, warehouseId),

    )
}

data class InventoryBatchVo(
    val id: String? = null,
    val orgId: String? = null,
    val batchName: String? = null,
    val scopeDesc: String? = null,
    val cycleMonths: Int? = null,
    val warehouseId: String? = null,
    val collectionIds: String? = null,
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val reportAttachmentIds: String? = null,
    val remark: String? = null,
    val plan: InventoryPlanVo? = null,
    val baseline: InventoryBaselineVo? = null,
    val checks: InventoryChecksVo? = null,
    val issueActions: InventoryIssueActionsVo? = null,
    val completion: InventoryCompletionVo? = null,
    val archives: List<Map<String, Any?>> = emptyList(),
    val overdueCollectionIds: Set<String> = emptySet(),
    val departureStatus: EnumInventoryDepartureStatus = EnumInventoryDepartureStatus.NOT_APPLICABLE,
    val evidenceErrors: List<String> = emptyList(),
    val supersededByBatchId: String? = null,
    val departureScopeErrors: List<String> = emptyList(),
)
