package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanReads
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagOperations
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagJobStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagOperationType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobVerifyVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobWriteVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagOperationVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagRetireVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagVo
import io.hizhengfu.cloud.collection.service.OrgScopedReadPolicy
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId

@Component
@Transactional
class RfidTagService(
    private val scope: CollectionScopeResolver,
    private val commandPort: RfidDeviceCommandPort,
) {
    @Transactional(readOnly = true)
    fun tags(request: HttpServletRequest): GenericPage<RfidTagVo> {
        val page = GenericPage<RfidTagVo>().build(request)
        val query = RfidTags.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidTags.orgId inList it } }
        request.cleanParameter("collectionId", 64)?.let { query.andWhere { RfidTags.collectionId eq it } }
        request.enumParameter<EnumRfidTagStatus>("status")?.let { query.andWhere { RfidTags.tagStatus eq it } }
        request.cleanParameter("keyword", 128)?.let { keyword ->
            query.andWhere {
                (RfidTags.epc like "%$keyword%") or
                    (RfidTags.tid like "%$keyword%") or
                    (RfidTags.collectionId like "%$keyword%")
            }
        }
        query.orderBy(RfidTags.createTime to SortOrder.DESC)
        page.list = query.pageRows(page).map(::tagVo)
        return page
    }

    @Transactional(readOnly = true)
    fun operations(request: HttpServletRequest): GenericPage<RfidTagOperationVo> {
        val page = GenericPage<RfidTagOperationVo>().build(request)
        val query = RfidTagOperations.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidTagOperations.orgId inList it } }
        request.cleanParameter("tagId", 64)?.let { query.andWhere { RfidTagOperations.tagId eq it } }
        request.enumParameter<EnumRfidTagOperationType>("operationType")?.let {
            query.andWhere { RfidTagOperations.operationType eq it }
        }
        request.getParameter("success")?.let { raw ->
            val success = raw.toBooleanStrictOrNull() ?: throw BadRequestException("success 参数无效")
            query.andWhere { RfidTagOperations.success eq success }
        }
        request.cleanParameter("keyword", 128)?.let { keyword ->
            query.andWhere {
                (RfidTagOperations.beforeEpc like "%$keyword%") or
                    (RfidTagOperations.afterEpc like "%$keyword%") or
                    (RfidTagOperations.tid like "%$keyword%") or
                    (RfidTagOperations.detail like "%$keyword%")
            }
        }
        request.epochParameter("occurredAtStart")?.let { query.andWhere { RfidTagOperations.occurredAt greaterEq it } }
        request.epochParameter("occurredAtEnd")?.let { query.andWhere { RfidTagOperations.occurredAt lessEq it } }
        query.orderBy(RfidTagOperations.occurredAt to SortOrder.DESC)
        page.list = query.pageRows(page).map(::operationVo)
        return page
    }

    fun createJob(vo: RfidTagJobCreateVo): RfidTagJobVo {
        val collectionId = vo.collectionId.clean(64) ?: throw BadRequestException("藏品必填")
        val workstationId = vo.workstationId.clean(64) ?: throw BadRequestException("发标工作站必填")
        val collectionQuery = Collections.selectAll().where { Collections.id eq collectionId }.limit(1)
        collectionQuery.forUpdate()
        val collection = collectionQuery.firstOrNull()
            ?: throw BadRequestException("藏品不存在")
        val orgId = collection[Collections.orgId]
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "藏品")
        val station = requireTagStation(workstationId, orgId)
        val requestedTargetEpc = RfidHex.normalize(vo.targetEpc, "targetEpc", required = false)
        val requestedExpectedTid = RfidHex.normalize(vo.expectedTid, "expectedTid", required = false)?.also(::requireTidSelector)
        val currentOperatorId = SecurityUtils.getUserId()
        val clientRequestId = vo.clientRequestId.clean(100)
        if (clientRequestId != null) {
            RfidTagJobs.selectAll().where {
                (RfidTagJobs.orgId eq orgId) and (RfidTagJobs.clientRequestId eq clientRequestId)
            }.limit(1).firstOrNull()?.let { existing ->
                val sameRequest = existing[RfidTagJobs.createBy] == currentOperatorId &&
                    existing[RfidTagJobs.collectionId] == collectionId &&
                    existing[RfidTagJobs.workstationId] == workstationId &&
                    (requestedTargetEpc == null || existing[RfidTagJobs.targetEpc] == requestedTargetEpc) &&
                    (requestedExpectedTid == null || existing[RfidTagJobs.expectedTid] == requestedExpectedTid)
                if (!sameRequest) throw BadRequestException("clientRequestId 已用于其他发标请求")
                return jobVo(existing)
            }
        }
        val jobId = IdGenerate.nextId()
        val targetEpc = requestedTargetEpc ?: generateEpc(orgId, collectionId, jobId)
        val expectedTid = requestedExpectedTid
        if (RfidTags.selectAll().where { (RfidTags.orgId eq orgId) and (RfidTags.epc eq targetEpc) }.limit(1).any()) {
            throw BadRequestException("目标 EPC 已存在")
        }
        val now = LocalDateTime.now()
        val tagId = IdGenerate.nextId()
        RfidTags.insert {
            it[id] = tagId
            it[RfidTags.orgId] = orgId
            it[epc] = targetEpc
            it[tid] = expectedTid
            it[RfidTags.collectionId] = collectionId
            it[tagStatus] = EnumRfidTagStatus.PROVISIONING
            it[issuedAt] = now
            it[issuedBy] = currentOperatorId
            it[createTime] = now
            it[createBy] = currentOperatorId
            it[updateTime] = now
            it[updateBy] = currentOperatorId
        }
        RfidTagJobs.insert {
            it[id] = jobId
            it[RfidTagJobs.orgId] = orgId
            it[RfidTagJobs.collectionId] = collectionId
            it[RfidTagJobs.workstationId] = workstationId
            it[RfidTagJobs.tagId] = tagId
            it[RfidTagJobs.targetEpc] = targetEpc
            it[RfidTagJobs.expectedTid] = expectedTid
            it[jobStatus] = EnumRfidTagJobStatus.CREATED
            it[RfidTagJobs.clientRequestId] = clientRequestId
            it[version] = 0
            it[createTime] = now
            it[createBy] = currentOperatorId
            it[updateTime] = now
            it[updateBy] = currentOperatorId
        }
        appendOperation(tagId, EnumRfidTagOperationType.CREATED, workstationId, null, null, targetEpc, expectedTid, true, null)
        // 已经由手持机先读出 TID 时可直接进入受 TID 约束的写入；否则由“写入”按钮补充 TID 后再下发。
        if (expectedTid != null) {
            val writerDeviceId = resolveWorkstationDevice(
                station,
                EnumRfidWorkstationDevicePurpose.RFID_WRITE,
                commandPort::isOnline,
            ) ?: throw BadRequestException("发标工作站没有在线的 RFID 写入设备")
            requestWriteInternal(jobId, writerDeviceId, expectedTid)
        }
        return getJob(jobId)
    }

    fun requestWrite(id: String, vo: RfidTagJobWriteVo): RfidTagJobVo {
        val job = requireWritableJob(id)
        if (job[RfidTagJobs.jobStatus] !in setOf(EnumRfidTagJobStatus.CREATED, EnumRfidTagJobStatus.FAILED)) {
            throw BadRequestException("当前发标任务不能重复请求写入")
        }
        val tid = RfidHex.normalize(vo.tid ?: job[RfidTagJobs.expectedTid], "tid")!!.also(::requireTidSelector)
        val station = requireTagStation(job[RfidTagJobs.workstationId], job[RfidTagJobs.orgId])
        RfidTagJobs.update({ RfidTagJobs.id eq id }) {
            it[expectedTid] = tid
            it[jobStatus] = EnumRfidTagJobStatus.WRITE_PENDING
            it[errorMessage] = null
            it[updateTime] = LocalDateTime.now()
            it[updateBy] = SecurityUtils.getUserId()
            it[version] = job[RfidTagJobs.version] + 1
        }
        RfidTags.update({ RfidTags.id eq job[RfidTagJobs.tagId] }) { it[RfidTags.tid] = tid }
        val writerDeviceId = resolveWorkstationDevice(
            station,
            EnumRfidWorkstationDevicePurpose.RFID_WRITE,
            commandPort::isOnline,
        ) ?: throw BadRequestException("发标工作站没有在线的 RFID 写入设备")
        requestWriteInternal(id, writerDeviceId, tid)
        return getJob(id)
    }

    /** 写后读回 EPC/TID 均一致才激活；这一步是标签绑定真正生效的唯一入口。 */
    fun verify(id: String, vo: RfidTagJobVerifyVo): RfidTagJobVo {
        val job = requireWritableJob(id)
        if (job[RfidTagJobs.jobStatus] != EnumRfidTagJobStatus.VERIFY_PENDING) {
            throw BadRequestException("设备写入成功后才能执行读回校验")
        }
        val scanSessionId = vo.scanSessionId.clean(64) ?: throw BadRequestException("写后读回会话必填")
        val scanSession = RfidScanSessions.selectAll().where { RfidScanSessions.id eq scanSessionId }.limit(1).firstOrNull()
            ?: throw BadRequestException("写后读回会话不存在")
        if (scanSession[RfidScanSessions.orgId] != job[RfidTagJobs.orgId] ||
            scanSession[RfidScanSessions.workstationId] != job[RfidTagJobs.workstationId] ||
            scanSession[RfidScanSessions.sessionType] != EnumRfidScanSessionType.TAG_VERIFY ||
            scanSession[RfidScanSessions.businessId] != id ||
            scanSession[RfidScanSessions.directInput]
        ) {
            throw BadRequestException("写后读回会话与发标任务不匹配")
        }
        if (scanSession[RfidScanSessions.sessionState] != EnumRfidScanSessionState.PREVIEW_READY ||
            scanSession[RfidScanSessions.settledAt]?.isAfter(LocalDateTime.now()) != false
        ) {
            throw BadRequestException("写后读回尚未停止或仍在收敛")
        }
        val epc = job[RfidTagJobs.targetEpc]
        val read = RfidScanReads.selectAll().where {
            (RfidScanReads.sessionId eq scanSessionId) and (RfidScanReads.epc eq epc)
        }.limit(1).firstOrNull() ?: throw BadRequestException("写后读回未发现目标 EPC，标签未激活")
        val tid = read[RfidScanReads.tid] ?: throw BadRequestException("写后读回未返回 TID，标签未激活")
        val expectedTid = job[RfidTagJobs.expectedTid] ?: throw BadRequestException("任务尚未锁定 TID")
        val success = tid == expectedTid
        val now = LocalDateTime.now()
        RfidTagJobs.update({ RfidTagJobs.id eq id }) {
            it[verifiedEpc] = epc
            it[verifiedTid] = tid
            it[jobStatus] = if (success) EnumRfidTagJobStatus.VERIFIED else EnumRfidTagJobStatus.FAILED
            it[errorMessage] = if (success) null else "写后读回 EPC/TID 不一致"
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
            it[version] = job[RfidTagJobs.version] + 1
        }
        appendOperation(
            job[RfidTagJobs.tagId],
            EnumRfidTagOperationType.VERIFIED,
            job[RfidTagJobs.workstationId],
            job[RfidTagJobs.commandId],
            null,
            epc,
            tid,
            success,
            if (success) null else "VERIFY_MISMATCH",
        )
        if (!success) {
            RfidTags.update({ RfidTags.id eq job[RfidTagJobs.tagId] }) { it[tagStatus] = EnumRfidTagStatus.ERROR }
            // 返回 FAILED 任务而不是抛异常，确保失败状态和证据操作在本事务内真正提交。
            return getJob(id)
        }
        activate(job, now)
        RfidScanSessions.update({ RfidScanSessions.id eq scanSessionId }) {
            it[sessionState] = EnumRfidScanSessionState.FINALIZED
            it[finalizedAt] = now
            it[finalResultJson] = JsonUtils.toJson(mapOf("tagJobId" to id, "epc" to epc, "tid" to tid)) ?: "{}"
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
            it[version] = scanSession[RfidScanSessions.version] + 1
        }
        return getJob(id)
    }

    /** 由受信采集服务回写写标命令结果，避免任务在设备拒绝或超时后仍显示“写入中”。 */
    fun acknowledgeWrite(commandId: String, success: Boolean, message: String?): String {
        val job = RfidTagJobs.selectAll().where { RfidTagJobs.commandId eq commandId }.limit(1).firstOrNull()
            ?: throw BadRequestException("未找到写标命令对应任务")
        val jobId = job[RfidTagJobs.id].value
        if (job[RfidTagJobs.jobStatus] != EnumRfidTagJobStatus.WRITING) return jobId
        val now = LocalDateTime.now()
        RfidTagJobs.update({ RfidTagJobs.id eq jobId }) {
            it[jobStatus] = if (success) EnumRfidTagJobStatus.VERIFY_PENDING else EnumRfidTagJobStatus.FAILED
            it[errorMessage] = if (success) null else message.clean(500) ?: "设备写入失败"
            it[updateTime] = now
            it[version] = job[RfidTagJobs.version] + 1
        }
        if (!success) {
            RfidTags.update({ RfidTags.id eq job[RfidTagJobs.tagId] }) {
                it[tagStatus] = EnumRfidTagStatus.ERROR
                it[updateTime] = now
            }
        }
        appendOperation(
            job[RfidTagJobs.tagId],
            if (success) EnumRfidTagOperationType.WRITE_SUCCEEDED else EnumRfidTagOperationType.WRITE_FAILED,
            job[RfidTagJobs.workstationId],
            commandId,
            null,
            job[RfidTagJobs.targetEpc],
            job[RfidTagJobs.expectedTid],
            success,
            if (success) null else "DEVICE_WRITE_FAILED",
            message,
        )
        return jobId
    }

    fun retire(id: String, vo: RfidTagRetireVo): String {
        val row = RfidTags.selectAll().where { RfidTags.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("标签不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidTags.orgId], "RFID 标签")
        if (row[RfidTags.tagStatus] in setOf(EnumRfidTagStatus.RETIRED, EnumRfidTagStatus.REPLACED)) return id
        val reason = vo.reason.clean(500) ?: throw BadRequestException("报废原因必填")
        val now = LocalDateTime.now()
        RfidTags.update({ RfidTags.id eq id }) {
            it[tagStatus] = EnumRfidTagStatus.RETIRED
            it[retiredAt] = now
            it[retiredBy] = SecurityUtils.getUserId()
            it[retiredReason] = reason
            it[collectionId] = null
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
            it[version] = row[RfidTags.version] + 1
        }
        row[RfidTags.collectionId]?.let { collectionId ->
            Collections.update({ (Collections.id eq collectionId) and (Collections.rfidTag eq row[RfidTags.epc]) }) {
                it[rfidTag] = null
            }
        }
        appendOperation(id, EnumRfidTagOperationType.RETIRED, null, null, row[RfidTags.epc], null, row[RfidTags.tid], true, null, reason)
        return id
    }

    fun getJob(id: String): RfidTagJobVo {
        var row = RfidTagJobs.selectAll().where { RfidTagJobs.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("发标任务不存在")
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), row[RfidTagJobs.orgId], "发标任务")
        if (row[RfidTagJobs.jobStatus] == EnumRfidTagJobStatus.WRITING) {
            row[RfidTagJobs.commandId]?.let { commandId ->
                commandPort.outcome(commandId)?.takeIf { it.terminal && !it.successful }?.let { outcome ->
                    acknowledgeWrite(commandId, false, outcome.message)
                    row = RfidTagJobs.selectAll().where { RfidTagJobs.id eq id }.limit(1).first()
                }
            }
        }
        return jobVo(row)
    }

    @Transactional(readOnly = true)
    fun workstationIdForJob(id: String): String = RfidTagJobs.selectAll()
        .where { RfidTagJobs.id eq id }
        .limit(1)
        .firstOrNull()
        ?.also { OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), it[RfidTagJobs.orgId], "发标任务") }
        ?.get(RfidTagJobs.workstationId)
        ?: throw BadRequestException("发标任务不存在")

    private fun requestWriteInternal(jobId: String, deviceId: String, tid: String) {
        val job = RfidTagJobs.selectAll().where { RfidTagJobs.id eq jobId }.limit(1).first()
        val commandId = commandPort.writeEpc(deviceId, jobId, job[RfidTagJobs.targetEpc], tid)
        val now = LocalDateTime.now()
        RfidTagJobs.update({ RfidTagJobs.id eq jobId }) {
            it[RfidTagJobs.commandId] = commandId
            it[jobStatus] = EnumRfidTagJobStatus.WRITING
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
            it[version] = job[RfidTagJobs.version] + 1
        }
        appendOperation(job[RfidTagJobs.tagId], EnumRfidTagOperationType.WRITE_REQUESTED, job[RfidTagJobs.workstationId], commandId, null, job[RfidTagJobs.targetEpc], tid, true, null)
    }

    private fun activate(job: ResultRow, now: LocalDateTime) {
        val tagId = job[RfidTagJobs.tagId]
        val collectionId = job[RfidTagJobs.collectionId]
        val oldTags = RfidTags.selectAll().where {
            (RfidTags.orgId eq job[RfidTagJobs.orgId]) and
                (RfidTags.collectionId eq collectionId) and
                (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE)
        }.toList()
        oldTags.forEach { old ->
            RfidTags.update({ RfidTags.id eq old[RfidTags.id].value }) {
                it[tagStatus] = EnumRfidTagStatus.REPLACED
                it[replacedById] = tagId
                it[retiredAt] = now
                it[retiredBy] = SecurityUtils.getUserId()
                it[retiredReason] = "发标任务 ${job[RfidTagJobs.id].value} 换标"
                it[RfidTags.collectionId] = null
                it[updateTime] = now
                it[updateBy] = SecurityUtils.getUserId()
            }
        }
        RfidTags.update({ RfidTags.id eq tagId }) {
            it[tagStatus] = EnumRfidTagStatus.ACTIVE
            it[activatedAt] = now
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        RfidTagJobs.update({ RfidTagJobs.id eq job[RfidTagJobs.id].value }) {
            it[jobStatus] = EnumRfidTagJobStatus.ACTIVATED
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        // 兼容现有盘点/查询入口；生命周期真相以 col_rfid_tag 为准。
        Collections.update({ Collections.id eq collectionId }) { it[rfidTag] = job[RfidTagJobs.targetEpc] }
        appendOperation(tagId, EnumRfidTagOperationType.ACTIVATED, job[RfidTagJobs.workstationId], job[RfidTagJobs.commandId], null, job[RfidTagJobs.targetEpc], job[RfidTagJobs.expectedTid], true, null)
    }

    private fun requireWritableJob(id: String): ResultRow {
        val row = RfidTagJobs.selectAll().where { RfidTagJobs.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("发标任务不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidTagJobs.orgId], "发标任务")
        return row
    }

    private fun requireTagStation(id: String, orgId: String): ResultRow {
        val station = RfidWorkstations.selectAll().where { RfidWorkstations.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("发标工作站不存在")
        if (station[RfidWorkstations.orgId] != orgId || !station[RfidWorkstations.enabled]) throw BadRequestException("发标工作站不可用")
        if (EnumRfidWorkstationCapability.TAG_ISSUE !in workstationCapabilities(station)) {
            throw BadRequestException("工作站未启用标签发放能力")
        }
        return station
    }

    private fun appendOperation(
        tagId: String,
        type: EnumRfidTagOperationType,
        workstationId: String?,
        commandId: String?,
        beforeEpc: String?,
        afterEpc: String?,
        tid: String?,
        success: Boolean,
        resultCode: String?,
        detail: String? = null,
    ) {
        val tag = RfidTags.selectAll().where { RfidTags.id eq tagId }.limit(1).first()
        RfidTagOperations.insert {
            it[id] = IdGenerate.nextId()
            it[orgId] = tag[RfidTags.orgId]
            it[RfidTagOperations.tagId] = tagId
            it[operationType] = type
            it[RfidTagOperations.workstationId] = workstationId
            it[operatorId] = SecurityUtils.getUserId()
            it[RfidTagOperations.commandId] = commandId
            it[RfidTagOperations.beforeEpc] = beforeEpc
            it[RfidTagOperations.afterEpc] = afterEpc
            it[RfidTagOperations.tid] = tid
            it[RfidTagOperations.success] = success
            it[RfidTagOperations.resultCode] = resultCode
            it[RfidTagOperations.detail] = detail.clean(500)
            it[occurredAt] = LocalDateTime.now()
        }
    }

    private fun tagVo(row: ResultRow): RfidTagVo {
        val collection = row[RfidTags.collectionId]?.let { id ->
            Collections.selectAll().where { Collections.id eq id }.limit(1).firstOrNull()
        }
        return RfidTagVo(
            id = row[RfidTags.id].value,
            orgId = row[RfidTags.orgId],
            epc = row[RfidTags.epc],
            tid = row[RfidTags.tid],
            collectionId = row[RfidTags.collectionId],
            collectionNo = collection?.get(Collections.totalRegNo),
            collectionName = collection?.get(Collections.name),
            status = row[RfidTags.tagStatus],
            issuedAt = row[RfidTags.issuedAt].epoch(),
            activatedAt = row[RfidTags.activatedAt].epoch(),
            retiredAt = row[RfidTags.retiredAt].epoch(),
            retiredReason = row[RfidTags.retiredReason],
        )
    }

    private fun jobVo(row: ResultRow) = RfidTagJobVo(
        id = row[RfidTagJobs.id].value,
        collectionId = row[RfidTagJobs.collectionId],
        workstationId = row[RfidTagJobs.workstationId],
        tagId = row[RfidTagJobs.tagId],
        targetEpc = row[RfidTagJobs.targetEpc],
        expectedTid = row[RfidTagJobs.expectedTid],
        verifiedEpc = row[RfidTagJobs.verifiedEpc],
        verifiedTid = row[RfidTagJobs.verifiedTid],
        commandId = row[RfidTagJobs.commandId],
        status = row[RfidTagJobs.jobStatus],
        errorMessage = row[RfidTagJobs.errorMessage],
    )

    private fun operationVo(row: ResultRow) = RfidTagOperationVo(
        id = row[RfidTagOperations.id].value,
        tagId = row[RfidTagOperations.tagId],
        operationType = row[RfidTagOperations.operationType],
        workstationId = row[RfidTagOperations.workstationId],
        operatorId = row[RfidTagOperations.operatorId],
        commandId = row[RfidTagOperations.commandId],
        beforeEpc = row[RfidTagOperations.beforeEpc],
        afterEpc = row[RfidTagOperations.afterEpc],
        tid = row[RfidTagOperations.tid],
        success = row[RfidTagOperations.success],
        resultCode = row[RfidTagOperations.resultCode],
        detail = row[RfidTagOperations.detail],
        occurredAt = row[RfidTagOperations.occurredAt].epoch()!!,
    )

    private fun generateEpc(orgId: String, collectionId: String, jobId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$orgId:$collectionId:$jobId".toByteArray())
        return "E2" + digest.joinToString("") { "%02X".format(it) }.take(22)
    }

    private fun requireTidSelector(tid: String) {
        // C5 选择器用 1 字节表示 bit 长度，因此完整 TID 最多 31 字节（248 bit）。
        if (tid.length > 62) throw BadRequestException("TID 过长，当前读写器最多支持 31 字节选择器")
    }

    private fun LocalDateTime?.epoch() = this?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    private fun String?.clean(max: Int) = this?.trim()?.takeIf(String::isNotBlank)?.take(max)
    private fun HttpServletRequest.cleanParameter(name: String, max: Int) = getParameter(name).clean(max)
    private fun HttpServletRequest.epochParameter(name: String) = getParameter(name)?.toLongOrNull()?.let {
        java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
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
}
