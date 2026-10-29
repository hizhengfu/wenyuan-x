package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.wms.dao.WmsArtifactVo
import io.hizhengfu.cloud.wms.dao.WmsArtifacts
import io.hizhengfu.cloud.wms.service.WmsArtifactStockService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 文物基本信息表接口
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/stock")
class WmsStockController(val wmsArtifactStockService: WmsArtifactStockService) {

    /**
     * 查询文物基本信息表
     * */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_ALL','WMS_ARTIFACT_SELECT')")
    fun get(@PathVariable id: String): ApiData {
        // 已入库：返回含库房/位置/保存状况的库存视图
        wmsArtifactStockService.get(id)?.let { return ApiData(data = WmsArtifacts.toModelStock(it)) }
        // 未入库：回退登记信息（库存接口本身仍只含已入库，详情页据此展示登记信息即可）
        val reg = wmsArtifactStockService.getRegister(id) ?: throw BadRequestException("数据不存在")
        return ApiData(data = WmsArtifacts.toModel(reg))
    }

    /**
     * 列表文物基本信息表
     * */
    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ARTIFACT_OPERATION_ALL','WMS_ARTIFACT_OPERATION_SELECT')")
    fun list(wmsArtifact: WmsArtifactVo, request: HttpServletRequest): ApiData {
        val apiData = ApiData()

        val page = wmsArtifactStockService.findPage(request, wmsArtifact)

        page?.apply {
            list = data?.map { WmsArtifacts.toModelStock(it) }
        }

        apiData.data = page

        return apiData
    }
}