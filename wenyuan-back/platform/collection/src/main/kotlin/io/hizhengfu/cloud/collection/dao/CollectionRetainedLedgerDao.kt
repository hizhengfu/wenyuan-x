package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionCatalogVersionVo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

data class CollectionRetainedLedgerIdentity(val orgId:String,val collectionId:String,val transferId:String,val targetOrgId:String)

data class CollectionRetainedLedgerSnapshot(
    val collection: CollectionVo,
    val catalogVersions: List<CollectionCatalogVersionVo> = emptyList(),
    val media: List<CollectionMediaVo> = emptyList(),
    val attachments: List<CollectionAttachmentVo> = emptyList(),
    /** 包括仅通过普通文件关联挂在主档或子记录上的原件；旧快照不回填。 */
    val originalFileIds: List<String> = emptyList(),
    /** 新留存冻结业务关联；旧档案不在读取时补造。 */
    val identity: CollectionRetainedLedgerIdentity? = null,
)
/** 原馆总账留存：转移后仍由原馆按原号检索，不从接收馆现时数据反推历史。 */
object CollectionRetainedLedgers : OrgAuditUIDTable("col_retained_ledger") {
    val collectionId=varchar("collection_id",64).index(shortIndexName("collection_id"))
    val transferId=varchar("transfer_id",64).uniqueIndex(shortIndexName("transfer_id",unique=true))
    val totalRegNo=varchar("total_reg_no",64)
    val name=varchar("name",500)
    val targetOrgId=varchar("target_org_id",64)
    val archiveId=varchar("archive_id",64)
    val snapshot=json("snapshot",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,CollectionRetainedLedgerSnapshot::class.java)!!})
    init { index(shortIndexName("org_id_total_reg_no"),false,orgId,totalRegNo) }
}
class CollectionRetainedLedgerDao(id: EntityID<String>) : OrgAuditUIDEntity(id,CollectionRetainedLedgers) {
    companion object : OrgAuditUIDEntityClass<CollectionRetainedLedgerDao>(CollectionRetainedLedgers)
    var collectionId by CollectionRetainedLedgers.collectionId
    var transferId by CollectionRetainedLedgers.transferId
    var totalRegNo by CollectionRetainedLedgers.totalRegNo
    var name by CollectionRetainedLedgers.name
    var targetOrgId by CollectionRetainedLedgers.targetOrgId
    var archiveId by CollectionRetainedLedgers.archiveId
    var snapshot by CollectionRetainedLedgers.snapshot
    fun summary()=mapOf("id" to id.value,"orgId" to orgId,"collectionId" to collectionId,"transferId" to transferId,"totalRegNo" to totalRegNo,"name" to name,"targetOrgId" to targetOrgId,"retainedAt" to createTime,"archiveId" to archiveId)
}
