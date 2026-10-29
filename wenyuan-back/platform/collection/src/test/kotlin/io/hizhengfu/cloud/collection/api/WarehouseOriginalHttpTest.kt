package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.WarehouseDiaryService
import io.hizhengfu.cloud.collection.service.WarehouseEntryApplicationService
import io.hizhengfu.cloud.collection.service.WarehouseEntryTimelineService
import io.hizhengfu.cloud.collection.service.WarehouseStaffingService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WarehouseOriginalHttpTest {
    private val diaries=mock(WarehouseDiaryService::class.java)
    private val entries=mock(WarehouseEntryApplicationService::class.java)
    private val timelines=mock(WarehouseEntryTimelineService::class.java)
    private val staffing=mock(WarehouseStaffingService::class.java)
    private val originals=mock(FileOriginalDownloadService::class.java)
    private val mvc=MockMvcBuilders.standaloneSetup(WarehouseOriginalController(diaries,entries,timelines,staffing,originals))
        .addPlaceholderValue("apiPath","/api").build()

    @Test fun `diary archive route validates journal before streaming`() {
        val response=mvc.perform(get("/api/collection/warehouse-log/diary/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(diaries).requireArchivedOriginal("diary","archive","file")
        assertStream(response)
    }

    @Test fun `timeline archive route validates review before streaming`() {
        val response=mvc.perform(get("/api/collection/warehouse-entry/visit/timeline/review/archive/original/file"))
            .andExpect(status().isOk).andReturn().response
        verify(timelines).requireArchivedOriginal("visit","archive","file")
        assertStream(response)
    }

    @Test fun `entry archive route validates selected application and file before streaming`() {
        val response=mvc.perform(get("/api/collection/warehouse-entry/visit/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(entries).requireArchivedOriginal("visit","archive","file")
        assertStream(response)
    }

    @Test fun `staffing archive route validates selected warehouse and file before streaming`() {
        val response=mvc.perform(get("/api/collection/warehouse/store/staffing/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(staffing).requireArchivedOriginal("store","archive","file")
        assertStream(response)
    }

    private fun assertStream(response:org.springframework.mock.web.MockHttpServletResponse) {
        val call=mockingDetails(originals).invocations.single {it.method.name=="downloadFromArchive"}
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
