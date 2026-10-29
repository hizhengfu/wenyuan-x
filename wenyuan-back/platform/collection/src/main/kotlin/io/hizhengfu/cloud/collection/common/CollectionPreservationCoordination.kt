package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import java.time.LocalDateTime

/** 派单、养护记录和告警复核统一先锁馆，再锁业务行；调用方必须处于同一事务。 */
object CollectionPreservationCoordination {
    fun lock(orgId: String) {
        CollectionOrgs.select(CollectionOrgs.id).where { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("收藏单位不存在")
    }
}

/** 可选修复模块提供实际养护证据，预防保护模块不直接依赖修复表。 */
interface CollectionRestorationAftercarePort {
    fun requireAlarmClosure(orgId: String, alarmId: String, affectedIds: Set<String>, at: LocalDateTime): List<RestorationAftercareProof>
    /** 告警服务完成当前查阅授权后获取证据版本；不要求项目已经具备关闭条件。 */
    fun alarmClosureRevision(orgId: String, alarmId: String): String = throw BadRequestException("当前修复模块未提供告警核对版本")
}

data class RestorationAftercareProof(
    val restorationId: String,
    val collectionId: String,
    val checkId: String,
    val acceptanceId: String,
    val checkedAt: LocalDateTime,
    val reviewedAt: LocalDateTime,
    val nextCheckAt: LocalDateTime,
    val checkArchiveId: String,
    val reviewArchiveId: String,
)

data class RestorationAftercareRecordedEvent(val orgId: String, val alarmId: String, val restorationId: String, val checkId: String)

data class RestorationAcceptanceChangedEvent(val orgId: String, val alarmId: String, val restorationId: String, val acceptanceId: String)
