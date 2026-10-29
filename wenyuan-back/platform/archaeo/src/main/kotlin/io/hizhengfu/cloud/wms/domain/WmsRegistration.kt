package io.hizhengfu.cloud.wms.domain

/**
 * 出土器物登记表 —— 智能统计结果模型
 *
 * 结构：行 = 遗迹单位（M1/M2…），列 = 器物质地（陶器/铜器…），
 * 单元格 = 该单位该材质下的器物聚合（同器名 + 同型式合并计数，礼器序排列），如 "鼎7、簋5"。
 */

/** 单条器物聚合项（同器名 + 同型式合并） */
data class WmsRegEntry(
    /** 器名（器型优先，缺省用文物名称），如 鼎 */
    val name: String,
    /** 型/式标注，如 "Ⅱ型1式"，可空 */
    val typeStyle: String,
    /** 件数合计 */
    val count: Long,
    /** 展示串，如 "鼎7" / "鼎Ⅱ式2" */
    val label: String,
    /** 该聚合包含的文物 id（点击打开卡片取首条） */
    val artifactIds: List<String>,
)

/** 一个遗迹单位（行） */
data class WmsRegRow(
    val cellId: String?,
    /** 遗迹单位，如 M2 */
    val cellName: String,
    /** 遗迹类型 */
    val cellType: String,
    /** 该单位文物件数合计 */
    val total: Long,
    /** 材质 -> 该材质下的聚合项列表（已按礼器序排序） */
    val cells: Map<String, List<WmsRegEntry>>,
)

/** 出土器物登记表整表 */
data class WmsRegistrationTable(
    val projectId: String,
    val projectName: String,
    /** 列头：出现过的材质（按总件数降序） */
    val materials: List<String>,
    val rows: List<WmsRegRow>,
    val total: Long,
)
