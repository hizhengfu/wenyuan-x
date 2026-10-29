package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable

import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime


/**
 * 库区信息表
 * */
object WmsStorageAreas : ContentAuditUIDTable("wms_storage_area") {
    val code = varchar("code", 100).comment("库区编号（例：Z01）")//库区编号（例：Z01）
    val image = varchar("image", 255).nullable().comment("缩略图")//缩略图
    val name = varchar("name", 50).comment("库区名称（如：陶瓷区、金属区）")//库区名称（如：陶瓷区、金属区）
    val manager = varchar("manager", 50).nullable().comment("库区负责人")//库区负责人
    val managerPhone = varchar("manager_phone", 20).nullable().comment("库区负责人电话")//库区负责人电话
    val location = varchar("location", 255).nullable().comment("位置")
    val lat = double("lat").nullable().comment("经度")//经度
    val lng = double("lng").nullable().comment("纬度")//纬度
    val remarks = text("remarks").nullable().comment("描述")//描述

    fun toModel(row: ResultRow): WmsStorageAreaVo {
        return WmsStorageAreaVo(
            id = row[id]?.value,
            code = row[code],
            image = row[image],
            name = row[name],
            manager = row[manager],
            managerPhone = row[managerPhone],
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

class WmsStorageAreaDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsStorageAreas) {
    companion object : ContentAuditUIDEntityClass<WmsStorageAreaDao>(WmsStorageAreas)

    var code by WmsStorageAreas.code
    var image by WmsStorageAreas.image
    var name by WmsStorageAreas.name
    var manager by WmsStorageAreas.manager
    var managerPhone by WmsStorageAreas.managerPhone
    var location by WmsStorageAreas.location
    var lat by WmsStorageAreas.lat
    var lng by WmsStorageAreas.lng
    var remarks by WmsStorageAreas.remarks

    fun toModel(): WmsStorageAreaVo {
        return WmsStorageAreaVo(
            id = id.value,
            code = code,
            image = image,
            name = name,
            manager = manager,
            managerPhone = managerPhone,
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

data class WmsStorageAreaVo(
    val id: String? = null,
    val code: String? = null,
    val image: String? = null,
    @field:NotBlank
    val name: String? = null,
    val manager: String? = null,
    val managerPhone: String? = null,
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
