package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.service.*
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.dao.entityCache
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class CollectionSamplingService(
    flow:FlowInstanceService,
    private val scope:CollectionScopeResolver,
    private val users:CollectionResponsibleUserService,
    private val archives:CollectionPermanentArchiveService,
    private val originals:CollectionOriginalAccessService,
    private val locks:CollectionOperationLockService,
    private val incidents:CollectionIncidentService?=null,
    private val directory:CollectionActorDirectory,
):FlowService<CollectionSamplingDao>(CollectionSamplingDao,flow,CollectionConst.CACHE) {
    override fun businessType()=CollectionConst.BIZ_SAMPLING
    override fun definitionKey()=CollectionConst.FLOW_SAMPLING
    override fun businessTypeName()="有损取样"
    override fun todoListRoute()="/collection/sampling"
    override fun buildTitle(entity:CollectionSamplingDao)=entity.title

    private fun access(orgId:String,write:Boolean=false) {
        val user=directory.requireActive(SecurityUtils.getUserDetail().id)
        requireCurrentPermission(user,write)
        OrgScopedReadPolicy.requireVisible(if(write)scope.currentWritableOrgIds(user) else scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG),orgId,"有损取样")
        val ids=if(!scope.isMulti())setOf(scope.currentOrgId()) else if(write)scope.writableOrgIds() else scope.readableOrgIds()
        OrgScopedReadPolicy.requireVisible(ids,orgId,"有损取样")
        if(write) {
            val org=CollectionOrgDao.findById(orgId) ?: fail("取样所属收藏单位不存在")
            org.refresh(flush=false)
            if(org.status!=EnumContentStatus.NORMAL || org.orgStatus!=EnumOrgStatus.ENABLED)fail("取样所属收藏单位已停用")
        }
    }
    private fun requireCurrentPermission(user:SecurityUser,write:Boolean) {
        if(EnumRole.ADMIN.value !in user.roles && user.permissions.none { it in setOf(CollectionConst.ADMIN,CollectionConst.SAMPLING) } && (write || user.roleCodes.none { it in setOf(CollectionConst.ROLE_CONSERVATION,CollectionConst.ROLE_STORAGE_HEAD) }))fail("缺少当前有损取样办理权限")
    }
    private fun root(id:String,write:Boolean=false):CollectionSamplingDao {
        val r=(if(write)CollectionSamplingDao.find { CollectionSamplings.id eq id }.forUpdate().singleOrNull() else CollectionSamplingDao.findById(id)) ?: fail("有损取样记录不存在")
        if(write){TransactionManager.current().entityCache.flush();r.refresh(flush=false)}
        access(r.orgId,write);return r
    }
    /** 实际 SQL 后核对当前资格和原案卷身份，失败时由业务事务整体回滚。 */
    private fun writerVerifier(r:CollectionSamplingDao,actorId:String=SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理有损取样")):()->Unit {
        val identity=listOf(r.orgId,r.collectionId,r.title,r.createBy)
        return {
            TransactionManager.current().entityCache.flush()
            if(SecurityUtils.getNullUserDetail()?.id!=actorId)fail("写入期间取样办理账号已变化，请重新登录")
            r.refresh(flush=false)
            if(listOf(r.orgId,r.collectionId,r.title,r.createBy)!=identity)fail("写入期间取样案卷身份已变化，请刷新核实")
            access(r.orgId,true)
        }
    }
    private fun lockAndRefreshCollection(r:CollectionSamplingDao):CollectionDao {
        TransactionManager.current().entityCache.flush()
        val c=CollectionDao.find { Collections.id eq r.collectionId }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        c.refresh(flush=false)
        return c
    }
    /** 方案、许可和实际取样依赖当前藏品；历史结束与样品保管仍按冻结原件核验。 */
    private fun currentSourceVerifier(r:CollectionSamplingDao):()->Unit {
        val c=lockAndRefreshCollection(r)
        val expected=source(r)
        return {
            TransactionManager.current().entityCache.flush()
            c.refresh(flush=false)
            if(source(r)!=expected)fail("写入期间藏品或取样基线已变化，须重新核实")
        }
    }
    // 只记住本次新增办理实际使用的任职核验；历史幂等返回不会增加旧参与人的新资格要求。
    private val participantChecks=ThreadLocal<LinkedHashMap<List<String>,()->Unit>>()
    private fun requireParticipant(key:List<String>,verify:()->Unit) {
        verify()
        participantChecks.get()?.put(key,verify)
    }
    private fun requireMuseumMember(userId:String,orgId:String)=requireParticipant(listOf("member",userId,orgId)) {
        users.requireMuseumMember(userId,orgId)
    }
    private fun requireDepartmentMember(userId:String,orgId:String,departmentId:String)=requireParticipant(listOf("department",userId,orgId,departmentId)) {
        users.requireDepartmentMember(userId,orgId,departmentId)
    }
    private fun requireRole(userId:String,orgId:String,role:String)=requireParticipant(listOf("role",userId,orgId,role)) {
        users.requireRole(userId,orgId,role)
    }
    private fun <T> verifyParticipantsAfterWrite(block:()->T):T {
        val previous=participantChecks.get()
        val checks=linkedMapOf<List<String>,()->Unit>()
        participantChecks.set(checks)
        try {
            val result=block()
            TransactionManager.current().entityCache.flush()
            checks.values.forEach {it()}
            previous?.putAll(checks)
            return result
        } finally {
            if(previous==null)participantChecks.remove() else participantChecks.set(previous)
        }
    }
    private fun <T> write(id:String,lockCollection:Boolean=false,currentSource:Boolean=false,block:(CollectionSamplingDao)->T):T = verifyParticipantsAfterWrite {
        val actorId=SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理有损取样")
        val r=root(id,true)
        val verify=writerVerifier(r,actorId)
        val verifySource=if(currentSource)currentSourceVerifier(r) else ({})
        if(lockCollection && !currentSource)lockAndRefreshCollection(r)
        verify()
        val result=block(r)
        verify()
        verifySource()
        result
    }
    private fun plans(r:CollectionSamplingDao)=CollectionSamplingPlanDao.find { CollectionSamplingPlans.samplingId eq r.id.value }.sortedBy { it.version }
    private fun latest(r:CollectionSamplingDao)=plans(r).lastOrNull() ?: fail("请先登记保护修复部门会同保管部门制定的实际取样方案")
    private fun samples(p:CollectionSamplingPlanDao)=CollectionSampleDao.find { CollectionSamples.planId eq p.id.value }.sortedBy { it.content.takenAt }
    private fun permits(p:CollectionSamplingPlanDao)=CollectionAdministrativeRecordDao.find { (CollectionAdministrativeRecords.bizType eq businessType()) and (CollectionAdministrativeRecords.bizId eq p.id.value) }.toList()
    private fun collection(r:CollectionSamplingDao)=(CollectionDao.findById(r.collectionId) ?: fail("藏品不存在")).also { if(it.orgId!=r.orgId)fail("藏品所属馆已变化") }
    private fun source(r:CollectionSamplingDao, legacyDefaults:Boolean=false):String {
        val base=mapOf("collectionId" to r.collectionId,"title" to r.title,"catalog" to (if(legacyDefaults) CollectionCatalogMapper.legacyUnknownDefaults(collection(r).toModel()) else CollectionCatalogMapper.fromCollection(collection(r).toModel())).copy(warehouseId=null))
        return json(base + (predecessor(r)?.let {mapOf("predecessor" to it)} ?: emptyMap()))
    }
    private fun snapshot(p:CollectionSamplingPlanDao)=mapOf("samplingId" to p.samplingId,"version" to p.version,"sourceSnapshot" to p.sourceSnapshot,"authority" to p.authority,"content" to p.content)
    private fun phase(p:CollectionSamplingPlanDao,key:String,expected:Any) {
        val a=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq businessType()) and (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase eq key) }.singleOrNull() ?: fail("缺少本轮取样永久原件：$key")
        if(a.orgId!=p.orgId || a.digest!=digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(json(expected)))fail("取样记录与永久原件不一致")
        archives.phaseForAuthorizedOrg(businessType(),p.id.value,p.orgId,key) ?: fail("缺少本轮取样永久原件：$key")
    }
    private fun seal(p:CollectionSamplingPlanDao,key:String,value:Any,files:String?=null)=archives.seal(p.orgId,businessType(),p.id.value,key,value,files)
    private fun recorded(r:CollectionSamplingDao):CollectionSamplingPlanDao=latest(r).also { TransactionManager.current().entityCache.flush();it.refresh(flush=false);if(it.orgId!=r.orgId)fail("方案所属馆不一致");phase(it,"PLAN",snapshot(it));verifyContinuationStart(it) }
    private fun prepared(r:CollectionSamplingDao):CollectionSamplingPlanDao=recorded(r).also { if(!CollectionCatalogBaselineComparison.sameSource(it.sourceSnapshot,source(r)))fail("藏品或取样基线已变化，须重新核实") }
    private fun notStarted(r:CollectionSamplingDao) { if(plans(r).any { it.started!=null })fail("已实际开始取样，不能覆盖、撤销或重启原方案") }

    override fun authorizeUserOperation(entity:CollectionSamplingDao,operation:FlowUserOperation) {
        scope.requireFlowAccess(entity.orgId,operation,"有损取样");access(entity.orgId,operation!=FlowUserOperation.FLOW_INFO)
        if(operation!=FlowUserOperation.FLOW_INFO)notStarted(entity)
        if(operation==FlowUserOperation.SAVE_DRAFT && entity.flowInstanceId!=null)fail("已进入办理的取样原件不能恢复为可编辑草稿")
    }
    override fun userOperationVerifier(entity:CollectionSamplingDao,operation:FlowUserOperation):()->Unit {
        if(operation==FlowUserOperation.FLOW_INFO)return {}
        val verify=writerVerifier(entity)
        val verifySource=if(operation==FlowUserOperation.SUBMIT)currentSourceVerifier(entity) else ({})
        // 获取藏品锁可能等待；取得锁后重新核实目录中的账号、权限及所属馆。
        verify()
        return {
            verify();verifySource()
            if(operation==FlowUserOperation.SUBMIT)requirePeople(entity,prepared(entity))
        }
    }
    override fun buildFormData(entity:CollectionSamplingDao):Map<String,Any?> {
        access(entity.orgId)
        return mapOf("id" to entity.id.value,"collectionId" to entity.collectionId,"title" to entity.title)+plans(entity).lastOrNull()?.let {
            mapOf("planId" to it.id.value,"plan" to it.content,"sourceSnapshot" to it.sourceSnapshot,
                "requiredAuthority" to it.authority,"administrative" to permits(it).map { a->a.content.copy(id=a.id.value) },
                "archives" to archives.list(businessType(),it.id.value))
        }.orEmpty()
    }
    override fun buildSelfSelectAssignees(entity:CollectionSamplingDao)=actors(entity).mapValues { listOf(it.value) }
    private fun actors(r:CollectionSamplingDao)=latest(r).content.let { linkedMapOf("conservation" to it.conservationUserId,"keeper" to it.keeperUserId) }
    private fun requirePeople(r:CollectionSamplingDao,p:CollectionSamplingPlanDao) {
        val v=p.content
        requireDepartmentMember(v.conservationUserId,r.orgId,v.conservationDepartmentId)
        requireRole(v.conservationUserId,r.orgId,CollectionConst.ROLE_CONSERVATION)
        requireMuseumMember(v.keeperUserId,r.orgId);requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
    }
    override fun beforeSubmit(entity:CollectionSamplingDao) {
        val row=CollectionSamplings.selectAll().where { CollectionSamplings.id eq entity.id.value }.forUpdate().single()
        if(row[CollectionSamplings.flowStatus] !in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.REJECT,EnumFlowStatus.CANCEL) || row[CollectionSamplings.collectionId]!=entity.collectionId || row[CollectionSamplings.title]!=entity.title)fail("取样申请状态或方案已变化，请重新核实")
        verifyParticipantsAfterWrite {
            notStarted(entity);val p=prepared(entity);requirePeople(entity,p);seal(p,"SUBMITTED",snapshot(p))
        }
    }
    override fun acquireApprovalResources(entity:CollectionSamplingDao) { CollectionDao.find { Collections.id eq entity.collectionId }.forUpdate().single();locks.acquire(entity.orgId,businessType(),entity.id.value,listOf(entity.collectionId)) }
    override fun releaseApprovalResources(entity:CollectionSamplingDao) { locks.release(businessType(),entity.id.value) }
    override fun onFlowTaskCompleted(entity:CollectionSamplingDao,event:FlowTaskCompletedEvent) {
        if(event.action!="APPROVE")return
        val expected=actors(entity)[event.nodeKey] ?: fail("取样流程确认节点不符合共同制定要求")
        if(event.operator!=expected || event.comment.isNullOrBlank())fail("指定保护修复或保管人员须本人核实并填写意见")
        notStarted(entity);requirePeople(entity,prepared(entity))
    }
    private fun approvals(r:CollectionSamplingDao):List<FlowInstanceLogDao> {
        val id=r.flowInstanceId ?: fail("缺少保护修复和保管共同确认记录")
        val all=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(id)
        val result=actors(r).map { (node,user)->all.lastOrNull { it.nodeKey==node && it.operator==user && it.taskId!=null && !it.comment.isNullOrBlank() } ?: fail("缺少本轮共同方案实际确认，旧通过状态不能代替") }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(result)
        return result
    }
    override fun onFlowCompleted(entity:CollectionSamplingDao,event:FlowCompletedEvent) {
        verifyParticipantsAfterWrite {
            notStarted(entity);val p=prepared(entity);requirePeople(entity,p);phase(p,"SUBMITTED",snapshot(p));val logs=approvals(entity)
            seal(p,"APPROVED_${logs.last().taskId!!.takeLast(40)}",logs.map { it.toVo() });super.onFlowCompleted(entity,event)
        }
    }
    override fun beforeFlowReentry(entity: CollectionSamplingDao, targetNodeKey: String) {
        notStarted(entity)
        if (targetNodeKey != "conservation") fail("重新确认必须从保护修复部门开始")
        requirePeople(entity, prepared(entity))
    }
    override fun onFlowCanceled(entity:CollectionSamplingDao,event:FlowCanceledEvent) { notStarted(entity);super.onFlowCanceled(entity,event) }
    override fun onFlowRejected(entity:CollectionSamplingDao,event:FlowRejectedEvent) { notStarted(entity);super.onFlowRejected(entity,event) }
    override fun onFlowJumped(entity:CollectionSamplingDao,event:FlowJumpedEvent) { notStarted(entity);super.onFlowJumped(entity,event) }

    fun create(collectionId:String,title:String):String {
        val actorId=SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理有损取样")
        val c=CollectionDao.find { Collections.id eq collectionId }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        TransactionManager.current().entityCache.flush();c.refresh(flush=false)
        val orgId=c.orgId;access(orgId,true)
        if(title.isBlank() || title.length>500)fail("请填写取样项目名称")
        CollectionOutboundPolicy.requireInStore(c.lifeStatus,"有损取样申请",c.inventoryHold)
        val r=CollectionSamplingDao.new { this.orgId=orgId;this.collectionId=collectionId;this.title=title;createBy=actorId }
        writerVerifier(r,actorId)()
        c.refresh(flush=false)
        if(c.orgId!=orgId)fail("写入期间藏品所属馆已变化，请刷新核实")
        return r.id.value
    }
    private fun continuationPhase(id:String)="CONTINUATION_$id"
    /** 只核验已冻结的前案结案依据；后续保管不会改写这里的历史版本。 */
    private fun closedBasis(p:CollectionSamplingPlanDao):Pair<LocalDateTime,String> {
        phase(p,"PLAN",snapshot(p));verifyContinuationStart(p)
        val started=p.started ?: fail("须先据实办理前案开始、结束和结案")
        val ended=p.ended ?: fail("前案尚未结束取样")
        phase(p,"STARTED",started);phase(p,"EXTRACTION_ENDED",ended)
        val states:Map<String,SamplingSampleJournalVo>
        val closedAt:LocalDateTime
        val closure:Any
        if(samples(p).isEmpty()) {
            val n=noSampleState(p);closedAt=n.closedAt ?: fail("须先共同确认未取得样品结案")
            if(!n.reviewCurrent)fail("未取得样品结案依据已失效")
            states=emptyMap();closure=mapOf("noSampleRevision" to n.revision)
        } else {
            states=completedSamples(p)
            if(p.completed!=null){closedAt=p.completed!!.completedAt;closure=p.completed!!}
            else {val stop=stoppedState(p,states);closedAt=stop.closedAt ?: fail("须先据实停作结案");closure=mapOf("stoppedRevision" to stop.revision)}
        }
        if(closedAt.isBefore(ended.endedAt))fail("前案结案时间早于实际取样结束")
        val administrative=permits(p).onEach {phase(p,"ADMIN_${it.id.value}",it.content)}.sortedBy {it.id.value}.map {it.content}
        return closedAt to digest(json(mapOf("plan" to snapshot(p),"started" to started,"ended" to ended,
            "clearance" to p.incidentClearance,"closure" to closure,"administrative" to administrative,
            "samples" to states.toSortedMap().mapValues {it.value.revision})))
    }
    private fun readContinuation(a:CollectionPermanentArchiveDao):SamplingContinuationVo {
        val v=try {JsonUtils.fromJson(a.snapshot,SamplingContinuationVo::class.java)!!} catch(_:Exception){fail("变更后续申请关联原件无法读取")}
        val p=CollectionSamplingPlanDao.findById(v.request.predecessorPlanId) ?: fail("缺少前案方案")
        val parent=CollectionSamplingDao.findById(v.predecessorId) ?: fail("缺少前案申请")
        val child=CollectionSamplingDao.findById(v.successorId) ?: fail("缺少变更后续申请")
        if(a.bizId!=p.id.value || a.phase!=continuationPhase(v.successorId) || p.samplingId!=v.predecessorId ||
            p.orgId!=parent.orgId || child.orgId!=parent.orgId || parent.collectionId!=v.collectionId || child.collectionId!=v.collectionId ||
            child.title!=v.request.title || v.predecessorId==v.successorId)fail("前后案卷所属馆、藏品或关联身份不一致")
        phase(p,a.phase,v)
        val closed=closedBasis(p)
        if(v.closedRevision!=closed.second || v.request.requestedAt.isBefore(closed.first))fail("前案结案原件与变更依据不一致")
        return v
    }
    private fun predecessor(r:CollectionSamplingDao):SamplingContinuationVo? {
        val all=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq businessType()) and
            (CollectionPermanentArchives.phase eq continuationPhase(r.id.value))}.toList()
        if(all.size>1)fail("后续申请存在多个前案关联，请核实原件")
        return all.singleOrNull()?.let(::readContinuation)
    }
    private fun successor(p:CollectionSamplingPlanDao):SamplingContinuationVo? {
        val all=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq businessType()) and
            (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase like "CONTINUATION_%")}.toList()
        if(all.size>1)fail("前案存在重复后续申请，请核实原件")
        return all.singleOrNull()?.let(::readContinuation)
    }
    private fun continuationSnapshot(p:CollectionSamplingPlanDao):String {
        val closed=closedBasis(p)
        val current=samples(p).associate {s->s.id.value to custodyJournal(p,s)}
        return json(mapOf("closedRevision" to closed.second,"closedAt" to closed.first,"planId" to p.id.value,
            "plan" to snapshot(p),"sampleFacts" to samples(p).associate {it.id.value to it.content}.toSortedMap(),
            "currentSamples" to current.toSortedMap()))
    }
    private fun continuationDetail(p:CollectionSamplingPlanDao):Map<String,Any?> {
        verifyContinuationStart(p)
        val next=runCatching {successor(p)}
        val closed=p.completed!=null || noSampleState(p).closedAt!=null || hasStoppedRecords(p) && stoppedState(p).closedAt!=null
        val basis=if(closed)runCatching {continuationSnapshot(p)} else null
        return mapOf("successor" to next.getOrNull(),"continuationBasisRevision" to basis?.getOrNull()?.let(::digest),
            "continuationIssue" to (next.exceptionOrNull()?.message ?: basis?.exceptionOrNull()?.message))
    }
    /** 后续取样实际开始时重新锁定前案并核查现存问题；保留登记当时和开始当时两份依据。 */
    private fun predecessorAtStart(r:CollectionSamplingDao,startedAt:LocalDateTime):SamplingContinuationStartVo? {
        val link=predecessor(r) ?: return null
        val parent=root(link.predecessorId,true);val p=recorded(parent)
        if(p.id.value!=link.request.predecessorPlanId)fail("前案方案身份发生变化")
        val current=samples(p).map {custodyJournal(p,it)}
        if(current.any {!canStopSample(it)})fail("前案现存样品出现未结差异、待确认处置或外部剩余，须先核实后再开始新增原件取样")
        current.flatMap {if(it.portions.isEmpty())listOf(it.remainingQuantity to it.custody) else it.portions.values.map {v->v.remainingQuantity to v.custody}}
            .filter {it.first>BigDecimal.ZERO}.forEach {requireMuseumMember(it.second.userId ?: fail("前案剩余样品缺少当前保管人员"),r.orgId)}
        val latestAt=maxOf(link.request.requestedAt,requireSafeEnd(parent,p),current.maxOfOrNull {it.lastOccurredAt} ?: link.request.requestedAt)
        if(startedAt.isBefore(latestAt))fail("后续实际开始时间须覆盖前案最新安全与样品保管核实依据")
        return SamplingContinuationStartVo(link,continuationSnapshot(p),startedAt)
    }
    private fun verifyContinuationStart(p:CollectionSamplingPlanDao) {
        val started=p.started ?: return
        val link=predecessor(CollectionSamplingDao[p.samplingId]) ?: return
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq businessType()) and
            (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase eq "PREDECESSOR_AT_START")}.singleOrNull()
            ?: fail("缺少变更后续取样实际开始时的前案核查原件")
        val v=try {JsonUtils.fromJson(a.snapshot,SamplingContinuationStartVo::class.java)!!} catch(_:Exception){fail("后续取样开始核查原件无法读取")}
        phase(p,"PREDECESSOR_AT_START",v)
        if(v.predecessor!=link || v.startedAt!=started.startedAt)fail("后续取样开始核查与实际开始依据不一致")
    }
    fun continueExtraction(id:String,v:SamplingContinuationRequestVo):String = write(id) {r ->
        val p=recorded(r)
        successor(p)?.let {if(it.request==v)return@write it.successorId;fail("已有变更后续申请，请在该申请办理；前案不能重复创建并行实施")}
        val closed=closedBasis(p);val prior=continuationSnapshot(p)
        if(v.predecessorPlanId!=p.id.value || v.basisRevision!=digest(prior))fail("前案或现存样品依据已经变化，请重新核对")
        val current=samples(p).map {custodyJournal(p,it)}
        if(current.any {!canStopSample(it)})fail("须先核实前案样品差异、待确认处置及外部剩余保管")
        val latestAt=maxOf(closed.first,requireSafeEnd(r,p),current.maxOfOrNull {it.lastOccurredAt} ?: closed.first)
        if(v.requestedAt.isBefore(latestAt) || v.requestedAt.isAfter(LocalDateTime.now()) || v.title.length>500 ||
            listOf(v.title,v.changeReason,v.changedScope,v.previousWorkAndSamplesAssessment,v.cumulativeInterventionAssessment,v.changeBasisFileIds,v.assessmentFileIds).any(String::isBlank))
            fail("须据实填写变更时间、原因、后续取样范围、既有工作和样品能否替代、累计干预评估及原件")
        recorder(r.orgId,v.recorderUserId)
        val child=create(r.collectionId,v.title)
        val record=SamplingContinuationVo(id,child,r.collectionId,closed.second,prior,v)
        seal(p,continuationPhase(child),record,"${v.changeBasisFileIds},${v.assessmentFileIds}")
        return@write child
    }
    fun page(request:HttpServletRequest):GenericPage<Map<String,Any?>> {
        val user=directory.requireActive(SecurityUtils.getUserDetail().id)
        requireCurrentPermission(user,true)
        val q=CollectionSamplings.selectAll();val ids=if(scope.isMulti())scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG) else setOf(scope.currentOrgId())
        scope.currentReadableOrgIds(user,CollectionConst.CROSS_ORG)?.let { q.andWhere { CollectionSamplings.orgId inList it } }
        ids?.let { q.andWhere { CollectionSamplings.orgId inList it } }
        request.getParameter("keyword")?.trim()?.takeIf(String::isNotBlank)?.let { q.andWhere { (CollectionSamplings.title like "%$it%") or (CollectionSamplings.collectionId eq it) } }
        q.orderBy(CollectionSamplings.createTime to SortOrder.DESC)
        val page=GenericPage<CollectionSamplingDao>().build(request);val result=findPage(q,page)
        val out=GenericPage<Map<String,Any?>>().build(request);out.total=result.total;out.list=result.data?.map { summary(it) };return out
    }
    private fun summary(r:CollectionSamplingDao)=mapOf("id" to r.id.value,"collectionId" to r.collectionId,"title" to r.title,"flowStatus" to r.flowStatus,"flowInstanceId" to r.flowInstanceId,"createTime" to r.createTime)
    fun detail(id:String):Map<String,Any?> {
        val r=root(id);val src=runCatching { source(r) };val prior=runCatching { predecessor(r) }
        val priorOriginal=prior.getOrNull()?.let { link->runCatching {
            val verified=archives.phaseForAuthorizedOrg(businessType(),link.request.predecessorPlanId,r.orgId,continuationPhase(r.id.value))
                ?: fail("前案变更后续取样原件不存在")
            mapOf("id" to verified["id"],"phase" to verified["phase"],"files" to verified["files"])
        } }
        return summary(r)+mapOf("predecessor" to prior.getOrNull(),"predecessorIssue" to prior.exceptionOrNull()?.message,
            "predecessorOriginal" to priorOriginal?.getOrNull(),"predecessorOriginalIssue" to priorOriginal?.exceptionOrNull()?.message,
            "sourceRevision" to src.getOrNull()?.let(::digest),"sourceIssue" to src.exceptionOrNull()?.message,"plans" to plans(r).map { p->
            val inspected=archives.inspectForAuthorizedOrg(businessType(),p.id.value,r.orgId)
            val stopped=runCatching {stoppedState(p)}
            val completionBasis=if(p.completed!=null || stopped.getOrNull()?.closedAt!=null)runCatching {completedSamples(p)} else null
            noSampleDetail(p)+runCatching {continuationDetail(p)}.getOrElse {mapOf("continuationIssue" to it.message)}+mapOf("id" to p.id.value,"version" to p.version,"authority" to p.authority,"content" to p.content,"started" to p.started,"ended" to p.ended,"completed" to p.completed,
                "stoppedClosure" to stopped.getOrNull(),"stoppedClosureIssue" to stopped.exceptionOrNull()?.message,
                "incidentClearance" to p.incidentClearance,"administrative" to permits(p).map { it.content.copy(id=it.id.value) },
                "samples" to samples(p).map { sampleDetail(p,it,completionBasis) },"archives" to inspected.archives,"archiveIssues" to inspected.issues) })
    }
    /** 只从本案某版已核验的永久档案读取原件，不能凭通用文件号绕开案卷职责。 */
    fun requireOriginal(id:String,archiveId:String,fileId:String) {
        val r=root(id)
        if(!fileId.matches(Regex("[0-9]{15,20}")))fail("取样原件文件标识不正确")
        val archive=CollectionPermanentArchiveDao.findById(archiveId)
            ?.takeIf { it.orgId==r.orgId && it.bizType==businessType() }
            ?: fail("本馆取样档案不存在")
        val p=CollectionSamplingPlanDao.findById(archive.bizId)
            ?.takeIf { it.orgId==r.orgId && it.samplingId==id }
            ?: fail("取样档案不属于本案方案")
        val verified=archives.listForAuthorizedOrg(businessType(),p.id.value,r.orgId)
            .singleOrNull { it["id"]==archiveId } ?: fail("取样冻结档案不存在")
        @Suppress("UNCHECKED_CAST")
        val files=verified["files"] as List<Map<String,Any?>>
        if(files.none { it["fileId"]==fileId })fail("该文件不是本案所选档案的归档原件")
    }
    fun savePlan(id:String,v:SamplingPlanVo):String = write(id,currentSource=true) {r ->
        notStarted(r)
        if(r.flowStatus!=EnumFlowStatus.DRAFT || r.flowInstanceId!=null)fail("方案已进入办理，不能覆盖共同原件")
        val c=collection(r);val src=source(r)
        predecessor(r)?.let {if(v.preparedAt.isBefore(it.request.requestedAt))fail("变更方案须在实际登记前案与变更依据之后形成，共同确认与批准须重新办理")}
        if(v.sourceRevision!=digest(src))fail("藏品基线已经变化，请重新核实")
        if(!c.registered || c.totalRegNo.isBlank() || c.catalogStatus!=EnumCatalogStatus.APPROVED || c.nature==null || (CollectionNaturePolicy.usesRelicRules(c.nature) && c.level in setOf(null,EnumRelicLevel.UNRATED)))fail("须先完成建账建档并依法核实藏品性质和级别")
        if(v.preparedAt.isAfter(LocalDateTime.now()) || v.plannedStart.isBefore(v.preparedAt) || !v.plannedEnd.isAfter(v.plannedStart))fail("方案形成与计划取样时段不合理")
        if(listOf(v.specialNeed,v.nonDestructiveAlternatives,v.alternativesFileIds,v.substituteSearchScope,v.substituteSearchFileIds,v.indispensabilityReason,v.location,v.laboratoryName,v.analysisMethod,v.remainingSamplePlan,v.safetyAndAftercarePlan,v.conditionFileIds,v.signedJointPlanFileIds).any(String::isBlank) || !v.originalIndispensable)fail("须证明保护或研究的特殊需要、非有损方法及替代评估、最小干预和原件取样不可替代性")
        if(v.conservationUserId==v.keeperUserId)fail("保护修复部门与保管部门须分别履行共同制定职责")
        val firstClass=CollectionNaturePolicy.usesRelicRules(c.nature) && c.level==EnumRelicLevel.FIRST
        if(v.alternatives.any { listOf(it.description,it.assessment,it.fileIds).any(String::isBlank) })fail("替代藏品调查、适用性评估和实际原件必须完整")
        if(firstClass && v.alternatives.any { it.canReplace })fail("一级文物存在可替代对象，应改用替代方案，不能继续原件有损取样")
        if(firstClass && v.alternatives.isEmpty())fail("一级文物一般不予取样，必须留存具体替代对象或未找到对象的调查评估原件")
        if(v.points.isEmpty() || v.points.map { it.key }.distinct().size!=v.points.size || v.points.any { it.key.isBlank() || it.key.length>64 || it.maximumQuantity<=BigDecimal.ZERO || it.maximumCount<=0 || listOf(it.location,it.locationFileIds,it.material,it.method,it.minimumInterventionReason,it.protectionMeasures).any(String::isBlank) })fail("每个取样部位须有唯一标识、定位依据、方法、最小干预理由、明确数量单位和份数上限")
        val old=plans(r).lastOrNull()
        if(old?.content==v && old.sourceSnapshot==src){phase(old,"PLAN",snapshot(old));return@write old.id.value}
        if(old!=null && (v.preparedAt.isBefore(old.content.preparedAt) || v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank()))fail("订正方案须追加原因和原件，旧版永久保留")
        val p=CollectionSamplingPlanDao.new {orgId=r.orgId;samplingId=id;version=(old?.version ?: 0)+1;sourceSnapshot=src;authority=if(CollectionNaturePolicy.usesRelicRules(c.nature) && c.level==EnumRelicLevel.FIRST)EnumAuthorityLevel.NATIONAL else EnumAuthorityLevel.PROVINCE;content=v}
        requirePeople(r,p)
        seal(p,"PLAN",snapshot(p),(listOfNotNull(v.alternativesFileIds,v.substituteSearchFileIds,v.conditionFileIds,v.signedJointPlanFileIds,v.replacementFileIds)+v.alternatives.map { it.fileIds }+v.points.map { it.locationFileIds }).joinToString(","));return@write p.id.value
    }
    fun administrative(id:String,planId:String,v:CollectionAdministrativeEvidenceVo):String = write(id,currentSource=true) {r ->
        notStarted(r);val p=prepared(r)
        if(p.id.value!=planId || v.action!=EnumAdministrativeAction.APPROVAL || v.level!=p.authority || listOf(v.authorityName,v.documentNo,v.fileIds).any(String::isBlank) || v.issuedAt.isBefore(p.content.preparedAt.toLocalDate()) || v.issuedAt.isAfter(LocalDate.now()))fail("须登记本方案适用层级的真实批准机关、文号、日期及原件")
        permits(p).find { it.content==v }?.let { phase(p,"ADMIN_${it.id.value}",it.content);return@write it.id.value }
        val a=CollectionAdministrativeRecordDao.new {orgId=r.orgId;bizType=businessType();bizId=p.id.value;content=v};seal(p,"ADMIN_${a.id.value}",v,v.fileIds);return@write a.id.value
    }
    fun start(id:String,v:SamplingStartVo):String = write(id,currentSource=true) {r ->
        val p=prepared(r)
        p.started?.let { phase(p,"STARTED",it);if(it==v)return@write p.id.value;fail("实际开始原件不能覆盖") }
        val priorAtStart=predecessorAtStart(r,v.startedAt)
        val logs=approvals(r);phase(p,"SUBMITTED",snapshot(p));phase(p,"APPROVED_${logs.last().taskId!!.takeLast(40)}",logs.map { it.toVo() });requirePeople(r,p)
        val a=permits(p);a.forEach { if(it.orgId!=r.orgId)fail("批文所属馆不一致");phase(p,"ADMIN_${it.id.value}",it.content) }
        if(a.none { it.content.level==p.authority && it.content.action==EnumAdministrativeAction.APPROVAL && !it.content.issuedAt.isAfter(v.startedAt.toLocalDate()) })fail("缺少实际开始前本方案所需行政批文，馆内确认不能替代许可")
        if(r.flowStatus!=EnumFlowStatus.PASS || v.planId!=p.id.value || v.startedAt.isBefore(logs.last().createTime) || v.startedAt.isBefore(p.content.plannedStart) || v.startedAt.isAfter(p.content.plannedEnd) || v.startedAt.isAfter(LocalDateTime.now()) || !v.originalSuitable || !v.siteSafe || listOf(v.safetyChecks,v.safetyFileIds,v.signedStartFileIds).any(String::isBlank))fail("须在共同确认、行政批准及方案时段内，核实原件和场所安全后据实开始")
        requireMuseumMember(v.operatorUserId,r.orgId);originals.requireAccess(r.orgId,r.collectionId,businessType(),id,v.startedAt,v.operatorUserId,v.requisitionId,v.onSiteWarehouseLogId)
        locks.acquire(r.orgId,businessType(),id,listOf(r.collectionId));priorAtStart?.let {seal(p,"PREDECESSOR_AT_START",it)};p.started=v;seal(p,"STARTED",v,"${v.safetyFileIds},${v.signedStartFileIds}");return@write p.id.value
    }
    fun take(id:String,v:SamplingTakeVo):String = write(id,currentSource=true) {r ->
        val p=prepared(r);val started=p.started ?: fail("请先登记实际开始");phase(p,"STARTED",started)
        val all=samples(p);all.find { it.content==v }?.let { phase(p,"SAMPLE_${it.id.value}",it.content);return@write it.id.value }
        if(p.ended!=null || all.any { !it.content.withinApprovedScope || it.content.unexpectedDamage })fail("取样已结束或已有异常待处置，不能继续本轮操作")
        if(v.takenAt.isBefore(started.startedAt) || v.takenAt.isAfter(LocalDateTime.now()) || v.quantity<=BigDecimal.ZERO || v.number.isBlank() || v.number.length>100 || listOf(v.actualLocation,v.conditionAfter,v.actualMeasures,v.sampleLocation,v.markingFileIds,v.conditionFileIds,v.signedTakeFileIds).any(String::isBlank))fail("请完整登记实际取样时间、位置、数量、标识、现状、样品保管及签署原件")
        if(v.withinApprovedScope && !v.unexpectedDamage) {
            locks.requireUnlocked(r.collectionId,businessType(),id)
            if(collection(r).inventoryHold)fail("取样期间出现新的清点异常，须先核实；已经发生的异常须据实留存")
        }
        if(all.any {journal(p,it).openDiscrepancySequences.isNotEmpty()})fail("已有样品差异待核查，不能继续正常取样")
        val point=p.content.points.find { it.key==v.pointKey };val previous=all.filter { it.content.pointKey==v.pointKey }
        val permitted=point!=null && point.unit==v.unit && !v.takenAt.isAfter(p.content.plannedEnd) && previous.size<point.maximumCount && previous.fold(BigDecimal.ZERO){sum,row->sum+row.content.quantity}+v.quantity<=point.maximumQuantity
        if(v.withinApprovedScope && !permitted)fail("实际取样超出批准部位、数量、份数、单位或时段，不能登记为符合方案")
        if((!v.withinApprovedScope || v.unexpectedDamage) && (v.exceptionExplanation.isNullOrBlank() || v.exceptionFileIds.isNullOrBlank()))fail("异常取样或意外损伤必须如实说明并留存原件，不能写成正常取样")
        requireMuseumMember(v.operatorUserId,r.orgId);requireMuseumMember(v.custodianUserId,r.orgId)
        if(CollectionSampleDao.find { (CollectionSamples.orgId eq r.orgId) and (CollectionSamples.number eq v.number) }.any())fail("本馆样品编号已使用")
        all.forEach { phase(p,"SAMPLE_${it.id.value}",it.content) }
        val row=CollectionSampleDao.new {orgId=r.orgId;planId=p.id.value;number=v.number;content=v}
        seal(p,"SAMPLE_${row.id.value}",v,listOfNotNull(v.markingFileIds,v.conditionFileIds,v.signedTakeFileIds,v.exceptionFileIds).joinToString(","));return@write row.id.value
    }
    fun endExtraction(id:String,v:SamplingExtractionEndVo):String = write(id) {r ->
        val p=recorded(r);val started=p.started ?: fail("请先登记实际开始");phase(p,"STARTED",started)
        p.ended?.let {phase(p,"EXTRACTION_ENDED",it);if(it==v)return@write p.id.value;fail("实际取样结束原件不能覆盖")}
        val all=samples(p);requireJoint(r,p,v.conservationUserId,v.keeperUserId)
        if(v.endedAt.isBefore(started.startedAt) || v.endedAt.isAfter(LocalDateTime.now()) || all.any { it.content.takenAt.isAfter(v.endedAt) } || v.sampleIds!=all.map { it.id.value }.toSet() || listOf(v.conditionAfter,v.actualAftercare,v.conditionFileIds,v.signedEndFileIds).any(String::isBlank))fail("须共同核对全部实际样品、取样后现状、养护和真实结束时间")
        if(v.safeToReleaseOriginal && all.any { !it.content.withinApprovedScope || it.content.unexpectedDamage })fail("存在范围异常或意外损伤，不能按正常结束释放原件")
        all.forEach { phase(p,"SAMPLE_${it.id.value}",it.content) }
        p.ended=v;seal(p,"EXTRACTION_ENDED",v,"${v.conditionFileIds},${v.signedEndFileIds}")
        if(v.safeToReleaseOriginal)locks.release(businessType(),id)
        return@write p.id.value
    }
    fun analysis(id:String,sampleId:String,v:SamplingAnalysisVo):String = write(id) {r ->
        val p=recorded(r);val ended=p.ended ?: fail("请先核对实际取样结束")
        phase(p,"EXTRACTION_ENDED",ended)
        requireNoStoppedImplementation(p)
        val clearedAt=requireSafeEnd(r,p)
        if(p.completed!=null)fail("取样异常待处置或项目已完成，不能继续分析登记")
        val sample=samples(p).find { it.id.value==sampleId } ?: fail("样品不属于本轮方案");phase(p,"SAMPLE_$sampleId",sample.content)
        if(journal(p,sample).events.isNotEmpty())fail("样品已有后续顺序记录，请从分批分析入口追加，不能覆盖旧分析")
        sample.analysis?.let {phase(p,"ANALYSIS_$sampleId",it);if(it==v)return@write sampleId;fail("实际分析和样品保管原件不能覆盖")}
        if(v.analyzedAt.isBefore(clearedAt) || v.analyzedAt.isAfter(LocalDateTime.now()) || v.laboratoryName!=p.content.laboratoryName || listOf(v.methodAndResult,v.reportFileIds,v.signedAnalysisAndCustodyFileIds).any(String::isBlank) || v.consumedQuantity<BigDecimal.ZERO || v.remainingQuantity<BigDecimal.ZERO || (v.consumedQuantity+v.remainingQuantity).compareTo(sample.content.quantity)!=0 || (v.consumedQuantity>BigDecimal.ZERO && !p.content.analysisMayConsumeSample))fail("分析单位、时间、许可消耗范围及剩余样品数量须与本方案和真实原件一致")
        requireMuseumMember(v.operatorUserId,r.orgId)
        if(v.remainingQuantity>BigDecimal.ZERO){if(v.remainingLocation.isNullOrBlank() || v.remainingCustodianUserId.isNullOrBlank())fail("剩余样品必须有实际保管位置和责任人员");requireMuseumMember(v.remainingCustodianUserId,r.orgId)}
        sample.analysis=v;seal(p,"ANALYSIS_$sampleId",v,"${v.reportFileIds},${v.signedAnalysisAndCustodyFileIds}");return@write sampleId
    }
    private fun sampleStates(p:CollectionSamplingPlanDao):Map<String,SamplingSampleJournalVo> {
        TransactionManager.current().entityCache.flush()
        return samples(p).onEach {it.refresh(flush=false)}.associate {it.id.value to journal(p,it)}
    }
    private fun stoppedState(p:CollectionSamplingPlanDao,states:Map<String,SamplingSampleJournalVo> = sampleStates(p)):SamplingStoppedClosureVo {
        phase(p,"PLAN",snapshot(p));return SamplingStoppedClosure.read(p,states) {key,value->phase(p,key,value)}
    }
    private fun requireNoStoppedImplementation(p:CollectionSamplingPlanDao) {
        if(hasStoppedRecords(p)) {
            stoppedState(p)
            fail("本方案已登记实际停作，不能追加分析或填写研究完成；样品保管和后续方案另按实际手续办理")
        }
    }
    private fun hasStoppedRecords(p:CollectionSamplingPlanDao)=CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq businessType()) and (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase like "STOPPEDV%")
    }.limit(1).any()
    private fun canStopSample(state:SamplingSampleJournalVo):Boolean = state.openDiscrepancySequences.isEmpty() && !state.dispositionPending &&
        (if(state.portions.isEmpty())state.remainingQuantity.compareTo(BigDecimal.ZERO)==0 || state.custody.kind==EnumSampleCustody.INTERNAL
        else state.portions.values.all {it.remainingQuantity.compareTo(BigDecimal.ZERO)==0 || it.custody.kind==EnumSampleCustody.INTERNAL})
    private fun requireStoppedBasis(r:CollectionSamplingDao,p:CollectionSamplingPlanDao,states:Map<String,SamplingSampleJournalVo>):LocalDateTime {
        if(p.started==null || p.ended==null || p.completed!=null || states.isEmpty())fail("停作结案须已有实际样品并据实结束取样；未取得样品使用独立未取样结案")
        val safeAt=requireSafeEnd(r,p)
        if(states.values.any {!canStopSample(it)})fail("须先核实全部样品差异、待确认处置及外部剩余的实际收回，不能用停作结案覆盖未结问题")
        states.values.flatMap {if(it.portions.isEmpty())listOf(it.remainingQuantity to it.custody) else it.portions.values.map {v->v.remainingQuantity to v.custody}}
            .filter {it.first>BigDecimal.ZERO}.forEach {requireMuseumMember(it.second.userId ?: fail("剩余样品缺少实际馆内保管人员"),r.orgId)}
        return maxOf(safeAt,states.values.maxOf {it.lastOccurredAt})
    }
    fun reviewStoppedClosure(id:String,v:SamplingStoppedReviewVo):String = write(id) {r ->
        val p=recorded(r);val states=sampleStates(p);val state=stoppedState(p,states)
        state.events.find {it.review==v}?.let {return@write SamplingStoppedClosure.phase(it.sequence)}
        val latestAt=requireStoppedBasis(r,p,states)
        if(state.closedAt!=null)fail("停作结案已经双方确认，原件不能覆盖")
        if(v.planId!=p.id.value || v.previousRevision!=state.revision || v.basisRevision!=state.basisRevision)fail("原件或样品依据已变化，请重新核对全部样品后办理停作")
        val analysisAt=(states.values.flatMap {it.events.mapNotNull {it.analysis?.analyzedAt}}+samples(p).mapNotNull {it.analysis?.analyzedAt}).maxOrNull()
        if(v.stoppedAt.isBefore(p.ended!!.endedAt) || analysisAt!=null && v.stoppedAt.isBefore(analysisAt) || v.reviewedAt.isBefore(latestAt) || v.stoppedAt.isAfter(v.reviewedAt) || v.reviewedAt.isAfter(LocalDateTime.now()) ||
            listOf(v.reason,v.actualWork,v.unfinishedWork,v.originalConditionAndCare,v.sampleCustodyAndFollowUp,v.authorityAndFutureWork,v.collectionArchiveIndex,
                v.stoppingBasisFileIds,v.actualWorkAndCustodyFileIds,v.signedReviewFileIds).any(String::isBlank))fail("须据实记录停止时间、原因、已经实施及未完成工作、全部样品保管、后续手续和签署原件")
        recorder(r.orgId,v.recorderUserId);requireSampleReviewPeople(r,p,v.conservationUserId,v.keeperUserId,true)
        val event=SamplingStoppedEventVo(state.events.size+1,review=v,operatorUserId=v.recorderUserId);SamplingStoppedClosure.advance(state,event)
        val key=SamplingStoppedClosure.phase(event.sequence);seal(p,key,event,"${v.stoppingBasisFileIds},${v.actualWorkAndCustodyFileIds},${v.signedReviewFileIds}");return@write key
    }
    fun confirmStoppedClosure(id:String,v:SamplingStoppedConfirmationVo):String = write(id) {r ->
        val p=recorded(r);val states=sampleStates(p);val state=stoppedState(p,states);val operator=SecurityUtils.getUserDetail().id
        state.events.find {it.confirmation==v && it.operatorUserId==operator}?.let {return@write SamplingStoppedClosure.phase(it.sequence)}
        val latestAt=requireStoppedBasis(r,p,states)
        val proposal=state.events.singleOrNull {it.sequence==v.reviewSequence}?.review ?: fail("缺少待核实的停作结案记录")
        if(v.previousRevision!=state.revision || !state.reviewCurrent || state.reviewSequence!=v.reviewSequence)fail("停作或样品保管依据已变化，须重新核对最新原件")
        requireSampleReviewPeople(r,p,proposal.conservationUserId,proposal.keeperUserId,true)
        if(operator !in setOf(proposal.conservationUserId,proposal.keeperUserId))fail("须由本次指定的保护修复人员与保管负责人分别本人确认停作结案")
        if(v.confirmedAt.isBefore(latestAt) || v.confirmedAt.isAfter(LocalDateTime.now()) || v.opinion.isBlank() || v.signedFileIds.isBlank())fail("须填写本人实际核实时间、停作意见和签署原件")
        val event=SamplingStoppedEventVo(state.events.size+1,confirmation=v,operatorUserId=operator);SamplingStoppedClosure.advance(state,event)
        val key=SamplingStoppedClosure.phase(event.sequence);seal(p,key,event,v.signedFileIds);return@write key
    }
    private fun noSampleState(p:CollectionSamplingPlanDao):SamplingNoSampleClosureVo {
        phase(p,"PLAN",snapshot(p));return SamplingNoSampleClosure.read(p) { key,value->phase(p,key,value) }
    }
    private fun noSampleDetail(p:CollectionSamplingPlanDao):Map<String,Any?> {
        val result=runCatching {noSampleState(p)}
        return mapOf("noSampleClosure" to result.getOrNull(),"noSampleClosureIssue" to result.exceptionOrNull()?.message)
    }
    private fun requireNoSampleClosure(r:CollectionSamplingDao,p:CollectionSamplingPlanDao):LocalDateTime {
        if(p.started==null || p.ended==null || samples(p).isNotEmpty() || p.completed!=null)fail("仅对已实际开始并结束、确未取得任何样品的项目办理独立结案")
        return requireSafeEnd(r,p)
    }
    fun reviewNoSampleClosure(id:String,v:SamplingNoSampleReviewVo):String = write(id) {r ->
        val p=recorded(r);val state=noSampleState(p)
        state.events.find {it.review==v}?.let {return@write SamplingNoSampleClosure.phase(it.sequence)}
        val safeAt=requireNoSampleClosure(r,p)
        if(state.closedAt!=null)fail("未取得样品结案已经双方确认，原件不能覆盖")
        if(v.planId!=p.id.value || v.previousRevision!=state.revision || v.basisRevision!=state.basisRevision)fail("结束或处置依据已变化，请重新核对未取得样品的实际事实")
        if(v.reviewedAt.isBefore(safeAt) || v.reviewedAt.isAfter(LocalDateTime.now()) || listOf(v.reason,v.actualOperations,v.conditionAfter,v.actualAftercare,v.followUp,v.collectionArchiveIndex,v.noSampleEvidenceFileIds,v.conditionFileIds,v.signedReviewFileIds).any(String::isBlank))fail("须核实真实停止原因、未取得样品的依据、实际操作、原件现状、养护、后续安排及归档签署")
        recorder(r.orgId,v.recorderUserId);requireJoint(r,p,v.conservationUserId,v.keeperUserId)
        val event=SamplingNoSampleEventVo(state.events.size+1,review=v,operatorUserId=v.recorderUserId)
        SamplingNoSampleClosure.advance(state,event)
        val key=SamplingNoSampleClosure.phase(event.sequence)
        seal(p,key,event,"${v.noSampleEvidenceFileIds},${v.conditionFileIds},${v.signedReviewFileIds}");return@write key
    }
    fun confirmNoSampleClosure(id:String,v:SamplingNoSampleConfirmationVo):String = write(id) {r ->
        val p=recorded(r);val state=noSampleState(p);val operator=SecurityUtils.getUserDetail().id
        state.events.find {it.confirmation==v && it.operatorUserId==operator}?.let {return@write SamplingNoSampleClosure.phase(it.sequence)}
        val safeAt=requireNoSampleClosure(r,p)
        val proposal=state.events.singleOrNull {it.sequence==v.reviewSequence}?.review ?: fail("缺少待核实的未取得样品结案记录")
        if(v.previousRevision!=state.revision || !state.reviewCurrent || state.reviewSequence!=v.reviewSequence || proposal.basisRevision!=state.basisRevision)fail("结案核查依据已变化，请重新核实最新原件")
        requireJoint(r,p,proposal.conservationUserId,proposal.keeperUserId)
        if(operator !in setOf(proposal.conservationUserId,proposal.keeperUserId))fail("须由本方案保护修复人员和保管负责人分别本人确认未取得样品结案")
        if(v.confirmedAt.isBefore(safeAt) || v.confirmedAt.isAfter(LocalDateTime.now()) || v.opinion.isBlank() || v.signedFileIds.isBlank())fail("须填写本人核实时间、意见和签署原件")
        val event=SamplingNoSampleEventVo(state.events.size+1,confirmation=v,operatorUserId=operator)
        SamplingNoSampleClosure.advance(state,event)
        val key=SamplingNoSampleClosure.phase(event.sequence);seal(p,key,event,v.signedFileIds);return@write key
    }
    fun complete(id:String,v:SamplingCompletionVo):String = completeReview(id,v,null)
    fun completeCurrent(id:String,v:SamplingCompletionReviewVo):String = completeReview(id,v.completion,v.sampleRevisions)
    private fun completeReview(id:String,v:SamplingCompletionVo,revisions:Map<String,String>?):String = write(id) {r ->
        val p=recorded(r);requireNoStoppedImplementation(p);val ended=p.ended ?: fail("尚未登记实际取样结束");phase(p,"EXTRACTION_ENDED",ended)
        TransactionManager.current().entityCache.flush()
        val all=samples(p).onEach {it.refresh(flush=false)};val states=all.associate { it.id.value to journal(p,it) };val currentRevisions=states.mapValues { it.value.revision }
        val hasEvents=states.values.any { it.events.isNotEmpty() }
        if((hasEvents || revisions!=null) && revisions!=currentRevisions)fail("样品分析或保管依据已变化，请重新核对全部样品")
        p.completed?.let {phase(p,"COMPLETED",it);if(hasEvents)phase(p,"COMPLETION_SAMPLES",currentRevisions);if(it==v)return@write p.id.value;fail("共同完成原件不能覆盖")}
        requireJoint(r,p,v.conservationUserId,v.keeperUserId);val clearedAt=requireSafeEnd(r,p)
        if(v.completedAt.isBefore(clearedAt) || all.isEmpty() || all.map { it.id.value }.toSet()!=v.sampleIds || states.values.any { !it.readyForCompletion || it.lastOccurredAt.isAfter(v.completedAt) } || v.completedAt.isBefore(ended.endedAt) || v.completedAt.isAfter(LocalDateTime.now()) || listOf(v.review,v.collectionArchiveIndex,v.signedReviewFileIds).any(String::isBlank))fail("须核对全部样品实际分析、非分析处置或差异处理及剩余保管、原件安全、档案索引和共同签署，未实施或异常不能按正常完成")
        states.values.flatMap { state ->
            if(state.portions.isEmpty())listOf(state.remainingQuantity to state.custody)
            else state.portions.values.map {it.remainingQuantity to it.custody}
        }.filter {it.first>BigDecimal.ZERO && it.second.kind==EnumSampleCustody.INTERNAL}.forEach {
            requireMuseumMember(it.second.userId ?: fail("剩余样品或分份缺少实际保管人员"),r.orgId)
        }
        if(hasEvents)seal(p,"COMPLETION_SAMPLES",currentRevisions)
        p.completed=v;seal(p,"COMPLETED",v,v.signedReviewFileIds);return@write p.id.value
    }
    private fun journal(p:CollectionSamplingPlanDao,s:CollectionSampleDao)=SamplingSampleJournal.read(p,s) { key,value->phase(p,key,value) }
    private fun completedSamples(p:CollectionSamplingPlanDao):Map<String,SamplingSampleJournalVo> {
        if(p.completed==null) {
            val states=sampleStates(p);val stopped=stoppedState(p,states)
            val closedAt=stopped.closedAt ?: fail("尚未共同完成或停作结案，不能登记结案后保管")
            if(!stopped.reviewCurrent || states.values.any {it.lastOccurredAt.isAfter(closedAt) || !canStopSample(it)})fail("停作结案时样品保管依据不一致")
            return states
        }
        val c=p.completed!!
        phase(p,"PLAN",snapshot(p));phase(p,"COMPLETED",c)
        val started=p.started ?: fail("缺少结案时实际开始原件");phase(p,"STARTED",started)
        val ended=p.ended ?: fail("缺少结案时实际结束原件");phase(p,"EXTRACTION_ENDED",ended)
        if(ended.endedAt.isBefore(started.startedAt) || c.completedAt.isBefore(ended.endedAt))fail("结案与实际开始结束时间不一致")
        if(!ended.safeToReleaseOriginal) {
            val clearance=p.incidentClearance ?: fail("缺少结案时事故处置解限原件")
            phase(p,"EXCEPTION_CLEARED",clearance)
            if(c.completedAt.isBefore(clearance.record.confirmedAt))fail("结案时间早于原事故处置解限")
        }
        TransactionManager.current().entityCache.flush()
        val all=samples(p).onEach {it.refresh(flush=false)}
        if(all.isEmpty() || c.sampleIds!=all.map {it.id.value}.toSet())fail("结案样品清单与原始材料不一致")
        val states=all.associate {it.id.value to journal(p,it)}
        if(states.values.any {!it.readyForCompletion || it.lastOccurredAt.isAfter(c.completedAt)})fail("结案时样品分析保管依据不一致")
        if(states.values.any {it.events.isNotEmpty()})phase(p,"COMPLETION_SAMPLES",states.mapValues {it.value.revision})
        return states
    }
    private fun custodyJournal(p:CollectionSamplingPlanDao,s:CollectionSampleDao,basis:Map<String,SamplingSampleJournalVo> = completedSamples(p))=
        SamplingSampleJournal.readCustody(p,s.id.value,basis[s.id.value] ?: fail("结案依据不包含该样品"),if(p.completed==null)stoppedState(p,basis) else null) {key,value->phase(p,key,value)}
    private fun eventJournal(p:CollectionSamplingPlanDao,s:CollectionSampleDao,afterCompletion:Boolean)=if(afterCompletion)custodyJournal(p,s) else journal(p,s)
    private fun eventPrefix(sampleId:String,afterCompletion:Boolean)=if(afterCompletion)SamplingSampleJournal.custodyPrefix(sampleId) else SamplingSampleJournal.prefix(sampleId)
    private fun sampleDetail(p:CollectionSamplingPlanDao,s:CollectionSampleDao,completionBasis:Result<Map<String,SamplingSampleJournalVo>>?):Map<String,Any?> {
        val result=runCatching { journal(p,s) }
        val custody=if(completionBasis!=null)runCatching {custodyJournal(p,s,completionBasis.getOrThrow())} else null
        val amendment=runCatching {analysisAmendment(p,s.id.value)}
        return mapOf("analysisAmendment" to amendment.getOrNull(),"analysisAmendmentIssue" to amendment.exceptionOrNull()?.message,
            "id" to s.id.value,"content" to s.content,"analysis" to s.analysis,
            "archivePhasePrefixes" to mapOf("journal" to SamplingSampleJournal.prefix(s.id.value),
                "custody" to SamplingSampleJournal.custodyPrefix(s.id.value),
                "analysisAmendment" to SamplingAnalysisAmendment.prefix(s.id.value)),
            "journal" to result.getOrNull(),"journalIssue" to result.exceptionOrNull()?.message,
            "custodyJournal" to custody?.getOrNull(),"custodyJournalIssue" to custody?.exceptionOrNull()?.message)
    }
    private fun sampleForEvent(r:CollectionSamplingDao,sampleId:String,checkSafety:Boolean=true,afterCompletion:Boolean=false):Triple<CollectionSamplingDao,CollectionSamplingPlanDao,CollectionSampleDao> {
        val p=recorded(r)
        val closed=p.completed!=null || hasStoppedRecords(p) && stoppedState(p).closedAt!=null
        if(afterCompletion && !closed)fail("尚未共同完成或停作结案，请从实际实施入口登记")
        if(!afterCompletion && closed)fail("已结案的项目不能追加样品实施事实，请从后续保管入口登记")
        if(checkSafety) {
            val ended=p.ended ?: fail("须先共同核实实际取样结束")
            phase(p,"EXTRACTION_ENDED",ended);requireSafeEnd(r,p)
        } else p.ended?.let {phase(p,"EXTRACTION_ENDED",it)}
        val s=samples(p).find { it.id.value==sampleId } ?: fail("样品不属于本轮方案")
        TransactionManager.current().entityCache.flush();s.refresh(flush=false)
        return Triple(r,p,s)
    }
    private fun requireSampleReviewPeople(r:CollectionSamplingDao,p:CollectionSamplingPlanDao,conservation:String,keeper:String,afterCompletion:Boolean) {
        if(!afterCompletion && !hasStoppedRecords(p)){requireJoint(r,p,conservation,keeper);return}
        if(conservation==keeper)fail("后续保管或停作核查须分别指定当前保护修复人员和保管负责人")
        requireMuseumMember(conservation,r.orgId);requireRole(conservation,r.orgId,CollectionConst.ROLE_CONSERVATION)
        requireMuseumMember(keeper,r.orgId);requireRole(keeper,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
    }
    private fun recorder(orgId:String,id:String) {
        if(id!=SecurityUtils.getUserDetail().id)fail("须由本次实际登记人员本人保存样品原件")
        requireMuseumMember(id,orgId)
    }
    private fun analysisAmendment(p:CollectionSamplingPlanDao,sampleId:String)=SamplingAnalysisAmendment.read(p,sampleId) {key,value->phase(p,key,value)}
    private fun analysisEvent(p:CollectionSamplingPlanDao,sampleId:String,e:SamplingAnalysisAmendmentEventVo,files:String):String {
        SamplingAnalysisAmendment.advance(analysisAmendment(p,sampleId),e)
        val key=SamplingAnalysisAmendment.phase(sampleId,e.sequence);seal(p,key,e,files);return key
    }
    private fun analysisPeople(r:CollectionSamplingDao,p:CollectionSamplingPlanDao,v:SamplingAnalysisProposalVo) {
        requireSampleReviewPeople(r,p,v.conservationUserId,v.keeperUserId,true)
        requireDepartmentMember(v.conservationUserId,r.orgId,v.conservationDepartmentId)
    }
    private fun currentAnalysisCustodians(r:CollectionSamplingDao,journal:SamplingSampleJournalVo) {
        val portions=if(journal.portions.isEmpty())listOf(journal.remainingQuantity to journal.custody) else journal.portions.values.map {it.remainingQuantity to it.custody}
        portions.filter {it.first>BigDecimal.ZERO && it.second.kind==EnumSampleCustody.INTERNAL}.forEach {
            requireMuseumMember(it.second.userId ?: fail("剩余样品缺少实际保管人员"),r.orgId)
        }
    }
    private fun analysisBasis(r:CollectionSamplingDao,p:CollectionSamplingPlanDao,s:CollectionSampleDao,v:SamplingAnalysisProposalVo):SamplingSampleJournalVo {
        val journal=custodyJournal(p,s)
        if(v.sampleRevision!=journal.revision || (v.sourceRevision!=digest(source(r)) && v.sourceRevision!=digest(source(r,true))))fail("现存样品或藏品依据变化，须追加核实后的分析方案并重新确认")
        if(journal.openDiscrepancySequences.isNotEmpty() || journal.dispositionPending)fail("旧样品差异或待确认处置尚未核实")
        analysisPeople(r,p,v);currentAnalysisCustodians(r,journal);return journal
    }
    fun proposeExistingAnalysis(id:String,sampleId:String,v:SamplingAnalysisProposalVo):String = write(id) {r ->
        val(_,p,s)=sampleForEvent(r,sampleId,true,true);val a=analysisAmendment(p,sampleId)
        a.events.find {it.proposal==v}?.let {return@write SamplingAnalysisAmendment.phase(sampleId,it.sequence)}
        val journal=analysisBasis(r,p,s,v);recorder(r.orgId,v.recorderUserId)
        if(v.previousRevision!=a.revision || v.preparedAt.isBefore(journal.lastOccurredAt) || v.preparedAt.isBefore(requireSafeEnd(r,p)) || v.preparedAt.isAfter(LocalDateTime.now()) ||
            v.plannedStart.isBefore(v.preparedAt) || !v.plannedEnd.isAfter(v.plannedStart) ||
            listOf(v.changeReason,v.researchNeedAndPriorWork,v.applicabilityExplanation,v.applicabilityFileIds,v.assessmentFileIds,v.signedPlanFileIds).any(String::isBlank) ||
            v.targets.isEmpty() || v.targets.map {it.portionKey}.distinct().size!=v.targets.size)fail("须核对旧样品分析实际需要、时间、逐份范围、审批适用依据及共同方案原件")
        v.targets.forEach {t->
            val target=SamplingSampleJournal.target(journal,t.portionKey)
            if(target.remainingQuantity<=BigDecimal.ZERO || t.maximumConsumption<BigDecimal.ZERO || t.maximumConsumption>target.remainingQuantity ||
                listOf(t.laboratoryName,t.method,t.remainingSamplePlan).any(String::isBlank))fail("分析范围须对应当前实存样品或分份，消耗上限不得超过其剩余量")
        }
        if(v.approvalTreatment==EnumExistingSampleApproval.EXISTING_APPROVAL_COVERS)existingAnalysisPermit(p,v)
        else if(!v.existingApprovalId.isNullOrBlank())fail("仅原批准覆盖情形可选择前案批文")
        return@write analysisEvent(p,sampleId,SamplingAnalysisAmendmentEventVo(a.events.size+1,v.recorderUserId,proposal=v),"${v.applicabilityFileIds},${v.assessmentFileIds},${v.signedPlanFileIds}")
    }
    private fun existingAnalysisPermit(p:CollectionSamplingPlanDao,v:SamplingAnalysisProposalVo):CollectionAdministrativeEvidenceVo {
        val a=permits(p).singleOrNull {it.id.value==v.existingApprovalId} ?: fail("原批准覆盖须选择本方案真实批准原件并说明覆盖范围")
        phase(p,"ADMIN_${a.id.value}",a.content)
        if(a.content.action!=EnumAdministrativeAction.APPROVAL || a.content.level!=p.authority)fail("原批准层级或类型不符合前案依据")
        return a.content
    }
    fun confirmExistingAnalysis(id:String,sampleId:String,v:SamplingAnalysisConfirmationVo):String = write(id) {r ->
        val(_,p,s)=sampleForEvent(r,sampleId,true,true);val a=analysisAmendment(p,sampleId);val who=SecurityUtils.getUserDetail().id
        a.events.find {it.confirmation==v && it.operatorUserId==who}?.let {return@write SamplingAnalysisAmendment.phase(sampleId,it.sequence)}
        val proposal=SamplingAnalysisAmendment.proposal(a)
        if(v.subjectSequence==a.proposalSequence)analysisBasis(r,p,s,proposal)
        else {
            val close=a.events.singleOrNull {it.sequence==a.closureSequence}?.closure ?: fail("缺少本次结束核查原件")
            val journal=custodyJournal(p,s)
            if(close.sampleRevision!=journal.revision || !canStopSample(journal))fail("结束核查后的样品依据变化，须重新核查并分别确认")
            requireSampleReviewPeople(r,p,close.conservationUserId,close.keeperUserId,true);currentAnalysisCustodians(r,journal)
        }
        if(v.confirmedAt.isAfter(LocalDateTime.now()) || v.confirmedAt.isBefore(requireSafeEnd(r,p)))fail("本人确认时间须覆盖真实核实依据")
        return@write analysisEvent(p,sampleId,SamplingAnalysisAmendmentEventVo(a.events.size+1,who,confirmation=v),v.signedFileIds)
    }
    fun approveExistingAnalysis(id:String,sampleId:String,v:SamplingAnalysisAdministrativeVo):String = write(id) {r ->
        val(_,p,s)=sampleForEvent(r,sampleId,true,true);val a=analysisAmendment(p,sampleId)
        a.events.find {it.administrative==v}?.let {return@write SamplingAnalysisAmendment.phase(sampleId,it.sequence)}
        val proposal=SamplingAnalysisAmendment.proposal(a);analysisBasis(r,p,s,proposal);val e=v.evidence
        if(e.level!=p.authority || e.action!=EnumAdministrativeAction.APPROVAL || e.issuedAt.isBefore(proposal.preparedAt.toLocalDate()) || e.issuedAt.isAfter(LocalDate.now()) || listOf(e.authorityName,e.documentNo,e.fileIds).any(String::isBlank))fail("须登记本次分析方案适用的新批准机关、日期、文号与原件")
        return@write analysisEvent(p,sampleId,SamplingAnalysisAmendmentEventVo(a.events.size+1,SecurityUtils.getUserDetail().id,administrative=v),e.fileIds)
    }
    fun activateExistingAnalysis(id:String,sampleId:String,v:SamplingAnalysisActivationVo):String = write(id) {r ->
        val(_,p,s)=sampleForEvent(r,sampleId,true,true);val a=analysisAmendment(p,sampleId)
        a.events.find {it.activation==v}?.let {custodyJournal(p,s);return@write SamplingAnalysisAmendment.phase(sampleId,it.sequence)}
        val proposal=SamplingAnalysisAmendment.proposal(a);val journal=analysisBasis(r,p,s,proposal)
        if(v.sampleRevision!=journal.revision || v.activatedAt.isBefore(journal.lastOccurredAt) || v.activatedAt.isBefore(proposal.plannedStart) || v.activatedAt.isAfter(proposal.plannedEnd) || v.activatedAt.isAfter(LocalDateTime.now()) || v.verification.isBlank() || v.signedFileIds.isBlank())fail("启用须核实当前样品、方案时段及实际手续，不产生取样或分析事实")
        val permit=when(proposal.approvalTreatment) {
            EnumExistingSampleApproval.NEW_APPROVAL->a.events.singleOrNull {it.sequence==a.administrativeSequence}?.administrative?.evidence ?: fail("须先登记新方案批准")
            EnumExistingSampleApproval.EXISTING_APPROVAL_COVERS->existingAnalysisPermit(p,proposal)
            EnumExistingSampleApproval.NOT_APPLICABLE->null
        }
        if(permit!=null && permit.issuedAt.isAfter(v.activatedAt.toLocalDate()))fail("不能在适用批准之前启用")
        val e=SamplingAnalysisAmendmentEventVo(a.events.size+1,SecurityUtils.getUserDetail().id,activation=v)
        val key=analysisEvent(p,sampleId,e,v.signedFileIds)
        val authorized=SamplingSampleEventVo(sampleId,journal.events.size+1,journal.custody,
            analysisAuthorization=SamplingAnalysisAuthorizationVo(journal.revision,v.proposalSequence,e.sequence,v.activatedAt))
        SamplingSampleJournal.advance(journal,authorized)
        seal(p,"${SamplingSampleJournal.custodyPrefix(sampleId)}${authorized.sequence}",authorized)
        return@write key
    }
    private fun activeExistingAnalysis(r:CollectionSamplingDao,p:CollectionSamplingPlanDao,sampleId:String):Pair<Int,SamplingAnalysisProposalVo> {
        val a=analysisAmendment(p,sampleId)
        if(a.activationSequence==null || a.closureSequence!=null || a.closedAt!=null)fail("须先启用经本人确认并完成适用手续的旧样品分析方案，结束核查后不能继续分析")
        val v=SamplingAnalysisAmendment.proposal(a);analysisPeople(r,p,v)
        if((v.sourceRevision!=digest(source(r)) && v.sourceRevision!=digest(source(r,true))))fail("藏品依据已变化，须重新核实后续分析方案")
        return a.proposalSequence!! to v
    }
    fun analyzeExistingSample(id:String,sampleId:String,v:SamplingBatchAnalysisVo):String = write(id) {r ->
        val(_,p,s)=sampleForEvent(r,sampleId,true,true);val journal=custodyJournal(p,s)
        journal.events.find {it.analysis==v}?.let {return@write "${SamplingSampleJournal.custodyPrefix(sampleId)}${it.sequence}"}
        val(seq,proposal)=activeExistingAnalysis(r,p,sampleId);val t=proposal.targets.singleOrNull {it.portionKey==v.portionKey} ?: fail("该样品分份不在已启用分析范围")
        val target=SamplingSampleJournal.target(journal,v.portionKey)
        val authorization=journal.events.lastOrNull {it.analysisAuthorization!=null}?.analysisAuthorization ?: fail("缺少分析方案启用的样品顺序原件")
        val consumed=journal.events.mapNotNull {it.analysis}.filter {it.analysisPlanSequence==seq && it.portionKey==v.portionKey}.fold(BigDecimal.ZERO){sum,b->sum+b.consumedQuantity}
        if(v.previousRevision!=journal.revision || v.analysisPlanSequence!=seq || authorization.proposalSequence!=seq || v.laboratoryName!=t.laboratoryName ||
            v.analyzedAt.isBefore(authorization.activatedAt) || v.analyzedAt.isBefore(requireSafeEnd(r,p)) || v.analyzedAt.isBefore(proposal.plannedStart) || v.analyzedAt.isAfter(proposal.plannedEnd) || v.analyzedAt.isAfter(LocalDateTime.now()) ||
            consumed+v.consumedQuantity>t.maximumConsumption || v.batchNumber.length>100 || listOf(v.batchNumber,v.analystName,v.methodAndResult,v.reportFileIds,v.signedAnalysisAndCustodyFileIds).any(String::isBlank) ||
            v.remainingQuantity>BigDecimal.ZERO && v.remainingLocation.isNullOrBlank())fail("须按已启用分析单位、逐份范围、时段和累计消耗上限记录真实分析")
        if(target.custody.kind==EnumSampleCustody.EXTERNAL && target.custody.externalUnit!=v.laboratoryName)fail("须先完成实际外部单位交接")
        if(target.custody.kind==EnumSampleCustody.INTERNAL)requireMuseumMember(target.custody.userId ?: fail("缺少实际保管人员"),r.orgId)
        recorder(r.orgId,v.recorderUserId)
        val event=SamplingSampleEventVo(sampleId,journal.events.size+1,target.custody,analysis=v);SamplingSampleJournal.advance(journal,event)
        val key="${SamplingSampleJournal.custodyPrefix(sampleId)}${event.sequence}";seal(p,key,event,"${v.reportFileIds},${v.signedAnalysisAndCustodyFileIds}");return@write key
    }
    fun closeExistingAnalysis(id:String,sampleId:String,v:SamplingAnalysisClosureVo):String = write(id) {r ->
        val(_,p,s)=sampleForEvent(r,sampleId,true,true);val a=analysisAmendment(p,sampleId)
        a.events.find {it.closure==v}?.let {return@write SamplingAnalysisAmendment.phase(sampleId,it.sequence)}
        val journal=custodyJournal(p,s);val proposal=SamplingAnalysisAmendment.proposal(a)
        if(v.sampleRevision!=journal.revision || !canStopSample(journal) || v.reviewedAt.isBefore(journal.lastOccurredAt) || v.reviewedAt.isBefore(requireSafeEnd(r,p)) || v.reviewedAt.isAfter(LocalDateTime.now()) ||
            listOf(v.actualWorkAndResults,v.unfinishedWorkAndReason,v.remainingCustodyAndFollowUp,v.resultFileIds,v.signedReviewFileIds).any(String::isBlank))fail("须核实实际完成及未完成工作、全部剩余保管和签署原件，未结问题不能由结束覆盖")
        val batches=journal.events.mapNotNull {it.analysis}.filter {it.analysisPlanSequence==a.proposalSequence}
        if(batches.any {it.analyzedAt.isAfter(v.endedAt)} || v.completedAsPlanned && proposal.targets.any {target->batches.none {it.portionKey==target.portionKey}})fail("结束不得早于已实施分析，未实施范围不能声明按计划完成")
        recorder(r.orgId,v.recorderUserId);requireSampleReviewPeople(r,p,v.conservationUserId,v.keeperUserId,true);currentAnalysisCustodians(r,journal)
        return@write analysisEvent(p,sampleId,SamplingAnalysisAmendmentEventVo(a.events.size+1,v.recorderUserId,closure=v),"${v.resultFileIds},${v.signedReviewFileIds}")
    }
    fun analyzeBatch(id:String,sampleId:String,v:SamplingBatchAnalysisVo):String = write(id) {r ->
        if(v.analysisPlanSequence!=null)fail("旧样品变更分析须从已启用方案入口办理")
        val (_,p,s)=sampleForEvent(r,sampleId);requireNoStoppedImplementation(p);val state=journal(p,s)
        // 网络重试只返回已封存的同一份记录，不再消耗数量；后续记录已经存在时也保留原返回值。
        state.events.find { it.analysis==v }?.let { return@write "${SamplingSampleJournal.prefix(sampleId)}${it.sequence}" }
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("样品数量或保管记录已变化，请重新核对")
        if(v.analyzedAt.isBefore(requireSafeEnd(r,p)) || v.analyzedAt.isAfter(LocalDateTime.now()) ||
            v.laboratoryName!=p.content.laboratoryName || v.batchNumber.length>100 ||
            listOf(v.batchNumber,v.analystName,v.methodAndResult,v.reportFileIds,v.signedAnalysisAndCustodyFileIds).any(String::isBlank) ||
            (v.consumedQuantity>BigDecimal.ZERO && !p.content.analysisMayConsumeSample) ||
            (v.remainingQuantity>BigDecimal.ZERO && v.remainingLocation.isNullOrBlank()))fail("须核对本批编号、批准分析单位、实际分析人、时间、许可消耗及剩余保管原件")
        if(target.custody.kind==EnumSampleCustody.EXTERNAL && target.custody.externalUnit!=v.laboratoryName)fail("实际保管单位与批准分析单位不一致，须先核实方案和交接")
        recorder(r.orgId,v.recorderUserId)
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,analysis=v)
        SamplingSampleJournal.advance(state,event)
        val key="${SamplingSampleJournal.prefix(sampleId)}${event.sequence}"
        seal(p,key,event,"${v.reportFileIds},${v.signedAnalysisAndCustodyFileIds}");return@write key
    }
    fun handoverSample(id:String,sampleId:String,v:SamplingSampleHandoverVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,true,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        state.events.find { it.handover==v }?.let { return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}" }
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("样品数量或保管记录已变化，请重新核对")
        val d=v.destination
        if(v.handedAt.isBefore(requireSafeEnd(r,p)) || v.handedAt.isAfter(LocalDateTime.now()) ||
            listOf(d.location,v.condition,v.purposeAndArrangements,v.basisFileIds,v.markingFileIds,v.signedBothPartiesFileIds).any(String::isBlank))fail("须填写真实交接时间、现状、保管安排、标识及双方签署原件")
        when(d.kind) {
            EnumSampleCustody.INTERNAL -> {
                if(d.userId.isNullOrBlank() || !d.externalUnit.isNullOrBlank() || !d.externalPerson.isNullOrBlank() || d.expectedReturnAt!=null)fail("馆内接收须明确实际人员，不能混填外部保管身份")
                requireMuseumMember(d.userId,r.orgId)
            }
            EnumSampleCustody.EXTERNAL -> {
                val amended=if(afterCompletion)analysisAmendment(p,sampleId) else null
                val approvedUnit=if(amended?.activationSequence!=null && amended.closureSequence==null) {
                    val(_,policy)=activeExistingAnalysis(r,p,sampleId)
                    policy.targets.singleOrNull {it.portionKey==v.portionKey}?.laboratoryName ?: fail("该分份不在当前分析交接范围")
                } else p.content.laboratoryName
                if(!d.userId.isNullOrBlank() || d.externalUnit!=approvedUnit || d.externalPerson.isNullOrBlank() || d.expectedReturnAt==null || !d.expectedReturnAt.isAfter(v.handedAt))fail("外部接收须对应批准分析单位，填写实际接收人及约定归还时间，不能借用馆内账号代替")
            }
        }
        recorder(r.orgId,v.recorderUserId)
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,handover=v)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,"${v.basisFileIds},${v.markingFileIds},${v.signedBothPartiesFileIds}");return@write key
    }
    fun splitSample(id:String,sampleId:String,v:SamplingSampleSplitVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,true,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        state.events.find {it.split==v}?.let {return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}"}
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("样品或分份数量、保管依据已变化，请重新核对")
        if(v.splitAt.isBefore(requireSafeEnd(r,p)) || v.splitAt.isAfter(LocalDateTime.now()) ||
            listOf(v.reasonAndMethod,v.condition,v.basisFileIds,v.markingFileIds,v.signedSplitAndCustodyFileIds).any(String::isBlank))fail("须记录实际分份时间、理由方法、现状、依据、逐份标识及拆分保管签署原件")
        recorder(r.orgId,v.recorderUserId)
        if(target.custody.kind==EnumSampleCustody.INTERNAL)requireMuseumMember(target.custody.userId ?: fail("缺少实际保管人员"),r.orgId)
        else if(target.custody.externalUnit!=recordedCustodyUnit(p,state,v.portionKey))fail("实际外部保管单位与批准方案不一致，须先核实")
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,split=v)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,"${v.basisFileIds},${v.markingFileIds},${v.signedSplitAndCustodyFileIds}");return@write key
    }
    /** 实际保管沿用最近启用原件；再分份继承父份的保管依据，但不自动继承分析消耗许可。 */
    private fun recordedCustodyUnit(p:CollectionSamplingPlanDao,state:SamplingSampleJournalVo,portionKey:String?):String {
        val event=state.events.lastOrNull {it.analysisAuthorization!=null} ?: return p.content.laboratoryName
        val policy=SamplingAnalysisAmendment.authorized(analysisAmendment(p,event.sampleId),event.analysisAuthorization!!)
        var key=portionKey
        val visited=mutableSetOf<String?>()
        while(visited.add(key)) {
            policy.targets.singleOrNull {it.portionKey==key}?.let {return it.laboratoryName}
            if(key==null)break
            val split=state.events.mapNotNull {it.split}.lastOrNull {v->v.portions.any {it.key==key}} ?: break
            key=split.portionKey
        }
        return p.content.laboratoryName
    }
    private fun requireDispositionCustody(r:CollectionSamplingDao,state:SamplingSampleJournalVo,approvedUnit:String) {
        when(state.custody.kind) {
            EnumSampleCustody.INTERNAL -> requireMuseumMember(state.custody.userId ?: fail("缺少实际样品保管人员"),r.orgId)
            EnumSampleCustody.EXTERNAL -> if(state.custody.externalUnit!=approvedUnit || state.custody.externalPerson.isNullOrBlank())fail("实际保管单位和人员与批准分析安排不符，须先核实差异")
        }
    }
    fun recordSampleDisposition(id:String,sampleId:String,v:SamplingSampleDispositionVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,true,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        state.events.find {it.disposition==v}?.let {return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}"}
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("样品处置依据已变化，请重新核对数量与保管")
        if(v.disposedAt.isBefore(requireSafeEnd(r,p)) || v.disposedAt.isAfter(LocalDateTime.now()) ||
            listOf(v.methodAndReason,v.authorityAndBasis,v.actualResultAndDestination,v.remainingConditionAndArrangements,
                v.authorizationFileIds,v.quantityAndResultFileIds,v.signedDispositionFileIds).any(String::isBlank))fail("须据实记录非分析处置的时间、方法、适用权限依据、实际去向、数量现状及签署原件；登记不授予处置许可")
        recorder(r.orgId,v.recorderUserId);requireSampleReviewPeople(r,p,v.conservationUserId,v.keeperUserId,afterCompletion)
        requireDispositionCustody(r,target,recordedCustodyUnit(p,state,v.portionKey))
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,disposition=v)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,listOf(v.authorizationFileIds,v.quantityAndResultFileIds,v.signedDispositionFileIds).joinToString(","));return@write key
    }
    fun confirmSampleDisposition(id:String,sampleId:String,v:SamplingSampleDispositionConfirmationVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,true,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        val operator=SecurityUtils.getUserDetail().id;val record=SamplingSampleDispositionConfirmationRecord(v,operator)
        state.events.find {it.dispositionConfirmation==record}?.let {return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}"}
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("处置依据或样品状态已变化，请重新核对")
        val proposal=state.events.singleOrNull {it.sequence==v.dispositionSequence}?.disposition ?: fail("缺少本次非分析处置原件")
        requireSampleReviewPeople(r,p,proposal.conservationUserId,proposal.keeperUserId,afterCompletion)
        if(operator !in setOf(proposal.conservationUserId,proposal.keeperUserId))fail("须由指定保护修复人员和保管负责人分别本人核实处置原件，不能代办")
        if(v.confirmedAt.isAfter(LocalDateTime.now()) || v.opinion.isBlank() || v.signedFileIds.isBlank())fail("须填写本人实际核实时间、意见及签署原件")
        requireDispositionCustody(r,target,recordedCustodyUnit(p,state,v.portionKey))
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,dispositionConfirmation=record)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,v.signedFileIds);return@write key
    }
    private fun observedCustody(value:SamplingSampleCustodyVo?) {
        if(value==null)return // 未查明的保管事实保留未知，不代填原位置或现任人员。
        if(value.location.isBlank())fail("已查明保管地点时须填写实际位置")
        when(value.kind) {
            EnumSampleCustody.INTERNAL -> if(value.userId.isNullOrBlank() || !value.externalUnit.isNullOrBlank() || !value.externalPerson.isNullOrBlank() || value.expectedReturnAt!=null)fail("馆内历史保管身份与外部单位身份不能混填")
            EnumSampleCustody.EXTERNAL -> if(!value.userId.isNullOrBlank() || value.externalUnit.isNullOrBlank() || value.externalPerson.isNullOrBlank())fail("外部保管须据实记录已查明单位和人员，不能借用馆内账号")
        }
    }
    fun recordSampleDiscrepancy(id:String,sampleId:String,v:SamplingSampleDiscrepancyVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,false,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        state.events.find {it.discrepancy==v}?.let {return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}"}
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("样品已有新记录，请重新核对后据实登记差异")
        if(v.observedAt.isBefore(v.portionKey?.let {state.portions[it]?.createdAt} ?: s.content.takenAt) || v.observedAt.isAfter(LocalDateTime.now()) ||
            listOf(v.findings,v.immediateMeasures,v.reportingAndFollowUp,v.evidenceFileIds,v.signedObservationFileIds).any(String::isBlank))fail("须记录真实发现时间、数量现状、保护措施、报告跟进及本人签署原件")
        observedCustody(v.observedCustody);recorder(r.orgId,v.recorderUserId)
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,discrepancy=v)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,"${v.evidenceFileIds},${v.signedObservationFileIds}");return@write key
    }
    fun reviewSampleDiscrepancy(id:String,sampleId:String,v:SamplingSampleResolutionVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,false,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        state.events.find {it.resolution==v}?.let {return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}"}
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("样品核查依据已变化，请重新核对全部差异")
        if(v.reviewedAt.isAfter(LocalDateTime.now()) || listOf(v.investigationAndCause,v.actualMeasures,v.reportingAndHandling,v.followUp,v.investigationFileIds,v.reportingAndHandlingFileIds,v.signedReviewFileIds).any(String::isBlank))fail("须留存实际调查原因、处置、报告处理结果、后续安排及签署原件")
        recorder(r.orgId,v.recorderUserId);requireSampleReviewPeople(r,p,v.conservationUserId,v.keeperUserId,afterCompletion);observedCustody(v.actualCustody)
        v.finalAccounting?.let { final ->
            requireSafeEnd(r,p)
            SamplingSampleJournal.validateFinalAccounting(target,v)
            val observedAt=state.events.filter {it.sequence in target.openDiscrepancySequences}.mapNotNull {it.discrepancy?.observedAt}.maxOrNull() ?: fail("缺少本次差异发现依据")
            if(final.decidedAt.isBefore(observedAt) || final.decidedAt.isAfter(v.reviewedAt) ||
                listOf(final.authorityAndBasis,final.decisionNo,final.decisionFileIds,final.followUpResponsibility,final.followUpFileIds).any(String::isBlank))fail("须核对差异发生后的实际处理决定、适用权限依据和持续跟进责任，不能以普通调查代替最终记账依据")
        }
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,resolution=v)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,listOfNotNull(v.investigationFileIds,v.reportingAndHandlingFileIds,v.signedReviewFileIds,v.custodyHandoverFileIds,v.finalAccounting?.decisionFileIds,v.finalAccounting?.followUpFileIds).joinToString(","));return@write key
    }
    fun confirmSampleDiscrepancy(id:String,sampleId:String,v:SamplingSampleConfirmationVo,afterCompletion:Boolean=false):String = write(id) {r ->
        val (_,p,s)=sampleForEvent(r,sampleId,false,afterCompletion);val state=eventJournal(p,s,afterCompletion)
        val operator=SecurityUtils.getUserDetail().id
        val record=SamplingSampleConfirmationRecord(v,operator)
        state.events.find {it.confirmation==record}?.let {return@write "${eventPrefix(sampleId,afterCompletion)}${it.sequence}"}
        val target=SamplingSampleJournal.target(state,v.portionKey)
        if(v.previousRevision!=state.revision)fail("调查或样品记录已变化，请重新核对本次实际调查")
        val proposal=state.events.singleOrNull {it.sequence==v.resolutionSequence}?.resolution ?: fail("缺少本次调查记录")
        requireSampleReviewPeople(r,p,proposal.conservationUserId,proposal.keeperUserId,afterCompletion)
        if(operator !in setOf(proposal.conservationUserId,proposal.keeperUserId))fail("须由本次核查指定的保护修复人员和保管负责人分别本人核实，不能由登记人代办")
        if(v.confirmedAt.isAfter(LocalDateTime.now()) || v.opinion.isBlank() || v.signedFileIds.isBlank())fail("须填写本人核实时间、意见和签署原件")
        proposal.finalAccounting?.let { requireSafeEnd(r,p);SamplingSampleJournal.validateFinalAccounting(target,proposal) }
        if(proposal.actualQuantity>BigDecimal.ZERO && proposal.identityConfirmed && proposal.safeToContinue && (proposal.actualQuantity.compareTo(target.remainingQuantity)==0 || proposal.finalAccounting!=null)) {
            val custody=proposal.actualCustody ?: fail("恢复正常办理前须查明实存样品的保管事实")
            when(custody.kind) {
                EnumSampleCustody.INTERNAL -> requireMuseumMember(custody.userId ?: fail("缺少实存样品保管人员"),r.orgId)
                EnumSampleCustody.EXTERNAL -> if(custody.externalUnit!=recordedCustodyUnit(p,state,v.portionKey) || custody.expectedReturnAt==null)fail("恢复正常办理前须核对批准分析单位及实际保管返还安排")
            }
            val firstIssue=target.openDiscrepancySequences.minOrNull() ?: fail("缺少原差异依据")
            fun identity(value:SamplingSampleCustodyVo)=listOf(value.kind.name,value.userId,value.externalUnit,value.externalPerson)
            val prior=state.events.filter {it.sequence>=firstIssue && SamplingSampleJournal.portionKey(it)==v.portionKey}.flatMap {event->listOfNotNull(event.from,event.discrepancy?.observedCustody,event.resolution?.actualCustody)}
            if(prior.any {identity(it)!=identity(custody)} && proposal.custodyHandoverFileIds.isNullOrBlank())fail("差异期间保管人员或单位发生变化，须保留实际交接及双方签署原件，不能由调查确认代替点交")
        }
        val event=SamplingSampleEventVo(sampleId,state.events.size+1,target.custody,confirmation=record)
        SamplingSampleJournal.advance(state,event)
        val key="${eventPrefix(sampleId,afterCompletion)}${event.sequence}"
        seal(p,key,event,v.signedFileIds);return@write key
    }
    fun clearIncident(id:String,v:IncidentClearanceVo):String = write(id,lockCollection=true) {r ->
        val p=recorded(r)
        val ended=p.ended ?: fail("须先记录实际异常取样结束，保留当时现状")
        phase(p,"EXTRACTION_ENDED",ended)
        if(ended.safeToReleaseOriginal || p.completed!=null)fail("仅对已实际异常结束且尚未完成的方案办理事故解限")
        p.incidentClearance?.let { phase(p,"EXCEPTION_CLEARED",it);if(it.record==v)return@write p.id.value;fail("事故解限原件不能覆盖") }
        requireJoint(r,p,v.conservationUserId,v.keeperUserId)
        if(v.confirmedAt.isBefore(ended.endedAt) || v.confirmedAt.isAfter(LocalDateTime.now()) || listOf(v.condition,v.actualMeasures,v.signedFileIds).any(String::isBlank))fail("须据实核对处置后现状、措施、时间和双方签署原件")
        if(collection(r).inventoryHold)fail("尚有清点异常，不能解除原件安全限制")
        val proof=incidents?.requireClearance(v.incidentId,r.orgId,r.collectionId,IncidentSourceVo(businessType(),id,p.id.value),v.confirmedAt) ?: fail("事故处理核验服务不可用")
        val saved=SamplingIncidentClearanceSnapshot(v,proof);p.incidentClearance=saved;seal(p,"EXCEPTION_CLEARED",saved,v.signedFileIds)
        locks.release(businessType(),id);return@write p.id.value
    }
    private fun requireSafeEnd(r:CollectionSamplingDao,p:CollectionSamplingPlanDao):LocalDateTime {
        val ended=p.ended ?: fail("尚未核实实际取样结束")
        if(ended.safeToReleaseOriginal)return ended.endedAt
        val saved=p.incidentClearance ?: fail("取样异常须先完成实际事故处理并共同核实解限")
        phase(p,"EXCEPTION_CLEARED",saved)
        val current=incidents?.requireClearance(saved.record.incidentId,r.orgId,r.collectionId,IncidentSourceVo(businessType(),r.id.value,p.id.value),saved.record.confirmedAt) ?: fail("事故处理核验服务不可用")
        if(current!=saved.incidentProof)fail("事故处置依据已变化，不能沿用原解限记录")
        return saved.record.confirmedAt
    }
    private fun requireJoint(r:CollectionSamplingDao,p:CollectionSamplingPlanDao,conservation:String,keeper:String) { if(conservation!=p.content.conservationUserId || keeper!=p.content.keeperUserId)fail("须由本方案保护修复和保管责任人员共同核实");requirePeople(r,p) }
    private fun json(value:Any)=JsonUtils.toJson(value)!!
    private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
