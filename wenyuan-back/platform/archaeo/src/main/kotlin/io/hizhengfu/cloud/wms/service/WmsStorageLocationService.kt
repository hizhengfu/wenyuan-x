package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.UniqueCodeGeneratorUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.wms.dao.WmsStorageLocationDao
import io.hizhengfu.cloud.wms.dao.WmsStorageLocationVo
import io.hizhengfu.cloud.wms.dao.WmsStorageLocations
import io.hizhengfu.cloud.wms.dao.WmsStorageRooms
import io.hizhengfu.cloud.wms.domain.EnumStorageStatus
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
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
class WmsStorageLocationService(val fileService: FileService) :
    CrudService<WmsStorageLocationDao>(WmsStorageLocationDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsStorageLocation: WmsStorageLocationVo): WmsStorageLocationDao {
        val userId = SecurityUtils.getUserId()
        val code =
            if (wmsStorageLocation.code.isNullOrBlank()) UniqueCodeGeneratorUtils.generate("WZ") else wmsStorageLocation.code

        val wmsStorageLocationDao = dao.new {
            roomId = wmsStorageLocation.roomId!!
            name = wmsStorageLocation.name!!
            this.code = code
            capacity = wmsStorageLocation.capacity
            currentCount = wmsStorageLocation.currentCount
            remarks = wmsStorageLocation.remarks
            status = wmsStorageLocation.status ?: EnumStorageStatus.USABLE
            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsStorageLocation.files?.forEach {
            fileService.insertFileContent(it, wmsStorageLocationDao.id?.value)
        }

        return wmsStorageLocationDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsStorageLocation: WmsStorageLocationVo): WmsStorageLocationDao {
        val id = wmsStorageLocation.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsStorageLocationDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsStorageLocationDao.roomId = wmsStorageLocation.roomId!!
        wmsStorageLocationDao.name = wmsStorageLocation.name!!
        wmsStorageLocationDao.code = wmsStorageLocation.code!!
        wmsStorageLocationDao.capacity = wmsStorageLocation.capacity
        wmsStorageLocationDao.currentCount = wmsStorageLocation.currentCount
        wmsStorageLocationDao.remarks = wmsStorageLocation.remarks
        wmsStorageLocationDao.status = wmsStorageLocation.status ?: EnumStorageStatus.USABLE
        wmsStorageLocationDao.updateBy = userId ?: "anonymous"
        wmsStorageLocationDao.updateTime = LocalDateTime.now()
        return wmsStorageLocationDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsStorageLocationDao {
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
    fun copy(id: String): WmsStorageLocationDao {
        val wmsStorageLocation = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsStorageLocation.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsStorageLocation: WmsStorageLocationVo): GenericPage<ResultRow> {
        val query = WmsStorageLocations.join(WmsStorageRooms, JoinType.LEFT) {
            WmsStorageLocations.roomId eq WmsStorageRooms.id
        }.select(listOf(*WmsStorageLocations.columns.toTypedArray(), WmsStorageRooms.id, WmsStorageRooms.name))

        wmsStorageLocation.status?.let {
            query.andWhere {
                WmsStorageLocations.status eq it
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsStorageLocations.createTime, SortOrder.ASC)
        } else {
            buildPageDslSort(query, page, WmsStorageLocations)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): WmsStorageLocationDao? {
        return super.findById(id)
    }
}
