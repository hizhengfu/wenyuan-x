package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuRobot
import io.hizhengfu.cloud.museum.dao.MuRobotDao
import io.hizhengfu.cloud.museum.dao.MuRobots
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
class MuRobotService : CrudService<MuRobotDao>(MuRobotDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muRobot: MuRobot): MuRobotDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muRobot.name!!
            serialNo = muRobot.serialNo
            factory = muRobot.factory
            version = muRobot.version
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muRobot: MuRobot): MuRobotDao {
        val id = muRobot.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muRobotDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muRobot.name.isNullOrBlank()) {
            muRobotDao.name = muRobot.name
        }
        if (!muRobot.serialNo.isNullOrBlank()) {
            muRobotDao.serialNo = muRobot.serialNo
        }
        if (!muRobot.factory.isNullOrBlank()) {
            muRobotDao.factory = muRobot.factory
        }
        if (!muRobot.version.isNullOrBlank()) {
            muRobotDao.version = muRobot.version
        }
        return muRobotDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuRobotDao {
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
    fun copy(id: String): MuRobotDao {
        val muRobot = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muRobot.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muRobot: MuRobot): GenericPage<ResultRow>? {
        val query = MuRobots.selectAll()
        if (!muRobot.name.isNullOrBlank()) {
            query.andWhere {
                MuRobots.name eq muRobot.name
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuRobots)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuRobotDao? {
        return super.findById(id)
    }
}
