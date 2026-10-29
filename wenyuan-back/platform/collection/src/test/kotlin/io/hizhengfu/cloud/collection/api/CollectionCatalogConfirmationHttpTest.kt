package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionFlowImporter
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

class CollectionCatalogConfirmationHttpTest {
    @ParameterizedTest @ValueSource(strings=["save", "save-submit", "submit", "review", "legacy"])
    fun `HTTP catalog mutations require the preview hash before any service call`(entry: String) {
        val catalog=mock(CollectionCatalogService::class.java);val main=mock(CollectionService::class.java)
        val controller=CollectionController(main,mock(CollectionImportService::class.java),mock(CollectionFileService::class.java),
            mock(CollectionFlowImporter::class.java),mock(CollectionDashboardService::class.java),mock(AnnualReportArchiveService::class.java),
            mock(AnnualReportEvidenceService::class.java),mock(CollectionUnifiedSearchService::class.java))
        val mvc=MockMvcBuilders.standaloneSetup(controller,CollectionCatalogController(catalog)).addPlaceholderValue("apiPath","/api").build()
        val request=when(entry) {
            "save" -> put("/api/collection/catalog/item")
            "legacy" -> post("/api/collection/item/catalog-audit")
            else -> post("/api/collection/catalog/item/$entry")
        }.contentType(MediaType.APPLICATION_JSON).content(if(entry in setOf("review","legacy")) """{"status":"REJECTED","opinion":"补充"}""" else "{}")
        val result=mvc.perform(request).andExpect(status().isBadRequest).andReturn()
        assertEquals("expectedStateHash",(result.resolvedException as MissingServletRequestParameterException).parameterName)
        verifyNoInteractions(catalog,main)
    }
}
