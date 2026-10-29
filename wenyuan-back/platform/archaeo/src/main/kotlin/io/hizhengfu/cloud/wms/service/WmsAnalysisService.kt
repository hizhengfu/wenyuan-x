package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.utils.ChartUtils
import io.hizhengfu.cloud.echarts.*
import io.hizhengfu.cloud.portal.domain.PortalStatData
import io.hizhengfu.cloud.sys.system.dao.Dicts
import io.hizhengfu.cloud.sys.system.service.DictService
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.EnumsAnalysisType
import io.hizhengfu.cloud.wms.domain.WmsAnalysisParams
import io.hizhengfu.cloud.wms.domain.WmsAnalysisVo
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


/**
 * 数据分析
 * */
@Service
@Transactional
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsAnalysisService(val dictService: DictService) {
    /**
     * 构建库存文物图表
     * */
    fun stockChart(params: WmsAnalysisParams): EchartsOptions {
        val dataset = Dataset(statStockArtifact(params))

        val serie = if (params.chartType == "pie") {
            Serie(
                type = "pie",
                radius = listOf("40%", "70%"),
                label = Label(formatter = "{b} {d}%"),
                selectedMode = "single",
                encode = Encode().buildPie("name", "value")
            )
        } else {
            Serie(
                type = params.chartType, selectedMode = "single", encode = Encode(x = "name", y = "value")
            )
        }

        val options = ChartUtils.buildChart(serie = serie, dataset = dataset)
        options.title.add(Title(text = "${params.type?.description}", left = "center"))
        return options
    }


    /**
     * 库存文物分析
     * */
    @Cacheable
    fun statStockArtifact(params: WmsAnalysisParams): List<WmsAnalysisVo> {
        val sumQuantityField = WmsArtifactStocks.quantity.sum()//入库总数
        val sumStockQuantityField = WmsArtifactStocks.stockQuantity.sum()//在库总数
        val sumRegisterQuantityField = WmsArtifacts.quantity.sum()//注册总数

        var joinQuery = if (params.stock) {
            WmsArtifactStocks.join(WmsArtifacts, JoinType.LEFT) {
                WmsArtifacts.id.eq(WmsArtifactStocks.id)
            }
        } else {
            WmsArtifacts.join(WmsArtifactStocks, JoinType.LEFT) {
                WmsArtifacts.id.eq(WmsArtifactStocks.id)
            }
        }.join(WmsArtifactExtends, JoinType.LEFT) {
            WmsArtifacts.id.eq(WmsArtifactExtends.id)
        }

        val field = when (params.type) {
            EnumsAnalysisType.PROJECT -> {
                joinQuery = joinQuery.join(WmsProjects, JoinType.LEFT) {
                    WmsArtifacts.projectId.eq(WmsProjects.id)
                }
                WmsProjects.name
            }

            EnumsAnalysisType.PROJECT_PLOT -> {
                joinQuery = joinQuery.join(WmsProjectCells, JoinType.LEFT) {
                    WmsArtifacts.projectCellId.eq(WmsProjectCells.id)
                }.join(WmsProjectPlots, JoinType.LEFT) {
                    WmsProjectCells.plotId.eq(WmsProjectPlots.id)
                }
                WmsProjectPlots.name
            }

            EnumsAnalysisType.PROJECT_CELL -> {
                joinQuery = joinQuery.join(WmsProjectCells, JoinType.LEFT) {
                    WmsArtifacts.projectCellId.eq(WmsProjectCells.id)
                }
                WmsProjectCells.name
            }

            EnumsAnalysisType.MATERIAL -> {
                joinQuery = joinQuery.join(Dicts, JoinType.LEFT) {
                    Dicts.type.eq("wms_artifact_material_type") and WmsArtifacts.material.eq(Dicts.value)
                }
                Dicts.label
            }

            EnumsAnalysisType.TIME_PERIOD -> {
                WmsArtifactExtends.timePeriod
            }

            EnumsAnalysisType.SAVE_STATUS -> {
                WmsArtifactStocks.saveStatus
            }

            EnumsAnalysisType.CULTURAL_ATTRIBUTE -> {
                WmsArtifactExtends.culturalAttribute
            }

            EnumsAnalysisType.OUT_PURPOSE -> {//出库原因
                WmsArtifactStocks.storageType
            }

            else -> {
                WmsArtifactStocks.saveStatus
            }
        }
        val query = joinQuery.select(field, sumQuantityField, sumStockQuantityField, sumRegisterQuantityField)


        if (!params.projectId.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.projectId.eq(params.projectId)
            }
        }
        if (!params.projectCellId.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.projectCellId.inList(params.projectCellId.split(","))
            }
        }

        if (!params.material.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.material.inList(params.material.split(","))
            }
        }

        if (!params.saveStatus.isNullOrBlank()) {
            query.andWhere {
                WmsArtifactStocks.saveStatus.eq(params.saveStatus)
            }
        }

        if (!params.timePeriod.isNullOrBlank()) {
            query.andWhere {
                WmsArtifactExtends.timePeriod.inList(params.timePeriod.split(","))
            }
        }
        if (!params.culturalAttribute.isNullOrBlank()) {
            query.andWhere {
                WmsArtifactExtends.culturalAttribute.inList(params.culturalAttribute.split(","))
            }
        }

        query.groupBy(field)

        query.orderBy(sumQuantityField, SortOrder.DESC)


        if (!params.stock) {//登记分析的情况
            return query.map { row ->
                WmsAnalysisVo(
                    name = row[field] ?: "其他",
                    value = row[sumRegisterQuantityField]?.toLong() ?: 0L,
                    sort = 0,
                    type = params.type
                )
            }
        }

        if (params.type == EnumsAnalysisType.OUT_PURPOSE) {//出库
            return query.map { row ->
                val sumQuantity = row[sumQuantityField]?.toLong() ?: 0L
                val sumStockQuantity = row[sumStockQuantityField]?.toLong() ?: 0L

                WmsAnalysisVo(
                    name = row[field] ?: "其他", value = sumQuantity, sort = 0, type = params.type
                )
            }
        } else {
            return query.map { row ->
                WmsAnalysisVo(
                    name = row[field] ?: "其他",
                    value = row[sumStockQuantityField]?.toLong() ?: 0L,
                    sort = 0,
                    type = params.type
                )
            }
        }

    }


    /**
     * 库房数据统计
     * */
    @Cacheable
    fun statStock(projectId: String? = null): List<PortalStatData> {
        val sumRegisterQuantityField = WmsArtifacts.quantity.sum()//注册总数
        val countRegisterQuantityField = WmsArtifacts.id.count()//注册总条数
        val sumQuantityField = WmsArtifactStocks.quantity.sum()//入库总数
        val countQuantityField = WmsArtifactStocks.id.count()//库存总条数
        val sumStockQuantityField = WmsArtifactStocks.stockQuantity.sum()//在库总数

        val query = WmsArtifacts.join(WmsArtifactStocks, JoinType.LEFT) {
            WmsArtifacts.id.eq(WmsArtifactStocks.id)
        }.select(
            countRegisterQuantityField,
            countQuantityField,
            sumRegisterQuantityField,
            sumQuantityField,
            sumStockQuantityField
        )

        val list = mutableListOf<PortalStatData>()
        query.forEach { row ->
            val countRegisterQuantity = row[countRegisterQuantityField] ?: 0
            val countQuantity = row[countQuantityField] ?: 0
            val sumRegisterQuantity = row[sumRegisterQuantityField] ?: 0
            val sumQuantity = row[sumQuantityField] ?: 0
            val sumStockQuantity = row[sumStockQuantityField] ?: 0
            list.add(
                PortalStatData(
                    title = "登记",
                    image = "h:cpdj",
                    value = (sumRegisterQuantity - sumQuantity).toDouble(),
                    valueSuffix = "件"
                )
            )
            list.add(
                PortalStatData(
                    title = "入库", image = "h:rk", value = sumQuantity.toDouble(), valueSuffix = "件"
                )
            )
            list.add(
                PortalStatData(
                    title = "在库", image = "h:zk", value = sumStockQuantity.toDouble(), valueSuffix = "件"
                )
            )
            list.add(
                PortalStatData(
                    title = "出库",
                    image = "h:ck",
                    value = (sumQuantity - sumStockQuantity).toDouble(),
                    valueSuffix = "件"
                )
            )
        }

        return list
    }

}