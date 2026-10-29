package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuVisitor
import io.hizhengfu.cloud.museum.dao.MuVisitorDao
import io.hizhengfu.cloud.museum.dao.MuVisitors
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
class MuVisitorService : CrudService<MuVisitorDao>(MuVisitorDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muVisitor: MuVisitor): MuVisitorDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muVisitor.name!!
            idCard = muVisitor.idCard
            image = muVisitor.image
            vipLevel = muVisitor.vipLevel
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            gender = muVisitor.gender
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muVisitor: MuVisitor): MuVisitorDao {
        val id = muVisitor.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muVisitorDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muVisitor.name.isNullOrBlank()) {
            muVisitorDao.name = muVisitor.name
        }
        if (!muVisitor.idCard.isNullOrBlank()) {
            muVisitorDao.idCard = muVisitor.idCard
        }
        if (!muVisitor.image.isNullOrBlank()) {
            muVisitorDao.image = muVisitor.image
        }
        muVisitor.vipLevel?.let {
            muVisitorDao.vipLevel = it
        }
        muVisitor.gender?.let {
            muVisitorDao.gender = it
        }
        return muVisitorDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuVisitorDao {
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
    fun copy(id: String): MuVisitorDao {
        val muVisitor = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muVisitor.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muVisitor: MuVisitor): GenericPage<ResultRow>? {
        val query = MuVisitors.selectAll()
        if (!muVisitor.name.isNullOrBlank()) {
            query.andWhere {
                MuVisitors.name eq muVisitor.name
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuVisitors)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuVisitorDao? {
        return super.findById(id)
    }
}
