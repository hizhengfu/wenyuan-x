package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionReturns
import io.hizhengfu.cloud.collection.dao.EnumReturnExecutionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionReturnRfidExecutionContractTest {

    @Test
    fun `legacy rows default to executed while new return service explicitly creates pending records`() {
        assertEquals(EnumReturnExecutionState.EXECUTED, CollectionReturns.executionState.defaultValueFun?.invoke())
        val source = read("src/main/kotlin/io/hizhengfu/cloud/collection/service/CollectionReturnService.kt")
        assertTrue(source.contains("executionState = EnumReturnExecutionState.PENDING"))
    }

    @Test
    fun `approval no longer performs physical return and rfid finalize invokes idempotent execution`() {
        val returnService = read("src/main/kotlin/io/hizhengfu/cloud/collection/service/CollectionReturnService.kt")
        val callback = Regex(
            """override fun onFlowCompleted\([\s\S]*?\n    \}""",
        ).find(returnService)?.value.orEmpty()
        assertFalse(callback.contains("executeReturn(entity)"))
        assertTrue(returnService.contains("if (entity.executionState == EnumReturnExecutionState.EXECUTED) {"))
        assertTrue(returnService.contains("archives.verifyExisting(original,entity.orgId,businessType(),id,\"RECEIVED\""))

        val rfidService = read("src/main/kotlin/io/hizhengfu/cloud/collection/rfid/service/RfidScanSessionService.kt")
        assertTrue(rfidService.contains("EnumRfidScanSessionType.RETURN_IN -> returnService.executeApprovedReturn"))
        assertTrue(rfidService.contains("EnumRfidScanSessionState.FINALIZED) return sessionVo(session)"))
    }

    private fun read(relative: String) = Files.readString(projectRoot().resolve(relative))

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(userDir, userDir.resolve("module/collection"))
            .first { Files.isDirectory(it.resolve("src/main/kotlin/io/hizhengfu/cloud/collection")) }
    }
}
