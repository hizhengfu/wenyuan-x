package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable

import io.hizhengfu.cloud.wms.domain.EnumStorageStatus
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime


/**
 * 库存位置表
 * */
object WmsStorageLocations : UIDTable("wms_storage_location") {
    val roomId = varchar("room_id", 64).comment("所属库房")//所属库房
    val name = varchar("name", 255).comment("位置名称）")//
    val code = varchar("code", 100).comment("分格编码（如A01）")//分格编码（如A01）
    val capacity = integer("capacity").nullable().comment("容量（默认1件文物）")//容量（默认1件文物）
    val currentCount = integer("current_count").nullable().comment("当前存放数量")//当前存放数量
    val remarks = varchar("remarks", 255).nullable().comment("备注")//备注
    val status = enumeration<EnumStorageStatus>("status").default(EnumStorageStatus.USABLE).comment("可用状态")
    val createBy = varchar("create_by", 64).nullable().comment("创建人")//创建人
    val createTime = datetime("create_time").comment("创建时间")//创建时间
    val updateBy = varchar("update_by", 64).nullable().comment("更新人")//更新人
    val updateTime = datetime("update_time").nullable().comment("更新时间")//更新时间

    fun toModel(row: ResultRow): WmsStorageLocationVo {
        return WmsStorageLocationVo(
            id = row[id]?.value,
            roomId = row[roomId],
            roomWmsStorageRoom = WmsStorageRoomVo(id = row[WmsStorageRooms.id].value, name = row[WmsStorageRooms.name]),
            name = row[name],
            code = row[code],
            capacity = row[capacity],
            currentCount = row[currentCount],
            remarks = row[remarks],
            status = row[status],
            createBy = row[createBy],
            createTime = row[createTime],
            updateBy = row[updateBy],
            updateTime = row[updateTime]
        )
    }
}

class WmsStorageLocationDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<WmsStorageLocationDao>(WmsStorageLocations)

    var roomId by WmsStorageLocations.roomId
    var name by WmsStorageLocations.name
    var code by WmsStorageLocations.code
    var capacity by WmsStorageLocations.capacity
    var currentCount by WmsStorageLocations.currentCount
    var remarks by WmsStorageLocations.remarks
    var status by WmsStorageLocations.status
    var createBy by WmsStorageLocations.createBy
    var createTime by WmsStorageLocations.createTime
    var updateBy by WmsStorageLocations.updateBy
    var updateTime by WmsStorageLocations.updateTime

    fun toModel(): WmsStorageLocationVo {
        return WmsStorageLocationVo(
            id = id.value,
            roomId = roomId,
            name = name,
            code = code,
            capacity = capacity,
            currentCount = currentCount,
            remarks = remarks,
            status = status,
            createBy = createBy,
            createTime = createTime,
            updateBy = updateBy,
            updateTime = updateTime
        )
    }
}

data class WmsStorageLocationVo(
    val id: String? = null,
    @field:NotNull
    val roomId: String? = null,
    val roomWmsStorageRoom: WmsStorageRoomVo? = null,
    val code: String? = null,
    val name: String? = null,
    val capacity: Int? = null,
    val currentCount: Int? = null,
    val remarks: String? = null,
    val status: EnumStorageStatus? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    val updateBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val files: List<String>? = null
)
