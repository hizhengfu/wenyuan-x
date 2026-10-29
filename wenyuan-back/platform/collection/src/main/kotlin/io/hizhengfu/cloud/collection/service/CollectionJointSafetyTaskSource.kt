package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as J

@Component
class CollectionJointSafetyTaskSource:CollectionSafetyTaskSource {
    override val domain=EnumCollectionSafetyDomain.JOINT
    override fun tasks(orgId:String,at:LocalDateTime)=RequisitionDao.find {Requisitions.orgId eq orgId}.filter {it.outExecuted || (it.supersededById == null && it.sourceRetirement == null && it.flowStatus !in setOf(EnumFlowStatus.CANCEL,EnumFlowStatus.REJECT,EnumFlowStatus.VOID))}.flatMap {r->
        try {tasks(r,at)} catch(e:BadRequestException) {listOf(safetyTask(orgId,domain,"ORIGINAL_ERROR","共同检查原件需核查",r.id.value,name=r.documentNo,at=at,basis="ORIGINAL_ERROR",status="ORIGINAL_ERROR",error=e.message))}
    }
    internal fun tasks(r:RequisitionDao,at:LocalDateTime):List<CollectionSafetyTask> = buildList {
        val id=r.id.value;val org=r.orgId;val ps=J.plans(id).onEach(J::plan);val rounds=J.rounds(id).onEach(J::round)
        val source=r.dossier?.let {J.source(r)}
        if(r.outExecuted && source!=null)J.outbound(r)
        val received=if(r.outExecuted)J.receivedIds(r) else emptySet()
        fun people(p:CollectionJointSafetyPlanDao)=mapOf("KEEPER" to CollectionSafetyAssignee(p.content.keeperUserId,setOf(CollectionConst.ROLE_STORAGE_HEAD)),"USE" to CollectionSafetyAssignee(p.content.useUserId,departmentId=r.dossier?.departmentGroupId),"SECURITY" to CollectionSafetyAssignee(p.content.securityUserId,setOf(CollectionConst.ROLE_SECURITY)))
        fun addTask(kind:String,title:String,basis:Any,actors:List<CollectionSafetyAssignee> = emptyList(),record:String?=null,cid:String?=null,due:LocalDateTime?=null,status:String?=null) {add(safetyTask(org,domain,kind,title,id,record,cid,cid?.let {CollectionDao.findById(it)?.takeIf {c->c.orgId==org}?.name} ?: r.documentNo,due,at,basis,actors,status))}
        val latest=ps.lastOrNull();val active=ps.lastOrNull(J::confirmed)?.takeIf {it.sourceSnapshot==source?.snapshot}
        val custody=RequisitionCustodyEvidence.entries(r)
        val recalls=RequisitionRecallEvidence.entries(r)
        fun currentPlanPeople(p:CollectionJointSafetyPlanDao):List<CollectionSafetyAssignee> {
            val effective=custody.lastOrNull {it.complete} ?: return people(p).values.toList()
            val actors=RequisitionCustodyEvidence.actors(r,effective)
            return listOf(
                CollectionSafetyAssignee(actors.getValue("KEEPER"),setOf(CollectionConst.ROLE_STORAGE_HEAD)),
                CollectionSafetyAssignee(actors.getValue("USE"),departmentId=r.dossier?.departmentGroupId),
                people(p).getValue("SECURITY"),
            )
        }
        if(r.outExecuted && source!=null) {
            val actors=RequisitionCustodyEvidence.actors(r,custody.lastOrNull {it.complete})
            val responsible=listOf(
                CollectionSafetyAssignee(actors.getValue("KEEPER"),setOf(CollectionConst.ROLE_STORAGE_HEAD)),
                CollectionSafetyAssignee(actors.getValue("USE"),departmentId=r.dossier!!.departmentGroupId),
            )
            splitCollectionIds(r.collectionIds).filter {it !in received}.forEach {cid->
                val directives=recalls.filter {cid in it.request.collectionIds}
                val due=minOf(source.returnAt,directives.minOfOrNull {it.request.returnBy} ?: source.returnAt)
                addTask("RETURN_DUE",if(directives.isEmpty())"提用藏品归库期限" else "提用藏品提前归库期限",listOf(source.snapshot,cid,due,directives),
                    responsible,cid=cid,due=due)
            }
        }
        custody.lastOrNull()?.takeIf {!it.complete && source?.returned==false && source.cancelled==false}?.let {entry->
            RequisitionCustodyEvidence.actors(r,entry).filterKeys {it !in entry.signatures}.forEach {(role,actor)->
                val assignee=when(role) {
                    "KEEPER"->CollectionSafetyAssignee(actor,setOf(CollectionConst.ROLE_STORAGE_HEAD))
                    "USE"->CollectionSafetyAssignee(actor,departmentId=r.dossier?.departmentGroupId)
                    else->CollectionSafetyAssignee(actor,setOf(CollectionConst.ROLE_DIRECTOR))
                }
                addTask("CUSTODY_$role","在用责任承接待本人核对",entry.record,listOf(assignee),entry.record.id)
            }
        }
        if(source==null && (r.outExecuted || r.flowStatus==EnumFlowStatus.PASS))addTask("MISSING_SOURCE","实际提用缺少共同检查来源与职责","MISSING_SOURCE")
        if(latest==null && splitCollectionIds(r.collectionIds).any {it !in received})addTask("MISSING_PLAN","尚缺本次提用共同检查方案",source?.snapshot ?: "MISSING_SOURCE",status=if(r.outExecuted || r.flowStatus==EnumFlowStatus.PASS)"PENDING" else "IN_PROGRESS")
        latest?.let {p->
            if(p.sourceSnapshot!=source?.snapshot)addTask("STALE_PLAN","提用来源变化，共同检查方案待核实",source?.snapshot ?: "MISSING_SOURCE",currentPlanPeople(p),p.id.value)
            else J.signatures(p).filter {it.third==null}.forEach {(role,_,_)->addTask("PLAN_$role","共同检查方案待本人核对",J.planValue(p),listOf(people(p).getValue(role)),p.id.value)}
        }
        if(r.outExecuted && active!=null)splitCollectionIds(r.collectionIds).filter {it !in received}.forEach {cid->
            val due=J.nextDue(r,active,cid);addTask("JOINT_CHECK_DUE","在用藏品定期共同安全检查",listOf(active.id.value,cid,due),people(active).values.toList(),cid=cid,due=due)
        }
        rounds.forEach {rr->
            val p=CollectionJointSafetyPlanDao[rr.planId]
            J.signatures(p,rr).filter {it.third==null}.forEach {(role,_,_)->addTask("ROUND_$role","实际共同检查待本人确认",J.roundValue(rr),listOf(people(p).getValue(role)),rr.id.value)}
            if(J.unsafe(rr) && !J.reportHandled(rr)) {
                if(rr.report==null)addTask("DIRECTOR_REPORT","共同检查发现问题，待实际报告主要负责人",J.roundValue(rr),people(p).values.toList(),rr.id.value)
                else {
                    val handover=CollectionJointReportHandoverEvidence.entries(rr).lastOrNull()
                    if(handover==null)addTask("DIRECTOR_CONFIRM","共同检查问题报告待主要负责人收悉处理",rr.report!!,listOf(CollectionSafetyAssignee(rr.report!!.directorUserId,setOf(CollectionConst.ROLE_DIRECTOR))),rr.id.value)
                    else CollectionJointReportHandoverEvidence.actors(handover).filterKeys {it !in handover.signatures}.forEach {(role,actor)->
                        addTask("REPORT_HANDOVER_$role",if(role=="KEEPER")"实际问题报告移交待本人核对" else "移交报告待新任主要负责人收悉处理",handover.record,
                            listOf(CollectionSafetyAssignee(actor,setOf(if(role=="KEEPER")CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR))),rr.id.value)
                    }
                }
            }
        }
    }
}
