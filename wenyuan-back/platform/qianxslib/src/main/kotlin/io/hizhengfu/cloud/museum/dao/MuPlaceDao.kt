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
 * 博物馆
 * */    
object MuPlaces : UIDTable("mu_place") {
    val name = varchar("name", 255)//名称
    val image = varchar("image", 255).nullable()//缩略图
    val description = text("description").nullable()//描述
    val address = varchar("address", 255).nullable()
    val lat = varchar("lat", 255).nullable()//纬度
    val lng = varchar("lng", 255).nullable()//经度
    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64)//创建人
    val updateTime = datetime("update_time").nullable()//更新时间
    val updateBy = varchar("update_by", 64).nullable()//更新人

    fun toModel(row: ResultRow): MuPlace {
        return MuPlace(
                id = row[id]?.value,
                name = row[name],
                image = row[image],
                description = row[description],
                address = row[address],
                lat = row[lat],
                lng = row[lng],
                createTime = row[createTime],
                createBy = row[createBy],
                updateTime = row[updateTime],
                updateBy = row[updateBy])
    }
}

class MuPlaceDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuPlaceDao>(MuPlaces)

    var name by MuPlaces.name
    var image by MuPlaces.image
    var description by MuPlaces.description
    var address by MuPlaces.address
    var lat by MuPlaces.lat
    var lng by MuPlaces.lng
    var createTime by MuPlaces.createTime
    var createBy by MuPlaces.createBy
    var updateTime by MuPlaces.updateTime
    var updateBy by MuPlaces.updateBy

    fun toModel(): MuPlace {
        return MuPlace(
                id = id.value,
                name = name,
                image = image,
                description = description,
                address = address,
                lat = lat,
                lng = lng,
                createTime = createTime,
                createBy = createBy,
                updateTime = updateTime,
                updateBy = updateBy)
    }
}

data class MuPlace(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null, 
        val image: String? = null, 
        val description: String? = null, 
        val address: String? = null, 
        val lat: String? = null, 
        val lng: String? = null, 
        val createTime: LocalDateTime? = null,
        val createBy: String? = null,
        val updateTime: LocalDateTime? = null, 
        val updateBy: String? = null)
