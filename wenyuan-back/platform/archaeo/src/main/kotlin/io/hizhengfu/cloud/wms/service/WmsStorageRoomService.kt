package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.UniqueCodeGeneratorUtils
import io.hizhengfu.cloud.sys.file.service.FileService
import io.hizhengfu.cloud.wms.dao.WmsStorageAreas
import io.hizhengfu.cloud.wms.dao.WmsStorageRoomDao
import io.hizhengfu.cloud.wms.dao.WmsStorageRoomVo
import io.hizhengfu.cloud.wms.dao.WmsStorageRooms
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
class WmsStorageRoomService(val fileService: FileService) : CrudService<WmsStorageRoomDao>(WmsStorageRoomDao, "WMS") {

    @CacheEvict(allEntries = true)
    fun create(wmsStorageRoom: WmsStorageRoomVo): WmsStorageRoomDao {
        val userId = SecurityUtils.getUserId()
        val code =
            if (wmsStorageRoom.code.isNullOrBlank()) UniqueCodeGeneratorUtils.generate("KF") else wmsStorageRoom.code

        val wmsStorageRoomDao = dao.new {
            areaId = wmsStorageRoom.areaId
            this.code = code
            image = wmsStorageRoom.image
            name = wmsStorageRoom.name!!
            temperatureRange = wmsStorageRoom.temperatureRange
            humidityRange = wmsStorageRoom.humidityRange
            securityLevel = wmsStorageRoom.securityLevel

            location = wmsStorageRoom.location
            lat = wmsStorageRoom.lat
            lng = wmsStorageRoom.lng
            remarks = wmsStorageRoom.remarks

            createBy = userId ?: "anonymous"
            createTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
        }

        wmsStorageRoom.files?.forEach {
            fileService.insertFileContent(it, wmsStorageRoomDao.id?.value)
        }

        return wmsStorageRoomDao
    }

    @CacheEvict(allEntries = true)
    fun update(wmsStorageRoom: WmsStorageRoomVo): WmsStorageRoomDao {
        val id = wmsStorageRoom.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val wmsStorageRoomDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        wmsStorageRoomDao.areaId = wmsStorageRoom.areaId
        wmsStorageRoomDao.code = wmsStorageRoom.code!!
        wmsStorageRoomDao.image = wmsStorageRoom.image
        wmsStorageRoomDao.name = wmsStorageRoom.name!!
        wmsStorageRoomDao.temperatureRange = wmsStorageRoom.temperatureRange
        wmsStorageRoomDao.humidityRange = wmsStorageRoom.humidityRange
        wmsStorageRoomDao.securityLevel = wmsStorageRoom.securityLevel
        wmsStorageRoomDao.location = wmsStorageRoom.location
        wmsStorageRoomDao.lat = wmsStorageRoom.lat
        wmsStorageRoomDao.lng = wmsStorageRoom.lng
        wmsStorageRoomDao.remarks = wmsStorageRoom.remarks
        wmsStorageRoomDao.updateBy = userId ?: "anonymous"
        wmsStorageRoomDao.updateTime = LocalDateTime.now()
        return wmsStorageRoomDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsStorageRoomDao {
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
    fun copy(id: String): WmsStorageRoomDao {
        val wmsStorageRoom = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(wmsStorageRoom.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsStorageRoom: WmsStorageRoomVo): GenericPage<ResultRow> {
        val query = WmsStorageRooms.join(WmsStorageAreas, JoinType.LEFT) {
            WmsStorageRooms.areaId eq WmsStorageAreas.id
        }.select(listOf(*WmsStorageRooms.columns.toTypedArray(), WmsStorageAreas.id, WmsStorageAreas.name))

        if (!wmsStorageRoom.name.isNullOrBlank()) {
            query.andWhere {
                WmsStorageRooms.name like "%${wmsStorageRoom.name}%"
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsStorageRooms.createTime, SortOrder.ASC)
        } else {
            buildPageDslSort(query, page, WmsStorageRooms)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): WmsStorageRoomDao? {
        return super.findById(id)
    }
}
