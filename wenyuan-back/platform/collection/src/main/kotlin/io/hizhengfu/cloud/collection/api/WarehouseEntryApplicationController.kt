package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.WarehouseEntryApplicationVo
import io.hizhengfu.cloud.collection.domain.WarehouseAttendanceVo
import io.hizhengfu.cloud.collection.service.WarehouseEntryApplicationService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/warehouse-entry")
class WarehouseEntryApplicationController(
    private val service: WarehouseEntryApplicationService,
) {

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = service.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = service.get(id))

    @Log("保存人员入库申请")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    fun save(@RequestBody @Validated vo: WarehouseEntryApplicationVo, @RequestParam expectedOrgId:String, @RequestParam expectedRevision:String): ApiData =
        ApiData(data = service.saveConfirmed(vo,expectedOrgId,expectedRevision))

    @Log("提交人员入库审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    fun submit(@PathVariable id: String,@RequestParam expectedOrgId:String,@RequestParam expectedRevision:String): ApiData =
        ApiData(data = service.operateConfirmed(id,FlowUserOperation.SUBMIT,expectedOrgId,expectedRevision))

    @Log("撤销人员入库审批")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    fun cancel(@PathVariable id: String,@RequestParam expectedOrgId:String,@RequestParam expectedRevision:String): ApiData =
        ApiData(data = service.operateConfirmed(id,FlowUserOperation.CANCEL,expectedOrgId,expectedRevision))

    @Log("作废人员入库申请")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    fun void(@PathVariable id: String,@RequestParam expectedOrgId:String,@RequestParam expectedRevision:String): ApiData =
        ApiData(data = service.operateConfirmed(id,FlowUserOperation.VOID,expectedOrgId,expectedRevision))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY','COLLECTION_SELECT')")
    fun flowInfo(@PathVariable id: String): ApiData = ApiData(data=service.getFlowInfo(id))

    @Log("人员入库核验")
    @PostMapping("{id}/check-in")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    fun checkIn(@PathVariable id: String, @RequestBody vo: WarehouseAttendanceVo,
        @RequestParam expectedOrgId:String,@RequestParam expectedRevision:String): ApiData =
        ApiData(data = service.checkIn(id, vo,expectedOrgId,expectedRevision))

    @Log("人员离库核验")
    @PostMapping("{id}/check-out")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    fun checkOut(@PathVariable id: String, @RequestBody vo: WarehouseAttendanceVo,
        @RequestParam expectedOrgId:String,@RequestParam expectedRevision:String): ApiData =
        ApiData(data = service.checkOut(id, vo,expectedOrgId,expectedRevision))

    @GetMapping("{id}/attendance-context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    fun attendanceContext(@PathVariable id:String):ApiData=ApiData(data=service.attendanceContext(id))
    @GetMapping("application-context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    fun newApplicationContext():ApiData=ApiData(data=service.applicationContext())

    @GetMapping("{id}/application-context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY','COLLECTION_SELECT')")
    fun applicationContext(@PathVariable id:String):ApiData=ApiData(data=service.applicationContext(id))

}
