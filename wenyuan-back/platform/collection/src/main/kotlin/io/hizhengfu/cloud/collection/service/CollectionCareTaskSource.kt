package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C

@Component
class CollectionCareTaskSource(private val incidents:CollectionIncidentService):CollectionSafetyTaskSource {
    override val domain=EnumCollectionSafetyDomain.CARE
    override fun tasks(orgId:String,at:LocalDateTime)=CollectionDao.find {Collections.orgId eq orgId}.filter {it.lifeStatus!=EnumCollectionStatus.DEREGISTERED && it.lifeStatus!=EnumCollectionStatus.PENDING}.flatMap {c->
        try {tasks(c,at)} catch(e:BadRequestException) {listOf(safetyTask(orgId,domain,"ORIGINAL_ERROR","检查养护原件需核查",c.id.value,collection=c.id.value,name=c.name,at=at,basis="ORIGINAL_ERROR",status="ORIGINAL_ERROR",error=e.message))}
    }
    internal fun tasks(c:CollectionDao,at:LocalDateTime):List<CollectionSafetyTask> = buildList {
        val id=c.id.value;val org=c.orgId;val ps=C.plans(id).onEach(C::plan);val cs=C.checks(id).onEach(C::check);val ws=C.works(id).onEach(C::work)
        val active=ps.lastOrNull {it.keeper!=null};val latest=ps.lastOrNull()
        fun people(p:CollectionCarePlanDao)=listOf(CollectionSafetyAssignee(p.content.conservationUserId,setOf(CollectionConst.ROLE_CONSERVATION)),CollectionSafetyAssignee(p.content.keeperUserId,setOf(CollectionConst.ROLE_STORAGE_HEAD)))
        fun addTask(kind:String,title:String,basis:Any,actors:List<CollectionSafetyAssignee> = active?.let(::people).orEmpty(),record:String?=null,due:LocalDateTime?=null,status:String?=null) {add(safetyTask(org,domain,kind,title,id,record,id,c.name,due,at,basis,actors,status))}
        if(latest==null)addTask("MISSING_PLAN","尚缺实际检查养护方案","MISSING_PLAN")
        else {
            if(latest.catalog!=C.catalog(c))addTask("STALE_PLAN","藏品资料已变化，检查养护方案待核实",C.catalog(c),people(latest),latest.id.value)
            if(latest.keeper==null) {
                val role=if(latest.conservation==null)"CONSERVATION" else "KEEPER"
                val actor=if(role=="CONSERVATION")people(latest).first() else people(latest).last()
                addTask("PLAN_$role","检查养护方案待本人核对",C.planValue(latest),listOf(actor),latest.id.value)
            }
        }
        if(active!=null) {
            val checkDue=CollectionCareSchedule.next(ps,cs.map {it.content.checkedAt}){it.content.inspectionIntervalDays}
            addTask("INSPECTION_DUE","定期保存状况检查",listOf(active.id.value,checkDue),due=checkDue)
            if(ws.none {it.ended==null}) {
                val careDue=CollectionCareSchedule.next(ps,ws.filter {it.ended?.completed==true && !C.unsafe(it)}.map {it.ended!!.endedAt}){it.content.careIntervalDays}
                addTask("CARE_DUE","定期日常养护",listOf(active.id.value,careDue),due=careDue)
            }
        }
        fun resolved(s:CollectionCareClearanceSnapshot?):Boolean {
            if(s==null)return false
            val ref=incidents.referenceSummary(org,s.record.incidentId)
            return ref["closed"]==true && ref["basisRevision"]==s.incidentProof["basisRevision"]
        }
        cs.filter {C.unsafe(it) && !resolved(it.clearance)}.forEach {addTask("CHECK_FOLLOWUP","实际检查异常待处置核对",C.checkValue(it),record=it.id.value)}
        ws.forEach {w->
            if(w.ended==null)addTask("WORK_IN_PROGRESS","实际养护尚在实施",C.startValue(w),listOf(CollectionSafetyAssignee(w.started.operatorUserId)),w.id.value,status="IN_PROGRESS")
            else if(C.unsafe(w) && !resolved(w.clearance))addTask("WORK_FOLLOWUP","实际养护异常待处置核对",listOf(C.startValue(w),w.ended!!),record=w.id.value)
        }
    }
}
