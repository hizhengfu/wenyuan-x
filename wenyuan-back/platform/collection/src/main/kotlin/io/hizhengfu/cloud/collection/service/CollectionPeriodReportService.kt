package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class CollectionPeriodReportService(
    private val scope:CollectionScopeResolver,
    private val ledger:CollectionLedgerStatisticsService,
    private val archives:CollectionPermanentArchiveService,
    private val users:CollectionResponsibleUserService,
) {
    companion object { const val BIZ="COLLECTION_PERIOD_REPORT" }
    fun preview(startDate:LocalDate,endDate:LocalDate,orgId:String?=null):CollectionPeriodReportVo {
        val balances=ledger.period(startDate,endDate,orgId)
        val org=balances.orgId
        val issues=mutableListOf<String>()
        val items=StorageMovementDao.find { (StorageMovements.orgId eq org) and (StorageMovements.type eq EnumMoveType.OUT) }
            .filter { it.operatedAt>=startDate.atStartOfDay() && it.operatedAt<balances.cutoff && it.reason !in setOf(EnumMoveReason.ACQUISITION,EnumMoveReason.TRANSFER,EnumMoveReason.EMERGENCY,EnumMoveReason.RELOCATION) }
            .sortedWith(compareBy({it.operatedAt},{it.id.value})).mapNotNull { row ->
                val ref=row.refBizId
                val request=ref?.let(RequisitionDao::findById)
                val loan=ref?.let(LoanDao::findById)
                if((request==null)==(loan==null)) { issues += "流水 ${row.id.value} 缺少唯一实际提用或借用来源，未计入提用次数";return@mapNotNull null }
                if((request?.orgId ?: loan?.orgId)!=org) throw BadRequestException("提用统计流水与来源所属馆不一致")
                val executed=request?.outExecuted ?: loan!!.outExecuted
                val at=request?.outExecutedTime ?: loan?.outExecutedTime
                val ids=splitCollectionIds(request?.collectionIds ?: loan?.collectionIds)
                if(!executed || at!=row.operatedAt || row.collectionId !in ids) { issues += "流水 ${row.id.value} 与来源实际出库事实不一致，未计入提用次数";return@mapNotNull null }
                val frozen=CollectionMovementFacts.read(row.toModel(),archives)?.collection
                if(frozen==null) issues += "流水 ${row.id.value} 未保留完整执行时著录；次数按实际流水列示，类别和数量不推算"
                PeriodUsageItemVo(row.id.value,ref!!,row.collectionId,row.operatedAt,if(loan!=null) "EXTERNAL" else "INTERNAL",row.reason,
                    frozen?.categoryId,frozen?.categoryName,frozen?.quantity?.toLong(),frozen?.unit,frozen?.actualQuantity?.toLong(),frozen?.actualQuantityUnit)
            }
        val duplicate=items.groupBy { Triple(it.businessId,it.collectionId,it.occurredAt) }.filterValues {it.size>1}
        if(duplicate.isNotEmpty()) throw BadRequestException("同一实际提用存在重复逐件出库流水，请核对原件后统计")
        val grouped=items.groupBy { Triple(it.scope,it.reason,it.categoryId) }.map { (key,values) ->
            PeriodUsageRowVo(key.first,key.second,key.third,values.mapNotNull {it.categoryName}.distinct().singleOrNull(),
                values.map {it.businessId}.distinct().size,values.size,
                quantities(values) {it.quantity to it.unit},quantities(values) {it.actualQuantity to it.actualUnit},
                values.count {it.quantity==null || it.unit.isNullOrBlank() || it.actualQuantity==null || it.actualUnit.isNullOrBlank()})
        }.sortedWith(compareBy({it.scope},{it.reason?.name},{it.categoryId}))
        val result=CollectionPeriodReportVo(balances,grouped,items,items.map {it.businessId}.distinct().size,items.size,issues,"")
        return result.copy(digest=CollectionCareEvidence.digest(JsonUtils.toJson(result)!!))
    }
    private fun quantities(items:List<PeriodUsageItemVo>,read:(PeriodUsageItemVo)->Pair<Long?,String?>):Map<String,Long> =
        items.map(read).filter {it.first!=null && !it.second.isNullOrBlank()}.groupBy {it.second!!}.mapValues {(_,rows)->rows.sumOf {it.first!!}}.toSortedMap()

    fun history(startDate:LocalDate,endDate:LocalDate,orgId:String?=null,verifyContent:Boolean=true):List<CollectionPeriodReportArchiveVo> {
        val access=CollectionSearchReadAccess.require(scope,additionalPermissions=setOf(CollectionConst.STAT),allowHistoricalOrgs=true)
        val org=orgId ?: access.currentOrgId(scope)
        OrgScopedReadPolicy.requireVisible(access.orgIds,org,"期间统计签署档案")
        val rows=records(org,verifyContent).filter {it.signed.startDate==startDate && it.signed.endDate==endDate}.sortedBy {it.version}
        if(rows.withIndex().any {(index,row)->row.version!=index+1 || row.signed.previousId!=rows.getOrNull(index-1)?.id})
            throw BadRequestException("期间报表历史版本缺失或订正链不一致")
        return rows
    }

    fun signed(value:CollectionPeriodReportSignVo):String {
        fun write() {
            val actor=CollectionCurrentOperationAccess.require(scope,value.orgId,true,setOf(CollectionConst.STAT),"期间报表签署归档")
            if(scope.currentOrgId(actor)!=value.orgId) throw BadRequestException("当前收藏单位已变化，请重新核对统计窗口")
        }
        write()
        CollectionOrgDao.find {CollectionOrgs.id eq value.orgId}.forUpdate().singleOrNull() ?: throw BadRequestException("收藏单位不存在")
        write()
        val previous=history(value.startDate,value.endDate,value.orgId).lastOrNull()
        if(previous?.signed==value) return previous.id
        if(previous?.id!=value.previousId) throw BadRequestException("签署版本已变化，请刷新后核实")
        if(previous!=null && (value.replacementReason.isNullOrBlank() || value.replacementFileIds.isNullOrBlank() || value.signedAt<previous.signed.signedAt))
            throw BadRequestException("订正报表须另行签署，说明原因并附订正原件，旧版本永久保留")
        val report=preview(value.startDate,value.endDate,value.orgId)
        if(!report.ledger.completePeriod || value.signedAt<value.endDate.plusDays(1).atStartOfDay() || value.signedAt>LocalDateTime.now())
            throw BadRequestException("统计期间结束后方可登记真实签署，不得预填未来日期")
        if(report.digest!=value.digest) throw BadRequestException("统计依据已变化，请重新预览并核对实际签署报表")
        if(value.signedReportFileIds.isBlank()) throw BadRequestException("请上传实际签署报表原件")
        val people=setOf(value.statisticianUserId,value.keeperUserId,value.leaderUserId)
        if(people.size!=3) throw BadRequestException("制表人、保管负责人和馆负责人须分别记录")
        people.forEach {users.requireMuseumMember(it,value.orgId)}
        users.requireRole(value.keeperUserId,value.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(value.leaderUserId,value.orgId,CollectionConst.ROLE_DIRECTOR)
        if((report.ledger.issues.isNotEmpty() || report.usageIssues.isNotEmpty() || report.ledger.unknownActualQuantityCount>0 || report.usage.any {it.unknownQuantityCount>0}) &&
            (value.reconciliationNote.isNullOrBlank() || value.reconciliationFileIds.isNullOrBlank()))
            throw BadRequestException("历史统计有缺口，须保留纸质总账、逐件明细和差异核对说明原件；不能把未知数量当作零")
        val id=IdGenerate.nextId()
        val saved=CollectionPeriodReportArchiveVo(id,(previous?.version ?: 0)+1,value,report)
        archives.seal(value.orgId,BIZ,id,"SIGNED",saved,files(value))
        write()
        if(preview(value.startDate,value.endDate,value.orgId).digest!=report.digest) throw BadRequestException("归档期间统计依据变化，本次归档已撤销，请重新核对")
        return id
    }
    private fun files(value:CollectionPeriodReportSignVo)=listOfNotNull(value.signedReportFileIds,value.reconciliationFileIds,value.replacementFileIds).joinToString(",")
    private fun records(org:String,verifyContent:Boolean=true):List<CollectionPeriodReportArchiveVo> = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.orgId eq org) and (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.phase eq "SIGNED")
    }.map { row ->
        val original=(if(verifyContent) archives.phaseForAuthorizedOrg(BIZ,row.bizId,org,"SIGNED") else archives.phaseForDisplay(BIZ,row.bizId,org,"SIGNED")) ?: throw BadRequestException("期间报表永久原件缺失")
        val saved=JsonUtils.fromJson(original["snapshot"] as String,CollectionPeriodReportArchiveVo::class.java) ?: throw BadRequestException("期间报表无法解析")
        if(saved.id!=row.bizId || saved.signed.orgId!=org || saved.calculated.ledger.orgId!=org) throw BadRequestException("期间报表归属不一致")
        archives.verifyExisting(row,org,BIZ,row.bizId,"SIGNED",saved,files(saved.signed),verifyContent=verifyContent)
        saved.copy(evidenceIssue=original["evidenceIssue"] as? String)
    }
}
