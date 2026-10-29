package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionEmergencyEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyEvidence as D

internal data class EmergencyDutyContext(val original:String,val label:String,val reportId:String?=null,val drill:CollectionEmergencyDrillDao?=null)

@Component
@Transactional
class CollectionEmergencyDutyService(
    private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,
    archives:CollectionPermanentArchiveService,
) {
    private val access=CollectionEmergencyAccess(scope)
    private val files=CollectionEmergencyArchive(archives,access)
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    private fun org(org:String) {
        access.require(org,true)
        CollectionOrgDao.find {CollectionOrgs.id eq org}.forUpdate().singleOrNull() ?: C.fail("收藏单位不存在")
    }
    internal fun context(org:String,t:EmergencyDutyTarget):EmergencyDutyContext {
        if(t.type!=EnumEmergencyDutyTarget.ISSUE && t.issueNumber!=0)C.fail("非问题职责不能指定问题序号")
        if(t.type==EnumEmergencyDutyTarget.PLAN) {
            val p=CollectionEmergencyPlanDao.findById(t.targetId) ?: C.fail("应急预案不存在")
            if(p.orgId!=org)C.fail("不能承接外馆预案职责")
            E.plan(p)
            val original=when(t.role){EnumEmergencyDutyRole.KEEPER->p.content.keeperUserId;EnumEmergencyDutyRole.SECURITY->p.content.securityUserId;EnumEmergencyDutyRole.DIRECTOR->p.content.directorUserId;else->C.fail("预案承接职责不存在")}
            return EmergencyDutyContext(original,"第${p.version}版预案：${p.content.title}")
        }
        val improvement=if(t.type==EnumEmergencyDutyTarget.IMPROVEMENT)CollectionEmergencyImprovementDao.findById(t.targetId) ?: C.fail("整改原件不存在") else null
        val d=CollectionEmergencyDrillDao.findById(improvement?.drillId ?: t.targetId) ?: C.fail("实际演练不存在")
        if(d.orgId!=org || improvement?.orgId?.let {it!=org}==true)C.fail("不能承接外馆演练职责")
        E.drill(d);improvement?.let(E::improvement)
        val r=E.report(d)
        val original=when(t.type) {
            EnumEmergencyDutyTarget.DRILL->when(t.role){EnumEmergencyDutyRole.RECORDER->d.content.conductorUserId;EnumEmergencyDutyRole.EVALUATOR->r.content.evaluatorUserId;EnumEmergencyDutyRole.DIRECTOR->E.drillDirector(org,r.content);else->C.fail("演练后续承接职责不存在")}
            EnumEmergencyDutyTarget.ISSUE->{if(t.role!=EnumEmergencyDutyRole.OWNER)C.fail("问题承接职责不存在");r.content.issues.getOrNull(t.issueNumber-1)?.ownerUserId ?: C.fail("实际问题不存在")}
            EnumEmergencyDutyTarget.IMPROVEMENT->{if(t.role!=EnumEmergencyDutyRole.REVIEWER)C.fail("整改复核承接职责不存在");improvement!!.content.verifierUserId}
            else->C.fail("承接对象不存在")
        }
        return EmergencyDutyContext(original,when(t.type){EnumEmergencyDutyTarget.ISSUE->"${r.content.title} / 问题${t.issueNumber}";EnumEmergencyDutyTarget.IMPROVEMENT->"${r.content.title} / 问题${improvement!!.issueNumber}第${improvement.version}次整改";else->r.content.title},r.id,d)
    }
    internal fun qualifications(t:EmergencyDutyTarget)=when(t.role) {
        EnumEmergencyDutyRole.KEEPER->setOf(EnumEmergencyDutyQualification.KEEPER)
        EnumEmergencyDutyRole.SECURITY->setOf(EnumEmergencyDutyQualification.SECURITY)
        EnumEmergencyDutyRole.DIRECTOR->setOf(EnumEmergencyDutyQualification.DIRECTOR)
        EnumEmergencyDutyRole.RECORDER,EnumEmergencyDutyRole.OWNER->setOf(EnumEmergencyDutyQualification.MEMBER)
        EnumEmergencyDutyRole.EVALUATOR->setOf(EnumEmergencyDutyQualification.KEEPER,EnumEmergencyDutyQualification.SECURITY)
        EnumEmergencyDutyRole.REVIEWER->setOf(EnumEmergencyDutyQualification.KEEPER,EnumEmergencyDutyQualification.SECURITY,EnumEmergencyDutyQualification.DIRECTOR)
    }
    internal fun qualified(org:String,actor:String,q:EnumEmergencyDutyQualification) {
        users.requireMuseumMember(actor,org)
        val role=when(q){EnumEmergencyDutyQualification.MEMBER->null;EnumEmergencyDutyQualification.KEEPER->CollectionConst.ROLE_STORAGE_HEAD;EnumEmergencyDutyQualification.SECURITY->CollectionConst.ROLE_SECURITY;EnumEmergencyDutyQualification.DIRECTOR->CollectionConst.ROLE_DIRECTOR}
        role?.let {users.requireRole(actor,org,it)}
    }
    internal fun requireOfficer(org:String,actor:String) {
        users.requireMuseumMember(actor,org)
        for(role in listOf(CollectionConst.ROLE_STORAGE_HEAD,CollectionConst.ROLE_SECURITY,CollectionConst.ROLE_DIRECTOR)) {
            try {users.requireRole(actor,org,role);return} catch (_:io.hizhengfu.cloud.common.exception.BadRequestException) { }
        }
        C.fail("独立复核人员须实际担任本馆保管、安全保卫或主要负责人职责")
    }
    internal fun independent(org:String,t:EmergencyDutyTarget,ctx:EmergencyDutyContext,to:String,at:LocalDateTime=now()) {
        fun actor(target:EmergencyDutyTarget):String {val c=context(org,target);return D.actor(org,target,c.original,at)}
        val excluded=when(t.type) {
            EnumEmergencyDutyTarget.PLAN->listOf(EnumEmergencyDutyRole.KEEPER,EnumEmergencyDutyRole.SECURITY,EnumEmergencyDutyRole.DIRECTOR).filter {it!=t.role}.map {actor(t.copy(role=it))}+E.planSigns(CollectionEmergencyPlanDao[t.targetId]).filter {it.first!=t.role.name}.mapNotNull {it.third?.actorUserId}
            EnumEmergencyDutyTarget.DRILL->buildList {
                listOf(EnumEmergencyDutyRole.RECORDER,EnumEmergencyDutyRole.EVALUATOR,EnumEmergencyDutyRole.DIRECTOR).filter {it!=t.role}.forEach {add(actor(t.copy(role=it)))}
                val report=E.report(ctx.drill!!)
                if(t.role!=EnumEmergencyDutyRole.RECORDER) {
                    add(ctx.drill.content.conductorUserId)
                    add(report.revision?.record?.submittedBy ?: ctx.drill.content.conductorUserId)
                }
                if(t.role==EnumEmergencyDutyRole.DIRECTOR) {
                    add(report.content.evaluatorUserId)
                    report.evaluator?.actorUserId?.let(::add)
                }
            }
            EnumEmergencyDutyTarget.ISSUE->E.improvements(t.targetId).filter {it.issueNumber==t.issueNumber && it.review==null}.map {actor(EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,it.id.value,EnumEmergencyDutyRole.REVIEWER))}
            EnumEmergencyDutyTarget.IMPROVEMENT->{val i=CollectionEmergencyImprovementDao[t.targetId];listOf(i.content.ownerUserId,actor(EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,i.drillId,EnumEmergencyDutyRole.OWNER,i.issueNumber)))}
        }
        if(to in excluded)C.fail("接任后的组织、评估、主要负责人和整改复核职责仍须独立，不得自行复核")
    }
    fun appoint(orgId:String,v:EmergencyDutyAppointmentVo):String {
        org(orgId);val ctx=context(orgId,v.target);val issuer=SecurityUtils.getUserId() ?: C.fail("请登录实际责任人员账号")
        qualified(orgId,issuer,EnumEmergencyDutyQualification.DIRECTOR)
        val rows=D.records(orgId,v.target)
        rows.lastOrNull()?.takeIf {it.appointment.request==v && it.appointment.issuerUserId==issuer}?.let {return it.id.value}
        if(v.previousId!=rows.lastOrNull()?.id?.value || v.expectedReportId!=ctx.reportId)C.fail("承接记录或演练报告已变化，请核对当前原件后重新指定")
        if(v.reason.isBlank() || v.fileIds.isBlank() || v.appointedAt>now() || v.effectiveAt<v.appointedAt)C.fail("请据实登记已作出的职责指定、原因、原件及连续生效时间")
        if(rows.lastOrNull()?.appointment?.request?.appointedAt?.let {v.appointedAt<it}==true)C.fail("后续指定须按实际作出的先后顺序登记，不能用更早的指定覆盖后来职责")
        if(v.predecessorUnavailable) {if(v.unavailableReason.isNullOrBlank() || v.unavailableFileIds.isNullOrBlank())C.fail("原任不能交接须由主要负责人记录原因及实际证明原件")}
        else if(!v.unavailableReason.isNullOrBlank() || !v.unavailableFileIds.isNullOrBlank())C.fail("能实际交接时不能预填无法交接结论")
        if(v.qualification !in qualifications(v.target))C.fail("接任职责与实际岗位资格不一致")
        qualified(orgId,v.toUserId,v.qualification)
        val from=D.active(rows,now())?.appointment?.request?.toUserId ?: ctx.original
        if(v.toUserId==from)C.fail("接任人不能与当前责任人相同")
        independent(orgId,v.target,ctx,v.toUserId,maxOf(now(),v.effectiveAt))
        val r=CollectionEmergencyDutyDao.new {this.orgId=orgId;targetKey=D.key(v.target);version=(rows.lastOrNull()?.version ?: 0)+1;appointment=EmergencyDutyAppointment(v,ctx.original,from,issuer,now())}
        CollectionEmergencyDutyWriteVerification.write(r,{
            files.seal(orgId,r.id.value,"DUTY_APPOINTED",D.value(r),listOfNotNull(v.fileIds,v.unavailableFileIds).joinToString(","))
        }) {
            qualified(orgId,issuer,EnumEmergencyDutyQualification.DIRECTOR);qualified(orgId,v.toUserId,v.qualification)
            val current=context(orgId,v.target)
            if(current.original!=ctx.original || current.reportId!=v.expectedReportId)C.fail("指定期间原职责或演练报告发生变化，请重新核对")
            independent(orgId,v.target,current,v.toUserId,maxOf(now(),v.effectiveAt))
        }
        return r.id.value
    }
    fun sign(orgId:String,id:String,phase:String,v:CollectionCareSignatureVo):String {
        org(orgId);val r=CollectionEmergencyDutyDao.findById(id) ?: C.fail("责任承接记录不存在")
        if(r.orgId!=orgId)C.fail("不能签署外馆责任承接")
        val a=r.appointment;val rows=D.records(orgId,a.request.target);val ctx=context(orgId,a.request.target)
        if(rows.last().id!=r.id)C.fail("该指定已被后续版本替代，请核对最新承接记录")
        if(a.request.expectedReportId!=ctx.reportId)C.fail("报告已变化，须由主要负责人按当前报告重新指定后承接")
        if(v.opinion.isBlank() || v.fileIds.isBlank())C.fail("请填写本人实际交接、核对及承接意见和签署原件")
        val actor=when(phase){"HANDOVER"->a.fromUserId;"ACCEPT"->a.request.toUserId;else->C.fail("承接签署环节不存在")}
        if(SecurityUtils.getUserId()!=actor)C.fail("须由原任或接任人本人签署，管理员不能代签")
        users.requireMuseumMember(actor,orgId)
        val old=if(phase=="HANDOVER")r.handover else r.acceptance
        old?.let {if(it.record==v)return id;C.fail("责任承接签署不能覆盖")}
        if(phase=="HANDOVER" && a.request.predecessorUnavailable)C.fail("本次已记录无法交接的证明，不应补造原任交接签署")
        if(phase=="ACCEPT") {
            if(r.handover==null && !a.request.predecessorUnavailable)C.fail("请先由原任本人交接；无法交接须另有主要负责人指定的实际说明及证明")
            qualified(orgId,actor,a.request.qualification);independent(orgId,a.request.target,ctx,actor,maxOf(now(),a.request.effectiveAt))
        }
        val s=CollectionCareSignature(actor,now(),v)
        if(phase=="HANDOVER")r.handover=s else r.acceptance=s
        CollectionEmergencyDutyWriteVerification.write(r,{
            files.seal(orgId,id,if(phase=="HANDOVER")"DUTY_HANDOVER" else "DUTY_ACCEPTED",s,v.fileIds)
        }) {
            users.requireMuseumMember(actor,orgId)
            val current=context(orgId,a.request.target)
            if(current.original!=ctx.original || current.reportId!=a.request.expectedReportId)C.fail("交接期间原职责或演练报告发生变化，请重新核对")
            if(phase=="ACCEPT") {
                qualified(orgId,actor,a.request.qualification)
                independent(orgId,a.request.target,current,actor,maxOf(now(),a.request.effectiveAt))
            }
        }
        return id
    }
    internal fun row(org:String,t:EmergencyDutyTarget):Map<String,Any?> {
        val ctx=context(org,t);val rows=D.records(org,t);val active=D.active(rows,now())
        return mapOf("key" to D.key(t),"target" to t,"label" to ctx.label,"originalUserId" to ctx.original,"currentUserId" to (active?.appointment?.request?.toUserId ?: ctx.original),"expectedReportId" to ctx.reportId,"previousId" to rows.lastOrNull()?.id?.value,"activeDutyId" to active?.id?.value,"qualifications" to qualifications(t),
            "records" to rows.map {r->mapOf("id" to r.id.value,"version" to r.version,"appointment" to r.appointment,"handover" to r.handover,"acceptance" to r.acceptance,"activeAt" to D.activeAt(r),"active" to (active?.id==r.id),"latest" to (rows.last().id==r.id),"basisMatches" to (r.appointment.request.expectedReportId==ctx.reportId))})
    }
    internal fun pendingCount(org:String):Int=CollectionEmergencyDutyDao.find {CollectionEmergencyDuties.orgId eq org}.map {it.appointment.request.target}.distinct().count {t->
        D.records(org,t).last().acceptance==null
    }
    internal fun targets(org:String):List<Map<String,Any?>> {
        val targets=buildList {
            E.plans(org).forEach {p->listOf(EnumEmergencyDutyRole.KEEPER,EnumEmergencyDutyRole.SECURITY,EnumEmergencyDutyRole.DIRECTOR).forEach {add(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,it))}}
            E.drills(org).forEach {d->
                listOf(EnumEmergencyDutyRole.RECORDER,EnumEmergencyDutyRole.EVALUATOR,EnumEmergencyDutyRole.DIRECTOR).forEach {add(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,it))}
                E.report(d).content.issues.indices.forEach {add(EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d.id.value,EnumEmergencyDutyRole.OWNER,it+1))}
                E.improvements(d.id.value).forEach {add(EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,it.id.value,EnumEmergencyDutyRole.REVIEWER))}
            }
        }
        return targets.map {row(org,it)}
    }
}
