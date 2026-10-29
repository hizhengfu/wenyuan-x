package io.hizhengfu.cloud.museum.service

import EnumLayerType
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.*
import io.hizhengfu.cloud.museum.domain.Coordinate
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
@CacheConfig(cacheNames = ["MUSEUM"], keyGenerator = "keyGenerator")
class MuDeviceService : CrudService<MuDeviceDao>(MuDeviceDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muDevice: MuDevice): MuDeviceDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muDevice.name!!
            type = muDevice.type
            description = muDevice.description
            serialNo = muDevice.serialNo
            addr = muDevice.addr
            roomId = muDevice.roomId
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muDevice: MuDevice): MuDeviceDao {
        val id = muDevice.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muDeviceDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muDevice.name.isNullOrBlank()) {
            muDeviceDao.name = muDevice.name
        }
        muDevice.type?.let {
            muDeviceDao.type = it
        }
        if (!muDevice.description.isNullOrBlank()) {
            muDeviceDao.description = muDevice.description
        }
        if (!muDevice.serialNo.isNullOrBlank()) {
            muDeviceDao.serialNo = muDevice.serialNo
        }
        if (!muDevice.addr.isNullOrBlank()) {
            muDeviceDao.addr = muDevice.addr
        }
        if (!muDevice.roomId.isNullOrBlank()) {
            muDeviceDao.roomId = muDevice.roomId
        }
        return muDeviceDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuDeviceDao {
        return super.delete(id)
    }

    /**
     * 批量删除
     * */
    @CacheEvict(allEntries = true)
    fun delete(ids: List<String>): Int {
        return super.deleteByIds(ids)
    }


    @CacheEvict(allEntries = true)
    fun copy(id: String): MuDeviceDao {
        val muDevice = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muDevice.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muDevice: MuDevice): GenericPage<ResultRow>? {
        val query = MuDevices.join(MuRooms, JoinType.LEFT) {
            MuDevices.roomId eq MuRooms.id
        }.select(MuDevices.columns + MuRooms.name)
        if (!muDevice.name.isNullOrBlank()) {
            query.andWhere {
                MuDevices.name eq muDevice.name
            }
        }
        muDevice.type?.let {
            query.andWhere {
                MuDevices.type eq it
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuDevices)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuDeviceDao? {
        return super.findById(id)
    }

    @Cacheable
    fun findByIdOrCode(id: String): MuDeviceDao? {
        return dao.find { MuDevices.id.eq(id) or MuDevices.addr.lowerCase().eq(id.lowercase()) }.firstOrNull()
    }


    /**
     * 根据设备地址获取平面安装位置
     * */
    @Cacheable
    fun positionByAddr(deviceAddr: String): Coordinate? {
        val query = MuDevices.join(MuPointPositions, JoinType.LEFT) {
            MuDevices.id.eq(MuPointPositions.deviceId)
        }.join(MuLayers, JoinType.LEFT) {
            MuLayers.id.eq(MuPointPositions.layerId)
        }.select(MuPointPositions.columns).where {
            MuLayers.type.eq(EnumLayerType.PLANE) and MuDevices.addr.eq(deviceAddr)
        }.firstOrNull() ?: return null
        return Coordinate(x = query[MuPointPositions.x], y = query[MuPointPositions.y])
    }
}
