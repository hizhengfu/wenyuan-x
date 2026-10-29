package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionCareService
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

class CollectionCareOriginalHttpTest {
    @Test fun `care original route passes museum record phase and file to the sealed archive`() {
        val service=mock(CollectionCareService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        `when`(service.archivedOriginal("piece","museum","check","CHECKED","proof")).thenReturn("sealed")
        val mvc=MockMvcBuilders.standaloneSetup(CollectionCareOriginalController(service,originals))
            .addPlaceholderValue("apiPath","/api/v1").build()
        mvc.perform(get("/api/v1/collection/care/piece/original/museum/check/CHECKED/proof"))
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Expose-Headers","Content-Disposition"))
        verify(service).archivedOriginal("piece","museum","check","CHECKED","proof")
        val download=mockingDetails(originals).invocations.single()
        assertEquals("downloadFromArchive",download.method.name)
        assertEquals(listOf("proof","sealed"),download.arguments.take(2))
        assertTrue(download.arguments[2] is HttpServletResponse)
    }
}
