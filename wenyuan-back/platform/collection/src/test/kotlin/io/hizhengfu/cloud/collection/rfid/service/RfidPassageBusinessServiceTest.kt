package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.rfid.dao.*
import io.hizhengfu.cloud.collection.rfid.domain.*
import io.hizhengfu.cloud.collection.rfid.integration.CollectionRfidDeviceCommandAdapter
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.common.MuseumScopeResolver
import io.hizhengfu.cloud.museum.device.domain.EnumRfidDeviceRole
import io.hizhengfu.cloud.museum.device.service.MuseumRfidDeviceService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

class RfidPassageBusinessServiceTest {
    @TempDir
    lateinit var temporary: Path

    @ParameterizedTest
    @EnumSource(value = EnumRfidScanSessionType::class, names = ["LOAN_OUT", "REQUISITION_OUT", "RETURN_IN"])
    fun `completed RFID movement permits any same museum gate and logs every reading`(type: EnumRfidScanSessionType) = fixture {
        complete(type)
        val first = service.recordEvent(event("READ-1"))
        val second = service.recordEvent(event("READ-2", "GATE-2"))
        val repeated = service.recordEvent(event("READ-3"))
        assertEquals(EnumRfidPassageDecision.AUTHORIZED, first.decision)
        assertEquals(EnumRfidPassageDecision.AUTHORIZED, second.decision)
        assertEquals(EnumRfidPassageDecision.AUTHORIZED, repeated.decision)
        assertEquals("GATE-1", first.gateId)
        assertEquals("GATE-2", second.gateId)
        assertEquals("SESSION", first.sourceSessionId)
        assertEquals("BUSINESS", first.sourceId)
        assertNull(first.grantId)
        assertEquals(EnumRfidDirection.UNKNOWN, first.direction, "不得把业务方向编造成设备实际方向")
        assertEquals(if (type == EnumRfidScanSessionType.RETURN_IN) EnumRfidDirection.IN else EnumRfidDirection.OUT, first.businessDirection)
        assertEquals(3L, RfidPassageEvents.selectAll().count())
        assertEquals(0L, RfidAnomalies.selectAll().count())
        assertEquals(0, port.alarms.size)
        assertTrue(RfidRealtimeEvents.selectAll().all { it[RfidRealtimeEvents.workstationId] == null })
    }

    @Test
    fun `unfinished scan or unfinished business cannot authorize passage`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        RfidScanSessions.update({ RfidScanSessions.id eq "SESSION" }) { it[sessionState] = EnumRfidScanSessionState.PREVIEW_READY }
        val unfinishedScan = service.recordEvent(event("UNFINISHED-SCAN"))
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, unfinishedScan.decision)
        assertTrue(unfinishedScan.reason!!.contains("未完成"))
        RfidScanSessions.update({ RfidScanSessions.id eq "SESSION" }) { it[sessionState] = EnumRfidScanSessionState.FINALIZED }
        Loans.update({ Loans.id eq "BUSINESS" }) { it[outExecuted] = false }
        val unfinishedBusiness = service.recordEvent(event("UNFINISHED-BUSINESS"))
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, unfinishedBusiness.decision)
        assertEquals(EnumRfidAlarmStatus.QUEUED, unfinishedBusiness.alarmStatus)
        assertEquals(2L, RfidPassageEvents.selectAll().count())
    }

    @Test
    fun `approval or manual grant alone is not RFID completion`() = fixture {
        Collections.update({ Collections.id eq "COLLECTION" }) { it[lifeStatus] = EnumCollectionStatus.PRE_OUT }
        val result = service.recordEvent(event("NOT-COMPLETED"))
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, result.decision)
        assertEquals(EnumRfidAlarmStatus.QUEUED, result.alarmStatus)
        assertThrows(BadRequestException::class.java) { service.createGrant(RfidPassageGrantCreateVo()) }
    }

    @Test
    fun `authorized reading never suppresses later TID conflict`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        assertEquals(EnumRfidPassageDecision.AUTHORIZED, service.recordEvent(event("OK")).decision)
        val conflict = service.recordEvent(event("CLONED").copy(tid = "FFFF"))
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, conflict.decision)
        assertEquals(EnumRfidAlarmStatus.QUEUED, conflict.alarmStatus)
        assertEquals(EnumRfidAnomalyType.TAG_CONFLICT, RfidAnomalies.selectAll().single()[RfidAnomalies.anomalyType])
        assertEquals(2L, RfidPassageEvents.selectAll().count())
    }

    @Test
    fun `forced completion without an actual matching read does not permit a tag`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        RfidScanReads.deleteWhere { RfidScanReads.sessionId eq "SESSION" }
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, service.recordEvent(event("NO-READ")).decision)
        assertEquals(1, port.alarms.size)
    }

    @Test
    fun `returned or changed collection state invalidates earlier outbound completion`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        Loans.update({ Loans.id eq "BUSINESS" }) { it[returned] = true }
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, service.recordEvent(event("RETURNED")).decision)
        Loans.update({ Loans.id eq "BUSINESS" }) { it[returned] = false }
        Collections.update({ Collections.id eq "COLLECTION" }) { it[lifeStatus] = EnumCollectionStatus.IN_STORE }
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, service.recordEvent(event("IN-STORE")).decision)
    }

    @Test
    fun `partial return invalidates an old outbound scan even when the whole loan is not returned`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        CollectionReturns.insert {
            it[id] = "PARTIAL-RETURN"; it[orgId] = "ORG"; it[sourceBizType] = CollectionConst.BIZ_LOAN
            it[sourceBizId] = "BUSINESS"; it[collectionIds] = "COLLECTION"
            it[flowStatus] = EnumFlowStatus.PASS; it[executionState] = EnumReturnExecutionState.EXECUTED
        }
        val result = service.recordEvent(event("OLD-OUTBOUND"))
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, result.decision)
        assertTrue(result.reason!!.contains("已办理归还"))
    }

    @Test
    fun `a finalized session from another museum or before a tag change cannot authorize`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        RfidScanSessions.update({ RfidScanSessions.id eq "SESSION" }) { it[orgId] = "OTHER-ORG" }
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, service.recordEvent(event("FOREIGN-SESSION")).decision)
        RfidScanSessions.update({ RfidScanSessions.id eq "SESSION" }) { it[orgId] = "ORG" }
        RfidTags.update({ RfidTags.id eq "TAG" }) { it[epc] = "AABBCCDD" }
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, service.recordEvent(event("REPLACED-TAG").copy(epc = "AABBCCDD")).decision)
    }

    @Test
    fun `unaccepted return item is recorded and alarmed`() = fixture {
        complete(EnumRfidScanSessionType.RETURN_IN)
        CollectionReturns.update({ CollectionReturns.id eq "BUSINESS" }) { it[acceptedCollectionIds] = "OTHER" }
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, service.recordEvent(event("UNACCEPTED")).decision)
        assertEquals(1, port.alarms.size)
    }

    @Test
    fun `explicit opposite direction is denied while unknown direction stays unknown`() = fixture {
        complete(EnumRfidScanSessionType.LOAN_OUT)
        val event = service.recordEvent(event("WRONG-DIRECTION").copy(direction = EnumRfidDirection.IN))
        assertEquals(EnumRfidPassageDecision.UNAUTHORIZED, event.decision)
        assertEquals(EnumRfidAnomalyType.WRONG_DIRECTION, RfidAnomalies.selectAll().single()[RfidAnomalies.anomalyType])
    }

    @Test
    fun `unknown and other museum tags are logged and alarmed`() = fixture {
        val unknown = service.recordEvent(event("UNKNOWN").copy(epc = "AABBCCDD"))
        assertEquals(EnumRfidPassageDecision.UNKNOWN, unknown.decision)
        RfidTags.update({ RfidTags.id eq "TAG" }) { it[orgId] = "OTHER-ORG" }
        val foreign = service.recordEvent(event("FOREIGN"))
        assertEquals(EnumRfidPassageDecision.UNKNOWN, foreign.decision)
        assertNull(foreign.collectionId)
        assertEquals(2, port.alarms.size)
    }

    @Test
    fun `alarm coalescing keeps all logs and receipt updates every related log`() = fixture {
        val first = service.recordEvent(event("DENIED-1"))
        val second = service.recordEvent(event("DENIED-2"))
        assertNotEquals(first.id, second.id)
        assertEquals(first.anomalyId, second.anomalyId)
        assertEquals(first.alarmCommandId, second.alarmCommandId)
        assertEquals(2L, RfidPassageEvents.selectAll().count())
        assertEquals(1, port.alarms.size)
        service.acknowledgeAlarm(first.alarmCommandId!!, true, "OK")
        assertTrue(RfidPassageEvents.selectAll().all { it[RfidPassageEvents.alarmStatus] == EnumRfidAlarmStatus.SUCCEEDED })
        assertEquals(first.id, service.recordEvent(event("DENIED-1")).id, "HTTP 重传保持幂等")
        assertEquals(2L, RfidPassageEvents.selectAll().count())
    }

    @Test
    fun `alarm submission failure retains business evidence`() = fixture {
        port.failAlarm = true
        val event = service.recordEvent(event("ALARM-FAIL"))
        assertEquals(EnumRfidAlarmStatus.FAILED, event.alarmStatus)
        assertTrue(event.alarmMessage!!.contains("设备离线"))
        assertEquals(1L, RfidPassageEvents.selectAll().count())
        assertEquals(1L, RfidAnomalies.selectAll().count())
    }

    @Test
    fun `offline alarm rejection commits passage evidence through actual Spring transactions`() {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url = "jdbc:sqlite:${temporary.resolve("offline.db")}" })
        val tx = TransactionTemplate(manager)
        fun <T : Any> transactional(target: T): T {
            @Suppress("UNCHECKED_CAST")
            return ProxyFactory(target).apply {
                isProxyTargetClass = true
                addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
            }.proxy as T
        }
        val deviceService = transactional(MuseumRfidDeviceService(MuseumScopeResolver()))
        val fixture = Fixture(CollectionRfidDeviceCommandAdapter(deviceService))
        val service = transactional(fixture.service)
        tx.executeWithoutResult {
            createSchema()
            SchemaUtils.create(MuseumRfidDeviceProfiles, MuseumDeviceConnections)
            fixture.seed()
            MuseumRfidDeviceProfiles.insert {
                it[id] = "GATE-PROFILE"; it[orgId] = "ORG"; it[deviceId] = "GATE-1"
                it[role] = EnumRfidDeviceRole.WAREHOUSE_GATE
            }
        }
        val event = service.recordEvent(fixture.event("OFFLINE-ALARM"))
        assertEquals(EnumRfidAlarmStatus.FAILED, event.alarmStatus)
        assertTrue(event.alarmMessage!!.contains("未在线"))
        tx.executeWithoutResult {
            assertEquals(1L, RfidPassageEvents.selectAll().count())
            assertEquals(1L, RfidAnomalies.selectAll().count())
            assertEquals(2L, RfidRealtimeEvents.selectAll().count())
        }
    }

    @Test
    fun `late facts are retained without emitting a live physical alarm`() = fixture {
        val event = service.recordEvent(event("LATE").copy(occurredAt = System.currentTimeMillis() - 600_000))
        assertEquals(EnumRfidPassageDecision.LATE, event.decision)
        assertNotNull(event.anomalyId)
        assertEquals(0, port.alarms.size)
    }

    private fun fixture(block: Fixture.() -> Unit) {
        val db = Database.connect("jdbc:sqlite:file:rfid-passage-${UUID.randomUUID()}?mode=memory&cache=shared", driver = "org.sqlite.JDBC")
        transaction(db) {
            // 刻意不创建工作站、用途绑定或手工授权表，证明新门禁链路不依赖它们。
            createSchema()
            Fixture().apply { seed(); block() }
        }
    }

    private fun createSchema() = SchemaUtils.create(MuseumDevices, Collections, RfidTags, RfidScanSessions, RfidScanExpectedItems,
        RfidScanReads, Loans, Requisitions, CollectionReturns, RfidPassageEvents, RfidAnomalies,
        RfidAnomalyTransitions, RfidRealtimeEvents)

    private class Fixture(commandPort: RfidDeviceCommandPort? = null) {
        val port = RecordingCommandPort()
        private val scope = CollectionScopeResolver("single", "ORG")
        val service = RfidPassageService(scope, commandPort ?: port, RfidRealtimeEventService(scope))
        private val completedAt = LocalDateTime.now().minusMinutes(1)

        fun event(source: String, device: String = "GATE-1") = RfidPassageEventCreateVo(
            deviceId = device, sourceEventId = source, epc = EPC, tid = TID, antenna = 1,
        )

        fun seed() {
            for (device in listOf("GATE-1", "GATE-2")) MuseumDevices.insert {
                it[id] = device; it[orgId] = "ORG"; it[code] = device; it[name] = device
                it[modelCode] = "CK_RFID_5A_READER_V1"; it[protocolCode] = "CK_RFID_5A_V1"
                it[gatewayId] = "GATEWAY"; it[externalDeviceId] = device
            }
            Collections.insert {
                it[id] = "COLLECTION"; it[orgId] = "ORG"; it[totalRegNo] = "TEST-1"
                it[name] = "门禁测试藏品"; it[lifeStatus] = EnumCollectionStatus.IN_STORE
            }
            RfidTags.insert {
                it[id] = "TAG"; it[orgId] = "ORG"; it[epc] = EPC; it[tid] = TID
                it[collectionId] = "COLLECTION"; it[tagStatus] = EnumRfidTagStatus.ACTIVE
            }
        }

        fun complete(type: EnumRfidScanSessionType) {
            val sourceType = when (type) {
                EnumRfidScanSessionType.LOAN_OUT -> CollectionConst.BIZ_LOAN
                EnumRfidScanSessionType.REQUISITION_OUT -> CollectionConst.BIZ_REQUISITION
                else -> CollectionConst.BIZ_RETURN
            }
            when (type) {
                EnumRfidScanSessionType.LOAN_OUT -> Loans.insert {
                    it[id] = "BUSINESS"; it[orgId] = "ORG"; it[collectionIds] = "COLLECTION"
                    it[flowStatus] = EnumFlowStatus.PASS; it[outExecuted] = true; it[outExecutedTime] = completedAt
                }
                EnumRfidScanSessionType.REQUISITION_OUT -> Requisitions.insert {
                    it[id] = "BUSINESS"; it[orgId] = "ORG"; it[collectionIds] = "COLLECTION"
                    it[flowStatus] = EnumFlowStatus.PASS; it[outExecuted] = true; it[outExecutedTime] = completedAt
                }
                else -> CollectionReturns.insert {
                    it[id] = "BUSINESS"; it[orgId] = "ORG"; it[collectionIds] = "COLLECTION"
                    it[sourceBizType] = CollectionConst.BIZ_LOAN; it[sourceBizId] = "LOAN"
                    it[flowStatus] = EnumFlowStatus.PASS; it[executionState] = EnumReturnExecutionState.EXECUTED
                    it[executedAt] = completedAt; it[acceptedCollectionIds] = "COLLECTION"
                }
            }
            Collections.update({ Collections.id eq "COLLECTION" }) {
                it[lifeStatus] = when (type) {
                    EnumRfidScanSessionType.LOAN_OUT -> EnumCollectionStatus.ON_LOAN
                    EnumRfidScanSessionType.REQUISITION_OUT -> EnumCollectionStatus.IN_USE
                    else -> EnumCollectionStatus.IN_STORE
                }
            }
            RfidScanSessions.insert {
                it[id] = "SESSION"; it[orgId] = "ORG"; it[sessionNo] = "RFID-TEST"; it[deviceId] = "DESK-READER"
                it[sessionType] = type; it[sessionState] = EnumRfidScanSessionState.FINALIZED
                it[businessType] = sourceType; it[businessId] = "BUSINESS"; it[operatorId] = "USER"
                it[finalizedAt] = completedAt
            }
            RfidScanExpectedItems.insert {
                it[id] = "EXPECTED"; it[sessionId] = "SESSION"; it[collectionId] = "COLLECTION"
                it[expectedEpc] = EPC; it[expectedTid] = TID
            }
            RfidScanReads.insert {
                it[id] = "SCAN-READ"; it[sessionId] = "SESSION"; it[epc] = EPC; it[tid] = TID
                it[direction] = EnumRfidDirection.UNKNOWN; it[firstSeenAt] = completedAt; it[lastSeenAt] = completedAt
                it[resolvedCollectionId] = "COLLECTION"; it[classification] = EnumRfidReadClassification.EXPECTED
            }
        }
    }

    private class RecordingCommandPort : RfidDeviceCommandPort {
        val alarms = mutableListOf<String>()
        var failAlarm = false
        override fun alarmOutput(deviceId: String, businessId: String, outputIndex: Int, durationMillis: Long): String {
            if (failAlarm) error("设备离线，无法提交报警")
            alarms += deviceId
            return "ALARM-${alarms.size}"
        }
        override fun supportsAnyRole(deviceId: String, orgId: String, roleCodes: Set<String>) = deviceId in setOf("GATE-1", "GATE-2") && orgId == "ORG"
        override fun startInventory(deviceId: String, sessionId: String, antennaMask: Long) = error("unexpected")
        override fun stopInventory(deviceId: String, sessionId: String) = error("unexpected")
        override fun writeEpc(deviceId: String, jobId: String, epc: String, tidFilter: String) = error("unexpected")
        override fun syncAllowlist(deviceId: String, businessId: String, epcs: List<String>) = error("unexpected")
        override fun supportsAllowlist(deviceId: String) = false
        override fun outcome(commandId: String): RfidDeviceCommandOutcome? = null
        override fun allowsDirectInput(deviceId: String) = false
    }

    private companion object {
        const val EPC = "E2801160600002094ED74AA6"
        const val TID = "E2801160200062A6DAE90929"
    }
}
