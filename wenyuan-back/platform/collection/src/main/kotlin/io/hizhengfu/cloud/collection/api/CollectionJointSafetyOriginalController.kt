package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionJointSafetyOriginalService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/joint-safety")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CARE_MANAGE')")
class CollectionJointSafetyOriginalController(
    private val service: CollectionJointSafetyOriginalService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("{id}/original/{recordId}/{phase}/{fileId}")
    @Log("查阅共同安全检查指定阶段永久原件")
    fun original(
        @PathVariable id: String,
        @PathVariable recordId: String,
        @PathVariable phase: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        val archiveId = service.requireArchive(id, recordId, phase, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
