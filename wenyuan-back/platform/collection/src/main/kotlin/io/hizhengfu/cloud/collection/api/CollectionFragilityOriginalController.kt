package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionFragilityOriginalService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/fragility/{collectionId}")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class CollectionFragilityOriginalController(
    private val service: CollectionFragilityOriginalService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("original/{orgId}/{recordId}/{phase}/{fileId}")
    @Log("查阅材质脆弱性与使用保护指定阶段永久原件")
    fun original(
        @PathVariable collectionId: String,
        @PathVariable orgId: String,
        @PathVariable recordId: String,
        @PathVariable phase: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        val archiveId = service.requireArchive(collectionId, orgId, recordId, phase, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @GetMapping("original/loan-monitoring/{orgId}/{adjustmentId}/{monitoringId}/{fileId}")
    @Log("查阅使用变更核查所引用的借用监管永久原件")
    fun loanMonitoringOriginal(
        @PathVariable collectionId: String,
        @PathVariable orgId: String,
        @PathVariable adjustmentId: String,
        @PathVariable monitoringId: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        val archiveId = service.requireLoanMonitoringSource(collectionId, orgId, adjustmentId, monitoringId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
