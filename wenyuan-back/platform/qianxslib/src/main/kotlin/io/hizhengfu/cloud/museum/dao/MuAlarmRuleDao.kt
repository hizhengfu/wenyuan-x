package io.hizhengfu.cloud.museum.dao

import EnumAlarmRuleType
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 预警规则表
 * */
object MuAlarmRules : UIDTable("mu_alarm_rule") {
    val type = enumeration<EnumAlarmRuleType>("type").default(EnumAlarmRuleType.PEOPLE)
    val value = double("value").nullable()//阈值
    val content = text("content").nullable()//描述

    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64)//创建人

    val status = enumeration<EnumContentStatus>("status").default(EnumContentStatus.NORMAL)

    fun toModel(row: ResultRow): MuAlarmRule {
        return MuAlarmRule(
                id = row[id]?.value,
                value = row[value],
                content = row[content],
                type = row[type],
                status = row[status],
                createBy = row[createBy],
                createTime = row[createTime])
    }
}


class MuAlarmRuleDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuAlarmRuleDao>(MuAlarmRules)

    var type by MuAlarmRules.type
    var value by MuAlarmRules.value
    var content by MuAlarmRules.content
    var status by MuAlarmRules.status
    var createTime by MuAlarmRules.createTime
    var createBy by MuAlarmRules.createBy

    fun toModel(): MuAlarmRule {
        return MuAlarmRule(
                id = id.value,
                type = type,
                value = value,
                content = content,
                status = status,
                createBy = createBy,
                createTime = createTime
        )
    }
}

data class MuAlarmRule(
        val id: String? = null,
        val type: EnumAlarmRuleType? = null,
        val value: Double? = null,
        val content: String? = null,
        val status: EnumContentStatus? = null,
        val createBy: String? = null,
        val createTime: LocalDateTime? = null,
)


