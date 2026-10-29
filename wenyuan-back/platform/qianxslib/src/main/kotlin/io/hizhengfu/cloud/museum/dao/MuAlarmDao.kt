package io.hizhengfu.cloud.museum.dao

import EnumAlarmStatus
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 异常事件表
 * */
object MuAlarmEvents : UIDTable("mu_alarm") {
    val dateTime = datetime("date_time").nullable()//数据时间
    val level = integer("level").nullable()//等级
    val code = varchar("code", 255).nullable()//异常代码
    val value = varchar("value", 512).nullable()//异常值
    val content = text("content").nullable()//异常内容
    val deviceId = varchar("device_id", 64).nullable()//设备id
    val createTime = datetime("create_time").nullable()//创建时间

    val eventId = varchar("event_id", 64).nullable()//异常事件处置id
    val disposeBy = varchar("dispose_by", 64).nullable()//处理人
    val disposeReason = text("dispose_reason").nullable()//处理理由
    val disposeTime = datetime("dispose_time").nullable()//处理时间
    val status = enumeration<EnumAlarmStatus>("status").default(EnumAlarmStatus.WAIT)

    fun toModel(row: ResultRow): MuAlarm {
        return MuAlarm(
                id = row[id]?.value,
                dateTime = row[dateTime],
                level = row[level],
                code = row[code],
                value = row[value],
                content = row[content],
                deviceId = row[deviceId],
                device = MuDevice(id = row[deviceId], name = row[MuDevices.name]),

                eventId = row[eventId],
                disposeBy = row[disposeBy],
                disposeReason = row[disposeReason],
                disposeTime = row[disposeTime],
                status = row[status],

                createTime = row[createTime])
    }
}

/**
 * 异常事件处置日志表
 * */
object MuAlarmDisposeLogs : UIDTable("mu_alarm_dispose_log") {
    val alarmEventId = varchar("alarm_event_id", 64)
    val taskId = varchar("task_id", 64).nullable()//如果选择了任务，记录任务id
    val reason = text("reason")//处理理由
    val createBy = varchar("create_by", 64).nullable()//处理人,自动处理的情况下为空
    val createTime = datetime("create_time")//处理时间
    val status = enumeration<EnumAlarmStatus>("status").default(EnumAlarmStatus.WAIT)//处理状态

    fun toModel(row: ResultRow): MuAlarmDisposeLog {
        return MuAlarmDisposeLog(
                id = row[id].value,
                alarmEventId = row[alarmEventId],
                reason = row[reason],
                createBy = row[createBy],
                createTime = row[createTime],
                status = row[status].description,
        )
    }
}

data class MuAlarmDisposeLog(
        val id: String,
        val alarmEventId: String,
        val reason: String?,
        val createBy: String?,
        val createTime: LocalDateTime,
        val status: String,
)

class MuAlarmDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuAlarmDao>(MuAlarmEvents)

    var dateTime by MuAlarmEvents.dateTime
    var level by MuAlarmEvents.level
    var code by MuAlarmEvents.code
    var value by MuAlarmEvents.value
    var content by MuAlarmEvents.content
    var deviceId by MuAlarmEvents.deviceId

    var eventId by MuAlarmEvents.eventId
    var disposeBy by MuAlarmEvents.disposeBy
    var disposeReason by MuAlarmEvents.disposeReason
    var disposeTime by MuAlarmEvents.disposeTime
    var status by MuAlarmEvents.status

    var createTime by MuAlarmEvents.createTime

    fun toModel(): MuAlarm {
        return MuAlarm(
                id = id.value,
                dateTime = dateTime,
                level = level,
                code = code,
                value = value,
                content = content,
                deviceId = deviceId,
                createTime = createTime
        )
    }
}

data class MuAlarm(
        val id: String? = null,
        val dateTime: LocalDateTime? = null,
        val dateTimeRange: List<LocalDateTime>? = null,
        val level: Int? = null,
        val code: String? = null,
        val value: String? = null,
        val content: String? = null,
        val deviceId: String? = null,
        val device: MuDevice? = null,

        val eventId: String? = null,
        val disposeBy: String? = null,
        val disposeReason: String? = null,
        val disposeTime: LocalDateTime? = null,
        val status: EnumAlarmStatus? = null,

        val createTime: LocalDateTime? = null,
)

