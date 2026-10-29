package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


/**
 * 多维交叉统计
 *
 * 对应 xlsx「统计分析」中 10 种组合检索；前端只需传一个 groupBy 序列即可表达：
 *   - "质地+陶系"          -> groupBy=[MATERIAL, POTTERY_QUALITY, POTTERY_COLOR, DECORATION]
 *   - "文化因素+用途+质地" -> groupBy=[CULTURAL_ATTRIBUTE, PURPOSE_L1, MATERIAL]
 *
 * 算法（内存聚合，不做 SQL ROLLUP，以兼容 SQLite / OpenGauss 等）：
 *   1. 拉一次符合过滤条件的"文物 × 库存"宽行；
 *   2. 用 Kotlin 在内存里按 groupBy 顺序分组求和；
 *   3. 返回嵌套 WmsCrossStatNode 树，叶子是末维数量。
 *
 * 行数上限：单遗址通常 <10万，单次查询完全可承受；如果未来要支撑百万级数据，再切换到 SQL 多重 GROUP BY。
 */
@Service
@Transactional(readOnly = true)
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsAnalysisCrossService {

    companion object {
        /** 礼器/常见器物排序基准：鼎、簋…置于前列（出土器物登记表用） */
        private val RITUAL_ORDER = listOf(
            "鼎", "簋", "鬲", "甗", "簠", "盨", "敦", "豆", "盆", "钵", "罐", "瓮", "壶", "瓶",
            "盘", "匜", "盂", "爵", "角", "斝", "觚", "觯", "尊", "卣", "罍", "瓿", "盉", "钟", "镈",
        )
    }

    @Cacheable
    fun crossStat(params: WmsCrossStatParams): WmsCrossStatResult {
        val rows = fetchFlatRows(params)
        val nodes = buildNodes(rows, params.groupBy, 0)
        val total = rows.sumOf { it.quantity }
        return WmsCrossStatResult(
            groupBy = params.groupBy,
            chartType = params.chartType,
            total = total,
            nodes = nodes,
        )
    }

    /**
     * 钻取：在已有 baseParams 之上加上 path 形成更严格的过滤条件，
     * 返回扁平的文物清单（直接复用 baseParams.groupBy 的最后一维作为"细维度"用于继续呈现）。
     */
    @Cacheable
    fun drill(params: WmsCrossDrillParams): List<FlatStatRow> {
        val refined = applyPath(params.baseParams, params.path)
        return fetchFlatRows(refined)
    }

    /**
     * 出土器物登记表（智能统计）
     *
     * 行 = 遗迹单位，列 = 器物质地；单元格按"器名 + 型式"合并计数（如 鼎7、簋5），
     * 单元格内按礼器序（鼎、簋…）排列，行按遗迹单位自然序排列。
     */
    @Cacheable
    fun registrationTable(projectId: String): WmsRegistrationTable {
        val join: Join = WmsArtifacts
            .join(WmsArtifactExtends, JoinType.LEFT) { WmsArtifacts.id eq WmsArtifactExtends.id }
            .join(WmsProjects, JoinType.LEFT) { WmsArtifacts.projectId eq WmsProjects.id }
            .join(WmsProjectCells, JoinType.LEFT) { WmsArtifacts.projectCellId eq WmsProjectCells.id }

        val q = join.select(
            WmsArtifacts.id,
            WmsArtifacts.name,
            WmsArtifacts.material,
            WmsArtifacts.quantity,
            WmsArtifacts.projectCellId,
            WmsProjects.name,
            WmsProjectCells.name,
            WmsProjectCells.type,
            WmsArtifactExtends.artifactType,
            WmsArtifactExtends.artifactStyle,
            WmsArtifactExtends.artifactSubStyle,
            WmsArtifactExtends.artifactStage,
        )
        q.andWhere { WmsArtifacts.projectId eq projectId }

        val raws = q.map { row ->
            RegRaw(
                artifactId = row[WmsArtifacts.id].value,
                projectName = row.getOrNull(WmsProjects.name) ?: "",
                name = row[WmsArtifacts.name],
                material = row[WmsArtifacts.material].split(",").firstOrNull()?.trim().orEmpty(),
                quantity = row[WmsArtifacts.quantity].toLong(),
                cellId = row.getOrNull(WmsArtifacts.projectCellId),
                cellName = row.getOrNull(WmsProjectCells.name) ?: "",
                cellType = row.getOrNull(WmsProjectCells.type) ?: "",
                artifactType = row.getOrNull(WmsArtifactExtends.artifactType),
                artifactStyle = row.getOrNull(WmsArtifactExtends.artifactStyle),
                artifactSubStyle = row.getOrNull(WmsArtifactExtends.artifactSubStyle),
                artifactStage = row.getOrNull(WmsArtifactExtends.artifactStage),
            )
        }

        val projectName = raws.firstOrNull()?.projectName ?: ""

        // 材质列顺序：按出现总件数降序
        val materials = raws.groupBy { it.material.ifBlank { "未填" } }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key }

        // 行：按遗迹单位（自然序）
        val rows = raws.groupBy { (it.cellId ?: "") to it.cellName.ifBlank { "未分单位" } }
            .entries
            .sortedWith(compareBy({ naturalPrefix(it.key.second) }, { naturalNum(it.key.second) }, { it.key.second }))
            .map { (cellKey, cellRaws) ->
                val (cellId, cellName) = cellKey
                val cellsMap = LinkedHashMap<String, List<WmsRegEntry>>()
                cellRaws.groupBy { it.material.ifBlank { "未填" } }.forEach { (mat, matRaws) ->
                    cellsMap[mat] = matRaws
                        .groupBy { rawEntryKey(it) }
                        .map { (key, group) ->
                            val (nm, ts) = key
                            val cnt = group.sumOf { it.quantity }
                            WmsRegEntry(
                                name = nm,
                                typeStyle = ts,
                                count = cnt,
                                label = nm + ts + (if (cnt > 1) cnt.toString() else ""),
                                artifactIds = group.map { it.artifactId },
                            )
                        }
                        .sortedWith(compareBy({ ritualIndex(it.name) }, { it.name }, { it.typeStyle }))
                }
                WmsRegRow(
                    cellId = cellId.ifBlank { null },
                    cellName = cellName,
                    cellType = cellRaws.firstOrNull()?.cellType ?: "",
                    total = cellRaws.sumOf { it.quantity },
                    cells = cellsMap,
                )
            }

        return WmsRegistrationTable(
            projectId = projectId,
            projectName = projectName,
            materials = materials,
            rows = rows,
            total = raws.sumOf { it.quantity },
        )
    }

    /** 聚合键：器名（器型优先）+ 型式（型+式） */
    private fun rawEntryKey(r: RegRaw): Pair<String, String> {
        val nm = r.artifactType?.takeIf { it.isNotBlank() } ?: r.name
        val ts = listOfNotNull(
            r.artifactStyle?.takeIf { it.isNotBlank() },
            r.artifactSubStyle?.takeIf { it.isNotBlank() },
            r.artifactStage?.takeIf { it.isNotBlank() },
        ).joinToString("")
        return nm to ts
    }

    /** 礼器序：鼎、簋…在前；其余按 ritualOrder 之后 */
    private fun ritualIndex(name: String): Int {
        val i = RITUAL_ORDER.indexOfFirst { name.startsWith(it) }
        return if (i >= 0) i else RITUAL_ORDER.size + 1
    }

    private fun naturalPrefix(s: String): String = Regex("^\\D*").find(s)?.value ?: s
    private fun naturalNum(s: String): Int = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: 0

    // --- 内部实现 ---

    private fun applyPath(base: WmsCrossStatParams, path: List<Pair<EnumCrossDim, String>>): WmsCrossStatParams {
        var p = base
        for ((dim, v) in path) {
            p = when (dim) {
                EnumCrossDim.MATERIAL -> p.copy(material = v)
                EnumCrossDim.ARTIFACT_TYPE -> p.copy(artifactType = v)
                EnumCrossDim.TIME_PERIOD -> p.copy(timePeriod = v)
                EnumCrossDim.CULTURAL_ATTRIBUTE -> p.copy(culturalAttribute = v)
                EnumCrossDim.PURPOSE_L1, EnumCrossDim.PURPOSE_L2, EnumCrossDim.PURPOSE_L3 -> p.copy(purpose = v)
                EnumCrossDim.POTTERY_QUALITY -> p.copy(potteryQuality = v)
                EnumCrossDim.POTTERY_COLOR -> p.copy(potteryColor = v)
                EnumCrossDim.DECORATION -> p.copy(decoration = v)
                EnumCrossDim.KILN_SITE -> p.copy(kilnSite = v)
                EnumCrossDim.KILN_SYSTEM -> p.copy(kilnSystem = v)
                EnumCrossDim.PROJECT -> p.copy(projectId = v)
                EnumCrossDim.PROJECT_PLOT -> p.copy(projectPlotId = v)
                EnumCrossDim.PROJECT_CELL -> p.copy(projectCellId = v)
                EnumCrossDim.CELL_TYPE -> p.copy(cellType = v)
                EnumCrossDim.SAVE_STATUS -> p.copy(saveStatus = v)
                EnumCrossDim.STORAGE_STATUS -> p.copy(storageStatus = v)
            }
        }
        return p
    }

    /**
     * 一次 join 出后续聚合所需的所有维度列。
     * 维度值是字符串；缺失/空值返回"未填"占位（保持图表完整）。
     */
    private fun fetchFlatRows(params: WmsCrossStatParams): List<FlatStatRow> {
        val join: Join = WmsArtifacts
            .join(WmsArtifactExtends, JoinType.LEFT) { WmsArtifacts.id eq WmsArtifactExtends.id }
            .join(WmsArtifactStocks, JoinType.LEFT) { WmsArtifacts.id eq WmsArtifactStocks.id }
            .join(WmsProjects, JoinType.LEFT) { WmsArtifacts.projectId eq WmsProjects.id }
            .join(WmsProjectCells, JoinType.LEFT) { WmsArtifacts.projectCellId eq WmsProjectCells.id }
            .join(WmsProjectPlots, JoinType.LEFT) { WmsProjectCells.plotId eq WmsProjectPlots.id }

        val q = join.select(
            WmsArtifacts.id,
            WmsArtifacts.quantity,
            WmsArtifacts.material,
            WmsArtifacts.projectId,
            WmsArtifacts.projectCellId,
            WmsProjects.name,
            WmsProjectCells.name,
            WmsProjectCells.type,
            WmsProjectPlots.name,
            WmsArtifactExtends.timePeriod,
            WmsArtifactExtends.culturalAttribute,
            WmsArtifactExtends.purposeL1,
            WmsArtifactExtends.purposeL2,
            WmsArtifactExtends.purposeL3,
            WmsArtifactExtends.artifactType,
            WmsArtifactExtends.potteryQuality,
            WmsArtifactExtends.potteryColor,
            WmsArtifactExtends.decoration,
            WmsArtifactExtends.kilnSite,
            WmsArtifactExtends.kilnSystem,
            WmsArtifactStocks.saveStatus,
            WmsArtifactStocks.stockQuantity,
        )

        applyFilters(q, params)

        return q.map { row ->
            FlatStatRow(
                artifactId = row[WmsArtifacts.id].value,
                quantity = (row[WmsArtifacts.quantity] ?: 0).toLong(),
                projectName = row.getOrNull(WmsProjects.name) ?: "",
                projectPlot = row.getOrNull(WmsProjectPlots.name) ?: "",
                projectCell = row.getOrNull(WmsProjectCells.name) ?: "",
                cellType = row.getOrNull(WmsProjectCells.type) ?: "",
                material = row[WmsArtifacts.material] ?: "",
                artifactType = row.getOrNull(WmsArtifactExtends.artifactType) ?: "",
                timePeriod = row.getOrNull(WmsArtifactExtends.timePeriod) ?: "",
                culturalAttribute = row.getOrNull(WmsArtifactExtends.culturalAttribute) ?: "",
                purposeL1 = row.getOrNull(WmsArtifactExtends.purposeL1) ?: "",
                purposeL2 = row.getOrNull(WmsArtifactExtends.purposeL2) ?: "",
                purposeL3 = row.getOrNull(WmsArtifactExtends.purposeL3) ?: "",
                potteryQuality = row.getOrNull(WmsArtifactExtends.potteryQuality) ?: "",
                potteryColor = row.getOrNull(WmsArtifactExtends.potteryColor) ?: "",
                decoration = row.getOrNull(WmsArtifactExtends.decoration) ?: "",
                kilnSite = row.getOrNull(WmsArtifactExtends.kilnSite) ?: "",
                kilnSystem = row.getOrNull(WmsArtifactExtends.kilnSystem) ?: "",
                saveStatus = row.getOrNull(WmsArtifactStocks.saveStatus) ?: "",
            )
        }
    }

    private fun applyFilters(q: Query, p: WmsCrossStatParams) {
        if (!p.projectId.isNullOrBlank()) q.andWhere { WmsArtifacts.projectId inList p.projectId.split(",") }
        if (!p.projectCellId.isNullOrBlank()) q.andWhere { WmsArtifacts.projectCellId inList p.projectCellId.split(",") }
        if (!p.projectPlotId.isNullOrBlank()) q.andWhere { WmsProjectCells.plotId inList p.projectPlotId.split(",") }
        if (!p.cellType.isNullOrBlank()) q.andWhere { WmsProjectCells.type inList p.cellType.split(",") }
        if (!p.material.isNullOrBlank()) q.andWhere { WmsArtifacts.material inList p.material.split(",") }
        if (!p.artifactType.isNullOrBlank()) q.andWhere { WmsArtifactExtends.artifactType inList p.artifactType.split(",") }
        if (!p.timePeriod.isNullOrBlank()) q.andWhere { WmsArtifactExtends.timePeriod inList p.timePeriod.split(",") }
        if (!p.culturalAttribute.isNullOrBlank()) q.andWhere { WmsArtifactExtends.culturalAttribute inList p.culturalAttribute.split(",") }
        if (!p.purpose.isNullOrBlank()) q.andWhere {
            // purpose 可能命中 L1/L2/L3 任一层
            (WmsArtifactExtends.purposeL1 inList p.purpose.split(",")) or
                (WmsArtifactExtends.purposeL2 inList p.purpose.split(",")) or
                (WmsArtifactExtends.purposeL3 inList p.purpose.split(","))
        }
        if (!p.potteryQuality.isNullOrBlank()) q.andWhere { WmsArtifactExtends.potteryQuality inList p.potteryQuality.split(",") }
        if (!p.potteryColor.isNullOrBlank()) q.andWhere { WmsArtifactExtends.potteryColor inList p.potteryColor.split(",") }
        if (!p.decoration.isNullOrBlank()) q.andWhere { WmsArtifactExtends.decoration inList p.decoration.split(",") }
        if (!p.kilnSite.isNullOrBlank()) q.andWhere { WmsArtifactExtends.kilnSite inList p.kilnSite.split(",") }
        if (!p.kilnSystem.isNullOrBlank()) q.andWhere { WmsArtifactExtends.kilnSystem inList p.kilnSystem.split(",") }
        if (!p.saveStatus.isNullOrBlank()) q.andWhere { WmsArtifactStocks.saveStatus inList p.saveStatus.split(",") }

        if (p.unearthDateStart != null) q.andWhere { WmsArtifacts.unearthDate greaterEq p.unearthDateStart }
        if (p.unearthDateEnd != null) q.andWhere { WmsArtifacts.unearthDate lessEq p.unearthDateEnd }
        if (p.storageTimeStart != null) q.andWhere { WmsArtifactStocks.storageTime greaterEq p.storageTimeStart }
        if (p.storageTimeEnd != null) q.andWhere { WmsArtifactStocks.storageTime lessEq p.storageTimeEnd }
    }

    private fun buildNodes(rows: List<FlatStatRow>, dims: List<EnumCrossDim>, depth: Int): List<WmsCrossStatNode> {
        if (depth >= dims.size) return emptyList()
        val dim = dims[depth]
        return rows.groupBy { it.dimValue(dim) }
            .map { (key, sub) ->
                val children = if (depth + 1 < dims.size) buildNodes(sub, dims, depth + 1) else null
                WmsCrossStatNode(
                    name = key.ifBlank { "未填" },
                    value = sub.sumOf { it.quantity },
                    dim = dim,
                    children = children,
                )
            }
            .sortedByDescending { it.value }
    }

    private fun <T> ResultRow.getOrNull(c: Expression<T>): T? = runCatching { this[c] }.getOrNull()
}

/**
 * 维度扁平行 — 内部聚合用
 */
data class FlatStatRow(
    val artifactId: String,
    val quantity: Long,
    val projectName: String,
    val projectPlot: String,
    val projectCell: String,
    val cellType: String,
    val material: String,
    val artifactType: String,
    val timePeriod: String,
    val culturalAttribute: String,
    val purposeL1: String,
    val purposeL2: String,
    val purposeL3: String,
    val potteryQuality: String,
    val potteryColor: String,
    val decoration: String,
    val kilnSite: String,
    val kilnSystem: String,
    val saveStatus: String,
) {
    fun dimValue(dim: EnumCrossDim): String = when (dim) {
        EnumCrossDim.PROJECT -> projectName
        EnumCrossDim.PROJECT_PLOT -> projectPlot
        EnumCrossDim.PROJECT_CELL -> projectCell
        EnumCrossDim.CELL_TYPE -> cellType
        EnumCrossDim.MATERIAL -> material
        EnumCrossDim.ARTIFACT_TYPE -> artifactType
        EnumCrossDim.TIME_PERIOD -> timePeriod
        EnumCrossDim.CULTURAL_ATTRIBUTE -> culturalAttribute
        EnumCrossDim.PURPOSE_L1 -> purposeL1
        EnumCrossDim.PURPOSE_L2 -> purposeL2
        EnumCrossDim.PURPOSE_L3 -> purposeL3
        EnumCrossDim.POTTERY_QUALITY -> potteryQuality
        EnumCrossDim.POTTERY_COLOR -> potteryColor
        EnumCrossDim.DECORATION -> decoration
        EnumCrossDim.KILN_SITE -> kilnSite
        EnumCrossDim.KILN_SYSTEM -> kilnSystem
        EnumCrossDim.SAVE_STATUS -> saveStatus
        EnumCrossDim.STORAGE_STATUS -> saveStatus
    }
}

/** 出土器物登记表内部原始行 */
private data class RegRaw(
    val artifactId: String,
    val projectName: String,
    val name: String,
    val material: String,
    val quantity: Long,
    val cellId: String?,
    val cellName: String,
    val cellType: String,
    val artifactType: String?,
    val artifactStyle: String?,
    val artifactSubStyle: String?,
    val artifactStage: String?,
)
