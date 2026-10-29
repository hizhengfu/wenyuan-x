package io.hizhengfu.cloud.museum.dao

import EnumTaskDetailType
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 任务表
 * */
object MuTasks : UIDTable("mu_task") {
    val name = varchar("name", 255).nullable()//名称
    val description = text("description").nullable()//描述

    //    val robotId = varchar("robot_id", 64).nullable()//机器人id
    val roomId = varchar("room_id", 64).nullable()//场馆id
    val createTime = datetime("create_time").nullable()//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人

    fun toModel(row: ResultRow): MuTask {
        return MuTask(
                id = row[id]?.value,
                name = row[name],
                description = row[description],
//                robotId = row[robotId],
//                robot = MuRobot(id = row[robotId], name = row[MuRobots.name]),
                roomId = row[roomId],
                room = MuRoom(id = row[roomId], name = row[MuRooms.name]),
                createTime = row[createTime],
                createBy = row[createBy])
    }
}


class MuTaskDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuTaskDao>(MuTasks)

    var name by MuTasks.name
    var description by MuTasks.description

    //    var robotId by MuTasks.robotId
    var roomId by MuTasks.roomId
    var createTime by MuTasks.createTime
    var createBy by MuTasks.createBy

    fun toModel(): MuTask {
        return MuTask(
                id = id.value,
                name = name,
                description = description,
//                robotId = robotId,
                roomId = roomId,
                createTime = createTime,
                createBy = createBy)
    }
}

data class MuTask(
        val id: String? = null,
        val name: String? = null,
        val description: String? = null,
        val robotId: String? = null,
//        val robot: MuRobot? = null,
        val roomId: String? = null,
        val room: MuRoom? = null,
        val createTime: LocalDateTime? = null,
        val createBy: String? = null)


/**
 * 任务详情
 * */
object MuTaskDetails : UIDTable("mu_task_detail") {
    val taskId = varchar("task_id", 64)//任务id
    val type = enumeration("type", EnumTaskDetailType::class)//任务id
    val tourId = varchar("tour_id", 64).nullable()//导览ID，当任务类型为导览时有效
    val text = text("text").nullable()//语音播报内容

    val x = double("x").nullable()//x坐标
    val y = double("y").nullable()//y坐标
    val z = double("z").nullable()//z坐标
    val r = double("r").nullable()//方向角度

    val sort = integer("sort").default(0)//排序

    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人

    fun toModel(row: ResultRow): MuTaskDetail {
        return MuTaskDetail(
                id = row[id].value,
                taskId = row[taskId],
                type = row[type],
                tourId = row[tourId],
                text = row[text],
                x = row[MuTours.x] ?: row[x],
                y = row[MuTours.y] ?: row[y],
                z = row[MuTours.z] ?: row[z],
                r = row[MuTours.r] ?: row[r],
                sort = row[sort],
                fromFirst = row[MuTours.fromFirst],
                preHint = row[MuTours.preHint],
                finalHint = row[MuTours.finalHint],
                commentary = row[MuTours.commentary],
                wait = row[MuTours.wait],
                inTheWay = row[MuTours.inTheWay],
                audioPath = row[MuTours.audioPath],
                videoPath = row[MuTours.videoPath],
        )
    }
}

data class MuTaskDetail(
        val id: String?,
        val taskId: String,
        val type: EnumTaskDetailType,
        val tourId: String?,
        val text: String?,

        val fromFirst: Boolean? = null,//一期数据标记

        val x: Double?,
        val y: Double?,
        val z: Double?,
        val r: Double?,

        val sort: Int?,

        val preHint: String? = null,
        val finalHint: String? = null,
        val commentary: String? = null,
        val wait: Int? = null,
        val inTheWay: String? = null,
        val audioPath: String? = null,
        val videoPath: String? = null,
)