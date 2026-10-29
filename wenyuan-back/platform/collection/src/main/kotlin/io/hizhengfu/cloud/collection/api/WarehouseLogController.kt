package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.WarehouseLogVo
import io.hizhengfu.cloud.collection.service.WarehouseLogService
import io.hizhengfu.cloud.collection.service.WarehouseDiaryService
import io.hizhengfu.cloud.collection.service.parseCollectionDateParam
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.springframework.web.bind.annotation.RequestParam
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 库房日志：保管员记录当日库房事件。 */
@RestController
@RequestMapping("\${apiPath}/collection/warehouse-log")
class WarehouseLogController(private val warehouseLogService: WarehouseLogService,private val diary: WarehouseDiaryService) {

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun get(@PathVariable id: String): ApiData = ApiData(data=diary.get(id))

    @GetMapping("entries")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun entries(@RequestParam warehouseId: String,@RequestParam logDate: String): ApiData = ApiData(data=diary.entries(warehouseId,parseCollectionDateParam(logDate) ?: throw BadRequestException("日期无效")))

    @GetMapping("gaps")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun gaps(@RequestParam from: String,@RequestParam to: String): ApiData = ApiData(data=diary.gaps(parseCollectionDateParam(from) ?: throw BadRequestException("开始日期无效"),parseCollectionDateParam(to) ?: throw BadRequestException("结束日期无效")))

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = warehouseLogService.findPage(request))

    @Log("保存库房日志")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun save(@RequestBody @Validated vo: WarehouseLogVo): ApiData = ApiData(data = warehouseLogService.save(vo))

    @Log("删除库房日志")
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun delete(@PathVariable id: String): ApiData = ApiData(data = warehouseLogService.remove(id))
}
