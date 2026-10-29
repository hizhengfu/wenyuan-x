package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import io.hizhengfu.cloud.collection.service.CollectionEmergencyEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyEvidence as D

@Component
@Transactional
class CollectionEmergencyService(
    private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,
    private val archives:CollectionPermanentArchiveService,private val incidents:CollectionIncidentService,
    private val duties:CollectionEmergencyDutyService=CollectionEmergencyDutyService(scope,users,archives),
) {
    private val access=CollectionEmergencyAccess(scope)
    private fun org(id:String,write:Boolean=false):CollectionOrgDao {
        access.require(id,write)
        return (if(write)CollectionOrgDao.find {CollectionOrgs.id eq id}.forUpdate().singleOrNull() else CollectionOrgDao.findById(id)) ?: C.fail("收藏单位不存在")
    }
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    private fun required(vararg values:String){if(values.any(String::isBlank))C.fail("请完整填写实际预案、履职事实及原件")}
    private fun plan(org:String,id:String)=(CollectionEmergencyPlanDao.findById(id) ?: C.fail("应急预案不存在")).also {if(it.orgId!=org)C.fail("应急预案不属于本馆");E.plan(it)}
    private fun drill(org:String,id:String)=(CollectionEmergencyDrillDao.findById(id) ?: C.fail("实际演练不存在")).also {if(it.orgId!=org)C.fail("演练不属于本馆");E.drill(it)}
    private fun people(org:String,v:CollectionEmergencyPlanVo) {
        val ids=listOf(v.keeperUserId,v.securityUserId,v.directorUserId)
        if(ids.toSet().size!=ids.size)C.fail("保管、安全保卫、主要负责人须分别履职")
        ids.forEach {users.requireMuseumMember(it,org)}
        users.requireRole(v.keeperUserId,org,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(v.securityUserId,org,CollectionConst.ROLE_SECURITY)
        users.requireRole(v.directorUserId,org,CollectionConst.ROLE_DIRECTOR)
    }
    private val emergencyArchive=CollectionEmergencyArchive(archives,access)
    private fun seal(org:String,id:String,phase:String,value:Any,files:String?)=emergencyArchive.seal(org,id,phase,value,files)
    private fun responsible(org:String,t:EmergencyDutyTarget,original:String,qualification:EnumEmergencyDutyQualification):Pair<String,String?> {
        val transfer=D.active(org,t,now());val actor=transfer?.appointment?.request?.toUserId ?: original
        if(SecurityUtils.getUserId()!=actor)C.fail("须由当前指定责任人本人办理，接任人员须先完成责任承接，管理员不能代签")
        duties.qualified(org,actor,transfer?.appointment?.request?.qualification ?: qualification)
        duties.independent(org,t,duties.context(org,t),actor)
        return actor to transfer?.id?.value
    }
    private fun planActors(org:String,p:CollectionEmergencyPlanDao,at:LocalDateTime=now())=listOf(
        EnumEmergencyDutyRole.KEEPER to p.content.keeperUserId,EnumEmergencyDutyRole.SECURITY to p.content.securityUserId,EnumEmergencyDutyRole.DIRECTOR to p.content.directorUserId,
    ).associate {(role,original)->role to D.actor(org,EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,role),original,at)}
    fun registerPlan(orgId:String,v:CollectionEmergencyPlanVo):String {
        org(orgId,true);val ps=E.plans(orgId);ps.forEach(E::plan)
        ps.lastOrNull()?.takeIf {it.content==v}?.let {return it.id.value}
        if(v.previousPlanId!=ps.lastOrNull()?.id?.value)C.fail("预案版本已变化，请核对最新原件")
        required(v.title,v.documentNo,v.commandAndDuties,v.warningAndActivation,v.emergencyContacts,v.siteProtectionAndTransfer,v.resourcesAndSupport,v.firstGradeDamageReporting,v.otherRelicDamageReporting,v.theftRobberyLossReporting,v.recoveryAndReview,v.originalFileIds)
        if(v.issuedAt.isAfter(now()) || v.effectiveAt.isBefore(v.issuedAt))C.fail("请据实登记已印发预案及其生效时间，不得虚填未来印发或倒置时序")
        if(ps.isNotEmpty() && (v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank() || v.issuedAt.isBefore(ps.last().content.issuedAt) || v.effectiveAt.isBefore(ps.last().content.effectiveAt)))C.fail("修订须保留实际修订原因、原件和连续版本时序")
        if(v.scenarios.isEmpty() || v.scenarios.map {it.scenario}.toSet().size!=v.scenarios.size)C.fail("请按实际风险设置不重复的演练情景与周期")
        v.scenarios.forEach {s->required(s.objectives);if(s.drillIntervalDays !in 1..3650 || s.firstDueAt.isBefore(v.effectiveAt) || s.firstDueAt.isAfter(v.effectiveAt.plusDays(s.drillIntervalDays.toLong())))C.fail("每个情景须明确真实评估的周期，首次期限应在生效后一个周期内")}
        people(orgId,v)
        val p=CollectionEmergencyPlanDao.new {this.orgId=orgId;version=(ps.lastOrNull()?.version ?: 0)+1;content=v}
        seal(orgId,p.id.value,"ISSUED",E.planValue(p),listOfNotNull(v.originalFileIds,v.replacementFileIds).joinToString(","));return p.id.value
    }
    fun confirmPlan(orgId:String,id:String,role:String,v:CollectionCareSignatureVo):String {
        org(orgId,true);val p=plan(orgId,id);required(v.opinion,v.fileIds)
        val s=E.planSigns(p).find {it.first==role} ?: C.fail("预案核对职责不存在")
        val (actor,dutyId)=responsible(orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,id,EnumEmergencyDutyRole.valueOf(role)),s.second,EnumEmergencyDutyQualification.valueOf(role))
        s.third?.let {if(it.actorUserId==actor && it.record==v)return id;C.fail("预案本人核对原件不能覆盖，接任仅承接后续职责")}
        if(role=="DIRECTOR" && (p.keeper==null || p.security==null))C.fail("请先由保管和安全保卫负责人核对原件")
        val signature=CollectionCareSignature(actor,now(),v)
        when(role){"KEEPER"->{p.keeper=signature;p.keeperDutyId=dutyId};"SECURITY"->{p.security=signature;p.securityDutyId=dutyId};"DIRECTOR"->{p.director=signature;p.directorDutyId=dutyId}}
        seal(orgId,id,role,D.signatureValue(signature,dutyId)!!,v.fileIds);return id
    }
    private fun validateReport(orgId:String,v:CollectionEmergencyDrillVo,p:CollectionEmergencyPlanDao,previous:CollectionEmergencyDrillVo?=null) {
        if(v.startedAt.isBefore(p.content.effectiveAt) || v.startedAt.isBefore(p.content.issuedAt) || v.endedAt.isBefore(v.startedAt) || v.endedAt.isAfter(now()))C.fail("演练须引用实际开始前已印发并生效的预案，据实填写时间")
        if(E.applicable(orgId,v.startedAt)?.id!=p.id && !v.hasIssues)C.fail("实际使用了过期预案须如实登记问题，该次演练不会更新周期")
        if(v.scenarios.isEmpty() || !p.content.scenarios.map {it.scenario}.containsAll(v.scenarios))C.fail("请记录本轮真正演练的预案情景")
        required(v.title,v.actualLocation,v.actualActions,v.communicationRehearsal,v.actualOutcome,v.attendanceFileIds,v.signedReportFileIds)
        val actualOfficers=planActors(orgId,p,v.startedAt)
        if(v.evaluatorUserId !in setOf(actualOfficers[EnumEmergencyDutyRole.KEEPER],actualOfficers[EnumEmergencyDutyRole.SECURITY]) || v.evaluatorUserId==v.conductorUserId || v.conductorUserId==actualOfficers[EnumEmergencyDutyRole.DIRECTOR])C.fail("实际评估须由不同的预案保管或安全保卫责任人员承担，组织人员与主要负责人分别履职")
        if(!v.participantUserIds.containsAll(setOf(v.conductorUserId,v.evaluatorUserId)))C.fail("参加人员须包含实际组织和现场评估人员")
        (v.participantUserIds-previous?.participantUserIds.orEmpty()).forEach {users.requireMuseumMember(it,orgId)}
        if(v.hasIssues!=v.issues.isNotEmpty())C.fail("是否发现问题须与实际问题清单一致")
        v.issues.drop(previous?.issues?.size ?: 0).forEach {i->required(i.title,i.findings,i.requiredActions);users.requireMuseumMember(i.ownerUserId,orgId);if(i.dueAt.isBefore(v.endedAt))C.fail("整改期限不能早于实际发现问题的演练结束时间")}
        if(v.hasRealIncident && !v.hasIssues)C.fail("发生真实事故不能记录为未发现问题，须如实保留问题和跟进安排")
        if(v.hasRealIncident!=v.actualIncidentIds.isNotEmpty())C.fail("演练期间真实事故须另行实际登记并关联，模拟情景不能生成真实事故")
        v.actualIncidentIds.forEach {id->
            val ref=incidents.referenceSummary(orgId,id)
            val discovered=ref["discoveredAt"] as LocalDateTime;val occurred=ref["occurredAt"] as? LocalDateTime
            if(discovered !in v.startedAt..v.endedAt && (occurred==null || occurred !in v.startedAt..v.endedAt))C.fail("请关联本轮实际发生或发现的事故；历史事故仅作为演练情景说明")
        }
        if(v.involvesOriginals) {
            if(v.mode!=EnumEmergencyDrillMode.FIELD || v.originalUseBasisVerified==null || v.originalOperations.isNullOrBlank() || v.originalOperationFileIds.isNullOrBlank())C.fail("涉及原件须据实记录现场操作、既有手续核对结论和原件")
            if(v.originalUseBasisVerified==false && !v.hasIssues)C.fail("原件操作依据尚未核实不能登记为没有问题，应保留实际问题及整改")
        } else if(v.requisitionIds.isNotEmpty() || v.originalUseBasisVerified!=null || !v.originalOperations.isNullOrBlank() || !v.originalOperationFileIds.isNullOrBlank())C.fail("未涉及原件不能关联实际取用记录或预填核实结论")
    }
    private fun reportFiles(v:CollectionEmergencyDrillVo)=listOfNotNull(v.attendanceFileIds,v.signedReportFileIds,v.originalOperationFileIds).joinToString(",")
    private fun distinctOccurrence(orgId:String,v:CollectionEmergencyDrillVo,exceptId:String?=null) {
        if(E.drills(orgId).filter {it.id.value!=exceptId}.onEach(E::drill).flatMap(E::reports).any {
            it.content.conductorUserId==v.conductorUserId && (it.content.startedAt==v.startedAt || v.startedAt<it.content.endedAt && it.content.startedAt<v.endedAt)
        })C.fail("该组织人员在此时间已有实际演练，请在同一次演练追加订正，不能重建报告绕过历史")
    }
    fun recordDrill(orgId:String,v:CollectionEmergencyDrillVo):String {
        org(orgId,true)
        if(v.conductorUserId!=SecurityUtils.getUserId())C.fail("须由真实组织实施人员本人登记演练")
        users.requireMuseumMember(v.conductorUserId,orgId)
        E.drills(orgId).firstOrNull {it.content==v}?.let {E.drill(it);return it.id.value}
        val p=plan(orgId,v.planId);validateReport(orgId,v,p);distinctOccurrence(orgId,v)
        val snapshot=E.operations(orgId,v)
        val d=CollectionEmergencyDrillDao.new {this.orgId=orgId;planId=p.id.value;content=v;operationSnapshot=snapshot}
        seal(orgId,d.id.value,"DRILL",E.drillValue(d),reportFiles(v));return d.id.value
    }
    fun reviseDrill(orgId:String,id:String,v:EmergencyDrillRevisionVo):String {
        org(orgId,true);val d=drill(orgId,id);val current=E.report(d)
        val (actor,dutyId)=responsible(orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,id,EnumEmergencyDutyRole.RECORDER),d.content.conductorUserId,EnumEmergencyDutyQualification.MEMBER)
        current.revision?.takeIf {it.record.request==v}?.let {return it.id.value}
        if(v.expectedReportId!=current.id)C.fail("演练报告已变化，请核对最新报告后再订正")
        required(v.reason,v.fileIds);E.retained(current.content,v.report)
        if(v.report==current.content)C.fail("订正须说明实际变化，不能重复生成相同报告")
        validateReport(orgId,v.report,plan(orgId,v.report.planId),current.content);distinctOccurrence(orgId,v.report,id)
        val snapshot=E.operations(orgId,v.report)
        val r=CollectionEmergencyDrillRevisionDao.new {this.orgId=orgId;drillId=id;version=current.version+1;record=EmergencyDrillRevisionRecord(v,actor,now());operationSnapshot=snapshot;recorderDutyId=dutyId}
        seal(orgId,r.id.value,"DRILL_REVISION",E.revisionValue(r),reportFiles(v.report)+","+v.fileIds);return r.id.value
    }
    private fun expected(d:CollectionEmergencyDrillDao,reportId:String)=E.report(d).also {if(it.id!=reportId)C.fail("演练报告已订正，请先核对当前版本，不能沿用旧页面办理")}
    fun confirmDrill(orgId:String,id:String,role:String,v:CollectionCareSignatureVo)=confirmDrill(orgId,id,id,role,v)
    fun confirmDrill(orgId:String,id:String,reportId:String,role:String,v:CollectionCareSignatureVo):String {
        org(orgId,true);val d=drill(orgId,id);val r=expected(d,reportId);val p=plan(orgId,r.content.planId);required(v.opinion,v.fileIds)
        if(!E.confirmed(p))C.fail("请先核对本次实际印发的预案原件，核对时间不会冒充原印发日期")
        val original=when(role){"EVALUATOR"->r.content.evaluatorUserId;"DIRECTOR"->E.drillDirector(orgId,r.content);else->C.fail("演练核对职责不存在")}
        val q=if(role=="DIRECTOR")EnumEmergencyDutyQualification.DIRECTOR else if(original==planActors(orgId,p,r.content.startedAt)[EnumEmergencyDutyRole.KEEPER])EnumEmergencyDutyQualification.KEEPER else EnumEmergencyDutyQualification.SECURITY
        val (actor,dutyId)=responsible(orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,id,EnumEmergencyDutyRole.valueOf(role)),original,q)
        val old=if(role=="EVALUATOR")r.evaluator else r.director
        old?.let {if(it.actorUserId==actor && it.record==v)return reportId;C.fail("实际演练确认不能覆盖，接任后核对只用于后续办理")}
        if(role=="DIRECTOR" && r.evaluator==null)C.fail("请先由实际评估人员本人核对演练结果与问题")
        val s=CollectionCareSignature(actor,now(),v)
        if(r.revision!=null) {if(role=="EVALUATOR"){r.revision.evaluator=s;r.revision.evaluatorDutyId=dutyId} else {r.revision.director=s;r.revision.directorDutyId=dutyId}}
        else {if(role=="EVALUATOR"){d.evaluator=s;d.evaluatorDutyId=dutyId} else {d.director=s;d.directorDutyId=dutyId}}
        seal(orgId,reportId,role,D.signatureValue(s,dutyId)!!,v.fileIds);return reportId
    }
    fun improve(orgId:String,drillId:String,v:EmergencyImprovementVo)=improve(orgId,drillId,drillId,v)
    fun improve(orgId:String,drillId:String,reportId:String,v:EmergencyImprovementVo):String {
        org(orgId,true);val d=drill(orgId,drillId);val current=expected(d,reportId);val p=plan(orgId,current.content.planId)
        val issue=current.content.issues.getOrNull(v.issueNumber-1) ?: C.fail("实际演练问题不存在")
        val old=E.improvements(drillId).filter {it.issueNumber==v.issueNumber}.onEach(E::improvement).lastOrNull()
        if(old?.content==v && E.basisMatches(d,old))return old.id.value
        if(v.previousId!=old?.id?.value || E.closed(d,old))C.fail("整改版本已变化或已实际复核，不能覆盖已完成事项")
        val (owner,ownerDutyId)=responsible(orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,drillId,EnumEmergencyDutyRole.OWNER,v.issueNumber),issue.ownerUserId,EnumEmergencyDutyQualification.MEMBER)
        if(v.ownerUserId!=owner)C.fail("须如实登记本项当前责任人员本人实际完成的整改或接任后核对")
        if(v.verifierUserId==v.ownerUserId || v.verifierUserId !in planActors(orgId,p).values)C.fail("整改应由另一位预案责任人员独立复核")
        users.requireMuseumMember(v.ownerUserId,orgId);duties.requireOfficer(orgId,v.verifierUserId)
        required(v.actualActions,v.result,v.fileIds)
        if(v.completedAt.isBefore(current.content.endedAt) || v.completedAt.isAfter(now()))C.fail("整改实际完成时间须在演练发现问题之后，不能虚填未来完成")
        D.requireActor(orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,drillId,EnumEmergencyDutyRole.OWNER,v.issueNumber),issue.ownerUserId,owner,v.completedAt,ownerDutyId)
        if(old!=null && v.replacementReason.isNullOrBlank())C.fail("继续整改须说明原因并保留先前实际结果")
        requireRevision(orgId,current.content,issue,v)
        val row=CollectionEmergencyImprovementDao.new {this.orgId=orgId;this.drillId=drillId;issueNumber=v.issueNumber;version=(old?.version ?: 0)+1;content=v;reportRevisionId=current.id;issueBasis=E.issueBasis(current.content,v.issueNumber);this.ownerDutyId=ownerDutyId}
        seal(orgId,row.id.value,"IMPROVEMENT",E.improvementValue(row),v.fileIds);return row.id.value
    }
    private fun requireRevision(orgId:String,d:CollectionEmergencyDrillVo,issue:EmergencyDrillIssueVo,v:EmergencyImprovementVo) {
        if(!issue.requiresPlanRevision && v.revisedPlanId==null)return
        val p=plan(orgId,v.revisedPlanId ?: C.fail("本问题要求修订预案，须关联已实际印发并核对的新版本"))
        if(!E.confirmed(p) || p.version<=CollectionEmergencyPlanDao[d.planId].version || p.content.issuedAt.isBefore(d.endedAt) || p.content.effectiveAt.isAfter(v.completedAt))C.fail("整改须引用演练发现问题后实际印发、已生效并核对的新版预案")
    }
    fun reviewImprovement(orgId:String,drillId:String,id:String,v:EmergencyImprovementReviewVo)=reviewImprovement(orgId,drillId,drillId,id,v)
    fun reviewImprovement(orgId:String,drillId:String,reportId:String,id:String,v:EmergencyImprovementReviewVo):String {
        org(orgId,true);val d=drill(orgId,drillId);val current=expected(d,reportId)
        val r=E.improvements(drillId).find {it.id.value==id && it.orgId==orgId} ?: C.fail("本次整改原件不存在")
        E.improvement(r);required(v.opinion,v.fileIds)
        val (actor,dutyId)=responsible(orgId,EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,id,EnumEmergencyDutyRole.REVIEWER),r.content.verifierUserId,EnumEmergencyDutyQualification.MEMBER)
        duties.requireOfficer(orgId,actor)
        if(!E.basisMatches(d,r))C.fail("报告相关事实已订正，请据实追加整改原件后重新复核")
        r.review?.let {if(it.actorUserId==actor && it.record==v)return id;C.fail("整改复核意见不能覆盖，请据实继续整改")}
        if(E.improvements(drillId).filter {it.issueNumber==r.issueNumber}.last().id!=r.id)C.fail("请核对最新整改版本")
        if(v.accepted)requireRevision(orgId,current.content,current.content.issues[r.issueNumber-1],r.content)
        val review=EmergencyImprovementReview(actor,now(),v);r.review=review;r.reviewDutyId=dutyId
        seal(orgId,id,"REVIEW",if(dutyId==null)review else mapOf("review" to review,"dutyId" to dutyId),v.fileIds);return id
    }
    private fun issueRows(d:CollectionEmergencyDrillDao)=E.report(d).content.issues.mapIndexed {index,i->
        val records=E.improvements(d.id.value).filter {it.issueNumber==index+1}.onEach(E::improvement)
        val closed=E.closed(d,records.lastOrNull())
        mapOf("number" to index+1,"content" to i,"closed" to closed,"overdue" to (!closed && !now().isBefore(i.dueAt)),"records" to records.map {mapOf("id" to it.id.value,"version" to it.version,"content" to it.content,"review" to it.review,"reportId" to (it.reportRevisionId ?: d.id.value),"basisMatches" to E.basisMatches(d,it),"ownerDutyId" to it.ownerDutyId,"reviewDutyId" to it.reviewDutyId)})
    }
    fun detail(orgId:String):Map<String,Any?> {
        val o=org(orgId);val ps=E.plans(orgId).onEach(E::plan);val ds=E.drills(orgId).onEach(E::drill)
        val active=E.applicable(ps,now())
        val dutyRows=duties.targets(orgId)
        val evidenceErrors=mutableListOf<String>()
        val archiveFiles=linkedMapOf<String,MutableMap<String,Any?>>()
        fun original(recordId:String,phase:String) {
            val archive=archives.phaseForDisplay(E.BIZ,recordId,orgId,phase)
            (archive?.get("evidenceIssue") as? String)?.let {evidenceErrors += it}
            archiveFiles.getOrPut(recordId){linkedMapOf()}[phase]=archive?.get("files") ?: emptyList<Map<String,Any?>>()
        }
        ps.forEach {p->
            original(p.id.value,"ISSUED")
            E.planSigns(p).filter {it.third!=null}.forEach {(role,_,_)->original(p.id.value,role)}
        }
        ds.forEach {d->
            E.reports(d).forEach {report->
                original(report.id,if(report.revision==null)"DRILL" else "DRILL_REVISION")
                if(report.evaluator!=null)original(report.id,"EVALUATOR")
                if(report.director!=null)original(report.id,"DIRECTOR")
            }
            E.improvements(d.id.value).onEach(E::improvement).forEach {improvement->
                original(improvement.id.value,"IMPROVEMENT")
                if(improvement.review!=null)original(improvement.id.value,"REVIEW")
            }
        }
        dutyRows.forEach {target->(target["records"] as? List<*>)?.filterIsInstance<Map<*,*>>()?.forEach {record->
            val recordId=record["id"] as String
            original(recordId,"DUTY_APPOINTED")
            if(record["handover"]!=null)original(recordId,"DUTY_HANDOVER")
            if(record["acceptance"]!=null)original(recordId,"DUTY_ACCEPTED")
        }}
        return mapOf("evidenceErrors" to evidenceErrors.distinct(),"archiveFiles" to archiveFiles,"orgId" to orgId,"name" to o.name,"activePlanId" to active?.id?.value,
            "plans" to ps.map {p->mapOf("id" to p.id.value,"version" to p.version,"content" to p.content,"confirmed" to E.confirmed(p),"missingReportScenarios" to (E.reportScenarios-p.content.scenarios.map {it.scenario}.toSet()),"signatures" to E.planSigns(p).map {(role,actor,s)->mapOf("role" to role,"actorUserId" to actor,"signature" to s,"dutyId" to E.planDutyId(p,role))})},
            "drills" to ds.map {d->val r=E.report(d);mapOf("id" to d.id.value,"reportId" to r.id,"reportVersion" to r.version,"content" to r.content,"counted" to E.counted(d,ps),"basisMatches" to (E.applicable(ps,r.content.startedAt)?.id?.value==r.content.planId),"evaluator" to r.evaluator,"director" to r.director,"directorUserId" to E.drillDirector(orgId,r.content),"issues" to issueRows(d),"actualIncidents" to r.content.actualIncidentIds.map {incidents.referenceSummary(orgId,it)},
                "reports" to E.reports(d).map {h->mapOf("id" to h.id,"version" to h.version,"content" to h.content,"evaluator" to h.evaluator,"director" to h.director,"revision" to h.revision?.record,"recorderDutyId" to h.revision?.recorderDutyId,"evaluatorDutyId" to (h.revision?.evaluatorDutyId ?: if(h.version==1)d.evaluatorDutyId else null),"directorDutyId" to (h.revision?.directorDutyId ?: if(h.version==1)d.directorDutyId else null))})},
            "requisitionOptions" to RequisitionDao.find {(Requisitions.orgId eq orgId) and (Requisitions.outExecuted eq true)}.map {mapOf("value" to it.id.value,"label" to (it.documentNo ?: it.id.value))},
            "incidentOptions" to CollectionIncidentDao.find {CollectionIncidents.orgId eq orgId}.map {mapOf("value" to it.id.value,"label" to it.title)},
            "duties" to dutyRows,
            "due" to dueRows(o,active,ps,ds))
    }
    private fun dueRows(o:CollectionOrgDao,p:CollectionEmergencyPlanDao?,ps:List<CollectionEmergencyPlanDao>,ds:List<CollectionEmergencyDrillDao>):List<Map<String,Any?>> {
        val pending=ds.sumOf {d->issueRows(d).count {it["closed"]!=true}}
        val pendingEvaluation=ds.count {!E.counted(it,ps)}
        val pendingDuty= duties.pendingCount(o.id.value)
        val openIncidents=ds.flatMap {E.report(it).content.actualIncidentIds}.distinct().count {incidents.referenceSummary(o.id.value,it)["closed"]!=true}
        val scenarios=(p?.content?.scenarios?.map {it.scenario}.orEmpty()+E.reportScenarios).distinct()
        return scenarios.map {scenario->
            val s=p?.content?.scenarios?.find {it.scenario==scenario}
            val due=if(s!=null)E.due(p,s,ps,ds) else null
            val status=when {p==null->"MISSING_PLAN";s==null->"MISSING_SCENARIO";!E.confirmed(p)->"PENDING_CONFIRMATION";due!=null && !now().isBefore(due)->"OVERDUE";else->"SCHEDULED"}
            mapOf("rowKey" to "${o.id.value}:$scenario","orgId" to o.id.value,"name" to o.name,"scenario" to scenario,"status" to status,"nextDueAt" to due,"pendingIssueCount" to pending,"pendingEvaluationCount" to pendingEvaluation,"pendingDutyCount" to pendingDuty,"openIncidentCount" to openIncidents)
        }
    }
    fun page(request:HttpServletRequest):GenericPage<Map<String,Any?>> {
        val ids=access.readableOrgs()
        val os=CollectionOrgDao.find {CollectionOrgs.id inList ids}.toList()
        val keyword=request.getParameter("keyword").orEmpty();val status=request.getParameter("status").orEmpty()
        val rows=os.filter {it.name.contains(keyword,true)}.flatMap {o->val ps=E.plans(o.id.value).onEach(E::plan);val ds=E.drills(o.id.value).onEach(E::drill);dueRows(o,E.applicable(ps,now()),ps,ds)}.filter {status.isBlank() || it["status"]==status}
        return GenericPage<Map<String,Any?>>().build(request).also {p->p.total=rows.size.toLong();p.list=if(p.pageSize==0)rows else rows.drop(p.startRow.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(p.pageSize)}
    }
}
