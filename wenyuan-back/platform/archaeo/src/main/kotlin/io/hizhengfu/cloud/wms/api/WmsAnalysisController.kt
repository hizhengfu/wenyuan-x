package io.hizhengfu.cloud.wms.api

import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.ChartUtils
import io.hizhengfu.cloud.echarts.*
import io.hizhengfu.cloud.echarts.axis.EnumAxisType
import io.hizhengfu.cloud.wms.domain.WmsAnalysisParams
import io.hizhengfu.cloud.wms.domain.WmsCrossDrillParams
import io.hizhengfu.cloud.wms.domain.WmsCrossStatParams
import io.hizhengfu.cloud.wms.service.WmsAnalysisCrossService
import io.hizhengfu.cloud.wms.service.WmsAnalysisService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 考古数据分析
 *
 * */
@RestController
@RequestMapping("\${apiPath}/wms/analysis")
class WmsAnalysisController(
    val wmsAnalysisService: WmsAnalysisService,
    val wmsAnalysisCrossService: WmsAnalysisCrossService,
) {

    /**
     * 多维交叉统计 — 对应统计分析.xlsx 10 类组合
     *
     * 示例：
     *   POST /api/wms/analysis/crossStat
     *   { "groupBy": ["CULTURAL_ATTRIBUTE", "MATERIAL"], "projectId": "xxx", ... }
     */
    @PostMapping("crossStat")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ANALYSIS')")
    fun crossStat(@RequestBody params: WmsCrossStatParams): ApiData =
        ApiData(data = wmsAnalysisCrossService.crossStat(params))

    /**
     * 单击图表块时的钻取明细
     */
    @PostMapping("drill")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ANALYSIS')")
    fun drill(@RequestBody params: WmsCrossDrillParams): ApiData =
        ApiData(data = wmsAnalysisCrossService.drill(params))

    /**
     * 文物数据分析
     * */
    @GetMapping("chart")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ANALYSIS')")
    fun chart(params: WmsAnalysisParams): ApiData {
        if (params.type == null && params.typeList.isNullOrEmpty()) {
            throw BadRequestException("请至少选择一个分析类型")
        }

        val typeList = params.typeList ?: listOf(params.type)

        val result =
            typeList.map { type ->
                val xAxis = Axis()
                val yAxis = Axis(name = "数量(件)", nameLocation = "center", nameGap = 40)

                val chartType = params.chartType ?: type?.chartType ?: "bar"

                val serie = if (chartType == "pie") {
                    Serie(
                        type = "pie",
                        radius = listOf("40%", "70%"),
                        label = Label(formatter = "{b} {d}%"),
                        selectedMode = "single",
                        encode = Encode().buildPie("name", "value")
                    )
                } else {
                    xAxis.type = EnumAxisType.CATEGORY
                    Serie(
                        name = type?.description,
                        type = chartType,
                        selectedMode = "single",
                        encode = Encode(x = "name", y = "value")
                    )
                }
                val param = params.copy(type = type)

                val options = ChartUtils.buildChart(
                    serie = serie,
                    dataset = Dataset(wmsAnalysisService.statStockArtifact(param)),
                    xAxis = xAxis,
                    yAxis = yAxis
                )

//                options.legend = Legend()
                type?.description.let {
                    options.title = mutableListOf(Title(text = it, left = "center"))
                }
                options
            }
        return ApiData(data = result)
    }

    /**
     * 库存数据分析
     * */
    @GetMapping("statStock")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ANALYSIS')")
    fun statStock(): ApiData {
        return ApiData(data = wmsAnalysisService.statStock())
    }

    /**
     * 出土器物登记表（智能统计）— 单位×材质交叉，含型式计数与礼器序排列
     */
    @GetMapping("registrationTable")
    @PreAuthorize("hasAnyAuthority('ADMIN','WMS_ANALYSIS')")
    fun registrationTable(@RequestParam projectId: String): ApiData =
        ApiData(data = wmsAnalysisCrossService.registrationTable(projectId))

}