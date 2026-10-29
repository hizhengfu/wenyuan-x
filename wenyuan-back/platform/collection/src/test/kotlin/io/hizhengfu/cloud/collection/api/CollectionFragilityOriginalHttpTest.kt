package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionFragilityOriginalService
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

class CollectionFragilityOriginalHttpTest {
    @Test fun `fragility original route passes collection museum record phase and file to sealed download`() {
        val service=mock(CollectionFragilityOriginalService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        `when`(service.requireArchive("piece","museum","record","ENVIRONMENT_VERIFIED","proof")).thenReturn("sealed")
        val mvc=MockMvcBuilders.standaloneSetup(CollectionFragilityOriginalController(service,originals))
            .addPlaceholderValue("apiPath","/api/v1").build()
        mvc.perform(get("/api/v1/collection/fragility/piece/original/museum/record/ENVIRONMENT_VERIFIED/proof"))
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Expose-Headers","Content-Disposition"))
        verify(service).requireArchive("piece","museum","record","ENVIRONMENT_VERIFIED","proof")
        val download=mockingDetails(originals).invocations.single()
        assertEquals("downloadFromArchive",download.method.name)
        assertEquals(listOf("proof","sealed"),download.arguments.take(2))
        assertTrue(download.arguments[2] is HttpServletResponse)
    }

    @Test fun `frozen loan monitoring source route downloads only the resolved original`() {
        val service=mock(CollectionFragilityOriginalService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        `when`(service.requireLoanMonitoringSource("piece","museum","adjustment","monitor","proof")).thenReturn("loan-sealed")
        val mvc=MockMvcBuilders.standaloneSetup(CollectionFragilityOriginalController(service,originals))
            .addPlaceholderValue("apiPath","/api/v1").build()
        mvc.perform(get("/api/v1/collection/fragility/piece/original/loan-monitoring/museum/adjustment/monitor/proof"))
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Expose-Headers","Content-Disposition"))
        verify(service).requireLoanMonitoringSource("piece","museum","adjustment","monitor","proof")
        val download=mockingDetails(originals).invocations.single()
        assertEquals("downloadFromArchive",download.method.name)
        assertEquals(listOf("proof","loan-sealed"),download.arguments.take(2))
        assertTrue(download.arguments[2] is HttpServletResponse)
    }
}
