package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionSamplingService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CollectionSamplingOriginalHttpTest {
    @Test fun `sampling original route checks selected case and archive before streaming`() {
        val service=mock(CollectionSamplingService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(CollectionSamplingController(service,originals))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/sampling/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(service).requireOriginal("case","archive","file")
        val call=mockingDetails(originals).invocations.single { it.method.name=="downloadFromArchive" }
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertTrue(call.arguments[2] is HttpServletResponse)
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
