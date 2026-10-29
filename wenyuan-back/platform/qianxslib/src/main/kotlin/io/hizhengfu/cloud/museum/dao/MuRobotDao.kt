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
 * 机器人表
 * */    
object MuRobots : UIDTable("mu_robot") {
    val name = varchar("name", 255)//名称
    val serialNo = varchar("serial_no", 100).nullable()//序列号
    val factory = varchar("factory", 255).nullable()//生产厂家
    val version = varchar("version", 50).nullable()//版本号
    val createTime = datetime("create_time").nullable()//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人

    fun toModel(row: ResultRow): MuRobot {
        return MuRobot(
                id = row[id]?.value,
                name = row[name],
                serialNo = row[serialNo],
                factory = row[factory],
                version = row[version],
                createTime = row[createTime],
                createBy = row[createBy])
    }
}

class MuRobotDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuRobotDao>(MuRobots)

    var name by MuRobots.name
    var serialNo by MuRobots.serialNo
    var factory by MuRobots.factory
    var version by MuRobots.version
    var createTime by MuRobots.createTime
    var createBy by MuRobots.createBy

    fun toModel(): MuRobot {
        return MuRobot(
                id = id.value,
                name = name,
                serialNo = serialNo,
                factory = factory,
                version = version,
                createTime = createTime,
                createBy = createBy)
    }
}

data class MuRobot(
        val id: String? = null,
        @field:NotBlank
        val name: String? = null, 
        val serialNo: String? = null, 
        val factory: String? = null, 
        val version: String? = null, 
        val createTime: LocalDateTime? = null, 
        val createBy: String? = null)
