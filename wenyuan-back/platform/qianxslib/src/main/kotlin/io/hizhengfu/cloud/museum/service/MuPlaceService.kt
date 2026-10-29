package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuPlace
import io.hizhengfu.cloud.museum.dao.MuPlaceDao
import io.hizhengfu.cloud.museum.dao.MuPlaces
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
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
@CacheConfig(cacheNames = ["MUSEUM"], keyGenerator = "keyGenerator")
class MuPlaceService : CrudService<MuPlaceDao>(MuPlaceDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muPlace: MuPlace): MuPlaceDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muPlace.name!!
            image = muPlace.image
            description = muPlace.description
            address = muPlace.address
            lat = muPlace.lat
            lng = muPlace.lng
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muPlace: MuPlace): MuPlaceDao {
        val id = muPlace.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muPlaceDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muPlace.name.isNullOrBlank()) {
            muPlaceDao.name = muPlace.name
        }
        if (!muPlace.image.isNullOrBlank()) {
            muPlaceDao.image = muPlace.image
        }
        if (!muPlace.description.isNullOrBlank()) {
            muPlaceDao.description = muPlace.description
        }
        if (!muPlace.address.isNullOrBlank()) {
            muPlaceDao.address = muPlace.address
        }
        if (!muPlace.lat.isNullOrBlank()) {
            muPlaceDao.lat = muPlace.lat
        }
        if (!muPlace.lng.isNullOrBlank()) {
            muPlaceDao.lng = muPlace.lng
        }
        muPlaceDao.updateTime = LocalDateTime.now()
        muPlaceDao.updateBy = userId ?: "anonymous"
        return muPlaceDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuPlaceDao {
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
    fun copy(id: String): MuPlaceDao {
        val muPlace = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muPlace.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muPlace: MuPlace): GenericPage<ResultRow>? {
        val query = MuPlaces.selectAll()
        if (!muPlace.name.isNullOrBlank()) {
            query.andWhere {
                MuPlaces.name eq muPlace.name
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuPlaces)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuPlaceDao? {
        return super.findById(id)
    }
}
