package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileObjectStorage
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Component
@Transactional
class DigitalAcquisitionService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val originals: CollectionOriginalAccessService,
    private val locks: CollectionOperationLockService,
    private val storage: FileObjectStorage,
) {
    companion object { const val BIZ="COLLECTION_DIGITAL_ACQUISITION" }
    fun digital(r:ReproductionDao)=r.repType in setOf(EnumRepType.PHOTO,EnumRepType.SCAN_3D)
    private fun rows(id:String):List<DigitalAcquisitionPlanDao> {
        val org=ReproductionDao.findById(id)?.orgId ?: fail("数字采集记录不存在")
        if(DigitalAcquisitionPlans.select(DigitalAcquisitionPlans.orgId).where {DigitalAcquisitionPlans.reproductionId eq id}.any {it[DigitalAcquisitionPlans.orgId]!=org})
            fail("数字采集方案所属馆与业务不一致")
        return DigitalAcquisitionPlanDao.find {DigitalAcquisitionPlans.reproductionId eq id}.sortedBy {it.version}
    }
    private fun latest(r:ReproductionDao)=rows(r.id.value).lastOrNull() ?: fail("请先登记信息化部门会同保管部门制定的实际操作及安全方案")
    private fun access(orgId:String,write:Boolean=false) {
        CollectionCurrentOperationAccess.require(scope,orgId,write,setOf(CollectionConst.STORAGE),"数字采集方案与资源",setOf(CollectionConst.ROLE_INFORMATION,CollectionConst.ROLE_STORAGE_HEAD))
    }
    private fun root(id:String,write:Boolean=false):ReproductionDao {
        val r=(if(write)ReproductionDao.find { Reproductions.id eq id }.forUpdate().singleOrNull() else ReproductionDao.findById(id)) ?: fail("数字采集记录不存在")
        access(r.orgId,write);if(!digital(r))fail("本入口仅适用摄影测量、影像采集及三维扫描")
        return r
    }
    /** 行锁等待和原件读取都可能跨越任职变化；实际 SQL 后再核权，失败时整体回滚。 */
    private fun <T> write(id:String,lockCollection:Boolean=false,block:(ReproductionDao)->T):T {
        val actorId=SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理数字采集")
        val r=root(id,true)
        val identity=r.toModel()
        if(lockCollection)CollectionDao.find {Collections.id eq r.collectionId}.forUpdate().singleOrNull() ?: fail("藏品不存在")
        fun verify() {
            TransactionManager.current().entityCache.flush()
            if(SecurityUtils.getNullUserDetail()?.id!=actorId)fail("写入期间办理账号已变化，请重新登录")
            r.refresh(flush=false)
            if(r.toModel()!=identity)fail("写入期间数字采集业务身份已变化，请刷新核实")
            CollectionOrgDao.findById(r.orgId)?.refresh(flush=false)
            access(r.orgId,true)
        }
        verify()
        val result=block(r)
        verify()
        return result
    }
    private fun collection(r:ReproductionDao)=(CollectionDao.findById(r.collectionId) ?: fail("藏品不存在")).also { if(it.orgId!=r.orgId)fail("藏品所属馆已变化，须核实原业务档案") }
    private fun source(r:ReproductionDao)=json(mapOf("collectionId" to r.collectionId,"repType" to r.repType,"purpose" to r.purpose,"repDate" to r.repDate,"catalog" to CollectionCatalogMapper.fromCollection(collection(r).toModel()).copy(warehouseId=null)))
    private fun snapshot(p:DigitalAcquisitionPlanDao)=mapOf("reproductionId" to p.reproductionId,"version" to p.version,"sourceSnapshot" to p.sourceSnapshot,"content" to p.content)
    private fun resources(p:DigitalAcquisitionPlanDao):List<DigitalAcquisitionResourceDao> {
        if(DigitalAcquisitionResources.select(DigitalAcquisitionResources.orgId).where {DigitalAcquisitionResources.planId eq p.id.value}.any {it[DigitalAcquisitionResources.orgId]!=p.orgId})
            fail("数字资源所属馆与方案不一致")
        return DigitalAcquisitionResourceDao.find {DigitalAcquisitionResources.planId eq p.id.value}.sortedBy {it.content.occurredAt}
    }
    private fun files(r:DigitalAcquisitionResourceDao):List<DigitalAcquisitionFileDao> {
        if(DigitalAcquisitionFiles.select(DigitalAcquisitionFiles.orgId,DigitalAcquisitionFiles.planId).where {DigitalAcquisitionFiles.resourceId eq r.id.value}.any {
                it[DigitalAcquisitionFiles.orgId]!=r.orgId || it[DigitalAcquisitionFiles.planId]!=r.planId})fail("数字资源文件所属馆或方案不一致")
        return DigitalAcquisitionFileDao.find {DigitalAcquisitionFiles.resourceId eq r.id.value}.sortedBy {it.fileId}
    }
    private fun resourceSnapshot(r:DigitalAcquisitionResourceDao)=mapOf("content" to r.content,"files" to files(r).map { it.snapshot })
    private fun phase(p:DigitalAcquisitionPlanDao,key:String,value:Any) {
        val a=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase eq key) }.singleOrNull() ?: fail("缺少数字采集本轮永久原件：$key")
        if(a.orgId!=p.orgId || a.digest!=digest(a.snapshot) || !DigitalArchiveSnapshotComparison.same(key,a.snapshot,json(value)))fail("数字采集记录与永久原件不一致")
    }
    private data class ReadPlan(val plan:DigitalAcquisitionPlanDao,val resources:List<DigitalAcquisitionResourceDao>,val archives:List<Map<String,Any?>>)
    private fun readPlans(r:ReproductionDao,verifyContent:Boolean=true):List<ReadPlan> = rows(r.id.value).map {p ->
        phase(p,"PLAN",snapshot(p))
        if(JsonUtils.mapper.readTree(p.sourceSnapshot).path("collectionId").asString()!=r.collectionId)fail("数字采集原方案与业务藏品身份不一致")
        val archiveRows=if(verifyContent) archives.listForAuthorizedOrg(BIZ,p.id.value,r.orgId) else archives.readForDisplay(BIZ,p.id.value,r.orgId).archives
        fun optional(key:String,value:Any?) {
            if(value!=null)phase(p,key,value)
            else if(archiveRows.any {it["phase"]==key})fail("不能清除数字采集已归档的阶段：$key")
        }
        optional("STARTED",p.started);optional("CAPTURED",p.captured);optional("COMPLETED",p.completed)
        val resourceRows=resources(p)
        val resourceIds=resourceRows.map {it.id.value}.toSet()
        val archivedIds=archiveRows.map {it["phase"] as String}.filter {it.startsWith("RESOURCE_")}.map {it.removePrefix("RESOURCE_")}.toSet()
        if(resourceIds!=archivedIds)fail("数字资源记录与永久原件清单不一致")
        resourceRows.forEach {row ->
            val references=files(row)
            if(references.map {it.fileId}.toSet()!=row.content.fileIds || references.any {it.snapshot.fileId!=it.fileId})fail("数字资源文件清单与冻结身份不一致")
            if(!resourceIds.containsAll(row.content.parentResourceIds))fail("数字资源来源不属于本轮方案")
            phase(p,"RESOURCE_${row.id.value}",resourceSnapshot(row))
        }
        ReadPlan(p,resourceRows,archiveRows)
    }
    fun detail(id:String):Map<String,Any?> {
        val r=root(id)
        val verified=readPlans(r,verifyContent=false)
        val currentSource=runCatching {source(r)}
        val canOperate=try {access(r.orgId,true);true} catch (_:BadRequestException) {false}
        return mapOf("canOperate" to canOperate,"id" to id,"collectionId" to r.collectionId,"orgId" to r.orgId,"repType" to r.repType,"purpose" to r.purpose,"flowStatus" to r.flowStatus,"flowInstanceId" to r.flowInstanceId,"sourceRevision" to currentSource.getOrNull()?.let(::digest),"sourceIssue" to currentSource.exceptionOrNull()?.message,
            "plans" to verified.map {read ->val p=read.plan;mapOf("id" to p.id.value,"version" to p.version,"content" to p.content,"started" to p.started,"captured" to p.captured,"completed" to p.completed,"resources" to read.resources.map {row ->mapOf("id" to row.id.value,"content" to row.content,"files" to files(row).map {it.snapshot})},"archives" to read.archives)})
    }
    data class OriginalDownload(
        val file:io.hizhengfu.cloud.common.domain.FileVo,
        val baseline:io.hizhengfu.cloud.common.file.FileContentBaseline?,
        val revision:io.hizhengfu.cloud.common.file.FileObjectRevision?,
    )
    fun originalFile(id:String,fileId:String)=originalDownload(id,fileId).file

    /** 访问索引只定位候选；方案、归档引用和资源快照才构成原件身份。 */
    fun originalDownload(id:String,fileId:String):OriginalDownload {
        val r=root(id)
        val plans=rows(id)
        if(plans.any {it.orgId!=r.orgId})fail("数字采集方案所属馆与业务不一致")
        val planIds=plans.map {it.id.value}.toSet()
        val accessRows=DigitalAcquisitionFileAccessDao.find {DigitalAcquisitionFileAccesses.fileId eq fileId}.filter {it.planId in planIds}
        if(accessRows.isEmpty() || accessRows.any {it.orgId!=r.orgId})fail("文件不属于本次数字采集永久原件")
        val selected=plans.filter {p->accessRows.any {it.planId==p.id.value}}
        val recordedResources=mutableListOf<Pair<String,String>>()
        val metadata=selected.flatMap {p ->
            phase(p,"PLAN",snapshot(p))
            if(JsonUtils.mapper.readTree(p.sourceSnapshot).path("collectionId").asString()!=r.collectionId)fail("数字采集原方案与业务藏品身份不一致")
            val archiveRows=archives.listForAuthorizedOrg(BIZ,p.id.value,r.orgId)
            archiveRows.filter {(it["phase"] as String).startsWith("RESOURCE_")}.forEach {a ->
                val value=JsonUtils.mapper.readTree(a["snapshot"] as String)
                if(value.path("files").any {it.path("fileId").asString()==fileId})
                    recordedResources+=p.id.value to (a["phase"] as String).removePrefix("RESOURCE_")
            }
            val references=archiveRows.flatMap {a ->
                @Suppress("UNCHECKED_CAST")
                (a["files"] as List<Map<String,Any?>>).filter {it["fileId"]==fileId}
            }
            if(references.isEmpty())fail("数字采集访问索引缺少对应永久原件引用")
            references.map {JsonUtils.mapper.readTree(it["metadata"] as String)}
        }
        val current=FileDao.findById(fileId) ?: fail("数字采集原件不存在")
        metadata.forEach {m ->
            if(m.path("path").asString()!=current.path || m.path("storageType").asString()!=current.storageType.name ||
                !m.path("size").isIntegralNumber || !m.path("size").canConvertToLong() || m.path("size").longValue()!=current.size)
                fail("数字采集文件定位或长度与留存身份不一致，请核对原件")
        }
        val baselines=metadata.mapNotNull {CollectionArchiveFileIdentity.content(it)}.toMutableSet()
        val selectedIds=selected.map {it.id.value}.toSet()
        val fileMatch=(DigitalAcquisitionFiles.fileId eq fileId) and (DigitalAcquisitionFiles.planId inList selectedIds)
        if(DigitalAcquisitionFiles.select(DigitalAcquisitionFiles.orgId).where {fileMatch}.any {it[DigitalAcquisitionFiles.orgId]!=r.orgId})
            fail("数字原件资源文件所属馆与业务不一致")
        val resourceRefs=DigitalAcquisitionFileDao.find {fileMatch}.toList()
        if(recordedResources.any {(planId,resourceId)->resourceRefs.none {it.planId==planId && it.resourceId==resourceId}} ||
            selected.any {p->p.captured?.rawFileIds?.contains(fileId)==true && resourceRefs.none {it.planId==p.id.value}})
            fail("数字资源文件快照缺失，不能降级为普通附件")
        resourceRefs.forEach {ref ->
            val p=selected.single {it.id.value==ref.planId}
            val resource=DigitalAcquisitionResourceDao.findById(ref.resourceId) ?: fail("数字原件的来源资源缺失")
            if(ref.orgId!=r.orgId || resource.orgId!=r.orgId || resource.planId!=p.id.value)fail("数字原件的资源、方案或所属馆不一致")
            val linked=files(resource)
            if(linked.any {it.orgId!=r.orgId || it.planId!=p.id.value || it.snapshot.fileId!=it.fileId} ||
                linked.map {it.fileId}.toSet()!=resource.content.fileIds)fail("数字资源文件清单与归档身份不一致")
            phase(p,"RESOURCE_${resource.id.value}",resourceSnapshot(resource))
            val frozen=ref.snapshot
            if(frozen.fileId!=fileId || frozen.path!=current.path || frozen.storageType!=current.storageType.name || frozen.size!=current.size ||
                !frozen.sha256.matches(Regex("[0-9a-f]{64}")))fail("数字资源文件与冻结身份不一致")
            baselines+=io.hizhengfu.cloud.common.file.FileContentBaseline(frozen.sha256,frozen.size)
        }
        // 有资源归档时必须保留对应的资源文件行，不能删除行后降级为无摘要附件。
        selected.forEach {p ->
            resources(p).filter {fileId in it.content.fileIds}.forEach {resource ->
                if(resourceRefs.none {it.resourceId==resource.id.value && it.planId==p.id.value})fail("数字资源文件快照缺失，不能降级为普通附件")
            }
        }
        if(baselines.size>1)fail("数字原件的多份留存内容基线不一致")
        val revisions=metadata.mapNotNull {
            CollectionPermanentRemoteRecoveryRegistry.effective(fileId, r.orgId, it.path("path").asString(),
                CollectionArchiveFileIdentity.revision(it), CollectionArchiveFileIdentity.content(it))
        }.distinct()
        if(revisions.size>1)fail("数字原件的留存存储版本不一致，请核对原档案")
        val frozen=metadata.first()
        return OriginalDownload(io.hizhengfu.cloud.common.domain.FileVo(id=fileId,name=frozen.path("name").asString(),
            path=frozen.path("path").asString(),storageType=frozen.path("storageType").asString(),size=current.size,
            hash=baselines.singleOrNull()?.sha256),baselines.singleOrNull(),revisions.singleOrNull())
    }
    fun requireEditable(r:ReproductionDao) { if(rows(r.id.value).any { it.started!=null })fail("数字采集已经实际开始，不能改写、撤销或重新审批原业务") }
    fun savePlan(id:String,v:DigitalAcquisitionPlanVo):String = write(id) {r ->
        requireEditable(r)
        if(r.flowStatus!=EnumFlowStatus.DRAFT || r.flowInstanceId!=null)fail("已进入办理的数字采集方案须保留原件，不能覆盖")
        val c=collection(r);val src=source(r);val old=rows(id).lastOrNull()
        if(v.sourceRevision!=digest(src))fail("藏品或采集资料已经变化，请重新核实")
        if(!c.registered || c.totalRegNo.isBlank() || c.catalogStatus!=EnumCatalogStatus.APPROVED)fail("须先完成本件建账建档")
        if(v.preparedAt.isAfter(LocalDateTime.now()) || v.plannedStart.isBefore(v.preparedAt) || !v.plannedEnd.isAfter(v.plannedStart))fail("请据实填写方案形成时间和经商定的采集时段")
        if(listOf(v.acquisitionMethod,v.workLocation,v.environmentAndExposureControls,v.handlingControls,v.processingStandards,v.organizationRules,v.storageAndBackupPlan,v.resourceSecurityPlan,v.rightsAndConfidentiality,v.conditionFileIds,v.operationPlanFileIds,v.safetyPlanFileIds,v.securityEvidenceFileIds,v.signedJointPlanFileIds).any(String::isBlank))fail("须完整记录采集、实物安全、加工、整理、储存备份、资源安全及共同签署方案")
        if(v.informationUserId==v.keeperUserId)fail("信息化部门与保管部门须分别履行共同制定方案的职责")
        requireJointActors(r.orgId,v.informationDepartmentId,v.informationUserId,v.keeperUserId)
        if(old?.content==v && old.sourceSnapshot==src){phase(old,"PLAN",snapshot(old));return@write old.id.value}
        if(old!=null && (v.preparedAt.isBefore(old.content.preparedAt) || v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank()))fail("订正须追加实际原因和原件，旧方案永久保留")
        val p=DigitalAcquisitionPlanDao.new { orgId=r.orgId;reproductionId=id;version=(old?.version ?: 0)+1;sourceSnapshot=src;content=v }
        seal(r.orgId,BIZ,p.id.value,"PLAN",snapshot(p),listOfNotNull(v.conditionFileIds,v.operationPlanFileIds,v.safetyPlanFileIds,v.securityEvidenceFileIds,v.signedJointPlanFileIds,v.replacementFileIds).joinToString(","))
        requireJointActors(r.orgId,v.informationDepartmentId,v.informationUserId,v.keeperUserId)
        p.id.value
    }
    fun prepared(r:ReproductionDao):DigitalAcquisitionPlanDao {
        val p=recorded(r);if(!CollectionCatalogBaselineComparison.sameSource(p.sourceSnapshot,source(r)))fail("本轮采集方案基线已经变化")
        return p
    }
    private fun recorded(r:ReproductionDao):DigitalAcquisitionPlanDao {
        val p=latest(r);if(p.orgId!=r.orgId)fail("本轮采集方案所属馆不一致")
        phase(p,"PLAN",snapshot(p));return p
    }
    fun actors(r:ReproductionDao)=latest(r).content.let { linkedMapOf("information" to it.informationUserId,"keeper" to it.keeperUserId) }
    fun formData(r:ReproductionDao):Map<String,Any?> {
        val current=root(r.id.value)
        return readPlans(current).lastOrNull()?.plan?.let {mapOf("digitalPlanId" to it.id.value,"digitalPlan" to it.content,"sourceSnapshot" to it.sourceSnapshot)}.orEmpty()
    }
    fun submit(r:ReproductionDao) = write(r.id.value) {currentRoot ->
        val current=Reproductions.selectAll().where { Reproductions.id eq r.id.value }.forUpdate().single()
        if(current[Reproductions.flowInstanceId]!=null || current[Reproductions.flowStatus] !in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.REJECT,EnumFlowStatus.CANCEL))fail("数字采集记录已进入办理，请核实当前流程")
        if(current[Reproductions.collectionId]!=r.collectionId || current[Reproductions.repType]!=r.repType || current[Reproductions.purpose]!=r.purpose || current[Reproductions.repDate]!=r.repDate)fail("采集方案已经变化，请刷新核实")
        requireEditable(currentRoot);val p=prepared(currentRoot);seal(currentRoot.orgId,BIZ,p.id.value,"SUBMITTED",snapshot(p),null)
        Unit
    }
    fun requireActor(r:ReproductionDao,node:String?,operator:String?,comment:String?) {
        val expected=actors(r)[node] ?: return
        if(operator!=expected || comment.isNullOrBlank())fail("指定信息化或保管人员须本人核实本轮方案并填写意见")
        val v=latest(r).content
        users.requireMuseumMember(expected,r.orgId)
        users.requireRole(expected,r.orgId,if(node=="information")CollectionConst.ROLE_INFORMATION else CollectionConst.ROLE_STORAGE_HEAD)
        if(node=="information")users.requireDepartmentMember(expected,r.orgId,v.informationDepartmentId)
    }
    private fun approvals(r:ReproductionDao):List<FlowInstanceLogDao> {
        val instance=r.flowInstanceId ?: fail("缺少信息化与保管共同确认的流程")
        val all=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instance)
        val result=actors(r).map { (node,user)->all.lastOrNull { it.nodeKey==node && it.operator==user && it.taskId!=null && !it.comment.isNullOrBlank() } ?: fail("缺少本轮信息化和保管人员实际确认，旧 PASS 不能代替") }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(result)
        return result
    }
    fun confirm(r:ReproductionDao) = write(r.id.value) {current ->
        val p=prepared(current);phase(p,"SUBMITTED",snapshot(p));val logs=approvals(current)
        seal(current.orgId,BIZ,p.id.value,"APPROVED_${logs.last().taskId!!.takeLast(40)}",logs.map {it.toVo()},null)
        Unit
    }
    fun start(id:String,v:DigitalAcquisitionStartVo):String = write(id,lockCollection=true) {r ->
        val p=prepared(r);val logs=approvals(r);phase(p,"SUBMITTED",snapshot(p));phase(p,"APPROVED_${logs.last().taskId!!.takeLast(40)}",logs.map { it.toVo() })
        if(r.flowStatus!=EnumFlowStatus.PASS)fail("须先完成本轮共同确认")
        p.started?.let { phase(p,"STARTED",it);if(it==v)return@write p.id.value;fail("实际开始原件不能覆盖") }
        if(p.id.value!=v.planId || v.startedAt.isBefore(logs.last().createTime) || v.startedAt.isBefore(p.content.plannedStart) || v.startedAt.isAfter(p.content.plannedEnd) || v.startedAt.isAfter(LocalDateTime.now()) || !v.conditionSafe || !v.siteSafe || listOf(v.actualControls,v.safetyCheckFileIds,v.signedStartFileIds).any(String::isBlank))fail("须在本轮确认及方案时段内，核实实物和场所安全后据实记录开始")
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        originals.requireAccess(r,v.startedAt,v.operatorUserId,v.requisitionId,v.onSiteWarehouseLogId)
        locks.acquire(r.orgId,BIZ,id,listOf(r.collectionId))
        p.started=v;seal(r.orgId,BIZ,p.id.value,"STARTED",v,"${v.safetyCheckFileIds},${v.signedStartFileIds}")
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        originals.requireAccess(r,v.startedAt,v.operatorUserId,v.requisitionId,v.onSiteWarehouseLogId)
        p.id.value
    }
    fun capture(id:String,v:DigitalCaptureVo):String = write(id) {r ->
        val p=prepared(r);val started=p.started ?: fail("请先记录实际采集开始")
        phase(p,"STARTED",started)
        p.captured?.let {
            phase(p,"CAPTURED",it)
            if(it!=v)fail("实际采集结束原件不能覆盖")
            val raw=recordedResourceRows(r,p).singleOrNull {row->row.content.stage==EnumDigitalResourceStage.RAW}
                ?: fail("实际采集原始成果清单缺失或重复，请核对原档案")
            if(raw.content.fileIds!=v.rawFileIds || raw.content.packageFiles!=v.rawPackageFiles)fail("实际采集原始文件与成果清单不一致")
            requireCurrentResource(p,raw)
            return@write p.id.value
        }
        locks.requireUnlocked(r.collectionId,BIZ,id)
        if(v.finishedAt.isBefore(started.startedAt) || v.finishedAt.isAfter(p.content.plannedEnd) || v.finishedAt.isAfter(LocalDateTime.now()) || !v.originalUndamaged || listOf(v.acquisitionParameters,v.conditionAfter,v.conditionFileIds,v.signedCaptureFileIds).any(String::isBlank) || v.rawFileIds.isEmpty())fail("须在方案时段内核实实际采集参数、原始数据及原件现状；发现损伤不能登记为未受损")
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        recordResource(r,p,DigitalResourceStageVo(EnumDigitalResourceStage.RAW,v.finishedAt,v.operatorUserId,emptySet(),v.rawFileIds,"实际采集原始数据",v.acquisitionParameters,v.signedCaptureFileIds,packageFiles=v.rawPackageFiles))
        p.captured=v;seal(r.orgId,BIZ,p.id.value,"CAPTURED",v,"${v.conditionFileIds},${v.signedCaptureFileIds}")
        // 原始成果阶段的复验之后还会读取结束签署材料；须覆盖最后一次封存读取。
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        locks.release(BIZ,id);p.id.value
    }
    fun resource(id:String,v:DigitalResourceStageVo):String = write(id) {r ->
        val p=recorded(r);val captured=p.captured ?: fail("请先登记实际采集结束及原始数据")
        phase(p,"CAPTURED",captured)
        if(p.completed!=null || v.stage==EnumDigitalResourceStage.RAW)fail("不能覆盖原始数据或已经共同复核完成的资源")
        recordResource(r,p,v)
    }
    private fun recordResource(r:ReproductionDao,p:DigitalAcquisitionPlanDao,v:DigitalResourceStageVo):String {
        if(v.occurredAt.isAfter(LocalDateTime.now()) || v.fileIds.isEmpty() || listOf(v.description,v.technicalMetadata,v.signedRecordFileIds).any(String::isBlank))fail("须据实填写阶段时间、成果文件、技术元数据、实际工作和签署原件")
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        val all=resources(p)
        all.find { it.content==v }?.let { requireCurrentResource(p,it);return it.id.value }
        val parents=v.parentResourceIds.map { key->all.find { it.id.value==key } ?: fail("来源资源须属于本次方案") }
        if(v.stage==EnumDigitalResourceStage.RAW) { if(parents.isNotEmpty() || all.any { it.content.stage==EnumDigitalResourceStage.RAW })fail("原始采集数据只能由实际采集结束记录一次") }
        else {
            val required=EnumDigitalResourceStage.entries[v.stage.ordinal-1]
            if(parents.isEmpty() || parents.any { it.content.stage!=required || it.content.occurredAt.isAfter(v.occurredAt) })fail("须按采集、加工、整理、储存的真实来源和发生顺序记录")
            parents.forEach { requireCurrentResource(p,it) }
        }
        if(v.stage==EnumDigitalResourceStage.STORED && (v.storageLocation.isNullOrBlank() || v.backupLocation.isNullOrBlank() || v.storageLocation==v.backupLocation || v.restorationVerifiedAt==null || v.restorationVerifiedAt.isBefore(parents.maxOf { it.content.occurredAt }) || v.restorationVerifiedAt.isAfter(v.occurredAt) || v.accessControlsVerified!=true || v.securityCheckFileIds.isNullOrBlank()))fail("须记录实际主存储、独立备份位置、恢复验证时间和权限安全核查原件")
        val snapshots=fileSnapshots(v.fileIds,v.packageFiles)
        val row=DigitalAcquisitionResourceDao.new {orgId=r.orgId;planId=p.id.value;content=v}
        snapshots.forEach { f->DigitalAcquisitionFileDao.new {orgId=r.orgId;planId=p.id.value;resourceId=row.id.value;fileId=f.fileId;snapshot=f} }
        seal(r.orgId,BIZ,p.id.value,"RESOURCE_${row.id.value}",resourceSnapshot(row),(v.fileIds+listOfNotNull(v.signedRecordFileIds,v.securityCheckFileIds)).joinToString(","))
        if(fileSnapshots(v.fileIds,v.packageFiles)!=snapshots)fail("归档期间数字成果内容或依赖发生变化，请重新核实本次完整文件清单")
        // 读取及封存大包后再次核对实际操作人员；经办账号有权不代表该人员仍可承担本次操作。
        users.requireMuseumMember(v.operatorUserId,r.orgId)
        return row.id.value
    }
    private fun fileSnapshots(ids:Set<String>,packageFiles:Map<String,String>?=null):List<DigitalFileSnapshot> {
        val inspected=ids.sorted().map { id ->
            val f=FileDao.findById(id) ?: fail("数字成果文件不存在")
            val identity=DigitalFileSnapshot(id,f.name,f.path,f.storageType.name,f.size,"")
            storage.openStream(f.path,f.storageType.name).use { DigitalModelDependencies.read(identity,f.ext,it,packageFiles?.filterValues {fileId->fileId==id}?.keys.orEmpty()) }
        }
        DigitalModelDependencies.requireClosed(inspected,packageFiles)
        return inspected.map {it.snapshot}
    }
    private fun recordedResourceRows(r:ReproductionDao,p:DigitalAcquisitionPlanDao):List<DigitalAcquisitionResourceDao> =
        readPlans(r).singleOrNull {it.plan.id.value==p.id.value}?.resources ?: fail("缺少本轮数字成果档案")

    private fun requireCurrentResource(p:DigitalAcquisitionPlanDao,row:DigitalAcquisitionResourceDao) {
        phase(p,"RESOURCE_${row.id.value}",resourceSnapshot(row))
        val frozen=files(row)
        if(frozen.map {it.fileId}.toSet()!=row.content.fileIds)fail("数字成果文件清单与留存引用不一致")
        val current=fileSnapshots(row.content.fileIds,row.content.packageFiles).associateBy {it.fileId}
        if(frozen.any {it.snapshot!=current[it.fileId]})fail("数字成果原件已经变化，请核对实际内容与留存版本")
    }

    fun complete(id:String,v:DigitalAcquisitionCompletionVo):String = write(id) {r ->
        val p=recorded(r);val captured=p.captured ?: fail("尚未完成实际采集")
        phase(p,"CAPTURED",captured)
        p.completed?.let {
            phase(p,"COMPLETED",it)
            if(it!=v)fail("数字资源共同复核原件不能覆盖")
            val recorded=recordedResourceRows(r,p)
            if(recorded.filter {row->row.content.stage==EnumDigitalResourceStage.STORED}.map {row->row.id.value}.toSet()!=v.storedResourceIds)
                fail("共同复核的储存成果与原档案清单不一致")
            recorded.forEach {row->requireCurrentResource(p,row)}
            return@write p.id.value
        }
        if(v.informationUserId!=p.content.informationUserId || v.keeperUserId!=p.content.keeperUserId || v.qualityAndSecurityReview.isBlank() || v.signedJointReviewFileIds.isBlank() || v.completedAt.isAfter(LocalDateTime.now()))fail("须由本轮信息化和保管责任人员实际共同复核，留存质量、安全和签署原件")
        requireCompletionActors(r,p,v)
        val all=recordedResourceRows(r,p);val stored=all.filter { it.content.stage==EnumDigitalResourceStage.STORED }
        if(stored.isEmpty() || stored.map { it.id.value }.toSet()!=v.storedResourceIds || all.any { it.content.occurredAt.isAfter(v.completedAt) })fail("须完整核对本批全部实际储存成果及真实处理时间")
        val covered=mutableSetOf<String>()
        fun include(row:DigitalAcquisitionResourceDao){if(covered.add(row.id.value))row.content.parentResourceIds.forEach { key->include(all.find { it.id.value==key } ?: fail("资源来源链不完整")) }}
        stored.forEach(::include)
        if(all.any { it.id.value !in covered })fail("仍有本批原始数据、加工或整理成果未纳入最终储存及共同复核")
        all.forEach {row->requireCurrentResource(p,row)}
        p.completed=v;seal(r.orgId,BIZ,p.id.value,"COMPLETED",v,v.signedJointReviewFileIds)
        // 共同复核文件读取期间可能发生任职变化，须在本次记录和档案写入后复验并整体回滚。
        requireCompletionActors(r,p,v)
        p.id.value
    }
    private fun requireCompletionActors(r:ReproductionDao,p:DigitalAcquisitionPlanDao,v:DigitalAcquisitionCompletionVo) {
        requireJointActors(r.orgId,p.content.informationDepartmentId,v.informationUserId,v.keeperUserId)
    }
    private fun requireJointActors(orgId:String,departmentId:String,informationUserId:String,keeperUserId:String) {
        users.requireDepartmentMember(informationUserId,orgId,departmentId)
        users.requireRole(informationUserId,orgId,CollectionConst.ROLE_INFORMATION)
        users.requireMuseumMember(keeperUserId,orgId)
        users.requireRole(keeperUserId,orgId,CollectionConst.ROLE_STORAGE_HEAD)
    }
    private fun seal(orgId:String,bizType:String,planId:String,phase:String,snapshot:Any,fileIds:String?):String {
        val id=archives.seal(orgId,bizType,planId,phase,snapshot,fileIds)
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.forEach { ref->
            val file=FileDao[ref.fileId]
            archives.fileReadKeys(file).forEach { key ->
                if(DigitalAcquisitionFileAccessDao.find { (DigitalAcquisitionFileAccesses.planId eq planId) and (DigitalAcquisitionFileAccesses.fileId eq file.id.value) and (DigitalAcquisitionFileAccesses.pathKey eq key) }.empty()) {
                    DigitalAcquisitionFileAccessDao.new {this.orgId=orgId;this.planId=planId;fileId=file.id.value;pathKey=key}
                }
            }
        }
        return id
    }
    private fun json(v:Any)=JsonUtils.toJson(v)!!
    private fun digest(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
