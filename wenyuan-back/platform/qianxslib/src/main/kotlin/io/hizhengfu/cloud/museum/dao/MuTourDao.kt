package io.hizhengfu.cloud.museum.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 导览表
 * */
object MuTours : UIDTable("mu_tour") {
    val name = varchar("name", 255)//名称
    val image = varchar("image", 255).nullable()//导览图片
    val serialNo = varchar("serial_no", 100).nullable()//序号
    val roomId = varchar("room_id", 64)//场馆ID

    val preHint = varchar("pre_hint", 255).nullable()//出发前提示
    val finalHint = varchar("final_hint", 255).nullable()//到达提示
    val commentary = text("commentary")//讲解词
    val wait = integer("wait").nullable()//等待时间
    val inTheWay = varchar("in_the_way", 255).nullable()//途中讲解
    val audioPath = varchar("audio_path", 255).nullable()//音频文件
    val videoPath = varchar("video_path", 255).nullable()//视频文件

    val fromFirst = bool("from_first").default(false).nullable()//一期数据

    val x = double("x").nullable()//x坐标
    val y = double("y").nullable()//y坐标
    val z = double("z").nullable()//z坐标
    val r = double("r").nullable()//方位

    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人

    val status = enumeration("status", EnumContentStatus::class).default(EnumContentStatus.AUDIT)

    fun toModel(row: ResultRow): MuTour {
        return MuTour(
                id = row[id]?.value,
                name = row[name],
                image = row[image],
                serialNo = row[serialNo],
                roomId = row[roomId],
                room = MuRoom(id = row[roomId], name = row[MuRooms.name]),
                x = row[x],
                y = row[y],
                z = row[z],
                r = row[r],
                createTime = row[createTime],
                createBy = row[createBy],
                preHint = row[preHint],
                finalHint = row[finalHint],
                commentary = row[commentary],
                wait = row[wait],
                inTheWay = row[inTheWay],
                audioPath = row[audioPath],
                videoPath = row[videoPath],
                status = row[status]
        )
    }
}

class MuTourDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuTourDao>(MuTours)

    var name by MuTours.name
    var image by MuTours.image
    var serialNo by MuTours.serialNo
    var roomId by MuTours.roomId
    var x by MuTours.x
    var y by MuTours.y
    var z by MuTours.z
    var createTime by MuTours.createTime
    var createBy by MuTours.createBy
    var preHint by MuTours.preHint
    var finalHint by MuTours.finalHint
    var commentary by MuTours.commentary
    var wait by MuTours.wait
    var inTheWay by MuTours.inTheWay
    var audioPath by MuTours.audioPath
    var videoPath by MuTours.videoPath
    var status by MuTours.status
    var fromFirst by MuTours.fromFirst

    fun toModel(): MuTour {
        return MuTour(
                id = id.value,
                name = name,
                image = image,
                serialNo = serialNo,
                roomId = roomId,
                x = x,
                y = y,
                z = z,
                createTime = createTime,
                createBy = createBy,
                preHint = preHint,
                finalHint = finalHint,
                commentary = commentary,
                wait = wait,
                inTheWay = inTheWay,
                audioPath = audioPath,
                videoPath = videoPath,
                status = status,
        )
    }
}

data class MuTour(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null,
        val image: String? = null,
        val serialNo: String? = null,
        @field:NotBlank
        val roomId: String? = null,
        val room: MuRoom? = null,

        val preHint: String? = null,
        val finalHint: String? = null,
        @field:NotBlank
        val commentary: String? = null,
        val wait: Int? = null,
        val inTheWay: String? = null,
        val audioPath: String? = null,
        val videoPath: String? = null,

        val x: Double? = null,
        val y: Double? = null,
        val z: Double? = null,
        val r: Double? = null,
        val createTime: LocalDateTime? = null,
        val createBy: String? = null,
        val status: EnumContentStatus? = null
)
