package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InstitutionItemHandoverEvidence as H
import io.hizhengfu.cloud.collection.service.InstitutionSuccessionCaseEvidence as E
import org.jetbrains.exposed.v1.core.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class InstitutionItemHandoverService(private val cases:InstitutionSuccessionCaseService,private val users:CollectionResponsibleUserService) {
    private fun required(vararg values:String?) {if(values.any {it.isNullOrBlank()})E.fail("请填写实际分类、身份、处理依据和签署原件")}
    private fun actual(at:LocalDateTime) {if(at>LocalDateTime.now())E.fail("实际办理时间不能填写未来")}
    private fun pieces(values:List<InstitutionHandoverPieceVo>) {
        if(values.isEmpty() || values.map {it.pieceNo.trim()}.toSet().size!=values.size)E.fail("逐件清册须非空且编号不重复")
        values.forEach {required(it.pieceNo,it.identification,it.condition,it.photoFileIds)}
    }
    fun propose(id:String,v:InstitutionItemProposalVo,expectedOrgId:String?=null):String {
        val c=cases.get(id,true,expectedOrgId);cases.actor(c.orgId,v.operatorUserId);actual(v.proposedAt)
        val item=cases.item(c,v.itemKey);required(v.identificationConclusion,v.identificationFileIds)
        if(v.proposedAt.toLocalDate()<c.content.decidedOn)E.fail("分类办理方案不能早于机构决定")
        if(item.sourceKind==EnumSuccessionSource.UNRECORDED) {
            val found=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(E.node(item.baseline).get("actual").toString(),InstitutionUnrecordedVo::class.java)!!
            if(v.proposedAt<found.foundAt)E.fail("方案不能早于本条对象实际发现时间")
        }
        val old=H.latest(c,v.itemKey)
        if(old!=null && E.node(old.content).get("request")==E.node(E.json(v))) {H.requireCurrent(c,old);return old.id.value}
        if(H.handovers(c,v.itemKey).isNotEmpty())E.fail("已经发生实际承接，须保留原方案和交接事实，不能改换接收对象")
        if(old==null) {if(v.previousProposalId!=null)E.fail("首次分类方案不能引用不存在的旧版本")}
        else {
            if(v.previousProposalId!=old.id.value || v.proposedAt<H.proposal(old).proposedAt)E.fail("请基于最新分类方案追加订正并保留实际时序")
            required(v.revisionReason,v.revisionFileIds)
        }
        if(v.route==EnumInstitutionItemRoute.RECORD_LINK) {
            required(v.linkedItemKey)
            if(v.pieces.isNotEmpty() || v.recipientIdentity!=null || v.nonCollectionConfirmed!=null)E.fail("来源关联应仅核实实际对应关系，不得混入物品处置方案")
        } else {
            if(v.linkedItemKey!=null || v.nonCollectionConfirmed!=true || v.ownership==null)E.fail("须据实际专业依据确认属于其他物品及其权属；文物、藏品及不明对象须先核实并办理相应法定手续")
            required(v.propertyBasisFileIds,v.ownerName,v.ownerIdentity,v.recipientName,v.recipientIdentity,v.recipientQualificationFileIds,v.dispositionBasisFileIds,v.thirdPartyRightsFileIds)
            if(v.thirdPartyRightsResolved!=true)E.fail("第三方权利尚未处理，不能授权交出")
            pieces(v.pieces)
            if(v.pieces.size!=E.node(item.baseline).get("quantity")?.asInt())E.fail("方案逐件清册须完整对应本条实际数量，数量差异须先核实")
            if(v.route==EnumInstitutionItemRoute.NON_COLLECTION_SUCCESSION) {
                if(v.recipientKind !in setOf(EnumInstitutionRecipient.PUBLIC_BENEFIT_ORGANIZATION,EnumInstitutionRecipient.STATE_TEACHING_RESEARCH) || v.noPrivateDistribution!=true)E.fail("本馆其他财产应按真实公益处置依据承接，不得分配给出资人或成员")
                required(v.publicBenefitPurpose)
            } else {
                if(v.recipientKind!=EnumInstitutionRecipient.LAWFUL_OWNER || v.ownerIdentity!=v.recipientIdentity || v.ownerName!=v.recipientName)E.fail("寄存等他人物品应按原关系交还真实权利人，不能借此处分本馆财产")
                required(v.custodyAgreementFileIds,v.returnAuthorizationFileIds)
            }
        }
        val facts=H.facts(c,item,v)
        return cases.record(c,EnumSuccessionCaseRecord.ITEM_PROPOSAL,mapOf("request" to v,"sourceDigest" to InstitutionSuccessionSources.digest(facts),"source" to facts),
            (listOfNotNull(v.identificationFileIds,v.revisionFileIds,v.propertyBasisFileIds,v.recipientQualificationFileIds,v.dispositionBasisFileIds,v.thirdPartyRightsFileIds,v.custodyAgreementFileIds,v.returnAuthorizationFileIds)+v.pieces.map {it.photoFileIds}).joinToString(","))
    }
    fun approve(id:String,v:InstitutionItemApprovalVo,representative:Boolean=false,expectedOrgId:String?=null):String {
        val c=cases.get(id,true,expectedOrgId);cases.actor(c.orgId,v.confirmerUserId);actual(v.confirmedAt);required(v.conclusion,v.signedFileIds)
        val p=H.findProposal(c,v.proposalRecordId);H.requireCurrent(c,p)
        val expected=if(representative)c.content.authorizedRepresentativeUserId else c.content.keeperUserId
        if(v.confirmerUserId!=expected)E.fail("须由本案指定责任人员本人核对分类、去向和依据")
        if(!representative)users.requireRole(v.confirmerUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        val kind=if(representative)EnumSuccessionCaseRecord.ITEM_AUTHORIZATION else EnumSuccessionCaseRecord.ITEM_KEEPER
        H.approvals(c,p.id.value,kind).firstOrNull {E.node(it.content).get("request")==E.node(E.json(v))}?.let {old->
            if(!representative || E.node(old.content).get("keeperRecordId")?.asText()==H.approvals(c,p.id.value,EnumSuccessionCaseRecord.ITEM_KEEPER).lastOrNull()?.id?.value)return old.id.value
        }
        if(H.handovers(c,H.proposal(p).itemKey).isNotEmpty())E.fail("已经发生实际交接，不能倒改本次确认原件或替换授权")
        if(v.confirmedAt<H.proposal(p).proposedAt)E.fail("实际确认不能早于分类方案")
        val keeper=if(representative)H.approvals(c,p.id.value,EnumSuccessionCaseRecord.ITEM_KEEPER).lastOrNull() ?: E.fail("须先由保管负责人本人核对") else null
        if(keeper!=null && v.confirmedAt<H.request<InstitutionItemApprovalVo>(keeper).confirmedAt)E.fail("依法负责人员确认不能早于本轮保管核对")
        return cases.record(c,if(representative)EnumSuccessionCaseRecord.ITEM_AUTHORIZATION else EnumSuccessionCaseRecord.ITEM_KEEPER,mapOf("request" to v,"keeperRecordId" to keeper?.id?.value),v.signedFileIds)
    }
    fun handOver(id:String,v:InstitutionItemHandoverVo,expectedOrgId:String?=null):String {
        val c=cases.get(id,true,expectedOrgId);cases.actor(c.orgId,v.operatorUserId);actual(v.handedAt)
        val p=H.findProposal(c,v.proposalRecordId);val item=H.requireCurrent(c,p);val plan=H.proposal(p);val authorization=H.authorization(c,p)
        if(plan.route==EnumInstitutionItemRoute.RECORD_LINK)E.fail("来源关联不会产生一次新的实物交接")
        if(v.handedAt<H.request<InstitutionItemApprovalVo>(authorization).confirmedAt)E.fail("实际交接不能早于依法负责人员确认")
        required(v.recipientName,v.recipientIdentity,v.actualReceivingLocation,v.signedReceiptFileIds,v.archiveHandoverFileIds)
        if(v.recipientName!=plan.recipientName || v.recipientIdentity!=plan.recipientIdentity)E.fail("实际接收单位或权利人与已确认方案不一致")
        if(v.senderNames.isEmpty() || v.receiverNames.isEmpty() || (v.senderNames+v.receiverNames).any {it.isBlank()})E.fail("须记录实际点交双方人员")
        pieces(v.pieces)
        if((!v.identityMatches || !v.conditionMatches))required(v.discrepancy,v.protectiveMeasures)
        val planned=plan.pieces.map {it.pieceNo.trim()}.toSet()
        if(v.pieces.any {it.pieceNo.trim() !in planned})E.fail("本批次必须对应已核对方案中的逐件编号")
        val prior=H.handovers(c,plan.itemKey)
        prior.firstOrNull {E.node(it.content).get("request")==E.node(E.json(v))}?.let {return it.id.value}
        val used=prior.flatMap {H.request<InstitutionItemHandoverVo>(it).pieces}.map {it.pieceNo.trim()}.toSet()
        if(v.pieces.any {it.pieceNo.trim() in used})E.fail("该件已有实际交接原件，不能重复交出或覆盖原批次")
        if(prior.any {v.handedAt<H.request<InstitutionItemHandoverVo>(it).handedAt})E.fail("分批交接应保留实际批次先后，不得倒填覆盖既有事实")
        if(item.sourceKind==EnumSuccessionSource.AUXILIARY) {
            CollectionAuxiliaryDao.find {CollectionAuxiliaries.id eq item.sourceId}.forUpdate().single()
            val elsewhere=InstitutionSuccessionRecordDao.find {(InstitutionSuccessionRecords.orgId eq c.orgId) and (InstitutionSuccessionRecords.kind eq EnumSuccessionCaseRecord.ITEM_HANDOVER)}.any {
                it.caseId!=id && E.node(it.content).get("itemKey")?.asText()==item.itemKey
            }
            if(elsewhere)E.fail("该辅助账对象已在另一机构案卷发生交接，请继续原案卷核实，不能重复处分")
        }
        return cases.record(c,EnumSuccessionCaseRecord.ITEM_HANDOVER,mapOf("request" to v,"itemKey" to item.itemKey,"authorizationId" to authorization.id.value),
            (v.pieces.map {it.photoFileIds}+listOf(v.signedReceiptFileIds,v.archiveHandoverFileIds)).joinToString(","))
    }
}
