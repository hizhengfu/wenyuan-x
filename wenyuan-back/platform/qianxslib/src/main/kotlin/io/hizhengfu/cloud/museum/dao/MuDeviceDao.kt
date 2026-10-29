package io.hizhengfu.cloud.museum.dao

import EnumDeviceType
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 设备表
 * */
object MuDevices : UIDTable("mu_device") {
    val name = varchar("name", 255)//名称
    val description = text("description").nullable()//描述
    val serialNo = varchar("serial_no", 100).nullable()//序列号
    val addr = varchar("addr", 512).nullable()//设备地址
    val type = enumeration("type", EnumDeviceType::class).nullable()//设备类型
    val roomId = varchar("room_id", 64).nullable()//场馆ID

    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64)//创建人

    fun toModel(row: ResultRow): MuDevice {
        return MuDevice(
                id = row[id]?.value,
                name = row[name],
                type = row[type],
                description = row[description],
                serialNo = row[serialNo],
                addr = row[addr],
                roomId = row[roomId],
                room = MuRoom(id = row[roomId], name = row[MuRooms.name]),
                createTime = row[createTime],
                createBy = row[createBy])
    }
}

class MuDeviceDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuDeviceDao>(MuDevices)

    var name by MuDevices.name
    var type by MuDevices.type
    var description by MuDevices.description
    var serialNo by MuDevices.serialNo
    var addr by MuDevices.addr
    var roomId by MuDevices.roomId

    var createTime by MuDevices.createTime
    var createBy by MuDevices.createBy

    fun toModel(): MuDevice {
        return MuDevice(
                id = id.value,
                name = name,
                type = type,
                description = description,
                serialNo = serialNo,
                addr = addr,
                roomId = roomId,
                createTime = createTime,
                createBy = createBy)
    }
}

data class MuDevice(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null,
        val type: EnumDeviceType? = null,
        val description: String? = null,
        val serialNo: String? = null,
        val addr: String? = null,
        val roomId: String? = null,
        val room: MuRoom? = null,
        val online: Boolean? = null,
        val createTime: LocalDateTime? = null,
        val createBy: String? = null)
