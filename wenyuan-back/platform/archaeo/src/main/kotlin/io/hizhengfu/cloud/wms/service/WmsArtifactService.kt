package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.extend.toLocalDateTime
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.UniqueCodeGeneratorUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.system.service.DictService
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.EnumArtifactUnitType
import io.hizhengfu.cloud.wms.domain.WmsArtifactModel
import io.hizhengfu.cloud.wms.domain.WmsArtifactParams
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import io.hizhengfu.cloud.wms.domain.WmsTypologyArtifactRow
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsArtifactService(
    val fileService: FileService,
    val dictService: DictService,
    val projectService: WmsProjectService,
    val projectCellService: WmsProjectCellService
) : CrudService<WmsArtifactDao>(WmsArtifactDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsArtifact: WmsArtifactVo): WmsArtifactDao {
        val userId = SecurityUtils.getUserId()
        val code =
            if (wmsArtifact.code.isNullOrBlank()) UniqueCodeGeneratorUtils.generate("WW") else wmsArtifact.code

        val wmsArtifactDao = dao.new {
            image = wmsArtifact.image
            name = wmsArtifact.name!!
            projectId = wmsArtifact.projectId!!
            this.code = code
            categoryId = wmsArtifact.categoryId
            material = wmsArtifact.material!!
            quantity = wmsArtifact.quantity!!
            quantityUnit = wmsArtifact.quantityUnit!!
            preservation = wmsArtifact.preservation
            unearthDate = wmsArtifact.unearthDate
            location = wmsArtifact.location
            projectCellId = wmsArtifact.projectCellId
            coordinate = wmsArtifact.coordinate
            lat = wmsArtifact.lat
            lng = wmsArtifact.lng
            remarks = wmsArtifact.remarks
            recorderBy = wmsArtifact.recorderBy
            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsArtifact.files?.forEach {
            fileService.insertFileContent(it, wmsArtifactDao.id?.value)
        }

        return wmsArtifactDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsArtifact: WmsArtifactVo): WmsArtifactDao {
        val id = wmsArtifact.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsArtifactDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsArtifactDao.image = wmsArtifact.image
        wmsArtifactDao.name = wmsArtifact.name!!
        wmsArtifactDao.projectId = wmsArtifact.projectId!!
        wmsArtifactDao.code = wmsArtifact.code
        wmsArtifactDao.categoryId = wmsArtifact.categoryId!!
        wmsArtifactDao.material = wmsArtifact.material!!
        wmsArtifactDao.quantity = wmsArtifact.quantity!!
        wmsArtifactDao.quantityUnit = wmsArtifact.quantityUnit!!

        wmsArtifactDao.preservation = wmsArtifact.preservation
        wmsArtifactDao.unearthDate = wmsArtifact.unearthDate
        wmsArtifactDao.location = wmsArtifact.location
        wmsArtifactDao.projectCellId = wmsArtifact.projectCellId
        wmsArtifactDao.coordinate = wmsArtifact.coordinate
        wmsArtifactDao.lat = wmsArtifact.lat
        wmsArtifactDao.lng = wmsArtifact.lng
        wmsArtifactDao.remarks = wmsArtifact.remarks
        wmsArtifactDao.recorderBy = wmsArtifact.recorderBy
        wmsArtifactDao.updateBy = userId ?: "anonymous"
        wmsArtifactDao.updateTime = LocalDateTime.now()
        return wmsArtifactDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsArtifactDao {
        return super.delete(id)
    }

    /**
     * 批量删除
     * */
    @CacheEvict(allEntries = true)
    fun delete(ids: List<String>): Int {
        return super.deleteByIds(ids)
    }

    /**
     * 复制
     * */
    @CacheEvict(allEntries = true)
    fun copy(id: String): WmsArtifactDao {
        val wmsArtifact = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsArtifact.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsArtifact: WmsArtifactVo): GenericPage<ResultRow> {
        val query = WmsArtifacts.join(WmsArtifactExtends, JoinType.LEFT) {
            WmsArtifacts.id eq WmsArtifactExtends.id
        }.join(WmsProjects, JoinType.LEFT) {
            WmsArtifacts.projectId eq WmsProjects.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.select(
            listOf(
                *WmsArtifacts.columns.toTypedArray(),
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectCells.name,
            )
        )

        if (!wmsArtifact.name.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.name like "%${wmsArtifact.name}%"
            }
        }
        if (!wmsArtifact.code.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.code.eq(wmsArtifact.code)
            }
        }
        if (!wmsArtifact.material.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.material.inList(wmsArtifact.material.split(","))
            }
        }

        if (!wmsArtifact.projectId.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.projectId.eq(wmsArtifact.projectId)
            }
        }

        if (!wmsArtifact.projectPlotId.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.plotId.inList(wmsArtifact.projectPlotId.split(","))
            }
        }

        if (!wmsArtifact.projectCellType.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.type.inList(wmsArtifact.projectCellType.split(","))
            }
        }

        if (!wmsArtifact.projectCellId.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.projectCellId.inList(wmsArtifact.projectCellId.split(","))
            }
        }
        wmsArtifact.status?.let {
            query.andWhere {
                WmsArtifacts.status.eq(it)
            }
        }
        if (wmsArtifact.unearthDateList?.isNotEmpty() == true) {
            wmsArtifact.unearthDateList.let {
                query.andWhere {
                    WmsArtifacts.unearthDate.between(it.first(), it.last())
                }
            }
        }

        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsArtifacts.createTime, SortOrder.DESC)
        } else {
            buildPageDslSort(query, page, WmsArtifacts)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): ResultRow? {
        val query = WmsArtifacts.join(WmsArtifactStocks, JoinType.LEFT) {
            WmsArtifactStocks.id eq WmsArtifacts.id
        }.join(WmsProjects, JoinType.LEFT) {
            WmsArtifacts.projectId eq WmsProjects.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.join(WmsStorageLocations, JoinType.LEFT) {
            WmsArtifactStocks.locationId eq WmsStorageLocations.id
        }.select(
            listOf(
                *WmsArtifacts.columns.toTypedArray(),
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectCells.name,
                WmsStorageLocations.name
            )
        ).where { WmsArtifacts.id.eq(id) }.singleOrNull()
        return query
    }

    /**
     * 批量导入
     * */
    @CacheEvict(allEntries = true)
    fun batchImport(list: List<WmsArtifactModel>): Int {
        val jwtUser = SecurityUtils.getUserDetail()

        val projectMap = mutableMapOf<String, String>()
        val projectCellMap = mutableMapOf<String, String>()

        var count = 0

        list.groupBy { it.project }.forEach { (project, listProject) ->
            if (!project.isNullOrBlank()) {
                projectService.findByIdOrName(project)?.let { projectDao ->
                    val projectId = projectDao.id.value
                    projectMap[project] = projectId

                    //给导入遗址id赋值
                    listProject.forEach { p ->
                        p.projectId = projectId

                        val projectCellName = p.projectCell

                        if (!projectCellName.isNullOrBlank()) {
                            val key = "${projectId}_${projectCellName}"
                            if (projectCellMap.containsKey(key)) {
                                p.projectCellId = projectCellMap[key]
                            } else {
                                projectCellService.findOrCreateByProjectIdAndCell(projectId, projectCellName)
                                    .let { projectCellDao ->
                                        val projectCellId = projectCellDao.id.value
                                        projectCellMap[key] = projectCellId
                                        p.projectCellId = projectCellId
                                    }
                            }
                        }
                    }
                }
            }
        }

        list.find { it.projectId == null }?.let {
            throw BadRequestException("未找到[${it.project}]遗址配置，请配置后再导入")
        }
        list.find { it.projectCell.isNullOrBlank() }?.let {
            throw BadRequestException("遗址单位不能为空，请修改后再导入")
        }
        list.find { it.name.isNullOrBlank() }?.let {
            throw BadRequestException("文物名称不能为空")
        }

        val materialDict = dictService.findByType("wms_artifact_material_type").associate { it.label to it.value }
        list.find { materialDict[it.material] == null }?.let {
            throw BadRequestException("[${it.material}]质地字典未建立，请先建立字典后再导入")
        }

        val preserveDict = dictService.findByType("wms_artifact_preserve_status").associate { it.label to it.value }
        list.find { preserveDict[it.preservation] == null }?.let {
            throw BadRequestException("[${it.preservation}]出土状态字典未建立，请先建立字典后再导入")
        }

//        val unitDict = dictService.findByType("wms_artifact_unit_type").associate { it.label to it.value }
//        list.find { unitDict[it.quantityUnit] == null }?.let {
//            throw BadRequestException("[${it.quantityUnit}]数量单位字典未建立，当前已有字典${unitDict.keys.joinToString(",")}请先建立字典后再导入")
//        }

        WmsArtifacts.batchInsert(list) {
            this[WmsArtifacts.name] = it.name!!
            this[WmsArtifacts.code] = it.code
            this[WmsArtifacts.projectId] = it.projectId!!
            this[WmsArtifacts.projectCellId] = it.projectCellId
            this[WmsArtifacts.material] = materialDict[it.material]!!//转换字典为值
            this[WmsArtifacts.quantity] = it.quantity ?: 1
            this[WmsArtifacts.quantityUnit] = EnumArtifactUnitType.PIECE.description
            this[WmsArtifacts.unearthDate] = it.unearthDate?.toLocalDateTime()
            this[WmsArtifacts.coordinate] = it.coordinate
            this[WmsArtifacts.preservation] = it.preservation
            this[WmsArtifacts.recorderBy] = it.recorderBy
            this[WmsArtifacts.remarks] = it.remarks

            this[WmsArtifacts.createTime] = LocalDateTime.now()
            this[WmsArtifacts.createBy] = jwtUser.id
            count++
        }

        return count
    }


    /**
     * 为型式分期智能拼装提供的轻量查询：
     *
     * 拉出指定遗址（必填）+ 材质或大类（可选）的全部文物以及型式字段。
     */
    @Cacheable
    fun queryForTypology(projectId: String, material: String?, categoryName: String?): List<WmsTypologyArtifactRow> {
        val q = WmsArtifacts.join(WmsArtifactExtends, JoinType.LEFT) {
            WmsArtifacts.id eq WmsArtifactExtends.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.select(
            WmsArtifacts.id,
            WmsArtifacts.code,
            WmsArtifacts.name,
            WmsArtifacts.material,
            WmsProjectCells.name,
            WmsArtifactExtends.artifactType,
            WmsArtifactExtends.artifactStyle,
            WmsArtifactExtends.artifactSubStyle,
            WmsArtifactExtends.artifactStage,
            WmsArtifactExtends.phaseName,
            WmsArtifactExtends.sectionName,
            WmsArtifactExtends.lineDiagram,
        ).andWhere { WmsArtifacts.projectId eq projectId }

        if (!material.isNullOrBlank()) q.andWhere { WmsArtifacts.material eq material }

        return q.map { row ->
            WmsTypologyArtifactRow(
                artifactId = row[WmsArtifacts.id].value,
                code = row[WmsArtifacts.code],
                name = row[WmsArtifacts.name],
                material = row[WmsArtifacts.material],
                projectCellName = runCatching { row[WmsProjectCells.name] }.getOrNull(),
                artifactType = row[WmsArtifactExtends.artifactType],
                artifactStyle = row[WmsArtifactExtends.artifactStyle],
                artifactSubStyle = row[WmsArtifactExtends.artifactSubStyle],
                artifactStage = row[WmsArtifactExtends.artifactStage],
                phaseName = row[WmsArtifactExtends.phaseName],
                sectionName = row[WmsArtifactExtends.sectionName],
                lineDiagram = row[WmsArtifactExtends.lineDiagram],
            )
        }
    }

    /**
     * 获取扩展数据
     * */
    fun getExtend(artifactId: String): ResultRow? {
        return WmsArtifactExtends.selectAll().where { WmsArtifactExtends.id.eq(artifactId) }.singleOrNull()
    }

    /**
     * 更新或存储扩展信息
     * */
    @CacheEvict(allEntries = true)
    fun saveExtend(rawParams: WmsArtifactParams) {
        val params = rawParams.normalized()
        val count = WmsArtifactExtends.selectAll().where { WmsArtifactExtends.id.eq(params.artifactId) }.count()

        if (count > 0) {
            WmsArtifactExtends.update({ WmsArtifactExtends.id.eq(params.artifactId) }) {
                it[timePeriod] = params.timePeriod
                it[culturalAttribute] = params.culturalAttribute
                it[purpose] = params.purpose
                it[purposeL1] = params.purposeL1
                it[purposeL2] = params.purposeL2
                it[purposeL3] = params.purposeL3
                it[shape] = params.shape
                it[decoration] = params.decoration
                it[craftingTechnique] = params.craftingTechnique
                it[lineDiagram] = params.lineDiagram
                it[artifactType] = params.artifactType
                it[dimensions] = params.dimensions
                it[weight] = params.weight
                it[potteryQuality] = params.potteryQuality
                it[potteryColor] = params.potteryColor
                it[kilnSite] = params.kilnSite
                it[kilnSystem] = params.kilnSystem
                it[glazeColor] = params.glazeColor
                it[markText] = params.markText
                it[lithology] = params.lithology
                it[inscription] = params.inscription
                it[species] = params.species
                it[artifactStyle] = params.artifactStyle
                it[artifactSubStyle] = params.artifactSubStyle
                it[artifactStage] = params.artifactStage
                it[phaseName] = params.phaseName
                it[sectionName] = params.sectionName
                it[extra] = params.extra
            }
        } else {
            WmsArtifactExtends.insert {
                it[id] = params.artifactId
                it[timePeriod] = params.timePeriod
                it[culturalAttribute] = params.culturalAttribute
                it[purpose] = params.purpose
                it[purposeL1] = params.purposeL1
                it[purposeL2] = params.purposeL2
                it[purposeL3] = params.purposeL3
                it[shape] = params.shape
                it[decoration] = params.decoration
                it[craftingTechnique] = params.craftingTechnique
                it[lineDiagram] = params.lineDiagram
                it[artifactType] = params.artifactType
                it[dimensions] = params.dimensions
                it[weight] = params.weight
                it[potteryQuality] = params.potteryQuality
                it[potteryColor] = params.potteryColor
                it[kilnSite] = params.kilnSite
                it[kilnSystem] = params.kilnSystem
                it[glazeColor] = params.glazeColor
                it[markText] = params.markText
                it[lithology] = params.lithology
                it[inscription] = params.inscription
                it[species] = params.species
                it[artifactStyle] = params.artifactStyle
                it[artifactSubStyle] = params.artifactSubStyle
                it[artifactStage] = params.artifactStage
                it[phaseName] = params.phaseName
                it[sectionName] = params.sectionName
                it[extra] = params.extra
            }
        }
    }
}
