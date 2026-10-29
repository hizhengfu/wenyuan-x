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

class CollectionFragilityConfirmationHttpTest {
    @ParameterizedTest @ValueSource(strings=["assessment","plan","original/sign","history","plan/original/start","record/original/monitor","monitor/original/clear","record/original/end","record/original/review","plan/original/adjustment","adjustment/original/sign/KEEPER","adjustment/original/withdraw"])
    fun `all manual HTTP writes require the museum confirmed in the opened form`(path:String) {
        val service=mock(CollectionFragilityService::class.java)
        val adjustments=mock(CollectionFragileAdjustmentService::class.java)
        val monitoring=mock(CollectionFragileMonitoringService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(CollectionFragilityController(service,adjustments,monitoring)).addPlaceholderValue("apiPath","/api").build()
        val response=mvc.perform(post("/api/collection/fragility/piece/$path").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedOrgId",(response.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(service,adjustments,monitoring)
    }
}
