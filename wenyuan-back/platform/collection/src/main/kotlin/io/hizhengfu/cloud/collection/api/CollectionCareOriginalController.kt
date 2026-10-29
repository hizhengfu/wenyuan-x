package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionCareService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/care/{collectionId}")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class CollectionCareOriginalController(
    private val service: CollectionCareService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("original/{orgId}/{recordId}/{phase}/{fileId}")
    @Log("查阅日常检查养护指定阶段永久原件")
    fun original(
        @PathVariable collectionId: String,
        @PathVariable orgId: String,
        @PathVariable recordId: String,
        @PathVariable phase: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        val archiveId = service.archivedOriginal(collectionId, orgId, recordId, phase, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
