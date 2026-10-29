package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.system.service.DictService
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.WmsProjectTree
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsProjectCellService(val fileService: FileService, val dictService: DictService) :
    CrudService<WmsProjectCellDao>(WmsProjectCellDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsProjectCell: WmsProjectCellVo): WmsProjectCellDao {
        val userId = SecurityUtils.getUserId()
        val wmsProjectCellDao = dao.new {
            name = wmsProjectCell.name!!
            projectId = wmsProjectCell.projectId!!
            plotId = wmsProjectCell.plotId
            image = wmsProjectCell.image
            code = wmsProjectCell.code
            remarks = wmsProjectCell.remarks
            type = wmsProjectCell.type
            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsProjectCell.files?.forEach {
            fileService.insertFileContent(it, wmsProjectCellDao.id?.value)
        }

        return wmsProjectCellDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsProjectCell: WmsProjectCellVo): WmsProjectCellDao {
        val id = wmsProjectCell.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsProjectCellDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsProjectCellDao.name = wmsProjectCell.name!!
        wmsProjectCellDao.projectId = wmsProjectCell.projectId!!
        wmsProjectCellDao.plotId = wmsProjectCell.plotId
        wmsProjectCellDao.image = wmsProjectCell.image
        wmsProjectCellDao.code = wmsProjectCell.code
        wmsProjectCellDao.remarks = wmsProjectCell.remarks
        wmsProjectCellDao.type = wmsProjectCell.type
        wmsProjectCellDao.updateBy = userId ?: "anonymous"
        wmsProjectCellDao.updateTime = LocalDateTime.now()
        return wmsProjectCellDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsProjectCellDao {
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
    fun copy(id: String): WmsProjectCellDao {
        val wmsProjectCell = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsProjectCell.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsProjectCell: WmsProjectCellVo): GenericPage<ResultRow> {
        val query = WmsProjectCells.join(WmsProjects, JoinType.LEFT) {
            WmsProjectCells.projectId eq WmsProjects.id
        }.join(WmsProjectPlots, JoinType.LEFT) {
            WmsProjectPlots.id eq WmsProjectCells.plotId
        }.select(
            listOf(
                *WmsProjectCells.columns.toTypedArray(),
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectPlots.name
            )
        )

        if (!wmsProjectCell.name.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.name like "%${wmsProjectCell.name}%"
            }
        }
        if (!wmsProjectCell.projectId.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.projectId.eq(wmsProjectCell.projectId)
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsProjectCells.sort, SortOrder.ASC)
        } else {
            buildPageDslSort(query, page, WmsProjectCells)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): WmsProjectCellDao? {
        return super.findById(id)
    }

    /**
     * 根据项目ID查询遗迹id
     * */
    @Cacheable
    fun findByProjectIdAndCellId(projectIdOrName: String, cellIdOrName: String): WmsProjectCellDao? {
        val cellDao = WmsProjectCellDao.find {
            (WmsProjectCells.projectId.eq(projectIdOrName) or WmsProjectCells.name.eq(projectIdOrName)) and
                    (WmsProjectCells.id.eq(cellIdOrName) or WmsProjectCells.name.eq(cellIdOrName))
        }.singleOrNull()

        return cellDao
    }

    /**
     * 根据项目ID查询遗迹id
     * */
    @Cacheable
    fun findOrCreateByProjectIdAndCell(projectId: String, cellName: String): WmsProjectCellDao {
        var cellDao = WmsProjectCellDao.find {
            WmsProjectCells.projectId.eq(projectId) and WmsProjectCells.name.eq(cellName)
        }.singleOrNull()

        if (cellDao == null) {
            cellDao = create(
                WmsProjectCellVo(
                    projectId = projectId,
                    name = cellName,
                    type = cellName.takeWhile { !it.isDigit() })
            )
        }

        return cellDao
    }

    /**
     * 与探方关联的id
     * */
    @Cacheable
    fun findByPlotId(plotId: String): List<String> {
        val query = WmsProjectCells.select(
            WmsProjectCells.id
        ).andWhere {
            WmsProjectCells.plotId.eq(plotId)
        }
        return query.map { it[WmsProjectCells.id].value }
    }

    /**
     * 更新探方与遗迹的关系
     * */
    @CacheEvict(allEntries = true)
    fun updatePlotId(plotId: String, ids: List<String>) {
        val list = WmsProjectCellDao.find { WmsProjectCells.id.inList(ids) }
        list.forEach {
            it.plotId = plotId
        }
    }


    @Cacheable
    fun tree(projectId: String, plotId: String? = null): List<WmsProjectTree>? {
        val query = WmsProjectCells.join(WmsProjectPlots, JoinType.LEFT) {
            WmsProjectCells.plotId eq WmsProjectPlots.id
        }.select(
            WmsProjectCells.id,
            WmsProjectCells.projectId,
            WmsProjectCells.plotId,
            WmsProjectCells.type,
            WmsProjectCells.name,
        ).andWhere {
            WmsProjectCells.projectId.eq(projectId)
        }
        if (!plotId.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.plotId.eq(plotId)
            }
        }

        query.orderBy(WmsProjectCells.sort, SortOrder.ASC)

        val cellTypeMap = dictService.findByType("wms_project_cell_type").associate { it.value to it.label }

        val list = query.map { row ->
            WmsProjectTree(
                id = row[WmsProjectCells.id].value,
                plotId = row[WmsProjectCells.plotId],
                name = row[WmsProjectCells.name],
                type = cellTypeMap[row[WmsProjectCells.type]] ?: "未知",
                projectId = row[WmsProjectCells.projectId],
            )
        }

        return list.groupBy { it.type }.map { (key, value) ->
            WmsProjectTree(
                id = key,
                name = key,
                type = "CELL_TYPE",
                projectId = projectId,
                children = value,
                checkboxDisabled = true,
                cellCount = 1
            )
        }
    }

    /**
     * 更新排序
     * */
    @CacheEvict(allEntries = true)
    fun updateSort(sorts: List<GenericUpdateSort>) {
        sorts.forEach {
            val tempDao = dao.findById(it.id) ?: throw BadRequestException("列无效")
            tempDao.sort = it.sort
        }
    }
}
