package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionFragileMonitoringEvidence as M
import io.hizhengfu.cloud.collection.service.CollectionFragileAutomaticEvidence as A
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class CollectionFragileMonitoringSafetyGuard(private val archives:CollectionPermanentArchiveService,@param:Lazy private val incidents:CollectionIncidentService?=null,private val restricted:CollectionRestrictedOperationService=CollectionRestrictedOperationService()):CollectionOperationSafetyGuard,IncidentResolutionGuard {
    private val originals=CollectionOperationSafetyOriginals(archives)
    override fun requireAllowed(collectionId:String,bizType:String?,bizId:String?) {
        if(bizType in setOf(CollectionConst.BIZ_RETURN,CollectionConst.BIZ_INVENTORY,CollectionConst.BIZ_CHANGE))return
        if(restricted.permits(collectionId,bizType,bizId)){requireSafeResolution(collectionId);return}
        if(CollectionFragileMonitorRuntimeDao.find {CollectionFragileMonitorRuntimes.collectionId eq collectionId}.any {it.lastError!=null})E.fail("本件自动监测核验失败尚未恢复，须先核实缺少的区间证据")
        val reports=M.all(collectionId).also {it.forEach(originals::fragileMonitoring)}
        E.records(collectionId).onEach(originals::fragileRecord).filter {it.started!=null && it.ended!=null}.forEach {use->
            val last=reports.filter {it.useRecordId==use.id.value}.lastOrNull()
            val coveredUntil=last?.let {JsonUtils.fromJson(M.evidence(it).get("to").toString(),java.time.LocalDateTime::class.java)}
            if(coveredUntil==null || coveredUntil<use.ended!!.endedAt)E.fail("此前实际展出或运输尚未核验至真实结束，不能以开始前合格测值代替全过程监测证据")
        }
        A.all(collectionId).forEach {r->
            originals.fragileAutomatic(r)
            if(r.needsHandling) {
                val saved=r.clearance ?: E.fail("本件自动监测发现的超限或证据缺口尚未实际处置核对，后来正常读数不能解除限制")
                val proof=incidents?.requireStoredClearance(saved.record.incidentId,r.orgId,collectionId,IncidentSourceVo(E.BIZ,collectionId,r.useRecordId),saved.record.confirmedAt,saved.incidentProof,keeperUserId=saved.record.keeperUserId)
                if(proof!=saved.incidentProof)E.fail("自动监测异常的实际处置依据已变化，须重新核实")
            }
        }
        reports.forEach {r->
            originals.fragileMonitoring(r)
            if(M.unsafe(r)) {
                val saved=r.clearance ?: E.fail("本件使用期间监测中断、超限或实物异常尚未实际处置核对，后来正常读数不能解除限制")
                val proof=incidents?.requireStoredClearance(saved.record.incidentId,r.orgId,collectionId,IncidentSourceVo(E.BIZ,collectionId,r.useRecordId),saved.record.confirmedAt,saved.incidentProof,keeperUserId=saved.record.keeperUserId)
                if(proof!=saved.incidentProof)E.fail("使用期间监测异常的处置依据已变化，须重新核实")
            }
        }
    }
    override fun requireExclusiveWork(collectionId:String,bizType:String,bizId:String)=requireSafeResolution(collectionId)
    override fun requireSafeResolution(collectionId:String) {
        M.all(collectionId).forEach(originals::fragileMonitoring)
        A.all(collectionId).forEach(originals::fragileAutomatic)
        if(E.records(collectionId).onEach(originals::fragileRecord).any {it.started!=null && it.ended==null})E.fail("本件实际展出或运输尚未停止结束，不能确认已经恢复安全")
    }
    override fun requireHandlingEvidence(facts:IncidentFactsVo,completedAt:java.time.LocalDateTime) {
        val source=facts.source?.takeIf {it.bizType==E.BIZ} ?: return
        val use=source.recordId?.let(CollectionFragileUseRecordDao::findById) ?: E.fail("实际使用来源不存在")
        originals.fragileRecord(use)
        val end=use.ended ?: E.fail("须据实记录使用停止结束")
        val reports=M.all(use.collectionId).filter {it.useRecordId==use.id.value && M.unsafe(it)}
        val automatic=A.forUse(use.id.value).filter {it.needsHandling}.also {it.forEach(originals::fragileAutomatic)}
        if(automatic.any {it.observedAt>completedAt})E.fail("处理完成不得早于本次使用自动发现的异常")
        if(completedAt<end.endedAt || reports.any {completedAt<it.request.checkedAt})E.fail("处理完成不得早于实际使用结束和来源核验")
    }
}

@Component
class CollectionFragileMonitoringIncidentSourceProvider(archives:CollectionPermanentArchiveService):CollectionIncidentSourceProvider {
    private val originals=CollectionOperationSafetyOriginals(archives)
    override val bizType=E.BIZ
    private fun use(org:String,source:IncidentSourceVo):CollectionFragileUseRecordDao {
        val r=source.recordId?.let(CollectionFragileUseRecordDao::findById) ?: E.fail("须关联发生监测异常的实际使用记录")
        if(r.orgId!=org || r.collectionId!=source.bizId || r.started==null)E.fail("来源不属于本馆本件实际使用")
        originals.fragileRecord(r);return r
    }
    private fun reports(use:CollectionFragileUseRecordDao)=M.all(use.collectionId).filter {it.useRecordId==use.id.value}.also {it.forEach(originals::fragileMonitoring)}
    override fun snapshot(orgId:String,source:IncidentSourceVo,items:List<IncidentItemVo>):Any {
        val use=use(orgId,source);val reports=reports(use).filter(M::unsafe)
        val automatic=A.forUse(use.id.value).onEach(originals::fragileAutomatic).filter {it.needsHandling}
        if((reports.isEmpty() && automatic.isEmpty()) || items.size!=1 || items.single().collectionId!=use.collectionId)E.fail("案卷须对应本次实际使用及已记录的监测异常")
        if(reports.any {it.request.damaged} && !items.single().damaged)E.fail("案卷不能省略监测核验已发现的损伤")
        val plan=CollectionFragileUsePlanDao[use.planId!!]
        return mapOf("collectionId" to use.collectionId,"useRecordId" to use.id.value,"catalog" to CollectionFragilityAssessmentDao[plan.content.assessmentId].catalog,"reports" to reports.map(M::value))+if(automatic.isEmpty())emptyMap() else mapOf("automaticChecks" to automatic.map(A::value))
    }
    override fun reportingCatalogs(evidence:String):Map<String,CollectionCatalogContentVo> {
        val root=JsonUtils.mapper.readTree(evidence)
        return mapOf(root.get("collectionId").stringValue() to JsonUtils.fromJson(root.get("catalog").toString(),CollectionCatalogContentVo::class.java)!!)
    }
    override fun stoppedCustody(orgId:String,collectionId:String,source:IncidentSourceVo):IncidentStoppedCustody {
        val use=use(orgId,source)
        if(collectionId!=use.collectionId)E.fail("实际保管来源与本件不一致")
        A.forUse(use.id.value).forEach(originals::fragileAutomatic)
        val ended=use.ended ?: E.fail("须先如实登记实际展出或运输停止结束")
        val current=reports(use).maxByOrNull {it.request.checkedAt} ?: E.fail("缺少停止后的实际保管核验")
        if(current.request.checkedAt<ended.endedAt || !current.request.physicallyPresent || current.request.custodianUserId.isNullOrBlank())E.fail("须在实际停止结束后重新核实实物在场、保管人员及位置")
        return IncidentStoppedCustody(current.request.checkedAt,E.json(mapOf("ended" to ended,"custody" to M.value(current))))
    }
}
