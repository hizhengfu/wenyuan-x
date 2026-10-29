package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.service.FileService
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
class WmsProjectPlotService(val fileService: FileService) : CrudService<WmsProjectPlotDao>(WmsProjectPlotDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsProjectPlot: WmsProjectPlotVo): WmsProjectPlotDao {
        val userId = SecurityUtils.getUserId()
        val wmsProjectPlotDao = dao.new {
            name = wmsProjectPlot.name!!
            projectId = wmsProjectPlot.projectId!!
            image = wmsProjectPlot.image
            code = wmsProjectPlot.code
            remarks = wmsProjectPlot.remarks
            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsProjectPlot.files?.forEach {
            fileService.insertFileContent(it, wmsProjectPlotDao.id?.value)
        }

        return wmsProjectPlotDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsProjectPlot: WmsProjectPlotVo): WmsProjectPlotDao {
        val id = wmsProjectPlot.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsProjectPlotDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsProjectPlotDao.name = wmsProjectPlot.name!!
        wmsProjectPlotDao.projectId = wmsProjectPlot.projectId!!
        wmsProjectPlotDao.image = wmsProjectPlot.image
        wmsProjectPlotDao.code = wmsProjectPlot.code
        wmsProjectPlotDao.remarks = wmsProjectPlot.remarks
        wmsProjectPlotDao.updateBy = userId ?: "anonymous"
        wmsProjectPlotDao.updateTime = LocalDateTime.now()
        return wmsProjectPlotDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsProjectPlotDao {
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
    fun copy(id: String): WmsProjectPlotDao {
        val wmsProjectPlot = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsProjectPlot.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsProjectPlot: WmsProjectPlotVo): GenericPage<ResultRow> {
        val query = WmsProjectPlots.join(WmsProjects, JoinType.LEFT) {
            WmsProjectPlots.projectId eq WmsProjects.id
        }.select(listOf(*WmsProjectPlots.columns.toTypedArray(), WmsProjects.id, WmsProjects.name))

        if (!wmsProjectPlot.name.isNullOrBlank()) {
            query.andWhere {
                WmsProjectPlots.name like "%${wmsProjectPlot.name}%"
            }
        }
        if (!wmsProjectPlot.projectId.isNullOrBlank()) {
            query.andWhere {
                WmsProjectPlots.projectId.eq(wmsProjectPlot.projectId)
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsProjectPlots.createTime, SortOrder.ASC)
        } else {
            buildPageDslSort(query, page, WmsProjectPlots)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): WmsProjectPlotDao? {
        return super.findById(id)
    }



    @Cacheable
    fun tree(projectId: String): List<WmsProjectTree>? {
        val cellCount = WmsProjectCells.id.count()
        val query = WmsProjectPlots.join(WmsProjectCells, JoinType.LEFT) {
            WmsProjectCells.plotId eq WmsProjectPlots.id
        }.select(
            WmsProjectPlots.id,
            WmsProjectPlots.projectId,
            WmsProjectPlots.name,
            cellCount
        ).andWhere {
            WmsProjectCells.projectId.eq(projectId)
        }

        query.groupBy(WmsProjectPlots.id, WmsProjectPlots.projectId, WmsProjectPlots.name)
        query.orderBy(WmsProjectPlots.name, SortOrder.ASC)

        return query.map { row ->
            WmsProjectTree(
                id = row[WmsProjectPlots.id].value,
                name = row[WmsProjectPlots.name],
                projectId = row[WmsProjectPlots.projectId],
                type = "PLOT",
                cellCount = row[cellCount]
            )
        }
    }
}
