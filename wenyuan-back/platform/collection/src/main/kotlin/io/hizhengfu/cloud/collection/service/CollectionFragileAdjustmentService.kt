package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionFragileAdjustmentEvidence as A
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
class CollectionFragileAdjustmentService(
    private val scope:CollectionScopeResolver,
    private val users:CollectionResponsibleUserService,
    private val archives:CollectionPermanentArchiveService,
    private val locks:CollectionOperationLockService,
    private val guards:List<CollectionOperationSafetyGuard>,
) {
    private fun collection(id:String,expectedOrgId:String?=null) = CollectionFragilityAccess.collection(scope,id,true,expectedOrgId)
    private fun plan(c:CollectionDao,id:String):CollectionFragileUsePlanDao {
        val p=CollectionFragileUsePlanDao.findById(id) ?: E.fail("原具体使用保护方案不存在")
        if(p.orgId!=c.orgId || p.collectionId!=c.id.value)E.fail("变更不属于本馆本件藏品")
        E.plan(p);A.outbound(c,p)
        if(E.selected(c.id.value,p.sourceBizType,p.sourceBizId)?.id!=p.id)E.fail("本次实际出库不依据本版方案，请核对真正实施的版本")
        return p
    }
    private fun actors(p:CollectionFragileUsePlanDao):FragileAdjustmentActors {
        val a=CollectionFragilityAssessmentDao[p.content.assessmentId]
        return if(p.sourceBizType==CollectionConst.BIZ_REQUISITION) {
            val d=RequisitionDao[p.sourceBizId].dossier ?: E.fail("缺少原提用责任人依据")
            FragileAdjustmentActors(a.content.conservationUserId,d.departmentHeadUserId,d.keeperUserId,d.leaderUserId)
        } else {
            val d=LoanDao[p.sourceBizId].dossier ?: E.fail("缺少原借用责任人依据")
            FragileAdjustmentActors(a.content.conservationUserId,null,d.keeperUserId,d.leaderUserId)
        }
    }
    private fun people(p:CollectionFragileUsePlanDao,a:FragileAdjustmentActors) {
        val all=listOfNotNull(a.conservation,a.department,a.keeper,a.director)
        if(all.toSet().size!=all.size)E.fail("保护修复、提用部门、保管和主要负责人须由不同实际责任人员分别确认")
        all.forEach {users.requireMuseumMember(it,p.orgId)}
        users.requireRole(a.conservation,p.orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireRole(a.keeper,p.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(a.director,p.orgId,CollectionConst.ROLE_DIRECTOR)
        a.department?.let {users.requireDepartmentHead(it,p.orgId,RequisitionDao[p.sourceBizId].dossier!!.departmentGroupId)}
    }
    private fun validate(c:CollectionDao,p:CollectionFragileUsePlanDao,v:FragileUseAdjustmentVo,candidate:CollectionFragileUseAdjustmentDao?=null) {
        if(listOf(v.reason,v.actualUnstartedEvidence,v.signedFileIds).any(String::isBlank) || v.adjustments.isEmpty())E.fail("请完整记录真实变更原因、未实施核查、具体段和签署原件")
        if(v.reviewedAt.isAfter(LocalDateTime.now()))E.fail("实际变更核查时间不能填写未来")
        val s=E.source(p.sourceBizType,p.sourceBizId) ?: E.fail("使用来源不存在")
        val check=CollectionCareCheckDao.findById(v.checkId) ?: E.fail("须关联本件实际现状检查")
        if(check.orgId!=c.orgId || check.collectionId!=c.id.value || check.content.checkedAt.isBefore(s.handedAt) || check.content.checkedAt.isAfter(v.reviewedAt))E.fail("须核对本馆本件实际出库后、变更核查前的现状检查")
        CollectionCareEvidence.check(check)
        if(CollectionCareEvidence.checks(c.id.value).lastOrNull()?.id!=check.id)E.fail("已有新的实际现状检查，请依据最新事实核查")
        val rr=E.records(c.id.value).also {it.forEach(E::record)}
        if(rr.any {it.started!=null && (it.ended==null || it.ended!!.endedAt.isAfter(check.content.checkedAt))})E.fail("须先如实结束实际展出或运输，再检查现状；变更不能代替实际停止")
        if(v.adjustments.map {it.kind to it.segmentIndex}.toSet().size!=v.adjustments.size)E.fail("本次变更不能重复选择同一段")
        val segments=A.segments(p).associateBy {it.kind to it.segmentIndex}
        v.adjustments.forEach {item->
            val old=segments[item.kind to item.segmentIndex] ?: E.fail("变更须对应原方案中的具体段")
            if(old.cancelled || rr.any {it.planId==p.id.value && it.started?.kind==item.kind && it.started?.segmentIndex==item.segmentIndex})E.fail("已实际开始或已核实取消的段不能改写为未实施或再次安排")
            if(item.reason.isBlank())E.fail("每段须说明实际变更或取消原因")
            if(item.action==EnumFragileAdjustmentAction.CANCEL) {
                if(item.from!=null || item.to!=null || item.route!=null || item.protection!=null)E.fail("未实施取消不能混填新的使用授权")
            } else {
                val from=item.from ?: E.fail("重新安排须填写新的开始时间")
                val to=item.to ?: E.fail("重新安排须填写新的结束时间")
                if(from.isBefore(LocalDateTime.now()) || !to.isAfter(from) || from.isBefore(s.outAt) || to.isAfter(s.returnAt))E.fail("新安排须在原批准期限内并在本人确认之后实施；不能补造过去授权或延期")
                if(item.kind==EnumFragileUseKind.TRANSPORT && p.content.environmentSources.filter {it.kind==item.kind && it.segmentIndex==item.segmentIndex}.any {it.context!=item.route})E.fail("新的运输路线已改变原测量环境，须另办保护方案，不能沿用原来源")
                if(item.protection.isNullOrBlank() || (item.kind==EnumFragileUseKind.TRANSPORT && item.route.isNullOrBlank()) || (item.kind==EnumFragileUseKind.DISPLAY && item.route!=null))E.fail("请核实本段实际保护措施及适用运输路线")
            }
        }
        if(v.adjustments.any {it.action==EnumFragileAdjustmentAction.RESCHEDULE}) {
            if(s.snapshot!=p.sourceSnapshot || E.selected(c.id.value,p.sourceBizType,p.sourceBizId)?.id!=p.id || E.current(c).id.value!=p.content.assessmentId)E.fail("原用途、人员、场所、协议或材质依据已变化，须另办适用申请，不能用本入口扩大原批准")
            E.requireLoanSafety(c,p.sourceBizType,p.sourceBizId)
            val expected=if(s.external)EnumCollectionStatus.ON_LOAN else EnumCollectionStatus.IN_USE
            if(s.returned || s.cancelled || c.lifeStatus!=expected || c.inventoryHold || CollectionCareEvidence.unsafe(check))E.fail("实物不在相应使用状态或仍有风险，不能重新授权普通展出或运输")
            locks.requireNoOtherApproval(c.id.value,p.sourceBizType,p.sourceBizId)
            guards.filterNot {it is CollectionFragilityGuard}.forEach {it.requireAllowed(c.id.value,null,null)}
            if(s.external && v.borrowerAgreementFileIds.isNullOrBlank())E.fail("外借重新安排须附借用双方实际签署的变更依据，不能代替适用行政手续")
        }
        if(candidate!=null) {
            val effective=A.segments(p,candidate).filterNot {it.cancelled}.sortedBy {it.from}
            if(effective.zipWithNext().any {(a,b)->b.from.isBefore(a.to)})E.fail("变更后的运输、展出时段不能相互重叠")
            if(v.adjustments.any {it.action==EnumFragileAdjustmentAction.RESCHEDULE})E.budget(c,E.current(c),p,s.handedAt!!,candidate)
        }
    }
    fun propose(collectionId:String,planId:String,v:FragileUseAdjustmentVo,expectedOrgId:String?=null):String {
        val c=collection(collectionId,expectedOrgId);val p=plan(c,planId);val all=A.all(planId)
        all.lastOrNull()?.takeIf {it.content==v}?.let {A.verify(it);return it.id.value}
        if(v.previousAdjustmentId!=all.lastOrNull()?.id?.value)E.fail("变更版本已变化，请重新核查")
        val facts=A.facts(c,p)
        if(v.factsRevision!=E.digest(facts))E.fail("实际使用、现状或安全事实已变化，请重新核查")
        validate(c,p,v)
        val roles=actors(p);people(p,roles)
        val a=CollectionFragileUseAdjustmentDao.new {orgId=c.orgId;this.collectionId=collectionId;this.planId=planId;proposedBy=SecurityUtils.getUserId() ?: E.fail("请登录");factsSnapshot=facts;actors=roles;content=v}
        archives.seal(c.orgId,E.BIZ,a.id.value,"ADJUSTMENT",A.value(a),listOfNotNull(v.signedFileIds,v.borrowerAgreementFileIds,checkFiles(v.checkId)).joinToString(","))
        validate(c,p,v,a);return CollectionFragilityAccess.finish(scope,c,a.id.value,expectedOrgId)
    }
    private fun checkFiles(id:String)=CollectionCareCheckDao[id].content.let {"${it.conditionFileIds},${it.signedFileIds}"}
    fun sign(collectionId:String,id:String,role:String,v:CollectionCareSignatureVo,expectedOrgId:String?=null):String {
        val c=collection(collectionId,expectedOrgId);val a=CollectionFragileUseAdjustmentDao.findById(id) ?: E.fail("实际使用变更不存在")
        val p=plan(c,a.planId);A.verify(a)
        if(a.collectionId!=collectionId || a.orgId!=c.orgId)E.fail("变更不属于本馆本件")
        val signatures=A.signatures(a);val target=signatures.find {it.first==role} ?: E.fail("本变更不存在该确认职责")
        if(target.second!=SecurityUtils.getUserId())E.fail("须由指定责任人员本人确认，管理员不能代签")
        people(p,a.actors)
        target.third?.let {if(it.record==v)return id;E.fail("本人确认原件不能覆盖")}
        if(a.withdrawal!=null || A.all(a.planId).last().id!=a.id)E.fail("请确认尚未撤回的最新变更版本")
        if(a.factsSnapshot!=A.facts(c,p) || a.actors!=actors(p))E.fail("核查后事实或实际责任人员已变化，须追加新版本并重新确认")
        if(signatures.takeWhile {it.first!=role}.any {it.third==null})E.fail("须依次由保护修复、提用部门（适用时）、保管、主要负责人本人确认")
        if(v.opinion.isBlank() || v.fileIds.isBlank())E.fail("本人意见及签署原件必填")
        validate(c,p,a.content,a)
        val sig=CollectionCareSignature(target.second,LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),v)
        when(role){"CONSERVATION"->a.conservation=sig;"DEPARTMENT"->a.department=sig;"KEEPER"->a.keeper=sig;"DIRECTOR"->a.director=sig}
        archives.seal(c.orgId,E.BIZ,id,"ADJUSTMENT_$role",sig,v.fileIds);return CollectionFragilityAccess.finish(scope,c,id,expectedOrgId)
    }
    fun withdraw(collectionId:String,id:String,v:FragileAdjustmentWithdrawalVo,expectedOrgId:String?=null):String {
        val c=collection(collectionId,expectedOrgId);val a=CollectionFragileUseAdjustmentDao.findById(id) ?: E.fail("变更不存在")
        if(a.collectionId!=collectionId || a.orgId!=c.orgId)E.fail("变更不属于本馆本件")
        A.verify(a)
        if(SecurityUtils.getUserId()!=a.proposedBy)E.fail("须由变更登记人员本人撤回")
        a.withdrawal?.let {if(it==v)return id;E.fail("撤回原件不能替换")}
        if(a.director!=null || A.all(a.planId).last().id!=a.id)E.fail("仅能撤回尚未生效的最新变更，已确认事实继续永久保留")
        if(v.withdrawnAt.isAfter(LocalDateTime.now()) || v.withdrawnAt.isBefore(a.content.reviewedAt) || v.reason.isBlank() || v.signedFileIds.isBlank())E.fail("请据实填写核查之后的撤回时间、原因及签署原件")
        a.withdrawal=v;archives.seal(c.orgId,E.BIZ,id,"ADJUSTMENT_WITHDRAWN",v,v.signedFileIds);return CollectionFragilityAccess.finish(scope,c,id,expectedOrgId)
    }
}
