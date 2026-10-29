package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsArtifacts
import io.hizhengfu.cloud.wms.dao.WmsOperateRecords
import io.hizhengfu.cloud.wms.domain.*
import io.hizhengfu.cloud.wms.service.WmsOperationService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 库存操作表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/operation")
class WmsOperationController(val wmsOperationService: WmsOperationService) {

    /**
     * 入库操作
     * */
    @Log("入库操作")
    @PostMapping("inbound")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_INBOUND')")
    fun inboundOperation(@RequestBody @Validated operationInbound: OperationInbound): ApiData {
        val result = wmsOperationService.inboundOperation(operationInbound)

        return ApiData("入库成功", result.id.value)
    }

    /**
     * 出库操作
     * */
    @Log("出库操作")
    @PostMapping("outbound")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_OUTBOUND')")
    fun outboundOperation(@RequestBody @Validated artifactOperationOutbound: OperationOutbound): ApiData {
        val result = wmsOperationService.outboundOperation(artifactOperationOutbound)

        return ApiData("出库成功", result.id.value)
    }

    /**
     * 移库操作
     * */
    @Log("移库操作")
    @PostMapping("transfer")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_TRANSFER')")
    fun transferOperation(@RequestBody @Validated transfer: OperationTransfer): ApiData {
        val result = wmsOperationService.transferOperation(transfer)

        return ApiData("移库成功", result.id.value)
    }

    /**
     * 归库操作
     * */
    @Log("归库操作")
    @PostMapping("return")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_RETURN')")
    fun returnOperation(@RequestBody @Validated artifactOperation: OperationReturn): ApiData {
        val result = wmsOperationService.returnOperation(artifactOperation)
        return ApiData("归库成功", result.id.value)
    }

    /**
     * 库存记录
     * */
    @GetMapping("history")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_SELECT')")
    fun history(request: HttpServletRequest, artifactId: String): ApiData {
        val list = wmsOperationService.findHistory(artifactId)

        val data = list.map { WmsOperateRecords.toModelHistory(it) }

        return ApiData(data = data)
    }

    /**
     * 关联文物列表
     * */
    @GetMapping("artifactList")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_SELECT')")
    fun findArtifactList(
        request: HttpServletRequest,
        recordId: String,
        type: EnumStorageOperationType? = null
    ): ApiData {
        val list = wmsOperationService.findArtifactList(recordId, type)

        val data = list.map { WmsArtifacts.toModelOperation(it) }

        return ApiData(data = data)
    }

    /**
     * 库存记录
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_SELECT')")
    fun list(request: HttpServletRequest, operationRecordVo: OperationRecordVo): ApiData {
        if (operationRecordVo.type == null) throw BadRequestException("缺少类型")
        val page = wmsOperationService.findPage(request, operationRecordVo)

        page.apply {
            list = data?.map { WmsOperateRecords.toModelList(it) }
        }

        return ApiData(data = page)
    }


    /**
     * 库存记录
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_SELECT')")
    fun get(request: HttpServletRequest, @PathVariable id: String, type: EnumStorageOperationType?): ApiData {
        if (type == null) throw BadRequestException("缺少类型")
        val page = wmsOperationService.findPage(request, OperationRecordVo(type = type, id = id))
        val data = page.data?.firstOrNull()?.run {
            WmsOperateRecords.toModelList(this)
        }
        return ApiData(data = data)
    }


}