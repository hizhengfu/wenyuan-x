package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class RequisitionResponsibilityReplacementService(
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val statutory: RequisitionStatutoryService,
    private val requisitions: RequisitionService,
    private val instances: FlowInstanceService,
) {
    private val evidence = RequisitionReplacementEvidence(archives)
    private fun fail(message: String): Nothing = throw BadRequestException(message)
    private fun source(id: String, write: Boolean, lock: Boolean = write): RequisitionDao {
        val e = (if (lock) RequisitionDao.find { Requisitions.id eq id }.forUpdate().singleOrNull() else RequisitionDao.findById(id)) ?: fail("提用单不存在")
        if (lock) e.refresh(flush = false)
        val org = if (write) {
            val current = (if (lock) CollectionOrgDao.find { CollectionOrgs.id eq e.orgId }.forUpdate().singleOrNull() else CollectionOrgDao.findById(e.orgId)) ?: fail("收藏单位不存在")
            if (lock) current.refresh(flush = false)
            current
        } else null
        // 权限和馆别必须在可能耗时的两处行锁之后重读，重复办理也不能沿用等待前的资格。
        val actor = directory.requireActive(SecurityUtils.getUserDetail().id)
        if (!scope.isMulti() && e.orgId != scope.currentOrgId()) fail("提用责任变更不属于当前配置收藏单位")
        val permissions = if (write) setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.STORAGE) else setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.STORAGE, CollectionConst.SELECT)
        if (actor.permissions.none { it in permissions }) fail("当前账号缺少提用业务权限")
        if (write) OrgScopedReadPolicy.requireVisible(scope.currentWritableOrgIds(actor), e.orgId, "提用责任变更")
        else CollectionCirculationReadAccess.require(scope).require(e.orgId, CollectionConst.BIZ_REQUISITION, e.id.value)
        if (write) {
            users.requireRole(actor.id, e.orgId, CollectionConst.ROLE_DIRECTOR)
            if (org!!.status != EnumContentStatus.NORMAL || org.orgStatus != EnumOrgStatus.ENABLED) fail("收藏单位已停用")
        }
        return e
    }
    private fun unexecuted(e: RequisitionDao) {
        if (e.outExecuted || e.outExecutedTime != null || e.handover != null || e.returned || e.returnDate != null ||
            evidence.rows(e.id.value).any { it.phase == "OUT_EXECUTED" || it.phase.startsWith("HANDOVER_") } ||
            StorageMovementDao.find { (StorageMovements.orgId eq e.orgId) and (StorageMovements.refBizId eq e.id.value) and (StorageMovements.type eq EnumMoveType.OUT) }.any()) fail("已登记实际点交、出库或归还，须核实实物及后续责任，不能撤换为未执行单据")
        if (e.sourceBizType == "COLLECTION_RESTORATION") fail("修复送修单还关联专项授权及修复原方案，须先办理对应来源的完整变更")
    }
    private fun instance(e: RequisitionDao): FlowInstanceDao {
        if (e.flowStatus !in setOf(EnumFlowStatus.AUDIT, EnumFlowStatus.PASS, EnumFlowStatus.CANCEL, EnumFlowStatus.REJECT)) fail("仅可为已提交、尚无点交事实的提用单办理责任变更")
        val instance = e.flowInstanceId?.let(FlowInstanceDao::findById) ?: fail("原审批实例不存在")
        val expectedStatus = when (e.flowStatus) { EnumFlowStatus.AUDIT -> "RUNNING"; EnumFlowStatus.PASS -> "COMPLETED"; EnumFlowStatus.CANCEL -> "CANCELED"; else -> "REJECTED" }
        if (instance.businessType != CollectionConst.BIZ_REQUISITION || instance.businessKey != e.id.value || instance.parentInstanceId != null || instance.parentTaskId != null || instance.status != expectedStatus) fail("原提用单与审批实例状态或来源不一致")
        return instance
    }
    fun get(id: String): Map<String, Any?> {
        val e = source(id, false)
        val actor = directory.requireActive(SecurityUtils.getUserDetail().id)
        return try {
            val records = evidence.source(e)
            val reason = try { source(id, true, lock = false); unexecuted(e); evidence.requireActive(e); instance(e); null } catch (ex: BadRequestException) { ex.message }
            mapOf("record" to evidence.record(e), "expectedInstanceId" to e.flowInstanceId,
                "expectedSubmittedDigest" to records.single { it.phase == "SUBMITTED" }.digest,
                "canReplace" to (reason == null), "notice" to reason, "directorUserId" to actor.id, "evidenceErrors" to emptyList<String>())
        } catch (ex: BadRequestException) {
            mapOf("canReplace" to false, "evidenceErrors" to listOf(ex.message ?: "原提用依据待核对"))
        }
    }
    private fun persisted(e:RequisitionDao)=JsonUtils.fromJson(JsonUtils.toJson(e.toModel()),RequisitionVo::class.java)!!
    private fun written(id:String,expected:RequisitionVo):RequisitionDao {
        val e=RequisitionDao.find {Requisitions.id eq id}.singleOrNull() ?: fail("责任承接写入后新旧申请缺失")
        e.refresh(flush=false)
        if(persisted(e)!=expected)fail("责任承接写入后的申请、所属馆或执行状态与本次核对内容不一致")
        return e
    }
    private fun flowState(id:String)=listOf(
        FlowInstances.selectAll().where {FlowInstances.id eq id}.map {row->JsonUtils.toJson(FlowInstances.columns.map {column->row[column].let {if(it is org.jetbrains.exposed.v1.core.dao.id.EntityID<*>)it.value else it}})}.sortedBy {it},
        FlowTasks.selectAll().where {FlowTasks.instanceId eq id}.map {row->JsonUtils.toJson(FlowTasks.columns.map {column->row[column].let {if(it is org.jetbrains.exposed.v1.core.dao.id.EntityID<*>)it.value else it}})}.sortedBy {it},
        FlowInstanceLogs.selectAll().where {FlowInstanceLogs.instanceId eq id}.map {row->JsonUtils.toJson(FlowInstanceLogs.columns.map {column->row[column].let {if(it is org.jetbrains.exposed.v1.core.dao.id.EntityID<*>)it.value else it}})}.sortedBy {it},
    )
    @CacheEvict(allEntries = true)
    fun replace(id: String, value: RequisitionReplacementRequest): String {
        val e = source(id, true)
        val actor = directory.requireActive(SecurityUtils.getUserDetail().id)
        evidence.record(e)?.let { old ->
            if (old.request == value && old.authorizedBy == actor.id) return old.replacementId
            fail("原申请已有后续提用单，请核对新申请及保留原件")
        }
        unexecuted(e)
        val instance = instance(e)
        val originals = evidence.source(e)
        if (value.expectedInstanceId != e.flowInstanceId || value.expectedSubmittedDigest != originals.single { it.phase == "SUBMITTED" }.digest) fail("原申请或实例已变化，请重新核对")
        if (value.reason.isBlank() || listOf(value.appointmentFileIds, value.withdrawalFileIds).any { BusinessAttachmentBindingPlanner.plan("", "", it).isEmpty() }) fail("请说明实际岗位变动并提供任职、旧申请终止执行的签署依据")
        if (value.dossier.leaderUserId != actor.id) fail("须由新方案指定的现任主要负责人本人核实并登记")
        users.requireDepartmentMember(value.operatorUserId, e.orgId, value.dossier.departmentGroupId)
        value.reviewerUserId?.let { users.requireMuseumMember(it, e.orgId) }
        val oldDossier = e.dossier ?: fail("原提用方案缺失")
        if (listOf(oldDossier.departmentHeadUserId, oldDossier.keeperUserId, oldDossier.leaderUserId, oldDossier.useResponsibleUserId, e.operator, e.reviewer) ==
            listOf(value.dossier.departmentHeadUserId, value.dossier.keeperUserId, value.dossier.leaderUserId, value.dossier.useResponsibleUserId, value.operatorUserId, value.reviewerUserId)) fail("请明确实际变动后的责任人员")
        if (e.flowStatus == EnumFlowStatus.PASS) statutory.requireCurrentApproval(e)
        // 必须先从旧 SUBMITTED 取得依据，不能由本次新草稿的读取身份自行授予继承资格。
        val replacementOriginals = archives.prepareRequisitionReplacementOriginals(id, value.expectedSubmittedDigest)
        val originalStatus = e.flowStatus
        if (e.flowStatus == EnumFlowStatus.AUDIT) instances.cancel(instance.id.value, actor.id, actor.name, skipPermissionCheck = true)
        requisitions.releaseForResponsibilityReplacement(e)
        val newId = requisitions.saveForResponsibilityReplacement(RequisitionVo(
            collectionIds = e.collectionIds, purpose = e.purpose, basis = e.basis, department = e.department,
            operator = value.operatorUserId, reviewer = value.reviewerUserId, attachmentIds = e.attachmentIds,
            sourceBizType = e.sourceBizType, sourceBizId = e.sourceBizId, toWarehouseId = e.toWarehouseId, dossier = value.dossier,
        ), replacementOriginals)
        val child = RequisitionDao[newId]
        statutory.requirePrepared(child)
        e.supersededById = newId; child.replacesId = id
        val record = RequisitionReplacementRecord(id, newId, value, originalStatus, originals.associate { it.id.value to it.digest }, JsonUtils.toJson(statutory.proposal(child))!!, actor.id, LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
        val files = "${value.appointmentFileIds},${value.withdrawalFileIds}"
        val orgId=e.orgId
        val expectedSource=persisted(e);val expectedChild=persisted(child)
        // 先执行已核定的新旧单及流程变更，再冻结旧流程实际终止结果。
        TransactionManager.current().entityCache.flush()
        val expectedFlow=flowState(instance.id.value)
        val sourceArchive=archives.seal(orgId, CollectionConst.BIZ_REQUISITION, id, RequisitionReplacementEvidence.REPLACED, record, files)
        val childArchive=archives.seal(orgId, CollectionConst.BIZ_REQUISITION, newId, RequisitionReplacementEvidence.CREATED, record, files)
        TransactionManager.current().entityCache.flush()
        val currentSource=written(id,expectedSource);val currentChild=written(newId,expectedChild)
        if(flowState(instance.id.value)!=expectedFlow)fail("责任承接归档期间原流程、任务或操作记录变化，请核实办理结果")
        for((businessId,archiveId,phase) in listOf(Triple(id,sourceArchive,RequisitionReplacementEvidence.REPLACED),Triple(newId,childArchive,RequisitionReplacementEvidence.CREATED))) {
            val rows=evidence.rows(businessId).onEach {it.refresh(flush=false)}
            val original=rows.singleOrNull {it.id.value==archiveId} ?: fail("责任承接新旧两端原件不完整，请核对实际登记结果")
            archives.verifyExisting(original,orgId,CollectionConst.BIZ_REQUISITION,businessId,phase,record,files)
        }
        evidence.source(currentSource);unexecuted(currentSource)
        val ids=splitCollectionIds(currentChild.collectionIds).toSet()
        val collections=CollectionDao.find {Collections.id inList ids}.toList()
        if(collections.map {it.id.value}.toSet()!=ids)fail("责任承接期间关联藏品缺失，请核对原清单")
        collections.forEach {it.refresh(flush=false)}
        val location=WarehouseDao.find {Warehouses.id eq currentChild.toWarehouseId.orEmpty()}.singleOrNull() ?: fail("责任承接期间归库位置缺失")
        location.refresh(flush=false)
        statutory.requirePrepared(currentChild)
        users.requireDepartmentMember(value.operatorUserId,orgId,value.dossier.departmentGroupId)
        value.reviewerUserId?.let {users.requireMuseumMember(it,orgId)}
        CollectionOrgDao.findById(orgId)?.refresh(flush=false)
        if(SecurityUtils.getNullUserDetail()?.id!=actor.id)fail("责任承接期间办理账号变化，请重新核实")
        source(id,true,lock=false)
        archives.verifyRequisitionReplacementOriginals(replacementOriginals, newId)
        return newId
    }
}
