package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAnomalyType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RfidPassageCompletionContractTest {
    @Test
    fun `alarm callback and passage authorization lifecycle are closed`() {
        val callbackClient = read("platform/museum-ingest/src/main/kotlin/io/hizhengfu/cloud/museum/ingest/connector/rfid/RfidCollectionCallbackClient.kt")
        val callbackController = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidIngestCallbackController.kt")
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidPassageController.kt")
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidPassageService.kt")

        assertTrue(callbackClient.contains("EnumDeviceCommandType.ALARM_OUTPUT -> \"alarm-completed\""))
        assertTrue(callbackController.contains("commands/{commandId}/alarm-completed"))
        assertTrue(controller.contains("grants/{id}/revoke"))
        assertTrue(service.contains("fun refreshGrantStatuses()"))
        assertTrue(service.contains("EnumRfidPassageGrantStatus.REVOKED"))
        assertTrue(EnumRfidAnomalyType.entries.containsAll(listOf(
            EnumRfidAnomalyType.EXPIRED_GRANT,
            EnumRfidAnomalyType.WRONG_DIRECTION,
            EnumRfidAnomalyType.DUPLICATE_PASSAGE,
            EnumRfidAnomalyType.STATE_MISMATCH,
        )))
    }

    @Test
    fun `passage list endpoints use server pagination and scope realtime`() {
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidPassageService.kt")
        val realtime = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidRealtimeEventService.kt")
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidRealtimeController.kt")

        assertTrue(service.contains("fun grants(request: HttpServletRequest): GenericPage<RfidPassageGrantVo>"))
        assertTrue(service.contains("fun events(request: HttpServletRequest): GenericPage<RfidPassageEventVo>"))
        assertTrue(service.contains("fun anomalies(request: HttpServletRequest): GenericPage<RfidAnomalyVo>"))
        assertTrue(service.contains("private fun Query.pageRows("))
        assertFalse(service.contains("limit(500)"))
        assertTrue(realtime.contains("fun subscribeScope("))
        assertTrue(controller.contains("@GetMapping(\"scope-events\""))
    }

    private fun read(relative: String) = Files.readString(projectRoot().resolve(relative))

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return generateSequence(userDir) { it.parent }.take(4).toList()
            .first { Files.isDirectory(it.resolve("module/collection")) }
    }
}
