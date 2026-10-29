package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuAlarmRule
import io.hizhengfu.cloud.museum.dao.MuAlarmRuleDao
import io.hizhengfu.cloud.museum.dao.MuAlarmRules
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
class MuAlarmRuleService : CrudService<MuAlarmRuleDao>(MuAlarmRuleDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muAlarmRule: MuAlarmRule): MuAlarmRuleDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            type = muAlarmRule.type!!
            value = muAlarmRule.value
            content = muAlarmRule.content
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            status = muAlarmRule.status!!
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muAlarmRule: MuAlarmRule): MuAlarmRuleDao {
        val id = muAlarmRule.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muAlarmRuleDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        muAlarmRule.type?.let {
            muAlarmRuleDao.type = it
        }
        muAlarmRule.value?.let {
            muAlarmRuleDao.value = it
        }
        muAlarmRule.content?.let {
            muAlarmRuleDao.content = it
        }
        muAlarmRule.status?.let {
            muAlarmRuleDao.status = it
        }
        return muAlarmRuleDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuAlarmRuleDao {
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
    fun copy(id: String): MuAlarmRuleDao {
        val muAlarmRule = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muAlarmRule.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muAlarmRule: MuAlarmRule): GenericPage<ResultRow>? {
        val query = MuAlarmRules.selectAll()
        muAlarmRule.type?.let {
            query.andWhere {
                MuAlarmRules.type eq it
            }
        }
        muAlarmRule.status?.let {
            query.andWhere {
                MuAlarmRules.status eq it
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuAlarmRules)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuAlarmRuleDao? {
        return super.findById(id)
    }
}
