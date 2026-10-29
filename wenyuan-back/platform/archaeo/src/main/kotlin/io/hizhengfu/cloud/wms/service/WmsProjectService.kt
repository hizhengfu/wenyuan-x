package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.UniqueCodeGeneratorUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.sys.system.dao.Groups
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
class WmsProjectService(val fileService: FileService) : CrudService<WmsProjectDao>(WmsProjectDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsProject: WmsProjectVo): WmsProjectDao {
        val userId = SecurityUtils.getUserId()

        val code =
            if (wmsProject.code.isNullOrBlank()) UniqueCodeGeneratorUtils.generate("KG") else wmsProject.code


        val wmsProjectDao = dao.new {
            name = wmsProject.name!!
            image = wmsProject.image
            this.code = code
            groupId = wmsProject.groupId
            leader = wmsProject.leader!!
            location = wmsProject.location!!
            startDate = wmsProject.startDate
            endDate = wmsProject.endDate
            lat = wmsProject.lat
            lng = wmsProject.lng
            remarks = wmsProject.remarks
            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsProject.files?.forEach {
            fileService.insertFileContent(it, wmsProjectDao.id?.value)
        }

        return wmsProjectDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsProject: WmsProjectVo): WmsProjectDao {
        val id = wmsProject.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsProjectDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsProjectDao.name = wmsProject.name!!
        wmsProjectDao.image = wmsProject.image
        wmsProjectDao.code = wmsProject.code!!
        wmsProjectDao.groupId = wmsProject.groupId
        wmsProjectDao.leader = wmsProject.leader!!
        wmsProjectDao.location = wmsProject.location!!
        wmsProjectDao.startDate = wmsProject.startDate
        wmsProjectDao.endDate = wmsProject.endDate
        wmsProjectDao.lat = wmsProject.lat
        wmsProjectDao.lng = wmsProject.lng
        wmsProjectDao.remarks = wmsProject.remarks
        wmsProjectDao.updateBy = userId ?: "anonymous"
        wmsProjectDao.updateTime = LocalDateTime.now()
        return wmsProjectDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsProjectDao {
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
    fun copy(id: String): WmsProjectDao {
        val wmsProject = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsProject.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsProject: WmsProjectVo): GenericPage<ResultRow> {
        val query = WmsProjects.join(Groups, JoinType.LEFT) {
            WmsProjects.groupId eq Groups.id
        }.select(listOf(*WmsProjects.columns.toTypedArray(), Groups.id, Groups.name))

        if (!wmsProject.name.isNullOrBlank()) {
            query.andWhere {
                WmsProjects.name like "%${wmsProject.name}%"
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsProjects.createTime, SortOrder.ASC)
        } else {
            buildPageDslSort(query, page, WmsProjects)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): WmsProjectDao? {
        return super.findById(id)
    }

    @Cacheable
    fun findByIdOrName(idOrName: String): WmsProjectDao? {
        return WmsProjectDao.find { WmsProjects.id.eq(idOrName) or WmsProjects.name.eq(idOrName) }.singleOrNull()
    }


    /**
     * 项目树
     * * */
    @Cacheable
    fun tree(id: String?, groupId: String? = null): List<WmsProjectTree> {
        val cellCount = WmsProjectCells.id.countDistinct()
        val plotCount = WmsProjectPlots.id.countDistinct()
        val query = WmsProjects.join(WmsProjectPlots, JoinType.LEFT) {
            WmsProjectPlots.projectId.eq(WmsProjects.id)
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsProjectCells.projectId.eq(WmsProjects.id)
        }.join(Groups, JoinType.LEFT) {
            WmsProjects.groupId.eq(Groups.id)
        }.select(
            WmsProjects.id,
            WmsProjects.name,
            cellCount,
            plotCount
        )

        query.groupBy(WmsProjects.id, WmsProjects.name)

        query.orderBy(WmsProjects.name)

        if (!id.isNullOrBlank()) {
            query.where {
                WmsProjects.id.eq(id)
            }
        }

        if (!groupId.isNullOrBlank()) {
            query.where {
                WmsProjects.groupId.eq(groupId) or Groups.parentIds.like("%,${groupId},%")
            }
        }

        val projects = query.map { row ->
            WmsProjectTree(
                id = row[WmsProjects.id].value,
                name = row[WmsProjects.name],
                projectId = row[WmsProjects.id].value,
                type = "PROJECT",
                plotCount = row[plotCount],
                cellCount = row[cellCount]
            )
        }

        return projects
    }
}
