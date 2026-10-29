package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.AcquisitionComplianceService
import io.hizhengfu.cloud.collection.service.AcquisitionService
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.DonationRecognitionService
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

class AcquisitionArchivedOriginalHttpTest {
    @Test fun `acquisition archive route validates confidential case stage before streaming`() {
        val compliance=mock(AcquisitionComplianceService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(AcquisitionController(mock(AcquisitionService::class.java),
            mock(CollectionPrintService::class.java),compliance,mock(DonationRecognitionService::class.java),originals))
            .addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/acquisition/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(compliance).requireArchivedOriginal("case","archive","file")
        val call=mockingDetails(originals).invocations.single { it.method.name=="downloadFromArchive" }
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertTrue(call.arguments[2] is HttpServletResponse)
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
