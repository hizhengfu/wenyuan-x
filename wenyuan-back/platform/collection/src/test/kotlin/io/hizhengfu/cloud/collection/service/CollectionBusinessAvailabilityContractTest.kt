package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionBusinessAvailabilityContractTest {

    @Test
    fun `collection business services require in-store collections before saving`() {
        val root = projectRoot()
        val services = listOf(
            "RequisitionService.kt" to "提用",
            "LoanService.kt" to "借展",
            // RestorationService 已迁移至 module-collection-restoration
            "CollectionTransferService.kt" to "调拨",
            "ExhibitionService.kt" to "展览",
            "AppraisalService.kt" to "鉴定",
            "ReproductionService.kt" to "复制拓印",
            // 退出允许已登记不可抗力灭失的 MISSING 藏品，由退出专门规则验证。
        )

        val violations = services.mapNotNull { (file, actionName) ->
            val source = Files.readString(root.resolve("src/main/kotlin/io/hizhengfu/cloud/collection/service/$file"))
            if (source.contains("CollectionOutboundPolicy.requireInStore") && source.contains("\"$actionName\"")) null
            else "$file must require IN_STORE collections for $actionName"
        }

        assertEquals(emptyList<String>(), violations)
    }

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(
            userDir,
            userDir.resolve("module/collection"),
        ).first { Files.isDirectory(it.resolve("src/main/kotlin/io/hizhengfu/cloud/collection")) }
    }
}
