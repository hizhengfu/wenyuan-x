package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.DesignatedReceptionEvidence as E
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class DesignatedReceptionService(private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,private val archives:CollectionPermanentArchiveService) {
    private val authorization=CollectionInstitutionOperationAccess(scope,"指定接收",setOf(CollectionConst.ACQUIRE),setOf(CollectionConst.ACQUIRE))
    private fun access(org:String,write:Boolean=false)=authorization.require(org,write)
    private fun finish(r:DesignatedReceptionDao,org:String,result:String):String {
        TransactionManager.current().entityCache.flush()
        r.refresh()
        if(r.orgId!=org || r.content.orgId!=org)E.fail("指定接收所属收藏单位已变化，请重新核对")
        access(org,true);return result
    }
    private fun actor(org:String,id:String) {
        if(id!=SecurityUtils.getUserId())E.fail("须由实际办理人员本人登记")
        users.requireMuseumMember(id,org)
    }
    private fun museum(org:String)=CollectionOrgDao.findById(org) ?: E.fail("收藏单位不存在")
    private fun stateMuseum(org:String) {val m=museum(org);if(m.ownership!=EnumOwnership.STATE || m.orgStatus!=EnumOrgStatus.ENABLED)E.fail("第十五条指定接收须为已核实举办主体的启用国有博物馆")}
    private fun required(vararg text:String) {if(text.any(String::isBlank))E.fail("请完整填写实际依据、责任人、结论和签署原件")}
    private fun actual(at:LocalDateTime) {if(at>LocalDateTime.now())E.fail("实际办理时间不能填写未来")}
    private fun case(id:String,write:Boolean=false,expectedOrgId:String?=null):DesignatedReceptionDao {
        if(write)TransactionManager.current().entityCache.flush()
        val r=(if(write)DesignatedReceptionDao.find {DesignatedReceptions.id eq id}.forUpdate().singleOrNull() else DesignatedReceptionDao.findById(id)) ?: E.fail("指定接收记录不存在")
        if(write)r.refresh()
        if(expectedOrgId!=null && expectedOrgId!=r.orgId)E.fail("原表单收藏单位与当前案卷不一致，请重新读取后办理")
        access(r.orgId,write);E.verify(r);return r
    }
    private fun unresolved(r:DesignatedReceptionDao) {if(r.acquisitionId!=null || E.last(r.id.value,EnumDesignatedRecord.SETTLEMENT)!=null)E.fail("已接收或已完成另行承接的原件不能改变")}
    private fun stage(r:DesignatedReceptionDao,kind:EnumDesignatedRecord)=E.last(r.id.value,kind) ?: E.fail("尚缺前置真实办理原件：${kind.name}")
    private fun record(r:DesignatedReceptionDao,kind:EnumDesignatedRecord,body:Any,files:String):String {
        val org=r.orgId
        val json=E.json(body)
        E.rows(r.id.value).firstOrNull {it.kind==kind && it.content==json}?.let {return it.id.value}
        val row=DesignatedReceptionRecordDao.new {orgId=r.orgId;caseId=r.id.value;this.kind=kind;actorUserId=SecurityUtils.getUserId()!!;recordedAt=LocalDateTime.now();content=json}
        archives.seal(r.orgId,E.BIZ,row.id.value,kind.name,E.recordValue(row),files);return finish(r,org,row.id.value)
    }
    private fun replacement(r:DesignatedReceptionDao,kind:EnumDesignatedRecord,previous:String?,reason:String?,files:String?) {
        val old=E.last(r.id.value,kind)
        if(old?.id?.value!=previous)E.fail("原件版本已变化，请重新核对")
        if(old!=null && (reason.isNullOrBlank() || files.isNullOrBlank()))E.fail("追加或订正须说明原因并保留签署原件，旧原件不能删除")
    }
    fun create(v:DesignatedNoticeVo,expectedOrgId:String?=null):String {
        val org=v.orgId ?: authorization.currentOrg(true);if(expectedOrgId!=null && expectedOrgId!=org)E.fail("建案收藏单位与原表单不一致，请重新核对");access(org,true);stateMuseum(org)
        val who=SecurityUtils.getUserId() ?: E.fail("须登录后登记");actor(org,who)
        if(v.authorityLevel !in setOf(EnumAuthorityLevel.NATIONAL,EnumAuthorityLevel.PROVINCE))E.fail("须为国务院或省级文物行政部门的真实指定决定")
        required(v.authorityCode,v.authorityName,v.documentNo,v.designatedReceiverName,v.senderName,v.senderIdentity,v.transferLegalBasis,v.documentFileIds,
            v.item.sourceItemNo,v.item.name,v.item.unit,v.item.condition,v.item.imageFileIds)
        if(listOf(v.authorityCode,v.documentNo,v.item.sourceItemNo).any {it.length>100})E.fail("部门标识、文号和原移交物编号不得超过100字")
        if(v.designatedReceiverName!=museum(org).name)E.fail("指定决定中的接收馆须与本馆正式名称一致，请核对原件")
        if(v.item.quantity<=0 || v.notifiedOn<v.issuedOn || v.notifiedOn>LocalDate.now())E.fail("请核对指定决定日期、实际收到通知日期和正数数量")
        val content=v.copy(orgId=org)
        val key=CollectionCareEvidence.digest(E.json(listOf(org,v.authorityCode.trim(),v.documentNo.trim(),v.item.sourceItemNo.trim())))
        CollectionOrgDao.find {CollectionOrgs.id eq org}.forUpdate().single()
        DesignatedReceptionDao.find {DesignatedReceptions.sourceKey eq key}.singleOrNull()?.let {
            E.verify(it);if(it.content==content)return it.id.value;E.fail("同一指定决定的同一对象已登记，不能重复建案或替换原件")
        }
        val r=DesignatedReceptionDao.new {orgId=org;sourceKey=key;this.content=content;orgSnapshot=E.json(mapOf("id" to org,"name" to museum(org).name,"ownership" to museum(org).ownership))}
        archives.seal(org,E.BIZ,r.id.value,"NOTICE",E.noticeValue(r),"${v.documentFileIds},${v.item.imageFileIds}");return finish(r,org,r.id.value)
    }
    fun assess(id:String,v:DesignatedAssessmentVo,expectedOrgId:String?=null):String {
        val r=case(id,true,expectedOrgId);unresolved(r);actor(r.orgId,v.operatorUserId);actual(v.assessedAt)
        E.rows(id).firstOrNull {it.kind==EnumDesignatedRecord.ASSESSMENT && it.content==E.json(v)}?.let {return it.id.value}
        if(E.last(id,EnumDesignatedRecord.REFERRAL)!=null)E.fail("已经报请另行指定，不能替换该报告依据")
        replacement(r,EnumDesignatedRecord.ASSESSMENT,v.previousId,v.correctionReason,v.correctionFileIds)
        required(v.standardsFileIds,v.standardsConclusion,v.signedFileIds)
        if(v.assessedAt.toLocalDate()<r.content.notifiedOn || v.opinions.isEmpty())E.fail("须保留收到指定通知后的实际专业人员鉴定评估")
        v.opinions.forEach {o->required(o.name,o.organization,o.specialty,o.qualificationFileIds,o.authenticityOpinion,o.valueOpinion,o.signedOpinionFileIds)}
        if(v.opinions.map {it.organization.trim() to it.name.trim()}.distinct().size!=v.opinions.size)E.fail("同一专业人员的意见不能重复计入")
        if(v.meetsStandards && v.opinions.any {!it.meetsStandards})E.fail("存在不符合标准的专业意见，不能确认符合本馆入藏标准")
        return record(r,EnumDesignatedRecord.ASSESSMENT,v,(v.opinions.flatMap {listOf(it.qualificationFileIds,it.signedOpinionFileIds)}+listOfNotNull(v.standardsFileIds,v.signedFileIds,v.correctionFileIds)).joinToString(","))
    }
    private fun pieces(v:List<AccessionPieceVo>,at:LocalDateTime,quantity:Int,complete:Boolean) {
        if(v.isEmpty() || v.map {it.pieceNo.trim()}.distinct().size!=v.size || v.size>quantity)E.fail("须逐件记录实际对象，不得重复编号或超过指定清单数量")
        v.forEach {p->
            required(p.pieceNo,p.identification,p.condition,p.photoFileIds)
            if(p.receivedAt>at || p.identityMatches==null || p.conditionMatches==null)E.fail("须如实核对每件发生时间、身份及保存状况")
            if((p.identityMatches!=true || p.conditionMatches!=true) && p.discrepancy.isNullOrBlank())E.fail("实物差异须说明情况")
        }
        if(complete && (v.size!=quantity || v.any {it.identityMatches!=true || it.conditionMatches!=true}))E.fail("须核实全部指定对象，实物差异未解决不能确认完成")
    }
    fun custody(id:String,v:DesignatedCustodyVo,expectedOrgId:String?=null):String {
        val r=case(id,true,expectedOrgId);unresolved(r);actor(r.orgId,v.operatorUserId);actual(v.checkedAt)
        E.rows(id).firstOrNull {it.kind==EnumDesignatedRecord.CUSTODY && it.content==E.json(v)}?.let {return it.id.value}
        replacement(r,EnumDesignatedRecord.CUSTODY,v.previousId,v.correctionReason,v.correctionFileIds)
        required(v.warehouseId,v.custodianUserId,v.signedFileIds)
        if(v.handoverNames.isEmpty() || v.handoverNames.any(String::isBlank))E.fail("须填写实际移交人员并上传双方签署原件")
        val w=CollectionLocationMutation.reference(r.orgId,v.warehouseId) ?: E.fail("实际保管位置不存在")
        if(w.id.value!=v.warehouseId)E.fail("实际保管位置编号不一致，请重新选择库位")
        if(w.orgId!=r.orgId || w.sceneType!=EnumLocationScene.STORAGE || w.type !in WarehouseLocationPolicy.storageTypes)E.fail("须使用本馆真实保管库位")
        users.requireMuseumMember(v.custodianUserId,r.orgId)
        pieces(v.pieces,v.checkedAt,r.content.item.quantity,false)
        E.last(id,EnumDesignatedRecord.CUSTODY)?.let {old->if(E.value<DesignatedCustodyVo>(old).checkedAt>v.checkedAt)E.fail("不能倒填到已有保管核验之前")}
        return record(r,EnumDesignatedRecord.CUSTODY,v,(v.pieces.map {it.photoFileIds}+listOfNotNull(v.signedFileIds,v.correctionFileIds)).joinToString(","))
    }
    fun accept(id:String,v:DesignatedAcceptanceVo,expectedOrgId:String?=null):String {
        val r=case(id,true,expectedOrgId);val org=r.orgId;actor(r.orgId,v.keeperUserId);users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD);actual(v.confirmedAt)
        E.last(id,EnumDesignatedRecord.ACCEPTANCE)?.let {if(it.content==E.json(v)){E.prepared(AcquisitionDao[r.acquisitionId!!]);return r.acquisitionId!!};E.fail("接收核对原件不能覆盖")}
        unresolved(r);stateMuseum(r.orgId)
        if(E.last(id,EnumDesignatedRecord.REFERRAL)!=null)E.fail("已报请另行指定，不能擅自转为本馆接收")
        val assessment=E.value<DesignatedAssessmentVo>(stage(r,EnumDesignatedRecord.ASSESSMENT))
        val custody=E.value<DesignatedCustodyVo>(stage(r,EnumDesignatedRecord.CUSTODY))
        if(!assessment.meetsStandards || assessment.opinions.any {!it.meetsStandards})E.fail("不符合本馆入藏标准，须报请原指定部门另行指定")
        if(v.confirmedAt<maxOf(assessment.assessedAt,custody.checkedAt) || v.actualAcquiredOn<maxOf(r.content.issuedOn,assessment.assessedAt.toLocalDate()) || v.actualAcquiredOn>v.confirmedAt.toLocalDate())E.fail("实际取得及本人核对时间与指定、评估、保管原件不符")
        required(v.acquisitionBasisFileIds,v.conclusion,v.signedFileIds)
        pieces(custody.pieces,custody.checkedAt,r.content.item.quantity,true)
        record(r,EnumDesignatedRecord.ACCEPTANCE,v,"${v.acquisitionBasisFileIds},${v.signedFileIds}")
        val item=r.content.item
        val a=AcquisitionDao.new {
            orgId=r.orgId;designatedReceptionId=id;name=item.name;source=EnumSource.DESIGNATED;quantity=item.quantity;unit=item.unit
            categoryId=item.categoryId;era=item.era;texture=item.texture;dimensions=item.dimensions;weight=item.weight
            completeness=item.completeness;completeStatus=item.condition;images=item.imageFileIds;holder=r.content.senderName
            acquireDate=v.actualAcquiredOn;flowStatus=EnumFlowStatus.PASS;reason="行政指定接收：${r.content.documentNo}"
        }
        r.acquisitionId=a.id.value
        archives.seal(r.orgId,E.BIZ,id,"READY",E.readyValue(r,a),v.signedFileIds)
        return finish(r,org,a.id.value)
    }
    fun refer(id:String,v:DesignatedReferralVo,expectedOrgId:String?=null):String {
        val r=case(id,true,expectedOrgId);unresolved(r);actor(r.orgId,v.operatorUserId);actual(v.reportedAt)
        E.last(id,EnumDesignatedRecord.REFERRAL)?.let {if(it.content==E.json(v))return it.id.value;E.fail("已报请的原件不能覆盖")}
        val assessment=E.value<DesignatedAssessmentVo>(stage(r,EnumDesignatedRecord.ASSESSMENT))
        if(assessment.meetsStandards)E.fail("已确认符合入藏标准，应依法接收；不能用不符合标准的报请路径绕过")
        if(v.authorityCode!=r.content.authorityCode || v.reportedAt<assessment.assessedAt)E.fail("须在实际评估后向原指定接收的文物行政部门报请")
        required(v.referenceNo,v.requestSummary,v.requestFileIds,v.deliveryReceiptFileIds)
        return record(r,EnumDesignatedRecord.REFERRAL,v,"${v.requestFileIds},${v.deliveryReceiptFileIds}")
    }
    fun reassign(id:String,v:DesignatedReassignmentVo,expectedOrgId:String?=null):String {
        val r=case(id,true,expectedOrgId);unresolved(r);actor(r.orgId,v.recordedByUserId)
        E.last(id,EnumDesignatedRecord.REASSIGNMENT)?.let {if(it.content==E.json(v))return it.id.value;E.fail("另行指定的行政原件不能覆盖")}
        val referral=E.value<DesignatedReferralVo>(stage(r,EnumDesignatedRecord.REFERRAL))
        if(v.authorityCode!=r.content.authorityCode || v.authorityName!=r.content.authorityName || v.decidedOn<referral.reportedAt.toLocalDate() || v.decidedOn>LocalDate.now())E.fail("须依据原指定部门报请后作出的真实另行指定决定")
        required(v.documentNo,v.recipientName,v.recipientIdentity,v.recipientKind,v.sourceItemNo,v.documentFileIds)
        if(v.sourceItemNo!=r.content.item.sourceItemNo || v.recipientName==r.content.designatedReceiverName)E.fail("另行指定须对应原对象及不同的接收单位")
        return record(r,EnumDesignatedRecord.REASSIGNMENT,v,v.documentFileIds)
    }
    fun settle(id:String,v:DesignatedSettlementVo,expectedOrgId:String?=null):String {
        val r=case(id,true,expectedOrgId);actor(r.orgId,v.recordedByUserId);actual(v.handedAt)
        E.last(id,EnumDesignatedRecord.SETTLEMENT)?.let {if(it.content==E.json(v))return it.id.value;E.fail("实际承接原件不能覆盖")}
        unresolved(r)
        val decision=E.value<DesignatedReassignmentVo>(stage(r,EnumDesignatedRecord.REASSIGNMENT))
        if(v.recipientName!=decision.recipientName || v.recipientIdentity!=decision.recipientIdentity || v.handedAt.toLocalDate()<decision.decidedOn)E.fail("实际承接单位和时间须与另行指定决定一致")
        val custody=E.last(id,EnumDesignatedRecord.CUSTODY)?.let {E.value<DesignatedCustodyVo>(it)}
        if((v.mode==EnumDesignatedHandover.FROM_THIS_MUSEUM)!=(custody!=null))E.fail("须按真实实物保管路径记录承接，本馆已保管的对象不能改称由原单位直接移交")
        if(custody!=null) {
            if(v.handedAt<custody.checkedAt)E.fail("实际交出不能早于本馆最后保管核验")
            pieces(custody.pieces,custody.checkedAt,r.content.item.quantity,true)
            if(custody.pieces.map {it.pieceNo.trim()}.toSet()!=v.pieces.map {it.pieceNo.trim()}.toSet())E.fail("实际交出对象须与本馆保管清册逐件对应")
        }
        required(v.actualResult,v.signedReceiptFileIds)
        if(v.senderNames.isEmpty() || v.receiverNames.isEmpty() || (v.senderNames+v.receiverNames).any(String::isBlank))E.fail("须有实际双方人员及双方签署的逐件承接原件")
        pieces(v.pieces,v.handedAt,r.content.item.quantity,true)
        return record(r,EnumDesignatedRecord.SETTLEMENT,v,(v.pieces.map {it.photoFileIds}+v.signedReceiptFileIds).joinToString(","))
    }
    fun detail(id:String):Map<String,Any?> {
        val r=case(id);val rows=E.rows(id)
        val org=r.orgId
        r.acquisitionId?.let {E.prepared(AcquisitionDao.findById(it) ?: E.fail("指定接收关联的待入馆对象不存在"))}
        val assessment=rows.lastOrNull {it.kind==EnumDesignatedRecord.ASSESSMENT}?.let {E.value<DesignatedAssessmentVo>(it)}
        val state=when {
            r.acquisitionId!=null->"READY_FOR_ACCESSION"
            rows.any {it.kind==EnumDesignatedRecord.SETTLEMENT}->"REASSIGNED_RECEIVED"
            rows.any {it.kind==EnumDesignatedRecord.REASSIGNMENT}->"AWAIT_HANDOVER"
            rows.any {it.kind==EnumDesignatedRecord.REFERRAL}->"AWAIT_AUTHORITY"
            assessment?.meetsStandards==false->"MUST_REFER"
            assessment?.meetsStandards==true->"MUST_RECEIVE"
            else->"AWAIT_ASSESSMENT"
        }
        val evidenceErrors=mutableListOf<String>()
        val archiveFiles=(listOf(id to "NOTICE")+rows.map {it.id.value to it.kind.name}).associate { (recordId,phase) ->
            val original=archives.phaseForDisplay(E.BIZ,recordId,org,phase) ?: E.fail("指定接收阶段原件显示信息缺失")
            (original["evidenceIssue"] as? String)?.let {evidenceErrors += it}
            recordId to mapOf(phase to original["files"])
        }
        val result=mapOf("evidenceErrors" to evidenceErrors.distinct(),"id" to id,"orgId" to r.orgId,"canOperate" to authorization.canWrite(r.orgId),"notice" to r.content,"state" to state,"acquisitionId" to r.acquisitionId,
            "collectionId" to r.acquisitionId?.let(AcquisitionDao::findById)?.collectionId,"assessmentId" to E.last(id,EnumDesignatedRecord.ASSESSMENT)?.id?.value,
            "custodyId" to E.last(id,EnumDesignatedRecord.CUSTODY)?.id?.value,"pendingSince" to (assessment?.assessedAt ?: r.content.notifiedOn.atStartOfDay()),
            "records" to rows.map {mapOf("id" to it.id.value,"kind" to it.kind,"actorUserId" to it.actorUserId,"recordedAt" to it.recordedAt,"content" to JsonUtils.mapper.readTree(it.content))},"archiveFiles" to archiveFiles)
        if(case(id).orgId!=org)E.fail("指定接收所属收藏单位已变化，请重新读取")
        return result
    }
    fun context():Map<String,Any?> {
        val org=authorization.contextOrg();if(org!=null)access(org)
        val allowed=authorization.writableOrgs()
        return mapOf("currentOrgId" to org,"currentUserId" to SecurityUtils.getUserId(),"canWrite" to (org!=null && authorization.canWrite(org)),"orgs" to CollectionOrgDao.all().filter {(it.id.value in allowed) && it.ownership==EnumOwnership.STATE && it.orgStatus==EnumOrgStatus.ENABLED}.map {mapOf("value" to it.id.value,"label" to it.name)},
            "locations" to WarehouseDao.all().filter {(it.orgId in allowed) && it.status==EnumContentStatus.NORMAL && it.sceneType==EnumLocationScene.STORAGE && it.type in WarehouseLocationPolicy.storageTypes}.map {mapOf("value" to it.id.value,"label" to it.name,"orgId" to it.orgId)})
    }
    fun page(page:Int,pageSize:Int,keyword:String?=null):Map<String,Any?> {
        if(page<1 || pageSize<0 || pageSize>200)E.fail("请选择有效页码和每页数量，0 表示明确查询全部")
        val ids=authorization.readableOrgs()
        val rows=DesignatedReceptionDao.all().filter {it.orgId in ids}.onEach {access(it.orgId)}.filter {keyword.isNullOrBlank() || listOf(it.content.item.name,it.content.documentNo,it.content.item.sourceItemNo).any {v->v.contains(keyword)}}.sortedByDescending {it.id.value}
        val selected=if(pageSize==0)rows else rows.drop(((page.toLong()-1)*pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize)
        return mapOf("total" to rows.size,"page" to page,"pageSize" to pageSize,"list" to selected.map {detail(it.id.value)})
    }

    /** 案卷入口只放行本案已封存的原件，实际字节仍由文件下载器再次核对所有来源的当前授权。 */
    fun requireOriginal(id:String,fileId:String) {
        val r=case(id)
        val businessIds=listOf(id)+E.rows(id).map { it.id.value }
        val linked=businessIds.any { businessId ->
            fileId in CollectionRetainedOriginalFiles.stageIds(E.BIZ,businessId,setOf(r.orgId))
        }
        if(!linked)E.fail("该文件不是本案已归档的指定接收原件")
    }
}
