package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.WarehouseVo
import io.hizhengfu.cloud.collection.domain.WarehouseImportRowVo
import io.hizhengfu.cloud.collection.domain.WarehouseLocationCorrectionVo
import io.hizhengfu.cloud.collection.service.WarehouseService
import io.hizhengfu.cloud.collection.service.WarehouseStaffingService
import io.hizhengfu.cloud.collection.domain.WarehouseStaffingVo
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 库房库位。 */
@RestController
@RequestMapping("\${apiPath}/collection/warehouse")
class WarehouseController(private val warehouseService: WarehouseService, private val staffing: WarehouseStaffingService,
                          private val originals: FileOriginalDownloadService) {

    @GetMapping("{id}/staffing")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_SELECT','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    fun staffing(@PathVariable id: String): ApiData = ApiData(data=staffing.get(id))

    @PostMapping("{id}/staffing")
    @Log("登记库房实际岗位及任职签署")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun saveStaffing(@PathVariable id: String,@RequestBody vo: WarehouseStaffingVo): ApiData = ApiData(data=staffing.save(id,vo))

    @GetMapping("tree")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    fun tree(): ApiData = ApiData(data = warehouseService.findAll())

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    fun list(): ApiData = ApiData(data = warehouseService.findAll())

    @GetMapping("location-check")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_SELECT')")
    fun locationCheck(): ApiData = ApiData(data = warehouseService.checkLocations())

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = warehouseService.get(id))

    @Log("库位创建")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun create(@RequestBody @Validated vo: WarehouseVo): ApiData =
        ApiData(data = warehouseService.create(vo))

    @GetMapping("import/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_SELECT')")
    fun importTemplate(): ApiData = ApiData(data = warehouseService.importTemplate())

    @PostMapping("import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun validateImport(@RequestBody rows: List<WarehouseImportRowVo>): ApiData =
        ApiData(data = warehouseService.validateImport(rows))

    @Log("批量导入库房库位")
    @PostMapping("import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun commitImport(@RequestBody rows: List<WarehouseImportRowVo>): ApiData =
        ApiData(data = warehouseService.commitImport(rows))

    @Log("库位更新")
    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun update(@PathVariable id: String, @RequestBody @Validated vo: WarehouseVo): ApiData =
        ApiData(data = warehouseService.update(id, vo))

    @Log("误分类库房退役并登记新展厅")
    @PostMapping("{id}/retire-as-exhibition")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun retireAsExhibition(@PathVariable id: String, @RequestBody vo: WarehouseLocationCorrectionVo): ApiData =
        ApiData(data = warehouseService.retireAsExhibition(id, vo))

    @GetMapping("{id}/corrections")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_SELECT')")
    fun correctionHistory(@PathVariable id: String): ApiData = ApiData(data = warehouseService.correctionHistory(id))

    @GetMapping("{id}/corrections/original/{archiveId}/{fileId}")
    @Log("查阅位置订正指定永久原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_SELECT')")
    fun correctionOriginal(@PathVariable id: String, @PathVariable archiveId: String,
                           @PathVariable fileId: String, response: HttpServletResponse) {
        warehouseService.requireCorrectionOriginal(id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @Log("库位删除")
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE')")
    fun delete(@PathVariable id: String): ApiData {
        warehouseService.delete(id)
        return ApiData("已删除")
    }
}
