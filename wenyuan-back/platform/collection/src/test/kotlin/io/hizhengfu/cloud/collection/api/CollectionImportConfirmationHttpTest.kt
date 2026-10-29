package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionFlowImporter
import io.hizhengfu.cloud.collection.service.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.MissingServletRequestParameterException

/** HTTP must not silently fall back to internal callers' optional confirmation arguments. */
class CollectionImportConfirmationHttpTest {
    @ParameterizedTest
    @CsvSource("ledger,expectedOrgId", "ledger,expectedValidationHash", "word,expectedOrgId", "word,expectedValidationHash", "files,expectedOrgId", "files,expectedValidationHash")
    fun `missing confirmation is rejected before invoking any import service`(kind: String, missing: String) {
        val imports = mock(CollectionImportService::class.java)
        val word = mock(CollectionWordImportService::class.java)
        val files = mock(CollectionFileService::class.java)
        val controller = CollectionController(mock(CollectionService::class.java), imports,
            files, mock(CollectionFlowImporter::class.java),
            mock(CollectionDashboardService::class.java), mock(AnnualReportArchiveService::class.java),
            mock(AnnualReportEvidenceService::class.java), mock(CollectionUnifiedSearchService::class.java))
        val mvc = MockMvcBuilders.standaloneSetup(controller, CollectionWordImportController(word))
            .addPlaceholderValue("apiPath", "/api").build()
        val request = if (kind == "ledger") post("/api/collection/import/commit")
            .contentType(MediaType.APPLICATION_JSON).content("[]")
        else if (kind == "files") post("/api/collection/file-import/commit").contentType(MediaType.APPLICATION_JSON).content("[]")
        else multipart("/api/collection/word-import/commit")
            .file(MockMultipartFile("file", "original.docx", "application/octet-stream", byteArrayOf(1)))
        if (missing != "expectedOrgId") request.param("expectedOrgId", "museum")
        if (missing != "expectedValidationHash") request.param("expectedValidationHash", "a".repeat(64))
        val result = mvc.perform(request).andExpect(status().isBadRequest).andReturn()
        assertEquals(missing, (result.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(imports, word, files)
    }
}
