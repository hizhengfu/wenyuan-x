package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.dao.FlowInstanceDao
import io.hizhengfu.cloud.flow.dao.FlowTasks
import io.hizhengfu.cloud.flow.dao.FlowInstances
import io.hizhengfu.cloud.flow.engine.FlowMutationCoordinator
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.domain.FlowReactivatedEvent
import io.hizhengfu.cloud.flow.domain.FlowTaskCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.security.MessageDigest

@Component
@Transactional
class CollectionChangeService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val locks: CollectionOperationLockService,
    private val attachments: BusinessAttachmentBinder,
    private val catalogService: CollectionCatalogService,
    private val responsibleUsers: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val retainedLedgers: CollectionRetainedLedgerService,
    private val directory: CollectionActorDirectory,
    private val paperLedgers: PaperLedgerService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<CollectionChangeDao>(CollectionChangeDao, flowInstanceService, CollectionConst.CACHE) {
    override fun businessType() = CollectionConst.BIZ_CHANGE
    override fun definitionKey() = CollectionConst.FLOW_CHANGE
    override fun businessTypeName() = "藏品信息变更"
    override fun todoListRoute() = "/collection/change"
    override fun buildTitle(entity: CollectionChangeDao) = "信息变更：${entity.before.formalName ?: entity.collectionId}"
    override fun buildFormData(entity: CollectionChangeDao): Map<String, Any?> = mapOf(
        "id" to entity.id.value, "collectionId" to entity.collectionId, "reason" to entity.reason,
        "before" to entity.before, "content" to entity.content, "keeperUserId" to entity.keeperUserId,
        "leaderUserId" to entity.leaderUserId, "evidenceFileIds" to entity.evidenceFileIds,
    ) + entity.setAmendment?.let { mapOf("setAmendment" to it) }.orEmpty() + entity.correction?.let { mapOf("correction" to it) }.orEmpty()
    override fun buildSelfSelectAssignees(entity: CollectionChangeDao) = mapOf(
        "keeper" to listOfNotNull(entity.keeperUserId), "leader" to listOfNotNull(entity.leaderUserId),
    )

    override fun authorizeUserOperation(entity: CollectionChangeDao, operation: FlowUserOperation) {
        requireAccess(entity.orgId, operation != FlowUserOperation.FLOW_INFO)
        if (operation == FlowUserOperation.SAVE_DRAFT) {
            requireDraftEditable(entity, "信息变更申请")
            if (entity.flowInstanceId != null) throw BadRequestException("已提交的变更申请永久保留，请新建申请后重新审批")
        }
        if (operation in setOf(FlowUserOperation.SAVE_DRAFT, FlowUserOperation.SUBMIT) && entity.appliedAt != null)
            throw BadRequestException("已生效变更须永久保留，后续变更请另建申请")
        if (operation == FlowUserOperation.VOID) requireNoCorrectionSuccessor(entity)
    }

    private fun requireAccess(orgId: String, write: Boolean) {
        CollectionCurrentOperationAccess.require(scope, orgId, write,
            if (write) setOf(CollectionConst.LEDGER_CORRECT) else setOf(CollectionConst.LEDGER_CORRECT, CollectionConst.SELECT), "信息变更申请")
        if (write) {
            val current = directory.requireActive(SecurityUtils.getUserDetail().id)
            CollectionDutySeparationPolicy.requireLedgerOperation(current.permissions, current.roleCodes)
        }
    }

    fun findPage(request: HttpServletRequest): GenericPage<CollectionChangeVo> {
        val access = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.LEDGER_CORRECT), allowHistoricalOrgs = true)
        val page = GenericPage<CollectionChangeVo>().build(request)
        val query = CollectionChanges.selectAll()
        query.andWhere { CollectionChanges.orgId inList access.orgIds }
        request.getParameter("collectionId")?.takeIf(String::isNotBlank)?.let { id -> query.andWhere { CollectionChanges.collectionId eq id } }
        request.getParameter("flowStatus")?.let { runCatching { EnumFlowStatus.valueOf(it) }.getOrNull() }
            ?.let { status -> query.andWhere { CollectionChanges.flowStatus eq status } }
        page.total = query.count()
        query.orderBy(CollectionChanges.createTime to SortOrder.DESC)
        if (page.pageSize > 0) query.limit(page.pageSize).offset(page.startRow)
        val inspectedRetained = mutableMapOf<String, CollectionRetainedLedgerService.BoundSnapshotInspection>()
        page.list = CollectionChangeDao.wrapRows(query).map { enrich(it, inspectedRetained) }
        return page
    }

    fun get(id: String): CollectionChangeVo = enrich(scoped(id), forDisplay=true)

    fun archivesForDisplay(id: String): List<Map<String, Any?>> {
        val entity = scoped(id)
        return archives.readForDisplay(businessType(), id, entity.orgId).archives
    }

    /** 案卷阅读岗位仅可下载本申请当前或经核验的历史归档原件。文件服务仍会复核实际文件授权。 */
    fun requireOriginal(id: String, fileId: String) {
        val entity = scoped(id)
        if (!fileId.matches(Regex("[0-9]{15,20}"))) throw BadRequestException("原件文件标识不正确")
        val current = listOfNotNull(entity.evidenceFileIds, entity.signedChangeFileIds, entity.correction?.evidenceFileIds)
            .flatMap { it.split(',') }.map(String::trim).toSet()
        val archived = archives.listForAuthorizedOrg(businessType(), id, entity.orgId)
            .flatMap { archive ->
                @Suppress("UNCHECKED_CAST")
                (archive["files"] as List<Map<String, Any?>>).mapNotNull { it["fileId"] as? String }
            }.toSet()
        if (fileId !in current && fileId !in archived)
            throw BadRequestException("该文件不是本信息变更申请的原件")
    }

    /** 只查阅本申请指定阶段的冻结原件，避免同一文件号被多个阶段复用时选错版本。 */
    fun requireArchivedOriginal(id: String, archiveId: String, fileId: String) {
        val entity = scoped(id)
        if (!archiveId.matches(Regex("[0-9]{15,20}")) || !fileId.matches(Regex("[0-9]{15,20}")))
            throw BadRequestException("归档原件标识不正确")
        val archive = archives.listForAuthorizedOrg(businessType(), id, entity.orgId)
            .singleOrNull { it["id"] == archiveId }
            ?: throw BadRequestException("该永久档案不属于本信息变更申请")
        @Suppress("UNCHECKED_CAST")
        val files = archive["files"] as List<Map<String, Any?>>
        if (files.none { it["fileId"] == fileId })
            throw BadRequestException("该文件不是所选信息变更永久档案的原件")
    }

    /** 补正只另建草稿，不复用批准、签署或生效记录。原流程协调与建档处于同一事务。 */
    fun createCorrection(id: String, request: CollectionChangeCorrectionRequest): String {
        val source = scoped(id, writable = true)
        val user = directory.requireActive(SecurityUtils.getUserDetail().id)
        if (request.requestKey.isBlank() || request.requestKey != request.requestKey.trim() || request.requestKey.length > 64)
            throw BadRequestException("补正请求标识不正确，请重新打开补正表单")
        val reason = request.reason.trim().takeIf(String::isNotBlank) ?: throw BadRequestException("补正原因必填")
        if (reason.length > 4000) throw BadRequestException("补正原因不能超过4000字")
        val files = normalizeFiles(request.evidenceFileIds) ?: throw BadRequestException("本次补正依据原件必填")
        requireClosedCorrectionSource(source, request.expectedInstanceId)
        val submitted = requireSubmittedArchive(source)
        val replay = CollectionChangeDao.find { CollectionChanges.correctionRequestKey eq request.requestKey }.singleOrNull()
        if (replay != null) {
            val origin = replay.correction
            if (replay.previousChangeId != id || origin?.recordedBy != user.id || origin.reason != reason || origin.evidenceFileIds != files)
                throw BadRequestException("该请求标识已经用于其他补正内容，请核对原补正记录")
            requireCorrectionOrigin(replay)
            return replay.id.value
        }
        requireCorrectionOrigin(source)
        requireCorrectionFamilyAvailable(source)
        val current = collection(source.collectionId, writable = true)
        val newId = save(CollectionChangeVo(collectionId = source.collectionId, reason = source.reason,
            content = source.content.copy(warehouseId = current.warehouseId), keeperUserId = source.keeperUserId, leaderUserId = source.leaderUserId,
            evidenceFileIds = listOfNotNull(source.evidenceFileIds, files).joinToString(","), setAmendment = source.setAmendment))
        val next = CollectionChangeDao[newId]
        next.previousChangeId = id
        next.correctionRequestKey = request.requestKey
        next.correction = CollectionChangeCorrectionOrigin(id, request.expectedInstanceId, source.flowStatus, submitted.id.value, submitted.digest,
            reason, files, user.id, LocalDateTime.now())
        archives.seal(next.orgId, businessType(), newId, "CORRECTION_ORIGIN", correctionArchive(next, submitted),
            listOfNotNull(source.evidenceFileIds, files).joinToString(","))
        return newId
    }

    fun corrections(id: String): Map<String, Any?> {
        val current = scoped(id)
        fun reference(row: CollectionChangeDao): Map<String, Any?> {
            if (row.orgId != current.orgId || row.collectionId != current.collectionId) throw BadRequestException("补正申请关联不一致，请保留原记录核对")
            scoped(row.id.value)
            return mapOf("id" to row.id.value, "reason" to row.reason, "flowStatus" to row.flowStatus,
                "flowInstanceId" to row.flowInstanceId, "appliedAt" to row.appliedAt, "createTime" to row.createTime, "correction" to row.correction)
        }
        return mapOf("previous" to current.previousChangeId?.let { previous -> reference(CollectionChangeDao.findById(previous)
            ?: throw BadRequestException("补正来源申请缺失")) },
            "following" to CollectionChangeDao.find { CollectionChanges.previousChangeId eq id }.sortedBy { it.createTime }.map(::reference))
    }

    fun proposal(collectionId: String, includeSetRelations: Boolean = false): CollectionChangeVo {
        val collection = collection(collectionId)
        val content = currentContent(collection)
        return CollectionChangeVo(collectionId = collectionId, orgId = collection.orgId,
            totalRegNo = collection.totalRegNo, collectionName = collection.name, before = content, content = content,
            setAmendment = if (includeSetRelations) CollectionSetAmendments.proposal(collection) else null)
    }

    /** 旧快捷更正入口仍可使用，但返回变更申请 ID，不再直接修改总账。 */
    fun requestCorrection(collectionId: String, vo: CollectionCorrectVo): String {
        val initial = proposal(collectionId)
        val content = CollectionChangePolicy.correct(initial.content!!, vo.field, vo.value)
        return save(initial.copy(content = content, reason = vo.reason))
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun save(vo: CollectionChangeVo): String {
        val collection = collection(vo.collectionId ?: throw BadRequestException("藏品必填"), writable = true)
        requireRegistered(collection)
        CollectionIdentityPolicy.requireUnchanged(collection.totalRegNo, vo.totalRegNo)
        if (vo.setAmendment != null) CollectionSetAmendments.coordinate(collection.orgId)
        locks.requireUnlocked(collection.id.value, businessType())
        if (collection.catalogStatus in setOf(EnumCatalogStatus.SUBMITTED, EnumCatalogStatus.PENDING)) {
            throw BadRequestException("编目正在审核，请先完成或驳回该审核")
        }
        val reason = vo.reason?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("变更事由必填")
        val supplied = vo.content ?: throw BadRequestException("变更内容必填")
        CollectionCategoryPolicy.requireSelected(supplied.categoryId)
        val proposed = supplied.copy(coverImage = attachments.resolveSingle(supplied.coverImage))
        if (proposed.warehouseId != collection.warehouseId) throw BadRequestException("库位变更请办理出入库或调拨手续")
        val before = currentContent(collection)
        val amendment = vo.setAmendment?.let { requested ->
            CollectionSetAmendments.prepare(collection, requested).also {
                CollectionSetAmendments.requireUnlocked(collection, it, locks)
                CollectionSetAmendments.validate(collection, it)
            }
        }
        if (proposed == before && (amendment == null || amendment.before == amendment.after)) throw BadRequestException("没有需要变更的内容")
        val evidence = normalizeFiles(vo.evidenceFileIds)
        // 变更前内容由当前主档取得，先保留该来源，不能由请求中的before为新封面授权。
        if (!before.coverImage.isNullOrBlank()) originalFiles?.rememberExistingMain(collection.id.value)
        val entity = saveDraft(vo.id, "变更申请不存在", createEntity = {
            dao.new {
                orgId = collection.orgId; collectionId = collection.id.value
                createBy = SecurityUtils.getUserId(); createTime = LocalDateTime.now()
            }
        }) {
            if (collectionId != collection.id.value) throw BadRequestException("变更申请不能更换藏品")
            if (orgId != collection.orgId) throw BadRequestException("变更申请与藏品所属收藏单位不一致，请保留原记录核对")
            if (!vo.id.isNullOrBlank()) originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_CHANGE, id.value)
            this.reason = reason
            this.before = before
            baseline = CollectionCatalogMapper.fromCollection(collection.toModel())
            content = proposed
            setAmendment = amendment
            keeperUserId = vo.keeperUserId?.trim()?.takeIf(String::isNotBlank)
            leaderUserId = vo.leaderUserId?.trim()?.takeIf(String::isNotBlank)
            evidenceFileIds = evidence
        }
        originalFiles?.remember(CollectionConst.BIZ_CHANGE, entity.id.value)
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "信息变更依据", evidence).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_CHANGE, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachments.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        return entity.id.value
    }

    override fun beforeSubmit(entity: CollectionChangeDao) {
        if (entity.flowInstanceId != null) throw BadRequestException("已提交申请不可复用，请保留原件并新建变更申请")
        requirePrepared(entity)
        originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_CHANGE, entity.id.value)
        archives.seal(entity.orgId, businessType(), entity.id.value, "SUBMITTED", buildFormData(entity) + mapOf("applicant" to entity.createBy), entity.evidenceFileIds)
    }

    private fun requirePrepared(entity: CollectionChangeDao) {
        requireNoCorrectionSuccessor(entity)
        requireCorrectionOrigin(entity)
        requireBaseline(entity)
        requireActors(entity)
        if (entity.evidenceFileIds.isNullOrBlank()) throw BadRequestException("变更依据附件必填")
        normalizeFiles(entity.evidenceFileIds)
        val completeness = CollectionCatalogPolicy.completeness(entity.content)
        if (!completeness.complete) throw BadRequestException("变更后著录信息不完整：${completeness.missingFields.joinToString("、")}")
    }

    override fun acquireApprovalResources(entity: CollectionChangeDao) {
        entity.setAmendment?.let { CollectionSetAmendments.coordinate(entity.orgId) }
        locks.acquire(entity.orgId, businessType(), entity.id.value,
            entity.setAmendment?.let { CollectionSetAmendments.affectedIds(entity.collectionId, it) } ?: listOf(entity.collectionId))
        requireBaseline(entity)
    }

    override fun releaseApprovalResources(entity: CollectionChangeDao) {
        // 批准至纸质订正生效期间继续占用，防止出库或其他申请改写审批基线。
        if (entity.flowStatus == EnumFlowStatus.PASS && entity.appliedAt == null) return
        locks.release(businessType(), entity.id.value)
    }

    override fun onFlowTaskCompleted(entity: CollectionChangeDao, event: FlowTaskCompletedEvent) {
        if (event.action != "APPROVE") return
        val expected = when (event.nodeKey) { "keeper" -> entity.keeperUserId; "leader" -> entity.leaderUserId; else -> return }
        if (event.operator != expected || event.comment.isNullOrBlank()) throw BadRequestException("必须由指定责任人本人填写意见并确认，不得代办")
        responsibleUsers.requireRole(event.operator, entity.orgId,
            if (event.nodeKey == "keeper") CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR)
    }

    override fun onFlowCompleted(entity: CollectionChangeDao, event: FlowCompletedEvent) {
        requireNoCorrectionSuccessor(entity)
        requireCorrectionOrigin(entity)
        if (event.instanceId != entity.flowInstanceId || entity.appliedAt != null) throw BadRequestException("审批实例与当前变更申请不一致")
        requireBaseline(entity)
        if (entity.setAmendment != null) requireSubmittedArchive(entity)
        requireActors(entity)
        // 引擎先发布完成事件、再发布最后一项任务事件，因此以同事务实际操作日志核对。
        val logs = io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(event.instanceId)
        val required = listOf("keeper" to entity.keeperUserId, "leader" to entity.leaderUserId).map { (node, person) ->
            logs.lastOrNull { it.nodeKey == node && it.operator == person && !it.comment.isNullOrBlank() && it.taskId != null }
                ?: throw BadRequestException("缺少${if (node == "keeper") "保管负责人确认" else "主要负责人批准"}，不能跳过法定审批环节")
        }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(required)
        required.forEach { log ->
            if (CollectionChangeApprovalDao.find { CollectionChangeApprovals.taskId eq log.taskId!! }.empty()) {
                CollectionChangeApprovalDao.new {
                    orgId = entity.orgId; changeId = entity.id.value; instanceId = event.instanceId
                    nodeKey = log.nodeKey!!; operator = log.operator!!; opinion = log.comment!!; taskId = log.taskId!!
                    createTime = log.createTime
                }
            }
        }
        archives.seal(entity.orgId, businessType(), entity.id.value, "APPROVED_" + required.last().taskId!!.takeLast(40), buildFormData(entity) + mapOf(
            "instanceId" to event.instanceId, "approvals" to required.map { mapOf("node" to it.nodeKey, "operator" to it.operator, "opinion" to it.comment, "time" to it.createTime) },
        ), entity.evidenceFileIds)
        super.onFlowCompleted(entity, event)
    }

    override fun beforeFlowReentry(entity: CollectionChangeDao, targetNodeKey: String) {
        if (entity.appliedAt != null) throw BadRequestException("已生效变更不能撤回审批，请发起新的变更申请")
        if (targetNodeKey != "keeper") throw BadRequestException("重新审批必须从保管负责人确认开始，不能复用上一轮意见")
        requirePrepared(entity)
    }


    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun execute(id: String, vo: CollectionChangeExecutionVo): String {
        val entity = scoped(id, writable = true)
        requireNoCorrectionSuccessor(entity)
        requireCorrectionOrigin(entity)
        if (entity.appliedAt != null) {
            requireAppliedOriginal(entity)
            requireSameAppliedExecution(entity, vo)
            return id
        }
        if (entity.flowStatus != EnumFlowStatus.PASS) throw BadRequestException("信息变更批准后才能办理签署归档和生效")
        CollectionChangePolicy.requireActors(SecurityUtils.getUserId() ?: throw BadRequestException("请登录后办理"), entity.keeperUserId, entity.leaderUserId)
        CollectionChangePolicy.requireExecution(vo)
        val paperPage = paperLedgers.requirePageReference(entity.orgId, vo.paperLedgerBookId, vo.paperLedgerPageNo)
        if (!vo.paperLedgerRef.isNullOrBlank() && vo.paperLedgerRef.trim() != paperPage.display)
            throw BadRequestException("纸质账文字引用与已核实的册号、页码不一致")
        val signed = normalizeFiles(vo.signedChangeFileIds)
        val currentTaskIds = io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(entity.flowInstanceId ?: "").mapNotNull { it.taskId }.toSet()
        val approvals = CollectionChangeApprovalDao.find {
            (CollectionChangeApprovals.changeId eq id) and (CollectionChangeApprovals.instanceId eq (entity.flowInstanceId ?: ""))
        }.filter { it.taskId in currentTaskIds }
        if (approvals.none { it.nodeKey == "keeper" && it.operator == entity.keeperUserId } ||
            approvals.none { it.nodeKey == "leader" && it.operator == entity.leaderUserId }) throw BadRequestException("法定审批证据不完整")
        val collection = requireBaseline(entity)
        if (entity.setAmendment != null) {
            requireSubmittedArchive(entity)
            val currentLogs = io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(entity.flowInstanceId!!)
            val required = listOf("keeper" to entity.keeperUserId, "leader" to entity.leaderUserId).map { (node, person) ->
                currentLogs.lastOrNull { it.nodeKey == node && it.operator == person && !it.comment.isNullOrBlank() && it.taskId != null }
                    ?: throw BadRequestException("套件订正缺少本轮批准原件")
            }
            io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(required)
            requireArchive(entity, "APPROVED_" + required.last().taskId!!.takeLast(40), buildFormData(entity) + mapOf(
                "instanceId" to entity.flowInstanceId, "approvals" to required.map { mapOf("node" to it.nodeKey, "operator" to it.operator, "opinion" to it.comment, "time" to it.createTime) },
            ))
        }
        locks.requireUnlocked(collection.id.value, businessType(), id)
        // 等待藏品行锁期间账号可能被撤权、调馆或改派库房职责；生效写入须使用锁后的当前资格。
        requireAccess(entity.orgId, write = true)
        val leaderApproval = approvals.single { it.nodeKey == "leader" }
        val approvedAt = leaderApproval.createTime
        if (vo.paperUpdatedAt!!.isBefore(approvedAt.toLocalDate())) throw BadRequestException("纸质账订正日期不能早于主要负责人批准日期")
        val now = LocalDateTime.now()
        val user = SecurityUtils.getUserId()
        val claimed = CollectionChanges.update({ (CollectionChanges.id eq id) and CollectionChanges.appliedAt.isNull() and (CollectionChanges.flowStatus eq EnumFlowStatus.PASS) }) {
            it[CollectionChanges.appliedAt] = now; it[CollectionChanges.appliedBy] = user
            it[CollectionChanges.signedChangeFileIds] = signed
            it[CollectionChanges.paperLedgerRef] = paperPage.display
            it[CollectionChanges.paperLedgerBookId] = paperPage.bookId
            it[CollectionChanges.paperLedgerPageNo] = paperPage.pageNo
            it[CollectionChanges.paperUpdatedAt] = vo.paperUpdatedAt
        }
        if (claimed != 1) throw BadRequestException("变更已由其他人员办理，请刷新")
        entity.refresh()
        val appliedRelations = entity.setAmendment?.let { CollectionSetAmendments.apply(collection, it) }
        catalogService.applyStatutoryChange(collection, entity.content, id, entity.reason, entity.createBy, leaderApproval.operator, approvedAt)
        CollectionLedgerLogDao.new {
            orgId = entity.orgId; collectionId = collection.id.value; totalRegNo = collection.totalRegNo
            action = EnumLedgerAction.CORRECT; field = "statutoryChange"
            before = JsonUtils.toJson(entity.before); after = JsonUtils.toJson(entity.content)
            reason = entity.reason; refBizId = id
        }
        entity.setAmendment?.let { amendment ->
            CollectionLedgerLogDao.new {
                orgId = entity.orgId; collectionId = collection.id.value; totalRegNo = collection.totalRegNo
                action = EnumLedgerAction.CORRECT; field = "setRelations"
                before = JsonUtils.toJson(amendment.before); after = JsonUtils.toJson(appliedRelations)
                reason = entity.reason; refBizId = id
            }
            archives.seal(entity.orgId, businessType(), id, "SET_APPLIED", mapOf("changeId" to id, "orgId" to entity.orgId,
                "collectionId" to entity.collectionId, "amendment" to amendment, "appliedRelations" to appliedRelations,
                "paperLedgerRef" to paperPage.display, "paperLedgerBookId" to paperPage.bookId,
                "paperLedgerPageNo" to paperPage.pageNo, "paperLedgerBookNo" to paperPage.bookNo,
                "paperLedgerPageCount" to paperPage.pageCount,
                "paperUpdatedAt" to vo.paperUpdatedAt, "appliedBy" to user, "appliedAt" to now), signed)
        }
        attachments.bind(id, "已签署藏品信息变更表", signed)
        attachments.bind(collection.id.value, "藏品信息变更表", signed)
        archives.seal(entity.orgId, businessType(), id, "APPLIED", entity.toModel(), listOfNotNull(entity.evidenceFileIds, signed).joinToString(","))
        locks.release(businessType(), id)
        return id
    }

    /** 重复办理不能用已生效标记掩盖签署表或套件订正原件的缺失、漂移。 */
    private fun requireAppliedOriginal(entity: CollectionChangeDao) {
        val id = entity.id.value
        val applied = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq businessType()) and (CollectionPermanentArchives.bizId eq id) and
                (CollectionPermanentArchives.phase eq "APPLIED")
        }.singleOrNull() ?: throw BadRequestException("已生效信息变更缺少签署归档原件，请专项核对")
        val signedSnapshot = try { JsonUtils.mapper.readTree(applied.snapshot) }
        catch (_: Exception) { throw BadRequestException("信息变更签署档案内容无法解析，请专项核对") }
        val currentSnapshot = JsonUtils.mapper.readTree(JsonUtils.toJson(entity.toModel()))
        if (!sameNonNullSnapshot(signedSnapshot, currentSnapshot))
            throw BadRequestException("已生效信息变更与签署归档原件不一致，请专项核对")
        archives.verifyExisting(applied, entity.orgId, businessType(), id, "APPLIED", signedSnapshot,
            listOfNotNull(entity.evidenceFileIds, entity.signedChangeFileIds).joinToString(","))
        entity.setAmendment?.let { amendment ->
            val setApplied = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq businessType()) and (CollectionPermanentArchives.bizId eq id) and
                    (CollectionPermanentArchives.phase eq "SET_APPLIED")
            }.singleOrNull() ?: throw BadRequestException("已生效套件订正缺少关系归档原件，请专项核对")
            val frozen = try { JsonUtils.mapper.readTree(setApplied.snapshot) }
            catch (_: Exception) { throw BadRequestException("套件生效档案内容无法解析，请专项核对") }
            fun matches(field: String, expected: Any?): Boolean {
                val actual = frozen.get(field)
                // 旧封存版本没有后来增补的可空字段；只在业务记录同样为空时承认原样历史。
                if (expected == null) return actual == null || actual.isNull
                return actual == JsonUtils.mapper.readTree(JsonUtils.toJson(expected) ?: "null")
            }
            val appliedRelations = frozen.get("appliedRelations")
            val legacyUnstructured = entity.paperLedgerBookId == null && entity.paperLedgerPageNo == null
            if (!frozen.isObject || !matches("changeId", id) || !matches("orgId", entity.orgId) ||
                !matches("collectionId", entity.collectionId) || !matches("amendment", amendment) ||
                !matches("paperLedgerRef", entity.paperLedgerRef) || !matches("paperLedgerBookId", entity.paperLedgerBookId) ||
                !matches("paperLedgerPageNo", entity.paperLedgerPageNo) || !matches("paperUpdatedAt", entity.paperUpdatedAt) ||
                !matches("appliedBy", entity.appliedBy) || !matches("appliedAt", entity.appliedAt) ||
                (appliedRelations != null && !appliedRelations.isArray) ||
                (!legacyUnstructured && appliedRelations?.isArray != true))
                throw BadRequestException("套件生效档案与已签署信息变更不一致，请专项核对")
            archives.verifyExisting(setApplied, entity.orgId, businessType(), id, "SET_APPLIED", frozen,
                entity.signedChangeFileIds)
        }
    }

    /** 原件完整也不能把另一份签署或纸账订正内容当作原请求重放成功。 */
    private fun requireSameAppliedExecution(entity: CollectionChangeDao, vo: CollectionChangeExecutionVo) {
        fun fail(): Nothing = throw BadRequestException("重复办理的信息变更签署或纸账订正与原件不一致，请核对原记录")
        val files=vo.signedChangeFileIds ?: fail()
        if (files != entity.signedChangeFileIds) {
            val references=files.split(',').map(String::trim).filter(String::isNotBlank)
            if (references.isEmpty()) fail()
            val resolved=references.map { attachments.resolveSingle(it) ?: fail() }.distinct().joinToString(",")
            if (resolved != entity.signedChangeFileIds) fail()
        }
        if (vo.paperUpdatedAt != entity.paperUpdatedAt ||
            vo.paperLedgerBookId != entity.paperLedgerBookId || vo.paperLedgerPageNo != entity.paperLedgerPageNo ||
            vo.paperLedgerRef?.trim()?.takeIf(String::isNotBlank)?.let { it != entity.paperLedgerRef } == true) fail()
    }

    /** 历史归档可缺少后来新增且业务值仍为空的字段；非空事实必须逐层完全一致。 */
    private fun sameNonNullSnapshot(left: tools.jackson.databind.JsonNode, right: tools.jackson.databind.JsonNode): Boolean {
        if (left.isObject && right.isObject) {
            val old = left.properties().filter { !it.value.isNull }.associate { it.key to it.value }
            val now = right.properties().filter { !it.value.isNull }.associate { it.key to it.value }
            return old.keys == now.keys && old.all { (key, value) -> sameNonNullSnapshot(value, now.getValue(key)) }
        }
        if (left.isArray && right.isArray)
            return left.size() == right.size() && (0 until left.size()).all { sameNonNullSnapshot(left.get(it), right.get(it)) }
        return left == right
    }

    private fun requireActors(entity: CollectionChangeDao) {
        CollectionChangePolicy.requireActors(entity.createBy ?: throw BadRequestException("变更申请缺少经办人"), entity.keeperUserId, entity.leaderUserId)
        responsibleUsers.requireRole(entity.keeperUserId!!, entity.orgId, CollectionConst.ROLE_STORAGE_HEAD)
        responsibleUsers.requireRole(entity.leaderUserId!!, entity.orgId, CollectionConst.ROLE_DIRECTOR)
    }

    private fun requireNoCorrectionSuccessor(entity: CollectionChangeDao) {
        if (CollectionChangeDao.find { CollectionChanges.previousChangeId eq entity.id.value }.any())
            throw BadRequestException("该申请已有后续补正，原申请和审批须保留，不能再次激活、批准、生效或作废原记录")
    }

    private fun requireClosedCorrectionSource(source: CollectionChangeDao, expectedInstanceId: String) {
        if (expectedInstanceId.isBlank() || source.flowInstanceId != expectedInstanceId) throw BadRequestException("原申请审批关联已变化，请刷新核对")
        val instance = FlowMutationCoordinator.instance(expectedInstanceId)
        CollectionChangeDao.find { CollectionChanges.id eq source.id.value }.forUpdate().single(); source.refresh()
        val state = when (source.flowStatus) { EnumFlowStatus.REJECT -> "REJECTED"; EnumFlowStatus.CANCEL -> "CANCELED"
            EnumFlowStatus.VOID -> instance.status.takeIf { it in setOf("REJECTED", "CANCELED") }; else -> null }
        if (state == null || source.appliedAt != null || source.signedChangeFileIds != null || source.paperUpdatedAt != null ||
            source.paperLedgerRef != null || source.paperLedgerBookId != null || source.paperLedgerPageNo != null ||
            source.flowInstanceId != instance.id.value || instance.status != state || instance.endTime == null || instance.parentInstanceId != null || instance.parentTaskId != null ||
            instance.businessType != businessType() || instance.businessKey != source.id.value || instance.definitionKey != definitionKey())
            throw BadRequestException("须先通过原业务驳回或撤销尚未生效的原申请；不能以补正替代已批准、签署或实施后的变更手续")
        if (FlowTasks.selectAll().where { (FlowTasks.instanceId eq instance.id.value) and (FlowTasks.status eq "PENDING") }.any() ||
            FlowInstances.selectAll().where { (FlowInstances.parentInstanceId eq instance.id.value) and (FlowInstances.status eq "RUNNING") }.any())
            throw BadRequestException("原审批仍有进行中的任务或子流程，请先核对原流程")
    }

    private fun requireSubmittedArchive(source: CollectionChangeDao): CollectionPermanentArchiveDao =
        requireArchive(source, "SUBMITTED", buildFormData(source) + mapOf("applicant" to source.createBy))

    private fun requireArchive(entity: CollectionChangeDao, phase: String, value: Any): CollectionPermanentArchiveDao {
        val row = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq businessType()) and
            (CollectionPermanentArchives.bizId eq entity.id.value) and (CollectionPermanentArchives.phase eq phase) }.singleOrNull()
            ?: throw BadRequestException("原申请缺少[$phase]阶段的已留存档案，须先专项核验，不能补造历史提交材料")
        val json = JsonUtils.toJson(value)!!
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (row.orgId != entity.orgId || row.snapshot != json || row.digest != digest) throw BadRequestException("原申请材料与永久档案不一致，请专项核对，不能继续沿用")
        return row
    }

    private fun correctionArchive(entity: CollectionChangeDao, submitted: CollectionPermanentArchiveDao) = mapOf(
        "newChangeId" to entity.id.value, "orgId" to entity.orgId, "collectionId" to entity.collectionId,
        "correction" to entity.correction, "sourceSubmittedSnapshot" to submitted.snapshot,
    )

    private fun requireCorrectionOrigin(entity: CollectionChangeDao) {
        val origin = entity.correction
        if (origin == null) {
            if (entity.previousChangeId != null || entity.correctionRequestKey != null) throw BadRequestException("补正来由记录不完整")
            return
        }
        if (entity.previousChangeId != origin.previousChangeId || entity.correctionRequestKey.isNullOrBlank() || origin.previousChangeId == entity.id.value)
            throw BadRequestException("补正申请的来源关联不一致")
        val previous = CollectionChangeDao.findById(origin.previousChangeId) ?: throw BadRequestException("原申请缺失，不能继续补正")
        if (previous.orgId != entity.orgId || previous.collectionId != entity.collectionId) throw BadRequestException("补正不能更换原馆或原藏品")
        requireClosedCorrectionSource(previous, origin.previousInstanceId)
        if (previous.flowStatus != origin.previousStatus) throw BadRequestException("原申请结束状态已变化，请先核对补正依据")
        val submitted = requireSubmittedArchive(previous)
        if (submitted.id.value != origin.submittedArchiveId || submitted.digest != origin.submittedDigest) throw BadRequestException("原提交档案关联已变化")
        requireArchive(entity, "CORRECTION_ORIGIN", correctionArchive(entity, submitted))
        requireCorrectionFamilyAvailable(entity)
    }

    /** 同一原申请的各次补正共用原流程协调；旧分支不能绕过正在办理的后续申请。 */
    private fun requireCorrectionFamilyAvailable(entity: CollectionChangeDao) {
        val ancestors = mutableSetOf<String>()
        var root = entity
        while (true) {
            if (!ancestors.add(root.id.value)) throw BadRequestException("补正来源存在循环关联，请专项核对")
            val previous = root.previousChangeId ?: break
            root = CollectionChangeDao.findById(previous) ?: throw BadRequestException("补正来源申请缺失")
            if (root.orgId != entity.orgId || root.collectionId != entity.collectionId) throw BadRequestException("补正来源馆别或藏品不一致")
        }
        requireClosedCorrectionSource(root, root.flowInstanceId ?: throw BadRequestException("原申请审批关联缺失"))
        val visited = mutableSetOf(root.id.value)
        var parents = listOf(root.id.value)
        while (parents.isNotEmpty()) {
            val children = CollectionChangeDao.find { CollectionChanges.previousChangeId inList parents }.toList()
            children.forEach { child ->
                if (!visited.add(child.id.value) || child.orgId != entity.orgId || child.collectionId != entity.collectionId)
                    throw BadRequestException("后续补正关联不一致，请专项核对")
                if (child.id.value != entity.id.value && (child.appliedAt != null ||
                    child.flowStatus !in setOf(EnumFlowStatus.REJECT, EnumFlowStatus.CANCEL, EnumFlowStatus.VOID)))
                    throw BadRequestException("原申请已有尚未结束或已批准的补正申请[${child.id.value}]，请先核对该申请")
            }
            parents = children.map { it.id.value }
        }
    }

    private fun requireBaseline(entity: CollectionChangeDao): CollectionDao {
        val collection = CollectionDao.findById(entity.collectionId) ?: throw BadRequestException("藏品不存在")
        requireRegistered(collection)
        if (collection.orgId != entity.orgId || !CollectionCatalogMapper.matchesSnapshot(entity.baseline, collection.toModel())) {
            throw BadRequestException("藏品信息已变化，请重新核对并新建变更申请")
        }
        entity.setAmendment?.let { amendment ->
            CollectionSetAmendments.coordinate(entity.orgId)
            CollectionSetAmendments.requireUnlocked(collection, amendment, locks, businessType(), entity.id.value)
            CollectionSetAmendments.validate(collection, amendment)
        }
        if (currentContent(collection) != entity.before) throw BadRequestException("正式编目版本已变化，请重新核对并新建申请")
        return collection
    }

    private fun requireRegistered(collection: CollectionDao) {
        if (!collection.registered || collection.catalogStatus != EnumCatalogStatus.APPROVED) {
            throw BadRequestException("请先完成总账正式登记，再办理纸质总账信息变更")
        }
    }

    private fun currentContent(collection: CollectionDao): CollectionCatalogContentVo {
        val approved = CollectionCatalogVersionDao.find {
            (CollectionCatalogVersions.orgId eq collection.orgId) and (CollectionCatalogVersions.collectionId eq collection.id.value) and
                (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED)
        }.maxByOrNull { it.version }?.content
        return approved?.copy(warehouseId = collection.warehouseId)
            ?: CollectionCatalogMapper.fromCollection(collection.toModel())
    }

    private fun collection(id: String, writable: Boolean = false): CollectionDao =
        (CollectionDao.findById(id) ?: throw BadRequestException("藏品不存在")).also {
            requireAccess(it.orgId, writable)
        }
    private fun scoped(id: String, writable: Boolean = false): CollectionChangeDao =
        (findById(id) ?: throw BadRequestException("变更申请不存在")).also {
            requireAccess(it.orgId, writable)
        }
    private fun enrich(
        entity: CollectionChangeDao,
        inspectedRetained: MutableMap<String, CollectionRetainedLedgerService.BoundSnapshotInspection>? = null,
        forDisplay: Boolean = false,
    ): CollectionChangeVo {
        val collection = CollectionDao.findById(entity.collectionId)?.takeIf { it.orgId == entity.orgId }
        if (collection != null) return entity.toModel().copy(totalRegNo = collection.totalRegNo, collectionName = collection.name)
        // 合法移馆后不能借接收馆的现值填原馆案卷；按案卷时间选原馆随后冻结的第一份留账。
        val retained = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.orgId eq entity.orgId) and
                (CollectionRetainedLedgers.collectionId eq entity.collectionId)
        }.filter { it.createTime >= entity.createTime }.sortedWith(compareBy({ it.createTime }, { it.id.value }))
        val first = retained.firstOrNull() ?: return entity.toModel()
        if (retained.drop(1).firstOrNull()?.createTime == first.createTime)
            throw BadRequestException("原馆留存总账时间重合，请核对历史交接")
        // 详情须先核对原馆快照绑定和摘要，文件不可用不影响读取已保存的名称、编号。
        val inspection = inspectedRetained?.getOrPut(first.id.value) {
            retainedLedgers.inspectBoundSnapshotForReader(first.id.value, entity.orgId,
                setOf(CollectionConst.LEDGER_CORRECT, CollectionConst.SELECT), CollectionConst.CROSS_ORG)
        } ?: if(forDisplay) retainedLedgers.boundSnapshotForDisplay(first.id.value, entity.orgId,
            setOf(CollectionConst.LEDGER_CORRECT, CollectionConst.SELECT), CollectionConst.CROSS_ORG) else null
        if(inspection?.snapshot==null && inspection?.issue!=null) return entity.toModel().copy(collectionEvidenceIssue = inspection.issue)
        val frozen = (inspection?.snapshot ?: retainedLedgers.verifiedBoundSnapshotForReader(first.id.value, entity.orgId,
            setOf(CollectionConst.LEDGER_CORRECT, CollectionConst.SELECT), CollectionConst.CROSS_ORG)).collection
        if (frozen.id != entity.collectionId || frozen.orgId != entity.orgId)
            throw BadRequestException("信息变更案卷与原馆留存总账身份不一致，请核对历史交接")
        return entity.toModel().copy(totalRegNo = frozen.totalRegNo, collectionName = frozen.name, collectionEvidenceIssue = inspection?.issue)
    }
    private fun normalizeFiles(ids: String?): String? = ids?.split(',')?.mapNotNull { attachments.resolveSingle(it) }
        ?.distinct()?.joinToString(",")?.takeIf(String::isNotBlank)
}
