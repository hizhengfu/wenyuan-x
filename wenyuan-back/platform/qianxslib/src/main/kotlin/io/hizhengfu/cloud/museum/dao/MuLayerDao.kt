package io.hizhengfu.cloud.museum.dao

import EnumLayerType
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 图层表
 * */
object MuLayers : UIDTable("mu_layer") {
    val name = varchar("name", 255)//名称
    val roomId = varchar("room_id", 64).nullable()//场馆id
    val type = enumeration("type", EnumLayerType::class)//类型
    val path = varchar("path", 255).nullable()//资源路径
    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64)//创建人
    val updateTime = datetime("update_time").nullable()//更新时间
    val updateBy = varchar("update_by", 64).nullable()//更新人

    fun toModel(row: ResultRow): MuLayer {
        return MuLayer(
                id = row[id]?.value,
                name = row[name],
                roomId = row[roomId],
                room = MuRoom(id = row[roomId], name = row[MuRooms.name]),
                type = row[type],
                path = row[path],
                createTime = row[createTime],
                createBy = row[createBy],
                updateTime = row[updateTime],
                updateBy = row[updateBy])
    }

    init {
        //同一个展馆同一种类型的只能创建一个
        index(true, roomId, type)
    }
}

/**
 * 点位位置表
 * */
object MuPointPositions : UIDTable("mu_point_position") {
    val layerId = varchar("layer_id", 64)
    val deviceId = varchar("device_id", 64)//关联设备信息
    val scene = varchar("scene", 255).nullable()//场景名称,360全景的情况下，需要存储以便区分位置
    val x = double("x").nullable()
    val y = double("y").nullable()
    val z = double("z").nullable()
    val r = double("r").nullable()
}

class MuLayerDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuLayerDao>(MuLayers)

    var name by MuLayers.name
    var type by MuLayers.type
    var roomId by MuLayers.roomId
    var path by MuLayers.path

    var createTime by MuLayers.createTime
    var createBy by MuLayers.createBy
    var updateTime by MuLayers.updateTime
    var updateBy by MuLayers.updateBy

    fun toModel(): MuLayer {
        return MuLayer(
                id = id.value,
                name = name,
                type = type,
                roomId = roomId,
                path = path,
                createTime = createTime,
                createBy = createBy,
                updateTime = updateTime,
                updateBy = updateBy)
    }
}

data class MuLayer(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null,
        val roomId: String? = null,
        val room: MuRoom? = null,
        @field:NotNull
        val type: EnumLayerType? = null,
        val path: String? = null,
        val createTime: LocalDateTime? = null,
        val createBy: String? = null,
        val updateTime: LocalDateTime? = null,
        val updateBy: String? = null)
