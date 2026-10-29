package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.InventoryDao
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.InventoryBatchVo
import io.hizhengfu.cloud.collection.domain.InventoryImportRowVo
import io.hizhengfu.cloud.collection.domain.InventoryRfidCheckVo
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.InventoryComplianceService
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.InventoryService
import io.hizhengfu.cloud.collection.service.InventoryBatchService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 盘点清查。 */
@RestController
@RequestMapping("\${apiPath}/collection/inventory")
class InventoryController(
    private val inventoryService: InventoryService,
    private val batchService: InventoryBatchService,
    private val printService: CollectionPrintService,
    private val compliance: InventoryComplianceService,
) : FlowController<InventoryDao>() {

    override fun flowService() = inventoryService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = inventoryService.findPage(request))

    @GetMapping("batches")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun batches(request: HttpServletRequest): ApiData = ApiData(data = inventoryService.batchSummaries(request))

    @GetMapping("batch")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun batchConfigs(): ApiData = ApiData(data = batchService.list())

    @Log("保存盘点批次配置")
    @PostMapping("batch")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun saveBatch(@RequestBody vo: InventoryBatchVo): ApiData = ApiData(data = batchService.save(vo))

    @GetMapping("batch/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun batch(@PathVariable id: String): ApiData = ApiData(data = compliance.get(id))

    @Log("开始实际清点")
    @PostMapping("batch/{id}/begin")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun begin(@PathVariable id: String, @RequestBody vo: InventoryPlanVo): ApiData = ApiData(data = compliance.begin(id, vo))

    @Log("记录账物卡照核验")
    @PostMapping("batch/{id}/check")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun check(@PathVariable id: String, @RequestBody vo: InventoryItemCheckVo): ApiData = ApiData(data = compliance.check(id, vo))

    @Log("登记清点差异处置")
    @PostMapping("batch/{id}/action")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun action(@PathVariable id: String, @RequestBody vo: InventoryIssueActionVo): ApiData = ApiData(data = compliance.action(id, vo))

    @Log("归档清点及离任签署结果")
    @PostMapping("batch/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun complete(@PathVariable id: String, @RequestBody vo: InventoryCompletionVo): ApiData = ApiData(data = compliance.complete(id, vo))

    @GetMapping("annual/{year}/coverage")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun coverage(@PathVariable year: Int): ApiData = ApiData(data = compliance.coverage(year))

    @GetMapping("annual/{year}/reports")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun annualReports(@PathVariable year: Int): ApiData = ApiData(data = compliance.annualReports(year))

    @Log("归档年度清点实际报送凭据")
    @PostMapping("annual/report")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun annualReport(@RequestBody vo: InventoryAnnualReportVo): ApiData = ApiData(data = compliance.report(vo))

    @Log("盘点登记")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun create(@RequestBody @Validated vo: InventoryVo): ApiData = ApiData(data = inventoryService.save(vo))

    @Log("提交盘点审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销盘点审批")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废盘点审批")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @GetMapping("import/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun importTemplate(): ApiData = ApiData(data = inventoryService.importTemplate())

    @PostMapping("import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun validateImport(@RequestBody rows: List<InventoryImportRowVo>): ApiData = ApiData(data = inventoryService.validateImport(rows))

    @Log("批量导入盘点记录")
    @PostMapping("import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun commitImport(@RequestBody rows: List<InventoryImportRowVo>): ApiData = ApiData(data = inventoryService.commitImport(rows))

    /** RFID 批量盘点：扫描标签自动比对生成盘点草稿行。 */
    @Log("RFID批量盘点")
    @PostMapping("rfid-check")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun rfidCheck(@RequestBody @Validated vo: InventoryRfidCheckVo): ApiData =
        ApiData(data = inventoryService.rfidCheck(vo))

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.inventoryPrint(id))
}
