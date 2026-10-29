package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidRealtimeEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidRealtimeEventType
import io.hizhengfu.cloud.collection.rfid.domain.RfidRealtimeEventVo
import io.hizhengfu.cloud.collection.service.OrgScopedReadPolicy
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
@Transactional
class RfidRealtimeEventService(private val scope: CollectionScopeResolver) {
    private val emitters = ConcurrentHashMap<String, CopyOnWriteArraySet<SseEmitter>>()
    private val scopeEmitters = CopyOnWriteArraySet<ScopeEmitter>()

    fun publish(
        orgId: String,
        workstationId: String?,
        sessionId: String?,
        type: EnumRfidRealtimeEventType,
        payload: Map<String, Any?>,
    ): RfidRealtimeEventVo {
        val id = IdGenerate.nextId()
        // 雪花 ID 在本系统内单调且全局唯一，直接作为 SSE 恢复游标，避免并发查询 max+1 冲突。
        val sequence = id.toLong()
        val now = LocalDateTime.now()
        val event = RfidRealtimeEventVo(id, type, workstationId, sessionId, sequence, now.epoch(), payload)
        RfidRealtimeEvents.insert {
            it[RfidRealtimeEvents.id] = id
            it[RfidRealtimeEvents.orgId] = orgId
            it[RfidRealtimeEvents.workstationId] = workstationId
            it[RfidRealtimeEvents.sessionId] = sessionId
            it[eventType] = type
            it[RfidRealtimeEvents.sequence] = sequence
            it[occurredAt] = now
            it[payloadJson] = JsonUtils.toJson(payload) ?: "{}"
            it[expiresAt] = now.plusHours(24)
        }
        afterCommit {
            workstationId?.let { send(it, event) }
            sendScope(orgId, event)
        }
        return event
    }

    @Transactional(readOnly = true)
    fun subscribe(workstationId: String, afterSequence: Long?): SseEmitter {
        val station = RfidWorkstations.selectAll().where { RfidWorkstations.id eq workstationId }.limit(1).firstOrNull()
            ?: throw BadRequestException("RFID 工作站不存在")
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), station[RfidWorkstations.orgId], "RFID 工作站")
        val emitter = SseEmitter(30 * 60 * 1000L)
        val set = emitters.computeIfAbsent(workstationId) { CopyOnWriteArraySet() }
        set += emitter
        emitter.onCompletion { remove(workstationId, emitter) }
        emitter.onTimeout { remove(workstationId, emitter) }
        emitter.onError { remove(workstationId, emitter) }
        val replay = replay(workstationId, afterSequence ?: 0, REPLAY_LIMIT + 1)
        if (replay.size > REPLAY_LIMIT) {
            sendOne(
                emitter,
                RfidRealtimeEventVo(
                    "0",
                    EnumRfidRealtimeEventType.RESYNC_REQUIRED,
                    workstationId,
                    null,
                    0,
                    System.currentTimeMillis(),
                    mapOf("reason" to "REPLAY_LIMIT_EXCEEDED", "limit" to REPLAY_LIMIT),
                ),
            )
        } else {
            replay.forEach { event -> sendOne(emitter, event) }
        }
        sendOne(emitter, RfidRealtimeEventVo("0", EnumRfidRealtimeEventType.AUTH_STATE, workstationId, null, 0, System.currentTimeMillis(), mapOf("connected" to true)))
        return emitter
    }

    @Transactional(readOnly = true)
    fun subscribeScope(afterSequence: Long?): SseEmitter {
        val orgIds = scope.readableOrgIds()
        val emitter = SseEmitter(30 * 60 * 1000L)
        val registration = ScopeEmitter(orgIds, emitter)
        scopeEmitters += registration
        emitter.onCompletion { scopeEmitters -= registration }
        emitter.onTimeout { scopeEmitters -= registration }
        emitter.onError { scopeEmitters -= registration }
        val replay = replayScope(orgIds, afterSequence ?: 0, REPLAY_LIMIT + 1)
        if (replay.size > REPLAY_LIMIT) {
            sendOne(
                emitter,
                RfidRealtimeEventVo(
                    "0",
                    EnumRfidRealtimeEventType.RESYNC_REQUIRED,
                    null,
                    null,
                    0,
                    System.currentTimeMillis(),
                    mapOf("reason" to "REPLAY_LIMIT_EXCEEDED", "limit" to REPLAY_LIMIT),
                ),
            )
        } else {
            replay.forEach { event -> sendOne(emitter, event) }
        }
        sendOne(
            emitter,
            RfidRealtimeEventVo(
                "0",
                EnumRfidRealtimeEventType.AUTH_STATE,
                null,
                null,
                0,
                System.currentTimeMillis(),
                mapOf("connected" to true),
            ),
        )
        return emitter
    }

    /** 实时事件只承担断线重放，每日清理超过 24 小时的记录，业务审计仍由会话和通行表保留。 */
    @Scheduled(cron = "\${collection.rfid.realtime-cleanup-cron:0 15 3 * * *}")
    fun cleanupExpired(): Int = RfidRealtimeEvents.deleteWhere { RfidRealtimeEvents.expiresAt less LocalDateTime.now() }

    @Transactional(readOnly = true)
    fun replay(workstationId: String, afterSequence: Long, limit: Int = REPLAY_LIMIT): List<RfidRealtimeEventVo> =
        RfidRealtimeEvents.selectAll().where {
            (RfidRealtimeEvents.workstationId eq workstationId) and (RfidRealtimeEvents.sequence greater afterSequence)
        }.orderBy(RfidRealtimeEvents.sequence to SortOrder.ASC).limit(limit).map {
            RfidRealtimeEventVo(
                id = it[RfidRealtimeEvents.id].value,
                type = it[RfidRealtimeEvents.eventType],
                workstationId = it[RfidRealtimeEvents.workstationId],
                sessionId = it[RfidRealtimeEvents.sessionId],
                sequence = it[RfidRealtimeEvents.sequence],
                occurredAt = it[RfidRealtimeEvents.occurredAt].epoch(),
                payload = JsonUtils.fromJson<Map<String, Any?>>(it[RfidRealtimeEvents.payloadJson]) ?: emptyMap(),
            )
        }

    @Transactional(readOnly = true)
    fun replayScope(orgIds: Set<String>?, afterSequence: Long, limit: Int = REPLAY_LIMIT): List<RfidRealtimeEventVo> {
        val query = RfidRealtimeEvents.selectAll().where { RfidRealtimeEvents.sequence greater afterSequence }
        orgIds?.let { query.andWhere { RfidRealtimeEvents.orgId inList it } }
        return query.orderBy(RfidRealtimeEvents.sequence to SortOrder.ASC).limit(limit).map(::eventVo)
    }

    private fun send(workstationId: String, event: RfidRealtimeEventVo) {
        emitters[workstationId]?.toList()?.forEach { emitter ->
            try { sendOne(emitter, event) } catch (_: Exception) { remove(workstationId, emitter) }
        }
    }

    private fun sendScope(orgId: String, event: RfidRealtimeEventVo) {
        scopeEmitters.toList().forEach { registration ->
            if (registration.orgIds != null && orgId !in registration.orgIds) return@forEach
            try {
                sendOne(registration.emitter, event)
            } catch (_: Exception) {
                scopeEmitters -= registration
            }
        }
    }

    private fun eventVo(row: org.jetbrains.exposed.v1.core.ResultRow) = RfidRealtimeEventVo(
        id = row[RfidRealtimeEvents.id].value,
        type = row[RfidRealtimeEvents.eventType],
        workstationId = row[RfidRealtimeEvents.workstationId],
        sessionId = row[RfidRealtimeEvents.sessionId],
        sequence = row[RfidRealtimeEvents.sequence],
        occurredAt = row[RfidRealtimeEvents.occurredAt].epoch(),
        payload = JsonUtils.fromJson<Map<String, Any?>>(row[RfidRealtimeEvents.payloadJson]) ?: emptyMap(),
    )

    private fun sendOne(emitter: SseEmitter, event: RfidRealtimeEventVo) {
        emitter.send(SseEmitter.event().id(event.sequence.toString()).name(event.type.name).data(event))
    }

    private fun remove(workstationId: String, emitter: SseEmitter) {
        emitters[workstationId]?.let { set ->
            set -= emitter
            if (set.isEmpty()) emitters.remove(workstationId, set)
        }
    }

    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return action()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = action()
        })
    }

    private fun LocalDateTime.epoch() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private data class ScopeEmitter(val orgIds: Set<String>?, val emitter: SseEmitter)

    private companion object {
        const val REPLAY_LIMIT = 500
    }
}
