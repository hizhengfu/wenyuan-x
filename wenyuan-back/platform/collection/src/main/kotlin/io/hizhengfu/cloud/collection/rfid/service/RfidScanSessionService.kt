package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionReturns
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.EnumReturnExecutionState
import io.hizhengfu.cloud.collection.dao.InventoryBatches
import io.hizhengfu.cloud.collection.dao.InventoryBatchDao
import io.hizhengfu.cloud.collection.dao.Loans
import io.hizhengfu.cloud.collection.dao.Requisitions
import io.hizhengfu.cloud.collection.domain.CollectionOutboundExecutionVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.InventoryRfidCheckVo
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanExpectedItems
import io.hizhengfu.cloud.collection.rfid.dao.RfidHandheldReadBatches
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanReads
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidReadClassification
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidRealtimeEventType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagStatus
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadBatchVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldReadBatchResultVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldSealVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldDeviceVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldTaskVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHandheldWorkstationVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidMissingVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidReadVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanFinalizeVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanPreviewVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanSessionCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanSessionStateMachine
import io.hizhengfu.cloud.collection.rfid.domain.RfidScanSessionVo
import io.hizhengfu.cloud.collection.service.InventoryService
import io.hizhengfu.cloud.collection.service.CollectionCurrentOperationAccess
import io.hizhengfu.cloud.collection.service.InventoryComplianceService
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.collection.service.LoanService
import io.hizhengfu.cloud.collection.service.CollectionReturnService
import io.hizhengfu.cloud.collection.service.OrgScopedReadPolicy
import io.hizhengfu.cloud.collection.service.RequisitionService
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.security.MessageDigest

@Component
@Transactional
class RfidScanSessionService(
    private val scope: CollectionScopeResolver,
    private val commandPort: RfidDeviceCommandPort,
    private val loanService: LoanService,
    private val requisitionService: RequisitionService,
    private val inventoryService: InventoryService,
    private val returnService: CollectionReturnService,
    private val realtime: RfidRealtimeEventService,
    @param:Value("\${collection.rfid.session-created-timeout-seconds:300}")
    private val createdTimeoutSeconds: Long,
    @param:Value("\${collection.rfid.session-command-timeout-seconds:180}")
    private val commandTimeoutSeconds: Long,
    @param:Value("\${collection.rfid.session-max-reading-seconds:14400}")
    private val maxReadingSeconds: Long,
    @param:Value("\${collection.rfid.session-preview-timeout-seconds:86400}")
    private val previewTimeoutSeconds: Long,
    @param:Value("\${collection.rfid.mobile-offline-window-seconds:259200}")
    private val mobileOfflineWindowSeconds: Long = 259_200,
    private val mobileInventory: InventoryComplianceService? = null,
) {
    @Transactional(readOnly = true)
    fun active(workstationId: String): RfidScanSessionVo? {
        val normalized = workstationId.clean(64) ?: throw BadRequestException("工作站必填")
        val station = RfidWorkstations.selectAll().where { RfidWorkstations.id eq normalized }.limit(1).firstOrNull()
            ?: throw BadRequestException("工作站不存在")
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), station[RfidWorkstations.orgId], "RFID 工作站")
        return RfidScanSessions.selectAll().where {
            (RfidScanSessions.workstationId eq normalized) and
                (RfidScanSessions.sessionState inList ACTIVE_STATES)
        }.orderBy(RfidScanSessions.createTime to SortOrder.DESC).limit(1).firstOrNull()?.let(::sessionVo)
    }

    @Transactional(readOnly = true)
    fun findPage(request: HttpServletRequest): GenericPage<RfidScanSessionVo> {
        val page = GenericPage<RfidScanSessionVo>().build(request)
        val query = RfidScanSessions.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidScanSessions.orgId inList it } }
        request.cleanParameter("workstationId", 64)?.let { query.andWhere { RfidScanSessions.workstationId eq it } }
        request.enumParameter<EnumRfidScanSessionType>("type")?.let { query.andWhere { RfidScanSessions.sessionType eq it } }
        request.enumParameter<EnumRfidScanSessionState>("state")?.let { query.andWhere { RfidScanSessions.sessionState eq it } }
        request.cleanParameter("keyword", 100)?.let { keyword ->
            query.andWhere {
                (RfidScanSessions.sessionNo like "%$keyword%") or
                    (RfidScanSessions.businessId like "%$keyword%") or
                    (RfidScanSessions.deviceId like "%$keyword%")
            }
        }
        request.epochParameter("createdAtStart")?.let { query.andWhere { RfidScanSessions.createTime greaterEq it } }
        request.epochParameter("createdAtEnd")?.let { query.andWhere { RfidScanSessions.createTime lessEq it } }
        query.orderBy(RfidScanSessions.createTime to SortOrder.DESC)
        page.list = query.pageRows(page).map(::sessionVo)
        return page
    }

    @Transactional(readOnly = true)
    fun handheldWorkstations(): List<RfidHandheldWorkstationVo> {
        val query = RfidWorkstations.selectAll().where { RfidWorkstations.enabled eq true }
        scope.writableOrgIds()?.let { query.andWhere { RfidWorkstations.orgId inList it } }
        return query.orderBy(RfidWorkstations.name to SortOrder.ASC).mapNotNull { station ->
            val taskTypes = handheldTaskTypes(station)
            if (taskTypes.isEmpty()) return@mapNotNull null
            val devices = handheldDevices(station[RfidWorkstations.id].value)
            if (devices.isEmpty()) return@mapNotNull null
            RfidHandheldWorkstationVo(
                id = station[RfidWorkstations.id].value,
                code = station[RfidWorkstations.code],
                name = station[RfidWorkstations.name],
                taskTypes = taskTypes,
                devices = devices,
            )
        }
    }

    @Transactional(readOnly = true)
    fun handheldTasks(request: HttpServletRequest): GenericPage<RfidHandheldTaskVo> {
        val type = request.enumParameter<EnumRfidScanSessionType>("type") ?: throw BadRequestException("type 必填")
        if (type !in HANDHELD_TASK_TYPES) throw BadRequestException("手持机不支持该扫描类型")
        val page = GenericPage<RfidHandheldTaskVo>().build(request)
        val keyword = request.cleanParameter("keyword", 100)
        val orgId = request.cleanParameter("orgId", 64) ?: scope.currentOrgId()
        val permissions = if (type == EnumRfidScanSessionType.INVENTORY)
            setOf(CollectionConst.RFID_SCAN, CollectionConst.STORAGE) else setOf(CollectionConst.RFID_SCAN)
        CollectionCurrentOperationAccess.require(scope, orgId, true, permissions, "手持作业")
        page.list = when (type) {
            EnumRfidScanSessionType.LOAN_OUT -> {
                val query = Loans.selectAll().where {
                    (Loans.orgId eq orgId) and (Loans.flowStatus eq EnumFlowStatus.PASS) and (Loans.outExecuted eq false)
                }
                keyword?.let { value -> query.andWhere {
                    (Loans.title like "%$value%") or (Loans.borrower like "%$value%") or (Loans.protocolNo like "%$value%")
                } }
                query.orderBy(Loans.createTime to SortOrder.DESC)
                query.pageRows(page).map { row ->
                    RfidHandheldTaskVo(
                        type,
                        row[Loans.id].value,
                        row[Loans.title] ?: "借展出库",
                        listOfNotNull(row[Loans.borrower], row[Loans.protocolNo]).joinToString(" · ").ifBlank { null },
                        collectionCount(row[Loans.collectionIds]),
                        row[Loans.createTime].epoch(),
                    )
                }
            }
            EnumRfidScanSessionType.REQUISITION_OUT -> {
                val query = Requisitions.selectAll().where {
                    (Requisitions.orgId eq orgId) and (Requisitions.flowStatus eq EnumFlowStatus.PASS) and
                        (Requisitions.outExecuted eq false) and Requisitions.supersededById.isNull() and Requisitions.sourceRetirement.isNull()
                }
                keyword?.let { value -> query.andWhere {
                    (Requisitions.documentNo like "%$value%") or (Requisitions.basis like "%$value%") or
                        (Requisitions.department like "%$value%")
                } }
                query.orderBy(Requisitions.createTime to SortOrder.DESC)
                query.pageRows(page).map { row ->
                    RfidHandheldTaskVo(
                        type,
                        row[Requisitions.id].value,
                        row[Requisitions.documentNo] ?: "提用出库",
                        listOfNotNull(row[Requisitions.department], row[Requisitions.basis]).joinToString(" · ").ifBlank { null },
                        collectionCount(row[Requisitions.collectionIds]),
                        row[Requisitions.createTime].epoch(),
                    )
                }
            }
            EnumRfidScanSessionType.RETURN_IN -> {
                val query = CollectionReturns.selectAll().where {
                    (CollectionReturns.orgId eq orgId) and (CollectionReturns.flowStatus eq EnumFlowStatus.PASS) and
                        (CollectionReturns.executionState eq EnumReturnExecutionState.PENDING)
                }
                keyword?.let { value -> query.andWhere {
                    (CollectionReturns.sourceDocumentNo like "%$value%") or (CollectionReturns.basis like "%$value%") or
                        (CollectionReturns.department like "%$value%")
                } }
                query.orderBy(CollectionReturns.createTime to SortOrder.DESC)
                query.pageRows(page).map { row ->
                    RfidHandheldTaskVo(
                        type,
                        row[CollectionReturns.id].value,
                        row[CollectionReturns.sourceDocumentNo] ?: "归还入库",
                        listOfNotNull(row[CollectionReturns.department], row[CollectionReturns.basis]).joinToString(" · ").ifBlank { null },
                        collectionCount(row[CollectionReturns.collectionIds]),
                        row[CollectionReturns.createTime].epoch(),
                    )
                }
            }
            EnumRfidScanSessionType.INVENTORY -> {
                val status = request.getParameter("inventoryStatus")?.trim()?.uppercase() ?: "ACTIVE"
                if (status !in setOf("ACTIVE", "COMPLETED")) throw BadRequestException("盘库任务状态无效")
                val query = InventoryBatches.selectAll().where {
                    (InventoryBatches.orgId eq orgId) and
                        InventoryBatches.plan.isNotNull() and InventoryBatches.baseline.isNotNull()
                }
                query.andWhere { if (status == "COMPLETED") InventoryBatches.finishedAt.isNotNull() else InventoryBatches.finishedAt.isNull() }
                keyword?.let { value -> query.andWhere {
                    (InventoryBatches.batchName like "%$value%") or (InventoryBatches.scopeDesc like "%$value%")
                } }
                query.orderBy(InventoryBatches.createTime to SortOrder.DESC)
                query.pageRows(page).map { row ->
                    RfidHandheldTaskVo(
                        type,
                        row[InventoryBatches.id].value,
                        row[InventoryBatches.batchName],
                        row[InventoryBatches.scopeDesc],
                        row[InventoryBatches.baseline]?.items?.size ?: collectionCount(row[InventoryBatches.collectionIds]),
                        row[InventoryBatches.createTime].epoch(),
                    )
                }
            }
            else -> emptyList()
        }
        return page
    }

    @Transactional(readOnly = true)
    fun activeHandheldSessions(): List<RfidScanSessionVo> {
        val user = SecurityUtils.getUserDetail()
        val query = RfidScanSessions.selectAll().where {
            (RfidScanSessions.directInput eq true) and
                (RfidScanSessions.operatorId eq user.id) and
                (RfidScanSessions.sessionState inList ACTIVE_STATES)
        }
        scope.readableOrgIds()?.let { query.andWhere { RfidScanSessions.orgId inList it } }
        return query.orderBy(RfidScanSessions.createTime to SortOrder.DESC).map(::sessionVo)
    }

    /** 固定设备会话返回其工作站；手持机直传会话由已认证用户和登记设备约束，不要求浏览器 Cookie。 */
    @Transactional(readOnly = true)
    fun fixedWorkstationId(id: String): String? {
        val session = findSession(id)
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), session[RfidScanSessions.orgId], "扫描会话")
        if (session[RfidScanSessions.directInput]) return null
        return session[RfidScanSessions.workstationId]
            ?: throw BadRequestException("固定设备会话缺少工作站绑定，请核对会话记录")
    }

    fun create(vo: RfidScanSessionCreateVo): RfidScanSessionVo {
        if (vo.directInput) return createMobileSession(vo)
        val workstationId = vo.workstationId.clean(64) ?: throw BadRequestException("工作站必填")
        val type = vo.type ?: throw BadRequestException("扫描类型必填")
        if (vo.directInput && type !in HANDHELD_TASK_TYPES) throw BadRequestException("手持机不支持该扫描类型")
        // 锁定工作站行，避免两个并发请求都在“无活动会话”检查后创建会话。
        val stationQuery = RfidWorkstations.selectAll().where { RfidWorkstations.id eq workstationId }.limit(1)
        stationQuery.forUpdate()
        val station = stationQuery.firstOrNull()
            ?: throw BadRequestException("工作站不存在")
        if (!station[RfidWorkstations.enabled]) throw BadRequestException("工作站已停用")
        requireStationSupports(type, station)
        val requestedDeviceId = vo.deviceId.clean(64)
        if (!vo.directInput && requestedDeviceId != null) throw BadRequestException("固定设备会话不能指定设备")
        val orgId = station[RfidWorkstations.orgId]
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "RFID 工作站")
        val businessId = vo.businessId.clean(64)
        val businessType = vo.businessType.clean(64) ?: defaultBusinessType(type)
        val currentOperatorId = SecurityUtils.getUserDetail().id
        val clientRequestId = vo.clientRequestId.clean(100)
        if (clientRequestId != null) {
            RfidScanSessions.selectAll().where {
                (RfidScanSessions.orgId eq orgId) and (RfidScanSessions.clientRequestId eq clientRequestId)
            }.limit(1).firstOrNull()?.let { existing ->
                val sameRequest = existing[RfidScanSessions.operatorId] == currentOperatorId &&
                    existing[RfidScanSessions.workstationId] == workstationId &&
                    existing[RfidScanSessions.sessionType] == type &&
                    existing[RfidScanSessions.directInput] == vo.directInput &&
                    existing[RfidScanSessions.businessType] == businessType &&
                    existing[RfidScanSessions.businessId] == businessId &&
                    (requestedDeviceId == null || existing[RfidScanSessions.deviceId] == requestedDeviceId)
                if (!sameRequest) throw BadRequestException("clientRequestId 已用于其他扫描请求")
                return sessionVo(existing)
            }
        }
        val selectedDeviceId = if (vo.directInput && requestedDeviceId != null) {
            if (handheldDevices(workstationId).none { it.id == requestedDeviceId }) {
                throw BadRequestException("所选手持机未绑定当前工作站或已停用")
            }
            requestedDeviceId
        } else resolveWorkstationDevice(station, requiredDevicePurpose(type)) { deviceId ->
            if (vo.directInput) commandPort.allowsDirectInput(deviceId) else commandPort.isOnline(deviceId)
        } ?: throw BadRequestException(
            if (vo.directInput) "工作站没有可用的已登记手持机" else "工作站没有在线的${requiredDevicePurpose(type).description}设备",
        )
        validateBusinessSource(type, businessId, orgId)
        val expectedIds = resolveExpectedIds(type, businessId, vo.collectionIds, orgId)
        if (expectedIds.isEmpty() && type !in setOf(EnumRfidScanSessionType.PASSAGE_DIAG, EnumRfidScanSessionType.TAG_VERIFY)) {
            throw BadRequestException("扫描会话没有应读藏品")
        }
        val collections = if (expectedIds.isEmpty()) emptyList() else Collections.selectAll()
            .where { Collections.id inList expectedIds }.toList()
        if (collections.size != expectedIds.size) throw BadRequestException("应读清单包含不存在的藏品")
        if (collections.any { it[Collections.orgId] != orgId }) throw BadRequestException("应读清单不能跨收藏单位")
        val active = RfidScanSessions.selectAll().where {
            (RfidScanSessions.workstationId eq workstationId) and
                (RfidScanSessions.sessionState inList ACTIVE_STATES)
        }.limit(1).any()
        if (active) throw BadRequestException("该工作站已有进行中的扫描会话")
        val id = IdGenerate.nextId()
        val now = LocalDateTime.now()
        RfidScanSessions.insert {
            it[RfidScanSessions.id] = id
            it[RfidScanSessions.orgId] = orgId
            it[sessionNo] = "RFID-$id"
            it[RfidScanSessions.workstationId] = workstationId
            it[deviceId] = selectedDeviceId
            it[sessionType] = type
            it[RfidScanSessions.businessType] = businessType
            it[RfidScanSessions.businessId] = businessId
            it[operatorId] = currentOperatorId
            it[directInput] = vo.directInput
            it[sessionState] = EnumRfidScanSessionState.CREATED
            it[expectedCount] = collections.size
            it[readCount] = 0
            it[RfidScanSessions.clientRequestId] = clientRequestId
            it[version] = 0
            it[createTime] = now
            it[createBy] = SecurityUtils.getUserId()
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        val activeTags = if (expectedIds.isEmpty()) emptyMap() else RfidTags.selectAll().where {
            (RfidTags.collectionId inList expectedIds) and (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE)
        }.associateBy { it[RfidTags.collectionId]!! }
        collections.forEach { collection ->
            val collectionId = collection[Collections.id].value
            val tag = activeTags[collectionId]
            RfidScanExpectedItems.insert {
                it[RfidScanExpectedItems.id] = IdGenerate.nextId()
                it[sessionId] = id
                it[RfidScanExpectedItems.collectionId] = collectionId
                it[expectedEpc] = tag?.get(RfidTags.epc) ?: collection[Collections.rfidTag]
                it[expectedTid] = tag?.get(RfidTags.tid)
                it[collectionNo] = collection[Collections.totalRegNo]
                it[collectionName] = collection[Collections.name].take(255)
            }
        }
        publishState(id)
        return sessionVo(findSession(id))
    }

    /** 原生手持机直接关联业务任务，不占用固定工作站，也不借用工作站设备身份。 */
    @Transactional(readOnly = true)
    fun mobileDevices(): List<RfidHandheldDeviceVo> {
        val orgId = scope.currentOrgId()
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.RFID_SCAN), "手持设备")
        return MuseumDevices.selectAll().where { MuseumDevices.orgId eq orgId }
            .orderBy(MuseumDevices.name to SortOrder.ASC).mapNotNull { row ->
                val id = row[MuseumDevices.id].value
                if (!commandPort.supportsAnyRole(id, orgId, setOf("HANDHELD"))) null
                else RfidHandheldDeviceVo(id, false, row[MuseumDevices.name], orgId)
            }
    }

    @Transactional(readOnly = true)
    fun mobileInventoryManifest(id: String): Map<String, Any?> {
        val batch = InventoryBatchDao.findById(id) ?: throw BadRequestException("盘库任务不存在")
        CollectionCurrentOperationAccess.require(scope, batch.orgId, true,
            setOf(CollectionConst.RFID_SCAN, CollectionConst.STORAGE), "盘库任务")
        val verified = requireCurrentMobileInventory(id)
        val baseline = batch.baseline ?: throw BadRequestException("请先冻结盘库任务清单")
        val ids = baseline.items.map { it.collectionId }
        val collections = Collections.selectAll().where {
            (Collections.id inList ids) and (Collections.orgId eq batch.orgId)
        }.associateBy { it[Collections.id].value }
        val tags = RfidTags.selectAll().where {
            (RfidTags.orgId eq batch.orgId) and (RfidTags.collectionId inList ids) and
                (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE)
        }.associateBy { it[RfidTags.collectionId] }
        val rounds = RfidScanSessions.selectAll().where {
            (RfidScanSessions.orgId eq batch.orgId) and (RfidScanSessions.businessId eq id) and
                (RfidScanSessions.sessionType eq EnumRfidScanSessionType.INVENTORY) and
                (RfidScanSessions.directInput eq true)
        }.orderBy(RfidScanSessions.createTime to SortOrder.ASC).toList()
        val sessions = rounds.filter { it[RfidScanSessions.sessionState] in setOf(
            EnumRfidScanSessionState.READING, EnumRfidScanSessionState.STOPPING,
            EnumRfidScanSessionState.PREVIEW_READY, EnumRfidScanSessionState.FINALIZED)
        }.map { it[RfidScanSessions.id].value }
        val reads = if (sessions.isEmpty()) emptyList() else RfidScanReads.selectAll().where {
            RfidScanReads.sessionId inList sessions
        }.toList()
        // 分区外不等于任务外：另一分区的串读也须放回完整冻结清单核对。
        val conflictIds = reads.filter { it[RfidScanReads.classification] == EnumRfidReadClassification.CONFLICT }
            .mapNotNull { it[RfidScanReads.resolvedCollectionId] }.toSet()
        val observed = reads.filter { it[RfidScanReads.classification] != EnumRfidReadClassification.CONFLICT }
            .mapNotNull { it[RfidScanReads.resolvedCollectionId] }.toSet() - conflictIds
        val roundIds = rounds.map { it[RfidScanSessions.id].value }
        val roundScopes = if (roundIds.isEmpty()) emptyMap() else RfidScanExpectedItems.selectAll().where {
            RfidScanExpectedItems.sessionId inList roundIds
        }.groupBy({ it[RfidScanExpectedItems.sessionId] }, { it[RfidScanExpectedItems.collectionId] })
        val itemLocations = baseline.items.associate { it.collectionId to it.locationName }
        val extraIds = reads.mapNotNull { it[RfidScanReads.resolvedCollectionId] }.toSet() - ids.toSet()
        val extraCollections = if (extraIds.isEmpty()) emptyMap() else Collections.selectAll().where {
            (Collections.id inList extraIds) and (Collections.orgId eq batch.orgId)
        }.associateBy { it[Collections.id].value }
        val checks = batch.checks?.items.orEmpty().associateBy { it.collectionId }
        val actions = batch.issueActions?.items.orEmpty().associateBy { it.collectionId }
        return mapOf(
            "id" to id, "orgId" to batch.orgId, "name" to batch.batchName,
            "scopeDescription" to batch.scopeDesc, "revision" to sha256(JsonUtils.toJson(baseline)!!),
            "checksRevision" to (batch.checks?.let { sha256(JsonUtils.toJson(it)!!) } ?: "NONE"),
            "issueActionsRevision" to (batch.issueActions?.let { sha256(JsonUtils.toJson(it)!!) } ?: "NONE"),
            "finished" to (batch.finishedAt != null), "finishedAt" to batch.finishedAt?.epoch(),
            "completion" to batch.completion, "plan" to batch.plan,
            "capturedAt" to baseline.capturedAt.epoch(), "downloadedAt" to System.currentTimeMillis(),
            "offlineWindowSeconds" to mobileOfflineWindowSeconds,
            "scanRounds" to rounds.map { round -> mapOf(
                "id" to round[RfidScanSessions.id].value, "deviceId" to round[RfidScanSessions.deviceId],
                "state" to round[RfidScanSessions.sessionState], "error" to round[RfidScanSessions.errorMessage],
                "scopeLocations" to roundScopes[round[RfidScanSessions.id].value].orEmpty().mapNotNull(itemLocations::get).distinct(),
                "expectedCount" to round[RfidScanSessions.expectedCount], "readCount" to round[RfidScanSessions.readCount],
                "startedAt" to round[RfidScanSessions.startedAt]?.epoch(),
            ) },
            "reads" to reads.map { read ->
                val collectionId = read[RfidScanReads.resolvedCollectionId]
                val collection = collections[collectionId] ?: extraCollections[collectionId]
                mapOf("epc" to read[RfidScanReads.epc], "tid" to read[RfidScanReads.tid],
                    "classification" to read[RfidScanReads.classification],
                    "collectionId" to collection?.get(Collections.id)?.value,
                    "collectionName" to collection?.get(Collections.name), "collectionNo" to collection?.get(Collections.totalRegNo),
                    "lifeStatus" to collection?.get(Collections.lifeStatus),
                    "location" to itemLocations[collectionId],
                    "scopeLocations" to roundScopes[read[RfidScanReads.sessionId]].orEmpty().mapNotNull(itemLocations::get).distinct(),
                    "sessionId" to read[RfidScanReads.sessionId], "lastSeenAt" to read[RfidScanReads.lastSeenAt].epoch(),
                    "readCount" to read[RfidScanReads.readCount], "rssi" to read[RfidScanReads.lastRssi])
            },
            "items" to baseline.items.map { item ->
                val current = collections[item.collectionId]
                mapOf("collectionId" to item.collectionId,
                    "name" to item.content.formalName, "collectionNo" to current?.get(Collections.totalRegNo),
                    "location" to item.locationName, "locationKey" to item.locationKey,
                    "locationVerified" to item.locationVerified, "lifeStatus" to current?.get(Collections.lifeStatus),
                    "overdue" to (item.overdue || item.collectionId in verified.overdueCollectionIds),
                    "coverImage" to item.content.coverImage, "content" to item.content,
                    "epc" to (tags[item.collectionId]?.get(RfidTags.epc) ?: current?.get(Collections.rfidTag)),
                    "tid" to tags[item.collectionId]?.get(RfidTags.tid),
                    "observed" to (item.collectionId in observed), "conflict" to (item.collectionId in conflictIds),
                    "check" to checks[item.collectionId], "issueAction" to actions[item.collectionId],
                    "checkRevision" to (checks[item.collectionId]?.let { sha256(JsonUtils.toJson(it)!!) } ?: "NONE"))
            },
        )
    }

    private fun createMobileSession(vo: RfidScanSessionCreateVo): RfidScanSessionVo {
        if (!vo.workstationId.isNullOrBlank()) throw BadRequestException("移动端作业不关联固定工作站")
        val type = vo.type ?: throw BadRequestException("扫描类型必填")
        if (type !in HANDHELD_TASK_TYPES) throw BadRequestException("手持机不支持该扫描类型")
        val deviceId = vo.deviceId.clean(64) ?: throw BadRequestException("请选择已登记的手持设备")
        // 设备行锁覆盖同一设备的并发创建；不同手持机可同时服务同一盘库批次。
        val device = MuseumDevices.selectAll().where { MuseumDevices.id eq deviceId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("手持设备不存在")
        val orgId = device[MuseumDevices.orgId]
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.RFID_SCAN), "手持作业")
        if (!commandPort.supportsAnyRole(deviceId, orgId, setOf("HANDHELD"))) throw BadRequestException("设备已停用或不是手持读写器")
        val businessId = vo.businessId.clean(64) ?: throw BadRequestException("请选择作业任务")
        val operator = SecurityUtils.getUserDetail().id
        val requestId = vo.clientRequestId.clean(100) ?: throw BadRequestException("clientRequestId 必填")
        RfidScanSessions.selectAll().where {
            (RfidScanSessions.orgId eq orgId) and (RfidScanSessions.clientRequestId eq requestId)
        }.singleOrNull()?.let { old ->
            if (!old[RfidScanSessions.directInput] || old[RfidScanSessions.operatorId] != operator ||
                old[RfidScanSessions.deviceId] != deviceId || old[RfidScanSessions.sessionType] != type ||
                old[RfidScanSessions.businessId] != businessId) throw BadRequestException("clientRequestId 已用于其他扫描请求")
            return sessionVo(old)
        }
        val expectedIds = if (type == EnumRfidScanSessionType.INVENTORY) {
            val batch = InventoryBatchDao.find { InventoryBatches.id eq businessId }.forUpdate().singleOrNull()
                ?: throw BadRequestException("盘库任务不存在")
            batch.refresh(flush = false)
            if (batch.orgId != orgId) throw BadRequestException("盘库任务与手持设备不属于同一收藏单位")
            requireCurrentMobileInventory(businessId)
            if (batch.finishedAt != null) throw BadRequestException("盘库任务已结束")
            val baseline = batch.baseline ?: throw BadRequestException("请先冻结盘库任务清单")
            if (batch.plan == null) throw BadRequestException("请先建立实际清点方案")
            val all = baseline.items.map { it.collectionId }
            val selected = vo.collectionIds.map(String::trim).filter(String::isNotBlank).distinct()
            if (selected.any { it !in all }) throw BadRequestException("扫描分区包含任务范围外藏品")
            selected.ifEmpty { all }
        } else {
            if (vo.collectionIds.isNotEmpty()) throw BadRequestException("借还核对不能替换已审批单据的藏品清单")
            validateBusinessSource(type, businessId, orgId)
            resolveExpectedIds(type, businessId, emptyList(), orgId)
        }
        if (expectedIds.isEmpty()) throw BadRequestException("作业任务没有应盘藏品")
        if (RfidScanSessions.selectAll().where {
                (RfidScanSessions.deviceId eq deviceId) and (RfidScanSessions.sessionState inList ACTIVE_STATES)
            }.any()) throw BadRequestException("该手持设备已有进行中的扫描会话")
        val collections = Collections.selectAll().where { Collections.id inList expectedIds }.toList()
        if (collections.size != expectedIds.size || collections.any { it[Collections.orgId] != orgId }) {
            throw BadRequestException("任务清单中的藏品不存在或不属于当前收藏单位")
        }
        val id = IdGenerate.nextId()
        val now = LocalDateTime.now()
        RfidScanSessions.insert {
            it[RfidScanSessions.id] = id; it[RfidScanSessions.orgId] = orgId; it[sessionNo] = "RFID-$id"
            it[workstationId] = null; it[RfidScanSessions.deviceId] = deviceId; it[sessionType] = type
            it[businessType] = defaultBusinessType(type); it[RfidScanSessions.businessId] = businessId
            it[operatorId] = operator; it[directInput] = true; it[sessionState] = EnumRfidScanSessionState.CREATED
            it[expectedCount] = collections.size; it[readCount] = 0; it[clientRequestId] = requestId
            it[version] = 0; it[createTime] = now; it[updateTime] = now; it[createBy] = operator; it[updateBy] = operator
        }
        val tags = RfidTags.selectAll().where {
            (RfidTags.collectionId inList expectedIds) and (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE)
        }.associateBy { it[RfidTags.collectionId] }
        collections.forEach { collection ->
            val cid = collection[Collections.id].value
            RfidScanExpectedItems.insert {
                it[RfidScanExpectedItems.id] = IdGenerate.nextId(); it[sessionId] = id; it[collectionId] = cid
                it[expectedEpc] = tags[cid]?.get(RfidTags.epc) ?: collection[Collections.rfidTag]
                it[expectedTid] = tags[cid]?.get(RfidTags.tid)
                it[collectionNo] = collection[Collections.totalRegNo]; it[collectionName] = collection[Collections.name].take(255)
            }
        }
        publishState(id)
        return sessionVo(findSession(id))
    }

    fun start(id: String): RfidScanSessionVo {
        var session = requireWritableSession(id)
        if (session[RfidScanSessions.sessionState] == EnumRfidScanSessionState.PREVIEW_READY) {
            // 重新读取是一次新的证据窗口，旧读数必须清除，否则会掩盖本次漏读。
            RfidScanReads.deleteWhere { RfidScanReads.sessionId eq id }
            RfidScanSessions.update({ RfidScanSessions.id eq id }) { it[readCount] = 0 }
        }
        transition(session, EnumRfidScanSessionState.STARTING)
        session = findSession(id)
        val now = LocalDateTime.now()
        if (session[RfidScanSessions.directInput]) {
            transition(session, EnumRfidScanSessionState.READING) {
                it[RfidScanSessions.startedAt] = now
                it[RfidScanSessions.settledAt] = null
            }
        } else {
            val commandId = commandPort.startInventory(session[RfidScanSessions.deviceId], id)
            RfidScanSessions.update({ RfidScanSessions.id eq id }) {
                it[startCommandId] = commandId
                it[updateTime] = now
            }
        }
        publishState(id)
        return sessionVo(findSession(id))
    }

    /** 固定读写器开始命令成功后才进入 READING，避免 UI 显示读卡中但设备实际未启动。 */
    fun acknowledgeStart(commandId: String, success: Boolean, message: String?): String {
        val session = RfidScanSessions.selectAll().where { RfidScanSessions.startCommandId eq commandId }
            .limit(1).firstOrNull() ?: throw BadRequestException("未找到开始命令对应会话")
        if (session[RfidScanSessions.sessionState] != EnumRfidScanSessionState.STARTING) return session[RfidScanSessions.id].value
        transition(session, if (success) EnumRfidScanSessionState.READING else EnumRfidScanSessionState.FAILED) {
            if (success) it[RfidScanSessions.startedAt] = LocalDateTime.now()
            else it[RfidScanSessions.errorMessage] = message.clean(500) ?: "设备启动失败"
        }
        publishState(session[RfidScanSessions.id].value)
        return session[RfidScanSessions.id].value
    }

    /** 登录用户直传只允许手持机 directInput 会话，固定读写器读数必须走采集服务凭证。 */
    fun recordRead(id: String, vo: RfidHandheldReadVo): RfidScanPreviewVo = recordReadInternal(id, vo, directInput = true)

    /** 固定设备读数的受信入口，仅由带独立 API Key 的 museum-ingest 回调调用。 */
    fun recordDeviceRead(id: String, vo: RfidHandheldReadVo): RfidScanPreviewVo = recordReadInternal(id, vo, directInput = false)

    private fun recordReadInternal(id: String, vo: RfidHandheldReadVo, directInput: Boolean): RfidScanPreviewVo {
        val session = findSession(id)
        if (session[RfidScanSessions.directInput] != directInput) {
            throw BadRequestException(if (directInput) "固定读写器会话不接受浏览器直传读数" else "手持机直传会话不接受采集服务读数")
        }
        if (directInput) {
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), session[RfidScanSessions.orgId], "扫描会话")
            requireDirectUploadOperator(session)
        }
        val state = session[RfidScanSessions.sessionState]
        val withinSettleWindow = state == EnumRfidScanSessionState.PREVIEW_READY &&
            session[RfidScanSessions.settledAt]?.isAfter(LocalDateTime.now()) == true
        if (state !in setOf(EnumRfidScanSessionState.READING, EnumRfidScanSessionState.STOPPING) && !withinSettleWindow) {
            throw BadRequestException("扫描会话当前不接收读数")
        }
        val sourceDeviceId = vo.deviceId.clean(64)
        if (directInput && sourceDeviceId == null) throw BadRequestException("deviceId 必填")
        if (sourceDeviceId != null && sourceDeviceId != session[RfidScanSessions.deviceId]) {
            throw BadRequestException("读数来源设备与会话不一致")
        }
        vo.sourceEventId.clean(100)?.let { sourceId ->
            if (RfidScanReads.selectAll().where { RfidScanReads.sourceEventId eq sourceId }.limit(1).any()) return preview(id)
        }
        val epc = RfidHex.normalize(vo.epc, "epc")!!
        val tid = RfidHex.normalize(vo.tid, "tid", required = false)
        val eventAt = vo.occurredAt.toLocalDateTime() ?: LocalDateTime.now()
        val expected = RfidScanExpectedItems.selectAll().where {
            (RfidScanExpectedItems.sessionId eq id) and (RfidScanExpectedItems.expectedEpc eq epc)
        }.limit(1).firstOrNull()
        val tag = RfidTags.selectAll().where {
            (RfidTags.orgId eq session[RfidScanSessions.orgId]) and (RfidTags.epc eq epc)
        }.limit(1).firstOrNull()
        val tidConflict = when {
            expected?.get(RfidScanExpectedItems.expectedTid) != null && tid != null -> expected[RfidScanExpectedItems.expectedTid] != tid
            tag?.get(RfidTags.tid) != null && tid != null -> tag[RfidTags.tid] != tid
            else -> false
        }
        val classification = when {
            tidConflict -> EnumRfidReadClassification.CONFLICT
            expected != null -> EnumRfidReadClassification.EXPECTED
            tag?.get(RfidTags.collectionId) != null -> EnumRfidReadClassification.EXTRA
            else -> EnumRfidReadClassification.UNKNOWN
        }
        val current = RfidScanReads.selectAll().where {
            (RfidScanReads.sessionId eq id) and (RfidScanReads.epc eq epc)
        }.limit(1).firstOrNull()
        if (current == null) {
            RfidScanReads.insert {
                it[RfidScanReads.id] = IdGenerate.nextId()
                it[sessionId] = id
                it[RfidScanReads.epc] = epc
                it[RfidScanReads.tid] = tid
                it[antenna] = vo.antenna
                it[direction] = vo.direction ?: EnumRfidDirection.UNKNOWN
                it[firstSeenAt] = eventAt
                it[lastSeenAt] = eventAt
                it[readCount] = 1
                it[peakRssi] = vo.rssi
                it[lastRssi] = vo.rssi
                it[sourceEventId] = vo.sourceEventId.clean(100)
                it[resolvedCollectionId] = expected?.get(RfidScanExpectedItems.collectionId) ?: tag?.get(RfidTags.collectionId)
                it[RfidScanReads.classification] = classification
            }
            RfidScanSessions.update({ RfidScanSessions.id eq id }) { it[readCount] = session[RfidScanSessions.readCount] + 1 }
        } else {
            RfidScanReads.update({ RfidScanReads.id eq current[RfidScanReads.id].value }) {
                it[RfidScanReads.tid] = tid ?: current[RfidScanReads.tid]
                it[antenna] = vo.antenna ?: current[RfidScanReads.antenna]
                it[direction] = vo.direction ?: current[RfidScanReads.direction]
                it[lastSeenAt] = maxOf(eventAt, current[RfidScanReads.lastSeenAt])
                it[readCount] = current[RfidScanReads.readCount] + 1
                it[peakRssi] = listOfNotNull(current[RfidScanReads.peakRssi], vo.rssi).maxOrNull()
                it[lastRssi] = vo.rssi
                it[sourceEventId] = vo.sourceEventId.clean(100) ?: current[RfidScanReads.sourceEventId]
                // 冲突一旦出现，本会话内不降级，必须由人工重新开始一轮读取。
                it[RfidScanReads.classification] = if (current[RfidScanReads.classification] == EnumRfidReadClassification.CONFLICT) {
                    EnumRfidReadClassification.CONFLICT
                } else classification
            }
        }
        if (withinSettleWindow) {
            // 手持机 HTTP 请求可能在“停止”之后才到达；每条延迟读数都重新开始静默窗口。
            RfidScanSessions.update({ RfidScanSessions.id eq id }) {
                it[settledAt] = LocalDateTime.now().plusNanos(SETTLE_MILLIS * 1_000_000)
            }
        }
        realtime.publish(
            session[RfidScanSessions.orgId],
            session[RfidScanSessions.workstationId],
            id,
            EnumRfidRealtimeEventType.TAG_BATCH,
            mapOf("epc" to epc, "classification" to classification.name),
        )
        return preview(id)
    }

    /** 手持机断网恢复后按本地会话和批次序号重传；整批与读数同事务，重试不会增加读取次数。 */
    fun recordReadBatch(id: String, vo: RfidHandheldReadBatchVo): RfidHandheldReadBatchResultVo {
        // 同一服务端会话的批次串行落库，避免并发重传在唯一键处退化为数据库异常。
        val sessionQuery = RfidScanSessions.selectAll().where { RfidScanSessions.id eq id }.limit(1)
        sessionQuery.forUpdate()
        val session = sessionQuery.firstOrNull() ?: throw BadRequestException("扫描会话不存在")
        if (!session[RfidScanSessions.directInput]) throw BadRequestException("只有手持机直传会话可上传离线批次")
        if (session[RfidScanSessions.workstationId] == null) {
            CollectionCurrentOperationAccess.require(scope, session[RfidScanSessions.orgId], true,
                setOf(CollectionConst.RFID_SCAN), "移动扫描会话")
            if (!commandPort.supportsAnyRole(session[RfidScanSessions.deviceId], session[RfidScanSessions.orgId], setOf("HANDHELD")))
                throw BadRequestException("手持设备已停用或登记角色不匹配")
        }
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), session[RfidScanSessions.orgId], "扫描会话")
        requireDirectUploadOperator(session)
        val deviceId = vo.deviceId.clean(64) ?: throw BadRequestException("deviceId 必填")
        if (deviceId != session[RfidScanSessions.deviceId]) throw BadRequestException("批次来源设备与会话不一致")
        val localSessionId = vo.localSessionId.clean(64) ?: throw BadRequestException("localSessionId 必填")
        val sequence = vo.batchSequence ?: throw BadRequestException("batchSequence 必填")
        if (sequence < 0) throw BadRequestException("batchSequence 不能小于 0")
        if (vo.reads.isEmpty() || vo.reads.size > 500) throw BadRequestException("reads 数量必须在 1 至 500 之间")
        val existing = RfidHandheldReadBatches.selectAll().where {
            (RfidHandheldReadBatches.deviceId eq deviceId) and
                (RfidHandheldReadBatches.localSessionId eq localSessionId) and
                (RfidHandheldReadBatches.batchSequence eq sequence)
        }.limit(1).firstOrNull()
        if (existing != null) {
            if (existing[RfidHandheldReadBatches.sessionId] != id) throw BadRequestException("本地会话批次已绑定其他服务端会话")
            return RfidHandheldReadBatchResultVo(id, localSessionId, sequence, existing[RfidHandheldReadBatches.readCount], true, preview(id))
        }
        RfidHandheldReadBatches.insert {
            it[RfidHandheldReadBatches.id] = IdGenerate.nextId()
            it[sessionId] = id
            it[RfidHandheldReadBatches.deviceId] = deviceId
            it[RfidHandheldReadBatches.localSessionId] = localSessionId
            it[batchSequence] = sequence
            it[readCount] = vo.reads.size
            it[receivedAt] = LocalDateTime.now()
        }
        vo.reads.forEachIndexed { index, read ->
            recordReadInternal(
                id,
                read.copy(
                    deviceId = deviceId,
                    sourceEventId = "HB:" + sha256("$deviceId:$localSessionId:$sequence:$index"),
                ),
                directInput = true,
            )
        }
        return RfidHandheldReadBatchResultVo(id, localSessionId, sequence, vo.reads.size, false, preview(id))
    }

    /** 工作站本机服务按流和序号整批重传，避免 HTTP 应答丢失后重复计入读卡次数。 */
    fun recordDeviceReadBatch(
        id: String, deviceId: String, streamId: String, sequence: Long, reads: List<RfidHandheldReadVo>,
    ): Int {
        val query = RfidScanSessions.selectAll().where { RfidScanSessions.id eq id }.limit(1)
        query.forUpdate()
        val session = query.firstOrNull() ?: throw BadRequestException("扫描会话不存在")
        if (session[RfidScanSessions.directInput] || session[RfidScanSessions.deviceId] != deviceId) {
            throw BadRequestException("批次来源设备与固定读写器会话不一致")
        }
        val normalizedStreamId = streamId.clean(64) ?: throw BadRequestException("读数流标识必填")
        if (!normalizedStreamId.matches(Regex("[0-9a-fA-F-]{36}")) || sequence < 0 || reads.isEmpty() || reads.size > 100) {
            throw BadRequestException("读数批次参数无效")
        }
        val localSessionId = "WS:$normalizedStreamId"
        val existing = RfidHandheldReadBatches.selectAll().where {
            (RfidHandheldReadBatches.deviceId eq deviceId) and
                (RfidHandheldReadBatches.localSessionId eq localSessionId) and
                (RfidHandheldReadBatches.batchSequence eq sequence)
        }.limit(1).firstOrNull()
        if (existing != null) {
            if (existing[RfidHandheldReadBatches.sessionId] != id) throw BadRequestException("读数流批次已绑定其他会话")
            if (existing[RfidHandheldReadBatches.readCount] != reads.size) throw BadRequestException("重传批次条数不一致")
            return existing[RfidHandheldReadBatches.readCount]
        }
        RfidHandheldReadBatches.insert {
            it[RfidHandheldReadBatches.id] = IdGenerate.nextId()
            it[sessionId] = id
            it[RfidHandheldReadBatches.deviceId] = deviceId
            it[RfidHandheldReadBatches.localSessionId] = localSessionId
            it[batchSequence] = sequence
            it[readCount] = reads.size
            it[receivedAt] = LocalDateTime.now()
        }
        reads.forEachIndexed { index, item ->
            recordReadInternal(
                id, item.copy(deviceId = deviceId, sourceEventId = "WS:" + sha256("$deviceId:$localSessionId:$sequence:$index")),
                directInput = false,
            )
        }
        return reads.size
    }

    fun stop(id: String): RfidScanSessionVo {
        val session = requireWritableSession(id)
        if (session[RfidScanSessions.sessionState] in setOf(
                EnumRfidScanSessionState.STOPPING,
                EnumRfidScanSessionState.PREVIEW_READY,
                EnumRfidScanSessionState.FINALIZED,
                EnumRfidScanSessionState.CANCELLED,
                EnumRfidScanSessionState.EXPIRED,
                EnumRfidScanSessionState.FAILED,
            )
        ) {
            return sessionVo(session)
        }
        transition(session, EnumRfidScanSessionState.STOPPING) { it[RfidScanSessions.stopRequestedAt] = LocalDateTime.now() }
        val stopping = findSession(id)
        if (stopping[RfidScanSessions.directInput]) {
            acknowledgeStoppedSession(stopping)
        } else {
            val commandId = commandPort.stopInventory(stopping[RfidScanSessions.deviceId], id)
            RfidScanSessions.update({ RfidScanSessions.id eq id }) {
                it[stopCommandId] = commandId
                it[updateTime] = LocalDateTime.now()
            }
        }
        publishState(id)
        return sessionVo(findSession(id))
    }

    fun sealMobileObservations(id: String, vo: RfidHandheldSealVo): Map<String, Any> {
        val locked = RfidScanSessions.selectAll().where { RfidScanSessions.id eq id }.forUpdate().singleOrNull()
            ?: throw BadRequestException("扫描会话不存在")
        requireWritableSession(id)
        if (!locked[RfidScanSessions.directInput] || locked[RfidScanSessions.sessionType] != EnumRfidScanSessionType.INVENTORY)
            throw BadRequestException("仅移动盘库会话可封存观察证据")
        if (locked[RfidScanSessions.sessionState] == EnumRfidScanSessionState.FINALIZED) return mapOf("sealed" to true)
        if (vo.batchCount < 0 || vo.localSessionId.isBlank() || vo.localSessionId.length > 64)
            throw BadRequestException("离线批次清单无效")
        val batches = RfidHandheldReadBatches.selectAll().where {
            (RfidHandheldReadBatches.sessionId eq id) and (RfidHandheldReadBatches.localSessionId eq vo.localSessionId)
        }.map { it[RfidHandheldReadBatches.batchSequence] }.sorted()
        if (RfidHandheldReadBatches.selectAll().where {
                (RfidHandheldReadBatches.sessionId eq id) and (RfidHandheldReadBatches.localSessionId neq vo.localSessionId)
            }.any()) throw BadRequestException("扫描会话的本地流不一致，请核对全部离线批次")
        if (batches.size.toLong() != vo.batchCount || batches.withIndex().any { it.value != it.index.toLong() })
            throw BadRequestException("离线批次尚未完整接收，请先同步读数")
        stop(id)
        val current = findSession(id)
        if (current[RfidScanSessions.sessionState] != EnumRfidScanSessionState.PREVIEW_READY)
            throw BadRequestException("当前会话不能封存，请核对状态")
        if (current[RfidScanSessions.settledAt]?.isAfter(LocalDateTime.now()) == true)
            return mapOf("sealed" to false, "retryAfterMillis" to SETTLE_MILLIS)
        transition(current, EnumRfidScanSessionState.FINALIZING)
        transition(findSession(id), EnumRfidScanSessionState.FINALIZED) {
            it[RfidScanSessions.finalizedAt] = LocalDateTime.now()
            it[RfidScanSessions.finalResultJson] = JsonUtils.toJson(mapOf("observationOnly" to true, "batchCount" to vo.batchCount))
        }
        publishState(id)
        return mapOf("sealed" to true)
    }

    fun acknowledgeStop(commandId: String, success: Boolean, message: String?): String {
        val session = RfidScanSessions.selectAll().where { RfidScanSessions.stopCommandId eq commandId }
            .limit(1).firstOrNull() ?: throw BadRequestException("未找到停止命令对应会话")
        if (session[RfidScanSessions.sessionState] != EnumRfidScanSessionState.STOPPING) return session[RfidScanSessions.id].value
        if (success) acknowledgeStoppedSession(session) else transition(session, EnumRfidScanSessionState.FAILED) {
            it[RfidScanSessions.errorMessage] = message.clean(500) ?: "设备停止失败"
        }
        publishState(session[RfidScanSessions.id].value)
        return session[RfidScanSessions.id].value
    }

    fun get(id: String): RfidScanSessionVo {
        val session = reconcileDeviceFailure(findSession(id))
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), session[RfidScanSessions.orgId], "扫描会话")
        return sessionVo(session)
    }

    /** 本机读写器断开后不保留可继续确认的扫描会话。 */
    fun failLocalDeviceSessions(deviceId: String, reason: String) {
        val sessions = RfidScanSessions.selectAll().where {
            (RfidScanSessions.deviceId eq deviceId) and
                (RfidScanSessions.directInput eq false) and
                (RfidScanSessions.sessionState inList listOf(
                    EnumRfidScanSessionState.STARTING, EnumRfidScanSessionState.READING, EnumRfidScanSessionState.STOPPING,
                ))
        }.toList()
        sessions.forEach { session ->
            transition(session, EnumRfidScanSessionState.FAILED) {
                it[RfidScanSessions.errorMessage] = reason.take(500)
            }
            publishState(session[RfidScanSessions.id].value)
        }
    }

    fun preview(id: String): RfidScanPreviewVo {
        val session = reconcileDeviceFailure(findSession(id))
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), session[RfidScanSessions.orgId], "扫描会话")
        val expectedRows = RfidScanExpectedItems.selectAll().where { RfidScanExpectedItems.sessionId eq id }.toList()
        val readRows = RfidScanReads.selectAll().where { RfidScanReads.sessionId eq id }
            .orderBy(RfidScanReads.firstSeenAt to SortOrder.ASC).toList()
        val expectedMatchedIds = readRows.filter { it[RfidScanReads.classification] == EnumRfidReadClassification.EXPECTED }
            .mapNotNull { it[RfidScanReads.resolvedCollectionId] }.toSet()
        val missingRows = expectedRows.filter { it[RfidScanExpectedItems.collectionId] !in expectedMatchedIds }
        val state = session[RfidScanSessions.sessionState]
        val stopAcknowledged = session[RfidScanSessions.stoppedAt] != null
        val settled = session[RfidScanSessions.settledAt]?.let { !it.isAfter(LocalDateTime.now()) } == true
        val blockReasons = buildList {
            if (state != EnumRfidScanSessionState.PREVIEW_READY) add("扫描会话尚未进入确认阶段")
            if (!stopAcknowledged) add("设备尚未确认停止读取")
            if (!settled) add("设备事件尚未收敛")
            if (missingRows.isNotEmpty()) add("有 ${missingRows.size} 件应读藏品未读到")
            val extra = readRows.count { it[RfidScanReads.classification] == EnumRfidReadClassification.EXTRA }
            val unknown = readRows.count { it[RfidScanReads.classification] == EnumRfidReadClassification.UNKNOWN }
            val conflict = readRows.count { it[RfidScanReads.classification] == EnumRfidReadClassification.CONFLICT }
            if (extra > 0) add("有 $extra 件范围外藏品")
            if (unknown > 0) add("有 $unknown 个未知标签")
            if (conflict > 0) add("有 $conflict 个 EPC/TID 冲突")
        }
        return RfidScanPreviewVo(
            sessionId = id,
            state = state,
            expected = expectedRows.size,
            matched = expectedMatchedIds.size,
            missing = missingRows.size,
            extra = readRows.count { it[RfidScanReads.classification] == EnumRfidReadClassification.EXTRA },
            unknown = readRows.count { it[RfidScanReads.classification] == EnumRfidReadClassification.UNKNOWN },
            conflict = readRows.count { it[RfidScanReads.classification] == EnumRfidReadClassification.CONFLICT },
            stopAcknowledged = stopAcknowledged,
            settled = settled,
            canFinalize = blockReasons.isEmpty(),
            blockReasons = blockReasons,
            reads = readRows.map(::readVo),
            missingItems = missingRows.map {
                RfidMissingVo(
                    collectionId = it[RfidScanExpectedItems.collectionId],
                    collectionNo = it[RfidScanExpectedItems.collectionNo],
                    collectionName = it[RfidScanExpectedItems.collectionName],
                    expectedEpc = it[RfidScanExpectedItems.expectedEpc],
                )
            },
        )
    }

    private fun requireCurrentMobileInventory(id: String): io.hizhengfu.cloud.collection.dao.InventoryBatchVo {
        val verified = (mobileInventory ?: throw BadRequestException("盘库核验服务未就绪")).get(id)
        if (verified.supersededByBatchId != null) throw BadRequestException("盘库任务已被后续批次替代，请刷新任务清单")
        if (verified.evidenceErrors.isNotEmpty()) throw BadRequestException("盘库任务归档证据无效：" + verified.evidenceErrors.joinToString("；"))
        if (verified.departureScopeErrors.isNotEmpty()) throw BadRequestException("离任清点范围待核对：" + verified.departureScopeErrors.joinToString("；"))
        return verified
    }

    fun finalize(id: String, vo: RfidScanFinalizeVo): RfidScanSessionVo {
        var session = requireWritableSession(id)
        if (session[RfidScanSessions.sessionState] == EnumRfidScanSessionState.FINALIZED) return sessionVo(session)
        if (session[RfidScanSessions.directInput] && session[RfidScanSessions.workstationId] == null) {
            CollectionCurrentOperationAccess.require(scope, session[RfidScanSessions.orgId], true,
                setOf(CollectionConst.RFID_FINALIZE), "移动借还确认")
            if (vo.force) CollectionCurrentOperationAccess.require(scope, session[RfidScanSessions.orgId], true,
                setOf(CollectionConst.RFID_OVERRIDE), "移动借还异常确认")
        }
        val preview = preview(id)
        if (!preview.stopAcknowledged || !preview.settled || preview.state != EnumRfidScanSessionState.PREVIEW_READY) {
            throw BadRequestException("设备停止并完成事件收敛后才能确认")
        }
        if (preview.blockReasons.isNotEmpty()) {
            if (!vo.force) throw BadRequestException(preview.blockReasons.joinToString("；"))
            if (!SecurityUtils.isAdminRole() && CollectionConst.RFID_OVERRIDE !in SecurityUtils.getUserDetail().permissions) {
                throw BadRequestException("无权强制确认异常扫描结果")
            }
            if (vo.reason.isNullOrBlank()) throw BadRequestException("强制确认原因必填")
        }
        transition(session, EnumRfidScanSessionState.FINALIZING)
        session = findSession(id)
        executeBusiness(session, vo)
        val result = mapOf(
            "force" to vo.force,
            "reason" to vo.reason.clean(500),
            "expected" to preview.expected,
            "matched" to preview.matched,
            "missing" to preview.missing,
            "extra" to preview.extra,
            "unknown" to preview.unknown,
            "conflict" to preview.conflict,
        )
        transition(session, EnumRfidScanSessionState.FINALIZED) {
            it[RfidScanSessions.finalizedAt] = LocalDateTime.now()
            it[RfidScanSessions.finalResultJson] = JsonUtils.toJson(result) ?: "{}"
        }
        publishState(id)
        return sessionVo(findSession(id))
    }

    fun cancel(id: String): RfidScanSessionVo {
        val session = requireWritableSession(id)
        if (session[RfidScanSessions.sessionState] !in setOf(EnumRfidScanSessionState.CREATED, EnumRfidScanSessionState.PREVIEW_READY)) {
            throw BadRequestException("读取中请先停止设备后再取消")
        }
        transition(session, EnumRfidScanSessionState.CANCELLED)
        publishState(id)
        return sessionVo(findSession(id))
    }

    /** 管理员处理断线、浏览器损坏等无法正常停止的会话；释放工作站前仍尽力补发停止命令。 */
    fun forceTerminate(id: String, reason: String?): RfidScanSessionVo {
        val session = requireWritableSession(id)
        if (session[RfidScanSessions.sessionState] !in ACTIVE_STATES) return sessionVo(session)
        expireSession(session, reason.clean(500) ?: "管理员强制终止 RFID 扫描会话")
        return sessionVo(findSession(id))
    }

    /**
     * 状态恢复不依赖工作台页面持续在线。命令卡住、读取超时或预览长期未处理时自动释放工作站，
     * 同时保留失败原因，避免下一个操作员只能通过删库恢复。
     */
    @Transactional(readOnly = true)
    fun hasActiveSessions(): Boolean = !RfidScanSessions.select(RfidScanSessions.id)
        .where {RfidScanSessions.sessionState inList ACTIVE_STATES}.limit(1).empty()

    fun recoverStaleSessions(): Int {
        val now = LocalDateTime.now()
        val rows = RfidScanSessions.selectAll().where { RfidScanSessions.sessionState inList ACTIVE_STATES }.toList()
        var recovered = 0
        rows.forEach { snapshot ->
            var session = findSession(snapshot[RfidScanSessions.id].value)
            val state = session[RfidScanSessions.sessionState]
            if (state !in ACTIVE_STATES) return@forEach
            if (state in setOf(EnumRfidScanSessionState.STARTING, EnumRfidScanSessionState.READING, EnumRfidScanSessionState.STOPPING)) {
                session = reconcileDeviceFailure(session)
                if (session[RfidScanSessions.sessionState] !in ACTIVE_STATES) {
                    recovered += 1
                    return@forEach
                }
            }
            val ageSeconds = Duration.between(session[RfidScanSessions.updateTime], now).seconds.coerceAtLeast(0)
            val staleReason = when (session[RfidScanSessions.sessionState]) {
                EnumRfidScanSessionState.CREATED ->
                    if (ageSeconds >= createdTimeoutSeconds) "会话创建后长时间未启动" else null
                EnumRfidScanSessionState.STARTING,
                EnumRfidScanSessionState.STOPPING,
                -> if (ageSeconds >= commandTimeoutSeconds) "设备命令长时间未完成" else null
                EnumRfidScanSessionState.READING -> {
                    val startedAt = session[RfidScanSessions.startedAt] ?: session[RfidScanSessions.createTime]
                    val allowed = if (session[RfidScanSessions.directInput]) mobileOfflineWindowSeconds else maxReadingSeconds
                    if (Duration.between(startedAt, now).seconds >= allowed) "读取或离线同步超过最大允许时长" else null
                }
                EnumRfidScanSessionState.PREVIEW_READY ->
                    if (ageSeconds >= previewTimeoutSeconds) "读取结果长时间未确认" else null
                EnumRfidScanSessionState.FINALIZING ->
                    if (ageSeconds >= commandTimeoutSeconds) "业务确认长时间未完成" else null
                else -> null
            }
            if (staleReason != null) {
                expireSession(session, staleReason)
                recovered += 1
            }
        }
        return recovered
    }

    private fun expireSession(session: ResultRow, reason: String) {
        val state = session[RfidScanSessions.sessionState]
        var stopCommandId: String? = session[RfidScanSessions.stopCommandId]
        var stopError: String? = null
        if (!session[RfidScanSessions.directInput] && state in setOf(
                EnumRfidScanSessionState.STARTING,
                EnumRfidScanSessionState.READING,
                EnumRfidScanSessionState.STOPPING,
            )
        ) {
            runCatching { commandPort.stopInventory(session[RfidScanSessions.deviceId], session[RfidScanSessions.id].value) }
                .onSuccess { stopCommandId = it }
                .onFailure { stopError = it.message.clean(200) }
        }
        val detail = listOfNotNull(reason, stopError?.let { "补发停止命令失败：$it" }).joinToString("；")
        transition(session, EnumRfidScanSessionState.EXPIRED) {
            it[RfidScanSessions.errorMessage] = detail.take(500)
            it[RfidScanSessions.stopCommandId] = stopCommandId
            it[RfidScanSessions.stoppedAt] = LocalDateTime.now()
        }
        publishState(session[RfidScanSessions.id].value)
    }

    private fun executeBusiness(session: ResultRow, vo: RfidScanFinalizeVo) {
        val businessId = session[RfidScanSessions.businessId]
        when (session[RfidScanSessions.sessionType]) {
            EnumRfidScanSessionType.LOAN_OUT -> loanService.executeOut(
                businessId ?: throw BadRequestException("借展业务 ID 缺失"),
                CollectionOutboundExecutionVo(vo.conditionNote, vo.conditionPhotoIds, vo.signedVoucherAttachmentIds),
            )
            EnumRfidScanSessionType.REQUISITION_OUT -> requisitionService.executeOut(
                businessId ?: throw BadRequestException("提用业务 ID 缺失"),
                CollectionOutboundExecutionVo(vo.conditionNote, vo.conditionPhotoIds, vo.signedVoucherAttachmentIds),
            )
            EnumRfidScanSessionType.INVENTORY -> {
                // 移动扫描只是分段观察证据；逐件核验和批次签署由 inventory/batch 接口办理。
                // 不在每台手持机结束一轮时生成全批次 MISSING 草稿。
                if (session[RfidScanSessions.directInput]) return
                val tags = RfidScanReads.selectAll().where { RfidScanReads.sessionId eq session[RfidScanSessions.id].value }
                    .map { it[RfidScanReads.epc] }
                inventoryService.rfidCheck(InventoryRfidCheckVo(batchName = businessId ?: "RFID-${session[RfidScanSessions.sessionNo]}", tags = tags))
            }
            EnumRfidScanSessionType.RETURN_IN -> returnService.executeApprovedReturn(
                businessId ?: throw BadRequestException("归还业务 ID 缺失"),
            )
            EnumRfidScanSessionType.TAG_VERIFY,
            EnumRfidScanSessionType.PASSAGE_DIAG,
            -> Unit
        }
    }

    private fun resolveExpectedIds(type: EnumRfidScanSessionType, businessId: String?, explicit: List<String>, orgId: String): List<String> {
        val supplied = explicit.mapNotNull { it.clean(64) }.distinct()
        if (supplied.isNotEmpty()) return supplied
        if (businessId == null) return emptyList()
        val raw = when (type) {
            EnumRfidScanSessionType.LOAN_OUT -> Loans.selectAll().where { Loans.id eq businessId }.limit(1).firstOrNull()?.get(Loans.collectionIds)
            EnumRfidScanSessionType.REQUISITION_OUT -> Requisitions.selectAll().where { Requisitions.id eq businessId }.limit(1).firstOrNull()?.get(Requisitions.collectionIds)
            EnumRfidScanSessionType.RETURN_IN -> CollectionReturns.selectAll().where { CollectionReturns.id eq businessId }.limit(1).firstOrNull()?.get(CollectionReturns.collectionIds)
            else -> null
        }
        if (raw != null) return raw.split(',').mapNotNull { it.clean(64) }.distinct()
        if (type != EnumRfidScanSessionType.INVENTORY) return emptyList()
        val batch = InventoryBatches.selectAll().where {
            (InventoryBatches.orgId eq orgId) and (InventoryBatches.batchName eq businessId)
        }.limit(1).firstOrNull() ?: return emptyList()
        val configuredIds = batch[InventoryBatches.collectionIds]
            ?.split(',')
            ?.mapNotNull { it.clean(64) }
            ?.distinct()
            .orEmpty()
        if (configuredIds.isNotEmpty()) return configuredIds
        val query = Collections.selectAll().where {
            (Collections.orgId eq orgId) and (Collections.lifeStatus eq EnumCollectionStatus.IN_STORE)
        }
        batch[InventoryBatches.warehouseId]?.let { warehouseId -> query.andWhere { Collections.warehouseId eq warehouseId } }
        return query.map { it[Collections.id].value }
    }

    private fun validateBusinessSource(type: EnumRfidScanSessionType, businessId: String?, orgId: String) {
        when (type) {
            EnumRfidScanSessionType.LOAN_OUT -> {
                val id = businessId ?: throw BadRequestException("请选择已审批的借展单")
                val row = Loans.selectAll().where { Loans.id eq id }.limit(1).firstOrNull()
                    ?: throw BadRequestException("借展单不存在")
                if (row[Loans.orgId] != orgId) throw BadRequestException("借展单与工作站不属于同一收藏单位")
                if (row[Loans.flowStatus] != EnumFlowStatus.PASS || row[Loans.outExecuted]) {
                    throw BadRequestException("只能选择审批通过且尚未执行出库的借展单")
                }
            }
            EnumRfidScanSessionType.REQUISITION_OUT -> {
                val id = businessId ?: throw BadRequestException("请选择已审批的提用单")
                val row = Requisitions.selectAll().where { Requisitions.id eq id }.limit(1).firstOrNull()
                    ?: throw BadRequestException("提用单不存在")
                if (row[Requisitions.orgId] != orgId) throw BadRequestException("提用单与工作站不属于同一收藏单位")
                if (row[Requisitions.flowStatus] != EnumFlowStatus.PASS || row[Requisitions.outExecuted] || row[Requisitions.supersededById] != null || row[Requisitions.sourceRetirement] != null) {
                    throw BadRequestException("只能选择审批通过且尚未执行出库的提用单")
                }
            }
            EnumRfidScanSessionType.RETURN_IN -> {
                val id = businessId ?: throw BadRequestException("请选择已审批的归还单")
                val row = CollectionReturns.selectAll().where { CollectionReturns.id eq id }.limit(1).firstOrNull()
                    ?: throw BadRequestException("归还单不存在")
                if (row[CollectionReturns.orgId] != orgId) throw BadRequestException("归还单与工作站不属于同一收藏单位")
                if (row[CollectionReturns.flowStatus] != EnumFlowStatus.PASS) throw BadRequestException("只能选择审批通过的归还单")
                if (row[CollectionReturns.executionState] != io.hizhengfu.cloud.collection.dao.EnumReturnExecutionState.PENDING) {
                    throw BadRequestException("归还单已经完成现场归库")
                }
            }
            EnumRfidScanSessionType.INVENTORY -> {
                val batchName = businessId ?: throw BadRequestException("请选择已配置的盘点批次")
                val row = InventoryBatches.selectAll().where {
                    (InventoryBatches.orgId eq orgId) and (InventoryBatches.batchName eq batchName)
                }.limit(1).firstOrNull() ?: throw BadRequestException("盘点批次不存在")
                if (row[InventoryBatches.finishedAt] != null) throw BadRequestException("盘点批次已结束")
            }
            EnumRfidScanSessionType.TAG_VERIFY,
            EnumRfidScanSessionType.PASSAGE_DIAG,
            -> Unit
        }
    }

    private fun requireStationSupports(type: EnumRfidScanSessionType, station: ResultRow) {
        if (requiredCapability(type) !in workstationCapabilities(station)) {
            throw BadRequestException("工作站未启用该 RFID 作业能力")
        }
    }

    private fun acknowledgeStoppedSession(session: ResultRow) {
        val now = LocalDateTime.now()
        transition(session, EnumRfidScanSessionState.PREVIEW_READY) {
            it[RfidScanSessions.stoppedAt] = now
            // 收到停止 ACK 后仍留出静默窗口；直至窗口内没有新读数才允许业务确认。
            it[RfidScanSessions.settledAt] = now.plusNanos(SETTLE_MILLIS * 1_000_000)
        }
    }

    private fun reconcileDeviceFailure(session: ResultRow): ResultRow {
        if (!session[RfidScanSessions.directInput] &&
            session[RfidScanSessions.sessionState] in setOf(
                EnumRfidScanSessionState.STARTING, EnumRfidScanSessionState.READING, EnumRfidScanSessionState.STOPPING,
            ) &&
            commandPort.isWorkstationLocalDevice(session[RfidScanSessions.deviceId]) &&
            !commandPort.isOnline(session[RfidScanSessions.deviceId])
        ) {
            transition(session, EnumRfidScanSessionState.FAILED) {
                it[RfidScanSessions.errorMessage] = "斯科读写器连接已中断，扫描结果需重新核对"
            }
            publishState(session[RfidScanSessions.id].value)
            return findSession(session[RfidScanSessions.id].value)
        }
        val commandId = when (session[RfidScanSessions.sessionState]) {
            EnumRfidScanSessionState.STARTING -> session[RfidScanSessions.startCommandId]
            EnumRfidScanSessionState.STOPPING -> session[RfidScanSessions.stopCommandId]
            else -> null
        } ?: return session
        val outcome = commandPort.outcome(commandId) ?: return session
        if (!outcome.terminal || outcome.successful) return session
        // 进程崩溃或回调网络中断时，前端轮询仍可以根据命令终态收敛会话。
        transition(session, EnumRfidScanSessionState.FAILED) {
            it[RfidScanSessions.errorMessage] = outcome.message.clean(500) ?: "RFID 设备命令失败"
        }
        publishState(session[RfidScanSessions.id].value)
        return findSession(session[RfidScanSessions.id].value)
    }

    private fun transition(
        session: ResultRow,
        to: EnumRfidScanSessionState,
        body: (org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>) -> Unit = {},
    ) {
        val from = session[RfidScanSessions.sessionState]
        RfidScanSessionStateMachine.requireTransition(from, to)
        val changed = RfidScanSessions.update({
            (RfidScanSessions.id eq session[RfidScanSessions.id].value) and
                (RfidScanSessions.sessionState eq from) and
                (RfidScanSessions.version eq session[RfidScanSessions.version])
        }) {
            it[sessionState] = to
            it[version] = session[RfidScanSessions.version] + 1
            it[updateTime] = LocalDateTime.now()
            it[updateBy] = SecurityUtils.getUserId()
            body(it)
        }
        if (changed != 1) throw BadRequestException("扫描会话状态已变化，请刷新后重试")
    }

    private fun findSession(id: String) = RfidScanSessions.selectAll().where { RfidScanSessions.id eq id }
        .limit(1).firstOrNull() ?: throw BadRequestException("扫描会话不存在")

    private fun requireWritableSession(id: String): ResultRow {
        val session = findSession(id)
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), session[RfidScanSessions.orgId], "扫描会话")
        if (session[RfidScanSessions.directInput] && session[RfidScanSessions.workstationId] == null)
            CollectionCurrentOperationAccess.require(scope, session[RfidScanSessions.orgId], true,
                setOf(CollectionConst.RFID_SCAN), "移动扫描会话")
        if (session[RfidScanSessions.directInput]) requireDirectUploadOperator(session)
        return session
    }

    private fun requireDirectUploadOperator(session: ResultRow) {
        val user = SecurityUtils.getUserDetail()
        if (session[RfidScanSessions.operatorId] == user.id || SecurityUtils.isAdminRole() || CollectionConst.ADMIN in user.permissions) {
            return
        }
        throw BadRequestException("只能由领取任务的操作员上传手持机读数")
    }

    private fun handheldTaskTypes(station: ResultRow): Set<EnumRfidScanSessionType> = buildSet {
        val capabilities = workstationCapabilities(station)
        if (io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability.BORROW_RETURN in capabilities) {
            add(EnumRfidScanSessionType.LOAN_OUT)
            add(EnumRfidScanSessionType.REQUISITION_OUT)
            add(EnumRfidScanSessionType.RETURN_IN)
        }
        if (io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability.INVENTORY in capabilities) {
            add(EnumRfidScanSessionType.INVENTORY)
        }
    }

    private fun handheldDevices(workstationId: String): List<RfidHandheldDeviceVo> =
        RfidWorkstationDeviceBindings.selectAll().where {
            (RfidWorkstationDeviceBindings.workstationId eq workstationId) and
                (RfidWorkstationDeviceBindings.purpose eq
                    io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose.RFID_READ) and
                (RfidWorkstationDeviceBindings.enabled eq true)
        }.orderBy(
            RfidWorkstationDeviceBindings.isDefault to SortOrder.DESC,
            RfidWorkstationDeviceBindings.priority to SortOrder.ASC,
        ).mapNotNull { binding ->
            val deviceId = binding[RfidWorkstationDeviceBindings.deviceId]
            if (!commandPort.allowsDirectInput(deviceId)) null else RfidHandheldDeviceVo(
                id = deviceId,
                isDefault = binding[RfidWorkstationDeviceBindings.isDefault],
            )
        }

    private fun collectionCount(ids: String?): Int? = ids?.split(',')?.map(String::trim)?.count(String::isNotBlank)

    private fun publishState(id: String) {
        val row = findSession(id)
        realtime.publish(
            row[RfidScanSessions.orgId],
            row[RfidScanSessions.workstationId],
            id,
            EnumRfidRealtimeEventType.SESSION_STATE,
            mapOf(
                "sessionId" to id,
                "state" to row[RfidScanSessions.sessionState].name,
                "readCount" to row[RfidScanSessions.readCount],
                "expectedCount" to row[RfidScanSessions.expectedCount],
            ),
        )
    }

    private fun readVo(row: ResultRow): RfidReadVo {
        val collection = row[RfidScanReads.resolvedCollectionId]?.let { id ->
            Collections.selectAll().where { Collections.id eq id }.limit(1).firstOrNull()
        }
        return RfidReadVo(
            epc = row[RfidScanReads.epc],
            tid = row[RfidScanReads.tid],
            antenna = row[RfidScanReads.antenna],
            direction = row[RfidScanReads.direction],
            firstSeenAt = row[RfidScanReads.firstSeenAt].epoch(),
            lastSeenAt = row[RfidScanReads.lastSeenAt].epoch(),
            readCount = row[RfidScanReads.readCount],
            peakRssi = row[RfidScanReads.peakRssi],
            collectionId = row[RfidScanReads.resolvedCollectionId],
            collectionNo = collection?.get(Collections.totalRegNo),
            collectionName = collection?.get(Collections.name),
            classification = row[RfidScanReads.classification],
        )
    }

    private fun sessionVo(row: ResultRow) = RfidScanSessionVo(
        id = row[RfidScanSessions.id].value,
        sessionNo = row[RfidScanSessions.sessionNo],
        orgId = row[RfidScanSessions.orgId],
        workstationId = row[RfidScanSessions.workstationId],
        deviceId = row[RfidScanSessions.deviceId],
        type = row[RfidScanSessions.sessionType],
        businessType = row[RfidScanSessions.businessType],
        businessId = row[RfidScanSessions.businessId],
        operatorId = row[RfidScanSessions.operatorId],
        directInput = row[RfidScanSessions.directInput],
        state = row[RfidScanSessions.sessionState],
        startCommandId = row[RfidScanSessions.startCommandId],
        stopCommandId = row[RfidScanSessions.stopCommandId],
        expectedCount = row[RfidScanSessions.expectedCount],
        readCount = row[RfidScanSessions.readCount],
        createdAt = row[RfidScanSessions.createTime].epoch(),
        startedAt = row[RfidScanSessions.startedAt].epochOrNull(),
        stoppedAt = row[RfidScanSessions.stoppedAt].epochOrNull(),
        finalizedAt = row[RfidScanSessions.finalizedAt].epochOrNull(),
        errorMessage = row[RfidScanSessions.errorMessage],
    )

    private fun defaultBusinessType(type: EnumRfidScanSessionType) = when (type) {
        EnumRfidScanSessionType.LOAN_OUT -> CollectionConst.BIZ_LOAN
        EnumRfidScanSessionType.REQUISITION_OUT -> CollectionConst.BIZ_REQUISITION
        EnumRfidScanSessionType.RETURN_IN -> CollectionConst.BIZ_RETURN
        EnumRfidScanSessionType.INVENTORY -> "INVENTORY"
        EnumRfidScanSessionType.TAG_VERIFY -> "RFID_TAG"
        EnumRfidScanSessionType.PASSAGE_DIAG -> "RFID_PASSAGE_DIAG"
    }

    private fun LocalDateTime.epoch() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun LocalDateTime?.epochOrNull() = this?.epoch()
    private fun Long?.toLocalDateTime() = this?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    private fun String?.clean(max: Int) = this?.trim()?.takeIf(String::isNotBlank)?.take(max)
    private fun HttpServletRequest.cleanParameter(name: String, max: Int) = getParameter(name).clean(max)
    private fun HttpServletRequest.epochParameter(name: String) = getParameter(name)?.toLongOrNull()?.toLocalDateTime()
    private inline fun <reified T : Enum<T>> HttpServletRequest.enumParameter(name: String): T? =
        getParameter(name)?.trim()?.takeIf(String::isNotBlank)?.let { value ->
            runCatching { enumValueOf<T>(value) }.getOrElse { throw BadRequestException("$name 参数无效") }
        }
    private fun Query.pageRows(page: GenericPage<*>): List<ResultRow> = if (page.pageSize > 0) {
        page.total = count()
        limit(page.pageSize).offset(page.startRow).toList()
    } else {
        toList().also { page.total = it.size.toLong() }
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        val ACTIVE_STATES = listOf(
            EnumRfidScanSessionState.CREATED,
            EnumRfidScanSessionState.STARTING,
            EnumRfidScanSessionState.READING,
            EnumRfidScanSessionState.STOPPING,
            EnumRfidScanSessionState.PREVIEW_READY,
            EnumRfidScanSessionState.FINALIZING,
        )
        val HANDHELD_TASK_TYPES = setOf(
            EnumRfidScanSessionType.LOAN_OUT,
            EnumRfidScanSessionType.REQUISITION_OUT,
            EnumRfidScanSessionType.RETURN_IN,
            EnumRfidScanSessionType.INVENTORY,
        )
    }
}
