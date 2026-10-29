package io.hizhengfu.cloud.museum.domain

import EnumAlarmStatus

/**
 * 异常事件处理
 * */
data class AlarmEventParam(
        val id: String,
        val taskId: String?,
        val reason: String,
        val status: EnumAlarmStatus? = null,
)