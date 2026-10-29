package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json

/** 只追加正式登记事实，不用藏品现状反推某馆往年总账。 */
object CollectionLedgerStatEvents : OrgAuditUIDTable("col_ledger_stat_event") {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val kind=enumeration<EnumLedgerStatEvent>("kind")
    val occurredAt=datetime("occurred_at")
    val sourceId=varchar("source_id",64)
    val reason=text("reason").nullable()
    val item=json("item",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,LedgerStatItemVo::class.java)!!})
    init { uniqueIndex(shortIndexName("org_collection_kind_source",unique=true),orgId,collectionId,kind,sourceId) }
}
class CollectionLedgerStatEventDao(id: EntityID<String>) : OrgAuditUIDEntity(id,CollectionLedgerStatEvents) {
    companion object : OrgAuditUIDEntityClass<CollectionLedgerStatEventDao>(CollectionLedgerStatEvents)
    var collectionId by CollectionLedgerStatEvents.collectionId
    var kind by CollectionLedgerStatEvents.kind
    var occurredAt by CollectionLedgerStatEvents.occurredAt
    var sourceId by CollectionLedgerStatEvents.sourceId
    var reason by CollectionLedgerStatEvents.reason
    var item by CollectionLedgerStatEvents.item
    fun toModel()=AnnualLedgerEventVo(id.value,kind,occurredAt,item,sourceId,createTime,reason)
}
