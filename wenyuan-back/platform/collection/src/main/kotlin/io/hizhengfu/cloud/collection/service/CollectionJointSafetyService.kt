package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
class CollectionJointSafetyService(private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,private val archives:CollectionPermanentArchiveService) {
    private fun access(org:String,write:Boolean) = CollectionCurrentOperationAccess.require(scope,org,write,setOf(CollectionConst.CARE),"多部门定期安全检查")
    private fun readableOrgs():Set<String> {
        val id=SecurityUtils.getNullUserDetail()?.id ?: C.fail("请登录后查询共同安全检查")
        val user=CollectionActorDirectory(scope).requireActive(id)
        if(EnumRole.ADMIN.value !in user.roles && user.permissions.none {it in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.CARE)})C.fail("当前账号没有共同安全检查权限")
        val current=scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG)
        val configured=if(scope.isMulti())null else scope.currentOrgId()
        return CollectionOrgDao.all().filter {org->
            (current==null || org.id.value in current) && (configured==null || org.id.value==configured) &&
                (current==null || (org.status==EnumContentStatus.NORMAL && org.orgStatus==EnumOrgStatus.ENABLED))
        }.map {it.id.value}.toSet()
    }
    private fun confirmedOrg(r:RequisitionDao,expectedOrgId:String?) {
        if(expectedOrgId!=null && expectedOrgId!=r.orgId)C.fail("提用所属收藏单位已变化，请重新读取后办理")
    }
    private fun request(id:String,write:Boolean=false,expectedOrgId:String?=null):RequisitionDao {
        if(write)TransactionManager.current().entityCache.flush()
        val source=RequisitionDao.findById(id) ?: C.fail("提用来源不存在")
        access(source.orgId,write);confirmedOrg(source,expectedOrgId)
        val org=source.orgId;val ids=splitCollectionIds(source.collectionIds).sorted()
        if(write) {
            ids.forEach {cid->CollectionDao.find {Collections.id eq cid}.forUpdate().toList()}
            RequisitionDao.find {Requisitions.id eq id}.forUpdate().single().refresh()
            if(source.orgId!=org || splitCollectionIds(source.collectionIds).sorted()!=ids)C.fail("提用所属馆或逐件清单已变化，请重新核对")
            access(source.orgId,true);confirmedOrg(source,expectedOrgId)
        }
        E.requireOwn(source)
        return source
    }
    private fun finish(r:RequisitionDao,id:String,expectedOrgId:String?):String {
        confirmedOrg(r,expectedOrgId);access(r.orgId,true);return id
    }
    private fun canOperate(org:String):Boolean {
        if(SecurityUtils.getNullUserDetail()?.authorities?.none {it.authority in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.CARE)}!=false)return false
        return try {access(org,true);true} catch(_:BadRequestException){false}
    }
    private fun own(r:RequisitionDao,id:String):CollectionJointSafetyPlanDao=(CollectionJointSafetyPlanDao.findById(id) ?: C.fail("共同检查方案不存在")).also {if(it.orgId!=r.orgId || it.requisitionId!=r.id.value)C.fail("方案不属于本馆本次提用");E.plan(it)}
    private fun ownRound(r:RequisitionDao,id:String):CollectionJointSafetyRoundDao=(CollectionJointSafetyRoundDao.findById(id) ?: C.fail("实际共同检查不存在")).also {if(it.orgId!=r.orgId || it.requisitionId!=r.id.value)C.fail("实际检查不属于本馆本次提用");E.round(it)}
    private fun required(vararg values:String){if(values.any(String::isBlank))C.fail("请完整填写实际方案、检查情况、措施及原件")}
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    private fun responsibility(p:CollectionJointSafetyPlanDao,r:RequisitionDao) {
        val v=p.content;val d=r.dossier ?: C.fail("缺少完整提用职责依据")
        val base=E.baseSource(r).snapshot
        val entry=RequisitionCustodyEvidence.entries(r).lastOrNull {it.complete && RequisitionCustodyEvidence.snapshot(base,it)==p.sourceSnapshot}
        if(entry==null && p.sourceSnapshot!=base)C.fail("共同检查方案缺少对应的在用责任承接原件")
        val actors=RequisitionCustodyEvidence.actors(r,entry)
        if(v.keeperUserId!=actors["KEEPER"] || v.useUserId!=actors["USE"] || v.directorUserId!=actors["DIRECTOR"])C.fail("保管、提用和主要负责人须对应本次提用职责或已确认的在用承接")
        val ids=listOf(v.keeperUserId,v.useUserId,v.securityUserId,v.directorUserId)
        if(ids.toSet().size!=ids.size)C.fail("保管、提用、安全保卫及主要负责人须分别由实际责任人员履职")
    }
    internal fun people(p:CollectionJointSafetyPlanDao,r:RequisitionDao) {
        responsibility(p,r)
        val v=p.content;val d=r.dossier!!
        val ids=listOf(v.keeperUserId,v.useUserId,v.securityUserId,v.directorUserId)
        ids.forEach {users.requireMuseumMember(it,r.orgId)}
        users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireDepartmentMember(v.useUserId,r.orgId,d.departmentGroupId)
        users.requireRole(v.securityUserId,r.orgId,CollectionConst.ROLE_SECURITY)
        users.requireRole(v.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
    }
    private fun roundParticipant(p:CollectionJointSafetyPlanDao,r:RequisitionDao,role:String) {
        if(p.sourceSnapshot==E.source(r).snapshot) {
            people(p,r)
            return
        }
        // 责任已正式承接的旧轮保留原实际参与人；其他旧人员离任不阻断有效参与人补齐本人事实。
        responsibility(p,r)
        val v=p.content
        when(role) {
            "KEEPER"->users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            "USE"->users.requireDepartmentMember(v.useUserId,r.orgId,r.dossier!!.departmentGroupId)
            "SECURITY"->users.requireRole(v.securityUserId,r.orgId,CollectionConst.ROLE_SECURITY)
            else->C.fail("本轮不存在该共同检查职责")
        }
    }
    private fun custodyPeople(r:RequisitionDao,v:RequisitionCustodyRequest) {
        if(setOf(v.keeperUserId,v.useUserId,v.directorUserId).size!=3)C.fail("承接的保管、提用和主要负责人须由不同实际责任人履职")
        users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireDepartmentMember(v.useUserId,r.orgId,r.dossier!!.departmentGroupId)
        users.requireRole(v.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
    }
    private fun custodyCurrent(r:RequisitionDao,v:RequisitionCustodyRequest) {
        val source=E.source(r)
        E.outbound(r)
        if(source.returned || source.cancelled || r.sourceBizType=="COLLECTION_RESTORATION")C.fail("须为尚在实际提用中的业务；修复来源须连同专项实施责任办理")
        if(v.collectionIds!=source.collectionIds-E.receivedIds(r))C.fail("承接须覆盖当前尚在用的完整清单，已接收归还件不能再承接")
        if(v.handedAt.isAfter(now()))C.fail("实际承接不能填写未来时间")
        custodyPeople(r,v)
    }
    fun registerCustody(id:String,v:RequisitionCustodyRequest,expectedOrgId:String):String {
        val r=request(id,true,expectedOrgId);val entries=RequisitionCustodyEvidence.entries(r)
        custodyCurrent(r,v)
        val actor=SecurityUtils.getUserId()
        if(actor!=v.keeperUserId)C.fail("须由所指定的新任保管部门负责人本人登记实际在用承接")
        entries.lastOrNull()?.takeIf {it.record.request==v && it.record.registeredBy==actor}?.let {return it.record.id}
        val effective=entries.lastOrNull {it.complete}
        if(v.previousId!=entries.lastOrNull()?.record?.id || v.expectedEffectiveId!=effective?.record?.id || v.sourceRevision!=C.digest(E.source(r).snapshot))C.fail("在用承接或提用来源版本已变化，请重新读取")
        required(v.reason,v.actualHandover,v.outstandingMatters,v.appointmentFileIds,v.handoverFileIds)
        val old=RequisitionCustodyEvidence.actors(r,effective)
        if(old==mapOf("KEEPER" to v.keeperUserId,"USE" to v.useUserId,"DIRECTOR" to v.directorUserId))C.fail("须明确本次实际变动后的责任人员")
        val cc=RequisitionCustodyEvidence.checks(r,v)
        if(effective?.effectiveAt?.let {at->v.handedAt.isBefore(at) || cc.any {it.content.checkedAt.isBefore(at)}}==true)C.fail("后续承接须重新核对上次责任生效后的实际现状")
        val out=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED")}.single()
        val record=RequisitionCustodyRecord(io.hizhengfu.cloud.common.utils.IdGenerate.nextId(),id,r.orgId,v,E.baseSource(r).snapshot,out.id.value,out.digest,E.checkSnapshot(cc),actor,now())
        RequisitionCustodyWriteVerification.seal(r,v,"${RequisitionCustodyEvidence.PREFIX}P_${record.id}",record,RequisitionCustodyEvidence.files(v),archives) {
            custodyCurrent(r,v)
        }
        return finish(r,record.id,expectedOrgId)
    }
    fun signCustody(id:String,recordId:String,role:String,v:CollectionCareSignatureVo,expectedOrgId:String):String {
        val r=request(id,true,expectedOrgId);val entries=RequisitionCustodyEvidence.entries(r)
        val e=entries.lastOrNull()?.takeIf {it.record.id==recordId} ?: C.fail("须核对最新在用承接登记")
        custodyCurrent(r,e.record.request);required(v.opinion,v.fileIds)
        val actor=RequisitionCustodyEvidence.actors(r,e)[role] ?: C.fail("未知的在用承接确认岗位")
        if(actor!=SecurityUtils.getUserId())C.fail("须由指定承接人员本人确认，管理员不能代签")
        e.signatures[role]?.let {if(it.record==v)return recordId;C.fail("承接本人确认不能覆盖，请追加新版本")}
        if(role=="DIRECTOR" && !e.signatures.keys.containsAll(setOf("KEEPER","USE")))C.fail("须先由保管和提用承接人员分别确认，再由主要负责人确认")
        RequisitionCustodyWriteVerification.seal(r,e.record.request,"${RequisitionCustodyEvidence.PREFIX}${role}_$recordId",CollectionCareSignature(actor,now(),v),v.fileIds,archives) {
            custodyCurrent(r,e.record.request)
        }
        return finish(r,recordId,expectedOrgId)
    }
    fun savePlan(id:String,v:CollectionJointSafetyPlanVo,expectedOrgId:String?=null):String {
        val r=request(id,true,expectedOrgId);val source=E.source(r);val all=E.plans(id)
        all.lastOrNull()?.takeIf {it.content==v}?.let {E.plan(it);return it.id.value}
        if(v.previousPlanId!=all.lastOrNull()?.id?.value || v.sourceRevision!=C.digest(source.snapshot))C.fail("提用来源或共同检查版本已变化，请重新核对")
        if(source.returned || source.cancelled)C.fail("本次使用已归还或撤销，不能再建立新的定期使用检查方案")
        if(RequisitionCustodyEvidence.entries(r).lastOrNull {it.complete}?.effectiveAt?.let {v.preparedAt.isBefore(it)}==true)C.fail("承接后的共同方案须在责任本人确认生效后制定，不能倒填旧方案")
        if(v.preparedAt.isAfter(now()) || v.inspectionIntervalDays !in 1..3650 || v.firstDueAt.isBefore(source.outAt) || v.firstDueAt.isAfter(minOf(source.returnAt,maxOf(source.outAt,now()).plusDays(v.inspectionIntervalDays.toLong()))))C.fail("请按实际风险明确周期及首次检查安排，首次检查应处于本次使用期及一个周期内")
        required(v.scopeAndMethod,v.environmentalAndSecurityChecks,v.measuresAndReporting,v.planFileIds)
        val p=CollectionJointSafetyPlanDao.new {orgId=r.orgId;requisitionId=id;version=(all.lastOrNull()?.version ?: 0)+1;sourceSnapshot=source.snapshot;content=v}
        people(p,r)
        CollectionJointReportWriteVerification.sealPlan(r,p,"PLAN",E.planValue(p),v.planFileIds,archives) {
            people(p,r)
            if(E.plans(id).lastOrNull()?.id!=p.id || p.sourceSnapshot!=E.source(r).snapshot)C.fail("共同检查方案或提用来源已变化，请重新核对")
        }
        return finish(r,p.id.value,expectedOrgId)
    }
    fun signPlan(id:String,planId:String,role:String,v:CollectionCareSignatureVo,expectedOrgId:String?=null):String {
        val r=request(id,true,expectedOrgId);val p=own(r,planId);people(p,r);required(v.opinion,v.fileIds)
        val target=E.signatures(p).find {it.first==role} ?: C.fail("共同检查职责不存在")
        if(target.second!=SecurityUtils.getUserId())C.fail("须由指定责任人员本人确认，管理员不能代签")
        target.third?.let {if(it.record==v)return planId;C.fail("本人确认原件不能覆盖")}
        if(E.plans(id).last().id!=p.id || p.sourceSnapshot!=E.source(r).snapshot || r.returned || E.source(r).cancelled)C.fail("须核对当前提用和最新方案版本")
        val sig=CollectionCareSignature(target.second,now(),v)
        when(role){"KEEPER"->p.keeper=sig;"USE"->p.use=sig;"SECURITY"->p.security=sig}
        CollectionJointReportWriteVerification.sealPlan(r,p,role,sig,v.fileIds,archives) {
            people(p,r)
            if(E.plans(id).lastOrNull()?.id!=p.id || p.sourceSnapshot!=E.source(r).snapshot)C.fail("共同检查方案或提用来源已变化，请重新核对")
        }
        return finish(r,planId,expectedOrgId)
    }
    fun recordRound(id:String,v:CollectionJointSafetyRoundVo,expectedOrgId:String?=null):String {
        val r=request(id,true,expectedOrgId);val p=own(r,v.planId);val source=E.source(r)
        E.rounds(id).firstOrNull {it.content==v}?.let {E.round(it);return it.id.value}
        if(!r.outExecuted || source.handedAt==null)C.fail("须依据已经实际点交提用的藏品记录检查，不生成虚构提用")
        E.outbound(r)
        if(!E.confirmed(p) || v.startedAt.isBefore(E.signatures(p).mapNotNull {it.third?.confirmedAt}.max()) || v.startedAt.isBefore(source.handedAt) || v.endedAt.isBefore(v.startedAt) || v.endedAt.isAfter(now()))C.fail("须如实记录共同方案确认及实际点交之后的检查时段")
        val applicable=E.plans(id).lastOrNull {E.confirmed(it) && E.signatures(it).mapNotNull {it.third?.confirmedAt}.max()<=v.startedAt}
        if(applicable?.id!=p.id)C.fail("本轮须依据实际开始时有效的共同检查方案")
        val responsibility=RequisitionCustodyEvidence.entries(r).lastOrNull {it.effectiveAt?.let {at->at<=v.startedAt}==true}
        if(p.sourceSnapshot!=RequisitionCustodyEvidence.snapshot(E.baseSource(r).snapshot,responsibility))C.fail("本轮检查开始时责任已承接，须先确认新任人员的共同方案")
        required(v.actualAreas,v.jointFindings,v.actualMeasures,v.signedFileIds)
        if(v.checkIds.isEmpty())C.fail("须关联本轮真正发生的逐件现状检查")
        if(E.rounds(id).any {it.content.checkIds.intersect(v.checkIds).isNotEmpty()})C.fail("同一实际检查不能重复算作另一轮定期检查")
        val cc=v.checkIds.map {CollectionCareCheckDao.findById(it) ?: C.fail("逐件实际现状检查不存在")}
        if(cc.map {it.collectionId}.toSet().size!=cc.size)C.fail("一轮内同件应选择对应的一份实际现状原件")
        val received=E.receivedAt(r)
        cc.forEach {c->
            if(c.orgId!=r.orgId || c.collectionId !in source.collectionIds)C.fail("本轮检查不属于本馆本次提用")
            C.check(c)
            if(received[c.collectionId]?.let {c.content.checkedAt.isAfter(it)}==true)C.fail("已实际归还后的现状检查不能计作本次在用期间共同检查")
            if(c.orgId!=r.orgId || c.collectionId !in source.collectionIds || c.content.checkedAt.isBefore(v.startedAt) || c.content.checkedAt.isAfter(v.endedAt))C.fail("本轮检查须属于本馆本次提用的实际件，且实际时间位于本轮期间")
        }
        val row=CollectionJointSafetyRoundDao.new {orgId=r.orgId;requisitionId=id;planId=p.id.value;checksSnapshot=E.checkSnapshot(cc);content=v}
        CollectionJointReportWriteVerification.seal(r,row,"ROUND",E.roundValue(row),(listOf(v.signedFileIds)+cc.flatMap {listOf(it.content.conditionFileIds,it.content.signedFileIds)}).joinToString(","),archives) {
            E.outbound(r)
        }
        return finish(r,row.id.value,expectedOrgId)
    }
    fun signRound(id:String,roundId:String,role:String,v:CollectionCareSignatureVo,expectedOrgId:String?=null):String {
        val r=request(id,true,expectedOrgId);val row=ownRound(r,roundId);val p=own(r,row.planId);roundParticipant(p,r,role);required(v.opinion,v.fileIds)
        val target=E.signatures(p,row).find {it.first==role} ?: C.fail("本轮不存在该共同检查职责")
        if(target.second!=SecurityUtils.getUserId())C.fail("须由本轮实际责任人员本人核对原件和确认参与情况")
        target.third?.let {if(it.record==v)return roundId;C.fail("实际参与确认原件不能覆盖")}
        val sig=CollectionCareSignature(target.second,now(),v)
        when(role){"KEEPER"->row.keeper=sig;"USE"->row.use=sig;"SECURITY"->row.security=sig}
        CollectionJointReportWriteVerification.seal(r,row,role,sig,v.fileIds,archives) {roundParticipant(p,r,role)}
        return finish(r,roundId,expectedOrgId)
    }
    fun report(id:String,roundId:String,v:CollectionJointSafetyReportVo,expectedOrgId:String?=null):String {
        val r=request(id,true,expectedOrgId);val row=ownRound(r,roundId);val p=own(r,row.planId)
        row.report?.let {if(it==v)return roundId;C.fail("实际报告原件不能替换，新的发现应据实另行登记")}
        if(!E.unsafe(row))C.fail("本轮尚无逐件原件记录的不安全因素，应先如实补齐发现的实际检查；紧急情况可直接首报")
        if(v.directorUserId!=p.content.directorUserId || v.reporterUserId!=SecurityUtils.getUserId())C.fail("须由实际报告人员本人登记，并对应本方案主要负责人")
        users.requireMuseumMember(v.reporterUserId,r.orgId);users.requireRole(v.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        val foundAt=E.checked(row).filter {C.unsafe(it)}.maxOf {it.content.checkedAt}
        if(v.reportedAt.isBefore(foundAt) || v.reportedAt.isAfter(now()))C.fail("请填写发现本轮问题之后真实的向主要负责人报告时间")
        required(v.content,v.immediateMeasures,v.signedFileIds)
        row.report=v
        CollectionJointReportWriteVerification.seal(r,row,"DIRECTOR_REPORT",v,v.signedFileIds,archives) {
            users.requireMuseumMember(v.reporterUserId,r.orgId);users.requireRole(v.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        }
        return finish(r,roundId,expectedOrgId)
    }
    fun acknowledge(id:String,roundId:String,v:CollectionCareSignatureVo,expectedOrgId:String?=null):String {
        val r=request(id,true,expectedOrgId);val row=ownRound(r,roundId);val report=row.report ?: C.fail("请先保留实际向主要负责人报告的原件")
        if(SecurityUtils.getUserId()!=report.directorUserId)C.fail("须由主要负责人本人收悉并记录处理意见")
        users.requireRole(report.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR);required(v.opinion,v.fileIds)
        row.director?.let {if(it.record==v)return roundId;C.fail("主要负责人实际处理意见不能覆盖")}
        if(E.reportHandled(row))C.fail("本报告已由后续接任负责人收悉处理，原接收人不能补写另一份办结记录")
        val sig=CollectionCareSignature(report.directorUserId,now(),v);row.director=sig
        CollectionJointReportWriteVerification.seal(r,row,"DIRECTOR",sig,v.fileIds,archives) {
            users.requireRole(report.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        }
        return finish(r,roundId,expectedOrgId)
    }
    fun reportToCurrentDirector(id:String,roundId:String,v:CollectionJointCurrentReportVo,expectedOrgId:String):String {
        val r=request(id,true,expectedOrgId);val row=ownRound(r,roundId);val p=own(r,row.planId);val report=v.report
        if(report.reporterUserId!=SecurityUtils.getUserId())C.fail("须由实际报告人员本人如实登记向现任负责人的报告")
        users.requireMuseumMember(report.reporterUserId,r.orgId)
        users.requireRole(report.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        if(report.directorUserId==p.content.directorUserId || report.directorUserId==report.reporterUserId)C.fail("须明确与原方案不同的现任主要负责人，报告与收悉分别本人办理")
        CollectionJointReportHandoverEvidence.currentBasis(row)?.let {if(it.request==v)return roundId;C.fail("实际报告及任职依据不能覆盖，请据实移交既有报告")}
        if(row.report!=null)C.fail("已有实际报告应保留原接收事实，并通过报告移交办理后续收悉")
        if(v.expectedRoundDigest!=CollectionJointReportHandoverEvidence.roundDigest(row))C.fail("原共同检查已变化，请重新读取")
        if(!E.unsafe(row))C.fail("原检查尚无逐件不安全因素依据，请先据实核实")
        val foundAt=E.checked(row).filter(C::unsafe).maxOf {it.content.checkedAt}
        if(report.reportedAt.isBefore(foundAt) || report.reportedAt.isAfter(now()))C.fail("请填写发现问题之后真实的向现任负责人报告时间")
        required(v.appointmentFileIds,v.changeReason,report.content,report.immediateMeasures,report.signedFileIds)
        val basis=CollectionJointCurrentReportRecord(r.orgId,id,roundId,v,now())
        row.report=report
        CollectionJointReportWriteVerification.seal(r,row,listOf(
            CollectionJointReportWriteVerification.Original(CollectionJointReportHandoverEvidence.CURRENT_BASIS,basis,v.appointmentFileIds),
            CollectionJointReportWriteVerification.Original("DIRECTOR_REPORT",report,report.signedFileIds),
        ),archives) {
            users.requireMuseumMember(report.reporterUserId,r.orgId);users.requireRole(report.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        }
        return finish(r,roundId,expectedOrgId)
    }
    private fun reportHandoverPeople(r:RequisitionDao,v:CollectionJointReportHandoverVo) {
        if(v.keeperUserId==v.directorUserId)C.fail("报告移交核对与主要负责人收悉须由不同人员本人办理")
        users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(v.directorUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
    }
    private fun reportHandoverSource(row:CollectionJointSafetyRoundDao,v:CollectionJointReportHandoverVo):CollectionPermanentArchiveDao {
        val original=CollectionJointReportHandoverEvidence.report(row)
        if(original.digest!=v.expectedReportDigest)C.fail("原问题报告已变化，请重新读取后核对")
        if(v.directorUserId==row.report!!.directorUserId)C.fail("原接收人仍本人办理时，请使用原报告收悉入口")
        if(v.handedAt.isBefore(row.report!!.reportedAt) || v.handedAt.isAfter(now()))C.fail("报告移交须在原报告形成之后据实登记时间，不得填写未来时间")
        return original
    }
    fun handOverReport(id:String,roundId:String,v:CollectionJointReportHandoverVo,expectedOrgId:String):String {
        val r=request(id,true,expectedOrgId);val row=ownRound(r,roundId)
        val original=reportHandoverSource(row,v);reportHandoverPeople(r,v)
        if(SecurityUtils.getUserId()!=v.keeperUserId)C.fail("须由所指定的现任保管部门负责人本人登记实际报告移交")
        val entries=CollectionJointReportHandoverEvidence.entries(row)
        entries.lastOrNull()?.takeIf {it.record.request==v}?.let {return it.record.id}
        if(E.reportHandled(row))C.fail("该报告已有实际收悉处理依据，后续新问题应另行据实报告")
        if(v.previousId!=entries.lastOrNull()?.record?.id)C.fail("报告移交版本已更新，请重新读取")
        required(v.reason,v.outstandingMatters,v.appointmentFileIds,v.handoverFileIds)
        val value=CollectionJointReportHandoverRecord(io.hizhengfu.cloud.common.utils.IdGenerate.nextId(),r.orgId,id,roundId,v,original.id.value,v.keeperUserId,now())
        CollectionJointReportWriteVerification.seal(r,row,"${CollectionJointReportHandoverEvidence.PREFIX}P_${value.id}",value,CollectionJointReportHandoverEvidence.files(v),archives) {
            reportHandoverSource(row,v);reportHandoverPeople(r,v)
        }
        return finish(r,value.id,expectedOrgId)
    }
    fun signReportHandover(id:String,roundId:String,recordId:String,role:String,v:CollectionCareSignatureVo,expectedOrgId:String):String {
        val r=request(id,true,expectedOrgId);val row=ownRound(r,roundId)
        val entry=CollectionJointReportHandoverEvidence.entries(row).lastOrNull()?.takeIf {it.record.id==recordId} ?: C.fail("须核对最新报告移交版本")
        reportHandoverSource(row,entry.record.request);reportHandoverPeople(r,entry.record.request);required(v.opinion,v.fileIds)
        val actor=CollectionJointReportHandoverEvidence.actors(entry)[role] ?: C.fail("未知的报告移交岗位")
        if(actor!=SecurityUtils.getUserId())C.fail("须由指定人员本人核对报告移交原件，管理员不能代签")
        entry.signatures[role]?.let {if(it.record==v)return recordId;C.fail("报告移交本人确认原件不能覆盖")}
        if(row.director!=null)C.fail("原接收人已实际收悉处理，本次未完成移交保留备查")
        if(role=="DIRECTOR" && entry.signatures["KEEPER"]==null)C.fail("须先由保管部门负责人确认实际报告移交，再由新任主要负责人收悉处理")
        CollectionJointReportWriteVerification.seal(r,row,"${CollectionJointReportHandoverEvidence.PREFIX}${role}_$recordId",CollectionCareSignature(actor,now(),v),v.fileIds,archives) {
            reportHandoverSource(row,entry.record.request);reportHandoverPeople(r,entry.record.request)
        }
        return finish(r,recordId,expectedOrgId)
    }
    private fun itemRows(r:RequisitionDao,ps:List<CollectionJointSafetyPlanDao>,rr:List<CollectionJointSafetyRoundDao>):List<Map<String,Any?>> {
        val source=r.dossier?.let {E.source(r)}
        val candidate=ps.lastOrNull(E::confirmed)
        val active=candidate?.takeIf {it.sourceSnapshot==source?.snapshot}
        val received=if(r.outExecuted)E.receivedIds(r) else emptySet()
        val recalls=RequisitionRecallEvidence.entries(r)
        if(r.outExecuted && source!=null)E.outbound(r)
        return splitCollectionIds(r.collectionIds).map {cid->
            val c=CollectionDao.findById(cid)?.takeIf {it.orgId==r.orgId}
            val pending=rr.count {row->E.checked(row).any {it.collectionId==cid && C.unsafe(it)} && !E.reportHandled(row)}
            val due=active?.takeIf {r.outExecuted && cid !in received}?.let {E.nextDue(r,it,cid)}
            val recallDue=recalls.filter {cid in it.request.collectionIds}.minOfOrNull {it.request.returnBy}
            val returnDue=source?.takeIf {r.outExecuted && cid !in received}?.let {minOf(it.returnAt,recallDue ?: it.returnAt)}
            val status=when {
                pending>0 -> "AWAITING_DIRECTOR"
                cid in received -> "RECEIVED"
                source==null -> "MISSING_SOURCE"
                candidate==null -> "MISSING_PLAN"
                active==null -> "STALE_PLAN"
                !r.outExecuted -> "PLANNING"
                due!=null && !now().isBefore(due) -> "OVERDUE"
                else -> "CURRENT"
            }
            mapOf("rowKey" to "${r.id.value}:$cid","requisitionId" to r.id.value,"documentNo" to r.documentNo,"collectionId" to cid,"name" to c?.name,"totalRegNo" to c?.totalRegNo,"actualReceived" to (cid in received),"plannedReturnAt" to source?.returnAt,"recallDueAt" to recallDue,"returnDueAt" to returnDue,"returnOverdue" to (returnDue?.let { !now().isBefore(it) } ?: false),"nextDueAt" to due,"status" to status,"pendingDirectorCount" to pending,"pendingRoundCount" to rr.count {row->E.checked(row).any {it.collectionId==cid} && !E.confirmed(CollectionJointSafetyPlanDao[row.planId],row)})
        }
    }
    fun detail(id:String):Map<String,Any?> {
        val r=request(id);val source=r.dossier?.let {E.source(r)};val ps=E.plans(id);val rr=E.rounds(id);ps.forEach(E::plan);rr.forEach(E::round)
        val used=rr.flatMap {it.content.checkIds}.toSet()
        val checks=if(!r.outExecuted || source?.handedAt==null)emptyList() else CollectionCareCheckDao.find { (CollectionCareChecks.orgId eq r.orgId) and (CollectionCareChecks.collectionId inList source.collectionIds) }.filter {it.id.value !in used && !it.content.checkedAt.isBefore(source.handedAt)}.sortedBy {it.content.checkedAt}.onEach(C::check)
        val custody=RequisitionCustodyEvidence.entries(r);val effective=custody.lastOrNull {it.complete}
        val actors=if(r.dossier!=null)RequisitionCustodyEvidence.actors(r,effective) else emptyMap()
        val received=if(r.outExecuted)E.receivedIds(r) else emptySet()
        val custodyChecks=if(!r.outExecuted || source?.handedAt==null)emptyList() else CollectionCareCheckDao.find {(CollectionCareChecks.orgId eq r.orgId) and (CollectionCareChecks.collectionId inList source.collectionIds)}.filter {it.collectionId !in received && it.content.checkedAt>=source.handedAt}.onEach(C::check)
        // Only stages already linked to this verified requisition history supply frozen display metadata.
        val evidenceErrors=mutableListOf<String>()
        val archiveFiles=linkedMapOf<String,MutableMap<String,Any?>>()
        fun original(bizType:String,recordId:String,phase:String) {
            val archive=archives.phaseForDisplay(bizType,recordId,r.orgId,phase)
            (archive?.get("evidenceIssue") as? String)?.let {evidenceErrors += it}
            archiveFiles.getOrPut(recordId){linkedMapOf()}[phase]=archive?.get("files") ?: emptyList<Map<String,Any?>>()
        }
        ps.forEach {p->
            original(E.BIZ,p.id.value,"PLAN")
            E.signatures(p).filter {it.third!=null}.forEach {(role,_,_)->original(E.BIZ,p.id.value,role)}
        }
        rr.forEach {row->
            original(E.BIZ,row.id.value,"ROUND")
            E.signatures(CollectionJointSafetyPlanDao[row.planId],row).filter {it.third!=null}.forEach {(role,_,_)->original(E.BIZ,row.id.value,role)}
            if(row.report!=null)original(E.BIZ,row.id.value,"DIRECTOR_REPORT")
            if(row.director!=null)original(E.BIZ,row.id.value,"DIRECTOR")
            if(CollectionJointReportHandoverEvidence.currentBasis(row)!=null)original(E.BIZ,row.id.value,CollectionJointReportHandoverEvidence.CURRENT_BASIS)
            CollectionJointReportHandoverEvidence.entries(row).forEach {entry->
                original(E.BIZ,row.id.value,"${CollectionJointReportHandoverEvidence.PREFIX}P_${entry.record.id}")
                entry.signatures.keys.forEach {role->original(E.BIZ,row.id.value,"${CollectionJointReportHandoverEvidence.PREFIX}${role}_${entry.record.id}")}
            }
        }
        (checks+custodyChecks+rr.flatMap {E.checked(it)}).distinctBy {it.id.value}.forEach {check->original(CollectionConst.BIZ_CARE,check.id.value,"CHECKED")}
        custody.forEach {entry->
            original(CollectionConst.BIZ_REQUISITION,id,"${RequisitionCustodyEvidence.PREFIX}P_${entry.record.id}")
            entry.signatures.keys.forEach {role->original(CollectionConst.BIZ_REQUISITION,id,"${RequisitionCustodyEvidence.PREFIX}${role}_${entry.record.id}")}
        }
        RequisitionRecallEvidence.entries(r).forEach {entry->original(CollectionConst.BIZ_REQUISITION,id,"${RequisitionRecallEvidence.PREFIX}${entry.id}")}
        return mapOf("evidenceErrors" to evidenceErrors.distinct(),"archiveFiles" to archiveFiles,"orgId" to r.orgId,"orgName" to CollectionOrgDao[r.orgId].name,"canOperate" to canOperate(r.orgId),"requisitionId" to id,"documentNo" to r.documentNo,"sourceRevision" to source?.let {C.digest(it.snapshot)},"source" to source,
            "actors" to mapOf("keeperUserId" to actors["KEEPER"],"useUserId" to actors["USE"],"directorUserId" to actors["DIRECTOR"]),
            "custody" to custody.map {e->mapOf("id" to e.record.id,"content" to e.record.request,"effective" to e.complete,"effectiveAt" to e.effectiveAt,"signatures" to RequisitionCustodyEvidence.actors(r,e).map {(role,actor)->mapOf("role" to role,"actorUserId" to actor,"signature" to e.signatures[role])})},
            "recalls" to RequisitionRecallEvidence.entries(r),
            "recallAvailable" to (r.outExecuted && !r.returned && source?.cancelled==false && source.collectionIds.any {it !in received}),
            "effectiveCustodyId" to effective?.record?.id,"custodyAvailable" to (r.outExecuted && !r.returned && source?.cancelled==false && !received.containsAll(source.collectionIds) && r.sourceBizType!="COLLECTION_RESTORATION"),
            "custodyChecks" to custodyChecks.map {mapOf("id" to it.id.value,"collectionId" to it.collectionId,"content" to it.content)},
            "plans" to ps.map {p->mapOf("id" to p.id.value,"version" to p.version,"content" to p.content,"confirmed" to E.confirmed(p),"sourceCurrent" to (p.sourceSnapshot==source?.snapshot),"signatures" to E.signatures(p).map {(role,actor,sig)->mapOf("role" to role,"actorUserId" to actor,"signature" to sig)})},
            "rounds" to rr.map {row->val p=CollectionJointSafetyPlanDao[row.planId];mapOf("id" to row.id.value,"content" to row.content,"checks" to E.checked(row).map {mapOf("id" to it.id.value,"collectionId" to it.collectionId,"content" to it.content,"clearance" to it.clearance)},"unsafe" to E.unsafe(row),"confirmed" to E.confirmed(p,row),"signatures" to E.signatures(p,row).map {(role,actor,sig)->mapOf("role" to role,"actorUserId" to actor,"signature" to sig)},"report" to row.report,"director" to row.director,"directorUserId" to p.content.directorUserId,
                "reportDigest" to row.report?.let {CollectionJointReportHandoverEvidence.report(row).digest},"roundDigest" to CollectionJointReportHandoverEvidence.roundDigest(row),"currentReportBasis" to CollectionJointReportHandoverEvidence.currentBasis(row)?.request,"reportHandled" to E.reportHandled(row),
                "reportHandovers" to CollectionJointReportHandoverEvidence.entries(row).map {e->mapOf("id" to e.record.id,"content" to e.record.request,"complete" to e.complete,"signatures" to CollectionJointReportHandoverEvidence.actors(e).map {(role,actor)->mapOf("role" to role,"actorUserId" to actor,"signature" to e.signatures[role])})})},
            "availableChecks" to checks.map {mapOf("id" to it.id.value,"collectionId" to it.collectionId,"content" to it.content,"clearance" to it.clearance)},
            "items" to itemRows(r,ps,rr))
    }
    fun page(request:HttpServletRequest):GenericPage<Map<String,Any?>> {
        val orgs=readableOrgs()
        val keyword=request.getParameter("keyword").orEmpty().trim()
        val status=request.getParameter("status").orEmpty()
        val visible=if(orgs.isEmpty())emptyList() else RequisitionDao.find {Requisitions.orgId inList orgs}.toList()
        val rows=visible.filter {it.outExecuted || it.flowStatus !in setOf(io.hizhengfu.cloud.common.domain.EnumFlowStatus.CANCEL,io.hizhengfu.cloud.common.domain.EnumFlowStatus.REJECT,io.hizhengfu.cloud.common.domain.EnumFlowStatus.VOID)}.flatMap {r->
            try {
                E.requireOwn(r)
                val ps=E.plans(r.id.value);val rr=E.rounds(r.id.value);ps.forEach(E::plan);rr.forEach(E::round)
                itemRows(r,ps,rr)
            } catch(e:BadRequestException) {
                splitCollectionIds(r.collectionIds).map {cid->val c=CollectionDao.findById(cid)?.takeIf {it.orgId==r.orgId};mapOf("rowKey" to "${r.id.value}:$cid","requisitionId" to r.id.value,"documentNo" to r.documentNo,"collectionId" to cid,"name" to c?.name,"totalRegNo" to c?.totalRegNo,"status" to "ORIGINAL_ERROR","evidenceError" to e.message)}
            }
        }.filter {row->(status.isBlank() || row["status"]==status) && (keyword.isBlank() || listOf("documentNo","name","totalRegNo").any {row[it]?.toString()?.contains(keyword,true)==true})}
            .sortedWith(compareBy<Map<String,Any?>> {it["nextDueAt"] as? LocalDateTime ?: LocalDateTime.MAX}.thenBy {it["documentNo"].toString()}.thenBy {it["collectionId"].toString()})
        return GenericPage<Map<String,Any?>>().build(request).also {p->p.total=rows.size.toLong();p.list=if(p.pageSize==0)rows else rows.drop(p.startRow.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(p.pageSize)}
    }

}

@Component
class CollectionJointSafetyGuard(private val users:CollectionResponsibleUserService):CollectionOperationSafetyGuard {
    override fun requireAllowed(collectionId:String,bizType:String?,bizId:String?) {
        if(bizType in setOf(CollectionConst.BIZ_RETURN,CollectionConst.BIZ_INVENTORY,CollectionConst.BIZ_CHANGE))return
        val org=CollectionDao.findById(collectionId)?.orgId ?: return
        val actual=RequisitionDao.find {(Requisitions.orgId eq org) and (Requisitions.outExecuted eq true)}.filter {collectionId in splitCollectionIds(it.collectionIds)}
        val requested=if(bizType==CollectionConst.BIZ_REQUISITION && bizId!=null)RequisitionDao.findById(bizId)?.takeIf {it.orgId==org && collectionId in splitCollectionIds(it.collectionIds)} else null
        val relevant=(actual+listOfNotNull(requested)).distinctBy {it.id.value}
        relevant.forEach {r->
            E.rounds(r.id.value).forEach {row->E.round(row);if(E.checked(row).any {it.collectionId==collectionId && C.unsafe(it)} && !E.reportHandled(row))C.fail("本件共同安全检查发现问题，尚未完成实际向主要负责人报告及本人处理意见")}
            if(r.outExecuted && collectionId in E.receivedIds(r))return@forEach
            if(!r.outExecuted && (bizType!=CollectionConst.BIZ_REQUISITION || r.id.value!=bizId))return@forEach
            if(r.dossier==null)C.fail("实际提用缺少完整来源和检查职责，请据实补齐历史资料")
            if(r.outExecuted)E.outbound(r)
            val p=E.current(r) ?: C.fail("本次提用尚缺保管、提用、安全保卫共同制定的定期检查方案")
            users.requireRole(p.content.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            users.requireRole(p.content.securityUserId,r.orgId,CollectionConst.ROLE_SECURITY)
            users.requireDepartmentMember(p.content.useUserId,r.orgId,r.dossier!!.departmentGroupId)
            if(!r.outExecuted && !LocalDateTime.now().isBefore(p.content.firstDueAt))C.fail("首次共同检查安排已过期，请先按本次实际安排重新核实方案")
            if(r.outExecuted && !LocalDateTime.now().isBefore(E.nextDue(r,p,collectionId)))C.fail("本件定期共同安全检查已到期或尚缺真实初次核查，请完成实际逐件检查及三方本人确认")
        }
    }
}
