package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.WarehouseSecurityDutyService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import jakarta.servlet.http.HttpServletResponse
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService

@RestController
@RequestMapping("\${apiPath}/collection/warehouse/{id}/security-duty")
@PreAuthorize("isAuthenticated()")
class WarehouseSecurityDutyController(private val service: WarehouseSecurityDutyService, private val originals: FileOriginalDownloadService) {
    @GetMapping("original/{fileId}")
    @Log("查阅本库区值班归档原件")
    fun original(@PathVariable id: String, @PathVariable fileId: String, response: HttpServletResponse) {
        service.requireOriginal(id, fileId)
        // 下载器在独立事务内再次核对所有来源的当前读取资格和原始字节。
        originals.download(fileId, response)
    }
    @GetMapping
    fun get(@PathVariable id: String, @RequestParam day: LocalDate) = ApiData(data = service.get(id, day))
    @PostMapping
    @Log("登记库区安全值班排班")
    fun schedule(@PathVariable id: String, @RequestBody value: WarehouseSecurityShiftRequest) = ApiData(data = service.schedule(id, value))
    @PostMapping("{shiftId}/cancel")
    @Log("取消库区安全值班排班")
    fun cancel(@PathVariable id: String, @PathVariable shiftId: String, @RequestBody value: WarehouseSecurityCancellationRequest) = ApiData(data = service.cancel(id, shiftId, value))
    @PostMapping("{shiftId}/attendance")
    @Log("本人登记库区实际值守")
    fun attend(@PathVariable id: String, @PathVariable shiftId: String, @RequestBody value: WarehouseSecurityAttendanceRequest) = ApiData(data = service.attend(id, shiftId, value))
    @PostMapping("{shiftId}/handover")
    @Log("本人核对库区值班交接")
    fun handover(@PathVariable id: String, @PathVariable shiftId: String, @RequestBody value: WarehouseSecurityHandoverRequest) = ApiData(data = service.handover(id, shiftId, value))
}
