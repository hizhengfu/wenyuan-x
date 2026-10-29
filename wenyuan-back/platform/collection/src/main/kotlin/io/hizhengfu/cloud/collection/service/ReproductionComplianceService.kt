package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class ReproductionComplianceService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val qualification: ObjectProvider<CollectionConservationQualificationPort>,
    private val locks: CollectionOperationLockService,
    private val originalAccess: CollectionOriginalAccessService,
    @param:org.springframework.context.annotation.Lazy private val incidents: CollectionIncidentService? = null,
) {
    companion object { const val BIZ="REPRODUCTION_COMPLIANCE_2026" }
    private fun fail(message: String): Nothing=throw BadRequestException(message)
    fun physical(r: ReproductionDao)=r.repType in setOf(EnumRepType.COPY,EnumRepType.RUBBING)
    private fun rows(id:String):List<ReproductionPlanDao> {
        val org=ReproductionDao.findById(id)?.orgId ?: fail("复制拓印记录不存在")
        if(ReproductionPlans.select(ReproductionPlans.orgId).where {ReproductionPlans.reproductionId eq id}.any {it[ReproductionPlans.orgId]!=org})fail("复制拓印方案所属馆与业务不一致")
        return ReproductionPlanDao.find {ReproductionPlans.reproductionId eq id}.sortedBy {it.version}
    }
    private fun latest(r: ReproductionDao)=rows(r.id.value).lastOrNull() ?: fail("请先登记实际复制拓印方案、资质及安全原件")
    private fun scoped(id: String,write: Boolean=false): ReproductionDao {
        val r=(if(write)ReproductionDao.find { Reproductions.id eq id }.forUpdate().singleOrNull() else ReproductionDao.findById(id)) ?: fail("复制拓印记录不存在")
        CollectionCurrentOperationAccess.require(scope,r.orgId,write,if(write)setOf(CollectionConst.STORAGE)else setOf(CollectionConst.STORAGE,CollectionConst.SELECT),"复制拓印办理")
        if(!physical(r))fail("摄影与三维采集应按数字采集方案办理，不能混用复制拓印许可")
        return r
    }
    private fun collection(r: ReproductionDao)=(CollectionDao.findById(r.collectionId) ?: fail("藏品不存在")).also { if(it.orgId!=r.orgId)fail("藏品所属馆已变化，请核实原业务资料") }
    private fun source(r: ReproductionDao)=json(mapOf("collectionId" to r.collectionId,"catalog" to CollectionCatalogMapper.fromCollection(collection(r).toModel()).copy(warehouseId=null),"repType" to r.repType,"purpose" to r.purpose,"repDate" to r.repDate))
    private fun required(r: ReproductionDao): EnumAuthorityLevel? {
        val c=collection(r)
        if(c.nature==null)fail("须先核实文物或其他藏品性质")
        if(!CollectionNaturePolicy.usesRelicRules(c.nature))return null
        return when(c.level){EnumRelicLevel.FIRST->EnumAuthorityLevel.NATIONAL;EnumRelicLevel.SECOND,EnumRelicLevel.THIRD->EnumAuthorityLevel.PROVINCE;EnumRelicLevel.GENERAL->null;else->fail("未依法区分等级的文物不能复制拓印")}
    }
    private fun snapshot(p: ReproductionPlanDao)=mapOf("reproductionId" to p.reproductionId,"version" to p.version,"sourceSnapshot" to p.sourceSnapshot,"qualification" to p.qualification,"content" to p.content)
    private fun permits(p:ReproductionPlanDao):List<CollectionAdministrativeRecordDao> {
        val matches=(CollectionAdministrativeRecords.bizType eq BIZ) and (CollectionAdministrativeRecords.bizId eq p.id.value)
        if(CollectionAdministrativeRecords.select(CollectionAdministrativeRecords.orgId).where {matches}.any {it[CollectionAdministrativeRecords.orgId]!=p.orgId})fail("行政原件所属馆与方案不一致")
        return CollectionAdministrativeRecordDao.find {matches}.toList()
    }
    private fun checks(p:ReproductionPlanDao):List<ReproductionSafetyDao> {
        if(ReproductionSafetyRecords.select(ReproductionSafetyRecords.orgId).where {ReproductionSafetyRecords.planId eq p.id.value}.any {it[ReproductionSafetyRecords.orgId]!=p.orgId})fail("安全检查所属馆与方案不一致")
        return ReproductionSafetyDao.find {ReproductionSafetyRecords.planId eq p.id.value}.toList()
    }
    private fun outputs(p:ReproductionPlanDao):List<ReproductionOutputDao> {
        if(ReproductionOutputs.select(ReproductionOutputs.orgId).where {ReproductionOutputs.planId eq p.id.value}.any {it[ReproductionOutputs.orgId]!=p.orgId})fail("产物所属馆与方案不一致")
        return ReproductionOutputDao.find {ReproductionOutputs.planId eq p.id.value}.toList()
    }
    private fun phase(p: ReproductionPlanDao,key: String,expected: Any,verifyContent:Boolean=true) {
        val a=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase eq key) }.singleOrNull() ?: fail("缺少本轮复制拓印永久原件：$key")
        if(a.orgId!=p.orgId || a.digest!=digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(json(expected)))fail("复制拓印记录与永久原件不一致")
        val files=ReproductionArchiveOriginalReferences.fileIds(key,a.snapshot) ?: fail("复制拓印阶段的原件清单规则未配置")
        archives.verifyExisting(a,p.orgId,BIZ,p.id.value,key,expected,files,verifyContent=verifyContent)
    }
    fun units(id: String)=scoped(id).let { r -> qualification.ifAvailable?.units(r.orgId).orEmpty() }
    private data class ReadPlan(val plan:ReproductionPlanDao,val permits:List<CollectionAdministrativeRecordDao>,val checks:List<ReproductionSafetyDao>,val outputs:List<Map<String,Any?>>,val archives:List<Map<String,Any?>>)
    private fun readPlans(r:ReproductionDao,verifyContent:Boolean=true):List<ReadPlan> = rows(r.id.value).map {p ->
        fun phase(p:ReproductionPlanDao,key:String,value:Any) = phase(p,key,value,verifyContent)
        phase(p,"PLAN",snapshot(p))
        if(JsonUtils.mapper.readTree(p.sourceSnapshot).path("collectionId").asString()!=r.collectionId)fail("复制拓印原方案与业务藏品身份不一致")
        val archiveRows=if(verifyContent) archives.listForAuthorizedOrg(BIZ,p.id.value,r.orgId) else archives.readForDisplay(BIZ,p.id.value,r.orgId).archives
        fun optional(key:String,value:Any?) {
            if(value!=null)phase(p,key,value)
            else if(archiveRows.any {it["phase"]==key})fail("不能清除复制拓印已归档的阶段：$key")
        }
        fun complete(prefix:String,ids:Set<String>) {
            val recorded=archiveRows.map {it["phase"] as String}.filter {it.startsWith(prefix)}.map {it.removePrefix(prefix)}.toSet()
            if(recorded!=ids)fail("复制拓印记录与永久原件清单不一致：$prefix")
        }
        optional("STARTED",p.started);optional("COMPLETED",p.completed);optional("STOPPED",p.stopped)
        if(p.stopped!=null && p.completed!=null)fail("实际停止与正常完成不能同时存在")
        p.clearance?.let { phase(p,"STOP_CLEARED_${it.version}",it) }
        val approvals=permits(p);val safety=checks(p);val products=outputs(p)
        approvals.forEach {phase(p,"ADMIN_${it.id.value}",it.content)}
        safety.forEach {phase(p,"SAFETY_${it.id.value}",it.content)}
        complete("ADMIN_",approvals.map {it.id.value}.toSet());complete("SAFETY_",safety.map {it.id.value}.toSet())
        complete("OUTPUT_",products.map {it.id.value}.toSet())
        complete("CUSTODY_",products.flatMap {it.custody?.records.orEmpty()}.map {it.id}.toSet())
        val useIds=mutableSetOf<String>();val returnIds=mutableSetOf<String>()
        val views=products.map {out ->
            if(out.number!=out.content.number)fail("产物专册编号与归档编号不一致")
            phase(p,"OUTPUT_${out.id.value}",out.content)
            val useRows=ReproductionOutputCustody.ordered(uses(out.id.value))
            val usage=useRows.map {u ->
                useIds+=u.id.value
                val bound=useProof(p,u,verifyContent)
                val returnedBound=if(u.returned==null)true else {returnIds+=u.id.value;returnProof(p,u,verifyContent)}
                mapOf("id" to u.id.value,"content" to u.content,"returned" to u.returned,"associationVerified" to (bound && returnedBound))
            }
            val custody=out.custody?.records.orEmpty()
            custody.forEach {phase(p,"CUSTODY_${it.id}",it)}
            ReproductionOutputCustody.verify(out,useRows,custody)
            val position=ReproductionOutputCustody.position(out,useRows,custody)
            val linked=usage.all {it["associationVerified"]==true}
            mapOf("id" to out.id.value,"content" to out.content,"uses" to usage,"custody" to mapOf(
                "position" to position,"revision" to ReproductionOutputCustody.revision(out,useRows,custody),
                "canTransfer" to (!position.inUse && linked),"history" to custody,
                "issue" to if(!linked)"旧使用原件未冻结产物关联，请核对原始凭证后再办理保管交接" else null))
        }
        complete("USE_",useIds);complete("RETURN_",returnIds)
        ReadPlan(p,approvals,safety,views,archiveRows)
    }
    fun detail(id:String):Map<String,Any?> {
        val r=scoped(id);val verified=readPlans(r,verifyContent=false);val latest=verified.lastOrNull()
        val current=runCatching {collection(r)}
        val currentSource=runCatching {source(r)}
        val authority=runCatching {required(r)}
        val sessionCanOperate=io.hizhengfu.cloud.common.utils.SecurityUtils.getUserDetail().authorities.any {it.authority in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.STORAGE)}
        val canOperate=sessionCanOperate && try {CollectionCurrentOperationAccess.require(scope,r.orgId,true,setOf(CollectionConst.STORAGE),"复制拓印办理");true} catch (_:BadRequestException) {false}
        return mapOf("id" to id,"collectionId" to r.collectionId,"orgId" to r.orgId,"repType" to r.repType,"purpose" to r.purpose,"canOperate" to canOperate,
            "sourceRevision" to currentSource.getOrNull()?.let(::digest),"flowStatus" to r.flowStatus,"flowInstanceId" to r.flowInstanceId,
            "sourceIssue" to (currentSource.exceptionOrNull()?.message ?: authority.exceptionOrNull()?.message),"requiredAuthority" to authority.getOrNull(),"nature" to current.getOrNull()?.nature,
            "plans" to verified.map {read ->val row=read.plan;mapOf("id" to row.id.value,"version" to row.version,"content" to row.content,"qualification" to row.qualification,"started" to row.started,"completed" to row.completed,"stopped" to row.stopped,"clearance" to row.clearance,"stopUnsafe" to ReproductionStopEvidence.unsafe(row,archives,verifyContent=false),
                "administrative" to read.permits.map {it.content.copy(id=it.id.value)},"safety" to read.checks.map {mapOf("id" to it.id.value,"content" to it.content)},"outputs" to read.outputs,"archives" to read.archives)},
            "nextSafetyCheckAt" to latest?.plan?.started?.let {(latest.checks.maxOfOrNull {c->c.content.checkedAt} ?: it.siteCheckedAt).plusDays(latest.plan.content.safetyCheckIntervalDays.toLong())})
    }
    /** 按本案、本版方案、永久档案阶段和文件引用定位冻结原件。 */
    fun requireArchivedOriginal(id:String,planId:String,archiveId:String,fileId:String) {
        if(!archiveId.matches(Regex("[0-9]{15,20}")) || !fileId.matches(Regex("[0-9]{15,20}")))fail("归档原件标识不正确")
        @Suppress("UNCHECKED_CAST")
        val plans=detail(id)["plans"] as List<Map<String,Any?>>
        val plan=plans.singleOrNull {it["id"]==planId} ?: fail("方案不属于本次复制拓印")
        @Suppress("UNCHECKED_CAST")
        val saved=plan["archives"] as List<Map<String,Any?>>
        val archive=saved.singleOrNull {it["id"]==archiveId} ?: fail("永久档案不属于本版方案")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as List<Map<String,Any?>>
        if(files.none {it["fileId"]==fileId})fail("该文件不是所选复制拓印阶段的原件")
    }
    fun requireEditable(r: ReproductionDao) { if(rows(r.id.value).any { it.started!=null })fail("已经实际开始复制拓印，不能改写、撤销或重新审批原记录") }
    fun savePlan(id: String,v: ReproductionPlanVo): String {
        val r=scoped(id,true);requireEditable(r)
        if(r.flowStatus!=EnumFlowStatus.DRAFT || r.flowInstanceId!=null)fail("已进入馆内办理的方案不得替换，请保留原件另行办理")
        val c=collection(r);required(r);val src=source(r);val old=readPlans(r).lastOrNull()?.plan
        if(v.sourceRevision!=digest(src))fail("藏品或业务资料已变化，请重新核实")
        if(c.catalogStatus!=EnumCatalogStatus.APPROVED || c.totalRegNo.isBlank() || !c.registered)fail("须先完成正式建账建档，不能以待登记藏品开展复制拓印")
        if(old?.content==v && old.sourceSnapshot==src)return old.id.value
        if(v.preparedAt.isAfter(LocalDateTime.now()) || old?.content?.preparedAt?.isAfter(v.preparedAt)==true)fail("请如实填写方案形成时间")
        if(v.maxQuantity<1 || v.safetyCheckIntervalDays !in 1..365 || !v.originalSuitable)fail("须核实原件适宜性、限定数量及实际安全检查间隔")
        if(v.massProduction && v.usesOriginal)fail("批量制作复制品或拓片不得使用文物原件")
        if(r.repType==EnumRepType.RUBBING && CollectionNaturePolicy.usesRelicRules(c.nature)) {
            if(v.rubbingYuanOrEarlier==null)fail("拓印文物须根据年代原件核实是否元代及以前")
            if(v.rubbingYuanOrEarlier && v.usesOriginal)fail("元代及以前文物须使用翻刻副版拓印")
            if(v.usesOriginal && !v.noHardToolBeating)fail("原件拓印不得使用尖硬器具捶打")
        }
        if(listOf(v.undertakingUnitName,v.noDamageMethod,v.conditionFileIds,v.eraAndMethodBasisFileIds,v.limitedQuantityReason,v.siteAddress,v.safetyStandards,v.safetyPlanFileIds,v.contractFileIds,v.signedPlanFileIds,v.confidentialityAssessment,v.confidentialityFileIds).any(String::isBlank))fail("须补齐实际承担单位、无损方法、现状及年代依据、数量理由、场所安全、合同、保密核查与签署方案")
        if(v.keeperUserId==v.leaderUserId)fail("保管负责人和主要负责人应分别履职")
        users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD);users.requireRole(v.leaderUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        val q=if(CollectionNaturePolicy.usesRelicRules(c.nature)) {
            val port=qualification.ifAvailable ?: fail("资质管理模块不可用，不能核验文物复制拓印承担单位")
            val value=port.requireQualified(r.orgId,v.undertakingUnitId ?: fail("须选择实际承担单位"),v.preparedAt.toLocalDate())
            if(v.undertakingUnitName!=value.unitName || v.scopeConfirmed!=true || v.scopeCheckedAt==null || v.scopeCheckedAt.isAfter(v.preparedAt) || v.scopeCheckedAt.toLocalDate().isBefore(value.issuedOn) || v.scopeReviewFileIds.isNullOrBlank())fail("须依据单位资质原件核实本次材质及复制拓印事项的业务范围，并留存实际核查原件")
            value
        } else null
        if(old!=null && (v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank()))fail("订正方案须说明原因并保留原件")
        val p=ReproductionPlanDao.new { orgId=r.orgId;reproductionId=id;version=(old?.version ?: 0)+1;sourceSnapshot=src;qualification=q;content=v }
        sealActivity(r,p,"PLAN",snapshot(p),planFiles(p),listOf(ReproductionWriteEvidence.plan(p)),responsibilities={
            users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            users.requireRole(v.leaderUserId,r.orgId,CollectionConst.ROLE_DIRECTOR)
        });return p.id.value
    }
    private fun planFiles(p: ReproductionPlanDao)=p.content.let { listOfNotNull(it.conditionFileIds,it.eraAndMethodBasisFileIds,it.scopeReviewFileIds,it.safetyPlanFileIds,it.contractFileIds,it.signedPlanFileIds,it.confidentialityFileIds,it.replacementFileIds,p.qualification?.fileId).joinToString(",") }
    fun requirePrepared(r: ReproductionDao): ReproductionPlanDao {
        val p=latest(r);if(!CollectionCatalogBaselineComparison.sameSource(p.sourceSnapshot,source(r)) || p.orgId!=r.orgId)fail("原件对应的藏品或业务方案已变化")
        phase(p,"PLAN",snapshot(p));val needed=required(r)
        val evidence=permits(p);evidence.forEach { if(it.orgId!=r.orgId)fail("行政原件所属馆不一致");phase(p,"ADMIN_${it.id.value}",it.content) }
        if(needed!=null && evidence.none { it.content.action==EnumAdministrativeAction.APPROVAL && it.content.level==needed })fail("缺少本方案分级行政批准原件，馆内审批不能代替")
        return p
    }
    fun administrative(id: String,v: ReproductionAdministrativeVo): String {
        val r=scoped(id,true);val p=latest(r)
        if(r.flowInstanceId!=null || p.started!=null || p.id.value!=v.planId)fail("须在馆内办理前登记当前方案对应的实际批文")
        phase(p,"PLAN",snapshot(p));if(!CollectionCatalogBaselineComparison.sameSource(p.sourceSnapshot,source(r)))fail("方案基线已变化")
        val a=v.evidence;val needed=required(r)
        if(a.action!=EnumAdministrativeAction.APPROVAL || (needed!=null && a.level!=needed) || listOf(a.authorityName,a.documentNo,a.fileIds).any(String::isBlank) || a.issuedAt.isBefore(p.content.preparedAt.toLocalDate()) || a.issuedAt.isAfter(LocalDate.now()))fail("请据实登记本方案适用层级的行政批准文书、实际日期和原件")
        permits(p).find { it.content==a }?.let { return it.id.value }
        val row=CollectionAdministrativeRecordDao.new {orgId=r.orgId;bizType=BIZ;bizId=p.id.value;content=a}
        sealActivity(r,p,"ADMIN_${row.id.value}",a,a.fileIds,listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.administrative(row)));return row.id.value
    }
    fun actors(r: ReproductionDao)=latest(r).content.let { linkedMapOf("keeper" to it.keeperUserId,"leader" to it.leaderUserId) }
    fun formData(r:ReproductionDao):Map<String,Any?> = readPlans(scoped(r.id.value)).lastOrNull()?.let {read ->val p=read.plan;mapOf("planId" to p.id.value,"plan" to p.content,"qualification" to p.qualification,"administrative" to read.permits.map {it.content},"sourceSnapshot" to p.sourceSnapshot)}.orEmpty()
    fun submit(r: ReproductionDao) {
        // FlowService 已读取实体；锁定后以数据库原始行再核实，防止并发提交复用旧草稿。
        val current=Reproductions.selectAll().where { Reproductions.id eq r.id.value }.forUpdate().single()
        if(current[Reproductions.flowInstanceId]!=null || current[Reproductions.flowStatus] !in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.REJECT,EnumFlowStatus.CANCEL))fail("本记录已进入办理，请刷新核实当前流程")
        if(current[Reproductions.collectionId]!=r.collectionId || current[Reproductions.repType]!=r.repType || current[Reproductions.purpose]!=r.purpose || current[Reproductions.repDate]!=r.repDate)fail("办理资料已变化，请刷新核实方案")
        requireEditable(r);val p=requirePrepared(r);archives.seal(r.orgId,BIZ,p.id.value,"SUBMITTED",snapshot(p),null)
    }
    fun requireActor(r: ReproductionDao,node: String?,operator: String?,comment: String?) {
        val expected=actors(r)[node] ?: return
        if(expected!=operator || comment.isNullOrBlank())fail("指定负责人须本人填写实际确认或批准意见")
        users.requireRole(expected,r.orgId,if(node=="keeper")CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR)
    }
    private fun approvals(r: ReproductionDao): List<FlowInstanceLogDao> {
        val instance=r.flowInstanceId ?: fail("缺少馆内审批实例")
        val all=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instance)
        val logs=actors(r).map { (node,user)->all.lastOrNull { it.nodeKey==node && it.operator==user && it.taskId!=null && !it.comment.isNullOrBlank() } ?: fail("缺少本轮保管确认和主要负责人批准，旧 PASS 或跳节点不能代替") }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(logs)
        return logs
    }
    fun confirm(r: ReproductionDao) { val p=requirePrepared(r);phase(p,"SUBMITTED",snapshot(p));val logs=approvals(r);archives.seal(r.orgId,BIZ,p.id.value,"APPROVED_${logs.last().taskId!!.takeLast(40)}",logs.map { it.toVo() },null) }
    private fun approved(r: ReproductionDao,p: ReproductionPlanDao): LocalDateTime {
        if(r.flowStatus!=EnumFlowStatus.PASS)fail("须完成本轮实际馆内批准")
        val logs=approvals(r);phase(p,"SUBMITTED",snapshot(p));phase(p,"APPROVED_${logs.last().taskId!!.takeLast(40)}",logs.map { it.toVo() });return logs.last().createTime
    }
    fun start(id: String,v: ReproductionStartVo): String {
        val actorId=io.hizhengfu.cloud.common.utils.SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理复制拓印")
        val r=scoped(id,true);val identity=r.toModel()
        CollectionDao.find { Collections.id eq r.collectionId }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        // 行锁等待和原件读取均可能跨越资格变化；重复开始也必须按当前账号及馆关系复核。
        fun verify() {
            TransactionManager.current().entityCache.flush()
            if(io.hizhengfu.cloud.common.utils.SecurityUtils.getNullUserDetail()?.id!=actorId)fail("登记期间办理账号已变化，请重新登录核实")
            r.refresh(flush=false)
            if(r.toModel()!=identity)fail("登记期间复制拓印业务身份已变化，请刷新核实")
            CollectionOrgDao.findById(r.orgId)?.refresh(flush=false)
            CollectionCurrentOperationAccess.require(scope,r.orgId,true,setOf(CollectionConst.STORAGE),"复制拓印办理")
        }
        verify()
        val p=requirePrepared(r);if(p.stopped!=null)fail("本批已实际停止，不能重新开始；继续制作须另行办理方案");val approvedAt=approved(r,p)
        p.started?.let { phase(p,"STARTED",it);if(it==v){verify();return p.id.value};fail("实际开始原件不能覆盖") }
        if(v.planId!=p.id.value || v.startedAt.isBefore(approvedAt) || v.startedAt.isAfter(LocalDateTime.now()) || v.siteCheckedAt.isBefore(p.content.preparedAt) || v.siteCheckedAt.isAfter(v.startedAt) || v.siteCheckedAt.plusDays(p.content.safetyCheckIntervalDays.toLong()).isBefore(v.startedAt) || !v.siteSafe || v.safetyCheckFileIds.isBlank() || v.signedStartFileIds.isBlank())fail("须在本轮批准及有效场所安全核查后据实登记开始")
        requireActivityQualification(r,p,v.startedAt)
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        if(p.content.usesOriginal) {
            originalAccess.requireAccess(r,v.startedAt,v.operatorUserId,v.requisitionId,v.onSiteWarehouseLogId)
            locks.acquire(r.orgId,BIZ,r.id.value,listOf(r.collectionId))
        }
        else if(v.requisitionId!=null || v.onSiteWarehouseLogId!=null)fail("方案不使用原件，不能据此登记原件调取")
        p.started=v
        sealActivity(r,p,"STARTED",v,"${v.safetyCheckFileIds},${v.signedStartFileIds}",listOf(ReproductionWriteEvidence.plan(p)),v.startedAt,
            responsibilities={users.requireMuseumMember(v.operatorUserId,r.orgId)})
        verify()
        return p.id.value
    }
    private fun requireActivityQualification(r: ReproductionDao,p: ReproductionPlanDao,at: LocalDateTime) {
        p.qualification?.let { old ->
            val current=qualification.ifAvailable?.requireQualified(r.orgId,old.unitId,at.toLocalDate())
                ?: fail("无法核验本次实际活动日期的单位资质")
            if(current!=old)fail("实际活动期间单位资质已经变化，请停止实施并重新核实方案")
        }
    }
    private fun verifyWrittenQualification(r: ReproductionDao,p: ReproductionPlanDao,at: LocalDateTime) {
        // 执行所有待写 SQL 后再核验；失败由外层事务回滚本次活动和永久档案，不提交半成品。
        TransactionManager.current().entityCache.flush()
        requireActivityQualification(r,p,at)
    }
    private fun sealActivity(
        r: ReproductionDao,p: ReproductionPlanDao,phase: String,value: Any,fileIds: String,
        evidence: List<() -> Unit>,activityAt: LocalDateTime?=null,responsibilities: () -> Unit = {},
    ) {
        val orgId=r.orgId;val planId=p.id.value
        val actorId=io.hizhengfu.cloud.common.utils.SecurityUtils.getUserId()
        val expected=json(value)
        val business=ReproductionWriteEvidence.business(r)
        val archiveId=archives.seal(orgId,BIZ,planId,phase,value,fileIds)
        // 先执行全部待写 SQL；资质服务及归档回调产生的变化也必须在核对范围内。
        if(activityAt!=null)verifyWrittenQualification(r,p,activityAt)
        else TransactionManager.current().entityCache.flush()
        business();evidence.forEach { it() }
        ReproductionWriteEvidence.archive(archiveId,orgId,planId,phase,expected)()
        // 末尾复查当前账号和原办理馆；不能使用登录快照或写前缓存的馆状态。
        TransactionManager.current().entityCache.flush()
        // 只复验本次新增登记的责任人员；不能用经办人权限代替其任职，也不重验历史签署人员。
        responsibilities()
        if(io.hizhengfu.cloud.common.utils.SecurityUtils.getNullUserDetail()?.id!=actorId)fail("登记期间办理账号已变化，请重新登录核实")
        CollectionOrgDao.findById(orgId)?.refresh(flush=false)
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.STORAGE),"复制拓印办理")
    }
    private fun workable(r: ReproductionDao,p: ReproductionPlanDao,at: LocalDateTime) {
        if(p.stopped!=null)fail("本批已实际停止，不能继续制作或改为正常完成")
        val started=p.started ?: fail("须先登记实际开始")
        phase(p,"STARTED",started)
        requireActivityQualification(r,p,at)
        if(p.content.usesOriginal)locks.requireUnlocked(r.collectionId,BIZ,r.id.value)
        if(p.content.usesOriginal && collection(r).inventoryHold)fail("原件有未处理的清点异常，须先核实处置")
        if(at.isBefore(started.startedAt) || at.isAfter(LocalDateTime.now()))fail("须如实填写实际开始后的时间")
        val checks=checks(p);checks.forEach { if(it.orgId!=r.orgId)fail("安全检查所属馆不一致");phase(p,"SAFETY_${it.id.value}",it.content) }
        val effective=checks.filter { !it.content.checkedAt.isAfter(at) }
        val resolved=effective.filter { it.content.safe }.flatMap { it.content.resolvesRecordIds }.toSet()
        if(effective.any { !it.content.safe && it.id.value !in resolved })fail("场所或原件安全问题尚未实际处理，不能继续制作或完成")
        val last=effective.maxOfOrNull { it.content.checkedAt } ?: started.siteCheckedAt
        if(last.plusDays(p.content.safetyCheckIntervalDays.toLong()).isBefore(at))fail("须按方案补齐实际安全检查")
    }
    fun safety(id: String,v: ReproductionSafetyVo): String {
        val r=scoped(id,true);val p=readPlans(r).lastOrNull()?.plan ?: fail("请先登记实际方案");val start=p.started ?: fail("请先登记实际开始")
        if(p.stopped?.stoppedAt?.isAfter(v.checkedAt)==true)fail("停止后的检查须登记实际后续时间，不能改写停止前原件")
        if(p.completed!=null || v.checkedAt.isBefore(start.startedAt) || v.checkedAt.isAfter(LocalDateTime.now()) || listOf(v.findings,v.measures,v.fileIds).any(String::isBlank))fail("须在实际实施期间据实记录安全检查、问题和有效处置原件")
        users.requireMuseumMember(v.checkerUserId,r.orgId);val previous=checks(p)
        previous.find { it.content==v }?.let { phase(p,"SAFETY_${it.id.value}",it.content);return it.id.value }
        v.resolvesRecordIds.forEach { key->val old=previous.find { it.id.value==key } ?: fail("只能关联本方案原问题");if(!v.safe || old.content.safe || !v.checkedAt.isAfter(old.content.checkedAt))fail("安全复查须在原问题发生后实际确认处理") }
        val row=ReproductionSafetyDao.new { orgId=r.orgId;planId=p.id.value;content=v }
        sealActivity(r,p,"SAFETY_${row.id.value}",v,v.fileIds,listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.safety(row)),
            responsibilities={users.requireMuseumMember(v.checkerUserId,r.orgId)})
        return row.id.value
    }
    fun output(id: String,v: ReproductionOutputVo): String {
        val r=scoped(id,true)
        val p=if(latest(r).stopped!=null)readPlans(r).last().plan else requirePrepared(r)
        if(p.completed!=null)fail("实施资料已封存，不能继续追加本批制作产物")
        val stopped=p.stopped
        if(stopped==null)workable(r,p,v.madeAt)
        else {
            val started=p.started ?: fail("缺少实际开始原件");phase(p,"STARTED",started)
            if(v.madeAt.isBefore(started.startedAt) || v.madeAt.isAfter(stopped.stoppedAt))fail("停止后只能补登记停止前实际制作的产物，不能继续生产")
            p.qualification?.let {q->if(v.madeAt.toLocalDate().isBefore(q.issuedOn) || q.expiresOn?.isBefore(v.madeAt.toLocalDate())==true)fail("产物制作日期不在原方案资质有效期内，请核查异常事实")}
        }
        if(v.registeredAt.isBefore(v.madeAt) || v.registeredAt.isAfter(LocalDateTime.now()) || v.number.isBlank() || v.number.length>100 || v.number!=v.number.trim() || !v.marked || listOf(v.name,v.markingFileIds,v.location,v.purpose,v.receiptFileIds).any(String::isBlank))fail("须逐件填写真实制作和登记时间、标识编号、影像、保管位置、用途及接收原件")
        if(v.purpose!=JsonUtils.mapper.readTree(p.sourceSnapshot).path("purpose").asString())fail("产物用途须与本次批准方案一致，不得挪作他用")
        users.requireMuseumMember(v.custodianUserId,r.orgId)
        if(v.markedUnitName!=p.content.undertakingUnitName || v.quantitySerial<1 || v.quantitySerial>p.content.maxQuantity)fail("须核实产物标明的实际制作单位和本批数量编号")
        if(r.repType==EnumRepType.RUBBING && v.markedMadeAt!=v.madeAt)fail("拓片须标明本次实际拓印时间")
        val all=outputs(p);all.find { it.number==v.number && it.content==v }?.let { phase(p,"OUTPUT_${it.id.value}",it.content);return it.id.value }
        if(all.any { it.content.quantitySerial==v.quantitySerial })fail("本批数量编号已使用")
        if(all.size>=p.content.maxQuantity)fail("产物数量已达到本次限定数量，不得超量制作")
        if(ReproductionOutputDao.find { (ReproductionOutputs.orgId eq r.orgId) and (ReproductionOutputs.number eq v.number) }.any())fail("产物编号已使用，不能重复")
        val row=ReproductionOutputDao.new {orgId=r.orgId;planId=p.id.value;number=v.number;content=v}
        sealActivity(r,p,"OUTPUT_${row.id.value}",v,"${v.markingFileIds},${v.receiptFileIds}",listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.outputs(p,all+row)),if(stopped==null)v.madeAt else null,
            responsibilities={users.requireMuseumMember(v.custodianUserId,r.orgId)})
        return row.id.value
    }
    fun complete(id: String,v: ReproductionCompletionVo): String {
        val r=scoped(id,true);val p=requirePrepared(r)
        p.completed?.let { phase(p,"COMPLETED",it);if(it==v)return p.id.value;fail("实际完成原件不能覆盖") }
        workable(r,p,v.completedAt);users.requireMuseumMember(v.operatorUserId,r.orgId)
        if(!v.originalUndamaged || listOf(v.condition,v.conditionFileIds,v.signedCompletionFileIds).any(String::isBlank))fail("须核实原件未受损并留存现状、完成签署；发现损伤应先记录安全问题并依法处理")
        val all=outputs(p)
        if(checks(p).any { it.content.checkedAt.isAfter(v.completedAt) })fail("实际完成时间不得早于本批已经发生的安全检查")
        if(all.isEmpty() || all.any { it.content.madeAt.isAfter(v.completedAt) || it.content.registeredAt.isAfter(v.completedAt) })fail("须先登记全部实际产物，再确认实际完成时间")
        all.forEach { phase(p,"OUTPUT_${it.id.value}",it.content) }
        p.completed=v;locks.release(BIZ,r.id.value)
        sealActivity(r,p,"COMPLETED",v,"${v.conditionFileIds},${v.signedCompletionFileIds}",listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.outputs(p,all)),v.completedAt,
            responsibilities={users.requireMuseumMember(v.operatorUserId,r.orgId)})
        return p.id.value
    }
    fun stop(id: String,v: ReproductionStopVo): String {
        val r=scoped(id,true);val read=readPlans(r).lastOrNull() ?: fail("请先登记实际方案")
        val p=read.plan;val started=p.started ?: fail("须先有本批实际开始原件")
        p.stopped?.let { if(it==v)return p.id.value;fail("实际停止原件不能覆盖；后续情况请追加安全检查或事故处置") }
        if(p.completed!=null || v.planId!=p.id.value)fail("已正常完成或所选方案不一致，不能改为实际停止")
        if(v.stoppedAt.isBefore(started.startedAt) || v.stoppedAt.isAfter(LocalDateTime.now()) ||
            listOf(v.reason,v.actualLocation,v.condition,v.actualMeasures,v.followUp,v.conditionFileIds,v.signedStopFileIds).any(String::isBlank))fail("请如实填写停止时间、原因、实际保管情况、后续安排及签署原件")
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        v.custodianUserId?.takeIf(String::isNotBlank)?.let {users.requireMuseumMember(it,r.orgId)}
        if(v.safeForCustody && (!v.originalUndamaged || !v.physicallyPresent || v.custodianUserId.isNullOrBlank()))fail("确认保管安全须核实原件未受损、实物在场及实际保管人员；不明或不安全应如实记录")
        val checks=read.checks;val products=outputs(p)
        if(checks.any {it.content.checkedAt.isAfter(v.stoppedAt)} || products.any {it.content.madeAt.isAfter(v.stoppedAt) || it.content.registeredAt.isAfter(v.stoppedAt)})fail("停止时间不得早于已发生的本批检查、制作及产物登记")
        if(v.safeForCustody && (ReproductionStopEvidence.unresolved(p,archives) || collection(r).inventoryHold || CollectionIncidentItemDao.find {(CollectionIncidentItems.collectionId eq r.collectionId) and (CollectionIncidentItems.restricted eq true)}.any()))fail("已有安全、事故或清点问题未核实处理，不能确认保管安全")
        p.stopped=v
        // 释放本次生产占用，使归还接收可以办理；独立安全保护仍由安全守卫维持。
        locks.release(BIZ,r.id.value)
        sealActivity(r,p,"STOPPED",v,"${v.conditionFileIds},${v.signedStopFileIds}",listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.outputs(p,products)),responsibilities={
            users.requireMuseumMember(v.operatorUserId,r.orgId)
            v.custodianUserId?.takeIf(String::isNotBlank)?.let {users.requireMuseumMember(it,r.orgId)}
        })
        return p.id.value
    }
    fun clearStop(id:String,v:IncidentClearanceVo):String {
        val r=scoped(id,true);val p=readPlans(r).lastOrNull()?.plan ?: fail("复制拓印方案不存在")
        val stopped=p.stopped ?: fail("须先登记实际停止及异常保管事实")
        if(!ReproductionStopEvidence.unsafe(p,archives))fail("本批没有待核对的停止安全异常")
        if(v.confirmedAt.isBefore(stopped.stoppedAt) || v.confirmedAt.isAfter(LocalDateTime.now()) || listOf(v.condition,v.actualMeasures,v.signedFileIds).any(String::isBlank))fail("请核实实际处置核对时间、现状、措施及签署原件")
        users.requireRole(v.conservationUserId,r.orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        if(v.keeperUserId!=io.hizhengfu.cloud.common.utils.SecurityUtils.getUserId() || v.keeperUserId==v.conservationUserId)fail("保管负责人须本人核对，并与保护修复责任人分别履职")
        if(collection(r).inventoryHold)fail("清点异常尚未处理，不能据此放行")
        val proof=(incidents ?: fail("事故处置核查服务不可用")).requireClearance(v.incidentId,r.orgId,r.collectionId,IncidentSourceVo(BIZ,id,p.id.value),v.confirmedAt,keeperUserId=v.keeperUserId)
        val old=p.clearance
        if(old!=null && v.confirmedAt.isBefore(old.record.confirmedAt))fail("追加核对不得早于既有核对时间")
        if(old?.record==v && old.incidentProof==proof)return p.id.value
        val saved=ReproductionStopClearanceSnapshot(v,proof,(old?.version ?: 0)+1);p.clearance=saved
        sealActivity(r,p,"STOP_CLEARED_${saved.version}",saved,v.signedFileIds,listOf({
            val current=incidents!!.requireClearance(v.incidentId,r.orgId,r.collectionId,IncidentSourceVo(BIZ,id,p.id.value),v.confirmedAt,keeperUserId=v.keeperUserId)
            if(current!=proof)fail("登记期间事故处置依据已变化，本次核对未完成")
        },ReproductionWriteEvidence.plan(p)),responsibilities={
            users.requireRole(v.conservationUserId,r.orgId,CollectionConst.ROLE_CONSERVATION)
            users.requireRole(v.keeperUserId,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        })
        return p.id.value
    }
    private fun uses(id:String):List<ReproductionOutputUseDao> {
        val org=ReproductionOutputDao.findById(id)?.orgId ?: fail("产物不存在")
        if(ReproductionOutputUses.select(ReproductionOutputUses.orgId).where {ReproductionOutputUses.outputId eq id}.any {it[ReproductionOutputUses.orgId]!=org})fail("产物使用记录所属馆不一致")
        return ReproductionOutputUseDao.find {ReproductionOutputUses.outputId eq id}.toList()
    }
    private fun useValue(u:ReproductionOutputUseDao)=mapOf("outputId" to u.outputId,"content" to u.content)
    private fun returnValue(u:ReproductionOutputUseDao)=mapOf("outputId" to u.outputId,"useId" to u.id.value,"content" to u.returned)
    /** 旧格式原文继续可查，但不据此宣称历史产物关联已冻结。 */
    private fun associationProof(p:ReproductionPlanDao,key:String,current:Any,legacy:Any,verifyContent:Boolean=true):Boolean {
        val original=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase eq key)}.singleOrNull() ?: fail("缺少产物使用永久原件")
        if(original.orgId!=p.orgId || original.digest!=digest(original.snapshot))fail("产物使用原件所属馆或摘要不一致")
        val bound=JsonUtils.mapper.readTree(original.snapshot).has("outputId")
        phase(p,key,if(bound)current else legacy,verifyContent)
        return bound
    }
    private fun useProof(p:ReproductionPlanDao,u:ReproductionOutputUseDao,verifyContent:Boolean=true)=associationProof(p,"USE_${u.id.value}",useValue(u),u.content,verifyContent)
    private fun returnProof(p:ReproductionPlanDao,u:ReproductionOutputUseDao,verifyContent:Boolean=true)=associationProof(p,"RETURN_${u.id.value}",returnValue(u),u.returned ?: fail("实际归还尚未登记"),verifyContent)
    fun transferCustody(id:String,outputId:String,v:ReproductionOutputCustodyVo):String {
        val r=scoped(id,true)
        if(v.expectedOrgId!=r.orgId)fail("原表单收藏单位不一致，请重新核对本件产物")
        val p=readPlans(r).lastOrNull()?.plan ?: fail("复制拓印方案不存在")
        val out=outputs(p).find {it.id.value==outputId} ?: fail("产物不属于本次方案")
        val old=out.custody?.records.orEmpty()
        old.find {it.record==v}?.let {return it.id}
        val usage=ReproductionOutputCustody.ordered(uses(outputId))
        if(usage.any {!useProof(p,it) || (it.returned!=null && !returnProof(p,it))})fail("旧使用原件的产物关联尚未核实，不能据此确认新的保管责任")
        val previous=ReproductionOutputCustody.position(out,usage,old)
        if(previous.inUse || usage.any {it.returned==null})fail("本件产物仍在使用中，须先据实办理使用归还")
        if(v.expectedRevision!=ReproductionOutputCustody.revision(out,usage,old) ||
            v.fromCustodianUserId!=previous.custodianUserId || v.fromLocation!=previous.location)fail("保管人、位置或使用归还资料已变化，请重新核对交接来源")
        if(v.handedOverAt.isBefore(previous.since) || v.handedOverAt.isAfter(LocalDateTime.now()) ||
            listOf(v.toCustodianUserId,v.toLocation,v.condition,v.reason,v.signedFileIds).any(String::isBlank))fail("请据实填写交接时间、新保管人员和位置、逐件现状、原因及双方签署原件")
        if(v.toCustodianUserId==v.fromCustodianUserId && v.toLocation==v.fromLocation)fail("保管人员和位置均未变化，无须另建交接；现状问题请依实际事实处理")
        users.requireMuseumMember(v.toCustodianUserId,r.orgId)
        val originalEvidence=ReproductionWriteEvidence.existingArchives(p)
        val record=ReproductionOutputCustodyRecord(io.hizhengfu.cloud.common.utils.IdGenerate.nextId(),outputId,old.size+1,v,usage.map {it.id.value},previous)
        out.custody=ReproductionOutputCustodyLedger(old+record)
        sealActivity(r,p,"CUSTODY_${record.id}",record,v.signedFileIds,
            listOf(originalEvidence,ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.output(out))+usage.map {ReproductionWriteEvidence.use(it)},
            responsibilities={users.requireMuseumMember(v.toCustodianUserId,r.orgId)})
        return record.id
    }
    fun recordUse(id: String,outputId: String,v: ReproductionOutputUseVo): String {
        val r=scoped(id,true);val p=readPlans(r).lastOrNull()?.plan ?: fail("复制拓印方案不存在");phase(p,"PLAN",snapshot(p))
        val done=p.completed ?: fail("实际制作和产物登记完成后才能办理产物使用")
        phase(p,"COMPLETED",done)
        val out=outputs(p).find { it.id.value==outputId } ?: fail("产物不属于本次方案");phase(p,"OUTPUT_${out.id.value}",out.content)
        if(v.purpose!=out.content.purpose || listOf(v.useLocation,v.signedFileIds).any(String::isBlank) || v.occurredAt.isBefore(done.completedAt) || v.occurredAt.isAfter(LocalDateTime.now()))fail("产物须按原批准用途据实办理使用，登记实际时间、位置及签署原件")
        users.requireMuseumMember(v.responsibleUserId,r.orgId)
        uses(outputId).find { it.content==v }?.let {useProof(p,it);return it.id.value}
        if(out.custody?.records?.lastOrNull()?.record?.handedOverAt?.isAfter(v.occurredAt)==true)fail("使用时间不得早于已经登记的保管交接")
        val previous=uses(outputId)
        previous.forEach { useProof(p,it);it.returned?.let { ret->returnProof(p,it) } }
        if(previous.any { it.returned==null || it.returned!!.returnedAt.isAfter(v.occurredAt) })fail("上次产物使用尚未实际归还，或本次使用时间早于前次归还")
        val row=ReproductionOutputUseDao.new {orgId=r.orgId;this.outputId=outputId;content=v}
        sealActivity(r,p,"USE_${row.id.value}",useValue(row),v.signedFileIds,listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.output(out),ReproductionWriteEvidence.use(row)),
            responsibilities={users.requireMuseumMember(v.responsibleUserId,r.orgId)})
        return row.id.value
    }
    fun returnOutput(id: String,outputId: String,useId: String,v: ReproductionOutputReturnVo): String {
        val r=scoped(id,true);val p=readPlans(r).lastOrNull()?.plan ?: fail("复制拓印方案不存在");phase(p,"PLAN",snapshot(p))
        val out=outputs(p).find { it.id.value==outputId } ?: fail("产物不属于本次方案")
        phase(p,"OUTPUT_${out.id.value}",out.content)
        val use=uses(outputId).find { it.id.value==useId && it.orgId==r.orgId } ?: fail("产物使用记录不存在")
        useProof(p,use)
        use.returned?.let { returnProof(p,use);if(it==v)return use.id.value;fail("实际归还原件不能覆盖") }
        if(v.returnedAt.isBefore(use.content.occurredAt) || v.returnedAt.isAfter(LocalDateTime.now()) || listOf(v.location,v.condition,v.signedFileIds).any(String::isBlank))fail("须核实实际归还时间、保管位置、现状及双方签署原件")
        users.requireMuseumMember(v.custodianUserId,r.orgId)
        use.returned=v
        sealActivity(r,p,"RETURN_${use.id.value}",returnValue(use),v.signedFileIds,listOf(ReproductionWriteEvidence.plan(p),ReproductionWriteEvidence.output(out),ReproductionWriteEvidence.use(use)),
            responsibilities={users.requireMuseumMember(v.custodianUserId,r.orgId)})
        return use.id.value
    }
    private fun json(v: Any)=JsonUtils.toJson(v)!!
    private fun digest(v: String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
}
