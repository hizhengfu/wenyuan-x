package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.MissingServletRequestParameterException

class ReturnResponsibilityHttpTest {
    @ParameterizedTest @ValueSource(strings=["responsibility","responsibility/record/KEEPER","resolve","read"])
    fun `return responsibility and resolution HTTP actions require original museum`(path:String) {
        val responsibility=mock(ReturnResponsibilityService::class.java);val service=mock(CollectionReturnService::class.java);val statutory=mock(ReturnStatutoryService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(ReturnResponsibilityController(responsibility),CollectionReturnController(service,statutory,mock(io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService::class.java))).addPlaceholderValue("apiPath","/api").build()
        val request=if(path=="read")get("/api/collection/return/original/responsibility")else post("/api/collection/return/original/$path").contentType(MediaType.APPLICATION_JSON).content("{}")
        val response=mvc.perform(request).andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedOrgId",(response.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(responsibility,service,statutory)
    }
}
