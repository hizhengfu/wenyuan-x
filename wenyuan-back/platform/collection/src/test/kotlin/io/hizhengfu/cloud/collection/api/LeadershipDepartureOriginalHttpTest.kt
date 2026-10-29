package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.LeadershipDepartureService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LeadershipDepartureOriginalHttpTest {
    @Test fun `selected departure archive and file are verified before streaming`() {
        val service = mock(LeadershipDepartureService::class.java)
        val originals = mock(FileOriginalDownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(LeadershipDepartureController(service, originals))
            .addPlaceholderValue("apiPath", "/api").build()

        val response = mvc.perform(get("/api/collection/org/museum/leadership-departures/original/archive/file"))
            .andExpect(status().isOk).andReturn().response

        verify(service).requireArchivedOriginal("museum", "archive", "file")
        val call = mockingDetails(originals).invocations.single { it.method.name == "downloadFromArchive" }
        assertEquals(listOf("file", "archive"), call.arguments.take(2))
        assertEquals("Content-Disposition", response.getHeader("Access-Control-Expose-Headers"))
    }
}
