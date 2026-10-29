package io.hizhengfu.cloud.museum.service

import EnumLayerType
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.*
import io.hizhengfu.cloud.museum.domain.AddPointPosition
import io.hizhengfu.cloud.museum.domain.Point
import io.hizhengfu.cloud.museum.domain.UpdatePointPosition
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["MUSEUM"], keyGenerator = "keyGenerator")
class MuLayerService : CrudService<MuLayerDao>(MuLayerDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muLayer: MuLayer): MuLayerDao {
        val userId = SecurityUtils.getUserId()
        //查询是否存在同一个展馆的图层
        val resultRow = MuLayers.selectAll()
            .where { MuLayers.roomId.eq(muLayer.roomId) and MuLayers.type.eq(muLayer.type!!) }.firstOrNull()
        if (resultRow != null) throw BadRequestException("同一个展馆只能创建一个同类型图层。")

        return dao.new {
            name = muLayer.name!!
            roomId = muLayer.roomId
            type = muLayer.type!!
            path = muLayer.path
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muLayer: MuLayer): MuLayerDao {
        val id = muLayer.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muLayerDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muLayer.name.isNullOrBlank()) {
            muLayerDao.name = muLayer.name
        }
        if (!muLayer.roomId.isNullOrBlank()) {
            muLayerDao.roomId = muLayer.roomId
        }
        muLayer.type?.let {
            muLayerDao.type = muLayer.type
        }
        if (!muLayer.path.isNullOrBlank()) {
            muLayerDao.path = muLayer.path
        }

        muLayerDao.updateTime = LocalDateTime.now()
        muLayerDao.updateBy = userId ?: "anonymous"
        return muLayerDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuLayerDao {
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
    fun copy(id: String): MuLayerDao {
        val muLayer = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muLayer.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muLayer: MuLayer): GenericPage<ResultRow>? {
        val query = MuLayers.join(MuRooms, JoinType.LEFT) {
            MuLayers.roomId eq MuRooms.id
        }.select(MuLayers.columns + MuRooms.name)
        if (!muLayer.name.isNullOrBlank()) {
            query.andWhere {
                MuLayers.name like "%${muLayer.name}%"
            }
        }
        muLayer.type?.let {
            query.andWhere {
                MuLayers.type eq muLayer.type
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuLayers)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuLayerDao? {
        return super.findById(id)
    }

    /**
     * 列出对应图层的点位
     * */
    fun listPoint(layerId: String, isDesign: Boolean = false): List<Point> {
        val layerDao = get(layerId) ?: return emptyList()
        val query = if (layerDao.type == EnumLayerType.POSITION) {
            MuPointPositions.selectAll().where { MuPointPositions.layerId eq layerId }
        } else {
            MuDevices.join(MuPointPositions, JoinType.LEFT) {
                MuPointPositions.deviceId.eq(MuDevices.id) and MuPointPositions.layerId.eq(layerId)
            }.select(MuPointPositions.columns + MuDevices.name + MuDevices.serialNo + MuDevices.addr + MuDevices.type + MuDevices.id)
        }

        if (!isDesign) {
            query.andWhere {
                MuPointPositions.layerId.eq(layerId)
            }
        }

        return if (layerDao.type == EnumLayerType.POSITION) {
            query.toList().map { row ->
                Point(
                        id = row[MuPointPositions.id]?.value,
                        name = row[MuPointPositions.deviceId],
                        deviceId = row[MuPointPositions.deviceId],
                        scene = row[MuPointPositions.scene],
                        x = row[MuPointPositions.x],
                        y = row[MuPointPositions.y],
                        z = row[MuPointPositions.z],
                )
            }
        } else {
            query.toList().map { row ->
                Point(
                        id = row[MuPointPositions.id]?.value,
                        name = row[MuDevices.name],
                        type = row[MuDevices.type],
                        deviceId = row[MuDevices.id].value,
                        addr = row[MuDevices.addr],
                        serialNo = row[MuDevices.serialNo],
                        scene = row[MuPointPositions.scene],
                        x = row[MuPointPositions.x],
                        y = row[MuPointPositions.y],
                        z = row[MuPointPositions.z],
                )
            }
        }
    }

    @CacheEvict(allEntries = true)
    fun addPoint(params: AddPointPosition): String {
        return MuPointPositions.insertAndGetId {
            it[layerId] = params.layerId
            it[deviceId] = params.deviceId
            it[scene] = params.scene
            it[x] = params.x
            it[y] = params.y
            it[z] = params.z
        }.value
    }

    @CacheEvict(allEntries = true)
    fun removePoint(id: String) {
        MuPointPositions.deleteWhere { MuPointPositions.id eq id }
    }

    /**
     * 更新位置
     * */
    @CacheEvict(allEntries = true)
    fun updatePoint(params: UpdatePointPosition) {
        MuPointPositions.update({ MuPointPositions.id eq params.id }) {
            it[x] = params.x
            it[y] = params.y
            it[z] = params.z
        }
    }
}
