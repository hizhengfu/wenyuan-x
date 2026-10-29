package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.InventoryComplianceService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class InventoryOriginalHttpTest {
    private val compliance = mock(InventoryComplianceService::class.java)
    private val originals = mock(FileOriginalDownloadService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(InventoryOriginalController(compliance, originals))
        .addPlaceholderValue("apiPath", "/api").build()

    @Test fun `batch route checks selected batch before streaming`() {
        val response = mvc.perform(get("/api/collection/inventory/batch/batch/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(compliance).requireBatchArchivedOriginal("batch", "archive", "file")
        assertStream(response)
    }
    @Test fun `annual route checks selected year and report before streaming`() {
        val response = mvc.perform(get("/api/collection/inventory/annual/2025/report/report/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(compliance).requireAnnualArchivedOriginal(2025, "report", "archive", "file")
        assertStream(response)
    }
    private fun assertStream(response: org.springframework.mock.web.MockHttpServletResponse) {
        val call = mockingDetails(originals).invocations.single { it.method.name == "downloadFromArchive" }
        assertEquals(listOf("file", "archive"), call.arguments.take(2))
        assertEquals("Content-Disposition", response.getHeader("Access-Control-Expose-Headers"))
    }
}
