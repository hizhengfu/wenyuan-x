package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import java.time.LocalDateTime

internal data class FragileEffectiveSegment(
    val kind:EnumFragileUseKind,val segmentIndex:Int,val from:LocalDateTime,val to:LocalDateTime,
    val route:String?,val protection:String,val cancelled:Boolean=false,val authorizedAt:LocalDateTime?,val adjustmentId:String?=null,
)
internal object CollectionFragileAdjustmentEvidence {
    fun all(planId:String)=CollectionFragileUseAdjustmentDao.find {CollectionFragileUseAdjustments.planId eq planId}.sortedBy {it.id.value}
    fun value(a:CollectionFragileUseAdjustmentDao)=mapOf("collectionId" to a.collectionId,"planId" to a.planId,"proposedBy" to a.proposedBy,"factsSnapshot" to a.factsSnapshot,"actors" to a.actors,"content" to a.content)
    fun signatures(a:CollectionFragileUseAdjustmentDao)=listOfNotNull(
        Triple("CONSERVATION",a.actors.conservation,a.conservation),
        a.actors.department?.let {Triple("DEPARTMENT",it,a.department)},
        Triple("KEEPER",a.actors.keeper,a.keeper),Triple("DIRECTOR",a.actors.director,a.director),
    )
    fun verify(a:CollectionFragileUseAdjustmentDao) {
        val p=CollectionFragileUsePlanDao.findById(a.planId) ?: E.fail("变更缺少原使用保护方案")
        if(a.collectionId!=p.collectionId || a.orgId!=p.orgId)E.fail("变更与原方案归属不一致")
        E.plan(p);E.exact(a.orgId,a.id.value,"ADJUSTMENT",value(a))
        var previous:CollectionCareSignature?=null
        var missing=false
        signatures(a).forEach {(role,actor,s)->
            if(s==null)missing=true else {
                E.exact(a.orgId,a.id.value,"ADJUSTMENT_$role",s)
                if(missing || actor!=s.actorUserId || previous?.confirmedAt?.isAfter(s.confirmedAt)==true)E.fail("变更确认人员或顺序与原件不一致")
                previous=s
            }
        }
        a.withdrawal?.let {E.exact(a.orgId,a.id.value,"ADJUSTMENT_WITHDRAWN",it);if(a.director!=null)E.fail("已生效变更不能撤回为未实施")}
    }
    fun pending(p:CollectionFragileUsePlanDao)=all(p.id.value).lastOrNull()?.takeIf {it.director==null && it.withdrawal==null}
    fun segments(p:CollectionFragileUsePlanDao,candidate:CollectionFragileUseAdjustmentDao?=null):List<FragileEffectiveSegment> {
        val result=mutableListOf<FragileEffectiveSegment>()
        p.content.displayFrom?.let {result+=FragileEffectiveSegment(EnumFragileUseKind.DISPLAY,0,it,p.content.displayTo!!,null,p.content.environmentAndProtection,authorizedAt=p.keeper?.confirmedAt)}
        p.content.transports.forEachIndexed {i,t->result+=FragileEffectiveSegment(EnumFragileUseKind.TRANSPORT,i,t.departsAt,t.arrivesAt,t.route,t.protection,authorizedAt=p.keeper?.confirmedAt)}
        all(p.id.value).also {it.forEach(::verify)}.filter {it.director!=null && it.withdrawal==null || it.id==candidate?.id}.forEach {a->
            a.content.adjustments.forEach {v->
                val index=result.indexOfFirst {it.kind==v.kind && it.segmentIndex==v.segmentIndex}
                if(index<0)E.fail("变更指向不存在的原计划段")
                val old=result[index]
                if(old.cancelled)E.fail("已核实取消的段不能重新启用，请另办新的具体安排")
                result[index]=if(v.action==EnumFragileAdjustmentAction.CANCEL)old.copy(cancelled=true,adjustmentId=a.id.value,authorizedAt=a.director?.confirmedAt)
                    else old.copy(from=v.from!!,to=v.to!!,route=v.route ?: old.route,protection=v.protection!!,adjustmentId=a.id.value,authorizedAt=a.director?.confirmedAt)
            }
        }
        return result
    }
    fun facts(c:CollectionDao,p:CollectionFragileUsePlanDao):String {
        val records=E.records(c.id.value).also {it.forEach(E::record)}
        val checks=CollectionCareEvidence.checks(c.id.value).also {it.forEach(CollectionCareEvidence::check)}
        return E.json(mapOf("catalog" to CollectionCareEvidence.catalog(c),"lifeStatus" to c.lifeStatus,"inventoryHold" to c.inventoryHold,
            "source" to E.source(p.sourceBizType,p.sourceBizId),
            "records" to records.map {mapOf("id" to it.id.value,"planId" to it.planId,"started" to it.started,"authorizationAdjustmentId" to it.authorizationAdjustmentId,"ended" to it.ended,"review" to it.review,"history" to it.history)},
            "loanMonitoring" to E.loanMonitoring(c.orgId,p.sourceBizType,p.sourceBizId).map {(id,v)->mapOf("id" to id,"content" to v)},
            "checks" to checks.map {mapOf("id" to it.id.value,"content" to it.content,"clearance" to it.clearance)},
            "incidents" to CollectionIncidentItemDao.find {CollectionIncidentItems.collectionId eq c.id.value}.sortedBy {it.id.value}.map {mapOf("id" to it.id.value,"restricted" to it.restricted)}))
    }
    fun outbound(c:CollectionDao,p:CollectionFragileUsePlanDao):CollectionPermanentArchiveDao {
        val s=E.source(p.sourceBizType,p.sourceBizId) ?: E.fail("实际使用来源不存在")
        val out=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq p.sourceBizType) and (CollectionPermanentArchives.bizId eq p.sourceBizId) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED")}.singleOrNull() ?: E.fail("缺少实际出库原件")
        val request=JsonUtils.mapper.readTree(out.snapshot).get("request") ?: E.fail("实际出库原件缺少申请资料")
        if(!s.outExecuted || s.handedAt==null || p.keeper==null || p.keeper!!.confirmedAt.isAfter(s.handedAt) || out.orgId!=c.orgId || out.digest!=E.digest(out.snapshot) || c.id.value !in request.get("collectionIds")?.stringValue().orEmpty().split(',') || request.get("handover")?.get("content")?.get("handedAt")!=JsonUtils.mapper.readTree(E.json(s.handedAt)))E.fail("实际出库与本件已批准原方案不一致")
        return out
    }
}
