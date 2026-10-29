package io.hizhengfu.cloud.museum.domain

import EnumTaskDetailType

data class TaskDetailParam(
        val id: String?,
        val taskId: String,
        val type: EnumTaskDetailType,
        val tourId: String?,
        val text: String?,

        val x: Double?,
        val y: Double?,
        val z: Double?,
        val r: Double?,

        val sort: Int?,
)

/**
 * 发送任务
 * */
data class TaskWrap(
        val robot_sn: String, // RSN_0001
        val task_info: List<TaskInfo>
)

data class TaskInfo(
        val task_order: Any,
        val task_type: String // position
)

data class TaskOrder(
        val rotation: Double? = null, // 160
        val x: Double? = null, // -5.22
        val y: Double? = null, // 13
        val text: String? = null, // 请勿触碰展品，请勿触碰展品，请勿触碰展品。

        val before_hint: String? = null, //出发前提示
        val explain_way: String? = null, //途中讲解
        val after_hint: String? = null, //到达后提示
        val explain: String? = null, //到达后讲解
)


/**
 * 任务响应
 * */
data class TaskRespWrap(
        val robot_sn: String,
        val task_id: String,
        val task_info: Any?,
        val task_state: String,
        val task_state_msg: String?,
        val task_time: String?,
        val created: String,
)
