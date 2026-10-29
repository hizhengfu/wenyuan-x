package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.MissingServletRequestParameterException

class CollectionJointSafetyConfirmationHttpTest {
    @org.junit.jupiter.api.Test
    fun `early return write requires confirmed museum`() {
        val service=mock(RequisitionRecallService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(RequisitionRecallController(service)).addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(post("/api/collection/joint-safety/req/recall").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedOrgId",(response.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(service)
    }
    @ParameterizedTest @ValueSource(strings=["plan","plan/original/KEEPER","round","round/original/sign/KEEPER","round/original/report","round/original/director","custody","custody/original/KEEPER","round/original/report-handover","round/original/report-handover/record/KEEPER","round/original/report-current"])
    fun `all manual HTTP writes require confirmed museum`(path:String) {
        val service=mock(CollectionJointSafetyService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(CollectionJointSafetyController(service)).addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(post("/api/collection/joint-safety/req/$path").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedOrgId",(response.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(service)
    }
}
