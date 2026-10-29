package io.hizhengfu.cloud.wms.domain

/**
 * 型式分期智能拼装时用的扁平行
 */
data class WmsTypologyArtifactRow(
    val artifactId: String,
    val code: String? = null,
    val name: String? = null,
    val material: String? = null,
    val projectCellName: String? = null,
    val artifactType: String? = null,
    val artifactStyle: String? = null,
    val artifactSubStyle: String? = null,
    val artifactStage: String? = null,
    val phaseName: String? = null,
    val sectionName: String? = null,
    val lineDiagram: String? = null,
)

/**
 * 型式分期自动构建请求
 */
data class WmsTypologyAutoBuildParams(
    val projectId: String,
    val projectName: String? = null,
    val material: String? = null,
    val categoryName: String? = null,
    val artifactTypes: List<String>? = null,
)

/**
 * 标型器候选（编辑器在"式"单元格选标型器时弹出的下拉项）
 *
 * @param lineImages 该文物的线图 URL 列表（来自文件服务 contentId=lineDiagram_{id}）
 * @param figureName 自动拼好的图名，如 "Ⅱ式(M:23)"
 */
data class WmsTypicalArtifactVo(
    val artifactId: String,
    val code: String? = null,
    val name: String? = null,
    val artifactType: String? = null,
    val artifactStyle: String? = null,
    val artifactStage: String? = null,
    val projectCellName: String? = null,
    val lineImages: List<String> = emptyList(),
    val figureName: String? = null,
)

/**
 * 自动分期统计结果（用于"新建-智能分期"预览：每类器物分几期/段、几型/几式）
 */
data class WmsTypologyAutoStat(
    val artifactType: String,
    val total: Long,
    val styleCount: Int,   // 型数
    val stageCount: Int,   // 式数
    val phaseCount: Int,   // 期数
    val sectionCount: Int, // 段数
)

/**
 * 新建型式分期时用的候选项。
 */
data class WmsTypologyOption(
    val value: String,
    val label: String = value,
    val count: Long = 0,
)

/**
 * 该遗址 / 材质下已录入的期段。
 */
data class WmsTypologyPeriodRow(
    val phase: String,
    val section: String,
)

/**
 * 新建型式分期概要：用于前端按遗址联动材质、器型，并预览统计结果。
 */
data class WmsTypologySummary(
    val materials: List<WmsTypologyOption> = emptyList(),
    val artifactTypes: List<WmsTypologyOption> = emptyList(),
    val periodRows: List<WmsTypologyPeriodRow> = emptyList(),
    val stats: List<WmsTypologyAutoStat> = emptyList(),
)
