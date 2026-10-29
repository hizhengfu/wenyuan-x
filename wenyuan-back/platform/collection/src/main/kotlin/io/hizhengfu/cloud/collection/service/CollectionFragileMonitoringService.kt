package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionFragileMonitoringEvidence as M
import io.hizhengfu.cloud.collection.service.CollectionFragileAutomaticEvidence as A
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class CollectionFragileMonitoringService(
    private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,
    private val archives:CollectionPermanentArchiveService,private val providers:List<CollectionFragileEnvironmentProvider>,
    @param:Lazy private val incidents:CollectionIncidentService,
) {
    private fun collection(id:String,write:Boolean=true,expectedOrgId:String?=null) = CollectionFragilityAccess.collection(scope,id,write,expectedOrgId)
    @Transactional(readOnly=true)
    fun samples(id:String,recordId:String,page:Int,pageSize:Int,automatic:Boolean=false):Map<String,Any?> {
        val c=collection(id,false)
        val (evidence,useId,asOf)=if(automatic) {
            val r=CollectionFragileAutomaticCheckDao.findById(recordId) ?: E.fail("自动核验原件不存在")
            if(r.orgId!=c.orgId || r.collectionId!=id)E.fail("自动核验原件不属于本馆本件藏品")
            A.verify(r);Triple(A.evidence(r),r.useRecordId,r.observedAt)
        } else {
            val r=CollectionFragileMonitoringDao.findById(recordId) ?: E.fail("区间监测原件不存在")
            if(r.orgId!=c.orgId || r.collectionId!=id)E.fail("区间监测原件不属于本馆本件藏品")
            M.verify(r);Triple(M.evidence(r),r.useRecordId,r.request.checkedAt)
        }
        if(page<1 || pageSize<0 || pageSize>200)E.fail("请选择有效页码和每页数量；0 表示明确查询全部")
        val metrics=evidence.path("metrics")
        val total=metrics.sumOf {it.get("samples")?.size()?.toLong() ?: 0L}
        var skip=if(pageSize==0)0L else (page.toLong()-1)*pageSize
        val limit=if(pageSize==0)total else pageSize.toLong()
        val result=mutableListOf<Map<String,Any?>>()
        val use=CollectionFragileUseRecordDao[useId].also(E::record)
        val origins=buildList {
            if(use.environmentArchiveId!=null)use.started?.environmentReadings?.forEach {add(Triple(it,useId,"ENVIRONMENT_VERIFIED"))}
            M.all(id,c.orgId).filter {it.useRecordId==useId && it.id.value<=recordId && it.request.checkedAt<=asOf}.forEach {report->
                M.verify(report)
                report.request.readings.forEach {add(Triple(it,report.id.value,"MONITORED"))}
            }
        }
        for(m in metrics) {
            val rows=m.get("samples") ?: continue
            if(skip>=rows.size()){skip-=rows.size();continue}
            for(index in skip.toInt() until rows.size()) {
                if(result.size.toLong()>=limit)break
                val sample=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(rows[index].toString(),Map::class.java)!!
                val signed=rows[index].get("evidence")?.get("signedOnSite")
                val source=if(signed==null) null else origins.firstOrNull { (reading,_,_) ->
                    io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(E.json(reading))==signed
                }
                result+=sample.entries.associate {it.key.toString() to it.value}+
                    mapOf("metricName" to m.get("metricName").stringValue())+
                    (source?.let {mapOf("originalRecordId" to it.second,"originalPhase" to it.third)} ?: emptyMap())
            }
            skip=0
            if(result.size.toLong()>=limit)break
        }
        return mapOf("total" to total,"list" to result,"page" to page,"pageSize" to pageSize)
    }
    @Transactional(readOnly=true)
    fun automaticStatus(id:String):List<Map<String,Any?>> {
        val c=collection(id,false)
        return E.records(id,c.orgId).filter {it.started!=null}.map {use->E.record(use);mapOf("useRecordId" to use.id.value,"status" to A.runtime(use,LocalDateTime.now()))}
    }
    @Transactional(readOnly=true)
    fun automaticPage(id:String,useId:String,page:Int,pageSize:Int):Map<String,Any?> {
        val c=collection(id,false)
        val use=CollectionFragileUseRecordDao.findById(useId) ?: E.fail("实际使用不存在")
        if(use.orgId!=c.orgId || use.collectionId!=id)E.fail("自动核验来源不属于本馆本件藏品")
        E.record(use)
        if(page<1 || pageSize<0 || pageSize>200)E.fail("请选择有效页码和每页数量；0 表示明确查询全部")
        val rows=CollectionFragileAutomaticCheckDao.find {(CollectionFragileAutomaticChecks.useRecordId eq useId) and (CollectionFragileAutomaticChecks.collectionId eq id) and (CollectionFragileAutomaticChecks.orgId eq c.orgId)}
            .orderBy(CollectionFragileAutomaticChecks.id to SortOrder.DESC)
        val total=rows.count()
        val selected=if(pageSize==0)rows else rows.limit(pageSize).offset((page.toLong()-1)*pageSize)
        return mapOf("total" to total,"page" to page,"pageSize" to pageSize,"list" to selected.map {A.verify(it);A.summary(it)})
    }
    fun inspect(id:String,useId:String,v:FragileEnvironmentMonitoringVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val use=CollectionFragileUseRecordDao.findById(useId) ?: E.fail("实际使用记录不存在")
        if(use.orgId!=c.orgId || use.collectionId!=id || use.started==null)E.fail("须核验本馆本件已实际开始的展出或运输")
        E.record(use)
        val all=M.all(id).filter {it.useRecordId==useId}.also {it.forEach(M::verify)}
        all.firstOrNull {it.request==v}?.let {return it.id.value}
        if(v.operatorUserId!=SecurityUtils.getUserId())E.fail("须由实际核验人员本人登记")
        users.requireMuseumMember(v.operatorUserId,c.orgId)
        if(v.checkedAt>LocalDateTime.now() || v.checkedAt<=use.started!!.startedAt || all.any {it.request.checkedAt>v.checkedAt})E.fail("实际核验时间须在开始之后、不在未来，且不能倒填到已登记核验之前")
        if(listOf(v.actualCustodyLocation,v.condition,v.actualMeasures,v.followUp,v.signedFileIds).any(String::isBlank))E.fail("须完整记录实物现状、实际措施、后续安排和签署原件")
        if(v.physicallyPresent && v.custodianUserId.isNullOrBlank())E.fail("实物在场须核实实际保管人员")
        v.custodianUserId?.let {users.requireMuseumMember(it,c.orgId)}
        val plan=CollectionFragileUsePlanDao[use.planId!!]
        val assessment=CollectionFragilityAssessmentDao[plan.content.assessmentId]
        val previous=all.lastOrNull()
        val previousTo=previous?.let {io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(M.evidence(it).get("to").toString(),LocalDateTime::class.java)!!} ?: use.started!!.startedAt
        val to=minOf(v.checkedAt,use.ended?.endedAt ?: v.checkedAt)
        val from=minOf(previousTo,to)
        val criteria=assessment.content.environmentCriteria.filter {it.kind==use.started!!.kind}
        val sources=plan.content.environmentSources.filter {it.kind==use.started!!.kind && it.segmentIndex==use.started!!.segmentIndex}
        val general=mutableListOf<String>()
        if(to<previousTo)general+="后来登记的实际结束早于此前已核验区间，须调查记录矛盾并保留全部原件"
        if(v.readings.any {it.measuredAt>v.checkedAt || it.recordedAt?.let {at->at>v.checkedAt || at<it.measuredAt}==true})general+="新提交测量原件的形成时间与实际采样或本次核验存在矛盾"
        if(to>from && (criteria.isEmpty() || sources.map {it.metricCode}.toSet()!=criteria.map {it.metricCode}.toSet() || sources.size!=criteria.size))general+="原使用缺少完整的专业数值要求或逐项测量来源，不能认定监测证据完整"
        if(v.readings.any {r->sources.none {it.metricCode==r.metricCode && it.type==EnumFragileEnvironmentSource.SIGNED_ON_SITE}})general+="存在未对应本段批准现场来源的测量原件"
        val (metrics,periodIssues)=CollectionFragilePeriodEvidence.evaluate(use,from,to,
            all.flatMap {row->row.request.readings.map {it to row.request.checkedAt}}+v.readings.map {it to v.checkedAt},providers)
        general+=periodIssues.filterNot {it in general}
        val body=mapOf("previousMonitoringId" to previous?.id?.value,"catalog" to assessment.catalog,"from" to from,"to" to to,"metrics" to metrics,"issues" to general,
            "needsHandling" to (general.isNotEmpty() || metrics.any {it.needsHandling} || v.damaged || !v.physicallyPresent),
            "scope" to "按采样及接收或原件记录时间核验本次新增区间，前期记录独立保留；起止相同时仅核实现状。不证明未采样时刻的实物环境持续合格，不自动停止实际展出或运输")
        val r=CollectionFragileMonitoringDao.new {orgId=c.orgId;collectionId=id;useRecordId=useId;request=v;evidenceSnapshot=E.json(body)}
        archives.seal(c.orgId,E.BIZ,r.id.value,"MONITORED",M.value(r),(listOf(v.signedFileIds)+v.readings.map {it.signedFileIds}).joinToString(","))
        return CollectionFragilityAccess.finish(scope,c,r.id.value,expectedOrgId)
    }
    fun clear(id:String,recordId:String,v:IncidentClearanceVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val r=M.all(id).find {it.id.value==recordId && it.orgId==c.orgId} ?: E.fail("使用期间监测核验不存在")
        M.verify(r)
        val automatic=A.forUse(r.useRecordId).filter {it.needsHandling}.also {it.forEach(A::verify)}
        if(!M.unsafe(r) && automatic.isEmpty())E.fail("本记录及本次使用没有待处理的监测或实物异常")
        if(v.keeperUserId!=SecurityUtils.getUserId() || v.keeperUserId==v.conservationUserId)E.fail("须由保管负责人本人核对，保护修复责任人员应分别履责")
        listOf(v.keeperUserId to CollectionConst.ROLE_STORAGE_HEAD,v.conservationUserId to CollectionConst.ROLE_CONSERVATION).forEach {(who,role)->users.requireMuseumMember(who,c.orgId);users.requireRole(who,c.orgId,role)}
        if(v.confirmedAt>LocalDateTime.now() || v.confirmedAt<r.request.checkedAt || r.clearance?.record?.confirmedAt?.let {v.confirmedAt<it}==true || c.inventoryHold)E.fail("处置核对时间或本件其他限制尚不满足")
        if(listOf(v.condition,v.actualMeasures,v.signedFileIds).any(String::isBlank))E.fail("须据实记录恢复现状、处理措施和签署原件")
        val use=CollectionFragileUseRecordDao[r.useRecordId]
        if(use.ended==null)E.fail("须先如实登记实际展出或运输停止结束，不能仅用正常测值解除限制")
        val pending=M.all(id).filter {it.useRecordId==r.useRecordId && M.unsafe(it)}.also {it.forEach(M::verify)}
        if(pending.any {it.request.checkedAt>v.confirmedAt})E.fail("处置核对不得早于本次使用的最新异常记录")
        if(automatic.any {it.observedAt>v.confirmedAt})E.fail("处置核对不得早于本次使用的最新自动异常")
        if(automatic.isNotEmpty()) {
            val runtime=CollectionFragileMonitorRuntimeDao.findById(use.id)
            val last=A.forUse(use.id.value).lastOrNull()?.also(A::verify)
            if(runtime?.completed!=true || runtime.lastError!=null || last==null || last.toAt<use.ended!!.endedAt)E.fail("本次实际使用尚未自动核验至真实结束，请处理核验失败或等待调度完成")
        }
        val proof=incidents.requireClearance(v.incidentId,c.orgId,id,IncidentSourceVo(E.BIZ,id,r.useRecordId),v.confirmedAt,keeperUserId=v.keeperUserId)
        automatic.forEach {row->
            if(row.clearance?.record!=v || row.clearance?.incidentProof!=proof) {
                val saved=CollectionCareClearanceSnapshot(v,proof,(row.clearance?.version ?: 0)+1)
                row.clearance=saved;archives.seal(c.orgId,E.BIZ,row.id.value,"AUTO_CLEARED_${saved.version}",saved,v.signedFileIds)
            }
        }
        pending.forEach {row->
            if(row.clearance?.record!=v || row.clearance?.incidentProof!=proof) {
                val saved=CollectionCareClearanceSnapshot(v,proof,(row.clearance?.version ?: 0)+1)
                row.clearance=saved;archives.seal(c.orgId,E.BIZ,row.id.value,"MONITOR_CLEARED_${saved.version}",saved,v.signedFileIds)
            }
        }
        return CollectionFragilityAccess.finish(scope,c,recordId,expectedOrgId)
    }
}
