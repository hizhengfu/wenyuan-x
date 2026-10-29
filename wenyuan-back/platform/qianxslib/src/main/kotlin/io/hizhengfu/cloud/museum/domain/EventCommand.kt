package io.hizhengfu.cloud.museum.domain

import EnumCommandType


/**
 * 事件推送指令
 * */
data class EventCommand(
        val type: EnumCommandType = EnumCommandType.DATA,
        val data: Any?
)