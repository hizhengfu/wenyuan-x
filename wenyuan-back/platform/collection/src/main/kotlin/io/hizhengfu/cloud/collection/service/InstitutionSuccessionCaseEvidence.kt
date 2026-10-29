package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*

internal object InstitutionSuccessionCaseEvidence {
    const val BIZ="COLLECTION_INSTITUTION_SUCCESSION"
    fun fail(message:String):Nothing=throw BadRequestException(message)
    fun json(value:Any)=InstitutionSuccessionSources.json(value)
    fun node(value:String)=JsonUtils.mapper.readTree(value)
    inline fun <reified T> value(r:InstitutionSuccessionRecordDao)=JsonUtils.fromJson(r.content,T::class.java)!!
    fun records(id:String)=InstitutionSuccessionRecordDao.find {InstitutionSuccessionRecords.caseId eq id}.sortedBy {it.id.value}
    fun items(id:String)=InstitutionSuccessionItemDao.find {InstitutionSuccessionItems.caseId eq id}.sortedBy {it.itemKey}
    fun itemValue(r:InstitutionSuccessionItemDao)=mapOf("caseId" to r.caseId,"orgId" to r.orgId,"itemKey" to r.itemKey,"sourceKind" to r.sourceKind,"sourceId" to r.sourceId,"baseline" to node(r.baseline))
    fun recordValue(r:InstitutionSuccessionRecordDao)=mapOf("caseId" to r.caseId,"kind" to r.kind,"actorUserId" to r.actorUserId,"recordedAt" to r.recordedAt,"content" to node(r.content))
    fun archive(org:String,biz:String,id:String,phase:String):CollectionPermanentArchiveDao {
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.singleOrNull() ?: fail("统筹案卷所需永久原件缺失：$phase")
        if(a.orgId!=org || a.digest!=CollectionCareEvidence.digest(a.snapshot))fail("统筹案卷引用原件所属馆或摘要不一致")
        return a
    }
    fun exact(org:String,id:String,phase:String,body:Any) {
        if(node(archive(org,BIZ,id,phase).snapshot)!=node(json(body)))fail("统筹案卷原件与记录不一致")
    }
    fun verify(c:InstitutionSuccessionCaseDao) {
        exact(c.orgId,c.id.value,"CASE",mapOf("orgId" to c.orgId,"sourceKey" to c.sourceKey,"content" to c.content))
        items(c.id.value).forEach {if(it.orgId!=c.orgId)fail("清册对象所属馆不一致");exact(c.orgId,it.id.value,"ITEM",itemValue(it))}
        records(c.id.value).forEach {if(it.orgId!=c.orgId)fail("统筹办理记录所属馆不一致");exact(c.orgId,it.id.value,it.kind.name,recordValue(it))}
    }
    private fun matchingPlan(c:InstitutionSuccessionCaseDao,s:CollectionInstitutionSuccessionVo) {
        val p=c.content
        if(!InstitutionSuccessionSources.sameEvent(p,s) || p.eventKind!=s.eventKind || p.sourceMuseumName!=s.sourceMuseumName ||
            p.decidedOn!=s.decidedOn || p.effectiveOn!=s.effectiveOn || p.authorizedRepresentativeUserId!=s.authorizedRepresentativeUserId ||
            p.eventFileIds!=s.eventFileIds || p.liquidationOrMergerPlanFileIds!=s.liquidationOrMergerPlanFileIds ||
            p.representativeAuthorizationFileIds!=s.representativeAuthorizationFileIds)fail("逐件承接依据不属于本次终止或合并决定及依法授权范围")
    }
    fun proof(c:InstitutionSuccessionCaseDao,item:InstitutionSuccessionItemDao,v:InstitutionItemResolutionVo,visited:Set<String> = emptySet()):Map<String,Any?> {
        if(item.itemKey in visited)fail("清册来源关联存在循环，不能互相引用为完成依据")
        return when(v.kind) {
            EnumSuccessionResolution.LINKED_ITEM,EnumSuccessionResolution.OTHER_ITEM_HANDOVER -> InstitutionItemHandoverEvidence.proof(c,item,v,visited+item.itemKey)
            EnumSuccessionResolution.DRAFT_NOT_ACQUIRED -> {
                if(item.sourceKind!=EnumSuccessionSource.COLLECTION || v.businessId!=item.sourceId)
                    fail("未取得草稿核实须对应本案同一待登记对象")
                val draft=CollectionDao.findById(item.sourceId) ?: fail("待登记草稿已消失，须先核实实物和历史记录")
                if(draft.orgId!=c.orgId || draft.lifeStatus!=EnumCollectionStatus.PENDING || draft.registered ||
                    draft.catalogStatus==EnumCatalogStatus.APPROVED ||
                    CollectionCatalogVersionDao.find { (CollectionCatalogVersions.collectionId eq draft.id.value) and
                        (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED) }.any { true } ||
                    AcquisitionDao.find { Acquisitions.collectionId eq draft.id.value }.any { true } ||
                    AccessionReceiptDao.find { AccessionReceipts.collectionId eq draft.id.value }.any { true } ||
                    CollectionPlacementDao.find { CollectionPlacements.collectionId eq draft.id.value }.any { true } ||
                    StorageMovementDao.find { StorageMovements.collectionId eq draft.id.value }.any { true } ||
                    CollectionTransferDao.find { CollectionTransfers.collectionId eq draft.id.value }.any { true } ||
                    CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.collectionId eq draft.id.value }.any { true } ||
                    CollectionOperationLockDao.find { CollectionOperationLocks.collectionId eq draft.id.value }.any { true } ||
                    CollectionLedgerStatEventDao.find { CollectionLedgerStatEvents.collectionId eq draft.id.value }.any { true } ||
                    CollectionLedgerLogDao.find { CollectionLedgerLogs.collectionId eq draft.id.value }
                        .any { it.action!=EnumLedgerAction.RESERVE_REG_NO } ||
                    CollectionRegistrationNumberDao.find {
                        (CollectionRegistrationNumbers.orgId eq c.orgId) and
                            (CollectionRegistrationNumbers.totalRegNo eq draft.totalRegNo)
                    }.any { it.collectionId!=draft.id.value || it.state!=EnumRegistrationNoState.USED ||
                        it.reason!="预留登记号" || it.refBizId!=null })
                    fail("已有取得、核收、正式登记、实际占位、库存或承接记录，不能按未取得草稿核销")
                val follow=records(c.id.value).lastOrNull { it.kind==EnumSuccessionCaseRecord.FOLLOW_UP &&
                    value<InstitutionItemFollowUpVo>(it).itemKey==item.itemKey }
                    ?: fail("须先由保管负责人留存逐库清点和未实收核查原件")
                val finding=value<InstitutionItemFollowUpVo>(follow)
                if(follow.actorUserId!=c.content.keeperUserId || finding.operatorUserId!=c.content.keeperUserId ||
                    finding.responsibleUserId!=c.content.authorizedRepresentativeUserId ||
                    v.operatorUserId!=c.content.authorizedRepresentativeUserId ||
                    v.recordedAt<follow.recordedAt || v.recordedAt<finding.recordedAt ||
                    !finding.actualMeasures.contains("未取得") || !finding.actualMeasures.contains("未实收") ||
                    !v.conclusion.contains("未取得") || !v.conclusion.contains("未实收"))
                    fail("须由本案保管负责人核查未取得未实收，再由依法授权人员本人签署同一结论")
                val current=mapOf("id" to draft.id.value,"orgId" to draft.orgId,"totalRegNo" to draft.totalRegNo,
                    "name" to draft.name,"lifeStatus" to draft.lifeStatus,"catalogStatus" to draft.catalogStatus,
                    "registeredAt" to draft.registeredAt, "historicalConfirmedAt" to draft.historicalConfirmedAt,"warehouseId" to draft.warehouseId,
                    "nature" to draft.nature,"quantity" to draft.quantity,"entryVoucherNo" to draft.entryVoucherNo)
                mapOf("draftId" to draft.id.value,"draftDigest" to InstitutionSuccessionSources.digest(current),
                    "keeperFollowUpId" to follow.id.value,"keeperEvidenceDigest" to InstitutionSuccessionSources.digest(recordValue(follow)))
            }
            EnumSuccessionResolution.INSTITUTION_TRANSFER -> {
                val collectionId=when(item.sourceKind) {
                    EnumSuccessionSource.COLLECTION->item.sourceId
                    EnumSuccessionSource.ACQUISITION->AcquisitionDao.findById(item.sourceId)?.collectionId
                    EnumSuccessionSource.DESIGNATED->DesignatedReceptionDao.findById(item.sourceId)?.acquisitionId?.let(AcquisitionDao::findById)?.collectionId
                    else->null
                } ?: fail("该对象尚未关联正式藏品及其实际逐件承接，不能只凭说明标为完成")
                val t=CollectionTransferDao.findById(v.businessId) ?: fail("实际逐件承接记录不存在")
                if(t.orgId!=c.orgId || t.fromOrgId!=c.orgId || t.collectionId!=collectionId || t.executedAt==null || t.execution==null || t.flowStatus!=EnumFlowStatus.PASS || t.dossier?.kind!=EnumCollectionTransferKind.INSTITUTION_SUCCESSION)fail("须引用本馆同一对象已经执行的终止或合并承接")
                val current=CollectionDao.findById(collectionId) ?: fail("当前藏品记录缺失，须核实承接后的对象去向")
                if(current.orgId==c.orgId)fail("对象已回到本馆，原交接不能消除当前保管及承接义务")
                if(v.recordedAt<t.executedAt!! || v.recordedAt.toLocalDate()<t.execution!!.handedOn)fail("完成核实时间不能早于实际交接及其执行记录")
                matchingPlan(c,t.dossier!!.succession ?: fail("逐件承接缺少机构依据"))
                val sent=archive(c.orgId,CollectionConst.BIZ_TRANSFER,t.id.value,"EXECUTED")
                // 原馆再次接收后产生了新的责任周期。即使后来另行转出，旧交接也不能重新生效。
                val laterReceipts=CollectionPermanentArchiveDao.find {
                    (CollectionPermanentArchives.orgId eq c.orgId) and (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and
                        (CollectionPermanentArchives.phase eq "RECEIVED") and (CollectionPermanentArchives.id greater sent.id.value)
                }
                if(laterReceipts.any {node(it.snapshot).get("collection")?.get("id")?.asText()==collectionId})fail("原交接之后本馆曾再次接收该对象，须引用当前责任周期的真实承接")
                val received=archive(t.toOrgId ?: fail("接收馆缺失"),CollectionConst.BIZ_TRANSFER,t.id.value,"RECEIVED")
                val original=node(sent.snapshot)
                for((key,body) in mapOf("id" to t.id.value,"collectionId" to t.collectionId,"orgId" to t.orgId,"fromOrgId" to t.fromOrgId,"toOrgId" to t.toOrgId,"baseline" to t.baseline,"dossier" to t.dossier,"execution" to t.execution,"flowStatus" to t.flowStatus,"executedAt" to t.executedAt,"executedBy" to t.executedBy)) {
                    if(body==null || original.get(key)!=node(json(body)))fail("实际承接记录与原馆签署执行原件不一致")
                }
                val receipt=node(received.snapshot)
                if(receipt.get("transferId")?.asText()!=t.id.value || receipt.get("execution")!=node(json(t.execution!!)) ||
                    receipt.get("collection")?.get("id")?.asText()!=collectionId || receipt.get("collection")?.get("orgId")?.asText()!=t.toOrgId ||
                    receipt.get("proposal")?.get("dossier")!=node(json(t.dossier!!)))fail("接收馆签署原件不一致")
                val retained=CollectionRetainedLedgerDao.find {CollectionRetainedLedgers.transferId eq t.id.value}.singleOrNull() ?: fail("原馆永久总账尚未保留")
                if(retained.orgId!=c.orgId || retained.collectionId!=collectionId || retained.targetOrgId!=t.toOrgId)fail("原馆留存总账与接收对象不一致")
                val ledger=archive(c.orgId,"COLLECTION_RETAINED_LEDGER",retained.id.value,"TRANSFERRED")
                if(retained.archiveId!=ledger.id.value || node(ledger.snapshot)!=node(json(retained.snapshot)) ||
                    retained.snapshot.collection.id!=collectionId || retained.snapshot.collection.orgId!=c.orgId ||
                    retained.snapshot.collection.totalRegNo!=retained.totalRegNo || retained.snapshot.collection.name!=retained.name)fail("原馆留存总账与永久原件不一致")
                mapOf("transferId" to t.id.value,"sentArchiveId" to sent.id.value,"sentDigest" to sent.digest,"receivedArchiveId" to received.id.value,"receivedDigest" to received.digest,"retainedLedgerId" to retained.id.value,"retainedDigest" to ledger.digest)
            }
            EnumSuccessionResolution.DESIGNATED_REASSIGNMENT -> {
                if(item.sourceKind!=EnumSuccessionSource.DESIGNATED || v.businessId!=item.sourceId)fail("另行指定结果须对应原指定接收对象")
                val d=DesignatedReceptionDao.findById(item.sourceId) ?: fail("指定接收来源不存在")
                if(d.orgId!=c.orgId)fail("指定接收对象不属于本馆")
                DesignatedReceptionEvidence.verify(d)
                val result=DesignatedReceptionEvidence.last(d.id.value,EnumDesignatedRecord.SETTLEMENT) ?: fail("尚未记录真实另行承接结果")
                if(v.recordedAt<result.recordedAt || v.recordedAt<DesignatedReceptionEvidence.value<DesignatedSettlementVo>(result).handedAt)fail("完成核实时间不能早于另行承接及其登记原件")
                mapOf("designatedId" to d.id.value,"settlementId" to result.id.value,"settlementDigest" to InstitutionSuccessionSources.digest(DesignatedReceptionEvidence.recordValue(result)))
            }
            EnumSuccessionResolution.ACQUISITION_NOT_COMPLETED -> {
                if(item.sourceKind!=EnumSuccessionSource.ACQUISITION || v.businessId!=item.sourceId)fail("须对应尚未取得及实收的原征集记录")
                val a=AcquisitionDao.findById(item.sourceId) ?: fail("征集来源不存在")
                if(a.orgId!=c.orgId || a.collectionId!=null || a.designatedReceptionId!=null ||
                    a.flowStatus !in setOf(EnumFlowStatus.REJECT,EnumFlowStatus.CANCEL,EnumFlowStatus.VOID) ||
                    AcquisitionComplianceCaseDao.find {AcquisitionComplianceCases.acquisitionId eq a.id.value}.any {it.completion!=null} ||
                    AccessionReceiptDao.find {AccessionReceipts.acquisitionId eq a.id.value}.any {it.content.pieces.isNotEmpty()})fail("已有完成取得、实收、派生对象或申请仍在办理，不能用未取得未实收结论消除义务")
                if(a.acquireDate==null && a.contractAttachmentIds.isNullOrBlank())
                    mapOf("acquisitionId" to a.id.value,"status" to a.flowStatus)
                else {
                    // 旧申请的日期和附件保持原样；以两人签署的纠错原件解释冲突，并在每次读案时重算。
                    if(EntryVoucherDao.find {(EntryVouchers.orgId eq c.orgId) and
                            ((EntryVouchers.acquisitionIds like "%${a.id.value}%") or
                                (EntryVouchers.verifiedAcquisitionIds like "%${a.id.value}%"))}.any {
                            a.id.value in splitCollectionIds(it.acquisitionIds) ||
                                a.id.value in splitCollectionIds(it.verifiedAcquisitionIds)
                        } ||
                        AccessionReceiptDao.find {AccessionReceipts.acquisitionId eq a.id.value}.any {true})
                        fail("有旧取得日期或合同字段的征集，须先核查废止依据与所有核收记录")
                    // 专家否决必须对应原否决原件；撤销、作废不能虚构专家意见。
                    val veto=if(a.flowStatus==EnumFlowStatus.REJECT)
                        archive(c.orgId,CollectionConst.BIZ_ACQUISITION,a.id.value,"VETO") else null
                    val follow=records(c.id.value).lastOrNull {it.kind==EnumSuccessionCaseRecord.FOLLOW_UP &&
                        value<InstitutionItemFollowUpVo>(it).itemKey==item.itemKey}
                        ?: fail("须先由保管负责人留存旧取得及合同字段误填的核查原件")
                    val finding=value<InstitutionItemFollowUpVo>(follow)
                    val required=listOfNotNull(if(a.acquireDate!=null)"取得日期" else null,
                        if(!a.contractAttachmentIds.isNullOrBlank())"合同附件" else null)
                    if(follow.actorUserId!=c.content.keeperUserId || finding.operatorUserId!=c.content.keeperUserId ||
                        finding.responsibleUserId!=c.content.authorizedRepresentativeUserId ||
                        v.operatorUserId!=c.content.authorizedRepresentativeUserId ||
                        v.recordedAt<follow.recordedAt || v.recordedAt<finding.recordedAt ||
                        listOf("旧字段误填","未取得","未实收").any {it !in finding.actualMeasures || it !in v.conclusion} ||
                        required.any {it !in finding.actualMeasures || it !in v.conclusion})
                        fail("旧字段误填须由保管负责人逐项核查，授权代表本人复核未取得未实收结论")
                    val legacy=mapOf("id" to a.id.value,"orgId" to a.orgId,"name" to a.name,
                        "acquireDate" to a.acquireDate,"contractAttachmentIds" to a.contractAttachmentIds,
                        "flowStatus" to a.flowStatus,"collectionId" to a.collectionId,
                        "designatedReceptionId" to a.designatedReceptionId)
                    buildMap {
                        put("acquisitionId",a.id.value);put("status",a.flowStatus)
                        put("legacyFieldsDigest",InstitutionSuccessionSources.digest(legacy))
                        if(veto!=null) {
                            put("vetoArchiveId",veto.id.value);put("vetoDigest",veto.digest)
                        } else put("terminalFlowInstanceId",a.flowInstanceId)
                        put("keeperFollowUpId",follow.id.value)
                        put("keeperEvidenceDigest",InstitutionSuccessionSources.digest(recordValue(follow)))
                    }
                }
            }
        }
    }
}
