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
 * 游客表
 * */    
object MuVisitors : UIDTable("mu_visitor") {
    val name = varchar("name", 255)//名称
    val idCard = varchar("id_card", 100).nullable()//身份证
    val image = varchar("image", 255).nullable()//头像
    val vipLevel = integer("vip_level").nullable()//VIP等级
    val createTime = datetime("create_time").nullable()//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人
    val gender = integer("gender").nullable()//性别

    fun toModel(row: ResultRow): MuVisitor {
        return MuVisitor(
                id = row[id]?.value,
                name = row[name],
                idCard = row[idCard],
                image = row[image],
                vipLevel = row[vipLevel],
                createTime = row[createTime],
                createBy = row[createBy],
                gender = row[gender])
    }
}

class MuVisitorDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuVisitorDao>(MuVisitors)

    var name by MuVisitors.name
    var idCard by MuVisitors.idCard
    var image by MuVisitors.image
    var vipLevel by MuVisitors.vipLevel
    var createTime by MuVisitors.createTime
    var createBy by MuVisitors.createBy
    var gender by MuVisitors.gender

    fun toModel(): MuVisitor {
        return MuVisitor(
                id = id.value,
                name = name,
                idCard = idCard,
                image = image,
                vipLevel = vipLevel,
                createTime = createTime,
                createBy = createBy,
                gender = gender)
    }
}

data class MuVisitor(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null, 
        val idCard: String? = null, 
        val image: String? = null, 
        val vipLevel: Int? = null, 
        val createTime: LocalDateTime? = null, 
        val createBy: String? = null, 
        val gender: Int? = null)
