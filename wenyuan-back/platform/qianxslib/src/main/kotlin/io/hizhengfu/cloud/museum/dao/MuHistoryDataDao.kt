package io.hizhengfu.cloud.museum.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 采集数据表
 * */
object MuHistoryDatas : UIDTable("mu_history_data") {
    val deviceId = varchar("device_id", 64).nullable()//设备id
    val dataValue0 = double("data_value0").nullable()//数据1
    val dataValue1 = double("data_value1").nullable()//数据2
    val dataValue2 = double("data_value2").nullable()//数据3
    val dateTime = datetime("date_time").nullable()//数据采集时间
    val createTime = datetime("create_time").nullable()//数据创建时间

    fun toModel(row: ResultRow): MuHistoryData {
        return MuHistoryData(
                id = row[id]?.value,
                deviceId = row[deviceId],
                device = MuDevice(id = row[deviceId], name = row[MuDevices.name]),
                dataValue0 = row[dataValue0],
                dataValue1 = row[dataValue1],
                dataValue2 = row[dataValue2],
                dateTime = row[dateTime],
                createTime = row[createTime])
    }
}

class MuHistoryDataDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuHistoryDataDao>(MuHistoryDatas)

    var deviceId by MuHistoryDatas.deviceId
    var dataValue0 by MuHistoryDatas.dataValue0
    var dataValue1 by MuHistoryDatas.dataValue1
    var dataValue2 by MuHistoryDatas.dataValue2
    var dateTime by MuHistoryDatas.dateTime
    var createTime by MuHistoryDatas.createTime

    fun toModel(): MuHistoryData {
        return MuHistoryData(
                id = id.value,
                deviceId = deviceId,
                dataValue0 = dataValue0,
                dataValue1 = dataValue1,
                dataValue2 = dataValue2,
                dateTime = dateTime,
                createTime = createTime)
    }
}

data class MuHistoryData(
        val id: String? = null,
        val deviceId: String? = null,
        val device: MuDevice? = null,
        val dataValue0: Double? = null,
        val dataValue1: Double? = null,
        val dataValue2: Double? = null,
        val dateTime: LocalDateTime? = null,
        val dateTimeRange: List<LocalDateTime>? = null,
        val createTime: LocalDateTime? = null)
