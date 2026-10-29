package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class CollectionCareSafetyGuard(private val archives:CollectionPermanentArchiveService,@param:Lazy private val incidents: CollectionIncidentService? = null) : CollectionOperationSafetyGuard, IncidentResolutionGuard {
    private val originals=CollectionOperationSafetyOriginals(archives)
    override fun requireSafeResolution(collectionId: String) {
        CollectionCareEvidence.checks(collectionId).forEach(originals::careCheck)
        if(CollectionCareEvidence.works(collectionId).onEach(originals::careWork).any {it.ended==null})CollectionCareEvidence.fail("本件实际养护尚未结束，不能确认安全或并行实施其他作业")
    }
    override fun requireExclusiveWork(collectionId: String,bizType: String,bizId: String) = requireSafeResolution(collectionId)
    override fun requireHandlingEvidence(facts: IncidentFactsVo,completedAt: java.time.LocalDateTime) {
        val source=facts.source?.takeIf {it.bizType==CollectionConst.BIZ_CARE} ?: return
        val at=source.recordId?.let {id->CollectionCareCheckDao.findById(id)?.content?.checkedAt ?: CollectionCareWorkDao.findById(id)?.ended?.endedAt}
            ?: CollectionCareEvidence.fail("检查养护来源尚未形成实际异常记录")
        if(completedAt.isBefore(at))CollectionCareEvidence.fail("事故处理完成时间不得早于来源检查或养护实际结束")
    }
    override fun requireAllowed(collectionId: String,bizType: String?,bizId: String?) {
        if(bizType in setOf(CollectionConst.BIZ_RETURN,CollectionConst.BIZ_INVENTORY,CollectionConst.BIZ_CHANGE))return
        requireSafeResolution(collectionId)
        CollectionCareEvidence.checks(collectionId).forEach {
            originals.careCheck(it)
            if(CollectionCareEvidence.unsafe(it))cleared(it.orgId,collectionId,it.id.value,it.clearance)
        }
        CollectionCareEvidence.works(collectionId).forEach {
            originals.careWork(it)
            if(CollectionCareEvidence.unsafe(it))cleared(it.orgId,collectionId,it.id.value,it.clearance)
        }
    }
    private fun cleared(org: String,collectionId: String,id: String,saved: CollectionCareClearanceSnapshot?) {
        saved ?: CollectionCareEvidence.fail("本件日常检查或养护异常尚未实际处置核对，普通审批不能解除")
        val proof=incidents?.requireStoredClearance(saved.record.incidentId,org,collectionId,IncidentSourceVo(CollectionConst.BIZ_CARE,collectionId,id),saved.record.confirmedAt,saved.incidentProof,keeperUserId=saved.record.keeperUserId)
        if(proof!=saved.incidentProof)CollectionCareEvidence.fail("检查养护事故处置依据已变化，须重新核实")
    }
}

@Component
class CollectionCareIncidentSourceProvider(archives:CollectionPermanentArchiveService) : CollectionIncidentSourceProvider {
    private val originals=CollectionOperationSafetyOriginals(archives)
    override val bizType = CollectionConst.BIZ_CARE
    override fun snapshot(orgId: String,source: IncidentSourceVo,items: List<IncidentItemVo>): Any {
        val id=source.recordId ?: CollectionCareEvidence.fail("须选择实际检查或养护结束记录")
        if(items.map {it.collectionId}.toSet()!=setOf(source.bizId))CollectionCareEvidence.fail("日常检查养护来源须对应本件藏品")
        val check=CollectionCareCheckDao.findById(id)
        if(check!=null) {
            if(check.orgId!=orgId || check.collectionId!=source.bizId || !CollectionCareEvidence.unsafe(check))CollectionCareEvidence.fail("来源须为本馆本件实际检查异常")
            originals.careCheck(check)
            if(check.content.damaged && !items.single().damaged)CollectionCareEvidence.fail("案卷不能省略检查已发现的损伤")
            return CollectionCareEvidence.checkValue(check)
        }
        val work=CollectionCareWorkDao.findById(id) ?: CollectionCareEvidence.fail("实际养护记录不存在")
        if(work.orgId!=orgId || work.collectionId!=source.bizId || work.ended==null || !CollectionCareEvidence.unsafe(work))CollectionCareEvidence.fail("须先据实记录本馆本件养护异常结束；紧急首报可独立登记")
        originals.careWork(work)
        if(work.ended!!.unexpectedDamage && !items.single().damaged)CollectionCareEvidence.fail("案卷不能省略养护已记录的意外损伤")
        return mapOf("collectionId" to work.collectionId,"catalog" to CollectionCarePlanDao[work.planId].catalog,"started" to work.started,"ended" to work.ended)
    }
    override fun reportingCatalogs(evidence: String): Map<String,CollectionCatalogContentVo> {
        val v=JsonUtils.mapper.readTree(evidence)
        return mapOf(v.get("collectionId").asText() to JsonUtils.fromJson(v.get("catalog").toString(),CollectionCatalogContentVo::class.java)!!)
    }
    override fun stoppedCustody(orgId: String,collectionId: String,source: IncidentSourceVo): IncidentStoppedCustody {
        if(source.bizId!=collectionId)CollectionCareEvidence.fail("检查养护保管来源与实物不一致")
        val check=source.recordId?.let(CollectionCareCheckDao::findById)
        val work=source.recordId?.let(CollectionCareWorkDao::findById)
        val damaged=check?.content?.damaged ?: work?.ended?.unexpectedDamage ?: false
        val evidence=snapshot(orgId,source,listOf(IncidentItemVo(collectionId,damaged,EnumIncidentSecurity.NONE,false,"核对实际保管")))
        val present=check?.content?.physicallyPresent ?: work!!.ended!!.physicallyPresent
        val person=check?.content?.custodianUserId ?: work?.ended?.custodianUserId
        if(!present || person.isNullOrBlank())CollectionCareEvidence.fail("来源尚未核实实物在场及保管人员，不能据此授权现场防护")
        return IncidentStoppedCustody(check?.content?.checkedAt ?: work!!.ended!!.endedAt,CollectionCareEvidence.json(evidence))
    }
}
