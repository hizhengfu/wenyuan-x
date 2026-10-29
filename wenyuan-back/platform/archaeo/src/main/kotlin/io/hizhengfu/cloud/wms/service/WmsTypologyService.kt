package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.WmsTypicalArtifactVo
import io.hizhengfu.cloud.wms.domain.WmsTypologyAutoStat
import io.hizhengfu.cloud.wms.domain.WmsTypologyArtifactRow
import io.hizhengfu.cloud.wms.domain.WmsTypologyOption
import io.hizhengfu.cloud.wms.domain.WmsTypologyPeriodRow
import io.hizhengfu.cloud.wms.domain.WmsTypologySummary
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


/**
 * 型式分期表服务
 *
 * - 智能分期：基于 ArtifactExtend.artifactStyle / artifactStage / phaseName 自动拼装 content；
 *   若数据稀疏（多为空），生成的就是空模板，前端可继续编辑。
 * - 自定义分期：直接保存用户在前端拖拽得到的 content（JSON 字符串）。
 */
@Service
@Transactional
@CacheConfig(cacheNames = ["WMS_TYPOLOGY"], keyGenerator = "keyGenerator")
class WmsTypologyService(
    private val artifactService: WmsArtifactService,
    private val fileService: FileService,
) : CrudService<WmsTypologyTableDao>(WmsTypologyTableDao, "WMS_TYPOLOGY") {

    @Cacheable
    fun findPage(params: WmsTypologyTableVo, year: Int?, request: HttpServletRequest): GenericPage<WmsTypologyTableVo> {
        val q = WmsTypologyTables.selectAll().orderBy(WmsTypologyTables.createTime, SortOrder.DESC)
        if (!params.projectId.isNullOrBlank()) q.andWhere { WmsTypologyTables.projectId eq params.projectId }
        if (!params.material.isNullOrBlank()) q.andWhere { WmsTypologyTables.material eq params.material }
        if (!params.categoryName.isNullOrBlank()) q.andWhere { WmsTypologyTables.categoryName eq params.categoryName }
        // 按发掘年度精确检索（excavateDate 落在 [year-01-01, year+1-01-01) 区间内）
        if (year != null) {
            val start = LocalDateTime.of(year, 1, 1, 0, 0)
            val end = start.plusYears(1)
            q.andWhere { WmsTypologyTables.excavateDate greaterEq start }
            q.andWhere { WmsTypologyTables.excavateDate less end }
        }

        val page = findPage(request, q)
        val result = GenericPage<WmsTypologyTableVo>()
        result.pageSize = page.pageSize
        result.pageNum = page.pageNum
        result.total = page.total
        result.list = page.data?.map { it.toModel() }
        return result
    }

    @CacheEvict(allEntries = true)
    fun create(vo: WmsTypologyTableVo): String {
        if (vo.projectId.isNullOrBlank()) throw BadRequestException("遗址不能为空")
        val userId = runCatching { SecurityUtils.getUserId() }.getOrNull()
        val dao = new {
            title = vo.title ?: defaultTitle(vo)
            projectId = vo.projectId
            projectName = vo.projectName
            material = vo.material
            categoryName = vo.categoryName
            source = vo.source ?: "CUSTOM"
            layout = vo.layout ?: "A4_LANDSCAPE"
            content = vo.content
            excavateDate = vo.excavateDate
            createBy = userId
            createTime = LocalDateTime.now()
        }
        return dao.id.value
    }

    @CacheEvict(allEntries = true)
    fun update(vo: WmsTypologyTableVo) {
        val id = vo.id ?: throw BadRequestException("ID 不能为空")
        val dao = findById(id) ?: throw BadRequestException("记录不存在")
        val userId = runCatching { SecurityUtils.getUserId() }.getOrNull()
        vo.title?.let { dao.title = it }
        vo.material?.let { dao.material = it }
        vo.categoryName?.let { dao.categoryName = it }
        vo.layout?.let { dao.layout = it }
        vo.content?.let { dao.content = it }
        vo.excavateDate?.let { dao.excavateDate = it }
        dao.updateBy = userId
        dao.updateTime = LocalDateTime.now()
    }

    fun get(id: String): WmsTypologyTableVo {
        val dao = findById(id) ?: throw BadRequestException("记录不存在")
        return dao.toModel()
    }

    /**
     * 新建型式分期概要：先选遗址，再按该遗址已有文物联动材质、器型、期段和统计预览。
     */
    fun summary(projectId: String, material: String?): WmsTypologySummary {
        if (projectId.isBlank()) throw BadRequestException("遗址不能为空")
        val allRows = artifactService.queryForTypology(projectId, null, null)
        val rows = if (material.isNullOrBlank()) allRows else allRows.filter { it.material == material }
        return WmsTypologySummary(
            materials = allRows
                .mapNotNull { it.material?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .map { WmsTypologyOption(value = it.key, count = it.value.toLong()) }
                .sortedBy { it.value },
            artifactTypes = rows
                .mapNotNull { it.artifactType?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .map { WmsTypologyOption(value = it.key, count = it.value.toLong()) }
                .sortedBy { it.value },
            periodRows = buildPeriodRows(rows).map { WmsTypologyPeriodRow(phase = it.first, section = it.second) },
            stats = buildStats(rows),
        )
    }

    /**
     * 智能分期：拉出指定遗址 + 材质（或大类）的所有文物，按 artifactType → artifactStyle → artifactStage 聚合，
     * 生成 content（JSON）。
     */
    fun autoBuild(projectId: String, material: String?, categoryName: String?, artifactTypes: List<String>?): String {
        val rows = artifactService.queryForTypology(projectId, material, categoryName)
        val selectedArtifactTypes = artifactTypes
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: rows
                .mapNotNull { it.artifactType?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted()
        val cols = selectedArtifactTypes.map { mapOf("artifactType" to it) }
        val periodRows = buildPeriodRows(rows).ifEmpty { listOf("" to "") }
        val grouped = rows
            .filter { !it.artifactType.isNullOrBlank() }
            .filter { it.artifactType in selectedArtifactTypes }
            .groupBy { TypologyCellKey(it.phaseName.orEmpty(), it.sectionName.orEmpty(), it.artifactType!!) }
        val tableRows = periodRows.map { (phase, section) ->
            val cells = selectedArtifactTypes.map { artifactType ->
                val items = grouped[TypologyCellKey(phase, section, artifactType)].orEmpty()
                val cellItems = items
                    .groupBy {
                        TypologyStyleKey(
                            artifactStyle = it.artifactStyle.orEmpty(),
                            artifactSubStyle = it.artifactSubStyle.orEmpty(),
                            artifactStage = it.artifactStage.orEmpty(),
                        )
                    }
                    .map { (styleKey, styleItems) ->
                        val typical = styleItems.firstOrNull()
                        mapOf(
                            "artifactStyle" to styleKey.artifactStyle,
                            "artifactSubStyle" to styleKey.artifactSubStyle,
                            "artifactStage" to styleKey.artifactStage,
                            "text" to buildCellText(styleKey),
                            "artifactId" to (typical?.artifactId ?: ""),
                            "artifactCode" to (typical?.code ?: ""),
                            "imageUrl" to (typical?.let(::resolveLineImage) ?: ""),
                            "figureName" to (typical?.let(::buildFigureName) ?: ""),
                            "imgWidth" to 120,
                        )
                    }
                    .sortedWith(
                        compareBy<Map<String, Any?>>(
                            { it["artifactStyle"]?.toString().orEmpty() },
                            { it["artifactSubStyle"]?.toString().orEmpty() },
                            { it["artifactStage"]?.toString().orEmpty() },
                        )
                    )
                mapOf(
                    "text" to cellItems.mapNotNull { it["text"]?.toString()?.takeIf(String::isNotBlank) }.distinct().joinToString("\n"),
                    "items" to cellItems,
                    "rowspan" to 1,
                    "colspan" to 1,
                )
            }
            mapOf(
                "phase" to phase,
                "section" to section,
                "cells" to cells,
            )
        }

        return JsonUtils.toJson(
            mapOf(
                "cols" to cols,
                "rows" to tableRows,
                "layout" to "A4_LANDSCAPE",
            )
        ) ?: "{}"
    }

    /**
     * 标型器候选：编辑器在某行（器型）的"式"单元格选标型器时调用。
     *
     * @param stage 可选，进一步过滤指定"式"（如"Ⅱ式"）
     */
    fun typicalArtifacts(
        projectId: String,
        artifactType: String,
        material: String?,
        stage: String?,
        artifactStyle: String?,
        artifactSubStyle: String?,
        phaseName: String?,
        sectionName: String?,
    ): List<WmsTypicalArtifactVo> {
        val rows = artifactService.queryForTypology(projectId, material, null)
            .filter { it.artifactType == artifactType }
            .filter { stage.isNullOrBlank() || it.artifactStage == stage }
            .filter { artifactStyle.isNullOrBlank() || it.artifactStyle == artifactStyle }
            .filter { artifactSubStyle.isNullOrBlank() || it.artifactSubStyle == artifactSubStyle }
            .filter { phaseName.isNullOrBlank() || it.phaseName == phaseName }
            .filter { sectionName.isNullOrBlank() || it.sectionName == sectionName }

        return rows.map { r ->
            val images = resolveLineImages(r.artifactId).ifEmpty {
                r.lineDiagram?.takeIf(String::isNotBlank)?.let { listOf(it) } ?: emptyList()
            }
            WmsTypicalArtifactVo(
                artifactId = r.artifactId,
                code = r.code,
                name = r.name,
                artifactType = r.artifactType,
                artifactStyle = r.artifactStyle,
                artifactStage = r.artifactStage,
                projectCellName = r.projectCellName,
                lineImages = images,
                figureName = buildFigureName(r),
            )
        }
    }

    /**
     * 自动分期统计：每类器物分几型/几式/几期/几段，用于"智能分期"预览。
     */
    fun autoStat(projectId: String, material: String?): List<WmsTypologyAutoStat> {
        val rows = artifactService.queryForTypology(projectId, material, null)
        return buildStats(rows)
    }

    private fun buildStats(rows: List<WmsTypologyArtifactRow>): List<WmsTypologyAutoStat> =
        rows
            .filter { !it.artifactType.isNullOrBlank() }
            .groupBy { it.artifactType!! }
            .map { (type, items) ->
                WmsTypologyAutoStat(
                    artifactType = type,
                    total = items.size.toLong(),
                    styleCount = items.mapNotNull { it.artifactStyle?.takeIf(String::isNotBlank) }.distinct().size,
                    stageCount = items.mapNotNull { it.artifactStage?.takeIf(String::isNotBlank) }.distinct().size,
                    phaseCount = items.mapNotNull { it.phaseName?.takeIf(String::isNotBlank) }.distinct().size,
                    sectionCount = items.mapNotNull { it.sectionName?.takeIf(String::isNotBlank) }.distinct().size,
                )
            }
            .sortedByDescending { it.total }

    private fun buildPeriodRows(rows: List<WmsTypologyArtifactRow>): List<Pair<String, String>> =
        rows
            .map { it.phaseName.orEmpty() to it.sectionName.orEmpty() }
            .distinct()
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })

    /** 线图文件：contentId 约定为 lineDiagram_{artifactId} */
    private fun resolveLineImages(artifactId: String): List<String> =
        runCatching { fileService.findAllByContent("lineDiagram_$artifactId").map { it.path } }
            .getOrDefault(emptyList())

    private fun resolveLineImage(r: WmsTypologyArtifactRow): String =
        resolveLineImages(r.artifactId).firstOrNull()
            ?: r.lineDiagram.orEmpty()

    /** 图名：式(单位) 如 "Ⅱ式(M:23)" */
    private fun buildFigureName(r: WmsTypologyArtifactRow): String {
        val unit = r.projectCellName ?: r.code ?: ""
        val stage = r.artifactStage ?: ""
        return if (unit.isBlank()) stage else "$stage($unit)"
    }

    private fun buildCellText(r: WmsTypologyArtifactRow): String =
        listOfNotNull(
            r.artifactStyle?.takeIf(String::isNotBlank),
            r.artifactSubStyle?.takeIf(String::isNotBlank),
            r.artifactStage?.takeIf(String::isNotBlank),
        ).joinToString(" ")

    private fun buildCellText(key: TypologyStyleKey): String =
        listOf(key.artifactStyle, key.artifactSubStyle, key.artifactStage)
            .filter(String::isNotBlank)
            .joinToString(" ")

    private fun defaultTitle(vo: WmsTypologyTableVo): String {
        val parts = listOfNotNull(vo.projectName, vo.categoryName ?: vo.material, "型式分期表")
        return parts.joinToString(" ")
    }

    private data class TypologyCellKey(
        val phase: String,
        val section: String,
        val artifactType: String,
    )

    private data class TypologyStyleKey(
        val artifactStyle: String,
        val artifactSubStyle: String,
        val artifactStage: String,
    )
}
