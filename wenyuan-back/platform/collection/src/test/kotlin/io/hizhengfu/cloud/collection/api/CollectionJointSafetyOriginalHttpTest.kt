package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionJointSafetyOriginalService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CollectionJointSafetyOriginalHttpTest {
    @Test fun `record and sealed phase select the archive used by original download`() {
        val service=mock(CollectionJointSafetyOriginalService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        `when`(service.requireArchive("requisition","round","DIRECTOR_REPORT","proof")).thenReturn("sealed")
        val mvc=MockMvcBuilders.standaloneSetup(CollectionJointSafetyOriginalController(service,originals))
            .addPlaceholderValue("apiPath","/api/v1").build()
        mvc.perform(get("/api/v1/collection/joint-safety/requisition/original/round/DIRECTOR_REPORT/proof"))
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Expose-Headers","Content-Disposition"))
        verify(service).requireArchive("requisition","round","DIRECTOR_REPORT","proof")
        val download=mockingDetails(originals).invocations.single()
        assertEquals("downloadFromArchive",download.method.name)
        assertEquals(listOf("proof","sealed"),download.arguments.take(2))
        assertTrue(download.arguments[2] is HttpServletResponse)
    }
}
