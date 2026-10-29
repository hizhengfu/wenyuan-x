package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionAuxiliaryService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CollectionAuxiliaryOriginalHttpTest {
    @Test fun `selected auxiliary registration archive is checked before streaming`() {
        val service=mock(CollectionAuxiliaryService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        `when`(service.requireArchivedOriginal("museum","record","archive","file")).thenReturn("archive")
        val mvc=MockMvcBuilders.standaloneSetup(CollectionAuxiliaryController(service,originals))
            .addPlaceholderValue("apiPath","/api").build()

        val response=mvc.perform(get("/api/collection/auxiliary/museum/record/original/archive/file"))
            .andExpect(status().isOk).andReturn().response

        verify(service).requireArchivedOriginal("museum","record","archive","file")
        val call=mockingDetails(originals).invocations.single {it.method.name=="downloadFromArchive"}
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
