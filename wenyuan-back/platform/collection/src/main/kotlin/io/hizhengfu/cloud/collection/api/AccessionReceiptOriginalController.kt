package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.AccessionReceiptService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/entry-voucher")
class AccessionReceiptOriginalController(
    private val receipts:AccessionReceiptService,
    private val originals:FileOriginalDownloadService,
) {
    @GetMapping("{voucherId}/receipts/{recordId}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_STORAGE')")
    @Log("查阅指定入馆核收版本归档原件")
    fun archivedOriginal(
        @PathVariable voucherId:String,
        @PathVariable recordId:String,
        @PathVariable archiveId:String,
        @PathVariable fileId:String,
        response:HttpServletResponse,
    ) {
        receipts.requireArchivedOriginal(voucherId,recordId,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
}
