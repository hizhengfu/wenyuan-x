package io.hizhengfu.cloud.collection.rfid.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RfidWorkstationBindingContractTest {
    @Test
    fun `workstation uses capabilities and purpose bindings without a single device type`() {
        val table = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/dao/RfidTables.kt")
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidWorkstationService.kt")
        val scanner = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidScanSessionService.kt")
        val tag = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidTagService.kt")

        assertTrue(table.contains("val defaultCapability"))
        assertTrue(table.contains("object RfidWorkstationDeviceBindings"))
        assertFalse(table.contains("val type = enumerationByName<EnumRfidWorkstationType>"))
        assertFalse(table.contains("val cardReaderDeviceId"))
        assertTrue(service.contains("vo.deviceBindings.mapIndexed(::normalizeBinding)"))
        assertTrue(service.contains("启用工作站前请配置"))
        assertTrue(scanner.contains("resolveWorkstationDevice(station, requiredDevicePurpose(type))"))
        assertTrue(tag.contains("EnumRfidWorkstationDevicePurpose.RFID_WRITE"))
    }

    @Test
    fun `browser context is resolved only from the HttpOnly credential`() {
        val domain = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/domain/RfidDomain.kt")
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidWorkstationService.kt")
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidWorkstationController.kt")
        val enrollMethod = service.substringAfter("fun enrollBrowser(vo: RfidWorkstationEnrollVo)").substringBefore("fun browserContext(")
        val contextMethod = service.substringAfter("fun browserContext(browserCredential: String?)").substringBefore("fun cardLogin(")

        assertTrue(service.contains("fun browserContext(browserCredential: String?): RfidWorkstationContextVo?"))
        assertTrue(service.contains("RfidWorkstations.browserCredentialHash"))
        assertFalse(service.contains("fun browserContext(stationCode:"))
        assertFalse(enrollMethod.contains("RfidWorkstations.enabled eq true"))
        assertFalse(contextMethod.contains("RfidWorkstations.enabled eq true"))
        assertTrue(domain.substringAfter("data class RfidWorkstationContextVo(").substringBefore(")").contains("val enabled: Boolean"))
        assertTrue(controller.contains("@GetMapping(\"public/context\")"))
        assertTrue(controller.contains("service.browserContext(credential)"))
    }

    @Test
    fun `workstation code is generated once by backend and is immutable`() {
        val domain = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/domain/RfidDomain.kt")
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidWorkstationService.kt")
        val saveVo = domain.substringAfter("data class RfidWorkstationSaveVo(").substringBefore("data class RfidWorkstationDeviceBindingSaveVo")
        val saveMethod = service.substringAfter("fun saveWorkstation(vo: RfidWorkstationSaveVo)").substringBefore("private fun validateConfiguration")

        assertFalse(saveVo.contains("val code:"))
        assertFalse(saveMethod.contains("工作站编码必填"))
        assertTrue(saveMethod.contains("val code = generatedWorkstationCode(createdId)"))
        assertTrue(service.contains("private fun generatedWorkstationCode(id: String) = \"RFID-WS-\$id\""))
        assertFalse(saveMethod.substringAfter("RfidWorkstations.update({ RfidWorkstations.id eq id })").substringBefore("replaceBindings(id").contains("RfidWorkstations.code"))
    }

    @Test
    fun `workstation consumers can load workstation options`() {
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidWorkstationController.kt")
        val listMethod = controller.substringAfter("@GetMapping(\"workstations\")").substringBefore("fun saveWorkstation")

        assertTrue(listMethod.contains("CollectionConst.RFID_DESK"))
        assertTrue(listMethod.contains("CollectionConst.RFID_CARD_MANAGE"))
        assertTrue(listMethod.contains("CollectionConst.RFID_PASSAGE"))
    }

    @Test
    fun `fixed device mutations require the active browser window lease`() {
        val table = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/dao/RfidTables.kt")
        val workstation = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidWorkstationController.kt")
        val scanner = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidScanSessionController.kt")
        val tag = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidTagController.kt")

        assertTrue(table.contains("val activeLeaseInstanceHash"))
        assertTrue(table.contains("val activeLeaseUntil"))
        assertTrue(workstation.contains("public/lease/acquire"))
        assertTrue(workstation.contains("public/lease/heartbeat"))
        assertTrue(workstation.contains("public/lease/release"))
        assertTrue(scanner.contains("workstationService.requireBrowserLease("))
        assertTrue(scanner.contains("request.rfidWorkstationClientInstance()"))
        assertTrue(tag.contains("workstationService.requireBrowserLease("))
        assertTrue(tag.contains("request.rfidWorkstationClientInstance()"))
    }

    @Test
    fun `operator card login forwards platform token lifetime and workstation permissions`() {
        val domain = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/domain/RfidDomain.kt")
        val policy = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/domain/RfidPolicies.kt")
        val service = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidWorkstationService.kt")
        val controller = read("module/collection/src/main/kotlin/io/hizhengfu/cloud/collection/rfid/api/RfidWorkstationController.kt")

        assertTrue(domain.substringAfter("data class RfidCardLoginResultVo(").substringBefore(")").contains("expirationMillis"))
        assertTrue(service.contains("expirationMillis = issued.expirationMillis"))
        assertTrue(service.contains("RfidWorkstationAccessPolicy.requiredAuthorities(stationCapabilities)"))
        assertTrue(service.contains("fun authorizeCurrentOperator("))
        assertTrue(policy.contains("grantedAuthorities.containsAll(requiredAuthorities)"))
        assertTrue(controller.contains("@GetMapping(\"workstation-access\")"))
        assertTrue(controller.contains("service.authorizeCurrentOperator("))
    }

    private fun read(relative: String) = Files.readString(projectRoot().resolve(relative))

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return generateSequence(userDir) { it.parent }.take(4).toList()
            .first { Files.isDirectory(it.resolve("module/collection")) }
    }
}
