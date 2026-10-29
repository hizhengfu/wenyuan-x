package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.AccessionReceiptVo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object AccessionReceipts : OrgAuditUIDTable("col_accession_receipt") {
    val voucherId = varchar("voucher_id",64).index(shortIndexName("voucher_id"))
    val acquisitionId = varchar("acquisition_id",64).index(shortIndexName("acquisition_id"))
    val collectionId = varchar("collection_id",64).nullable().index(shortIndexName("collection_id"))
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val sourceSnapshot = text("source_snapshot")
    val content = json("content",{ JsonUtils.toJson(it)!! },{ JsonUtils.fromJson(it,AccessionReceiptVo::class.java)!! })
    init { uniqueIndex(shortIndexName("acquisition_version",unique=true),acquisitionId,version) }
}
class AccessionReceiptDao(id: EntityID<String>) : OrgAuditUIDEntity(id,AccessionReceipts) {
    companion object : OrgAuditUIDEntityClass<AccessionReceiptDao>(AccessionReceipts)
    var voucherId by AccessionReceipts.voucherId
    var acquisitionId by AccessionReceipts.acquisitionId
    var collectionId by AccessionReceipts.collectionId
    var version by AccessionReceipts.version
    var sourceSnapshot by AccessionReceipts.sourceSnapshot
    var content by AccessionReceipts.content
}
