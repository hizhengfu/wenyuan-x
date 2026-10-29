package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuQuestion
import io.hizhengfu.cloud.museum.dao.MuQuestionDao
import io.hizhengfu.cloud.museum.dao.MuQuestions
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
class MuQuestionService : CrudService<MuQuestionDao>(MuQuestionDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muQuestion: MuQuestion): MuQuestionDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            title = muQuestion.title!!
            keywords = muQuestion.keywords
            content = muQuestion.content
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            updateTime = LocalDateTime.now()
            updateBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muQuestion: MuQuestion): MuQuestionDao {
        val id = muQuestion.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muQuestionDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muQuestion.title.isNullOrBlank()) {
            muQuestionDao.title = muQuestion.title
        }
        if (!muQuestion.keywords.isNullOrBlank()) {
            muQuestionDao.keywords = muQuestion.keywords
        }
        if (!muQuestion.content.isNullOrBlank()) {
            muQuestionDao.content = muQuestion.content
        }
        muQuestionDao.updateTime = LocalDateTime.now()
        muQuestionDao.updateBy = userId ?: "anonymous"
        return muQuestionDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuQuestionDao {
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
    fun copy(id: String): MuQuestionDao {
        val muQuestion = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muQuestion.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muQuestion: MuQuestion): GenericPage<ResultRow>? {
        val query = MuQuestions.selectAll()
        if (!muQuestion.title.isNullOrBlank()) {
            query.andWhere {
                MuQuestions.title eq muQuestion.title
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuQuestions)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuQuestionDao? {
        return super.findById(id)
    }
}
