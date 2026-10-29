package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.AnnualReportEvidenceService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/stat/annual/reports")
class AnnualReportOriginalController(
    private val evidence: AnnualReportEvidenceService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("{year}/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    @Log("查阅年度报表指定阶段归档原件")
    fun archivedOriginal(
        @PathVariable year:Int,
        @PathVariable id:String,
        @PathVariable archiveId:String,
        @PathVariable fileId:String,
        response:HttpServletResponse,
    ) {
        evidence.requireArchivedOriginal(year,id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
}
