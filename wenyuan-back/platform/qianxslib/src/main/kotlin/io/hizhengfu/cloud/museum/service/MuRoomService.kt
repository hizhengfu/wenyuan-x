package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuPlaces
import io.hizhengfu.cloud.museum.dao.MuRoom
import io.hizhengfu.cloud.museum.dao.MuRoomDao
import io.hizhengfu.cloud.museum.dao.MuRooms
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
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
class MuRoomService : CrudService<MuRoomDao>(MuRoomDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muRoom: MuRoom): MuRoomDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muRoom.name!!
            image = muRoom.image
            placeId = muRoom.placeId
            serialNo = muRoom.serialNo
            description = muRoom.description
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muRoom: MuRoom): MuRoomDao {
        val id = muRoom.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muRoomDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muRoom.name.isNullOrBlank()) {
            muRoomDao.name = muRoom.name
        }
        if (!muRoom.image.isNullOrBlank()) {
            muRoomDao.image = muRoom.image
        }
        if (!muRoom.placeId.isNullOrBlank()) {
            muRoomDao.placeId = muRoom.placeId
        }
        if (!muRoom.serialNo.isNullOrBlank()) {
            muRoomDao.serialNo = muRoom.serialNo
        }
        if (!muRoom.description.isNullOrBlank()) {
            muRoomDao.description = muRoom.description
        }
        muRoomDao.updateTime = LocalDateTime.now()
        muRoomDao.updateBy = userId ?: "anonymous"
        return muRoomDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuRoomDao {
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
    fun copy(id: String): MuRoomDao {
        val muRoom = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muRoom.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muRoom: MuRoom): GenericPage<ResultRow>? {
        val query = MuRooms.join(MuPlaces, JoinType.LEFT) {
            MuRooms.placeId eq MuPlaces.id
        }.select(MuRooms.columns + MuPlaces.name)
        if (!muRoom.name.isNullOrBlank()) {
            query.andWhere {
                MuRooms.name like "%${muRoom.name}%"
            }
        }
        if (!muRoom.serialNo.isNullOrBlank()) {
            query.andWhere {
                MuRooms.serialNo like "%${muRoom.serialNo}%"
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuRooms)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuRoomDao? {
        return super.findById(id)
    }
}
