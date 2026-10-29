package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionFragileAdjustmentEvidence as A
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
class CollectionFragilityService(private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,private val archives:CollectionPermanentArchiveService,private val locks:CollectionOperationLockService,private val environmentProviders:List<CollectionFragileEnvironmentProvider> = emptyList()) {
    private fun environmentProvider()=environmentProviders.singleOrNull()
    fun environmentSources(id:String):List<FragileEnvironmentDeviceOption> {
        val c=collection(id);val result=environmentProvider()?.sources(c.orgId).orEmpty()
        CollectionFragilityAccess.require(scope,c.orgId,false);return result
    }
    private fun collection(id:String,write:Boolean=false,expectedOrgId:String?=null) = CollectionFragilityAccess.collection(scope,id,write,expectedOrgId)
    private fun people(p:CollectionFragilityAssessmentDao) {
        val v=p.content
        if(v.conservationUserId==v.keeperUserId)E.fail("保护修复与保管确认须分别指定实际责任人员")
        users.requireMuseumMember(v.conservationUserId,p.orgId);users.requireRole(v.conservationUserId,p.orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireMuseumMember(v.keeperUserId,p.orgId);users.requireRole(v.keeperUserId,p.orgId,CollectionConst.ROLE_STORAGE_HEAD)
    }
    private fun required(vararg s:String){if(s.any(String::isBlank))E.fail("请完整填写实际评估、保护要求和签署原件")}
    private fun actual(at:LocalDateTime){if(at.isAfter(LocalDateTime.now()))E.fail("实际发生时间不能填写未来")}
    fun detail(id:String):Map<String,Any?> {
        val c=collection(id);val aa=E.assessments(id,c.orgId);val pp=E.plans(id,c.orgId);val rr=E.records(id,c.orgId)
        aa.forEach(E::assessment);pp.forEach(E::plan);rr.forEach(E::record)
        val evidenceErrors=mutableListOf<String>()
        val archiveFiles=linkedMapOf<String,MutableMap<String,Any?>>()
        fun original(recordId:String,phase:String,bizType:String=E.BIZ) {
            val archive=archives.phaseForDisplay(bizType,recordId,c.orgId,phase)
            (archive?.get("evidenceIssue") as? String)?.let {evidenceErrors += it}
            archiveFiles.getOrPut(recordId){linkedMapOf()}[phase]=archive?.get("files") ?: emptyList<Map<String,Any?>>()
        }
        aa.forEach {assessment->
            original(assessment.id.value,"ASSESSMENT")
            if(assessment.conservation!=null)original(assessment.id.value,"CONSERVATION")
            if(assessment.keeper!=null)original(assessment.id.value,"KEEPER")
        }
        pp.forEach {plan->
            original(plan.id.value,"USE_PLAN")
            if(plan.conservation!=null)original(plan.id.value,"CONSERVATION")
            if(plan.keeper!=null)original(plan.id.value,"KEEPER")
            A.all(plan.id.value).forEach {adjustment->
                original(adjustment.id.value,"ADJUSTMENT")
                A.signatures(adjustment).filter {it.third!=null}.forEach {(role,_,_)->original(adjustment.id.value,"ADJUSTMENT_$role")}
                if(adjustment.withdrawal!=null)original(adjustment.id.value,"ADJUSTMENT_WITHDRAWN")
                val check=CollectionCareCheckDao.findById(adjustment.content.checkId)
                    ?.takeIf {it.orgId==c.orgId && it.collectionId==id} ?: E.fail("变更核查缺少本馆本件现状检查原件")
                CollectionCareEvidence.check(check);original(check.id.value,"CHECKED",CollectionConst.BIZ_CARE)
                // Borrowed monitoring metadata is visible only through this adjustment's frozen source fact.
                if(plan.sourceBizType==CollectionConst.BIZ_LOAN) {
                    val frozen=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(adjustment.factsSnapshot).path("loanMonitoring")
                    val sources=E.loanMonitoring(c.orgId,plan.sourceBizType,plan.sourceBizId)
                    frozen.forEach {source->
                        val monitoringId=source.path("id").asString()
                        val fileId=sources.singleOrNull {it.first==monitoringId}?.second?.fileIds?.let(::splitCollectionIds)?.firstOrNull()
                        if(fileId!=null) {
                            CollectionFragilityOriginalService(scope,archives).requireLoanMonitoringSource(id,c.orgId,adjustment.id.value,monitoringId,fileId,verifyContent=false)
                            val archive=archives.phaseForDisplay(CollectionConst.BIZ_LOAN,plan.sourceBizId,c.orgId,"MONITORING_$monitoringId")
                            (archive?.get("evidenceIssue") as? String)?.let {evidenceErrors += it}
                            archiveFiles.getOrPut(monitoringId){linkedMapOf()}["MONITORING_$monitoringId"]=archive?.get("files") ?: emptyList<Map<String,Any?>>()
                        }
                    }
                }
            }
        }
        rr.forEach {record->
            if(record.history!=null)original(record.id.value,"HISTORY")
            if(record.started!=null) {
                original(record.id.value,"STARTED")
                if(record.environmentArchiveId!=null)original(record.id.value,"ENVIRONMENT_VERIFIED")
            }
            if(record.ended!=null)original(record.id.value,"ENDED")
            if(record.review!=null)original(record.id.value,"REVIEWED")
        }
        CollectionFragileMonitoringEvidence.all(id,c.orgId).forEach {monitoring->
            CollectionFragileMonitoringEvidence.verify(monitoring);original(monitoring.id.value,"MONITORED")
            CollectionFragileMonitoringEvidence.clearances(monitoring).forEach {original(monitoring.id.value,"MONITOR_CLEARED_${it.version}")}
        }
        CollectionFragileAutomaticEvidence.all(id).filter {it.orgId==c.orgId && it.clearance!=null}.forEach {automatic->
            CollectionFragileAutomaticEvidence.verify(automatic)
            CollectionFragileAutomaticEvidence.clearances(automatic).forEach {original(automatic.id.value,"AUTO_CLEARED_${it.version}")}
        }
        return mapOf("evidenceErrors" to evidenceErrors.distinct(),"archiveFiles" to archiveFiles,"collectionId" to id,"orgId" to c.orgId,"orgName" to CollectionOrgDao[c.orgId].name,"canOperate" to CollectionFragilityAccess.canOperate(scope,c.orgId),"name" to c.name,"sourceRevision" to E.digest(E.json(CollectionCareEvidence.catalog(c))),
            "monitoringReports" to CollectionFragileMonitoringEvidence.all(id,c.orgId).map {m->CollectionFragileMonitoringEvidence.verify(m);mapOf("id" to m.id.value,"useRecordId" to m.useRecordId,"request" to m.request,"evidence" to CollectionFragileMonitoringEvidence.summary(m),"clearance" to m.clearance,"clearances" to CollectionFragileMonitoringEvidence.clearances(m))},
            "assessments" to aa.map {mapOf("id" to it.id.value,"version" to it.version,"content" to it.content,"conservation" to it.conservation,"keeper" to it.keeper)},
            "plans" to pp.map {mapOf("id" to it.id.value,"content" to it.content,"conservation" to it.conservation,"keeper" to it.keeper,
                "effectiveSegments" to A.segments(it),"adjustmentPending" to (A.pending(it)!=null),
                "adjustmentFactsRevision" to E.digest(A.facts(c,it)),
                "adjustments" to A.all(it.id.value).map {a->mapOf("id" to a.id.value,"content" to a.content,"facts" to io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(a.factsSnapshot),"actors" to a.actors,"proposedBy" to a.proposedBy,"signatures" to A.signatures(a).map {(role,actor,sig)->mapOf("role" to role,"actorUserId" to actor,"signature" to sig)},"withdrawal" to a.withdrawal,"effective" to (a.director!=null),"latest" to (A.all(a.planId).last().id==a.id))},
                "sourceHasOutbound" to (E.source(it.sourceBizType,it.sourceBizId)?.outExecuted==true && E.selected(id,it.sourceBizType,it.sourceBizId)?.id==it.id),
                "sourceOutExecuted" to (E.source(it.sourceBizType,it.sourceBizId)?.let {s->s.outExecuted && !s.returned && !s.cancelled}==true && c.lifeStatus in setOf(EnumCollectionStatus.IN_USE,EnumCollectionStatus.ON_LOAN)),"sourceEditable" to E.source(it.sourceBizType,it.sourceBizId)?.editable,"current" to (E.selected(id,it.sourceBizType,it.sourceBizId)?.id==it.id && aa.lastOrNull {a->a.keeper!=null}?.takeIf {a->a.catalog==CollectionCareEvidence.catalog(c)}?.id?.value==it.content.assessmentId && E.source(it.sourceBizType,it.sourceBizId)?.snapshot==it.sourceSnapshot))},
            "records" to rr.map {r->mapOf("id" to r.id.value,"planId" to r.planId,"automaticMonitoring" to if(r.started!=null)CollectionFragileAutomaticEvidence.runtime(r,LocalDateTime.now()) else null,"started" to r.started,"ended" to r.ended,"history" to r.history,"review" to r.review,"authorizationAdjustmentId" to r.authorizationAdjustmentId,"environmentVerification" to E.environment(r),
                "plannedEnd" to r.planId?.let(CollectionFragileUsePlanDao::findById)?.let {p->A.segments(p).find {it.kind==r.started?.kind && it.segmentIndex==r.started?.segmentIndex}?.to})})
    }
    fun source(id:String,type:String,biz:String):Map<String,Any?> {
        val c=collection(id);val s=E.source(type,biz) ?: E.fail("请先保存完整提用或借用方案")
        if(s.orgId!=c.orgId || id !in s.collectionIds)E.fail("使用来源不属于本馆本件藏品")
        return mapOf("sourceRevision" to E.digest(s.snapshot),"expectedOutAt" to s.outAt,"expectedReturnAt" to s.returnAt,"display" to s.display,"external" to s.external,"outExecuted" to s.outExecuted,"editable" to (s.editable && CollectionFragilityAccess.canOperate(scope,c.orgId)))
    }
    fun assess(id:String,v:FragilityAssessmentVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val all=E.assessments(id,c.orgId)
        all.lastOrNull()?.takeIf {it.content==v}?.let {E.assessment(it);return it.id.value}
        if(v.previousAssessmentId!=all.lastOrNull()?.id?.value || v.sourceRevision!=E.digest(E.json(CollectionCareEvidence.catalog(c))))E.fail("藏品资料或评估版本已变化，请重新核对")
        actual(v.assessedAt)
        required(v.materialAndCondition,v.environmentRequirements,v.storageProtection,v.useRiskAssessment,v.historyReviewFileIds,v.assessmentFileIds)
        if(v.historyVerifiedFrom.isAfter(v.assessedAt))E.fail("历史使用核对起点不得晚于实际评估时间")
        if(v.fragileMaterial) {
            val l=v.limits ?: E.fail("材质脆弱藏品须按实际评估填写全部使用限值")
            if(l.windowDays !in 1..3650 || l.maximumExtractions !in 0..1000000 || l.maximumTransports !in 0..1000000 || l.maximumDisplayHours !in 0..87600 || l.minimumRestHours !in 0..87600)E.fail("请填写有效的统计窗口和非负使用限值，0 表示禁止该类常规使用")
            if(v.historyVerifiedFrom.isAfter(v.assessedAt.minusDays(l.windowDays.toLong())))E.fail("须先核对本件完整统计窗口内的提取、运输和展出历史")
        } else if(v.limits!=null)E.fail("非脆弱材质评估不应混填脆弱材质限额，请核实实际结论")
        CollectionFragileEnvironmentEvidence.criteria(v)
        val p=CollectionFragilityAssessmentDao.new {orgId=c.orgId;collectionId=id;version=E.nextAssessmentVersion(id);catalog=CollectionCareEvidence.catalog(c);content=v}
        people(p);archives.seal(c.orgId,E.BIZ,p.id.value,"ASSESSMENT",E.assessmentValue(p),"${v.assessmentFileIds},${v.historyReviewFileIds}");return CollectionFragilityAccess.finish(scope,c,p.id.value,expectedOrgId)
    }
    fun savePlan(id:String,v:FragileUsePlanVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val a=E.current(c);people(a)
        if(!a.content.fragileMaterial || v.assessmentId!=a.id.value)E.fail("本次具体保护方案须依据当前共同确认的脆弱材质评估")
        val s=E.source(v.sourceBizType,v.sourceBizId) ?: E.fail("须先保存完整提用或借用方案")
        if(s.orgId!=c.orgId || id !in s.collectionIds || v.sourceRevision!=E.digest(s.snapshot))E.fail("来源使用清单或方案已变化，请重新核对")
        val old=E.selected(id,v.sourceBizType,v.sourceBizId)
        old?.takeIf {it.content==v}?.let {E.plan(it);return it.id.value}
        if(!s.editable || E.records(id).any {it.planId==old?.id?.value && it.started!=null})E.fail("本次使用已提交确认或已实际发生，不能替换保护方案；未实施申请须撤回并重新建立")
        required(v.environmentAndProtection,v.necessityAndAlternatives,v.signedPlanFileIds)
        if((v.displayFrom==null)!=(v.displayTo==null) || s.display!=(v.displayFrom!=null))E.fail("实际展览用途须明确展出起止，非展览用途不能混填展出授权")
        if(v.displayFrom!=null && (!v.displayTo!!.isAfter(v.displayFrom) || v.displayFrom.isBefore(s.outAt) || v.displayTo.isAfter(s.returnAt)))E.fail("展出时间须在本次使用范围内且结束晚于开始")
        if(s.external && v.transports.size<2)E.fail("外借须核实实际出借和归还两个方向的运输安排")
        val trips=v.transports.sortedBy {it.departsAt}
        if(v.transports!=trips || trips.any {it.route.isBlank() || it.protection.isBlank() || !it.arrivesAt.isAfter(it.departsAt) || it.departsAt.isBefore(s.outAt) || it.arrivesAt.isAfter(s.returnAt)} || trips.zipWithNext().any {(a,b)->b.departsAt.isBefore(a.arrivesAt)})E.fail("运输安排须按实际顺序明确时段、路线和保护措施，并在使用范围内")
        if(v.displayFrom!=null && trips.any {it.departsAt.isBefore(v.displayTo) && it.arrivesAt.isAfter(v.displayFrom)})E.fail("实际展出与运输安排不能重叠")
        CollectionFragileEnvironmentEvidence.sources(a.content,v)
        val options=if(v.environmentSources.any {it.type==EnumFragileEnvironmentSource.REGISTERED_DEVICE})
            (environmentProvider() ?: E.fail("联网监测来源服务不可用，请恢复后核对方案")).sources(c.orgId) else emptyList()
        v.environmentSources.filter {it.type==EnumFragileEnvironmentSource.REGISTERED_DEVICE}.forEach {src->
            val criterion=a.content.environmentCriteria.single {it.kind==src.kind && it.metricCode==src.metricCode}
            if(options.none {it.deviceId==src.deviceId && it.locationId==src.locationId && it.metricCode==src.metricCode && it.unit==criterion.unit})E.fail("联网测量来源不属于本馆有效藏品位置，或指标单位与本件评估不一致")
        }
        val p=CollectionFragileUsePlanDao.new {orgId=c.orgId;collectionId=id;sourceBizType=v.sourceBizType;sourceBizId=v.sourceBizId;sourceSnapshot=s.snapshot;content=v}
        archives.seal(c.orgId,E.BIZ,p.id.value,"USE_PLAN",E.planValue(p),(listOf(v.signedPlanFileIds)+v.environmentSources.map {it.qualificationFileIds}).joinToString(","))
        E.budget(c,a,p,maxOf(s.outAt,LocalDateTime.now()));return CollectionFragilityAccess.finish(scope,c,p.id.value,expectedOrgId)
    }
    fun sign(id:String,recordId:String,v:CollectionCareSignatureVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);required(v.opinion,v.fileIds)
        val p=CollectionFragilityAssessmentDao.findById(recordId)
        val use=if(p==null)CollectionFragileUsePlanDao.findById(recordId) ?: E.fail("评估或使用方案不存在") else null
        val a=p ?: CollectionFragilityAssessmentDao[use!!.content.assessmentId]
        if(a.orgId!=c.orgId || a.collectionId!=id || (use!=null && (use.orgId!=c.orgId || use.collectionId!=id)))E.fail("确认原件不属于本馆本件藏品")
        E.assessment(a);people(a);CollectionFragileEnvironmentEvidence.criteria(a.content)
        if(p!=null && E.assessments(id,c.orgId).last().id!=p.id)E.fail("请确认最新评估版本")
        if(a.catalog!=CollectionCareEvidence.catalog(c))E.fail("藏品资料已变化，须重新评估")
        if(use!=null) {
            E.plan(use);CollectionFragileEnvironmentEvidence.sources(a.content,use.content);val s=E.source(use.sourceBizType,use.sourceBizId) ?: E.fail("使用来源不存在")
            if(E.current(c).id!=a.id || E.selected(id,use.sourceBizType,use.sourceBizId)?.id!=use.id || s.snapshot!=use.sourceSnapshot || !s.editable)E.fail("使用依据已改变或已经实际出库，不能补造事前确认")
            E.budget(c,a,use,maxOf(s.outAt,LocalDateTime.now()))
        }
        val who=SecurityUtils.getUserId()
        val role=when(who){a.content.conservationUserId->"CONSERVATION";a.content.keeperUserId->"KEEPER";else->E.fail("须由本方案指定责任人员本人确认，管理员不能代签")}
        val ca=if(p!=null)p.conservation else use!!.conservation;val cb=if(p!=null)p.keeper else use!!.keeper
        val old=if(role=="CONSERVATION")ca else cb
        old?.let {if(it.record==v)return recordId;E.fail("本人确认原件不能替换，改变须另立版本")}
        if(role=="KEEPER" && ca==null)E.fail("须先由保护修复责任人员本人确认")
        val sig=CollectionCareSignature(who,LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),v)
        if(p!=null){if(role=="CONSERVATION")p.conservation=sig else p.keeper=sig} else {if(role=="CONSERVATION")use!!.conservation=sig else use!!.keeper=sig}
        archives.seal(c.orgId,E.BIZ,recordId,role,sig,v.fileIds);return CollectionFragilityAccess.finish(scope,c,recordId,expectedOrgId)
    }
    fun history(id:String,v:FragileUseHistoryVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);actual(v.endedAt)
        if(v.endedAt.isBefore(v.startedAt))E.fail("实际结束不得早于开始")
        required(v.reference,v.actualDetails,v.fileIds)
        E.records(id,c.orgId).firstOrNull {it.history==v}?.let {E.record(it);return it.id.value}
        val r=CollectionFragileUseRecordDao.new {orgId=c.orgId;collectionId=id;history=v}
        archives.seal(c.orgId,E.BIZ,r.id.value,"HISTORY",mapOf("collectionId" to id,"history" to v),v.fileIds);return CollectionFragilityAccess.finish(scope,c,r.id.value,expectedOrgId)
    }
    fun start(id:String,planId:String,v:FragileUseStartVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val p=CollectionFragileUsePlanDao.findById(planId) ?: E.fail("使用保护方案不存在")
        if(p.collectionId!=id || p.orgId!=c.orgId)E.fail("使用保护方案不属于本馆本件藏品")
        E.records(id).firstOrNull {it.planId==planId && it.started==v}?.let {E.record(it);return it.id.value}
        locks.requireUnlocked(id,p.sourceBizType,p.sourceBizId)
        if(c.inventoryHold)E.fail("藏品仍有清点异常限制，不能开始普通展出或运输")
        val expectedStatus=if(p.sourceBizType==CollectionConst.BIZ_LOAN)EnumCollectionStatus.ON_LOAN else EnumCollectionStatus.IN_USE
        if(c.lifeStatus!=expectedStatus)E.fail("本次实物已不在相应提用或借出状态，不能登记新的展出或运输开始")
        val approved=E.requireUse(c,p.sourceBizType,p.sourceBizId)
        if(approved?.id!=p.id)E.fail("本段须依据当前有效的具体使用保护方案")
        val s=E.source(p.sourceBizType,p.sourceBizId) ?: E.fail("实际使用来源或完整方案不存在")
        if(p.sourceBizType==CollectionConst.BIZ_LOAN && v.kind==EnumFragileUseKind.DISPLAY)LoanExhibitionEndEvidence.requireDisplayStart(LoanDao[p.sourceBizId],v.startedAt)
        if(!s.outExecuted || s.handedAt==null || v.startedAt.isBefore(s.handedAt) || v.startedAt.isBefore(p.keeper!!.confirmedAt))E.fail("须在实际提取点交及保护方案确认后，才能登记实际展出或运输开始")
        val out=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq p.sourceBizType) and (CollectionPermanentArchives.bizId eq p.sourceBizId) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED")}.singleOrNull() ?: E.fail("缺少实际出库原件")
        val root=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(out.snapshot).get("request") ?: E.fail("实际出库原件缺少申请资料")
        if(out.orgId!=c.orgId || out.digest!=E.digest(out.snapshot) || root.get("handover")?.get("content")?.get("handedAt")!=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(E.json(s.handedAt)) || id !in root.get("collectionIds")?.stringValue().orEmpty().split(','))E.fail("实际出库原件与本件点交不一致")
        actual(v.startedAt);required(v.actualLocationOrRoute,v.actualEnvironmentAndProtection,v.signedFileIds)
        if(v.operatorUserId!=SecurityUtils.getUserId())E.fail("须由实际操作人员本人登记")
        users.requireMuseumMember(v.operatorUserId,c.orgId)
        val own=E.records(id).filter {it.planId==planId}
        if(own.any {it.ended==null || it.review==null || v.startedAt.isBefore(it.ended!!.endedAt) || it.review?.let {v.startedAt.isBefore(CollectionCareCheckDao[it.checkId].content.checkedAt)}==true})E.fail("须先登记前段实际结束并核对现状，后段实际开始不能早于前段结束")
        if(own.any {it.started?.kind==v.kind && it.started?.segmentIndex==v.segmentIndex})E.fail("本段已记录实际开始，不能覆盖或重复计次")
        if(E.records(id).any {it.started!=null && it.ended==null})E.fail("已有实际展出或运输尚未结束，不能同时实施")
        val segment=A.segments(p).find {it.kind==v.kind && it.segmentIndex==v.segmentIndex && !it.cancelled} ?: E.fail("该段不存在或已核实取消，不能登记实际开始")
        if(segment.authorizedAt==null || v.startedAt.isBefore(segment.authorizedAt))E.fail("须在本段实际使用变更共同确认之后实施，不能补造事前批准")
        if(v.startedAt.isBefore(segment.from) || !v.startedAt.isBefore(segment.to))E.fail("实际开始须处于本次已确认的使用段时限内，超出计划的已发生事实应据实补录并处理")
        val environment=CollectionFragileEnvironmentEvidence.verify(c.orgId,p.content.assessmentId,E.current(c).content,p.content,v,environmentProvider())
        val r=CollectionFragileUseRecordDao.new {orgId=c.orgId;collectionId=id;this.planId=planId;authorizationAdjustmentId=segment.adjustmentId;started=v}
        r.environmentArchiveId=archives.seal(c.orgId,E.BIZ,r.id.value,"ENVIRONMENT_VERIFIED",environment,(listOf(v.signedFileIds)+v.environmentReadings.map {it.signedFileIds}).joinToString(","))
        archives.seal(c.orgId,E.BIZ,r.id.value,"STARTED",E.startValue(r),v.signedFileIds)
        E.budget(c,E.current(c),p,s.handedAt)
        CollectionFragileMonitorRuntimeDao.new(r.id.value) {orgId=c.orgId;collectionId=id;nextAttemptAt=LocalDateTime.now()}
        return CollectionFragilityAccess.finish(scope,c,r.id.value,expectedOrgId)
    }
    fun end(id:String,recordId:String,v:FragileUseEndVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val r=E.records(id).find {it.id.value==recordId && it.orgId==c.orgId} ?: E.fail("实际使用记录不存在")
        E.record(r);r.ended?.let {if(it==v)return recordId;E.fail("实际结束原件不能替换")}
        val start=r.started ?: E.fail("历史补录不能改为当时的实际开始授权")
        actual(v.endedAt);required(v.actualLocation,v.actualResult,v.signedFileIds)
        if(v.endedAt.isBefore(start.startedAt))E.fail("实际结束不能早于开始")
        if(SecurityUtils.getUserId()!=start.operatorUserId)E.fail("须由实际操作人员本人登记结束")
        r.ended=v;archives.seal(c.orgId,E.BIZ,recordId,"ENDED",v,v.signedFileIds)
        CollectionFragileMonitorRuntimeDao.findById(recordId)?.let {it.completed=false;it.nextAttemptAt=LocalDateTime.now()}
        return CollectionFragilityAccess.finish(scope,c,recordId,expectedOrgId)
    }
    fun review(id:String,recordId:String,v:FragileUseReviewVo,expectedOrgId:String?=null):String {
        val c=collection(id,true,expectedOrgId);val r=E.records(id).find {it.id.value==recordId && it.orgId==c.orgId} ?: E.fail("实际使用记录不存在")
        E.record(r);r.review?.let {if(it==v)return recordId;E.fail("本次使用后检查原件不能覆盖，后续检查须另行登记")}
        val end=r.ended ?: E.fail("须先据实登记实际结束，再核对现状检查")
        val check=CollectionCareCheckDao.findById(v.checkId) ?: E.fail("实际现状检查不存在")
        if(check.collectionId!=id || check.orgId!=c.orgId || check.content.checkedAt.isBefore(end.endedAt))E.fail("须关联本馆本件使用结束后的实际检查")
        CollectionCareEvidence.check(check);required(v.signedFileIds)
        val actor=SecurityUtils.getUserId() ?: E.fail("请登录后核对")
        users.requireMuseumMember(actor,c.orgId);users.requireRole(actor,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        r.review=v;archives.seal(c.orgId,E.BIZ,recordId,"REVIEWED",mapOf("review" to v,"check" to CollectionCareEvidence.checkValue(check)),v.signedFileIds);return CollectionFragilityAccess.finish(scope,c,recordId,expectedOrgId)
    }
}

/** 生产环境通过既有操作锁统一注入，覆盖提交、实际出库及 RFID 共用执行路径。 */
@Component
class CollectionFragilityGuard(private val users:CollectionResponsibleUserService):CollectionOperationSafetyGuard {
    override fun requireAllowed(collectionId:String,bizType:String?,bizId:String?) {
        if(bizType in setOf(CollectionConst.BIZ_RETURN,CollectionConst.BIZ_INVENTORY,CollectionConst.BIZ_CHANGE))return
        val lock=if(bizType==null)CollectionOperationLockDao.find {CollectionOperationLocks.collectionId eq collectionId}.singleOrNull() else null
        val type=bizType ?: lock?.bizType;val id=bizId ?: lock?.bizId
        if(type !in setOf(CollectionConst.BIZ_REQUISITION,CollectionConst.BIZ_LOAN) || id==null)return
        val c=CollectionDao.findById(collectionId) ?: E.fail("藏品不存在")
        if(E.source(type!!,id)==null)return
        val a=E.current(c)
        users.requireRole(a.content.conservationUserId,c.orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireRole(a.content.keeperUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        E.requireUse(c,type,id)
    }
}
