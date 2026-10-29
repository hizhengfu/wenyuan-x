package io.hizhengfu.cloud.museum.service

import EnumTaskType
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.HttpClientUtils
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuEvent
import io.hizhengfu.cloud.museum.dao.MuEventDao
import io.hizhengfu.cloud.museum.dao.MuEvents
import io.hizhengfu.cloud.museum.dao.MuTasks
import io.hizhengfu.cloud.museum.domain.*
import io.hizhengfu.cloud.museum.utils.dispatch
import io.hizhengfu.cloud.museum.utils.px2physics
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import io.hizhengfu.cloud.common.extend.scale
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["MUSEUM"], keyGenerator = "keyGenerator")
class MuEventService(
        val muTaskService: MuTaskService,
        val muAlarmEventService: MuAlarmEventService,
        val muDeviceService: MuDeviceService,
        @Value("\${robotUrl:http://101.133.156.32:8800}") val robotUrl: String,
) : CrudService<MuEventDao>(MuEventDao, "MUSEUM") {

    val logger: Logger = LoggerFactory.getLogger("MuEventService")

    @CacheEvict(allEntries = true)
    fun create(muEvent: MuEvent): MuEventDao {
        val userId = SecurityUtils.getUserId()

        if (!muEvent.code.isNullOrBlank()) {
            val count = MuEvents.selectAll().where { MuEvents.code.eq(muEvent.code) }.count()
            if (count > 0) {
                findByIdOrCodeDao(muEvent.code) ?: throw BadRequestException("异常事件代码重复")
            }
        }

        return dao.new {
            name = muEvent.name
            code = muEvent.code
            type = muEvent.type
            eventType = muEvent.eventType
            dateTime = muEvent.dateTime
            taskId = muEvent.taskId!!
            description = muEvent.description
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muEvent: MuEvent): MuEventDao {
        val id = muEvent.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muEventDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muEvent.name.isNullOrBlank()) {
            muEventDao.name = muEvent.name
        }
        if (!muEvent.code.isNullOrBlank()) {
            val existDao = findByIdOrCodeDao(muEvent.code)
            if (existDao != null && existDao.id != muEventDao.id) {
                throw BadRequestException("异常事件代码重复")
            }
            muEventDao.code = muEvent.code
        }
        muEvent.type?.let {
            muEventDao.type = it
        }
        muEvent.eventType?.let {
            muEventDao.eventType = it
        }
        muEvent.dateTime?.let {
            muEventDao.dateTime = it
        }
        if (!muEvent.taskId.isNullOrBlank()) {
            muEventDao.taskId = muEvent.taskId
        }
        if (!muEvent.description.isNullOrBlank()) {
            muEventDao.description = muEvent.description
        }
        return muEventDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuEventDao {
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
    fun copy(id: String): MuEventDao {
        val muEvent = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muEvent.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muEvent: MuEvent): GenericPage<ResultRow>? {
        val query = MuEvents.join(MuTasks, JoinType.LEFT) {
            MuTasks.id eq MuEvents.taskId
        }.select(MuEvents.columns + MuTasks.name)
        if (!muEvent.name.isNullOrBlank()) {
            query.andWhere {
                MuEvents.name eq muEvent.name
            }
        }
        muEvent.type?.let {
            query.andWhere {
                MuEvents.type eq it
            }
        }
        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuEvents)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuEventDao? {
        return super.findById(id)
    }

    @Cacheable
    fun findByIdOrCodeDao(id: String): MuEventDao? {
        return MuEventDao.find { MuEvents.id.eq(id) or MuEvents.code.eq(id) }.firstOrNull()
    }

    /**
     * 获取待执行任务
     * */
    fun findWaitProcess(): List<MuEventDao> {
        val query = MuEvents.select(MuEvents.id).where { MuEvents.eventType.eq(EnumTaskType.USUAL) and MuEvents.dateTime.lessEq(LocalDateTime.now()) and MuEvents.finalDateTime.isNull() }
        return MuEventDao.wrapRows(query).toList()
    }

    @CacheEvict
    fun updateEventFinalDateTime(id: String) {
        MuEvents.update({ MuEvents.id.eq(id) }) {
            it[finalDateTime] = LocalDateTime.now()
        }
    }

    @CacheEvict
    fun resetEventFinalDateTime(id: String) {
        MuEvents.update({ MuEvents.id.eq(id) }) {
            it[finalDateTime] = null
        }
    }

    /**
     * 执行事件定义
     * @param id 事件代码或code
     * @param deviceAddr 设备地址
     * @param alarmId 预警工单id
     * */
    fun handTask(id: String, robots: List<RobotWrap>, deviceAddr: String? = null, taskInfo: TaskInfo? = null, alarmId: String? = null) {
        val eventData = MuEvents.selectAll().where { MuEvents.id.eq(id) or MuEvents.code.eq(id) }.firstOrNull() ?: return

        //获取任务列表
        val taskList = muTaskService.listTaskDetail(eventData[MuEvents.taskId])

        if (taskList.isEmpty()) return

        val taskInfoList = mutableListOf<TaskInfo>()

        //获取第一个有坐标值的任务
        var firstPoint: Coordinate? = null

        //根据设备地址安装位置调度到指定位置
        if (!deviceAddr.isNullOrBlank()) {
            //获取第一个有坐标值的任务
            val devicePosition = muDeviceService.positionByAddr(deviceAddr)
            val x = devicePosition?.x
            val y = devicePosition?.y
            if (y != null && x != null) {
                val point = px2physics(y, x)

                val r = if (deviceAddr == "Raster_0001") {
                    160.0
                } else {
                    30.0
                }

                taskInfoList.add(TaskInfo(task_type = "position", task_order = TaskOrder(x = point.x?.scale(2), y = point.y?.scale(2), rotation = r)))
            }
        }
        //有预定义任务加入任务到执行队列
        if (taskInfo != null) {
            taskInfoList.add(taskInfo)
        }

        //构建任务数据
        taskList.forEach {
            var point = Coordinate(x = it.x, y = it.y)
            //当非一期数据的情况下需要转换坐标为有效坐标
            if (it.x != null && it.y != null) {
                if (it.fromFirst != true) {
                    point = px2physics(it.y, it.x)
                }
            }
            if (firstPoint == null) {
                firstPoint = point
            }
            taskInfoList.add(TaskInfo(task_type = it.type.name.lowercase(), task_order = TaskOrder(
                    x = point.x?.scale(2), y = point.y?.scale(2), rotation = it.r?.scale(2) ?: 0.0, text = it.text,
                    before_hint = it.preHint,
                    explain_way = it.inTheWay,
                    after_hint = it.finalHint,
                    explain = it.commentary,
            )))
        }

        //对异常任务增加alert结束字符串
        if (eventData[MuEvents.eventType] == EnumTaskType.EVENT) {
            taskInfoList.add(TaskInfo(task_type = "alert", task_order = "alert"))
        }

        //判断是否是一期导入数据
        val robot = dispatch(robots, Coordinate(x = firstPoint?.x, y = firstPoint?.y))
        val taskData = TaskWrap(robot_sn = robot.robot_sn, task_info = taskInfoList)
        logger.info("推送数据：{}", JsonUtils.toJson(taskData))
        pushTask(taskData, alarmId)
    }

    /**
     * 推送任务到机器人
     * */
    fun pushTask(taskData: TaskWrap, alarmId: String? = null) {
        HttpClientUtils.post("${robotUrl}/api/task/", data = taskData) { json ->
            logger.info("推送任务成功到机器人：{}，返回数据：{}", taskData, json)
            val resp = JsonUtils.fromJson<TaskRespWrap>(json)
            resp?.let {
                if (!alarmId.isNullOrBlank()) {
                    muAlarmEventService.updateEventId(alarmId, it.task_id)
                }
            }
        }
    }
}
