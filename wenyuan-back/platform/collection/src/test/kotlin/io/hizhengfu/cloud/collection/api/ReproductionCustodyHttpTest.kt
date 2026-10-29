package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.ReproductionOutputCustodyVo
import io.hizhengfu.cloud.collection.service.ReproductionComplianceService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class ReproductionCustodyHttpTest {
    private val service=mock(ReproductionComplianceService::class.java)
    private val at=LocalDateTime.of(2026,9,1,12,0,0,123000000)
    private val v=ReproductionOutputCustodyVo("museum","revision",at,"old","原位置","new","新位置","逐件现状","实际交接","signed")
    private fun mvc()=MockMvcBuilders.standaloneSetup(ReproductionComplianceController(service,mock(FileOriginalDownloadService::class.java)))
        .setMessageConverters(JacksonJsonHttpMessageConverter(JsonUtils.apiMapper)).addPlaceholderValue("apiPath","/api").build()
    @ParameterizedTest @ValueSource(strings=["expectedOrgId","expectedRevision","fromCustodianUserId","fromLocation"])
    fun `custody request requires original context before entering service`(missing:String) {
        val fields=mutableMapOf<String,Any>("expectedOrgId" to "museum","expectedRevision" to "revision","handedOverAt" to at,
            "fromCustodianUserId" to "old","fromLocation" to "原位置","toCustodianUserId" to "new","toLocation" to "新位置",
            "condition" to "逐件现状","reason" to "实际交接","signedFileIds" to "signed")
        fields.remove(missing)
        mvc().perform(post("/api/collection/reproduction-compliance/rep/output/out/custody")
            .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(fields)!!)).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }
    @Test fun `custody HTTP preserves original identity actual milliseconds and signed file ids`() {
        mvc().perform(post("/api/collection/reproduction-compliance/rep/output/out/custody")
            .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(v)!!)).andExpect(status().isOk)
        verify(service).transferCustody("rep","out",v)
    }
}
