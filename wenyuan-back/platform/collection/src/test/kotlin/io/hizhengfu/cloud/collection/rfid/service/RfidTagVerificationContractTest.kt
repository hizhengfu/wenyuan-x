package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.collection.rfid.domain.RfidTagJobVerifyVo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RfidTagVerificationContractTest {

    @Test
    fun `tag activation accepts a scan evidence id instead of browser supplied epc and tid`() {
        assertEquals("SESSION-1", RfidTagJobVerifyVo("SESSION-1").scanSessionId)
        assertEquals(listOf("scanSessionId"), RfidTagJobVerifyVo::class.java.declaredFields.map { it.name })

        val source = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidTagService.kt")
        assertTrue(source.contains("EnumRfidScanSessionType.TAG_VERIFY"))
        assertTrue(source.contains("scanSession[RfidScanSessions.businessId] != id"))
        assertTrue(source.contains("scanSession[RfidScanSessions.directInput]"))
        assertTrue(source.contains("写后读回尚未停止或仍在收敛"))
        assertFalse(source.contains("RfidHex.normalize(vo.epc,"))
        assertFalse(source.contains("RfidHex.normalize(vo.tid,"))
    }

    @Test
    fun `fixed reader evidence cannot be posted through the authenticated handheld endpoint`() {
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidScanSessionService.kt")
        val callback = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidIngestCallbackController.kt")

        assertTrue(service.contains("fun recordRead(id: String, vo: RfidHandheldReadVo): RfidScanPreviewVo = recordReadInternal(id, vo, directInput = true)"))
        assertTrue(service.contains("fun recordDeviceRead(id: String, vo: RfidHandheldReadVo): RfidScanPreviewVo = recordReadInternal(id, vo, directInput = false)"))
        assertTrue(callback.contains("scanService.recordDeviceRead(sessionId, vo)"))
    }

    @Test
    fun `tag writer can poll its write job without a separate registry read permission`() {
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidTagController.kt")
        val jobLookup = controller.substringAfter("@GetMapping(\"tag-jobs/{id}\")").substringBefore("fun job(")

        assertTrue(jobLookup.contains("CollectionConst.RFID_TAG_SELECT"))
        assertTrue(jobLookup.contains("CollectionConst.RFID_TAG_WRITE"))
    }

    @Test
    fun `tag job idempotency cannot return another operator or another request`() {
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidTagService.kt")

        assertTrue(service.contains("clientRequestId 已用于其他发标请求"))
        assertTrue(service.contains("existing[RfidTagJobs.createBy] == currentOperatorId"))
        assertTrue(service.contains("existing[RfidTagJobs.collectionId] == collectionId"))
        assertTrue(service.contains("existing[RfidTagJobs.workstationId] == workstationId"))
    }

    private fun read(relative: String) = Files.readString(projectRoot().resolve(relative))

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return generateSequence(userDir) { it.parent }.take(4).toList()
            .first { Files.isDirectory(it.resolve("module/collection")) }
    }
}
