package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.service.FlowRecordPolicy
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionFlowDeleteContractTest {

    @Test
    fun `flow business records cannot be deleted while in process`() {
        val root = projectRoot()
        val exhibitionService =
            Files.readString(root.resolve("src/main/kotlin/io/hizhengfu/cloud/collection/service/ExhibitionService.kt"))
        val exhibitionController =
            Files.readString(root.resolve("src/main/kotlin/io/hizhengfu/cloud/collection/api/ExhibitionController.kt"))

        val violations = buildList {
            if (!exhibitionService.contains("requireFlowDeletable(entity, \"展览项目\")")) {
                add("ExhibitionService.deleteChecked must guard deletion through FlowService")
            }
            if (!exhibitionController.contains("@PostMapping(\"{id}/void\")") || !exhibitionController.contains("super.void(id)")) {
                add("ExhibitionController must expose void endpoint through FlowController before deletion")
            }
            listOf(
                "\"title\" to entity.title",
                "\"exhibitionNo\" to entity.exhibitionNo",
                "\"venue\" to entity.venue",
                "\"organizer\" to entity.organizer",
                "\"collectionIds\" to entity.collectionIds",
                "\"coverImage\" to entity.coverImage",
                "\"attachmentIds\" to entity.attachmentIds",
                "\"startDate\" to entity.startDate",
                "\"endDate\" to entity.endDate",
                "\"state\" to entity.state",
                "\"remark\" to entity.remark",
            ).forEach { fragment ->
                if (!exhibitionService.contains(fragment)) {
                    add("ExhibitionService.buildFormData must include $fragment for FlowFormDrawer")
                }
            }
        }

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `deletion policy only allows records outside active flow`() {
        listOf(EnumFlowStatus.DRAFT, EnumFlowStatus.VOID).forEach { status ->
            assertDoesNotThrow { FlowRecordPolicy.requireDeletable(status, "展览项目") }
        }

        listOf(
            EnumFlowStatus.AUDIT,
            EnumFlowStatus.REVIEWED,
            EnumFlowStatus.PASS,
            EnumFlowStatus.REJECT,
            EnumFlowStatus.CANCEL,
            EnumFlowStatus.COMPLETE,
            null,
        ).forEach { status ->
            assertThrows(BadRequestException::class.java) {
                FlowRecordPolicy.requireDeletable(status, "展览项目")
            }
        }
    }

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(
            userDir,
            userDir.resolve("module/collection"),
        ).first { Files.isDirectory(it.resolve("src/main/kotlin/io/hizhengfu/cloud/collection")) }
    }
}
