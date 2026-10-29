package io.hizhengfu.cloud.museum.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 场馆表
 * */
object MuRooms : UIDTable("mu_room") {
    val name = varchar("name", 255)//名称
    val image = varchar("image", 255).nullable()//缩略图
    val placeId = varchar("place_id", 64).nullable()//场馆id
    val serialNo = varchar("serial_no", 100).nullable()//展馆编号
    val description = text("description").nullable()//描述
    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64)//创建人
    val updateTime = datetime("update_time").nullable()//更新时间
    val updateBy = varchar("update_by", 64).nullable()//更新人

    fun toModel(row: ResultRow): MuRoom {
        return MuRoom(
                id = row[id]?.value,
                image = row[image],
                name = row[name],
                placeId = row[placeId],
                place = MuPlace(id = row[placeId], name = row[MuPlaces.name]),
                serialNo = row[serialNo],
                description = row[description],
                createTime = row[createTime],
                createBy = row[createBy],
                updateTime = row[updateTime],
                updateBy = row[updateBy])
    }
}

class MuRoomDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuRoomDao>(MuRooms)

    var name by MuRooms.name
    var image by MuRooms.image
    var placeId by MuRooms.placeId
    var serialNo by MuRooms.serialNo
    var description by MuRooms.description
    var createTime by MuRooms.createTime
    var createBy by MuRooms.createBy
    var updateTime by MuRooms.updateTime
    var updateBy by MuRooms.updateBy

    fun toModel(): MuRoom {
        return MuRoom(
                id = id.value,
                name = name,
                image = image,
                placeId = placeId,
                serialNo = serialNo,
                description = description,
                createTime = createTime,
                createBy = createBy,
                updateTime = updateTime,
                updateBy = updateBy)
    }
}

data class MuRoom(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null,
        val image: String? = null,
        val placeId: String? = null,
        val place: MuPlace? = null,
        val serialNo: String? = null,
        val description: String? = null,
        val createTime: LocalDateTime? = null,
        val createBy: String? = null,
        val updateTime: LocalDateTime? = null,
        val updateBy: String? = null)
