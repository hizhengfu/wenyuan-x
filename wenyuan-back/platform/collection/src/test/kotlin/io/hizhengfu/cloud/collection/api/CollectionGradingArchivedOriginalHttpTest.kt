package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionGradingService
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

class CollectionGradingArchivedOriginalHttpTest {
    @Test fun `grading stage route validates collection record and archive before original stream`() {
        val service=mock(CollectionGradingService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(CollectionGradingController(service,originals))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/grading/collection/collection/record/record/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(service).requireArchivedOriginal("collection","record","archive","file")
        val call=mockingDetails(originals).invocations.single { it.method.name=="downloadFromArchive" }
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertTrue(call.arguments[2] is HttpServletResponse)
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
    @Test fun `historical grading route checks original museum stage before streaming`() {
        val service=mock(CollectionGradingService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(CollectionGradingController(service,originals))
            .addPlaceholderValue("apiPath","/api").build()
        mvc.perform(get("/api/collection/grading/history/record/original/archive/file"))
            .andExpect(status().isOk)
        verify(service).requireHistoricalArchivedOriginal("record","archive","file")
        val call=mockingDetails(originals).invocations.single { it.method.name=="downloadFromArchive" }
        assertEquals(listOf("file","archive"),call.arguments.take(2))
    }
}
