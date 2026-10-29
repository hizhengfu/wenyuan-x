package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RequisitionInsertContractTest {

    @Test
    fun `new requisition initializes non-null collection ids before exposed insert`() {
        val source = Files.readString(
            projectRoot().resolve("src/main/kotlin/io/hizhengfu/cloud/collection/service/RequisitionService.kt"),
        )
        val createEntityBlock = Regex(
            """createEntity\s*=\s*\{\s*dao\.new\s*\{([\s\S]*?)\}\s*\}""",
        ).find(source)?.groupValues?.get(1).orEmpty()

        assertTrue(
            createEntityBlock.contains("collectionIds = persistedCollectionIds"),
            "Requisition collectionIds must be assigned inside dao.new before Exposed executes INSERT",
        )
    }

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(
            userDir,
            userDir.resolve("module/collection"),
        ).first { Files.isDirectory(it.resolve("src/main/kotlin/io/hizhengfu/cloud/collection")) }
    }
}
