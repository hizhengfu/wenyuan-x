package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.BIZ
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.catalog
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.check
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.checks
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.checkValue
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.digest
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.fail
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.json
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.plan
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.plans
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.planValue
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.startValue
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.unsafe
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.work
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence.works
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class CollectionCareService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val originals: CollectionOriginalAccessService,
    private val locks: CollectionOperationLockService,
    private val incidents: CollectionIncidentService,
) {
    private fun collection(id: String,write: Boolean=false): CollectionDao {
        val c=(if(write)CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull() else CollectionDao.findById(id)) ?: fail("藏品不存在")
        CollectionCurrentOperationAccess.require(scope,c.orgId,write,setOf(CollectionConst.CARE),"日常检查养护")
        return c
    }
    /** Actual SQL and archive binding may cross qualification changes; reject inside the same transaction. */
    private fun <T> write(id: String, block: (CollectionDao, (() -> Unit) -> Unit) -> T): T {
        val actorId=SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理检查养护")
        val c=collection(id,true)
        val orgId=c.orgId
        val rechecks=mutableListOf<() -> Unit>()
        fun verifyWriter() {
            TransactionManager.current().entityCache.flush()
            c.refresh(flush=false)
            if(c.orgId!=orgId)fail("写入期间藏品所属馆已变化，请刷新核实")
            if(SecurityUtils.getNullUserDetail()?.id!=actorId)fail("写入期间养护办理账号已变化，请重新登录")
            CollectionOrgDao.findById(orgId)?.refresh(flush=false)
            CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.CARE),"日常检查养护")
        }
        verifyWriter()
        val result=block(c) { rechecks.add(it) }
        verifyWriter()
        rechecks.forEach { it() }
        return result
    }
    /** 新方案、确认、检查和开工依赖当前资料；历史事实的结束和重放保留冻结依据。 */
    private fun recheckCatalog(c:CollectionDao,recheck:(() -> Unit) -> Unit) {
        val expected=json(catalog(c))
        recheck {if(json(catalog(c))!=expected)fail("写入期间藏品资料已变化，请刷新后重新核实检查养护依据")}
    }
    private fun ownPlan(c: CollectionDao,id: String): CollectionCarePlanDao = (CollectionCarePlanDao.findById(id) ?: fail("养护方案不存在")).also {
        if(it.orgId!=c.orgId || it.collectionId!=c.id.value)fail("养护方案不属于本馆本件藏品");verifiedPlan(it)
    }
    private fun people(p: CollectionCarePlanDao) {
        if(p.content.conservationUserId==p.content.keeperUserId)fail("保护修复与保管确认人员须分别指定")
        users.requireMuseumMember(p.content.conservationUserId,p.orgId);users.requireRole(p.content.conservationUserId,p.orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireMuseumMember(p.content.keeperUserId,p.orgId);users.requireRole(p.content.keeperUserId,p.orgId,CollectionConst.ROLE_STORAGE_HEAD)
    }
    private fun self(org: String,id: String) { if(SecurityUtils.getUserId()!=id)fail("须由实际办理人员本人操作，管理员不能代签");users.requireMuseumMember(id,org) }
    private fun required(vararg values: String) { if(values.any(String::isBlank))fail("请完整登记实际现状、措施和签署原件") }
    private fun actual(at: LocalDateTime) { if(at.isAfter(LocalDateTime.now()))fail("实际发生时间不能晚于当前时间") }
    private fun custody(org: String,present: Boolean,custodian: String?,safe: Boolean) {
        if(safe && (!present || custodian.isNullOrBlank()))fail("确认安全须实物在场且实际保管人员明确")
        custodian?.takeIf(String::isNotBlank)?.let { users.requireMuseumMember(it,org) }
    }
    fun detail(id: String): Map<String,Any?> {
        val c=collection(id)
        return readView(id,c.orgId,c)
    }
    /** 按原业务馆查阅冻结历史；不读取接收馆当前主档，也不开放历史办理。 */
    fun history(id:String,orgId:String):Map<String,Any?> {
        CollectionCurrentOperationAccess.require(scope,orgId,false,setOf(CollectionConst.CARE),"日常检查养护历史")
        return readView(id,orgId,null)
    }
    /** The record, phase and file must all occur together in this museum's verified care history. */
    fun archivedOriginal(id:String,orgId:String,recordId:String,phase:String,fileId:String):String {
        val view=history(id,orgId)
        val records=listOf("plans","checks","works").flatMap { key ->
            (view[key] as? List<*>)?.filterIsInstance<Map<*,*>>() ?: emptyList()
        }
        val record=records.singleOrNull {it["id"]==recordId}
            ?: throw BadRequestException("原件记录不属于本馆本件检查养护历史")
        val archive=(record["archives"] as? List<*>)?.filterIsInstance<Map<*,*>>()?.singleOrNull {it["phase"]==phase}
            ?: throw BadRequestException("原件阶段不属于指定检查养护记录")
        val files=(archive["files"] as? List<*>)?.filterIsInstance<Map<*,*>>() ?: emptyList()
        if(files.none {it["fileId"]==fileId})throw BadRequestException("文件不属于指定阶段永久原件")
        return archive["id"] as? String ?: throw BadRequestException("永久原件编号缺失")
    }
    private fun readView(id:String,orgId:String,current:CollectionDao?):Map<String,Any?> {
        // SQL同时限定藏品身份和业务馆，外馆正文不进入反序列化。
        val ps=plans(id,orgId);val cs=checks(id,orgId);val ws=works(id,orgId)
        ps.forEach(::plan);cs.forEach(::check);ws.forEach(::work)
        val sessionCanOperate=SecurityUtils.getUserDetail().authorities.any {it.authority in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.CARE)}
        val canOperate=current!=null && sessionCanOperate && try {CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.CARE),"日常检查养护办理");true}
            catch (_:io.hizhengfu.cloud.common.exception.BadRequestException) {false}
        return mapOf("collectionId" to id,"orgId" to orgId,"historical" to (current==null),
            "name" to (current?.name ?: ps.lastOrNull()?.catalog?.totalName ?: cs.lastOrNull()?.catalog?.totalName),
            "sourceRevision" to current?.let {digest(json(catalog(it)))},"canOperate" to canOperate,
            "nextInspectionAt" to current?.let {CollectionCareSchedule.next(ps,cs.map {it.content.checkedAt}) {it.content.inspectionIntervalDays}},
            "nextCareAt" to current?.let {CollectionCareSchedule.next(ps,ws.filter {it.ended?.completed==true && !unsafe(it)}.map {it.ended!!.endedAt}) {it.content.careIntervalDays}},
            "plans" to ps.map {mapOf("id" to it.id.value,"version" to it.version,"content" to it.content,"conservation" to it.conservation,"keeper" to it.keeper,"archives" to planArchives(it,verifyContent=false))},
            "checks" to cs.map {mapOf("id" to it.id.value,"content" to it.content,"clearance" to it.clearance,"clearances" to CollectionCareEvidence.clearances(it.orgId,it.id.value,it.clearance),"archives" to checkArchives(it,verifyContent=false))},
            "works" to ws.map {mapOf("id" to it.id.value,"planId" to it.planId,"started" to it.started,"ended" to it.ended,"clearance" to it.clearance,"clearances" to CollectionCareEvidence.clearances(it.orgId,it.id.value,it.clearance),"archives" to workArchives(it,verifyContent=false))})
    }
    private data class CareArchivedPhase(val snapshot:Any,val fileIds:String?)
    private fun recordArchives(org:String,id:String,phases:Map<String,CareArchivedPhase>,verifyContent:Boolean=true):List<Map<String,Any?>> {
        val rows=if(verifyContent) archives.listForAuthorizedOrg(BIZ,id,org) else archives.readForDisplay(BIZ,id,org).archives
        if(rows.map {it["phase"] as String}.toSet()!=phases.keys)fail("日常检查养护归档阶段清单不一致")
        rows.forEach {row ->
            val archive=CollectionPermanentArchiveDao[row["id"] as String]
            val expected=phases.getValue(archive.phase)
            archives.verifyExisting(archive,org,BIZ,id,archive.phase,expected.snapshot,expected.fileIds,verifyContent=verifyContent)
        }
        return rows
    }
    private fun planArchives(p:CollectionCarePlanDao,verifyContent:Boolean=true):List<Map<String,Any?>> {
        val phases=mutableMapOf("PLAN" to CareArchivedPhase(planValue(p),p.content.planFileIds))
        p.conservation?.let {phases["CONSERVATION"]=CareArchivedPhase(it,it.record.fileIds)}
        p.keeper?.let {phases["KEEPER"]=CareArchivedPhase(it,it.record.fileIds)}
        return recordArchives(p.orgId,p.id.value,phases,verifyContent)
    }
    private fun checkArchives(c:CollectionCareCheckDao,verifyContent:Boolean=true):List<Map<String,Any?>> {
        val phases=mutableMapOf("CHECKED" to CareArchivedPhase(checkValue(c),"${c.content.conditionFileIds},${c.content.signedFileIds}"))
        CollectionCareEvidence.clearances(c.orgId,c.id.value,c.clearance).forEach {phases["CLEARED_${it.version}"]=CareArchivedPhase(it,it.record.signedFileIds)}
        return recordArchives(c.orgId,c.id.value,phases,verifyContent)
    }
    private fun workArchives(w:CollectionCareWorkDao,verifyContent:Boolean=true):List<Map<String,Any?>> {
        val phases=mutableMapOf("STARTED" to CareArchivedPhase(startValue(w),w.started.signedFileIds))
        w.ended?.let {phases["ENDED"]=CareArchivedPhase(it,"${it.conditionFileIds},${it.signedFileIds}")}
        CollectionCareEvidence.clearances(w.orgId,w.id.value,w.clearance).forEach {phases["CLEARED_${it.version}"]=CareArchivedPhase(it,it.record.signedFileIds)}
        return recordArchives(w.orgId,w.id.value,phases,verifyContent)
    }
    private fun verifiedPlan(p:CollectionCarePlanDao) { plan(p);planArchives(p) }
    private fun verifiedCheck(c:CollectionCareCheckDao) { check(c);checkArchives(c) }
    private fun verifiedWork(w:CollectionCareWorkDao) {
        work(w)
        planArchives(CollectionCarePlanDao[w.planId])
        checkArchives(CollectionCareCheckDao[w.started.checkId])
        workArchives(w)
    }
    fun savePlan(id: String,v: CollectionCarePlanVo): String = write(id) { c,recheck ->
        val all=plans(id,c.orgId);all.lastOrNull()?.let(::verifiedPlan)
        all.lastOrNull()?.takeIf {it.content==v}?.let {return@write it.id.value}
        if(works(id).any {it.ended==null})fail("实际养护尚未结束，须先据实记录，不能替换实施依据")
        if(v.previousPlanId!=all.lastOrNull()?.id?.value || v.sourceRevision!=digest(json(catalog(c))))fail("藏品资料或方案版本已变化，请重新核对")
        recheckCatalog(c,recheck)
        actual(v.preparedAt);required(v.instructions,v.environmentRequirements,v.storageProtection,v.safetyInstructions,v.planFileIds)
        if(v.inspectionIntervalDays !in 1..3650 || v.careIntervalDays !in 1..3650 || v.methods.isEmpty())fail("须按实际评估明确检查养护周期及允许方法")
        val p=CollectionCarePlanDao.new {orgId=c.orgId;collectionId=id;version=(CollectionCarePlans.select(CollectionCarePlans.version).where {CollectionCarePlans.collectionId eq id}.maxOfOrNull {it[CollectionCarePlans.version]} ?: 0)+1;catalog=catalog(c);content=v}
        people(p);recheck { people(p) };archives.seal(c.orgId,BIZ,p.id.value,"PLAN",planValue(p),v.planFileIds);return@write p.id.value
    }
    fun sign(id: String,planId: String,v: CollectionCareSignatureVo): String = write(id) { c,recheck ->
        val p=ownPlan(c,planId);people(p);required(v.opinion,v.fileIds)
        if(plans(id,c.orgId).last().id!=p.id || !CollectionCatalogBaselineComparison.sameCatalog(p.catalog,catalog(c)))fail("须核对当前藏品和最新养护方案后确认")
        val actor=SecurityUtils.getUserId()
        val role=when(actor) {p.content.conservationUserId->"CONSERVATION";p.content.keeperUserId->"KEEPER";else->fail("仅本方案指定责任人可本人确认")}
        self(c.orgId,actor)
        val old=if(role=="CONSERVATION")p.conservation else p.keeper
        old?.let {if(it.record==v)return@write p.id.value;fail("本人确认原件不能覆盖，变化须另立方案版本")}
        if(role=="KEEPER" && p.conservation==null)fail("请先由保护修复责任人员本人确认")
        recheckCatalog(c,recheck)
        recheck { people(p);self(c.orgId,actor) }
        val saved=CollectionCareSignature(actor,LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),v)
        if(role=="CONSERVATION")p.conservation=saved else p.keeper=saved
        archives.seal(c.orgId,BIZ,p.id.value,role,saved,v.fileIds);return@write p.id.value
    }
    fun inspect(id: String,v: CollectionCareCheckVo,sourceRevision:String?=null): String = write(id) { c,recheck ->
        checks(id,c.orgId).firstOrNull {it.content==v}?.let {verifiedCheck(it);return@write it.id.value}
        // 请求版本不进入永久检查正文，旧档与精确重放继续使用原冻结结构。
        if(sourceRevision.isNullOrBlank() || sourceRevision!=digest(json(catalog(c))))fail("藏品资料已变化或缺少检查表单版本，请重新读取并核实实际检查")
        recheckCatalog(c,recheck)
        self(c.orgId,v.operatorUserId);actual(v.checkedAt)
        v.planId?.let {ownPlan(c,it)}
        required(v.actualLocation,v.condition,v.diseases,v.environmentObservation,v.actualMeasures,v.followUp,v.conditionFileIds,v.signedFileIds)
        custody(c.orgId,v.physicallyPresent,v.custodianUserId,v.safeForRoutineCare)
        if(v.damaged && v.safeForRoutineCare)fail("检查已发现损伤，须先核实处置，不能直接作为普通养护安全依据")
        recheck { self(c.orgId,v.operatorUserId);custody(c.orgId,v.physicallyPresent,v.custodianUserId,v.safeForRoutineCare) }
        val row=CollectionCareCheckDao.new {orgId=c.orgId;collectionId=id;catalog=catalog(c);content=v}
        archives.seal(c.orgId,BIZ,row.id.value,"CHECKED",checkValue(row),"${v.conditionFileIds},${v.signedFileIds}");return@write row.id.value
    }
    fun start(id: String,v: CollectionCareStartVo): String = write(id) { c,recheck ->
        works(id,c.orgId).firstOrNull {it.started==v}?.let {verifiedWork(it);return@write it.id.value}
        recheckCatalog(c,recheck)
        val p=ownPlan(c,v.planId);people(p);self(c.orgId,v.operatorUserId);actual(v.startedAt)
        if(plans(id,c.orgId).last().id!=p.id || p.keeper==null || !CollectionCatalogBaselineComparison.sameCatalog(p.catalog,catalog(c)) || v.startedAt.isBefore(p.keeper!!.confirmedAt))fail("须在当前方案共同确认后依据实际原件开始养护")
        val check=checks(id,c.orgId).lastOrNull() ?: fail("须先如实检查本件现状和病害")
        verifiedCheck(check)
        if(check.id.value!=v.checkId || check.content.planId!=p.id.value || unsafe(check) || !CollectionCatalogBaselineComparison.sameCatalog(check.catalog,catalog(c)) || v.startedAt.isBefore(check.content.checkedAt) || v.startedAt.isAfter(check.content.checkedAt.plusDays(p.content.inspectionIntervalDays.toLong())))fail("须使用本件最新、在方案周期内的实际安全检查，不能沿用旧检查")
        if(v.methods.isEmpty() || !p.content.methods.containsAll(v.methods))fail("实施方法须在当前养护方案范围内")
        required(v.actualLocation,v.safetyChecks,v.signedFileIds)
        locks.requireUnlocked(id,BIZ,id)
        if(works(id).any {it.ended==null})fail("本件已有实际养护未结束")
        originals.requireAccess(c.orgId,id,BIZ,id,v.startedAt,v.operatorUserId,v.requisitionId,v.onSiteWarehouseLogId)
        recheck {
            people(p);self(c.orgId,v.operatorUserId)
            originals.requireAccess(c.orgId,id,BIZ,id,v.startedAt,v.operatorUserId,v.requisitionId,v.onSiteWarehouseLogId)
        }
        locks.acquire(c.orgId,BIZ,id,listOf(id))
        val row=CollectionCareWorkDao.new {orgId=c.orgId;collectionId=id;planId=p.id.value;started=v}
        archives.seal(c.orgId,BIZ,row.id.value,"STARTED",startValue(row),v.signedFileIds);return@write row.id.value
    }
    fun end(id: String,workId: String,v: CollectionCareEndVo): String = write(id) { c,recheck ->
        val w=works(id,c.orgId).find {it.id.value==workId && it.orgId==c.orgId} ?: fail("养护实施记录不存在")
        verifiedWork(w);w.ended?.let {if(it==v)return@write workId;fail("实际结束原件不能覆盖")}
        self(c.orgId,w.started.operatorUserId);actual(v.endedAt)
        if(v.endedAt.isBefore(w.started.startedAt))fail("实际结束不得早于实际开始")
        required(v.actualWork,v.condition,v.diseases,v.actualLocation,v.actualMeasures,v.followUp,v.conditionFileIds,v.signedFileIds)
        custody(c.orgId,v.physicallyPresent,v.custodianUserId,v.safeToRelease)
        if(v.completed && (v.actualMethods!=w.started.methods || v.unexpectedDamage || !v.safeToRelease))fail("未完整实施、有意外损伤或仍不安全，不能作为正常完成")
        if(v.safeToRelease) {
            if(v.unexpectedDamage || !w.started.methods.containsAll(v.actualMethods) || c.inventoryHold)fail("存在损伤、范围变化或清点异常，须按异常结束并核查")
            if(checks(id).any {unsafe(it) && it.clearance==null})fail("检查发现的异常尚未实际处置，不能直接填报安全释放")
            // 本件的养护占用由本次结束释放；其他事故和业务占用仍须独立核实。
            if(CollectionIncidentItemDao.find { io.hizhengfu.cloud.collection.dao.CollectionIncidentItems.collectionId eq id }.any {it.restricted})fail("已有事故限制，须据实结束并关联处置，不能直接填报安全释放")
        }
        recheck {
            self(c.orgId,w.started.operatorUserId);custody(c.orgId,v.physicallyPresent,v.custodianUserId,v.safeToRelease)
            if(v.safeToRelease && c.inventoryHold)fail("写入期间出现新的清点异常，须据实核查，不能确认安全释放")
        }
        w.ended=v;archives.seal(c.orgId,BIZ,w.id.value,"ENDED",v,"${v.conditionFileIds},${v.signedFileIds}")
        if(v.safeToRelease)locks.release(BIZ,id)
        return@write workId
    }
    fun clear(id: String,recordId: String,v: IncidentClearanceVo): String = write(id) { c,recheck ->
        val check=checks(id,c.orgId).find {it.id.value==recordId};val w=works(id,c.orgId).find {it.id.value==recordId}
        if(check==null && w==null)fail("检查养护来源不存在")
        if(check!=null)verifiedCheck(check) else verifiedWork(w!!)
        val old=check?.clearance ?: w?.clearance
        if(old!=null && v.confirmedAt.isBefore(old.record.confirmedAt))fail("追加事故核对不能早于既有核对记录")
        if((check!=null && !unsafe(check)) || (w!=null && (w.ended==null || !unsafe(w))))fail("仅对已记录的检查异常或实际异常结束核对处置")
        val at=check?.content?.checkedAt ?: w!!.ended!!.endedAt
        actual(v.confirmedAt);if(v.confirmedAt.isBefore(at))fail("处置核对时间不得早于来源异常")
        required(v.condition,v.actualMeasures,v.signedFileIds)
        users.requireRole(v.conservationUserId,c.orgId,CollectionConst.ROLE_CONSERVATION);users.requireMuseumMember(v.conservationUserId,c.orgId)
        users.requireRole(v.keeperUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD);self(c.orgId,v.keeperUserId)
        if(v.keeperUserId==v.conservationUserId || c.inventoryHold)fail("须分别核对责任人员及剩余清点限制")
        val proof=incidents.requireClearance(v.incidentId,c.orgId,id,IncidentSourceVo(BIZ,id,recordId),v.confirmedAt,keeperUserId=v.keeperUserId)
        if(old?.record==v && old.incidentProof==proof)return@write recordId
        recheck {
            users.requireRole(v.conservationUserId,c.orgId,CollectionConst.ROLE_CONSERVATION)
            users.requireMuseumMember(v.conservationUserId,c.orgId)
            users.requireRole(v.keeperUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            self(c.orgId,v.keeperUserId)
            if(c.inventoryHold)fail("写入期间出现新的清点异常，不能解除检查养护限制")
        }
        val saved=CollectionCareClearanceSnapshot(v,proof,(old?.version ?: 0)+1)
        if(check!=null)check.clearance=saved else w!!.clearance=saved
        archives.seal(c.orgId,BIZ,recordId,"CLEARED_${saved.version}",saved,v.signedFileIds)
        if(w!=null)locks.release(BIZ,id)
        return@write recordId
    }
}
