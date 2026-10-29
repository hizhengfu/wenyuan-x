package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.*
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.MissingServletRequestParameterException

class InstitutionOperationConfirmationHttpTest {
    @Test
    fun `institution source archive resolves a frozen file before streaming`() {
        val cases=mock(InstitutionSuccessionCaseService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        `when`(cases.requireArchivedOriginal("case","record","legacy/path")).thenReturn("archive" to "file")
        val mvc=MockMvcBuilders.standaloneSetup(InstitutionSuccessionCaseController(cases,mock(InstitutionItemHandoverService::class.java),
            mock(InstitutionItemInvestigationService::class.java),originals)).addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/institution-succession/case/original/record")
            .param("fileId","legacy/path")).andExpect(status().isOk).andReturn().response
        verify(cases).requireArchivedOriginal("case","record","legacy/path")
        val call=mockingDetails(originals).invocations.single {it.method.name=="downloadFromArchive"}
        assertEquals(listOf("file","archive"),call.arguments.take(2))
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
    }
    @Test
    fun `designated original exposes its authorized server filename to the browser`() {
        val service=mock(DesignatedReceptionService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(DesignatedReceptionController(service,originals)).addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(get("/api/collection/designated-reception/case/original/file"))
            .andExpect(status().isOk).andReturn().response
        assertEquals("Content-Disposition",response.getHeader("Access-Control-Expose-Headers"))
        verify(service).requireOriginal("case","file")
    }
    @ParameterizedTest @ValueSource(strings=["", "/case/scope", "/case/unrecorded", "/case/follow-up", "/case/resolution", "/case/keeper-review", "/case/representative-review", "/case/item-proposal", "/case/item-keeper", "/case/item-authorization", "/case/item-handover", "/case/item-investigation", "/case/item-investigation-keeper", "/case/item-investigation-authorization"])
    fun `every institution HTTP write requires original museum before parsing private body`(path:String) {
        val cases=mock(InstitutionSuccessionCaseService::class.java);val items=mock(InstitutionItemHandoverService::class.java);val investigations=mock(InstitutionItemInvestigationService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(InstitutionSuccessionCaseController(cases,items,investigations,mock(FileOriginalDownloadService::class.java))).addPlaceholderValue("apiPath","/api").build()
        val r=mvc.perform(post("/api/collection/institution-succession$path").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedOrgId",(r.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(cases,items,investigations)
    }
    @ParameterizedTest @ValueSource(strings=["", "/notice/assessment", "/notice/custody", "/notice/acceptance", "/notice/referral", "/notice/reassignment", "/notice/settlement"])
    fun `every designated HTTP write requires original museum before parsing private body`(path:String) {
        val service=mock(DesignatedReceptionService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(DesignatedReceptionController(service,mock(FileOriginalDownloadService::class.java))).addPlaceholderValue("apiPath","/api").build()
        val r=mvc.perform(post("/api/collection/designated-reception$path").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedOrgId",(r.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(service)
    }
}
