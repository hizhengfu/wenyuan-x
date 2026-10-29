package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.*
import io.hizhengfu.cloud.museum.domain.TaskDetailParam
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
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
class MuTaskService : CrudService<MuTaskDao>(MuTaskDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muTask: MuTask): MuTaskDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muTask.name
            description = muTask.description
//            robotId = muTask.robotId
            roomId = muTask.roomId
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muTask: MuTask): MuTaskDao {
        val id = muTask.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muTaskDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muTask.name.isNullOrBlank()) {
            muTaskDao.name = muTask.name
        }
        if (!muTask.description.isNullOrBlank()) {
            muTaskDao.description = muTask.description
        }
//        if (!muTask.robotId.isNullOrBlank()) {
//            muTaskDao.robotId = muTask.robotId
//        }
        if (!muTask.roomId.isNullOrBlank()) {
            muTaskDao.roomId = muTask.roomId
        }
        return muTaskDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuTaskDao {
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
    fun copy(id: String): MuTaskDao {
        val muTask = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muTask.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muTask: MuTask): GenericPage<ResultRow>? {
        val query = MuTasks.join(MuRooms, JoinType.LEFT) {
            MuTasks.roomId.eq(MuRooms.id)
        }.select(MuTasks.columns + MuRooms.name)
        if (!muTask.name.isNullOrBlank()) {
            query.andWhere {
                MuTasks.name eq muTask.name
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuTasks)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuTaskDao? {
        return super.findById(id)
    }


    /**
     * 任务详情列表
     * */
    @Cacheable
    fun listTaskDetail(taskId: String): List<MuTaskDetail> {
        val query = MuTaskDetails.join(MuTours, JoinType.LEFT) {
            MuTaskDetails.tourId eq MuTours.id
        }.selectAll()
        query.orderBy(Pair(MuTaskDetails.sort, SortOrder.ASC))
        query.andWhere {
            MuTaskDetails.taskId eq taskId
        }
        return query.toList().map { MuTaskDetails.toModel(it) }
    }

    /**
     * 创建任务详情
     * */
    @CacheEvict(allEntries = true)
    fun addTaskDetail(param: TaskDetailParam): String {
        return MuTaskDetails.insertAndGetId {
            it[taskId] = param.taskId
            it[type] = param.type
            it[tourId] = param.tourId
            it[text] = param.text
            it[x] = param.x
            it[y] = param.y
            it[z] = param.z
            it[r] = param.r
            it[sort] = param.sort ?: 0
            it[createBy] = SecurityUtils.getUserId()
            it[createTime] = LocalDateTime.now()
        }.value
    }

    /**
     * 更新任务详情
     * */
    @CacheEvict(allEntries = true)
    fun saveTaskDetail(params: List<TaskDetailParam>) {
        params.forEach { param ->
            val id = param.id
            if (id.isNullOrBlank()) {
                MuTaskDetails.insertAndGetId {
                    it[taskId] = param.taskId
                    it[type] = param.type
                    it[tourId] = param.tourId
                    it[text] = param.text
                    it[x] = param.x
                    it[y] = param.y
                    it[z] = param.z
                    it[r] = param.r
                    it[sort] = param.sort ?: 0
                    it[createBy] = SecurityUtils.getUserId()
                    it[createTime] = LocalDateTime.now()
                }
            } else {
                MuTaskDetails.update({ MuTaskDetails.id eq id }) {
                    it[type] = param.type
                    it[tourId] = param.tourId
                    it[text] = param.text
                    it[x] = param.x
                    it[y] = param.y
                    it[z] = param.z
                    it[r] = param.r
                    it[sort] = param.sort ?: 0
                }
            }
        }
    }

    @CacheEvict(allEntries = true)
    fun updateSort(sorts: List<GenericUpdateSort>) {
        sorts.forEach { temp ->
            MuTaskDetails.update({ MuTaskDetails.id eq temp.id }) {
                it[sort] = temp.sort.toInt()
            }
        }
    }

    /**
     * 删除任务
     * */
    @CacheEvict(allEntries = true)
    fun deleteTaskDetail(id: String) {
        MuTaskDetails.deleteWhere { MuTaskDetails.id eq id }
    }
}
