package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.LedgerReconciliationService
import io.hizhengfu.cloud.collection.service.PaperLedgerService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PaperLedgerOriginalHttpTest {
    @Test fun `paper book and finance originals validate exact archive before streaming`() {
        val books=mock(PaperLedgerService::class.java)
        val finance=mock(LedgerReconciliationService::class.java)
        val originals=mock(FileOriginalDownloadService::class.java)
        val mvc=MockMvcBuilders.standaloneSetup(PaperLedgerController(books,finance,originals))
            .addPlaceholderValue("apiPath","/api").build()

        val book=mvc.perform(get("/api/collection/org/museum/paper-ledger/book/original/book-archive/book-file"))
            .andExpect(status().isOk).andReturn().response
        val report=mvc.perform(get("/api/collection/org/museum/paper-ledger/reconciliations/report/original/report-archive/report-file"))
            .andExpect(status().isOk).andReturn().response

        verify(books).requireArchivedOriginal("museum","book","book-archive","book-file")
        verify(finance).requireArchivedOriginal("museum","report","report-archive","report-file")
        val calls=mockingDetails(originals).invocations.filter {it.method.name=="downloadFromArchive"}
        assertEquals(listOf(listOf("book-file","book-archive"),listOf("report-file","report-archive")),calls.map {it.arguments.take(2)})
        assertEquals("Content-Disposition",book.getHeader("Access-Control-Expose-Headers"))
        assertEquals("Content-Disposition",report.getHeader("Access-Control-Expose-Headers"))
    }
}
