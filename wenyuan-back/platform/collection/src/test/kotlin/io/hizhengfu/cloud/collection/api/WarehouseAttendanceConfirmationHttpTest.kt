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

class WarehouseAttendanceConfirmationHttpTest {
    @ParameterizedTest @CsvSource("check-in,expectedOrgId","check-out,expectedOrgId","check-in,expectedRevision","check-out,expectedRevision")
    fun `HTTP attendance cannot omit either opened confirmation parameter`(path:String,missing:String) {
        val service=mock(WarehouseEntryApplicationService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(WarehouseEntryApplicationController(service)).addPlaceholderValue("apiPath","/api").build()
        val request=post("/api/collection/warehouse-entry/visit/$path").contentType(MediaType.APPLICATION_JSON)
            .content("""{"occurredAt":"2026-09-01T10:00:00","identityChecked":true,"continuousAccompaniment":true,"signedRecordFileIds":"proof"}""")
        request.param(if(missing=="expectedOrgId")"expectedRevision" else "expectedOrgId",if(missing=="expectedOrgId")"a".repeat(64) else "museum")
        val response=mvc.perform(request).andExpect(status().isBadRequest).andReturn()
        assertEquals(missing,(response.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(service)
    }
}
