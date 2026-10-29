package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.WarehouseEntryApplicationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.MissingServletRequestParameterException

class WarehouseApplicationConfirmationHttpTest {
    @ParameterizedTest @CsvSource("save,expectedOrgId","save,expectedRevision","submit,expectedOrgId","submit,expectedRevision","cancel,expectedOrgId","cancel,expectedRevision","void,expectedOrgId","void,expectedRevision")
    fun `HTTP application writes require both original museum and version`(op:String,missing:String) {
        val service=mock(WarehouseEntryApplicationService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(WarehouseEntryApplicationController(service)).addPlaceholderValue("apiPath","/api").build()
        val request=post("/api/collection/warehouse-entry/"+if(op=="save")"save" else "visit/$op")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"warehouseIds":"store","entrantIds":"a,b","purpose":"合成巡库","plannedStart":"2026-09-01T10:00:00","plannedEnd":"2026-09-01T11:00:00"}""")
        request.param(if(missing=="expectedOrgId")"expectedRevision" else "expectedOrgId",if(missing=="expectedOrgId")"a".repeat(64) else "museum")
        val response=mvc.perform(request).andExpect(status().isBadRequest).andReturn()
        assertEquals(missing,(response.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(service)
    }
}
