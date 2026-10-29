package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyEvidence as D

internal data class EmergencyReportState(
    val id:String,val version:Int,val content:CollectionEmergencyDrillVo,
    val evaluator:CollectionCareSignature?,val director:CollectionCareSignature?,
    val revision:CollectionEmergencyDrillRevisionDao?=null,
)

internal object CollectionEmergencyEvidence {
    const val BIZ="COLLECTION_EMERGENCY"
    val reportScenarios=setOf(EnumEmergencyScenario.DAMAGE,EnumEmergencyScenario.THEFT,EnumEmergencyScenario.ROBBERY,EnumEmergencyScenario.LOSS)
    fun plans(org:String)=CollectionEmergencyPlanDao.find {CollectionEmergencyPlans.orgId eq org}.sortedBy {it.version}
    fun drills(org:String)=CollectionEmergencyDrillDao.find {CollectionEmergencyDrills.orgId eq org}.sortedBy {it.content.endedAt}
    fun improvements(id:String)=CollectionEmergencyImprovementDao.find {CollectionEmergencyImprovements.drillId eq id}.sortedBy {it.version}
    fun planValue(p:CollectionEmergencyPlanDao)=mapOf("version" to p.version,"content" to p.content)
    fun canonical(v:CollectionEmergencyDrillVo)=v.copy(scenarios=v.scenarios.sortedBy {it.name}.toCollection(linkedSetOf()),participantUserIds=v.participantUserIds.sorted().toCollection(linkedSetOf()),requisitionIds=v.requisitionIds.sorted().toCollection(linkedSetOf()),actualIncidentIds=v.actualIncidentIds.sorted().toCollection(linkedSetOf()))
    fun drillValue(d:CollectionEmergencyDrillDao)=mapOf("planId" to d.planId,"content" to canonical(d.content),"operationSnapshot" to d.operationSnapshot)
    fun revisions(id:String)=CollectionEmergencyDrillRevisionDao.find {CollectionEmergencyDrillRevisions.drillId eq id}.sortedBy {it.version}
    fun reports(d:CollectionEmergencyDrillDao)=listOf(EmergencyReportState(d.id.value,1,d.content,d.evaluator,d.director))+revisions(d.id.value).map {EmergencyReportState(it.id.value,it.version,it.record.request.report,it.evaluator,it.director,it)}
    fun report(d:CollectionEmergencyDrillDao)=reports(d).last()
    fun revisionValue(r:CollectionEmergencyDrillRevisionDao)=linkedMapOf<String,Any>("drillId" to r.drillId,"version" to r.version,"record" to r.record.copy(request=r.record.request.copy(report=canonical(r.record.request.report))),"operationSnapshot" to r.operationSnapshot).apply {r.recorderDutyId?.let {put("recorderDutyId",it)}}
    fun improvementValue(i:CollectionEmergencyImprovementDao)=linkedMapOf<String,Any>("drillId" to i.drillId,"issueNumber" to i.issueNumber,"version" to i.version,"content" to i.content).apply {
        // 旧原件没有这些字段，保持原 JSON 与摘要；新增原件固定实际办理依据。
        i.reportRevisionId?.let {put("reportRevisionId",it)};i.issueBasis?.let {put("issueBasis",it)};i.ownerDutyId?.let {put("ownerDutyId",it)}
    }
    fun issueBasis(v:CollectionEmergencyDrillVo,number:Int)=C.digest(C.json(mapOf("planId" to v.planId,"endedAt" to v.endedAt,"issue" to (v.issues.getOrNull(number-1) ?: C.fail("实际演练问题不存在")))))
    fun basisMatches(d:CollectionEmergencyDrillDao,i:CollectionEmergencyImprovementDao)=
        (i.issueBasis ?: issueBasis(d.content,i.issueNumber))==issueBasis(report(d).content,i.issueNumber)
    fun closed(d:CollectionEmergencyDrillDao,i:CollectionEmergencyImprovementDao?)=i!=null && i.review?.record?.accepted==true && basisMatches(d,i)
    private fun archive(org:String,id:String,phase:String)=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.singleOrNull()?.also {if(it.orgId!=org || it.digest!=C.digest(it.snapshot))C.fail("应急预案及演练永久原件不一致")}
    fun exact(org:String,id:String,phase:String,value:Any?,files:String?) {
        val a=archive(org,id,phase)
        if(value==null) {if(a!=null)C.fail("应急预案及演练已归档原件不能置空");return}
        if(a==null || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(C.json(value)))C.fail("应急预案及演练缺少一致的永久原件：$phase")
        CollectionEmergencyOriginalEvidence.require(a,files)
    }
    fun planSigns(p:CollectionEmergencyPlanDao)=listOf(Triple("KEEPER",p.content.keeperUserId,p.keeper),Triple("SECURITY",p.content.securityUserId,p.security),Triple("DIRECTOR",p.content.directorUserId,p.director))
    fun plan(p:CollectionEmergencyPlanDao) {
        exact(p.orgId,p.id.value,"ISSUED",planValue(p),listOfNotNull(p.content.originalFileIds,p.content.replacementFileIds).joinToString(","))
        planSigns(p).forEach {(role,actor,s)->dutySignature(p.orgId,p.id.value,role,EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,EnumEmergencyDutyRole.valueOf(role)),actor,s,planDutyId(p,role))}
    }
    fun planDutyId(p:CollectionEmergencyPlanDao,role:String)=when(role){"KEEPER"->p.keeperDutyId;"SECURITY"->p.securityDutyId;"DIRECTOR"->p.directorDutyId;else->C.fail("预案职责不存在")}
    private fun dutySignature(org:String,id:String,phase:String,target:EmergencyDutyTarget,original:String,s:CollectionCareSignature?,dutyId:String?) {
        exact(org,id,phase,D.signatureValue(s,dutyId),s?.record?.fileIds)
        s?.let {D.requireActor(org,target,original,it.actorUserId,it.confirmedAt,dutyId)}
    }
    fun confirmed(p:CollectionEmergencyPlanDao)=planSigns(p).all {it.third!=null}
    fun applicable(org:String,at:LocalDateTime)=applicable(plans(org),at)
    fun applicable(all:List<CollectionEmergencyPlanDao>,at:LocalDateTime)=all.lastOrNull {it.content.effectiveAt<=at && it.content.issuedAt<=at}
    fun operations(org:String,v:CollectionEmergencyDrillVo):String=C.json(v.requisitionIds.sorted().map {id->
        val r=RequisitionDao.findById(id) ?: C.fail("演练关联的实际提用不存在")
        if(r.orgId!=org)C.fail("演练不能关联外馆提用")
        CollectionJointSafetyEvidence.outbound(r)
        if(r.handover!!.content.handedAt.isAfter(v.startedAt))C.fail("所关联原件须在实际演练开始前已点交")
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED")}.single()
        mapOf("requisitionId" to id,"archiveId" to a.id.value,"digest" to a.digest)
    })
    fun drill(d:CollectionEmergencyDrillDao) {
        val p=CollectionEmergencyPlanDao.findById(d.planId) ?: C.fail("演练所依据的预案不存在")
        if(d.orgId!=p.orgId || d.content.planId!=p.id.value)C.fail("演练与预案归属不一致")
        plan(p);exact(d.orgId,d.id.value,"DRILL",drillValue(d),reportFiles(d.content))
        if(operations(d.orgId,d.content)!=d.operationSnapshot)C.fail("演练关联的实际提用原件发生变化")
        dutySignature(d.orgId,d.id.value,"EVALUATOR",EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,EnumEmergencyDutyRole.EVALUATOR),d.content.evaluatorUserId,d.evaluator,d.evaluatorDutyId)
        dutySignature(d.orgId,d.id.value,"DIRECTOR",EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,EnumEmergencyDutyRole.DIRECTOR),drillDirector(d.orgId,d.content),d.director,d.directorDutyId)
        if(d.director!=null && d.evaluator==null)C.fail("演练实际评估及主要负责人确认顺序不一致")
        var previous=EmergencyReportState(d.id.value,1,d.content,d.evaluator,d.director)
        val rs=revisions(d.id.value)
        rs.forEach {r->
            val v=r.record.request.report
            if(r.orgId!=d.orgId || r.version!=previous.version+1 || r.record.request.expectedReportId!=previous.id)C.fail("演练报告订正版本不连续")
            retained(previous.content,v)
            exact(d.orgId,r.id.value,"DRILL_REVISION",revisionValue(r),reportFiles(v)+","+r.record.request.fileIds)
            val rp=CollectionEmergencyPlanDao.findById(v.planId) ?: C.fail("订正报告预案不存在")
            if(rp.orgId!=d.orgId)C.fail("订正报告不能引用外馆预案")
            plan(rp)
            if(operations(d.orgId,v)!=r.operationSnapshot)C.fail("订正报告关联的实物操作原件发生变化")
            D.requireActor(d.orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,EnumEmergencyDutyRole.RECORDER),d.content.conductorUserId,r.record.submittedBy,r.record.submittedAt,r.recorderDutyId)
            dutySignature(d.orgId,r.id.value,"EVALUATOR",EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,EnumEmergencyDutyRole.EVALUATOR),v.evaluatorUserId,r.evaluator,r.evaluatorDutyId)
            dutySignature(d.orgId,r.id.value,"DIRECTOR",EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,EnumEmergencyDutyRole.DIRECTOR),drillDirector(d.orgId,v),r.director,r.directorDutyId)
            if(r.director!=null && r.evaluator==null)C.fail("订正报告提交、评估或确认职责不一致")
            previous=EmergencyReportState(r.id.value,r.version,v,r.evaluator,r.director,r)
        }
        val archived=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.orgId eq d.orgId) and (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.phase eq "DRILL_REVISION")}.onEach {if(it.digest!=C.digest(it.snapshot))C.fail("演练报告订正永久原件摘要不一致")}.filter {JsonUtils.mapper.readTree(it.snapshot)["drillId"]?.asText()==d.id.value}.map {it.bizId}.toSet()
        if(archived!=rs.map {it.id.value}.toSet())C.fail("演练报告订正原件不能删除或脱离本次演练")
    }
    fun retained(old:CollectionEmergencyDrillVo,v:CollectionEmergencyDrillVo) {
        if(v.conductorUserId!=old.conductorUserId)C.fail("订正不能改变本次实际组织人员；责任承接应另行办理")
        if(v.issues.size<old.issues.size || v.issues.take(old.issues.size)!=old.issues)C.fail("订正须保留原问题与顺序，新发现仅能追加")
        if(!v.actualIncidentIds.containsAll(old.actualIncidentIds) || !v.requisitionIds.containsAll(old.requisitionIds))C.fail("订正不能删除已关联真实事故或实物操作原件")
    }
    fun drillDirector(org:String,v:CollectionEmergencyDrillVo):String {
        val p=CollectionEmergencyPlanDao[v.planId]
        return D.actor(org,EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,EnumEmergencyDutyRole.DIRECTOR),p.content.directorUserId,v.startedAt)
    }
    fun counted(d:CollectionEmergencyDrillDao,all:List<CollectionEmergencyPlanDao> = plans(d.orgId)):Boolean {
        val r=report(d);val p=all.find {it.id.value==r.content.planId} ?: return false
        return applicable(all,r.content.startedAt)?.id==p.id && confirmed(p) && r.evaluator!=null && r.director!=null
    }
    private fun reportFiles(v:CollectionEmergencyDrillVo)=listOfNotNull(v.attendanceFileIds,v.signedReportFileIds,v.originalOperationFileIds).joinToString(",")
    fun improvement(i:CollectionEmergencyImprovementDao) {
        exact(i.orgId,i.id.value,"IMPROVEMENT",improvementValue(i),i.content.fileIds)
        exact(i.orgId,i.id.value,"REVIEW",if(i.reviewDutyId==null)i.review else i.review?.let {mapOf("review" to it,"dutyId" to i.reviewDutyId)},i.review?.record?.fileIds)
        if(i.content.issueNumber!=i.issueNumber || i.review==null && i.reviewDutyId!=null)C.fail("整改复核与实际问题或指定人员不一致")
        i.review?.let {D.requireActor(i.orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,i.id.value,EnumEmergencyDutyRole.REVIEWER),i.content.verifierUserId,it.actorUserId,it.confirmedAt,i.reviewDutyId)}
        val d=CollectionEmergencyDrillDao.findById(i.drillId) ?: C.fail("整改所属演练不存在")
        if(d.orgId!=i.orgId || (i.reportRevisionId==null)!=(i.issueBasis==null))C.fail("整改所属馆或报告依据不一致")
        val source=reports(d).find {it.id==(i.reportRevisionId ?: d.id.value)} ?: C.fail("整改依据的报告原件不存在")
        val originalOwner=source.content.issues.getOrNull(i.issueNumber-1)?.ownerUserId ?: C.fail("整改原问题不存在")
        D.requireActor(i.orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d.id.value,EnumEmergencyDutyRole.OWNER,i.issueNumber),originalOwner,i.content.ownerUserId,i.content.completedAt,i.ownerDutyId)
        if(i.reportRevisionId!=null) {
            val r=reports(d).find {it.id==i.reportRevisionId} ?: C.fail("整改依据的报告原件不存在")
            if(i.issueBasis!=issueBasis(r.content,i.issueNumber))C.fail("整改问题依据与永久原件不一致")
        }
    }
    fun due(p:CollectionEmergencyPlanDao,s:EmergencyScenarioPlanVo,allPlans:List<CollectionEmergencyPlanDao>,allDrills:List<CollectionEmergencyDrillDao>):LocalDateTime {
        val actual=allDrills.filter {counted(it,allPlans)}.map(::report).filter {s.scenario in it.content.scenarios}
        val last=actual.maxOfOrNull {it.content.endedAt}
        val history=allPlans.filter {it.version<=p.version}
        // 修订预案不能把尚未真实演练的旧欠期滚动到未来。
        if(last==null)return history.flatMap {it.content.scenarios}.filter {it.scenario==s.scenario}.minOf {it.firstDueAt}
        var deadline=last.plusDays(s.drillIntervalDays.toLong())
        history.zipWithNext().forEach {(old,next)->
            val previous=old.content.scenarios.find {it.scenario==s.scenario} ?: return@forEach
            val before=actual.filter {it.content.endedAt<=next.content.effectiveAt}.maxOfOrNull {it.content.endedAt}
            val missed=before?.plusDays(previous.drillIntervalDays.toLong()) ?: previous.firstDueAt
            if(missed<=next.content.effectiveAt && last<missed)deadline=minOf(deadline,missed)
        }
        return deadline
    }
}
