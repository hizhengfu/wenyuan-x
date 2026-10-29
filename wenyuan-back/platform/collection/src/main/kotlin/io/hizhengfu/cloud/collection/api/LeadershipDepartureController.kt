package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.LeadershipDepartureService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/org/{orgId}/leadership-departures")
@PreAuthorize("isAuthenticated()") // 服务按当前账号、职责与馆别逐次核对。
class LeadershipDepartureController(
    private val service: LeadershipDepartureService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping fun get(@PathVariable orgId: String) = ApiData(data = service.get(orgId))
    @PostMapping @Log("登记负责人离任交接及任职依据")
    fun register(@PathVariable orgId: String, @RequestBody value: LeadershipDepartureRequest) = ApiData(data = service.register(orgId, value))
    @PostMapping("{id}/confirm/{stage}") @Log("本人核对负责人离任交接原件")
    fun confirm(@PathVariable orgId: String, @PathVariable id: String, @PathVariable stage: String, @RequestBody value: CollectionCareSignatureVo) = ApiData(data = service.confirm(orgId, id, stage, value))

    @GetMapping("original/{archiveId}/{fileId}")
    @Log("查阅负责人离任交接指定永久原件")
    fun original(
        @PathVariable orgId: String,
        @PathVariable archiveId: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        service.requireArchivedOriginal(orgId, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
