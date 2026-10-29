package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import tools.jackson.databind.JsonNode

data class WarehouseEntryTimelineConflict(val archiveId:String,val businessType:String,val startedAt:LocalDateTime,val operatorUserId:String,val diaryId:String,val digest:String)
data class WarehouseEntryTimelineDiary(val archiveId:String,val diaryId:String,val digest:String)
data class WarehouseEntryTimelineView(val baseline:String,val conflicts:List<WarehouseEntryTimelineConflict>,val evidenceErrors:List<String>,val reviews:List<Map<String,Any?>>,val diaryEvidence:List<WarehouseEntryTimelineDiary>)
data class WarehouseEntryTimelineReviewVo(val requestKey:String,val baseline:String,val reviewedAt:LocalDateTime,val comparedOriginals:Boolean,val statement:String,val followUp:String,val fileIds:String)

/** 冲突是待核实线索，不自动推定事故、改写历史时刻或把材料登记视为已解决。 */
@Component
@Transactional
class WarehouseEntryTimelineService(private val scope:CollectionScopeResolver,private val directory:CollectionActorDirectory,private val archives:CollectionPermanentArchiveService) {
    companion object {
        const val PHASE="ONSITE_TIME_REVIEW_"
        val READ_PERMISSIONS=setOf(CollectionConst.ADMIN,CollectionConst.WAREHOUSE_ENTRY_VERIFY)
    }
    private val kinds=setOf(CollectionConst.BIZ_CARE,ReproductionComplianceService.BIZ,DigitalAcquisitionService.BIZ,CollectionConst.BIZ_SAMPLING)
    private fun authorized(id:String,write:Boolean):WarehouseEntryApplicationDao {
        val user=directory.requireActive(SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后核对实际进出记录"))
        if(EnumRole.ADMIN.value !in user.roles && user.permissions.none {it in READ_PERMISSIONS})fail("缺少人员进出核验权限")
        val row=WarehouseEntryApplicationDao.findById(id) ?: fail("人员进出记录不存在")
        if(!scope.isMulti() && row.orgId!=scope.currentOrgId())fail("人员进出核查不属于当前配置收藏单位")
        if(CollectionOrgDao.findById(row.orgId)==null)fail("人员进出核查所属收藏单位不存在")
        val visible=if(write)scope.currentWritableOrgIds(user) else scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG)
        OrgScopedReadPolicy.requireVisible(visible,row.orgId,"人员进出时间核查")
        return row
    }
    private fun parse(text:String):JsonNode=try {JsonUtils.mapper.readTree(text)} catch (_:tools.jackson.core.JacksonException){fail("相关原件无法解析，请核实历史材料")}
    private fun time(node:JsonNode):LocalDateTime=try {JsonUtils.mapper.treeToValue(node,LocalDateTime::class.java)} catch (_:Exception){fail("相关原件时间无法核实")}
    private fun millis(value:LocalDateTime)=value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
    /** 按主键分批读取同馆永久原件，覆盖已完成操作及后来修改的业务行，不用可变计划抹去历史。 */
    private fun scan(orgId:String,types:Set<String>,phase:String?,consume:(String,String,String,String,String,String)->Unit) {
        val a=CollectionPermanentArchives;var after:String?=null
        while(true) {
            val rows=a.select(a.id,a.bizType,a.bizId,a.phase,a.snapshot,a.digest).where {
                (a.orgId eq orgId) and (a.bizType inList types) and
                    (phase?.let {a.phase eq it} ?: Op.TRUE) and (after?.let {a.id greater it} ?: Op.TRUE)
            }.orderBy(a.id).limit(200).toList()
            if(rows.isEmpty())break
            rows.forEach {r->consume(r[a.id].value,r[a.bizType],r[a.bizId],r[a.phase],r[a.snapshot],r[a.digest])}
            after=rows.last()[a.id].value
        }
    }
    private fun inspect(row:WarehouseEntryApplicationDao,current:ResultRow?=null):WarehouseEntryTimelineView {
        val table=WarehouseEntryApplications
        val enteredAt=if(current==null)row.actualEntryAt else current[table.actualEntryAt]
        val exitedAt=if(current==null)row.actualExitAt else current[table.actualExitAt]
        val entry=if(current==null)row.entryRecord else current[table.entryRecord]
        val exit=if(current==null)row.exitRecord else current[table.exitRecord]
        val errors=linkedSetOf<String>();val visits=linkedMapOf<String,MutableList<JsonNode>>()
        val exitAt=exitedAt?.let(::millis)
        val conflicts=mutableListOf<WarehouseEntryTimelineConflict>()
        val diaryEvidence=mutableListOf<WarehouseEntryTimelineDiary>()
        if(exitAt!=null) {
            scan(row.orgId,setOf(WarehouseDiaryService.BIZ),null) {archiveId,_,bizId,phase,snapshot,digest->
                if(phase.startsWith("RECORDED_")) {
                    try {
                        val data=parse(snapshot)
                        val related=data.path("entries").path("items").filter {it.path("applicationId").asString()==row.id.value}
                        if(related.isNotEmpty()) {
                            if(digest!=CollectionCareEvidence.digest(snapshot))errors+="相关库房日记原件摘要不一致"
                            else {
                                visits.getOrPut(bizId){mutableListOf()}.addAll(related)
                                diaryEvidence+=WarehouseEntryTimelineDiary(archiveId,bizId,digest)
                            }
                        }
                    } catch (_:BadRequestException){errors+="部分同馆库房日记原件无法核实"}
                }
            }
            scan(row.orgId,kinds,"STARTED") {id,biz,_,_,snapshot,digest->
                try {
                    val document=parse(snapshot);val started=if(biz==CollectionConst.BIZ_CARE)document.path("started") else document
                    val diaryId=started.path("onSiteWarehouseLogId").asString();val candidates=visits[diaryId].orEmpty()
                    if(candidates.isNotEmpty()) {
                        val operator=started.path("operatorUserId").asString()
                        val related=candidates.any {v->(v.path("entry").path("entrantUserIds").toList()+v.path("entry").path("escortUserIds").toList()).any {it.asString()==operator}}
                        if(related) {
                            if(digest!=CollectionCareEvidence.digest(snapshot))errors+="相关原件操作档案摘要不一致"
                            else {
                                val at=millis(time(started.path("startedAt")))
                                if(!at.isBefore(exitAt))conflicts+=WarehouseEntryTimelineConflict(id,biz,at,operator,diaryId,digest)
                            }
                        }
                    }
                } catch (_:BadRequestException){errors+="部分同馆原件操作档案无法核实"}
            }
        }
        val originals=try { archives.listForAuthorizedOrg(CollectionConst.BIZ_WAREHOUSE_ENTRY,row.id.value,row.orgId) }
            catch (_:BadRequestException) { errors+="已登记核查材料原件无法核实"; emptyList() }
        val reviews=originals.filter {it["phase"].toString().startsWith(PHASE)}.filter {item->
            val record=CollectionPermanentArchiveDao.findById(item["id"].toString())
            val valid=try {
                record!=null && record.orgId==row.orgId && record.digest==CollectionCareEvidence.digest(record.snapshot) &&
                    parse(record.snapshot).path("kind").asString()=="ONSITE_TIME_REVIEW" && parse(record.snapshot).path("entryId").asString()==row.id.value
            } catch (_:BadRequestException){false}
            if(!valid)errors+="已登记核查材料原件无法核实"
            valid
        }
        val sorted=conflicts.sortedWith(compareBy({it.startedAt},{it.archiveId}))
        val diaries=diaryEvidence.sortedBy {it.archiveId}
        val baseline=CollectionCareEvidence.digest(JsonUtils.toJson(mapOf("entryId" to row.id.value,"orgId" to row.orgId,"entry" to entry,"enteredAt" to enteredAt,"exit" to exit,"exitedAt" to exitedAt,"conflicts" to sorted,"diaryEvidence" to diaries,"errors" to errors.toList()))!!)
        val markedReviews=reviews.map {it+("baselineCurrent" to (parse(it["snapshot"].toString()).path("content").path("baseline").asString()==baseline))}
        return WarehouseEntryTimelineView(baseline,sorted,errors.toList(),markedReviews,diaries)
    }
    fun get(id:String)=inspect(authorized(id,false))
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if (listOf(id,archiveId,fileId).any { it.isBlank() || it.length>100 }) fail("时间核查归档原件标识不正确")
        val review=get(id).reviews.singleOrNull { it["id"]==archiveId }
            ?: fail("该核查原件不属于当前进出记录")
        @Suppress("UNCHECKED_CAST")
        val files=review["files"] as? List<Map<String,Any?>> ?: fail("时间核查归档文件清单无效")
        if (files.none { it["fileId"]==fileId }) fail("该文件不是所选时间核查永久档案的原件")
    }
    fun review(id:String,value:WarehouseEntryTimelineReviewVo):String {
        val row=authorized(id,true)
        // 先与日记签署/离库协调同馆锁，再锁进出行；顺序与现有人员办理一致。
        CollectionLocationMutation.acquire(row.orgId)
        // 读取锁定后的实际值，不改写人员进出业务行。
        val table=WarehouseEntryApplications
        val current=table.selectAll().where {table.id eq id}.forUpdate().singleOrNull() ?: fail("人员进出记录不存在")
        if(current[table.orgId]!=row.orgId)fail("人员进出记录所属馆已变化，请重新核实")
        val view=inspect(row,current)
        if(value.baseline!=view.baseline)fail("关联时间或原件已经变化，请刷新后重新核查")
        if(view.evidenceErrors.isNotEmpty())fail("相关原件尚未核实完整，不能登记为已核查")
        if(view.conflicts.isEmpty())fail("当前没有需要登记核查材料的时间冲突")
        if(!Regex("[A-Za-z0-9_-]{8,60}").matches(value.requestKey) || !value.comparedOriginals || value.statement.isBlank() || value.followUp.isBlank() || value.fileIds.isBlank())fail("请逐项核对原件，填写核查情况、后续处置及真实签署材料")
        if(value.reviewedAt.isAfter(LocalDateTime.now()) || value.reviewedAt.isBefore(view.conflicts.maxOf {it.startedAt}))fail("请填写核对这些原始记录之后的实际核查时间")
        authorized(id,true) // 分批核对耗时期间，实际账号资格可能变化；归档前再核对。
        // phase 列为 64 字符；摘要后新阶段固定 59 字符，并识别历史短阶段的同一请求。
        val legacy=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_WAREHOUSE_ENTRY) and
                (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq PHASE+value.requestKey)
        }.singleOrNull()?.takeIf {parse(it.snapshot).path("content").path("requestKey").asString()==value.requestKey}
        val phase=legacy?.phase ?: PHASE+CollectionCareEvidence.digest(value.requestKey).take(40)
        return archives.seal(row.orgId,CollectionConst.BIZ_WAREHOUSE_ENTRY,id,phase,
            mapOf("kind" to "ONSITE_TIME_REVIEW","entryId" to id,"conflicts" to view.conflicts,"diaryEvidence" to view.diaryEvidence,"content" to value,"recordedBy" to SecurityUtils.getUserDetail().id),value.fileIds)
    }
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
