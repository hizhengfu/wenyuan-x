package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.CollectionEmergencyEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyEvidence as D

@Component
class CollectionEmergencyTaskSource:CollectionSafetyTaskSource {
    override val domain=EnumCollectionSafetyDomain.EMERGENCY
    private val officers=setOf(CollectionConst.ROLE_STORAGE_HEAD,CollectionConst.ROLE_SECURITY,CollectionConst.ROLE_DIRECTOR)
    override fun tasks(orgId:String,at:LocalDateTime):List<CollectionSafetyTask> = try {collect(orgId,at)} catch(e:BadRequestException) {listOf(safetyTask(orgId,domain,"ORIGINAL_ERROR","应急预案或演练原件需核查",orgId,at=at,basis="ORIGINAL_ERROR",status="ORIGINAL_ERROR",error=e.message))}
    private fun qualificationRoles(q:EnumEmergencyDutyQualification)=when(q) {
        EnumEmergencyDutyQualification.MEMBER->emptySet()
        EnumEmergencyDutyQualification.KEEPER->setOf(CollectionConst.ROLE_STORAGE_HEAD)
        EnumEmergencyDutyQualification.SECURITY->setOf(CollectionConst.ROLE_SECURITY)
        EnumEmergencyDutyQualification.DIRECTOR->setOf(CollectionConst.ROLE_DIRECTOR)
    }
    private fun collect(org:String,at:LocalDateTime):List<CollectionSafetyTask> = buildList {
        val ps=E.plans(org).onEach(E::plan);val ds=E.drills(org).onEach(E::drill);val active=E.applicable(ps,at)
        fun assigned(t:EmergencyDutyTarget,original:String,roles:Set<String> = emptySet()):CollectionSafetyAssignee {
            val d=D.active(org,t,at)
            val role=d?.appointment?.request?.qualification?.let(::qualificationRoles) ?: roles
            return CollectionSafetyAssignee(d?.appointment?.request?.toUserId ?: original,role)
        }
        fun planActors(p:CollectionEmergencyPlanDao)=E.planSigns(p).map {(role,id,_)->assigned(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,EnumEmergencyDutyRole.valueOf(role)),id,when(role){"KEEPER"->setOf(CollectionConst.ROLE_STORAGE_HEAD);"SECURITY"->setOf(CollectionConst.ROLE_SECURITY);else->setOf(CollectionConst.ROLE_DIRECTOR)})}
        fun addTask(kind:String,title:String,basis:Any,actors:List<CollectionSafetyAssignee> = emptyList(),record:String?=null,due:LocalDateTime?=null,name:String?=null) {add(safetyTask(org,domain,kind,title,org,record,name=name,due=due,at=at,basis=basis,assignees=actors))}
        if(active==null)addTask("MISSING_PLAN","尚缺已生效的藏品安全应急预案","MISSING_PLAN")
        val needed=ds.map {E.report(it).content.planId}.toSet()+listOfNotNull(active?.id?.value,ps.lastOrNull()?.id?.value)
        ps.filter {it.id.value in needed}.forEach {p->
            E.planSigns(p).filter {it.third==null && (it.first!="DIRECTOR" || p.keeper!=null && p.security!=null)}.forEach {(role,id,_)->
                val actor=assigned(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,EnumEmergencyDutyRole.valueOf(role)),id,when(role){"KEEPER"->setOf(CollectionConst.ROLE_STORAGE_HEAD);"SECURITY"->setOf(CollectionConst.ROLE_SECURITY);else->setOf(CollectionConst.ROLE_DIRECTOR)})
                addTask("PLAN_$role","应急预案待本人核对原件",E.planValue(p),listOf(actor),p.id.value,name=p.content.title)
            }
        }
        active?.let {p->
            val missing=E.reportScenarios-p.content.scenarios.map {it.scenario}.toSet()
            if(missing.isNotEmpty())addTask("MISSING_SCENARIOS","应急预案情景安排待补齐",missing.sortedBy {it.name},planActors(p),p.id.value,name=p.content.title)
            p.content.scenarios.forEach {scenario->
                val due=E.due(p,scenario,ps,ds)
                addTask("DRILL_DUE_${scenario.scenario}","应急情景定期演练",listOf(p.id.value,scenario.scenario,due),planActors(p),due=due,name=scenario.scenario.name)
            }
        }
        ds.forEach {d->
            val r=E.report(d);val p=CollectionEmergencyPlanDao[r.content.planId]
            fun actor(role:EnumEmergencyDutyRole,original:String,roles:Set<String> = emptySet())=assigned(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d.id.value,role),original,roles)
            if(E.applicable(ps,r.content.startedAt)?.id?.value!=r.content.planId)addTask("REPORT_BASIS","演练实际依据与当时预案不一致，待核实",listOf(r.id,r.content.planId),listOf(actor(EnumEmergencyDutyRole.RECORDER,d.content.conductorUserId)),r.id,name=r.content.title)
            if(E.confirmed(p)) {
                val actualKeeper=D.actor(org,EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p.id.value,EnumEmergencyDutyRole.KEEPER),p.content.keeperUserId,r.content.startedAt)
                val evaluatorRoles=setOf(if(r.content.evaluatorUserId==actualKeeper)CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_SECURITY)
                if(r.evaluator==null)addTask("REPORT_EVALUATOR","本版演练报告待本人核对",E.canonical(r.content),listOf(actor(EnumEmergencyDutyRole.EVALUATOR,r.content.evaluatorUserId,evaluatorRoles)),r.id,name=r.content.title)
                else if(r.director==null)addTask("REPORT_DIRECTOR","本版演练报告待主要负责人核对",E.canonical(r.content),listOf(actor(EnumEmergencyDutyRole.DIRECTOR,E.drillDirector(org,r.content),setOf(CollectionConst.ROLE_DIRECTOR))),r.id,name=r.content.title)
            }
            r.content.issues.forEachIndexed {index,issue->
                val rows=E.improvements(d.id.value).filter {it.issueNumber==index+1}.onEach(E::improvement);val last=rows.lastOrNull()
                if(!E.closed(d,last)) {
                    if(last!=null && last.review==null && E.basisMatches(d,last))addTask("IMPROVEMENT_REVIEW","实际整改待独立复核",E.improvementValue(last),listOf(assigned(EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,last.id.value,EnumEmergencyDutyRole.REVIEWER),last.content.verifierUserId,officers)),last.id.value,name=issue.title)
                    else addTask("ISSUE_${index+1}","演练问题整改或重新核对",listOf(r.id,E.issueBasis(r.content,index+1),last?.id?.value),listOf(assigned(EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d.id.value,EnumEmergencyDutyRole.OWNER,index+1),issue.ownerUserId)),d.id.value,issue.dueAt,issue.title)
                }
            }
        }
        CollectionEmergencyDutyDao.find {CollectionEmergencyDuties.orgId eq org}.map {it.appointment.request.target}.distinct().forEach {target->
            val r=D.records(org,target).last();val a=r.appointment;val v=a.request
            if(r.acceptance==null) {
                val currentReport=when(target.type){EnumEmergencyDutyTarget.PLAN->null;EnumEmergencyDutyTarget.IMPROVEMENT->E.report(CollectionEmergencyDrillDao[CollectionEmergencyImprovementDao[target.targetId].drillId]).id;else->E.report(CollectionEmergencyDrillDao[target.targetId]).id}
                if(v.expectedReportId!=currentReport)addTask("DUTY_REAPPOINT","报告已订正，责任承接待重新核对指定",listOf(r.id.value,currentReport),listOf(CollectionSafetyAssignee(a.issuerUserId,setOf(CollectionConst.ROLE_DIRECTOR))),r.id.value)
                else if(r.handover==null && !v.predecessorUnavailable)addTask("DUTY_HANDOVER","责任承接待原任本人交接",D.value(r),listOf(CollectionSafetyAssignee(a.fromUserId)),r.id.value)
                else addTask("DUTY_ACCEPT","责任承接待接任人本人接受",D.value(r),listOf(CollectionSafetyAssignee(v.toUserId,qualificationRoles(v.qualification))),r.id.value)
            }
        }
    }
}
