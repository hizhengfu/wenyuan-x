package io.hizhengfu.cloud.museum.service

import CONFIG_EVENT_TIMEOUT
import EnumAlarmRuleType
import EnumAlarmStatus
import EnumCommandType
import TOPIC_ROBOT
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.*
import io.hizhengfu.cloud.museum.domain.AlarmEventParam
import io.hizhengfu.cloud.museum.domain.EventCommand
import io.hizhengfu.cloud.sys.system.service.ConfigService
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["MUSEUM"], keyGenerator = "keyGenerator")
class MuAlarmEventService(
        val muDeviceService: MuDeviceService,
        val configService: ConfigService,
        val simpMessagingTemplate: SimpMessagingTemplate,
) : CrudService<MuAlarmDao>(MuAlarmDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muAlarm: MuAlarm): MuAlarmDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            dateTime = muAlarm.dateTime
            level = muAlarm.level
            code = muAlarm.code
            value = muAlarm.value
            content = muAlarm.content
            deviceId = muAlarm.deviceId
            createTime = LocalDateTime.now()
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muAlarm: MuAlarm): MuAlarmDao {
        val id = muAlarm.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muAlarmDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        muAlarm.dateTime?.let {
            muAlarmDao.dateTime = it
        }
        muAlarm.level?.let {
            muAlarmDao.level = it
        }
        if (!muAlarm.code.isNullOrBlank()) {
            muAlarmDao.code = muAlarm.code
        }
        if (!muAlarm.value.isNullOrBlank()) {
            muAlarmDao.value = muAlarm.value
        }
        if (!muAlarm.content.isNullOrBlank()) {
            muAlarmDao.content = muAlarm.content
        }
        if (!muAlarm.deviceId.isNullOrBlank()) {
            muAlarmDao.deviceId = muAlarm.deviceId
        }
        return muAlarmDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuAlarmDao {
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
    fun copy(id: String): MuAlarmDao {
        val muAlarm = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muAlarm.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muAlarm: MuAlarm): GenericPage<ResultRow>? {
        val query = MuAlarmEvents.join(MuDevices, JoinType.LEFT) {
            MuAlarmEvents.deviceId eq MuDevices.id
        }.select(MuAlarmEvents.columns + MuDevices.name)

        if (!muAlarm.deviceId.isNullOrBlank()) {
            query.andWhere {
                MuAlarmEvents.deviceId eq muAlarm.deviceId
            }
        }
        if (!muAlarm.dateTimeRange.isNullOrEmpty()) {
            query.andWhere {
                MuAlarmEvents.dateTime inList muAlarm.dateTimeRange
            }
        }

        muAlarm.status?.let {
            query.andWhere {
                MuAlarmEvents.status eq it
            }
        }

        val page = GenericPage<ResultRow>().build(request)

        //默认倒序排列
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(MuAlarmEvents.dateTime, SortOrder.DESC)
        } else {
            buildPageDslSort(query, page, MuAlarmEvents)
        }
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuAlarmDao? {
        return super.findById(id)
    }

    /**
     * 删除所有
     * */
    @CacheEvict
    fun delete(): Int {
        return MuAlarmEvents.deleteAll()
    }

    /**
     * 存储事件，当同设备事件未处理的情况下不多次存储
     * @return 异常id
     * */
    fun saveEvent(addr: String, code: String, message: String, dateTime: LocalDateTime): String? {
        val deviceDao = muDeviceService.findByIdOrCode(addr) ?: return null

        var id: String? = null

        val count = MuAlarmEvents.selectAll()
            .where { MuAlarmEvents.deviceId.eq(deviceDao.id.value) and MuAlarmEvents.status.neq(EnumAlarmStatus.FINISH) }
            .count().toInt()
        //当没有待执行的预警数据时，创建预警
        if (count == 0) {
            create(MuAlarm(deviceId = deviceDao.id.value, code = code, content = message, dateTime = dateTime)).let {
                id = it.id.value
            }
        }
        return id
    }

    /**
     * 更新任务id
     * */
    fun updateEventId(id: String, eventId: String) {
        findById(id)?.let {
            it.eventId = eventId
        }
    }

    /**
     * 存储人流预警事件
     * */
    fun savePeopleEvent(peoples: Int): Int {
        val deviceDao = muDeviceService.findByIdOrCode(EnumAlarmRuleType.PEOPLE.name) ?: return -1

        val query = MuAlarmRules.selectAll().where { MuAlarmRules.type.eq(EnumAlarmRuleType.PEOPLE) }.firstOrNull() ?: return 0

        val alarmValue = query[MuAlarmRules.value]?.toInt()
        if (peoples > alarmValue!!) {
            val count = MuAlarmEvents.selectAll()
                .where { MuAlarmEvents.deviceId.eq(deviceDao.id.value) and MuAlarmEvents.status.neq(EnumAlarmStatus.FINISH) }
                .count().toInt()
            //当没有待执行的预警数据时，创建预警
            if (count == 0) {
                create(MuAlarm(deviceId = deviceDao.id.value, code = "100", content = "人员聚集:在馆人数${peoples}超过阈值${alarmValue}，请及时巡查处理！", dateTime = LocalDateTime.now()))
            }
            return count
        }

        return 0
    }

    /**
     * 超时自动关闭
     * */
    fun timeoutDisposeEvent() {
        val timeout = configService.findByName(CONFIG_EVENT_TIMEOUT)?.content?.toLong() ?: 5
        //对超出超时时间的数据进行处置
        val query = MuAlarmEvents.selectAll().where {
            MuAlarmEvents.dateTime.lessEq(
                LocalDateTime.now().minusMinutes(timeout)
            ) and MuAlarmEvents.status.neq(
                EnumAlarmStatus.FINISH
            )
        }
        val now = LocalDateTime.now()
        val userId = SecurityUtils.getUserId()

        val alarmList = MuAlarmDao.wrapRows(query)
        if (alarmList.count() > 0) {
            transaction {
                alarmList.forEach { alarmEventDao ->
                    //更新异常处置记录
                    alarmEventDao.disposeTime = now
                    alarmEventDao.disposeBy = userId
                    alarmEventDao.disposeReason = "超时自动关闭"
                    alarmEventDao.status = EnumAlarmStatus.FINISH

                    //更新异常处理日志
                    MuAlarmDisposeLogs.insert {
                        it[taskId] = alarmEventDao.eventId
                        it[alarmEventId] = alarmEventDao.id.value
                        it[reason] = "超时自动关闭"
                        it[createBy] = userId
                        it[createTime] = now
                        it[status] = EnumAlarmStatus.TIMEOUT
                    }
                }
                commit()
            }
            simpMessagingTemplate.convertAndSend(TOPIC_ROBOT, JsonUtils.toJson(EventCommand(type = EnumCommandType.ALARM, data = null))!!)
        }
    }


    /**
     * 事件处理
     * */
    @CacheEvict(allEntries = true)
    fun dispose(params: AlarmEventParam) {
        val userId = SecurityUtils.getUserId()
        val now = LocalDateTime.now()

        val alarmEventDao = findById(params.id) ?: throw BadRequestException("预警事件不存在")

        //更新异常处置记录
        alarmEventDao.disposeTime = now
        alarmEventDao.disposeBy = userId
        alarmEventDao.disposeReason = params.reason
        alarmEventDao.status = params.status ?: EnumAlarmStatus.AUTO

        //更新异常处理日志
        MuAlarmDisposeLogs.insert {
            it[taskId] = params.taskId
            it[alarmEventId] = alarmEventDao.id.value
            it[reason] = params.reason
            it[createBy] = userId
            it[createTime] = now
            it[status] = params.status ?: EnumAlarmStatus.AUTO
        }
    }

    /**
     * 异常事件处置日志信息
     * */
    @Cacheable
    fun alarmDisposeLogs(id: String): List<MuAlarmDisposeLog> {
        val alarmEventDao = get(id) ?: throw BadRequestException("事件不存在")

        val query = MuAlarmDisposeLogs.selectAll().where { MuAlarmDisposeLogs.alarmEventId eq id }
        query.orderBy(MuAlarmDisposeLogs.createTime, SortOrder.DESC)
        val list = mutableListOf<MuAlarmDisposeLog>()

        list.addAll(query.toList().map { MuAlarmDisposeLogs.toModel(it) })
        list.add(MuAlarmDisposeLog(
                id = "1",
                alarmEventId = alarmEventDao.id.value,
                reason = alarmEventDao.content,
                createBy = "后端任务",
                createTime = alarmEventDao.createTime ?: LocalDateTime.now(),
                status = EnumAlarmStatus.WAIT.description,
        ))

        return list
    }


    /**
     * 根据任务id处理自动执行的任务
     * */
    fun findWaitALarmEvent(ids: Set<String>) {
        val now = LocalDateTime.now()
        //找到待执行的事件
        val alarmList = MuAlarmDao.find { MuAlarmEvents.status.eq(EnumAlarmStatus.WAIT) and MuAlarmEvents.eventId.notInList(ids) }.toList()

        alarmList.forEach { eventDao ->
            eventDao.status = EnumAlarmStatus.FINISH
            eventDao.disposeBy = "admin"
            eventDao.disposeTime = now
            eventDao.disposeReason = "机器人现场处理"

            MuAlarmDisposeLogs.insert {
                it[taskId] = eventDao.eventId
                it[alarmEventId] = eventDao.id.value
                it[reason] = "机器人现场处理"
                it[createBy] = "admin"
                it[createTime] = now
                it[status] = EnumAlarmStatus.AUTO
            }
        }
    }
}
