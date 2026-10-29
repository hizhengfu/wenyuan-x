package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.WarehouseStaffingHandoverService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WarehouseStaffingHandoverOriginalHttpTest {
    @Test fun `selected handover archive and file are checked before streaming`() {
        val service = mock(WarehouseStaffingHandoverService::class.java)
        val originals = mock(FileOriginalDownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(WarehouseStaffingHandoverController(service, originals))
            .addPlaceholderValue("apiPath", "/api").build()

        val response = mvc.perform(get("/api/collection/warehouse/store/staffing-handovers/original/archive/file"))
            .andExpect(status().isOk).andReturn().response

        verify(service).requireArchivedOriginal("store", "archive", "file")
        val call = mockingDetails(originals).invocations.single { it.method.name == "downloadFromArchive" }
        assertEquals(listOf("file", "archive"), call.arguments.take(2))
        assertEquals("Content-Disposition", response.getHeader("Access-Control-Expose-Headers"))
    }

    @Test fun `before and after assignment source route checks the selected archive before streaming`() {
        val service = mock(WarehouseStaffingHandoverService::class.java)
        val originals = mock(FileOriginalDownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(WarehouseStaffingHandoverController(service, originals))
            .addPlaceholderValue("apiPath", "/api").build()

        val response = mvc.perform(get("/api/collection/warehouse/store/staffing-handovers/original/assignment/archive/file"))
            .andExpect(status().isOk).andReturn().response

        verify(service).requireAssignmentSourceOriginal("store", "archive", "file")
        val call = mockingDetails(originals).invocations.single { it.method.name == "downloadFromArchive" }
        assertEquals(listOf("file", "archive"), call.arguments.take(2))
        assertEquals("Content-Disposition", response.getHeader("Access-Control-Expose-Headers"))
    }
}
