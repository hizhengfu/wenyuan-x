package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionManagementRulesService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/org/{orgId}/management-rules")
class CollectionManagementRulesController(
    private val service: CollectionManagementRulesService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE','COLLECTION_SELECT')")
    fun detail(@PathVariable orgId: String) = ApiData(data = service.detail(orgId))

    @GetMapping("{id}/archive/{archiveId}/original/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE','COLLECTION_SELECT')")
    @Log("查阅藏品管理制度永久归档原件")
    fun original(@PathVariable orgId: String, @PathVariable id: String,
                 @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        service.requireOriginal(orgId, id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @PostMapping
    @Log("登记实际印发的藏品管理制度及内部流程")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun register(@PathVariable orgId: String, @RequestBody value: CollectionManagementRulesVo) = ApiData(data = service.register(orgId, value))

    @PostMapping("{id}/submission")
    @Log("登记藏品管理制度实际报送凭据")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun submission(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: CollectionRulesSubmissionVo) = ApiData(data = service.submission(orgId, id, value))

    @PostMapping("{id}/filing")
    @Log("登记藏品管理制度实际备案回执")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun filing(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: CollectionRulesFilingVo) = ApiData(data = service.filing(orgId, id, value))
}
