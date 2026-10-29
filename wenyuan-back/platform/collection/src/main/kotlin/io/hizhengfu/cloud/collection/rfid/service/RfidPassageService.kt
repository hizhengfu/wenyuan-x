package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageDeviceVo
import io.hizhengfu.cloud.collection.rfid.dao.RfidAnomalies
import io.hizhengfu.cloud.collection.rfid.dao.RfidAnomalyTransitions
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageGrants
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAlarmStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAnomalyStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAnomalyType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidPassageDecision
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidPassageGrantStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWhitelistSyncStatus
import io.hizhengfu.cloud.collection.rfid.domain.RfidAnomalyProcessVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidAnomalyVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageEventCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageEventVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageGrantCreateVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageGrantRevokeVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidPassageGrantVo
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
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Component
@Transactional
class RfidPassageService(
    private val scope: CollectionScopeResolver,
    private val commandPort: RfidDeviceCommandPort,
    private val realtime: RfidRealtimeEventService,
) {
    /** 保留旧客户端的明确错误提示；新通行资格只来自已完成的 RFID 出入库流程。 */
    fun createGrant(@Suppress("UNUSED_PARAMETER") vo: RfidPassageGrantCreateVo): RfidPassageGrantVo =
        throw BadRequestException("通行资格由 RFID 出入库流程完成后自动核验，无需手工创建或绑定门禁")

    @Transactional(readOnly = true)
    fun gateDevices(): List<RfidPassageDeviceVo> {
        val query = MuseumDevices.join(MuseumRfidDeviceProfiles, org.jetbrains.exposed.v1.core.JoinType.INNER,
            MuseumDevices.id, MuseumRfidDeviceProfiles.deviceId).selectAll().where {
            (MuseumDevices.status eq EnumContentStatus.NORMAL) and
                (MuseumRfidDeviceProfiles.orgId eq MuseumDevices.orgId) and
                (MuseumRfidDeviceProfiles.enabled eq true) and
                (MuseumRfidDeviceProfiles.status eq EnumContentStatus.NORMAL)
        }
        scope.readableOrgIds()?.let { query.andWhere { MuseumDevices.orgId inList it } }
        return query.orderBy(MuseumDevices.name to SortOrder.ASC).mapNotNull { device ->
            val id = device[MuseumDevices.id].value
            if (!commandPort.supportsAnyRole(id, device[MuseumDevices.orgId], GATE_ROLES)) null
            else RfidPassageDeviceVo(id, device[MuseumDevices.name], device[MuseumDevices.code])
        }
    }

    /** 兼容旧白名单任务的回执；门禁新事件不再读取手工通行授权。 */
    fun acknowledgeAllowlist(commandId: String, success: Boolean): String {
        val grant = RfidPassageGrants.selectAll().where { RfidPassageGrants.whitelistCommandId eq commandId }
            .limit(1).firstOrNull() ?: throw BadRequestException("未找到白名单命令对应授权")
        val id = grant[RfidPassageGrants.id].value
        RfidPassageGrants.update({ RfidPassageGrants.id eq id }) {
            it[whitelistSyncStatus] = if (success) EnumRfidWhitelistSyncStatus.SYNCED else EnumRfidWhitelistSyncStatus.FAILED
            it[updateTime] = LocalDateTime.now()
        }
        return id
    }

    @Transactional(readOnly = true)
    fun grants(request: HttpServletRequest): GenericPage<RfidPassageGrantVo> {
        val page = GenericPage<RfidPassageGrantVo>().build(request)
        val query = RfidPassageGrants.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidPassageGrants.orgId inList it } }
        request.enumParameter<EnumRfidPassageGrantStatus>("status")?.let {
            query.andWhere { RfidPassageGrants.grantStatus eq it }
        }
        request.enumParameter<EnumRfidDirection>("direction")?.let {
            query.andWhere { RfidPassageGrants.direction eq it }
        }
        request.cleanParameter("gateId", 64)?.let { query.andWhere { RfidPassageGrants.gateId eq it } }
        request.cleanParameter("sourceType", 64)?.let { query.andWhere { RfidPassageGrants.sourceType eq it } }
        request.cleanParameter("keyword", 128)?.let { keyword ->
            query.andWhere {
                (RfidPassageGrants.epc like "%$keyword%") or
                    (RfidPassageGrants.collectionId like "%$keyword%") or
                    (RfidPassageGrants.sourceId like "%$keyword%")
            }
        }
        query.orderBy(RfidPassageGrants.createTime to SortOrder.DESC)
        page.list = query.pageRows(page).map(::grantVo)
        return page
    }

    fun revokeGrant(id: String, vo: RfidPassageGrantRevokeVo): RfidPassageGrantVo {
        val row = RfidPassageGrants.selectAll().where { RfidPassageGrants.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("RFID 通行授权不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidPassageGrants.orgId], "RFID 通行授权")
        if (row[RfidPassageGrants.grantStatus] in setOf(
                EnumRfidPassageGrantStatus.USED,
                EnumRfidPassageGrantStatus.EXPIRED,
                EnumRfidPassageGrantStatus.REVOKED,
            )
        ) {
            throw BadRequestException("当前状态的通行授权不能撤销")
        }
        val reason = vo.reason.clean(500) ?: throw BadRequestException("撤销原因必填")
        val now = LocalDateTime.now()
        RfidPassageGrants.update({ RfidPassageGrants.id eq id }) {
            it[grantStatus] = EnumRfidPassageGrantStatus.REVOKED
            it[revokedAt] = now
            it[revokedBy] = SecurityUtils.getUserId()
            it[revokeReason] = reason
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        return grantVo(RfidPassageGrants.selectAll().where { RfidPassageGrants.id eq id }.first())
    }

    /** 每个采集事实均落日志；重复投递幂等，同次经过的重复读卡只合并报警输出。 */
    fun recordEvent(vo: RfidPassageEventCreateVo): RfidPassageEventVo {
        val deviceId = vo.deviceId.clean(64) ?: throw BadRequestException("deviceId 必填")
        val sourceEventId = vo.sourceEventId.clean(100) ?: throw BadRequestException("sourceEventId 必填")
        val device = MuseumDevices.selectAll().where { MuseumDevices.id eq deviceId }.limit(1).firstOrNull()
            ?: throw BadRequestException("门禁设备不存在")
        val orgId = device[MuseumDevices.orgId]
        if (device[MuseumDevices.status] != EnumContentStatus.NORMAL || !commandPort.supportsAnyRole(deviceId, orgId, GATE_ROLES)) {
            throw BadRequestException("设备已停用或未登记为 RFID 门禁")
        }
        val epc = RfidHex.normalize(vo.epc, "epc")!!
        val tid = RfidHex.normalize(vo.tid, "tid", required = false)
        RfidPassageEvents.selectAll().where { RfidPassageEvents.sourceEventId eq sourceEventId }
            .limit(1).firstOrNull()?.let {
                if (it[RfidPassageEvents.orgId] != orgId || it[RfidPassageEvents.deviceId] != deviceId ||
                    it[RfidPassageEvents.epc] != epc || it[RfidPassageEvents.tid] != tid) {
                    throw BadRequestException("sourceEventId 已用于其他门禁读卡记录")
                }
                return eventVo(it)
            }
        // gateId 仅用于保留历史接口字段；新记录始终指向实际上报设备，不接受客户端指定授权通道。
        val gateId = deviceId
        val direction = vo.direction ?: EnumRfidDirection.UNKNOWN
        val occurredAt = vo.occurredAt.toLocalDateTime() ?: LocalDateTime.now()
        val receivedAt = LocalDateTime.now()
        val tag = RfidTags.selectAll().where {
            (RfidTags.orgId eq orgId) and (RfidTags.epc eq epc) and (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE)
        }.limit(1).firstOrNull()
        val collectionId = tag?.get(RfidTags.collectionId)
        val collection = collectionId?.let { id ->
            Collections.selectAll().where { (Collections.id eq id) and (Collections.orgId eq orgId) }.limit(1).firstOrNull()
        }
        val tidConflict = tag?.get(RfidTags.tid) != null && tid != null && tag[RfidTags.tid] != tid
        val late = Duration.between(occurredAt, receivedAt).abs() > Duration.ofMinutes(5)
        val check = when {
            tidConflict -> RfidPassageBusinessCheck(false, "标签 TID 与绑定档案不一致", EnumRfidAnomalyType.TAG_CONFLICT)
            tag == null || collection == null -> RfidPassageBusinessCheck(false, "未知标签或未关联本馆有效藏品", EnumRfidAnomalyType.UNKNOWN_TAG)
            else -> RfidPassageBusinessVerifier.verify(collection, epc, tid, direction, occurredAt)
        }
        val decision = when {
            late -> EnumRfidPassageDecision.LATE
            tag == null || collection == null -> EnumRfidPassageDecision.UNKNOWN
            check.allowed -> EnumRfidPassageDecision.AUTHORIZED
            else -> EnumRfidPassageDecision.UNAUTHORIZED
        }
        val reason = if (late) "离线迟到或设备时间异常；${check.reason}" else check.reason
        val anomalyType = if (late) EnumRfidAnomalyType.LATE_OFFLINE_EVENT else check.anomalyType
        val id = IdGenerate.nextId()
        RfidPassageEvents.insert {
            it[RfidPassageEvents.id] = id
            it[RfidPassageEvents.orgId] = orgId
            it[RfidPassageEvents.deviceId] = deviceId
            it[RfidPassageEvents.gateId] = gateId
            it[antenna] = vo.antenna
            it[RfidPassageEvents.epc] = epc
            it[RfidPassageEvents.tid] = tid
            it[RfidPassageEvents.direction] = direction
            it[RfidPassageEvents.occurredAt] = occurredAt
            it[RfidPassageEvents.receivedAt] = receivedAt
            it[RfidPassageEvents.collectionId] = collection?.get(Collections.id)?.value
            it[RfidPassageEvents.decision] = decision
            it[RfidPassageEvents.sourceEventId] = sourceEventId
            it[alarmStatus] = EnumRfidAlarmStatus.NOT_REQUIRED
            it[contextJson] = JsonUtils.toJson(mapOf(
                "reason" to reason, "tidConflict" to tidConflict, "late" to late,
                "sourceSessionId" to check.sessionId, "sourceType" to check.sourceType, "sourceId" to check.sourceId,
                "businessDirection" to check.direction?.name, "collectionLifeStatus" to collection?.get(Collections.lifeStatus)?.name,
            )) ?: "{}"
        }
        val policy = device[MuseumDevices.instanceConfig]?.let {
            runCatching { JsonUtils.fromJson<Map<String, Any?>>(it) }.getOrNull()
        }.orEmpty()
        val debounceSeconds = (policy["passageDebounceSeconds"] as? Number)?.toLong()?.coerceIn(1, 60) ?: 3L
        val recentAlarm = if (anomalyType == null || late) null else RfidPassageEvents
            .join(RfidAnomalies, org.jetbrains.exposed.v1.core.JoinType.INNER, RfidPassageEvents.id, RfidAnomalies.eventId)
            .selectAll().where {
                (RfidPassageEvents.orgId eq orgId) and (RfidPassageEvents.deviceId eq deviceId) and
                    (RfidPassageEvents.epc eq epc) and (RfidPassageEvents.tid eq tid) and
                    (RfidPassageEvents.direction eq direction) and (RfidAnomalies.anomalyType eq anomalyType) and
                    (RfidPassageEvents.receivedAt greaterEq receivedAt.minusSeconds(debounceSeconds)) and
                    (RfidPassageEvents.alarmStatus inList listOf(EnumRfidAlarmStatus.QUEUED, EnumRfidAlarmStatus.SUCCEEDED))
            }.orderBy(RfidPassageEvents.receivedAt to SortOrder.DESC).limit(1).firstOrNull()
        val anomalyId = if (anomalyType == null) null else recentAlarm?.get(RfidAnomalies.id)?.value
            ?: createAnomaly(anomalyType, reason, id, orgId, collection?.get(Collections.id)?.value, epc)
        if (anomalyId != null) {
            RfidPassageEvents.update({ RfidPassageEvents.id eq id }) { it[RfidPassageEvents.anomalyId] = anomalyId }
            if (!late) {
                val alarm = if (recentAlarm != null) Result.success(recentAlarm[RfidPassageEvents.alarmCommandId]) else runCatching {
                    commandPort.alarmOutput(
                        deviceId, anomalyId,
                        (policy["alarmOutputIndex"] as? Number)?.toInt()?.coerceIn(1, 32) ?: 1,
                        (policy["alarmDurationMillis"] as? Number)?.toLong()?.coerceIn(100, 60_000) ?: 3_000,
                    )
                }
                RfidPassageEvents.update({ RfidPassageEvents.id eq id }) {
                    it[alarmCommandId] = alarm.getOrNull()
                    it[alarmStatus] = recentAlarm?.get(RfidPassageEvents.alarmStatus)
                        ?: if (alarm.isSuccess) EnumRfidAlarmStatus.QUEUED else EnumRfidAlarmStatus.FAILED
                    it[alarmMessage] = alarm.exceptionOrNull()?.message.clean(500)
                }
            }
        }
        val event = eventVo(RfidPassageEvents.selectAll().where { RfidPassageEvents.id eq id }.first())
        realtime.publish(orgId, null, check.sessionId, io.hizhengfu.cloud.collection.rfid.domain.EnumRfidRealtimeEventType.GATE_EVENT,
            mapOf("eventId" to id, "deviceId" to deviceId, "decision" to decision.name, "epc" to epc, "direction" to direction.name, "reason" to reason))
        if (anomalyId != null && recentAlarm == null) {
            realtime.publish(orgId, null, check.sessionId, io.hizhengfu.cloud.collection.rfid.domain.EnumRfidRealtimeEventType.BUSINESS_ANOMALY,
                mapOf("anomalyId" to anomalyId, "eventId" to id, "deviceId" to deviceId, "decision" to decision.name, "epc" to epc, "reason" to reason))
        }
        return event
    }

    fun acknowledgeAlarm(commandId: String, success: Boolean, message: String?): String {
        val event = RfidPassageEvents.selectAll().where { RfidPassageEvents.alarmCommandId eq commandId }
            .limit(1).firstOrNull() ?: throw BadRequestException("未找到报警命令对应通行事件")
        val id = event[RfidPassageEvents.id].value
        RfidPassageEvents.update({ RfidPassageEvents.alarmCommandId eq commandId }) {
            it[alarmStatus] = if (success) EnumRfidAlarmStatus.SUCCEEDED else EnumRfidAlarmStatus.FAILED
            it[alarmMessage] = message.clean(500)
        }
        realtime.publish(
            event[RfidPassageEvents.orgId],
            null,
            null,
            io.hizhengfu.cloud.collection.rfid.domain.EnumRfidRealtimeEventType.COMMAND_RESULT,
            mapOf("eventId" to id, "commandId" to commandId, "success" to success, "message" to message.clean(500)),
        )
        return id
    }

    @Transactional(readOnly = true)
    fun nextGrantBoundary(): LocalDateTime? {
        val t=RfidPassageGrants;val from=t.validFrom.min();val until=t.validUntil.min()
        val activation=t.select(from).where {t.grantStatus eq EnumRfidPassageGrantStatus.PENDING}.single()[from]
        val expiry=t.select(until).where {t.grantStatus inList listOf(EnumRfidPassageGrantStatus.PENDING,EnumRfidPassageGrantStatus.ACTIVE)}.single()[until]
        // valid_until 为闭区间，下一毫秒再过期，避免等于边界时重复执行。
        return listOfNotNull(activation,expiry?.plusNanos(1_000_000)).minOrNull()
    }

    fun refreshGrantStatuses(): Int {
        val now = LocalDateTime.now()
        val activated = RfidPassageGrants.update({
            (RfidPassageGrants.grantStatus eq EnumRfidPassageGrantStatus.PENDING) and
                (RfidPassageGrants.validFrom lessEq now) and
                (RfidPassageGrants.validUntil greaterEq now)
        }) {
            it[grantStatus] = EnumRfidPassageGrantStatus.ACTIVE
            it[updateTime] = now
        }
        val expired = RfidPassageGrants.update({
            (RfidPassageGrants.grantStatus inList listOf(EnumRfidPassageGrantStatus.PENDING, EnumRfidPassageGrantStatus.ACTIVE)) and
                (RfidPassageGrants.validUntil less now)
        }) {
            it[grantStatus] = EnumRfidPassageGrantStatus.EXPIRED
            it[updateTime] = now
        }
        return activated + expired
    }

    @Transactional(readOnly = true)
    fun events(request: HttpServletRequest): GenericPage<RfidPassageEventVo> {
        val page = GenericPage<RfidPassageEventVo>().build(request)
        val query = RfidPassageEvents.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidPassageEvents.orgId inList it } }
        request.cleanParameter("gateId", 64)?.let { query.andWhere { RfidPassageEvents.gateId eq it } }
        request.enumParameter<EnumRfidPassageDecision>("decision")?.let {
            query.andWhere { RfidPassageEvents.decision eq it }
        }
        request.enumParameter<EnumRfidDirection>("direction")?.let {
            query.andWhere { RfidPassageEvents.direction eq it }
        }
        request.cleanParameter("keyword", 128)?.let { keyword ->
            query.andWhere {
                (RfidPassageEvents.epc like "%$keyword%") or
                    (RfidPassageEvents.collectionId like "%$keyword%") or
                    (RfidPassageEvents.sourceEventId like "%$keyword%")
            }
        }
        request.epochParameter("occurredAtStart")?.let { query.andWhere { RfidPassageEvents.occurredAt greaterEq it } }
        request.epochParameter("occurredAtEnd")?.let { query.andWhere { RfidPassageEvents.occurredAt lessEq it } }
        query.orderBy(RfidPassageEvents.occurredAt to SortOrder.DESC)
        page.list = query.pageRows(page).map(::eventVo)
        return page
    }

    @Transactional(readOnly = true)
    fun anomalies(request: HttpServletRequest): GenericPage<RfidAnomalyVo> {
        val page = GenericPage<RfidAnomalyVo>().build(request)
        val query = RfidAnomalies.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidAnomalies.orgId inList it } }
        request.enumParameter<EnumRfidAnomalyStatus>("status")?.let {
            query.andWhere { RfidAnomalies.anomalyStatus eq it }
        }
        if (request.getParameter("active")?.toBooleanStrictOrNull() == true) {
            query.andWhere { RfidAnomalies.anomalyStatus notInList CLOSED_ANOMALY_STATES.toList() }
        }
        request.enumParameter<EnumRfidAnomalyType>("type")?.let {
            query.andWhere { RfidAnomalies.anomalyType eq it }
        }
        request.cleanParameter("keyword", 128)?.let { keyword ->
            query.andWhere {
                (RfidAnomalies.epc like "%$keyword%") or
                    (RfidAnomalies.collectionId like "%$keyword%") or
                    (RfidAnomalies.title like "%$keyword%") or
                    (RfidAnomalies.detail like "%$keyword%")
            }
        }
        request.epochParameter("openedAtStart")?.let { query.andWhere { RfidAnomalies.openedAt greaterEq it } }
        request.epochParameter("openedAtEnd")?.let { query.andWhere { RfidAnomalies.openedAt lessEq it } }
        query.orderBy(RfidAnomalies.openedAt to SortOrder.DESC)
        page.list = query.pageRows(page).map(::anomalyVo)
        return page
    }

    @Transactional(readOnly = true)
    fun anomaly(id: String): RfidAnomalyVo {
        val row = RfidAnomalies.selectAll().where { RfidAnomalies.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("RFID 异常不存在")
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), row[RfidAnomalies.orgId], "RFID 异常")
        val event = row[RfidAnomalies.eventId]?.let { eventId ->
            RfidPassageEvents.selectAll().where { RfidPassageEvents.id eq eventId }.limit(1).firstOrNull()
        }
        return anomalyVo(row, event)
    }

    fun processAnomaly(id: String, vo: RfidAnomalyProcessVo): RfidAnomalyVo {
        val row = RfidAnomalies.selectAll().where { RfidAnomalies.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("RFID 异常不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidAnomalies.orgId], "RFID 异常")
        val to = vo.status ?: throw BadRequestException("处理状态必填")
        val from = row[RfidAnomalies.anomalyStatus]
        requireAnomalyTransition(from, to)
        val now = LocalDateTime.now()
        RfidAnomalies.update({ RfidAnomalies.id eq id }) {
            it[anomalyStatus] = to
            it[handlerId] = SecurityUtils.getUserId()
            it[resolutionNote] = vo.note.clean(1000)
            if (to == EnumRfidAnomalyStatus.ACKNOWLEDGED && row[RfidAnomalies.acknowledgedAt] == null) it[acknowledgedAt] = now
            if (to in CLOSED_ANOMALY_STATES) it[resolvedAt] = now
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        RfidAnomalyTransitions.insert {
            it[RfidAnomalyTransitions.id] = IdGenerate.nextId()
            it[anomalyId] = id
            it[fromStatus] = from
            it[toStatus] = to
            it[operatorId] = SecurityUtils.getUserDetail().id
            it[note] = vo.note.clean(1000)
            it[occurredAt] = now
        }
        return anomalyVo(RfidAnomalies.selectAll().where { RfidAnomalies.id eq id }.first())
    }

    private fun createAnomaly(type: EnumRfidAnomalyType, reason: String, eventId: String, orgId: String, collectionId: String?, epc: String): String {
        val id = IdGenerate.nextId()
        val now = LocalDateTime.now()
        RfidAnomalies.insert {
            it[RfidAnomalies.id] = id
            it[RfidAnomalies.orgId] = orgId
            it[RfidAnomalies.eventId] = eventId
            it[RfidAnomalies.collectionId] = collectionId
            it[RfidAnomalies.epc] = epc
            it[anomalyType] = type
            it[anomalyStatus] = EnumRfidAnomalyStatus.OPEN
            it[title] = type.description
            it[detail] = reason
            it[openedAt] = now
            it[createTime] = now
            it[updateTime] = now
        }
        RfidAnomalyTransitions.insert {
            it[RfidAnomalyTransitions.id] = IdGenerate.nextId()
            it[anomalyId] = id
            it[fromStatus] = null
            it[toStatus] = EnumRfidAnomalyStatus.OPEN
            it[operatorId] = "SYSTEM"
            it[note] = "RFID 出入库核验自动创建"
            it[occurredAt] = now
        }
        return id
    }

    private fun requireAnomalyTransition(from: EnumRfidAnomalyStatus, to: EnumRfidAnomalyStatus) {
        val allowed = when (from) {
            EnumRfidAnomalyStatus.OPEN -> setOf(EnumRfidAnomalyStatus.ACKNOWLEDGED, EnumRfidAnomalyStatus.INVESTIGATING, EnumRfidAnomalyStatus.FALSE_POSITIVE, EnumRfidAnomalyStatus.ESCALATED)
            EnumRfidAnomalyStatus.ACKNOWLEDGED -> setOf(EnumRfidAnomalyStatus.INVESTIGATING, EnumRfidAnomalyStatus.RESOLVED, EnumRfidAnomalyStatus.FALSE_POSITIVE, EnumRfidAnomalyStatus.ESCALATED)
            EnumRfidAnomalyStatus.INVESTIGATING, EnumRfidAnomalyStatus.ESCALATED -> setOf(EnumRfidAnomalyStatus.RESOLVED, EnumRfidAnomalyStatus.FALSE_POSITIVE, EnumRfidAnomalyStatus.ESCALATED)
            EnumRfidAnomalyStatus.RESOLVED, EnumRfidAnomalyStatus.FALSE_POSITIVE -> emptySet()
        }
        if (to !in allowed) throw BadRequestException("异常不能从${from.description}变为${to.description}")
    }

    private fun grantVo(row: ResultRow) = RfidPassageGrantVo(
        id = row[RfidPassageGrants.id].value,
        collectionId = row[RfidPassageGrants.collectionId], epc = row[RfidPassageGrants.epc],
        sourceType = row[RfidPassageGrants.sourceType], sourceId = row[RfidPassageGrants.sourceId],
        gateId = row[RfidPassageGrants.gateId], direction = row[RfidPassageGrants.direction],
        validFrom = row[RfidPassageGrants.validFrom].epoch(), validUntil = row[RfidPassageGrants.validUntil].epoch(),
        status = row[RfidPassageGrants.grantStatus], whitelistSyncStatus = row[RfidPassageGrants.whitelistSyncStatus],
    )

    private fun eventVo(row: ResultRow): RfidPassageEventVo {
        val context = JsonUtils.fromJson<Map<String, Any?>>(row[RfidPassageEvents.contextJson]).orEmpty()
        return RfidPassageEventVo(
            id = row[RfidPassageEvents.id].value, deviceId = row[RfidPassageEvents.deviceId], gateId = row[RfidPassageEvents.gateId],
            antenna = row[RfidPassageEvents.antenna],
            epc = row[RfidPassageEvents.epc], tid = row[RfidPassageEvents.tid], direction = row[RfidPassageEvents.direction],
            occurredAt = row[RfidPassageEvents.occurredAt].epoch(), receivedAt = row[RfidPassageEvents.receivedAt].epoch(),
            collectionId = row[RfidPassageEvents.collectionId], grantId = row[RfidPassageEvents.grantId],
            decision = row[RfidPassageEvents.decision], anomalyId = row[RfidPassageEvents.anomalyId],
            alarmCommandId = row[RfidPassageEvents.alarmCommandId],
            alarmStatus = row[RfidPassageEvents.alarmStatus],
            alarmMessage = row[RfidPassageEvents.alarmMessage],
            sourceSessionId = context["sourceSessionId"] as? String,
            sourceType = context["sourceType"] as? String,
            sourceId = context["sourceId"] as? String,
            reason = context["reason"] as? String,
            businessDirection = (context["businessDirection"] as? String)?.let { EnumRfidDirection.valueOf(it) },
        )
    }

    private fun anomalyVo(row: ResultRow, event: ResultRow? = null) = RfidAnomalyVo(
        id = row[RfidAnomalies.id].value, eventId = row[RfidAnomalies.eventId], collectionId = row[RfidAnomalies.collectionId],
        epc = row[RfidAnomalies.epc], type = row[RfidAnomalies.anomalyType], status = row[RfidAnomalies.anomalyStatus],
        title = row[RfidAnomalies.title], detail = row[RfidAnomalies.detail], openedAt = row[RfidAnomalies.openedAt].epoch(),
        acknowledgedAt = row[RfidAnomalies.acknowledgedAt].epochOrNull(), resolvedAt = row[RfidAnomalies.resolvedAt].epochOrNull(),
        handlerId = row[RfidAnomalies.handlerId], resolutionNote = row[RfidAnomalies.resolutionNote],
        evidence = event?.let(::eventVo),
    )

    private fun Long?.toLocalDateTime() = this?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    private fun LocalDateTime.epoch() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun LocalDateTime?.epochOrNull() = this?.epoch()
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

    private companion object {
        val GATE_ROLES = setOf("WAREHOUSE_GATE", "MUSEUM_GATE")
        val CLOSED_ANOMALY_STATES = setOf(EnumRfidAnomalyStatus.RESOLVED, EnumRfidAnomalyStatus.FALSE_POSITIVE)
    }
}
