package io.hizhengfu.cloud.wms.domain

import java.time.LocalDateTime

/**
 * 多维交叉统计 — 维度枚举
 *
 * 与 xlsx 中 10 类组合检索的"维度"对齐，前端通过组合 `groupBy` 数组传入：
 *   一维：groupBy=[MATERIAL] → 按材质统计
 *   二维：groupBy=[MATERIAL, POTTERY_SYSTEM] → 质地+陶系（陶质/陶色/纹饰）
 *   三维及以上：通过 child 嵌套钻取
 */
enum class EnumCrossDim(val description: String) {
    PROJECT("遗址"),
    PROJECT_PLOT("探方"),
    PROJECT_CELL("遗迹单位"),
    CELL_TYPE("遗迹类型"),
    MATERIAL("质地"),
    ARTIFACT_TYPE("器型"),
    TIME_PERIOD("时代"),
    CULTURAL_ATTRIBUTE("文化因素"),
    PURPOSE_L1("用途一级"),
    PURPOSE_L2("用途二级"),
    PURPOSE_L3("用途三级"),
    POTTERY_QUALITY("陶质"),
    POTTERY_COLOR("陶色"),
    DECORATION("纹饰"),
    KILN_SITE("窑口"),
    KILN_SYSTEM("窑系"),
    SAVE_STATUS("保存状况"),
    STORAGE_STATUS("存放状态"),
}

/**
 * 多维交叉统计请求
 *
 * @param groupBy   分组维度数组（按顺序作为外/内层）。例如 [MATERIAL, POTTERY_QUALITY] 表示先按质地，再按陶质细分。
 * @param chartType bar / pie；缺省 bar。
 *
 * 过滤条件：
 * - projectId / projectPlotId / projectCellId 支持单值与逗号分隔多值
 * - material / culturalAttribute / timePeriod / purpose 等支持多值
 * - unearthDate / storageTime 支持区间
 */
data class WmsCrossStatParams(
    val groupBy: List<EnumCrossDim> = listOf(EnumCrossDim.MATERIAL),
    val chartType: String = "bar",

    // 过滤条件（多值用逗号分隔）
    val projectId: String? = null,
    val projectPlotId: String? = null,
    val projectCellId: String? = null,
    val cellType: String? = null,
    val material: String? = null,
    val artifactType: String? = null,
    val timePeriod: String? = null,
    val culturalAttribute: String? = null,
    val purpose: String? = null,
    val potteryQuality: String? = null,
    val potteryColor: String? = null,
    val decoration: String? = null,
    val kilnSite: String? = null,
    val kilnSystem: String? = null,
    val saveStatus: String? = null,
    val storageStatus: String? = null,

    val unearthDateStart: LocalDateTime? = null,
    val unearthDateEnd: LocalDateTime? = null,
    val storageTimeStart: LocalDateTime? = null,
    val storageTimeEnd: LocalDateTime? = null,

    /** 是否包含已出库（false=仅在库） */
    val includeOut: Boolean = true,
)

/**
 * 一维或嵌套钻取的统计节点
 *
 * @param name     当前维度的取值（如"陶器"）
 * @param value    当前节点的件数合计
 * @param dim      产生这一节点的维度
 * @param children 下一层维度的统计（仅当 groupBy 多维时）
 */
data class WmsCrossStatNode(
    val name: String,
    val value: Long,
    val dim: EnumCrossDim,
    val children: List<WmsCrossStatNode>? = null,
)

/**
 * 多维交叉统计返回
 */
data class WmsCrossStatResult(
    val groupBy: List<EnumCrossDim>,
    val chartType: String,
    val total: Long,
    val nodes: List<WmsCrossStatNode>,
)

/**
 * 钻取明细 — 单击图表块时拉取的文物清单条件
 */
data class WmsCrossDrillParams(
    val baseParams: WmsCrossStatParams,
    /** 路径过滤：例如 [("MATERIAL","陶器"),("POTTERY_QUALITY","泥质")] */
    val path: List<Pair<EnumCrossDim, String>>,
)
