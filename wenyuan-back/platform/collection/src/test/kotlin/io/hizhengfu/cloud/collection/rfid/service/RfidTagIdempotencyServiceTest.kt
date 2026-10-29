package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagJobStatus
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandOutcome
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobCreateVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class RfidTagIdempotencyServiceTest {

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `tag idempotency replay requires the original operator and request`() {
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-tag-idempotency?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val service = RfidTagService(CollectionScopeResolver("single", ""), NoopDeviceCommandPort)

        transaction(database) {
            SchemaUtils.create(Collections, RfidWorkstations, RfidTagJobs)
            insertCollection()
            insertWorkstation()
            insertTagJob()

            authenticate(ORIGINAL_OPERATOR_ID)
            val replayed = service.createJob(request())
            assertEquals(TAG_JOB_ID, replayed.id)
            assertEquals(1, RfidTagJobs.selectAll().count())

            authenticate("USER-2")
            val otherOperator = assertThrows(BadRequestException::class.java) {
                service.createJob(request())
            }
            assertTrue(otherOperator.message.orEmpty().contains("clientRequestId 已用于其他发标请求"))

            authenticate(ORIGINAL_OPERATOR_ID)
            val changedRequest = assertThrows(BadRequestException::class.java) {
                service.createJob(request().copy(targetEpc = "300833B2DDD9014000000002"))
            }
            assertTrue(changedRequest.message.orEmpty().contains("clientRequestId 已用于其他发标请求"))
            assertEquals(1, RfidTagJobs.selectAll().count())
        }
    }

    private fun request() = RfidTagJobCreateVo(
        collectionId = COLLECTION_ID,
        workstationId = WORKSTATION_ID,
        clientRequestId = CLIENT_REQUEST_ID,
    )

    private fun insertCollection() {
        Collections.insert {
            it[id] = COLLECTION_ID
            it[orgId] = ORG_ID
            it[totalRegNo] = "总登-幂等-1"
            it[name] = "幂等测试藏品"
            it[lifeStatus] = EnumCollectionStatus.IN_STORE
            it[createBy] = ORIGINAL_OPERATOR_ID
        }
    }

    private fun insertWorkstation() {
        val now = LocalDateTime.now()
        RfidWorkstations.insert {
            it[id] = WORKSTATION_ID
            it[orgId] = ORG_ID
            it[code] = "RFID-WS-TAG-IDEMPOTENCY"
            it[name] = "发标幂等测试工作站"
            it[capabilitiesJson] = "[\"TAG_ISSUE\"]"
            it[enrollmentTokenHash] = RfidHex.sha256("unused")
            it[enabled] = true
            it[createTime] = now
            it[createBy] = ORIGINAL_OPERATOR_ID
            it[updateTime] = now
            it[updateBy] = ORIGINAL_OPERATOR_ID
        }
    }

    private fun insertTagJob() {
        val now = LocalDateTime.now()
        RfidTagJobs.insert {
            it[id] = TAG_JOB_ID
            it[orgId] = ORG_ID
            it[collectionId] = COLLECTION_ID
            it[workstationId] = WORKSTATION_ID
            it[tagId] = "TAG-1"
            it[targetEpc] = "300833B2DDD9014000000001"
            it[jobStatus] = EnumRfidTagJobStatus.CREATED
            it[clientRequestId] = CLIENT_REQUEST_ID
            it[version] = 0
            it[createTime] = now
            it[createBy] = ORIGINAL_OPERATOR_ID
            it[updateTime] = now
            it[updateBy] = ORIGINAL_OPERATOR_ID
        }
    }

    private fun authenticate(userId: String) {
        val user = SecurityUser(
            id = userId,
            name = "RFID 测试操作员",
            username = userId.lowercase(),
            password = "",
            groupIds = emptySet(),
            postIds = emptySet(),
            dataType = 0,
            roles = emptySet(),
            authorities = emptyList(),
            enabled = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    private object NoopDeviceCommandPort : RfidDeviceCommandPort {
        override fun startInventory(deviceId: String, sessionId: String, antennaMask: Long) = error("not expected")
        override fun stopInventory(deviceId: String, sessionId: String) = error("not expected")
        override fun writeEpc(deviceId: String, jobId: String, epc: String, tidFilter: String) = error("not expected")
        override fun syncAllowlist(deviceId: String, businessId: String, epcs: List<String>) = error("not expected")
        override fun supportsAllowlist(deviceId: String) = false
        override fun alarmOutput(deviceId: String, businessId: String, outputIndex: Int, durationMillis: Long) =
            error("not expected")
        override fun outcome(commandId: String): RfidDeviceCommandOutcome? = null
        override fun allowsDirectInput(deviceId: String) = false
    }

    private companion object {
        const val ORG_ID = "ORG-1"
        const val COLLECTION_ID = "COLLECTION-1"
        const val WORKSTATION_ID = "WORKSTATION-1"
        const val TAG_JOB_ID = "TAG-JOB-1"
        const val CLIENT_REQUEST_ID = "CLIENT-REQUEST-TAG-1"
        const val ORIGINAL_OPERATOR_ID = "USER-1"
    }
}
