package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.dao.WarehouseEntryApplicationDao
import io.hizhengfu.cloud.collection.dao.WarehouseEntryApplicationVo
import io.hizhengfu.cloud.collection.dao.WarehouseEntryApplications
import io.hizhengfu.cloud.collection.dao.WarehouseEntryCheckVo
import io.hizhengfu.cloud.collection.dao.Warehouses
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.extend.toLocalDateTime
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.dao.alertSubscribers
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import io.hizhengfu.cloud.sys.system.dao.Groups
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object WarehouseEntryTimePolicy {
    fun requireCheckInAllowed(
        flowStatus: EnumFlowStatus,
        plannedStart: LocalDateTime,
        plannedEnd: LocalDateTime,
        actualEntryAt: LocalDateTime?,
        occurredAt: LocalDateTime,
    ) {
        if (flowStatus != EnumFlowStatus.PASS) throw BadRequestException("人员入库申请尚未审批通过")
        if (actualEntryAt != null) throw BadRequestException("人员已经登记入库，不能重复操作")
        if (occurredAt.isBefore(plannedStart) || occurredAt.isAfter(plannedEnd)) {
            throw BadRequestException("当前时间不在批准的入库时段内")
        }
    }

    fun requireCheckOutAllowed(actualEntryAt: LocalDateTime?, actualExitAt: LocalDateTime?, occurredAt: LocalDateTime) {
        val enteredAt = actualEntryAt ?: throw BadRequestException("尚未登记入库，不能登记离库")
        if (actualExitAt != null) throw BadRequestException("人员已经登记离库，不能重复操作")
        if (occurredAt.isBefore(enteredAt)) throw BadRequestException("离库时间不能早于入库时间")
    }
}

@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_WAREHOUSE])
class WarehouseEntryApplicationService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val userResolver: CollectionUserResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val statutory: WarehouseEntryStatutoryService,
    private val staff: WarehouseStaffingService,
    private val archives: CollectionPermanentArchiveService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<WarehouseEntryApplicationDao>(
    WarehouseEntryApplicationDao,
    flowInstanceService,
    CollectionConst.CACHE_WAREHOUSE,
) {

    override fun businessType() = CollectionConst.BIZ_WAREHOUSE_ENTRY
    override fun definitionKey() = CollectionConst.FLOW_WAREHOUSE_ENTRY
    override fun businessTypeName() = "人员入库申请"
    override fun authorizeUserOperation(entity: WarehouseEntryApplicationDao, operation: FlowUserOperation) {
        if(operation==FlowUserOperation.FLOW_INFO) {
            OrgScopedReadPolicy.requireVisible(readOrgIds(),entity.orgId,"人员入库申请流程")
            return
        }
        // 所有用户写入口按实际行串行办理，等待期间的变更不能被旧 DAO 缓存遮蔽。
        dao.find { WarehouseEntryApplications.id eq entity.id.value }.forUpdate().singleOrNull()
            ?: throw BadRequestException("人员入库申请不存在")
        entity.refresh(flush=false)
        val actor=applicationWriter(entity.orgId)
        requireCancellationActor(entity,operation,actor)
        if (entity.actualEntryAt != null) throw BadRequestException("已实际入库的记录只能补充离库事实，不能撤销、作废或改换原申请")
        if (operation == FlowUserOperation.SAVE_DRAFT) {
            requireDraftEditable(entity)
            if(entity.flowInstanceId != null)throw BadRequestException("提交后的人员、时段和陪同方案须保留，变更请另行申请")
        }
    }

    override fun userOperationVerifier(entity:WarehouseEntryApplicationDao,operation:FlowUserOperation):()->Unit {
        if(operation==FlowUserOperation.FLOW_INFO)return {}
        val orgId=entity.orgId
        val actorId=SecurityUtils.getUserDetail().id
        val applicant=entity.applicantId
        val number=entity.applicationNo
        val creator=entity.createBy
        // 新草稿在 applyDraft 前尚未填全业务字段；完整载荷由 save() 在附件回调后复核。
        val original=if(operation==FlowUserOperation.SAVE_DRAFT)null else applicationBody(entity,operation)
        return {
            if(SecurityUtils.getNullUserDetail()?.id!=actorId)throw BadRequestException("人员入库申请办理期间账号已变化，请重新登录")
            if(entity.orgId!=orgId || entity.applicantId!=applicant || entity.applicationNo!=number || entity.createBy!=creator)
                throw BadRequestException("人员入库申请办理期间原馆、申请人或编号已变化，请重新核对")
            val actor=applicationWriter(orgId)
            requireCancellationActor(entity,operation,actor)
            if(original!=null && applicationBody(entity,operation)!=original)
                throw BadRequestException("人员入库申请办理期间原方案或进出记录已变化，请重新核对")
            if(entity.actualEntryAt!=null || (operation==FlowUserOperation.SAVE_DRAFT && (entity.flowStatus!=EnumFlowStatus.DRAFT || entity.flowInstanceId!=null)))
                throw BadRequestException("人员入库申请状态已变化，不能继续保存旧草稿")
            val validState=when(operation) {
                FlowUserOperation.SUBMIT->entity.flowStatus in setOf(EnumFlowStatus.AUDIT,EnumFlowStatus.PASS)
                FlowUserOperation.CANCEL->entity.flowStatus==EnumFlowStatus.CANCEL
                FlowUserOperation.VOID->entity.flowStatus==EnumFlowStatus.VOID
                else->true
            }
            if(!validState)throw BadRequestException("人员入库申请写入后的流程状态与本次操作不符")
            if(operation==FlowUserOperation.SUBMIT) {
                val instance=entity.flowInstanceId?.let {io.hizhengfu.cloud.flow.dao.FlowInstanceDao.findById(it)}
                    ?: throw BadRequestException("人员入库申请写入后的审批实例不存在")
                instance.refresh(flush=false)
                if(instance.businessKey!=entity.id.value || instance.businessType!=businessType() || instance.definitionKey!=definitionKey(entity) || instance.initiator!=actorId)
                    throw BadRequestException("人员入库申请写入后的审批实例不属于原申请及办理人")
            }
        }
    }

    private fun applicationBody(entity:WarehouseEntryApplicationDao,operation:FlowUserOperation)=entity.toModel().let {
        it.copy(flowStatus=null,flowInstanceId=if(operation==FlowUserOperation.SUBMIT)null else it.flowInstanceId,
            approvedAt=if(operation==FlowUserOperation.SUBMIT)null else it.approvedAt)
    }

    private fun applicationWriter(orgId:String):io.hizhengfu.cloud.common.domain.SecurityUser {
        // 不在此处 flush：新草稿的前置职责检查发生在完整字段填充之前。
        clearCachedDirectory()
        return CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.WAREHOUSE_ENTRY_APPLY),"人员入库申请")
    }

    private fun requireCancellationActor(entity:WarehouseEntryApplicationDao,operation:FlowUserOperation,actor:io.hizhengfu.cloud.common.domain.SecurityUser) {
        if(operation!=FlowUserOperation.CANCEL)return
        val manager=io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value in actor.roles || actor.permissions.any {it in setOf("ADMIN","FLOW_ALL","FLOW_MANAGE")}
        if(entity.applicantId!=actor.id && !manager)throw BadRequestException("只有原申请人或具备当前流程管理权限的人员才能撤销")
    }
    override fun requireCancellationPermission(entity: WarehouseEntryApplicationDao, user: io.hizhengfu.cloud.common.domain.SecurityUser): Boolean {
        val actor=applicationWriter(entity.orgId)
        requireCancellationActor(entity,FlowUserOperation.CANCEL,actor)
        return io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value in actor.roles || actor.permissions.any {it in setOf("ADMIN","FLOW_ALL","FLOW_MANAGE")}
    }
    override fun buildTitle(entity: WarehouseEntryApplicationDao) = "人员入库：${entity.applicationNo}"
    override fun todoListRoute() = "/collection/warehouse-entry"
    override fun buildFormData(entity: WarehouseEntryApplicationDao) = statutory.proposal(entity)
    override fun buildSelfSelectAssignees(entity: WarehouseEntryApplicationDao) = statutory.actors(entity).mapValues { listOf(it.value) }
    override fun beforeSubmit(entity: WarehouseEntryApplicationDao) {
        if (entity.flowInstanceId != null) throw BadRequestException("已提交方案请按原流程办理，不能改换审批实例")
        statutory.requirePrepared(entity)
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_WAREHOUSE_ENTRY, entity.id.value)
        archives.seal(entity.orgId,businessType(),entity.id.value,"SUBMITTED",statutory.proposal(entity),statutory.files(entity))
    }

    fun findPage(request: HttpServletRequest): GenericPage<WarehouseEntryApplicationVo> {
        val page = GenericPage<WarehouseEntryApplicationVo>().build(request)
        val q = WarehouseEntryApplications.selectAll()
        q.andWhere { WarehouseEntryApplications.orgId inList readOrgIds() }
        request.getParameter("keyword")?.trim()?.takeIf(String::isNotBlank)?.let { keyword ->
            q.andWhere {
                (WarehouseEntryApplications.applicationNo like "%$keyword%") or
                    (WarehouseEntryApplications.purpose like "%$keyword%") or
                    (WarehouseEntryApplications.externalEntrants like "%$keyword%")
            }
        }
        request.getParameter("flowStatus")?.trim()?.let {
            runCatching { EnumFlowStatus.valueOf(it) }.getOrNull()
        }?.let { q.andWhere { WarehouseEntryApplications.flowStatus eq it } }
        request.getParameter("plannedStart")?.trim()?.toLocalDateTime()
            ?.let { q.andWhere { WarehouseEntryApplications.plannedStart greaterEq it } }
        request.getParameter("plannedEnd")?.trim()?.toLocalDateTime()
            ?.let { q.andWhere { WarehouseEntryApplications.plannedEnd lessEq it } }
        q.orderBy(WarehouseEntryApplications.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<WarehouseEntryApplicationDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let(::enrich)
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<WarehouseEntryApplicationVo>
    }

    private fun readOrgIds() = CollectionSearchReadAccess.require(scope,
        additionalPermissions=setOf(CollectionConst.WAREHOUSE_ENTRY_APPLY,CollectionConst.WAREHOUSE_ENTRY_VERIFY),
        allowHistoricalOrgs=true).orgIds

    fun get(id: String): WarehouseEntryApplicationVo {
        val row=scopedFind(id)
        val inspected=archives.inspectForAuthorizedOrg(businessType(),id,row.orgId,WarehouseEntryTimelineService.PHASE)
        return row.toModel().copy(
            // 核查情况及签署材料由专用核查接口按当前核验职责提供。
            archives=inspected.archives,
            evidenceErrors=inspected.issues,
        ).let { enrich(listOf(it)).single() }
    }

    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if (listOf(id,archiveId,fileId).any { !it.matches(Regex("[0-9]{15,20}")) })
            throw BadRequestException("人员入库归档原件标识不正确")
        val row=scopedFind(id)
        val archive=archives.archiveForAuthorizedOrg(businessType(),id,row.orgId,archiveId,WarehouseEntryTimelineService.PHASE)
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>>
            ?: throw BadRequestException("人员入库永久档案文件清单无效")
        if (files.none { it["fileId"]==fileId })
            throw BadRequestException("该文件不是所选人员入库永久档案的原件")
    }

    fun applicationContext(id:String?=null):WarehouseApplicationContext {
        val loginId=SecurityUtils.getUserDetail().id
        val actor=CollectionActorDirectory(scope).requireActive(loginId)
        // 完成前序业务写入及其审计回写，再读取稳定回执；真实后续变更仍会单独审计。
        val tx=TransactionManager.current()
        tx.entityCache.flush();tx.alertSubscribers()
        tx.entityCache.flush();tx.alertSubscribers()
        val entity=id?.let {scopedFind(it).also {e->e.refresh(flush=true)}}
        val orgId=entity?.orgId ?: scope.currentOrgId(actor)
        val writer=try {applicationWriter(orgId)} catch(e:BadRequestException) {if(entity==null)throw e else null}
        val revision=WarehouseAttendanceConfirmation.applicationRevision(entity,orgId,loginId)
        val application=entity?.let {get(it.id.value)}
        entity?.refresh(flush=true)
        clearCachedDirectory()
        if(SecurityUtils.getNullUserDetail()?.id!=loginId || (entity!=null && entity.orgId!=orgId) ||
            WarehouseAttendanceConfirmation.applicationRevision(entity,orgId,loginId)!=revision)
            throw BadRequestException("人员入库申请资料读取期间已变化，请重新核对")
        if(entity!=null)OrgScopedReadPolicy.requireVisible(readOrgIds(),orgId,"人员入库申请")
        val currentWriter=try {applicationWriter(orgId)} catch(e:BadRequestException) {if(entity==null)throw e else null}
        val canWrite=writer!=null && currentWriter!=null && application?.evidenceErrors.orEmpty().isEmpty()
        val beforeEntry=entity?.actualEntryAt==null
        val draft=entity==null || (entity.flowStatus==EnumFlowStatus.DRAFT && entity.flowInstanceId==null)
        val manager=currentWriter?.let {io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value in it.roles || it.permissions.any {p->p in setOf("ADMIN","FLOW_ALL","FLOW_MANAGE")}}==true
        return WarehouseApplicationContext(application,orgId,CollectionOrgDao[orgId].name,loginId,actor.name,revision,
            canWrite && beforeEntry && draft,
            canWrite && entity!=null && beforeEntry && draft,
            canWrite && entity?.flowStatus==EnumFlowStatus.AUDIT && beforeEntry && (entity.applicantId==loginId || manager),
            canWrite && entity!=null && beforeEntry && entity.flowStatus !in setOf(EnumFlowStatus.PASS,EnumFlowStatus.VOID))
    }

    private fun confirmApplication(id:String?,orgId:String,revision:String):String {
        if(orgId.isBlank() || revision.isBlank())throw BadRequestException("请重新读取并确认人员入库申请的原馆与版本")
        val entity=id?.let {scopedFind(it,writable=true)}
        if(entity!=null && entity.orgId!=orgId)throw BadRequestException("人员入库申请不属于所确认的原馆")
        CollectionLocationMutation.acquire(orgId)
        entity?.refresh()
        val actor=applicationWriter(orgId)
        if(entity?.orgId?.let {it!=orgId}==true || WarehouseAttendanceConfirmation.applicationRevision(entity,orgId,actor.id)!=revision)
            throw BadRequestException("人员入库申请、馆别、办理账号或资料版本已变化，请重新读取后核对")
        // 详情允许提示原件异常，办理仍须核验原案卷，不能用可读详情代替完整原件。
        entity?.let {archives.listForAuthorizedOrg(businessType(),it.id.value,orgId)}
        return actor.id
    }

    @CacheEvict(allEntries = true)
    fun saveConfirmed(vo:WarehouseEntryApplicationVo,expectedOrgId:String,expectedRevision:String):WarehouseApplicationContext {
        val actor=confirmApplication(vo.id?.takeIf(String::isNotBlank),expectedOrgId,expectedRevision)
        val locations=splitCollectionIds(vo.warehouseIds)
        if(locations.isEmpty() || locations.any {WarehouseDao.findById(it)?.orgId!=expectedOrgId})
            throw BadRequestException("请选择所确认收藏单位的实际库房")
        val verifyResources=WarehouseAttendanceConfirmation.resourceVerifier(vo.id?.takeIf(String::isNotBlank)?.let {dao[it]},expectedOrgId,vo)
        val id=save(vo)
        return applicationContext(id).also {
            if(it.orgId!=expectedOrgId || it.actorId!=actor)throw BadRequestException("保存结果与所确认的申请馆别或账号不一致")
            verifyResources()
        }
    }

    @CacheEvict(allEntries = true)
    fun operateConfirmed(id:String,operation:FlowUserOperation,expectedOrgId:String,expectedRevision:String):WarehouseApplicationContext {
        if(operation !in setOf(FlowUserOperation.SUBMIT,FlowUserOperation.CANCEL,FlowUserOperation.VOID))throw BadRequestException("不支持此项申请操作")
        val actor=confirmApplication(id,expectedOrgId,expectedRevision)
        val entity=dao[id]
        if(operation==FlowUserOperation.SUBMIT && (entity.flowStatus!=EnumFlowStatus.DRAFT || entity.flowInstanceId!=null))
            throw BadRequestException("仅尚未提交的草稿可从入库申请窗口提交；历史流程须保留原处理依据")
        if(operation==FlowUserOperation.VOID && entity.flowStatus==EnumFlowStatus.VOID)
            throw BadRequestException("原申请已作废，请重新读取结果")
        val verifyResources=WarehouseAttendanceConfirmation.resourceVerifier(dao[id],expectedOrgId)
        when(operation) {FlowUserOperation.SUBMIT->submit(id);FlowUserOperation.CANCEL->cancel(id);FlowUserOperation.VOID->void(id);else->error("unreachable")}
        return applicationContext(id).also {
            if(it.orgId!=expectedOrgId || it.actorId!=actor)throw BadRequestException("操作结果与所确认的申请馆别或账号不一致")
            verifyResources()
        }
    }

    @CacheEvict(allEntries = true)
    fun save(vo: WarehouseEntryApplicationVo): String {
        val warehouseIds = splitIds(vo.warehouseIds)
        if (warehouseIds.isEmpty()) throw BadRequestException("请选择拟进入库房")
        val entrantIds = splitIds(vo.entrantIds)
        val dossier=vo.dossier ?: throw BadRequestException("请填写入库类别及实际陪同方案")
        if (entrantIds.isEmpty() && dossier.externalNames.isEmpty()) throw BadRequestException("请选择入库人员或填写外来人员")
        val purpose = vo.purpose?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("入库事由必填")
        val start = vo.plannedStart ?: throw BadRequestException("计划进入时间必填")
        val end = vo.plannedEnd ?: throw BadRequestException("计划离开时间必填")
        if (!end.isAfter(start)) throw BadRequestException("计划离开时间必须晚于进入时间")
        val warehouses = warehouseIds.map {
            WarehouseDao.findById(it) ?: throw BadRequestException("库房不存在：$it")
        }
        if (warehouses.any { it.status != io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL ||
                it.sceneType != EnumLocationScene.STORAGE }) throw BadRequestException("人员只能申请进入有效保管库房")
        if (warehouses.any { it.type !in setOf(EnumWarehouseType.WAREHOUSE, EnumWarehouseType.AREA) }) {
            throw BadRequestException("人员入库申请只能选择库房或库区，不能选择柜、层或盒")
        }
        val orgId = warehouses.first().orgId
        if (warehouses.any { it.orgId != orgId }) throw BadRequestException("同一申请只能选择同一收藏单位的库房")
        val currentUser = applicationWriter(orgId)
        val staffingBaseline = staff.baselineForWrite(orgId, warehouseIds)
        val participantIds = entrantIds + dossier.escortUserIds
        val knownUsers = userResolver.names(participantIds)
        val unknownUsers = participantIds.distinct().filterNot(knownUsers::containsKey)
        if (unknownUsers.isNotEmpty()) throw BadRequestException("入库或陪同人员不存在：${unknownUsers.joinToString()}")
        val entity = saveDraft(vo.id, "人员入库申请不存在", createEntity = {
            dao.new {
                this.orgId = orgId
                applicationNo = nextApplicationNo(orgId)
                applicantId = currentUser.id
                createBy = currentUser.id
            }
        }) {
            if(applicationWriter(orgId).id!=currentUser.id)throw BadRequestException("保存入库申请期间账号已变化")
            if (this.orgId != orgId) throw BadRequestException("人员入库申请不能改换所属馆")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_WAREHOUSE_ENTRY, id.value)
            this.entrantIds = entrantIds.joinToString(",")
            externalEntrants = dossier.externalNames.joinToString("；").ifBlank { null }
            this.dossier = dossier
            baseline = staffingBaseline
            this.warehouseIds = warehouseIds.joinToString(",")
            this.purpose = purpose
            plannedStart = start
            plannedEnd = end
            escortUserId = dossier.escortUserIds.firstOrNull()
            attachmentIds = vo.attachmentIds
        }
        val expected=entity.toModel().copy(orgId=orgId,entrantIds=entrantIds.joinToString(","),
            externalEntrants=dossier.externalNames.joinToString("；").ifBlank { null },dossier=dossier,baseline=staffingBaseline,
            warehouseIds=warehouseIds.joinToString(","),purpose=purpose,plannedStart=start,plannedEnd=end,
            escortUserId=dossier.escortUserIds.firstOrNull(),attachmentIds=vo.attachmentIds)
        originalFiles?.remember(CollectionConst.BIZ_WAREHOUSE_ENTRY, entity.id.value)
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "人员入库申请附件", entity.attachmentIds).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_WAREHOUSE_ENTRY, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        TransactionManager.current().entityCache.flush()
        entity.refresh(flush=false)
        if(entity.toModel()!=expected)throw BadRequestException("保存入库申请期间原方案或进出记录已变化，请重新核对")
        if(SecurityUtils.getNullUserDetail()?.id!=currentUser.id || applicationWriter(orgId).id!=currentUser.id)
            throw BadRequestException("保存入库申请期间账号或办理资格已变化")
        return entity.id.value
    }

    @CacheEvict(allEntries = true)
    fun checkIn(id: String, vo: WarehouseAttendanceVo, expectedOrgId:String?=null, expectedRevision:String?=null): WarehouseEntryApplicationVo =
        recordAttendance(id,vo,false,expectedOrgId,expectedRevision)

    @CacheEvict(allEntries = true)
    fun checkOut(id: String, vo: WarehouseAttendanceVo, expectedOrgId:String?=null, expectedRevision:String?=null): WarehouseEntryApplicationVo =
        recordAttendance(id,vo,true,expectedOrgId,expectedRevision)

    fun attendanceContext(id:String):WarehouseAttendanceContext {
        val entity=scopedFind(id,writable=true)
        entity.refresh()
        val actor=attendanceVerifier(entity.orgId)
        val org=entity.orgId
        val revision=WarehouseAttendanceConfirmation.revision(entity,actor)
        val application=get(id)
        entity.refresh()
        if(entity.orgId!=org || attendanceVerifier(org)!=actor || WarehouseAttendanceConfirmation.revision(entity,actor)!=revision)
            throw BadRequestException("人员进出核验依据读取期间已变化，请重新读取")
        return WarehouseAttendanceContext(application,org,actor,revision,
            application.evidenceErrors.isEmpty() && entity.flowStatus==EnumFlowStatus.PASS && entity.baseline!=null && entity.dossier!=null && entity.actualEntryAt==null,
            entity.actualEntryAt!=null && entity.actualExitAt==null && entity.dossier!=null)
    }

    private fun recordAttendance(id:String,vo:WarehouseAttendanceVo,exit:Boolean,expectedOrgId:String?,expectedRevision:String?):WarehouseEntryApplicationVo {
        val entity = scopedFind(id, writable = true)
        // 离库事实须与日记签署的进出快照协调；不重新要求当日任职或活动库位资格。
        if(exit)CollectionLocationMutation.acquire(entity.orgId)
        entity.refresh()
        val actor=attendanceVerifier(entity.orgId)
        if(expectedOrgId!=null || expectedRevision!=null) {
            if(expectedOrgId.isNullOrBlank() || expectedRevision.isNullOrBlank())throw BadRequestException("请重新读取核验申请并确认原馆及版本")
            if(entity.orgId!=expectedOrgId || WarehouseAttendanceConfirmation.revision(entity,actor)!=expectedRevision)
                throw BadRequestException("人员进出核验的原馆、账号或申请依据已变化，请重新读取后办理")
        }
        val before=entity.toModel()
        val expected=if((if(exit)entity.exitRecord else entity.entryRecord)==vo)before else {
            if(exit)WarehouseEntryTimePolicy.requireCheckOutAllowed(entity.actualEntryAt,entity.actualExitAt,vo.occurredAt)
            else {
                WarehouseEntryTimePolicy.requireCheckInAllowed(entity.flowStatus,entity.plannedStart,entity.plannedEnd,entity.actualEntryAt,vo.occurredAt)
                statutory.requireApproved(entity)
            }
            statutory.attendance(entity,vo,exit)
            val remark=mergeRemark(before.accessRemark,if(exit)"离库" else "入库",vo.remark)
            entity.accessRemark=remark
            if(exit) {
                entity.exitRecord=vo;entity.actualExitAt=vo.occurredAt;entity.checkOutBy=actor
                before.copy(exitRecord=vo,actualExitAt=vo.occurredAt,checkOutBy=actor,accessRemark=remark)
            } else {
                entity.entryRecord=vo;entity.actualEntryAt=vo.occurredAt;entity.checkInBy=actor
                before.copy(entryRecord=vo,actualEntryAt=vo.occurredAt,checkInBy=actor,accessRemark=remark)
            }
        }
        val result=get(id)
        // get() 的查阅资格不能代替核验职责；复验必须晚于归档回调和实际 SQL。
        TransactionManager.current().entityCache.flush()
        entity.refresh(flush=false)
        if(entity.toModel()!=expected)throw BadRequestException("人员进出核验期间原申请或实际记录已变化，请重新核对")
        if(SecurityUtils.getNullUserDetail()?.id!=actor)throw BadRequestException("人员进出核验期间办理账号已变化，请重新登录")
        if(attendanceVerifier(before.orgId!!)!=actor)throw BadRequestException("人员进出核验人员已变化，请重新核对")
        return result
    }

    private fun attendanceVerifier(orgId:String):String {
        TransactionManager.current().entityCache.flush()
        clearCachedDirectory()
        return CollectionCurrentOperationAccess.require(scope,orgId,true,
            setOf(CollectionConst.WAREHOUSE_ENTRY_VERIFY),"人员进出核验").id
    }

    private fun clearCachedDirectory() {
        val cache=TransactionManager.current().entityCache
        // 关系表由当前目录直接查询；清除已缓存的机构/馆实体，避免实际 SQL 变更被旧实体遮蔽。
        cache.findAll(GroupDao).toList().forEach {cache.remove(Groups,it)}
        cache.findAll(CollectionOrgDao).toList().forEach {cache.remove(CollectionOrgs,it)}
    }

    override fun onFlowTaskCompleted(entity: WarehouseEntryApplicationDao,event: FlowTaskCompletedEvent) {
        if(event.action=="APPROVE")statutory.requireActor(entity,event.nodeKey,event.operator,event.comment)
    }
    override fun onFlowCompleted(entity: WarehouseEntryApplicationDao, event: FlowCompletedEvent) {
        if(entity.flowInstanceId!=event.instanceId || entity.actualEntryAt!=null)throw BadRequestException("审批实例不符或已经实际入库")
        if(entity.flowStatus==EnumFlowStatus.PASS){statutory.requireApproved(entity);return}
        statutory.confirm(entity)
        super.onFlowCompleted(entity,event)
    }
    override fun beforeFlowReentry(entity: WarehouseEntryApplicationDao, targetNodeKey: String) {
        if(entity.actualEntryAt!=null || targetNodeKey!="keeper")throw BadRequestException("尚未入库的原方案须从保管负责人重新确认")
        statutory.requirePrepared(entity);entity.approvedAt=null
    }
    override fun onFlowCanceled(entity: WarehouseEntryApplicationDao,event: FlowCanceledEvent) {
        if(entity.actualEntryAt!=null)throw BadRequestException("实际入库后不能撤销原记录")
        super.onFlowCanceled(entity,event)
    }
    override fun onFlowRejected(entity: WarehouseEntryApplicationDao,event: FlowRejectedEvent) {
        if(entity.actualEntryAt!=null)throw BadRequestException("实际入库后不能驳回原记录")
        super.onFlowRejected(entity,event)
    }

    private fun scopedFind(id: String, writable: Boolean = false): WarehouseEntryApplicationDao {
        val entity = (if(writable) dao.find { WarehouseEntryApplications.id eq id }.forUpdate().singleOrNull() else WarehouseEntryApplicationDao.findById(id)) ?: throw BadRequestException("人员入库申请不存在")
        val orgIds = if (writable) scope.writableOrgIds() else readOrgIds()
        OrgScopedReadPolicy.requireVisible(orgIds, entity.orgId, "人员入库申请")
        return entity
    }

    private fun enrich(items: List<WarehouseEntryApplicationVo>): List<WarehouseEntryApplicationVo> {
        val userIds = items.flatMap { item ->
            splitIds(item.entrantIds) + listOfNotNull(item.applicantId, item.escortUserId)
        }
        val names = userResolver.names(userIds)
        val warehouseIds = items.flatMap { splitIds(it.warehouseIds) }.toSet()
        val warehouseNames = if (warehouseIds.isEmpty()) emptyMap() else WarehouseDao.find {
            Warehouses.id inList warehouseIds
        }.associate { it.id.value to (it.orgId to it.name) }
        items.forEach { item ->
            item.applicantName = item.applicantId?.let(names::get)
            item.entrantNames = splitIds(item.entrantIds).map { names[it] ?: it }
            item.escortUserName = item.escortUserId?.let { names[it] ?: it }
            item.warehouseNames = splitIds(item.warehouseIds).map { warehouseNames[it]?.takeIf { name -> name.first==item.orgId }?.second ?: it }
        }
        return items
    }

    private fun nextApplicationNo(orgId: String): String {
        val prefix = "RK-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-"
        val next = (dao.find {
            (WarehouseEntryApplications.orgId eq orgId) and (WarehouseEntryApplications.applicationNo like "$prefix%")
        }.mapNotNull { it.applicationNo.removePrefix(prefix).toIntOrNull() }.maxOrNull() ?: 0) + 1
        return prefix + next.toString().padStart(4, '0')
    }

    private fun mergeRemark(existing: String?, action: String, remark: String?): String? {
        val clean = remark?.trim()?.takeIf(String::isNotBlank) ?: return existing
        return listOfNotNull(existing?.takeIf(String::isNotBlank), "$action：$clean").joinToString("\n").take(1000)
    }

    private fun splitIds(value: String?): List<String> = value.orEmpty()
        .split(',', '，', ';', '；', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}
