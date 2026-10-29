package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InstitutionSuccessionCaseEvidence as E
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import org.jetbrains.exposed.v1.core.*

internal object InstitutionItemHandoverEvidence {
    /** 原辅助账位置保持原记录；实际交接另作只读投影，不写回永久登记原件。 */
    fun auxiliarySummaries(org:String,ids:Set<String>):Map<String,Map<String,Any?>> {
        if(ids.isEmpty())return emptyMap()
        val keys=ids.map {"AUXILIARY:$it"}.toSet()
        val rows=InstitutionSuccessionRecordDao.find {(InstitutionSuccessionRecords.orgId eq org) and (InstitutionSuccessionRecords.kind eq EnumSuccessionCaseRecord.ITEM_HANDOVER)}.filter {E.node(it.content).get("itemKey")?.asText() in keys}
        val cases=rows.map {it.caseId}.distinct().associateWith {id->
            val c=InstitutionSuccessionCaseDao.findById(id) ?: E.fail("辅助账交接案卷不存在")
            if(c.orgId!=org)E.fail("辅助账交接案卷不属于当前收藏单位")
            c.also(E::verify)
        }
        return rows.groupBy {E.node(it.content).get("itemKey").asText().removePrefix("AUXILIARY:")}.mapValues {(_,r)->
            if(r.map {it.caseId}.distinct().size!=1)E.fail("同一辅助账出现多个机构案卷的交接记录，须先核实")
            val c=cases.getValue(r.first().caseId);val batches=r.sortedBy {it.id.value};val actual=batches.map {request<InstitutionItemHandoverVo>(it)}
            val p=findProposal(c,actual.first().proposalRecordId);authorization(c,p)
            val handed=actual.sumOf {it.pieces.size};val total=proposal(p).pieces.size
            val investigation=InstitutionItemInvestigationEvidence.status(c,proposal(p).itemKey)
            mapOf("caseId" to c.id.value,"handedQuantity" to handed,"remainingQuantity" to (total-handed),"lastHandedAt" to actual.last().handedAt,
                "investigationState" to investigation?.get("state"),"hadDiscrepancy" to actual.any {!it.identityMatches || !it.conditionMatches},
                "state" to if(investigation?.get("requiresInvestigation")==true && investigation["confirmed"]!=true)"DISCREPANCY" else if(handed<total)"PARTIAL" else "HANDED_OVER")
        }
    }
    inline fun <reified T> request(r:InstitutionSuccessionRecordDao):T=JsonUtils.fromJson(E.node(r.content).get("request").toString(),T::class.java)!!
    fun proposal(r:InstitutionSuccessionRecordDao)=request<InstitutionItemProposalVo>(r)
    fun findProposal(c:InstitutionSuccessionCaseDao,id:String):InstitutionSuccessionRecordDao {
        val r=InstitutionSuccessionRecordDao.findById(id) ?: E.fail("分类办理方案不存在")
        if(r.caseId!=c.id.value || r.orgId!=c.orgId || r.kind!=EnumSuccessionCaseRecord.ITEM_PROPOSAL)E.fail("方案不属于本馆当前案卷")
        return r
    }
    fun latest(c:InstitutionSuccessionCaseDao,key:String)=E.records(c.id.value).lastOrNull {it.kind==EnumSuccessionCaseRecord.ITEM_PROPOSAL && proposal(it).itemKey==key}
    fun approvals(c:InstitutionSuccessionCaseDao,id:String,kind:EnumSuccessionCaseRecord)=E.records(c.id.value).filter {it.kind==kind && request<InstitutionItemApprovalVo>(it).proposalRecordId==id}
    fun handovers(c:InstitutionSuccessionCaseDao,key:String)=E.records(c.id.value).filter {it.kind==EnumSuccessionCaseRecord.ITEM_HANDOVER && E.node(it.content).get("itemKey")?.asText()==key}
    private fun auxiliary(c:InstitutionSuccessionCaseDao,item:InstitutionSuccessionItemDao):Pair<CollectionAuxiliaryDao,Map<String,Any?>> {
        val a=CollectionAuxiliaryDao.findById(item.sourceId) ?: E.fail("辅助账来源缺失")
        if(a.orgId!=c.orgId)E.fail("辅助账不属于本馆")
        val original=E.archive(c.orgId,"COLLECTION_AUXILIARY",a.id.value,"REGISTERED")
        if(E.node(original.snapshot)!=E.node(E.json(a.toModel())))E.fail("辅助账与登记原件不一致")
        val proof=linkedMapOf<String,Any?>("auxiliary" to a.toModel(),"registeredDigest" to original.digest)
        if(a.content.kind==EnumAuxiliaryKind.LAWFUL_EXIT) {
            val d=a.exitId?.let(DeregistrationDao::findById) ?: E.fail("依法退出辅助账缺少实际退出记录")
            val old=CollectionDao.findById(d.collectionId) ?: E.fail("退出藏品永久账记录缺失")
            if(d.orgId!=c.orgId || d.collectionId!=a.content.originCollectionId || d.executedAt==null || d.flowStatus!=EnumFlowStatus.PASS ||
                d.dossier?.destinationType!=EnumExitDestination.AUXILIARY_ACCOUNT || old.orgId!=c.orgId || old.lifeStatus!=EnumCollectionStatus.DEREGISTERED)E.fail("辅助账未对应本馆真实退出及原定去向，须先核实法定手续")
            val exit=E.archive(c.orgId,CollectionConst.BIZ_DEREGISTER,d.id.value,"EXECUTED")
            val r=E.node(exit.snapshot).get("request")
            for((key,value) in mapOf("id" to d.id.value,"collectionId" to d.collectionId,"orgId" to d.orgId,"dossier" to d.dossier,"reason" to d.reason,"flowStatus" to d.flowStatus,"executedAt" to d.executedAt)) {
                if(value==null || r?.get(key)!=E.node(E.json(value)))E.fail("退出事实或方案与永久执行原件不一致")
            }
            // 原基线保存在提出退出方案时的原件中，执行 DTO 本身没有基线字段。
            val proposed=E.archive(c.orgId,CollectionConst.BIZ_DEREGISTER,d.id.value,"PROPOSAL")
            if(d.baseline==null || E.node(proposed.snapshot).get("baseline")!=E.node(E.json(d.baseline!!)) ||
                E.node(proposed.snapshot).get("dossier")!=E.node(E.json(d.dossier!!)))E.fail("退出基线或处置方案与提出时的永久原件不一致")
            if(old.nature!=d.baseline?.nature)E.fail("退出原件与原藏品性质不一致，不能据此改按非文物处置")
            proof["exitDigest"]=exit.digest;proof["exitProposalDigest"]=proposed.digest;proof["originalNature"]=d.baseline?.nature
        } else if(a.exitId!=null || a.content.originCollectionId!=null || a.content.originTotalRegNo!=null)E.fail("普通辅助账带有正式藏品来源，不能绕过法定退出")
        return a to proof
    }
    fun facts(c:InstitutionSuccessionCaseDao,item:InstitutionSuccessionItemDao,v:InstitutionItemProposalVo):Map<String,Any?> {
        val result=linkedMapOf<String,Any?>("source" to E.itemValue(item))
        if(v.route==EnumInstitutionItemRoute.RECORD_LINK) {
            val target=E.items(c.id.value).singleOrNull {it.itemKey==v.linkedItemKey} ?: E.fail("须先把实际关联来源纳入本案清册")
            if(target.itemKey==item.itemKey || target.sourceKind==EnumSuccessionSource.UNRECORDED)E.fail("须关联实际业务来源，不能相互指向现场发现记录")
            if(item.sourceKind==EnumSuccessionSource.COLLECTION) {
                if(target.sourceKind!=EnumSuccessionSource.AUXILIARY)E.fail("退出历史账只能关联其依法产生的辅助账")
                val (a,proof)=auxiliary(c,target)
                if(a.content.kind!=EnumAuxiliaryKind.LAWFUL_EXIT || a.content.originCollectionId!=item.sourceId)E.fail("须对应本件实际退出所产生的辅助账")
                result["auxiliaryOrigin"]=proof
            } else if(item.sourceKind!=EnumSuccessionSource.UNRECORDED)E.fail("此来源应通过本身业务办理，不能另挂一条记录消除义务")
            val from=E.node(item.baseline);val to=E.node(target.baseline)
            if(from.get("quantity")!=to.get("quantity") || from.get("unit")!=to.get("unit"))E.fail("关联对象数量或计量单位不一致，须先核实拆分、合并及身份关系")
            result["target"]=E.itemValue(target)
        } else {
            if(item.sourceKind !in setOf(EnumSuccessionSource.AUXILIARY,EnumSuccessionSource.UNRECORDED))E.fail("文物、正式藏品及取得来源须走相应法定办理流程")
            if(item.sourceKind==EnumSuccessionSource.AUXILIARY) {
                val (a,proof)=auxiliary(c,item);result["auxiliaryOrigin"]=proof
                if(a.content.kind==EnumAuxiliaryKind.LAWFUL_EXIT) {
                    if(CollectionNaturePolicy.usesRelicRules(proof["originalNature"] as? EnumCollectionNature))E.fail("退出辅助账中的文物仍须核实专门处置与指定依据，不能按普通非藏品交出")
                    if(v.route!=EnumInstitutionItemRoute.NON_COLLECTION_SUCCESSION || v.recipientKind!=EnumInstitutionRecipient.STATE_TEACHING_RESEARCH || v.ownership!=EnumOwnership.STATE)E.fail("依法退出的其他藏品须继续遵守原退出去向，提供国有教学科研单位保管使用")
                }
            }
        }
        return result
    }
    fun requireCurrent(c:InstitutionSuccessionCaseDao,r:InstitutionSuccessionRecordDao):InstitutionSuccessionItemDao {
        val v=proposal(r)
        if(latest(c,v.itemKey)?.id!=r.id)E.fail("分类办理方案已经更新，须按新方案重新确认")
        val item=E.items(c.id.value).singleOrNull {it.itemKey==v.itemKey} ?: E.fail("本案来源记录缺失")
        if(E.node(r.content).get("sourceDigest")?.asText()!=InstitutionSuccessionSources.digest(facts(c,item,v)))E.fail("来源或关联依据已变化，不能沿用原分类办理方案")
        return item
    }
    fun authorization(c:InstitutionSuccessionCaseDao,r:InstitutionSuccessionRecordDao):InstitutionSuccessionRecordDao {
        requireCurrent(c,r)
        val keeper=approvals(c,r.id.value,EnumSuccessionCaseRecord.ITEM_KEEPER).lastOrNull() ?: E.fail("缺少保管负责人本人核对")
        val approval=approvals(c,r.id.value,EnumSuccessionCaseRecord.ITEM_AUTHORIZATION).lastOrNull() ?: E.fail("缺少依法授权人员本人确认")
        if(E.node(approval.content).get("keeperRecordId")?.asText()!=keeper.id.value)E.fail("依法授权人员未确认当前保管核对原件")
        return approval
    }
    fun proof(c:InstitutionSuccessionCaseDao,item:InstitutionSuccessionItemDao,v:InstitutionItemResolutionVo,visited:Set<String>):Map<String,Any?> {
        if(v.kind==EnumSuccessionResolution.LINKED_ITEM) {
            val p=findProposal(c,v.businessId);val plan=proposal(p)
            if(plan.itemKey!=item.itemKey || plan.route!=EnumInstitutionItemRoute.RECORD_LINK)E.fail("须引用本条已核对的实际来源关联方案")
            val approval=authorization(c,p)
            if(v.recordedAt<approval.recordedAt)E.fail("完成核实不能早于来源关联的本人确认记录")
            val target=E.items(c.id.value).single {it.itemKey==plan.linkedItemKey}
            val action=E.records(c.id.value).lastOrNull {r-> when(r.kind) {
                EnumSuccessionCaseRecord.RESOLUTION->request<InstitutionItemResolutionVo>(r).itemKey==target.itemKey
                EnumSuccessionCaseRecord.FOLLOW_UP->E.value<InstitutionItemFollowUpVo>(r).itemKey==target.itemKey
                else->false
            }} ?: E.fail("关联来源尚未核实实际完成")
            if(action.kind!=EnumSuccessionCaseRecord.RESOLUTION)E.fail("关联来源仍有新的未完成事项")
            val resolution=request<InstitutionItemResolutionVo>(action)
            if(resolution.kind==EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED)E.fail("已发现实物不能通过未取得未实收的申请结论消除保管义务")
            if(v.recordedAt<action.recordedAt)E.fail("本次核实不能早于关联对象的实际完成核实")
            val targetProof=E.proof(c,target,resolution,visited)
            if(E.node(action.content).get("proof")!=E.node(E.json(targetProof)))E.fail("关联对象完成依据已变化，须重新核实")
            return mapOf("proposalId" to p.id.value,"authorizationId" to approval.id.value,"linkedItemKey" to target.itemKey,"resolutionId" to action.id.value,"linkedProof" to targetProof)
        }
        val handover=InstitutionSuccessionRecordDao.findById(v.businessId) ?: E.fail("实际承接记录不存在")
        if(handover.caseId!=c.id.value || handover.orgId!=c.orgId || handover.kind!=EnumSuccessionCaseRecord.ITEM_HANDOVER || E.node(handover.content).get("itemKey")?.asText()!=item.itemKey)E.fail("须引用本馆本条实际承接原件")
        val actual=request<InstitutionItemHandoverVo>(handover)
        val p=findProposal(c,actual.proposalRecordId);val approval=authorization(c,p)
        if(proposal(p).route==EnumInstitutionItemRoute.RECORD_LINK)E.fail("来源关联方案不构成实际交接")
        val batches=handovers(c,item.itemKey)
        if(batches.lastOrNull()?.id!=handover.id)E.fail("已有后续实际交接批次，请引用最新批次核实完整承接")
        val all=batches.map {request<InstitutionItemHandoverVo>(it)}
        if(all.any {it.proposalRecordId!=p.id.value} || batches.any {E.node(it.content).get("authorizationId")?.asText()!=approval.id.value})E.fail("各批次须对应同一已确认方案和依法负责人员授权原件")
        val investigation=InstitutionItemInvestigationEvidence.completedProof(c,item.itemKey,v.recordedAt)
        if(all.flatMap {it.pieces}.map {it.pieceNo.trim()}.toSet()!=proposal(p).pieces.map {it.pieceNo.trim()}.toSet())E.fail("实际承接尚未收齐全部方案对象，不能核实为完成")
        if(v.recordedAt<handover.recordedAt || v.recordedAt<actual.handedAt)E.fail("核实时间不能早于实际承接及其登记原件")
        val proof=linkedMapOf<String,Any?>("proposalId" to p.id.value,"authorizationId" to approval.id.value,"handoverId" to handover.id.value,"handoverDigest" to InstitutionSuccessionSources.digest(batches.map(E::recordValue)))
        if(investigation!=null)proof["investigation"]=investigation
        return proof
    }
}
