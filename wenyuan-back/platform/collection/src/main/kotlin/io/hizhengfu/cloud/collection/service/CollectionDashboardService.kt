package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionSamplings

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.AcquisitionPlans
import io.hizhengfu.cloud.collection.dao.Acquisitions
import io.hizhengfu.cloud.collection.dao.Appraisals
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogs
import io.hizhengfu.cloud.collection.dao.CollectionReturns
import io.hizhengfu.cloud.collection.dao.CollectionTransfers
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.Deregistrations
import io.hizhengfu.cloud.collection.dao.Exhibitions
import io.hizhengfu.cloud.collection.dao.Inventories
import io.hizhengfu.cloud.collection.dao.Loans
import io.hizhengfu.cloud.collection.dao.Reproductions
import io.hizhengfu.cloud.collection.dao.Requisitions
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.dao.StorageMovements
import io.hizhengfu.cloud.collection.dao.WarehouseEntryApplications
import io.hizhengfu.cloud.collection.domain.AnnualStatVo
import io.hizhengfu.cloud.collection.domain.CatalogAuditSummaryVo
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumLedgerStatEvent
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.collection.domain.MovementTrendVo
import io.hizhengfu.cloud.collection.domain.OngoingFlowVo
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.flow.dao.FlowInstanceDao
import io.hizhengfu.cloud.flow.dao.FlowInstances
import io.hizhengfu.cloud.flow.dao.FlowTaskDao
import io.hizhengfu.cloud.flow.dao.FlowTasks
import io.hizhengfu.cloud.flow.domain.TaskStatus
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 出入库按月趋势聚合（内存分组，规避多数据库日期函数差异；补齐无流水的月份）。 */
internal object MovementTrendBuilder {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun build(rows: List<Pair<LocalDateTime, EnumMoveType>>, months: Int, now: YearMonth = YearMonth.now()): List<MovementTrendVo> {
        val buckets = (0 until months).map { now.minusMonths((months - 1 - it).toLong()) }
        val grouped = rows.groupBy { YearMonth.from(it.first) }
        return buckets.map { month ->
            val inMonth = grouped[month].orEmpty()
            MovementTrendVo(
                month = month.format(formatter),
                inCount = inMonth.count { it.second == EnumMoveType.IN },
                outCount = inMonth.count { it.second == EnumMoveType.OUT },
            )
        }
    }
}

/** 年度统计聚合：期初/期末按「登账时间 + 退出馆藏时间」区间判定，不依赖当前状态回推。 */
internal object AnnualStatBuilder {
    data class Snapshot(
        val registeredAt: LocalDateTime?,
        val deregisteredAt: LocalDateTime?,
        val source: String?,
        val level: String?,
        val category: String?,
    )

    fun build(
        year: Int,
        items: List<Snapshot>,
        movements: List<Pair<LocalDateTime, EnumMoveType>>,
        deregLogs: List<Pair<LocalDateTime, String?>>,
    ): AnnualStatVo {
        val yearStart = LocalDateTime.of(year, 1, 1, 0, 0)
        val yearEnd = yearStart.plusYears(1)
        val registered = items.filter { it.registeredAt != null }
        fun aliveAt(item: Snapshot, at: LocalDateTime): Boolean =
            item.registeredAt!! < at && (item.deregisteredAt == null || item.deregisteredAt >= at)

        val newItems = registered.filter { it.registeredAt!! >= yearStart && it.registeredAt < yearEnd }
        val deregInYear = deregLogs.filter { it.first >= yearStart && it.first < yearEnd }
        val movesInYear = movements.filter { it.first >= yearStart && it.first < yearEnd }
        val closingItems = registered.filter { aliveAt(it, yearEnd) }
        return AnnualStatVo(
            year = year,
            openingTotal = registered.count { aliveAt(it, yearStart) },
            newTotal = newItems.size,
            newBySource = newItems.groupingBy { it.source ?: "未设置" }.eachCount(),
            deregTotal = deregInYear.size,
            deregByReason = deregInYear.groupingBy { it.second?.takeIf { r -> r.isNotBlank() } ?: "未说明" }.eachCount(),
            closingTotal = closingItems.size,
            inCount = movesInYear.count { it.second == EnumMoveType.IN },
            outCount = movesInYear.count { it.second == EnumMoveType.OUT },
            closingByLevel = closingItems.groupingBy { it.level ?: "未定级" }.eachCount(),
            closingByCategory = closingItems.groupingBy { it.category ?: "未分类" }.eachCount(),
        )
    }
}

internal object CatalogAuditSummaryBuilder {
    fun build(counts: Map<EnumCatalogStatus, Int>): CatalogAuditSummaryVo {
        val approved = counts[EnumCatalogStatus.APPROVED] ?: 0
        val rejected = counts[EnumCatalogStatus.REJECTED] ?: 0
        val decided = approved + rejected
        return CatalogAuditSummaryVo(
            draft = counts[EnumCatalogStatus.DRAFT] ?: 0,
            pending = (counts[EnumCatalogStatus.PENDING] ?: 0) + (counts[EnumCatalogStatus.SUBMITTED] ?: 0),
            approved = approved,
            rejected = rejected,
            passRate = if (decided <= 0) 0 else ((approved.toDouble() / decided) * 100).roundToInt(),
        )
    }
}

/** 藏品管理工作台聚合：出入库趋势 / 编目审核通过率 / 在批单据动态。 */
@Component
@Transactional(readOnly = true)
class CollectionDashboardService(
    private val scope: CollectionScopeResolver,
    private val userResolver: CollectionUserResolver,
    private val ledgerStatistics: CollectionLedgerStatisticsService,
) {

    fun movementTrend(months: Int): List<MovementTrendVo> {
        val orgIds = dashboardOrgIds()
        val safeMonths = months.coerceIn(1, 36)
        val start = YearMonth.now().minusMonths((safeMonths - 1).toLong()).atDay(1).atStartOfDay()
        val q = StorageMovements.selectAll().where { StorageMovements.operatedAt greaterEq start }
        q.andWhere { StorageMovements.orgId inList orgIds }
        val rows = q.map { it[StorageMovements.operatedAt] to it[StorageMovements.type] }
        return MovementTrendBuilder.build(rows, safeMonths)
    }

    fun auditSummary(): CatalogAuditSummaryVo {
        val orgIds = dashboardOrgIds()
        val q = Collections.selectAll()
        q.andWhere { Collections.orgId inList orgIds }
        val counts = q.groupingBy { it[Collections.catalogStatus] }.eachCount()
        return CatalogAuditSummaryBuilder.build(counts)
    }

    /** 年度统计（年终上报）：期初/新增/退出馆藏/期末/出入库流动量。 */
    @Transactional
    fun annual(year: Int): AnnualStatVo {
        val preview=ledgerStatistics.preview(year)
        val movements=StorageMovementDao.find { StorageMovements.orgId eq preview.orgId }
            .filter { it.operatedAt.year==year && it.operatedAt<preview.cutoff }
        return AnnualStatVo(year,preview.openingTotal,preview.newTotal,
            preview.events.filter { it.kind==EnumLedgerStatEvent.REGISTER }.groupingBy { it.item.source?.name ?: "未设置" }.eachCount(),
            preview.deregTotal,preview.events.filter { it.kind==EnumLedgerStatEvent.DEREGISTER }.groupingBy { it.reason ?: "见原退出凭证" }.eachCount(),
            preview.closingTotal,movements.count { it.type==EnumMoveType.IN },movements.count { it.type==EnumMoveType.OUT },
            preview.closingByLevel,preview.closingByCategory,preview.orgId,preview.cutoff,preview.completeYear,
            preview.transferInTotal,preview.transferOutTotal,preview.quantityBalances,preview.issues,preview.events)
    }

    /** 各业务表在批（flowStatus=AUDIT）单据合并按时间倒序，取最新 limit 条。 */
    fun ongoing(limit: Int): List<OngoingFlowVo> {
        val orgIds = dashboardOrgIds()
        val safeLimit = limit.coerceIn(1, 100)
        val items = ongoingSources.flatMap { it.query(orgIds) }
            .sortedByDescending { it.createTime }
            .take(safeLimit)
        val names = userResolver.names(items.map { it.applicant })
        items.forEach { it.applicantName = it.applicant?.let { u -> names[u] } }
        val instanceIds = items.mapNotNull { it.flowInstanceId }.distinct()
        val instances = if (instanceIds.isEmpty()) emptyMap() else FlowInstanceDao.find { FlowInstances.id inList instanceIds }.associateBy { it.id.value }
        fun matches(item: OngoingFlowVo) = instances[item.flowInstanceId]?.let {
            it.businessKey == item.id && it.businessType == item.bizType
        } == true
        val validInstanceIds = items.filter(::matches).mapNotNull { it.flowInstanceId }.toSet()
        val stageByInstance = if (validInstanceIds.isEmpty()) emptyMap() else FlowTaskDao.find {
            (FlowTasks.instanceId inList validInstanceIds) and
                (FlowTasks.status eq TaskStatus.PENDING.name)
        }.filter { it.approvalRoundId == instances[it.instanceId]?.approvalRoundId }.groupBy { it.instanceId }
            .mapValues { (_, tasks) -> tasks.map { it.nodeName }.distinct().joinToString(" / ") }
        items.forEach { item ->
            item.stage = if (item.flowInstanceId != null && !matches(item)) "流程关联异常，请核实"
                else item.flowInstanceId?.let(stageByInstance::get)?.takeIf(String::isNotBlank) ?: "审核中"
        }
        return items
    }

    private fun dashboardOrgIds() = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.STAT)).orgIds

    private class OngoingSource(
        val bizType: String,
        val bizName: String,
        val table: FlowUIDTable,
        val orgId: Column<String>,
        val title: (ResultRow) -> String?,
    ) {
        fun query(orgIds: Collection<String>?): List<OngoingFlowVo> {
            val q = table.selectAll().where { table.flowStatus eq EnumFlowStatus.AUDIT }
            orgIds?.let { ids -> q.andWhere { orgId inList ids } }
            return q.map { row ->
                OngoingFlowVo(
                    bizType = bizType,
                    bizName = bizName,
                    id = row[table.id].value,
                    title = title(row),
                    applicant = row[table.createBy],
                    flowInstanceId = row[table.flowInstanceId],
                    createTime = row[table.createTime],
                )
            }
        }
    }

    private val ongoingSources: List<OngoingSource> = listOf(
        OngoingSource(CollectionConst.BIZ_ACQUISITION_PLAN, "征集计划", AcquisitionPlans, AcquisitionPlans.orgId) { it[AcquisitionPlans.title] },
        OngoingSource(CollectionConst.BIZ_ACQUISITION, "征集入藏", Acquisitions, Acquisitions.orgId) { it[Acquisitions.name] },
        OngoingSource(CollectionConst.BIZ_APPRAISAL, "鉴定定级", Appraisals, Appraisals.orgId) { it[Appraisals.collectionId] },
        OngoingSource(CollectionConst.BIZ_REQUISITION, "藏品提用", Requisitions, Requisitions.orgId) { it[Requisitions.documentNo] },
        OngoingSource(CollectionConst.BIZ_LOAN, "藏品借展", Loans, Loans.orgId) { it[Loans.borrower] ?: it[Loans.protocolNo] },
        OngoingSource(CollectionConst.BIZ_RETURN, "归还管理", CollectionReturns, CollectionReturns.orgId) { it[CollectionReturns.sourceDocumentNo] },
        OngoingSource(CollectionConst.BIZ_TRANSFER, "跨馆调拨", CollectionTransfers, CollectionTransfers.orgId) { it[CollectionTransfers.toOrgId] },
        OngoingSource(CollectionConst.BIZ_DEREGISTER, "藏品退出馆藏", Deregistrations, Deregistrations.orgId) { it[Deregistrations.collectionId] },
        OngoingSource(CollectionConst.BIZ_INVENTORY, "藏品盘点", Inventories, Inventories.orgId) { it[Inventories.batchName] },
        OngoingSource(CollectionConst.BIZ_EXHIBITION, "展览管理", Exhibitions, Exhibitions.orgId) { it[Exhibitions.title] },
        OngoingSource(CollectionConst.BIZ_REPRODUCTION, "复制拓印", Reproductions, Reproductions.orgId) { null },
        OngoingSource(CollectionConst.BIZ_SAMPLING, "有损取样", CollectionSamplings, CollectionSamplings.orgId) { it[CollectionSamplings.title] },
        OngoingSource(CollectionConst.BIZ_WAREHOUSE_ENTRY, "入库申请", WarehouseEntryApplications, WarehouseEntryApplications.orgId) {
            it[WarehouseEntryApplications.applicationNo]
        },
    )
}
