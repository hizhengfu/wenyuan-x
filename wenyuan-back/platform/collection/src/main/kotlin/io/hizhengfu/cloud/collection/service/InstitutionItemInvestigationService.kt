package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InstitutionItemHandoverEvidence as H
import io.hizhengfu.cloud.collection.service.InstitutionItemInvestigationEvidence as D
import io.hizhengfu.cloud.collection.service.InstitutionSuccessionCaseEvidence as E
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class InstitutionItemInvestigationService(private val cases:InstitutionSuccessionCaseService,private val users:CollectionResponsibleUserService) {
    private fun required(vararg v:String?) {if(v.any {it.isNullOrBlank()})E.fail("请填写本次实际调查、处理和原件")}
    private fun actual(at:LocalDateTime) {if(at>LocalDateTime.now())E.fail("实际办理时间不能填写未来")}
    fun investigate(id:String,v:InstitutionItemInvestigationVo,expectedOrgId:String?=null):String {
        val c=cases.get(id,true,expectedOrgId);cases.actor(c.orgId,v.operatorUserId);actual(v.investigatedAt)
        val h=InstitutionSuccessionRecordDao.findById(v.handoverRecordId) ?: E.fail("实际交接批次不存在")
        if(h.caseId!=id || h.orgId!=c.orgId || h.kind!=EnumSuccessionCaseRecord.ITEM_HANDOVER)E.fail("须引用本馆本案实际交接批次")
        val key=E.node(h.content).get("itemKey").asText();val b=D.basis(c,key)
        if(v.handoverRecordId!=b.batches.last().id.value || v.basisDigest!=b.digest)E.fail("交接批次或后续问题版本已变化，请重新读取核查范围")
        if(v.investigatedAt<b.after)E.fail("本次实际核查不能早于已知交接及后续问题")
        val old=D.latest(c,key)
        if(old!=null && E.node(old.content).get("request")==E.node(E.json(v)))return old.id.value
        if(old==null) {if(v.previousInvestigationId!=null)E.fail("首次核查不能引用不存在的旧原件")}
        else {
            if(v.previousInvestigationId!=old.id.value || v.investigatedAt<H.request<InstitutionItemInvestigationVo>(old).investigatedAt)E.fail("须引用最新核查原件追加实际处理，不能倒填或覆盖历史")
            required(v.revisionReason,v.revisionFileIds)
        }
        required(v.cause,v.actions,v.investigationFileIds,v.signedFileIds,v.thirdPartyRightsFileIds,v.reportingAssessment,v.reportingBasisFileIds)
        val actualPieces=b.batches.flatMap {H.request<InstitutionItemHandoverVo>(it).pieces}.map {it.pieceNo.trim()}.toSet()
        if(v.pieces.size!=actualPieces.size || v.pieces.map {it.pieceNo.trim()}.toSet()!=actualPieces)E.fail("本次核查须逐件覆盖所有已实际交接对象，不包含尚未交出的对象")
        v.pieces.forEach {required(it.pieceNo,it.identification,it.condition)}
        if(D.incomplete(v,b.plan)!=null) {
            required(v.remainingIssues,v.responsibleUserId)
            if(v.nextCheckAt==null || v.nextCheckAt<v.investigatedAt)E.fail("未完成核查须记录后续责任人及核查安排")
            users.requireMuseumMember(v.responsibleUserId!!,c.orgId)
        }
        val files=listOfNotNull(v.investigationFileIds,v.signedFileIds,v.senderStatementFileIds,v.receiverStatementFileIds,v.thirdPartyRightsFileIds,v.reportingBasisFileIds,v.reportFileIds,v.revisionFileIds)+v.pieces.mapNotNull {it.photoFileIds}
        return cases.record(c,EnumSuccessionCaseRecord.ITEM_INVESTIGATION,mapOf("request" to v,"itemKey" to key),files.filter(String::isNotBlank).joinToString(","))
    }
    fun confirm(id:String,v:InstitutionItemInvestigationConfirmationVo,representative:Boolean=false,expectedOrgId:String?=null):String {
        val c=cases.get(id,true,expectedOrgId);cases.actor(c.orgId,v.confirmerUserId);actual(v.confirmedAt);required(v.conclusion,v.signedFileIds)
        val r=D.find(c,v.investigationRecordId);val b=D.current(c,r);val result=H.request<InstitutionItemInvestigationVo>(r)
        D.incomplete(result,b.plan)?.let(E::fail)
        if(v.confirmedAt<result.investigatedAt)E.fail("实际确认不能早于本次差异核查")
        if(v.confirmerUserId!=(if(representative)c.content.authorizedRepresentativeUserId else c.content.keeperUserId))E.fail("须由本案指定保管负责人或依法负责人员本人确认")
        if(!representative)users.requireRole(v.confirmerUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        val k=if(representative)D.confirmations(c,r.id.value,false).lastOrNull() ?: E.fail("须先由保管负责人核对本版差异处理结果") else null
        if(k!=null && v.confirmedAt<H.request<InstitutionItemInvestigationConfirmationVo>(k).confirmedAt)E.fail("依法负责人员复核不能早于本轮保管核对")
        D.confirmations(c,r.id.value,representative).lastOrNull {E.node(it.content).get("request")==E.node(E.json(v)) && (!representative || E.node(it.content).get("keeperRecordId")?.asText()==k?.id?.value)}?.let {return it.id.value}
        return cases.record(c,if(representative)EnumSuccessionCaseRecord.ITEM_INVESTIGATION_AUTHORIZATION else EnumSuccessionCaseRecord.ITEM_INVESTIGATION_KEEPER,
            mapOf("request" to v,"itemKey" to b.key,"keeperRecordId" to k?.id?.value),v.signedFileIds)
    }
}
