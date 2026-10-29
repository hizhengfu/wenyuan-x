package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.EnumDisclosureSubject
import io.hizhengfu.cloud.collection.service.CollectionDisclosureService
import io.hizhengfu.cloud.collection.service.CollectionStaffEducationService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CollectionPeopleDisclosureOriginalHttpTest {
    private val education=mock(CollectionStaffEducationService::class.java)
    private val disclosure=mock(CollectionDisclosureService::class.java)
    private val originals=mock(FileOriginalDownloadService::class.java)
    private val mvc=MockMvcBuilders.standaloneSetup(CollectionPeopleDisclosureOriginalController(education,disclosure,originals))
        .addPlaceholderValue("apiPath","/api").build()

    @Test fun `education route validates case before streaming`() {
        val response=mvc.perform(get("/api/collection/staff-education/case/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(education).requireArchivedOriginal("case","archive","file")
        assertStream(response)
    }

    @Test fun `disclosure route validates object and review before streaming`() {
        val response=mvc.perform(get("/api/collection/disclosure/museum/RESOURCE/one/record/review/original/archive/file"))
            .andExpect(status().isOk).andReturn().response
        verify(disclosure).requireArchivedOriginal("museum",EnumDisclosureSubject.RESOURCE,"one","review","archive","file")
        assertStream(response)
    }

    private fun assertStream(response:org.springframework.mock.web.MockHttpServletResponse) {
        val call=mockingDetails(originals).invocations.single {it.method.name=="downloadFromArchive"}
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
}
