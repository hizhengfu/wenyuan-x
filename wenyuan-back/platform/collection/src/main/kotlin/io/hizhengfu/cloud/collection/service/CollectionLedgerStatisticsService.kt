package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** Called only from the same transaction as the actual formal ledger mutation. */
internal object CollectionLedgerStatRecorder {
    fun record(collection: CollectionDao, kind: EnumLedgerStatEvent, sourceId: String, at: LocalDateTime=LocalDateTime.now(), reason: String?=null, statReason: EnumLedgerChangeReason?=null) {
        val item=LedgerStatItemVo(collection.id.value,collection.totalRegNo,collection.name,collection.nature,collection.level,
            collection.categoryId,collection.source,collection.quantity.toLong(),collection.unit,collection.actualQuantity?.toLong(),collection.actualQuantityUnit,
            collection.categoryId?.let(CollectionCategoryDao::findById)?.name,
            statReason ?: CollectionCategoryReasonBalances.reason(kind, collection.source))
        val previous=CollectionLedgerStatEventDao.find {
            (CollectionLedgerStatEvents.orgId eq collection.orgId) and (CollectionLedgerStatEvents.collectionId eq collection.id.value) and
                (CollectionLedgerStatEvents.kind eq kind) and (CollectionLedgerStatEvents.sourceId eq sourceId)
        }.singleOrNull()
        if(previous!=null) {
            if(previous.item.copy(categoryName=null,changeReason=null)!=item.copy(categoryName=null,changeReason=null))throw BadRequestException("该正式登记事实已留存，不能覆盖统计历史")
            return
        }
        CollectionLedgerStatEventDao.new {
            orgId=collection.orgId;collectionId=collection.id.value;this.kind=kind;this.sourceId=sourceId;occurredAt=at;this.item=item;this.reason=reason
        }
    }
}

@Component
@Transactional
class CollectionLedgerStatisticsService(private val scope: CollectionScopeResolver) {
    fun preview(year: Int, orgId: String?=null): AnnualLedgerPreviewVo {
        if(year !in 1900..LocalDateTime.now().year)throw BadRequestException("请选择实际统计年度")
        val start=LocalDateTime.of(year,1,1,0,0)
        return calculate(start,start.plusYears(1),orgId)
    }

    fun period(startDate: java.time.LocalDate, endDate: java.time.LocalDate, orgId: String?=null): PeriodLedgerPreviewVo {
        if(startDate.year<1900 || endDate<startDate || endDate>java.time.LocalDate.now())throw BadRequestException("请选择有效的实际统计期间")
        val v=calculate(startDate.atStartOfDay(),endDate.plusDays(1).atStartOfDay(),orgId)
        return PeriodLedgerPreviewVo(v.orgId,startDate,endDate,v.cutoff,v.completeYear,v.openingTotal,v.newTotal,v.transferInTotal,v.transferOutTotal,
            v.deregTotal,v.closingTotal,v.quantityBalances,v.actualQuantityBalances.orEmpty(),v.unknownActualQuantityCount ?: 0,
            v.closingByLevel,v.closingByCategory,v.issues,v.events,v.categoryReasonBalances)
    }

    private fun calculate(start:LocalDateTime,end:LocalDateTime,orgId:String?): AnnualLedgerPreviewVo {
        val access=CollectionSearchReadAccess.require(scope, additionalPermissions=setOf(CollectionConst.STAT), allowHistoricalOrgs=true)
        val org=orgId ?: access.currentOrgId(scope)
        val now=LocalDateTime.now()
        OrgScopedReadPolicy.requireVisible(access.orgIds,org,"正式总账期间统计")
        val cutoff=minOf(end,now)
        val events=CollectionLedgerStatEventDao.find { CollectionLedgerStatEvents.orgId eq org }.map {
            if(it.collectionId!=it.item.collectionId)throw BadRequestException("总账统计事件与留存藏品身份不一致，请核对原账")
            it.toModel()
        }
        val known=events.groupBy { it.item.collectionId }
        val candidates=linkedMapOf<String,String>()
        CollectionDao.find { Collections.orgId eq org }.filter { it.registered || it.lifeStatus!=EnumCollectionStatus.PENDING || it.catalogStatus==EnumCatalogStatus.APPROVED }
            .forEach { candidates[it.id.value]=it.totalRegNo }
        CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.orgId eq org }.forEach { candidates[it.collectionId]=it.totalRegNo }
        known.forEach { (id,rows)->candidates.putIfAbsent(id,rows.first().item.totalRegNo) }
        val issues=mutableListOf<AnnualLedgerIssueVo>()
        val opening=mutableListOf<LedgerStatItemVo>();val closing=mutableListOf<LedgerStatItemVo>()
        val selected=mutableListOf<AnnualLedgerEventVo>()
        val changes=mutableListOf<Pair<LedgerStatItemVo,LedgerStatItemVo>>()
        candidates.forEach { (id,no) ->
            val current = CollectionDao.findById(id)
            if (current?.historicalConfirmedAt != null && current.registeredAt == null) {
                issues += AnnualLedgerIssueVo(id, no, "已登账历史迁入，原登记日期待考：计入现存馆藏；未分配登记年度，不计导入年度新增，期间增减及期初期末须另核原账")
                return@forEach
            }
            val history=known[id].orEmpty().sortedWith(compareBy({it.occurredAt.toLocalDate()},{it.recordedAt},{it.id}))
            if(history.isEmpty() || history.first().kind !in setOf(EnumLedgerStatEvent.REGISTER,EnumLedgerStatEvent.TRANSFER_IN)) {
                issues+=AnnualLedgerIssueVo(id,no,"缺少可核实的本馆首次正式登记或调入历史，请用原总账和逐件明细核对补录")
                return@forEach
            }
            if(history.any { it.kind==EnumLedgerStatEvent.REGISTER && it.occurredAt<cutoff && it.reason=="旧系统迁移：历史增减待核实" }) {
                issues+=AnnualLedgerIssueVo(id,no,"旧系统迁移仅保留原登记事实与迁移时点快照，期间数量、单位和馆藏增减尚未核实，不能作为完整年度报送数")
            }
            var state: LedgerStatItemVo?=null
            var initial: LedgerStatItemVo?=null
            var atOpeningCaptured=false
            val localEvents=mutableListOf<AnnualLedgerEventVo>();val localChanges=mutableListOf<Pair<LedgerStatItemVo,LedgerStatItemVo>>()
            var invalid=false
            for(event in history.filter { it.occurredAt<cutoff }) {
                if(!atOpeningCaptured && event.occurredAt>=start){initial=state;atOpeningCaptured=true}
                when(event.kind) {
                    EnumLedgerStatEvent.REGISTER,EnumLedgerStatEvent.TRANSFER_IN -> {if(state!=null){invalid=true;break};state=event.item}
                    EnumLedgerStatEvent.CHANGE -> {val before=state;if(before==null){invalid=true;break};if(event.occurredAt>=start)localChanges+=before to event.item;state=event.item}
                    EnumLedgerStatEvent.TRANSFER_OUT,EnumLedgerStatEvent.DEREGISTER -> {if(state==null || state.copy(categoryName=null,changeReason=null)!=event.item.copy(categoryName=null,changeReason=null)){invalid=true;break};state=null}
                }
                if(event.occurredAt>=start)localEvents+=event
            }
            if(invalid){issues+=AnnualLedgerIssueVo(id,no,"正式总账事件顺序不完整或冲突，不能以当前馆藏状态推算往年数");return@forEach}
            if(!atOpeningCaptured)initial=state
            initial?.let(opening::add);state?.let(closing::add);selected+=localEvents;changes+=localChanges
        }
        fun count(kind: EnumLedgerStatEvent)=selected.count { it.kind==kind }
        fun amounts(items: List<LedgerStatItemVo>,unit: String)=items.filter { it.unit==unit }.sumOf { it.quantity }
        fun amount(kind: EnumLedgerStatEvent,unit: String)=amounts(selected.filter { it.kind==kind }.map { it.item },unit)
        val units=(opening+closing+selected.map { it.item }+changes.flatMap { listOf(it.first,it.second) }).map { it.unit }.distinct().sorted()
        return AnnualLedgerPreviewVo(org,start.year,cutoff,now>=end,
            opening.size,count(EnumLedgerStatEvent.REGISTER),count(EnumLedgerStatEvent.TRANSFER_IN),count(EnumLedgerStatEvent.TRANSFER_OUT),count(EnumLedgerStatEvent.DEREGISTER),closing.size,
            units.map { unit -> AnnualUnitBalanceVo(unit,amounts(opening,unit),amount(EnumLedgerStatEvent.REGISTER,unit),amount(EnumLedgerStatEvent.TRANSFER_IN,unit),amount(EnumLedgerStatEvent.TRANSFER_OUT,unit),amount(EnumLedgerStatEvent.DEREGISTER,unit),changes.sumOf { (before,after)->(if(after.unit==unit)after.quantity else 0)-(if(before.unit==unit)before.quantity else 0) },amounts(closing,unit)) },
            closing.groupingBy { it.level.name }.eachCount(),closing.groupingBy { it.categoryId ?: "未分类" }.eachCount(),issues,selected.sortedWith(compareBy({it.occurredAt.toLocalDate()},{it.recordedAt},{it.id})),
            actualBalances(opening,closing,selected,changes),
            (opening+closing+selected.map {it.item}).filter { it.actualQuantity==null || it.actualQuantityUnit.isNullOrBlank() }.map {it.collectionId}.distinct().size,
            CollectionCategoryReasonBalances.build(opening,closing,selected,changes))
    }

    private fun actualBalances(opening:List<LedgerStatItemVo>,closing:List<LedgerStatItemVo>,events:List<AnnualLedgerEventVo>,changes:List<Pair<LedgerStatItemVo,LedgerStatItemVo>>):List<AnnualUnitBalanceVo> {
        val units=(opening+closing+events.map {it.item}+changes.flatMap {listOf(it.first,it.second)}).mapNotNull {it.actualQuantityUnit?.takeIf(String::isNotBlank)}.distinct().sorted()
        fun amount(items:List<LedgerStatItemVo>,unit:String)=items.filter {it.actualQuantityUnit==unit}.sumOf {it.actualQuantity ?: 0}
        fun event(kind:EnumLedgerStatEvent,unit:String)=amount(events.filter {it.kind==kind}.map {it.item},unit)
        return units.map {unit -> AnnualUnitBalanceVo(unit,amount(opening,unit),event(EnumLedgerStatEvent.REGISTER,unit),event(EnumLedgerStatEvent.TRANSFER_IN,unit),
            event(EnumLedgerStatEvent.TRANSFER_OUT,unit),event(EnumLedgerStatEvent.DEREGISTER,unit),
            changes.sumOf {(before,after)->(if(after.actualQuantityUnit==unit) after.actualQuantity ?: 0 else 0)-(if(before.actualQuantityUnit==unit) before.actualQuantity ?: 0 else 0)},amount(closing,unit)) }

    }
}
