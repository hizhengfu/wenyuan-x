package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable

import io.hizhengfu.cloud.sys.system.dao.GroupVo
import io.hizhengfu.cloud.sys.system.dao.Groups
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime


/**
 * 考古项目表
 * */
object WmsProjects : ContentAuditUIDTable("wms_project") {
    val name = varchar("name", 100).comment("项目名称")//项目名称
    val image = varchar("image", 255).nullable().comment("缩略图")//缩略图
    val code = varchar("code", 100).comment("项目编号(例:2023KZ001)")//项目编号(例:2023KZ001)
    val groupId = varchar("group_id", 64).nullable().comment("组织机构")//组织机构
    val leader = varchar("leader", 50).comment("项目负责人")//项目负责人
    val location = varchar("location", 200).comment("项目位置")//项目位置
    val startDate = date("start_date").nullable().comment("项目开始日期")//项目开始日期
    val endDate = date("end_date").nullable().comment("项目结束日期")//项目结束日期
    val lat = double("lat").nullable().comment("经度")//经度
    val lng = double("lng").nullable().comment("纬度")//纬度
    val remarks = text("remarks").nullable().comment("备注")

    fun toModel(row: ResultRow): WmsProjectVo {
        return WmsProjectVo(
            id = row[id]?.value,
            name = row[name],
            image = row[image],
            code = row[code],
            groupId = row[groupId],
            group = GroupVo(id = row[Groups.id]?.value, name = row[Groups.name]),
            leader = row[leader],
            location = row[location],
            startDate = row[startDate],
            endDate = row[endDate],
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

class WmsProjectDao(id: EntityID<String>) : ContentAuditUIDEntity(id, WmsProjects) {
    companion object : ContentAuditUIDEntityClass<WmsProjectDao>(WmsProjects)

    var name by WmsProjects.name
    var image by WmsProjects.image
    var code by WmsProjects.code
    var groupId by WmsProjects.groupId
    var leader by WmsProjects.leader
    var location by WmsProjects.location
    var startDate by WmsProjects.startDate
    var endDate by WmsProjects.endDate
    var lat by WmsProjects.lat
    var lng by WmsProjects.lng
    var remarks by WmsProjects.remarks

    fun toModel(): WmsProjectVo {
        return WmsProjectVo(
            id = id.value,
            name = name,
            image = image,
            code = code,
            groupId = groupId,
            leader = leader,
            location = location,
            startDate = startDate,
            endDate = endDate,
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

data class WmsProjectVo(
    val id: String? = null,
    @field:NotBlank
    val name: String? = null,
    val image: String? = null,
    val code: String? = null,
    val groupId: String? = null,
    val group: GroupVo? = null,
    val leader: String? = null,
    @field:NotBlank
    val location: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    var lat: Double? = null,
    var lng: Double? = null,
    val remarks: String? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    val updateBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val files: List<String>? = null
){
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

