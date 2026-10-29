package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionIncidentService
import io.hizhengfu.cloud.collection.service.IncidentRelocationService
import io.hizhengfu.cloud.collection.service.IncidentWorkService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class IncidentOriginalHttpTest {
    private val incidents = mock(CollectionIncidentService::class.java)
    private val works = mock(IncidentWorkService::class.java)
    private val relocations = mock(IncidentRelocationService::class.java)
    private val originals = mock(FileOriginalDownloadService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(IncidentOriginalController(incidents, works, relocations, originals))
        .addPlaceholderValue("apiPath", "/api").build()

    @Test fun `incident route checks selected case archive before streaming`() {
        val response = mvc.perform(get("/api/collection/incident/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(incidents).requireArchivedOriginal("case", "archive", "file")
        assertStream(response)
    }
    @Test fun `work route checks selected case archive before streaming`() {
        val response = mvc.perform(get("/api/collection/incident-work/work/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(works).requireArchivedOriginal("work", "archive", "file")
        assertStream(response)
    }
    @Test fun `relocation route checks selected case archive before streaming`() {
        val response = mvc.perform(get("/api/collection/incident-relocation/move/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(relocations).requireArchivedOriginal("move", "archive", "file")
        assertStream(response)
    }
    private fun assertStream(response: org.springframework.mock.web.MockHttpServletResponse) {
        val call = mockingDetails(originals).invocations.single { it.method.name == "downloadFromArchive" }
        assertEquals(listOf("file", "archive"), call.arguments.take(2))
        assertEquals("Content-Disposition", response.getHeader("Access-Control-Expose-Headers"))
    }
}
