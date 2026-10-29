package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.InventoryComplianceService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/inventory")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
class InventoryOriginalController(
    private val compliance: InventoryComplianceService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("batch/{id}/original/{archiveId}/{fileId}")
    @Log("查阅清点批次指定永久原件")
    fun batch(@PathVariable id: String, @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        compliance.requireBatchArchivedOriginal(id, archiveId, fileId)
        download(archiveId, fileId, response)
    }

    @GetMapping("annual/{year}/report/{id}/original/{archiveId}/{fileId}")
    @Log("查阅年度清点报告指定永久原件")
    fun annual(@PathVariable year: Int, @PathVariable id: String, @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        compliance.requireAnnualArchivedOriginal(year, id, archiveId, fileId)
        download(archiveId, fileId, response)
    }

    private fun download(archiveId: String, fileId: String, response: HttpServletResponse) {
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
