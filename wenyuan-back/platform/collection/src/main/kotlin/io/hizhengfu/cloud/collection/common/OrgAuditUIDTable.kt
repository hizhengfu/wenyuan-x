package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditUIDTable
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/**
 * 收藏单位作用域基表：ContentAuditUIDTable（status + 审计） + orgId（收藏单位隔离列）。
 * 多馆下所有业务表继承此基类，查询经 [CollectionScopeResolver] 注入 orgId 过滤。
 */
open class OrgAuditUIDTable(name: String) : ContentAuditUIDTable(name) {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位 ID（关联 col_org.id）")
}

abstract class OrgAuditUIDEntity(id: EntityID<String>, table: OrgAuditUIDTable) : ContentAuditUIDEntity(id, table) {
    var orgId by table.orgId
}

abstract class OrgAuditUIDEntityClass<out E : OrgAuditUIDEntity>(table: OrgAuditUIDTable) :
    ContentAuditUIDEntityClass<E>(table)
