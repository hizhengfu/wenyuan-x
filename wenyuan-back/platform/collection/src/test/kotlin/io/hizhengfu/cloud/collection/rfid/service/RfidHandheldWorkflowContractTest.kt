package io.hizhengfu.cloud.collection.rfid.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RfidHandheldWorkflowContractTest {
    @Test
    fun `handheld workflow selects registered device and business task instead of typing identifiers`() {
        val domain = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/domain/RfidDomain.kt")
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidScanSessionController.kt")
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidScanSessionService.kt")
        val layout = read("clients/rfid-handheld-android/app/src/main/res/layout/activity_main.xml")
        val activity = read("clients/rfid-handheld-android/app/src/main/java/io/hizhengfu/collection/rfid/handheld/MainActivity.java")

        assertTrue(domain.contains("data class RfidHandheldWorkstationVo("))
        assertTrue(domain.substringAfter("data class RfidScanSessionCreateVo(").substringBefore(")").contains("val deviceId: String?"))
        assertTrue(controller.contains("handheld/workstations"))
        assertTrue(controller.contains("handheld/tasks"))
        assertTrue(controller.contains("handheld/active"))
        assertTrue(service.contains("fun handheldWorkstations()"))
        assertTrue(service.contains("fun handheldTasks(request: HttpServletRequest)"))
        assertTrue(service.contains("handheldDevices(workstationId).none { it.id == requestedDeviceId }"))
        assertTrue(activity.contains("api().createSession("))
        assertTrue(activity.contains("selected(device, CollectionRfidApi.HandheldDevice.class)"))
        assertFalse(layout.contains("@+id/deviceId"))
        assertFalse(layout.contains("@+id/sessionId"))
        assertFalse(layout.contains("登录 JWT"))
    }

    @Test
    fun `handheld login follows platform captcha rsa and mfa while uploads remain operator bound`() {
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidScanSessionService.kt")
        val api = read("clients/rfid-handheld-android/app/src/main/java/io/hizhengfu/collection/rfid/handheld/CollectionRfidApi.java")
        val build = read("clients/rfid-handheld-android/app/build.gradle.kts")

        assertTrue(api.contains("/auth/captcha"))
        assertTrue(api.contains("RSA/ECB/PKCS1Padding"))
        assertTrue(api.contains("/auth/mfaCode"))
        assertTrue(api.contains("verifyMfa("))
        assertTrue(service.contains("requireDirectUploadOperator(session)"))
        assertTrue(service.contains("clientRequestId 已用于其他扫描请求"))
        assertTrue(service.contains("existing[RfidScanSessions.operatorId] == currentOperatorId"))
        assertTrue(service.contains("existing[RfidScanSessions.workstationId] == workstationId"))
        assertTrue(service.contains("if (directInput && sourceDeviceId == null)"))
        assertTrue(service.contains("scope.writableOrgIds()"))
        assertTrue(build.contains("readerJniDir"))
    }

    private fun read(relative: String) = Files.readString(projectRoot().resolve(relative))

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return generateSequence(userDir) { it.parent }.take(4).toList()
            .first { Files.isDirectory(it.resolve("module/collection")) }
    }
}
