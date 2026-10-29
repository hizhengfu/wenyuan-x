package io.hizhengfu.cloud.museum.domain

import io.hizhengfu.cloud.museum.utils.idleTask

/**
 * 机器人接口包装层
 * */
data class ProxyWrap<T>(
        val count: Long,
        val results: List<T>?
)

/**
 * 人流位置
 * */
data class PeopleWrap(
        val people_position: List<PeoplePosition>?,
        val created: String
)


/**
 * 人流位置
 * */
data class PeoplePosition(
        val x: Double?,
        val y: Double?,
)

/**
 * 人流预警信息
 * */
data class PeopleAlarm(
        val created: String, // 2022-11-09T11:49:24.823588+08:00
        val people_center: PeopleCenter?,
        val people_number: Int, // 6
        val position: RobotPosition?,
        val url: String // http://101.133.156.32:8800/api/warning/4/
)

/**
 * 聚集人群中心点
 * */
data class PeopleCenter(
        val x: Double, // -3.6
        val y: Double // 10.1
)


/**
 * 机器人信息
 * */
data class RobotWrap(
        val url: String,
        val battery: Battery?,
        val created: String, // 2022-09-02T09:09:32.339783Z
        val position: RobotPosition?,
        val robot_sn: String, // RSN_0001
        val state: RobotState?,
        val task_list: List<Task>?, // null
) {

    /**
     * 是否任务空闲
     * */
    val idleTask: Boolean
        get() {
            return idleTask(task_list)
        }
}


/**
 * 任务信息
 * */
data class Task(
        val task_id: String,
        val task_order: Any?,
        val task_state: String?,
        val task_time: String?,
)

/**
 *  电池信息
 * */
data class Battery(
        val created: String, // 2022-08-12T05:35:57.609682Z
        val value: String // 80
)

/**
 * 机器人位置
 * */
data class RobotPosition(
        val created: String? = null, // 2022-08-12T05:35:14.151671Z
        val rotation: Double, // 1.0
        val x: Double, // 1.0
        val y: Double, // 1.0
        val z: Double // 1.0
)

/**
 * 机器人状态
 * */
data class RobotState(
        val created: String, // 2022-08-12T05:36:34.131855Z
        val state: String, // 501
        val state_msg: String // 待机中
)

/**
 * 光栅信息
 * */
data class RasterWrap(
        val raster_state: RasterState?,
        val raster_id: String,
)

/**
 * 光栅状态
 * */
data class RasterState(
        val state: String,
        val state_msg: String,
        val created: String,
)

