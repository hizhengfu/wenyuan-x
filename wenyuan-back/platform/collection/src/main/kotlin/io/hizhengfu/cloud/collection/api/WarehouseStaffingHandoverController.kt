package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.WarehouseStaffingHandoverService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/warehouse/{id}/staffing-handovers")
@PreAuthorize("isAuthenticated()") // 服务逐次核对当前有效账号、权限、实际任职与馆别。
class WarehouseStaffingHandoverController(
    private val service: WarehouseStaffingHandoverService,
    private val originals: FileOriginalDownloadService,
) {
    @GetMapping
    fun get(@PathVariable id: String) = ApiData(data = service.get(id))
    @PostMapping
    @Log("登记库管岗位变动与离任清点核对")
    fun register(@PathVariable id: String, @RequestBody value: WarehouseStaffingHandoverRequest) = ApiData(data = service.register(id, value))
    @PostMapping("{recordId}/confirm/{role}")
    @Log("本人确认库管岗位交接核对")
    fun confirm(@PathVariable id: String, @PathVariable recordId: String, @PathVariable role: String, @RequestBody value: CollectionCareSignatureVo) = ApiData(data = service.confirm(id, recordId, role, value))

    @GetMapping("original/{archiveId}/{fileId}")
    @Log("查阅库管离任交接指定永久原件")
    fun original(
        @PathVariable id: String,
        @PathVariable archiveId: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        service.requireArchivedOriginal(id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @GetMapping("original/assignment/{archiveId}/{fileId}")
    @Log("查阅库管离任交接前后任职签署原件")
    fun assignmentOriginal(
        @PathVariable id: String,
        @PathVariable archiveId: String,
        @PathVariable fileId: String,
        response: HttpServletResponse,
    ) {
        service.requireAssignmentSourceOriginal(id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }
}
