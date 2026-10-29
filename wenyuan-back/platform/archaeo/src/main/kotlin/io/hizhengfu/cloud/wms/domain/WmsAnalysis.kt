package io.hizhengfu.cloud.wms.domain


/**
 * WMS统计数据格式
 * */
data class WmsAnalysisVo(
    val name: String,
    val value: Long,
    val percent: Double? = null,
    val sort: Int,
    val type: EnumsAnalysisType? = null,
)

/**
 * 统计类型
 * */
enum class EnumsAnalysisType(val description: String, val chartType: String = "bar") {
    PROJECT("按遗址统计", "bar"),
    PROJECT_PLOT("按探方统计", "bar"),
    PROJECT_CELL("按遗迹统计", "pie"),
    MATERIAL("按材质统计", "pie"),
    TIME_PERIOD("按年代统计", "pie"),
    SAVE_STATUS("按保存状态统计", "pie"),
    CULTURAL_ATTRIBUTE("按文化因素统计", "pie"),
    OUT_PURPOSE("按出库原因统计", "pie"),
}

/**
 * 统计类型
 * */
enum class EnumsRegisterAnalysisType(val description: String) {
    PROJECT("按遗址统计"),
    PROJECT_PLOT("按探方统计"),
    PROJECT_CELL("按遗迹统计"),
    MATERIAL("按材质统计"),
}


/**
 * 查询参数
 * */
data class WmsAnalysisParams(
    val type: EnumsAnalysisType? = null,
    val typeList: List<EnumsAnalysisType>? = null,//分析类型
    val chartType: String? = null,
    val stock: Boolean = true,//是否为库存分析
    val projectId: String? = null,//遗址
    val name: String? = null,
    val material: String? = null,//材质
    val timePeriod: String? = null,//时代
    val saveStatus: String? = null,//保存状态
    val projectCellId: String? = null,//出土单位
    val culturalAttribute: String? = null,//文化属性
)