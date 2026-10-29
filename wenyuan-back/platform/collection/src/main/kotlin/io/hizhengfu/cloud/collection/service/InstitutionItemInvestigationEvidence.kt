package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InstitutionItemHandoverEvidence as H
import io.hizhengfu.cloud.collection.service.InstitutionSuccessionCaseEvidence as E
import java.time.LocalDateTime

internal object InstitutionItemInvestigationEvidence {
    data class Basis(val key:String,val batches:List<InstitutionSuccessionRecordDao>,val plan:InstitutionItemProposalVo,
        val digest:String,val after:LocalDateTime,val followUps:List<InstitutionSuccessionRecordDao>)
    fun latest(c:InstitutionSuccessionCaseDao,key:String)=E.records(c.id.value).lastOrNull {
        it.kind==EnumSuccessionCaseRecord.ITEM_INVESTIGATION && E.node(it.content).get("itemKey")?.asText()==key
    }
    fun basis(c:InstitutionSuccessionCaseDao,key:String):Basis {
        val batches=H.handovers(c,key)
        if(batches.isEmpty())E.fail("尚无实际交接原件，不能登记该对象的交接差异核查")
        val p=H.findProposal(c,H.request<InstitutionItemHandoverVo>(batches.first()).proposalRecordId)
        val authorization=H.authorization(c,p)
        val followUps=E.records(c.id.value).filter {it.kind==EnumSuccessionCaseRecord.FOLLOW_UP && E.value<InstitutionItemFollowUpVo>(it).itemKey==key}
        val after=(batches.map {H.request<InstitutionItemHandoverVo>(it).handedAt}+followUps.map {E.value<InstitutionItemFollowUpVo>(it).recordedAt}).max()
        val facts=mapOf("proposal" to E.recordValue(p),"authorization" to E.recordValue(authorization),
            "batches" to batches.map(E::recordValue),"followUps" to followUps.map(E::recordValue))
        return Basis(key,batches,H.proposal(p),InstitutionSuccessionSources.digest(facts),after,followUps)
    }
    fun find(c:InstitutionSuccessionCaseDao,id:String):InstitutionSuccessionRecordDao {
        val r=InstitutionSuccessionRecordDao.findById(id) ?: E.fail("交接差异核查原件不存在")
        if(r.orgId!=c.orgId || r.caseId!=c.id.value || r.kind!=EnumSuccessionCaseRecord.ITEM_INVESTIGATION)E.fail("核查原件不属于本馆本案")
        return r
    }
    fun current(c:InstitutionSuccessionCaseDao,r:InstitutionSuccessionRecordDao):Basis {
        val key=E.node(r.content).get("itemKey").asText();val b=basis(c,key)
        if(latest(c,key)?.id!=r.id)E.fail("已有新的核查原件，请核对最新版本")
        val v=H.request<InstitutionItemInvestigationVo>(r)
        if(v.basisDigest!=b.digest || v.handoverRecordId!=b.batches.last().id.value)E.fail("交接批次或未结事项已变化，须追加本版实际核查并重新确认")
        return b
    }
    fun incomplete(v:InstitutionItemInvestigationVo,p:InstitutionItemProposalVo):String? = when {
        !v.allIssuesResolved -> "已知问题尚未全部处理"
        !v.thirdPartyRightsResolved || v.thirdPartyRightsFileIds.isBlank() -> "第三方权利尚未处理"
        v.senderStatementFileIds.isNullOrBlank() || v.receiverStatementFileIds.isNullOrBlank() -> "尚缺点交双方各自的实际书面说明"
        v.reportingRequired && (!v.reportingCompleted || v.reportFileIds.isNullOrBlank()) -> "按实际核查依据应履行的报告尚未完成"
        v.pieces.any {!it.nonCollectionConfirmed || !it.identityConfirmed || !it.present || !it.safeCustody} -> "仍有身份、性质、实物在场或安全保管待核事项"
        v.pieces.any {it.recipientName!=p.recipientName || it.recipientIdentity!=p.recipientIdentity || it.actualLocation.isNullOrBlank() || it.custodianDescription.isNullOrBlank() || it.photoFileIds.isNullOrBlank()} -> "实际接收身份、保管地点、人员或本次影像尚未核实；保管关系变更须另行办理"
        else -> null
    }
    fun confirmations(c:InstitutionSuccessionCaseDao,id:String,representative:Boolean)=E.records(c.id.value).filter {
        it.kind==(if(representative)EnumSuccessionCaseRecord.ITEM_INVESTIGATION_AUTHORIZATION else EnumSuccessionCaseRecord.ITEM_INVESTIGATION_KEEPER) &&
            H.request<InstitutionItemInvestigationConfirmationVo>(it).investigationRecordId==id
    }
    fun requiresInvestigation(c:InstitutionSuccessionCaseDao,b:Basis)=
        b.batches.any {H.request<InstitutionItemHandoverVo>(it).let {v->!v.identityMatches || !v.conditionMatches}} || latest(c,b.key)!=null ||
            b.followUps.any {it.id.value>b.batches.first().id.value}
    fun completedProof(c:InstitutionSuccessionCaseDao,key:String,at:LocalDateTime):Map<String,Any?>? {
        val b=basis(c,key)
        if(!requiresInvestigation(c,b))return null
        val r=latest(c,key) ?: E.fail("交接差异或后续问题尚未登记实际核查处理结果")
        current(c,r);incomplete(H.request(r),b.plan)?.let(E::fail)
        val k=confirmations(c,r.id.value,false).lastOrNull() ?: E.fail("尚缺保管负责人本人核对差异处理结果")
        val a=confirmations(c,r.id.value,true).lastOrNull() ?: E.fail("尚缺依法负责人员本人复核差异处理结果")
        if(E.node(a.content).get("keeperRecordId")?.asText()!=k.id.value)E.fail("依法负责人员须复核当前保管核对原件")
        if(at<a.recordedAt || at<H.request<InstitutionItemInvestigationConfirmationVo>(a).confirmedAt)E.fail("完成核实不能早于差异处理的实际复核及其登记")
        return mapOf("investigationId" to r.id.value,"investigationDigest" to InstitutionSuccessionSources.digest(E.recordValue(r)),"keeperId" to k.id.value,"authorizationId" to a.id.value,"basisDigest" to b.digest)
    }
    fun status(c:InstitutionSuccessionCaseDao,key:String):Map<String,Any?>? {
        if(H.handovers(c,key).isEmpty())return null
        val b=basis(c,key);val r=latest(c,key);val v=r?.let {H.request<InstitutionItemInvestigationVo>(it)}
        val needed=requiresInvestigation(c,b)
        val fresh=v?.let {it.basisDigest==b.digest && it.handoverRecordId==b.batches.last().id.value}==true
        val k=r?.let {confirmations(c,it.id.value,false).lastOrNull()}
        val a=r?.let {confirmations(c,it.id.value,true).lastOrNull()}
        val issue=v?.let {incomplete(it,b.plan)}
        val confirmed=fresh && issue==null && k!=null && a!=null && E.node(a.content).get("keeperRecordId")?.asText()==k.id.value
        val state=when { !needed->"NO_DIFFERENCE";r==null->"NEEDS_INVESTIGATION";!fresh->"NEEDS_RECHECK";issue!=null->"INVESTIGATING";confirmed->"CONFIRMED";k!=null->"KEEPER_CONFIRMED";else->"READY_FOR_REVIEW" }
        return mapOf("state" to state,"requiresInvestigation" to needed,"confirmed" to confirmed,"basisDigest" to b.digest,
            "handoverRecordId" to b.batches.last().id.value,"investigationId" to r?.id?.value,"investigation" to v,
            "issue" to issue,"overdue" to (needed && !confirmed && v?.nextCheckAt?.isBefore(LocalDateTime.now())==true))
    }
}
