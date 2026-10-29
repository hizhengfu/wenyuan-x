package io.hizhengfu.cloud.museum.service

import EnumCommandType
import TOPIC_ROBOT
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.extend.toLocalDateTime
import io.hizhengfu.cloud.common.utils.HttpClientUtils
import io.hizhengfu.cloud.museum.domain.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import io.hizhengfu.cloud.common.extend.scale
import java.time.LocalDateTime


@Service
class ProxyMKXService(
        @Value("\${robotUrl:http://101.133.156.32:8800}") val robotUrl: String,
        val muAlarmEventService: MuAlarmEventService,
        val muEventService: MuEventService,
        val simpMessagingTemplate: SimpMessagingTemplate,
) {
    private var cachePeople: String? = null
    private var cachePeopleAlarm: String? = null
    private var cacheRobot: String? = null
    private var cacheRobotList: List<RobotWrap>? = null
    private var cacheRaster: String? = null

    private var hasRefresh: Boolean = false

    private var cacheLastDate = mutableMapOf<String, LocalDateTime?>()

    private val logger = LoggerFactory.getLogger("ProxyMKXService")

    /**
     * 获取人流数据
     * */
    fun getCameraPeople(): List<PeoplePosition>? {
        val list = mutableListOf<PeoplePosition>()
        HttpClientUtils.get("${robotUrl}/api/camera/") { json ->
            if (cachePeople != json) {
                cachePeople = json
                hasRefresh = true
                JsonUtils.fromJson<ProxyWrap<PeopleWrap>>(json)?.results?.forEach { peopleWrap ->
                    val lastDate = cacheLastDate["people"]
                    val newDate = peopleWrap.created.toLocalDateTime()
                    if (!peopleWrap.people_position.isNullOrEmpty() && (lastDate == null || lastDate.isBefore(newDate))) {
                        list.addAll(peopleWrap.people_position.filter { it.x != null })
                        cacheLastDate["people"] = newDate
                    }
                }
            }
        }
        return list
    }

    /**
     * 获取人流预警数据
     * */
    fun getCameraAlarmPeople(): List<PeopleAlarm>? {
        val list = mutableListOf<PeopleAlarm>()
        HttpClientUtils.get("${robotUrl}/api/warning/") { json ->
            if (cachePeopleAlarm != json) {
                cachePeopleAlarm = json
                hasRefresh = true
                JsonUtils.fromJson<ProxyWrap<PeopleAlarm>>(json)?.results?.forEach { alarm ->
                    if (alarm.position != null) {
                        //根据数据更新时间判断是否需要处理，考虑到时间不一定同步，使用本次时间与上次时间差来判断
                        val lastDate = cacheLastDate["people_alarm"]
                        val newDate = alarm.created.toLocalDateTime()
                        //当第一次收到光栅预警或更新时间比前次更新时间有所变化的情况下更新光栅预警数据
                        if (lastDate == null || lastDate.isBefore(newDate)) {
                            list.add(alarm)
                            cacheLastDate["people_alarm"] = newDate
                        }
                    }
                }
            }
        }
        return list
    }

    /**
     * 获取光栅数据
     * */
    fun getRaster(): List<RasterWrap>? {
        val list = mutableListOf<RasterWrap>()
        HttpClientUtils.get("${robotUrl}/api/raster/group/") { json ->
            if (cacheRaster != json) {
                cacheRaster = json
                hasRefresh = true
                JsonUtils.fromJson<ProxyWrap<RasterWrap>>(json)?.results?.forEach { wrap ->
                    val lastDate = cacheLastDate[wrap.raster_id]
                    val newDate = wrap.raster_state?.created?.toLocalDateTime()
                    //当第一次收到光栅预警或更新时间比前次更新时间有所变化的情况下更新光栅预警数据
                    if (lastDate == null || (newDate != null && lastDate.isBefore(newDate))) {
                        list.add(wrap)
                        cacheLastDate[wrap.raster_id] = newDate
                    }
                }
            }
        }
        return list
    }


    /**
     * 获取机器人信息
     * */
    fun getRobot(): List<RobotWrap>? {
        HttpClientUtils.get("${robotUrl}/api/robot/group/") { json ->
            if (cacheRobot != json) {
                val robotList = mutableListOf<RobotWrap>()
                cacheRobot = json
                hasRefresh = true
                json?.let {
                    JsonUtils.fromJson<ProxyWrap<RobotWrap>>(it)?.results.let { results ->
                        if (!results.isNullOrEmpty()) {
                            robotList.addAll(results)
                        }
                    }
                }
                cacheRobotList = robotList

                val taskIds = mutableSetOf<String>()
                robotList.forEach { robot ->
                    robot.task_list?.forEach {
                        taskIds.add(it.task_id)
                    }
                }
                if (taskIds.isNotEmpty()) {
                    muAlarmEventService.findWaitALarmEvent(taskIds)
                }
            }
        }
        return cacheRobotList
    }


    /**
     * 获取人流与机器人信息
     * */
    fun fetchInfo(force: Boolean = false) {
        hasRefresh = false
        //不存在机器人时不执行下列逻辑
        val robots = getRobot() ?: return
        val people = getCameraPeople()
        val raster = getRaster()
        val peopleAlarm = getCameraAlarmPeople()

        raster?.forEach {
            //当光栅有预警的情况下,推送预警信息到机器人
            if (it.raster_state?.state == "302") {
                logger.info("接收到光栅预警信息:{}", it)
                val alarmId = muAlarmEventService.saveEvent(
                        it.raster_id,
                        it.raster_state.state,
                        message = it.raster_state.state_msg,
                        dateTime = it.raster_state.created.toLocalDateTime()!!
                )
                if (!alarmId.isNullOrBlank()) {
                    //处理光栅异常事件
                    muEventService.handTask("raster", robots, it.raster_id, alarmId = alarmId)

                    //当有闲置机器人用闲置，否则使用1号机器人
                    /*val taskData = TaskWrap(idleRobotSn, task_info = listOf(
                            TaskInfo(task_type = "position", task_order = TaskOrder(x = -5.22, y = 13.0, rotation = 160.0)),
                            TaskInfo(task_type = "speech", task_order = TaskOrder(text = "请勿触碰展品，请勿触碰展品，请勿触碰展品。"))
                    ))*/
//                    pushTask(taskData)

                    //推送机器人状态信息
                    simpMessagingTemplate.convertAndSend(TOPIC_ROBOT, JsonUtils.toJson(EventCommand(type = EnumCommandType.ALARM, data = null))!!)
                    hasRefresh = true
                }
            }
        }

        /**
         * 人员聚集预警
         * */
        peopleAlarm?.forEach() {
            if (it.position == null) return@forEach

            val alarmId = muAlarmEventService.saveEvent("people", "100",
                    message = "场馆人员「${it.people_number}」聚集，请及时巡查。",
                    dateTime = it.created.toLocalDateTime()!!)
            if (!alarmId.isNullOrBlank()) {
                val taskInfo = TaskInfo(task_type = "position", task_order = TaskOrder(
                        x = it.position.x.scale(2), y = it.position.y.scale(2), rotation = it.position.rotation.scale(2)
                ))

                //处理光栅异常事件
                muEventService.handTask("people", robots = robots, taskInfo = taskInfo, alarmId = alarmId)

                //推送机器人状态信息
                simpMessagingTemplate.convertAndSend(TOPIC_ROBOT, JsonUtils.toJson(EventCommand(type = EnumCommandType.ALARM, data = null))!!)
                hasRefresh = true
            }
        }

        /*people?.let {
            val count = muAlarmEventService.savePeopleEvent(it.size)
            if (count > 0) {
                //推送预警信息，以便前台刷新
                simpMessagingTemplate.convertAndSend(TOPIC_ROBOT, JsonUtils.toJson(EventCommand(type = EnumCommandType.ALARM, data = null))!!)
            }
        }*/

        if (force || hasRefresh) {
            val data = RoomStatus(people = people, robot = robots, raster = raster)

            simpMessagingTemplate.convertAndSend(TOPIC_ROBOT, JsonUtils.toJson(EventCommand(data = data))!!)
//            simpMessagingTemplate.convertAndSend(TOPIC_ROBOT, data)
        }

        //处理日常任务事件
        val waitList = muEventService.findWaitProcess()
        waitList.forEach {
            muEventService.handTask(it.id.value, robots)
            muEventService.updateEventFinalDateTime(it.id.value)
        }

    }
}