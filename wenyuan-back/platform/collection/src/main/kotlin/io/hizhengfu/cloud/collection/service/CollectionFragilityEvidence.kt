package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.service.CollectionFragileAdjustmentEvidence as A
import java.time.Duration
import java.time.LocalDateTime

internal data class FragileUseSource(val orgId:String,val collectionIds:Set<String>,val snapshot:String,val outAt:LocalDateTime,val returnAt:LocalDateTime,val display:Boolean,val external:Boolean,val outExecuted:Boolean,val cancelled:Boolean,val handedAt:LocalDateTime?,val editable:Boolean,val returned:Boolean)

internal object CollectionFragilityEvidence {
    const val BIZ=CollectionConst.BIZ_FRAGILITY
    fun fail(s:String):Nothing=throw BadRequestException(s)
    fun json(v:Any)=CollectionCareEvidence.json(v)
    fun digest(v:String)=CollectionCareEvidence.digest(v)
    fun assessments(id:String,orgId:String?=null)=CollectionFragilityAssessmentDao.find {(CollectionFragilityAssessments.collectionId eq id) and (orgId?.let {CollectionFragilityAssessments.orgId eq it} ?: org.jetbrains.exposed.v1.core.Op.TRUE)}.sortedBy {it.version}
    fun plans(id:String,orgId:String?=null)=CollectionFragileUsePlanDao.find {(CollectionFragileUsePlans.collectionId eq id) and (orgId?.let {CollectionFragileUsePlans.orgId eq it} ?: org.jetbrains.exposed.v1.core.Op.TRUE)}.sortedBy {it.id.value}
    fun records(id:String,orgId:String?=null)=CollectionFragileUseRecordDao.find {(CollectionFragileUseRecords.collectionId eq id) and (orgId?.let {CollectionFragileUseRecords.orgId eq it} ?: org.jetbrains.exposed.v1.core.Op.TRUE)}.sortedBy {it.id.value}
    fun nextAssessmentVersion(id:String)=(CollectionFragilityAssessments.select(CollectionFragilityAssessments.version).where {CollectionFragilityAssessments.collectionId eq id}.maxOfOrNull {it[CollectionFragilityAssessments.version]} ?: 0)+1
    fun assessmentValue(p:CollectionFragilityAssessmentDao)=mapOf("collectionId" to p.collectionId,"version" to p.version,"catalog" to p.catalog,"content" to p.content)
    fun planValue(p:CollectionFragileUsePlanDao)=mapOf("collectionId" to p.collectionId,"sourceSnapshot" to p.sourceSnapshot,"content" to p.content)
    fun startValue(r:CollectionFragileUseRecordDao)=mapOf("collectionId" to r.collectionId,"planId" to r.planId,"started" to r.started) + (r.authorizationAdjustmentId?.let {mapOf("authorizationAdjustmentId" to it)} ?: emptyMap()) + (r.environmentArchiveId?.let {mapOf("environmentArchiveId" to it,"environmentDigest" to (CollectionPermanentArchiveDao.findById(it)?.digest ?: fail("实际使用缺少环境核验原件")))} ?: emptyMap())
    fun environment(r:CollectionFragileUseRecordDao):Any? {
        val id=r.environmentArchiveId ?: return null
        val a=CollectionPermanentArchiveDao.findById(id) ?: fail("实际使用缺少环境核验原件")
        if(a.orgId!=r.orgId || a.bizType!=BIZ || a.bizId!=r.id.value || a.phase!="ENVIRONMENT_VERIFIED" || a.digest!=digest(a.snapshot))fail("环境核验原件归属或内容不一致")
        return JsonUtils.mapper.readTree(a.snapshot)
    }
    fun exact(org:String,id:String,phase:String,v:Any) {
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.singleOrNull() ?: fail("缺少脆弱材质保护原件：$phase")
        if(a.orgId!=org || a.digest!=digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(json(v)))fail("脆弱材质保护记录与永久原件不一致")
    }
    private fun optional(org:String,id:String,phase:String,value:Any?) {
        if(value!=null)exact(org,id,phase,value)
        else if(CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.any())fail("脆弱材质保护阶段不能清除已归档的原件：$phase")
    }
    fun assessment(p:CollectionFragilityAssessmentDao) {
        exact(p.orgId,p.id.value,"ASSESSMENT",assessmentValue(p))
        signature(p.orgId,p.id.value,p.content.conservationUserId,p.content.keeperUserId,p.conservation,p.keeper)
    }
    private fun signature(org:String,id:String,conservation:String,keeper:String,a:CollectionCareSignature?,b:CollectionCareSignature?) {
        optional(org,id,"CONSERVATION",a);optional(org,id,"KEEPER",b)
        a?.let {exact(org,id,"CONSERVATION",it);if(it.actorUserId!=conservation)fail("保护修复确认人与指定人员不一致")}
        b?.let {exact(org,id,"KEEPER",it);if(it.actorUserId!=keeper || a==null || it.confirmedAt.isBefore(a.confirmedAt))fail("保管确认人员或确认顺序不一致")}
    }
    fun plan(p:CollectionFragileUsePlanDao) {
        if(p.sourceBizType!=p.content.sourceBizType || p.sourceBizId!=p.content.sourceBizId)fail("使用来源与冻结原方案不一致")
        exact(p.orgId,p.id.value,"USE_PLAN",planValue(p))
        val a=CollectionFragilityAssessmentDao.findById(p.content.assessmentId) ?: fail("使用方案的脆弱性评估不存在")
        if(a.orgId!=p.orgId || a.collectionId!=p.collectionId)fail("使用方案与脆弱性评估归属不一致")
        assessment(a);signature(p.orgId,p.id.value,a.content.conservationUserId,a.content.keeperUserId,p.conservation,p.keeper)
    }
    fun record(r:CollectionFragileUseRecordDao) {
        optional(r.orgId,r.id.value,"HISTORY",r.history?.let {mapOf("collectionId" to r.collectionId,"history" to it)})
        r.history?.let {if(r.started!=null || r.planId!=null || r.ended!=null || r.review!=null || r.environmentArchiveId!=null || r.authorizationAdjustmentId!=null)fail("既有事实补录不能改为当时已授权实施");return}
        val p=r.planId?.let(CollectionFragileUsePlanDao::findById) ?: fail("实际使用记录缺少原方案")
        if(p.orgId!=r.orgId || p.collectionId!=r.collectionId)fail("实际使用记录归属不一致")
        plan(p);exact(r.orgId,r.id.value,"STARTED",startValue(r));environment(r)
        r.authorizationAdjustmentId?.let {id->
            val a=CollectionFragileUseAdjustmentDao.findById(id) ?: fail("实际使用缺少所依据的变更确认原件")
            A.verify(a)
            val v=r.started ?: fail("实际使用缺少开始原件")
            val segment=a.content.adjustments.find {it.kind==v.kind && it.segmentIndex==v.segmentIndex} ?: fail("变更未覆盖本次实际使用段")
            if(a.orgId!=r.orgId || a.planId!=p.id.value || a.director==null || a.withdrawal!=null || segment.action!=EnumFragileAdjustmentAction.RESCHEDULE || v.startedAt.isBefore(a.director!!.confirmedAt) || v.startedAt.isBefore(segment.from) || !v.startedAt.isBefore(segment.to))fail("实际使用与其当时有效的变更确认不一致")
        }
        optional(r.orgId,r.id.value,"ENDED",r.ended)
        if(r.review==null)optional(r.orgId,r.id.value,"REVIEWED",null)
        r.review?.let {v->
            val check=CollectionCareCheckDao.findById(v.checkId) ?: fail("使用后现状检查不存在")
            if(check.orgId!=r.orgId || check.collectionId!=r.collectionId)fail("使用后现状检查归属不一致")
            CollectionCareEvidence.check(check)
            exact(r.orgId,r.id.value,"REVIEWED",mapOf("review" to v,"check" to CollectionCareEvidence.checkValue(check)))
        }
    }
    fun source(type:String,id:String):FragileUseSource? = when(type) {
        CollectionConst.BIZ_REQUISITION -> RequisitionDao.findById(id)?.let {r->r.dossier?.let {d->FragileUseSource(r.orgId,splitCollectionIds(r.collectionIds).toSet(),json(mapOf("id" to id,"collectionIds" to r.collectionIds,"documentNo" to r.documentNo,"purpose" to r.purpose,"sourceBizType" to r.sourceBizType,"sourceBizId" to r.sourceBizId,"basis" to r.basis,"department" to r.department,"operator" to r.operator,"reviewer" to r.reviewer,"attachmentIds" to r.attachmentIds,"toWarehouseId" to r.toWarehouseId,"expectReturnDate" to r.expectReturnDate,"createBy" to r.createBy,"dossier" to d,"baseline" to r.baseline)),d.expectedOutAt,d.expectedReturnAt,r.purpose==EnumMoveReason.EXHIBIT,false,r.outExecuted,(r.supersededById != null || r.sourceRetirement != null || r.flowStatus in setOf(EnumFlowStatus.CANCEL,EnumFlowStatus.REJECT,EnumFlowStatus.VOID)),r.handover?.content?.handedAt,r.flowStatus==EnumFlowStatus.DRAFT && r.flowInstanceId==null && !r.outExecuted,r.returned)}}
        CollectionConst.BIZ_LOAN -> LoanDao.findById(id)?.let {r->r.dossier?.let {d->
            val extensions=LoanOverseasExtensionEvidence.extensions(r)
            val extensionSnapshot=if(extensions.isEmpty())emptyMap() else mapOf("overseasExtensions" to extensions.map {listOf(it.first.id.value,it.second)})
            FragileUseSource(r.orgId,splitCollectionIds(r.collectionIds).toSet(),json(mapOf("id" to id,"orgId" to r.orgId,"title" to r.title,"collectionIds" to r.collectionIds,"borrower" to r.borrower,"protocolNo" to r.protocolNo,"insurance" to r.insurance,"attachmentIds" to r.attachmentIds,"operator" to r.operator,"reviewer" to r.reviewer,"loanDate" to r.loanDate,"returnDate" to r.returnDate,"dossier" to d,"baseline" to r.baseline)+extensionSnapshot),d.agreedOutAt,extensions.lastOrNull()?.second?.request?.agreedReturnAt ?: d.agreedReturnAt,d.purpose==EnumLoanPurpose.EXHIBITION,true,r.outExecuted,r.flowStatus in setOf(EnumFlowStatus.CANCEL,EnumFlowStatus.REJECT,EnumFlowStatus.VOID),r.handover?.content?.handedAt,r.flowStatus==EnumFlowStatus.DRAFT && r.flowInstanceId==null && !r.outExecuted,r.returned)}}
        else -> null
    }
    fun loanMonitoring(org:String,type:String,biz:String):List<Pair<String,LoanMonitoringVo>> {
        if(type!=CollectionConst.BIZ_LOAN)return emptyList()
        return LoanComplianceRecordDao.find {(LoanComplianceRecords.loanId eq biz) and (LoanComplianceRecords.kind eq "MONITORING")}.sortedBy {it.id.value}.map {row->
            val archive=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq type) and (CollectionPermanentArchives.bizId eq biz) and (CollectionPermanentArchives.phase eq "MONITORING_${row.id.value}")}.singleOrNull() ?: fail("缺少借用期间监管永久原件")
            if(row.orgId!=org || archive.orgId!=org || archive.digest!=digest(archive.snapshot) || JsonUtils.mapper.readTree(archive.snapshot)!=JsonUtils.mapper.readTree(row.content))fail("借用期间监管记录与永久原件不一致")
            row.id.value to (JsonUtils.fromJson(row.content,LoanMonitoringVo::class.java) ?: fail("借用监管原件无法读取"))
        }
    }
    fun requireLoanSafety(c:CollectionDao,type:String,biz:String) {
        val records=loanMonitoring(c.orgId,type,biz)
        val issues=records.filter {(_,v)->c.id.value in v.collectionIds && v.kind !in setOf(EnumLoanMonitoringKind.MAJOR_REPORT,EnumLoanMonitoringKind.ISSUE_RESOLVED) && (v.safetyConfirmed==false || v.majorSituation || v.kind==EnumLoanMonitoringKind.RECALL)}
        if(issues.any {(id,issue)->records.none {(_,v)->v.kind==EnumLoanMonitoringKind.ISSUE_RESOLVED && v.relatedRecordId==id && c.id.value in v.collectionIds && v.safetyConfirmed==true && !v.occurredAt.isBefore(issue.occurredAt)}})fail("借用期间仍有未实际核实安全的异常、重大情况或催还，不能开始新的普通展出或运输")
    }
    fun current(c:CollectionDao):CollectionFragilityAssessmentDao {
        val p=assessments(c.id.value,c.orgId).lastOrNull {it.keeper!=null} ?: fail("本件尚未实际核实材质脆弱性，不能将未知当作普通藏品放行；请先完成使用保护评估")
        assessment(p)
        if(p.catalog!=CollectionCareEvidence.catalog(c))fail("藏品资料已变化，请重新核实材质脆弱性和使用限制")
        return p
    }
    fun selected(id:String,type:String,biz:String)=plans(id).lastOrNull {it.sourceBizType==type && it.sourceBizId==biz}
    fun pendingSource(p:CollectionFragileUsePlanDao):Boolean=source(p.sourceBizType,p.sourceBizId)?.let {!it.cancelled || it.outExecuted} ?: true
    fun requireUse(c:CollectionDao,type:String,biz:String):CollectionFragileUsePlanDao? {
        val s=source(type,biz) ?: return null // 尚未形成使用方案的草稿不作为使用授权。
        if(s.orgId!=c.orgId || c.id.value !in s.collectionIds)fail("使用来源与本馆本件藏品不一致")
        if(s.returned || s.cancelled)fail("本次使用已归还或已撤销，不能沿用旧出库原件重新开始")
        requireLoanSafety(c,type,biz)
        val a=current(c)
        if(s.handedAt!=null && a.keeper!!.confirmedAt.isAfter(s.handedAt))fail("材质脆弱性评估应在实际提取之前确认")
        val selected=selected(c.id.value,type,biz)
        records(c.id.value).forEach {record(it);if(it.planId!=selected?.id?.value && it.history==null && (it.ended==null || it.review==null))fail("此前实际展出或运输尚未结束并核对现状，不能开始新的普通使用")}
        plans(c.id.value).groupBy {it.sourceBizType to it.sourceBizId}.values.map {it.last()}.filter {(it.sourceBizType!=type || it.sourceBizId!=biz) && it.keeper!=null && source(it.sourceBizType,it.sourceBizId)?.outExecuted==true}.forEach {old->
            val rr=records(c.id.value).filter {it.planId==old.id.value}
            if(A.pending(old)!=null || A.segments(old).filterNot {it.cancelled}.any {segment->rr.none {it.started?.kind==segment.kind && it.started?.segmentIndex==segment.segmentIndex && it.ended!=null && it.review!=null}})fail("此前使用方案尚缺实际展出、运输及结束现状原件，不能默认未发生而释放频次")
        }
        if(selected!=null && selected.content.assessmentId!=a.id.value)fail("本次使用所依据的材质评估已变更，须撤回未实施申请并重新核实")
        if(!a.content.fragileMaterial)return null
        val p=selected ?: fail("脆弱材质藏品须先核对本次具体使用时段、运输安排和保护方案")
        plan(p);CollectionFragileEnvironmentEvidence.sources(a.content,p.content)
        if(A.pending(p)!=null)fail("本次实际使用变更尚待共同确认，不能开始新的普通使用")
        if(p.keeper==null || p.content.assessmentId!=a.id.value || p.sourceSnapshot!=s.snapshot)fail("本次保护方案尚未共同确认，或使用/保护依据已变化")
        if(s.handedAt!=null && (a.keeper!!.confirmedAt.isAfter(s.handedAt) || p.keeper!!.confirmedAt.isAfter(s.handedAt)))fail("实际提取须在保护评估和本次使用方案确认之后")
        budget(c,a,p,s.handedAt ?: maxOf(s.outAt,LocalDateTime.now()))
        return p
    }
    fun budget(c:CollectionDao,a:CollectionFragilityAssessmentDao,p:CollectionFragileUsePlanDao,outAt:LocalDateTime,adjustment:CollectionFragileUseAdjustmentDao?=null) {
        val limit=a.content.limits ?: fail("脆弱材质使用限制尚未核实")
        if(a.content.historyVerifiedFrom.isAfter(outAt.minusDays(limit.windowDays.toLong())))fail("尚未核实本次频次统计窗口内的既有使用记录")
        val extractions=StorageMovementDao.find {StorageMovements.collectionId eq c.id.value}.filter {it.type==EnumMoveType.OUT && it.refBizId!=p.sourceBizId}.map {it.operatedAt}+outAt
        maximumPoints(extractions,limit.windowDays,limit.maximumExtractions,"提取",outAt)
        val rr=records(c.id.value).also {it.forEach(::record)}
        val pp=plans(c.id.value).filter {it.id==p.id || (it.keeper!=null && pendingSource(it))}.groupBy {it.sourceBizType to it.sourceBizId}.map {(_,v)->v.last()}
        val transports=rr.mapNotNull {r->r.history?.takeIf {it.kind==EnumFragileUseKind.TRANSPORT}?.startedAt ?: r.started?.takeIf {it.kind==EnumFragileUseKind.TRANSPORT}?.startedAt}.toMutableList()
        val display=rr.mapNotNull {r->r.history?.takeIf {it.kind==EnumFragileUseKind.DISPLAY}?.let {it.startedAt to it.endedAt} ?: r.started?.takeIf {it.kind==EnumFragileUseKind.DISPLAY}?.let {it.startedAt to (r.ended?.endedAt ?: maxOf(LocalDateTime.now(),r.planId?.let(CollectionFragileUsePlanDao::findById)?.content?.displayTo ?: LocalDateTime.now()))}}.toMutableList()
        pp.forEach {plan->
            A.segments(plan,adjustment?.takeIf {it.planId==plan.id.value}).filterNot {it.cancelled}.forEach {segment->
                if(rr.none {it.planId==plan.id.value && it.started?.kind==segment.kind && it.started?.segmentIndex==segment.segmentIndex}) {
                    if(segment.kind==EnumFragileUseKind.TRANSPORT)transports+=segment.from else display+=segment.from to segment.to
                }
            }
        }
        maximumPoints(transports,limit.windowDays,limit.maximumTransports,"运输（含尚待实施或核实的已确认安排）",outAt)
        maximumDisplay(display,limit,outAt)
    }
    fun maximumPoints(times:List<LocalDateTime>,days:Int,maximum:Int,label:String,from:LocalDateTime) {
        if((times.filter {!it.isBefore(from)}+from).any {end->times.count {!it.isBefore(end.minusDays(days.toLong())) && !it.isAfter(end)}>maximum})fail("$label 次数超过本件评估确定的滚动 $days 天限额")
    }
    fun maximumDisplay(spans:List<Pair<LocalDateTime,LocalDateTime>>,limit:FragileUseLimitsVo,from:LocalDateTime) {
        val sorted=spans.sortedBy {it.first}
        if(sorted.zipWithNext().any {(a,b)->!b.first.isBefore(from) && b.first.isBefore(a.second.plusHours(limit.minimumRestHours.toLong()))})fail("展出安排重叠或未满足本件展后间隔要求")
        val endpoints=spans.flatMap {listOf(it.first,it.second,it.first.plusDays(limit.windowDays.toLong()),it.second.plusDays(limit.windowDays.toLong()))}
        if((endpoints.filter {!it.isBefore(from)}+from).any {end->
            val start=end.minusDays(limit.windowDays.toLong())
            spans.sumOf {v->val from=maxOf(v.first,start);val to=minOf(v.second,end);if(to.isAfter(from))Duration.between(from,to).toMillis() else 0L}>limit.maximumDisplayHours.toLong()*3600000
        })fail("累计展出时长超过本件评估确定的滚动 ${limit.windowDays} 天限额")
    }
}
