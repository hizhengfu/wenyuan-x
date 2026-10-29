package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionFragileAutomaticEvidence as A
import io.hizhengfu.cloud.collection.service.CollectionFragileMonitoringEvidence as M
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 复用 CARE 的本馆责任人、协调人、开关、去重和重试，不代替人工办理。 */
@Component
class CollectionFragileMonitoringTaskSource(@param:Lazy private val incidents:CollectionIncidentService):CollectionSafetyTaskSource {
    override val domain=EnumCollectionSafetyDomain.CARE
    override fun tasks(orgId:String,at:LocalDateTime):List<CollectionSafetyTask> =
        CollectionFragileUseRecordDao.find {(CollectionFragileUseRecords.orgId eq orgId) and CollectionFragileUseRecords.started.isNotNull()}.flatMap {use->
            try {tasks(use,at)} catch(e:BadRequestException) {
                listOf(safetyTask(orgId,domain,"FRAGILE_ORIGINAL_ERROR","使用监测原件需核查",use.collectionId,use.id.value,use.collectionId,at=at,basis="FRAGILE_ORIGINAL_ERROR",status="ORIGINAL_ERROR",error=e.message))
            }
        }
    private fun tasks(use:CollectionFragileUseRecordDao,at:LocalDateTime):List<CollectionSafetyTask> = buildList {
        E.record(use)
        val c=CollectionDao[use.collectionId]
        if(c.orgId!=use.orgId)E.fail("使用监测的藏品归属需核实")
        val plan=CollectionFragileUsePlanDao[use.planId!!]
        val assessment=CollectionFragilityAssessmentDao[plan.content.assessmentId].content
        val actors=listOf(CollectionSafetyAssignee(assessment.conservationUserId,setOf(CollectionConst.ROLE_CONSERVATION)),
            CollectionSafetyAssignee(assessment.keeperUserId,setOf(CollectionConst.ROLE_STORAGE_HEAD)),CollectionSafetyAssignee(use.started!!.operatorUserId))
        fun addTask(kind:String,title:String,basis:Any,error:String?=null) {
            add(safetyTask(use.orgId,domain,kind,title,use.collectionId,use.id.value,use.collectionId,c.name,at=at,basis=basis,assignees=actors,status=if(error==null)"PENDING" else "ORIGINAL_ERROR",error=error))
        }
        val runtime=A.runtime(use,at)
        if(runtime["lastError"]!=null || runtime["overdue"]==true)
            addTask("FRAGILE_MONITOR_DELAY","实际使用自动监测未及时完成",listOf(use.id.value,"MONITOR_DELAY"),runtime["lastError"] as? String)
        val autos=A.forUse(use.id.value).also {it.forEach(A::verify)}
        val manual=M.all(use.collectionId).filter {it.useRecordId==use.id.value}.also {it.forEach(M::verify)}
        fun unresolved(saved:CollectionCareClearanceSnapshot?):Boolean {
            if(saved==null)return true
            return try {incidents.requireStoredClearance(saved.record.incidentId,use.orgId,use.collectionId,IncidentSourceVo(E.BIZ,use.collectionId,use.id.value),saved.record.confirmedAt,saved.incidentProof,keeperUserId=saved.record.keeperUserId)!=saved.incidentProof}
            catch(_:BadRequestException) {true}
        }
        val problems=autos.filter {it.needsHandling && unresolved(it.clearance)}
        val manualProblems=manual.filter {M.unsafe(it) && unresolved(it.clearance)}
        if(problems.isNotEmpty() || manualProblems.isNotEmpty()) {
            // 同一未处置问题持续跨越多个技术分批时不每分钟重新提醒；新的问题类型会改变依据。
            val kinds=problems.flatMap {r->A.evidence(r).path("metrics").asSequence().flatMap {m->m.path("spans").asSequence().map {it.get("state").stringValue()}}.toList()}.filter {it!="AVAILABLE"}.toSet().sorted()
            addTask("FRAGILE_MONITOR_FOLLOWUP","使用期间超限或监测缺口待实际处置",listOf(problems.firstOrNull()?.id?.value,manualProblems.firstOrNull()?.id?.value,kinds))
        }
        if(use.ended!=null && manual.none {it.request.checkedAt>=use.ended!!.endedAt})
            addTask("FRAGILE_FINAL_INSPECTION","实际使用结束后待核实保管现状",listOf(use.id.value,use.ended))
    }
}
