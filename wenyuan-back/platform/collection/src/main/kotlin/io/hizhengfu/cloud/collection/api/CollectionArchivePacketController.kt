package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionArchivePacketService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/org/{orgId}/archives")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ARCHIVE_MANAGE','COLLECTION_ARCHIVE_SELECT')")
class CollectionArchivePacketController(
    private val service: CollectionArchivePacketService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping("collections")
    fun collections(@PathVariable orgId: String) = ApiData(data = service.collections(orgId))
    @GetMapping("jurisdiction")
    fun jurisdictions(@PathVariable orgId: String) = ApiData(data = service.jurisdictions(orgId))
    @GetMapping("original/{archiveId}/{fileId}")
    @Log("查阅藏品档案永久归档原件")
    fun original(@PathVariable orgId: String, @PathVariable archiveId: String,
                 @PathVariable fileId: String, response: HttpServletResponse) {
        service.requireOriginal(orgId, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
    @PostMapping("jurisdiction")
    @Log("核实文物档案主管归属与实际依据")
    fun jurisdiction(@PathVariable orgId: String, @RequestBody value: ArchiveJurisdictionVo) = ApiData(data = service.jurisdiction(orgId, value))
    @GetMapping("collection/{id}")
    fun detail(@PathVariable orgId: String, @PathVariable id: String) = ApiData(data = service.detail(orgId, id))
    @PostMapping("collection/{id}")
    @Log("登记实际藏品档案目录和编制核验原件")
    fun record(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: CollectionArchivePacketVo) = ApiData(data = service.record(orgId, id, value))
    @PostMapping("packet/{id}/submission")
    @Log("登记文物藏品档案实际报送凭据")
    fun submission(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: ArchivePacketSubmissionVo) = ApiData(data = service.submission(orgId, id, value))
    @PostMapping("submission/{id}/receipt")
    @Log("登记文物藏品档案实际备案回执")
    fun receipt(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: ArchivePacketReceiptVo) = ApiData(data = service.receipt(orgId, id, value))
}
