package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionFlowDraftContractTest {

    @Test
    fun `inventory save is idempotent draft upsert backed by flow service`() {
        val service = Files.readString(projectRoot().resolve("src/main/kotlin/io/hizhengfu/cloud/collection/service/InventoryService.kt"))
        val controller = Files.readString(projectRoot().resolve("src/main/kotlin/io/hizhengfu/cloud/collection/api/InventoryController.kt"))

        val violations = buildList {
            if (!service.contains("fun save(vo: InventoryVo): String")) {
                add("InventoryService must expose save(vo) for draft upsert")
            }
            if (!service.contains("saveDraft(vo.id")) {
                add("InventoryService.save must delegate id handling and editability to FlowService.saveDraft")
            }
            if (service.contains("fun create(vo: InventoryVo): String") && !service.contains("fun create(vo: InventoryVo): String = save(vo)")) {
                add("InventoryService.create must not keep a dao.new-only implementation")
            }
            if (!controller.contains("inventoryService.save(vo)")) {
                add("InventoryController create endpoint must use save(vo) so existing drafts are updated")
            }
        }

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `collection flow services use shared flow edit guard for draft saves`() {
        val root = projectRoot()
        val services = listOf(
            "AcquisitionService.kt",
            "AppraisalService.kt",
            "CollectionTransferService.kt",
            "DeregistrationService.kt",
            "ExhibitionService.kt",
            "InventoryService.kt",
            "LoanService.kt",
            "ReproductionService.kt",
            "RequisitionService.kt",
            // RestorationService 已迁移至 module-collection-restoration
        )

        val violations = services.mapNotNull { fileName ->
            val source = Files.readString(root.resolve("src/main/kotlin/io/hizhengfu/cloud/collection/service/$fileName"))
            if (source.contains("dao.new") && !source.contains("saveDraft(")) {
                "$fileName must use FlowService.saveDraft for flow draft create or edit"
            } else {
                null
            }
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
