package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.UniqueCodeGeneratorUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.wms.dao.WmsStorageAreaDao
import io.hizhengfu.cloud.wms.dao.WmsStorageAreaVo
import io.hizhengfu.cloud.wms.dao.WmsStorageAreas
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsStorageAreaService(val fileService: FileService) : CrudService<WmsStorageAreaDao>(WmsStorageAreaDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsStorageArea: WmsStorageAreaVo): WmsStorageAreaDao {
        val userId = SecurityUtils.getUserId()
        val code =
            if (wmsStorageArea.code.isNullOrBlank()) UniqueCodeGeneratorUtils.generate("KQ") else wmsStorageArea.code

        val wmsStorageAreaDao = dao.new {
            this.code = code
            image = wmsStorageArea.image
            name = wmsStorageArea.name!!
            manager = wmsStorageArea.manager
            managerPhone = wmsStorageArea.managerPhone
            location = wmsStorageArea.location
            lat = wmsStorageArea.lat
            lng = wmsStorageArea.lng
            remarks = wmsStorageArea.remarks
            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsStorageArea.files?.forEach {
            fileService.insertFileContent(it, wmsStorageAreaDao.id?.value)
        }

        return wmsStorageAreaDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsStorageArea: WmsStorageAreaVo): WmsStorageAreaDao {
        val id = wmsStorageArea.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsStorageAreaDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsStorageAreaDao.code = wmsStorageArea.code!!
        wmsStorageAreaDao.image = wmsStorageArea.image
        wmsStorageAreaDao.name = wmsStorageArea.name!!
        wmsStorageAreaDao.manager = wmsStorageArea.manager
        wmsStorageAreaDao.managerPhone = wmsStorageArea.managerPhone
        wmsStorageAreaDao.location = wmsStorageArea.location
        wmsStorageAreaDao.lat = wmsStorageArea.lat
        wmsStorageAreaDao.lng = wmsStorageArea.lng
        wmsStorageAreaDao.remarks = wmsStorageArea.remarks
        wmsStorageAreaDao.updateBy = userId ?: "anonymous"
        wmsStorageAreaDao.updateTime = LocalDateTime.now()
        return wmsStorageAreaDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsStorageAreaDao {
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
    fun copy(id: String): WmsStorageAreaDao {
        val wmsStorageArea = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsStorageArea.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsStorageArea: WmsStorageAreaVo): GenericPage<ResultRow> {
        val query = WmsStorageAreas.selectAll()
        if (!wmsStorageArea.name.isNullOrBlank()) {
            query.andWhere {
                WmsStorageAreas.name like "%${wmsStorageArea.name}%"
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsStorageAreas.createTime, SortOrder.ASC)
        } else {
            buildPageDslSort(query, page, WmsStorageAreas)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): WmsStorageAreaDao? {
        return super.findById(id)
    }
}
