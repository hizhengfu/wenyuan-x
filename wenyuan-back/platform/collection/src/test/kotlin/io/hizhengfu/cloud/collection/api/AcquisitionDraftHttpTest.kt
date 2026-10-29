package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.service.*
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class AcquisitionDraftHttpTest {
    @Test fun `intention draft needs a name but not final attributes and still rejects invalid provided quantity`() {
        val service=mock(AcquisitionService::class.java)
        `when`(service.save(AcquisitionVo(name="拟征集项目"))).thenReturn("draft")
        val validator=LocalValidatorFactoryBean().apply {afterPropertiesSet()}
        try {
            val mvc=MockMvcBuilders.standaloneSetup(AcquisitionController(service,mock(CollectionPrintService::class.java),
                mock(AcquisitionComplianceService::class.java),mock(DonationRecognitionService::class.java),mock(FileOriginalDownloadService::class.java)))
                .setValidator(validator).setMessageConverters(JacksonJsonHttpMessageConverter(JsonUtils.apiMapper))
                .addPlaceholderValue("apiPath","/api").build()
            mvc.perform(post("/api/collection/acquisition/save").contentType(MediaType.APPLICATION_JSON).content("""{"name":"拟征集项目"}"""))
                .andExpect(status().isOk)
            verify(service).save(AcquisitionVo(name="拟征集项目"))
            mvc.perform(post("/api/collection/acquisition/save").contentType(MediaType.APPLICATION_JSON).content("""{"name":" "}"""))
                .andExpect(status().isBadRequest)
            mvc.perform(post("/api/collection/acquisition/save").contentType(MediaType.APPLICATION_JSON).content("""{"name":"意向","quantity":-1}"""))
                .andExpect(status().isBadRequest)
            verifyNoMoreInteractions(service)
        } finally {validator.close()}
    }
}
