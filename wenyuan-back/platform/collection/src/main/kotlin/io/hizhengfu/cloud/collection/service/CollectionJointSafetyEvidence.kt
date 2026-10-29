package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.LocalDateTime

internal object CollectionJointSafetyEvidence {
    const val BIZ="COLLECTION_JOINT_SAFETY"
    // 拒绝错馆子记录后才读取 JSON 原件，不能以另一馆记录填补本案历史。
    fun requireOwn(r:RequisitionDao) {
        if(CollectionJointSafetyPlans.selectAll().where {(CollectionJointSafetyPlans.requisitionId eq r.id.value) and (CollectionJointSafetyPlans.orgId neq r.orgId)}.count()>0 ||
            CollectionJointSafetyRounds.selectAll().where {(CollectionJointSafetyRounds.requisitionId eq r.id.value) and (CollectionJointSafetyRounds.orgId neq r.orgId)}.count()>0)C.fail("共同检查子记录不属于本馆本次提用，请核查原件归属")
    }
    fun plans(id:String)=CollectionJointSafetyPlanDao.find {CollectionJointSafetyPlans.requisitionId eq id}.sortedBy {it.version}
    fun rounds(id:String)=CollectionJointSafetyRoundDao.find {CollectionJointSafetyRounds.requisitionId eq id}.sortedWith(compareBy({it.content.endedAt},{it.id.value}))
    fun baseSource(r:RequisitionDao)=CollectionFragilityEvidence.source(CollectionConst.BIZ_REQUISITION,r.id.value) ?: C.fail("须先保存完整提用方案及逐件清单")
    fun source(r:RequisitionDao):FragileUseSource {
        val base=baseSource(r)
        val effective=RequisitionCustodyEvidence.entries(r).lastOrNull {it.complete}
        return base.copy(snapshot=RequisitionCustodyEvidence.snapshot(base.snapshot,effective))
    }
    fun planValue(p:CollectionJointSafetyPlanDao)=mapOf("requisitionId" to p.requisitionId,"version" to p.version,"sourceSnapshot" to p.sourceSnapshot,"content" to p.content)
    fun roundValue(r:CollectionJointSafetyRoundDao)=mapOf("requisitionId" to r.requisitionId,"planId" to r.planId,"checksSnapshot" to r.checksSnapshot,"content" to r.content)
    fun exact(org:String,id:String,phase:String,value:Any) {
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.singleOrNull() ?: C.fail("缺少多部门安全检查永久原件：$phase")
        val original=JsonUtils.mapper.readTree(a.snapshot);val current=JsonUtils.mapper.readTree(C.json(value))
        // checkIds 是集合。保留原始 JSON 和摘要，仅比对时规范集合顺序，兼容已归档的排列。
        if(phase=="ROUND")listOf(original,current).forEach {node->
            val content=node.get("content") as? tools.jackson.databind.node.ObjectNode ?: C.fail("共同检查原件缺少实际记录")
            val ids=content.get("checkIds") ?: C.fail("共同检查原件缺少逐件检查")
            content.set("checkIds",JsonUtils.mapper.readTree(C.json(JsonUtils.fromJson(ids.toString(),Array<String>::class.java)!!.sorted())))
        }
        if(a.orgId!=org || a.digest!=C.digest(a.snapshot) || original!=current)C.fail("多部门安全检查与永久原件不一致")
    }
    fun signatures(p:CollectionJointSafetyPlanDao)=listOf(Triple("KEEPER",p.content.keeperUserId,p.keeper),Triple("USE",p.content.useUserId,p.use),Triple("SECURITY",p.content.securityUserId,p.security))
    fun signatures(p:CollectionJointSafetyPlanDao,r:CollectionJointSafetyRoundDao)=listOf(Triple("KEEPER",p.content.keeperUserId,r.keeper),Triple("USE",p.content.useUserId,r.use),Triple("SECURITY",p.content.securityUserId,r.security))
    private fun verifySigns(org:String,id:String,values:List<Triple<String,String,CollectionCareSignature?>>) {
        values.forEach {(role,user,s)->
            val archived=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq role)}.any()
            if((s!=null)!=archived)C.fail("共同检查本人确认与永久原件缺失状态不一致")
            s?.let {exact(org,id,role,it);if(it.actorUserId!=user)C.fail("共同检查确认人与指定职责不一致")}
        }
    }
    fun plan(p:CollectionJointSafetyPlanDao) {exact(p.orgId,p.id.value,"PLAN",planValue(p));verifySigns(p.orgId,p.id.value,signatures(p))}
    fun checked(r:CollectionJointSafetyRoundDao)=r.content.checkIds.map {CollectionCareCheckDao.findById(it) ?: C.fail("共同检查关联的实际现状原件不存在")}.sortedBy {it.collectionId}
    fun checkSnapshot(checks:List<CollectionCareCheckDao>)=C.json(checks.sortedBy {it.collectionId}.map {mapOf("id" to it.id.value,"collectionId" to it.collectionId,"catalog" to it.catalog,"content" to it.content)})
    fun round(r:CollectionJointSafetyRoundDao) {
        val p=CollectionJointSafetyPlanDao.findById(r.planId) ?: C.fail("共同检查方案不存在")
        if(p.orgId!=r.orgId || p.requisitionId!=r.requisitionId || r.content.planId!=p.id.value)C.fail("共同检查方案和实际记录归属不一致")
        plan(p);exact(r.orgId,r.id.value,"ROUND",roundValue(r));verifySigns(r.orgId,r.id.value,signatures(p,r))
        val cc=checked(r);cc.forEach {if(it.orgId!=r.orgId)C.fail("实际现状检查不属于本馆");C.check(it)}
        if(checkSnapshot(cc)!=r.checksSnapshot)C.fail("共同核查的逐件现状原件发生变化")
        for((phase,present) in listOf("DIRECTOR_REPORT" to (r.report!=null),"DIRECTOR" to (r.director!=null))) {
            if(CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq r.id.value) and (CollectionPermanentArchives.phase eq phase)}.any()!=present)C.fail("共同检查报告或收悉标记与原件缺失状态不一致")
        }
        r.report?.let {exact(r.orgId,r.id.value,"DIRECTOR_REPORT",it)}
        r.director?.let {exact(r.orgId,r.id.value,"DIRECTOR",it);if(r.report==null || it.actorUserId!=r.report!!.directorUserId || it.confirmedAt.isBefore(r.report!!.reportedAt))C.fail("主要负责人实际收悉处理与报告原件不一致")}
        val currentBasis=CollectionJointReportHandoverEvidence.currentBasis(r)
        if(r.report!=null && r.report!!.directorUserId!=p.content.directorUserId && currentBasis==null)C.fail("向现任负责人报告缺少对应的职责变更原件")
        CollectionJointReportHandoverEvidence.entries(r)
    }
    fun reportHandled(r:CollectionJointSafetyRoundDao)=r.director!=null || CollectionJointReportHandoverEvidence.entries(r).any {it.complete}
    fun confirmed(p:CollectionJointSafetyPlanDao)=signatures(p).all {it.third!=null}
    fun confirmed(p:CollectionJointSafetyPlanDao,r:CollectionJointSafetyRoundDao)=signatures(p,r).all {it.third!=null}
    fun unsafe(r:CollectionJointSafetyRoundDao)=checked(r).any {C.unsafe(it)}
    fun current(r:RequisitionDao):CollectionJointSafetyPlanDao?=plans(r.id.value).lastOrNull(::confirmed)?.also {plan(it);if(it.sourceSnapshot!=source(r).snapshot)C.fail("本次提用或参与职责已变化，须重新核实共同安全检查方案")}
    fun nextDue(r:RequisitionDao,p:CollectionJointSafetyPlanDao,collectionId:String):LocalDateTime {
        val inheritedAt=RequisitionCustodyEvidence.entries(r).lastOrNull {it.complete && RequisitionCustodyEvidence.snapshot(baseSource(r).snapshot,it)==p.sourceSnapshot}?.effectiveAt
        val last=rounds(r.id.value).filter {row->round(row);confirmed(CollectionJointSafetyPlanDao[row.planId],row) && checked(row).any {it.collectionId==collectionId}}.flatMap {checked(it)}.filter {it.collectionId==collectionId && (inheritedAt==null || !it.content.checkedAt.isBefore(inheritedAt))}.maxOfOrNull {it.content.checkedAt}
        if(last!=null)return last.plusDays(p.content.inspectionIntervalDays.toLong())
        val s=source(r);val agreed=signatures(p).mapNotNull {it.third?.confirmedAt}.maxOrNull()!!
        // 事后补建机制不补造此前已定期检查；普通新作业前应先完成真实初次核查。
        if(s.handedAt!=null && agreed.isAfter(s.handedAt))return agreed
        return minOf(p.content.firstDueAt,s.handedAt?.plusDays(p.content.inspectionIntervalDays.toLong()) ?: p.content.firstDueAt)
    }
    fun outbound(r:RequisitionDao) {
        val source=baseSource(r)
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq r.id.value) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED")}.singleOrNull() ?: C.fail("缺少实际提用出库原件")
        val root=JsonUtils.mapper.readTree(a.snapshot).get("request") ?: C.fail("实际出库原件缺少申请资料")
        if(!r.outExecuted || source.handedAt==null || a.orgId!=r.orgId || a.digest!=C.digest(a.snapshot) || root.get("id")?.stringValue()!=r.id.value || root.get("collectionIds")?.stringValue()!=r.collectionIds || root.get("handover")!=JsonUtils.mapper.readTree(C.json(r.handover ?: C.fail("缺少实际点交"))))C.fail("实际出库原件与本次提用清单及点交不一致")
    }
    fun receivedIds(r:RequisitionDao):Set<String> = receivedAt(r).keys
    fun receivedAt(r:RequisitionDao):Map<String,LocalDateTime> = CollectionReturnDao.find {(CollectionReturns.sourceBizType eq CollectionConst.BIZ_REQUISITION) and (CollectionReturns.sourceBizId eq r.id.value) and (CollectionReturns.executionState eq EnumReturnExecutionState.EXECUTED)}.flatMap {ret->
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq ret.id.value) and (CollectionPermanentArchives.phase eq "RECEIVED")}.singleOrNull() ?: C.fail("归还标记缺少实际接收永久原件")
        val root=JsonUtils.mapper.readTree(a.snapshot)
        if(ret.orgId!=r.orgId || a.orgId!=r.orgId || a.digest!=C.digest(a.snapshot) || root.get("id")?.stringValue()!=ret.id.value || root.get("orgId")?.stringValue()!=r.orgId || root.get("sourceBizType")?.stringValue()!=CollectionConst.BIZ_REQUISITION || root.get("sourceBizId")?.stringValue()!=r.id.value || root.get("collectionIds")?.stringValue()!=ret.collectionIds || root.get("inspection")!=JsonUtils.mapper.readTree(C.json(ret.inspection ?: C.fail("缺少实际接收查验"))) || splitCollectionIds(ret.collectionIds).any {it !in splitCollectionIds(r.collectionIds)})C.fail("归还实际接收原件与来源清单不一致")
        splitCollectionIds(ret.collectionIds).map {it to ret.inspection!!.inspectedAt}
    }.groupBy({it.first},{it.second}).mapValues {it.value.min()}
}
