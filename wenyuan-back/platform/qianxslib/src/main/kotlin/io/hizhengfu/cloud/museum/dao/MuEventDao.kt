package io.hizhengfu.cloud.museum.dao

import EnumTaskProcessType
import EnumTaskType
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 异况表
 * */
object MuEvents : UIDTable("mu_event") {
    val name = varchar("name", 255).nullable()//名称
    val code = varchar("code", 100).nullable()//代码
    val type = enumeration("type", EnumTaskProcessType::class).nullable()//处置方式0 自动，1人工
    val taskId = varchar("task_id", 64)//任务id

    val eventType = enumeration("event_type", EnumTaskType::class).nullable()//时间
    val dateTime = datetime("date_time").nullable()//计划执行时间

    val finalDateTime = datetime("final_date_time").nullable()//计划任务完成执行时间

    val description = text("description").nullable()//描述
    val createTime = datetime("create_time").nullable()//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人

    fun toModel(row: ResultRow): MuEvent {
        return MuEvent(
                id = row[id]?.value,
                name = row[name],
                code = row[code],
                type = row[type],
                eventType = row[eventType],
                dateTime = row[dateTime],
                taskId = row[taskId],
                task = MuTask(id = row[taskId], name = row[MuTasks.name]),
                description = row[description],
                createTime = row[createTime],
                createBy = row[createBy])
    }
}

class MuEventDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuEventDao>(MuEvents)

    var name by MuEvents.name
    var code by MuEvents.code
    var type by MuEvents.type
    var eventType by MuEvents.eventType
    var dateTime by MuEvents.dateTime
    var taskId by MuEvents.taskId
    var description by MuEvents.description
    var finalDateTime by MuEvents.finalDateTime
    var createTime by MuEvents.createTime
    var createBy by MuEvents.createBy

    fun toModel(): MuEvent {
        return MuEvent(
                id = id.value,
                name = name,
                code = code,
                type = type,
                eventType = eventType,
                dateTime = dateTime,
                taskId = taskId,
                description = description,
                createTime = createTime,
                createBy = createBy)
    }
}

data class MuEvent(
        val id: String? = null,
        val name: String? = null,
        val code: String? = null,
        val type: EnumTaskProcessType? = null,

        val eventType: EnumTaskType? = null,
        val dateTime: LocalDateTime? = null,

        @field:NotBlank
        val taskId: String? = null,
        val task: MuTask? = null,
        val description: String? = null,
        val createTime: LocalDateTime? = null,
        val createBy: String? = null)
