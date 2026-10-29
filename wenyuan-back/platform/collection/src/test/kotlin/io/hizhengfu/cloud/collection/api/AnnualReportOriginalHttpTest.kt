package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.AnnualReportEvidenceService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnnualReportOriginalHttpTest {
    @Test fun `original route validates selected report before streaming archive`() {
        val evidence=mock(AnnualReportEvidenceService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(AnnualReportOriginalController(evidence,originals))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/stat/annual/reports/2025/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(evidence).requireArchivedOriginal(2025,"case","archive","file")
        val call=mockingDetails(originals).invocations.single {it.method.name=="downloadFromArchive"}
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
