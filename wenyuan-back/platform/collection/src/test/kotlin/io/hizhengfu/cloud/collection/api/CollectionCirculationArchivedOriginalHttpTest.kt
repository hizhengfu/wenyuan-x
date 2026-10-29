package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionReturnService
import io.hizhengfu.cloud.collection.service.LoanService
import io.hizhengfu.cloud.collection.service.LoanStatutoryService
import io.hizhengfu.cloud.collection.service.RequisitionService
import io.hizhengfu.cloud.collection.service.RequisitionStatutoryService
import io.hizhengfu.cloud.collection.service.ReturnStatutoryService
import io.hizhengfu.cloud.collection.service.CollectionPrintService
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

class CollectionCirculationArchivedOriginalHttpTest {
    @Test fun `loan archive route validates the case before streaming`() {
        val service=mock(LoanService::class.java)
        val original=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(LoanController(service,mock(CollectionPrintService::class.java),mock(LoanStatutoryService::class.java),original))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/loan/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(service).requireArchivedOriginal("case","archive","file")
        assertStream(original,response)
    }

    @Test fun `requisition archive route validates the case before streaming`() {
        val service=mock(RequisitionService::class.java)
        val original=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(RequisitionController(service,mock(CollectionPrintService::class.java),mock(RequisitionStatutoryService::class.java),original))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/requisition/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(service).requireArchivedOriginal("case","archive","file")
        assertStream(original,response)
    }

    @Test fun `return archive route validates the case before streaming`() {
        val service=mock(CollectionReturnService::class.java)
        val original=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(CollectionReturnController(service,mock(ReturnStatutoryService::class.java),original))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/return/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(service).requireArchivedOriginal("case","archive","file")
        assertStream(original,response)
    }

    private fun assertStream(original:FileOriginalDownloadService,response:org.springframework.mock.web.MockHttpServletResponse) {
        val call=mockingDetails(original).invocations.single {it.method.name=="downloadFromArchive"}
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertTrue(call.arguments[2] is HttpServletResponse)
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
