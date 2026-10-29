package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionIncidentService
import io.hizhengfu.cloud.collection.service.IncidentRelocationService
import io.hizhengfu.cloud.collection.service.IncidentWorkService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_INCIDENT_MANAGE')")
class IncidentOriginalController(
    private val incidents: CollectionIncidentService,
    private val works: IncidentWorkService,
    private val relocations: IncidentRelocationService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("incident/{id}/original/{archiveId}/{fileId}")
    @Log("查阅事故案卷指定永久原件")
    fun incident(@PathVariable id: String, @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        incidents.requireArchivedOriginal(id, archiveId, fileId)
        download(archiveId, fileId, response)
    }

    @GetMapping("incident-work/{id}/original/{archiveId}/{fileId}")
    @Log("查阅事故防护指定永久原件")
    fun work(@PathVariable id: String, @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        works.requireArchivedOriginal(id, archiveId, fileId)
        download(archiveId, fileId, response)
    }

    @GetMapping("incident-relocation/{id}/original/{archiveId}/{fileId}")
    @Log("查阅事故搬移指定永久原件")
    fun relocation(@PathVariable id: String, @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        relocations.requireArchivedOriginal(id, archiveId, fileId)
        download(archiveId, fileId, response)
    }

    private fun download(archiveId: String, fileId: String, response: HttpServletResponse) {
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
