package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable

import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime


/**
 * 库房信息表
 * */
object WmsStorageRooms : ContentAuditUIDTable("wms_storage_room") {
    val areaId = varchar("area_id", 64).nullable().comment("所属库区")//所属库区
    val code = varchar("code", 100).comment("库房编号（例：Z01-R01）")//库房编号（例：Z01-R01）
    val image = varchar("image", 255).nullable().comment("缩略图")//缩略图
    val name = varchar("name", 50).comment("库房名称（如：青铜器库房）")//库房名称（如：青铜器库房）
    val temperatureRange = varchar("temperature_range", 20).nullable().comment("温度控制范围（℃）")//温度控制范围（℃）
    val humidityRange = varchar("humidity_range", 20).nullable().comment("湿度控制范围（%）")//湿度控制范围（%）
    val securityLevel = integer("security_level").nullable().comment("安防等级")//安防等级
    val location = varchar("location", 255).nullable().comment("位置")
    val lat = double("lat").nullable().comment("经度")//经度
    val lng = double("lng").nullable().comment("纬度")//纬度
    val remarks = text("remarks").nullable().comment("描述")//描述

    fun toModel(row: ResultRow): WmsStorageRoomVo {
        return WmsStorageRoomVo(
            id = row[id]?.value,
            areaId = row[areaId],
            areaWmsStorageArea = WmsStorageAreaVo(id = row[WmsStorageAreas.id]?.value, name = row[WmsStorageAreas.name]),
            code = row[code],
            image = row[image],
            name = row[name],
            temperatureRange = row[temperatureRange],
            humidityRange = row[humidityRange],
            securityLevel = row[securityLevel],
            location = row[location],
            lat = row[lat],
            lng = row[lng],
            remarks = row[remarks],
            createBy = row[createBy],
            createTime = row[createTime],
            updateBy = row[updateBy],
            updateTime = row[updateTime]
        )
    }
}

class WmsStorageRoomDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsStorageRooms) {
    companion object : ContentAuditUIDEntityClass<WmsStorageRoomDao>(WmsStorageRooms)

    var areaId by WmsStorageRooms.areaId
    var code by WmsStorageRooms.code
    var image by WmsStorageRooms.image
    var name by WmsStorageRooms.name
    var temperatureRange by WmsStorageRooms.temperatureRange
    var humidityRange by WmsStorageRooms.humidityRange
    var securityLevel by WmsStorageRooms.securityLevel
    var location by WmsStorageRooms.location
    var lat by WmsStorageRooms.lat
    var lng by WmsStorageRooms.lng
    var remarks by WmsStorageRooms.remarks

    fun toModel(): WmsStorageRoomVo {
        return WmsStorageRoomVo(
            id = id.value,
            areaId = areaId,
            code = code,
            image = image,
            name = name,
            temperatureRange = temperatureRange,
            humidityRange = humidityRange,
            securityLevel = securityLevel,
            location = location,
            lat = lat,
            lng = lng,
            remarks = remarks,
            createBy = createBy,
            createTime = createTime,
            updateBy = updateBy,
            updateTime = updateTime
        )
    }
}

data class WmsStorageRoomVo(
    val id: String? = null,
    val areaId: String? = null,
    val areaWmsStorageArea: WmsStorageAreaVo? = null,
    val code: String? = null,
    val image: String? = null,
    @field:NotBlank
    val name: String? = null,
    val temperatureRange: String? = null,
    val humidityRange: String? = null,
    val securityLevel: Int? = null,
    val location: String? = null,
    var lat: Double? = null,
    var lng: Double? = null,
    val remarks: String? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    val updateBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val files: List<String>? = null
) {
    var latlng: String?
        get() {
            return if (lng != null && lat != null) {
                "${lat},${lng}"
            } else {
                null
            }
        }
        set(value) {
            if (!value.isNullOrBlank()) {
                val temp = value.split(",")
                if (temp.size == 2) {
                    lng = temp[1].toDouble()
                    lat = temp[0].toDouble()
                }
            }
        }
}
